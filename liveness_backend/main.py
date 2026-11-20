import os
import sqlite3
import logging
from typing import Tuple, Optional, List, Dict, Any
import cv2
import numpy as np
from fastapi import FastAPI, Request, File, UploadFile, Form, HTTPException
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.middleware.cors import CORSMiddleware

# Nạp TFLite Interpreter (Tương thích cả ai_edge_litert, tflite_runtime và tensorflow)
try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:
    try:
        from tflite_runtime.interpreter import Interpreter
    except ImportError:
        import tensorflow as tf
        Interpreter = tf.lite.Interpreter

# Cấu hình Logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("LivenessBackend")

# Đường dẫn tuyệt đối cho toàn bộ tài nguyên Backend
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ANDROID_DB_FILE = os.path.join(BASE_DIR, "IrisDatabase.db")
MODEL_PATH = os.path.join(BASE_DIR, "models", "minivision_anti_spoof.tflite")
FACE_MATCH_MODEL_PATH = os.path.join(BASE_DIR, "models", "mobile_face_net.tflite")
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")

os.makedirs(os.path.join(UPLOAD_DIR, "students"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "admins"), exist_ok=True)
os.makedirs(os.path.join(BASE_DIR, "models"), exist_ok=True)

# Khởi tạo Ứng dụng FastAPI
app = FastAPI(
    title="Web Liveness Detection & Face Recognition Production API",
    description="Backend Python FastAPI tích hợp đầy đủ các tính năng Liveness, Anti-Spoofing và So khớp khuôn mặt 1:1 từ Android",
    version="3.0.0"
)

# Cấu hình CORS để Web chính (React / Vue / Angular / Django / PHP / HTML5) gọi API không bị chặn
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Mount thư mục tĩnh static và uploads
app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))


# ==================== 1. MINIVISION ANTI-SPOOFING (TFLite) ====================

class AntiSpoofHelper:
    """Mô hình AI Passive Anti-Spoofing (MiniVision TFLite) phân biệt người thật vs ảnh/video giả mạo."""
    REAL_THRESHOLD = 0.50

    def __init__(self, model_path: str):
        self.interpreter = None
        self.is_loaded = False
        self.is_nchw = True
        self.input_width = 80
        self.input_height = 80
        self.output_class_count = 3

        if not os.path.exists(model_path):
            logger.warning(f"Chưa tìm thấy model MiniVision tại: {model_path}")
            return

        try:
            self.interpreter = Interpreter(model_path=model_path)
            self.interpreter.allocate_tensors()
            in_det = self.interpreter.get_input_details()[0]
            out_det = self.interpreter.get_output_details()[0]
            self.input_index = in_det["index"]
            self.output_index = out_det["index"]

            shape = in_det["shape"]
            if len(shape) == 4:
                if shape[1] == 3:
                    self.is_nchw = True
                    self.input_height, self.input_width = int(shape[2]), int(shape[3])
                else:
                    self.is_nchw = False
                    self.input_height, self.input_width = int(shape[1]), int(shape[2])

            if len(out_det["shape"]) >= 2:
                self.output_class_count = int(out_det["shape"][1])
            self.is_loaded = True
            logger.info(f"Đã nạp MiniVision TFLite: NCHW={self.is_nchw}, Size={self.input_width}x{self.input_height}, Classes={self.output_class_count}")
        except Exception as e:
            logger.error(f"Lỗi nạp MiniVision TFLite: {e}")

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        e = np.exp(logits - np.max(logits))
        return e / np.sum(e)

    def classify_face(self, expanded_crop_bgr: np.ndarray) -> Tuple[bool, float, float]:
        if not self.is_loaded or expanded_crop_bgr is None or expanded_crop_bgr.size == 0:
            return False, 0.0, 1.0

        resized = cv2.resize(expanded_crop_bgr, (self.input_width, self.input_height), interpolation=cv2.INTER_LINEAR)
        img_float = resized.astype(np.float32)  # Hệ màu BGR thô [0..255]
        tensor_input = np.transpose(img_float, (2, 0, 1))[np.newaxis, ...] if self.is_nchw else img_float[np.newaxis, ...]

        self.interpreter.set_tensor(self.input_index, tensor_input)
        self.interpreter.invoke()
        probs = self._softmax(self.interpreter.get_tensor(self.output_index)[0])

        real_score = float(probs[1])
        spoof_score = float(probs[0] + probs[2]) if self.output_class_count == 3 else float(probs[0])
        return real_score >= self.REAL_THRESHOLD, real_score, spoof_score


# ==================== 2. MOBILEFACENET SO KHỚP KHUÔN MẶT ====================

class FaceMatchHelper:
    """Mô hình MobileFaceNet TFLite trích xuất Vector 192/128 chiều để so khớp khuôn mặt 1:1."""
    MATCH_THRESHOLD = 0.65  # Ngưỡng Cosine Similarity chuẩn cho MobileFaceNet

    def __init__(self, model_path: str):
        self.interpreter = None
        self.is_loaded = False
        if not os.path.exists(model_path):
            logger.warning(f"Chưa tìm thấy model MobileFaceNet tại: {model_path}")
            return

        try:
            self.interpreter = Interpreter(model_path=model_path)
            self.interpreter.allocate_tensors()
            self.input_details = self.interpreter.get_input_details()
            self.output_details = self.interpreter.get_output_details()
            self.is_loaded = True
            logger.info("Đã nạp MobileFaceNet TFLite cho So khớp khuôn mặt thành công!")
        except Exception as e:
            logger.error(f"Lỗi nạp MobileFaceNet TFLite: {e}")

    def extract_embedding(self, face_bgr: np.ndarray) -> Optional[np.ndarray]:
        if not self.is_loaded or face_bgr is None or face_bgr.size == 0:
            return None

        try:
            in_shape = self.input_details[0]["shape"]
            h = int(in_shape[2]) if (len(in_shape) == 4 and in_shape[1] == 3) else int(in_shape[1])
            w = int(in_shape[3]) if (len(in_shape) == 4 and in_shape[1] == 3) else int(in_shape[2])

            resized = cv2.resize(face_bgr, (w, h), interpolation=cv2.INTER_LINEAR)
            rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB).astype(np.float32)

            # Chuẩn hóa về dải [-1.0, 1.0] khớp 100% với FaceMatchHelper.java trên Android
            normalized_rgb = (rgb - 127.5) / 128.0
            input_data = np.expand_dims(normalized_rgb, axis=0)

            if len(in_shape) == 4 and in_shape[1] == 3:
                input_data = np.transpose(input_data, (0, 3, 1, 2))

            self.interpreter.set_tensor(self.input_details[0]["index"], input_data)
            self.interpreter.invoke()
            embedding = self.interpreter.get_tensor(self.output_details[0]["index"])[0]

            norm = np.linalg.norm(embedding)
            return (embedding / norm) if norm > 0 else None
        except Exception as e:
            logger.error(f"Lỗi trích xuất Vector MobileFaceNet: {e}")
            return None

    @staticmethod
    def compute_similarity(emb1: np.ndarray, emb2: np.ndarray) -> float:
        if emb1 is None or emb2 is None:
            return 0.0
        return float(np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2) + 1e-8))

    def compute_max_similarity(self, face1_bgr: np.ndarray, face2_bgr: np.ndarray) -> float:
        """Tính Cosine Similarity tối đa giữa 2 ảnh khuôn mặt (tự động đảo chiều lật gương camera)."""
        if face1_bgr is None or face2_bgr is None or face1_bgr.size == 0 or face2_bgr.size == 0:
            return 0.0

        face1_flip = cv2.flip(face1_bgr, 1)
        face2_flip = cv2.flip(face2_bgr, 1)

        e1_orig = self.extract_embedding(face1_bgr)
        e1_flip = self.extract_embedding(face1_flip)
        e2_orig = self.extract_embedding(face2_bgr)
        e2_flip = self.extract_embedding(face2_flip)

        sims = []
        for e1 in [e1_orig, e1_flip]:
            for e2 in [e2_orig, e2_flip]:
                if e1 is not None and e2 is not None:
                    sims.append(self.compute_similarity(e1, e2))

        return max(sims) if sims else 0.0


anti_spoof_helper = AntiSpoofHelper(MODEL_PATH)
face_match_helper = FaceMatchHelper(FACE_MATCH_MODEL_PATH)


# ==================== 3. KẾT NỐI VÀ QUẢN LÝ DATABASE ====================

def get_db_connection() -> sqlite3.Connection:
    """Kết nối trực tiếp vào IrisDatabase.db chuẩn Android."""
    if not os.path.exists(ANDROID_DB_FILE):
        init_db()
    conn = sqlite3.connect(ANDROID_DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn

def init_db():
    """Khởi tạo CSDL và nạp dữ liệu từ các file CSV mẫu nếu chưa có."""
    conn = sqlite3.connect(ANDROID_DB_FILE)
    cursor = conn.cursor()

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS students (
            student_id TEXT PRIMARY KEY,
            full_name TEXT NOT NULL,
            email TEXT,
            phone TEXT,
            photo_path TEXT,
            checkin_password TEXT DEFAULT ''
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS admin (
            admin_id TEXT PRIMARY KEY,
            admin_code TEXT,
            full_name TEXT NOT NULL,
            email TEXT,
            phone TEXT,
            photo TEXT
        )
    """)

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS web_liveness_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_id TEXT NOT NULL,
            folder TEXT NOT NULL,
            full_name TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            liveness_status TEXT NOT NULL,
            real_score REAL,
            spoof_score REAL,
            shift_score REAL,
            match_similarity REAL,
            reason TEXT
        )
    """)

    # Nạp dữ liệu từ CSV nếu bảng students chưa có bản ghi
    cursor.execute("SELECT COUNT(*) FROM students")
    if cursor.fetchone()[0] == 0:
        csv_student_path = os.path.join(BASE_DIR, "..", "sample_students_import.csv")
        if os.path.exists(csv_student_path):
            try:
                import csv
                with open(csv_student_path, "r", encoding="utf-8") as f:
                    reader = csv.DictReader(f)
                    for row in reader:
                        sid = row.get("student_id", "").strip()
                        name = row.get("full_name", "").strip()
                        phone = row.get("phone", "").strip()
                        email = row.get("email", "").strip()
                        photo = row.get("photo_path", "").strip()
                        if sid and name:
                            cursor.execute("INSERT OR REPLACE INTO students (student_id, full_name, email, phone, photo_path) VALUES (?, ?, ?, ?, ?)",
                                           (sid, name, email, phone, photo))
                conn.commit()
            except Exception as e:
                logger.error(f"Lỗi đọc CSV sinh viên: {e}")

    conn.close()

init_db()

def ensure_web_log_table():
    if not os.path.exists(ANDROID_DB_FILE):
        init_db()
    conn = get_db_connection()
    conn.execute("""
        CREATE TABLE IF NOT EXISTS web_liveness_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_id TEXT NOT NULL,
            folder TEXT NOT NULL,
            full_name TEXT NOT NULL,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
            liveness_status TEXT NOT NULL,
            real_score REAL,
            spoof_score REAL,
            shift_score REAL,
            match_similarity REAL,
            reason TEXT
        )
    """)
    conn.commit()
    conn.close()

ensure_web_log_table()

def resolve_local_photo_path(entity_id: str, folder: str, android_db_photo_path: Optional[str]) -> Optional[str]:
    """Ánh xạ đường dẫn ảnh đại diện chuẩn trên đĩa PC."""
    candidates = [
        os.path.join(UPLOAD_DIR, folder, f"avatar_{entity_id}.jpg"),
        os.path.join(UPLOAD_DIR, folder, f"face_{entity_id}.jpg"),
    ]
    if android_db_photo_path:
        base_name = os.path.basename(android_db_photo_path)
        if base_name:
            candidates.append(os.path.join(UPLOAD_DIR, folder, base_name))

    for path in candidates:
        if os.path.exists(path) and os.path.isfile(path):
            return path
    return None

def fetch_all_android_users() -> List[Dict[str, Any]]:
    """Đọc trực tiếp danh sách từ 2 bảng `students` và `admins` của Android IrisDatabase.db."""
    conn = get_db_connection()
    cursor = conn.cursor()
    users: List[Dict[str, Any]] = []

    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r["name"] for r in cursor.fetchall()]

    if "students" in tables:
        cursor.execute("SELECT * FROM students")
        for r in cursor.fetchall():
            row_dict = dict(r)
            eid = str(row_dict.get("student_id") or row_dict.get("id") or "")
            fname = row_dict.get("full_name") or row_dict.get("name") or eid
            ppath = row_dict.get("photo_path") or row_dict.get("photo") or ""
            if eid:
                local_photo = resolve_local_photo_path(eid, "students", ppath)
                has_photo = bool(ppath and len(str(ppath).strip()) > 0) or (local_photo is not None)
                users.append({
                    "entity_id": eid,
                    "folder": "students",
                    "full_name": fname,
                    "has_photo": has_photo,
                    "local_photo": local_photo
                })

    if "admins" in tables or "admin" in tables:
        tbl = "admin" if "admin" in tables else "admins"
        cursor.execute(f"SELECT * FROM {tbl}")
        for r in cursor.fetchall():
            row_dict = dict(r)
            eid = str(row_dict.get("admin_id") or row_dict.get("admin_code") or row_dict.get("id") or "")
            fname = row_dict.get("full_name") or row_dict.get("name") or eid
            ppath = row_dict.get("photo") or row_dict.get("photo_path") or ""
            if eid:
                local_photo = resolve_local_photo_path(eid, "admins", ppath)
                has_photo = bool(ppath and len(str(ppath).strip()) > 0) or (local_photo is not None)
                users.append({
                    "entity_id": eid,
                    "folder": "admins",
                    "full_name": fname,
                    "has_photo": has_photo,
                    "local_photo": local_photo
                })

    conn.close()
    return users

def check_duplicate_face_on_disk(new_face_bgr: np.ndarray, current_entity_id: str, current_folder: str) -> Optional[Any]:
    """
    1:1 Port của hàm checkDuplicateFace() trong AddStudentActivity.java & AdminDetailActivity.java:
    Quét trực tiếp tất cả file ảnh .jpg trong thư mục uploads/students/ và uploads/admins/
    để phát hiện gian lận trùng lặp khuôn mặt giữa các đối tượng khác nhau.
    """
    if not face_match_helper.is_loaded or new_face_bgr is None or new_face_bgr.size == 0:
        return None

    search_dirs = [
        ("students", os.path.join(UPLOAD_DIR, "students")),
        ("admins", os.path.join(UPLOAD_DIR, "admins"))
    ]

    for f_name, dir_path in search_dirs:
        if not os.path.exists(dir_path):
            continue

        for filename in os.listdir(dir_path):
            if not filename.lower().endswith(".jpg") or filename.startswith("temp_"):
                continue

            if filename == f"avatar_{current_entity_id}.jpg" or filename == f"face_{current_entity_id}.jpg":
                continue

            file_full_path = os.path.join(dir_path, filename)
            try:
                ref_img = cv2.imread(file_full_path)
                if ref_img is None or ref_img.size == 0:
                    continue

                similarity = face_match_helper.compute_max_similarity(new_face_bgr, ref_img)
                logger.info(f"Quét đĩa đối soát 1:1 -> [{current_entity_id}] vs [{filename}] = {similarity:.4f}")

                if similarity >= 0.55:
                    other_id = filename.replace("avatar_", "").replace("face_", "").replace(".jpg", "")
                    other_name = other_id
                    try:
                        conn = get_db_connection()
                        cursor = conn.cursor()
                        cursor.execute("SELECT full_name FROM students WHERE student_id = ?", (other_id,))
                        row = cursor.fetchone()
                        if not row:
                            cursor.execute("SELECT full_name FROM admin WHERE admin_id = ? OR admin_code = ?", (other_id, other_id))
                            row = cursor.fetchone()
                        conn.close()
                        if row:
                            other_name = row[0]
                    except Exception as e:
                        logger.error(f"Lỗi truy vấn tên đối tượng trùng: {e}")

                    return (other_name, other_id), similarity
            except Exception as e:
                logger.error(f"Lỗi quét file đối soát {filename}: {e}")

    return None

# ==================== 4. XỬ LÝ ẢNH & SPECTRUM FLASH ====================

def decode_image(file_bytes: bytes) -> np.ndarray:
    img = cv2.imdecode(np.frombuffer(file_bytes, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("Không thể giải mã ảnh")
    return img

def crop_tight_face_1_0x(img_bgr: np.ndarray, bx: float, by: float, bw: float, bh: float) -> np.ndarray:
    if img_bgr is None or img_bgr.size == 0:
        return img_bgr
    h, w, _ = img_bgr.shape
    x1 = int(max(0, bx * w))
    y1 = int(max(0, by * h))
    x2 = int(min(w, (bx + bw) * w))
    y2 = int(min(h, (by + bh) * h))
    if x2 - x1 < 10 or y2 - y1 < 10:
        return img_bgr
    cropped = img_bgr[y1:y2, x1:x2]
    return cropped if cropped.size > 0 else img_bgr

def crop_expanded_face_2_7x(img_bgr: np.ndarray, bx: float, by: float, bw: float, bh: float) -> np.ndarray:
    h, w, _ = img_bgr.shape
    cx, cy = (bx + bw / 2.0) * w, (by + bh / 2.0) * h
    max_dim = max(bw * w, bh * h) * 2.7 / 2.0
    return img_bgr[int(max(0, cy - max_dim)):int(min(h, cy + max_dim)), int(max(0, cx - max_dim)):int(min(w, cx + max_dim))]

def extract_face_core_rgb(img_bgr: np.ndarray, bx: float, by: float, bw: float, bh: float) -> Tuple[float, float, float]:
    h, w, _ = img_bgr.shape
    x1 = int(max(0, (bx + 0.22 * bw) * w))
    y1 = int(max(0, (by + 0.22 * bh) * h))
    x2 = int(min(w, (bx + 0.78 * bw) * w))
    y2 = int(min(h, (by + 0.78 * bh) * h))

    if x2 - x1 < 8 or y2 - y1 < 8:
        x1, y1, x2, y2 = int(w * 0.35), int(h * 0.35), int(w * 0.65), int(h * 0.65)

    core = img_bgr[y1:y2, x1:x2]
    mean_bgr = np.mean(core, axis=(0, 1))
    return float(mean_bgr[2]), float(mean_bgr[1]), float(mean_bgr[0])

def evaluate_red_blue_ratio_cycle(rgb_base: Tuple[float, float, float], rgb_red: Tuple[float, float, float], rgb_blue: Tuple[float, float, float]) -> Tuple[bool, float, str]:
    r_red, g_red, b_red = rgb_red
    r_blue, g_blue, b_blue = rgb_blue
    r_base, _, b_base = rgb_base

    if (0.299 * r_red + 0.587 * g_red + 0.114 * b_red) > 240.0 and (0.299 * r_blue + 0.587 * g_blue + 0.114 * b_blue) > 240.0:
        return False, 0.0, "Phát hiện độ chói đèn nền màn hình quá cao"

    ratio_red = r_red / (b_red + 1.0)
    ratio_blue = r_blue / (b_blue + 1.0)
    shift = ratio_red - ratio_blue

    sum_red = r_red + g_red + b_red + 1e-3
    sum_blue = r_blue + g_blue + b_blue + 1e-3
    norm_r = (r_red / sum_red) - (r_blue / sum_blue)
    norm_b = (b_blue / sum_blue) - (b_red / sum_red)

    passed = (shift >= 0.008) and (norm_r >= 0.0010) and (norm_b >= 0.0010) and (ratio_red > ratio_blue) and ((r_red - r_base) >= -2.0) and ((b_blue - b_base) >= -2.0)
    if not passed:
        return False, round(shift, 4), "Phát hiện video giả mạo! (Không đạt phản xạ ánh sáng Flash)"
    return True, round(shift, 4), "Hợp lệ"

def validate_and_process_student_photo(
        img_bgr: np.ndarray,
        entity_id: str,
        folder: str,
        tight_crop: Optional[np.ndarray] = None
) -> Tuple[bool, str, Dict[str, Any]]:
    """
    Logic 1:1 với AddStudentActivity.java & AdminDetailActivity.java trên Android:
    1. Kiểm tra gian lận trùng khuôn mặt với TẤT CẢ sinh viên/admin KHÁC trong CSDL (checkIfFaceAlreadyExists).
    2. Nếu đối tượng này ĐÃ CÓ avatar cũ -> Kiểm tra ảnh mới có khớp với avatar cũ hay không (isOwner).
    3. Nếu hợp lệ -> Lưu ảnh chính thức và cập nhật photo_path vào IrisDatabase.db.
    """
    users = fetch_all_android_users()
    target_user = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == folder), None)
    full_name = target_user["full_name"] if target_user else entity_id
    save_folder = folder if folder in ["students", "admins"] else "students"

    crop_to_use = tight_crop if (tight_crop is not None and tight_crop.size > 0) else img_bgr

    # Bước 1: Kiểm tra gian lận trùng khuôn mặt với các sinh viên KHÁC (checkIfFaceAlreadyExists)
    dup_info = check_duplicate_face_on_disk(img_bgr, entity_id, folder)
    if dup_info is None and tight_crop is not None and tight_crop.size > 0:
        dup_info = check_duplicate_face_on_disk(tight_crop, entity_id, folder)

    if dup_info is not None:
        (ex_name, ex_id), raw_similarity = dup_info
        similarity_val = float(raw_similarity)
        msg = f"❌ Gian lận: Khuôn mặt này đã được sử dụng bởi sinh viên mã: {ex_id} ({ex_name}) - Khớp {similarity_val * 100:.1f}%!"
        logger.warning(f"Từ chối đăng ký ảnh: {msg}")
        return False, msg, {"duplicate_id": str(ex_id), "duplicate_name": str(ex_name), "similarity": round(similarity_val, 4)}

    # Bước 2: Nếu đối tượng ĐÃ CÓ avatar cũ -> Kiểm tra ảnh mới có khớp chính chủ hay không (isOwner)
    existing_official_photo = os.path.join(UPLOAD_DIR, save_folder, f"avatar_{entity_id}.jpg")
    if os.path.exists(existing_official_photo):
        try:
            old_img = cv2.imread(existing_official_photo)
            if old_img is not None and face_match_helper.is_loaded:
                crop_for_owner = tight_crop if (tight_crop is not None and tight_crop.size > 0) else img_bgr
                raw_similarity = face_match_helper.compute_max_similarity(crop_for_owner, old_img)
                similarity_val = float(raw_similarity)
                logger.info(f"Kiểm tra chính chủ cập nhật ảnh -> [{entity_id}] vs [Avatar cũ] = {similarity_val:.4f}")
                is_owner = similarity_val >= 0.45  # Ngưỡng an toàn khi chính chủ chụp cập nhật ảnh mới
                if not is_owner:
                    msg = f"❌ Khuôn mặt không khớp với avatar hiện tại của sinh viên này! (Độ khớp: {similarity_val * 100:.1f}%)"
                    logger.warning(msg)
                    return False, msg, {"owner_mismatch": True, "similarity": round(similarity_val, 4)}
        except Exception as e:
            logger.error(f"Lỗi kiểm tra chính chủ: {e}")

    # Bước 3: Lưu ảnh chính thức và cập nhật CSDL
    file_path = os.path.join(UPLOAD_DIR, save_folder, f"avatar_{entity_id}.jpg")
    cv2.imwrite(file_path, crop_to_use)

    conn = get_db_connection()
    if save_folder == "students":
        conn.execute("UPDATE students SET photo_path = ? WHERE student_id = ?", (file_path, entity_id))
    else:
        conn.execute("UPDATE admin SET photo = ? WHERE admin_id = ? OR admin_code = ?", (file_path, entity_id, entity_id))
    conn.commit()
    conn.close()

    success_msg = f"✅ Đăng ký khuôn mặt thành công cho sinh viên {full_name} ({entity_id})!"
    logger.info(success_msg)
    return True, success_msg, {"photo_path": str(file_path)}


# ==================== 5. TOÀN BỘ API ENDPOINTS CHO WEB THẬT ====================

@app.get("/", response_class=HTMLResponse)
async def serve_home(request: Request):
    """Trang chủ giao diện Web Test."""
    return templates.TemplateResponse("index.html", {"request": request})

@app.get("/health")
async def health_check():
    """API Kiểm tra Trạng thái Server & AI Models."""
    return {
        "status": "ONLINE",
        "database": os.path.exists(ANDROID_DB_FILE),
        "anti_spoof_model_loaded": anti_spoof_helper.is_loaded,
        "face_match_model_loaded": face_match_helper.is_loaded
    }

@app.get("/api/v1/users")
async def get_users():
    """API Lấy danh sách Sinh viên & Admin từ CSDL Android IrisDatabase.db."""
    try:
        users = fetch_all_android_users()
        return {"success": True, "users": users}
    except Exception as e:
        return JSONResponse(status_code=200, content={"success": False, "message": str(e), "users": []})

@app.get("/api/v1/attendance-logs")
async def get_logs():
    """API Lấy lịch sử Điểm danh trên Web."""
    ensure_web_log_table()
    conn = get_db_connection()
    rows = conn.execute("""
        SELECT entity_id, folder, full_name, timestamp, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason
        FROM web_liveness_logs ORDER BY id DESC LIMIT 25
    """).fetchall()
    conn.close()
    return {"success": True, "logs": [dict(r) for r in rows]}

@app.post("/api/v1/add-user")
async def add_user(
    entity_id: str = Form(...),
    full_name: str = Form(...),
    folder: str = Form("students"),
    sub_info: str = Form("Sinh viên mới")
):
    """API Thêm đối tượng mới trực tiếp vào CSDL Android."""
    try:
        conn = get_db_connection()
        if folder == "students":
            conn.execute("INSERT OR REPLACE INTO students (student_id, full_name, email, phone, photo_path) VALUES (?, ?, '', ?, '')",
                         (entity_id, full_name, sub_info))
        else:
            conn.execute("INSERT OR REPLACE INTO admin (admin_id, admin_code, full_name, email, phone, photo) VALUES (?, ?, ?, '', '', '')",
                         (entity_id, entity_id, full_name))
        conn.commit()
        conn.close()
        return {"success": True, "message": f"✅ Đã thêm đối tượng {full_name} ({entity_id}) vào CSDL!"}
    except Exception as e:
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ Lỗi thêm đối tượng: {str(e)}"})

@app.post("/api/v1/register-face")
async def register_face(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        bbox_x: float = Form(0.25),
        bbox_y: float = Form(0.20),
        bbox_w: float = Form(0.50),
        bbox_h: float = Form(0.60),
        face_image: UploadFile = File(...)
):
    """API Chụp Đăng ký qua Camera Web (MODE_REGISTER)"""
    try:
        img_bgr = decode_image(await face_image.read())
        tight_crop = crop_tight_face_1_0x(img_bgr, bbox_x, bbox_y, bbox_w, bbox_h)

        success, message, extra = validate_and_process_student_photo(img_bgr, entity_id, folder, tight_crop)

        return JSONResponse(
            status_code=200,
            content={"success": success, "message": message, **extra}
        )
    except Exception as e:
        logger.error(f"Lỗi register_face API: {e}", exc_info=True)
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ Lỗi Server: {str(e)}"})

@app.post("/api/v1/upload-student-photo")
async def upload_student_photo(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        photo_file: UploadFile = File(...)
):
    """API Upload File Ảnh từ Máy tính / Web thật cho Sinh viên (Khớp 1:1 AddStudentActivity.java)"""
    try:
        img_bgr = decode_image(await photo_file.read())

        success, message, extra = validate_and_process_student_photo(img_bgr, entity_id, folder)

        return JSONResponse(
            status_code=200,
            content={"success": success, "message": message, **extra}
        )
    except Exception as e:
        logger.error(f"Lỗi upload_student_photo API: {e}", exc_info=True)
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ Lỗi Server: {str(e)}"})

@app.post("/api/v1/verify-liveness")
async def verify_liveness(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        bbox_x: float = Form(0.25),
        bbox_y: float = Form(0.20),
        bbox_w: float = Form(0.50),
        bbox_h: float = Form(0.60),
        baseline_file: UploadFile = File(...),
        red_flash_file: UploadFile = File(...),
        blue_flash_file: UploadFile = File(...)
):
    """API Xác thực Liveness + So khớp khuôn mặt 1:1 cho Chế độ Điểm danh (MODE_CHECKIN)"""
    ensure_web_log_table()
    users = fetch_all_android_users()
    target_user = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == folder), None)

    full_name = target_user["full_name"] if target_user else entity_id
    registered_photo_path = target_user["local_photo"] if target_user else None

    conn = get_db_connection()

    # 1. Từ chối ngay nếu Sinh viên CHƯA ĐĂNG KÝ ảnh đại diện trong CSDL
    if not registered_photo_path or not os.path.exists(registered_photo_path):
        fail_msg = f"TỪ CHỐI ĐIỂM DANH: Sinh viên {full_name} chưa đăng ký ảnh đại diện mẫu trong CSDL! Vui lòng nhấn 'Chụp Đăng ký' trước."
        conn.execute("""
            INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
            VALUES (?, ?, ?, 'FAILED', 0.0, 0.0, 0.0, 0.0, 'Chưa đăng ký ảnh mẫu')
        """, (entity_id, folder, full_name))
        conn.commit()
        conn.close()
        return JSONResponse(status_code=200, content={
            "success": False,
            "is_matched": False,
            "requires_registration": True,
            "message": f"❌ {fail_msg}"
        })

    img_base = decode_image(await baseline_file.read())
    img_red = decode_image(await red_flash_file.read())
    img_blue = decode_image(await blue_flash_file.read())

    # 2. Bước A: Phân tích phổ màu phản xạ chớp sáng Screen Flash
    rgb_base = extract_face_core_rgb(img_base, bbox_x, bbox_y, bbox_w, bbox_h)
    rgb_red = extract_face_core_rgb(img_red, bbox_x, bbox_y, bbox_w, bbox_h)
    rgb_blue = extract_face_core_rgb(img_blue, bbox_x, bbox_y, bbox_w, bbox_h)

    flash_passed, shift_score, flash_msg = evaluate_red_blue_ratio_cycle(rgb_base, rgb_red, rgb_blue)
    if not flash_passed:
        conn.execute("""
            INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
            VALUES (?, ?, ?, 'FAILED', 0.0, 1.0, ?, 0.0, ?)
        """, (entity_id, folder, full_name, shift_score, flash_msg))
        conn.commit()
        conn.close()
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ {flash_msg}"})

    # 3. Bước B: Phân tích AI MiniVision Passive Anti-Spoofing (2.7x Crop)
    expanded_crop = crop_expanded_face_2_7x(img_base, bbox_x, bbox_y, bbox_w, bbox_h)
    is_real, real_score, spoof_score = anti_spoof_helper.classify_face(expanded_crop)

    if not is_real:
        fail_reason = f"Phát hiện video/ảnh giả mạo! (Score: {spoof_score:.2f})"
        conn.execute("""
            INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
            VALUES (?, ?, ?, 'FAILED', ?, ?, ?, 0.0, ?)
        """, (entity_id, folder, full_name, round(real_score, 3), round(spoof_score, 3), shift_score, fail_reason))
        conn.commit()
        conn.close()
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ {fail_reason}"})

    # 4. Bước C: So khớp danh tính MobileFaceNet 1:1 với Ảnh đại diện gốc
    ref_img = cv2.imread(registered_photo_path)
    tight_crop = crop_tight_face_1_0x(img_base, bbox_x, bbox_y, bbox_w, bbox_h)

    raw_similarity = face_match_helper.compute_max_similarity(tight_crop, ref_img)
    similarity_val = float(raw_similarity)

    if similarity_val < face_match_helper.MATCH_THRESHOLD:
        fail_reason = f"SAI KHUÔN MẶT! Khuôn mặt đứng trước camera không trùng khớp với ảnh đại diện của {full_name} (Độ giống: {similarity_val * 100:.1f}%)"
        conn.execute("""
            INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
            VALUES (?, ?, ?, 'FAILED', ?, ?, ?, ?, 'Sai khuôn mặt')
        """, (entity_id, folder, full_name, round(real_score, 3), round(spoof_score, 3), shift_score, round(similarity_val, 4)))
        conn.commit()
        conn.close()
        return JSONResponse(status_code=200, content={"success": False, "is_matched": False, "message": f"❌ {fail_reason}"})

    # ĐIỂM DANH THÀNH CÔNG THỎA MÃN CẢ 3 LỚP
    success_msg = f"✅ Liveness & So khớp khuôn mặt THÀNH CÔNG! Đã xác nhận điểm danh cho {full_name} (Độ giống: {similarity_val * 100:.1f}%)"
    conn.execute("""
        INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
        VALUES (?, ?, ?, 'PASSED', ?, ?, ?, ?, 'Hợp lệ')
    """, (entity_id, folder, full_name, round(real_score, 3), round(spoof_score, 3), shift_score, round(similarity_val, 4)))
    conn.commit()
    conn.close()

    return JSONResponse(status_code=200, content={
        "success": True,
        "is_matched": True,
        "message": success_msg,
        "metrics": {
            "real_score": round(real_score, 3),
            "shift_score": shift_score,
            "match_similarity": round(similarity_val, 4)
        }
    })

@app.post("/api/v1/log-client-fail")
async def log_client_fail(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        reason: str = Form(...)
):
    """Ghi nhận lỗi từ Client State Machine (Freeze frame, Splice cut, Timeout, 3D Parallax)"""
    ensure_web_log_table()
    conn = get_db_connection()
    cursor = conn.cursor()
    users = fetch_all_android_users()
    target_user = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == folder), None)
    full_name = target_user["full_name"] if target_user else entity_id

    conn.execute("""
        INSERT INTO web_liveness_logs (entity_id, folder, full_name, liveness_status, real_score, spoof_score, shift_score, match_similarity, reason)
        VALUES (?, ?, ?, 'FAILED', 0.0, 1.0, 0.0, 0.0, ?)
    """, (entity_id, folder, full_name, reason))
    conn.commit()
    conn.close()
    return {"success": True, "message": "Đã ghi nhận log thất bại"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)

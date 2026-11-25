import os
import sqlite3
import logging
from datetime import datetime
from typing import Tuple, Optional, List, Dict, Any
import cv2
import numpy as np

from fastapi import FastAPI, Request, Response, File, UploadFile, Form
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from fastapi.middleware.cors import CORSMiddleware

from core.tflite_models import AntiSpoofHelper, FaceMatchHelper

# Cấu hình Logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s - %(levelname)s - %(message)s")
logger = logging.getLogger("LivenessBackend")

# Đường dẫn tuyệt đối chuẩn (Đồng bộ với DatabaseHelper.java -> attendance.db)
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DB_FILE = os.path.join(BASE_DIR, "attendance.db")
MODEL_PATH = os.path.join(BASE_DIR, "models", "minivision_anti_spoof.tflite")
FACE_MATCH_MODEL_PATH = os.path.join(BASE_DIR, "models", "mobile_face_net.tflite")
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")

os.makedirs(os.path.join(UPLOAD_DIR, "students"), exist_ok=True)
os.makedirs(os.path.join(UPLOAD_DIR, "admins"), exist_ok=True)
os.makedirs(os.path.join(BASE_DIR, "models"), exist_ok=True)

app = FastAPI(
    title="Web Liveness Detection & Face Recognition API (1:1 Android Port)",
    description="Backend FastAPI đồng bộ 1:1 với AntiSpoofHelper, FaceMatchHelper, LivenessLogic, FaceCaptureActivity, AddStudentActivity, AdminDetailActivity và DatabaseHelper.",
    version="5.1.0"
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.mount("/static", StaticFiles(directory=os.path.join(BASE_DIR, "static")), name="static")
app.mount("/uploads", StaticFiles(directory=UPLOAD_DIR), name="uploads")
templates = Jinja2Templates(directory=os.path.join(BASE_DIR, "templates"))

# Nạp 2 mô hình AI & bộ phát hiện khuôn mặt cho ảnh upload tĩnh
anti_spoof_helper = AntiSpoofHelper(MODEL_PATH)
face_match_helper = FaceMatchHelper(FACE_MATCH_MODEL_PATH)
haar_face_cascade = cv2.CascadeClassifier(cv2.data.haarcascades + "haarcascade_frontalface_default.xml")


# ==================== CƠ SỞ DỮ LIỆU SQLITE (attendance.db) ====================

def get_db_connection() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_FILE)
    conn.row_factory = sqlite3.Row
    return conn


def init_db():
    conn = get_db_connection()
    cursor = conn.cursor()

    # Bảng students (khớp DatabaseHelper.java)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS students (
            student_id TEXT PRIMARY KEY,
            full_name TEXT,
            email TEXT,
            phone TEXT,
            photo_path TEXT,
            checkin_password TEXT
        )
    """)

    # Bảng admin (khớp DatabaseHelper.java: cột photo_path và role)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS admin (
            admin_id TEXT PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            full_name TEXT NOT NULL,
            date_of_birth TEXT,
            phone TEXT,
            gender TEXT,
            photo_path TEXT,
            description TEXT,
            role TEXT NOT NULL DEFAULT 'ADMIN',
            admin_code TEXT
        )
    """)

    # Bảng checkin_history (khớp DatabaseHelper.java)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS checkin_history (
            checkin_id INTEGER PRIMARY KEY AUTOINCREMENT,
            student_id TEXT,
            subject_id TEXT,
            checkin_time TEXT,
            checkin_date TEXT
        )
    """)

    # Bảng liveness_logs (lưu chi tiết điểm AI & lý do để hiển thị trên Web test/Web chính)
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS liveness_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            entity_id TEXT,
            folder TEXT,
            full_name TEXT,
            liveness_status TEXT,
            real_score REAL DEFAULT 0.0,
            shift_score REAL DEFAULT 0.0,
            match_similarity REAL DEFAULT 0.0,
            reason TEXT,
            timestamp TEXT
        )
    """)

    conn.commit()
    conn.close()


init_db()


def save_liveness_log(
        entity_id: str,
        folder: str,
        full_name: str,
        status: str,
        reason: str,
        real_score: float = 0.0,
        shift_score: float = 0.0,
        match_similarity: float = 0.0,
        subject_id: Optional[str] = None
):
    now = datetime.now()
    ts = now.strftime("%Y-%m-%d %H:%M:%S")
    conn = get_db_connection()
    conn.execute(
        """
        INSERT INTO liveness_logs (entity_id, folder, full_name, liveness_status, real_score, shift_score, match_similarity, reason, timestamp)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (entity_id, folder, full_name, status, real_score, shift_score, match_similarity, reason, ts)
    )
    # Nếu là sinh viên điểm danh PASSED -> ghi thêm vào checkin_history (chuẩn ngày YYYY-MM-DD đồng bộ Nhóm 1)
    if status == "PASSED" and folder == "students":
        conn.execute(
            """
            INSERT INTO checkin_history (student_id, subject_id, checkin_time, checkin_date)
            VALUES (?, ?, ?, ?)
            """,
            (entity_id, subject_id or "WEB_CHECKIN", now.strftime("%H:%M:%S"), now.strftime("%Y-%m-%d"))
        )
    conn.commit()
    conn.close()


def resolve_local_photo_path(entity_id: str, folder: str, db_photo_path: Optional[str]) -> Optional[str]:
    candidates = [
        os.path.join(UPLOAD_DIR, folder, f"avatar_{entity_id}.jpg"),
        os.path.join(UPLOAD_DIR, folder, f"face_{entity_id}.jpg"),
    ]
    if db_photo_path:
        if os.path.exists(db_photo_path) and os.path.isfile(db_photo_path):
            return db_photo_path
        base_name = os.path.basename(db_photo_path)
        if base_name:
            candidates.append(os.path.join(UPLOAD_DIR, folder, base_name))

    for path in candidates:
        if os.path.exists(path) and os.path.isfile(path):
            return path
    return None


def get_admin_role(admin_id: str) -> Optional[str]:
    conn = get_db_connection()
    row = conn.execute("SELECT role FROM admin WHERE admin_id = ?", (admin_id,)).fetchone()
    conn.close()
    return row["role"] if row else None


def fetch_all_users() -> List[Dict[str, Any]]:
    conn = get_db_connection()
    cursor = conn.cursor()
    users: List[Dict[str, Any]] = []

    cursor.execute("SELECT name FROM sqlite_master WHERE type='table'")
    tables = [r["name"] for r in cursor.fetchall()]

    if "students" in tables:
        for r in cursor.execute("SELECT * FROM students").fetchall():
            d = dict(r)
            eid = str(d.get("student_id") or "")
            if eid:
                ppath = d.get("photo_path") or ""
                local_photo = resolve_local_photo_path(eid, "students", ppath)
                users.append({
                    "entity_id": eid,
                    "folder": "students",
                    "full_name": d.get("full_name") or eid,
                    "role": "STUDENT",
                    "has_photo": local_photo is not None,
                    "local_photo": local_photo
                })

    if "admin" in tables:
        for r in cursor.execute("SELECT * FROM admin").fetchall():
            d = dict(r)
            eid = str(d.get("admin_id") or "")
            if eid:
                ppath = d.get("photo_path") or d.get("photo") or ""
                local_photo = resolve_local_photo_path(eid, "admins", ppath)
                users.append({
                    "entity_id": eid,
                    "folder": "admins",
                    "full_name": d.get("full_name") or eid,
                    "role": d.get("role") or "ADMIN",
                    "has_photo": local_photo is not None,
                    "local_photo": local_photo
                })

    conn.close()
    return users


# ==================== XỬ LÝ ẢNH & CROP ====================

def decode_image(file_bytes: bytes) -> np.ndarray:
    img = cv2.imdecode(np.frombuffer(file_bytes, np.uint8), cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("File tải lên không phải định dạng ảnh hợp lệ")
    return img


def detect_and_crop_face_static(img_bgr: np.ndarray, margin: float = 0.35) -> Optional[np.ndarray]:
    """Tương đương ML Kit FaceDetector ở AddStudentActivity & AdminDetailActivity."""
    if img_bgr is None or img_bgr.size == 0:
        return None
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)
    faces = haar_face_cascade.detectMultiScale(gray, scaleFactor=1.1, minNeighbors=5, minSize=(60, 60))
    if len(faces) == 0:
        return None
    x, y, w, h = max(faces, key=lambda f: f[2] * f[3])
    img_h, img_w, _ = img_bgr.shape

    pad_w = int(w * margin)
    pad_h = int(h * (margin + 0.05))

    x1 = max(0, x - pad_w)
    y1 = max(0, y - pad_h)
    x2 = min(img_w, x + w + pad_w)
    y2 = min(img_h, y + h + pad_h)

    cropped = img_bgr[y1:y2, x1:x2]
    return cropped if cropped.size > 0 else img_bgr


def get_face_crop_for_matching(img_bgr: np.ndarray, bx: Optional[float] = None, by: Optional[float] = None, bw: Optional[float] = None, bh: Optional[float] = None) -> np.ndarray:
    """Thống nhất 1 hàm cắt ảnh chuẩn (margin=0.20) cho cả Đăng ký, Upload, Check-duplicate và Xác thực."""
    if img_bgr is None or img_bgr.size == 0:
        return img_bgr
    cropped_haar = detect_and_crop_face_static(img_bgr, margin=0.20)
    if cropped_haar is not None and cropped_haar.size > 0:
        return cropped_haar
    if all(v is not None for v in (bx, by, bw, bh)):
        return crop_tight_face_1_0x(img_bgr, bx, by, bw, bh)
    return img_bgr


def check_face_lighting(face_bgr: np.ndarray, min_brightness: float = 65.0, max_brightness: float = 225.0) -> Tuple[bool, str]:
    """Kiểm tra độ sáng trung bình trên vùng khuôn mặt (kênh Y trong không gian YCrCb)."""
    ycrcb = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2YCrCb)
    mean_luma = float(np.mean(ycrcb[:, :, 0]))
    if mean_luma < min_brightness:
        return False, f"❌ Ánh sáng quá yếu ({mean_luma:.0f}/255)! Vui lòng bật đèn hoặc đến nơi đủ sáng rồi thử lại."
    if mean_luma > max_brightness:
        return False, f"❌ Ánh sáng quá chói ({mean_luma:.0f}/255)! Vui lòng tránh ngược sáng hoặc giảm độ chói."
    return True, "OK"


def normalize_face_lighting(face_bgr: np.ndarray) -> np.ndarray:
    """Cân bằng ánh sáng cục bộ bằng CLAHE trên kênh L (LAB) để MobileFaceNet ổn định hơn khi đổi ánh sáng."""
    if face_bgr is None or face_bgr.size == 0:
        return face_bgr
    lab = cv2.cvtColor(face_bgr, cv2.COLOR_BGR2LAB)
    l_ch, a_ch, b_ch = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    l_norm = clahe.apply(l_ch)
    merged = cv2.merge((l_norm, a_ch, b_ch))
    return cv2.cvtColor(merged, cv2.COLOR_LAB2BGR)


def crop_tight_face_1_0x(img_bgr: np.ndarray, bx: float, by: float, bw: float, bh: float) -> np.ndarray:
    h, w, _ = img_bgr.shape
    x1, y1 = int(max(0, bx * w)), int(max(0, by * h))
    x2, y2 = int(min(w, (bx + bw) * w)), int(min(h, (by + bh) * h))
    if x2 - x1 < 10 or y2 - y1 < 10:
        return img_bgr
    cropped = img_bgr[y1:y2, x1:x2]
    return cropped if cropped.size > 0 else img_bgr


def crop_expanded_face_2_7x(img_bgr: np.ndarray, bx: float, by: float, bw: float, bh: float) -> np.ndarray:
    h, w, _ = img_bgr.shape
    cx, cy = (bx + bw / 2.0) * w, (by + bh / 2.0) * h
    max_dim = max(bw * w, bh * h) * 2.7 / 2.0
    x1, y1 = int(max(0, cx - max_dim)), int(max(0, cy - max_dim))
    x2, y2 = int(min(w, cx + max_dim)), int(min(h, cy + max_dim))
    cropped = img_bgr[y1:y2, x1:x2]
    return cropped if cropped.size > 0 else img_bgr


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


def evaluate_red_blue_ratio_cycle(
        rgb_base: Tuple[float, float, float],
        rgb_red: Tuple[float, float, float],
        rgb_blue: Tuple[float, float, float]
) -> Tuple[bool, float, str]:
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

    passed = (
            (shift >= 0.008)
            and (norm_r >= 0.0010)
            and (norm_b >= 0.0010)
            and (ratio_red > ratio_blue)
            and ((r_red - r_base) >= -2.0)
            and ((b_blue - b_base) >= -2.0)
    )
    if not passed:
        return False, round(shift, 4), "Phát hiện video giả mạo! (Không đạt phản xạ ánh sáng Flash)"
    return True, round(shift, 4), "Hợp lệ"


# ==================== CHECK TRÙNG & CHÍNH CHỦ ====================

def check_if_face_already_exists(
        new_face_bgr: np.ndarray,
        current_entity_id: str,
        folder: str
) -> Optional[Any]:
    if not face_match_helper.is_model_loaded or new_face_bgr is None or new_face_bgr.size == 0:
        return None

    new_emb = face_match_helper.get_face_embedding(normalize_face_lighting(new_face_bgr))
    if new_emb is None:
        return None

    dir_path = os.path.join(UPLOAD_DIR, folder)
    if not os.path.exists(dir_path):
        return None

    # AdminDetailActivity: chỉ so trùng trong CÙNG role (SUPER_ADMIN vs SUPER_ADMIN, ADMIN vs ADMIN)
    my_role = get_admin_role(current_entity_id) if folder == "admins" else None

    for filename in os.listdir(dir_path):
        if not filename.lower().endswith(".jpg") or filename.startswith("temp_"):
            continue
        if filename in (f"avatar_{current_entity_id}.jpg", f"face_{current_entity_id}.jpg"):
            continue

        other_id = filename.replace("avatar_", "").replace("face_", "").replace(".jpg", "")
        if folder == "admins" and my_role is not None:
            other_role = get_admin_role(other_id)
            if other_role is not None and my_role != other_role:
                continue

        file_full_path = os.path.join(dir_path, filename)
        try:
            ref_img = cv2.imread(file_full_path)
            if ref_img is None or ref_img.size == 0:
                continue
            exist_emb = face_match_helper.get_face_embedding(normalize_face_lighting(ref_img))
            if exist_emb is not None:
                matched, similarity = face_match_helper.compare_faces(exist_emb, new_emb)
                if matched and similarity is not None:
                    return other_id, float(similarity)
        except Exception as e:
            logger.error(f"Lỗi đọc file đối chiếu {filename}: {e}")

    return None


def validate_and_save_photo(
        full_img_bgr: np.ndarray,
        entity_id: str,
        folder: str,
        bx: float = 0.25, by: float = 0.20, bw: float = 0.50, bh: float = 0.60
) -> Tuple[bool, str, Dict[str, Any]]:
    save_folder = folder if folder in ("students", "admins") else "students"
    users = fetch_all_users()
    target_user = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == save_folder), None)
    full_name = target_user["full_name"] if target_user else entity_id

    face_crop = get_face_crop_for_matching(full_img_bgr, bx, by, bw, bh)

    light_ok, light_msg = check_face_lighting(face_crop)
    if not light_ok:
        return False, light_msg, {}

    new_emb = face_match_helper.get_face_embedding(normalize_face_lighting(face_crop))
    if new_emb is None:
        new_emb = face_match_helper.get_face_embedding(normalize_face_lighting(full_img_bgr))

    if new_emb is None:
        return False, "❌ Lỗi: Không trích xuất được đặc trưng khuôn mặt.", {}

    target_dir = os.path.join(UPLOAD_DIR, save_folder)
    existing_file = resolve_local_photo_path(entity_id, save_folder, target_user["local_photo"] if target_user else None)

    # Bước 1: Kiểm tra chính chủ nếu đã có ảnh cũ
    if existing_file and os.path.exists(existing_file):
        old_img = cv2.imread(existing_file)
        if old_img is not None:
            old_emb = face_match_helper.get_face_embedding(normalize_face_lighting(old_img))
            if old_emb is not None:
                is_owner, owner_sim = face_match_helper.compare_faces(old_emb, new_emb)
                if not is_owner:
                    return False, f"❌ Khuôn mặt không khớp với avatar hiện tại của sinh viên này! (Độ khớp: {float(owner_sim or 0.0) * 100:.1f}%)", {
                        "owner_mismatch": True,
                        "similarity": round(float(owner_sim or 0.0), 4)
                    }

    # Bước 2: Kiểm tra trùng khuôn mặt với sinh viên khác
    dup_res = check_if_face_already_exists(face_crop, entity_id, save_folder)
    if dup_res is None and face_crop.shape != full_img_bgr.shape:
        dup_res = check_if_face_already_exists(full_img_bgr, entity_id, save_folder)

    if dup_res is not None:
        duplicate_id, similarity = dup_res
        return False, f"❌ Gian lận: Khuôn mặt này đã được dùng bởi sinh viên mã: {duplicate_id}", {
            "duplicate_id": duplicate_id,
            "similarity": round(float(similarity), 4)
        }

    # Bước 3: Xóa file cũ và lưu thống nhất avatar_<id>.jpg
    for prefix in ("avatar_", "face_"):
        old_p = os.path.join(target_dir, f"{prefix}{entity_id}.jpg")
        if os.path.exists(old_p):
            try:
                os.remove(old_p)
            except Exception as e:
                logger.error(f"Lỗi xóa file cũ {old_p}: {e}")

    file_path = os.path.join(target_dir, f"avatar_{entity_id}.jpg")
    cv2.imwrite(file_path, face_crop, [int(cv2.IMWRITE_JPEG_QUALITY), 92])

    conn = get_db_connection()
    if save_folder == "students":
        conn.execute("UPDATE students SET photo_path = ? WHERE student_id = ?", (file_path, entity_id))
    else:
        conn.execute("UPDATE admin SET photo_path = ? WHERE admin_id = ?", (file_path, entity_id))
    conn.commit()
    conn.close()

    return True, f"✅ Đăng ký khuôn mặt thành công cho sinh viên {full_name} ({entity_id})!", {"photo_path": file_path}


# ==================== API CHO TRANG WEB PHỤ (TEST) & WEB CHÍNH ====================

@app.get("/", response_class=HTMLResponse)
async def serve_home(request: Request):
    return templates.TemplateResponse(request=request, name="index.html")


@app.get("/favicon.ico", include_in_schema=False)
async def favicon():
    return Response(status_code=204)


@app.get("/health")
async def health_check():
    return {
        "status": "ONLINE",
        "database": os.path.exists(DB_FILE),
        "anti_spoof_model_loaded": anti_spoof_helper.is_model_loaded,
        "face_match_model_loaded": face_match_helper.is_model_loaded
    }


@app.get("/api/v1/users")
async def get_users():
    try:
        # Ẩn đường dẫn file nội bộ (local_photo) khỏi response để đảm bảo bảo mật
        safe_users = [
            {k: v for k, v in u.items() if k != "local_photo"}
            for u in fetch_all_users()
        ]
        return {"success": True, "users": safe_users}
    except Exception as e:
        return JSONResponse(status_code=500, content={"success": False, "message": str(e), "users": []})


@app.get("/api/v1/attendance-logs")
async def get_attendance_logs(limit: int = 30):
    conn = get_db_connection()
    rows = conn.execute("SELECT * FROM liveness_logs ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    conn.close()
    return {"success": True, "logs": [dict(r) for r in rows]}


@app.post("/api/v1/log-client-fail")
async def log_client_fail(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        reason: str = Form(...)
):
    users = fetch_all_users()
    target = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == folder), None)
    full_name = target["full_name"] if target else entity_id
    save_liveness_log(entity_id, folder, full_name, "FAILED", reason)
    return {"success": True}


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
    try:
        img_bgr = decode_image(await face_image.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    try:
        face_region = crop_tight_face_1_0x(img_bgr, bbox_x, bbox_y, bbox_w, bbox_h)
        light_ok, light_msg = check_face_lighting(face_region)
        if not light_ok:
            return JSONResponse(status_code=200, content={"success": False, "message": light_msg})

        if detect_and_crop_face_static(img_bgr) is None:
            return JSONResponse(status_code=200, content={
                "success": False,
                "message": "❌ Không tìm thấy khuôn mặt trong ảnh! Vui lòng thử lại."
            })
        success, message, extra = validate_and_save_photo(img_bgr, entity_id, folder, bbox_x, bbox_y, bbox_w, bbox_h)
        return JSONResponse(status_code=200, content={"success": success, "message": message, **extra})
    except Exception as e:
        logger.error(f"Lỗi register_face: {e}", exc_info=True)
        return JSONResponse(status_code=500, content={"success": False, "message": f"❌ Lỗi Server: {str(e)}"})


@app.post("/api/v1/upload-student-photo")
async def upload_student_photo(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        photo_file: UploadFile = File(...)
):
    try:
        img_bgr = decode_image(await photo_file.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    try:
        if detect_and_crop_face_static(img_bgr) is None:
            light_ok, light_msg = check_face_lighting(img_bgr)
            if not light_ok:
                return JSONResponse(status_code=200, content={"success": False, "message": light_msg})
            return JSONResponse(status_code=200, content={
                "success": False,
                "message": "❌ Không tìm thấy khuôn mặt trong ảnh này! Vui lòng chọn ảnh rõ mặt."
            })
        success, message, extra = validate_and_save_photo(img_bgr, entity_id, folder)
        return JSONResponse(status_code=200, content={"success": success, "message": message, **extra})
    except Exception as e:
        logger.error(f"Lỗi upload_student_photo: {e}", exc_info=True)
        return JSONResponse(status_code=500, content={"success": False, "message": f"❌ Lỗi Server: {str(e)}"})


@app.post("/api/v1/verify-liveness")
async def verify_liveness(
        entity_id: str = Form(...),
        folder: str = Form("students"),
        subject_id: Optional[str] = Form(None),
        bbox_x: float = Form(0.25),
        bbox_y: float = Form(0.20),
        bbox_w: float = Form(0.50),
        bbox_h: float = Form(0.60),
        baseline_file: UploadFile = File(...),
        red_flash_file: UploadFile = File(...),
        blue_flash_file: UploadFile = File(...)
):
    users = fetch_all_users()
    target_user = next((u for u in users if u["entity_id"] == entity_id and u["folder"] == folder), None)

    if target_user is None:
        msg = f"Không tìm thấy tài khoản {entity_id} trong hệ thống!"
        save_liveness_log(entity_id, folder, entity_id, "FAILED", msg)
        return JSONResponse(status_code=404, content={"success": False, "is_matched": False, "message": f"❌ {msg}"})

    full_name = target_user["full_name"]
    registered_photo_path = target_user["local_photo"]

    if not registered_photo_path or not os.path.exists(registered_photo_path):
        msg = f"TỪ CHỐI ĐIỂM DANH: {full_name} chưa đăng ký ảnh đại diện mẫu!"
        save_liveness_log(entity_id, folder, full_name, "FAILED", msg)
        return JSONResponse(status_code=200, content={"success": False, "is_matched": False, "requires_registration": True, "message": f"❌ {msg}"})

    try:
        img_base = decode_image(await baseline_file.read())
        img_red = decode_image(await red_flash_file.read())
        img_blue = decode_image(await blue_flash_file.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    # 1. Kiểm tra Flash Red/Blue
    rgb_base = extract_face_core_rgb(img_base, bbox_x, bbox_y, bbox_w, bbox_h)
    rgb_red = extract_face_core_rgb(img_red, bbox_x, bbox_y, bbox_w, bbox_h)
    rgb_blue = extract_face_core_rgb(img_blue, bbox_x, bbox_y, bbox_w, bbox_h)

    flash_passed, shift_score, flash_msg = evaluate_red_blue_ratio_cycle(rgb_base, rgb_red, rgb_blue)
    if not flash_passed:
        save_liveness_log(entity_id, folder, full_name, "FAILED", flash_msg, shift_score=shift_score)
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ {flash_msg}"})

    # 2. Kiểm tra MiniVision Anti-Spoofing (chấm chặt trên khung 2.7x chuẩn MiniVision)
    expanded_crop = crop_expanded_face_2_7x(img_base, bbox_x, bbox_y, bbox_w, bbox_h)
    anti_spoof_res = anti_spoof_helper.classify_face(expanded_crop)

    if not anti_spoof_res.is_real:
        msg = f"Phát hiện video/ảnh giả mạo! (Score: {anti_spoof_res.spoof_score:.2f})"
        save_liveness_log(entity_id, folder, full_name, "FAILED", msg, real_score=anti_spoof_res.real_score, shift_score=shift_score)
        return JSONResponse(status_code=200, content={"success": False, "message": f"❌ {msg}"})

    # 3. So khớp MobileFaceNet 1:1 (1.0x Tight Crop + chuẩn hóa ánh sáng CLAHE)
    ref_img = cv2.imread(registered_photo_path)
    face_current = get_face_crop_for_matching(img_base, bbox_x, bbox_y, bbox_w, bbox_h)
    emb_current = face_match_helper.get_face_embedding(normalize_face_lighting(face_current))
    emb_ref = face_match_helper.get_face_embedding(normalize_face_lighting(ref_img))

    matched, similarity = face_match_helper.compare_faces(emb_ref, emb_current)
    sim_val = float(similarity or 0.0)

    if not matched:
        msg = f"SAI KHUÔN MẶT! Không khớp với {full_name} (Độ giống: {sim_val * 100:.1f}%)"
        save_liveness_log(entity_id, folder, full_name, "FAILED", msg, real_score=anti_spoof_res.real_score, shift_score=shift_score, match_similarity=sim_val)
        return JSONResponse(status_code=200, content={"success": False, "is_matched": False, "message": f"❌ {msg}"})

    success_msg = f"✅ Xác thực thành công cho {full_name} (Độ giống: {sim_val * 100:.1f}%)"
    save_liveness_log(entity_id, folder, full_name, "PASSED", "Hợp lệ", real_score=anti_spoof_res.real_score, shift_score=shift_score, match_similarity=sim_val, subject_id=subject_id)
    return JSONResponse(status_code=200, content={
        "success": True,
        "is_matched": True,
        "message": success_msg,
        "metrics": {
            "real_score": round(anti_spoof_res.real_score, 3),
            "spoof_score": round(anti_spoof_res.spoof_score, 3),
            "shift_score": shift_score,
            "match_similarity": round(sim_val, 4)
        }
    })


# ==================== API ĐỘC LẬP CHO 2 MODEL ====================

@app.post("/api/v1/ai/anti-spoof")
async def api_anti_spoof(
        image: UploadFile = File(...),
        bbox_x: float = Form(0.25),
        bbox_y: float = Form(0.20),
        bbox_w: float = Form(0.50),
        bbox_h: float = Form(0.60)
):
    """Chạy độc lập model minivision_anti_spoof.tflite (tự kiểm tra khuôn mặt và crop 2.7x nếu có bbox)."""
    try:
        img_bgr = decode_image(await image.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    # Kiểm tra có khuôn mặt trong ảnh hay không
    if detect_and_crop_face_static(img_bgr) is None:
        return JSONResponse(status_code=200, content={
            "success": False,
            "has_face": False,
            "message": "❌ Không tìm thấy khuôn mặt trong ảnh!"
        })

    has_bbox = all(v is not None for v in (bbox_x, bbox_y, bbox_w, bbox_h))
    target = crop_expanded_face_2_7x(img_bgr, bbox_x, bbox_y, bbox_w, bbox_h) if has_bbox else img_bgr
    res = anti_spoof_helper.classify_face(target)
    return {"success": True, "has_face": True, "result": res.to_dict()}


@app.post("/api/v1/ai/face-match")
async def api_face_match(image1: UploadFile = File(...), image2: UploadFile = File(...)):
    """Chạy độc lập model mobile_face_net.tflite để so sánh 2 ảnh khuôn mặt."""
    try:
        img1 = decode_image(await image1.read())
        img2 = decode_image(await image2.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    c1 = get_face_crop_for_matching(img1)
    c2 = get_face_crop_for_matching(img2)
    emb1 = face_match_helper.get_face_embedding(normalize_face_lighting(c1))
    emb2 = face_match_helper.get_face_embedding(normalize_face_lighting(c2))
    matched, similarity = face_match_helper.compare_faces(emb1, emb2)
    return {
        "success": True,
        "is_matched": matched,
        "similarity": round(float(similarity or 0.0), 4),
        "threshold": face_match_helper.MATCH_THRESHOLD
    }


@app.post("/api/v1/ai/check-duplicate")
async def api_check_duplicate(
        image: UploadFile = File(...),
        current_entity_id: str = Form(""),
        folder: str = Form("students")
):
    """Kiểm tra ảnh khuôn mặt có bị trùng với tài khoản khác trong hệ thống không."""
    try:
        img_bgr = decode_image(await image.read())
    except ValueError as ve:
        return JSONResponse(status_code=400, content={"success": False, "message": f"❌ {str(ve)}"})

    # Đồng bộ margin 20% với lúc upload/đăng ký để tránh lệch embedding
    cropped = get_face_crop_for_matching(img_bgr)
    dup = check_if_face_already_exists(cropped, current_entity_id, folder)
    if dup:
        return {"success": True, "is_duplicate": True, "duplicate_id": dup[0], "similarity": round(dup[1], 4)}
    return {"success": True, "is_duplicate": False}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
import os
import glob
import zipfile
import cv2
import numpy as np
import onnxruntime as ort

# ==============================================================================
# SCRIPT ĐÁNH GIÁ MÔ HÌNH MINIVISION ANTI-SPOOFING (DATASET ARCHIVE)
# ==============================================================================

MODEL_PATH = "minivision.onnx"
ZIP_PATH = "dataset_anti_spoof/archive.zip"
EXTRACT_DIR = "dataset_anti_spoof/extracted"

def extract_dataset():
    """Tự động giải nén archive.zip nếu chưa giải nén"""
    if not os.path.exists(EXTRACT_DIR) and os.path.exists(ZIP_PATH):
        print("📦 Đang giải nén dataset archive.zip...")
        with zipfile.ZipFile(ZIP_PATH, 'r') as z:
            z.extractall(EXTRACT_DIR)
        print("✅ Giải nén thành công!")

def load_face_detector():
    """Khởi tạo bộ phát hiện khuôn mặt OpenCV Haar Cascade"""
    cascade_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
    return cv2.CascadeClassifier(cascade_path)

def crop_face(img, face_cascade, scale=2.7):
    """Cắt và mở rộng vùng mặt (Expanded Crop) chuẩn MiniVision"""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    faces = face_cascade.detectMultiScale(gray, 1.1, 4)
    h, w = img.shape[:2]

    if len(faces) == 0:
        size = min(h, w)
        x, y = (w - size) // 2, (h - size) // 2
        return img[y:y+size, x:x+size]

    x, y, fw, fh = faces[0]
    cx, cy = x + fw // 2, y + fh // 2
    max_dim = int(max(fw, fh) * scale / 2.0)

    ex = max(0, cx - max_dim)
    ey = max(0, cy - max_dim)
    ew = min(w - ex, max_dim * 2)
    eh = min(h - ey, max_dim * 2)
    return img[ey:ey+eh, ex:ex+ew]

def predict_real_score(img_path, session, input_name, output_name, face_cascade):
    """Dự đoán xác suất người thật (Real Score) từ ảnh"""
    img = cv2.imread(img_path)
    if img is None:
        return None

    cropped = crop_face(img, face_cascade)
    rgb = cv2.cvtColor(cropped, cv2.COLOR_BGR2RGB)
    resized = cv2.resize(rgb, (80, 80))

    # NCHW RGB [1, 3, 80, 80]
    nchw = np.transpose(resized.astype(np.float32), (2, 0, 1))
    input_data = np.expand_dims(nchw, axis=0)

    outputs = session.run([output_name], {input_name: input_data})[0][0]

    exp_scores = np.exp(outputs - np.max(outputs))
    probs = exp_scores / np.sum(exp_scores)

    # Index 1 = REAL PERSON (Người thật)
    return float(probs[1])

def main():
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Không tìm thấy model tại: {MODEL_PATH}")
        return

    extract_dataset()

    session = ort.InferenceSession(MODEL_PATH)
    input_name = session.get_inputs()[0].name
    output_name = session.get_outputs()[0].name
    face_cascade = load_face_detector()

    # Tìm đường dẫn ClientRaw (Real) và ImposterRaw (Spoof)
    client_dir = os.path.join(EXTRACT_DIR, "raw", "ClientRaw")
    imposter_dir = os.path.join(EXTRACT_DIR, "raw", "ImposterRaw")

    if not os.path.exists(client_dir) or not os.path.exists(imposter_dir):
        # Thử tìm trực tiếp trong thư mục dataset_anti_spoof nếu đã giải nén
        client_dir = os.path.join("dataset_anti_spoof", "ClientRaw")
        imposter_dir = os.path.join("dataset_anti_spoof", "ImposterRaw")

    if not os.path.exists(client_dir) or not os.path.exists(imposter_dir):
        print("❌ Không tìm thấy thư mục ClientRaw và ImposterRaw trong archive.")
        return

    # Lấy mẫu test (giới hạn số lượng mẫu để chạy nhanh, ví dụ 100 ảnh mỗi loại nếu dataset lớn)
    real_paths = glob.glob(os.path.join(client_dir, "**", "*.jpg"), recursive=True)[:100]
    spoof_paths = glob.glob(os.path.join(imposter_dir, "**", "*.jpg"), recursive=True)[:100]

    print(f"📁 Dữ liệu test: {len(real_paths)} ảnh Real (ClientRaw) | {len(spoof_paths)} ảnh Spoof (ImposterRaw)")
    print("⏳ Đang chạy suy luận (Inference)...")

    reals = [predict_real_score(p, session, input_name, output_name, face_cascade) for p in real_paths]
    spoofs = [predict_real_score(p, session, input_name, output_name, face_cascade) for p in spoof_paths]

    reals = [s for s in reals if s is not None]
    spoofs = [s for s in spoofs if s is not None]

    if not reals or not spoofs:
        print("⚠️ Không đọc được ảnh từ dataset.")
        return

    # Thống kê điểm số
    print("\n" + "="*50)
    print("📊 THỐNG KÊ ĐIỂM SỐ (REAL SCORE)")
    print("="*50)
    print(f"• Người thật (Real) : Min={min(reals):.3f} | Max={max(reals):.3f} | Mean={np.mean(reals):.3f}")
    print(f"• Giả mạo (Spoof)   : Min={min(spoofs):.3f} | Max={max(spoofs):.3f} | Mean={np.mean(spoofs):.3f}")
    print("="*50)

    # Quét Threshold tìm điểm tối ưu
    print("\n" + "="*65)
    print("📈 BẢNG ĐÁNH GIÁ CHỈ SỐ THEO NGƯỠNG (THRESHOLD SWEEP)")
    print("="*65)
    print(f"{'Threshold':<10} | {'FAR (APCER)':<12} | {'FRR (BPCER)':<12} | {'Accuracy':<10}")
    print("-" * 65)

    best_th, best_acc = 0.5, 0.0

    for th in np.arange(0.10, 1.00, 0.05):
        fa = sum(1 for s in spoofs if s >= th)
        fr = sum(1 for s in reals if s < th)
        ta = sum(1 for s in reals if s >= th)
        tr = sum(1 for s in spoofs if s < th)

        far = (fa / len(spoofs)) * 100.0
        frr = (fr / len(reals)) * 100.0
        acc = ((ta + tr) / (len(reals) + len(spoofs))) * 100.0

        print(f"{th:<10.2f} | {far:<11.2f}% | {frr:<11.2f}% | {acc:<9.2f}%")

        if acc > best_acc:
            best_acc = acc
            best_th = th

    print("-" * 65)
    print(f"🏆 Ngưỡng tối ưu mang lại Accuracy cao nhất ({best_acc:.2f}%): THRESHOLD = {best_th:.2f}")
    print("="*65 + "\n")

if __name__ == "__main__":
    main()

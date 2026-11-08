import os
import cv2
import numpy as np
import torch
import torch.nn.functional as F
from fastapi import FastAPI, UploadFile, File, HTTPException, Query
from models.MiniFASNet import MiniFASNetV2

app = FastAPI(
    title="Tổ 3 - MiniVision Anti-Spoofing REST API",
    description="Dịch vụ API nhận diện Liveness / Anti-Spoofing phục vụ nhóm Client Android và Web",
    version="1.0.0"
)

# 1. Khởi tạo và nạp mô hình PyTorch MiniFASNetV2
DEVICE = torch.device("cpu")
MODEL_PATH = os.path.join("models", "2.7_80x80_MiniFASNetV2.pth")

model = MiniFASNetV2(conv6_kernel=(5, 5)).to(DEVICE)
state_dict = torch.load(MODEL_PATH, map_location=DEVICE)
state_dict = {
    key.removeprefix("module."): value
    for key, value in state_dict.items()
}
model.load_state_dict(state_dict)
model.eval()

# Ngưỡng mặc định chọn trên NUAA dataset (từ results/threshold.json)
DEFAULT_THRESHOLD = 0.108

def preprocess_image(image_bgr: np.ndarray) -> torch.Tensor:
    # Resize 80x80
    resized = cv2.resize(image_bgr, (80, 80))
    # NCHW format & Convert to Tensor [1, 3, 80, 80]
    tensor = torch.from_numpy(resized).permute(2, 0, 1).unsqueeze(0).float()
    return tensor.to(DEVICE)

@app.get("/", summary="Kiểm tra trạng thái Server")
def root():
    return {
        "status": "online",
        "service": "MiniVision Anti-Spoofing API",
        "model_loaded": "2.7_80x80_MiniFASNetV2.pth"
    }

@app.post("/api/v1/predict-liveness", summary="Xác thực người thật vs ảnh/video giả mạo")
async def predict_liveness(
    file: UploadFile = File(...),
    threshold: float = Query(DEFAULT_THRESHOLD, description="Ngưỡng phân loại Real/Fake (Mặc định 0.108)")
):
    if not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="File tải lên phải là hình ảnh (JPEG/PNG)")

    try:
        # Đọc dữ liệu ảnh từ Client
        contents = await file.read()
        nparr = np.frombuffer(contents, np.uint8)
        image_bgr = cv2.imdecode(nparr, cv2.IMREAD_COLOR)

        if image_bgr is None:
            raise HTTPException(status_code=400, detail="Không thể giải mã file ảnh")

        # Tiền xử lý & Chạy mô hình PyTorch MiniVision
        input_tensor = preprocess_image(image_bgr)
        with torch.no_grad():
            outputs = model(input_tensor)
            probs = F.softmax(outputs, dim=1).cpu().numpy()[0]

        # Index 1 = REAL, Index 0 & 2 = FAKE
        real_score = float(probs[1])
        spoof_score = float(max(probs[0], probs[2]))
        is_real = real_score >= threshold

        return {
            "status": "success",
            "result": "REAL" if is_real else "FAKE",
            "is_real": is_real,
            "real_score": round(real_score, 4),
            "spoof_score": round(spoof_score, 4),
            "threshold_used": threshold,
            "message": "Xác thực người thật thành công" if is_real else "Cảnh báo: Phát hiện ảnh/video giả mạo!"
        }

    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Lỗi xử lý hệ thống: {str(e)}")

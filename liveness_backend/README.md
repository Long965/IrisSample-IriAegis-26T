# 🌐 WEB LIVENESS DETECTION BACKEND (PYTHON FASTAPI + MEDIAPIPE)

Hệ thống Xác thực Liveness Detection & Anti-Spoofing chạy trên nền tảng **Web Browser (MediaPipe WASM)** và **Python FastAPI Backend**.

---

### 🚀 1. Hướng dẫn Cài đặt & Khởi chạy Server Python

#### Bước 1: Mở Terminal tại thư mục `liveness_backend`
```bash
cd D:/liveness_backend
```

#### Bước 2: Cài đặt các thư viện Python cần thiết
```bash
pip install -r requirements.txt
```

#### Bước 3: Khởi chạy FastAPI Server bằng Uvicorn
```bash
python main.py
```
*(Hoặc chạy lệnh uvicorn trực tiếp: `uvicorn main:app --reload --host 0.0.0.0 --port 8000`)*

---

### 💻 2. Hướng dẫn Truy cập & Thử nghiệm trên Web

1. Mở trình duyệt Web (Chrome / Edge / Firefox) và truy cập đường dẫn:
   **`http://localhost:8000`** hoặc **`http://127.0.0.1:8000`**

2. Cho phép trình duyệt truy cập Webcam.

3. Nhấn nút **"Bắt đầu Thử nghiệm Liveness"** và thực hiện theo chỉ dẫn trên màn hình:
   - **Bước 1**: Đưa khuôn mặt nhìn thẳng camera.
   - **Bước 2**: Chớp mắt tự nhiên (Hệ thống tự động tính tỷ lệ EAR).
   - **Bước 3**: Lắc đầu sang trái và sang phải.
   - **Bước 4**: Giữ thẳng mặt - Màn hình tự động nháy màu Đỏ & Xanh để chụp phản xạ ánh sáng.

4. Server FastAPI sẽ nhận 3 bức ảnh và trả về kết quả phân tích JSON thời gian thực (Red/Blue Spectrum Shift, Status: PASS/FAIL).

---

### 📂 3. Cấu trúc Dự án Backend:
```
liveness_backend/
├── main.py                   # Server FastAPI xử lý API /api/v1/verify-liveness
├── requirements.txt           # Danh sách thư viện Python (fastapi, uvicorn, opencv-python, numpy)
├── static/
│   ├── css/style.css         # Giao diện Glassmorphic UI & Animation
│   └── js/liveness.js        # MediaPipe Client-side Liveness State Machine
├── templates/
│   └── index.html            # Trang Web HTML5 hiển thị Webcam & Chỉ dẫn
└── README.md                 # Hướng dẫn sử dụng
```

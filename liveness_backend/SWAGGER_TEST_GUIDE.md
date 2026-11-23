# 📖 HƯỚNG DẪN TEST CÁC API VÀ ĐỐI SOÁT KẾT QUẢ TRÊN SWAGGER UI

Tài liệu này hướng dẫn chi tiết cách kiểm tra và chạy thử toàn bộ **Các API Production (Khớp 1:1 mã nguồn Java Android)** trực tiếp trên giao diện **Swagger UI**.

---

## 🚀 1. Khởi chạy Server & Mở Swagger UI

1. Mở **Terminal / Command Prompt** tại thư mục `liveness_backend`:
   ```bash
   cd D:/HKDN/HKDN/IrisSample-New/liveness_backend
   python main.py
   ```
2. Mở trình duyệt Web (Chrome / Edge) và truy cập vào đường dẫn:
   👉 **[http://localhost:8000/docs](http://localhost:8000/docs)** *(hoặc `http://127.0.0.1:8000/docs`)*

---

## 📋 2. Hướng dẫn Chi tiết Test từng API

---

### 🔹 API 1: `GET /health` (Kiểm tra Trạng thái Server & AI Models)
- **Mục đích**: Kiểm tra Server FastAPI, CSDL SQLite (`IrisDatabase.db`) và 2 Mô hình AI TFLite (`minivision_anti_spoof.tflite` & `mobile_face_net.tflite`) đã sẵn sàng hoạt động hay chưa.
- **Các bước thực hiện**:
  1. Nhấp vào dòng màu xanh dương **`GET /health`**.
  2. Nhấp nút **Try it out** ở góc phải $\rightarrow$ Nhấn **Execute**.
- **Kết quả trả về kỳ vọng (HTTP 200)**:
  ```json
  {
    "status": "ONLINE",
    "database": true,
    "anti_spoof_model_loaded": true,
    "face_match_model_loaded": true
  }
  ```

---

### 🔹 API 2: `GET /api/v1/users` (Lấy Danh sách Sinh viên & Giảng viên)
- **Mục đích**: Truy vấn danh sách toàn bộ đối tượng từ 2 bảng `students` và `admin` trong CSDL `IrisDatabase.db`.
- **Các bước thực hiện**:
  1. Nhấp vào dòng **`GET /api/v1/users`**.
  2. Nhấp nút **Try it out** $\rightarrow$ Nhấn **Execute**.
- **Kết quả trả về kỳ vọng (HTTP 200)**:
  ```json
  {
    "success": true,
    "users": [
      {
        "entity_id": "SV999",
        "folder": "students",
        "full_name": "Lt Long",
        "has_photo": true,
        "local_photo": "D:\\...\\uploads\\students\\"
      },
      {
        "entity_id": "SV001",
        "folder": "students",
        "full_name": "Nguyễn Văn A",
        "has_photo": false,
        "local_photo": null
      }
    ]
  }
  ```

---

### 🔹 API 3: `POST /api/v1/upload-student-photo` (Tải Ảnh đại diện từ File máy tính - 1:1 AddStudentActivity)
- **Mục đích**: Đăng ký/Tải lên ảnh đại diện cho Sinh viên hoặc Admin từ file ảnh JPG/PNG nguyên bản bất kỳ trên máy tính. Server sẽ **tự động cắt mặt margin 35% tỉ lệ vàng** và kiểm tra bảo mật 2 bước:
  1. **Bước 1 (isOwner)**: Nếu đối tượng đã có avatar cũ $\rightarrow$ Kiểm tra ảnh mới có đúng chính chủ hay không (`compare_faces > 0.75`).
  2. **Bước 2 (checkIfFaceAlreadyExists)**: Kiểm tra ảnh mới có bị trùng mặt với sinh viên khác cùng role hay không (`compare_faces > 0.75`).
- **Các bước thực hiện**:
  1. Nhấp vào **`POST /api/v1/upload-student-photo`** $\rightarrow$ Nhấp **Try it out**.
  2. Điền thông tin:
     - `entity_id`: `SV999`
     - `folder`: `students` *(hoặc `admins`)*
     - `photo_file`: Chọn file ảnh bất kỳ từ máy tính.
  3. Nhấn **Execute**.
- **Các kịch bản kết quả**:
  - 🟢 **Thành công**: `{"success": true, "message": "✅ Đăng ký khuôn mặt thành công cho sinh viên Lt Long (SV999)!"}`
  - 🔴 **Gian lận (Trùng khuôn mặt với tài khoản khác)**: `{"success": false, "message": "❌ Gian lận: Khuôn mặt này đã được dùng bởi sinh viên mã: SV001"}`
  - 🔴 **Sai chính chủ (Người khác cố tình ghi đè tài khoản)**: `{"success": false, "message": "❌ Khuôn mặt không khớp với avatar hiện tại của sinh viên này! (Độ khớp: 20.8%)"}`

---

### 🔹 API 4: `POST /api/v1/register-face` (Chụp Đăng ký qua Camera Web - MODE_REGISTER)
- **Mục đích**: Đăng ký ảnh đại diện qua camera webcam thời gian thực.
- **Tham số**: `entity_id`, `folder`, `bbox_x`, `bbox_y`, `bbox_w`, `bbox_h`, `face_image` (File Binary).
- **Cách test**: Chọn file ảnh binary chụp từ camera $\rightarrow$ Nhấn **Execute**.

---

### 🔹 API 5: `POST /api/v1/verify-liveness` (Xác thực Liveness & Điểm danh 1:1 - MODE_CHECKIN)
- **Mục đích**: Điểm danh khuôn mặt 3 Tầng an toàn khớp 1:1 với `FaceCaptureActivity.java`.
- **Các bước thực hiện**:
  1. Nhấp vào **`POST /api/v1/verify-liveness`** $\rightarrow$ Nhấp **Try it out**.
  2. Điền thông tin:
     - `entity_id`: `SV999`
     - `folder`: `students`
     - `bbox_x`: `0.25`, `bbox_y`: `0.20`, `bbox_w`: `0.50`, `bbox_h`: `0.60`
     - `baseline_file`: Chọn ảnh môi trường chuẩn (`base.jpg`).
     - `red_flash_file`: Chọn ảnh dưới đèn Đỏ (`red.jpg`).
     - `blue_flash_file`: Chọn ảnh dưới đèn Xanh (`blue.jpg`).
  3. Nhấn **Execute**.
- **Các kịch bản kết quả**:
  - 🔴 **Chưa đăng ký ảnh mẫu**: `{"success": false, "is_matched": false, "message": "❌ TỪ CHỐI ĐIỂM DANH: Sinh viên Lt Long chưa đăng ký ảnh đại diện mẫu..."}`
  - 🔴 **Không đạt phản xạ Flash**: `{"success": false, "message": "❌ Phát hiện video giả mạo! (Không đạt phản xạ ánh sáng Flash)"}`
  - 🔴 **Fake / Video giả mạo (MiniVision AI)**: `{"success": false, "message": "❌ Phát hiện video/ảnh giả mạo! (Score: 0.98)"}`
  - 🔴 **Sai khuôn mặt (MobileFaceNet AI)**: `{"success": false, "is_matched": false, "message": "❌ SAI KHUÔN MẶT! Khuôn mặt đứng trước camera không trùng khớp..."}`
  - 🟢 **Điểm danh Thành công**:
    ```json
    {
      "success": true,
      "is_matched": true,
      "message": "✅ Liveness & So khớp khuôn mặt THÀNH CÔNG! Đã xác nhận điểm danh cho Lt Long (Độ giống: 93.4%)",
      "metrics": {
        "real_score": 0.985,
        "shift_score": 0.0452,
        "match_similarity": 0.9344
      }
    }
    ```

---

## 🛠️ 3. Tài liệu Kỹ thuật ReDoc UI

Ngoài Swagger UI (`/docs`), bạn có thể xem sơ đồ tài liệu ReDoc UI chuẩn quốc tế tại:
👉 **[http://localhost:8000/redoc](http://localhost:8000/redoc)**

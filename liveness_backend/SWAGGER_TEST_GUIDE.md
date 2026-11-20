# 📖 HƯỚNG DẪN CHI TIẾT TEST CÁC API LIVENESS TRÊN SWAGGER UI

Tài liệu này hướng dẫn chi tiết cách kiểm tra và chạy thử toàn bộ **8 API xác thực Liveness Detection, Anti-Spoofing & So khớp khuôn mặt** trực tiếp trên giao diện **Swagger UI**.

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
- **Mục đích**: Kiểm tra Server FastAPI, CSDL SQLite (`IrisDatabase.db`) và 2 Model AI TFLite (`minivision_anti_spoof.tflite` & `mobile_face_net.tflite`) đã sẵn sàng hoạt động hay chưa.
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
- **Mục đích**: Truy vấn danh sách toàn bộ đối tượng từ các bảng `students` và `admins` trong CSDL `IrisDatabase.db`.
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
        "local_photo": "D:\\...\\uploads\\students\\avatar_SV999.jpg"
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

### 🔹 API 3: `POST /api/v1/add-user` (Thêm Sinh viên / Admin mới)
- **Mục đích**: Thêm đối tượng mới trực tiếp vào CSDL SQLite.
- **Các bước thực hiện**:
  1. Nhấp vào dòng màu xanh lá **`POST /api/v1/add-user`** $\rightarrow$ Nhấp **Try it out**.
  2. Điền các thông số:
     - `entity_id`: `SV012`
     - `full_name`: `Trần Văn Hùng`
     - `folder`: `students` *(hoặc `admins`)*
     - `sub_info`: `SĐT: 0912345678 | Email: hung.tran@school.edu.vn`
  3. Nhấn **Execute**.
- **Kết quả trả về kỳ vọng**:
  ```json
  {
    "success": true,
    "message": "✅ Đã thêm đối tượng Trần Văn Hùng (SV012) vào CSDL!"
  }
  ```

---

### 🔹 API 4: `POST /api/v1/upload-student-photo` (Tải Ảnh đại diện mẫu từ Máy tính)
- **Mục đích**: Đăng ký ảnh đại diện cho Sinh viên/Admin từ file ảnh JPG/PNG trên máy tính (Khớp 1:1 với `AddStudentActivity.java` trên Android).
- **Các bước thực hiện**:
  1. Nhấp vào **`POST /api/v1/upload-student-photo`** $\rightarrow$ Nhấp **Try it out**.
  2. Điền thông tin:
     - `entity_id`: `SV012`
     - `folder`: `students`
     - `photo_file`: Nhấn nút **Choose File** chọn 1 bức ảnh khuôn mặt từ máy tính.
  3. Nhấn **Execute**.
- **Các kịch bản kết quả**:
  - 🟢 **Thành công**: `{"success": true, "message": "✅ Đăng ký khuôn mặt thành công cho sinh viên Trần Văn Hùng (SV012)!"}`
  - 🔴 **Gian lận (Khuôn mặt đã thuộc về sinh viên khác)**: `{"success": false, "message": "❌ Gian lận: Khuôn mặt này đã được sử dụng bởi sinh viên mã: SV999 (Lt Long) - Khớp 100.0%!"}`
  - 🔴 **Không đúng chính chủ (Ảnh mới không khớp với avatar cũ)**: `{"success": false, "message": "❌ Khuôn mặt không khớp với avatar hiện tại của sinh viên này!"}`

---

### 🔹 API 5: `POST /api/v1/register-face` (Chụp Đăng ký qua Webcam)
- **Mục đích**: Đăng ký ảnh khuôn mặt từ webcam client (MODE_REGISTER).
- **Tham số**: `entity_id`, `folder`, `bbox_x`, `bbox_y`, `bbox_w`, `bbox_h`, `face_image` (File Binary).
- **Cách test**: Tương tự API 4, chọn `face_image` tải lên từ đĩa.

---

### 🔹 API 6: `POST /api/v1/verify-liveness` (Xác thực Liveness & Điểm danh 1:1)
- **Mục đích**: Điểm danh khuôn mặt 3 Tầng an toàn (MODE_CHECKIN).
- **Các bước thực hiện**:
  1. Nhấp vào **`POST /api/v1/verify-liveness`** $\rightarrow$ Nhấp **Try it out**.
  2. Điền thông tin:
     - `entity_id`: `SV999`
     - `folder`: `students`
     - `bbox_x`: `0.25`, `bbox_y`: `0.20`, `bbox_w`: `0.50`, `bbox_h`: `0.60`
     - `baseline_file`: Chọn file ảnh chụp tự nhiên (`baseline.jpg`).
     - `red_flash_file`: Chọn file ảnh chụp dưới ánh sáng Đỏ (`red_flash.jpg`).
     - `blue_flash_file`: Chọn file ảnh chụp dưới ánh sáng Xanh (`blue_flash.jpg`).
  3. Nhấn **Execute**.
- **Các kịch bản kết quả**:
  - 🔴 **Chưa đăng ký ảnh mẫu**:
    ```json
    {
      "success": false,
      "is_matched": false,
      "requires_registration": true,
      "message": "❌ TỪ CHỐI ĐIỂM DANH: Sinh viên Lt Long chưa đăng ký ảnh đại diện mẫu trong CSDL! Vui lòng nhấn 'Chụp Đăng ký' trước."
    }
    ```
  - 🔴 **Fake / Video giả mạo**:
    ```json
    {
      "success": false,
      "message": "❌ Phát hiện video/ảnh giả mạo! (Score: 0.98)"
    }
    ```
  - 🔴 **Sai khuôn mặt (Người khác đứng thế chỗ)**:
    ```json
    {
      "success": false,
      "is_matched": false,
      "message": "❌ SAI KHUÔN MẶT! Khuôn mặt đứng trước camera không trùng khớp với ảnh đại diện của Lt Long (Độ giống: 21.5%)"
    }
    ```
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

### 🔹 API 7: `POST /api/v1/log-client-fail` (Ghi nhận Nhật ký Lỗi Client)
- **Mục đích**: Đẩy thông báo lỗi từ Client (Freeze frame, Splice cut, Timeout, 3D Parallax fail) lưu vào CSDL.
- **Tham số**: `entity_id`, `folder`, `reason`.

---

### 🔹 API 8: `GET /api/v1/attendance-logs` (Lấy Lịch sử Nhật ký Điểm danh)
- **Mục đích**: Truy vấn 25 bản ghi nhật ký điểm danh mới nhất từ bảng `web_liveness_logs` trong CSDL `IrisDatabase.db`.
- **Các bước thực hiện**: Nhấp `GET /api/v1/attendance-logs` $\rightarrow$ **Try it out** $\rightarrow$ **Execute**.

---

## 🛠️ 3. Mẹo xem Tài liệu Kỹ thuật Đẹp mắt với ReDoc UI

Ngoài Swagger UI (`/docs`), FastAPI còn hỗ trợ trang xem tài liệu kỹ thuật chuẩn định dạng **ReDoc UI**:
👉 **[http://localhost:8000/redoc](http://localhost:8000/redoc)**

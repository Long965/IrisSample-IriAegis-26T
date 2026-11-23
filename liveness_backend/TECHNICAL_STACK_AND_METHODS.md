# 🛠️ CÔNG NGHỆ VÀ PHƯƠNG THỨC XỬ LÝ (TECHNICAL STACK & METHODS)

Tài liệu này mô tả chi tiết toàn bộ **Công nghệ, Kiến trúc Hệ thống, Mô hình AI và Thuật toán Kỹ thuật** được triển khai đồng bộ **1:1 giữa Android (Java) và Web Backend (Python FastAPI)** cho hệ thống **Liveness Detection & So khớp khuôn mặt 1:1**.

---

## 🏗️ 1. Kiến trúc & Công nghệ Sử dụng (Tech Stack)

### 🔹 Web Frontend & Client-side Liveness
- **HTML5 & Glassmorphic CSS3**: Giao diện người dùng thời gian thực, tự động nháy màu viền chớp sáng Screen Flash.
- **MediaPipe FaceMesh WASM**: Trích xuất 468 điểm mốc khuôn mặt 3D (Landmarks) thời gian thực ngay trên trình duyệt web client mà không tốn tài nguyên server.
- **Canvas HTML5 API**: Xử lý capture khung hình webcam và lật gương ngang ($1:1$ với camera selfie di động).

### 🔹 Mobile Android (Java App)
- **Java Native**: Quản lý vòng đời camera (`CameraX` / `Camera2`), ép độ sáng màn hình lên 100% (`BRIGHTNESS_OVERRIDE_FULL` & `FLAG_KEEP_SCREEN_ON`).
- **ML Kit Face Detection**: Tìm vị trí khung khuôn mặt và căn lề tự động trên thiết bị di động.
- **TFLite Android Runtime**: Nạp trực tiếp 2 mô hình `.tflite` chạy suy luận AI phần cứng trên điện thoại.

### 🔹 Web Backend Server (Python FastAPI)
- **Python 3.10+ & FastAPI**: Framework RESTful API hiệu năng cao với bất đồng bộ `async/await`.
- **Uvicorn ASGI Server**: Chạy server ứng dụng Web trên cổng `8000`.
- **OpenCV Python (`cv2`)**: Giải mã ảnh BGR, cắt khuôn mặt `Haar Cascade` có margin padding 35%, biến đổi không gian màu RGB/BGR.
- **TensorFlow Lite Runtime / AI Edge LiteRT**: Chạy mô hình suy luận TFLite trên CPU Server.
- **SQLite Database (`IrisDatabase.db`)**: CSDL lưu trữ danh sách Sinh viên, Giảng viên Admin và đường dẫn ảnh đại diện.
- **CORSMiddleware**: Cấu hình `allow_origins=["*"]` cho phép các ứng dụng Web chính (React, Vue, Angular, Django, PHP) gọi API trực tiếp.

---

## 🧠 2. Các Mô hình AI & Thuật toán Kỹ thuật Core

### 1️⃣ Mô hình Passive Anti-Spoofing: MiniFASNetV2 (`minivision_anti_spoof.tflite`)
Phân biệt người thật với ảnh chụp màn hình, ảnh in giấy hoặc video giả mạo (Replay Attack).

- **Đầu vào (Input)**: Ảnh vùng mặt cắt rộng $2.7\times$ (`expanded_crop`), định dạng kênh **NCHW BGR**, dải giá trị float không chuẩn hóa `[0.0, 255.0]`, kích thước $80 \times 80 \times 3$.
- **Đầu ra (Output)**: Logits của 3 lớp phân loại:
  - `0`: Ảnh in / Giấy (Paper/Photo)
  - `1`: Người thật (Real Person)
  - `2`: Màn hình / Video (Screen/Replay)
- **Thuật toán Softmax**:
  $$P(y_i) = \frac{e^{z_i - \max(Z)}}{\sum_{j=0}^{2} e^{z_j - \max(Z)}}$$
- **Mapping Class & Ngưỡng (Khớp 1:1 Java)**:
  - So sánh giữa `Candidate 0` và `Candidate 1`, chọn giá trị xác suất lớn nhất làm `realScore`.
  - **Ngưỡng quyết định**: `REAL_THRESHOLD = 0.35f` ($35\%$). Nút thắt an toàn: Thử linh hoạt đa vùng cắt ($2.7\times \rightarrow 1.0\times \rightarrow$ ảnh gốc) để đảm bảo người thật đứng trước webcam luôn đi qua $100\%$ mượt mà.

---

### 2️⃣ Mô hình So khớp khuôn mặt: MobileFaceNet (`mobile_face_net.tflite`)
Trích xuất Vector đặc trưng 192 chiều đại diện cho danh tính khuôn mặt để đối soát 1:1.

- **Đầu vào (Input)**: Ảnh khuôn mặt cắt lề 35% (`face_crop`), định dạng kênh **RGB**, chuẩn hóa float về dải $[-1.0, 1.0]$:
  $$\text{Pixel}_{\text{norm}} = \frac{\text{RGB} - 127.5}{128.0}$$
  Kích thước input: $112 \times 112 \times 3$.
- **Đầu ra (Output)**: Vector đặc trưng 192 chiều $\mathbf{e} \in \mathbb{R}^{192}$.
- **Chuẩn hóa L2 Norm Vector**:
  $$\mathbf{\hat{e}} = \frac{\mathbf{e}}{\|\mathbf{e}\|_2}$$
- **Độ tương đồng Cosine Similarity (Cosine Distance)**:
  $$S(\mathbf{e}_1, \mathbf{e}_2) = \frac{\mathbf{e}_1 \cdot \mathbf{e}_2}{\|\mathbf{e}_1\|_2 \|\mathbf{e}_2\|_2} = \sum_{i=1}^{192} \hat{e}_{1,i} \cdot \hat{e}_{2,i}$$
- **Ngưỡng khớp danh tính (Khớp 1:1 Java)**:
  - **Duy nhất 1 ngưỡng cố định**: `MATCH_THRESHOLD = 0.75f` ($75\%$).
  - Nếu $S > 0.75 \rightarrow$ Xác nhận cùng 1 người (`compare_faces = true`).
  - Nếu $S \le 0.75 \rightarrow$ Từ chối sai khuôn mặt (`compare_faces = false`).

---

### 3️⃣ Phân tích phổ màu phản xạ chớp sáng Screen Flash (`evaluate_red_blue_ratio_cycle`)
Chống gian lận bằng cách nháy chớp màu Đỏ (#FF0000) và Xanh dương (#0000FF) trên màn hình thiết bị để kiểm tra sự biến đổi phản xạ ánh sáng trên da người thật.

- **Khu vực trích xuất core RGB**: Vùng lõi khuôn mặt ($35\%$ - $65\%$ chiều rộng và chiều cao).
- **Tỷ lệ dịch chuyển phổ màu Da người ($\Delta_{\text{shift}}$)**:
  $$\text{Ratio}_{\text{Red}} = \frac{R_{\text{red}}}{B_{\text{red}} + 1.0}, \quad \text{Ratio}_{\text{Blue}} = \frac{R_{\text{blue}}}{B_{\text{blue}} + 1.0}$$
  $$\Delta_{\text{shift}} = \text{Ratio}_{\text{Red}} - \text{Ratio}_{\text{Blue}}$$
- **Độ chuẩn hóa kênh Đỏ/Xanh ($Norm_R, Norm_B$)**:
  $$\text{Norm}_R = \frac{R_{\text{red}}}{\text{Sum}_{\text{red}}} - \frac{R_{\text{blue}}}{\text{Sum}_{\text{blue}}}, \quad \text{Norm}_B = \frac{B_{\text{blue}}}{\text{Sum}_{\text{blue}}} - \frac{B_{\text{red}}}{\text{Sum}_{\text{red}}}$$
- **Điều kiện Hợp lệ**:
  $$\Delta_{\text{shift}} \ge 0.008 \quad \land \quad \text{Norm}_R \ge 0.0010 \quad \land \quad \text{Norm}_B \ge 0.0010$$

---

### 4️⃣ Liveness chủ động Client-side (`LivenessLogic.java` / `liveness.js`)
- **Tỷ lệ chớp mắt EAR (Eye Aspect Ratio)**:
  $$\text{EAR} = \frac{\|p_2 - p_6\| + \|p_3 - p_5\|}{2 \|p_1 - p_4\|}$$
  - $\text{EAR} \le 0.16 \rightarrow$ Nhắm mắt (`EYE_CLOSED`).
  - $\text{EAR} \ge 0.22 \rightarrow$ Mở mắt (`EYE_OPEN`).
- **Góc quay 3D Euler Angles**:
  - Yaw $\le -11.5^\circ \rightarrow$ Lắc đầu sang trái.
  - Yaw $\ge +11.5^\circ \rightarrow$ Lắc đầu sang phải.
- **Biến thiên thị giác 3D Nose-to-Eye Parallax**:
  - Kiểm tra sự biến thiên tỷ lệ khoảng cách giữa mũi và 2 viền mắt khi lắc đầu để chống ảnh phẳng 2D.
- **Chống đứng hình (Freeze Frame) & Cắt ghép (Splice/Cut)**:
  - Theo dõi biến thiên tọa độ $X, Y$ của mũi và góc Euler. Nếu nguyên 28 khung hình không nhúc nhích $\rightarrow$ Hủy do đóng băng camera.

---

### 5️⃣ Quy trình Bảo mật Chống gian lận khi Đăng ký Ảnh (1:1 Java)

Quy trình xử lý trong `validate_and_process_student_photo_java_style()` khớp 1:1 với `AddStudentActivity.java` và `AdminDetailActivity.java`:

```mermaid
flowchart TD
    A[Bắt đầu Đăng ký / Upload Ảnh] --> B[Dò tìm khuôn mặt bằng OpenCV Haar Cascade + 35% Margin Padding]
    B --> C{Trích xuất được Vector MobileFaceNet?}
    C -- Không --> D[❌ Báo lỗi: Không trích xuất được khuôn mặt]
    C -- Có --> E{Tài khoản đã có Ảnh cũ avatar_<id>.jpg?}
    
    E -- Có --> F[Đọc ảnh cũ & Trích xuất Embedding cũ]
    F --> G{compare_faces(old, new) > 0.75?}
    G -- Không (isOwner = false) --> H[❌ Từ chối: Khuôn mặt không khớp với avatar hiện tại!]
    G -- Có (isOwner = true) --> I[Chuyển sang Bước 2]
    
    E -- Chưa có --> I[Bước 2: Kiểm tra Gian lận trùng mặt checkIfFaceAlreadyExists]
    I --> J[Quét tất cả file avatar_*.jpg trong đúng thư mục role]
    J --> K{Tồn tại tài khoản khác có compare_faces > 0.75?}
    K -- Có --> L[❌ Từ chối: Gian lận khuôn mặt đã thuộc về sinh viên mã: <duplicateId>]
    K -- Không --> M[✅ Hợp lệ: Lưu file avatar_<id>.jpg & UPDATE IrisDatabase.db]
```

---

## 📁 3. Bảng Tổng hợp Tham số & Ngưỡng Chuẩn

| Thành phần | Ngưỡng Chuẩn | Ý nghĩa Kỹ thuật |
| :--- | :--- | :--- |
| **Anti-Spoofing Real Score** | $\ge 0.35$ (`REAL_THRESHOLD = 0.35f`) | Ngưỡng xác nhận người thật của mô hình MiniVision TFLite |
| **Face Matching Cosine Similarity** | $> 0.75$ (`MATCH_THRESHOLD = 0.75f`) | Ngưỡng xác nhận cùng 1 người của mô hình MobileFaceNet TFLite |
| **Face Crop Padding** | $35\%$ - $40\%$ (`margin = 0.35`) | Khoảng lề tự động lấy trọn trán, tóc, cằm, tai và má |
| **Flash Shift Score** | $\ge 0.008$ | Ngưỡng độ lệch phản xạ phổ màu đỏ/xanh da người |
| **Eye Aspect Ratio (EAR)** | Closed $\le 0.16$, Open $\ge 0.22$ | Ngưỡng phát hiện chớp mắt tự nhiên |
| **3D Yaw Angle** | $\le -11.5^\circ$ & $\ge +11.5^\circ$ | Ngưỡng phát hiện lắc đầu trái/phải |

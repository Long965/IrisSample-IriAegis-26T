# Báo cáo Phân tích Dự án IrisSample-IriAegis-26T

## 1. Dự án giải quyết vấn đề gì
- **Dự án dùng để làm gì?** Quản lý điểm danh sinh viên bằng các công nghệ sinh trắc học (nhận diện khuôn mặt, mống mắt).
- **Đối tượng sử dụng là ai?** Các trường học, trung tâm đào tạo, giảng viên và quản trị viên hệ thống.
- **Có những vai trò nào:** 
  - **SUPER_ADMIN**: Quản lý toàn bộ hệ thống, quản trị viên cấp dưới (Admin), tất cả các môn học, cũng như backup và restore cơ sở dữ liệu.
  - **ADMIN** (Giảng viên): Quản lý môn học được phân công, quản lý sinh viên trong lớp, thực hiện điểm danh, xem thống kê và gửi báo cáo điểm danh qua email.
  - **STUDENT** (Sinh viên): Tham gia vào việc điểm danh (thông qua nhận diện khuôn mặt hoặc mống mắt).
- **Những chức năng chính là gì?**
  - Quản lý tài khoản (Đăng nhập, Quên/Reset mật khẩu).
  - Quản lý Quản trị viên/Giảng viên (Thêm, xóa, sửa, xem chi tiết).
  - Quản lý Môn học (Thêm mới, gán giảng viên, xem danh sách).
  - Quản lý Sinh viên (Thêm sinh viên, gán vào lớp, import từ file CSV).
  - Điểm danh sinh trắc học: Nhận diện bằng Khuôn mặt (Face Recognition) và Mống mắt (Iris Scanner), cũng có chế độ Mock Check-in (Điểm danh giả lập).
  - Quản lý Lịch sử Điểm danh: Thống kê, xuất báo cáo CSV, quản lý danh sách email nhận báo cáo và gửi báo cáo điểm danh trực tiếp qua SMTP.
  - Hệ thống System: Backup và Restore cơ sở dữ liệu.
- **Dự án đang phát triển mới hay tiếp quản code cũ?** Nhìn vào mức độ hoàn thiện của các chức năng (UI, CSDL local đầy đủ, hệ thống role phức tạp, NDK), đây giống như một dự án tiếp quản hoặc đã được build nền tảng khá kỹ.

## 2. Ngôn ngữ và phiên bản đang sử dụng
- **Ngôn ngữ:** Java (Android) và C++ (JNI/NDK).
- **Framework và các thư viện phụ thuộc:**
  - Android SDK: Min SDK 19, Target SDK 29.
  - Cấu trúc: Android View (XML) truyền thống.
  - AI & Biometrics: 
    - Google ML Kit (`com.google.mlkit:face-detection`) để phát hiện khuôn mặt.
    - TensorFlow Lite (`org.tensorflow:tensorflow-lite`) kèm mô hình `mobile_face_net.tflite` để so sánh và nhận diện khuôn mặt (Trích xuất Embedding vector 192).
    - AndroidX Biometric.
    - SDK mống mắt (Iris) từ bên thứ 3 (IriTech).
  - Tiện ích khác: 
    - `JavaMail API` để gửi email SMTP (Brevo/Sendinblue).
    - `MPAndroidChart` để vẽ biểu đồ thống kê.
    - `Gson` để parse JSON.
- **Cách cài đặt môi trường:** 
  - Android Studio.
  - Cài đặt NDK (phiên bản `16.1.4479499`) để biên dịch thư viện C/C++.
  - JDK 8.
- **Cách cấu hình database, API key và biến môi trường:**
  - Database là SQLite lưu cục bộ, không cần cấu hình server.
  - Cấu hình SMTP (Brevo) được lưu trong file `local.properties` (có thể bị bỏ trống) và fallback sang buildConfigField trong `app/build.gradle`.
- **Cách khởi động ứng dụng:**
  - Build ứng dụng bằng Android Studio (Nhấn nút Run) hoặc chạy lệnh `./gradlew assembleDebug`, sau đó cài đặt APK lên thiết bị Android hoặc máy ảo có Camera.
- **Cách đăng nhập và thử các chức năng chính:**
  - Chạy app, mở màn hình Đăng nhập (`LoginActivity`).
  - Nếu DB trống, có thể có cơ chế tạo Super Admin khởi tạo hoặc tạo qua giao diện Register Admin. 
  - Sau khi đăng nhập bằng Super Admin, tạo Admin thường, tạo Môn học, Thêm Sinh viên và kiểm thử việc điểm danh (`FaceCaptureActivity`).

## 3. Đọc cấu trúc thư mục và xác định file quan trọng
- **File cấu hình và file khởi chạy:**
  - `app/build.gradle`: File cấu hình build (SDK, dependencies, proguard, NDK, Cmake, biến môi trường SMTP).
  - `AndroidManifest.xml`: Định nghĩa quyền, intent-filter, và `LoginActivity` là màn hình khởi chạy.
- **Những module chính:** Toàn bộ code nằm trong `com.iritech.irissample`.
- **Các file đại diện cho nghiệp vụ quan trọng:**
  - `DatabaseHelper.java`: Định nghĩa CSDL SQLite và xử lý mọi thao tác CRUD (tạo bảng Admin, Sinh viên, Môn học, Điểm danh).
  - `FaceMatchHelper.java`: Xử lý AI nhận diện khuôn mặt thông qua mô hình TFLite.
  - `FaceCaptureActivity.java`: Màn hình chụp/nhận diện khuôn mặt điểm danh.
  - `EmailService.java`: Dịch vụ gửi email báo cáo (qua JavaMail).
- **Các module phụ trợ:**
  - `models/*`: Các class data model (VD: `AttendanceRecord.java`, `EmailRecipient.java`).
  - `adapters/*`: Xử lý hiển thị danh sách (RecyclerView).
  - Các Helper khác: `BackupRestoreHelper`, `EncryptionHelper`, `LicenseCheckHelper`.

## 4. Sơ đồ kiến trúc hệ thống


## 5. Đọc luồng xử lý của từng chức năng
**Luồng chức năng Điểm danh (Check-in Khuôn mặt):**
- **Bắt đầu:** Giảng viên chọn một môn học và nhấn bắt đầu điểm danh.
- **Giao diện nhận yêu cầu:** `FaceCaptureActivity.java`.
- **Hàm xử lý nghiệp vụ chính:**
  - Camera preview lấy khung hình.
  - ML Kit (`com.google.mlkit:face-detection`) phát hiện có khuôn mặt trong khung hình hay không.
  - Chuyển bitmap khuôn mặt cho `FaceMatchHelper.java` (gọi hàm `getFaceEmbedding()`). Hàm này đẩy ảnh qua model `mobile_face_net.tflite` để lấy Vector đặc trưng.
  - Hàm `compareFaces()` tính toán độ tương đồng (Cosine Similarity) với các vector khuôn mặt của sinh viên trong cơ sở dữ liệu.
- **Dữ liệu được lấy từ đâu:** CSDL SQLite (bảng `students` cột `photo_path` chứa đường dẫn ảnh gốc).
- **Dữ liệu được lưu ở đâu:** Nếu điểm danh thành công, gọi `DatabaseHelper` lưu vào bảng `checkin_history`.
- **Kết quả trả về:** Giao diện hiển thị thông báo "Điểm danh thành công cho sinh viên X" và có thể tiếp tục điểm danh người tiếp theo.

## 6. Hiểu cơ sở dữ liệu
CSDL SQLite `attendance.db` (version 20) nằm trong `DatabaseHelper.java`. Có 6 bảng chính:
1. **`admin`**: Lưu thông tin đăng nhập của giảng viên và quản trị viên (email, password_hash, fullName, role = SUPER_ADMIN/ADMIN, is_first_login, v.v.).
2. **`subjects`**: Lưu môn học (subject_id, subject_name, instructor_id [Khóa ngoại liên kết admin_id], time_slot, trạng thái...).
3. **`students`**: Lưu danh sách toàn bộ sinh viên (student_id, full_name, photo_path, checkin_password).
4. **`enrollments`**: Bảng nối giữa Sinh viên và Môn học (student_id, subject_id, status). Quan hệ Nhiều - Nhiều.
5. **`checkin_history`**: Lưu lịch sử điểm danh (checkin_id, student_id, subject_id, checkin_time, checkin_date). Khóa ngoại là student_id và subject_id.
6. **`email_recipients`**: Lưu cấu hình email nhận báo cáo riêng cho từng môn học (email_id, subject_id, email_address). Khóa ngoại là subject_id.

- **Bảng được tạo/đọc/sửa/xóa bởi từng chức năng:** 
  - Chức năng điểm danh: Đọc `students`, `enrollments`, tạo mới bản ghi vào `checkin_history`.
  - Quản lý lớp: Đọc/Sửa `subjects`, `enrollments`.
- **Migration & Sample Data:** Có hỗ trợ import sinh viên và môn học hàng loạt từ CSV (`ImportCsvActivity.java`), các file mẫu có sẵn như `sample_admins_import.csv`, `sample_students_import.csv`. Quá trình tạo Database diễn ra tự động trong `onCreate()` của `DatabaseHelper`. Không có script seed data tự động, người dùng Super Admin phải thao tác hoặc Import qua CSV.
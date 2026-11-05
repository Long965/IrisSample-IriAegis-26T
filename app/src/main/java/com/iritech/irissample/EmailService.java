package com.iritech.irissample;

import android.os.AsyncTask;
import android.util.Log;

import java.util.Properties;
import javax.mail.Authenticator; 
import javax.mail.Message; // này dùng để tạo email message
import javax.mail.MessagingException; // này dùng để xử lý lỗi liên quan đến email
import javax.mail.PasswordAuthentication; // này dùng để xác thực email
import javax.mail.Session; // này dùng để tạo phiên làm việc với máy chủ email
import javax.mail.Transport; // này dùng để gửi email
import javax.mail.internet.InternetAddress; // này dùng để định dạng địa chỉ email
import javax.mail.internet.MimeMessage; // này dùng để tạo email theo định dạng MIME
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import javax.mail.Multipart;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import java.io.File;


// Service gửi email thông qua Brevo SMTP
// Sử dụng JavaMail API để gửi email bất đồng bộ
// Config được đọc từ local.properties thông qua BuildConfig

public class EmailService {
    
    private static final String TAG = "EmailService";
    
    // Brevo SMTP Configuration
    private static final String SMTP_HOST = "smtp-relay.brevo.com";
    private static final String SMTP_PORT = "587"; // TLS port
    
    // Brevo authentication - Login từ Brevo SMTP settings
    private static final String SMTP_USERNAME = BuildConfig.BREVO_SMTP_LOGIN;
    
    // Đọc từ BuildConfig (được inject từ local.properties)
    private static final String SMTP_KEY = BuildConfig.BREVO_SMTP_KEY;
    private static final String SENDER_EMAIL = BuildConfig.BREVO_SENDER_EMAIL;
    private static final String SENDER_NAME = BuildConfig.BREVO_SENDER_NAME;
    
    // Landing page redirect URL - GitHub Pages
    // Trang này sẽ tự động redirect đến app khi user click link trong email
    private static final String APP_REDIRECT_URL = "https://test-html-iota-henna.vercel.app/";
    
    /**
     * Gửi email đăng ký thành công cho người dùng tự đăng ký
     * @param recipientEmail Email người nhận
     * @param fullName Tên đầy đủ người nhận
     * @param role Vai trò (Super Admin / Admin)
     */
    public static void sendRegistrationSuccessEmail(String recipientEmail, String fullName, String role) {
        String subject = "Đăng ký tài khoản thành công - " + SENDER_NAME;
        String body = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<p>Xin chào <strong>" + fullName + "</strong>,</p>" +
                "<p>Chúc mừng bạn đã đăng ký tài khoản <strong>" + role + "</strong> thành công trên hệ thống " + SENDER_NAME + ".</p>" +
                "<h3>Thông tin tài khoản:</h3>" +
                "<ul>" +
                "<li>Email: " + recipientEmail + "</li>" +
                "<li>Vai trò: " + role + "</li>" +
                "</ul>" +
                "<p>Bạn có thể đăng nhập vào hệ thống bằng email và mật khẩu đã đăng ký.</p>" +
                "<p style='margin-top: 20px;'>" +
                "<a href='" + APP_REDIRECT_URL + "' style='display: inline-block; padding: 12px 24px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;'>" +
                "Đăng nhập ngay</a>" +
                "</p>" +
                "<div style='background-color: #f5f5f5; padding: 12px; margin: 15px 0; border-radius: 5px;'>" +
                "<p style='margin: 0; font-size: 13px; color: #555;'><strong>Lưu ý:</strong> Nếu nút không hoạt động, vui lòng:</p>" +
                "<ol style='margin: 8px 0 0 0; padding-left: 20px; font-size: 13px; color: #555;'>" +
                "<li>Mở ứng dụng <strong>Iris Sample</strong> trên thiết bị</li>" +
                "<li>Hoặc truy cập: <a href='" + APP_REDIRECT_URL + "' style='color: #667eea;'>" + APP_REDIRECT_URL + "</a></li>" +
                "</ol>" +
                "</div>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" + SENDER_NAME + "</p>" +
                "</body></html>";
        
        sendEmailHtml(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email thông báo tài khoản được tạo bởi Super Admin
     * @param recipientEmail Email người nhận
     * @param fullName Tên đầy đủ
     * @param adminCode Mã số giảng viên
     */
    public static void sendAccountCreatedEmail(String recipientEmail, String fullName, String adminCode) {
        String subject = "Tài khoản Admin đã được tạo - " + SENDER_NAME;
        String body = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<p>Xin chào <strong>" + fullName + "</strong>,</p>" +
                "<p>Super Admin đã tạo tài khoản Admin cho bạn trên hệ thống " + SENDER_NAME + ".</p>" +
                "<h3>Thông tin đăng nhập:</h3>" +
                "<ul>" +
                "<li>Email: " + recipientEmail + "</li>" +
                "<li><strong>Mã số giảng viên: " + adminCode + "</strong></li>" +
                "<li>Mật khẩu: <em>(Super Admin sẽ cung cấp trực tiếp cho bạn)</em></li>" +
                "</ul>" +
                "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 12px; margin: 15px 0;'>" +
                "<h4 style='margin-top: 0; color: #856404;'>LƯU Ý QUAN TRỌNG:</h4>" +
                "<ul style='margin-bottom: 0;'>" +
                "<li>Vui lòng liên hệ Super Admin để nhận mật khẩu</li>" +
                "<li>Đăng nhập và đổi mật khẩu ngay sau lần đăng nhập đầu tiên</li>" +
                "<li>Không chia sẻ mật khẩu với bất kỳ ai</li>" +
                "<li>Xác thực bằng Device Lock (Vân tay/PIN thiết bị) khi thực hiện các thao tác quan trọng</li>" +
                "</ul>" +
                "</div>" +
                "<p style='margin-top: 20px;'>" +
                "<a href='" + APP_REDIRECT_URL + "' style='display: inline-block; padding: 12px 24px; background-color: #4CAF50; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;'>" +
                "Đăng nhập ngay</a>" +
                "</p>" +
                "<div style='background-color: #f5f5f5; padding: 12px; margin: 15px 0; border-radius: 5px;'>" +
                "<p style='margin: 0; font-size: 13px; color: #555;'><strong>Lưu ý:</strong> Nếu nút không hoạt động, vui lòng:</p>" +
                "<ol style='margin: 8px 0 0 0; padding-left: 20px; font-size: 13px; color: #555;'>" +
                "<li>Mở ứng dụng <strong>Iris Sample</strong> trên thiết bị</li>" +
                "<li>Hoặc truy cập: <a href='" + APP_REDIRECT_URL + "' style='color: #667eea;'>" + APP_REDIRECT_URL + "</a></li>" +
                "</ol>" +
                "</div>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" + SENDER_NAME + "</p>" +
                "</body></html>";
        
        sendEmailHtml(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email reset password với deep link
     * @param recipientEmail Email người nhận
     * @param token Reset token (UUID)
     */
    public static void sendPasswordResetEmail(String recipientEmail, String token) throws Exception {
        // Tạo web redirect link (Vercel) - sẽ tự động redirect đến app
        String resetLink = "https://reset-password-vert.vercel.app/?email=" + 
                          java.net.URLEncoder.encode(recipientEmail, "UTF-8") + 
                          "&token=" + token;
        
        String subject = "Đặt lại mật khẩu - " + SENDER_NAME;
        String body = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<p>Xin chào,</p>" +
                "<p>Bạn đã yêu cầu đặt lại mật khẩu cho tài khoản <strong>" + recipientEmail + "</strong>.</p>" +
                "<div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 12px; margin: 15px 0;'>" +
                "<p style='margin: 0; font-size: 14px; color: #856404;'><strong>LƯU Ý:</strong> Link này chỉ có hiệu lực trong <strong>2 phút (120 giây)</strong>.</p>" +
                "</div>" +
                "<p>Nhấn vào nút dưới đây để đặt lại mật khẩu:</p>" +
                "<p style='margin-top: 20px;'>" +
                "<a href='" + resetLink + "' style='display: inline-block; padding: 12px 24px; background-color: #ff5722; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;'>" +
                "Đặt lại mật khẩu</a>" +
                "</p>" +
                "<div style='background-color: #f5f5f5; padding: 12px; margin: 15px 0; border-radius: 5px;'>" +
                "<p style='margin: 0; font-size: 13px; color: #555;'><strong>Nếu nút không hoạt động:</strong></p>" +
                "<ol style='margin: 8px 0 0 0; padding-left: 20px; font-size: 13px; color: #555;'>" +
                "<li>Sao chép link sau và dán vào trình duyệt trên thiết bị di động:</li>" +
                "</ol>" +
                "<p style='margin: 8px 0 0 0; padding: 8px; background-color: #fff; border: 1px solid #ddd; border-radius: 3px; word-break: break-all; font-size: 12px; font-family: monospace;'>" +
                resetLink + "</p>" +
                "</div>" +
                "<div style='background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 12px; margin: 15px 0;'>" +
                "<p style='margin: 0; font-size: 13px; color: #721c24;'><strong>Cảnh báo bảo mật:</strong></p>" +
                "<ul style='margin: 8px 0 0 0; padding-left: 20px; font-size: 13px; color: #721c24;'>" +
                "<li>Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này</li>" +
                "<li>Không chia sẻ link này với bất kỳ ai</li>" +
                "<li>Link sẽ tự động hết hiệu lực sau 2 phút</li>" +
                "</ul>" +
                "</div>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" + SENDER_NAME + "</p>" +
                "</body></html>";
        
        // Gửi email đồng bộ (ForgotPasswordActivity đã chạy trong thread riêng)
        sendEmailSync(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email đồng bộ (blocking) - Dùng cho password reset
     * @throws Exception nếu gửi email thất bại
     */
    private static void sendEmailSync(String recipientEmail, String subject, String body) throws Exception {
        // Cấu hình properties cho Brevo SMTP
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true"); // TLS
        // ---- BẠN HÃY THÊM DÒNG NÀY VÀO ĐÂY ----
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.trust", SMTP_HOST);

        // Tạo session với Brevo SMTP authentication
        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USERNAME, SMTP_KEY);
            }
        });
        
        // Tạo message HTML
        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SENDER_EMAIL, SENDER_NAME));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
        message.setSubject(subject);
        message.setContent(body, "text/html; charset=utf-8");
        
        // Gửi email
        Transport.send(message);
        
        Log.d(TAG, "Password reset email sent successfully to: " + recipientEmail);
    }
    
    /**
     * Gửi email thông báo Super Admin đã cập nhật thông tin tài khoản
     * @param recipientEmail Email Admin bị thay đổi thông tin
     * @param fullName Tên đầy đủ
     * @param changesDescription Mô tả những thay đổi
     */
    public static void sendAccountUpdatedEmail(String recipientEmail, String fullName, 
                                                String changesDescription) {
        String subject = "Thông tin tài khoản đã được cập nhật - " + SENDER_NAME;
        String body = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<p>Xin chào <strong>" + fullName + "</strong>,</p>" +
                "<p>Super Admin đã cập nhật thông tin tài khoản của bạn trên hệ thống " + SENDER_NAME + ".</p>" +
                "<h3>Các thay đổi:</h3>" +
                "<p>" + changesDescription.replace("\n", "<br/>") + "</p>" +
                "<div style='background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 12px; margin: 15px 0;'>" +
                "<p style='margin: 0; color: #721c24;'>Nếu bạn không yêu cầu thay đổi này, vui lòng liên hệ với Super Admin ngay lập tức.</p>" +
                "</div>" +
                "<p style='margin-top: 20px;'>" +
                "<a href='" + APP_REDIRECT_URL + "' style='display: inline-block; padding: 12px 24px; background-color: #2196F3; color: white; text-decoration: none; border-radius: 5px; font-weight: bold;'>" +
                "Xem tài khoản</a>" +
                "</p>" +
                "<div style='background-color: #f5f5f5; padding: 12px; margin: 15px 0; border-radius: 5px;'>" +
                "<p style='margin: 0; font-size: 13px; color: #555;'><strong>Lưu ý:</strong> Nếu nút không hoạt động, truy cập: <a href='" + APP_REDIRECT_URL + "' style='color: #667eea;'>" + APP_REDIRECT_URL + "</a></p>" +
                "</div>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" + SENDER_NAME + "</p>" +
                "</body></html>";
        
        sendEmailHtml(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email thông báo tài khoản đã bị xóa bởi Super Admin
     * @param recipientEmail Email Admin bị xóa
     * @param fullName Tên đầy đủ
     */
    public static void sendAccountDeletedEmail(String recipientEmail, String fullName) {
        String subject = "Tài khoản đã bị xóa - " + SENDER_NAME;
        String body = "<html><body style='font-family: Arial, sans-serif;'>" +
                "<p>Xin chào <strong>" + fullName + "</strong>,</p>" +
                "<p>Tài khoản Admin của bạn trên hệ thống " + SENDER_NAME + " đã bị Super Admin xóa.</p>" +
                "<h3>Thông tin:</h3>" +
                "<ul>" +
                "<li>Email: " + recipientEmail + "</li>" +
                "<li>Trạng thái: <span style='color: #dc3545; font-weight: bold;'>Đã xóa</span></li>" +
                "</ul>" +
                "<div style='background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 12px; margin: 15px 0;'>" +
                "<p style='margin: 0; color: #721c24;'>Bạn sẽ không thể đăng nhập vào hệ thống bằng tài khoản này nữa.</p>" +
                "</div>" +
                "<p>Nếu bạn cho rằng đây là một sai sót, vui lòng liên hệ với Super Admin để được hỗ trợ.</p>" +
                "<p style='margin-top: 30px;'>Trân trọng,<br/>" + SENDER_NAME + "</p>" +
                "</body></html>";
        
        sendEmailHtml(recipientEmail, subject, body);
    }
    
    /**
     * Gửi email báo cáo điểm danh với file CSV đính kèm
     * @param recipients Danh sách email người nhận
     * @param subject Tiêu đề email
     * @param htmlBody Nội dung email HTML
     * @param csvFile File CSV đính kèm
     * @param callback Callback để thông báo kết quả
     */
    public static void sendAttendanceReportEmail(final String[] recipients, final String subject, 
                                                   final String htmlBody, final File csvFile,
                                                   final EmailCallback callback) {
        new AsyncTask<Void, Void, Boolean>() {
            private Exception exception;
            
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Cấu hình properties cho Brevo SMTP
                    Properties props = new Properties();
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtp.starttls.enable", "true");
                    // ---- BẠN HÃY THÊM DÒNG NÀY VÀO ĐÂY ----
                    props.put("mail.smtp.starttls.required", "true");
                    props.put("mail.smtp.host", SMTP_HOST);
                    props.put("mail.smtp.port", SMTP_PORT);
                    props.put("mail.smtp.ssl.trust", SMTP_HOST);

                    // Tạo session với Brevo SMTP authentication
                    Session session = Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {

                            // ---- CHÈN 2 DÒNG LOG VÀO ĐÚNG ĐOẠN NÀY ----
                            Log.d("CHECK_BREVO", "DỮ LIỆU LOGIN: " + SMTP_USERNAME);
                            Log.d("CHECK_BREVO", "DỮ LIỆU KEY: " + SMTP_KEY);
                            // ------------------------------------------

                            return new PasswordAuthentication(SMTP_USERNAME, SMTP_KEY);
                        }
                    });
                    
                    // Tạo message
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(SENDER_EMAIL, SENDER_NAME));
                    
                    // Thêm tất cả recipients
                    InternetAddress[] addresses = new InternetAddress[recipients.length];
                    for (int i = 0; i < recipients.length; i++) {
                        addresses[i] = new InternetAddress(recipients[i]);
                    }
                    message.setRecipients(Message.RecipientType.TO, addresses);
                    message.setSubject(subject);
                    
                    // Tạo multipart message
                    Multipart multipart = new MimeMultipart();
                    
                    // Phần 1: HTML body
                    MimeBodyPart htmlPart = new MimeBodyPart();
                    htmlPart.setContent(htmlBody, "text/html; charset=utf-8");
                    multipart.addBodyPart(htmlPart);
                    
                    // Phần 2: CSV attachment
                    if (csvFile != null && csvFile.exists()) {
                        MimeBodyPart attachmentPart = new MimeBodyPart();
                        FileDataSource source = new FileDataSource(csvFile);
                        attachmentPart.setDataHandler(new DataHandler(source));
                        attachmentPart.setFileName(csvFile.getName());
                        multipart.addBodyPart(attachmentPart);
                    }
                    
                    // Set multipart content
                    message.setContent(multipart);
                    
                    // Gửi email
                    Transport.send(message);
                    
                    Log.d(TAG, "Attendance report email sent successfully to " + recipients.length + " recipients");
                    return true;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send attendance report email", e);
                    exception = e;
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (callback != null) {
                    if (success) {
                        callback.onSuccess();
                    } else {
                        callback.onError(exception != null ? exception.getMessage() : "Unknown error");
                    }
                }
            }
        }.execute();
    }
    
    /**
     * Callback interface cho email sending
     */
    public interface EmailCallback {
        void onSuccess();
        void onError(String error);
    }
    
    /**
     * Gửi email HTML bất đồng bộ
     * @param recipientEmail Email người nhận
     * @param subject Tiêu đề email
     * @param body Nội dung email HTML
     */
    private static void sendEmailHtml(final String recipientEmail, final String subject, final String body) {
        // Sử dụng AsyncTask để gửi email trong background thread
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    // Cấu hình properties cho Brevo SMTP
                    Properties props = new Properties();
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtp.starttls.enable", "true"); // TLS
                    // ---- BẠN HÃY THÊM DÒNG NÀY VÀO ĐÂY ----
                    props.put("mail.smtp.starttls.required", "true");
                    props.put("mail.smtp.host", SMTP_HOST);
                    props.put("mail.smtp.port", SMTP_PORT);
                    props.put("mail.smtp.ssl.trust", SMTP_HOST);
                    
                    // Tạo session với Brevo SMTP authentication
                    Session session = Session.getInstance(props, new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(SMTP_USERNAME, SMTP_KEY);
                        }
                    });
                    
                    // Tạo message HTML
                    Message message = new MimeMessage(session);
                    message.setFrom(new InternetAddress(SENDER_EMAIL, SENDER_NAME));
                    message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                    message.setSubject(subject);
                    message.setContent(body, "text/html; charset=utf-8");
                    
                    // Gửi email
                    Transport.send(message);
                    
                    Log.d(TAG, "Email sent successfully to: " + recipientEmail);
                    return true;
                    
                } catch (Exception e) {
                    Log.e(TAG, "Failed to send email to: " + recipientEmail, e);
                    return false;
                }
            }
            
            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Log.i(TAG, "Email delivery completed successfully");
                } else {
                    Log.w(TAG, "Email delivery failed. Check SMTP configuration.");
                }
            }
        }.execute();
    }
}

package com.iritech.irissample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

//import com.iritech.iris.CaptureActivity;
//import com.iritech.iris.Constants;

import java.io.File;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText editTextEmail;
    private Button buttonSendResetLink;
    private Button buttonResetWithIris; // THÊM MỚI: Button reset bằng mống mắt
    private TextView textViewError;
    private TextView textViewOrDivider; // THÊM MỚI: Divider "hoặc"
    private ProgressBar progressBar;
    private DatabaseHelper dbHelper;
    private PasswordResetHelper resetHelper;
    
    private static final int REQUEST_CODE_IDENTIFY_FOR_RESET = 3001; // THEM MOI
    private static final int REQUEST_IRIS_PERMISSIONS = 3002;
    private static final int REQUEST_IRIS_SDK_PERMISSION = 3003;
    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";

    private Intent pendingIrisIntent; // Intent cho den duoc cap permission

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        dbHelper = new DatabaseHelper(this);
        resetHelper = new PasswordResetHelper(dbHelper);

        // QUAN TRONG: Thiet lap USB Activity de SDK co the mo sensor phan cung
        //CaptureActivity.setUSBActivity(this);
        initIrisFolders();

        // Yêu cầu quyền SDK mống mắt ngay khi khởi động (fire-and-forget, giống MainActivity.loadConfigs)
        if (ContextCompat.checkSelfPermission(this, IRIS_SDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{IRIS_SDK_PERMISSION}, REQUEST_IRIS_SDK_PERMISSION);
        }

        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        editTextEmail = findViewById(R.id.editTextEmail);
        buttonSendResetLink = findViewById(R.id.buttonSendResetLink);
        buttonResetWithIris = findViewById(R.id.buttonResetWithIris); // THÊM MỚI
        textViewOrDivider = findViewById(R.id.textViewOrDivider); // THÊM MỚI
        textViewError = findViewById(R.id.textViewError);
        progressBar = findViewById(R.id.progressBar);
    }

    private void setupListeners() {
        buttonSendResetLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendResetLink();
            }
        });
        
        // Listener cho button reset bằng mống mắt
        buttonResetWithIris.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPasswordWithIris();
            }
        });
    }

    private void sendResetLink() {
        String email = editTextEmail.getText().toString().trim();

        // Validate email
        if (TextUtils.isEmpty(email)) {
            showError("Vui lòng nhập email");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Email không hợp lệ");
            return;
        }

        // Show loading
        progressBar.setVisibility(View.VISIBLE);
        buttonSendResetLink.setEnabled(false);
        textViewError.setVisibility(View.GONE);

        // Tạo reset token trong database
        String token = resetHelper.createPasswordResetToken(email);

        if (token == null) {
            // Email không tồn tại
            progressBar.setVisibility(View.GONE);
            buttonSendResetLink.setEnabled(true);
            showError("Email không tồn tại trong hệ thống");
            return;
        }

        // Gửi email trong background thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    EmailService.sendPasswordResetEmail(email, token);

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            buttonSendResetLink.setEnabled(true);
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Link đặt lại mật khẩu đã được gửi đến email của bạn.\nLink có hiệu lực trong 2 phút.",
                                    Toast.LENGTH_LONG).show();
                            finish(); // Quay về màn hình trước
                        }
                    });

                } catch (final Exception e) {
                    e.printStackTrace();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setVisibility(View.GONE);
                            buttonSendResetLink.setEnabled(true);
                            showError("Không thể gửi email. Vui lòng thử lại sau.\n" + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    
    /**
     * THÊM MỚI: Reset password bằng mống mắt
     * Flow:
     * 1. Mở CaptureActivity với ACTION_IDENTIFY
     * 2. SDK verify mống mắt và trả về user_id (email) nếu match
     * 3. Mở ResetPasswordActivity với email đã verify
     */
    private void resetPasswordWithIris() {
        // Kiểm tra có ít nhất 1 admin đã ghi danh mống mắt không
        if (!dbHelper.hasAnyAdminWithIris()) {
            Toast.makeText(this, 
                          "Chưa có Admin nào đăng ký mống mắt.\nVui lòng sử dụng phương thức reset qua email.",
                          Toast.LENGTH_LONG).show();
            return;
        }
        
        // Mở CaptureActivity để verify identity
        //Intent intent = new Intent(this, CaptureActivity.class);
        //intent.setAction(Constants.ACTION_IDENTIFY);
        //intent.putExtra(Constants.EXTRA_USER_ID, ""); // Để trống, SDK sẽ tìm trong tất cả templates
        
        // Kiểm tra quyền trước (WRITE trước, CAMERA sau)
        //checkIrisPermissionsAndStart(intent);
    }

    /**
     * Kiểm tra quyền WRITE_EXTERNAL_STORAGE và CAMERA trước khi khởi chạy CaptureActivity.
     * Thứ tự: Ghi (WRITE) trước → Chụp (CAMERA) sau.
     */
    private void checkIrisPermissionsAndStart(Intent intent) {
        boolean hasWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            //LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_IDENTIFY_FOR_RESET);
            return;
        }

        pendingIrisIntent = intent;
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                },
                REQUEST_IRIS_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IRIS_SDK_PERMISSION) {
            // Kết quả fire-and-forget – không chặn luồng người dùng
        } else if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingIrisIntent != null) {
//                LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, REQUEST_CODE_IDENTIFY_FOR_RESET);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để đặt lại mật khẩu bằng mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_IDENTIFY_FOR_RESET && resultCode == RESULT_OK && data != null) {
            /*int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
            
            if (resultCodeExt == 0 && resultItems != null) {
                // Verify thành công - Parse user_id từ result
                String identifiedUserId = extractUserIdFromResult(resultItems);
                
                if (identifiedUserId != null && !identifiedUserId.isEmpty()) {
                    // Convert safe user_id về email format
                    // VD: "admin_at_example_com" -> "admin@example.com"
                    String email = identifiedUserId.replace("_at_", "@").replace("_", ".");
                    
                    // Kiểm tra email có tồn tại trong database không
                    if (dbHelper.isEmailExists(email)) {
                        // Verify thành công → Chuyển đến ResetPasswordActivity
                        Toast.makeText(this, 
                                      "Xác thực mống mắt thành công!\nBạn có thể đặt lại mật khẩu ngay bây giờ.",
                                      Toast.LENGTH_LONG).show();
                        
                        // Tạo token tạm để bypass kiểm tra token trong ResetPasswordActivity
                        String tempToken = resetHelper.createPasswordResetTokenForIris(email);
                        
                        // Chuyển đến ResetPasswordActivity
                        Intent resetIntent = new Intent(this, ResetPasswordActivity.class);
                        resetIntent.putExtra("email", email);
                        resetIntent.putExtra("token", tempToken);
                        resetIntent.putExtra("verified_by_iris", true); // Đánh dấu đã verify bằng mống mắt
                        startActivity(resetIntent);
                        finish();
                    } else {
                        Toast.makeText(this, "Email không tồn tại trong hệ thống", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Toast.makeText(this, "Không thể xác định danh tính", Toast.LENGTH_SHORT).show();
                }
            } else {
                // Verify thất bại
                Toast.makeText(this, "Không tìm thấy mống mắt phù hợp.\nVui lòng thử lại hoặc sử dụng email.",
                              Toast.LENGTH_LONG).show();
            }*/
        }
    }
    
    /**
     * Trích xuất user_id từ result string của SDK
     * Format: "ID: safeUserId, distance: xxx" hoặc "ID: safeUserId1, distance: xxx; ID: safeUserId2, distance: yyy"
     */
    private String extractUserIdFromResult(String resultItems) {
        if (resultItems == null || resultItems.isEmpty()) {
            return null;
        }
        
        try {
            // Get the first ID (best match)
            if (resultItems.contains("ID: ")) {
                int startIndex = resultItems.indexOf("ID: ") + 4;
                int endIndex = resultItems.indexOf(", distance:", startIndex);
                if (endIndex > startIndex) {
                    return resultItems.substring(startIndex, endIndex).trim();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    public void onBackToLoginClick(View view) {
        finish(); // Quay về màn hình login
    }

    /**
     * Tạo thư mục cần thiết cho SDK mống mắt (giống MainActivity.initFolder)
     */
    private void initIrisFolders() {
        String base = Environment.getExternalStorageDirectory().toString() + File.separator + "iritech";
        File enrollFolder = new File(base + File.separator + "enroll");
        if (!enrollFolder.exists()) enrollFolder.mkdirs();
        File verifyFolder = new File(base + File.separator + "verify");
        if (!verifyFolder.exists()) verifyFolder.mkdirs();
    }
}

package com.iritech.irissample;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class ResetPasswordActivity extends AppCompatActivity {

    private TextView textViewEmail;
    private EditText editTextNewPassword;
    private EditText editTextConfirmPassword;
    private Button buttonResetPassword;
    private TextView textViewError;
    private DatabaseHelper dbHelper;
    private PasswordResetHelper resetHelper;
    private PasswordValidationHelper validationHelper;

    private String email;
    private String token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        dbHelper = new DatabaseHelper(this);
        resetHelper = new PasswordResetHelper(dbHelper);
        validationHelper = new PasswordValidationHelper(dbHelper);

        initializeViews();
        handleDeepLink(getIntent());
        setupListeners();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    private void initializeViews() {
        textViewEmail = findViewById(R.id.textViewEmail);
        editTextNewPassword = findViewById(R.id.editTextNewPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        buttonResetPassword = findViewById(R.id.buttonResetPassword);
        textViewError = findViewById(R.id.textViewError);
    }

    private void handleDeepLink(Intent intent) {
        // THÊM MỚI: Kiểm tra xem có phải từ Iris verification không
        boolean verifiedByIris = intent.getBooleanExtra("verified_by_iris", false);
        
        if (verifiedByIris) {
            // Đến từ iris verification - lấy email và token trực tiếp từ intent
            email = intent.getStringExtra("email");
            token = intent.getStringExtra("token");
            
            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(token)) {
                Toast.makeText(this, "Dữ liệu không hợp lệ", Toast.LENGTH_SHORT).show();
                navigateToLogin();
                return;
            }
            
            // Hiển thị thông báo đã verify bằng mống mắt
            textViewEmail.setText("Đã xác thực bằng mống mắt\nĐặt lại mật khẩu cho: " + email);
            textViewEmail.setTextColor(0xFF4CAF50); // Green color
            return;
        }
        
        // Flow cũ: Deep link từ email
        Uri data = intent.getData();

        if (data != null && "irissample".equals(data.getScheme()) && "reset-password".equals(data.getHost())) {
            email = data.getQueryParameter("email");
            token = data.getQueryParameter("token");

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(token)) {
                // Deep link không hợp lệ
                Toast.makeText(this, "Link không hợp lệ", Toast.LENGTH_SHORT).show();
                navigateToLogin();
                return;
            }

            // Kiểm tra token có hợp lệ và chưa hết hạn không
            if (!resetHelper.isResetTokenValid(email, token)) {
                // Token hết hạn hoặc không hợp lệ
                Toast.makeText(this, "Link đã hết hạn hoặc không hợp lệ.\nVui lòng yêu cầu link mới.", Toast.LENGTH_LONG).show();
                
                // Xóa token hết hạn
                resetHelper.clearExpiredToken(email);
                
                navigateToLogin();
                return;
            }

            // Token hợp lệ → Hiển thị email
            textViewEmail.setText("Đặt lại mật khẩu cho: " + email);

        } else {
            // Không có deep link và không phải từ iris → quay về login
            Toast.makeText(this, "Truy cập không hợp lệ", Toast.LENGTH_SHORT).show();
            navigateToLogin();
        }
    }

    private void setupListeners() {
        buttonResetPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                resetPassword();
            }
        });
    }

    private void resetPassword() {
        String newPassword = editTextNewPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();

        // Validate
        if (TextUtils.isEmpty(newPassword)) {
            showError("Vui lòng nhập mật khẩu mới");
            return;
        }

        if (newPassword.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp");
            return;
        }

        // Kiểm tra password mới không được trùng với password cũ
        if (validationHelper.isSameAsOldPassword(email, newPassword)) {
            showError("Mật khẩu mới không được giống với mật khẩu cũ");
            return;
        }

        // Reset password trong database
        boolean success = resetHelper.resetPassword(email, newPassword, token);

        if (success) {
            Toast.makeText(this, "Đặt lại mật khẩu thành công!\nVui lòng đăng nhập bằng mật khẩu mới.", Toast.LENGTH_LONG).show();
            navigateToLogin();
        } else {
            showError("Không thể đặt lại mật khẩu. Link có thể đã hết hạn.\nVui lòng yêu cầu link mới.");
        }
    }

    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}

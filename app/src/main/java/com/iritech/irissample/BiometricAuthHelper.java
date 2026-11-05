package com.iritech.irissample;

import android.content.Context;
import android.os.Build;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import java.util.concurrent.Executor;

/**
 * Helper class để xử lý xác thực Device PIN/Biometric.
 * Ưu tiên: Vân tay/Khuôn mặt → Device PIN/Pattern/Password → Mật khẩu App.
 *
 * Logic 3 tầng:
 *   Tầng 1 – Biometric (BIOMETRIC_WEAK): thiết bị có vân tay/khuôn mặt đã đăng ký.
 *   Tầng 2 – Device Credential only: thiết bị có PIN/Pattern/Password nhưng CHƯA đăng ký vân tay.
 *   Tầng 3 – App password fallback: thiết bị chưa thiết lập Device Lock nào cả.
 */
public class BiometricAuthHelper {

    public interface AuthCallback {
        void onAuthSuccess();
        void onAuthFailed();
        void onAuthError(String errorMessage);
    }

    /**
     * Hiển thị xác thực theo logic 3 tầng.
     *
     * @param activity          Activity hiện tại
     * @param title             Tiêu đề dialog
     * @param subtitle          Mô tả
     * @param currentUserEmail  Email người dùng đang đăng nhập (dùng cho fallback mật khẩu app)
     * @param dbHelper          DatabaseHelper (dùng cho fallback mật khẩu app)
     * @param callback          Callback kết quả
     */
    public static void showBiometricPrompt(FragmentActivity activity,
                                           String title,
                                           String subtitle,
                                           String currentUserEmail,
                                           DatabaseHelper dbHelper,
                                           AuthCallback callback) {

        BiometricManager biometricManager = BiometricManager.from(activity);

        // Kiểm tra riêng biệt từng tầng
        boolean hasBiometric = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS;

        boolean hasDeviceCredential = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == BiometricManager.BIOMETRIC_SUCCESS;

        if (!hasBiometric && !hasDeviceCredential) {
            // Tầng 3: Không có device lock → fallback về mật khẩu app
            showAppPasswordFallback(activity, title, currentUserEmail, dbHelper, callback);
            return;
        }

        // Chọn authenticator đúng để tránh lỗi "No fingerprints enrolled"
        int authenticators;
        if (hasBiometric) {
            // Tầng 1: Có biometric đã đăng ký → dùng biometric + PIN fallback
            authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        } else {
            // Tầng 2: Chỉ có PIN/Pattern/Password, không có biometric → device credential only
            // Không dùng BIOMETRIC_STRONG để tránh lỗi "No fingerprints enrolled"
            authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        }

        startBiometricAuth(activity, title, subtitle, authenticators,
                currentUserEmail, dbHelper, callback);
    }

    private static void startBiometricAuth(FragmentActivity activity,
                                            String title,
                                            String subtitle,
                                            int authenticators,
                                            String currentUserEmail,
                                            DatabaseHelper dbHelper,
                                            AuthCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            callback.onAuthFailed();
                        } else if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS
                                || errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL) {
                            // Thiết bị không có vân tay/credential → fallback mật khẩu app
                            showAppPasswordFallback(activity, title, currentUserEmail, dbHelper, callback);
                        } else {
                            callback.onAuthError("Xác thực thất bại: " + errString);
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(
                            @NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onAuthSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        // Vân tay/khuôn mặt không khớp, user vẫn còn lượt thử → không gọi callback
                    }
                });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(authenticators)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    /**
     * Tầng 3 fallback: Thiết bị chưa thiết lập Device Lock.
     * Hỏi mật khẩu ứng dụng (email + password đăng nhập).
     */
    private static void showAppPasswordFallback(FragmentActivity activity,
                                                 String title,
                                                 String currentUserEmail,
                                                 DatabaseHelper dbHelper,
                                                 AuthCallback callback) {
        if (currentUserEmail == null || dbHelper == null) {
            callback.onAuthError("Vui lòng thiết lập Device Lock (PIN/Pattern/Password) trong Settings để bảo mật tài khoản.");
            return;
        }

        EditText passwordInput = new EditText(activity);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint("Nhập mật khẩu đăng nhập của bạn");

        // Thêm padding cho EditText
        LinearLayout container = new LinearLayout(activity);
        container.setPadding(60, 20, 60, 0);
        container.addView(passwordInput);

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage("Thiết bị chưa thiết lập Device Lock.\nNhập mật khẩu đăng nhập để xác thực:")
                .setView(container)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String enteredPassword = passwordInput.getText().toString();
                    if (enteredPassword.isEmpty()) {
                        callback.onAuthError("Vui lòng nhập mật khẩu");
                        return;
                    }
                    // Debug log
                    android.util.Log.d("BiometricAuthHelper", "Verifying password for email: " + currentUserEmail);
                    boolean verified = dbHelper.verifyAdminPassword(currentUserEmail, enteredPassword);
                    android.util.Log.d("BiometricAuthHelper", "Verification result: " + verified);

                    if (verified) {
                        callback.onAuthSuccess();
                    } else {
                        callback.onAuthError("Mật khẩu không đúng. Vui lòng thử lại.");
                    }
                })
                .setNegativeButton("Hủy", (dialog, which) -> callback.onAuthFailed())
                .setCancelable(false)
                .show();
    }
}


package com.iritech.irissample;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Toast;

import com.iritech.irissample.R;
//import com.iritech.iris.CaptureActivity;
//import com.iritech.iris.Constants;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends AppCompatActivity {

    private EditText  editTextEmail;
    private EditText  editTextPassword;
    private Button    buttonLogin;
    private Button    buttonLoginWithIris;
    private Button    buttonRegister;
    private TextView  textViewError;
    private TextView  textViewForgotPassword;

    private DatabaseHelper dbHelper;

    // ── Request codes ────────────────────────────────────────────────────────
    private static final int REQUEST_CODE_IDENTIFY_LOGIN   = 3001;
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_IRIS_PERMISSIONS      = 102;
    private static final int REQUEST_IRIS_SDK_PERMISSION   = 103;
    private static final int REQUEST_ALL_PERMISSIONS       = 104;

    /**
     * Request code dùng khi mở FaceCaptureActivity để xác thực khuôn mặt
     * sau khi đăng nhập bằng email + password thành công.
     */
    private static final int REQUEST_CODE_FACE_VERIFY = 300;

    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";

    private Intent pendingIrisIntent;

    /**
     * Lưu tạm thông tin admin đã xác thực email/password thành công.
     * Dùng để chuyển sang MainActivity sau khi xác thực khuôn mặt xong.
     */
    private String pendingEmail;
    private String pendingRole;
    private String pendingName;

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        android.util.Log.d("LoginActivity", "=== onCreate() started ===");

        dbHelper = new DatabaseHelper(this);

        //CaptureActivity.setUSBActivity(this);
        initIrisFolders();
        requestCriticalPermissions();

        if (ContextCompat.checkSelfPermission(this, IRIS_SDK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{IRIS_SDK_PERMISSION}, REQUEST_IRIS_SDK_PERMISSION);
        }

        boolean superAdminExists = dbHelper.isSuperAdminExists();
        android.util.Log.d("LoginActivity", "Super Admin exists: " + superAdminExists);

        if (!superAdminExists) {
            if (!checkStoragePermission()) {
                android.util.Log.d("LoginActivity", "Storage permission not granted, waiting...");
                return;
            }
            if (checkForBackupOnStartup()) return;

            android.util.Log.d("LoginActivity", "No backup found → RegisterAdminActivity");
            startActivity(new Intent(this, RegisterAdminActivity.class));
            finish();
            return;
        }

        // ── Ánh xạ view ──────────────────────────────────────────────────────
        editTextEmail        = findViewById(R.id.editTextEmail);
        editTextPassword     = findViewById(R.id.editTextPassword);
        buttonLogin          = findViewById(R.id.buttonLogin);
        buttonLoginWithIris  = findViewById(R.id.buttonLoginWithIris);
        buttonRegister       = findViewById(R.id.buttonRegister);
        textViewError        = findViewById(R.id.textViewError);
        textViewForgotPassword = findViewById(R.id.textViewForgotPassword);

        handleDeepLink(getIntent());

        buttonRegister.setVisibility(View.GONE);

        buttonLogin.setOnClickListener(v -> attemptLogin());

        buttonLoginWithIris.setOnClickListener(v -> loginWithIris());

        textViewForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    // ==================== ĐĂNG NHẬP BẰNG EMAIL + PASSWORD ====================

    private void attemptLogin() {
        String email    = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();

        Cursor cursor = dbHelper.authenticateUser(email, password);

        if (cursor != null && cursor.moveToFirst()) {
            String role     = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
            String fullName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
            String photo    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_PHOTO));
            cursor.close();

            // Lưu tạm — sẽ dùng sau khi xác thực khuôn mặt (nếu có)
            pendingEmail = email;
            pendingRole  = role;
            pendingName  = fullName;

            // Kiểm tra file ảnh thực sự tồn tại trên đĩa
            boolean hasValidPhoto = !android.text.TextUtils.isEmpty(photo)
                    && new File(photo).exists();

            if (hasValidPhoto) {
                // ── Có avatar → bắt buộc xác thực khuôn mặt ─────────────────
                String adminId = dbHelper.getAdminIdByEmail(email);
                Intent intent  = new Intent(LoginActivity.this, FaceCaptureActivity.class);
                intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID,     adminId);
                intent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, photo);
                intent.putExtra(FaceCaptureActivity.EXTRA_MODE,   FaceCaptureActivity.MODE_CHECKIN);
                intent.putExtra(FaceCaptureActivity.EXTRA_FOLDER, FaceCaptureActivity.FOLDER_ADMINS);
                startActivityForResult(intent, REQUEST_CODE_FACE_VERIFY);
            } else {
                // ── Chưa có avatar → vào thẳng MainActivity ──────────────────
                goToMain();
            }

        } else {
            // Đăng nhập thất bại
            if (textViewError != null) {
                textViewError.setText("Tên đăng nhập hoặc mật khẩu không đúng.");
                textViewError.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Tên đăng nhập hoặc mật khẩu không đúng.", Toast.LENGTH_SHORT).show();
            }
            editTextPassword.setText("");
        }
    }

    /**
     * Chuyển sang MainActivity với thông tin đã xác thực.
     */
    private void goToMain() {
        Toast.makeText(this, "Đăng nhập thành công! Xin chào " + pendingName, Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.putExtra("USER_EMAIL", pendingEmail);
        intent.putExtra("USER_ROLE",  pendingRole);
        intent.putExtra("USER_NAME",  pendingName);
        startActivity(intent);
        finish();
    }

    // ==================== ACTIVITY RESULT ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ── Xác thực khuôn mặt sau đăng nhập email/password ─────────────────
        if (requestCode == REQUEST_CODE_FACE_VERIFY) {
            if (resultCode == RESULT_OK && data != null) {
                boolean matched = data.getBooleanExtra(
                        FaceCaptureActivity.RESULT_FACE_MATCHED, false);
                if (matched) {
                    goToMain();
                } else {
                    // Không khớp → báo lỗi, cho nhập lại
                    Toast.makeText(this,
                            "❌ Xác thực khuôn mặt thất bại. Vui lòng thử lại.",
                            Toast.LENGTH_LONG).show();
                    editTextPassword.setText("");
                }
            } else {
                // Người dùng bấm hủy hoặc hết timeout
                Toast.makeText(this, "Đã hủy xác thực khuôn mặt.", Toast.LENGTH_SHORT).show();
                editTextPassword.setText("");
            }
            return; // Đã xử lý xong, không rơi xuống các case khác
        }

        // ── Kết quả đăng nhập bằng mống mắt ─────────────────────────────────
        // ── Kết quả đăng nhập bằng mống mắt ─────────────────────────────────
        if (requestCode == REQUEST_CODE_IDENTIFY_LOGIN && resultCode == RESULT_OK) {
            /*if (data != null) {
                int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
                String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);

                android.util.Log.d("LoginActivity", "Iris identify: code=" + resultCodeExt + ", msg=" + resultMsg);

                if (resultCodeExt == 0) {
                    int resultCount = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
                    String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);

                    if (resultCount > 0 && resultItems != null) {
                        String email = extractEmailFromResult(resultItems);
                        if (email != null && !email.isEmpty()) {
                            Cursor cursor = dbHelper.getAdminByEmail(email);
                            if (cursor != null && cursor.moveToFirst()) {

                                // 1. Lưu tạm thông tin Admin nhận diện từ mống mắt
                                pendingEmail = email;
                                pendingRole  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
                                pendingName  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
                                String photo = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_PHOTO));
                                cursor.close();

                                // 2. Kiểm tra dữ liệu khuôn mặt của Admin này trên thiết bị
                                boolean hasValidPhoto = !android.text.TextUtils.isEmpty(photo) && new java.io.File(photo).exists();

                                if (hasValidPhoto) {
                                    // CÓ dữ liệu khuôn mặt -> Mở FaceCaptureActivity để quét xác thực khuôn mặt
                                    String adminId = dbHelper.getAdminIdByEmail(email);
                                    Intent faceIntent = new Intent(LoginActivity.this, FaceCaptureActivity.class);
                                    faceIntent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID,     adminId);
                                    faceIntent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, photo);
                                    faceIntent.putExtra(FaceCaptureActivity.EXTRA_MODE,           FaceCaptureActivity.MODE_CHECKIN);
                                    faceIntent.putExtra(FaceCaptureActivity.EXTRA_FOLDER,         FaceCaptureActivity.FOLDER_ADMINS);

                                    // Sử dụng lại REQUEST_CODE_FACE_VERIFY để hứng kết quả trùng với luồng mật khẩu
                                    startActivityForResult(faceIntent, REQUEST_CODE_FACE_VERIFY);
                                } else {
                                    // KHÔNG có dữ liệu khuôn mặt -> Báo lỗi theo yêu cầu của bạn
                                    Toast.makeText(this, "Admin chưa đăng kí mống mắt", Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(this, "Không tìm thấy thông tin Admin", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Không thể xác định email từ kết quả", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, "Không tìm thấy mống mắt khớp. Vui lòng thử lại hoặc đăng nhập bằng mật khẩu.", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Lỗi nhận diện: " + resultMsg, Toast.LENGTH_SHORT).show();
                }
            }*/
        }
    }

    // ==================== ĐĂNG NHẬP BẰNG MỐNG MẮT ====================

    private void loginWithIris() {
        if (!dbHelper.hasAnyAdminWithIris()) {
            Toast.makeText(this,
                    "Chưa có Admin nào đăng ký mống mắt.\nVui lòng đăng nhập bằng email và mật khẩu.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        //Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        //intent.setAction(Constants.ACTION_IDENTIFY);
        //intent.putExtra(Constants.EXTRA_USER_ID, "");
        //checkIrisPermissionsAndStart(intent);
    }

    private void checkIrisPermissionsAndStart(Intent intent) {
        boolean hasWrite  = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
//            LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_IDENTIFY_LOGIN);
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

    // ==================== DEEP LINK ====================

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleDeepLink(intent);
    }

    private void handleDeepLink(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            android.net.Uri uri = intent.getData();
            if (uri != null && "irissample".equals(uri.getScheme())
                    && "login".equals(uri.getHost())) {
                Toast.makeText(this,
                        "Chào mừng bạn quay lại! Vui lòng đăng nhập để tiếp tục",
                        Toast.LENGTH_LONG).show();
                String emailParam = uri.getQueryParameter("email");
                if (emailParam != null && editTextEmail != null) {
                    editTextEmail.setText(emailParam);
                    editTextPassword.requestFocus();
                }
            }
        }
    }

    // ==================== QUYỀN ====================

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_IRIS_SDK_PERMISSION) return; // fire-and-forget

        if (requestCode == REQUEST_ALL_PERMISSIONS) {
            int granted = 0;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) granted++;
            android.util.Log.d("LoginActivity",
                    "Critical permissions: " + granted + "/" + grantResults.length + " granted");

            if (!dbHelper.isSuperAdminExists()) {
                if (checkStoragePermission()) {
                    if (checkForBackupOnStartup()) return;
                    startActivity(new Intent(this, RegisterAdminActivity.class));
                    finish();
                } else {
                    Toast.makeText(this,
                            "Không có quyền truy cập storage.", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, RegisterAdminActivity.class));
                    finish();
                }
            }
            return;
        }

        if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int r : grantResults)
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted && pendingIrisIntent != null) {
               // LicenseCheckHelper.checkLicenseAndStartCapture(
               //         this, pendingIrisIntent, REQUEST_CODE_IDENTIFY_LOGIN);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để sử dụng đăng nhập bằng mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (!dbHelper.isSuperAdminExists()) {
                    if (checkForBackupOnStartup()) return;
                    startActivity(new Intent(this, RegisterAdminActivity.class));
                    finish();
                }
            } else {
                Toast.makeText(this,
                        "Không có quyền truy cập storage.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, RegisterAdminActivity.class));
                finish();
            }
        }
    }

    // ==================== BACKUP / RESTORE ====================

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean checkForBackupOnStartup() {
        android.util.Log.d("LoginActivity", "checkForBackupOnStartup()");
        BackupRestoreHelper backupHelper = new BackupRestoreHelper(this);
        if (!dbHelper.isSuperAdminExists()) {
            java.io.File latestBackup = backupHelper.findLatestBackup();
            if (latestBackup != null) {
                android.util.Log.d("LoginActivity", "Found backup: " + latestBackup.getAbsolutePath());
                showRestoreBackupDialog(latestBackup, backupHelper);
                return true;
            }
        }
        return false;
    }

    private void showRestoreBackupDialog(final java.io.File backupFile,
                                         final BackupRestoreHelper backupHelper) {
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(backupFile);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Phát hiện dữ liệu cũ")
                .setMessage("Tìm thấy file backup:\n\n"
                        + "Tên: " + info.fileName + "\n"
                        + "Kích thước: " + info.getFormattedSize() + "\n"
                        + "Thời gian: " + info.getFormattedDate() + "\n\n"
                        + "Bạn có muốn khôi phục dữ liệu này không?")
                .setCancelable(false)
                .setPositiveButton("Khôi phục",
                        (d, w) -> showRestorePasswordDialog(backupFile, backupHelper))
                .setNegativeButton("Bỏ qua", (d, w) -> {
                    d.dismiss();
                    startActivity(new Intent(this, RegisterAdminActivity.class));
                    finish();
                })
                .show();
    }

    private void showRestorePasswordDialog(final java.io.File backupFile,
                                           final BackupRestoreHelper backupHelper) {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Mật khẩu");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Nhập mật khẩu")
                .setMessage("Nhập mật khẩu Super Admin đã dùng để mã hóa file backup:")
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String pwd = input.getText().toString().trim();
                    if (pwd.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                        showRestorePasswordDialog(backupFile, backupHelper);
                        return;
                    }
                    performRestoreOnStartup(backupFile, pwd, backupHelper);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performRestoreOnStartup(final java.io.File backupFile,
                                         final String password,
                                         final BackupRestoreHelper backupHelper) {
        android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(this);
        progressDialog.setMessage("Đang khôi phục dữ liệu...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        new Thread(() -> {
            boolean success = backupHelper.restoreDatabase(backupFile, password);
            runOnUiThread(() -> {
                progressDialog.dismiss();
                if (success) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Khôi phục thành công!")
                            .setMessage("App sẽ khởi động lại để áp dụng thay đổi.")
                            .setCancelable(false)
                            .setPositiveButton("Khởi động lại", (d, w) -> restartApp())
                            .show();
                } else {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Khôi phục thất bại")
                            .setMessage("Sai mật khẩu hoặc file backup bị hỏng.")
                            .setPositiveButton("Thử lại",
                                    (d, w) -> showRestorePasswordDialog(backupFile, backupHelper))
                            .setNegativeButton("Bỏ qua", null)
                            .show();
                }
            });
        }).start();
    }

    private void restartApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        System.exit(0);
    }

    // ==================== HELPERS ====================

    /**
     * Parse email từ result string của mống mắt SDK.
     * Format: "ID: admin_at_example_com, distance: 0.123; ..."
     */
    private String extractEmailFromResult(String resultItems) {
        try {
            if (resultItems.contains("ID: ")) {
                int start = resultItems.indexOf("ID: ") + 4;
                int end   = resultItems.indexOf(", distance:", start);
                if (end > start) {
                    String safeId = resultItems.substring(start, end).trim();
                    String email  = safeId.replace("_at_", "@");
                    int atIndex   = email.indexOf("@");
                    if (atIndex != -1) {
                        String domain = email.substring(atIndex + 1).replace("_", ".");
                        email = email.substring(0, atIndex + 1) + domain;
                    }
                    return email;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void initIrisFolders() {
        String base = Environment.getExternalStorageDirectory()
                + File.separator + "iritech";
        new File(base + File.separator + "enroll").mkdirs();
        new File(base + File.separator + "verify").mkdirs();
    }

    private void requestCriticalPermissions() {
        List<String> needed = new ArrayList<>();
        String[] required = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.ACCESS_NETWORK_STATE
        };
        for (String p : required) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED)
                needed.add(p);
        }
        if (!needed.isEmpty()) {
            android.util.Log.d("LoginActivity", "Requesting " + needed.size() + " permissions");
            ActivityCompat.requestPermissions(this,
                    needed.toArray(new String[0]), REQUEST_ALL_PERMISSIONS);
        }
    }
}
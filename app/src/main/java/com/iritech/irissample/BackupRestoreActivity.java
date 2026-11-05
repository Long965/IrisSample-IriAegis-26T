package com.iritech.irissample;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;

/**
 * Activity quản lý Backup và Restore Database
 * CHỈ Super Admin mới có quyền truy cập activity này
 * 
 * Chức năng:
 * - Backup database ra file mã hóa
 * - Restore database từ file backup
 * - Xem thông tin backup
 * - Xóa backup cũ
 */
public class BackupRestoreActivity extends AppCompatActivity {
    
    private static final int REQUEST_STORAGE_PERMISSION = 200;
    
    private Button btnBackup;
    private Button btnRestore;
    private Button btnViewBackups;
    private Button btnDeleteBackups;
    private TextView textViewBackupInfo;
    private TextView textViewStatus;
    
    private BackupRestoreHelper backupHelper;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup_restore);
        
        // Khởi tạo helpers
        backupHelper = new BackupRestoreHelper(this);
        dbHelper = new DatabaseHelper(this);
        
        // Lấy email user hiện tại
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        
        // Kiểm tra quyền Super Admin
        if (!isSuperAdmin()) {
            Toast.makeText(this, "Chỉ Super Admin mới có quyền truy cập!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
        initializeViews();
        setupListeners();
        checkStoragePermission();
        updateBackupInfo();
    }
    
    private void initializeViews() {
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnViewBackups = findViewById(R.id.btnViewBackups);
        btnDeleteBackups = findViewById(R.id.btnDeleteBackups);
        textViewBackupInfo = findViewById(R.id.textViewBackupInfo);
        textViewStatus = findViewById(R.id.textViewStatus);
    }
    
    private void setupListeners() {
        btnBackup.setOnClickListener(v -> showBackupDialog());
        btnRestore.setOnClickListener(v -> showRestoreDialog());
        btnViewBackups.setOnClickListener(v -> showBackupsList());
        btnDeleteBackups.setOnClickListener(v -> confirmDeleteAllBackups());
    }
    
    /**
     * Kiểm tra user có phải Super Admin không
     */
    private boolean isSuperAdmin() {
        if (currentUserEmail == null) {
            return false;
        }
        Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
        if (cursor != null && cursor.moveToFirst()) {
            String role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ROLE));
            cursor.close();
            return "SUPER_ADMIN".equals(role);
        }
        return false;
    }
    
    /**
     * Kiểm tra quyền truy cập storage
     */
    private void checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_STORAGE_PERMISSION);
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                          @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Quyền truy cập storage đã được cấp", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Cần quyền truy cập storage để backup/restore",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    /**
     * Hiển thị dialog để backup database
     */
    private void showBackupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Backup Database");
        builder.setMessage("Nhập mật khẩu Super Admin để mã hóa file backup.\n\n" +
                          "LƯU Ý: Hãy nhớ mật khẩu này! Bạn sẽ cần nó để restore sau này.");
        
        // Tạo input field cho password
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Mật khẩu Super Admin");
        builder.setView(input);
        
        builder.setPositiveButton("Backup", (dialog, which) -> {
            String password = input.getText().toString().trim();
            
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Verify password trước khi backup
            if (!dbHelper.verifyAdminPassword(currentUserEmail, password)) {
                Toast.makeText(this, "Mật khẩu không đúng!", Toast.LENGTH_SHORT).show();
                return;
            }
            
            performBackup(password);
        });
        
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
    
    /**
     * Thực hiện backup database
     */
    private void performBackup(String password) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang backup database...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Chạy backup trong background thread
        new Thread(() -> {
            File backupFile = backupHelper.backupDatabase(password);
            
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (backupFile != null) {
                    BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(backupFile);
                    
                    new AlertDialog.Builder(this)
                        .setTitle("Backup thành công!")
                        .setMessage("File backup đã được lưu tại:\n\n" +
                                   info.filePath + "\n\n" +
                                   "Kích thước: " + info.getFormattedSize() + "\n" +
                                   "Thời gian: " + info.getFormattedDate() + "\n\n" +
                                   "Hãy nhớ mật khẩu bạn vừa nhập để restore sau này!")
                        .setPositiveButton("OK", null)
                        .show();
                    
                    updateBackupInfo();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Backup thất bại")
                        .setMessage("Không thể tạo file backup. Vui lòng kiểm tra:\n" +
                                   "- Quyền truy cập storage\n" +
                                   "- Dung lượng đĩa còn trống")
                        .setPositiveButton("OK", null)
                        .show();
                }
            });
        }).start();
    }
    
    /**
     * Hiển thị dialog để restore database
     */
    private void showRestoreDialog() {
        // Tìm file backup
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup == null) {
            new AlertDialog.Builder(this)
                .setTitle("Không tìm thấy backup")
                .setMessage("Không có file backup nào trong hệ thống.\n\n" +
                           "Vui lòng tạo backup trước hoặc đảm bảo file backup có trong:\n" +
                           "Documents/IrisSample/")
                .setPositiveButton("OK", null)
                .show();
            return;
        }
        
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
        
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Restore Database");
        builder.setMessage("Tìm thấy file backup:\n\n" +
                          "Tên: " + info.fileName + "\n" +
                          "Kích thước: " + info.getFormattedSize() + "\n" +
                          "Thời gian: " + info.getFormattedDate() + "\n\n" +
                          "CẢNH BÁO: Restore sẽ XÓA TOÀN BỘ dữ liệu hiện tại!\n\n" +
                          "Nhập mật khẩu Super Admin đã dùng để mã hóa file này:");
        
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("Mật khẩu");
        builder.setView(input);
        
        builder.setPositiveButton("Restore", (dialog, which) -> {
            String password = input.getText().toString().trim();
            
            if (password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập mật khẩu", Toast.LENGTH_SHORT).show();
                return;
            }
            
            performRestore(latestBackup, password);
        });
        
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
    
    /**
     * Thực hiện restore database
     */
    private void performRestore(File backupFile, String password) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Đang restore database...");
        progressDialog.setCancelable(false);
        progressDialog.show();
        
        // Chạy restore trong background thread
        new Thread(() -> {
            boolean success = backupHelper.restoreDatabase(backupFile, password);
            
            runOnUiThread(() -> {
                progressDialog.dismiss();
                
                if (success) {
                    new AlertDialog.Builder(this)
                        .setTitle("Restore thành công!")
                        .setMessage("Database đã được khôi phục.\n\n" +
                                   "App sẽ khởi động lại để áp dụng thay đổi.")
                        .setCancelable(false)
                        .setPositiveButton("Khởi động lại", (dialog, which) -> {
                            // Restart app
                            restartApp();
                        })
                        .show();
                } else {
                    new AlertDialog.Builder(this)
                        .setTitle("Restore thất bại")
                        .setMessage("Không thể restore database.\n\n" +
                                   "Nguyên nhân có thể:\n" +
                                   "- Sai mật khẩu\n" +
                                   "- File backup bị hỏng\n" +
                                   "- File không phải do app này tạo ra")
                        .setPositiveButton("Thử lại", (dialog, which) -> showRestoreDialog())
                        .setNegativeButton("Hủy", null)
                        .show();
                }
            });
        }).start();
    }
    
    /**
     * Hiển thị danh sách các file backup
     */
    private void showBackupsList() {
        // TODO: Implement list view for all backups
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup == null) {
            Toast.makeText(this, "Không có file backup nào", Toast.LENGTH_SHORT).show();
            return;
        }
        
        BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
        
        new AlertDialog.Builder(this)
            .setTitle("File Backup gần nhất")
            .setMessage("Tên: " + info.fileName + "\n" +
                       "Đường dẫn: " + info.filePath + "\n" +
                       "Kích thước: " + info.getFormattedSize() + "\n" +
                       "Thời gian: " + info.getFormattedDate())
            .setPositiveButton("OK", null)
            .show();
    }
    
    /**
     * Xác nhận xóa tất cả backup
     */
    private void confirmDeleteAllBackups() {
        new AlertDialog.Builder(this)
            .setTitle("Xóa tất cả backup")
            .setMessage("Bạn có chắc muốn xóa TẤT CẢ file backup?\n\n" +
                       "Hành động này KHÔNG THỂ HOÀN TÁC!")
            .setPositiveButton("Xóa", (dialog, which) -> deleteAllBackups())
            .setNegativeButton("Hủy", null)
            .show();
    }
    
    /**
     * Xóa tất cả file backup
     */
    private void deleteAllBackups() {
        int deletedCount = backupHelper.deleteAllBackups();
        
        if (deletedCount > 0) {
            Toast.makeText(this, "Đã xóa " + deletedCount + " file backup", Toast.LENGTH_SHORT).show();
            updateBackupInfo();
        } else {
            Toast.makeText(this, "Không có file backup nào để xóa", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Cập nhật thông tin backup hiện tại
     */
    private void updateBackupInfo() {
        File latestBackup = backupHelper.findLatestBackup();
        
        if (latestBackup != null) {
            BackupRestoreHelper.BackupInfo info = backupHelper.getBackupInfo(latestBackup);
            textViewBackupInfo.setText("File backup gần nhất:\n\n" +
                                      "Tên: " + info.fileName + "\n" +
                                      "Kích thước: " + info.getFormattedSize() + "\n" +
                                      "Thời gian: " + info.getFormattedDate());
            textViewBackupInfo.setVisibility(View.VISIBLE);
            
            btnRestore.setEnabled(true);
            btnDeleteBackups.setEnabled(true);
        } else {
            textViewBackupInfo.setText("Chưa có file backup nào");
            textViewBackupInfo.setVisibility(View.VISIBLE);
            
            btnRestore.setEnabled(false);
            btnDeleteBackups.setEnabled(false);
        }
    }
    
    /**
     * Restart app
     */
    private void restartApp() {
        android.content.Intent intent = getPackageManager()
            .getLaunchIntentForPackage(getPackageName());
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
        System.exit(0);
    }
}

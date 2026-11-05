package com.iritech.irissample;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Helper class quản lý Backup và Restore database
 * Tích hợp với EncryptionHelper để mã hóa/giải mã an toàn
 * 
 * Flow Backup:
 * 1. Super Admin bấm "Backup Database"
 * 2. Nhập password để mã hóa
 * 3. File database được mã hóa và lưu ra bộ nhớ ngoài
 * 4. File backup: /Documents/IrisSample/backup_YYYYMMDD_HHMMSS.enc
 * 
 * Flow Restore:
 * 1. App khởi động → Quét thư mục backup
 * 2. Nếu tìm thấy file backup → Hiện dialog "Phát hiện backup cũ"
 * 3. Nhập password Super Admin để giải mã
 * 4. Nếu đúng password → Restore database → Restart app
 * 5. Nếu sai password → Báo lỗi, cho phép thử lại hoặc bỏ qua
 */
public class BackupRestoreHelper {
    
    private static final String TAG = "BackupRestoreHelper";
    
    // Tên thư mục lưu backup trong bộ nhớ ngoài
    private static final String BACKUP_FOLDER_NAME = "IrisSample";
    private static final String BACKUP_FILE_PREFIX = "backup_";
    private static final String BACKUP_FILE_EXTENSION = ".enc";
    
    // Tên database file (phải khớp với DatabaseHelper)
    private static final String DATABASE_NAME = "attendance.db";
    
    // Tên thư mục avatar trong file ZIP backup
    private static final String ZIP_AVATAR_FOLDER = "avatars";
    
    private Context context;
    private DatabaseHelper dbHelper;
    
    public BackupRestoreHelper(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new DatabaseHelper(context);
    }
    
    /**
     * Backup database + avatar images ra file được mã hóa
     * Format mới: ZIP(DB + avatars) → Encrypt → .enc
     * Tương thích ngược: restore vẫn hỗ trợ format cũ (raw DB)
     * 
     * @param password Mật khẩu Super Admin để mã hóa
     * @return File backup đã tạo, hoặc null nếu thất bại
     */
    public File backupDatabase(String password) {
        try {
            // 1. Lấy danh sách avatar files TRƯỚC KHI đóng DB
            List<File> avatarFiles = getAvatarFiles();
            
            // 2. Đóng database trước khi backup (để tránh file bị lock)
            dbHelper.close();
            
            // 3. Lấy đường dẫn file database hiện tại
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found: " + dbFile.getAbsolutePath());
                return null;
            }
            
            // 4. Tạo thư mục backup nếu chưa có
            File backupFolder = getBackupFolder();
            if (!backupFolder.exists()) {
                if (!backupFolder.mkdirs()) {
                    Log.e(TAG, "Failed to create backup folder");
                    return null;
                }
            }
            
            // 5. Tạo tên file backup với timestamp
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            String backupFileName = BACKUP_FILE_PREFIX + timestamp + BACKUP_FILE_EXTENSION;
            File backupFile = new File(backupFolder, backupFileName);
            
            // 6. Tạo temp ZIP chứa DB + avatar images
            File tempZip = new File(context.getCacheDir(), "temp_backup.zip");
            createBackupZip(dbFile, avatarFiles, tempZip);
            
            // 7. Mã hóa ZIP file
            Log.d(TAG, "Starting backup encryption...");
            Log.d(TAG, "Source ZIP: " + tempZip.getAbsolutePath() + " (" + tempZip.length() + " bytes)");
            Log.d(TAG, "Destination: " + backupFile.getAbsolutePath());
            Log.d(TAG, "Avatar files included: " + avatarFiles.size());
            
            boolean success = EncryptionHelper.encryptFile(tempZip, backupFile, password);
            
            // 8. Xóa temp ZIP
            tempZip.delete();
            
            if (success) {
                Log.d(TAG, "Backup completed successfully");
                Log.d(TAG, "Backup file size: " + backupFile.length() + " bytes");
                return backupFile;
            } else {
                Log.e(TAG, "Backup encryption failed");
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Backup failed", e);
            return null;
        }
    }
    
    /**
     * Restore database + avatar images từ file backup được mã hóa
     * Hỗ trợ cả format mới (ZIP chứa DB + avatars) và format cũ (raw DB)
     * 
     * @param backupFile File backup cần restore
     * @param password Mật khẩu Super Admin để giải mã
     * @return true nếu restore thành công, false nếu thất bại (sai password)
     */
    public boolean restoreDatabase(File backupFile, String password) {
        try {
            // 1. Kiểm tra file backup có tồn tại không
            if (!backupFile.exists()) {
                Log.e(TAG, "Backup file not found: " + backupFile.getAbsolutePath());
                return false;
            }
            
            // 2. Đóng database hiện tại
            dbHelper.close();
            
            // 3. Lấy đường dẫn file database
            File dbFile = context.getDatabasePath(DATABASE_NAME);
            
            // 4. Tạo file tạm để giải mã trước
            File tempFile = new File(context.getCacheDir(), "temp_restore.dat");
            
            // 5. Giải mã file backup
            Log.d(TAG, "Starting restore decryption...");
            Log.d(TAG, "Source: " + backupFile.getAbsolutePath());
            Log.d(TAG, "Temp destination: " + tempFile.getAbsolutePath());
            
            boolean success = EncryptionHelper.decryptFile(backupFile, tempFile, password);
            
            if (!success) {
                Log.e(TAG, "Restore decryption failed - Wrong password or corrupted file");
                tempFile.delete();
                return false;
            }
            
            // 6. Kiểm tra format: ZIP (DB + avatars) hay raw DB (backup cũ)
            if (isZipFile(tempFile)) {
                Log.d(TAG, "Detected ZIP format backup (DB + avatars)");
                restoreFromZip(tempFile, dbFile);
            } else {
                Log.d(TAG, "Detected legacy format backup (DB only)");
                if (dbFile.exists()) {
                    dbFile.delete();
                }
                if (!tempFile.renameTo(dbFile)) {
                    copyFile(tempFile, dbFile);
                }
            }
            
            // 7. Xóa file tạm
            tempFile.delete();
            
            Log.d(TAG, "Restore completed successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Restore failed", e);
            return false;
        }
    }
    
    /**
     * Quét và tìm file backup mới nhất trong thư mục
     * 
     * @return File backup mới nhất, hoặc null nếu không tìm thấy
     */
    public File findLatestBackup() {
        File backupFolder = getBackupFolder();
        
        Log.d(TAG, "findLatestBackup() - Scanning folder: " + backupFolder.getAbsolutePath());
        
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            Log.w(TAG, "Backup folder does not exist or is not a directory");
            return null;
        }
        
        File[] backupFiles = backupFolder.listFiles((dir, name) -> 
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_EXTENSION)
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            Log.w(TAG, "No backup files found in folder");
            // List tất cả files trong thư mục để debug
            File[] allFiles = backupFolder.listFiles();
            if (allFiles != null && allFiles.length > 0) {
                Log.d(TAG, "Found " + allFiles.length + " files in folder:");
                for (File file : allFiles) {
                    Log.d(TAG, "  - " + file.getName());
                }
            } else {
                Log.d(TAG, "Folder is empty or cannot list files");
            }
            return null;
        }
        
        Log.d(TAG, "Found " + backupFiles.length + " backup file(s)");
        
        // Tìm file mới nhất (theo thời gian sửa đổi)
        File latestBackup = backupFiles[0];
        for (File file : backupFiles) {
            Log.d(TAG, "  - " + file.getName() + " (" + file.lastModified() + ")");
            if (file.lastModified() > latestBackup.lastModified()) {
                latestBackup = file;
            }
        }
        
        Log.d(TAG, "Latest backup: " + latestBackup.getName());
        return latestBackup;
    }
    
    /**
     * Xóa tất cả file backup cũ
     * Dùng sau khi restore thành công hoặc khi user muốn xóa backup
     * 
     * @return Số file đã xóa
     */
    public int deleteAllBackups() {
        File backupFolder = getBackupFolder();
        
        if (!backupFolder.exists() || !backupFolder.isDirectory()) {
            return 0;
        }
        
        File[] backupFiles = backupFolder.listFiles((dir, name) -> 
            name.startsWith(BACKUP_FILE_PREFIX) && name.endsWith(BACKUP_FILE_EXTENSION)
        );
        
        if (backupFiles == null || backupFiles.length == 0) {
            return 0;
        }
        
        int deletedCount = 0;
        for (File file : backupFiles) {
            if (file.delete()) {
                deletedCount++;
                Log.d(TAG, "Deleted backup: " + file.getName());
            }
        }
        
        return deletedCount;
    }
    
    /**
     * Xóa một file backup cụ thể
     */
    public boolean deleteBackup(File backupFile) {
        if (backupFile != null && backupFile.exists()) {
            return backupFile.delete();
        }
        return false;
    }
    
    /**
     * Lấy thông tin về file backup
     */
    public BackupInfo getBackupInfo(File backupFile) {
        if (backupFile == null || !backupFile.exists()) {
            return null;
        }
        
        BackupInfo info = new BackupInfo();
        info.fileName = backupFile.getName();
        info.filePath = backupFile.getAbsolutePath();
        info.fileSize = backupFile.length();
        info.lastModified = backupFile.lastModified();
        
        // Parse timestamp từ tên file
        // Format: backup_YYYYMMDD_HHMMSS.enc
        String fileName = backupFile.getName();
        if (fileName.startsWith(BACKUP_FILE_PREFIX)) {
            String timestamp = fileName.substring(BACKUP_FILE_PREFIX.length(), fileName.length() - BACKUP_FILE_EXTENSION.length());
            info.timestamp = timestamp;
        }
        
        return info;
    }
    
    /**
     * Lấy thư mục backup (Documents/IrisSample)
     * Thư mục này không bị xóa khi uninstall app
     */
    private File getBackupFolder() {
        // Android 10+ (API 29+): Dùng getExternalFilesDir() thay vì Environment.getExternalStorageDirectory()
        // Hoặc dùng Documents folder qua MediaStore
        
        // Cách 1: Lưu trong thư mục app-specific (bị xóa khi uninstall)
        // File backupFolder = new File(context.getExternalFilesDir(null), BACKUP_FOLDER_NAME);
        
        // Cách 2: Lưu trong Documents (không bị xóa khi uninstall) - KHUYẾN NGHỊ
        File documentsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File backupFolder = new File(documentsFolder, BACKUP_FOLDER_NAME);
        
        Log.d(TAG, "Backup folder path: " + backupFolder.getAbsolutePath());
        Log.d(TAG, "Backup folder exists: " + backupFolder.exists());
        
        return backupFolder;
    }
    
    // ============ AVATAR BACKUP HELPER METHODS ============
    
    /**
     * Lấy danh sách file avatar từ DB (phải gọi TRƯỚC KHI đóng DB)
     */
    private List<File> getAvatarFiles() {
        List<File> files = new ArrayList<>();
        try {
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_ADMIN_PHOTO +
                " FROM " + DatabaseHelper.TABLE_ADMIN +
                " WHERE " + DatabaseHelper.COL_ADMIN_PHOTO + " IS NOT NULL" +
                " AND " + DatabaseHelper.COL_ADMIN_PHOTO + " != ''", null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String path = cursor.getString(0);
                    if (path != null && !path.isEmpty()) {
                        File file = new File(path);
                        if (file.exists()) {
                            files.add(file);
                            Log.d(TAG, "Avatar found: " + file.getName());
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting avatar files", e);
        }
        return files;
    }
    
    /**
     * Tạo file ZIP chứa database + avatar images
     */
    private void createBackupZip(File dbFile, List<File> avatarFiles, File outputZip) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip));
        
        // Add database file
        addFileToZip(zos, dbFile, DATABASE_NAME);
        
        // Add avatar files
        for (File avatar : avatarFiles) {
            addFileToZip(zos, avatar, ZIP_AVATAR_FOLDER + "/" + avatar.getName());
        }
        
        zos.close();
        Log.d(TAG, "Backup ZIP created: " + outputZip.length() + " bytes, " +
              (1 + avatarFiles.size()) + " files");
    }
    
    /**
     * Thêm một file vào ZIP
     */
    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = fis.read(buffer)) > 0) {
            zos.write(buffer, 0, len);
        }
        fis.close();
        zos.closeEntry();
    }
    
    /**
     * Kiểm tra file có phải ZIP không (dựa vào magic bytes PK)
     */
    private boolean isZipFile(File file) {
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] magic = new byte[2];
            int read = fis.read(magic);
            fis.close();
            return read == 2 && magic[0] == 0x50 && magic[1] == 0x4B;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Restore từ file ZIP chứa DB + avatars
     */
    private void restoreFromZip(File zipFile, File dbFile) throws IOException {
        ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile));
        ZipEntry entry;
        
        File avatarDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (avatarDir != null && !avatarDir.exists()) {
            avatarDir.mkdirs();
        }
        
        while ((entry = zis.getNextEntry()) != null) {
            String entryName = entry.getName();
            
            // Bảo mật: chống path traversal attack
            if (entryName.contains("..")) {
                Log.w(TAG, "Skipping suspicious ZIP entry: " + entryName);
                zis.closeEntry();
                continue;
            }
            
            if (entryName.equals(DATABASE_NAME)) {
                // Restore database
                if (dbFile.exists()) {
                    dbFile.delete();
                }
                writeStreamToFile(zis, dbFile);
                Log.d(TAG, "Restored database file");
                
            } else if (entryName.startsWith(ZIP_AVATAR_FOLDER + "/") && !entry.isDirectory()) {
                // Restore avatar image
                String fileName = entryName.substring((ZIP_AVATAR_FOLDER + "/").length());
                if (!fileName.isEmpty() && !fileName.contains("/") && avatarDir != null) {
                    File avatarFile = new File(avatarDir, fileName);
                    writeStreamToFile(zis, avatarFile);
                    Log.d(TAG, "Restored avatar: " + fileName);
                }
            }
            zis.closeEntry();
        }
        zis.close();
        
        // Cập nhật photo_path trong DB cho đúng đường dẫn thiết bị hiện tại
        if (avatarDir != null) {
            updatePhotoPathsAfterRestore(dbFile, avatarDir);
        }
    }
    
    /**
     * Ghi dữ liệu từ ZipInputStream vào file
     */
    private void writeStreamToFile(ZipInputStream zis, File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buffer = new byte[8192];
        int len;
        while ((len = zis.read(buffer)) > 0) {
            fos.write(buffer, 0, len);
        }
        fos.close();
    }
    
    /**
     * Cập nhật photo_path sau khi restore để phù hợp với thiết bị hiện tại
     * (trường hợp restore sang thiết bị khác hoặc đường dẫn thay đổi)
     */
    private void updatePhotoPathsAfterRestore(File dbFile, File avatarDir) {
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(
                dbFile.getAbsolutePath(), null, SQLiteDatabase.OPEN_READWRITE);
            
            Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_ADMIN_ID + ", " + DatabaseHelper.COL_ADMIN_PHOTO +
                " FROM " + DatabaseHelper.TABLE_ADMIN +
                " WHERE " + DatabaseHelper.COL_ADMIN_PHOTO + " IS NOT NULL" +
                " AND " + DatabaseHelper.COL_ADMIN_PHOTO + " != ''", null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String adminId = cursor.getString(0);
                    String oldPath = cursor.getString(1);
                    String fileName = new File(oldPath).getName();
                    File avatarFile = new File(avatarDir, fileName);
                    
                    if (avatarFile.exists()) {
                        String newPath = avatarFile.getAbsolutePath();
                        if (!newPath.equals(oldPath)) {
                            ContentValues values = new ContentValues();
                            values.put(DatabaseHelper.COL_ADMIN_PHOTO, newPath);
                            db.update(DatabaseHelper.TABLE_ADMIN, values,
                                DatabaseHelper.COL_ADMIN_ID + " = ?", new String[]{adminId});
                            Log.d(TAG, "Updated photo path: " + oldPath + " -> " + newPath);
                        }
                    }
                }
                cursor.close();
            }
            
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error updating photo paths after restore", e);
        }
    }
    
    /**
     * Copy file thủ công (fallback nếu renameTo() thất bại)
     */
    private void copyFile(File source, File dest) throws Exception {
        FileInputStream fis = new FileInputStream(source);
        FileOutputStream fos = new FileOutputStream(dest);
        
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        
        fis.close();
        fos.close();
    }
    
    /**
     * Kiểm tra database hiện tại có dữ liệu không
     * Dùng để quyết định có nên hiện dialog restore không
     */
    public boolean isDatabaseEmpty() {
        return !dbHelper.isSuperAdminExists();
    }
    
    /**
     * Class chứa thông tin về file backup
     */
    public static class BackupInfo {
        public String fileName;
        public String filePath;
        public long fileSize;
        public long lastModified;
        public String timestamp;
        
        public String getFormattedSize() {
            if (fileSize < 1024) {
                return fileSize + " B";
            } else if (fileSize < 1024 * 1024) {
                return String.format(Locale.US, "%.2f KB", fileSize / 1024.0);
            } else {
                return String.format(Locale.US, "%.2f MB", fileSize / (1024.0 * 1024.0));
            }
        }
        
        public String getFormattedDate() {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US);
            return sdf.format(new Date(lastModified));
        }
    }
}

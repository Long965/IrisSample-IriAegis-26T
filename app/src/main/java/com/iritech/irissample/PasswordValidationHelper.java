package com.iritech.irissample;

import android.database.sqlite.SQLiteDatabase;

/**
 * Helper class cho chức năng password validation
 * Tách ra từ DatabaseHelper để giảm tải code
 */
public class PasswordValidationHelper {
    
    private DatabaseHelper dbHelper;
    
    public PasswordValidationHelper(DatabaseHelper dbHelper) {
        this.dbHelper = dbHelper;
    }
    
    /**
     * Kiểm tra xem password mới có trùng với password cũ không
     * @param email Email của admin
     * @param newPassword Password mới cần kiểm tra
     * @return true nếu password mới trùng với password cũ (KHÔNG được phép)
     */
    public boolean isSameAsOldPassword(String email, String newPassword) {
        return dbHelper.verifyAdminPassword(email, newPassword);
    }
}

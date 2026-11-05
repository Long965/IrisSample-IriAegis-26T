package com.iritech.irissample;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "attendance.db";
    private static final int DATABASE_VERSION = 20; // Version 20: Thêm semester_start_date/semester_end_date cho subjects

    // Bảng ADMIN
    public static final String TABLE_ADMIN = "admin";
    public static final String COL_ADMIN_ID = "admin_id";
    public static final String COL_ADMIN_EMAIL = "email";
    public static final String COL_ADMIN_PASSWORD = "password";
    public static final String COL_ADMIN_FULL_NAME = "full_name";
    public static final String COL_ADMIN_DOB = "date_of_birth";
    public static final String COL_ADMIN_PHONE = "phone";
    public static final String COL_ADMIN_GENDER = "gender";
    public static final String COL_ADMIN_PHOTO = "photo_path";
    public static final String COL_ADMIN_DESC = "description";
    public static final String COL_ADMIN_ROLE = "role";
    public static final String COL_ADMIN_CODE = "admin_code"; // Mã số giảng viên (chỉ Admin thường)
    public static final String COL_ADMIN_RESET_TOKEN = "reset_token"; // Token reset password
    public static final String COL_ADMIN_EXPIRE_AT = "expire_at"; // Thời gian hết hạn token (milliseconds)
    public static final String COL_ADMIN_IS_FIRST_LOGIN = "is_first_login"; // Cờ đánh dấu lần đăng nhập đầu (cần cập nhật avatar/password)
    public static final String COL_ADMIN_HAS_IRIS = "has_iris"; // Đánh dấu đã ghi danh mống mắt chưa (0 = chưa, 1 = rồi)

    // Bảng SUBJECTS
    public static final String TABLE_SUBJECTS = "subjects";
    public static final String COL_SUBJECT_ID = "subject_id";
    public static final String COL_SUBJECT_NAME = "subject_name";
    public static final String COL_TIME_SLOT = "time_slot";
    public static final String COL_SUBJECT_CREATED_BY = "created_by";
    public static final String COL_SUBJECT_INSTRUCTOR_ID = "instructor_id";
    public static final String COL_SUBJECT_STATUS = "subject_status";
    public static final String COL_SUBJECT_CREATED_AT = "created_at";
    public static final String COL_SUBJECT_SEMESTER_START = "semester_start_date"; // Ngày bắt đầu kì (dd/MM/yyyy)
    public static final String COL_SUBJECT_SEMESTER_END = "semester_end_date";     // Ngày kết thúc kì = bắt đầu + 90 ngày (dd/MM/yyyy)

    public static final String STATUS_UNASSIGNED = "UNASSIGNED";
    public static final String STATUS_ASSIGNED = "ASSIGNED";

    // Bảng STUDENTS
    public static final String TABLE_STUDENTS = "students";
    public static final String COL_STUDENT_ID = "student_id";
    public static final String COL_FULL_NAME = "full_name";
    public static final String COL_PHONE = "phone";
    public static final String COL_EMAIL = "email";
    public static final String COL_PHOTO_PATH = "photo_path";
    public static final String COL_CHECKIN_PASSWORD = "checkin_password";

    // Bảng ENROLLMENTS
    public static final String TABLE_ENROLLMENTS = "enrollments";
    public static final String COL_ENROLLMENT_STATUS = "enrollment_status";

    // Bảng CHECKIN_HISTORY
    public static final String TABLE_CHECKIN_HISTORY = "checkin_history";
    public static final String COL_CHECKIN_ID = "checkin_id";
    public static final String COL_CHECKIN_TIME = "checkin_time";
    public static final String COL_CHECKIN_DATE = "checkin_date"; // Ngày điểm danh để biết điểm danh ngày nào

    // Bảng EMAIL_RECIPIENTS - Lưu danh sách email nhận báo cáo điểm danh (per-subject)
    public static final String TABLE_EMAIL_RECIPIENTS = "email_recipients";
    public static final String COL_EMAIL_ID = "email_id";
    public static final String COL_EMAIL_ADDRESS = "email_address";
    public static final String COL_RECIPIENT_NAME = "recipient_name";
    public static final String COL_ADDED_DATE = "added_date";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // TẠO BẢNG ADMIN - Có phân quyền và thông tin đầy đủ
        String CREATE_ADMIN_TABLE = "CREATE TABLE " + TABLE_ADMIN + " (" +
                COL_ADMIN_ID + " TEXT PRIMARY KEY, " +
                COL_ADMIN_EMAIL + " TEXT UNIQUE NOT NULL, " +
                COL_ADMIN_PASSWORD + " TEXT NOT NULL, " +
                COL_ADMIN_FULL_NAME + " TEXT NOT NULL, " +
                COL_ADMIN_DOB + " TEXT, " +
                COL_ADMIN_PHONE + " TEXT, " +
                COL_ADMIN_GENDER + " TEXT, " +
                COL_ADMIN_PHOTO + " TEXT, " +
                COL_ADMIN_DESC + " TEXT, " +
                COL_ADMIN_ROLE + " TEXT NOT NULL DEFAULT 'ADMIN', " +
                COL_ADMIN_CODE + " TEXT, " +
                COL_ADMIN_RESET_TOKEN + " TEXT, " +
                COL_ADMIN_EXPIRE_AT + " INTEGER, " +
                COL_ADMIN_IS_FIRST_LOGIN + " INTEGER DEFAULT 1, " + // 1 = first login, 0 = đã cập nhật
                COL_ADMIN_HAS_IRIS + " INTEGER DEFAULT 0)"; // 0 = chưa ghi danh, 1 = đã ghi danh

        // TẠO BẢNG SUBJECTS - Có instructor_id, subject_status, created_at, semester_start/end
        String CREATE_SUBJECTS_TABLE = "CREATE TABLE " + TABLE_SUBJECTS + " (" +
                COL_SUBJECT_ID + " TEXT PRIMARY KEY, " +
                COL_SUBJECT_NAME + " TEXT NOT NULL, " +
                COL_TIME_SLOT + " TEXT, " +
                COL_SUBJECT_CREATED_BY + " TEXT, " +
                COL_SUBJECT_INSTRUCTOR_ID + " TEXT, " +
                COL_SUBJECT_STATUS + " TEXT NOT NULL DEFAULT 'UNASSIGNED', " +
                COL_SUBJECT_CREATED_AT + " TEXT, " +
                COL_SUBJECT_SEMESTER_START + " TEXT, " +
                COL_SUBJECT_SEMESTER_END + " TEXT, " +
                "FOREIGN KEY(" + COL_SUBJECT_CREATED_BY + ") REFERENCES " +
                TABLE_ADMIN + "(" + COL_ADMIN_ID + ") ON DELETE SET NULL, " +
                "FOREIGN KEY(" + COL_SUBJECT_INSTRUCTOR_ID + ") REFERENCES " +
                TABLE_ADMIN + "(" + COL_ADMIN_ID + ") ON DELETE SET NULL);";

        // TẠO BẢNG STUDENTS
        String CREATE_STUDENTS_TABLE = "CREATE TABLE " + TABLE_STUDENTS + " (" +
                COL_STUDENT_ID + " TEXT PRIMARY KEY, " +
                COL_FULL_NAME + " TEXT, " +
                COL_EMAIL + " TEXT, " +
                COL_PHONE + " TEXT, " +
                COL_PHOTO_PATH + " TEXT, " +
                COL_CHECKIN_PASSWORD + " TEXT);";

        // TẠO BẢNG ENROLLMENTS
        String CREATE_ENROLLMENTS_TABLE = "CREATE TABLE " + TABLE_ENROLLMENTS + " (" +
                COL_STUDENT_ID + " TEXT, " +
                COL_SUBJECT_ID + " TEXT, " +
                COL_ENROLLMENT_STATUS + " TEXT, " +
                "PRIMARY KEY (" + COL_STUDENT_ID + ", " + COL_SUBJECT_ID + "), " +
                "FOREIGN KEY(" + COL_STUDENT_ID + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_STUDENT_ID + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + "));";

        // TẠO BẢNG CHECKIN_HISTORY
        String CREATE_CHECKIN_HISTORY_TABLE = "CREATE TABLE " + TABLE_CHECKIN_HISTORY + " (" +
                COL_CHECKIN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_STUDENT_ID + " TEXT, " +
                COL_SUBJECT_ID + " TEXT, " +
                COL_CHECKIN_TIME + " TEXT, " +
                COL_CHECKIN_DATE + " TEXT, " +
                "FOREIGN KEY(" + COL_STUDENT_ID + ") REFERENCES " + TABLE_STUDENTS + "(" + COL_STUDENT_ID + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + "));";

        // TẠO BẢNG EMAIL_RECIPIENTS
        String CREATE_EMAIL_RECIPIENTS_TABLE = "CREATE TABLE " + TABLE_EMAIL_RECIPIENTS + " (" +
                COL_EMAIL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_SUBJECT_ID + " TEXT NOT NULL, " +
                COL_EMAIL_ADDRESS + " TEXT NOT NULL, " +
                COL_RECIPIENT_NAME + " TEXT, " +
                COL_ADDED_DATE + " TEXT, " +
                "UNIQUE(" + COL_SUBJECT_ID + ", " + COL_EMAIL_ADDRESS + "), " +
                "FOREIGN KEY(" + COL_SUBJECT_ID + ") REFERENCES " + TABLE_SUBJECTS + "(" + COL_SUBJECT_ID + ") ON DELETE CASCADE);";

        db.execSQL(CREATE_ADMIN_TABLE);
        db.execSQL(CREATE_SUBJECTS_TABLE);
        db.execSQL(CREATE_STUDENTS_TABLE);
        db.execSQL(CREATE_ENROLLMENTS_TABLE);
        db.execSQL(CREATE_CHECKIN_HISTORY_TABLE);
        db.execSQL(CREATE_EMAIL_RECIPIENTS_TABLE);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_EMAIL_RECIPIENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHECKIN_HISTORY);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ENROLLMENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_STUDENTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SUBJECTS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ADMIN);
        onCreate(db);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean isSuperAdminExists() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + TABLE_ADMIN +
                " WHERE " + COL_ADMIN_ROLE + " = 'SUPER_ADMIN'";
        Cursor cursor = db.rawQuery(query, null);
        boolean exists = false;
        if (cursor.moveToFirst()) {
            exists = cursor.getInt(0) > 0;
        }
        cursor.close();
        return exists;
    }

    public boolean isEmailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String queryAdmin = "SELECT COUNT(*) FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?";
        Cursor cursorAdmin = db.rawQuery(queryAdmin, new String[]{email});
        boolean existsInAdmin = false;
        if (cursorAdmin.moveToFirst()) {
            existsInAdmin = cursorAdmin.getInt(0) > 0;
        }
        cursorAdmin.close();

        if (existsInAdmin) return true;

        String queryStudent = "SELECT COUNT(*) FROM " + TABLE_STUDENTS + " WHERE " + COL_EMAIL + " = ?";
        Cursor cursorStudent = db.rawQuery(queryStudent, new String[]{email});
        boolean existsInStudent = false;
        if (cursorStudent.moveToFirst()) {
            existsInStudent = cursorStudent.getInt(0) > 0;
        }
        cursorStudent.close();
        return existsInStudent;
    }

    public Cursor authenticateUser(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) return null;

        String queryAdmin = "SELECT * FROM " + TABLE_ADMIN +
                " WHERE " + COL_ADMIN_EMAIL + " = ? AND " + COL_ADMIN_PASSWORD + " = ?";
        Cursor cursorAdmin = db.rawQuery(queryAdmin, new String[]{email, hashedPassword});
        if (cursorAdmin.getCount() > 0) {
            return cursorAdmin;
        }
        cursorAdmin.close();
        return null;
    }

    public boolean verifyAdminPassword(String email, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) return false;

        String queryPassword = "SELECT COUNT(*) FROM " + TABLE_ADMIN +
                " WHERE " + COL_ADMIN_EMAIL + " = ? AND " + COL_ADMIN_PASSWORD + " = ?";
        Cursor cursor = db.rawQuery(queryPassword, new String[]{email, hashedPassword});
        boolean verified = false;
        if (cursor.moveToFirst() && cursor.getInt(0) > 0) {
            verified = true;
        }
        cursor.close();
        return verified;
    }

    public boolean insertSuperAdmin(String email, String password,
                                    String fullName, String dob, String phone,
                                    String gender, String photoPath, String description) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) return false;

        String adminId = "SADM_" + System.currentTimeMillis();
        values.put(COL_ADMIN_ID, adminId);
        values.put(COL_ADMIN_EMAIL, email);
        values.put(COL_ADMIN_PASSWORD, hashedPassword);
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        values.put(COL_ADMIN_PHOTO, photoPath);
        values.put(COL_ADMIN_DESC, description);
        values.put(COL_ADMIN_ROLE, "SUPER_ADMIN");

        long result = db.insert(TABLE_ADMIN, null, values);
        return result != -1;
    }

    public boolean insertAdmin(String email, String password,
                               String fullName, String dob, String phone,
                               String gender, String photoPath, String description,
                               String adminCode) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) return false;

        String adminId = "ADM_" + System.currentTimeMillis();
        values.put(COL_ADMIN_ID, adminId);
        values.put(COL_ADMIN_EMAIL, email);
        values.put(COL_ADMIN_PASSWORD, hashedPassword);
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        values.put(COL_ADMIN_PHOTO, photoPath);
        values.put(COL_ADMIN_DESC, description);
        values.put(COL_ADMIN_ROLE, "ADMIN");
        values.put(COL_ADMIN_CODE, adminCode);

        long result = db.insert(TABLE_ADMIN, null, values);
        return result != -1;
    }

    public boolean deleteAdmin(String adminId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues subjectValues = new ContentValues();
            subjectValues.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            subjectValues.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
            db.update(TABLE_SUBJECTS, subjectValues, COL_SUBJECT_INSTRUCTOR_ID + " = ?", new String[]{adminId});

            int rowsDeleted = db.delete(TABLE_ADMIN, COL_ADMIN_ID + " = ?", new String[]{adminId});
            db.setTransactionSuccessful();
            return rowsDeleted > 0;
        } finally {
            db.endTransaction();
        }
    }

    public Cursor getAllAdmins() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_ROLE + " = 'ADMIN'";
        return db.rawQuery(query, null);
    }

    public Cursor getAllSubjectsWithInstructor() {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
                "FROM " + TABLE_SUBJECTS + " s " +
                "LEFT JOIN " + TABLE_ADMIN + " a ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
                "ORDER BY s." + COL_SUBJECT_STATUS + " DESC, s." + COL_SUBJECT_NAME + " ASC";
        return db.rawQuery(query, null);
    }

    public Cursor getSubjectsByInstructor(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
                "FROM " + TABLE_SUBJECTS + " s " +
                "LEFT JOIN " + TABLE_ADMIN + " a ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
                "WHERE s." + COL_SUBJECT_INSTRUCTOR_ID + " = ? " +
                "ORDER BY s." + COL_SUBJECT_NAME + " ASC";
        return db.rawQuery(query, new String[]{adminId});
    }

    public Cursor getAdminByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?";
        return db.rawQuery(query, new String[]{email});
    }

    public boolean updateAdmin(String email, String password,
                               String fullName, String dob, String phone,
                               String gender, String photoPath, String description) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        if (password != null && !password.isEmpty()) {
            String hashedPassword = hashPassword(password);
            if (hashedPassword == null) return false;
            values.put(COL_ADMIN_PASSWORD, hashedPassword);
        }
        values.put(COL_ADMIN_FULL_NAME, fullName);
        values.put(COL_ADMIN_DOB, dob);
        values.put(COL_ADMIN_PHONE, phone);
        values.put(COL_ADMIN_GENDER, gender);
        if (photoPath != null) {
            values.put(COL_ADMIN_PHOTO, photoPath);
        }
        values.put(COL_ADMIN_DESC, description);

        int rowsAffected = db.update(TABLE_ADMIN, values, COL_ADMIN_EMAIL + " = ?", new String[]{email});
        return rowsAffected > 0;
    }

    public boolean updateAdminEmail(String oldEmail, String newEmail) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_EMAIL, newEmail);
        int rowsAffected = db.update(TABLE_ADMIN, values, COL_ADMIN_EMAIL + " = ?", new String[]{oldEmail});
        return rowsAffected > 0;
    }

    public boolean isFirstLogin(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_ADMIN_IS_FIRST_LOGIN + " FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?", new String[]{email});
        boolean isFirst = true;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_IS_FIRST_LOGIN);
            if (columnIndex != -1) {
                isFirst = cursor.getInt(columnIndex) == 1;
            }
            cursor.close();
        }
        return isFirst;
    }

    public boolean markFirstLoginComplete(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_IS_FIRST_LOGIN, 0);
        int rowsAffected = db.update(TABLE_ADMIN, values, COL_ADMIN_EMAIL + " = ?", new String[]{email});
        return rowsAffected > 0;
    }

    public String getAdminIdByEmail(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_ADMIN_ID + " FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?", new String[]{email});
        String adminId = null;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_ID);
            if (columnIndex != -1) {
                adminId = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return adminId;
    }

    public String getEmailByAdminId(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_ADMIN_EMAIL + " FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_ID + " = ?", new String[]{adminId});
        String email = null;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_EMAIL);
            if (columnIndex != -1) {
                email = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return email;
    }

    public boolean updateAdminIrisEnrollment(String email) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_ADMIN_HAS_IRIS, 1);
        int rowsAffected = db.update(TABLE_ADMIN, values, COL_ADMIN_EMAIL + " = ?", new String[]{email});
        return rowsAffected > 0;
    }

    public boolean hasAdminEnrolledIris(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_ADMIN_HAS_IRIS + " FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?", new String[]{email});
        boolean hasIris = false;
        if (cursor != null && cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(COL_ADMIN_HAS_IRIS);
            if (columnIndex != -1) {
                hasIris = cursor.getInt(columnIndex) == 1;
            }
            cursor.close();
        }
        return hasIris;
    }

    public boolean hasAnyAdminWithIris() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_HAS_IRIS + " = 1", null);
        boolean hasAny = false;
        if (cursor != null && cursor.moveToFirst()) {
            hasAny = cursor.getInt(0) > 0;
            cursor.close();
        }
        return hasAny;
    }

    public String getAdminRole(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_ADMIN_ROLE + " FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_EMAIL + " = ?", new String[]{email});
        String role = null;
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(COL_ADMIN_ROLE);
            if (idx != -1) role = cursor.getString(idx);
            cursor.close();
        }
        return role;
    }

    /**
     * Lấy role của Admin theo admin_id (dùng để lọc khi check trùng khuôn mặt theo role)
     */
    public String getRoleByAdminId(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT " + COL_ADMIN_ROLE + " FROM " + TABLE_ADMIN +
                        " WHERE " + COL_ADMIN_ID + " = ?",
                new String[]{adminId}
        );
        String role = null;
        if (cursor != null && cursor.moveToFirst()) {
            int idx = cursor.getColumnIndex(COL_ADMIN_ROLE);
            if (idx != -1) role = cursor.getString(idx);
            cursor.close();
        }
        return role;
    }

    public long createSubject(String subjectId, String subjectName, String timeSlot,
                              String createdById, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_ID, subjectId);
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        values.put(COL_SUBJECT_CREATED_BY, createdById);
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        values.put(COL_SUBJECT_CREATED_AT, new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date()));

        java.text.SimpleDateFormat semesterSdf = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        java.util.Calendar cal = java.util.Calendar.getInstance();
        String semesterStart = semesterSdf.format(cal.getTime());
        cal.add(java.util.Calendar.DAY_OF_YEAR, 90);
        String semesterEnd = semesterSdf.format(cal.getTime());

        values.put(COL_SUBJECT_SEMESTER_START, semesterStart);
        values.put(COL_SUBJECT_SEMESTER_END, semesterEnd);

        return db.insert(TABLE_SUBJECTS, null, values);
    }

    public String[] getSubjectSemesterRange(String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_SUBJECT_SEMESTER_START + ", " + COL_SUBJECT_SEMESTER_END + " FROM " + TABLE_SUBJECTS + " WHERE " + COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        String[] result = null;
        if (cursor != null && cursor.moveToFirst()) {
            result = new String[]{cursor.getString(0), cursor.getString(1)};
            cursor.close();
        }
        return result;
    }

    public boolean assignInstructor(String subjectId, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        int rows = db.update(TABLE_SUBJECTS, values, COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        return rows > 0;
    }

    public boolean isSubjectOwnedByAdmin(String subjectId, String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_SUBJECTS + " WHERE " + COL_SUBJECT_ID + " = ? AND " + COL_SUBJECT_INSTRUCTOR_ID + " = ?", new String[]{subjectId, adminId});
        boolean owned = cursor.moveToFirst();
        cursor.close();
        return owned;
    }

    public Cursor getSubjectById(String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT s.*, a." + COL_ADMIN_FULL_NAME + " AS instructor_name " +
                "FROM " + TABLE_SUBJECTS + " s " +
                "LEFT JOIN " + TABLE_ADMIN + " a ON s." + COL_SUBJECT_INSTRUCTOR_ID + " = a." + COL_ADMIN_ID + " " +
                "WHERE s." + COL_SUBJECT_ID + " = ?";
        return db.rawQuery(query, new String[]{subjectId});
    }

    public boolean updateSubjectBySuperAdmin(String subjectId, String subjectName,
                                             String timeSlot, String instructorId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        if (instructorId != null) {
            values.put(COL_SUBJECT_INSTRUCTOR_ID, instructorId);
            values.put(COL_SUBJECT_STATUS, STATUS_ASSIGNED);
        } else {
            values.putNull(COL_SUBJECT_INSTRUCTOR_ID);
            values.put(COL_SUBJECT_STATUS, STATUS_UNASSIGNED);
        }
        int rows = db.update(TABLE_SUBJECTS, values, COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        return rows > 0;
    }

    public boolean updateSubjectByAdmin(String subjectId, String adminId,
                                        String subjectName, String timeSlot) {
        if (!isSubjectOwnedByAdmin(subjectId, adminId)) {
            return false;
        }
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COL_SUBJECT_NAME, subjectName);
        values.put(COL_TIME_SLOT, timeSlot);
        int rows = db.update(TABLE_SUBJECTS, values, COL_SUBJECT_ID + " = ? AND " + COL_SUBJECT_INSTRUCTOR_ID + " = ?", new String[]{subjectId, adminId});
        return rows > 0;
    }

    public boolean isAdminIdExists(String adminId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT 1 FROM " + TABLE_ADMIN + " WHERE " + COL_ADMIN_ID + " = ?", new String[]{adminId});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public String verifyCheckinPassword(String studentId, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + COL_FULL_NAME + " FROM " + TABLE_STUDENTS + " WHERE " + COL_STUDENT_ID + " = ? AND " + COL_CHECKIN_PASSWORD + " = ?", new String[]{studentId, password});
        String name = null;
        if (cursor != null && cursor.moveToFirst()) {
            name = cursor.getString(0);
            cursor.close();
        }
        return name;
    }

    public Cursor getAllStudents() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_STUDENTS + " ORDER BY " + COL_FULL_NAME, null);
    }

    /**
     * XÓA VĨNH VIỄN sinh viên khỏi toàn bộ hệ thống.
     * Sẽ xóa luôn dữ liệu ghi danh (enrollments) và lịch sử điểm danh
     * (checkin_history) của sinh viên này ở TẤT CẢ các môn học.
     *
     * CHỈ dùng cho màn hình "Tất cả sinh viên" (isAllStudentsMode = true).
     * Nếu muốn xóa sinh viên khỏi MỘT môn học cụ thể (mà vẫn giữ ở các
     * môn khác), hãy dùng {@link #unenrollStudentFromSubject(String, String)}.
     */
    public boolean deleteStudent(String studentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.execSQL("PRAGMA foreign_keys = OFF");
            db.delete(TABLE_CHECKIN_HISTORY, COL_STUDENT_ID + " = ?", new String[]{studentId});
            db.delete(TABLE_ENROLLMENTS, COL_STUDENT_ID + " = ?", new String[]{studentId});
            int rows = db.delete(TABLE_STUDENTS, COL_STUDENT_ID + " = ?", new String[]{studentId});
            db.execSQL("PRAGMA foreign_keys = ON");
            db.setTransactionSuccessful();
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Hủy ghi danh sinh viên khỏi MỘT môn học cụ thể.
     * Chỉ xóa bản ghi trong enrollments và lịch sử điểm danh (checkin_history)
     * ứng với môn học đó — KHÔNG động tới các môn học khác và KHÔNG xóa
     * bản ghi sinh viên khỏi bảng students.
     *
     * Đây là hàm nên dùng khi bấm nút "Xóa" sinh viên từ danh sách của
     * một môn học cụ thể (subject_id != null).
     */
    public boolean unenrollStudentFromSubject(String studentId, String subjectId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            // Xóa lịch sử điểm danh của sinh viên CHỈ trong môn học này
            db.delete(TABLE_CHECKIN_HISTORY,
                    COL_STUDENT_ID + " = ? AND " + COL_SUBJECT_ID + " = ?",
                    new String[]{studentId, subjectId});

            // Xóa ghi danh của sinh viên CHỈ trong môn học này
            int rows = db.delete(TABLE_ENROLLMENTS,
                    COL_STUDENT_ID + " = ? AND " + COL_SUBJECT_ID + " = ?",
                    new String[]{studentId, subjectId});

            db.setTransactionSuccessful();
            return rows > 0;
        } finally {
            db.endTransaction();
        }
    }

    public Cursor getAbsentStudents(String subjectId, String startDate, String endDate) {
        SQLiteDatabase db = this.getReadableDatabase();
        StringBuilder sql = new StringBuilder(
                "SELECT s." + COL_STUDENT_ID + ", s." + COL_FULL_NAME +
                        " FROM " + TABLE_ENROLLMENTS + " e" +
                        " JOIN " + TABLE_STUDENTS + " s ON e." + COL_STUDENT_ID + " = s." + COL_STUDENT_ID +
                        " WHERE e." + COL_SUBJECT_ID + " = ?" +
                        " AND s." + COL_STUDENT_ID + " NOT IN (" +
                        "   SELECT h." + COL_STUDENT_ID + " FROM " + TABLE_CHECKIN_HISTORY + " h" +
                        "   WHERE h." + COL_SUBJECT_ID + " = ?"
        );

        java.util.List<String> args = new java.util.ArrayList<>();
        args.add(subjectId);
        args.add(subjectId);

        if (startDate != null && endDate != null) {
            sql.append(" AND h.").append(COL_CHECKIN_DATE).append(" BETWEEN ? AND ?");
            args.add(startDate);
            args.add(endDate);
        }

        sql.append(" )").append(" ORDER BY s.").append(COL_FULL_NAME).append(" ASC");
        return db.rawQuery(sql.toString(), args.toArray(new String[0]));
    }

    public Cursor getStudentCheckinHistory(String subjectId, String studentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT " + COL_CHECKIN_DATE + ", " + COL_CHECKIN_TIME +
                " FROM " + TABLE_CHECKIN_HISTORY +
                " WHERE " + COL_SUBJECT_ID + " = ? AND " + COL_STUDENT_ID + " = ?" +
                " ORDER BY " + COL_CHECKIN_DATE + " DESC";
        return db.rawQuery(query, new String[]{subjectId, studentId});
    }

    public int getTotalSessionCount(String subjectId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(DISTINCT " + COL_CHECKIN_DATE + ") FROM " + TABLE_CHECKIN_HISTORY + " WHERE " + COL_SUBJECT_ID + " = ?", new String[]{subjectId});
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }
}
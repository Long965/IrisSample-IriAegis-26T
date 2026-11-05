package com.iritech.irissample;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/*import com.iritech.android.widget.alertdialog.BestImageDialog;
import com.iritech.iris.CaptureActivity;
import com.iritech.iris.Constants;
import com.iritech.iris.DeveloperSettings;
import com.iritech.iris.LicenseInfo;
import com.iritech.mqel704.GemResult;*/

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class StudentListActivity extends AppCompatActivity {

    private int REQUEST_CODE_IDENTIFY = 1111;
    private int REQUEST_CODE_CAPTURE  = 1112;
    private int REQUEST_CODE_ENROLL   = 1113;
    private int REQUEST_CODE_UNENROLL = 1115;

    private static final int REQUEST_CODE_FACE_CAPTURE = 1002;

    private String selectedStudentId = null;
    private String currentSubjectId  = null;
    private int mResultCode;
    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 100;
    private static final int REQUEST_IRIS_PERMISSIONS = 101;

    private Intent pendingIrisIntent;
    private int pendingRequestCode;

    private boolean isAllStudentsMode = false;

    ListView listViewStudents;
    EditText edtSearch;
    SwitchCompat switchFilterAttendance;
    DatabaseHelper dbHelper;

    ArrayList<String> studentList     = new ArrayList<>();
    ArrayList<String> studentIds      = new ArrayList<>();
    ArrayList<String> studentSubs     = new ArrayList<>();
    ArrayList<String> filteredStudentList = new ArrayList<>();
    ArrayList<String> filteredStudentIds  = new ArrayList<>();
    ArrayList<String> filteredStudentSubs = new ArrayList<>();

    StudentAdapter adapter;

    // Bộ lắng nghe sự kiện dùng chung tối ưu
    private final StudentAdapter.OnStudentActionListener mStudentActionListener = new StudentAdapter.OnStudentActionListener() {
        @Override
        public void onEditClick(String studentId) {
            Intent intent = new Intent(StudentListActivity.this, AddStudentActivity.class);
            if (!isAllStudentsMode) {
                intent.putExtra("subject_id", currentSubjectId);
            }
            intent.putExtra("student_id", studentId);
            startActivityForResult(intent, 1001);
        }

        @Override
        public void onDeleteClick(String studentId) {
            confirmDelete(studentId);
        }

        @Override
        public void onStudentSelected(String studentId) {
            selectedStudentId = studentId;
        }
    };

    @Override
    protected void onCreate(Bundle Bundle) {
        super.onCreate(Bundle);
        setContentView(R.layout.activity_student_list);

        //LicenseInfo licenseInfo = LicenseInfo.getInstance();
        //if (!licenseInfo.isInitialized()) licenseInfo.initialize(this);

        listViewStudents       = findViewById(R.id.listViewStudents);
        edtSearch              = findViewById(R.id.edtSearch);
        switchFilterAttendance = findViewById(R.id.switchFilterAttendance);
        dbHelper               = new DatabaseHelper(this);

        currentSubjectId = getIntent().getStringExtra("subject_id");

        if (currentSubjectId == null || currentSubjectId.isEmpty()) {
            isAllStudentsMode = true;
        }

        if (isAllStudentsMode) {
            loadAllStudents();
        } else {
            loadStudentsForSubject(currentSubjectId);
        }

        setupSearch();
        setupAttendanceFilter();

        Button btnAddStudent = findViewById(R.id.btnAddStudent);
        btnAddStudent.setOnClickListener(v -> {
            Intent intent = new Intent(StudentListActivity.this, AddStudentActivity.class);
            if (!isAllStudentsMode) {
                intent.putExtra("subject_id", currentSubjectId);
            }
            startActivityForResult(intent, 1001);
        });

        Button btnImportCsv = findViewById(R.id.btnImportCsv);
        btnImportCsv.setOnClickListener(v -> {
            Intent intent = new Intent(StudentListActivity.this, ImportCsvActivity.class);
            if (!isAllStudentsMode) {
                intent.putExtra("subject_id", currentSubjectId);
            }
            startActivityForResult(intent, 1001);
        });

        Button btnIdentify = findViewById(R.id.btnIdentify);
        btnIdentify.setOnClickListener(v -> {
            if (selectedStudentId == null) {
                Toast.makeText(StudentListActivity.this, "Vui lòng chọn sinh viên", Toast.LENGTH_SHORT).show();
                return;
            }

            // FIX: Chặn điểm danh lần 2 trong cùng buổi học để không làm sai lệch thống kê
            if (!isAllStudentsMode && currentSubjectId != null && !currentSubjectId.isEmpty()) {
                Calendar nowCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
                SimpleDateFormat todayFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
                String todayStr = todayFormat.format(nowCal.getTime());

                if (hasStudentCheckedInToday(selectedStudentId, todayStr)) {
                    new AlertDialog.Builder(StudentListActivity.this)
                            .setTitle("Thông báo")
                            .setMessage("Sinh viên đã điểm danh cho buổi học hôm nay rồi.")
                            .setPositiveButton("Đã hiểu", null)
                            .show();
                    return;
                }
            }

            // FIX: Kiểm tra khung giờ điểm danh NGAY khi bấm nút "Điểm danh",
            // trước khi hiện hộp thoại nhập mật khẩu. Nếu ngoài giờ thì báo luôn
            // và không mở hộp thoại mật khẩu.
            if (!isAllStudentsMode && currentSubjectId != null && !currentSubjectId.isEmpty()) {
                String timeSlot = null;
                SQLiteDatabase db = dbHelper.getReadableDatabase();
                Cursor timeSlotCursor = db.rawQuery(
                        "SELECT " + DatabaseHelper.COL_TIME_SLOT +
                                " FROM " + DatabaseHelper.TABLE_SUBJECTS +
                                " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                        new String[]{currentSubjectId});
                if (timeSlotCursor != null && timeSlotCursor.moveToFirst()) {
                    timeSlot = timeSlotCursor.getString(0);
                    timeSlotCursor.close();
                }

                AttendanceTimeUtils.TimeWindow window = AttendanceTimeUtils.parseTimeSlot(timeSlot);
                String nowHms = AttendanceTimeUtils.getCurrentTimeHms();
                String windowStatus = AttendanceTimeUtils.evaluateCheckinWindow(nowHms, window);

                /*if (AttendanceTimeUtils.STATUS_WAIT_END.equals(windowStatus)) {
                    new AlertDialog.Builder(StudentListActivity.this)
                            .setTitle("Chưa đến giờ điểm danh")
                            .setMessage("Đã quá 15 phút đầu giờ học.\n" +
                                    "Vui lòng chờ đến cuối buổi (sau " + window.endTime.substring(0, 5) +
                                    ") để điểm danh muộn.")
                            .setPositiveButton("Đã hiểu", null)
                            .show();
                    return;
                }

                if (AttendanceTimeUtils.STATUS_CLOSED.equals(windowStatus)) {
                    new AlertDialog.Builder(StudentListActivity.this)
                            .setTitle("Ngoài thời gian điểm danh")
                            .setMessage("Hiện không trong thời gian cho phép điểm danh của môn học này.")
                            .setPositiveButton("Đóng", null)
                            .show();
                    return;
                }*/
                // ON_TIME_WINDOW hoặc LATE_WINDOW -> hợp lệ, tiếp tục nhập mật khẩu
            }

            showPasswordDialog();
        });

        Button btnExport = findViewById(R.id.btnExport);
        btnExport.setOnClickListener(v -> {
            if (isAllStudentsMode || currentSubjectId == null || currentSubjectId.isEmpty()) {
                Toast.makeText(StudentListActivity.this, "Vui lòng vào một môn học cụ thể trước khi xuất dữ liệu điểm danh!", Toast.LENGTH_LONG).show();
                return;
            }

            Intent intent = new Intent(StudentListActivity.this, AttendanceManagementActivity.class);

            // Đổi về "subject_id" viết thường để khớp với trang nhận
            intent.putExtra("subject_id", currentSubjectId);

            startActivity(intent);
        });
    }

    // ==================== LOAD DATA ====================

    private void loadAllStudents() {
        studentList.clear();
        studentIds.clear();
        studentSubs.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT " + DatabaseHelper.COL_STUDENT_ID + ", " + DatabaseHelper.COL_FULL_NAME +
                " FROM " + DatabaseHelper.TABLE_STUDENTS + " ORDER BY " + DatabaseHelper.COL_FULL_NAME;

        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                String studentId = cursor.getString(0);
                String fullName  = cursor.getString(1);
                studentIds.add(studentId);
                studentList.add(fullName);

                String subjectQuery = "SELECT s." + DatabaseHelper.COL_SUBJECT_NAME +
                        " FROM " + DatabaseHelper.TABLE_SUBJECTS + " s " +
                        " JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e ON s." + DatabaseHelper.COL_SUBJECT_ID + " = e." + DatabaseHelper.COL_SUBJECT_ID +
                        " WHERE e." + DatabaseHelper.COL_STUDENT_ID + " = ?";
                Cursor subCursor = db.rawQuery(subjectQuery, new String[]{studentId});
                StringBuilder subNames = new StringBuilder();
                if (subCursor.moveToFirst()) {
                    do {
                        if (subNames.length() > 0) subNames.append(", ");
                        subNames.append(subCursor.getString(0));
                    } while (subCursor.moveToNext());
                }
                subCursor.close();
                studentSubs.add(subNames.length() > 0 ? subNames.toString() : "Chưa có môn học");
            } while (cursor.moveToNext());
        }
        cursor.close();

        applyFilters();
    }

    private void loadStudentsForSubject(String subjectId) {
        studentList.clear();
        studentIds.clear();
        studentSubs.clear();

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME +
                " FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
                " JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e ON s." + DatabaseHelper.COL_STUDENT_ID + " = e." + DatabaseHelper.COL_STUDENT_ID +
                " WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?";

        Cursor cursor = db.rawQuery(query, new String[]{subjectId});
        if (cursor.moveToFirst()) {
            do {
                studentIds.add(cursor.getString(0));
                studentList.add(cursor.getString(1));
                studentSubs.add("");
            } while (cursor.moveToNext());
        }
        cursor.close();

        filteredStudentList.clear();
        filteredStudentIds.clear();
        filteredStudentSubs.clear();
        filteredStudentList.addAll(studentList);
        filteredStudentIds.addAll(studentIds);
        filteredStudentSubs.addAll(studentSubs);

        adapter = new StudentAdapter(this, filteredStudentList, filteredStudentIds, filteredStudentSubs, mStudentActionListener);
        listViewStudents.setAdapter(adapter);
    }

    private void reloadStudents(String subjectId) {
        studentList.clear();
        studentIds.clear();
        studentSubs.clear();
        filteredStudentList.clear();
        filteredStudentIds.clear();
        filteredStudentSubs.clear();
        if (isAllStudentsMode) {
            loadAllStudents();
        } else {
            loadStudentsForSubject(subjectId);
        }
        applyFilters();
    }

    // ==================== SEARCH & FILTER ====================

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupAttendanceFilter() {
        switchFilterAttendance.setOnCheckedChangeListener((buttonView, isChecked) -> applyFilters());
    }

    private void applyFilters() {
        filteredStudentList.clear();
        filteredStudentIds.clear();
        filteredStudentSubs.clear();

        String searchText        = edtSearch.getText().toString();
        boolean filterNotAttended = !isAllStudentsMode && switchFilterAttendance.isChecked();

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String today = dateFormat.format(calendar.getTime());

        for (int i = 0; i < studentList.size(); i++) {
            String studentName = studentList.get(i);
            String studentId   = studentIds.get(i);
            String studentSub  = studentSubs.get(i);

            boolean matchesSearch = searchText.isEmpty()
                    || studentName.toLowerCase().contains(searchText.toLowerCase())
                    || studentId.toLowerCase().contains(searchText.toLowerCase());

            if (!matchesSearch) continue;

            if (filterNotAttended) {
                if (!hasStudentCheckedInToday(studentId, today)) {
                    filteredStudentList.add(studentName);
                    filteredStudentIds.add(studentId);
                    filteredStudentSubs.add(studentSub);
                }
            } else {
                filteredStudentList.add(studentName);
                filteredStudentIds.add(studentId);
                filteredStudentSubs.add(studentSub);
            }
        }

        adapter = new StudentAdapter(this, filteredStudentList, filteredStudentIds, filteredStudentSubs, mStudentActionListener);
        listViewStudents.setAdapter(adapter);
        if(adapter != null) adapter.notifyDataSetChanged();
    }

    private boolean hasStudentCheckedInToday(String studentId, String today) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                DatabaseHelper.COL_SUBJECT_ID + " = ? AND " +
                DatabaseHelper.COL_CHECKIN_DATE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{studentId, currentSubjectId, today});
        boolean hasCheckedIn = false;
        if (cursor.moveToFirst()) hasCheckedIn = cursor.getInt(0) > 0;
        cursor.close();
        return hasCheckedIn;
    }

    private void markAttendance(String studentId) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String currentTime = timeFormat.format(calendar.getTime());
        String currentDate = dateFormat.format(calendar.getTime());

        // FIX: Chốt chặn an toàn - không cho ghi điểm danh trùng trong cùng ngày/môn học
        // (phòng trường hợp gọi markAttendance() từ luồng nhận diện tự động, bỏ qua bước
        // kiểm tra ở nút "Điểm danh").
        if (!isAllStudentsMode && currentSubjectId != null && !currentSubjectId.isEmpty()
                && hasStudentCheckedInToday(studentId, currentDate)) {
            new AlertDialog.Builder(StudentListActivity.this)
                    .setTitle("Thông báo")
                    .setMessage("Sinh viên này đã điểm danh cho buổi học hôm nay rồi.\n" +
                            "Không thể điểm danh lại để tránh ảnh hưởng đến thống kê điểm danh.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String query = "INSERT INTO " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " (" +
                DatabaseHelper.COL_STUDENT_ID + ", " + DatabaseHelper.COL_SUBJECT_ID + ", " +
                DatabaseHelper.COL_CHECKIN_TIME + ", " + DatabaseHelper.COL_CHECKIN_DATE +
                ") VALUES (?, ?, ?, ?)";
        db.execSQL(query, new Object[]{studentId, currentSubjectId, currentTime, currentDate});

        String studentName = "";
        Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_FULL_NAME + " FROM " + DatabaseHelper.TABLE_STUDENTS +
                " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});
        if (cursor.moveToFirst()) studentName = cursor.getString(0);
        cursor.close();

        BestImageDialog dialog = new BestImageDialog(this);
        dialog.setTitle("Thông báo!");
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.9),
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
            );
        }

        dialog.setBestImages(null, null, null);
        dialog.setMessage("Điểm danh thành công!\n\nSinh viên: " + studentName + "\nThời gian: " + currentTime + "\nNgày: " + currentDate);

        applyFilters();
    }

    // ==================== ACTIVITY RESULT ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //if (data != null) mResultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);

        if (requestCode == REQUEST_CODE_FACE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                String photoPath = data.getStringExtra(FaceCaptureActivity.RESULT_PHOTO_PATH);
                boolean isFaceMatched = data.getBooleanExtra(FaceCaptureActivity.RESULT_FACE_MATCHED, false);

                if (isFaceMatched) {
                    if (photoPath != null && !photoPath.isEmpty()) {
                        SQLiteDatabase db = dbHelper.getWritableDatabase();
                        ContentValues values = new ContentValues();
                        values.put(DatabaseHelper.COL_PHOTO_PATH, photoPath);

                        db.update(DatabaseHelper.TABLE_STUDENTS,
                                values,
                                DatabaseHelper.COL_STUDENT_ID + " = ?",
                                new String[]{selectedStudentId});
                    }
                    markAttendance(selectedStudentId);
                } else {
                    Toast.makeText(this, "Xác thực khuôn mặt thất bại. Không thể điểm danh!", Toast.LENGTH_LONG).show();
                }
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(this, "Đã hủy luồng quét khuôn mặt.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        /*if (mResultCode != GemResult.IDDK_UVC_DEVICE_ACCESS_DENIED) {
            if (resultCode == RESULT_OK) {
                if (requestCode == REQUEST_CODE_IDENTIFY) {
                    processIdentifyResult(data);
                } else if (requestCode == REQUEST_CODE_ENROLL) {
                    processEnrollResult(data);
                } else if (requestCode == REQUEST_CODE_UNENROLL) {
                    processUnenrollResult(data);
                } else if (requestCode == 1001) {
                    reloadStudents(currentSubjectId);
                } else {
                    Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getApplicationContext(), "Capture activity failed", Toast.LENGTH_LONG).show();
            }
        }*/
    }

    // ==================== FACE DETECTION FLOW ====================

    private void startFaceCaptureFlow(String studentId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String existingPhotoPath = null;

        Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_PHOTO_PATH +
                " FROM " + DatabaseHelper.TABLE_STUDENTS +
                " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});

        if (cursor.moveToFirst()) {
            existingPhotoPath = cursor.getString(0);
        }
        cursor.close();

        Intent intent = new Intent(StudentListActivity.this, FaceCaptureActivity.class);
        intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID, studentId);
        intent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, existingPhotoPath);
        startActivityForResult(intent, REQUEST_CODE_FACE_CAPTURE);
    }

    private void processIdentifyResult(Intent data) {
        /*int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
        if (resultCode == 0) {
            int resultCount    = data.getIntExtra(Constants.EXTRA_MATCHING_COUNT, 0);
            String resultItems = data.getStringExtra(Constants.EXTRA_MATCHING_ITEMS);
            if (resultCount > 0 && resultItems != null) {
                String studentId = extractStudentIdFromResult(resultItems);
                if (studentId != null && !studentId.isEmpty()) {
                    if (isStudentInSubject(studentId, currentSubjectId)) {
                        markAttendance(studentId);
                    } else {
                        BestImageDialog dialog = new BestImageDialog(this);
                        dialog.setTitle("Thông báo!");
                        dialog.show();
                        dialog.setBestImages(null, null, null);
                        dialog.setMessage("Sinh viên không thuộc lớp này");
                    }
                } else {
                    Toast.makeText(this, "Không thể xác định sinh viên từ kết quả", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Không tìm thấy sinh viên khớp", Toast.LENGTH_SHORT).show();
            }
        } else {
            String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
            Toast.makeText(this, "Lỗi điểm danh: " + resultMsg, Toast.LENGTH_SHORT).show();
        }*/
    }

    private boolean isStudentInSubject(String studentId, String subjectId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ENROLLMENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{studentId, subjectId});
        boolean exists = false;
        if (cursor.moveToFirst()) exists = cursor.getInt(0) > 0;
        cursor.close();
        return exists;
    }

    private String extractStudentIdFromResult(String resultItems) {
        try {
            if (resultItems.contains("ID: ")) {
                int startIndex = resultItems.indexOf("ID: ") + 4;
                int endIndex   = resultItems.indexOf(", distance:", startIndex);
                if (endIndex > startIndex)
                    return resultItems.substring(startIndex, endIndex).trim();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void processEnrollResult(Intent data) {
        /*int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        if (resultCode == 0) {
            Toast.makeText(this, "Ghi danh thành công!", Toast.LENGTH_SHORT).show();
            reloadStudents(currentSubjectId);
        } else {
            Toast.makeText(this, "Lỗi ghi danh: " + data.getStringExtra(Constants.EXTRA_RESULT_MSG), Toast.LENGTH_SHORT).show();
        }*/
    }

    private void processUnenrollResult(Intent data) {
        /*int resultCode = data.getIntExtra(Constants.EXTRA_RESULT_CODE, 0);
        if (resultCode == 0) {
            Toast.makeText(this, "Hủy ghi danh thành công!", Toast.LENGTH_SHORT).show();
            reloadStudents(currentSubjectId);
        } else {
            Toast.makeText(this, "Lỗi hủy ghi danh: " + data.getStringExtra(Constants.EXTRA_RESULT_MSG), Toast.LENGTH_SHORT).show();
        }*/
    }

    private void showPasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Nhập mật khẩu");

        final EditText input = new EditText(this);
        input.setHint("Nhập mật khẩu điểm danh");
        builder.setView(input);

        builder.setPositiveButton("Xác nhận", (dialog, which) -> {
            String password = input.getText().toString().trim();
            String studentName = dbHelper.verifyCheckinPassword(selectedStudentId, password);

            if (studentName == null) {
                Toast.makeText(StudentListActivity.this, "Sai mật khẩu điểm danh", Toast.LENGTH_SHORT).show();
                return;
            }

            // Khung giờ điểm danh đã được kiểm tra ngay khi bấm nút "Điểm danh"
            // (xem onClickListener của btnIdentify trong onCreate).
            startFaceCaptureFlow(selectedStudentId);
        });

        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    // ==================== XÓA / HỦY GHI DANH SINH VIÊN ====================

    /**
     * FIX: Trước đây hàm này luôn gọi dbHelper.deleteStudent(studentId), hàm này xóa
     * sinh viên khỏi TOÀN BỘ hệ thống (mọi enrollments + checkin_history ở mọi môn học).
     * Hậu quả: nếu 1 sinh viên học 2 môn, xóa ở màn hình môn A sẽ làm sinh viên biến mất
     * luôn ở môn B.
     *
     * Nay tách thành 2 trường hợp:
     *  - isAllStudentsMode == true (màn "Tất cả sinh viên"): xóa vĩnh viễn khỏi hệ thống,
     *    dùng dbHelper.deleteStudent().
     *  - isAllStudentsMode == false (đang ở trong 1 môn học cụ thể): chỉ hủy ghi danh khỏi
     *    môn học hiện tại, dùng dbHelper.unenrollStudentFromSubject(), các môn khác không
     *    bị ảnh hưởng.
     */
    private void confirmDelete(String studentId) {
        if (isAllStudentsMode) {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa sinh viên khỏi hệ thống")
                    .setMessage("Bạn có chắc muốn xóa VĨNH VIỄN sinh viên này khỏi hệ thống?\n" +
                            "Toàn bộ dữ liệu ghi danh và điểm danh ở TẤT CẢ các môn học sẽ bị xóa.")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        // Xóa file ảnh trước khi xóa khỏi DB
                        deleteStudentPhotoFile(studentId);

                        boolean success = dbHelper.deleteStudent(studentId);
                        if (success) {
                            Toast.makeText(StudentListActivity.this, "Đã xóa sinh viên khỏi hệ thống", Toast.LENGTH_SHORT).show();
                            if (studentId.equals(selectedStudentId)) {
                                selectedStudentId = null;
                            }
                            reloadStudents(currentSubjectId);
                        } else {
                            Toast.makeText(StudentListActivity.this, "Xóa thất bại", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Xóa sinh viên khỏi môn học")
                    .setMessage("Bạn có chắc muốn xóa sinh viên này khỏi môn học hiện tại?\n" +
                            "Sinh viên vẫn được giữ nguyên ở các môn học khác (nếu có).")
                    .setPositiveButton("Xóa", (dialog, which) -> {
                        boolean success = dbHelper.unenrollStudentFromSubject(studentId, currentSubjectId);
                        if (success) {
                            Toast.makeText(StudentListActivity.this, "Đã xóa sinh viên khỏi môn học", Toast.LENGTH_SHORT).show();
                            if (studentId.equals(selectedStudentId)) {
                                selectedStudentId = null;
                            }
                            reloadStudents(currentSubjectId);
                        } else {
                            Toast.makeText(StudentListActivity.this, "Xóa thất bại", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }

    /**
     * Xóa file ảnh khuôn mặt của sinh viên khỏi bộ nhớ.
     * Chỉ gọi khi xóa VĨNH VIỄN sinh viên khỏi hệ thống (isAllStudentsMode == true),
     * để tránh file mồ côi gây ra lỗi phát hiện gian lận khi đăng ký khuôn mặt mới.
     * Không gọi khi chỉ hủy ghi danh khỏi 1 môn học, vì sinh viên còn có thể ở môn khác.
     */
    private void deleteStudentPhotoFile(String studentId) {
        File dir = new File(getExternalFilesDir(null), "students");
        if (!dir.exists()) return;

        String[] prefixes = {"face_", "avatar_"};
        for (String prefix : prefixes) {
            File file = new File(dir, prefix + studentId + ".jpg");
            if (file.exists()) {
                boolean deleted = file.delete();
                if (!deleted) {
                    android.util.Log.w("StudentListActivity",
                            "Không thể xóa file ảnh: " + file.getAbsolutePath());
                }
            }
        }
    }

    // ==================== PERMISSIONS ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int r : grantResults)
                if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted && pendingIrisIntent != null) {
                //LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, pendingRequestCode);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this, "Cần cấp quyền Camera và bộ nhớ để sử dụng tính năng.", Toast.LENGTH_LONG).show();
            }
        }
    }
}
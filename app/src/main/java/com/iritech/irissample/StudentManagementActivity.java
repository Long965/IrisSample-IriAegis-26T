package com.iritech.irissample;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class StudentManagementActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_CSV = 200;
    private static final int REQUEST_FACE_CAPTURE = 300;

    private RecyclerView recyclerStudents;
    private View layoutEmptyState;
    private EditText editSearch;
    private FloatingActionButton btnAdd;
    private Button btnImportCsv;
    private Button btnCheckinByPassword;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;
    private String currentUserRole;

    // Lưu thông tin chờ kết quả camera
    private String pendingSubjectId;
    private String pendingSubjectName;
    private String pendingStudentId;
    private String pendingStudentName;

    private List<StudentItem> allStudents = new ArrayList<>();
    private List<StudentItem> filteredStudents = new ArrayList<>();
    private StudentMgmtAdapter adapter;

    static class StudentItem {
        String studentId, fullName, email, phone, checkinPassword;

        StudentItem(String id, String name, String email, String phone, String pwd) {
            this.studentId = id;
            this.fullName = name;
            this.email = email;
            this.phone = phone;
            this.checkinPassword = pwd;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_management);

        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        currentUserRole  = getIntent().getStringExtra("USER_ROLE");
        dbHelper = new DatabaseHelper(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("Quản lý Sinh viên");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerStudents     = findViewById(R.id.recyclerStudents);
        layoutEmptyState     = findViewById(R.id.layoutEmptyState);
        editSearch           = findViewById(R.id.editSearchStudent);
        btnAdd               = findViewById(R.id.btnAddStudent);
        btnImportCsv         = findViewById(R.id.btnImportCsv);
        btnCheckinByPassword = findViewById(R.id.btnCheckinByPassword);

        setupRecyclerView();
        setupSearch();

        btnAdd.setOnClickListener(v -> showAddEditDialog(null));
        btnImportCsv.setOnClickListener(v -> openCsvPicker());
        btnCheckinByPassword.setOnClickListener(v -> showCheckinByPasswordDialog());

        loadStudents();
    }

    // ==================== RECYCLER VIEW ====================

    private void setupRecyclerView() {
        adapter = new StudentMgmtAdapter(filteredStudents,
                new StudentMgmtAdapter.OnActionListener() {
                    @Override public void onEdit(StudentItem s)   { showAddEditDialog(s); }
                    @Override public void onDelete(StudentItem s) { confirmDelete(s); }
                });
        recyclerStudents.setLayoutManager(new LinearLayoutManager(this));
        recyclerStudents.setAdapter(adapter);
    }

    private void setupSearch() {
        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                filterStudents(s.toString());
            }
        });
    }

    // ==================== LOAD DATA ====================

    private void loadStudents() {
        allStudents.clear();
        Cursor cursor = dbHelper.getAllStudents();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String id    = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STUDENT_ID));
                String name  = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FULL_NAME));
                String email = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL));
                String phone = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHONE));
                String pwd   = "";
                int pwdIdx   = cursor.getColumnIndex(DatabaseHelper.COL_CHECKIN_PASSWORD);
                if (pwdIdx != -1) pwd = cursor.getString(pwdIdx);
                allStudents.add(new StudentItem(id, name, email, phone, pwd));
            } while (cursor.moveToNext());
            cursor.close();
        }
        filterStudents(editSearch.getText().toString());
    }

    private void filterStudents(String query) {
        filteredStudents.clear();
        String q = query.toLowerCase().trim();
        for (StudentItem s : allStudents) {
            if (q.isEmpty()
                    || s.studentId.toLowerCase().contains(q)
                    || s.fullName.toLowerCase().contains(q)
                    || (s.email != null && s.email.toLowerCase().contains(q))) {
                filteredStudents.add(s);
            }
        }
        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = filteredStudents.isEmpty();
        layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerStudents.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ==================== THÊM / SỬA SINH VIÊN ====================

    private void showAddEditDialog(@Nullable StudentItem student) {
        boolean isEdit = (student != null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "Sửa thông tin sinh viên" : "Thêm sinh viên mới");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText edtId    = makeEditText("Mã sinh viên (VD: SV001)");
        EditText edtName  = makeEditText("Họ và tên *");
        EditText edtEmail = makeEditText("Email");
        EditText edtPhone = makeEditText("Số điện thoại");
        EditText edtPwd   = makeEditText("Mật khẩu điểm danh (VD: 1234)");

        layout.addView(edtId);
        layout.addView(edtName);
        layout.addView(edtEmail);
        layout.addView(edtPhone);
        layout.addView(edtPwd);
        builder.setView(layout);

        if (isEdit) {
            edtId.setText(student.studentId);
            edtId.setEnabled(false);
            edtName.setText(student.fullName);
            edtEmail.setText(student.email);
            edtPhone.setText(student.phone);
            edtPwd.setText(student.checkinPassword);
        }

        builder.setPositiveButton(isEdit ? "Cập nhật" : "Thêm", (dialog, which) -> {
            String id    = edtId.getText().toString().trim();
            String name  = edtName.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String phone = edtPhone.getText().toString().trim();
            String pwd   = edtPwd.getText().toString().trim();

            if (id.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Mã và Họ tên", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isEdit) updateStudent(id, name, email, phone, pwd);
            else insertStudent(id, name, email, phone, pwd);
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    private EditText makeEditText(String hint) {
        EditText edt = new EditText(this);
        edt.setHint(hint);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(8 * getResources().getDisplayMetrics().density);
        edt.setLayoutParams(lp);
        return edt;
    }

    private void insertStudent(String id, String name, String email, String phone, String pwd) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_STUDENT_ID, id);
        values.put(DatabaseHelper.COL_FULL_NAME, name);
        values.put(DatabaseHelper.COL_EMAIL, email);
        values.put(DatabaseHelper.COL_PHONE, phone);
        values.put(DatabaseHelper.COL_CHECKIN_PASSWORD, pwd);

        long result = db.insert(DatabaseHelper.TABLE_STUDENTS, null, values);
        if (result != -1) {
            Toast.makeText(this, "Thêm sinh viên thành công!", Toast.LENGTH_SHORT).show();
            loadStudents();
        } else {
            Toast.makeText(this, "Mã sinh viên đã tồn tại!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStudent(String id, String name, String email, String phone, String pwd) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseHelper.COL_FULL_NAME, name);
        values.put(DatabaseHelper.COL_EMAIL, email);
        values.put(DatabaseHelper.COL_PHONE, phone);
        values.put(DatabaseHelper.COL_CHECKIN_PASSWORD, pwd);

        int rows = db.update(DatabaseHelper.TABLE_STUDENTS, values,
                DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{id});
        if (rows > 0) {
            Toast.makeText(this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
            loadStudents();
        }
    }

    private void confirmDelete(StudentItem student) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa sinh viên")
                .setMessage("Xóa sinh viên \"" + student.fullName + "\"?\n\nTất cả dữ liệu điểm danh sẽ bị xóa!")
                .setPositiveButton("Xóa", (d, w) -> {
                    if (dbHelper.deleteStudent(student.studentId)) {
                        Toast.makeText(this, "Đã xóa sinh viên", Toast.LENGTH_SHORT).show();
                        loadStudents();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ==================== ĐIỂM DANH BẰNG MẬT KHẨU + KHUÔN MẶT ====================

    private void showCheckinByPasswordDialog() {
        Cursor subjectCursor = dbHelper.getReadableDatabase().rawQuery(
                "SELECT " + DatabaseHelper.COL_SUBJECT_ID + ", " + DatabaseHelper.COL_SUBJECT_NAME +
                        " FROM " + DatabaseHelper.TABLE_SUBJECTS +
                        " ORDER BY " + DatabaseHelper.COL_SUBJECT_NAME, null);

        List<String> subjectIds   = new ArrayList<>();
        List<String> subjectNames = new ArrayList<>();
        if (subjectCursor != null && subjectCursor.moveToFirst()) {
            do {
                subjectIds.add(subjectCursor.getString(0));
                subjectNames.add(subjectCursor.getString(1));
            } while (subjectCursor.moveToNext());
            subjectCursor.close();
        }

        if (subjectIds.isEmpty()) {
            Toast.makeText(this, "Chưa có môn học nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn môn học để điểm danh")
                .setItems(subjectNames.toArray(new String[0]), (dialog, which) ->
                        showEnterCredentialsDialog(subjectIds.get(which), subjectNames.get(which)))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void showEnterCredentialsDialog(String subjectId, String subjectName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Môn: " + subjectName);
        tvInfo.setPadding(0, 0, 0, pad / 2);

        EditText edtStudentId = makeEditText("Mã sinh viên (VD: SV001)");
        EditText edtPassword  = makeEditText("Mật khẩu điểm danh");
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        layout.addView(tvInfo);
        layout.addView(edtStudentId);
        layout.addView(edtPassword);

        new AlertDialog.Builder(this)
                .setTitle("Điểm danh bằng mật khẩu")
                .setView(layout)
                .setPositiveButton("Tiếp theo", (dialog, which) -> {
                    String studentId = edtStudentId.getText().toString().trim();
                    String password  = edtPassword.getText().toString().trim();
                    if (studentId.isEmpty() || password.isEmpty()) {
                        Toast.makeText(this, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performCheckin(subjectId, subjectName, studentId, password);
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void performCheckin(String subjectId, String subjectName,
                                String studentId, String password) {
        // Bước 1: Xác thực mật khẩu
        String studentName = dbHelper.verifyCheckinPassword(studentId, password);
        if (studentName == null) {
            new AlertDialog.Builder(this)
                    .setTitle("❌ Sai thông tin")
                    .setMessage("Mã sinh viên hoặc mật khẩu không đúng.\nVui lòng thử lại.")
                    .setPositiveButton("Thử lại", (d, w) ->
                            showEnterCredentialsDialog(subjectId, subjectName))
                    .setNegativeButton("Hủy", null)
                    .show();
            return;
        }

        // Bước 2: Kiểm tra sinh viên có trong môn học không
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor enrollCursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_ENROLLMENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ? AND " +
                        DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{studentId, subjectId});
        boolean enrolled = false;
        if (enrollCursor != null && enrollCursor.moveToFirst()) {
            enrolled = enrollCursor.getInt(0) > 0;
            enrollCursor.close();
        }
        if (!enrolled) {
            Toast.makeText(this, studentName + " không thuộc môn " + subjectName,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Bước 2.5: Kiểm tra khung giờ điểm danh dựa trên ca học của môn
        // (Bắt đầu -> +15p: đúng giờ | +15p -> Kết thúc: chờ cuối buổi |
        //  Kết thúc -> +15p: muộn | ngoài các khung trên: ngoài giờ, tính vắng)
        String timeSlot = null;
        Cursor timeSlotCursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_TIME_SLOT +
                        " FROM " + DatabaseHelper.TABLE_SUBJECTS +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{subjectId});
        if (timeSlotCursor != null && timeSlotCursor.moveToFirst()) {
            timeSlot = timeSlotCursor.getString(0);
            timeSlotCursor.close();
        }

        AttendanceTimeUtils.TimeWindow window = AttendanceTimeUtils.parseTimeSlot(timeSlot);
        String nowHms = AttendanceTimeUtils.getCurrentTimeHms();
        String windowStatus = AttendanceTimeUtils.evaluateCheckinWindow(nowHms, window);

        /*if (AttendanceTimeUtils.STATUS_WAIT_END.equals(windowStatus)) {
            new AlertDialog.Builder(this)
                    .setTitle("Chưa đến giờ điểm danh")
                    .setMessage("Đã quá 15 phút đầu giờ học.\n" +
                            "Vui lòng chờ đến cuối buổi (sau " + window.endTime.substring(0, 5) +
                            ") để điểm danh muộn.")
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }

        if (AttendanceTimeUtils.STATUS_CLOSED.equals(windowStatus)) {
            new AlertDialog.Builder(this)
                    .setTitle("Ngoài thời gian điểm danh")
                    .setMessage("Hiện không trong thời gian cho phép điểm danh của môn " +
                            subjectName + ".\nBuổi học này sẽ được tính là VẮNG MẶT.")
                    .setPositiveButton("Đóng", null)
                    .show();
            return;
        }*/
        // Nếu windowStatus là ON_TIME_WINDOW hoặc LATE_WINDOW -> cho phép mở camera điểm danh

        // Bước 3: Lưu thông tin chờ camera
        pendingSubjectId   = subjectId;
        pendingSubjectName = subjectName;
        pendingStudentId   = studentId;
        pendingStudentName = studentName;

        // Bước 4: Lấy avatar hiện tại
        String existingPhoto = null;
        Cursor studentCursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_PHOTO_PATH +
                        " FROM " + DatabaseHelper.TABLE_STUDENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                new String[]{studentId});
        if (studentCursor != null && studentCursor.moveToFirst()) {
            existingPhoto = studentCursor.getString(0);
            if (existingPhoto != null && existingPhoto.isEmpty()) existingPhoto = null;
            studentCursor.close();
        }

        // Bước 5: Mở camera
        Intent intent = new Intent(this, FaceCaptureActivity.class);
        intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID, studentId);
        intent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, existingPhoto);
        startActivityForResult(intent, REQUEST_FACE_CAPTURE);
    }

    // ==================== KẾT QUẢ CAMERA ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Import CSV
        if (requestCode == REQUEST_CODE_IMPORT_CSV && resultCode == RESULT_OK && data != null) {
            importFromCsv(data.getData());
            return;
        }

        // Kết quả chụp khuôn mặt
        if (requestCode == REQUEST_FACE_CAPTURE) {
            if (resultCode != RESULT_OK || data == null) {
                Toast.makeText(this, "Điểm danh bị hủy", Toast.LENGTH_SHORT).show();
                return;
            }

            String photoPath = data.getStringExtra(FaceCaptureActivity.RESULT_PHOTO_PATH);
            boolean matched  = data.getBooleanExtra(FaceCaptureActivity.RESULT_FACE_MATCHED, false);

            if (!matched) {
                Toast.makeText(this, "❌ Khuôn mặt không khớp, điểm danh thất bại",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Cập nhật avatar sinh viên
            SQLiteDatabase wdb = dbHelper.getWritableDatabase();
            ContentValues cvPhoto = new ContentValues();
            cvPhoto.put(DatabaseHelper.COL_PHOTO_PATH, photoPath);
            wdb.update(DatabaseHelper.TABLE_STUDENTS, cvPhoto,
                    DatabaseHelper.COL_STUDENT_ID + " = ?",
                    new String[]{pendingStudentId});

            // Ghi điểm danh
            java.util.Calendar cal = java.util.Calendar.getInstance(
                    java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
            String date = String.format(java.util.Locale.getDefault(),
                    "%02d/%02d/%04d",
                    cal.get(java.util.Calendar.DAY_OF_MONTH),
                    cal.get(java.util.Calendar.MONTH) + 1,
                    cal.get(java.util.Calendar.YEAR));
            String time = String.format(java.util.Locale.getDefault(),
                    "%02d:%02d:%02d",
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    cal.get(java.util.Calendar.SECOND));

            ContentValues cvCheckin = new ContentValues();
            cvCheckin.put(DatabaseHelper.COL_STUDENT_ID, pendingStudentId);
            cvCheckin.put(DatabaseHelper.COL_SUBJECT_ID, pendingSubjectId);
            cvCheckin.put(DatabaseHelper.COL_CHECKIN_TIME, time);
            cvCheckin.put(DatabaseHelper.COL_CHECKIN_DATE, date);
            wdb.insert(DatabaseHelper.TABLE_CHECKIN_HISTORY, null, cvCheckin);

            new AlertDialog.Builder(this)
                    .setTitle("✅ Điểm danh thành công!")
                    .setMessage("Sinh viên: " + pendingStudentName + "\n" +
                            "Môn học: "   + pendingSubjectName  + "\n" +
                            "Thời gian: " + time + "\n" +
                            "Ngày: "      + date)
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    // ==================== IMPORT CSV ====================

    private void openCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV sinh viên"),
                REQUEST_CODE_IMPORT_CSV);
    }

    private void importFromCsv(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));

            reader.mark(1);
            if (reader.read() != 0xFEFF) reader.reset();
            reader.readLine(); // bỏ header

            int success = 0, skip = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] t = line.split(",", -1);
                if (t.length < 2) { skip++; continue; }

                String id    = t[0].trim();
                String name  = t[1].trim();
                String phone = t.length > 2 ? t[2].trim() : "";
                String email = t.length > 3 ? t[3].trim() : "";
                String pwd   = t.length > 5 ? t[5].trim() : "";

                if (id.isEmpty() || name.isEmpty()) { skip++; continue; }

                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put(DatabaseHelper.COL_STUDENT_ID, id);
                cv.put(DatabaseHelper.COL_FULL_NAME, name);
                cv.put(DatabaseHelper.COL_PHONE, phone);
                cv.put(DatabaseHelper.COL_EMAIL, email);
                cv.put(DatabaseHelper.COL_CHECKIN_PASSWORD, pwd);

                long r = db.insertWithOnConflict(DatabaseHelper.TABLE_STUDENTS,
                        null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                if (r != -1) success++; else skip++;
            }
            reader.close();

            new AlertDialog.Builder(this)
                    .setTitle("Kết quả Import CSV")
                    .setMessage("✅ Thêm thành công: " + success + " sinh viên\n" +
                            "⏭ Bỏ qua (đã tồn tại/lỗi): " + skip)
                    .setPositiveButton("OK", (d, w) -> loadStudents())
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "Lỗi import CSV: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
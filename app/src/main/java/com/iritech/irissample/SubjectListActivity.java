package com.iritech.irissample;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.iritech.irissample.adapter.SubjectWithSchedulesAdapter;
import com.iritech.irissample.model.SubjectWithSchedules;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Activity hiển thị danh sách môn học
 * - Super Admin: thấy tất cả môn, import CSV, gán giảng viên
 * - Admin: chỉ thấy môn của mình
 */
public class SubjectListActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORT_SUBJECTS_CSV = 100;

    private RecyclerView recyclerSubjects;
    private View layoutEmptyState;
    private EditText editSearchSubject;
    private DatabaseHelper dbHelper;
    private SubjectWithSchedulesAdapter adapter;
    private List<SubjectWithSchedules> subjectList;
    private List<SubjectWithSchedules> allSubjectList;
    private FloatingActionButton btnAddSubject;
    private ImageButton btnImportSubjectsCsv;
    private String currentUserEmail;
    private String currentAdminId;
    private String currentRole;
    private boolean isSuperAdmin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_list);

        recyclerSubjects = findViewById(R.id.recyclerSubjects);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        editSearchSubject = findViewById(R.id.editSearchSubject);
        btnAddSubject = findViewById(R.id.btnAddSubject);
        btnImportSubjectsCsv = findViewById(R.id.btnImportSubjectsCsv);
        
        dbHelper = new DatabaseHelper(this);
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        currentAdminId = dbHelper.getAdminIdByEmail(currentUserEmail);
        currentRole = dbHelper.getAdminRole(currentUserEmail);
        isSuperAdmin = "SUPER_ADMIN".equals(currentRole);

        setupRecyclerView();
        loadSubjects();
        setupSearch();

        // Nút thêm môn học
        btnAddSubject.setOnClickListener(v -> {
            Intent intent = new Intent(SubjectListActivity.this, AddSubjectActivity.class);
            intent.putExtra("USER_EMAIL", currentUserEmail);
            intent.putExtra("MODE", "ADD");
            startActivity(intent);
        });

        // Nút import môn học từ CSV (Super Admin và Admin)
        btnImportSubjectsCsv.setVisibility(View.VISIBLE);
        btnImportSubjectsCsv.setOnClickListener(v -> openSubjectCsvPicker());
    }

    /**
     * Setup RecyclerView với adapter
     */
    private void setupRecyclerView() {
        allSubjectList = new ArrayList<>();
        subjectList = new ArrayList<>();
        adapter = new SubjectWithSchedulesAdapter(this, subjectList);
        recyclerSubjects.setLayoutManager(new LinearLayoutManager(this));
        recyclerSubjects.setAdapter(adapter);

        // Listener cho các hành động
        adapter.setOnSubjectActionListener(new SubjectWithSchedulesAdapter.OnSubjectActionListener() {
            @Override
            public void onSubjectClick(SubjectWithSchedules subject) {
                showSubjectDetailDialog(subject);
            }

            @Override
            public void onViewStudentsClick(SubjectWithSchedules subject) {
                Intent intent = new Intent(SubjectListActivity.this, StudentListActivity.class);
                intent.putExtra("subject_id", subject.getSubjectId());
                startActivity(intent);
            }

            @Override
            public void onImportStudentsClick(SubjectWithSchedules subject) {
                Intent intent = new Intent(SubjectListActivity.this, ImportCsvActivity.class);
                intent.putExtra("subject_id", subject.getSubjectId());
                startActivity(intent);
            }

            @Override
            public void onMoreOptionsClick(SubjectWithSchedules subject, View anchor) {
                showSubjectMenu(subject, anchor);
            }
        });
    }

    /**
     * Load danh sách môn học từ database (phân quyền theo role)
     */
    private void loadSubjects() {
        allSubjectList.clear();
        subjectList.clear();

        // Super Admin: thấy tất cả, Admin: chỉ thấy môn của mình
        Cursor cursor;
        if (isSuperAdmin) {
            cursor = dbHelper.getAllSubjectsWithInstructor();
        } else {
            cursor = dbHelper.getSubjectsByInstructor(currentAdminId);
        }

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String subjectId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_ID));
                String subjectName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_NAME));
                String timeSlot = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TIME_SLOT));
                String createdBy = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_CREATED_BY));
                String instructorId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_INSTRUCTOR_ID));
                String subjectStatus = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_STATUS));
                String createdAt = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_CREATED_AT));

                // Lấy tên giảng viên từ LEFT JOIN
                int instrNameIdx = cursor.getColumnIndex("instructor_name");
                String instructorName = (instrNameIdx != -1) ? cursor.getString(instrNameIdx) : null;

                SubjectWithSchedules subject = new SubjectWithSchedules(
                    subjectId, subjectName, timeSlot, createdBy,
                    instructorId, subjectStatus, instructorName, createdAt);

                allSubjectList.add(subject);
            } while (cursor.moveToNext());
            cursor.close();
        }

        // Apply search filter
        filterSubjects();
    }

    private void setupSearch() {
        editSearchSubject.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterSubjects();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void filterSubjects() {
        subjectList.clear();
        String searchText = editSearchSubject.getText().toString().toLowerCase().trim();

        for (SubjectWithSchedules subject : allSubjectList) {
            boolean matchSearch = searchText.isEmpty()
                    || subject.getSubjectName().toLowerCase().contains(searchText)
                    || subject.getSubjectId().toLowerCase().contains(searchText)
                    || (subject.getInstructorName() != null && subject.getInstructorName().toLowerCase().contains(searchText));

            if (matchSearch) {
                subjectList.add(subject);
            }
        }

        adapter.notifyDataSetChanged();
        updateEmptyState();
    }

    /**
     * Hiển thị menu context (Sửa/Xóa)
     */
    private void showSubjectMenu(SubjectWithSchedules subject, View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.menu_subject_options, popup.getMenu());

        // Ẩn/hiện menu item tùy role
        if (!isSuperAdmin) {
            // Admin chỉ sửa/xóa môn của mình
            boolean isOwned = dbHelper.isSubjectOwnedByAdmin(subject.getSubjectId(), currentAdminId);
            popup.getMenu().findItem(R.id.action_edit_subject).setVisible(isOwned);
            popup.getMenu().findItem(R.id.action_delete_subject).setVisible(isOwned);
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.action_edit_subject) {
                openEditSubject(subject);
                return true;
            } else if (itemId == R.id.action_delete_subject) {
                confirmDeleteSubject(subject);
                return true;
            }

            return false;
        });

        popup.show();
    }

    /**
     * Mở màn hình sửa môn học
     */
    private void openEditSubject(SubjectWithSchedules subject) {
        Intent intent = new Intent(this, AddSubjectActivity.class);
        intent.putExtra("USER_EMAIL", currentUserEmail);
        intent.putExtra("MODE", "EDIT");
        intent.putExtra("SUBJECT_ID", subject.getSubjectId());
        startActivity(intent);
    }

    /**
     * Xác nhận xóa môn học
     */
    private void confirmDeleteSubject(SubjectWithSchedules subject) {
        new AlertDialog.Builder(this)
            .setTitle("Xóa Môn Học")
            .setMessage("Bạn có chắc muốn xóa môn học \"" + subject.getSubjectName() + "\"?\n\n" +
                       "Lưu ý: Tất cả sinh viên đã đăng ký và dữ liệu điểm danh sẽ bị xóa!")
            .setPositiveButton("Xóa", (dialog, which) -> deleteSubject(subject))
            .setNegativeButton("Hủy", null)
            .show();
    }

    /**
     * Xóa môn học khỏi database
     */
    private void deleteSubject(SubjectWithSchedules subject) {
        // Kiểm tra quyền: Admin chỉ xóa được môn của mình
        if (!isSuperAdmin && !dbHelper.isSubjectOwnedByAdmin(subject.getSubjectId(), currentAdminId)) {
            Toast.makeText(this, "Bạn không có quyền xóa môn học này", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        
        try {
            db.beginTransaction();
            
            // Bảng schedules đã bị xóa - bỏ qua
            
            // Xóa enrollments
            db.delete(DatabaseHelper.TABLE_ENROLLMENTS, 
                     DatabaseHelper.COL_SUBJECT_ID + " = ?", 
                     new String[]{subject.getSubjectId()});
            
            // Xóa checkin_history
            db.delete(DatabaseHelper.TABLE_CHECKIN_HISTORY, 
                     DatabaseHelper.COL_SUBJECT_ID + " = ?", 
                     new String[]{subject.getSubjectId()});
            
            // Xóa subject
            db.delete(DatabaseHelper.TABLE_SUBJECTS, 
                     DatabaseHelper.COL_SUBJECT_ID + " = ?", 
                     new String[]{subject.getSubjectId()});
            
            db.setTransactionSuccessful();
            Toast.makeText(this, "Đã xóa môn học", Toast.LENGTH_SHORT).show();
            loadSubjects(); // Reload danh sách
            
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Mở file picker để chọn CSV import môn học
     */
    private void openSubjectCsvPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("text/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn file CSV môn học"), REQUEST_CODE_IMPORT_SUBJECTS_CSV);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_IMPORT_SUBJECTS_CSV && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            importSubjectsFromCsv(uri);
        }
    }

    /**
     * Import môn học từ file CSV (hỗ trợ cột instructor_id)
     * Super Admin: subject_id,subject_name,time_slot,instructor_id (4 cột, instructor_id tùy chọn)
     * Admin: subject_id,subject_name,time_slot (3 cột, tự động gán instructor = chính mình)
     */
    private void importSubjectsFromCsv(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

            // Check BOM
            reader.mark(1);
            if (reader.read() != 0xFEFF) {
                reader.reset();
            }

            String line;
            int successCount = 0;
            int warningCount = 0;
            int errorCount = 0;
            int lineNumber = 0;
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            // Skip header
            reader.readLine();
            lineNumber++;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] tokens = line.split(",", -1);

                // Validate bắt buộc: subject_id và subject_name
                String subjectId = tokens.length > 0 ? tokens[0].trim() : "";
                String subjectName = tokens.length > 1 ? tokens[1].trim() : "";

                if (subjectId.isEmpty()) {
                    errors.add("Dòng " + lineNumber + ": Thiếu subject_id");
                    errorCount++;
                    continue;
                }
                if (subjectName.isEmpty()) {
                    errors.add("Dòng " + lineNumber + ": Thiếu subject_name");
                    errorCount++;
                    continue;
                }

                String timeSlot = tokens.length > 2 ? tokens[2].trim() : "";
                String rawInstructorId = tokens.length > 3 ? tokens[3].trim() : "";

                // Xử lý instructor_id
                String instructorId = null;
                if (!isSuperAdmin) {
                    // Admin: tự động gán instructor = chính mình
                    instructorId = currentAdminId;
                } else if (!rawInstructorId.isEmpty()) {
                    if (dbHelper.isAdminIdExists(rawInstructorId)) {
                        instructorId = rawInstructorId;
                    } else {
                        warnings.add("Dòng " + lineNumber + ": instructor_id '" + rawInstructorId + "' không tồn tại \u2192 Chưa phân công");
                        warningCount++;
                    }
                } else {
                    if (tokens.length > 3) {
                        warnings.add("Dòng " + lineNumber + ": Không có instructor_id \u2192 Chưa phân công");
                        warningCount++;
                    }
                }

                // Insert
                long result = dbHelper.createSubject(subjectId, subjectName,
                        timeSlot.isEmpty() ? null : timeSlot,
                        currentAdminId, instructorId);

                if (result != -1) {
                    successCount++;
                } else {
                    errors.add("Dòng " + lineNumber + ": subject_id '" + subjectId + "' đã tồn tại \u2013 bỏ qua");
                    errorCount++;
                }
            }

            reader.close();

            // Hiển thị báo cáo kết quả
            showImportReport(successCount, warningCount, errorCount, warnings, errors);
            loadSubjects();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi import CSV: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Hiển thị báo cáo kết quả import CSV
     */
    private void showImportReport(int success, int warnings, int errorCount,
                                   List<String> warningList, List<String> errorList) {
        StringBuilder msg = new StringBuilder();
        msg.append("[OK] ").append(success).append(" môn đã thêm thành công\n");
        if (warnings > 0) {
            msg.append("[!] ").append(warnings).append(" môn không tìm thấy giảng viên -> Chưa phân công\n");
        }
        if (errorCount > 0) {
            msg.append("[X] ").append(errorCount).append(" dòng bị bỏ qua\n");
        }

        // Chi tiết lỗi/cảnh báo
        if (!warningList.isEmpty() || !errorList.isEmpty()) {
            msg.append("\nChi tiết:\n");
            for (String w : warningList) msg.append("  - ").append(w).append("\n");
            for (String e : errorList) msg.append("  - ").append(e).append("\n");
        }

        new AlertDialog.Builder(this)
            .setTitle("Kết quả Import CSV")
            .setMessage(msg.toString().trim())
            .setPositiveButton("Xem danh sách môn", (d, w) -> loadSubjects())
            .setNegativeButton("Đóng", null)
            .show();
    }

    /**
     * Update trạng thái empty state
     */
    private void updateEmptyState() {
        if (subjectList.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerSubjects.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            recyclerSubjects.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSubjects();
    }

    /**
     * Hiển thị dialog chi tiết môn học khi click vào item
     */
    private void showSubjectDetailDialog(SubjectWithSchedules subject) {
        StringBuilder info = new StringBuilder();
        info.append("Ma mon hoc: ").append(subject.getSubjectId()).append("\n\n");
        info.append("Ten mon hoc: ").append(subject.getSubjectName()).append("\n\n");

        String timeSlot = subject.getTimeSlot();
        if (timeSlot != null && !timeSlot.isEmpty()) {
            info.append("Khung gio hoc: ").append(timeSlot).append("\n\n");
        }

        if (subject.isAssigned() && subject.getInstructorName() != null) {
            info.append("Giang vien: ").append(subject.getInstructorName()).append("\n\n");
        } else {
            info.append("Giang vien: Chua phan cong\n\n");
        }

        info.append("Trang thai: ").append(subject.isAssigned() ? "Da phan cong" : "Chua phan cong").append("\n\n");

        if (subject.getCreatedAt() != null && !subject.getCreatedAt().isEmpty()) {
            info.append("Ngay tao: ").append(subject.getCreatedAt());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(subject.getSubjectName())
            .setMessage(info.toString().trim())
            .setPositiveButton("Dong", null)
            .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }
}

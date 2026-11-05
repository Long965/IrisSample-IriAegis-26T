package com.iritech.irissample;

import android.database.Cursor;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity để thêm/sửa môn học
 * - Chế độ ADD: Tạo mới môn học
 * - Chế độ EDIT: Sửa môn học hiện có
 * - Super Admin: có dropdown chọn giảng viên
 * - Admin: tự gán chính mình làm giảng viên
 */
public class AddSubjectActivity extends AppCompatActivity {

    private EditText edtSubjectId, edtSubjectName, edtTimeSlot;
    private Button btnSaveSubject;
    private LinearLayout layoutInstructorSection;
    private Spinner spinnerInstructor;
    private TextView txtInstructorHint;
    private DatabaseHelper dbHelper;
    private String currentUserEmail;
    private String currentAdminId;
    private String currentRole;
    private boolean isSuperAdmin;

    // Chế độ: "ADD" hoặc "EDIT"
    private String mode = "ADD";
    private String editSubjectId = null;

    // Danh sách admin cho dropdown
    private List<String> adminIdList = new ArrayList<>();
    private List<String> adminNameList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_subject);

        // Khởi tạo views
        edtSubjectId = findViewById(R.id.edtSubjectId);
        edtSubjectName = findViewById(R.id.edtSubjectName);
        edtTimeSlot = findViewById(R.id.edtTimeSlot);
        btnSaveSubject = findViewById(R.id.btnSaveSubject);
        layoutInstructorSection = findViewById(R.id.layoutInstructorSection);
        spinnerInstructor = findViewById(R.id.spinnerInstructor);
        txtInstructorHint = findViewById(R.id.txtInstructorHint);

        dbHelper = new DatabaseHelper(this);
        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        mode = getIntent().getStringExtra("MODE") != null ? getIntent().getStringExtra("MODE") : "ADD";
        editSubjectId = getIntent().getStringExtra("SUBJECT_ID");

        // Lấy admin_id và role từ email
        currentAdminId = dbHelper.getAdminIdByEmail(currentUserEmail);
        currentRole = dbHelper.getAdminRole(currentUserEmail);
        isSuperAdmin = "SUPER_ADMIN".equals(currentRole);

        setupInstructorDropdown();
        setupEditMode();

        // Listener cho nút lưu
        btnSaveSubject.setOnClickListener(v -> saveSubject());
    }

    /**
     * Setup dropdown chọn giảng viên (chỉ cho Super Admin)
     */
    private void setupInstructorDropdown() {
        if (!isSuperAdmin) {
            layoutInstructorSection.setVisibility(View.GONE);
            return;
        }

        layoutInstructorSection.setVisibility(View.VISIBLE);

        // Lấy danh sách Admin (giảng viên)
        adminIdList.clear();
        adminNameList.clear();

        // Option đầu tiên: "Phân công sau"
        adminIdList.add(null);
        adminNameList.add("— Phân công sau —");

        Cursor cursor = dbHelper.getAllAdmins();
        if (cursor != null && cursor.moveToFirst()) {
            do {
                String id = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_ID));
                String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_FULL_NAME));
                String code = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_ADMIN_CODE));
                adminIdList.add(id);
                adminNameList.add(name + (code != null ? " (" + code + ")" : ""));
            } while (cursor.moveToNext());
            cursor.close();
        }

        if (adminIdList.size() <= 1) {
            // Không có Admin nào → hiện thông báo
            txtInstructorHint.setText("Chưa có giảng viên nào trong hệ thống. Môn sẽ được lưu ở trạng thái Chưa phân công.");
            txtInstructorHint.setVisibility(View.VISIBLE);
            spinnerInstructor.setEnabled(false);
        } else {
            txtInstructorHint.setText("Bạn có thể gán giảng viên sau trong phần Sửa môn.");
            txtInstructorHint.setVisibility(View.VISIBLE);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, adminNameList);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInstructor.setAdapter(adapter);
    }

    /**
     * Setup chế độ sửa (pre-fill dữ liệu)
     */
    private void setupEditMode() {
        if (!"EDIT".equals(mode) || editSubjectId == null) return;

        edtSubjectId.setText(editSubjectId);
        edtSubjectId.setEnabled(false); // Không cho sửa mã môn
        btnSaveSubject.setText("Cập nhật môn học");

        Cursor cursor = dbHelper.getSubjectById(editSubjectId);
        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_NAME));
            String timeSlot = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TIME_SLOT));
            String instructorId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SUBJECT_INSTRUCTOR_ID));

            edtSubjectName.setText(name);
            edtTimeSlot.setText(timeSlot != null ? timeSlot : "");

            // Chọn đúng giảng viên trong spinner (cho Super Admin)
            if (isSuperAdmin && instructorId != null) {
                for (int i = 0; i < adminIdList.size(); i++) {
                    if (instructorId.equals(adminIdList.get(i))) {
                        spinnerInstructor.setSelection(i);
                        break;
                    }
                }
            }
            cursor.close();
        }
    }

    /**
     * Lưu hoặc cập nhật môn học vào database
     */
    private void saveSubject() {
        String id = edtSubjectId.getText().toString().trim();
        String name = edtSubjectName.getText().toString().trim();
        String timeSlot = edtTimeSlot.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(id)) {
            Toast.makeText(this, "Vui lòng nhập mã môn học", Toast.LENGTH_SHORT).show();
            edtSubjectId.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "Vui lòng nhập tên môn học", Toast.LENGTH_SHORT).show();
            edtSubjectName.requestFocus();
            return;
        }

        // Xác định instructor_id
        String instructorId;
        if (isSuperAdmin) {
            int selectedPos = spinnerInstructor.getSelectedItemPosition();
            instructorId = (selectedPos > 0) ? adminIdList.get(selectedPos) : null;
        } else {
            // Admin tự gán chính mình
            instructorId = currentAdminId;
        }

        if ("EDIT".equals(mode)) {
            // Chế độ sửa
            boolean success;
            if (isSuperAdmin) {
                success = dbHelper.updateSubjectBySuperAdmin(id, name, timeSlot, instructorId);
            } else {
                success = dbHelper.updateSubjectByAdmin(id, currentAdminId, name, timeSlot);
            }
            if (success) {
                Toast.makeText(this, "Cập nhật môn học thành công!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Lỗi khi cập nhật môn học", Toast.LENGTH_SHORT).show();
            }
        } else {
            // Chế độ thêm mới
            long result = dbHelper.createSubject(id, name, timeSlot, currentAdminId, instructorId);
            if (result != -1) {
                Toast.makeText(this, "Thêm môn học thành công!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Lỗi: Mã môn học đã tồn tại", Toast.LENGTH_SHORT).show();
            }
        }
    }
}

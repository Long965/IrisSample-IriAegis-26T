package com.iritech.irissample;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.CheckBox;
import android.widget.EditText;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.InputType;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * MockCheckinActivity - Activity để TEST điểm danh nhanh
 * CHO PHÉP điểm danh BẤT KỲ LÚC NÀO, không bị giới hạn thời gian
 * Thích hợp cho DEMO với leader
 */
public class MockCheckinActivity extends AppCompatActivity {

    private DatabaseHelper dbHelper;
    private Spinner spinnerSubject;
    private ListView listViewStudents;
    private Button btnCheckinAll;
    private Button btnRandomCheckin;
    private CheckBox chkUseCustomTime;
    private EditText edtCustomTime;
    private Button btnBack;
    private Button btnClearToday;

    private List<Subject> subjectList = new ArrayList<>();
    private List<StudentInfo> studentList = new ArrayList<>();
    private ArrayAdapter<String> studentAdapter;
    
    private String selectedSubjectId;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    // Class để lưu thông tin môn học
    private static class Subject {
        String id;
        String name;

        Subject(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    // Class để lưu thông tin sinh viên
    private static class StudentInfo {
        String studentId;
        String fullName;
        boolean checkedIn;
        String checkinTime;

        StudentInfo(String studentId, String fullName) {
            this.studentId = studentId;
            this.fullName = fullName;
            this.checkedIn = false;
            this.checkinTime = null;
        }

        @Override
        public String toString() {
            if (checkedIn) {
                return "[x] " + studentId + " - " + fullName + " (" + checkinTime + ")";
            }
            return "[ ] " + studentId + " - " + fullName;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mock_checkin);

        dbHelper = new DatabaseHelper(this);
        
        // Initialize views
        spinnerSubject = findViewById(R.id.spinnerSubject);
        listViewStudents = findViewById(R.id.listViewStudents);
        btnCheckinAll = findViewById(R.id.btnCheckinAll);
        btnRandomCheckin = findViewById(R.id.btnRandomCheckin);
        chkUseCustomTime = findViewById(R.id.chkUseCustomTime);
        edtCustomTime = findViewById(R.id.edtCustomTime);
        btnBack = findViewById(R.id.btnBack);
        btnClearToday = findViewById(R.id.btnClearToday);

        // Setup adapters
        setupSubjectSpinner();
        setupStudentListView();
        
        // Setup button listeners
        btnCheckinAll.setOnClickListener(v -> checkinAllStudents());
        btnRandomCheckin.setOnClickListener(v -> randomCheckinAllStudents());
        btnClearToday.setOnClickListener(v -> clearTodayCheckins());
        btnBack.setOnClickListener(v -> finish());
        
        // Toggle custom time input
        chkUseCustomTime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            edtCustomTime.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                edtCustomTime.setText(timeFormat.format(new Date()));
            }
        });
    }

    private void setupSubjectSpinner() {
        // Load subjects from database
        subjectList.clear();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT " + DatabaseHelper.COL_SUBJECT_ID + ", " + 
                                    DatabaseHelper.COL_SUBJECT_NAME + 
                                    " FROM " + DatabaseHelper.TABLE_SUBJECTS, null);
        
        List<String> subjectNames = new ArrayList<>();
        while (cursor.moveToNext()) {
            String id = cursor.getString(0);
            String name = cursor.getString(1);
            subjectList.add(new Subject(id, name));
            subjectNames.add(name);
        }
        cursor.close();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, subjectNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSubject.setAdapter(adapter);
        
        spinnerSubject.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSubjectId = subjectList.get(position).id;
                loadStudents();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void setupStudentListView() {
        studentAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_list_item_1, new ArrayList<String>());
        listViewStudents.setAdapter(studentAdapter);
        
        // Click để checkin từng sinh viên với dialog nhập thời gian
        listViewStudents.setOnItemClickListener((parent, view, position, id) -> {
            StudentInfo student = studentList.get(position);
            showCheckinDialog(student, position);
        });
    }

    private void loadStudents() {
        studentList.clear();
        
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String query = "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", " +
                      "s." + DatabaseHelper.COL_FULL_NAME + 
                      " FROM " + DatabaseHelper.TABLE_STUDENTS + " s " +
                      "INNER JOIN " + DatabaseHelper.TABLE_ENROLLMENTS + " e " +
                      "ON s." + DatabaseHelper.COL_STUDENT_ID + " = e." + DatabaseHelper.COL_STUDENT_ID + 
                      " WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{selectedSubjectId});
        
        List<String> studentNames = new ArrayList<>();
        while (cursor.moveToNext()) {
            String studentId = cursor.getString(0);
            String fullName = cursor.getString(1);
            StudentInfo student = new StudentInfo(studentId, fullName);
            studentList.add(student);
            studentNames.add(student.toString());
        }
        cursor.close();
        
        studentAdapter.clear();
        studentAdapter.addAll(studentNames);
        studentAdapter.notifyDataSetChanged();
        
        Toast.makeText(this, "Đã load " + studentList.size() + " sinh viên", 
                      Toast.LENGTH_SHORT).show();
    }

    private void checkinStudent(String studentId, String studentName) {
        String currentDate = dateFormat.format(new Date());
        String currentTime;
        
        // Kiểm tra xem có dùng custom time không
        if (chkUseCustomTime.isChecked() && !edtCustomTime.getText().toString().isEmpty()) {
            currentTime = edtCustomTime.getText().toString().trim();
        } else {
            currentTime = timeFormat.format(new Date());
        }
        
        // Insert vào database đơn giản
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(DatabaseHelper.COL_STUDENT_ID, studentId);
        values.put(DatabaseHelper.COL_SUBJECT_ID, selectedSubjectId);
        values.put(DatabaseHelper.COL_CHECKIN_TIME, currentTime);
        values.put(DatabaseHelper.COL_CHECKIN_DATE, currentDate);
        
        long rowId = db.insert(DatabaseHelper.TABLE_CHECKIN_HISTORY, null, values);
        
        if (rowId != -1) {
            Toast.makeText(this, studentName + " - Điểm danh lúc: " + currentTime, 
                          Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Lỗi khi điểm danh " + studentName, 
                          Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Hiển thị dialog nhập thời gian cho từng sinh viên
     */
    private void showCheckinDialog(StudentInfo student, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Điểm danh: " + student.fullName);
        
        // Tạo EditText nhập thời gian
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("HH:mm (ví dụ: 08:30)");
        input.setText(timeFormat.format(new Date()));
        input.setSelection(input.getText().length());
        builder.setView(input);
        
        // Thêm các suggestions nhanh
        builder.setMessage("Chọn nhanh hoặc nhập thời gian:\n\n" +
                          "• Đúng giờ: 07:05\n" +
                          "• Muộn 1h: 08:05\n" +
                          "• Muộn 2h: 09:05\n" +
                          "• Hoặc nhập tùy chọn");
        
        builder.setPositiveButton("Điểm danh", (dialog, which) -> {
            String time = input.getText().toString().trim();
            if (time.isEmpty()) {
                time = timeFormat.format(new Date());
            }
            checkinStudentWithTime(student, position, time);
        });
        
        builder.setNeutralButton("Random", (dialog, which) -> {
            String randomTime = generateRandomTime();
            checkinStudentWithTime(student, position, randomTime);
        });
        
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }
    
    /**
     * Điểm danh sinh viên với thời gian cụ thể
     */
    private void checkinStudentWithTime(StudentInfo student, int position, String time) {
        String currentDate = dateFormat.format(new Date());
        
        // Insert vào database đơn giản
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(DatabaseHelper.COL_STUDENT_ID, student.studentId);
        values.put(DatabaseHelper.COL_SUBJECT_ID, selectedSubjectId);
        values.put(DatabaseHelper.COL_CHECKIN_TIME, time);
        values.put(DatabaseHelper.COL_CHECKIN_DATE, currentDate);
        
        long rowId = db.insert(DatabaseHelper.TABLE_CHECKIN_HISTORY, null, values);
        
        if (rowId != -1) {
            // Update student info
            student.checkedIn = true;
            student.checkinTime = time;
            
            // Refresh ListView
            studentAdapter.notifyDataSetChanged();
            
            Toast.makeText(this, student.fullName + " - Điểm danh lúc: " + time, 
                          Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Lỗi khi điểm danh " + student.fullName, 
                          Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Tạo thời gian random thực tế (cho demo)
     */
    private String generateRandomTime() {
        // Tạo thời gian random trong khoảng 7:00 - 17:00
        int startHour = 7;
        int startMinute = 0;
        
        // Random: 30% sớm, 40% đúng giờ, 30% muộn
        int random = (int)(Math.random() * 100);
        int offsetMinutes;
        
        if (random < 30) {
            // Sớm 5-15 phút
            offsetMinutes = -5 - (int)(Math.random() * 10);
        } else if (random < 70) {
            // Đúng giờ (0-30 phút sau)
            offsetMinutes = (int)(Math.random() * 30);
        } else {
            // Muộn (60-120 phút sau)
            offsetMinutes = 60 + (int)(Math.random() * 60);
        }
        
        int totalMinutes = startHour * 60 + startMinute + offsetMinutes;
        int hour = (totalMinutes / 60) % 24;
        int minute = totalMinutes % 60;
        
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }
    
    /**
     * Random điểm danh tất cả với thời gian đa dạng
     */
    private void randomCheckinAllStudents() {
        if (studentList.isEmpty()) {
            Toast.makeText(this, "Không có sinh viên để điểm danh!", 
                          Toast.LENGTH_SHORT).show();
            return;
        }
        
        int successCount = 0;
        for (int i = 0; i < studentList.size(); i++) {
            StudentInfo student = studentList.get(i);
            String randomTime = generateRandomTime();
            checkinStudentWithTime(student, i, randomTime);
            successCount++;
        }
        
        Toast.makeText(this, "Đã random điểm danh " + successCount + " sinh viên!\n" +
                      "(Có người sớm, đúng giờ, muộn - data thực tế)", 
                      Toast.LENGTH_LONG).show();
    }
    
    /**
     * Điểm danh tất cả với thời gian hiện tại
     */
    private void checkinAllStudents() {
        if (studentList.isEmpty()) {
            Toast.makeText(this, "Không có sinh viên để điểm danh!", 
                          Toast.LENGTH_SHORT).show();
            return;
        }
        
        String currentTime = timeFormat.format(new Date());
        int successCount = 0;
        
        for (int i = 0; i < studentList.size(); i++) {
            StudentInfo student = studentList.get(i);
            checkinStudentWithTime(student, i, currentTime);
            successCount++;
        }
        
        Toast.makeText(this, "Đã điểm danh " + successCount + " sinh viên cùng lúc!", 
                      Toast.LENGTH_LONG).show();
    }
    
    /**
     * Xóa dữ liệu điểm danh hôm nay
     */
    private void clearTodayCheckins() {
        new AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Xóa tất cả dữ liệu điểm danh hôm nay của môn này?")
            .setPositiveButton("Xóa", (dialog, which) -> {
                String today = dateFormat.format(new Date());
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                int deleted = db.delete(DatabaseHelper.TABLE_CHECKIN_HISTORY,
                    DatabaseHelper.COL_CHECKIN_DATE + " = ? AND " +
                    DatabaseHelper.COL_SUBJECT_ID + " = ?",
                    new String[]{today, selectedSubjectId});
                
                // Reset student status
                for (StudentInfo student : studentList) {
                    student.checkedIn = false;
                    student.checkinTime = null;
                }
                studentAdapter.notifyDataSetChanged();
                
                Toast.makeText(this, "Đã xóa " + deleted + " bản ghi", 
                              Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Hủy", null)
            .show();
    }
}

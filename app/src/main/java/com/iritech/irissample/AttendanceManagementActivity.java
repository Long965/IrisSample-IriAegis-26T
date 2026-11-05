package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.tabs.TabLayout;
import com.iritech.irissample.adapter.AttendanceRecordAdapter;
import com.iritech.irissample.adapter.EmailRecipientAdapter;
import com.iritech.irissample.model.AttendanceRecord;
import com.iritech.irissample.model.EmailRecipient;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class AttendanceManagementActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_STORAGE_PERMISSION = 101;
    private static final int REQUEST_CODE_IMPORT_EMAIL_CSV = 102;
    private static final int REQUEST_CODE_CREATE_CSV_DOCUMENT = 104;

    private static final String CSV_DELIMITER = ";";

    private static final String EXPORT_MODE_DAY = "DAY";
    private static final String EXPORT_MODE_SEMESTER = "SEMESTER";
    private String exportMode = EXPORT_MODE_DAY;

    private TextView textSubjectInfo, textRecordCount;
    private Button btnTabExport, btnTabEmail, btnTabItemView;
    private ScrollView layoutExportTab;

    private RelativeLayout layoutEmailTab;

    private LinearLayout layoutViewTab;
    private TabLayout subTabLayout;

    private Button btnExportCsv;

    private EditText editEmailAddress, editRecipientName;
    private Button btnAddEmail, btnImportEmailCsv, btnSendEmail;
    private CheckBox checkBoxSelectAll;
    private TextView textSelectedCount, textEmptyEmailList;
    private RecyclerView recyclerEmailList;
    private EmailRecipientAdapter emailAdapter;
    private List<EmailRecipient> emailRecipientList = new ArrayList<>();

    private TabLayout viewModeTabLayout;
    private Button btnSelectDateRange;

    private ScrollView layoutSubTabOverview;
    private PieChart pieChartAttendance;
    private TextView txtNoChartDataMessage;
    private GridLayout gridChartStats;
    private TextView txtStatRow1, txtStatRow2, txtStatRow3, txtStatRow4;

    private LinearLayout layoutSubTabDetail;
    private TabLayout statusTabLayout;
    private TextView textViewStats, textEmptyAttendanceList;
    private RecyclerView recyclerAttendanceList;
    private AttendanceRecordAdapter attendanceAdapter;

    private DatabaseHelper dbHelper;
    private String currentSubjectId;
    private String currentSubjectName = "";
    private String subjectTimeSlotStr = "";
    private String currentInstructorName = "";

    private List<AttendanceRecord> fullRecordList = new ArrayList<>();
    private List<AttendanceRecord> displayRecordList = new ArrayList<>();
    private String filterStartDate = null;
    private String filterEndDate = null;

    private boolean isSemesterMode = false;
    private String singleDaySelectedDate;

    private boolean hasCheckinDataForPeriod = false;

    // ĐÃ THÊM: Biến cờ hiệu kiểm tra trạng thái xuất file
    private boolean isFileExported = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_management);

        dbHelper = new DatabaseHelper(this);
        currentSubjectId = getIntent().getStringExtra("subject_id");

        if (currentSubjectId == null) {
            Toast.makeText(this, "Không tìm thấy mã môn học!", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        String todayStr = sdf.format(cal.getTime());
        singleDaySelectedDate = todayStr;
        filterStartDate = todayStr;
        filterEndDate = todayStr;

        initViews();
        setupToolbar();
        setupMainTabs();
        setupSubTabs();
        setupViewModeTabs();
        setupEmailComponent();
        setupAttendanceComponent();

        btnSelectDateRange.setText("Ngày: " + todayStr);

        loadSubjectInfo();
        loadAttendanceRecords();
    }

    private void initViews() {
        textSubjectInfo = findViewById(R.id.textSubjectInfo);
        textRecordCount = findViewById(R.id.textRecordCount);

        btnTabExport = findViewById(R.id.btnTabExport);
        btnTabEmail = findViewById(R.id.btnTabEmail);
        btnTabItemView = findViewById(R.id.btnTabItemView);

        layoutExportTab = findViewById(R.id.layoutExportTab);
        layoutEmailTab = findViewById(R.id.layoutEmailTab);

        layoutViewTab = findViewById(R.id.layoutViewTab);
        subTabLayout = findViewById(R.id.subTabLayout);

        btnExportCsv = findViewById(R.id.btnExportCsv);
        btnExportCsv.setOnClickListener(v -> showExportModeDialog());

        editEmailAddress = findViewById(R.id.editEmailAddress);
        editRecipientName = findViewById(R.id.editRecipientName);
        btnAddEmail = findViewById(R.id.btnAddEmail);
        btnImportEmailCsv = findViewById(R.id.btnImportEmailCsv);
        checkBoxSelectAll = findViewById(R.id.checkBoxSelectAll);
        textSelectedCount = findViewById(R.id.textSelectedCount);
        textEmptyEmailList = findViewById(R.id.textEmptyEmailList);
        recyclerEmailList = findViewById(R.id.recyclerEmailList);
        btnSendEmail = findViewById(R.id.btnSendEmail);

        viewModeTabLayout = findViewById(R.id.viewModeTabLayout);
        btnSelectDateRange = findViewById(R.id.btnSelectDateRange);

        layoutSubTabOverview = findViewById(R.id.layoutSubTabOverview);
        pieChartAttendance = findViewById(R.id.pieChartAttendance);
        txtNoChartDataMessage = findViewById(R.id.txtNoChartDataMessage);
        gridChartStats = findViewById(R.id.gridChartStats);
        txtStatRow1 = findViewById(R.id.txtStatRow1);
        txtStatRow2 = findViewById(R.id.txtStatRow2);
        txtStatRow3 = findViewById(R.id.txtStatRow3);
        txtStatRow4 = findViewById(R.id.txtStatRow4);

        layoutSubTabDetail = findViewById(R.id.layoutSubTabDetail);
        statusTabLayout = findViewById(R.id.statusTabLayout);
        textViewStats = findViewById(R.id.textViewStats);
        textEmptyAttendanceList = findViewById(R.id.textEmptyAttendanceList);
        recyclerAttendanceList = findViewById(R.id.recyclerAttendanceList);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> onBackPressed());
        }
    }

    private void setupMainTabs() {
        View.OnClickListener tabClickListener = v -> {
            resetTabButtons();
            layoutExportTab.setVisibility(View.GONE);
            layoutEmailTab.setVisibility(View.GONE);
            layoutViewTab.setVisibility(View.GONE);

            int id = v.getId();
            if (id == R.id.btnTabExport) {
                btnTabExport.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                layoutExportTab.setVisibility(View.VISIBLE);
            } else if (id == R.id.btnTabEmail) {
                btnTabEmail.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                layoutEmailTab.setVisibility(View.VISIBLE);
            } else if (id == R.id.btnTabItemView) {
                btnTabItemView.setTextColor(ContextCompat.getColor(this, R.color.colorPrimaryDark));
                layoutViewTab.setVisibility(View.VISIBLE);
                applyFilterAndRefresh();
            }
        };

        btnTabExport.setOnClickListener(tabClickListener);
        btnTabEmail.setOnClickListener(tabClickListener);
        btnTabItemView.setOnClickListener(tabClickListener);
    }

    private void resetTabButtons() {
        int colorSec = ContextCompat.getColor(this, R.color.colorTextSecondary);
        btnTabExport.setTextColor(colorSec);
        btnTabEmail.setTextColor(colorSec);
        btnTabItemView.setTextColor(colorSec);
    }

    private void setupSubTabs() {
        subTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutSubTabOverview.setVisibility(View.VISIBLE);
                    layoutSubTabDetail.setVisibility(View.GONE);
                    calculateAndShowChart();
                } else {
                    layoutSubTabOverview.setVisibility(View.GONE);
                    layoutSubTabDetail.setVisibility(View.VISIBLE);
                    loadAttendanceRecords();
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnSelectDateRange.setOnClickListener(v -> {
            if (isSemesterMode) {
                Toast.makeText(this, "Đang xem theo kì học, chuyển sang \"Theo ngày\" để chọn ngày cụ thể.", Toast.LENGTH_SHORT).show();
                return;
            }
            pickSingleDate();
        });
    }

    private void setupViewModeTabs() {
        if (viewModeTabLayout != null) {
            viewModeTabLayout.removeAllTabs();
            viewModeTabLayout.addTab(viewModeTabLayout.newTab().setText("Theo ngày"));
            viewModeTabLayout.addTab(viewModeTabLayout.newTab().setText("Theo kì"));

            viewModeTabLayout.setTabMode(TabLayout.MODE_FIXED);
            viewModeTabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
            viewModeTabLayout.setTabTextColors(Color.parseColor("#757575"), Color.parseColor("#00796B"));
            viewModeTabLayout.setSelectedTabIndicatorColor(Color.parseColor("#00796B"));
            viewModeTabLayout.setSelectedTabIndicatorHeight((int) (3 * getResources().getDisplayMetrics().density));

            viewModeTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 0) {
                        switchToDayMode();
                    } else {
                        switchToSemesterMode();
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void switchToDayMode() {
        isSemesterMode = false;

        if (singleDaySelectedDate == null) {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            singleDaySelectedDate = sdf.format(Calendar.getInstance().getTime());
        }
        filterStartDate = singleDaySelectedDate;
        filterEndDate = singleDaySelectedDate;

        btnSelectDateRange.setText("Ngày: " + singleDaySelectedDate);
        btnSelectDateRange.setEnabled(true);
        btnSelectDateRange.setAlpha(1f);

        applyFilterAndRefresh();
    }

    private void switchToSemesterMode() {
        isSemesterMode = true;

        String[] range = dbHelper.getSubjectSemesterRange(currentSubjectId);
        if (range != null && range[0] != null && range[1] != null) {
            filterStartDate = range[0];
            filterEndDate = range[1];
            btnSelectDateRange.setText("Kì học: " + range[0] + " → " + range[1]);
        } else {
            filterStartDate = null;
            filterEndDate = null;
            btnSelectDateRange.setText("Kì học: Không xác định");
        }

        btnSelectDateRange.setEnabled(false);
        btnSelectDateRange.setAlpha(0.5f);

        applyFilterAndRefresh();
    }

    private void pickSingleDate() {
        Calendar cal = Calendar.getInstance();
        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(year, month, dayOfMonth);
            String pickedDate = sdf.format(cal.getTime());
            singleDaySelectedDate = pickedDate;
            filterStartDate = pickedDate;
            filterEndDate = pickedDate;
            btnSelectDateRange.setText("Ngày: " + pickedDate);
            applyFilterAndRefresh();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void setupEmailComponent() {
        recyclerEmailList.setLayoutManager(new LinearLayoutManager(this));
        emailAdapter = new EmailRecipientAdapter(this, emailRecipientList);

        emailAdapter.setOnRecipientActionListener(new EmailRecipientAdapter.OnRecipientActionListener() {
            @Override
            public void onDeleteClick(EmailRecipient recipient) {
                emailRecipientList.remove(recipient);
                emailAdapter.notifyDataSetChanged();
                checkEmailListEmptyState();
                updateSelectedCount(emailAdapter.getSelectedCount());
            }

            @Override
            public void onSelectionChanged(int selectedCount) {
                updateSelectedCount(selectedCount);
            }
        });

        recyclerEmailList.setAdapter(emailAdapter);

        btnAddEmail.setOnClickListener(v -> {
            String email = editEmailAddress.getText().toString().trim();
            String name = editRecipientName.getText().toString().trim();

            if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Địa chỉ email không hợp lệ!", Toast.LENGTH_SHORT).show();
                return;
            }

            EmailRecipient recipient = new EmailRecipient();
            recipient.setEmailAddress(email);
            recipient.setRecipientName(name.isEmpty() ? "Người nhận" : name);
            recipient.setSelected(true);

            emailRecipientList.add(recipient);
            emailAdapter.notifyDataSetChanged();
            checkEmailListEmptyState();
            updateSelectedCount(emailAdapter.getSelectedCount());

            editEmailAddress.setText("");
            editRecipientName.setText("");
        });

        btnImportEmailCsv.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/*");
            startActivityForResult(intent, REQUEST_CODE_IMPORT_EMAIL_CSV);
        });

        checkBoxSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (emailAdapter != null) {
                emailAdapter.selectAll(isChecked);
            }
        });

        btnSendEmail.setOnClickListener(v -> {
            // ĐÃ SỬA: Ràng buộc yêu cầu xuất file CSV trước khi gửi
            if (!isFileExported) {
                Toast.makeText(this, "Vui lòng xuất file báo cáo trước khi gửi email!", Toast.LENGTH_LONG).show();
                return;
            }

            String[] recipients = emailAdapter.getSelectedEmails();
            if (recipients == null || recipients.length == 0) {
                Toast.makeText(this, "Vui lòng thêm và chọn ít nhất một email từ danh sách!", Toast.LENGTH_SHORT).show();
                return;
            }

            java.io.File cacheDir = getCacheDir();
            java.io.File tempCsvFile = new java.io.File(cacheDir, "BaoCaoDiemDanh_" + currentSubjectId + ".csv");
            try {
                java.io.FileOutputStream fos = new java.io.FileOutputStream(tempCsvFile);
                if (EXPORT_MODE_SEMESTER.equals(exportMode)) {
                    writeSemesterModeCsv(fos);
                } else {
                    writeDayModeCsv(fos);
                }
                fos.close();
            } catch (java.io.IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Lỗi khi tạo tệp đính kèm!", Toast.LENGTH_SHORT).show();
                return;
            }

            String subject = "Báo cáo điểm danh môn: " + currentSubjectName;
            String htmlBody = "<html><body><h3>Kính gửi</h3><p>Đây là báo cáo lịch sử dữ liệu điểm danh được gửi tự động từ ứng dụng quản lý.</p></body></html>";

            Toast.makeText(this, "Đang tiến hành gửi email...", Toast.LENGTH_SHORT).show();

            EmailService.sendAttendanceReportEmail(recipients, subject, htmlBody, tempCsvFile, new EmailService.EmailCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> Toast.makeText(AttendanceManagementActivity.this, "Gửi email báo cáo thành công!", Toast.LENGTH_LONG).show());
                    if (tempCsvFile.exists()) tempCsvFile.delete();
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(AttendanceManagementActivity.this, "Gửi email thất bại: " + error, Toast.LENGTH_SHORT).show());
                    if (tempCsvFile.exists()) tempCsvFile.delete();
                }
            });
        });
    }

    private void setupAttendanceComponent() {
        recyclerAttendanceList.setLayoutManager(new LinearLayoutManager(this));
        attendanceAdapter = new AttendanceRecordAdapter(this, displayRecordList);
        attendanceAdapter.setOnItemClickListener(this::showStudentStatsDialog);
        recyclerAttendanceList.setAdapter(attendanceAdapter);

        if (statusTabLayout != null) {
            statusTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    filterAttendanceList("");
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {}

                @Override
                public void onTabReselected(TabLayout.Tab tab) {}
            });
        }
    }

    private void loadSubjectInfo() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String query = "SELECT s." + DatabaseHelper.COL_SUBJECT_NAME + ", s." + DatabaseHelper.COL_TIME_SLOT + ", a." + DatabaseHelper.COL_ADMIN_FULL_NAME +
                " FROM " + DatabaseHelper.TABLE_SUBJECTS + " s" +
                " LEFT JOIN " + DatabaseHelper.TABLE_ADMIN + " a ON s." + DatabaseHelper.COL_SUBJECT_INSTRUCTOR_ID + " = a." + DatabaseHelper.COL_ADMIN_ID +
                " WHERE s." + DatabaseHelper.COL_SUBJECT_ID + " = ?";

        Cursor subjectCursor = db.rawQuery(query, new String[]{currentSubjectId});

        if (subjectCursor.moveToFirst()) {
            currentSubjectName = subjectCursor.getString(0);
            subjectTimeSlotStr = subjectCursor.getString(1);
            currentInstructorName = subjectCursor.getString(2);

            if (currentInstructorName == null) {
                currentInstructorName = "Chưa phân công";
            }
            if (subjectTimeSlotStr == null) subjectTimeSlotStr = "";

            if (textSubjectInfo != null) {
                textSubjectInfo.setText("Môn học: " + currentSubjectName);
            }
        }
        subjectCursor.close();

        Cursor countCursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
        );

        if (countCursor.moveToFirst() && textRecordCount != null) {
            textRecordCount.setText("Số bản ghi điểm danh: " + countCursor.getInt(0));
        }
        countCursor.close();
    }

    private void applyFilterAndRefresh() {
        // ĐÃ SỬA: Reset cờ hiệu khi thay đổi tiêu chí bộ lọc thông tin
        isFileExported = false;

        loadAttendanceRecords();
        if (subTabLayout.getSelectedTabPosition() == 0) {
            calculateAndShowChart();
        }
    }

    private String[] computeStartTimeWindow() {
        String startTimeStart = "08:00:00";
        String startTimeLimit = "08:15:00";

        if (subjectTimeSlotStr != null && subjectTimeSlotStr.contains("-") && subjectTimeSlotStr.contains("đến")) {
            try {
                String timePart = subjectTimeSlotStr.split("-")[1].trim();
                String startHourStr = timePart.split("đến")[0].trim();

                String[] hourMin = startHourStr.split(":");
                int hour = Integer.parseInt(hourMin[0].trim());
                int min = Integer.parseInt(hourMin[1].trim());

                startTimeStart = String.format(Locale.getDefault(), "%02d:%02d:00", hour, min);

                min += 15;
                if (min >= 60) {
                    hour += 1;
                    min -= 60;
                }
                startTimeLimit = String.format(Locale.getDefault(), "%02d:%02d:00", hour, min);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return new String[]{startTimeStart, startTimeLimit};
    }

    private String classifyCheckinStatus(String checkinTime, String startTimeStart, String startTimeLimit) {
        if (checkinTime == null) {
            return AttendanceRecord.STATUS_ON_TIME;
        }
        if (checkinTime.compareTo(startTimeLimit) > 0) {
            return AttendanceRecord.STATUS_LATE;
        }
        return AttendanceRecord.STATUS_ON_TIME;
    }

    private boolean isDateInRange(String dateStr, String startStr, String endStr) {
        if (dateStr == null) return false;
        if (startStr == null || endStr == null) return true;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            java.util.Date d = sdf.parse(dateStr);
            java.util.Date start = sdf.parse(startStr);
            java.util.Date end = sdf.parse(endStr);
            if (d == null || start == null || end == null) return false;
            return !d.before(start) && !d.after(end);
        } catch (Exception e) {
            return false;
        }
    }

    private void loadAttendanceRecords() {
        fullRecordList.clear();

        if (isSemesterMode) {
            loadSemesterAggregateRecords();
            displayRecordList.clear();
            displayRecordList.addAll(fullRecordList);
            filterAttendanceList("");
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        StringBuilder sql = new StringBuilder(
                "SELECT h." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME +
                        ", h." + DatabaseHelper.COL_CHECKIN_DATE + ", h." + DatabaseHelper.COL_CHECKIN_TIME +
                        " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " h" +
                        " JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON h." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID +
                        " WHERE h." + DatabaseHelper.COL_SUBJECT_ID + " = ?"
        );

        List<String> args = new ArrayList<>();
        args.add(currentSubjectId);

        if (filterStartDate != null && filterEndDate != null) {
            sql.append(" AND h.").append(DatabaseHelper.COL_CHECKIN_DATE).append(" BETWEEN ? AND ?");
            args.add(filterStartDate);
            args.add(filterEndDate);
        }

        sql.append(" ORDER BY h.").append(DatabaseHelper.COL_CHECKIN_DATE).append(" DESC, h.").append(DatabaseHelper.COL_CHECKIN_TIME).append(" DESC");

        String[] timeWindow = computeStartTimeWindow();
        String startTimeStart = timeWindow[0];
        String startTimeLimit = timeWindow[1];

        Cursor cursor = db.rawQuery(sql.toString(), args.toArray(new String[0]));

        hasCheckinDataForPeriod = cursor.getCount() > 0;

        if (cursor.moveToFirst()) {
            do {
                String studentId = cursor.getString(0);
                String fullName = cursor.getString(1);
                String checkinDate = cursor.getString(2);
                String checkinTime = cursor.getString(3);
                String status = classifyCheckinStatus(checkinTime, startTimeStart, startTimeLimit);

                AttendanceRecord r = new AttendanceRecord(studentId, fullName, status, checkinDate, checkinTime);
                fullRecordList.add(r);
            } while (cursor.moveToNext());
        }
        cursor.close();

        Cursor absentCursor = dbHelper.getAbsentStudents(currentSubjectId, filterStartDate, filterEndDate);
        if (absentCursor != null) {
            if (absentCursor.moveToFirst()) {
                do {
                    String studentId = absentCursor.getString(0);
                    String fullName = absentCursor.getString(1);
                    fullRecordList.add(new AttendanceRecord(studentId, fullName));
                } while (absentCursor.moveToNext());
            }
            absentCursor.close();
        }

        displayRecordList.clear();
        displayRecordList.addAll(fullRecordList);

        filterAttendanceList("");
    }

    private void loadSemesterAggregateRecords() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        List<String[]> enrolledStudents = new ArrayList<>();
        Cursor enrollCursor = db.rawQuery(
                "SELECT e." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME +
                        " FROM " + DatabaseHelper.TABLE_ENROLLMENTS + " e" +
                        " JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON e." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID +
                        " WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
        );
        if (enrollCursor.moveToFirst()) {
            do {
                enrolledStudents.add(new String[]{enrollCursor.getString(0), enrollCursor.getString(1)});
            } while (enrollCursor.moveToNext());
        }
        enrollCursor.close();

        List<String> sessionDates = new ArrayList<>();
        Cursor dateCursor = db.rawQuery(
                "SELECT DISTINCT " + DatabaseHelper.COL_CHECKIN_DATE +
                        " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
        );
        if (dateCursor.moveToFirst()) {
            do {
                String d = dateCursor.getString(0);
                if (isDateInRange(d, filterStartDate, filterEndDate)) {
                    sessionDates.add(d);
                }
            } while (dateCursor.moveToNext());
        }
        dateCursor.close();
        int totalSessions = sessionDates.size();

        String[] timeWindow = computeStartTimeWindow();
        String startTimeStart = timeWindow[0];
        String startTimeLimit = timeWindow[1];

        for (String[] studentInfo : enrolledStudents) {
            String studentId = studentInfo[0];
            String fullName = studentInfo[1];

            int onTimeCount = 0;
            int lateCount = 0;

            Cursor checkinCursor = db.rawQuery(
                    "SELECT " + DatabaseHelper.COL_CHECKIN_DATE + ", " + DatabaseHelper.COL_CHECKIN_TIME +
                            " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? AND " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                    new String[]{currentSubjectId, studentId}
            );
            if (checkinCursor.moveToFirst()) {
                do {
                    String checkinDate = checkinCursor.getString(0);
                    String checkinTime = checkinCursor.getString(1);

                    if (!isDateInRange(checkinDate, filterStartDate, filterEndDate)) {
                        continue;
                    }

                    String status = classifyCheckinStatus(checkinTime, startTimeStart, startTimeLimit);
                    if (AttendanceRecord.STATUS_LATE.equals(status)) {
                        lateCount++;
                    } else {
                        onTimeCount++;
                    }
                } while (checkinCursor.moveToNext());
            }
            checkinCursor.close();

            int absentCount = totalSessions - onTimeCount - lateCount;
            if (absentCount < 0) absentCount = 0;

            fullRecordList.add(new AttendanceRecord(studentId, fullName, onTimeCount, lateCount, absentCount, totalSessions));
        }
    }

    private void calculateAndShowChart() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        List<String> enrolledStudentIds = new ArrayList<>();
        Cursor enrollCursor = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_STUDENT_ID +
                        " FROM " + DatabaseHelper.TABLE_ENROLLMENTS +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
        );
        if (enrollCursor.moveToFirst()) {
            do {
                enrolledStudentIds.add(enrollCursor.getString(0));
            } while (enrollCursor.moveToNext());
        }
        enrollCursor.close();

        int totalClassStudents = enrolledStudentIds.size();

        List<String> sessionDates = new ArrayList<>();
        StringBuilder sessionsSql = new StringBuilder(
                "SELECT DISTINCT " + DatabaseHelper.COL_CHECKIN_DATE +
                        " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?"
        );
        List<String> sessionArgs = new ArrayList<>();
        sessionArgs.add(currentSubjectId);

        if (!isSemesterMode && filterStartDate != null && filterEndDate != null) {
            sessionsSql.append(" AND ").append(DatabaseHelper.COL_CHECKIN_DATE).append(" BETWEEN ? AND ?");
            sessionArgs.add(filterStartDate);
            sessionArgs.add(filterEndDate);
        }

        Cursor dateCursor = db.rawQuery(sessionsSql.toString(), sessionArgs.toArray(new String[0]));
        if (dateCursor.moveToFirst()) {
            do {
                sessionDates.add(dateCursor.getString(0));
            } while (dateCursor.moveToNext());
        }
        dateCursor.close();

        if (sessionDates.isEmpty()) {
            pieChartAttendance.setVisibility(View.GONE);
            if (txtNoChartDataMessage != null) txtNoChartDataMessage.setVisibility(View.VISIBLE);

            if (isSemesterMode) {
                txtStatRow1.setText(String.format(Locale.getDefault(), "• Tổng số SV: %d SV", totalClassStudents));
                txtStatRow2.setText("• Tỉ lệ đúng giờ: 0%");
                txtStatRow3.setText("• Tỉ lệ đi muộn: 0%");
                txtStatRow4.setText("• Tỉ lệ vắng mặt: 0%");
            } else {
                txtStatRow1.setText(String.format(Locale.getDefault(), "• Sĩ số lớp: %d SV", totalClassStudents));
                txtStatRow2.setText("• Đúng giờ: 0 SV");
                txtStatRow3.setText("• Đi muộn: 0 SV");
                txtStatRow4.setText("• Vắng mặt: 0 SV");
            }
            return;
        }

        pieChartAttendance.setVisibility(View.VISIBLE);
        if (txtNoChartDataMessage != null) txtNoChartDataMessage.setVisibility(View.GONE);

        int globalOnTimeCount = 0;
        int globalLateCount = 0;
        int globalAbsentCount = 0;

        String[] timeWindow = computeStartTimeWindow();
        String startTimeStart = timeWindow[0];
        String startTimeLimit = timeWindow[1];

        for (String dateStr : sessionDates) {
            Cursor checkinCursor = db.rawQuery(
                    "SELECT " + DatabaseHelper.COL_STUDENT_ID + ", " + DatabaseHelper.COL_CHECKIN_TIME +
                            " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? AND " + DatabaseHelper.COL_CHECKIN_DATE + " = ?",
                    new String[]{currentSubjectId, dateStr}
            );

            Set<String> attendedStudentsInSession = new HashSet<>();
            int sessionOnTime = 0;
            int sessionLate = 0;

            if (checkinCursor.moveToFirst()) {
                do {
                    String studentId = checkinCursor.getString(0);
                    String checkinTime = checkinCursor.getString(1);
                    attendedStudentsInSession.add(studentId);

                    String status = classifyCheckinStatus(checkinTime, startTimeStart, startTimeLimit);
                    if (AttendanceRecord.STATUS_LATE.equals(status)) {
                        sessionLate++;
                    } else {
                        sessionOnTime++;
                    }
                } while (checkinCursor.moveToNext());
            }
            checkinCursor.close();

            int sessionAbsent = totalClassStudents - attendedStudentsInSession.size();
            if (sessionAbsent < 0 || totalClassStudents == 0) {
                sessionAbsent = 0;
            }

            globalOnTimeCount += sessionOnTime;
            globalLateCount += sessionLate;
            globalAbsentCount += sessionAbsent;
        }

        int grandTotalEvaluations = globalOnTimeCount + globalLateCount + globalAbsentCount;
        if (grandTotalEvaluations == 0) {
            grandTotalEvaluations = totalClassStudents > 0 ? totalClassStudents : 1;
        }

        double onTimePercent = (globalOnTimeCount * 100.0) / grandTotalEvaluations;
        double latePercent = (globalLateCount * 100.0) / grandTotalEvaluations;
        double absentPercent = (globalAbsentCount * 100.0) / grandTotalEvaluations;

        if (isSemesterMode) {
            txtStatRow1.setText(String.format(Locale.getDefault(), "• Tổng số SV: %d SV", totalClassStudents));
            txtStatRow2.setText(String.format(Locale.getDefault(), "• Tỉ lệ đúng giờ: %.0f%%", onTimePercent));
            txtStatRow3.setText(String.format(Locale.getDefault(), "• Tỉ lệ đi muộn: %.0f%%", latePercent));
            txtStatRow4.setText(String.format(Locale.getDefault(), "• Tỉ lệ vắng mặt: %.0f%%", absentPercent));
        } else {
            int displayTotal = (totalClassStudents > 0) ? totalClassStudents : (globalOnTimeCount + globalLateCount);
            txtStatRow1.setText(String.format(Locale.getDefault(), "• Sĩ số lớp: %d SV", displayTotal));
            txtStatRow2.setText(String.format(Locale.getDefault(), "• Đúng giờ: %d SV", globalOnTimeCount));
            txtStatRow3.setText(String.format(Locale.getDefault(), "• Đi muộn: %d SV", globalLateCount));
            txtStatRow4.setText(String.format(Locale.getDefault(), "• Vắng mặt: %d SV", globalAbsentCount));
        }

        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(globalOnTimeCount, "Đúng giờ"));
        entries.add(new PieEntry(globalLateCount, "Đi muộn"));
        entries.add(new PieEntry(globalAbsentCount, "Vắng mặt"));

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(Color.parseColor("#0288D1"), Color.parseColor("#26A69A"), Color.parseColor("#FF7043"));
        dataSet.setSliceSpace(2f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(14f);

        PieData pieData = new PieData(dataSet);
        final int finalGrand = grandTotalEvaluations;

        pieData.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                double percent = (value * 100.0) / finalGrand;
                return String.format(Locale.getDefault(), "%.0f%%", percent);
            }
        });

        pieChartAttendance.setData(pieData);
        pieChartAttendance.setUsePercentValues(false);
        pieChartAttendance.getDescription().setEnabled(false);
        pieChartAttendance.setDrawHoleEnabled(false);
        pieChartAttendance.setDrawCenterText(false);
        pieChartAttendance.setEntryLabelColor(Color.WHITE);
        pieChartAttendance.setEntryLabelTextSize(12f);
        pieChartAttendance.setRotationEnabled(true);
        pieChartAttendance.setHighlightPerTapEnabled(true);

        Legend legend = pieChartAttendance.getLegend();
        legend.setEnabled(true);
        legend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        legend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);
        legend.setOrientation(Legend.LegendOrientation.HORIZONTAL);
        legend.setDrawInside(false);
        legend.setTextSize(13f);
        legend.setXEntrySpace(15f);
        legend.setYOffset(5f);

        List<com.github.mikephil.charting.components.LegendEntry> legendEntries = new ArrayList<>();
        legendEntries.add(new com.github.mikephil.charting.components.LegendEntry("Đúng giờ", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#0288D1")));
        legendEntries.add(new com.github.mikephil.charting.components.LegendEntry("Đi muộn", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#26A69A")));
        legendEntries.add(new com.github.mikephil.charting.components.LegendEntry("Vắng mặt", Legend.LegendForm.CIRCLE, 10f, 2f, null, Color.parseColor("#FF7043")));
        legend.setCustom(legendEntries);

        pieChartAttendance.animateY(800);
        pieChartAttendance.invalidate();
    }

    private String getSelectedStatusFilter() {
        if (statusTabLayout == null) {
            return AttendanceRecord.STATUS_ON_TIME;
        }
        int pos = statusTabLayout.getSelectedTabPosition();
        if (pos == 1) return AttendanceRecord.STATUS_LATE;
        if (pos == 2) return AttendanceRecord.STATUS_ABSENT;
        return AttendanceRecord.STATUS_ON_TIME;
    }

    private void filterAttendanceList(String query) {
        String targetStatus = getSelectedStatusFilter();
        List<AttendanceRecord> filteredList = new ArrayList<>();

        if (isSemesterMode) {
            filteredList.addAll(fullRecordList);
        } else {
            for (AttendanceRecord r : fullRecordList) {
                boolean matchesStatus = targetStatus.equals(r.getStatus());
                if (matchesStatus) {
                    filteredList.add(r);
                }
            }
        }

        if (!isSemesterMode && !hasCheckinDataForPeriod) {
            textEmptyAttendanceList.setText("Không có dữ liệu điểm danh cho thời gian này.");
            textEmptyAttendanceList.setVisibility(View.VISIBLE);
            recyclerAttendanceList.setVisibility(View.GONE);
        } else if (fullRecordList.isEmpty()) {
            textEmptyAttendanceList.setText("Không có dữ liệu điểm danh cho thời gian này.");
            textEmptyAttendanceList.setVisibility(View.VISIBLE);
            recyclerAttendanceList.setVisibility(View.GONE);
        } else if (filteredList.isEmpty()) {
            String statusLabel = AttendanceRecord.STATUS_LATE.equals(targetStatus) ? "đi muộn" :
                    AttendanceRecord.STATUS_ABSENT.equals(targetStatus) ? "vắng mặt" : "đúng giờ";
            textEmptyAttendanceList.setText("Không có sinh viên nào " + statusLabel + ".");
            textEmptyAttendanceList.setVisibility(View.VISIBLE);
            recyclerAttendanceList.setVisibility(View.GONE);
        } else {
            textEmptyAttendanceList.setVisibility(View.GONE);
            recyclerAttendanceList.setVisibility(View.VISIBLE);
        }

        displayRecordList.clear();
        displayRecordList.addAll(filteredList);
        if (attendanceAdapter != null) {
            attendanceAdapter.setDisplayStatus(targetStatus);
            attendanceAdapter.notifyDataSetChanged();
        }

        if (isSemesterMode) {
            textViewStats.setVisibility(View.GONE);
        } else {
            textViewStats.setVisibility(View.VISIBLE);
            String statusLabel = AttendanceRecord.STATUS_LATE.equals(targetStatus) ? "đi muộn" :
                    AttendanceRecord.STATUS_ABSENT.equals(targetStatus) ? "vắng mặt" : "đúng giờ";

            int displayCount = hasCheckinDataForPeriod ? filteredList.size() : 0;
            textViewStats.setText("Tổng số (" + statusLabel + "): " + displayCount + " sinh viên");
        }
    }

    private void showStudentStatsDialog(AttendanceRecord record) {
        String[] timeWindow = computeStartTimeWindow();
        String startTimeStart = timeWindow[0];
        String startTimeLimit = timeWindow[1];

        int onTimeCount = 0;
        int lateCount = 0;
        int totalCheckins = 0;

        Cursor cursor = dbHelper.getStudentCheckinHistory(currentSubjectId, record.getStudentId());
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                do {
                    String checkinTime = cursor.getString(1);
                    String status = classifyCheckinStatus(checkinTime, startTimeStart, startTimeLimit);
                    if (AttendanceRecord.STATUS_LATE.equals(status)) {
                        lateCount++;
                    } else {
                        onTimeCount++;
                    }
                    totalCheckins++;
                } while (cursor.moveToNext());
            }
            cursor.close();
        }

        int totalSessions = dbHelper.getTotalSessionCount(currentSubjectId);
        int absentCount = totalSessions - totalCheckins;
        if (absentCount < 0) absentCount = 0;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_attendance_stats, null);
        TextView tvName = dialogView.findViewById(R.id.dialogStudentName);
        TextView tvId = dialogView.findViewById(R.id.dialogStudentId);
        TextView tvOnTime = dialogView.findViewById(R.id.dialogOnTimeCount);
        TextView tvLate = dialogView.findViewById(R.id.dialogLateCount);
        TextView tvAbsent = dialogView.findViewById(R.id.dialogAbsentCount);
        TextView tvTotal = dialogView.findViewById(R.id.dialogTotalSessions);

        tvName.setText(record.getFullName());
        tvId.setText("MSSV: " + record.getStudentId());
        tvOnTime.setText(onTimeCount + " buổi");
        tvLate.setText(lateCount + " buổi");
        tvAbsent.setText(absentCount + " buổi");
        tvTotal.setText("Tổng số buổi học: " + totalSessions);

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton("Đóng", null)
                .show();
    }

    private void showExportModeDialog() {
        String todayOrSelectedDate = (singleDaySelectedDate != null) ? singleDaySelectedDate :
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime());

        CharSequence[] options = new CharSequence[]{
                "Xuất theo ngày (" + todayOrSelectedDate + ")",
                "Xuất theo kì học"
        };

        new AlertDialog.Builder(this)
                .setTitle("Chọn phạm vi xuất dữ liệu")
                .setItems(options, (dialog, which) -> {
                    exportMode = (which == 0) ? EXPORT_MODE_DAY : EXPORT_MODE_SEMESTER;
                    checkStoragePermissionAndExport();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void checkStoragePermissionAndExport() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_STORAGE_PERMISSION);
        } else {
            openCreateDocumentPicker();
        }
    }

    private String sanitizeDateForFileName(String date) {
        return date != null ? date.replace("/", "-") : "";
    }

    private void openCreateDocumentPicker() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");

        String fileName;
        if (EXPORT_MODE_SEMESTER.equals(exportMode)) {
            String[] range = dbHelper.getSubjectSemesterRange(currentSubjectId);
            String rangeSafe = (range != null && range[0] != null && range[1] != null)
                    ? (sanitizeDateForFileName(range[0]) + "_" + sanitizeDateForFileName(range[1]))
                    : "KyHoc";
            fileName = "DiemDanh_" + currentSubjectId + "_KiHoc_" + rangeSafe + ".csv";
        } else {
            String exportDate = (singleDaySelectedDate != null) ? singleDaySelectedDate :
                    new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime());
            fileName = "DiemDanh_" + currentSubjectId + "_Ngay_" + sanitizeDateForFileName(exportDate) + ".csv";
        }

        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, REQUEST_CODE_CREATE_CSV_DOCUMENT);
    }

    private String csvEscape(String value) {
        if (value == null) value = "";
        boolean needsQuote = value.contains(CSV_DELIMITER) || value.contains("\"")
                || value.contains("\n") || value.contains("\r");
        String escaped = value.replace("\"", "\"\"");
        return needsQuote ? "\"" + escaped + "\"" : escaped;
    }

    private String joinCsv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(CSV_DELIMITER);
            sb.append(csvEscape(fields[i]));
        }
        return sb.toString();
    }

    private void writeLine(FileOutputStream fos, String line) throws IOException {
        fos.write((line + "\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String getStudentEmail(String studentId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DatabaseHelper.COL_EMAIL + " FROM " + DatabaseHelper.TABLE_STUDENTS +
                        " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                new String[]{studentId}
        );
        String email = null;
        if (c.moveToFirst()) {
            email = c.getString(0);
        }
        c.close();
        return email;
    }

    private void writeCsvToUri(Uri uri) {
        try (FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri)) {
            if (fos != null) {
                if (EXPORT_MODE_SEMESTER.equals(exportMode)) {
                    writeSemesterModeCsv(fos);
                } else {
                    writeDayModeCsv(fos);
                }

                // ĐÃ SỬA: Đánh dấu bật cờ hiệu đã xuất file thành công
                isFileExported = true;

                Toast.makeText(this, "Xuất dữ liệu CSV thành công!", Toast.LENGTH_LONG).show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi ghi tệp CSV!", Toast.LENGTH_SHORT).show();
        }
    }

    private void writeDayModeCsv(FileOutputStream fos) throws IOException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String exportDate = (singleDaySelectedDate != null) ? singleDaySelectedDate :
                new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Calendar.getInstance().getTime());

        String[] timeWindow = computeStartTimeWindow();

        List<String[]> rows = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        Cursor checkinCursor = db.rawQuery(
                "SELECT s." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME +
                        ", s." + DatabaseHelper.COL_EMAIL + ", h." + DatabaseHelper.COL_CHECKIN_TIME +
                        " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY + " h" +
                        " JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON h." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID +
                        " WHERE h." + DatabaseHelper.COL_SUBJECT_ID + " = ? AND h." + DatabaseHelper.COL_CHECKIN_DATE + " = ?",
                new String[]{currentSubjectId, exportDate}
        );
        if (checkinCursor.moveToFirst()) {
            do {
                String sid = checkinCursor.getString(0);
                rows.add(new String[]{sid, checkinCursor.getString(1), checkinCursor.getString(2), checkinCursor.getString(3)});
                seenIds.add(sid);
            } while (checkinCursor.moveToNext());
        }
        checkinCursor.close();

        Cursor absentCursor = dbHelper.getAbsentStudents(currentSubjectId, exportDate, exportDate);
        if (absentCursor != null) {
            if (absentCursor.moveToFirst()) {
                do {
                    String sid = absentCursor.getString(0);
                    if (!seenIds.contains(sid)) {
                        String name = absentCursor.getString(1);
                        String email = getStudentEmail(sid);
                        rows.add(new String[]{sid, name, email, null});
                        seenIds.add(sid);
                    }
                } while (absentCursor.moveToNext());
            }
            absentCursor.close();
        }

        Collections.sort(rows, (a, b) -> {
            String n1 = a[1] != null ? a[1] : "";
            String n2 = b[1] != null ? b[1] : "";
            return n1.compareToIgnoreCase(n2);
        });

        fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        writeLine(fos, "THÔNG TIN MÔN HỌC");
        writeLine(fos, "Mã môn học: " + currentSubjectId);
        writeLine(fos, "Tên môn học: " + currentSubjectName);
        writeLine(fos, "Giảng viên phụ trách: " + currentInstructorName);
        writeLine(fos, "Ngày điểm danh: " + exportDate);
        writeLine(fos, "");

        writeLine(fos, joinCsv("Mã Sinh Viên", "Họ Tên", "Email", "Trạng Thái", "Giờ Điểm Danh"));

        for (String[] r : rows) {
            String sid = r[0] != null ? r[0] : "-";
            String name = r[1] != null ? r[1] : "-";
            String email = r[2] != null ? r[2] : "";
            String time = r[3];

            String status;
            String timeDisplay;
            if (time == null) {
                status = "Vắng mặt";
                timeDisplay = "-";
            } else {
                String st = classifyCheckinStatus(time, timeWindow[0], timeWindow[1]);
                status = AttendanceRecord.STATUS_LATE.equals(st) ? "Đi muộn" : "Đúng giờ";
                timeDisplay = time;
            }

            writeLine(fos, joinCsv(sid, name, email, status, timeDisplay));
        }
    }

    private void writeSemesterModeCsv(FileOutputStream fos) throws IOException {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String[] range = dbHelper.getSubjectSemesterRange(currentSubjectId);
        String semStart = (range != null) ? range[0] : null;
        String semEnd = (range != null) ? range[1] : null;

        List<String[]> enrolledStudents = new ArrayList<>();
        Cursor enrollCursor = db.rawQuery(
                "SELECT e." + DatabaseHelper.COL_STUDENT_ID + ", s." + DatabaseHelper.COL_FULL_NAME + ", s." + DatabaseHelper.COL_EMAIL +
                        " FROM " + DatabaseHelper.TABLE_ENROLLMENTS + " e" +
                        " JOIN " + DatabaseHelper.TABLE_STUDENTS + " s ON e." + DatabaseHelper.COL_STUDENT_ID + " = s." + DatabaseHelper.COL_STUDENT_ID +
                        " WHERE e." + DatabaseHelper.COL_SUBJECT_ID + " = ?" +
                        " ORDER BY s." + DatabaseHelper.COL_FULL_NAME + " ASC",
                new String[]{currentSubjectId}
        );
        if (enrollCursor.moveToFirst()) {
            do {
                enrolledStudents.add(new String[]{enrollCursor.getString(0), enrollCursor.getString(1), enrollCursor.getString(2)});
            } while (enrollCursor.moveToNext());
        }
        enrollCursor.close();

        List<String> sessionDates = new ArrayList<>();
        Cursor dateCursor = db.rawQuery(
                "SELECT DISTINCT " + DatabaseHelper.COL_CHECKIN_DATE +
                        " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                        " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ?",
                new String[]{currentSubjectId}
        );
        if (dateCursor.moveToFirst()) {
            do {
                String d = dateCursor.getString(0);
                if (isDateInRange(d, semStart, semEnd)) {
                    sessionDates.add(d);
                }
            } while (dateCursor.moveToNext());
        }
        dateCursor.close();

        final SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        Collections.sort(sessionDates, (a, b) -> {
            try {
                return sdf.parse(a).compareTo(sdf.parse(b));
            } catch (Exception e) {
                return a.compareTo(b);
            }
        });

        String[] timeWindow = computeStartTimeWindow();

        fos.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

        writeLine(fos, "THÔNG TIN MÔN HỌC");
        writeLine(fos, "Mã môn học: " + currentSubjectId);
        writeLine(fos, "Tên môn học: " + currentSubjectName);
        writeLine(fos, "Giảng viên phụ trách: " + currentInstructorName);
        String semesterLabel = (semStart != null && semEnd != null)
                ? ("Thời gian kì học: " + semStart + " đến " + semEnd)
                : "Thời gian kì học: Không xác định";
        writeLine(fos, semesterLabel);
        writeLine(fos, "");

        if (sessionDates.isEmpty()) {
            writeLine(fos, joinCsv("Mã Sinh Viên", "Họ Tên", "Email"));
            for (String[] st : enrolledStudents) {
                writeLine(fos, joinCsv(st[0], st[1], st[2] != null ? st[2] : ""));
            }
            return;
        }

        List<String> headerCols = new ArrayList<>();
        headerCols.add("Mã Sinh Viên");
        headerCols.add("Họ Tên");
        headerCols.add("Email");
        headerCols.addAll(sessionDates);
        writeLine(fos, joinCsv(headerCols.toArray(new String[0])));

        for (String[] st : enrolledStudents) {
            String sid = st[0];
            String name = st[1];
            String email = st[2] != null ? st[2] : "";

            Map<String, String> dateToTime = new HashMap<>();
            Cursor checkinCursor = db.rawQuery(
                    "SELECT " + DatabaseHelper.COL_CHECKIN_DATE + ", " + DatabaseHelper.COL_CHECKIN_TIME +
                            " FROM " + DatabaseHelper.TABLE_CHECKIN_HISTORY +
                            " WHERE " + DatabaseHelper.COL_SUBJECT_ID + " = ? AND " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                    new String[]{currentSubjectId, sid}
            );
            if (checkinCursor.moveToFirst()) {
                do {
                    String d = checkinCursor.getString(0);
                    String t = checkinCursor.getString(1);
                    if (isDateInRange(d, semStart, semEnd)) {
                        dateToTime.put(d, t);
                    }
                } while (checkinCursor.moveToNext());
            }
            checkinCursor.close();

            List<String> rowCols = new ArrayList<>();
            rowCols.add(sid);
            rowCols.add(name);
            rowCols.add(email);

            for (String date : sessionDates) {
                String time = dateToTime.get(date);
                if (time == null) {
                    rowCols.add("Vắng mặt");
                } else {
                    String status = classifyCheckinStatus(time, timeWindow[0], timeWindow[1]);
                    String statusLabel = AttendanceRecord.STATUS_LATE.equals(status) ? "Đi muộn" : "Đúng giờ";
                    rowCols.add(time + " - " + statusLabel);
                }
            }

            writeLine(fos, joinCsv(rowCols.toArray(new String[0])));
        }
    }

    private void checkEmailListEmptyState() {
        if (emailRecipientList.isEmpty()) {
            textEmptyEmailList.setVisibility(View.VISIBLE);
            recyclerEmailList.setVisibility(View.GONE);
        } else {
            textEmptyEmailList.setVisibility(View.GONE);
            recyclerEmailList.setVisibility(View.VISIBLE);
        }
    }

    private void updateSelectedCount(int count) {
        textSelectedCount.setText("Đã chọn: " + count + " email");
    }

    private void importEmailsFromCsv(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (line.contains(",") && !line.startsWith("Email")) {
                    String[] tokens = line.split(",");
                    if (tokens.length >= 1) {
                        String email = tokens[0].trim();
                        String name = tokens.length >= 2 ? tokens[1].trim() : "Người nhận";
                        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {

                            EmailRecipient recipient = new EmailRecipient();
                            recipient.setEmailAddress(email);
                            recipient.setRecipientName(name);
                            recipient.setSelected(true);

                            emailRecipientList.add(recipient);
                            count++;
                        }
                    }
                }
            }
            emailAdapter.notifyDataSetChanged();
            checkEmailListEmptyState();
            updateSelectedCount(emailAdapter.getSelectedCount());
            Toast.makeText(this, "Đã nhập thành công " + count + " email từ CSV!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Lỗi khi đọc file CSV!", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_CODE_IMPORT_EMAIL_CSV && uri != null) {
                importEmailsFromCsv(uri);
            } else if (requestCode == REQUEST_CODE_CREATE_CSV_DOCUMENT && uri != null) {
                writeCsvToUri(uri);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCreateDocumentPicker();
            } else {
                Toast.makeText(this, "Cần cấp quyền để xuất báo cáo!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
package com.iritech.irissample;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import android.os.Environment;

//import com.iritech.iris.CaptureActivity;
//import com.iritech.iris.Constants;

import java.io.File;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class RegisterAdminActivity extends AppCompatActivity {
    
    private EditText editTextEmail;
    private EditText editTextPassword;
    private EditText editTextConfirmPassword;
    private EditText editTextFullName;
    private EditText editTextAdminCode; // Mã số giảng viên (chỉ cho Admin thường)
    private EditText editTextDob;
    private EditText editTextPhone;
    private Spinner spinnerGender;
    private EditText editTextDescription;
    private TextView textViewError;
    private Button buttonRegister;
    private Button buttonCancel;
    private TextView textViewTitle;
    private TextView textViewSubtitle;
    private CheckBox checkBoxShowPassword;
    private Button buttonEnrollIris;
    private TextView textViewIrisStatus;
    
    private DatabaseHelper dbHelper;
    private boolean isCreatingSuperAdmin;
    private String tempAdminEmail; // Email tạm để ghi danh mống mắt
    private boolean hasEnrolledIris = false; // Đánh dấu đã ghi danh chưa
    private Intent pendingIrisIntent; // Intent chờ được cấp permission
    private String superAdminEmail; // Email Super Admin đang đăng nhập (dùng khi tạo Admin mới)
    
    private static final int REQUEST_CODE_ENROLL_IRIS = 2001;
    private static final int REQUEST_IRIS_PERMISSIONS = 2002;
    private static final int REQUEST_IRIS_SDK_PERMISSION = 2003;
    private static final int REQUEST_ALL_PERMISSIONS = 2004; // Request tất cả permissions quan trọng
    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register_admin);
        
        dbHelper = new DatabaseHelper(this);

        // QUAN TRONG: Thiet lap USB Activity de SDK co the mo sensor phan cung
        // Giong nhu MainActivity.onCreate() goi CaptureActivity.setUSBActivity(this)
        //CaptureActivity.setUSBActivity(this);
        initIrisFolders();

        // Request tất cả permissions quan trọng ngay khi Activity khởi động
        requestCriticalPermissions();

        // Yêu cầu quyền SDK mống mắt ngay khi khởi động (fire-and-forget, giống MainActivity.loadConfigs)
        if (ContextCompat.checkSelfPermission(this, IRIS_SDK_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{IRIS_SDK_PERMISSION}, REQUEST_IRIS_SDK_PERMISSION);
        }
        
        // Khoi tao views
        editTextEmail = findViewById(R.id.editTextEmail);
        editTextPassword = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        editTextFullName = findViewById(R.id.editTextFullName);
        editTextAdminCode = findViewById(R.id.editTextAdminCode); // Mã số giảng viên
        editTextDob = findViewById(R.id.editTextDob);
        editTextPhone = findViewById(R.id.editTextPhone);
        spinnerGender = findViewById(R.id.spinnerGender);
        editTextDescription = findViewById(R.id.editTextDescription);
        textViewError = findViewById(R.id.textViewError);
        buttonRegister = findViewById(R.id.buttonRegister);
        buttonCancel = findViewById(R.id.buttonCancel);
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewSubtitle = findViewById(R.id.textViewSubtitle);
        checkBoxShowPassword = findViewById(R.id.checkBoxShowPassword);
        buttonEnrollIris = findViewById(R.id.buttonEnrollIris);
        textViewIrisStatus = findViewById(R.id.textViewIrisStatus);
        
        // Setup Spinner cho gioi tinh
        String[] genders = {"Nam", "Nữ", "Khác"};
        ArrayAdapter<String> genderAdapter = new ArrayAdapter<>(this, 
                android.R.layout.simple_spinner_item, genders);
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(genderAdapter);
        
        // QUAN TRONG: Kiem tra dang tao Super Admin hay Admin thuong
        isCreatingSuperAdmin = !dbHelper.isSuperAdminExists();

        if (isCreatingSuperAdmin) {
            // Lan dau tien - tao Super Admin
            textViewTitle.setText("Đăng ký ");
            textViewSubtitle.setText("Tạo tài khoản Admin đầu tiên");
            buttonCancel.setVisibility(View.GONE); // Khong cho huy khi tao Super Admin
            editTextAdminCode.setVisibility(View.GONE); // Super Admin không cần mã số
        } else {
            // Da co Super Admin - dang tao Admin thuong
            textViewTitle.setText("Thêm Admin mới");
            textViewSubtitle.setText("Tạo tài khoản Admin cho giảng viên");
            editTextAdminCode.setVisibility(View.VISIBLE); // Hiển thị field mã số

            // Lấy email Super Admin từ Intent (để xác thực khi tạo Admin mới)
            superAdminEmail = getIntent().getStringExtra("SUPER_ADMIN_EMAIL");
            if (superAdminEmail == null || superAdminEmail.isEmpty()) {
                Toast.makeText(this, "Lỗi: Không tìm thấy thông tin Super Admin", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }
        
        // Setup DatePickerDialog cho ngày sinh
        setupDatePicker();
        
        // Xu ly checkbox hien thi password
        checkBoxShowPassword.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // Hien password
                    editTextPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    editTextConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                } else {
                    // An password
                    editTextPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    editTextConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                }
                // Dua con tro ve cuoi
                editTextPassword.setSelection(editTextPassword.getText().length());
                editTextConfirmPassword.setSelection(editTextConfirmPassword.getText().length());
            }
        });
        
        buttonRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Nếu đang tạo Admin thường (Super Admin tạo), yêu cầu xác thực Device Lock
                if (!isCreatingSuperAdmin) {
                    authenticateDeviceLockForCreateAdmin();
                } else {
                    // Tạo Super Admin không cần xác thực
                    attemptRegister();
                }
            }
        });
        
        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        buttonEnrollIris.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enrollIrisForAdmin();
            }
        });
    }
    
    private void attemptRegister() {
        // Lay du lieu tu form
        String email = editTextEmail.getText().toString().trim();
        String password = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        String fullName = editTextFullName.getText().toString().trim();
        String adminCode = editTextAdminCode.getText().toString().trim(); // Mã số giảng viên
        String dob = editTextDob.getText().toString().trim();
        String phone = editTextPhone.getText().toString().trim();
        String gender = spinnerGender.getSelectedItem().toString();
        String description = editTextDescription.getText().toString().trim();
        
        // Validate du lieu
        if (email.isEmpty() || password.isEmpty() || fullName.isEmpty()) {
            showError("Vui lòng điền đầy đủ các trường bắt buộc (*)");
            return;
        }
        
        // Validate mã số giảng viên (chỉ khi tạo Admin thường)
        if (!isCreatingSuperAdmin) {
            if (adminCode.isEmpty()) {
                showError("Vui lòng nhập mã số giảng viên");
                return;
            }
            // Kiểm tra format: GV + 4 chữ số năm + 5 chữ số
            if (!adminCode.matches("^GV\\d{9}$")) {
                showError("Mã số giảng viên không đúng định dạng (VD: GV202612345)");
                return;
            }
        }
        
        if (!email.contains("@")) {
            showError("Email không hợp lệ");
            return;
        }
        
        if (password.length() < 6) {
            showError("Password phải có ít nhất 6 ký tự");
            return;
        }
        
        // Validate số điện thoại (phải đúng 10 số)
        if (!phone.isEmpty() && !phone.matches("^\\d{10}$")) {
            showError("Số điện thoại phải đúng 10 chữ số");
            return;
        }
        
        // Kiem tra password khop nhau
        if (!password.equals(confirmPassword)) {
            showError("Password xác nhận không khớp");
            return;
        }
        
        // Kiem tra email da ton tai chua
        if (dbHelper.isEmailExists(email)) {
            showError("Email đã tồn tại trong hệ thống");
            return;
        }
        
        // Luu vao database
        boolean success;
        if (isCreatingSuperAdmin) {
            // Tao Super Admin
            success = dbHelper.insertSuperAdmin(email, password, fullName, 
                    dob, phone, gender, null, description);
        } else {
            // Tao Admin thuong
            success = dbHelper.insertAdmin(email, password, fullName,
                    dob, phone, gender, null, description, adminCode);
        }
        
        if (success) {
            // Nếu đã ghi danh mống mắt, cập nhật trạng thái trong database
            if (hasEnrolledIris) {
                // Lưu trạng thái đã ghi danh (eye photo sẽ được lưu sau khi capture - tạm để null)
                dbHelper.updateAdminIrisEnrollment(email);
            }
            
            String message = isCreatingSuperAdmin ? 
                    "Tạo Super Admin thành công!" : 
                    "Thêm Admin mới thành công!";
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            
            if (isCreatingSuperAdmin) {
                // Super Admin tự đăng ký - gửi email chào mừng
                EmailService.sendRegistrationSuccessEmail(email, fullName, "Super Admin");
                
                // Sau khi tao Super Admin -> quay ve LoginActivity
                Intent intent = new Intent(RegisterAdminActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            } else {
                // Super Admin tạo Admin thường - gửi email với mã số đã nhập
                EmailService.sendAccountCreatedEmail(email, fullName, adminCode);
            }
            finish();
        } else {
            showError("Lỗi khi lưu vào database");
        }
    }
    
    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }
    
    /**
     * Setup DatePickerDialog cho trường ngày sinh
     * Giới hạn: Từ 1980 đến năm hiện tại
     * Sử dụng Spinner Mode để chọn năm nhanh hơn
     */
    private void setupDatePicker() {
        final Calendar calendar = Calendar.getInstance();
        final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        
        // Disable keyboard input, chỉ cho chọn từ DatePicker
        editTextDob.setFocusable(false);
        editTextDob.setClickable(true);
        
        editTextDob.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Đặt năm mặc định là 1995 (tuổi hợp lý cho giảng viên/admin)
                // Thay vì năm hiện tại (2026) để không phải lướt ngược lại quá nhiều
                int defaultYear = 1995;
                int defaultMonth = 0; // January
                int defaultDay = 1;
                
                // Nếu đã có ngày được chọn trước đó, dùng ngày đó
                String currentDob = editTextDob.getText().toString();
                if (!currentDob.isEmpty()) {
                    try {
                        calendar.setTime(dateFormat.parse(currentDob));
                        defaultYear = calendar.get(Calendar.YEAR);
                        defaultMonth = calendar.get(Calendar.MONTH);
                        defaultDay = calendar.get(Calendar.DAY_OF_MONTH);
                    } catch (Exception e) {
                        // Keep default values
                    }
                }
                
                DatePickerDialog datePickerDialog = new DatePickerDialog(
                    RegisterAdminActivity.this,
                    android.R.style.Theme_Holo_Light_Dialog_NoActionBar, // Theme cho spinner mode
                    new DatePickerDialog.OnDateSetListener() {
                        @Override
                        public void onDateSet(android.widget.DatePicker view, int selectedYear, int selectedMonth, int selectedDay) {
                            calendar.set(selectedYear, selectedMonth, selectedDay);
                            editTextDob.setText(dateFormat.format(calendar.getTime()));
                        }
                    },
                    defaultYear, defaultMonth, defaultDay
                );
                
                // Chuyển sang Spinner Mode (3 cột: Ngày | Tháng | Năm) - dễ cuộn hơn
                datePickerDialog.getDatePicker().setCalendarViewShown(false);
                datePickerDialog.getDatePicker().setSpinnersShown(true);
                
                // Giới hạn năm: Từ 1980 đến năm hiện tại
                Calendar minDate = Calendar.getInstance();
                minDate.set(1980, 0, 1); // 01/01/1980
                datePickerDialog.getDatePicker().setMinDate(minDate.getTimeInMillis());
                
                Calendar maxDate = Calendar.getInstance(); // Năm hiện tại
                datePickerDialog.getDatePicker().setMaxDate(maxDate.getTimeInMillis());
                
                datePickerDialog.show();
            }
        });
    }
    
    /**
     * Lấy mã số giảng viên từ database sau khi tạo Admin
     */
    private String getAdminCodeByEmail(String email) {
        android.database.Cursor cursor = dbHelper.getAdminByEmail(email);
        String adminCode = null;
        
        if (cursor != null && cursor.moveToFirst()) {
            int codeIndex = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_CODE);
            if (codeIndex != -1) {
                adminCode = cursor.getString(codeIndex);
            }
            cursor.close();
        }
        
        return adminCode != null ? adminCode : "N/A";
    }
    
    /**
     * Xác thực Device Lock trước khi Super Admin tạo Admin mới
     */
    private void authenticateDeviceLockForCreateAdmin() {
        // Debug log
        android.util.Log.d("RegisterAdmin", "authenticateDeviceLockForCreateAdmin called");
        android.util.Log.d("RegisterAdmin", "superAdminEmail = " + superAdminEmail);

        // Sử dụng superAdminEmail (lấy từ Intent) để xác thực, KHÔNG phải editTextEmail (email admin mới)
        BiometricAuthHelper.showBiometricPrompt(
                this,
                "Xác thực để tạo Admin mới",
                "Sử dụng vân tay hoặc mã PIN thiết bị",
                superAdminEmail,
                dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override
                    public void onAuthSuccess() {
                        attemptRegister();
                    }

                    @Override
                    public void onAuthFailed() {
                        Toast.makeText(RegisterAdminActivity.this,
                                "Xác thực không khớp", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onAuthError(String error) {
                        Toast.makeText(RegisterAdminActivity.this,
                                "Xác thực thất bại: " + error, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
    
    /**
     * Ghi danh mống mắt cho Admin đang đăng ký
     */
    private void enrollIrisForAdmin() {
        // Validate email trước khi ghi danh
        String email = editTextEmail.getText().toString().trim();
        if (email.isEmpty()) {
            showError("Vui lòng nhập email trước khi ghi danh mống mắt");
            return;
        }
        
        if (!email.contains("@")) {
            showError("Email không hợp lệ");
            return;
        }
        
        // Lưu email tạm để dùng làm ID cho template
        tempAdminEmail = email;
        
        // Convert email thành format an toàn cho SDK (SDK không chấp nhận @, .)
        // VD: admin@example.com -> admin_at_example_com
        String safeUserId = email.replace("@", "_at_").replace(".", "_");
        
        // Nếu đã ghi danh rồi (hasEnrolledIris = true), hiển thị cảnh báo
        /*if (hasEnrolledIris) {
            new android.app.AlertDialog.Builder(this)
                .setTitle("Ghi danh lại mống mắt")
                .setMessage("Bạn đã ghi danh trước đó. Template cũ sẽ bị xóa và thay thế. Tiếp tục?")
                .setPositiveButton("Tiếp tục", (dialog, which) -> {
                    Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
                    intent.setAction(Constants.ACTION_ENROLL);
                    intent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
                    checkIrisPermissionsAndStart(intent);
                })
                .setNegativeButton("Hủy", null)
                .show();
        } else {
            Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
            intent.setAction(Constants.ACTION_ENROLL);
            intent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
            checkIrisPermissionsAndStart(intent);
        }*/
    }

    /**
     * Kiểm tra quyền WRITE_EXTERNAL_STORAGE và CAMERA trước khi khởi chạy CaptureActivity.
     * Thứ tự: Ghi (WRITE) trước → Chụp (CAMERA) sau, đúng yêu cầu của SDK.
     */
    private void checkIrisPermissionsAndStart(Intent intent) {
        boolean hasWrite = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            //LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_ENROLL_IRIS);
            return;
        }

        // Lưu intent để dùng lại sau khi được cấp permission
        pendingIrisIntent = intent;
        // Request theo đúng thứ tự: WRITE trước, CAMERA sau
        ActivityCompat.requestPermissions(this,
                new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA
                },
                REQUEST_IRIS_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_IRIS_SDK_PERMISSION) {
            // Kết quả fire-and-forget – không chặn luồng người dùng
        } else if (requestCode == REQUEST_ALL_PERMISSIONS) {
            // Fire-and-forget - log kết quả nhưng không chặn flow
            int granted = 0;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) granted++;
            }
            android.util.Log.d("RegisterAdmin", "Critical permissions: " + granted + "/" + grantResults.length + " granted");
        } else if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted && pendingIrisIntent != null) {
                //LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, REQUEST_CODE_ENROLL_IRIS);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this,
                        "Cần cấp quyền Camera và bộ nhớ để ghi danh mống mắt.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_ENROLL_IRIS && resultCode == RESULT_OK) {
            /*if (data != null) {
                int resultCodeExt = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
                String resultMsg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
                
                // Log chi tiết để debug
                android.util.Log.d("RegisterAdmin", "Iris enroll result: code=" + resultCodeExt + ", msg=" + resultMsg);
                
                if (resultCodeExt == 0) {
                    // Ghi danh thành công
                    hasEnrolledIris = true;
                    textViewIrisStatus.setText("Đã ghi danh mống mắt");
                    textViewIrisStatus.setTextColor(0xFF4CAF50); // Green
                    buttonEnrollIris.setText("Ghi danh lại");
                    Toast.makeText(this, "Ghi danh mống mắt thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    // Ghi danh thất bại - Hiển thị AlertDialog để xem đầy đủ lỗi
                    new android.app.AlertDialog.Builder(this)
                        .setTitle("Lỗi ghi danh mống mắt")
                        .setMessage("Mã lỗi: " + resultCodeExt + "\n\nChi tiết: " + (resultMsg != null ? resultMsg : "Không rõ"))
                        .setPositiveButton("OK", null)
                        .show();
                }
            }*/
        }
    }

    /**
     * Tạo thư mục cần thiết cho SDK mống mắt (giống MainActivity.initFolder)
     */
    private void initIrisFolders() {
        String base = Environment.getExternalStorageDirectory().toString() + File.separator + "iritech";
        File enrollFolder = new File(base + File.separator + "enroll");
        if (!enrollFolder.exists()) enrollFolder.mkdirs();
        File verifyFolder = new File(base + File.separator + "verify");
        if (!verifyFolder.exists()) verifyFolder.mkdirs();
    }

    /**
     * Request tất cả permissions quan trọng ngay khi Activity khởi động.
     * Đảm bảo user được request đầy đủ permissions giống MainActivity.
     */
    private void requestCriticalPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();

        // Camera - cho chụp mống mắt
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA);
        }

        // Storage - cho lưu template và ảnh
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // Phone state - SDK license check
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.READ_PHONE_STATE);
        }

        // Network state - kết nối mạng
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_NETWORK_STATE);
        }

        if (!permissionsNeeded.isEmpty()) {
            android.util.Log.d("RegisterAdmin", "Requesting " + permissionsNeeded.size() + " critical permissions");
            ActivityCompat.requestPermissions(this,
                    permissionsNeeded.toArray(new String[0]),
                    REQUEST_ALL_PERMISSIONS);
        }
    }
}

package com.iritech.irissample;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
//import com.iritech.iris.CaptureActivity;
//import com.iritech.iris.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileActivity extends AppCompatActivity {

    private DatabaseHelper          dbHelper;
    private PasswordValidationHelper validationHelper;
    private FaceDetector            faceDetector;
    private FaceMatchHelper         faceMatchHelper;

    private String  currentUserEmail;
    private String  currentUserRole;
    private boolean isEditMode   = false;
    private boolean isFirstLogin = false;

    // ── Views ────────────────────────────────────────────────────────────────
    private ImageView    imageViewAvatar;
    private EditText     editTextEmail, editTextPassword, editTextConfirmPassword;
    private EditText     editTextFullName, editTextDob, editTextPhone, editTextDescription;
    private CheckBox     checkBoxShowPassword;
    private Spinner      spinnerGender;
    private TextView     textViewRole, textViewEmployeeCode, textViewError, textViewIrisStatus;
    private Button       buttonSave, buttonCancel, buttonEdit;
    private Button       buttonCamera, buttonGallery, buttonEnrollIris;
    private LinearLayout layoutPasswordSection, layoutActionButtons, layoutAvatarButtons;

    private String photoPath; // đường dẫn ảnh đang lưu trong DB / vừa chọn

    // ── Request codes ────────────────────────────────────────────────────────
    private static final int REQUEST_STORAGE_PERMISSION  = 101;
    private static final int REQUEST_GALLERY_PICK        = 103;
    private static final int REQUEST_CODE_ENROLL_IRIS    = 2002;
    private static final int REQUEST_IRIS_PERMISSIONS    = 104;
    private static final int REQUEST_IRIS_SDK_PERMISSION = 105;
    private static final int REQUEST_CODE_FACE_CAPTURE   = 200; // ★ camera qua FaceCaptureActivity

    private static final String IRIS_SDK_PERMISSION = "com.id2mp.permissions.IRIS";
    private Intent pendingIrisIntent;

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        dbHelper         = new DatabaseHelper(this);
        validationHelper = new PasswordValidationHelper(dbHelper);
        //CaptureActivity.setUSBActivity(this);

        currentUserEmail = getIntent().getStringExtra("USER_EMAIL");
        isFirstLogin     = getIntent().getBooleanExtra("IS_FIRST_LOGIN", false);

        if (TextUtils.isEmpty(currentUserEmail)) {
            Toast.makeText(this, "Không tìm thấy thông tin người dùng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // MLKit face detector — chỉ dùng cho flow thư viện ảnh
        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector    = FaceDetection.getClient(opts);
        faceMatchHelper = new FaceMatchHelper(this);

        if (isFirstLogin) isEditMode = true;

        initializeViews();
        setupGenderSpinner();
        loadUserData();
        setViewMode();
        setupListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector    != null) faceDetector.close();
        if (faceMatchHelper != null) faceMatchHelper.close();
    }

    // ==================== INIT ====================

    private void initializeViews() {
        imageViewAvatar         = findViewById(R.id.imageViewAvatar);
        editTextEmail           = findViewById(R.id.editTextEmail);
        editTextPassword        = findViewById(R.id.editTextPassword);
        editTextConfirmPassword = findViewById(R.id.editTextConfirmPassword);
        checkBoxShowPassword    = findViewById(R.id.checkBoxShowPassword);
        editTextFullName        = findViewById(R.id.editTextFullName);
        editTextDob             = findViewById(R.id.editTextDob);
        editTextPhone           = findViewById(R.id.editTextPhone);
        spinnerGender           = findViewById(R.id.spinnerGender);
        editTextDescription     = findViewById(R.id.editTextDescription);
        textViewRole            = findViewById(R.id.textViewRole);
        textViewEmployeeCode    = findViewById(R.id.textViewEmployeeCode);
        textViewError           = findViewById(R.id.textViewError);
        textViewIrisStatus      = findViewById(R.id.textViewIrisStatus);
        buttonSave              = findViewById(R.id.buttonSave);
        buttonCancel            = findViewById(R.id.buttonCancel);
        buttonEdit              = findViewById(R.id.buttonEdit);
        buttonCamera            = findViewById(R.id.buttonCamera);
        buttonGallery           = findViewById(R.id.buttonGallery);
        buttonEnrollIris        = findViewById(R.id.buttonEnrollIris);
        layoutPasswordSection   = findViewById(R.id.layoutPasswordSection);
        layoutActionButtons     = findViewById(R.id.layoutActionButtons);
        layoutAvatarButtons     = findViewById(R.id.layoutAvatarButtons);
    }

    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    // ==================== LOAD DATA ====================

    private void loadUserData() {
        Cursor cursor = dbHelper.getAdminByEmail(currentUserEmail);
        if (cursor != null && cursor.moveToFirst()) {
            editTextEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow("email")));
            editTextFullName.setText(cursor.getString(cursor.getColumnIndexOrThrow("full_name")));
            editTextDob.setText(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
            editTextPhone.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone")));

            String gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
            setSpinnerValue(spinnerGender, gender);

            editTextDescription.setText(cursor.getString(cursor.getColumnIndexOrThrow("description")));

            String role = cursor.getString(cursor.getColumnIndexOrThrow("role"));
            currentUserRole = role;
            textViewRole.setText("Vai trò: " + ("SUPER_ADMIN".equals(role) ? "Super Admin" : "Admin"));

            if ("SUPER_ADMIN".equals(role)) {
                textViewEmployeeCode.setVisibility(View.GONE);
            } else {
                int codeIdx = cursor.getColumnIndex(DatabaseHelper.COL_ADMIN_CODE);
                if (codeIdx != -1) {
                    String adminCode = cursor.getString(codeIdx);
                    if (!TextUtils.isEmpty(adminCode)) {
                        textViewEmployeeCode.setText("Mã số giảng viên: " + adminCode);
                        textViewEmployeeCode.setVisibility(View.VISIBLE);
                    } else {
                        textViewEmployeeCode.setVisibility(View.GONE);
                    }
                } else {
                    textViewEmployeeCode.setVisibility(View.GONE);
                }
            }

            photoPath = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"));
            loadAvatarImage(cursor.getString(cursor.getColumnIndexOrThrow("full_name")));

            cursor.close();
        }

        // Trạng thái mống mắt
        boolean hasIris = dbHelper.hasAdminEnrolledIris(currentUserEmail);
        if (hasIris) {
            textViewIrisStatus.setText("Đã ghi danh mống mắt");
            textViewIrisStatus.setTextColor(0xFF4CAF50);
            buttonEnrollIris.setText("Ghi danh lại");
        } else {
            textViewIrisStatus.setText("Chưa ghi danh");
            textViewIrisStatus.setTextColor(0xFF999999);
            buttonEnrollIris.setText("Ghi danh mống mắt");
        }
    }

    private void loadAvatarImage(String fullName) {
        if (!TextUtils.isEmpty(photoPath) && new File(photoPath).exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(photoPath);
            if (bmp != null) {
                imageViewAvatar.setImageBitmap(bmp);
                return;
            }
        }
        imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(fullName, 192));
    }

    private void setSpinnerValue(Spinner spinner, String value) {
        ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            if (adapter.getItem(i).toString().equalsIgnoreCase(value)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    // ==================== LISTENERS ====================

    private void setupListeners() {

        buttonEdit.setOnClickListener(v -> setEditMode());

        buttonCancel.setOnClickListener(v -> {
            loadUserData();
            setViewMode();
        });

        checkBoxShowPassword.setOnCheckedChangeListener((btn, checked) -> {
            int type = checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            editTextPassword.setInputType(type);
            editTextConfirmPassword.setInputType(type);
        });

        // ── Nút Camera → FaceCaptureActivity (MODE_REGISTER, folder admins) ──
        buttonCamera.setOnClickListener(v -> openFaceCapture());

        // ── Nút Thư viện → chọn ảnh rồi detect + check trùng ──
        buttonGallery.setOnClickListener(v -> {
            if (checkStoragePermission()) openGallery();
        });

        buttonEnrollIris.setOnClickListener(v -> enrollIrisForCurrentUser());

        buttonSave.setOnClickListener(v -> showBiometricAuthentication());
    }

    // ==================== MỞ FACE CAPTURE (CAMERA) ====================

    /**
     * Mở FaceCaptureActivity ở chế độ đăng ký (chụp thủ công).
     * Folder = admins → file lưu vào admins/face_<adminId>.jpg
     */
    private void openFaceCapture() {
        String adminId = dbHelper.getAdminIdByEmail(currentUserEmail);
        Intent intent  = new Intent(this, FaceCaptureActivity.class);
        intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID,     adminId);
        intent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, photoPath);
        intent.putExtra(FaceCaptureActivity.EXTRA_MODE,   FaceCaptureActivity.MODE_REGISTER);
        intent.putExtra(FaceCaptureActivity.EXTRA_FOLDER, FaceCaptureActivity.FOLDER_ADMINS);
        startActivityForResult(intent, REQUEST_CODE_FACE_CAPTURE);
    }

    // ==================== MỞ THƯ VIỆN ẢNH ====================

    private boolean checkStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return false;
        }
        return true;
    }

    private void openGallery() {
        Intent pick = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pick, REQUEST_GALLERY_PICK);
    }

    // ==================== XỬ LÝ KẾT QUẢ ====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // ── Kết quả từ FaceCaptureActivity (camera) ──────────────────────────
        if (requestCode == REQUEST_CODE_FACE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                String capturedPath = data.getStringExtra(FaceCaptureActivity.RESULT_PHOTO_PATH);
                if (capturedPath != null && !capturedPath.isEmpty()) {
                    // FaceCaptureActivity đã xử lý đầy đủ (detect, check trùng, lưu file)
                    // → chỉ cần nhận đường dẫn và hiển thị
                    photoPath = capturedPath;
                    Bitmap bmp = BitmapFactory.decodeFile(photoPath);
                    if (bmp != null) imageViewAvatar.setImageBitmap(bmp);
                    Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Không nhận được ảnh hợp lệ.", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

        // ── Kết quả từ thư viện ảnh ──────────────────────────────────────────
        if (requestCode == REQUEST_GALLERY_PICK && resultCode == RESULT_OK && data != null) {
            Uri selectedUri = data.getData();
            if (selectedUri == null) return;
            try {
                InputStream is      = getContentResolver().openInputStream(selectedUri);
                Bitmap      rawBitmap = BitmapFactory.decodeStream(is);
                if (rawBitmap == null) {
                    Toast.makeText(this, "Không thể đọc ảnh này", Toast.LENGTH_SHORT).show();
                    return;
                }
                // Detect mặt trong ảnh rồi xử lý tiếp trên background thread
                InputImage inputImage = InputImage.fromBitmap(rawBitmap, 0);
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            if (faces == null || faces.isEmpty()) {
                                Toast.makeText(this,
                                        "❌ Không tìm thấy khuôn mặt trong ảnh này!",
                                        Toast.LENGTH_LONG).show();
                                rawBitmap.recycle();
                                return;
                            }
                            Face face   = faces.get(0);
                            Rect bounds = face.getBoundingBox();
                            int x = Math.max(0, bounds.left);
                            int y = Math.max(0, bounds.top);
                            int w = Math.min(bounds.width(),  rawBitmap.getWidth()  - x);
                            int h = Math.min(bounds.height(), rawBitmap.getHeight() - y);

                            if (w <= 0 || h <= 0) {
                                Toast.makeText(this,
                                        "❌ Không xác định được vùng khuôn mặt!",
                                        Toast.LENGTH_SHORT).show();
                                rawBitmap.recycle();
                                return;
                            }
                            Bitmap cropped = Bitmap.createBitmap(rawBitmap, x, y, w, h);
                            rawBitmap.recycle();
                            processGalleryFace(cropped);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi phân tích ảnh: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            rawBitmap.recycle();
                        });
            } catch (Exception e) {
                Toast.makeText(this, "Không thể đọc dữ liệu ảnh", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // ── Kết quả ghi danh mống mắt ────────────────────────────────────────
        /*if (requestCode == REQUEST_CODE_ENROLL_IRIS && resultCode == RESULT_OK && data != null) {
            int code = data.getIntExtra(Constants.EXTRA_RESULT_CODE, -1);
            if (code == 0) {
                if (dbHelper.updateAdminIrisEnrollment(currentUserEmail)) {
                    textViewIrisStatus.setText("Đã ghi danh mống mắt");
                    textViewIrisStatus.setTextColor(0xFF4CAF50);
                    buttonEnrollIris.setText("Ghi danh lại");
                    Toast.makeText(this, "Ghi danh mống mắt thành công!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Lỗi cập nhật database", Toast.LENGTH_SHORT).show();
                }
            } else {
                String msg = data.getStringExtra(Constants.EXTRA_RESULT_MSG);
                new AlertDialog.Builder(this)
                        .setTitle("Lỗi ghi danh mống mắt")
                        .setMessage("Mã lỗi: " + code + "\n\n" + (msg != null ? msg : "Không rõ"))
                        .setPositiveButton("OK", null)
                        .show();
            }
        }*/
    }

    // ==================== XỬ LÝ ẢNH TỪ THƯ VIỆN ====================

    /**
     * Kiểm tra khớp chủ (nếu đã có avatar) hoặc check trùng (nếu chưa có).
     * Chạy trên background thread.
     */
    private void processGalleryFace(Bitmap faceBitmap) {
        new Thread(() -> {
            String adminId = dbHelper.getAdminIdByEmail(currentUserEmail);

            // Bước 1: Có avatar cũ → chỉ kiểm tra khớp chủ
            File existingFile = getAdminAvatarFile(adminId);
            if (existingFile != null && existingFile.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap oldBitmap = BitmapFactory.decodeFile(existingFile.getAbsolutePath(), opts);
                if (oldBitmap != null) {
                    float[] oldEmb = faceMatchHelper.getFaceEmbedding(oldBitmap);
                    float[] newEmb = faceMatchHelper.getFaceEmbedding(faceBitmap);
                    oldBitmap.recycle();

                    if (oldEmb != null && newEmb != null) {
                        if (!faceMatchHelper.compareFaces(oldEmb, newEmb)) {
                            faceBitmap.recycle();
                            runOnUiThread(() -> Toast.makeText(this,
                                    "❌ Khuôn mặt không khớp với ảnh hiện tại!",
                                    Toast.LENGTH_LONG).show());
                            return;
                        }
                    }
                    // Khớp chủ → lưu, không check trùng
                    saveGalleryPhoto(faceBitmap, adminId);
                    return;
                }
            }

            // Bước 2: Chưa có avatar → kiểm tra trùng trong thư mục admins/
            String duplicateId = checkDuplicateInAdminsFolder(faceBitmap, adminId);
            if (duplicateId != null) {
                faceBitmap.recycle();
                runOnUiThread(() -> Toast.makeText(this,
                        "❌ Khuôn mặt đã tồn tại: " + duplicateId,
                        Toast.LENGTH_LONG).show());
                return;
            }

            saveGalleryPhoto(faceBitmap, adminId);
        }).start();
    }

    /**
     * Lưu ảnh từ thư viện vào thư mục admins/ và cập nhật UI.
     */
    private void saveGalleryPhoto(Bitmap bitmap, String adminId) {
        File dir = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        if (!dir.exists()) dir.mkdirs();

        // Xóa file cũ
        new File(dir, "avatar_" + adminId + ".jpg").delete();
        new File(dir, "face_"   + adminId + ".jpg").delete();

        File outFile = new File(dir, "face_" + adminId + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (IOException e) {
            bitmap.recycle();
            runOnUiThread(() -> Toast.makeText(this,
                    "Lỗi lưu ảnh. Hãy thử lại.", Toast.LENGTH_SHORT).show());
            return;
        }

        String savedPath = outFile.getAbsolutePath();
        bitmap.recycle();

        runOnUiThread(() -> {
            photoPath = savedPath;
            Bitmap preview = BitmapFactory.decodeFile(photoPath);
            if (preview != null) imageViewAvatar.setImageBitmap(preview);
            Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
        });
    }

    /** Tìm file avatar hiện tại của admin trong thư mục admins/ */
    private File getAdminAvatarFile(String adminId) {
        File dir    = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        File avatar = new File(dir, "avatar_" + adminId + ".jpg");
        File face   = new File(dir, "face_"   + adminId + ".jpg");
        if (avatar.exists()) return avatar;
        if (face.exists())   return face;
        return null;
    }

    /** Kiểm tra trùng khuôn mặt trong thư mục admins/ (bỏ qua file của chính adminId) */
    private String checkDuplicateInAdminsFolder(Bitmap newFace, String adminId) {
        File dir = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        if (!dir.exists()) return null;
        File[] files = dir.listFiles();
        if (files == null) return null;

        float[] newEmb = faceMatchHelper.getFaceEmbedding(newFace);
        if (newEmb == null) return null;

        // Chỉ so trùng trong CÙNG role (SUPER_ADMIN chỉ so với SUPER_ADMIN, ADMIN chỉ so với ADMIN)
        String myRole = dbHelper.getRoleByAdminId(adminId);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;

        for (File file : files) {
            String name = file.getName();
            if (name.contains(adminId)) continue; // bỏ qua file của chính mình
            if (!file.isFile() || !name.endsWith(".jpg")) continue;

            // Lấy adminId khác từ tên file rồi kiểm tra role có khớp không
            String otherId = name.replace("avatar_", "").replace("face_", "").replace(".jpg", "");
            String otherRole = dbHelper.getRoleByAdminId(otherId);
            if (myRole != null && !myRole.equals(otherRole)) continue; // khác role → bỏ qua, không tính là trùng

            try {
                Bitmap existing = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (existing != null) {
                    float[] existEmb = faceMatchHelper.getFaceEmbedding(existing);
                    existing.recycle();
                    if (existEmb != null && faceMatchHelper.compareFaces(existEmb, newEmb)) {
                        return otherId;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("ProfileActivity", "Lỗi quét file: " + name, e);
            }
        }
        return null;
    }

    // ==================== CHẾ ĐỘ XEM / SỬA ====================

    private void setViewMode() {
        isEditMode = false;
        buttonEdit.setVisibility(View.VISIBLE);
        layoutActionButtons.setVisibility(View.GONE);
        layoutPasswordSection.setVisibility(View.GONE);
        layoutAvatarButtons.setVisibility(View.GONE);
        editTextEmail.setEnabled(false);
        editTextFullName.setEnabled(false);
        editTextDob.setEnabled(false);
        editTextPhone.setEnabled(false);
        spinnerGender.setEnabled(false);
        editTextDescription.setEnabled(false);
        textViewError.setVisibility(View.GONE);
        editTextPassword.setText("");
        editTextConfirmPassword.setText("");
    }

    private void setEditMode() {
        isEditMode = true;
        buttonEdit.setVisibility(View.GONE);
        layoutActionButtons.setVisibility(View.VISIBLE);
        layoutPasswordSection.setVisibility(View.VISIBLE);
        layoutAvatarButtons.setVisibility(View.VISIBLE);
        editTextEmail.setEnabled(true);
        editTextFullName.setEnabled(true);
        editTextDob.setEnabled(true);
        editTextPhone.setEnabled(true);
        spinnerGender.setEnabled(true);
        editTextDescription.setEnabled(true);
        textViewError.setVisibility(View.GONE);
    }

    // ==================== LƯU THÔNG TIN ====================

    private void showBiometricAuthentication() {
        BiometricAuthHelper.showBiometricPrompt(
                this,
                "Xác thực để lưu",
                "Sử dụng vân tay, khuôn mặt hoặc Device PIN",
                currentUserEmail,
                dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override public void onAuthSuccess()               { attemptSave(); }
                    @Override public void onAuthFailed()                { Toast.makeText(ProfileActivity.this, "Đã hủy xác thực", Toast.LENGTH_SHORT).show(); }
                    @Override public void onAuthError(String errorMsg)  { Toast.makeText(ProfileActivity.this, errorMsg, Toast.LENGTH_LONG).show(); }
                }
        );
    }

    private void attemptSave() {
        textViewError.setVisibility(View.GONE);

        String email           = editTextEmail.getText().toString().trim();
        String password        = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        String fullName        = editTextFullName.getText().toString().trim();
        String dob             = editTextDob.getText().toString().trim();
        String phone           = editTextPhone.getText().toString().trim();
        String gender          = spinnerGender.getSelectedItem().toString();
        String description     = editTextDescription.getText().toString().trim();

        if (!email.equals(currentUserEmail)) {
            if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showError("Email không hợp lệ");
                return;
            }
            if (dbHelper.isEmailExists(email)) {
                showError("Email này đã được sử dụng");
                return;
            }
        }

        if (!TextUtils.isEmpty(password) || !TextUtils.isEmpty(confirmPassword)) {
            if (password.length() < 6) { showError("Password phải có ít nhất 6 ký tự"); return; }
            if (!password.equals(confirmPassword)) { showError("Password và xác nhận không khớp"); return; }
            if (validationHelper.isSameAsOldPassword(currentUserEmail, password)) {
                showError("Mật khẩu mới không được giống với mật khẩu cũ");
                return;
            }
        }

        if (TextUtils.isEmpty(fullName)) { showError("Vui lòng nhập họ và tên"); return; }

        boolean result = dbHelper.updateAdmin(
                currentUserEmail,
                TextUtils.isEmpty(password) ? null : password,
                fullName, dob, phone, gender, photoPath, description
        );

        boolean emailChanged = !email.equals(currentUserEmail);
        if (emailChanged && result) result = dbHelper.updateAdminEmail(currentUserEmail, email);

        if (result) {
            if (isFirstLogin) {
                String emailToMark = emailChanged ? email : currentUserEmail;
                dbHelper.markFirstLoginComplete(emailToMark);
                Toast.makeText(this, "Đã hoàn thành thiết lập tài khoản!", Toast.LENGTH_LONG).show();
                isFirstLogin = false;
            } else {
                Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();
            }
            if (emailChanged) {
                EmailService.sendAccountUpdatedEmail(currentUserEmail, fullName,
                        "- Email đã thay đổi: " + currentUserEmail + " → " + email + "\n");
                currentUserEmail = email;
            }
            loadUserData();
            setViewMode();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    // ==================== QUYỀN / IRIS ====================

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Cần quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == REQUEST_IRIS_SDK_PERMISSION) {
            // fire-and-forget
        } else if (requestCode == REQUEST_IRIS_PERMISSIONS) {
            boolean allGranted = grantResults.length > 0;
            for (int r : grantResults) if (r != PackageManager.PERMISSION_GRANTED) { allGranted = false; break; }
            if (allGranted && pendingIrisIntent != null) {
                //LicenseCheckHelper.checkLicenseAndStartCapture(this, pendingIrisIntent, REQUEST_CODE_ENROLL_IRIS);
                pendingIrisIntent = null;
            } else {
                Toast.makeText(this, "Cần cấp quyền Camera và bộ nhớ để ghi danh mống mắt.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void enrollIrisForCurrentUser() {
        String safeUserId = currentUserEmail.replace("@", "_at_").replace(".", "_");
        boolean hasIris   = dbHelper.hasAdminEnrolledIris(currentUserEmail);

        if (hasIris) {
            new AlertDialog.Builder(this)
                    .setTitle("Ghi danh lại mống mắt")
                    .setMessage("Template cũ sẽ bị thay thế. Tiếp tục?")
                    .setPositiveButton("Tiếp tục", (d, w) -> startIrisEnroll(safeUserId))
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            startIrisEnroll(safeUserId);
        }
    }

    private void startIrisEnroll(String safeUserId) {
        /*Intent intent = new Intent(getApplicationContext(), CaptureActivity.class);
        intent.setAction(Constants.ACTION_ENROLL);
        intent.putExtra(Constants.EXTRA_USER_ID, safeUserId);
        checkIrisPermissionsAndStart(intent);*/
    }

    private void checkIrisPermissionsAndStart(Intent intent) {
        boolean hasWrite  = ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        boolean hasCamera = ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;

        if (hasWrite && hasCamera) {
            //LicenseCheckHelper.checkLicenseAndStartCapture(this, intent, REQUEST_CODE_ENROLL_IRIS);
            return;
        }
        pendingIrisIntent = intent;
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA},
                REQUEST_IRIS_PERMISSIONS);
    }
}
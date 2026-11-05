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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AdminDetailActivity extends AppCompatActivity {

    private DatabaseHelper  dbHelper;
    private FaceDetector    faceDetector;
    private FaceMatchHelper faceMatchHelper;

    private String currentSuperAdminEmail;
    private String targetAdminEmail;
    private boolean isEditMode = false;

    private ImageView    imageViewAvatar;
    private EditText     editTextEmail, editTextPassword, editTextConfirmPassword;
    private EditText     editTextFullName, editTextDob, editTextPhone, editTextDescription;
    private CheckBox     checkBoxShowPassword;
    private Spinner      spinnerGender;
    private TextView     textViewRole, textViewEmployeeCode, textViewError;
    private Button       buttonSave, buttonCancel, buttonEdit, buttonDelete;
    private Button       buttonCamera, buttonGallery;
    private LinearLayout layoutPasswordSection, layoutActionButtons;
    private LinearLayout layoutViewButtons, layoutAvatarButtons;

    private String photoPath;

    private static final int REQUEST_STORAGE_PERMISSION = 101;
    private static final int REQUEST_GALLERY_PICK       = 103;
    private static final int REQUEST_CODE_FACE_CAPTURE  = 200;

    private String oldFullName, oldDob, oldPhone, oldGender, oldDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_detail);

        dbHelper = new DatabaseHelper(this);

        currentSuperAdminEmail = getIntent().getStringExtra("SUPER_ADMIN_EMAIL");
        targetAdminEmail       = getIntent().getStringExtra("TARGET_ADMIN_EMAIL");

        if (TextUtils.isEmpty(currentSuperAdminEmail) || TextUtils.isEmpty(targetAdminEmail)) {
            Toast.makeText(this, "Lỗi: Không tìm thấy thông tin", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        FaceDetectorOptions opts = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector    = FaceDetection.getClient(opts);
        faceMatchHelper = new FaceMatchHelper(this);

        initializeViews();
        setupGenderSpinner();
        loadAdminData();
        setViewMode();
        setupListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector    != null) faceDetector.close();
        if (faceMatchHelper != null) faceMatchHelper.close();
    }

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
        buttonSave              = findViewById(R.id.buttonSave);
        buttonCancel            = findViewById(R.id.buttonCancel);
        buttonEdit              = findViewById(R.id.buttonEdit);
        buttonDelete            = findViewById(R.id.buttonDelete);
        layoutPasswordSection   = findViewById(R.id.layoutPasswordSection);
        layoutActionButtons     = findViewById(R.id.layoutActionButtons);
        layoutViewButtons       = findViewById(R.id.layoutViewButtons);
        layoutAvatarButtons     = findViewById(R.id.layoutAvatarButtons);
        buttonCamera            = findViewById(R.id.buttonCamera);
        buttonGallery           = findViewById(R.id.buttonGallery);
    }

    private void setupGenderSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.gender_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerGender.setAdapter(adapter);
    }

    private void loadAdminData() {
        Cursor cursor = dbHelper.getAdminByEmail(targetAdminEmail);
        if (cursor != null && cursor.moveToFirst()) {
            editTextEmail.setText(cursor.getString(cursor.getColumnIndexOrThrow("email")));
            editTextFullName.setText(cursor.getString(cursor.getColumnIndexOrThrow("full_name")));
            editTextDob.setText(cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth")));
            editTextPhone.setText(cursor.getString(cursor.getColumnIndexOrThrow("phone")));

            String gender = cursor.getString(cursor.getColumnIndexOrThrow("gender"));
            setSpinnerValue(spinnerGender, gender);

            editTextDescription.setText(cursor.getString(cursor.getColumnIndexOrThrow("description")));

            oldFullName    = cursor.getString(cursor.getColumnIndexOrThrow("full_name"));
            oldDob         = cursor.getString(cursor.getColumnIndexOrThrow("date_of_birth"));
            oldPhone       = cursor.getString(cursor.getColumnIndexOrThrow("phone"));
            oldGender      = gender;
            oldDescription = cursor.getString(cursor.getColumnIndexOrThrow("description"));

            String role = cursor.getString(cursor.getColumnIndexOrThrow("role"));
            textViewRole.setText("Vai trò: " + ("SUPER_ADMIN".equals(role) ? "Super Admin" : "Admin"));

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

            photoPath = cursor.getString(cursor.getColumnIndexOrThrow("photo_path"));
            loadAvatarImage(oldFullName);

            cursor.close();
        }
    }

    private void loadAvatarImage(String fullName) {
        if (!TextUtils.isEmpty(photoPath) && new File(photoPath).exists()) {
            Bitmap bmp = BitmapFactory.decodeFile(photoPath);
            if (bmp != null) { imageViewAvatar.setImageBitmap(bmp); return; }
        }
        imageViewAvatar.setImageBitmap(InitialsAvatarHelper.generateAvatar(fullName, 176));
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

    private void setupListeners() {
        buttonEdit.setOnClickListener(v -> setEditMode());

        buttonCancel.setOnClickListener(v -> {
            loadAdminData();
            setViewMode();
        });

        buttonDelete.setOnClickListener(v -> showBiometricAuthForDelete());

        checkBoxShowPassword.setOnCheckedChangeListener((btn, checked) -> {
            int type = checked
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
            editTextPassword.setInputType(type);
            editTextConfirmPassword.setInputType(type);
        });

        buttonSave.setOnClickListener(v -> showBiometricAuthentication());
        buttonCamera.setOnClickListener(v -> openFaceCapture());
        buttonGallery.setOnClickListener(v -> {
            if (checkStoragePermission()) openGallery();
        });
    }

    private void openFaceCapture() {
        String adminId = dbHelper.getAdminIdByEmail(targetAdminEmail);
        Intent intent  = new Intent(this, FaceCaptureActivity.class);
        intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID,     adminId);
        intent.putExtra(FaceCaptureActivity.EXTRA_EXISTING_PHOTO, photoPath);
        intent.putExtra(FaceCaptureActivity.EXTRA_MODE,   FaceCaptureActivity.MODE_REGISTER);
        intent.putExtra(FaceCaptureActivity.EXTRA_FOLDER, FaceCaptureActivity.FOLDER_ADMINS);
        startActivityForResult(intent, REQUEST_CODE_FACE_CAPTURE);
    }

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_FACE_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                String capturedPath = data.getStringExtra(FaceCaptureActivity.RESULT_PHOTO_PATH);
                if (capturedPath != null && !capturedPath.isEmpty()) {
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

        if (requestCode == REQUEST_GALLERY_PICK && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;
            try {
                InputStream is        = getContentResolver().openInputStream(uri);
                Bitmap      rawBitmap = BitmapFactory.decodeStream(is);
                if (rawBitmap == null) {
                    Toast.makeText(this, "Không thể đọc ảnh này", Toast.LENGTH_SHORT).show();
                    return;
                }
                InputImage inputImage = InputImage.fromBitmap(rawBitmap, 0);
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            if (faces == null || faces.isEmpty()) {
                                Toast.makeText(this, "❌ Không tìm thấy khuôn mặt trong ảnh!", Toast.LENGTH_LONG).show();
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
                                Toast.makeText(this, "❌ Không xác định được vùng mặt!", Toast.LENGTH_SHORT).show();
                                rawBitmap.recycle();
                                return;
                            }
                            Bitmap cropped = Bitmap.createBitmap(rawBitmap, x, y, w, h);
                            rawBitmap.recycle();
                            processGalleryFace(cropped);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this, "Lỗi phân tích ảnh: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            rawBitmap.recycle();
                        });
            } catch (Exception e) {
                Toast.makeText(this, "Không thể đọc dữ liệu ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processGalleryFace(Bitmap faceBitmap) {
        new Thread(() -> {
            String adminId = dbHelper.getAdminIdByEmail(targetAdminEmail);

            File existingFile = getAdminAvatarFile(adminId);
            if (existingFile != null && existingFile.exists()) {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 2;
                Bitmap oldBitmap = BitmapFactory.decodeFile(existingFile.getAbsolutePath(), opts);
                if (oldBitmap != null) {
                    float[] oldEmb = faceMatchHelper.getFaceEmbedding(oldBitmap);
                    float[] newEmb = faceMatchHelper.getFaceEmbedding(faceBitmap);
                    oldBitmap.recycle();

                    if (oldEmb != null && newEmb != null && !faceMatchHelper.compareFaces(oldEmb, newEmb)) {
                        faceBitmap.recycle();
                        runOnUiThread(() -> Toast.makeText(this, "❌ Khuôn mặt không khớp với ảnh hiện tại!", Toast.LENGTH_LONG).show());
                        return;
                    }
                    saveGalleryPhoto(faceBitmap, adminId);
                    return;
                }
            }

            String duplicateId = checkDuplicateInAdminsFolder(faceBitmap, adminId);
            if (duplicateId != null) {
                faceBitmap.recycle();
                runOnUiThread(() -> Toast.makeText(this, "❌ Khuôn mặt đã tồn tại: " + duplicateId, Toast.LENGTH_LONG).show());
                return;
            }

            saveGalleryPhoto(faceBitmap, adminId);
        }).start();
    }

    private void saveGalleryPhoto(Bitmap bitmap, String adminId) {
        File dir = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        if (!dir.exists()) dir.mkdirs();

        new File(dir, "avatar_" + adminId + ".jpg").delete();
        new File(dir, "face_"   + adminId + ".jpg").delete();

        File outFile = new File(dir, "face_" + adminId + ".jpg");
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (IOException e) {
            bitmap.recycle();
            runOnUiThread(() -> Toast.makeText(this, "Lỗi lưu ảnh. Hãy thử lại.", Toast.LENGTH_SHORT).show());
            return;
        }

        String saved = outFile.getAbsolutePath();
        bitmap.recycle();

        runOnUiThread(() -> {
            photoPath = saved;
            Bitmap preview = BitmapFactory.decodeFile(photoPath);
            if (preview != null) imageViewAvatar.setImageBitmap(preview);
            Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
        });
    }

    private File getAdminAvatarFile(String adminId) {
        File dir    = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        File avatar = new File(dir, "avatar_" + adminId + ".jpg");
        File face   = new File(dir, "face_"   + adminId + ".jpg");
        if (avatar.exists()) return avatar;
        if (face.exists())   return face;
        return null;
    }

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
            if (name.contains(adminId)) continue;
            if (!file.isFile() || !name.endsWith(".jpg")) continue;

            // Lấy adminId khác từ tên file rồi kiểm tra role có khớp không
            String otherId = name.replace("avatar_", "").replace("face_", "").replace(".jpg", "");
            String otherRole = dbHelper.getRoleByAdminId(otherId);
            if (myRole != null && !myRole.equals(otherRole)) continue; // khác role → bỏ qua

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
                android.util.Log.e("AdminDetailActivity", "Lỗi quét file: " + name, e);
            }
        }
        return null;
    }

    private void setViewMode() {
        isEditMode = false;
        layoutViewButtons.setVisibility(View.VISIBLE);
        layoutActionButtons.setVisibility(View.GONE);
        layoutPasswordSection.setVisibility(View.GONE);
        layoutAvatarButtons.setVisibility(View.GONE);
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
        layoutViewButtons.setVisibility(View.GONE);
        layoutActionButtons.setVisibility(View.VISIBLE);
        layoutPasswordSection.setVisibility(View.VISIBLE);
        layoutAvatarButtons.setVisibility(View.VISIBLE);
        editTextFullName.setEnabled(true);
        editTextDob.setEnabled(true);
        editTextPhone.setEnabled(true);
        spinnerGender.setEnabled(true);
        editTextDescription.setEnabled(true);
        textViewError.setVisibility(View.GONE);
    }

    private void showBiometricAuthForDelete() {
        BiometricAuthHelper.showBiometricPrompt(
                this, "Xác thực Super Admin", "Xác thực để xóa Admin này",
                currentSuperAdminEmail, dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override public void onAuthSuccess()              { performDelete(); }
                    @Override public void onAuthFailed()               { Toast.makeText(AdminDetailActivity.this, "Đã hủy xóa", Toast.LENGTH_SHORT).show(); }
                    @Override public void onAuthError(String errorMsg) { Toast.makeText(AdminDetailActivity.this, errorMsg, Toast.LENGTH_LONG).show(); }
                }
        );
    }

    private void performDelete() {
        String adminFullName = editTextFullName.getText().toString().trim();
        Cursor cursor = dbHelper.getAdminByEmail(targetAdminEmail);
        if (cursor != null && cursor.moveToFirst()) {
            String adminId = cursor.getString(cursor.getColumnIndexOrThrow("admin_id"));
            cursor.close();
            deleteAdminPhotoFiles(adminId);
            boolean success = dbHelper.deleteAdmin(adminId);
            if (success) {
                Toast.makeText(this, "Đã xóa Admin", Toast.LENGTH_SHORT).show();
                EmailService.sendAccountDeletedEmail(targetAdminEmail, adminFullName);
                finish();
            } else {
                Toast.makeText(this, "Lỗi khi xóa Admin", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void deleteAdminPhotoFiles(String adminId) {
        File dir = new File(getExternalFilesDir(null), FaceCaptureActivity.FOLDER_ADMINS);
        if (!dir.exists()) return;
        for (String prefix : new String[]{"face_", "avatar_"}) {
            File f = new File(dir, prefix + adminId + ".jpg");
            if (f.exists()) f.delete();
        }
    }

    private void showBiometricAuthentication() {
        BiometricAuthHelper.showBiometricPrompt(
                this, "Xác thực Super Admin", "Xác thực để lưu thay đổi",
                currentSuperAdminEmail, dbHelper,
                new BiometricAuthHelper.AuthCallback() {
                    @Override public void onAuthSuccess()              { attemptSave(); }
                    @Override public void onAuthFailed()               { Toast.makeText(AdminDetailActivity.this, "Đã hủy xác thực", Toast.LENGTH_SHORT).show(); }
                    @Override public void onAuthError(String errorMsg) { Toast.makeText(AdminDetailActivity.this, errorMsg, Toast.LENGTH_LONG).show(); }
                }
        );
    }

    private void attemptSave() {
        textViewError.setVisibility(View.GONE);

        String password        = editTextPassword.getText().toString().trim();
        String confirmPassword = editTextConfirmPassword.getText().toString().trim();
        String fullName        = editTextFullName.getText().toString().trim();
        String dob             = editTextDob.getText().toString().trim();
        String phone           = editTextPhone.getText().toString().trim();
        String gender          = spinnerGender.getSelectedItem().toString();
        String description     = editTextDescription.getText().toString().trim();

        if (!TextUtils.isEmpty(password) || !TextUtils.isEmpty(confirmPassword)) {
            if (password.length() < 6)            { showError("Password phải có ít nhất 6 ký tự"); return; }
            if (!password.equals(confirmPassword)) { showError("Password và xác nhận không khớp");  return; }
        }

        if (TextUtils.isEmpty(fullName)) { showError("Vui lòng nhập họ và tên"); return; }

        boolean result = dbHelper.updateAdmin(
                targetAdminEmail,
                TextUtils.isEmpty(password) ? null : password,
                fullName, dob, phone, gender, photoPath, description
        );

        if (result) {
            Toast.makeText(this, "Cập nhật thành công", Toast.LENGTH_SHORT).show();

            StringBuilder changes = new StringBuilder();
            if (!TextUtils.isEmpty(password))          changes.append("- Mật khẩu đã được thay đổi\n");
            if (!fullName.equals(oldFullName))         changes.append("- Họ tên: ").append(oldFullName).append(" → ").append(fullName).append("\n");
            if (!TextUtils.isEmpty(dob) && !dob.equals(oldDob))         changes.append("- Ngày sinh đã cập nhật\n");
            if (!TextUtils.isEmpty(phone) && !phone.equals(oldPhone))   changes.append("- SĐT: ").append(oldPhone).append(" → ").append(phone).append("\n");
            if (!gender.equals(oldGender))             changes.append("- Giới tính đã cập nhật\n");
            if (!description.equals(oldDescription))   changes.append("- Mô tả đã cập nhật\n");

            if (changes.length() > 0) {
                EmailService.sendAccountUpdatedEmail(targetAdminEmail, fullName, changes.toString());
            }

            loadAdminData();
            setViewMode();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    private void showError(String message) {
        textViewError.setText(message);
        textViewError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "Cần quyền truy cập bộ nhớ", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
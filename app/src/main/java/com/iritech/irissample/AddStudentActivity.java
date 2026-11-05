package com.iritech.irissample;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AddStudentActivity extends AppCompatActivity {

    private static final String TAG = "AddStudentActivity";
    private static final int REQUEST_IMAGE_PICK        = 2001;
    private static final int REQUEST_CODE_FACE_CAPTURE = 2005;

    private EditText  edtStudentId, edtStudentName, edtStudentEmail, edtStudentPhone, edtCheckinPassword;
    private Button    btnSaveStudent;
    private ImageView imgAvatar;
    private CardView  cardAvatar;

    private DatabaseHelper  dbHelper;
    private FaceDetector    faceDetector;
    private FaceMatchHelper faceMatchHelper;

    private String  subjectId;
    private String  editStudentId;
    private boolean isEditMode = false;

    private String currentSelectedPhotoPath = null;
    private Bitmap pendingFaceBitmap        = null;

    // ===================== LIFECYCLE =====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_student);

        edtStudentId       = findViewById(R.id.edtStudentId);
        edtStudentName     = findViewById(R.id.edtStudentName);
        edtStudentEmail    = findViewById(R.id.edtStudentEmail);
        edtStudentPhone    = findViewById(R.id.edtStudentPhone);
        edtCheckinPassword = findViewById(R.id.edtCheckinPassword);
        btnSaveStudent     = findViewById(R.id.btnSaveStudent);
        imgAvatar          = findViewById(R.id.imgAvatar);
        cardAvatar         = findViewById(R.id.cardAvatar);

        dbHelper  = new DatabaseHelper(this);
        subjectId = getIntent().getStringExtra("subject_id");
        editStudentId = getIntent().getStringExtra("student_id");

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(0.15f)
                .build();
        faceDetector    = FaceDetection.getClient(options);
        faceMatchHelper = new FaceMatchHelper(this);

        if (editStudentId != null && !editStudentId.isEmpty()) {
            isEditMode = true;
            loadStudentInfo(editStudentId);
            edtStudentId.setEnabled(false);
            btnSaveStudent.setText("Cập nhật thông tin");
        }

        cardAvatar.setOnClickListener(v -> showImageImportDialog());

        btnSaveStudent.setOnClickListener(v -> {
            if (isEditMode) updateStudent();
            else            saveStudent();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetector    != null) faceDetector.close();
        if (faceMatchHelper != null) faceMatchHelper.close();
        if (pendingFaceBitmap != null) {
            pendingFaceBitmap.recycle();
            pendingFaceBitmap = null;
        }
        // Dọn dẹp ảnh tạm nếu người dùng rời màn hình mà không bấm lưu
        cleanUpTempPhotoIfNeeded();
    }

    private void cleanUpTempPhotoIfNeeded() {
        if (currentSelectedPhotoPath != null && currentSelectedPhotoPath.contains("temp_avatar_")) {
            File tempFile = new File(currentSelectedPhotoPath);
            if (tempFile.exists()) tempFile.delete();
        }
    }

    // ===================== DIALOG CHỌN NGUỒN ẢNH =====================

    private void showImageImportDialog() {
        String[] options = {"Chụp ảnh mới bằng Camera", "Chọn ảnh từ thư viện thiết bị"};
        new AlertDialog.Builder(this)
                .setTitle("Thay đổi ảnh đại diện gốc")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, FaceCaptureActivity.class);
                        String currentId = edtStudentId.getText().toString().trim();
                        intent.putExtra(FaceCaptureActivity.EXTRA_STUDENT_ID,
                                currentId.isEmpty() ? "temp_capture" : currentId);
                        startActivityForResult(intent, REQUEST_CODE_FACE_CAPTURE);
                    } else {
                        Intent pickPhoto = new Intent(
                                Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                        startActivityForResult(pickPhoto, REQUEST_IMAGE_PICK);
                    }
                })
                .show();
    }

    // ===================== XỬ LÝ KẾT QUẢ TỪ CAMERA / THƯ VIỆN =====================

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) return;

        if (requestCode == REQUEST_CODE_FACE_CAPTURE && data != null) {
            String photoPath = data.getStringExtra(FaceCaptureActivity.RESULT_PHOTO_PATH);

            if (photoPath == null || photoPath.isEmpty()) {
                Toast.makeText(this, "Không nhận diện được khuôn mặt hợp lệ.", Toast.LENGTH_LONG).show();
                return;
            }

            Bitmap bitmap = BitmapFactory.decodeFile(photoPath);
            if (bitmap == null) {
                Toast.makeText(this, "Không thể đọc ảnh chụp.", Toast.LENGTH_SHORT).show();
                return;
            }

            cleanUpTempPhotoIfNeeded(); // Dọn ảnh tạm cũ nếu chụp lại ảnh mới

            if (pendingFaceBitmap != null) pendingFaceBitmap.recycle();
            pendingFaceBitmap        = bitmap;
            currentSelectedPhotoPath = photoPath; // Đường dẫn file tạm từ FaceCaptureActivity
            imgAvatar.setImageBitmap(bitmap);
            Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
        }

        else if (requestCode == REQUEST_IMAGE_PICK && data != null) {
            Uri selectedImage = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(selectedImage);
                Bitmap rawBitmap = BitmapFactory.decodeStream(inputStream);
                if (rawBitmap == null) {
                    Toast.makeText(this, "Không thể đọc ảnh này", Toast.LENGTH_SHORT).show();
                    return;
                }

                InputImage inputImage = InputImage.fromBitmap(rawBitmap, 0);
                faceDetector.process(inputImage)
                        .addOnSuccessListener(faces -> {
                            if (faces.isEmpty()) {
                                Toast.makeText(this,
                                        "❌ Không tìm thấy khuôn mặt trong ảnh này!\nVui lòng chọn ảnh có khuôn mặt rõ ràng.",
                                        Toast.LENGTH_LONG).show();
                                rawBitmap.recycle();
                                return;
                            }

                            Face detectedFace = faces.get(0);
                            Rect bounds = detectedFace.getBoundingBox();
                            int x = Math.max(0, bounds.left);
                            int y = Math.max(0, bounds.top);
                            int w = Math.min(bounds.width(),  rawBitmap.getWidth()  - x);
                            int h = Math.min(bounds.height(), rawBitmap.getHeight() - y);

                            if (w <= 0 || h <= 0) {
                                Toast.makeText(this,
                                        "❌ Không thể xác định vùng khuôn mặt!",
                                        Toast.LENGTH_SHORT).show();
                                rawBitmap.recycle();
                                return;
                            }

                            Bitmap croppedFace = Bitmap.createBitmap(rawBitmap, x, y, w, h);
                            rawBitmap.recycle();

                            processFaceBitmap(croppedFace, null);
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(this,
                                    "Lỗi phân tích ảnh: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                            rawBitmap.recycle();
                        });

            } catch (Exception e) {
                Log.e(TAG, "Lỗi đọc ảnh thư viện", e);
                Toast.makeText(this, "Không thể đọc dữ liệu ảnh này", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void processFaceBitmap(Bitmap faceBitmap, String tempFilePath) {
        new Thread(() -> {
            String currentStudentId = edtStudentId.getText().toString().trim();

            if (!currentStudentId.isEmpty()) {
                File existingAvatarFile = getAvatarFileForStudent(currentStudentId);
                if (existingAvatarFile != null) {
                    BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
                    decodeOpts.inSampleSize = 2;
                    Bitmap oldBitmap = BitmapFactory.decodeFile(
                            existingAvatarFile.getAbsolutePath(), decodeOpts);

                    if (oldBitmap != null) {
                        float[] oldEmbedding = faceMatchHelper.getFaceEmbedding(oldBitmap);
                        float[] newEmbedding = faceMatchHelper.getFaceEmbedding(faceBitmap);
                        oldBitmap.recycle();

                        if (oldEmbedding != null && newEmbedding != null) {
                            boolean isOwner = faceMatchHelper.compareFaces(oldEmbedding, newEmbedding);
                            if (!isOwner) {
                                faceBitmap.recycle();
                                deleteIfExists(tempFilePath);
                                runOnUiThread(() -> Toast.makeText(this,
                                        "❌ Khuôn mặt không khớp với avatar hiện tại của sinh viên này!",
                                        Toast.LENGTH_LONG).show());
                                return;
                            }
                        }

                        deleteIfExists(tempFilePath);
                        runOnUiThread(() -> {
                            cleanUpTempPhotoIfNeeded();
                            if (pendingFaceBitmap != null) pendingFaceBitmap.recycle();
                            pendingFaceBitmap        = faceBitmap;
                            currentSelectedPhotoPath = null;
                            imgAvatar.setImageBitmap(faceBitmap);
                            Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
                        });
                        return;
                    }
                }
            }

            String duplicateId = checkIfFaceAlreadyExists(faceBitmap, currentStudentId);
            if (duplicateId != null) {
                faceBitmap.recycle();
                deleteIfExists(tempFilePath);
                runOnUiThread(() -> Toast.makeText(this,
                        "❌ Gian lận: Khuôn mặt này đã được dùng bởi sinh viên mã: " + duplicateId,
                        Toast.LENGTH_LONG).show());
                return;
            }

            deleteIfExists(tempFilePath);
            runOnUiThread(() -> {
                cleanUpTempPhotoIfNeeded();
                if (pendingFaceBitmap != null) pendingFaceBitmap.recycle();
                pendingFaceBitmap        = faceBitmap;
                currentSelectedPhotoPath = null;
                imgAvatar.setImageBitmap(faceBitmap);
                Toast.makeText(this, "✅ Ảnh đại diện hợp lệ!", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void deleteIfExists(String path) {
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) f.delete();
        }
    }

    // ===================== KIỂM TRA CHỦ SỞ HỮU AVATAR =====================

    private File getAvatarFileForStudent(String studentId) {
        File dir    = new File(getExternalFilesDir(null), "students");
        File avatar = new File(dir, "avatar_" + studentId + ".jpg");
        if (avatar.exists()) return avatar;
        File face   = new File(dir, "face_"   + studentId + ".jpg");
        if (face.exists())   return face;
        return null;
    }

    // ===================== KIỂM TRA TRÙNG KHUÔN MẶT VỚI SINH VIÊN KHÁC =====================

    private String checkIfFaceAlreadyExists(Bitmap newFaceCrop, String currentStudentId) {
        File dir = new File(getExternalFilesDir(null), "students");
        if (!dir.exists()) return null;

        File[] files = dir.listFiles();
        if (files == null) return null;

        float[] newEmbedding = faceMatchHelper.getFaceEmbedding(newFaceCrop);
        if (newEmbedding == null) return null;

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = 2;

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("temp_")) continue;

            if (!currentStudentId.isEmpty()
                    && (name.contains("face_"   + currentStudentId)
                    ||  name.contains("avatar_" + currentStudentId))) continue;

            if (file.isFile() && name.endsWith(".jpg")) {
                try {
                    Bitmap existing = BitmapFactory.decodeFile(file.getAbsolutePath(), decodeOptions);
                    if (existing != null) {
                        float[] existingEmbedding = faceMatchHelper.getFaceEmbedding(existing);
                        existing.recycle();
                        if (existingEmbedding != null
                                && faceMatchHelper.compareFaces(existingEmbedding, newEmbedding)) {
                            return name.replace("avatar_", "").replace("face_", "").replace(".jpg", "");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi đối chiếu tệp: " + e.getMessage());
                }
            }
        }
        return null;
    }

    // ===================== LƯU/CHUYỂN FILE ẢNH VÀO THƯ MỤC CHÍNH THỨC =====================

    /**
     * Đồng bộ duy nhất tên file `avatar_<studentId>.jpg`.
     * Xóa sạch file cũ (cả `avatar_` và `face_`) và chuyển file mới vào chính thức.
     */
    private String finalizePhotoFile(String studentId) {
        File dir = new File(getExternalFilesDir(null), "students");
        if (!dir.exists()) dir.mkdirs();

        File targetOfficialFile = new File(dir, "avatar_" + studentId + ".jpg");

        // 1. Trường hợp chọn từ thư viện (có pendingFaceBitmap)
        if (pendingFaceBitmap != null && (currentSelectedPhotoPath == null || !currentSelectedPhotoPath.contains("temp_avatar_"))) {
            deleteOldStudentPhotoFiles(dir, studentId);
            try (FileOutputStream fos = new FileOutputStream(targetOfficialFile)) {
                pendingFaceBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                return targetOfficialFile.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, "Lỗi ghi file ảnh chính thức từ Bitmap", e);
                return null;
            }
        }

        // 2. Trường hợp chụp từ camera (có file tạm temp_avatar_...)
        if (currentSelectedPhotoPath != null && currentSelectedPhotoPath.contains("temp_avatar_")) {
            File tempFile = new File(currentSelectedPhotoPath);
            if (tempFile.exists()) {
                deleteOldStudentPhotoFiles(dir, studentId);
                if (tempFile.renameTo(targetOfficialFile)) {
                    return targetOfficialFile.getAbsolutePath();
                } else {
                    // Mẹo dự phòng nếu renameTo thất bại khác filesystem
                    try (InputStream in = new java.io.FileInputStream(tempFile);
                         FileOutputStream out = new FileOutputStream(targetOfficialFile)) {
                        byte[] buf = new byte[1024];
                        int len;
                        while ((len = in.read(buf)) > 0) {
                            out.write(buf, 0, len);
                        }
                        tempFile.delete();
                        return targetOfficialFile.getAbsolutePath();
                    } catch (IOException e) {
                        Log.e(TAG, "Lỗi copy file tạm sang chính thức", e);
                    }
                }
            }
        }

        return currentSelectedPhotoPath;
    }

    private void deleteOldStudentPhotoFiles(File dir, String studentId) {
        File oldAvatar = new File(dir, "avatar_" + studentId + ".jpg");
        File oldFace   = new File(dir, "face_"   + studentId + ".jpg");
        if (oldAvatar.exists()) oldAvatar.delete();
        if (oldFace.exists())   oldFace.delete();
    }

    // ===================== LOAD THÔNG TIN SINH VIÊN =====================

    private void loadStudentInfo(String studentId) {
        SQLiteDatabase db     = dbHelper.getReadableDatabase();
        Cursor         cursor = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.TABLE_STUDENTS
                        + " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                new String[]{studentId});

        if (cursor.moveToFirst()) {
            edtStudentId.setText(cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_STUDENT_ID)));
            edtStudentName.setText(cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_FULL_NAME)));
            edtStudentEmail.setText(cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_EMAIL)));
            edtStudentPhone.setText(cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHONE)));
            edtCheckinPassword.setText(cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_CHECKIN_PASSWORD)));

            currentSelectedPhotoPath = cursor.getString(
                    cursor.getColumnIndexOrThrow(DatabaseHelper.COL_PHOTO_PATH));
            if (currentSelectedPhotoPath != null
                    && !currentSelectedPhotoPath.isEmpty()
                    && new File(currentSelectedPhotoPath).exists()) {
                imgAvatar.setImageBitmap(BitmapFactory.decodeFile(currentSelectedPhotoPath));
            }
        }
        cursor.close();
    }

    // ===================== CẬP NHẬT SINH VIÊN =====================

    private void updateStudent() {
        String studentId       = edtStudentId.getText().toString().trim();
        String studentName     = edtStudentName.getText().toString().trim();
        String studentEmail    = edtStudentEmail.getText().toString().trim();
        String studentPhone    = edtStudentPhone.getText().toString().trim();
        String checkinPassword = edtCheckinPassword.getText().toString().trim();

        if (studentId.isEmpty() || studentName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển ảnh tạm / Bitmap thành file chính thức khi lưu thành công
        String finalPath = finalizePhotoFile(studentId);
        if (finalPath != null) currentSelectedPhotoPath = finalPath;

        SQLiteDatabase db     = dbHelper.getWritableDatabase();
        ContentValues  values = new ContentValues();
        values.put(DatabaseHelper.COL_FULL_NAME,        studentName);
        values.put(DatabaseHelper.COL_EMAIL,            studentEmail);
        values.put(DatabaseHelper.COL_PHONE,            studentPhone);
        values.put(DatabaseHelper.COL_CHECKIN_PASSWORD, checkinPassword);
        if (currentSelectedPhotoPath != null) {
            values.put(DatabaseHelper.COL_PHOTO_PATH, currentSelectedPhotoPath);
        }

        int rowsAffected = db.update(DatabaseHelper.TABLE_STUDENTS, values,
                DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});

        if (rowsAffected > 0) {
            Toast.makeText(this, "Cập nhật thông tin thành công", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Cập nhật thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== THÊM SINH VIÊN MỚI =====================

    private void saveStudent() {
        String studentId       = edtStudentId.getText().toString().trim();
        String studentName     = edtStudentName.getText().toString().trim();
        String studentEmail    = edtStudentEmail.getText().toString().trim();
        String studentPhone    = edtStudentPhone.getText().toString().trim();
        String checkinPassword = edtCheckinPassword.getText().toString().trim();

        if (studentId.isEmpty() || studentName.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkinPassword.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập mật khẩu điểm danh", Toast.LENGTH_SHORT).show();
            return;
        }

        // Chuyển ảnh tạm / Bitmap thành file chính thức khi lưu
        String finalPath = finalizePhotoFile(studentId);
        if (finalPath != null) currentSelectedPhotoPath = finalPath;

        SQLiteDatabase db     = dbHelper.getWritableDatabase();
        Cursor         cursor = db.rawQuery(
                "SELECT * FROM " + DatabaseHelper.TABLE_STUDENTS
                        + " WHERE " + DatabaseHelper.COL_STUDENT_ID + " = ?",
                new String[]{studentId});

        if (cursor.getCount() == 0) {
            // Trường hợp 1: Sinh viên chưa tồn tại -> INSERT mới
            ContentValues studentValues = new ContentValues();
            studentValues.put(DatabaseHelper.COL_STUDENT_ID,       studentId);
            studentValues.put(DatabaseHelper.COL_FULL_NAME,        studentName);
            studentValues.put(DatabaseHelper.COL_EMAIL,            studentEmail);
            studentValues.put(DatabaseHelper.COL_PHONE,            studentPhone);
            studentValues.put(DatabaseHelper.COL_CHECKIN_PASSWORD, checkinPassword);
            if (currentSelectedPhotoPath != null) {
                studentValues.put(DatabaseHelper.COL_PHOTO_PATH, currentSelectedPhotoPath);
            }

            long result = db.insert(DatabaseHelper.TABLE_STUDENTS, null, studentValues);
            if (result == -1) {
                Toast.makeText(this, "Thêm sinh viên thất bại", Toast.LENGTH_SHORT).show();
                cursor.close();
                return;
            }
        } else if (currentSelectedPhotoPath != null) {
            // Trường hợp 2: Sinh viên ĐÃ tồn tại + Có chọn/chụp ảnh mới -> UPDATE photo_path mới
            ContentValues photoValues = new ContentValues();
            photoValues.put(DatabaseHelper.COL_PHOTO_PATH, currentSelectedPhotoPath);
            db.update(DatabaseHelper.TABLE_STUDENTS, photoValues,
                    DatabaseHelper.COL_STUDENT_ID + " = ?", new String[]{studentId});
        }
        cursor.close();

        // Ghi nhận sinh viên vào bảng Enrollments cho môn học
        ContentValues enrollmentValues = new ContentValues();
        enrollmentValues.put(DatabaseHelper.COL_STUDENT_ID,        studentId);
        enrollmentValues.put(DatabaseHelper.COL_SUBJECT_ID,        subjectId);
        enrollmentValues.put(DatabaseHelper.COL_ENROLLMENT_STATUS, "Not Enrolled");

        long enrollmentResult = db.insert(DatabaseHelper.TABLE_ENROLLMENTS, null, enrollmentValues);
        if (enrollmentResult != -1) {
            Toast.makeText(this, "Thêm sinh viên thành công", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        } else {
            Toast.makeText(this, "Sinh viên đã được thêm vào môn học này", Toast.LENGTH_SHORT).show();
        }
    }
}
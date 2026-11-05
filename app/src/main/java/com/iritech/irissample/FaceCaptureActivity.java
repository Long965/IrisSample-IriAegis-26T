package com.iritech.irissample;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FaceCaptureActivity extends AppCompatActivity
        implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final String TAG = "FaceCaptureActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 100;

    // ── Extras truyền vào ──────────────────────────────────────────────────────
    public static final String EXTRA_STUDENT_ID     = "student_id";
    public static final String EXTRA_EXISTING_PHOTO = "existing_photo";
    public static final String EXTRA_MODE           = "mode";

    public static final String EXTRA_FOLDER  = "folder";
    public static final String FOLDER_STUDENTS = "students";
    public static final String FOLDER_ADMINS   = "admins";

    public static final String MODE_REGISTER = "register";
    public static final String MODE_CHECKIN  = "checkin";

    // ── Extras trả về ──────────────────────────────────────────────────────────
    public static final String RESULT_PHOTO_PATH   = "photo_path";
    public static final String RESULT_FACE_MATCHED = "face_matched";

    // ── Views ──────────────────────────────────────────────────────────────────
    private SurfaceView surfaceView;
    private TextView    tvStatus;
    private Button      btnCapture;
    private Button      btnCancel;

    // ── Camera & AI ───────────────────────────────────────────────────────────
    private Camera          camera;
    private FaceDetector    faceDetector;
    private FaceMatchHelper faceMatchHelper;
    private ExecutorService backgroundExecutor;

    // ── Trạng thái ────────────────────────────────────────────────────────────
    private volatile boolean isSafeToCapture   = false;
    private volatile boolean isProcessingFrame = false;

    // ── Dữ liệu từ Intent ─────────────────────────────────────────────────────
    private String  entityId;           // student_id hoặc admin_id
    private String  existingPhotoPath;
    private boolean isAutoCheckinMode = false;
    private String  targetFolder;       // "students" hoặc "admins"

    // ── Timeout (chỉ dùng ở chế độ checkin) ──────────────────────────────────
    private final Handler timeoutHandler = new Handler();
    private Runnable timeoutRunnable;
    private boolean  isTimeoutStarted = false;

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face_capture);

        entityId          = getIntent().getStringExtra(EXTRA_STUDENT_ID);
        existingPhotoPath = getIntent().getStringExtra(EXTRA_EXISTING_PHOTO);
        String mode       = getIntent().getStringExtra(EXTRA_MODE);
        targetFolder      = getIntent().getStringExtra(EXTRA_FOLDER);

        if (targetFolder == null || targetFolder.isEmpty()) {
            targetFolder = FOLDER_STUDENTS;
        }

        surfaceView = findViewById(R.id.surfaceView);
        tvStatus    = findViewById(R.id.tvStatus);
        btnCapture  = findViewById(R.id.btnCapture);
        btnCancel   = findViewById(R.id.btnCancel);

        faceMatchHelper    = new FaceMatchHelper(this);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.25f)
                .build();
        faceDetector = FaceDetection.getClient(options);

        surfaceView.getHolder().addCallback(this);

        if (MODE_CHECKIN.equals(mode)) {
            isAutoCheckinMode = true;
        } else if (MODE_REGISTER.equals(mode)) {
            isAutoCheckinMode = false;
        } else {
            isAutoCheckinMode = (existingPhotoPath != null
                    && !existingPhotoPath.isEmpty()
                    && new File(existingPhotoPath).exists());
        }

        if (isAutoCheckinMode) {
            btnCapture.setVisibility(View.GONE);
            btnCancel.setVisibility(View.GONE);
            tvStatus.setText("🤖 Đang tự động quét khuôn mặt...");
        } else {
            btnCapture.setVisibility(View.VISIBLE);
            btnCancel.setVisibility(View.VISIBLE);
            btnCapture.setEnabled(true);
            tvStatus.setText("Nhìn thẳng camera và bấm nút để chụp ảnh đại diện");
        }

        btnCapture.setOnClickListener(v -> {
            if (isSafeToCapture) capturePhotoManual();
        });

        btnCancel.setOnClickListener(v -> {
            stopTimeout();
            setResult(RESULT_CANCELED);
            finish();
        });

        checkCameraPermission();
    }

    @Override
    protected void onDestroy() {
        stopTimeout();
        releaseCamera();
        if (faceDetector    != null) faceDetector.close();
        if (faceMatchHelper != null) faceMatchHelper.close();
        if (backgroundExecutor != null) backgroundExecutor.shutdown();
        super.onDestroy();
    }

    // ==================== TIMEOUT ====================

    private void startTimeoutCount() {
        if (isTimeoutStarted) return;
        isTimeoutStarted = true;

        timeoutRunnable = () -> {
            isSafeToCapture   = false;
            isProcessingFrame = true;
            runOnUiThread(() -> {
                tvStatus.setText("❌ Hết thời gian! Xác thực không thành công.");
                Toast.makeText(FaceCaptureActivity.this,
                        "Quá thời gian quét! Tự động thoát...", Toast.LENGTH_LONG).show();

                Intent result = new Intent();
                result.putExtra(RESULT_FACE_MATCHED, false);
                setResult(RESULT_OK, result);
                finish();
            });
        };
        timeoutHandler.postDelayed(timeoutRunnable, 10000);
    }

    private void stopTimeout() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
        }
        isTimeoutStarted = false;
    }

    // ==================== QUYỀN CAMERA ====================

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền truy cập Camera", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    // ==================== CAMERA ====================

    private void startCamera() {
        try {
            int cameraCount   = Camera.getNumberOfCameras();
            int frontCameraId = -1;

            for (int i = 0; i < cameraCount; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    frontCameraId = i;
                }
            }

            if (frontCameraId == -1) {
                Log.w(TAG, "Không tìm thấy camera trước, fallback ID=0");
                frontCameraId = 0;
            }

            camera = Camera.open(frontCameraId);
            camera.setPreviewDisplay(surfaceView.getHolder());
            setCameraDisplayOrientation();

            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> supported  = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize      = null;
            for (Camera.Size size : supported) {
                if (size.width <= 1024 && size.height <= 768) {
                    if (optimalSize == null || size.width > optimalSize.width) {
                        optimalSize = size;
                    }
                }
            }
            if (optimalSize != null) {
                parameters.setPreviewSize(optimalSize.width, optimalSize.height);
                camera.setParameters(parameters);
            }

            camera.setPreviewCallback(this);
            camera.startPreview();
            isSafeToCapture = true;

        } catch (Exception e) {
            Log.e(TAG, "Camera error", e);
            Toast.makeText(this, "Không thể mở camera trước", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info = new Camera.CameraInfo();
        int cameraId = 0;
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                cameraId = i;
                break;
            }
        }
        Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:   degrees = 0;   break;
            case Surface.ROTATION_90:  degrees = 90;  break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    // ==================== LUỒNG PREVIEW (TỰ ĐỘNG CHECKIN) ====================

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!isAutoCheckinMode || isProcessingFrame || !isSafeToCapture) return;
        isProcessingFrame = true;

        try {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            InputImage image = InputImage.fromByteArray(
                    data, size.width, size.height, 270, InputImage.IMAGE_FORMAT_NV21);

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces == null || faces.isEmpty()) {
                            isProcessingFrame = false;
                            return;
                        }

                        Face detectedFace = faces.get(0);
                        backgroundExecutor.execute(() -> {
                            try {
                                YuvImage yuvImage = new YuvImage(
                                        data, ImageFormat.NV21, size.width, size.height, null);
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                yuvImage.compressToJpeg(
                                        new Rect(0, 0, size.width, size.height), 75, os);
                                byte[] jpegBytes = os.toByteArray();

                                Bitmap srcBitmap = BitmapFactory.decodeByteArray(
                                        jpegBytes, 0, jpegBytes.length);
                                if (srcBitmap == null) {
                                    isProcessingFrame = false;
                                    return;
                                }

                                Matrix matrix = new Matrix();
                                matrix.postRotate(270);
                                matrix.postScale(-1, 1);
                                Bitmap rotatedBitmap = Bitmap.createBitmap(
                                        srcBitmap, 0, 0,
                                        srcBitmap.getWidth(), srcBitmap.getHeight(),
                                        matrix, true);
                                srcBitmap.recycle();

                                Rect bounds = detectedFace.getBoundingBox();
                                int x = Math.max(0, bounds.left);
                                int y = Math.max(0, bounds.top);
                                int w = Math.min(bounds.width(),  rotatedBitmap.getWidth()  - x);
                                int h = Math.min(bounds.height(), rotatedBitmap.getHeight() - y);

                                if (w > 0 && h > 0) {
                                    Bitmap croppedFace = Bitmap.createBitmap(
                                            rotatedBitmap, x, y, w, h);
                                    rotatedBitmap.recycle();
                                    verifyFaceAuto(croppedFace);
                                } else {
                                    rotatedBitmap.recycle();
                                    isProcessingFrame = false;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Auto checkin background error", e);
                                isProcessingFrame = false;
                            }
                        });
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Auto checkin MLKit error", e);
                        isProcessingFrame = false;
                    });

        } catch (Exception e) {
            Log.e(TAG, "onPreviewFrame error", e);
            isProcessingFrame = false;
        }
    }

    private void verifyFaceAuto(Bitmap currentFaceCrop) {
        if (existingPhotoPath == null || !new File(existingPhotoPath).exists()) {
            currentFaceCrop.recycle();
            isProcessingFrame = false;
            return;
        }

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;
        Bitmap existingBitmap = BitmapFactory.decodeFile(existingPhotoPath, opts);

        if (existingBitmap == null) {
            currentFaceCrop.recycle();
            isProcessingFrame = false;
            return;
        }

        float[] existingEmbedding = faceMatchHelper.getFaceEmbedding(existingBitmap);
        float[] currentEmbedding  = faceMatchHelper.getFaceEmbedding(currentFaceCrop);

        existingBitmap.recycle();
        currentFaceCrop.recycle();

        if (existingEmbedding == null || currentEmbedding == null) {
            isProcessingFrame = false;
            return;
        }

        boolean matched = faceMatchHelper.compareFaces(existingEmbedding, currentEmbedding);

        if (matched) {
            stopTimeout();
            isSafeToCapture = false;
            runOnUiThread(() -> tvStatus.setText("✅ Xác thực thành công!"));

            Intent result = new Intent();
            result.putExtra(RESULT_FACE_MATCHED, true);
            setResult(RESULT_OK, result);
            finish();
        } else {
            startTimeoutCount();
            runOnUiThread(() -> tvStatus.setText("❌ Không khớp. Đang quét lại..."));
            surfaceView.postDelayed(() -> isProcessingFrame = false, 500);
        }
    }

    // ==================== LUỒNG CHỤP THỦ CÔNG (REGISTER) ====================

    private void capturePhotoManual() {
        isSafeToCapture = false;
        btnCapture.setEnabled(false);
        tvStatus.setText("Đang phân tích ảnh...");

        if (camera == null) {
            Log.e(TAG, "camera NULL khi capturePhotoManual");
            resetCaptureState();
            return;
        }

        camera.setPreviewCallback(null);

        new Handler(getMainLooper()).postDelayed(() -> {
            if (camera == null) {
                resetCaptureState();
                return;
            }
            try {
                camera.takePicture(
                        null,
                        null,
                        (data, cam) -> {
                            if (data == null || data.length == 0) {
                                runOnUiThread(() -> {
                                    tvStatus.setText("Lỗi lấy dữ liệu ảnh! Hãy thử lại.");
                                    resetCaptureState();
                                });
                                return;
                            }
                            new Thread(() -> decodeAndRotateBitmap(data)).start();
                        }
                );
            } catch (Exception e) {
                Log.e(TAG, "Lỗi camera.takePicture", e);
                runOnUiThread(() -> {
                    tvStatus.setText("Không thể kết nối phần cứng camera!");
                    resetCaptureState();
                });
            }
        }, 300);
    }

    private void decodeAndRotateBitmap(byte[] data) {
        try {
            Bitmap srcBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (srcBitmap == null) {
                runOnUiThread(() -> {
                    tvStatus.setText("Lỗi giải mã hình ảnh!");
                    resetCaptureState();
                });
                return;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(270);
            matrix.postScale(-1, 1);
            Bitmap rotatedBitmap = Bitmap.createBitmap(
                    srcBitmap, 0, 0,
                    srcBitmap.getWidth(), srcBitmap.getHeight(),
                    matrix, true);
            srcBitmap.recycle();

            runOnUiThread(() -> detectFaceOnMainThread(rotatedBitmap));

        } catch (Exception e) {
            Log.e(TAG, "Lỗi decodeAndRotateBitmap", e);
            runOnUiThread(() -> {
                tvStatus.setText("Lỗi xử lý ảnh.");
                resetCaptureState();
            });
        }
    }

    private void detectFaceOnMainThread(Bitmap rotatedBitmap) {
        InputImage image = InputImage.fromBitmap(rotatedBitmap, 0);

        faceDetector.process(image)
                .addOnSuccessListener(faces -> {
                    if (faces == null || faces.isEmpty()) {
                        rotatedBitmap.recycle();
                        tvStatus.setText("❌ Không tìm thấy khuôn mặt! Hãy thử lại.");
                        resetCaptureState();
                        return;
                    }

                    Face detectedFace = faces.get(0);
                    Rect bounds = detectedFace.getBoundingBox();
                    int x = Math.max(0, bounds.left);
                    int y = Math.max(0, bounds.top);
                    int w = Math.min(bounds.width(),  rotatedBitmap.getWidth()  - x);
                    int h = Math.min(bounds.height(), rotatedBitmap.getHeight() - y);

                    if (w <= 0 || h <= 0) {
                        rotatedBitmap.recycle();
                        tvStatus.setText("❌ Vùng khuôn mặt lỗi! Hãy thử lại.");
                        resetCaptureState();
                        return;
                    }

                    Bitmap croppedFace = Bitmap.createBitmap(rotatedBitmap, x, y, w, h);
                    rotatedBitmap.recycle();

                    backgroundExecutor.execute(() -> processEmbeddingAndSave(croppedFace));
                })
                .addOnFailureListener(e -> {
                    rotatedBitmap.recycle();
                    Log.e(TAG, "MLKit faceDetector thất bại", e);
                    tvStatus.setText("Lỗi phân tích: " + e.getMessage());
                    resetCaptureState();
                });
    }

    private void processEmbeddingAndSave(Bitmap croppedFace) {
        try {
            // ── Bước 1: Nếu đã có avatar cũ → kiểm tra khớp chủ ──
            if (entityId != null && !entityId.isEmpty() && !entityId.equals("temp_capture")) {
                File existingFile = getAvatarFile(entityId);
                if (existingFile != null && existingFile.exists()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = 2;
                    Bitmap oldBitmap = BitmapFactory.decodeFile(existingFile.getAbsolutePath(), opts);

                    if (oldBitmap != null) {
                        float[] oldEmbedding     = faceMatchHelper.getFaceEmbedding(oldBitmap);
                        float[] currentEmbedding = faceMatchHelper.getFaceEmbedding(croppedFace);
                        oldBitmap.recycle();

                        if (oldEmbedding != null && currentEmbedding != null) {
                            boolean isOwner = faceMatchHelper.compareFaces(oldEmbedding, currentEmbedding);
                            if (!isOwner) {
                                croppedFace.recycle();
                                runOnUiThread(() -> {
                                    tvStatus.setText("❌ Khuôn mặt không khớp với ảnh hiện tại!");
                                    resetCaptureState();
                                });
                                return;
                            }
                        }

                        // Khớp chủ → lưu dạng ảnh tạm và trả về
                        runOnUiThread(() -> tvStatus.setText("✅ Xác thực thành công!"));
                        savePhotoAndReturn(croppedFace);
                        return;
                    }
                }
            }

            // ── Bước 2: Không có avatar cũ → kiểm tra trùng khuôn mặt ──
            String duplicateId = checkDuplicateFace(croppedFace);
            if (duplicateId != null) {
                croppedFace.recycle();
                runOnUiThread(() -> {
                    tvStatus.setText("❌ Khuôn mặt đã tồn tại: " + duplicateId);
                    resetCaptureState();
                });
            } else {
                runOnUiThread(() -> tvStatus.setText("✅ Xác thực thành công!"));
                savePhotoAndReturn(croppedFace);
            }

        } catch (Exception e) {
            Log.e(TAG, "Lỗi processEmbeddingAndSave", e);
            croppedFace.recycle();
            runOnUiThread(() -> {
                tvStatus.setText("Lỗi xử lý embedding.");
                resetCaptureState();
            });
        }
    }

    // ==================== CÁC HÀM BỔ TRỢ ====================

    private File getTargetDir() {
        File dir = new File(getExternalFilesDir(null), targetFolder);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Thống nhất chỉ dùng duy nhất prefix "avatar_"
     */
    private File getAvatarFile(String id) {
        File dir = getTargetDir();
        File avatar = new File(dir, "avatar_" + id + ".jpg");
        if (avatar.exists()) return avatar;
        // Kiểm tra dự phòng nếu còn file face_ cũ từ phiên bản trước
        File oldFace = new File(dir, "face_" + id + ".jpg");
        if (oldFace.exists()) return oldFace;
        return null;
    }

    private String checkDuplicateFace(Bitmap newFaceCrop) {
        File dir = getTargetDir();
        File[] files = dir.listFiles();
        if (files == null) return null;

        float[] newEmbedding = faceMatchHelper.getFaceEmbedding(newFaceCrop);
        if (newEmbedding == null) return null;

        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 2;

        for (File file : files) {
            String name = file.getName();

            // Bỏ qua file tạm và file chính thức thuộc sinh viên này
            if (name.startsWith("temp_")) continue;
            if (entityId != null && !entityId.isEmpty()) {
                if (name.contains("avatar_" + entityId) || name.contains("face_" + entityId)) continue;
            }

            if (file.isFile() && name.endsWith(".jpg")) {
                try {
                    Bitmap existing = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                    if (existing != null) {
                        float[] existingEmbedding = faceMatchHelper.getFaceEmbedding(existing);
                        existing.recycle();
                        if (existingEmbedding != null
                                && faceMatchHelper.compareFaces(existingEmbedding, newEmbedding)) {
                            return name.replace("avatar_", "")
                                    .replace("face_", "")
                                    .replace(".jpg", "");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Lỗi quét file: " + name, e);
                }
            }
        }
        return null;
    }

    private void resetCaptureState() {
        runOnUiThread(() -> {
            try {
                if (camera != null) {
                    camera.startPreview();
                    camera.setPreviewCallback(this);
                }
            } catch (Exception e) {
                Log.e(TAG, "Lỗi restart preview", e);
            }
            isSafeToCapture   = true;
            isProcessingFrame = false;
            btnCapture.setEnabled(true);
        });
    }

    /**
     * Lưu ảnh vào file TẠM THỜI (không xóa file avatar chính thức cũ).
     * Chỉ thực hiện đổi tên và xóa file cũ bên màn hình AddStudentActivity khi nhấn Lưu.
     */
    private void savePhotoAndReturn(Bitmap bitmap) {
        File dir = getTargetDir();

        // Lưu ảnh tạm thời kèm timestamp
        String tempFileName = "temp_avatar_" + (entityId != null ? entityId : "new") + "_" + System.currentTimeMillis() + ".jpg";
        File tempPhotoFile = new File(dir, tempFileName);

        try (FileOutputStream fos = new FileOutputStream(tempPhotoFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
        } catch (IOException e) {
            Log.e(TAG, "Lỗi ghi file ảnh tạm", e);
            runOnUiThread(() -> {
                tvStatus.setText("Lỗi lưu ảnh. Hãy thử lại.");
                resetCaptureState();
            });
            return;
        } finally {
            bitmap.recycle();
        }

        Intent result = new Intent();
        result.putExtra(RESULT_PHOTO_PATH,   tempPhotoFile.getAbsolutePath());
        result.putExtra(RESULT_FACE_MATCHED, true);
        setResult(RESULT_OK, result);
        finish();
    }

    // ==================== SURFACE CALLBACKS ====================

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.setPreviewDisplay(holder);
                camera.setPreviewCallback(this);
                camera.startPreview();
                isSafeToCapture = true;
            } catch (Exception e) {
                Log.e(TAG, "surfaceChanged error", e);
            }
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        releaseCamera();
    }

    private void releaseCamera() {
        if (camera != null) {
            isSafeToCapture = false;
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }
}
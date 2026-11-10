package com.iritech.irissample;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
    private View        vFlashOverlay;

    // ── Camera & AI ───────────────────────────────────────────────────────────
    private Camera          camera;
    private FaceDetector    faceDetector;
    private FaceMatchHelper faceMatchHelper;
    private ExecutorService backgroundExecutor;
    private LivenessLogic   livenessLogic;
    private AntiSpoofHelper antiSpoofHelper;

    // ── Trạng thái Red/Blue Flash Challenge Continuous ────────────────────────
    // colorFlashPhase: 0=Wait/Off, 1=RED (#FF0000), 2=BLUE (#0000FF), 3=BASELINE (Trước khi nháy)
    private volatile boolean isContinuousFlashing = false;
    private volatile int colorFlashPhase = 0;
    private float[] rgbBaseline  = new float[]{0f, 0f, 0f};
    private float[] rgbRedFlash  = new float[]{0f, 0f, 0f};
    private float[] rgbBlueFlash = new float[]{0f, 0f, 0f};
    private int baseSampleCount  = 0;
    private int redSampleCount   = 0;
    private int blueSampleCount  = 0;

    // Lưu vị trí khuôn mặt ngay trước và trong khi nháy Flash để đo đúng vùng da mặt
    private volatile Rect lastFaceBounds = null;
    private volatile float lastFaceEulerY = 0f;
    private volatile float lastFaceEulerX = 0f;

    private Handler flashHandler = new Handler(Looper.getMainLooper());
    private Runnable flashLoopRunnable;

    // ── Trạng thái ────────────────────────────────────────────────────────────
    private volatile boolean isSafeToCapture   = false;
    private volatile boolean isProcessingFrame = false;

    private int verifyFailCount = 0;
    private static final int MAX_VERIFY_RETRIES_PER_LIVENESS = 3;

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

        surfaceView   = findViewById(R.id.surfaceView);
        tvStatus      = findViewById(R.id.tvStatus);
        btnCapture    = findViewById(R.id.btnCapture);
        btnCancel     = findViewById(R.id.btnCancel);
        vFlashOverlay = findViewById(R.id.vFlashOverlay);

        faceMatchHelper    = new FaceMatchHelper(this);
        antiSpoofHelper    = new AntiSpoofHelper(this);
        backgroundExecutor = Executors.newSingleThreadExecutor();

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setMinFaceSize(0.25f)
                .build();
        faceDetector = FaceDetection.getClient(options);

        livenessLogic = new LivenessLogic(new LivenessLogic.OnLivenessStateChangeListener() {
            @Override
            public void onStateChanged(LivenessLogic.State newState, String instructionMessage) {
                runOnUiThread(() -> tvStatus.setText(instructionMessage));
            }

            @Override
            public void onRequestFlashChallenge() {
                startSingleColorFlashChallenge();
            }

            @Override
            public void onLivenessCompleted() {
                verifyFailCount = 0;
                runOnUiThread(() -> tvStatus.setText("✅ Liveness hợp lệ! Đang so khớp khuôn mặt..."));
            }

            @Override
            public void onLivenessFailed(String reason) {
                stopTimeout();
                stopContinuousColorFlash();
                isSafeToCapture = false;
                runOnUiThread(() -> {
                    tvStatus.setText("❌ " + reason);
                    Toast.makeText(FaceCaptureActivity.this, reason, Toast.LENGTH_LONG).show();
                });
                // Tự động trở về màn hình điểm danh sau 1.5 giây
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing()) {
                        Intent result = new Intent();
                        result.putExtra(RESULT_FACE_MATCHED, false);
                        setResult(RESULT_OK, result);
                        finish();
                    }
                }, 1500);
            }
        });

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
            livenessLogic.start();
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
            stopContinuousColorFlash();
            setResult(RESULT_CANCELED);
            finish();
        });

        checkCameraPermission();
    }

    @Override
    protected void onDestroy() {
        stopTimeout();
        stopContinuousColorFlash();
        releaseCamera();
        if (livenessLogic   != null) livenessLogic.reset();
        if (antiSpoofHelper != null) antiSpoofHelper.close();
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

    // ==================== AE/AWB LOCK & RED/BLUE SPECTRUM FLASH ====================

    private void lockCameraAutoExposureAndWhiteBalance(boolean lock) {
        if (camera == null) return;
        try {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters.isAutoExposureLockSupported()) {
                parameters.setAutoExposureLock(lock);
            }
            if (parameters.isAutoWhiteBalanceLockSupported()) {
                parameters.setAutoWhiteBalanceLock(lock);
            }
            camera.setParameters(parameters);
            Log.d(TAG, "Camera hardware AE/AWB Lock state set to: " + lock);
        } catch (Exception e) {
            Log.e(TAG, "Lỗi thiết lập AE/AWB Lock: " + e.getMessage());
        }
    }

    private void startSingleColorFlashChallenge() {
        runOnUiThread(() -> {
            if (vFlashOverlay == null) return;

            isContinuousFlashing = true;
            colorFlashPhase = 3; // Pha 3: Thu mẫu Baseline (Màu da gốc trước khi nháy)
            rgbBaseline  = new float[]{0f, 0f, 0f};
            rgbRedFlash  = new float[]{0f, 0f, 0f};
            rgbBlueFlash = new float[]{0f, 0f, 0f};
            baseSampleCount = 0;
            redSampleCount  = 0;
            blueSampleCount = 0;

            // Khóa cứng Auto-Exposure (AE) và Auto-White-Balance (AWB) phần cứng camera
            lockCameraAutoExposureAndWhiteBalance(true);

            Handler mainHandler = new Handler(Looper.getMainLooper());

            // Sau 150ms thu mẫu Baseline -> Bật nền ĐỎ TƯƠI (#FF0000)
            mainHandler.postDelayed(() -> {
                if (!isContinuousFlashing) return;
                colorFlashPhase = 0;
                if (vFlashOverlay != null) {
                    vFlashOverlay.setBackgroundColor(Color.parseColor("#FF0000"));
                    vFlashOverlay.setVisibility(View.VISIBLE);
                }

                // Chờ 150ms cho màn hình Đỏ và camera đồng bộ rồi thu mẫu trong 350ms
                mainHandler.postDelayed(() -> {
                    if (isContinuousFlashing) colorFlashPhase = 1;
                }, 150);

                // Kết thúc pha Đỏ, chuyển sang XANH DƯƠNG (#0000FF)
                mainHandler.postDelayed(() -> {
                    if (!isContinuousFlashing) return;
                    colorFlashPhase = 0;
                    if (vFlashOverlay != null) {
                        vFlashOverlay.setBackgroundColor(Color.parseColor("#0000FF"));
                    }

                    // Chờ 150ms cho màn hình Xanh và camera đồng bộ rồi thu mẫu trong 350ms
                    mainHandler.postDelayed(() -> {
                        if (isContinuousFlashing) colorFlashPhase = 2;
                    }, 150);

                    mainHandler.postDelayed(() -> {
                        if (vFlashOverlay != null) {
                            vFlashOverlay.setVisibility(View.GONE);
                        }
                        colorFlashPhase = 0;
                        lockCameraAutoExposureAndWhiteBalance(false);

                        float shift = calculateRedBlueShiftValue();
                        boolean passed = evaluateRedBlueRatioCycle();

                        // Chờ 220ms sau khi tắt Flash để camera cân bằng sáng lại trước khi cắt ảnh MobileFaceNet
                        mainHandler.postDelayed(() -> {
                            isContinuousFlashing = false;
                            if (livenessLogic != null) {
                                livenessLogic.onFlashChallengeResult(passed, shift);
                            }
                        }, 220);
                    }, 500);
                }, 500);
            }, 150);
        });
    }

    private void stopContinuousColorFlash() {
        isContinuousFlashing = false;
        colorFlashPhase = 0;
        lockCameraAutoExposureAndWhiteBalance(false);

        if (flashHandler != null && flashLoopRunnable != null) {
            flashHandler.removeCallbacks(flashLoopRunnable);
        }
        runOnUiThread(() -> {
            if (vFlashOverlay != null) {
                vFlashOverlay.setVisibility(View.GONE);
            }
        });
    }

    private float calculateRedBlueShiftValue() {
        float ratioRedInRedFlash  = rgbRedFlash[0]  / (rgbRedFlash[2]  + 1.0f);
        float ratioRedInBlueFlash = rgbBlueFlash[0] / (rgbBlueFlash[2] + 1.0f);
        return ratioRedInRedFlash - ratioRedInBlueFlash;
    }

    private boolean evaluateRedBlueRatioCycle() {
        // 1. Tuyệt đối KHÔNG cho qua nếu không thu thập được mẫu hoặc mất khuôn mặt trong lúc nháy Flash
        if (redSampleCount == 0 || blueSampleCount == 0 || lastFaceBounds == null) {
            Log.w(TAG, "Flash thất bại: Không thu thập đủ mẫu hoặc mất dấu khuôn mặt");
            return false;
        }

        // 2. Nếu trong lúc nháy Flash mà khuôn mặt trong video vẫn quay nghiêng quá 15 độ -> Loại
        if (Math.abs(lastFaceEulerY) > 15.0f || Math.abs(lastFaceEulerX) > 15.0f) {
            Log.w(TAG, "Flash thất bại: Khuôn mặt không giữ thẳng trong lúc kiểm tra ánh sáng");
            return false;
        }

        float rRed  = rgbRedFlash[0],  gRed  = rgbRedFlash[1],  bRed  = rgbRedFlash[2];
        float rBlue = rgbBlueFlash[0], gBlue = rgbBlueFlash[1], bBlue = rgbBlueFlash[2];

        // 3. Chống màn hình điện thoại tự phát sáng quá gắt (Backlight/Specular Glare Saturation)
        float lumaRed  = 0.299f * rRed  + 0.587f * gRed  + 0.114f * bRed;
        float lumaBlue = 0.299f * rBlue + 0.587f * gBlue + 0.114f * bBlue;
        if (lumaRed > 232.0f && lumaBlue > 232.0f) {
            Log.w(TAG, "Flash thất bại: Phát hiện độ chói đèn nền màn hình quá cao (Luma=" + lumaRed + ")");
            return false;
        }

        float ratioRedInRedFlash  = rRed  / (bRed  + 1.0f);
        float ratioRedInBlueFlash = rBlue / (bBlue + 1.0f);
        float redBlueShift        = ratioRedInRedFlash - ratioRedInBlueFlash;

        // 4. Tính sắc độ chuẩn hóa (Chromaticity) để loại bỏ nhiễu do thay đổi độ sáng đơn thuần của video
        float sumRed  = rRed  + gRed  + bRed  + 1e-3f;
        float sumBlue = rBlue + gBlue + bBlue + 1e-3f;
        float normRedShift  = (rRed / sumRed)   - (rBlue / sumBlue); // Phải dương khi nháy Đỏ
        float normBlueShift = (bBlue / sumBlue) - (bRed / sumRed);   // Phải dương khi nháy Xanh

        // 5. Kiểm tra đối xứng qua Baseline (nếu có mẫu Baseline trước khi nháy)
        boolean baselineSymmetric = true;
        if (baseSampleCount > 0) {
            float ratioBase = rgbBaseline[0] / (rgbBaseline[2] + 1.0f);
            // Khi nháy Đỏ tỷ lệ R/B phải tăng lên so với Baseline, khi nháy Xanh phải giảm xuống so với Baseline
            baselineSymmetric = (ratioRedInRedFlash > ratioBase + 0.015f)
                    && (ratioRedInBlueFlash < ratioBase - 0.015f);
        }

        // 6. Ngưỡng chặn Video:
        // Vì đã lấy mẫu thuần túy bên trong vùng lõi da mặt (không bị tường phía sau làm loãng),
        // người thật ở xa (40-50cm) vẫn đạt Shift >= 0.095f, trong khi màn hình phát video < 0.08f.
        boolean shiftPassed = (redBlueShift >= 0.095f)
                && (normRedShift >= 0.014f)
                && (normBlueShift >= 0.014f)
                && baselineSymmetric;

        Log.d(TAG, String.format(
                "Flash Check -> R/B_Red: %.3f, R/B_Blue: %.3f, Shift: %.3f, normR: %.3f, normB: %.3f, baseSym: %b => PASS: %b",
                ratioRedInRedFlash, ratioRedInBlueFlash, redBlueShift, normRedShift, normBlueShift, baselineSymmetric, shiftPassed));

        return shiftPassed;
    }

    /**
     * Chỉ lấy mẫu RGB vùng lõi da mặt (co vào 22% từ BoundingBox của ML Kit)
     * để loại bỏ hoàn toàn bức tường phía sau (khi đứng xa) và ngón tay/viền máy (khi dùng video).
     */
    private float[] calculateFaceCoreRGB(byte[] nv21Data, int width, int height, Rect faceBoundsRot270) {
        if (nv21Data == null || width <= 0 || height <= 0) return new float[]{0f, 0f, 0f};

        int startX = width * 3 / 8;
        int endX   = width * 5 / 8;
        int startY = height * 3 / 8;
        int endY   = height * 5 / 8;

        // Ánh xạ từ hệ tọa độ ảnh xoay 270 độ (rotW = height, rotH = width) về mảng NV21 gốc (width x height)
        if (faceBoundsRot270 != null && faceBoundsRot270.width() > 20 && faceBoundsRot270.height() > 20) {
            int padX = (int) (faceBoundsRot270.width()  * 0.22f);
            int padY = (int) (faceBoundsRot270.height() * 0.22f);

            int coreLeft   = faceBoundsRot270.left   + padX;
            int coreRight  = faceBoundsRot270.right  - padX;
            int coreTop    = faceBoundsRot270.top    + padY;
            int coreBottom = faceBoundsRot270.bottom - padY;

            int mappedStartY = Math.max(0, Math.min(height - 1, coreLeft));
            int mappedEndY   = Math.max(0, Math.min(height,     coreRight));
            int mappedStartX = Math.max(0, Math.min(width - 1,  width - coreBottom));
            int mappedEndX   = Math.max(0, Math.min(width,      width - coreTop));

            if (mappedEndX - mappedStartX >= 12 && mappedEndY - mappedStartY >= 12) {
                startX = mappedStartX;
                endX   = mappedEndX;
                startY = mappedStartY;
                endY   = mappedEndY;
            }
        }

        int frameSize = width * height;
        long sumR = 0, sumG = 0, sumB = 0;
        int count = 0;

        for (int y = startY; y < endY; y += 4) {
            for (int x = startX; x < endX; x += 4) {
                int yIndex  = y * width + x;
                int uvIndex = frameSize + (y >> 1) * width + (x & ~1);

                if (uvIndex + 1 < nv21Data.length) {
                    int yVal = nv21Data[yIndex] & 0xFF;
                    int vVal = (nv21Data[uvIndex] & 0xFF) - 128;
                    int uVal = (nv21Data[uvIndex + 1] & 0xFF) - 128;

                    int r = (int) (yVal + 1.402f * vVal);
                    int g = (int) (yVal - 0.34414f * uVal - 0.71414f * vVal);
                    int b = (int) (yVal + 1.772f * uVal);

                    sumR += Math.max(0, Math.min(255, r));
                    sumG += Math.max(0, Math.min(255, g));
                    sumB += Math.max(0, Math.min(255, b));
                    count++;
                }
            }
        }

        if (count == 0) return new float[]{0f, 0f, 0f};
        return new float[]{sumR / (float) count, sumG / (float) count, sumB / (float) count};
    }

    // ==================== LUỒNG PREVIEW (TỰ ĐỘNG CHECKIN) ====================

    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        if (!isAutoCheckinMode || !isSafeToCapture) return;

        // Lấy mẫu RGB vùng lõi da mặt theo 3 pha: Baseline (3), ĐỎ (1), XANH DƯƠNG (2)
        if (isContinuousFlashing) {
            try {
                Camera.Size size = camera.getParameters().getPreviewSize();
                int phase = colorFlashPhase;
                if (phase == 1 || phase == 2 || phase == 3) {
                    float[] sample = calculateFaceCoreRGB(data, size.width, size.height, lastFaceBounds);
                    if (phase == 3) {
                        for (int i = 0; i < 3; i++) {
                            rgbBaseline[i] = (rgbBaseline[i] * baseSampleCount + sample[i]) / (baseSampleCount + 1);
                        }
                        baseSampleCount++;
                    } else if (phase == 1) {
                        for (int i = 0; i < 3; i++) {
                            rgbRedFlash[i] = (rgbRedFlash[i] * redSampleCount + sample[i]) / (redSampleCount + 1);
                        }
                        redSampleCount++;
                    } else if (phase == 2) {
                        for (int i = 0; i < 3; i++) {
                            rgbBlueFlash[i] = (rgbBlueFlash[i] * blueSampleCount + sample[i]) / (blueSampleCount + 1);
                        }
                        blueSampleCount++;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Flash sample error", e);
            }
            return;
        }

        if (isProcessingFrame) return;
        isProcessingFrame = true;

        try {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            InputImage image = InputImage.fromByteArray(
                    data, size.width, size.height, 270, InputImage.IMAGE_FORMAT_NV21);

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces == null || faces.isEmpty()) {
                            lastFaceBounds = null;
                            if (livenessLogic != null) {
                                if (livenessLogic.isCompleted()) {
                                    verifyFailCount = 0;
                                    livenessLogic.start();
                                } else {
                                    livenessLogic.processFace(null);
                                }
                            }
                            isProcessingFrame = false;
                            return;
                        }

                        Face detectedFace = faces.get(0);
                        lastFaceBounds = detectedFace.getBoundingBox();
                        lastFaceEulerY = detectedFace.getHeadEulerAngleY();
                        lastFaceEulerX = detectedFace.getHeadEulerAngleX();

                        // Kiểm tra Liveness (chớp mắt + lắc đầu 3D + flash)
                        if (livenessLogic != null) {
                            livenessLogic.processFace(detectedFace);

                            if (!livenessLogic.isCompleted()) {
                                isProcessingFrame = false;
                                return;
                            }
                        }

                        // Đảm bảo khuôn mặt đang nhìn thẳng trước khi cắt ảnh đưa vào MobileFaceNet
                        if (Math.abs(detectedFace.getHeadEulerAngleY()) > 12.0f
                                || Math.abs(detectedFace.getHeadEulerAngleX()) > 12.0f) {
                            runOnUiThread(() -> tvStatus.setText("Vui lòng giữ thẳng mặt nhìn vào camera..."));
                            isProcessingFrame = false;
                            return;
                        }

                        backgroundExecutor.execute(() -> {
                            try {
                                YuvImage yuvImage = new YuvImage(
                                        data, ImageFormat.NV21, size.width, size.height, null);
                                ByteArrayOutputStream os = new ByteArrayOutputStream();
                                yuvImage.compressToJpeg(
                                        new Rect(0, 0, size.width, size.height), 85, os);
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

                                // Đối xứng lại trục X vì rotatedBitmap đã postScale(-1, 1)
                                int mirroredLeft = rotatedBitmap.getWidth() - bounds.right;

                                // 1. Cắt sát mặt (Tight Crop) đúng tọa độ đã lật gương cho MobileFaceNet
                                int tx = Math.max(0, mirroredLeft);
                                int ty = Math.max(0, bounds.top);
                                int tw = Math.min(bounds.width(),  rotatedBitmap.getWidth()  - tx);
                                int th = Math.min(bounds.height(), rotatedBitmap.getHeight() - ty);

                                // 2. Cắt rộng (Expanded Crop 2.5x) đúng tâm đã lật gương cho MiniVision AI Anti-Spoofing
                                int centerX = rotatedBitmap.getWidth() - bounds.centerX();
                                int centerY = bounds.centerY();
                                int maxDim  = (int) (Math.max(bounds.width(), bounds.height()) * 2.5f / 2.0f);

                                int ex = Math.max(0, centerX - maxDim);
                                int ey = Math.max(0, centerY - maxDim);
                                int ew = Math.min(rotatedBitmap.getWidth()  - ex, maxDim * 2);
                                int eh = Math.min(rotatedBitmap.getHeight() - ey, maxDim * 2);

                                if (tw > 0 && th > 0 && ew > 0 && eh > 0) {
                                    Bitmap tightFaceCrop    = Bitmap.createBitmap(rotatedBitmap, tx, ty, tw, th);
                                    Bitmap expandedFaceCrop = Bitmap.createBitmap(rotatedBitmap, ex, ey, ew, eh);
                                    rotatedBitmap.recycle();
                                    verifyFaceAuto(tightFaceCrop, expandedFaceCrop);
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

    private void verifyFaceAuto(Bitmap tightFaceCrop, Bitmap expandedFaceCrop) {
        // ── Bước 1: Kiểm tra Passive Liveness AI (MiniVision Anti-Spoofing) trên ảnh cắt rộng ──
        if (antiSpoofHelper != null && antiSpoofHelper.isModelLoaded()) {
            AntiSpoofHelper.AntiSpoofResult spoofResult = antiSpoofHelper.classifyFace(expandedFaceCrop);
            expandedFaceCrop.recycle();

            if (!spoofResult.isReal()) {
                Log.w(TAG, "Phát hiện thiết bị giả mạo / quay lại màn hình! RealScore: " + spoofResult.getRealScore() + ", SpoofScore: " + spoofResult.getSpoofScore());
                tightFaceCrop.recycle();
                if (livenessLogic != null) {
                    livenessLogic.fail(String.format("Phát hiện video/ảnh giả mạo! (Score: %.2f)", spoofResult.getSpoofScore()));
                }
                isProcessingFrame = false;
                return;
            }
        } else {
            expandedFaceCrop.recycle();
        }

        // ── Bước 2: So khớp khuôn mặt (MobileFaceNet) trên ảnh cắt sát mặt ──
        if (existingPhotoPath == null || !new File(existingPhotoPath).exists()) {
            tightFaceCrop.recycle();
            isProcessingFrame = false;
            return;
        }

        // Giải mã ảnh đại diện ở độ phân giải gốc (KHÔNG dùng inSampleSize = 2)
        Bitmap existingBitmap = BitmapFactory.decodeFile(existingPhotoPath);

        if (existingBitmap == null) {
            tightFaceCrop.recycle();
            isProcessingFrame = false;
            return;
        }

        float[] existingEmbedding = faceMatchHelper.getFaceEmbedding(existingBitmap);
        float[] currentEmbedding  = faceMatchHelper.getFaceEmbedding(tightFaceCrop);

        existingBitmap.recycle();
        tightFaceCrop.recycle();

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
            verifyFailCount++;
            startTimeoutCount();

            if (verifyFailCount >= MAX_VERIFY_RETRIES_PER_LIVENESS) {
                verifyFailCount = 0;
                runOnUiThread(() -> tvStatus.setText("❌ Khuôn mặt không khớp! Vui lòng xác thực lại từ Bước 1/3..."));
                surfaceView.postDelayed(() -> {
                    if (livenessLogic != null && isSafeToCapture) {
                        livenessLogic.start();
                    }
                    isProcessingFrame = false;
                }, 1000);
            } else {
                runOnUiThread(() -> tvStatus.setText("Đang lấy nét lại khuôn mặt chính diện..."));
                surfaceView.postDelayed(() -> isProcessingFrame = false, 300);
            }
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
            // ── Bước 1: Luôn kiểm tra trùng khuôn mặt với các sinh viên khác trước ──
            String duplicateId = checkDuplicateFace(croppedFace);
            if (duplicateId != null) {
                croppedFace.recycle();
                runOnUiThread(() -> {
                    tvStatus.setText("❌ Khuôn mặt đã tồn tại: " + duplicateId);
                    resetCaptureState();
                });
                return;
            }

            // ── Bước 2: Nếu đã có avatar cũ → kiểm tra khớp chủ ──
            if (entityId != null && !entityId.isEmpty() && !entityId.equals("temp_capture")) {
                File existingFile = getAvatarFile(entityId);
                if (existingFile != null && existingFile.exists()) {
                    Bitmap oldBitmap = BitmapFactory.decodeFile(existingFile.getAbsolutePath());

                    if (oldBitmap != null) {
                        float[] oldEmbedding     = faceMatchHelper.getFaceEmbedding(oldBitmap);
                        float[] currentEmbedding = faceMatchHelper.getFaceEmbedding(croppedFace);
                        oldBitmap.recycle();

                        if (oldEmbedding == null || currentEmbedding == null) {
                            croppedFace.recycle();
                            runOnUiThread(() -> {
                                tvStatus.setText("❌ Không trích xuất được đặc trưng khuôn mặt! Hãy thử lại.");
                                resetCaptureState();
                            });
                            return;
                        }

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
                }
            }

            // Hợp lệ hoàn toàn → lưu dạng ảnh tạm và trả về
            runOnUiThread(() -> tvStatus.setText("✅ Xác thực thành công!"));
            savePhotoAndReturn(croppedFace);

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

    private File getAvatarFile(String id) {
        File dir = getTargetDir();
        File avatar = new File(dir, "avatar_" + id + ".jpg");
        if (avatar.exists()) return avatar;
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

        for (File file : files) {
            String name = file.getName();

            if (name.startsWith("temp_")) continue;

            if (entityId != null && !entityId.isEmpty()) {
                if (name.equals("avatar_" + entityId + ".jpg") || name.equals("face_" + entityId + ".jpg")) {
                    continue;
                }
            }

            if (file.isFile() && name.endsWith(".jpg")) {
                try {
                    Bitmap existing = BitmapFactory.decodeFile(file.getAbsolutePath());
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

    private void savePhotoAndReturn(Bitmap bitmap) {
        File dir = getTargetDir();

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
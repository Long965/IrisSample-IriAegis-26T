package com.iritech.irissample;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Lớp quản lý logic xác thực Liveness Detection nâng cao (Multilayer Anti-Spoofing):
 *  1. WAITING_FOR_FACE: Chờ mở camera và phát hiện khuôn mặt hợp lệ.
 *  2. BLINK_REQUESTED: Yêu cầu sinh viên chớp mắt (nhắm mắt rồi mở mắt).
 *  3. HEAD_SHAKE_REQUESTED: Yêu cầu lắc đầu + Phân tích phối cảnh không gian 3D (3D Perspective Foreshortening).
 *  4. FLASH_CHALLENGE: Thử thách phản xạ ánh sáng màn hình (Screen Flash Reflection Challenge).
 *  5. COMPLETED: Xác thực thành công.
 *  6. FAILED: Thất bại do timeout hoặc phát hiện video giả mạo.
 */
public class LivenessLogic {

    public enum State {
        IDLE,                   // Chưa bắt đầu
        WAITING_FOR_FACE,       // Bước 1: Mở camera & chờ phát hiện khuôn mặt
        BLINK_REQUESTED,        // Bước 2: Nhắc chớp mắt
        HEAD_SHAKE_REQUESTED,   // Bước 3: Nhắc lắc đầu + Kiểm tra không gian 3D
        FLASH_CHALLENGE,        // Bước 4: Thử thách phản xạ ánh sáng màn hình
        COMPLETED,              // Hoàn thành tất cả lớp Liveness
        FAILED                  // Thất bại
    }

    // Các ngưỡng cấu hình cho Liveness (đã tinh chỉnh chặt hơn để tránh tự qua bước chớp mắt)
    public static final float EYE_CLOSED_THRESHOLD   = 0.15f;  // Ngưỡng mắt nhắm (<= 0.15)
    public static final float EYE_OPEN_THRESHOLD     = 0.75f;  // Ngưỡng mắt mở (>= 0.75)
    public static final float HEAD_SHAKE_LEFT_ANGLE  = -15.0f; // Góc quay trái (<= -15 độ)
    public static final float HEAD_SHAKE_RIGHT_ANGLE = 15.0f;  // Góc quay phải (>= 15 độ)

    // Giảm ngưỡng biến thiên phối cảnh 3D xuống 0.025f để hỗ trợ trường hợp để mặt cách xa điện thoại
    private static final float MIN_3D_PERSPECTIVE_VARIANCE = 0.025f;

    // Ngưỡng chênh lệch độ sáng (Luma Shift) khi chớp màu màn hình
    public static final float MIN_FLASH_LUMA_SHIFT = 6.0f;

    private static final long DEFAULT_TIMEOUT_MS = 25000; // Timeout 25 giây

    public interface OnLivenessStateChangeListener {
        void onStateChanged(State newState, String instructionMessage);
        void onRequestFlashChallenge();
        void onLivenessCompleted();
        void onLivenessFailed(String reason);
    }

    private State currentState = State.IDLE;
    private OnLivenessStateChangeListener listener;

    // Biến lưu trạng thái chớp mắt (yêu cầu mắt mở ổn định trước khi nhắm)
    private int initialOpenFrames = 0;
    private static final int REQUIRED_OPEN_FRAMES_BEFORE_BLINK = 3;
    private boolean hasEyeClosed = false;

    // Biến lưu trạng thái lắc đầu
    private boolean hasTurnedLeft  = false;
    private boolean hasTurnedRight = false;

    // Biến theo dõi biến thiên không gian 3D (Perspective Foreshortening)
    private float minEyeRatio = 1.0f;
    private float maxEyeRatio = 0.0f;
    private boolean has3DPerspectiveShift = false;

    // Biến theo dõi phản xạ ánh sáng (Screen Flash)
    private boolean hasFlashPassed = false;

    // Cờ theo dõi trạng thái mất khuôn mặt để khôi phục thông báo khi đưa mặt vào lại
    private boolean isFaceLost = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    public LivenessLogic() {
        this(null);
    }

    public LivenessLogic(OnLivenessStateChangeListener listener) {
        this.listener = listener;
    }

    public void setListener(OnLivenessStateChangeListener listener) {
        this.listener = listener;
    }

    public void start() {
        reset();
        changeState(State.WAITING_FOR_FACE, "Vui lòng đưa khuôn mặt vào khung hình");
        startTimeoutTimer();
    }

    public void reset() {
        stopTimeoutTimer();
        currentState = State.IDLE;
        initialOpenFrames = 0;
        hasEyeClosed = false;
        hasTurnedLeft = false;
        hasTurnedRight = false;
        minEyeRatio = 1.0f;
        maxEyeRatio = 0.0f;
        has3DPerspectiveShift = false;
        hasFlashPassed = false;
        isFaceLost = false;
    }

    /**
     * Nhận đối tượng Face từ ML Kit để phân tích
     */
    public void processFace(Face face) {
        if (currentState == State.IDLE || currentState == State.COMPLETED || currentState == State.FAILED) {
            return;
        }

        if (face == null) {
            if (currentState != State.WAITING_FOR_FACE && !isFaceLost) {
                isFaceLost = true;
                notifyInstruction("Không tìm thấy khuôn mặt, vui lòng nhìn thẳng camera");
            }
            return;
        }

        // Khôi phục lại câu hướng dẫn của bước hiện tại ngay khi đưa mặt vào lại camera
        if (isFaceLost) {
            isFaceLost = false;
            notifyInstruction(getInstructionForCurrentState());
        }

        Float leftEye  = face.getLeftEyeOpenProbability();
        Float rightEye = face.getRightEyeOpenProbability();
        float headY    = face.getHeadEulerAngleY();

        // 1. Kiểm tra 3D Perspective Foreshortening từ Landmark Mắt & Bounding Box
        check3DPerspective(face);

        // 2. Chạy luồng State Machine
        processFaceValues(leftEye, rightEye, headY);
    }

    private String getInstructionForCurrentState() {
        switch (currentState) {
            case WAITING_FOR_FACE:
                return "Vui lòng đưa khuôn mặt vào khung hình";
            case BLINK_REQUESTED:
                return "Bước 1/3: Vui lòng chớp mắt";
            case HEAD_SHAKE_REQUESTED:
                return "Bước 2/3: Vui lòng quay sang trái & quay sang phải";
            case FLASH_CHALLENGE:
                return "Bước 3/3: Đang kiểm tra phản chiếu ánh sáng...";
            default:
                return "";
        }
    }

    /**
     * Tính toán sự biến đổi tỷ lệ phối cảnh 3D khi đầu chuyển động.
     * Người thật (vật thể 3D): Tỷ lệ giữa khoảng cách 2 mắt và chiều rộng khuôn mặt sẽ biến thiên khi xoay.
     * Video màn hình 2D: Tỷ lệ co giãn dạng tịnh tiến phẳng (Affine transform), không có độ nén phối cảnh 3D.
     */
    private void check3DPerspective(Face face) {
        if (currentState != State.HEAD_SHAKE_REQUESTED) return;

        FaceLandmark leftLandmark  = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE);
        Rect bounds = face.getBoundingBox();

        if (leftLandmark != null && rightLandmark != null && bounds != null && bounds.width() > 0) {
            float eyeDist = Math.abs(rightLandmark.getPosition().x - leftLandmark.getPosition().x);
            float ratio   = eyeDist / (float) bounds.width();

            if (ratio < minEyeRatio) minEyeRatio = ratio;
            if (ratio > maxEyeRatio) maxEyeRatio = ratio;

            float variance = maxEyeRatio - minEyeRatio;
            if (variance >= MIN_3D_PERSPECTIVE_VARIANCE) {
                has3DPerspectiveShift = true;
            }
        }
    }

    public void processFaceValues(Float leftEyeOpenProb, Float rightEyeOpenProb, float headEulerAngleY) {
        switch (currentState) {
            case WAITING_FOR_FACE:
                changeState(State.BLINK_REQUESTED, "Bước 1/3: Vui lòng chớp mắt");
                break;

            case BLINK_REQUESTED:
                checkBlink(leftEyeOpenProb, rightEyeOpenProb);
                break;

            case HEAD_SHAKE_REQUESTED:
                checkHeadShake(headEulerAngleY);
                break;

            default:
                break;
        }
    }

    private void checkBlink(Float leftEyeProb, Float rightEyeProb) {
        if (leftEyeProb == null || rightEyeProb == null) return;
        if (leftEyeProb < 0 || rightEyeProb < 0) return;

        boolean eyesCurrentlyClosed = (leftEyeProb <= EYE_CLOSED_THRESHOLD && rightEyeProb <= EYE_CLOSED_THRESHOLD);
        boolean eyesCurrentlyOpen   = (leftEyeProb >= EYE_OPEN_THRESHOLD && rightEyeProb >= EYE_OPEN_THRESHOLD);

        // Bắt buộc mắt phải mở to ổn định trước khi ghi nhận hành động nhắm mắt
        if (!hasEyeClosed) {
            if (eyesCurrentlyOpen) {
                initialOpenFrames++;
            }
            if (initialOpenFrames >= REQUIRED_OPEN_FRAMES_BEFORE_BLINK && eyesCurrentlyClosed) {
                hasEyeClosed = true;
            }
        } else if (eyesCurrentlyOpen) {
            changeState(State.HEAD_SHAKE_REQUESTED, "Bước 2/3: Vui lòng quay sang trái & quay sang phải");
        }
    }

    private void checkHeadShake(float headEulerAngleY) {
        if (headEulerAngleY <= HEAD_SHAKE_LEFT_ANGLE) {
            hasTurnedLeft = true;
        }

        if (headEulerAngleY >= HEAD_SHAKE_RIGHT_ANGLE) {
            hasTurnedRight = true;
        }

        // Yêu cầu quay cả 2 hướng + Có biến thiên không gian 3D hợp lệ
        if (hasTurnedLeft && hasTurnedRight) {
            if (!has3DPerspectiveShift) {
                // Nếu lắc đầu nhưng tỷ lệ 3D không co giãn -> Phát hiện chuyển động tịnh tiến của màn hình phẳng 2D!
                notifyInstruction("⚠️ Hãy lắc đầu tự nhiên (Phát hiện chuyển động 2D)");
                return;
            }

            // Đã qua lắc đầu 3D -> Chuyển sang Bước 4: Thử thách phản chiếu ánh sáng (Screen Flash)
            triggerFlashChallenge();
        }
    }

    private void triggerFlashChallenge() {
        changeState(State.FLASH_CHALLENGE, "Bước 3/3: Đang kiểm tra phản chiếu ánh sáng...");
        if (listener != null) {
            listener.onRequestFlashChallenge();
        }
    }

    /**
     * Xác nhận kết quả phân tích độ sáng/phổ màu phản chiếu màn hình
     */
    public void onFlashChallengeResult(boolean success, float lumaShift) {
        if (currentState != State.FLASH_CHALLENGE) return;

        if (success) {
            hasFlashPassed = true;
            stopTimeoutTimer();
            changeState(State.COMPLETED, "Xác thực thành công!");
            if (listener != null) {
                listener.onLivenessCompleted();
            }
        } else {
            fail("Phát hiện video giả mạo");
        }
    }

    private void changeState(State newState, String instructionMessage) {
        this.currentState = newState;
        if (listener != null) {
            listener.onStateChanged(newState, instructionMessage);
        }
    }

    private void notifyInstruction(String message) {
        if (listener != null) {
            listener.onStateChanged(currentState, message);
        }
    }

    public void fail(String reason) {
        stopTimeoutTimer();
        changeState(State.FAILED, "Xác thực thất bại: " + reason);
        if (listener != null) {
            listener.onLivenessFailed(reason);
        }
    }

    private void startTimeoutTimer() {
        stopTimeoutTimer();
        timeoutRunnable = () -> fail("Hết thời gian xác thực");
        mainHandler.postDelayed(timeoutRunnable, timeoutMs);
    }

    private void stopTimeoutTimer() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    public State getCurrentState() {
        return currentState;
    }

    public boolean isCompleted() {
        return currentState == State.COMPLETED;
    }
}
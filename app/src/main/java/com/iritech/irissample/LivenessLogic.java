package com.iritech.irissample;

import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceLandmark;

/**
 * Lớp quản lý logic xác thực Liveness Detection nâng cao (Multilayer Anti-Spoofing):
 *  1. WAITING_FOR_FACE: Chờ mở camera và phát hiện khuôn mặt hợp lệ.
 *  2. BLINK_REQUESTED: Yêu cầu chớp mắt + Bẫy giữ thẳng đầu (chống video vừa lắc vừa chớp).
 *  3. HEAD_SHAKE_REQUESTED: Yêu cầu lắc đầu + Kiểm tra Thị sai 3D Mũi-Mắt (3D Nose Parallax) + Nhìn thẳng lại.
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

    // Các ngưỡng cấu hình cho Liveness
    public static final float EYE_CLOSED_THRESHOLD   = 0.15f;  // Ngưỡng mắt nhắm (<= 0.15)
    public static final float EYE_OPEN_THRESHOLD     = 0.75f;  // Ngưỡng mắt mở (>= 0.75)
    public static final float HEAD_SHAKE_LEFT_ANGLE  = -11.5f; // Góc quay trái (<= -15 độ)
    public static final float HEAD_SHAKE_RIGHT_ANGLE = 11.5f;  // Góc quay phải (>= 15 độ)
    public static final float HEAD_CENTER_ANGLE      = 10.0f;  // Góc nhìn thẳng lại sau khi lắc đầu (|Y| <= 10 độ)

    // Ngưỡng biến thiên phối cảnh 3D & Thị sai Mũi - Mắt (3D Nose-to-Eye Parallax)
    private static final float MIN_3D_PERSPECTIVE_VARIANCE = 0.025f;
    private static final float MIN_NOSE_PARALLAX_VARIANCE  = 0.12f;

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

    // Biến lưu trạng thái chớp mắt
    private int initialOpenFrames = 0;
    private int closedFrameCount  = 0;
    private static final int REQUIRED_OPEN_FRAMES_BEFORE_BLINK = 3;
    private boolean hasEyeClosed = false;

    // Biến lưu trạng thái lắc đầu & giữ thẳng mặt trước khi nháy Flash
    private boolean hasTurnedLeft  = false;
    private boolean hasTurnedRight = false;
    private int centerHoldFrames   = 0;
    private static final int REQUIRED_CENTER_HOLD_FRAMES = 3;
    private Float prevHeadY        = null;

    // Biến theo dõi biến thiên không gian 3D & Thị sai Mũi (Nose Parallax)
    private float minEyeRatio  = 1.0f;
    private float maxEyeRatio  = 0.0f;
    private float minNoseRatio = 1.0f;
    private float maxNoseRatio = 0.0f;
    private boolean has3DPerspectiveShift = false;

    // Biến theo dõi phản xạ ánh sáng (Screen Flash)
    private boolean hasFlashPassed = false;
    private boolean isFaceLost     = false;

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
        closedFrameCount = 0;
        hasEyeClosed = false;
        hasTurnedLeft = false;
        hasTurnedRight = false;
        centerHoldFrames = 0;
        prevHeadY = null;
        minEyeRatio = 1.0f;
        maxEyeRatio = 0.0f;
        minNoseRatio = 1.0f;
        maxNoseRatio = 0.0f;
        has3DPerspectiveShift = false;
        hasFlashPassed = false;
        isFaceLost = false;
    }

    /**
     * Nhận đối tượng Face từ ML Kit để phân tích
     */
    public void processFace(Face face) {
        if (currentState == State.IDLE || currentState == State.FAILED) {
            return;
        }

        if (currentState == State.COMPLETED) {
            if (face == null) {
                start();
            }
            return;
        }

        if (face == null) {
            if (currentState != State.WAITING_FOR_FACE && !isFaceLost) {
                isFaceLost = true;
                notifyInstruction("Không tìm thấy khuôn mặt, vui lòng nhìn thẳng camera");
            }
            return;
        }

        if (isFaceLost) {
            isFaceLost = false;
            notifyInstruction(getInstructionForCurrentState());
        }

        Float leftEye  = face.getLeftEyeOpenProbability();
        Float rightEye = face.getRightEyeOpenProbability();
        float headY    = face.getHeadEulerAngleY();

        // Bẫy chống tua/cắt ghép video: góc đầu nhảy quá 24 độ trong 1 frame đơn lẻ
        if (prevHeadY != null && Math.abs(headY - prevHeadY) > 24.0f) {
            fail("Phát hiện chuyển động video bất thường!");
            return;
        }
        prevHeadY = headY;

        // 1. Kiểm tra 3D Perspective & Thị sai Mũi - Mắt
        check3DPerspective(face);

        // 2. Chạy luồng State Machine
        processFaceValues(leftEye, rightEye, headY);
    }

    private String getInstructionForCurrentState() {
        switch (currentState) {
            case WAITING_FOR_FACE:
                return "Vui lòng đưa khuôn mặt vào khung hình";
            case BLINK_REQUESTED:
                return "Bước 1/3: Vui lòng giữ thẳng đầu và chớp mắt";
            case HEAD_SHAKE_REQUESTED:
                if (hasTurnedLeft && hasTurnedRight) {
                    return "Bước 2/3: Tốt! Giờ hãy nhìn thẳng vào camera";
                }
                return "Bước 2/3: Vui lòng quay sang trái & quay sang phải";
            case FLASH_CHALLENGE:
                return "Bước 3/3: Giữ thẳng mặt - Đang kiểm tra ánh sáng...";
            default:
                return "";
        }
    }

    /**
     * Phân tích cấu trúc 3D thực của khuôn mặt khi xoay đầu:
     * 1) Tỷ lệ khoảng cách 2 mắt / chiều rộng hộp giới hạn.
     * 2) Thị sai 3D của chóp mũi so với 2 mắt: Trên màn hình phẳng 2D, mũi và mắt đồng phẳng
     *    nên tỷ lệ (noseX - leftEyeX)/(rightEyeX - leftEyeX) không đổi khi nghiêng điện thoại.
     *    Trên mặt người thật 3D, mũi nhô cao nên tỷ lệ này dịch chuyển mạnh khi quay trái/phải.
     */
    private void check3DPerspective(Face face) {
        if (currentState != State.HEAD_SHAKE_REQUESTED) return;

        FaceLandmark leftLandmark  = face.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark rightLandmark = face.getLandmark(FaceLandmark.RIGHT_EYE);
        FaceLandmark noseLandmark  = face.getLandmark(FaceLandmark.NOSE_BASE);
        Rect bounds = face.getBoundingBox();

        if (leftLandmark != null && rightLandmark != null && bounds != null && bounds.width() > 0) {
            float lx = leftLandmark.getPosition().x;
            float rx = rightLandmark.getPosition().x;
            float eyeDist = Math.abs(rx - lx);
            float ratio   = eyeDist / (float) bounds.width();

            if (ratio < minEyeRatio) minEyeRatio = ratio;
            if (ratio > maxEyeRatio) maxEyeRatio = ratio;

            float eyeVariance = maxEyeRatio - minEyeRatio;

            float noseVariance = 0.0f;
            if (noseLandmark != null && eyeDist > 10.0f) {
                float minX = Math.min(lx, rx);
                float noseRatio = (noseLandmark.getPosition().x - minX) / eyeDist;
                if (noseRatio < minNoseRatio) minNoseRatio = noseRatio;
                if (noseRatio > maxNoseRatio) maxNoseRatio = noseRatio;
                noseVariance = maxNoseRatio - minNoseRatio;
            }

            if (eyeVariance >= MIN_3D_PERSPECTIVE_VARIANCE && noseVariance >= MIN_NOSE_PARALLAX_VARIANCE) {
                has3DPerspectiveShift = true;
            }
        }
    }

    public void processFaceValues(Float leftEyeOpenProb, Float rightEyeOpenProb, float headEulerAngleY) {
        switch (currentState) {
            case WAITING_FOR_FACE:
                if (Math.abs(headEulerAngleY) <= 12.0f) {
                    changeState(State.BLINK_REQUESTED, "Bước 1/3: Vui lòng chớp mắt");
                } else {
                    notifyInstruction("Vui lòng nhìn thẳng vào camera để bắt đầu");
                }
                break;

            case BLINK_REQUESTED:
                checkBlink(leftEyeOpenProb, rightEyeOpenProb, headEulerAngleY);
                break;

            case HEAD_SHAKE_REQUESTED:
                checkHeadShake(headEulerAngleY);
                break;

            default:
                break;
        }
    }

    private void checkBlink(Float leftEyeProb, Float rightEyeProb, float headEulerAngleY) {
        if (leftEyeProb == null || rightEyeProb == null) return;
        if (leftEyeProb < 0 || rightEyeProb < 0) return;

        // BẪY HÀNH ĐỘNG CHÉO: Nếu đang ở bước chớp mắt mà đầu quay nghiêng > 15 độ (dấu hiệu video đang lắc đầu) -> Reset
        if (Math.abs(headEulerAngleY) > 15.0f) {
            initialOpenFrames = 0;
            closedFrameCount = 0;
            hasEyeClosed = false;
            notifyInstruction("Bước 1/3: Giữ thẳng đầu và chớp mắt");
            return;
        }

        boolean eyesCurrentlyClosed = (leftEyeProb <= EYE_CLOSED_THRESHOLD && rightEyeProb <= EYE_CLOSED_THRESHOLD);
        boolean eyesCurrentlyOpen   = (leftEyeProb >= EYE_OPEN_THRESHOLD && rightEyeProb >= EYE_OPEN_THRESHOLD);

        if (!hasEyeClosed) {
            if (eyesCurrentlyOpen) {
                initialOpenFrames++;
            }
            if (initialOpenFrames >= REQUIRED_OPEN_FRAMES_BEFORE_BLINK && eyesCurrentlyClosed) {
                hasEyeClosed = true;
                closedFrameCount = 1;
            }
        } else {
            if (eyesCurrentlyClosed) {
                closedFrameCount++;
                // Nếu nhắm mắt quá lâu (> 15 frames) -> Không phải chớp mắt tự nhiên, reset lại
                if (closedFrameCount > 15) {
                    initialOpenFrames = 0;
                    closedFrameCount = 0;
                    hasEyeClosed = false;
                }
            } else if (eyesCurrentlyOpen) {
                changeState(State.HEAD_SHAKE_REQUESTED, "Bước 2/3: Vui lòng quay sang trái & quay sang phải");
            }
        }
    }

    private void checkHeadShake(float headEulerAngleY) {
        if (headEulerAngleY <= HEAD_SHAKE_LEFT_ANGLE) {
            hasTurnedLeft = true;
        }

        if (headEulerAngleY >= HEAD_SHAKE_RIGHT_ANGLE) {
            hasTurnedRight = true;
        }

        // Yêu cầu quay cả 2 hướng + Có biến thiên thị sai 3D hợp lệ
        if (hasTurnedLeft && hasTurnedRight) {
            if (!has3DPerspectiveShift) {
                notifyInstruction("⚠️ Hãy lắc đầu thật (Phát hiện màn hình phẳng 2D)");
                return;
            }

            // Bắt buộc người dùng quay mặt về lại chính diện (|Y| <= 10 độ) và giữ ổn định 3 frame trước khi nháy Flash
            if (Math.abs(headEulerAngleY) > HEAD_CENTER_ANGLE) {
                centerHoldFrames = 0;
                notifyInstruction("Bước 2/3: Tốt! Giờ hãy nhìn thẳng vào camera");
                return;
            }

            centerHoldFrames++;
            if (centerHoldFrames >= REQUIRED_CENTER_HOLD_FRAMES) {
                triggerFlashChallenge();
            }
        }
    }

    private void triggerFlashChallenge() {
        changeState(State.FLASH_CHALLENGE, "Bước 3/3: Giữ thẳng mặt - Đang kiểm tra ánh sáng...");
        if (listener != null) {
            listener.onRequestFlashChallenge();
        }
    }

    public void onFlashChallengeResult(boolean success, float lumaShift) {
        if (currentState != State.FLASH_CHALLENGE) return;

        if (success) {
            hasFlashPassed = true;
            stopTimeoutTimer();
            changeState(State.COMPLETED, "Xác thực Liveness thành công!");
            if (listener != null) {
                listener.onLivenessCompleted();
            }
        } else {
            fail("Phát hiện video giả mạo! Màn hình không phản chiếu ánh sáng (Shift: " + String.format("%.3f", lumaShift) + ")");
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
        timeoutRunnable = () -> fail("Hết thời gian xác thực (Timeout)");
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
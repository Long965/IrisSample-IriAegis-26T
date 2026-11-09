package com.iritech.irissample;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * Lớp trợ giúp kiểm tra AI Passive Liveness (Anti-Spoofing) sử dụng mô hình Deep Learning MiniVision (TFLite).
 * Phân biệt NGƯỜI THẬT (Real Person) vs ẢNH/VIDEO GIẢ MẠO (Spoof / Screen Replay / Photo).
 */
public class AntiSpoofHelper {

    private static final String TAG = "AntiSpoofHelper";

    public static final String MODEL_FILE = "minivision_anti_spoof.tflite";

    // Ngưỡng xác suất đánh giá người thật (Mặc định >= 0.50 là người thật)
    private static final float REAL_THRESHOLD = 0.50f;

    private Interpreter tflite;
    private boolean isModelLoaded = false;

    // Cấu hình linh hoạt từ Tensor TFLite
    private boolean isNCHW = true; // PyTorch export sang TFLite thường là NCHW: [1, 3, H, W]
    private int inputWidth = 80;
    private int inputHeight = 80;
    private int outputClassCount = 3; // MiniVision chuẩn 3 classes: [0: Paper, 1: Real, 2: Screen]

    public static class AntiSpoofResult {
        private final boolean isReal;
        private final float realScore;
        private final float spoofScore;

        public AntiSpoofResult(boolean isReal, float realScore, float spoofScore) {
            this.isReal = isReal;
            this.realScore = realScore;
            this.spoofScore = spoofScore;
        }

        public boolean isReal() {
            return isReal;
        }

        public float getRealScore() {
            return realScore;
        }

        public float getSpoofScore() {
            return spoofScore;
        }

        @Override
        public String toString() {
            return String.format("AntiSpoofResult{isReal=%b, realScore=%.3f, spoofScore=%.3f}",
                    isReal, realScore, spoofScore);
        }
    }

    public AntiSpoofHelper(Context context) {
        try {
            ByteBuffer modelBuffer = loadModelFile(context);
            if (modelBuffer != null) {
                Interpreter.Options options = new Interpreter.Options();
                options.setNumThreads(4);
                tflite = new Interpreter(modelBuffer, options);

                // Tự động phân tích Kích thước Tensor từ file .tflite
                inspectModelTensors();

                isModelLoaded = true;
                Log.d(TAG, "Mô hình MiniVision Anti-Spoofing TFLite đã nạp thành công!");
            } else {
                Log.w(TAG, "Chưa tìm thấy file " + MODEL_FILE + " trong thư mục assets.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Lỗi nạp mô hình TFLite Anti-Spoofing: " + e.getMessage(), e);
            isModelLoaded = false;
        }
    }

    private void inspectModelTensors() {
        if (tflite == null) return;

        try {
            int[] inputShape = tflite.getInputTensor(0).shape();
            Log.d(TAG, "Input Tensor Shape: " + Arrays.toString(inputShape));

            if (inputShape.length == 4) {
                if (inputShape[1] == 3) {
                    isNCHW = true;
                    inputHeight = inputShape[2];
                    inputWidth  = inputShape[3];
                } else {
                    isNCHW = false;
                    inputHeight = inputShape[1];
                    inputWidth  = inputShape[2];
                }
            }

            int[] outputShape = tflite.getOutputTensor(0).shape();
            Log.d(TAG, "Output Tensor Shape: " + Arrays.toString(outputShape));
            if (outputShape.length >= 2) {
                outputClassCount = outputShape[1];
            }

            Log.d(TAG, String.format("Phân tích Model: NCHW=%b, Size=%dx%d, Classes=%d",
                    isNCHW, inputWidth, inputHeight, outputClassCount));

        } catch (Exception e) {
            Log.e(TAG, "Lỗi đọc Tensor shape: " + e.getMessage());
        }
    }

    private ByteBuffer loadModelFile(Context context) {
        try {
            AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
            FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
            FileChannel fileChannel = inputStream.getChannel();
            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        } catch (IOException e) {
            Log.w(TAG, "File model " + MODEL_FILE + " chưa được copy vào app/src/main/assets/");
            return null;
        }
    }

    /**
     * Phân tích ảnh khuôn mặt để kiểm tra người thật vs giả mạo
     */
    public AntiSpoofResult classifyFace(Bitmap faceBitmap) {
        if (faceBitmap == null) {
            return new AntiSpoofResult(false, 0.0f, 1.0f);
        }

        if (!isModelLoaded || tflite == null) {
            Log.w(TAG, "File .tflite chưa load thành công. Đang chạy chế độ từ chối an toàn.");
            return new AntiSpoofResult(false, 0.0f, 1.0f);
        }

        try {
            // 1. Resize ảnh về chuẩn đầu vào của mô hình
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(faceBitmap, inputWidth, inputHeight, true);

            // 2. Chuyển đổi Bitmap sang ByteBuffer với chuẩn hóa [-1.0, 1.0]
            ByteBuffer imgData = ByteBuffer.allocateDirect(1 * 3 * inputHeight * inputWidth * 4);
            imgData.order(ByteOrder.nativeOrder());
            imgData.rewind();

            int[] intValues = new int[inputWidth * inputHeight];
            scaledBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            if (scaledBitmap != faceBitmap) {
                scaledBitmap.recycle();
            }

            if (isNCHW) {
                // Định dạng PyTorch [1, 3, H, W]
                for (int c = 0; c < 3; c++) {
                    for (int i = 0; i < intValues.length; i++) {
                        int val = intValues[i];
                        float color;
                        if (c == 0)      color = (((val >> 16) & 0xFF) - 127.5f) / 128.0f;
                        else if (c == 1) color = (((val >> 8)  & 0xFF) - 127.5f) / 128.0f;
                        else             color = ((val & 0xFF)         - 127.5f) / 128.0f;
                        imgData.putFloat(color);
                    }
                }
            } else {
                // Định dạng TensorFlow [1, H, W, 3]
                for (int i = 0; i < intValues.length; i++) {
                    int val = intValues[i];
                    imgData.putFloat((((val >> 16) & 0xFF) - 127.5f) / 128.0f);
                    imgData.putFloat((((val >> 8)  & 0xFF) - 127.5f) / 128.0f);
                    imgData.putFloat(((val & 0xFF)         - 127.5f) / 128.0f);
                }
            }

            // 3. Khai báo mảng chứa đầu ra
            float[][] outputScores = new float[1][outputClassCount];

            // 4. Run Inference
            tflite.run(imgData, outputScores);

            // 5. Áp dụng Softmax
            float[] probs = softmax(outputScores[0]);
            Log.d(TAG, "Raw Logits: " + Arrays.toString(outputScores[0]) + " -> Probs: " + Arrays.toString(probs));

            float realScore;
            float spoofScore;

            if (outputClassCount == 3) {
                // Chuẩn MiniVision Silent-Face-Anti-Spoofing:
                // Index 0: Fake Paper/Photo
                // Index 1: REAL PERSON (Người thật)
                // Index 2: Fake Screen/Video Replay
                realScore  = probs[1];
                spoofScore = Math.max(probs[0], probs[2]);
            } else {
                // 2 classes: Index 1 = Real, Index 0 = Fake
                realScore  = probs[1];
                spoofScore = probs[0];
            }

            boolean isReal = realScore >= REAL_THRESHOLD;
            Log.d(TAG, String.format("Kết quả MiniVision AI -> RealScore: %.3f, SpoofScore: %.3f => isReal: %b",
                    realScore, spoofScore, isReal));

            return new AntiSpoofResult(isReal, realScore, spoofScore);

        } catch (Exception e) {
            Log.e(TAG, "Lỗi phân tích MiniVision TFLite: " + e.getMessage(), e);
            return new AntiSpoofResult(false, 0.0f, 1.0f);
        }
    }

    private float[] softmax(float[] logits) {
        float[] probs = new float[logits.length];
        float max = logits[0];
        for (float v : logits) {
            if (v > max) max = v;
        }
        float sum = 0.0f;
        for (int i = 0; i < logits.length; i++) {
            probs[i] = (float) Math.exp(logits[i] - max);
            sum += probs[i];
        }
        if (sum != 0) {
            for (int i = 0; i < probs.length; i++) {
                probs[i] /= sum;
            }
        }
        return probs;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
        isModelLoaded = false;
    }

    public boolean isModelLoaded() {
        return isModelLoaded;
    }
}
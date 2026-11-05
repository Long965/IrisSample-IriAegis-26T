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

public class FaceMatchHelper {
    private static final String TAG = "FaceMatchHelper";

    // ĐÃ SỬA: Đổi tên file trùng khớp 100% với file tải từ GitHub về
    private static final String MODEL_FILE = "mobile_face_net.tflite";

    // Model MobileFaceNet yêu cầu ảnh đầu vào kích thước 112x112 pixel
    private static final int INPUT_IMAGE_SIZE = 112;

    private Interpreter tflite;

    public FaceMatchHelper(Context context) {
        try {
            tflite = new Interpreter(loadModelFile(context));
            Log.d(TAG, "TFLite Model loaded successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error loading TFLite model: " + e.getLocalizedMessage());
        }
    }

    // Đọc file model từ thư mục assets
    private ByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Trích xuất khuôn mặt thành mảng Vector 192 số đặc trưng (Embedding)
     */
    public float[] getFaceEmbedding(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "TFLite Interpreter is null. Cannot extract embedding.");
            return null;
        }

        // 1. Resize ảnh khuôn mặt về chuẩn 112x112 của bộ model AI
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);

        // 2. Chuyển đổi Bitmap thành ByteBuffer (Định dạng Float 32bit, gồm 3 kênh màu R, G, B)
        // 1 ảnh * 112 rộng * 112 cao * 3 kênh màu * 4 bytes (độ rộng kiểu float)
        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4);
        imgData.order(ByteOrder.nativeOrder());

        int[] intValues = new int[INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE];
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());

        // ĐÃ SỬA: Viết lại đoạn bóc tách và chuẩn hóa màu sắc rõ ràng để tránh lỗi cú pháp toán học
        imgData.rewind();
        for (int i = 0; i < INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE; ++i) {
            final int val = intValues[i];

            // Tách các kênh màu Red, Green, Blue và đưa về khoảng [-1.0, 1.0]
            float r = (((val >> 16) & 0xFF) - 127.5f) / 128.0f;
            float g = (((val >> 8) & 0xFF) - 127.5f) / 128.0f;
            float b = ((val & 0xFF) - 127.5f) / 128.0f;

            // Nạp tuần tự vào ByteBuffer đầu vào của AI
            imgData.putFloat(r);
            imgData.putFloat(g);
            imgData.putFloat(b);
        }

        // 3. Khai báo mảng chứa kết quả đầu ra (MobileFaceNet trả về mảng dạng 1 dòng x 192 cột số đặc trưng)
        float[][] outputResult = new float[1][192];

        // 4. Chạy AI để tiến hành trích xuất đặc trưng khuôn mặt
        tflite.run(imgData, outputResult);

        return outputResult[0];
    }

    /**
     * So sánh khoảng cách hình học (Cosine Similarity) giữa 2 mảng vector khuôn mặt
     * Trả về true nếu độ tương đồng cao hơn ngưỡng (Thường là 0.75 - 0.80 cho MobileFaceNet)
     */
    public boolean compareFaces(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null) return false;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            normA += Math.pow(embedding1[i], 2);
            normB += Math.pow(embedding2[i], 2);
        }

        double similarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
        Log.d(TAG, "Độ tương đồng thực tế giữa 2 khuôn mặt: " + similarity);

        // Ngưỡng khuyến nghị tối ưu cho MobileFaceNet: 0.75 (Càng gần 1.0 càng chính chủ)
        return similarity > 0.75;
    }

    public void close() {
        if (tflite != null) {
            tflite.close();
            tflite = null;
        }
    }
}
"""
Port 1:1 của 2 lớp Java dùng model TFLite:
  - AntiSpoofHelper.java  -> AntiSpoofHelper (minivision_anti_spoof.tflite)
  - FaceMatchHelper.java  -> FaceMatchHelper (mobile_face_net.tflite)

Mọi thông số (kích thước input, chuẩn hóa màu, thứ tự kênh, softmax, mapping class,
ngưỡng) giữ nguyên như code Android.
Ảnh đầu vào ở dạng numpy BGR (OpenCV) – tương đương Bitmap ARGB trên Android.
"""
import logging
import os
import threading
from dataclasses import dataclass
from typing import List, Optional, Tuple

import cv2
import numpy as np

# Nạp TFLite Interpreter (ai_edge_litert -> tflite_runtime -> tensorflow)
try:
    from ai_edge_litert.interpreter import Interpreter
except ImportError:  # pragma: no cover
    try:
        from tflite_runtime.interpreter import Interpreter
    except ImportError:
        import tensorflow as tf
        Interpreter = tf.lite.Interpreter

logger = logging.getLogger("TFLiteModels")


def _resize_like_bitmap(img_bgr: np.ndarray, width: int, height: int) -> np.ndarray:
    """Bitmap.createScaledBitmap(bitmap, w, h, filter=true) = nội suy song tuyến (bilinear)."""
    return cv2.resize(img_bgr, (width, height), interpolation=cv2.INTER_LINEAR)


# =====================================================================================
# 1. AntiSpoofHelper.java
# =====================================================================================

@dataclass
class AntiSpoofResult:
    is_real: bool
    real_score: float
    spoof_score: float
    raw_logits: Optional[List[float]] = None
    probs: Optional[List[float]] = None

    def to_dict(self) -> dict:
        return {
            "is_real": self.is_real,
            "real_score": round(float(self.real_score), 6),
            "spoof_score": round(float(self.spoof_score), 6),
            "raw_logits": self.raw_logits,
            "probs": self.probs,
        }

    def __str__(self) -> str:
        return "AntiSpoofResult{isReal=%s, realScore=%.3f, spoofScore=%.3f}" % (
            str(self.is_real).lower(), self.real_score, self.spoof_score)


class AntiSpoofHelper:
    """Kiểm tra AI Passive Liveness (Anti-Spoofing) bằng MiniVision TFLite."""

    MODEL_FILE = "minivision_anti_spoof.tflite"

    # Ngưỡng xác suất đánh g ngiáười thật (Cân bằng an toàn >= 0.35f)
    REAL_THRESHOLD = 0.35

    def __init__(self, model_path: str):
        self._lock = threading.Lock()
        self.tflite = None
        self.is_model_loaded = False

        # Cấu hình linh hoạt từ Tensor TFLite (giá trị mặc định giống Java)
        self.is_nchw = True
        self.input_width = 80
        self.input_height = 80
        self.output_class_count = 3  # [0: Paper, 1: Real, 2: Screen]

        try:
            if not os.path.exists(model_path):
                logger.warning("Chưa tìm thấy file %s", model_path)
                return
            # options.setNumThreads(4)
            try:
                self.tflite = Interpreter(model_path=model_path, num_threads=4)
            except TypeError:
                self.tflite = Interpreter(model_path=model_path)
            self.tflite.allocate_tensors()
            self._inspect_model_tensors()
            self.is_model_loaded = True
            logger.info("Mô hình MiniVision Anti-Spoofing TFLite đã nạp thành công!")
        except Exception as e:  # noqa: BLE001
            logger.error("Lỗi nạp mô hình TFLite Anti-Spoofing: %s", e)
            self.is_model_loaded = False

    def _inspect_model_tensors(self) -> None:
        if self.tflite is None:
            return
        try:
            in_det = self.tflite.get_input_details()[0]
            out_det = self.tflite.get_output_details()[0]
            self._input_index = in_det["index"]
            self._output_index = out_det["index"]

            input_shape = [int(v) for v in in_det["shape"]]
            logger.info("Input Tensor Shape: %s", input_shape)
            if len(input_shape) == 4:
                if input_shape[1] == 3:
                    self.is_nchw = True
                    self.input_height = input_shape[2]
                    self.input_width = input_shape[3]
                else:
                    self.is_nchw = False
                    self.input_height = input_shape[1]
                    self.input_width = input_shape[2]

            output_shape = [int(v) for v in out_det["shape"]]
            logger.info("Output Tensor Shape: %s", output_shape)
            if len(output_shape) >= 2:
                self.output_class_count = output_shape[1]

            logger.info("Phân tích Model: NCHW=%s, Size=%dx%d, Classes=%d",
                        self.is_nchw, self.input_width, self.input_height, self.output_class_count)
        except Exception as e:  # noqa: BLE001
            logger.error("Lỗi đọc Tensor shape: %s", e)

    @staticmethod
    def _softmax(logits: np.ndarray) -> np.ndarray:
        logits = logits.astype(np.float32)
        mx = np.float32(np.max(logits))
        probs = np.exp(logits - mx).astype(np.float32)
        s = np.float32(np.sum(probs))
        if s != 0:
            probs = probs / s
        return probs

    def classify_face(self, face_bgr: Optional[np.ndarray]) -> AntiSpoofResult:
        """Phân tích ảnh khuôn mặt để kiểm tra người thật vs giả mạo."""
        if face_bgr is None or face_bgr.size == 0:
            return AntiSpoofResult(False, 0.0, 1.0)

        if not self.is_model_loaded or self.tflite is None:
            logger.warning("File .tflite chưa load thành công. Đang chạy chế độ từ chối an toàn.")
            return AntiSpoofResult(False, 0.0, 1.0)

        try:
            # 1. Resize ảnh về chuẩn đầu vào của mô hình
            scaled = _resize_like_bitmap(face_bgr, self.input_width, self.input_height)

            # 2. Hệ màu BGR, dải [0.0f, 255.0f] (OpenCV vốn đã là BGR)
            img = scaled.astype(np.float32)
            if self.is_nchw:
                tensor = np.transpose(img, (2, 0, 1))[np.newaxis, ...]  # [1, 3, H, W]
            else:
                tensor = img[np.newaxis, ...]  # [1, H, W, 3]
            tensor = np.ascontiguousarray(tensor, dtype=np.float32)

            # 3-4. Run Inference
            with self._lock:
                self.tflite.set_tensor(self._input_index, tensor)
                self.tflite.invoke()
                output_scores = np.array(self.tflite.get_tensor(self._output_index)[0], dtype=np.float32)

            # 5. Áp dụng Softmax
            probs = self._softmax(output_scores)
            logger.info("Raw Logits: %s -> Probs: %s", output_scores.tolist(), probs.tolist())

            if self.output_class_count == 3:
                # Hỗ trợ linh hoạt cả 2 chuẩn class mapping của MiniVision
                real_candidate1 = probs[1]
                real_candidate0 = probs[0]
                if real_candidate1 >= real_candidate0 and real_candidate1 >= probs[2]:
                    real_score = real_candidate1
                    spoof_score = max(probs[0], probs[2])
                elif real_candidate0 >= real_candidate1 and real_candidate0 >= probs[2]:
                    real_score = real_candidate0
                    spoof_score = max(probs[1], probs[2])
                else:
                    real_score = max(probs[0], probs[1])
                    spoof_score = probs[2]
            else:
                real_score = max(probs[0], probs[1])
                spoof_score = min(probs[0], probs[1])

            is_real = bool(real_score >= self.REAL_THRESHOLD)
            logger.info("Kết quả MiniVision AI -> RealScore: %.3f, SpoofScore: %.3f => isReal: %s",
                        real_score, spoof_score, is_real)
            return AntiSpoofResult(is_real, float(real_score), float(spoof_score),
                                   [float(v) for v in output_scores], [float(v) for v in probs])
        except Exception as e:  # noqa: BLE001
            logger.error("Lỗi phân tích MiniVision TFLite: %s", e)
            return AntiSpoofResult(False, 0.0, 1.0)


# =====================================================================================
# 2. FaceMatchHelper.java
# =====================================================================================

class FaceMatchHelper:
    """MobileFaceNet: trích xuất vector 192 chiều và so sánh Cosine Similarity."""

    MODEL_FILE = "mobile_face_net.tflite"

    # Model MobileFaceNet yêu cầu ảnh đầu vào kích thước 112x112 pixel
    INPUT_IMAGE_SIZE = 112
    EMBEDDING_SIZE = 192

    # Ngưỡng khuyến nghị tối ưu cho MobileFaceNet: 0.75 (similarity > 0.75)
    MATCH_THRESHOLD = 0.65

    def __init__(self, model_path: str):
        self._lock = threading.Lock()
        self.tflite = None
        try:
            if not os.path.exists(model_path):
                raise FileNotFoundError(model_path)
            self.tflite = Interpreter(model_path=model_path)
            self.tflite.allocate_tensors()
            self._input_index = self.tflite.get_input_details()[0]["index"]
            self._output_index = self.tflite.get_output_details()[0]["index"]
            logger.info("TFLite Model loaded successfully.")
        except Exception as e:  # noqa: BLE001
            logger.error("Error loading TFLite model: %s", e)
            self.tflite = None

    @property
    def is_model_loaded(self) -> bool:
        return self.tflite is not None

    def get_face_embedding(self, face_bgr: Optional[np.ndarray]) -> Optional[np.ndarray]:
        """Trích xuất khuôn mặt thành mảng Vector 192 số đặc trưng (Embedding)."""
        if self.tflite is None:
            logger.error("TFLite Interpreter is null. Cannot extract embedding.")
            return None
        if face_bgr is None or face_bgr.size == 0:
            return None

        # 1. Resize ảnh khuôn mặt về chuẩn 112x112
        scaled = _resize_like_bitmap(face_bgr, self.INPUT_IMAGE_SIZE, self.INPUT_IMAGE_SIZE)

        # 2. Float32, 3 kênh R, G, B, chuẩn hóa (x - 127.5) / 128.0 -> [-1.0, 1.0]
        rgb = cv2.cvtColor(scaled, cv2.COLOR_BGR2RGB).astype(np.float32)
        img_data = ((rgb - np.float32(127.5)) / np.float32(128.0))[np.newaxis, ...]
        img_data = np.ascontiguousarray(img_data, dtype=np.float32)

        # 3-4. Chạy AI -> mảng 1 x 192
        with self._lock:
            self.tflite.set_tensor(self._input_index, img_data)
            self.tflite.invoke()
            output = np.array(self.tflite.get_tensor(self._output_index)[0], dtype=np.float32)
        return output

    @classmethod
    def cosine_similarity(cls, embedding1: Optional[np.ndarray], embedding2: Optional[np.ndarray]) -> Optional[float]:
        if embedding1 is None or embedding2 is None:
            return None
        e1 = np.asarray(embedding1, dtype=np.float32)
        e2 = np.asarray(embedding2, dtype=np.float32)
        n = len(e1)
        dot = float(np.sum((e1[:n] * e2[:n]).astype(np.float64)))
        norm_a = float(np.sum(np.power(e1[:n].astype(np.float64), 2)))
        norm_b = float(np.sum(np.power(e2[:n].astype(np.float64), 2)))
        denom = np.sqrt(norm_a) * np.sqrt(norm_b)
        if denom == 0:
            return float("nan")
        return dot / denom

    @classmethod
    def compare_faces(cls, embedding1: Optional[np.ndarray], embedding2: Optional[np.ndarray]) -> Tuple[bool, Optional[float]]:
        """So sánh Cosine Similarity; trả về (similarity > 0.75, similarity)."""
        if embedding1 is None or embedding2 is None:
            return False, None
        similarity = cls.cosine_similarity(embedding1, embedding2)
        logger.info("Độ tương đồng thực tế giữa 2 khuôn mặt: %s", similarity)
        if similarity is None or np.isnan(similarity):
            return False, None  # Java: NaN > 0.75 == false
        return bool(similarity > cls.MATCH_THRESHOLD), float(similarity)

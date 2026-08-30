package com.dgpack.chamcong.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dgpack.chamcong.face.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** [eyeOpenProbability] = trung bình 2 mắt, null nếu ML Kit không trả về được (hiếm). */
data class FaceDetectionResult(val croppedBitmap: Bitmap, val eyeOpenProbability: Float?)

/**
 * Phân tích khung hình camera: ML Kit chỉ PHÁT HIỆN có khuôn mặt (không biết là ai),
 * throttle xuống ~[targetFps] để tránh nóng máy vô ích trên thiết bị yếu (mục [4.3]).
 * Khi tìm thấy mặt, crop + resize rồi trả về qua [onFaceDetected] để bước sau
 * (FaceEmbedder + FaceMatcher) nhận diện danh tính. Bật classification mode để lấy kèm
 * xác suất mắt mở — dùng cho liveness detection kiểu chớp mắt (Phase 2, mục [12]).
 */
class FaceAnalyzer(
    private val targetFps: Int = 3,
    private val onFaceDetected: (FaceDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = 1000L / targetFps
    private var lastProcessedAt = 0L

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.15f)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastProcessedAt < minIntervalMs) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        lastProcessedAt = now

        val rotation = imageProxy.imageInfo.rotationDegrees
        val inputImage = InputImage.fromMediaImage(mediaImage, rotation)

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                // Phase 1 chỉ xử lý 1 người tại 1 thời điểm — nếu nhiều mặt trong khung
                // hình, lấy mặt lớn nhất (gần camera nhất, khả năng cao nhất là người
                // đang chấm công, không phải người đi ngang phía sau).
                val largest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (largest != null) {
                    try {
                        val bitmap = ImageUtils.imageProxyToBitmap(imageProxy)
                        val cropped = ImageUtils.cropAndResizeFace(bitmap, largest.boundingBox)
                        val leftProb = largest.leftEyeOpenProbability
                        val rightProb = largest.rightEyeOpenProbability
                        val eyeOpenProbability = when {
                            leftProb != null && rightProb != null -> (leftProb + rightProb) / 2f
                            leftProb != null -> leftProb
                            rightProb != null -> rightProb
                            else -> null
                        }
                        onFaceDetected(FaceDetectionResult(cropped, eyeOpenProbability))
                    } catch (_: Exception) {
                        // Khung hình lỗi (mặt sát biên, crop rỗng...) — bỏ qua, không crash camera loop.
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}

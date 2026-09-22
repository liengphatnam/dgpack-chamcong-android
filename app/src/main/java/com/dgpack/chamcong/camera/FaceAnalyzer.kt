package com.dgpack.chamcong.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dgpack.chamcong.face.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark

/**
 * @param croppedBitmap     mặt đã căn chỉnh theo 2 mắt (hoặc crop thô nếu thiếu landmark), 112x112
 * @param eyeOpenProbability trung bình 2 mắt, null nếu ML Kit không trả về được (hiếm)
 * @param faceWidthPx       bề rộng khuôn mặt trong khung hình gốc — quá nhỏ = người đứng xa,
 *                          ảnh phóng to bị nhoè, embedding không đáng tin
 * @param aligned           true nếu đã căn theo landmark 2 mắt
 */
data class FaceDetectionResult(
    val croppedBitmap: Bitmap,
    val eyeOpenProbability: Float?,
    val faceWidthPx: Int,
    val aligned: Boolean,
    /** Thời gian ML Kit phát hiện mặt + chuẩn bị ảnh cho frame này (ms) — hiện lên màn hình để chẩn đoán máy chậm. */
    val detectMs: Long
)

/**
 * Phân tích khung hình camera: ML Kit chỉ PHÁT HIỆN có khuôn mặt (không biết là ai),
 * throttle xuống ~[targetFps] để tránh nóng máy vô ích trên thiết bị yếu (mục [4.3]).
 * Khi tìm thấy mặt, căn chỉnh theo 2 mắt + resize rồi trả về qua [onFaceDetected] để bước
 * sau (FaceEmbedder + FaceMatcher) nhận diện danh tính. Bật classification mode để lấy kèm
 * xác suất mắt mở (liveness chớp mắt) và landmark mode để lấy vị trí 2 mắt (căn chỉnh).
 */
class FaceAnalyzer(
    private val targetFps: Int = 3,
    private val onFaceDetected: (FaceDetectionResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = 1000L / targetFps
    /**
     * Mốc HOÀN TẤT frame trước (không phải mốc bắt đầu). Nếu ML Kit mất 400 ms trên máy yếu
     * mà tính từ lúc bắt đầu thì frame kế tiếp chạy ngay khi frame trước vừa xong -> CPU 100%
     * liên tục, preview giật. Tính từ lúc xong đảm bảo luôn có khoảng nghỉ >= minIntervalMs.
     */
    @Volatile private var lastCompletedAt = 0L
    @Volatile private var busy = false

    private companion object {
        /** Vùng chuyển sang bitmap = bounding box mở rộng thêm 60% mỗi phía. */
        const val REGION_PAD_RATIO = 0.6f
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            // minFaceSize càng nhỏ ML Kit càng phải quét nhiều tầng -> càng nặng. 12% của
            // khung 600 px (dọc) ~ 72 px: đúng bằng ngưỡng mặt tối thiểu để nhận diện.
            .setMinFaceSize(0.12f)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (busy || now - lastCompletedAt < minIntervalMs) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        busy = true
        val startedAt = now

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
                        // Chỉ chuyển vùng quanh mặt (mở rộng 60% mỗi phía để đủ chỗ cho phép căn
                        // chỉnh xoay/co giãn), KHÔNG chuyển cả khung hình -> nhẹ máy, hết giật.
                        val box = largest.boundingBox
                        val padX = (box.width() * REGION_PAD_RATIO).toInt()
                        val padY = (box.height() * REGION_PAD_RATIO).toInt()
                        val wanted = Rect(box.left - padX, box.top - padY, box.right + padX, box.bottom + padY)
                        val face = ImageUtils.faceRegionToBitmap(imageProxy, wanted)
                        val region = face.region

                        val leftEye = largest.getLandmark(FaceLandmark.LEFT_EYE)?.position
                        val rightEye = largest.getLandmark(FaceLandmark.RIGHT_EYE)?.position
                        val aligned = leftEye != null && rightEye != null
                        val cropped = if (aligned) {
                            ImageUtils.alignFace(
                                face.bitmap,
                                PointF(leftEye!!.x - region.left, leftEye.y - region.top),
                                PointF(rightEye!!.x - region.left, rightEye.y - region.top)
                            )
                        } else {
                            val boxInRegion = Rect(
                                box.left - region.left, box.top - region.top,
                                box.right - region.left, box.bottom - region.top
                            )
                            ImageUtils.cropAndResizeFace(face.bitmap, boxInRegion)
                        }
                        face.bitmap.recycle()
                        val leftProb = largest.leftEyeOpenProbability
                        val rightProb = largest.rightEyeOpenProbability
                        val eyeOpenProbability = when {
                            leftProb != null && rightProb != null -> (leftProb + rightProb) / 2f
                            leftProb != null -> leftProb
                            rightProb != null -> rightProb
                            else -> null
                        }
                        onFaceDetected(
                            FaceDetectionResult(
                                croppedBitmap = cropped,
                                eyeOpenProbability = eyeOpenProbability,
                                faceWidthPx = largest.boundingBox.width(),
                                aligned = aligned,
                                detectMs = System.currentTimeMillis() - startedAt
                            )
                        )
                    } catch (_: Exception) {
                        // Khung hình lỗi (mặt sát biên, crop rỗng...) — bỏ qua, không crash camera loop.
                    }
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
                lastCompletedAt = System.currentTimeMillis()
                busy = false
            }
    }
}

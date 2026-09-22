package com.dgpack.chamcong.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.dgpack.chamcong.face.ImageUtils
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/**
 * @param eyeOpenProbability trung bình 2 mắt, null nếu ML Kit không trả về được (hiếm)
 * @param faceWidthPx       bề rộng khuôn mặt trong khung hình gốc
 * @param detectMs          thời gian ML Kit phát hiện + chuẩn bị ảnh (ms)
 * @param imageWidth/Height kích thước khung hình đã xoay đúng chiều (để vẽ khung tròn đúng chỗ)
 * @param evidenceBitmap    ảnh vùng mặt thu nhỏ (~200 px) làm bằng chứng quên thẻ — chỉ có khi
 *                          [FaceAnalyzer.wantEvidence]; người nhận phải recycle.
 */
data class FaceDetectionResult(
    val eyeOpenProbability: Float?,
    val faceWidthPx: Int,
    val detectMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val evidenceBitmap: Bitmap? = null
)

/**
 * Phân tích khung hình camera bằng ML Kit: PHÁT HIỆN mặt + xác suất mắt mở (chớp mắt) — app không
 * còn nhận diện danh tính bằng khuôn mặt; camera chỉ dùng ở bước "quên thẻ" để chụp bằng chứng.
 * Throttle ~[targetFps], phát hiện trên khung thu nhỏ 1/2 cho nhẹ máy.
 *
 * @param onNoFace gọi khi khung hình không có mặt nào.
 */
class FaceAnalyzer(
    private val targetFps: Int = 3,
    private val onFaceDetected: (FaceDetectionResult) -> Unit,
    private val onNoFace: () -> Unit = {},
    /**
     * Hỏi MỖI khung: có cần ảnh bằng chứng ở khung này không. Chuyển vùng mặt sang bitmap + thu nhỏ
     * tốn ~20–40 ms trên máy yếu, chỉ làm đúng 1 khung sau khi đã chớp mắt đủ, không làm mọi khung.
     */
    private val wantEvidence: () -> Boolean = { false },
    /** Mặt nhỏ nhất ML Kit phải tìm (tỉ lệ bề rộng khung). Lớn hơn = quét ít tầng hơn = nhanh hơn. */
    private val minFaceSize: Float = 0.12f
) : ImageAnalysis.Analyzer {

    private val minIntervalMs = 1000L / targetFps
    /** Mốc HOÀN TẤT frame trước — đảm bảo luôn có khoảng nghỉ, không chạy nối đuôi 100% CPU. */
    @Volatile private var lastCompletedAt = 0L
    @Volatile private var busy = false

    private companion object {
        /** Vùng ảnh bằng chứng = bounding box mở rộng 40% mỗi phía (thấy cả tóc/cằm). */
        const val EVIDENCE_PAD_RATIO = 0.4f
        /** ML Kit phát hiện trên khung thu nhỏ từng này lần (chỉ khi khung gốc đủ rộng). */
        const val DETECT_DOWNSCALE = 2
        const val DOWNSCALE_MIN_WIDTH = 640
        /** Ảnh bằng chứng quên thẻ: 200 px bề rộng, đủ nhận ra người, ~10 KB JPEG. */
        const val EVIDENCE_WIDTH_PX = 200
    }

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(minFaceSize)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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
        val factor = if (imageProxy.width >= DOWNSCALE_MIN_WIDTH) DETECT_DOWNSCALE else 1
        val inputImage = if (factor == 1) {
            InputImage.fromMediaImage(mediaImage, rotation)
        } else {
            val small = ImageUtils.downscaledNv21(imageProxy, factor)
            InputImage.fromByteArray(small.data, small.width, small.height, rotation, InputImage.IMAGE_FORMAT_NV21)
        }
        val uprightW = if (rotation == 90 || rotation == 270) imageProxy.height else imageProxy.width
        val uprightH = if (rotation == 90 || rotation == 270) imageProxy.width else imageProxy.height

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                // Nhiều mặt trong khung -> lấy mặt lớn nhất (người đứng gần camera nhất).
                val largest = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (largest == null) {
                    onNoFace()
                } else {
                    try {
                        val bb = largest.boundingBox
                        val box = Rect(bb.left * factor, bb.top * factor, bb.right * factor, bb.bottom * factor)
                        val evidence = if (wantEvidence()) {
                            val padX = (box.width() * EVIDENCE_PAD_RATIO).toInt()
                            val padY = (box.height() * EVIDENCE_PAD_RATIO).toInt()
                            val region = ImageUtils.faceRegionToBitmap(
                                imageProxy, Rect(box.left - padX, box.top - padY, box.right + padX, box.bottom + padY)
                            )
                            val scale = EVIDENCE_WIDTH_PX.toFloat() / region.bitmap.width
                            val scaled = Bitmap.createScaledBitmap(
                                region.bitmap, EVIDENCE_WIDTH_PX, (region.bitmap.height * scale).toInt().coerceAtLeast(1), true
                            )
                            region.bitmap.recycle()
                            scaled
                        } else null
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
                                eyeOpenProbability = eyeOpenProbability,
                                faceWidthPx = box.width(),
                                detectMs = System.currentTimeMillis() - startedAt,
                                imageWidth = uprightW,
                                imageHeight = uprightH,
                                evidenceBitmap = evidence
                            )
                        )
                    } catch (_: Exception) {
                        // Khung hình lỗi (mặt sát biên, vùng rỗng...) — bỏ qua, không crash camera loop.
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

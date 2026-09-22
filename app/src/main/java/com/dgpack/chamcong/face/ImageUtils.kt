package com.dgpack.chamcong.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import kotlin.math.atan2
import kotlin.math.hypot

object ImageUtils {

    /**
     * Chuyển ImageProxy (định dạng YUV_420_888 từ CameraX ImageAnalysis) sang Bitmap
     * đã xoay đúng chiều theo rotationDegrees. Cách làm thủ công qua NV21 + YuvImage
     * để tương thích ổn định với mọi thiết bị (không phụ thuộc API mới/chưa chắc có).
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val nv21 = yuv420888ToNv21(imageProxy)
        val yuvImage = YuvImage(nv21, android.graphics.ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 95, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) rotateBitmap(bitmap, rotation) else bitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * YUV_420_888 -> NV21 (Y liền, rồi V/U xen kẽ) đúng kích thước width*height*3/2.
     *
     * Bản cũ copy nguyên buffer Y (`buffer.remaining()`) và coi rowStride == width. Trên nhiều
     * tablet giá rẻ (MediaTek/Unisoc, vd MFISO B1 PRO) rowStride > width (mỗi hàng có byte
     * đệm) nên ảnh bị "xé" chéo, và phần chroma bị lệch offset -> màu sai. ML Kit đọc thẳng
     * từ Image nên bounding box vẫn đúng, còn bitmap ta crop lại méo -> embedding của mọi
     * người na ná nhau -> NHẬN NHẦM NGƯỜI. Vì vậy phải copy từng hàng theo rowStride.
     * Cũng rewind() buffer trước khi đọc vì ML Kit có thể đã dịch position.
     */
    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val nv21 = ByteArray(width * height * 3 / 2)

        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer.duplicate().also { it.rewind() }
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        if (yPixelStride == 1) {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }
        } else {
            for (row in 0 until height) {
                val rowStart = row * yRowStride
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(rowStart + col * yPixelStride)
                }
            }
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuffer = uPlane.buffer.duplicate().also { it.rewind() }
        val vBuffer = vPlane.buffer.duplicate().also { it.rewind() }
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uLimit = uBuffer.limit()
        val vLimit = vBuffer.limit()
        for (row in 0 until chromaHeight) {
            val vRow = row * vRowStride
            val uRow = row * uRowStride
            for (col in 0 until chromaWidth) {
                val vIndex = vRow + col * vPixelStride
                val uIndex = uRow + col * uPixelStride
                nv21[pos++] = if (vIndex < vLimit) vBuffer.get(vIndex) else 0
                nv21[pos++] = if (uIndex < uLimit) uBuffer.get(uIndex) else 0
            }
        }
        return nv21
    }

    /**
     * Crop vùng khuôn mặt (theo boundingBox ML Kit trả về) từ ảnh gốc, thêm margin,
     * clamp trong biên ảnh, rồi resize đúng kích thước input của model (112x112).
     * Dùng làm phương án dự phòng khi ML Kit không trả về vị trí 2 mắt (xem [alignFace]).
     */
    fun cropAndResizeFace(source: Bitmap, boundingBox: Rect, marginRatio: Float = 0.15f): Bitmap {
        val marginX = (boundingBox.width() * marginRatio).toInt()
        val marginY = (boundingBox.height() * marginRatio).toInt()

        val left = (boundingBox.left - marginX).coerceIn(0, source.width - 1)
        val top = (boundingBox.top - marginY).coerceIn(0, source.height - 1)
        val right = (boundingBox.right + marginX).coerceIn(left + 1, source.width)
        val bottom = (boundingBox.bottom + marginY).coerceIn(top + 1, source.height)

        val cropped = Bitmap.createBitmap(source, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, FaceEmbedder.INPUT_SIZE, FaceEmbedder.INPUT_SIZE, true)
    }

    // Mẫu căn chỉnh chuẩn ArcFace/InsightFace cho ảnh 112x112: vị trí 2 mắt (trái ảnh, phải ảnh).
    // MobileFaceNet được huấn luyện trên ảnh đã căn theo mẫu này — đưa mắt về đúng chỗ làm
    // embedding phân biệt người tốt hơn hẳn so với crop thô theo bounding box.
    private const val TEMPLATE_LEFT_EYE_X = 38.2946f
    private const val TEMPLATE_LEFT_EYE_Y = 51.6963f
    private const val TEMPLATE_RIGHT_EYE_X = 73.5318f
    private const val TEMPLATE_RIGHT_EYE_Y = 51.5014f

    /**
     * Căn chỉnh khuôn mặt bằng phép biến đổi đồng dạng (xoay + co giãn + tịnh tiến) đưa 2 mắt
     * về đúng vị trí mẫu ArcFace trong khung 112x112. Kết quả: mặt nghiêng đầu, đứng lệch,
     * xa/gần đều được đưa về cùng một khung so sánh.
     *
     * [eyeA]/[eyeB] là toạ độ 2 mắt trong hệ toạ độ của [source] (ảnh đã xoay đúng chiều,
     * cùng hệ với bounding box ML Kit). Không cần biết mắt nào là trái/phải của người —
     * hàm tự lấy mắt có x nhỏ hơn làm "mắt bên trái ảnh" nên camera trước có gương hay không
     * đều đúng.
     */
    fun alignFace(source: Bitmap, eyeA: PointF, eyeB: PointF): Bitmap {
        val (leftEye, rightEye) = if (eyeA.x <= eyeB.x) eyeA to eyeB else eyeB to eyeA

        val srcDx = rightEye.x - leftEye.x
        val srcDy = rightEye.y - leftEye.y
        val srcDist = hypot(srcDx, srcDy)
        require(srcDist > 1f) { "2 mắt trùng nhau" }

        val dstDx = TEMPLATE_RIGHT_EYE_X - TEMPLATE_LEFT_EYE_X
        val dstDy = TEMPLATE_RIGHT_EYE_Y - TEMPLATE_LEFT_EYE_Y
        val dstDist = hypot(dstDx, dstDy)

        val scale = dstDist / srcDist
        val angleDeg = Math.toDegrees(
            (atan2(dstDy, dstDx) - atan2(srcDy, srcDx)).toDouble()
        ).toFloat()

        val matrix = Matrix().apply {
            // Đưa mắt trái ảnh về gốc, xoay + co giãn quanh gốc, rồi đẩy tới vị trí mẫu.
            postTranslate(-leftEye.x, -leftEye.y)
            postRotate(angleDeg)
            postScale(scale, scale)
            postTranslate(TEMPLATE_LEFT_EYE_X, TEMPLATE_LEFT_EYE_Y)
        }

        val out = Bitmap.createBitmap(FaceEmbedder.INPUT_SIZE, FaceEmbedder.INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }
}

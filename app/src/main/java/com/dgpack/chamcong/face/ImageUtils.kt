package com.dgpack.chamcong.face

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import kotlin.math.atan2
import kotlin.math.hypot

object ImageUtils {

    /**
     * Kết quả [faceRegionToBitmap]: [bitmap] là vùng [region] (toạ độ trong ảnh ĐÃ XOAY đúng
     * chiều, cùng hệ với bounding box / landmark ML Kit trả về), đã xoay đúng chiều.
     */
    class FaceRegion(val bitmap: Bitmap, val region: Rect)

    /**
     * Chuyển CHỈ vùng khuôn mặt của khung hình YUV_420_888 sang Bitmap ARGB, không qua JPEG.
     *
     * Bản cũ nén CẢ khung 1280x720 thành JPEG rồi giải nén, rồi xoay cả ảnh (cấp phát ~3.6 MB
     * mỗi lần) chỉ để lấy vùng mặt vài trăm px -> trên tablet yếu mỗi frame mất hàng trăm ms,
     * camera giật. Giờ chỉ đọc đúng số pixel của vùng mặt (ít hơn 10–30 lần), chuyển YUV->RGB
     * trực tiếp, xoay bitmap nhỏ. Đọc plane theo rowStride/pixelStride nên máy có byte đệm
     * mỗi hàng (MediaTek/Unisoc) vẫn đúng.
     *
     * @param regionRotated vùng cần lấy trong hệ toạ độ ảnh đã xoay (vd bounding box mở rộng).
     */
    fun faceRegionToBitmap(imageProxy: ImageProxy, regionRotated: Rect): FaceRegion {
        val rotation = imageProxy.imageInfo.rotationDegrees
        val w = imageProxy.width
        val h = imageProxy.height
        val uprightW = if (rotation == 90 || rotation == 270) h else w
        val uprightH = if (rotation == 90 || rotation == 270) w else h

        // Clamp trong ảnh đã xoay, ép toạ độ/kích thước chẵn để khớp lưới chroma 2x2.
        val left = (regionRotated.left.coerceIn(0, uprightW - 2)) and 1.inv()
        val top = (regionRotated.top.coerceIn(0, uprightH - 2)) and 1.inv()
        val right = (regionRotated.right.coerceIn(left + 2, uprightW)) and 1.inv()
        val bottom = (regionRotated.bottom.coerceIn(top + 2, uprightH)) and 1.inv()
        val region = Rect(left, top, right, bottom)

        // Ánh xạ ngược vùng đã xoay về toạ độ gốc của cảm biến.
        val orig = when (rotation) {
            90 -> Rect(top, h - right, bottom, h - left)
            180 -> Rect(w - right, h - bottom, w - left, h - top)
            270 -> Rect(w - bottom, left, w - top, right)
            else -> Rect(left, top, right, bottom)
        }

        val pixels = yuvRegionToArgb(imageProxy, orig)
        var bitmap = Bitmap.createBitmap(pixels, orig.width(), orig.height(), Bitmap.Config.ARGB_8888)
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            bitmap = rotated
        }
        return FaceRegion(bitmap, region)
    }

    /** YUV_420_888 -> ARGB cho vùng [rect] (toạ độ gốc cảm biến, đã chẵn). BT.601 full-range. */
    private fun yuvRegionToArgb(image: ImageProxy, rect: Rect): IntArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        // duplicate() + rewind(): ML Kit có thể đã dịch position của buffer gốc.
        val yBuf = yPlane.buffer.duplicate().also { it.rewind() }
        val uBuf = uPlane.buffer.duplicate().also { it.rewind() }
        val vBuf = vPlane.buffer.duplicate().also { it.rewind() }
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val uRow = uPlane.rowStride
        val uPix = uPlane.pixelStride
        val vRow = vPlane.rowStride
        val vPix = vPlane.pixelStride
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()
        val yLimit = yBuf.limit()

        val width = rect.width()
        val height = rect.height()
        val out = IntArray(width * height)
        var i = 0
        for (row in 0 until height) {
            val sy = rect.top + row
            val yBase = sy * yRow
            val cBaseU = (sy / 2) * uRow
            val cBaseV = (sy / 2) * vRow
            for (col in 0 until width) {
                val sx = rect.left + col
                val yi = yBase + sx * yPix
                val ui = cBaseU + (sx / 2) * uPix
                val vi = cBaseV + (sx / 2) * vPix
                val y = if (yi < yLimit) yBuf.get(yi).toInt() and 0xFF else 0
                val u = (if (ui < uLimit) uBuf.get(ui).toInt() and 0xFF else 128) - 128
                val v = (if (vi < vLimit) vBuf.get(vi).toInt() and 0xFF else 128) - 128
                // Hệ số nhân 1024 để tính bằng số nguyên.
                val r = (y + ((1436 * v) shr 10)).coerceIn(0, 255)
                val g = (y - ((352 * u + 731 * v) shr 10)).coerceIn(0, 255)
                val b = (y + ((1815 * u) shr 10)).coerceIn(0, 255)
                out[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /** Khung NV21 đã thu nhỏ để đưa vào ML Kit (toạ độ gốc cảm biến, chưa xoay). */
    class Nv21Frame(val data: ByteArray, val width: Int, val height: Int)

    /**
     * Tạo khung NV21 thu nhỏ [factor] lần bằng cách lấy mẫu thưa (mỗi [factor] pixel lấy 1) —
     * ~150k phép copy cho 800x600/2, vài ms. ML Kit chỉ cần TÌM mặt, không cần nét; chạy trên
     * ảnh 400x300 nhanh ~4 lần so với 800x600 (trên MFISO B1 PRO đo được 1100 ms/khung ở
     * 800x600). Vị trí mặt/mắt trả về nhân lại [factor] để crop từ khung gốc đầy đủ.
     */
    fun downscaledNv21(image: ImageProxy, factor: Int): Nv21Frame {
        val w = (image.width / factor) and 1.inv()
        val h = (image.height / factor) and 1.inv()
        val out = ByteArray(w * h * 3 / 2)

        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer.duplicate().also { it.rewind() }
        val yRow = yPlane.rowStride
        val yPix = yPlane.pixelStride
        val yLimit = yBuf.limit()
        var pos = 0
        for (row in 0 until h) {
            val srcRow = row * factor * yRow
            for (col in 0 until w) {
                val i = srcRow + col * factor * yPix
                out[pos++] = if (i < yLimit) yBuf.get(i) else 0
            }
        }

        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuf = uPlane.buffer.duplicate().also { it.rewind() }
        val vBuf = vPlane.buffer.duplicate().also { it.rewind() }
        val uLimit = uBuf.limit()
        val vLimit = vBuf.limit()
        val ch = h / 2
        val cw = w / 2
        for (row in 0 until ch) {
            val vRowIdx = row * factor * vPlane.rowStride
            val uRowIdx = row * factor * uPlane.rowStride
            for (col in 0 until cw) {
                val vi = vRowIdx + col * factor * vPlane.pixelStride
                val ui = uRowIdx + col * factor * uPlane.pixelStride
                out[pos++] = if (vi < vLimit) vBuf.get(vi) else 0
                out[pos++] = if (ui < uLimit) uBuf.get(ui) else 0
            }
        }
        return Nv21Frame(out, w, h)
    }

    /**
     * Đo độ nét (phương sai Laplacian 4 láng giềng) và độ sáng trung bình trên ảnh xám của
     * [bitmap] 112x112 — ~12k pixel nên rất rẻ, dùng cho FaceQualityChecker lúc đăng ký.
     * @return Pair(sharpness, brightness)
     */
    fun measureSharpnessAndBrightness(bitmap: Bitmap): Pair<Float, Float> {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h)
        var sum = 0L
        for (i in px.indices) {
            val p = px[i]
            val g = (((p shr 16) and 0xFF) * 77 + ((p shr 8) and 0xFF) * 150 + (p and 0xFF) * 29) shr 8
            gray[i] = g
            sum += g
        }
        val brightness = sum.toFloat() / px.size

        var lapSum = 0.0
        var lapSqSum = 0.0
        var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val l = 4 * gray[i] - gray[i - 1] - gray[i + 1] - gray[i - w] - gray[i + w]
                lapSum += l
                lapSqSum += l.toDouble() * l
                n++
            }
        }
        if (n == 0) return 0f to brightness
        val mean = lapSum / n
        val variance = lapSqSum / n - mean * mean
        return variance.toFloat() to brightness
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
     * [eyeA]/[eyeB] là toạ độ 2 mắt trong hệ toạ độ của [source]. Không cần biết mắt nào là
     * trái/phải của người — hàm tự lấy mắt có x nhỏ hơn làm "mắt bên trái ảnh" nên camera
     * trước có gương hay không đều đúng.
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

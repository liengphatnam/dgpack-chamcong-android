package com.dgpack.chamcong.face

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

/**
 * Chuyển đổi khung hình YUV_420_888 của CameraX — chỉ còn 2 việc: thu nhỏ để ML Kit tìm mặt,
 * và cắt vùng mặt làm ảnh bằng chứng quên thẻ. Luôn đọc plane theo rowStride/pixelStride
 * (tablet giá rẻ MediaTek/Unisoc có byte đệm mỗi hàng).
 */
object ImageUtils {

    /** Vùng [region] (toạ độ ảnh ĐÃ XOAY, cùng hệ với ML Kit) đã chuyển sang bitmap ARGB xoay đúng chiều. */
    class FaceRegion(val bitmap: Bitmap, val region: Rect)

    /** Khung NV21 đã thu nhỏ để đưa vào ML Kit (toạ độ gốc cảm biến, chưa xoay). */
    class Nv21Frame(val data: ByteArray, val width: Int, val height: Int)

    /** Chuyển CHỈ vùng mặt sang Bitmap ARGB (không qua JPEG, không chuyển cả khung). */
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
                val r = (y + ((1436 * v) shr 10)).coerceIn(0, 255)
                val g = (y - ((352 * u + 731 * v) shr 10)).coerceIn(0, 255)
                val b = (y + ((1815 * u) shr 10)).coerceIn(0, 255)
                out[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return out
    }

    /**
     * Khung NV21 thu nhỏ [factor] lần bằng lấy mẫu thưa — vài ms. ML Kit chỉ cần TÌM mặt, không
     * cần nét; chạy trên ảnh 1/2 nhanh ~4 lần. Toạ độ trả về nhân lại [factor].
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
}

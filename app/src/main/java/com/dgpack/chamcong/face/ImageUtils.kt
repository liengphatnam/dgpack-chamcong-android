package com.dgpack.chamcong.face

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

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
        yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 90, out)
        val jpegBytes = out.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        val rotation = imageProxy.imageInfo.rotationDegrees
        return if (rotation != 0) rotateBitmap(bitmap, rotation) else bitmap
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = yPlane.buffer.remaining()
        val uSize = uPlane.buffer.remaining()
        val vSize = vPlane.buffer.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)

        yPlane.buffer.get(nv21, 0, ySize)

        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        // Trường hợp phổ biến (pixelStride=2) cần xen kẽ V/U thủ công cho đúng NV21.
        var pos = ySize
        val chromaHeight = image.height / 2
        val chromaWidth = image.width / 2
        val vArray = ByteArray(vBuffer.remaining())
        vBuffer.get(vArray)
        val uArray = ByteArray(uBuffer.remaining())
        uBuffer.get(uArray)
        for (row in 0 until chromaHeight) {
            for (col in 0 until chromaWidth) {
                val vIndex = row * vRowStride + col * vPixelStride
                val uIndex = row * uRowStride + col * uPixelStride
                if (pos + 1 < nv21.size && vIndex < vArray.size && uIndex < uArray.size) {
                    nv21[pos++] = vArray[vIndex]
                    nv21[pos++] = uArray[uIndex]
                }
            }
        }
        return nv21
    }

    /**
     * Crop vùng khuôn mặt (theo boundingBox ML Kit trả về) từ ảnh gốc, thêm margin,
     * clamp trong biên ảnh, rồi resize đúng kích thước input của model (112x112).
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
}

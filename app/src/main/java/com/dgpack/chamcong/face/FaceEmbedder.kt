package com.dgpack.chamcong.face

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Wrapper cho model TFLite MobileFaceNet (xem app/src/main/assets/MODEL_LICENSE.txt).
 * Input:  float32 [1,112,112,3], chuẩn hoá (pixel-128)/128.
 * Output: float32 [1,192] — vector embedding, sau đó được L2-normalize để so khớp
 *         bằng cosine similarity (mục [4.1] tài liệu).
 */
class FaceEmbedder(context: Context) : Closeable {

    companion object {
        const val INPUT_SIZE = 112
        const val EMBEDDING_SIZE = 192
        private const val MODEL_FILE = "mobilefacenet.tflite"
    }

    private val interpreter: Interpreter

    init {
        val model = FileUtil.loadMappedFile(context, MODEL_FILE)
        interpreter = Interpreter(model, Interpreter.Options().apply { setNumThreads(4) })
    }

    /** [bitmap] phải đã được crop quanh khuôn mặt và resize đúng 112x112 trước khi gọi. */
    fun embed(bitmap: Bitmap): FloatArray {
        require(bitmap.width == INPUT_SIZE && bitmap.height == INPUT_SIZE) {
            "Bitmap đầu vào phải đúng ${INPUT_SIZE}x${INPUT_SIZE}"
        }
        val inputBuffer = bitmapToInputBuffer(bitmap)
        val output = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interpreter.run(inputBuffer, output)
        return l2Normalize(output[0])
    }

    private fun bitmapToInputBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat((r - 128) / 128f)
            buffer.putFloat((g - 128) / 128f)
            buffer.putFloat((b - 128) / 128f)
        }
        buffer.rewind()
        return buffer
    }

    private fun l2Normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) sumSquares += v.toDouble() * v.toDouble()
        val norm = sqrt(sumSquares).toFloat()
        if (norm < 1e-6f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    override fun close() {
        interpreter.close()
    }
}

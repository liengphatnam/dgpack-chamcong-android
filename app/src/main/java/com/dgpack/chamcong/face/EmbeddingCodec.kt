package com.dgpack.chamcong.face

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Serialize/deserialize FloatArray embedding <-> BLOB để lưu trong Room. */
object EmbeddingCodec {

    fun toByteArray(embedding: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(4 * embedding.size).order(ByteOrder.nativeOrder())
        for (v in embedding) buffer.putFloat(v)
        return buffer.array()
    }

    fun fromByteArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        val size = bytes.size / 4
        return FloatArray(size) { buffer.float }
    }
}

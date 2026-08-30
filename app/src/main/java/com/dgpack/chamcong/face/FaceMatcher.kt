package com.dgpack.chamcong.face

data class EnrolledFace(val employeeCode: String, val embedding: FloatArray)

data class MatchResult(val employeeCode: String, val similarity: Float)

/**
 * So khớp embedding truy vấn với toàn bộ danh sách đã enroll bằng cosine similarity
 * (mục [4.1]). Danh sách enrolled được nạp sẵn vào RAM lúc khởi động app để so khớp
 * nhanh (không query DB mỗi frame).
 */
object FaceMatcher {

    /** Cả 2 vector nên đã được L2-normalize trước (xem FaceEmbedder.embed) để phép tính
     * chỉ còn là tích vô hướng, nhưng hàm này vẫn tự chia norm để an toàn nếu đầu vào chưa chuẩn hoá. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "2 vector phải cùng số chiều" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i].toDouble() * b[i].toDouble()
            normA += a[i].toDouble() * a[i].toDouble()
            normB += b[i].toDouble() * b[i].toDouble()
        }
        if (normA < 1e-12 || normB < 1e-12) return 0f
        return (dot / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB))).toFloat()
    }

    /**
     * Trả về mã NV có similarity cao nhất VÀ vượt ngưỡng [threshold], hoặc null nếu
     * không ai đạt ngưỡng (bình thường — người đi ngang không phải để chấm công).
     */
    fun findBestMatch(
        query: FloatArray,
        enrolled: List<EnrolledFace>,
        threshold: Float
    ): MatchResult? {
        var best: MatchResult? = null
        for (candidate in enrolled) {
            val sim = cosineSimilarity(query, candidate.embedding)
            if (best == null || sim > best.similarity) {
                best = MatchResult(candidate.employeeCode, sim)
            }
        }
        return best?.takeIf { it.similarity >= threshold }
    }

    /** Trung bình nhiều embedding (từ 3-5 ảnh enroll) rồi re-normalize (mục [4.2]). */
    fun averageEmbedding(embeddings: List<FloatArray>): FloatArray {
        require(embeddings.isNotEmpty()) { "Cần ít nhất 1 embedding" }
        val size = embeddings.first().size
        val sum = FloatArray(size)
        for (emb in embeddings) {
            for (i in 0 until size) sum[i] += emb[i]
        }
        for (i in 0 until size) sum[i] /= embeddings.size
        var normSq = 0.0
        for (v in sum) normSq += v.toDouble() * v.toDouble()
        val norm = kotlin.math.sqrt(normSq).toFloat()
        if (norm < 1e-6f) return sum
        return FloatArray(size) { sum[it] / norm }
    }
}

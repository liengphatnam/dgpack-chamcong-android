package com.dgpack.chamcong.face

import kotlin.math.roundToInt

data class EnrolledFace(val employeeCode: String, val embedding: FloatArray)

/**
 * Kết quả so khớp: người có similarity cao nhất trong danh sách enroll.
 * [confidencePercent] là similarity quy đổi sang thang 0–100% để hiển thị cho người
 * dùng và so với ngưỡng "độ tin cậy tối thiểu" cấu hình ở Cài đặt (mặc định 80%).
 */
data class MatchResult(val employeeCode: String, val similarity: Float) {
    val confidencePercent: Int
        get() = FaceMatcher.toConfidencePercent(similarity)

    /** Quy tắc nghiệp vụ: chỉ coi là nhận diện ĐƯỢC khi độ tin cậy >= [minPercent]. */
    fun isConfident(minPercent: Int): Boolean = confidencePercent >= minPercent
}

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
     * Quy đổi cosine similarity (-1..1) sang % độ tin cậy (0..100). Similarity âm coi
     * như 0% — về nghiệp vụ không có "tin cậy âm". Đây là thang tuyến tính đơn giản,
     * đủ để admin cấu hình và đọc hiểu; ngưỡng mặc định 80% ~ similarity 0.80.
     */
    fun toConfidencePercent(similarity: Float): Int =
        (similarity.coerceIn(0f, 1f) * 100f).roundToInt()

    /**
     * Trả về người có similarity cao nhất (KHÔNG áp ngưỡng), hoặc null nếu chưa enroll ai.
     * Caller tự kiểm tra [MatchResult.isConfident] để quyết định nhận diện được hay
     * chưa — tách ra như vậy để UI có thể hiện "chưa nhận dạng được (xx%)" khi chưa đủ
     * độ tin cậy thay vì im lặng.
     */
    fun findBestMatch(query: FloatArray, enrolled: List<EnrolledFace>): MatchResult? {
        var best: MatchResult? = null
        for (candidate in enrolled) {
            val sim = cosineSimilarity(query, candidate.embedding)
            if (best == null || sim > best.similarity) {
                best = MatchResult(candidate.employeeCode, sim)
            }
        }
        return best
    }

    /**
     * Phiên bản có ngưỡng similarity thô (0..1): trả về null nếu không ai đạt ngưỡng.
     * Giữ lại cho test/tương thích; luồng chấm công dùng [findBestMatch] + [MatchResult.isConfident].
     */
    fun findBestMatch(
        query: FloatArray,
        enrolled: List<EnrolledFace>,
        threshold: Float
    ): MatchResult? = findBestMatch(query, enrolled)?.takeIf { it.similarity >= threshold }

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

package com.dgpack.chamcong.face

/**
 * Chống giả mạo ĐƠN GIẢN (Phase 2, mục [12]): yêu cầu phát hiện đủ 1 nhịp chớp mắt
 * (mắt mở -> nhắm -> mở lại) trước khi xác nhận chấm công — chặn được ảnh tĩnh in giấy,
 * KHÔNG chống được video giả mạo tinh vi hay ảnh động lặp mắt nhắm/mở.
 *
 * Dùng leftEyeOpenProbability/rightEyeOpenProbability có sẵn của ML Kit
 * (cần setClassificationMode(CLASSIFICATION_MODE_ALL) — xem FaceAnalyzer), không cần
 * model/thư viện thêm.
 */
class LivenessTracker(
    private val eyeClosedThreshold: Float = 0.3f,
    private val eyeOpenThreshold: Float = 0.6f
) {
    private enum class State { WAIT_OPEN, WAIT_CLOSED, WAIT_OPEN_AGAIN, CONFIRMED }

    private var state = State.WAIT_OPEN

    val isConfirmed: Boolean get() = state == State.CONFIRMED

    /** [probability] là trung bình leftEyeOpenProbability/rightEyeOpenProbability của 1 frame. */
    fun onEyeOpenSample(probability: Float) {
        when (state) {
            State.WAIT_OPEN -> if (probability >= eyeOpenThreshold) state = State.WAIT_CLOSED
            State.WAIT_CLOSED -> if (probability <= eyeClosedThreshold) state = State.WAIT_OPEN_AGAIN
            State.WAIT_OPEN_AGAIN -> if (probability >= eyeOpenThreshold) state = State.CONFIRMED
            State.CONFIRMED -> Unit
        }
    }
}

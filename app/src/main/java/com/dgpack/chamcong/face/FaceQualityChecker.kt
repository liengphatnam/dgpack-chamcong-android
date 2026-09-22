package com.dgpack.chamcong.face

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Tư thế yêu cầu ở từng bước đăng ký (giống eKYC ngân hàng: nhìn thẳng, quay nhẹ 2 bên). */
enum class EnrollPose { FRONT, SIDE_A, SIDE_B }

/** Gợi ý ưu tiên cao nhất cho người đứng trước camera. */
enum class QualityHint {
    NO_FACE, MULTIPLE_FACES, MOVE_INTO_FRAME, MOVE_CLOSER, MOVE_BACK,
    LOOK_STRAIGHT, TURN_SIDE, TURN_OTHER_WAY, TURN_LESS,
    TOO_DARK, TOO_BRIGHT, BLURRY, OPEN_EYES, GOOD
}

/** Số đo của 1 khung hình có mặt — thuần số, không phụ thuộc Android để test được. */
data class FaceFrameInfo(
    val imageWidth: Int,
    val imageHeight: Int,
    val faceLeft: Int,
    val faceTop: Int,
    val faceRight: Int,
    val faceBottom: Int,
    val faceCount: Int,
    /** Góc đầu (độ) theo ML Kit: yaw = quay trái/phải, pitch = ngẩng/cúi, roll = nghiêng. */
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val eyeOpenProbability: Float?,
    /** Phương sai Laplacian trên ảnh xám 112x112 đã căn — càng cao càng nét. */
    val sharpness: Float,
    /** Độ sáng trung bình ảnh xám 0..255. */
    val brightness: Float
)

data class QualityResult(
    /** 0..100, chỉ = 100 khi [passed]. */
    val percent: Int,
    val hint: QualityHint,
    val passed: Boolean
)

/**
 * Chấm điểm chất lượng khung hình để đăng ký khuôn mặt, kiểu eKYC ngân hàng: mặt phải nằm
 * trong khung tròn, đủ lớn, nhìn đúng tư thế yêu cầu, đủ sáng, không mờ, mở mắt, chỉ 1 người.
 * Mỗi tiêu chí cho điểm 0..1 (dốc mềm để % tăng dần khi người dùng chỉnh), [QualityResult.passed]
 * chỉ true khi MỌI tiêu chí đạt ngưỡng cứng. Gợi ý = tiêu chí điểm thấp nhất.
 *
 * Khung oval định nghĩa trong toạ độ CHUẨN HOÁ của khung hình phân tích (0..1) để UI vẽ
 * đúng chỗ dù preview co giãn kiểu gì (xem EnrollScreen.FaceOvalOverlay).
 */
object FaceQualityChecker {
    const val OVAL_CX = 0.5f
    const val OVAL_CY = 0.45f
    /** Khung là HÌNH TRÒN: bán kính = [OVAL_R] x bề rộng khung hình (cả 2 trục cùng 1 bán kính). */
    const val OVAL_R = 0.32f

    /** Bề rộng mặt / bề rộng khung hình: đạt khi trong [MIN_FACE_RATIO, MAX_FACE_RATIO]. */
    const val MIN_FACE_RATIO = 0.22f
    const val MAX_FACE_RATIO = 0.62f
    /** Tâm mặt cách tâm oval (đơn vị bán kính oval) tối đa từng này thì coi là "trong khung". */
    const val MAX_CENTER_OFFSET = 0.5f

    const val FRONT_MAX_YAW = 12f
    const val SIDE_MIN_YAW = 12f
    const val SIDE_MAX_YAW = 40f
    const val MAX_PITCH = 15f
    const val MAX_ROLL = 15f

    const val MIN_BRIGHTNESS = 70f
    const val MAX_BRIGHTNESS = 190f
    /** Phương sai Laplacian tối thiểu trên ảnh 112x112; đạt tối đa điểm ở [SHARP_FULL]. Chỉnh sau khi đo thực tế. */
    const val MIN_SHARPNESS = 40f
    const val SHARP_FULL = 100f
    const val MIN_EYE_OPEN = 0.5f

    /**
     * @param pose          tư thế bước hiện tại
     * @param requiredSign  với [EnrollPose.SIDE_B]: dấu của yaw bắt buộc (ngược với bước SIDE_A đã chụp); null = bên nào cũng được
     */
    fun evaluate(frame: FaceFrameInfo, pose: EnrollPose, requiredSign: Int? = null): QualityResult {
        val scores = ArrayList<Pair<Float, QualityHint>>(7)

        // 1. Chỉ một người trong khung
        scores += if (frame.faceCount == 1) 1f to QualityHint.GOOD else 0f to QualityHint.MULTIPLE_FACES

        // 2. Kích thước mặt
        val faceW = (frame.faceRight - frame.faceLeft).toFloat()
        val ratio = faceW / frame.imageWidth
        scores += when {
            ratio < MIN_FACE_RATIO -> ramp(ratio, MIN_FACE_RATIO * 0.5f, MIN_FACE_RATIO) to QualityHint.MOVE_CLOSER
            ratio > MAX_FACE_RATIO -> ramp(-ratio, -(MAX_FACE_RATIO + 0.15f), -MAX_FACE_RATIO) to QualityHint.MOVE_BACK
            else -> 1f to QualityHint.GOOD
        }

        // 3. Mặt nằm trong oval
        val cx = (frame.faceLeft + frame.faceRight) / 2f / frame.imageWidth
        val cy = (frame.faceTop + frame.faceBottom) / 2f / frame.imageHeight
        val radiusPx = OVAL_R * frame.imageWidth
        val dx = (cx - OVAL_CX) * frame.imageWidth / radiusPx
        val dy = (cy - OVAL_CY) * frame.imageHeight / radiusPx
        val dist = sqrt(dx * dx + dy * dy)
        scores += if (dist <= MAX_CENTER_OFFSET) 1f to QualityHint.GOOD
        else ramp(-dist, -1.2f, -MAX_CENTER_OFFSET) to QualityHint.MOVE_INTO_FRAME

        // 4. Tư thế đầu
        scores += poseScore(frame, pose, requiredSign)

        // 5. Độ sáng
        scores += when {
            frame.brightness < MIN_BRIGHTNESS -> ramp(frame.brightness, 30f, MIN_BRIGHTNESS) to QualityHint.TOO_DARK
            frame.brightness > MAX_BRIGHTNESS -> ramp(-frame.brightness, -235f, -MAX_BRIGHTNESS) to QualityHint.TOO_BRIGHT
            else -> 1f to QualityHint.GOOD
        }

        // 6. Độ nét
        scores += if (frame.sharpness >= MIN_SHARPNESS) 1f to QualityHint.GOOD
        else ramp(frame.sharpness, 0f, MIN_SHARPNESS) * 0.99f to QualityHint.BLURRY

        // 7. Mắt mở
        val eye = frame.eyeOpenProbability
        scores += if (eye == null || eye >= MIN_EYE_OPEN) 1f to QualityHint.GOOD
        else ramp(eye, 0f, MIN_EYE_OPEN) * 0.99f to QualityHint.OPEN_EYES

        val passed = scores.all { it.first >= 1f }
        val avg = scores.sumOf { it.first.toDouble() } / scores.size
        // Điểm nét/mắt được nhân 0.99 để chưa đạt thì không bao giờ bị coi là 1.0.
        val percent = if (passed) 100 else (avg * 100).roundToInt().coerceIn(0, 99)
        val worst = scores.minByOrNull { it.first }!!
        val hint = if (passed) QualityHint.GOOD else worst.second
        return QualityResult(percent, hint, passed)
    }

    private fun poseScore(frame: FaceFrameInfo, pose: EnrollPose, requiredSign: Int?): Pair<Float, QualityHint> {
        val pitchOk = abs(frame.pitch) <= MAX_PITCH
        val rollOk = abs(frame.roll) <= MAX_ROLL
        val tiltScore = minOf(
            ramp(-abs(frame.pitch), -MAX_PITCH * 2, -MAX_PITCH),
            ramp(-abs(frame.roll), -MAX_ROLL * 2, -MAX_ROLL)
        )
        val yawAbs = abs(frame.yaw)
        return when (pose) {
            EnrollPose.FRONT -> {
                val yawScore = ramp(-yawAbs, -FRONT_MAX_YAW * 2, -FRONT_MAX_YAW)
                val s = minOf(yawScore, tiltScore)
                if (s >= 1f && pitchOk && rollOk) 1f to QualityHint.GOOD else s to QualityHint.LOOK_STRAIGHT
            }
            EnrollPose.SIDE_A, EnrollPose.SIDE_B -> {
                val sign = if (frame.yaw >= 0) 1 else -1
                when {
                    yawAbs < SIDE_MIN_YAW -> {
                        val hint = if (pose == EnrollPose.SIDE_B && requiredSign != null) QualityHint.TURN_OTHER_WAY else QualityHint.TURN_SIDE
                        (ramp(yawAbs, 0f, SIDE_MIN_YAW) * 0.99f) to hint
                    }
                    requiredSign != null && sign != requiredSign -> 0.3f to QualityHint.TURN_OTHER_WAY
                    yawAbs > SIDE_MAX_YAW -> ramp(-yawAbs, -(SIDE_MAX_YAW + 20f), -SIDE_MAX_YAW) to QualityHint.TURN_LESS
                    !pitchOk || !rollOk -> tiltScore to QualityHint.LOOK_STRAIGHT
                    else -> 1f to QualityHint.GOOD
                }
            }
        }
    }

    /** 0 tại [lo], 1 tại [hi], tuyến tính ở giữa. */
    private fun ramp(v: Float, lo: Float, hi: Float): Float =
        ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
}

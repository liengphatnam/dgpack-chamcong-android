package com.dgpack.chamcong

import com.dgpack.chamcong.face.EnrollPose
import com.dgpack.chamcong.face.FaceFrameInfo
import com.dgpack.chamcong.face.FaceQualityChecker
import com.dgpack.chamcong.face.QualityHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chấm điểm chất lượng ảnh đăng ký (eKYC): chỉ 100% khi mọi tiêu chí đạt, gợi ý đúng
 * tiêu chí yếu nhất, tư thế quay trái/phải theo đúng kịch bản.
 */
class FaceQualityCheckerTest {

    /** Khung 600x800, mặt 200 px ở đúng tâm oval, nhìn thẳng, đủ sáng, nét, mở mắt, 1 người. */
    private fun good(
        faceW: Int = 200,
        cx: Float = 0.5f,
        cy: Float = 0.45f,
        faceCount: Int = 1,
        yaw: Float = 0f,
        pitch: Float = 0f,
        roll: Float = 0f,
        eye: Float? = 0.9f,
        sharp: Float = 150f,
        bright: Float = 120f
    ): FaceFrameInfo {
        val left = (cx * 600 - faceW / 2).toInt()
        val top = (cy * 800 - faceW * 1.2f / 2).toInt()
        return FaceFrameInfo(600, 800, left, top, left + faceW, top + (faceW * 1.2f).toInt(),
            faceCount, yaw, pitch, roll, eye, sharp, bright)
    }

    @Test
    fun `khung hinh tot thi 100 phan tram va passed`() {
        val r = FaceQualityChecker.evaluate(good(), EnrollPose.FRONT)
        assertTrue(r.passed)
        assertEquals(100, r.percent)
        assertEquals(QualityHint.GOOD, r.hint)
    }

    @Test
    fun `mat qua nho thi bao lai gan hon, khong passed, phan tram duoi 100`() {
        val r = FaceQualityChecker.evaluate(good(faceW = 90), EnrollPose.FRONT)
        assertFalse(r.passed)
        assertTrue(r.percent in 1..99)
        assertEquals(QualityHint.MOVE_CLOSER, r.hint)
    }

    @Test
    fun `mat qua to thi bao lui ra`() {
        val r = FaceQualityChecker.evaluate(good(faceW = 420), EnrollPose.FRONT)
        assertFalse(r.passed)
        assertEquals(QualityHint.MOVE_BACK, r.hint)
    }

    @Test
    fun `mat lech khoi oval thi bao dua vao giua khung`() {
        val r = FaceQualityChecker.evaluate(good(cx = 0.85f), EnrollPose.FRONT)
        assertFalse(r.passed)
        assertEquals(QualityHint.MOVE_INTO_FRAME, r.hint)
    }

    @Test
    fun `hai nguoi trong khung thi diem 0 tieu chi do va bao chi mot nguoi`() {
        val r = FaceQualityChecker.evaluate(good(faceCount = 2), EnrollPose.FRONT)
        assertFalse(r.passed)
        assertEquals(QualityHint.MULTIPLE_FACES, r.hint)
    }

    @Test
    fun `buoc nhin thang ma quay dau thi bao giu dau thang`() {
        val r = FaceQualityChecker.evaluate(good(yaw = 25f), EnrollPose.FRONT)
        assertFalse(r.passed)
        assertEquals(QualityHint.LOOK_STRAIGHT, r.hint)
        assertFalse(FaceQualityChecker.evaluate(good(pitch = 25f), EnrollPose.FRONT).passed)
        assertFalse(FaceQualityChecker.evaluate(good(roll = 25f), EnrollPose.FRONT).passed)
    }

    @Test
    fun `buoc quay ben A - nhin thang thi bao quay, quay 20 do ben nao cung dat`() {
        val straight = FaceQualityChecker.evaluate(good(yaw = 0f), EnrollPose.SIDE_A)
        assertFalse(straight.passed)
        assertEquals(QualityHint.TURN_SIDE, straight.hint)
        assertTrue(FaceQualityChecker.evaluate(good(yaw = 20f), EnrollPose.SIDE_A).passed)
        assertTrue(FaceQualityChecker.evaluate(good(yaw = -20f), EnrollPose.SIDE_A).passed)
        // Quay quá nhiều thì bảo quay ít lại
        assertEquals(QualityHint.TURN_LESS, FaceQualityChecker.evaluate(good(yaw = 55f), EnrollPose.SIDE_A).hint)
    }

    @Test
    fun `buoc quay ben B phai nguoc dau voi ben A`() {
        // Ben A đã quay dương -> B yêu cầu dấu âm
        val wrongWay = FaceQualityChecker.evaluate(good(yaw = 20f), EnrollPose.SIDE_B, requiredSign = -1)
        assertFalse(wrongWay.passed)
        assertEquals(QualityHint.TURN_OTHER_WAY, wrongWay.hint)
        assertTrue(FaceQualityChecker.evaluate(good(yaw = -20f), EnrollPose.SIDE_B, requiredSign = -1).passed)
        // Chưa quay gì ở bước B cũng nhắc "quay sang bên kia"
        assertEquals(
            QualityHint.TURN_OTHER_WAY,
            FaceQualityChecker.evaluate(good(yaw = 0f), EnrollPose.SIDE_B, requiredSign = -1).hint
        )
    }

    @Test
    fun `thieu sang, qua sang, mo, nham mat deu khong passed va goi y dung`() {
        assertEquals(QualityHint.TOO_DARK, FaceQualityChecker.evaluate(good(bright = 40f), EnrollPose.FRONT).hint)
        assertEquals(QualityHint.TOO_BRIGHT, FaceQualityChecker.evaluate(good(bright = 225f), EnrollPose.FRONT).hint)
        assertEquals(QualityHint.BLURRY, FaceQualityChecker.evaluate(good(sharp = 10f), EnrollPose.FRONT).hint)
        assertEquals(QualityHint.OPEN_EYES, FaceQualityChecker.evaluate(good(eye = 0.1f), EnrollPose.FRONT).hint)
        // Không có xác suất mắt (thiết bị hiếm) -> không chặn
        assertTrue(FaceQualityChecker.evaluate(good(eye = null), EnrollPose.FRONT).passed)
    }

    @Test
    fun `phan tram tang dan khi tien lai gan`() {
        val far = FaceQualityChecker.evaluate(good(faceW = 80), EnrollPose.FRONT).percent
        val mid = FaceQualityChecker.evaluate(good(faceW = 110), EnrollPose.FRONT).percent
        val near = FaceQualityChecker.evaluate(good(faceW = 140), EnrollPose.FRONT).percent
        assertTrue("$far < $mid < $near", far < mid && mid <= near)
        assertEquals(100, near)
    }
}

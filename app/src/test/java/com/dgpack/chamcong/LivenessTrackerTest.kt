package com.dgpack.chamcong

import com.dgpack.chamcong.face.LivenessTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivenessTrackerTest {

    @Test
    fun `xac nhan khi co du nhip mo-nham-mo`() {
        val tracker = LivenessTracker()
        tracker.onEyeOpenSample(0.9f) // mở
        assertFalse(tracker.isConfirmed)
        tracker.onEyeOpenSample(0.1f) // nhắm
        assertFalse(tracker.isConfirmed)
        tracker.onEyeOpenSample(0.9f) // mở lại -> đủ 1 nhịp chớp mắt
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `khong xac nhan neu mat luon mo (co the la anh tinh)`() {
        val tracker = LivenessTracker()
        repeat(10) { tracker.onEyeOpenSample(0.95f) }
        assertFalse(tracker.isConfirmed)
    }

    @Test
    fun `khong xac nhan neu mat luon nham (chua bao gio thay mo truoc do)`() {
        val tracker = LivenessTracker()
        repeat(10) { tracker.onEyeOpenSample(0.05f) }
        assertFalse(tracker.isConfirmed)
    }

    @Test
    fun `gia tri lung chung giua nguong khong lam doi trang thai`() {
        val tracker = LivenessTracker()
        tracker.onEyeOpenSample(0.9f) // mở
        tracker.onEyeOpenSample(0.45f) // lưng chừng — không tính là nhắm hẳn
        assertFalse(tracker.isConfirmed)
        tracker.onEyeOpenSample(0.1f) // nhắm hẳn
        tracker.onEyeOpenSample(0.9f)
        assertTrue(tracker.isConfirmed)
    }

    @Test
    fun `sau khi xac nhan cac mau tiep theo khong lam mat trang thai confirmed`() {
        val tracker = LivenessTracker()
        tracker.onEyeOpenSample(0.9f)
        tracker.onEyeOpenSample(0.1f)
        tracker.onEyeOpenSample(0.9f)
        assertTrue(tracker.isConfirmed)
        tracker.onEyeOpenSample(0.1f)
        assertTrue(tracker.isConfirmed)
    }
}

package com.dgpack.chamcong

import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.luckydraw.LuckyDrawCandidate
import com.dgpack.chamcong.luckydraw.LuckyDrawConfig
import com.dgpack.chamcong.luckydraw.LuckyDrawEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * Luật quay thưởng lon nước ngọt (tháng 8–9/2026): khoảng ngày, chỉ lần đầu trong ngày,
 * sinh nhật chắc chắn 3 lon, quota/ngày không bao giờ vượt, trọng số ưu tiên người
 * không đi trễ/về sớm và người được khen thưởng.
 */
class LuckyDrawEngineTest {

    private val config = LuckyDrawConfig(
        enabled = true,
        startDate = LocalDate.of(2026, 8, 1),
        endDate = LocalDate.of(2026, 9, 30),
        dailyQuota = 10
    )
    private val aug20 = LocalDate.of(2026, 8, 20)

    private fun pool(n: Int) = List(n) { LuckyDrawCandidate("NV%03d".format(it)) }

    @Test
    fun `ngoai khoang ngay hoac tat thi khong quay`() {
        val c = LuckyDrawCandidate("NV001", birthDate = "1990-07-31")
        assertNull(LuckyDrawEngine.draw(c, LocalDate.of(2026, 7, 31), config, true, 0, pool(10), Random(1)))
        assertNull(LuckyDrawEngine.draw(c, LocalDate.of(2026, 10, 1), config, true, 0, pool(10), Random(1)))
        assertNull(LuckyDrawEngine.draw(c, aug20, config.copy(enabled = false), true, 0, pool(10), Random(1)))
        // Biên trong đợt thì vẫn quay bình thường
        assertTrue(LuckyDrawEngine.isInCampaign(config, LocalDate.of(2026, 8, 1)))
        assertTrue(LuckyDrawEngine.isInCampaign(config, LocalDate.of(2026, 9, 30)))
    }

    @Test
    fun `khong phai lan cham cong dau tien trong ngay thi khong quay, ke ca sinh nhat`() {
        val birthday = LuckyDrawCandidate("NV001", birthDate = "1990-08-20")
        assertNull(LuckyDrawEngine.draw(birthday, aug20, config, isFirstScanToday = false, 0, pool(10), Random(1)))
    }

    @Test
    fun `sinh nhat thi chac chan trung 3 lon, khong tinh quota`() {
        val birthday = LuckyDrawCandidate("NV001", birthDate = "1990-08-20")
        // Quota đã hết vẫn trúng
        val win = LuckyDrawEngine.draw(birthday, aug20, config, true, randomWinsToday = 10, pool(10), Random(1))
        assertNotNull(win)
        assertEquals(3, win!!.cans)
        assertEquals(LuckyDrawReason.BIRTHDAY, win.reason)
        assertEquals(1.0, win.chance, 0.0)

        // ERP trả kiểu .NET DateTime cũng nhận ra
        val dotNet = LuckyDrawCandidate("NV002", birthDate = "1985-08-20T00:00:00")
        assertEquals(3, LuckyDrawEngine.draw(dotNet, aug20, config, true, 10, pool(10), Random(1))!!.cans)

        // Sinh 29/02, năm 2026 không nhuận thi mừng 28/02
        assertTrue(LuckyDrawEngine.isBirthday("2000-02-29", LocalDate.of(2026, 2, 28)))
        assertFalse(LuckyDrawEngine.isBirthday("2000-02-29", LocalDate.of(2026, 3, 1)))
        assertFalse(LuckyDrawEngine.isBirthday("khong-phai-ngay", aug20))
        assertFalse(LuckyDrawEngine.isBirthday(null, aug20))
    }

    @Test
    fun `het quota trong ngay thi khong trung nua`() {
        val c = LuckyDrawCandidate("NV001")
        repeat(200) { seed ->
            assertNull(LuckyDrawEngine.draw(c, aug20, config, true, randomWinsToday = 10, listOf(c), Random(seed)))
        }
    }

    @Test
    fun `nguoi cuoi cung trong pool ma con quota thi chac chan trung`() {
        val c = LuckyDrawCandidate("NV001")
        val win = LuckyDrawEngine.draw(c, aug20, config, true, randomWinsToday = 9, pool = listOf(c), Random(7))
        assertNotNull(win)
        assertEquals(1, win!!.cans)
        assertEquals(LuckyDrawReason.RANDOM, win.reason)
        assertEquals(1.0, win.chance, 0.0)
    }

    @Test
    fun `trong so - khong tre som x2, khen thuong +50% moi lan (toi da 4), da trung x0_5`() {
        assertEquals(2.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 0)), 1e-9)
        assertEquals(1.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 1)), 1e-9)
        assertEquals(1.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 2)), 1e-9)
        assertEquals(0.5, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 3)), 1e-9)
        assertEquals(3.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 0, commendationCount = 1)), 1e-9)
        assertEquals(6.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 0, commendationCount = 4)), 1e-9)
        assertEquals(6.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 0, commendationCount = 9)), 1e-9)
        assertEquals(1.0, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 0, winsThisCampaign = 1)), 1e-9)
        // Sàn 0.1 để ai cũng còn cơ hội
        assertEquals(0.1, LuckyDrawEngine.weightOf(LuckyDrawCandidate("a", lateEarlyCount30d = 5, winsThisCampaign = 6)), 1e-9)
    }

    @Test
    fun `mo phong 1 ngay 120 nguoi - khong bao gio vuot 10, trung binh xap xi 10`() {
        val employees = List(120) { i ->
            LuckyDrawCandidate("NV%03d".format(i), lateEarlyCount30d = if (i % 3 == 0) 2 else 0, commendationCount = i % 5)
        }
        var totalWins = 0
        val days = 300
        repeat(days) { day ->
            val rnd = Random(day)
            val order = employees.shuffled(rnd)
            val remainingPool = order.toMutableList()
            var winsToday = 0
            for (e in order) {
                val win = LuckyDrawEngine.draw(e, aug20, config, true, winsToday, remainingPool.toList(), rnd)
                remainingPool.remove(e)
                if (win != null) winsToday++
            }
            assertTrue("ngày $day trúng $winsToday > quota", winsToday <= 10)
            totalWins += winsToday
        }
        val avg = totalWins.toDouble() / days
        assertTrue("trung bình $avg lệch xa 10", avg > 9.0 && avg <= 10.0)
    }

    @Test
    fun `nguoi khong tre som, duoc khen thuong trung nhieu hon nguoi hay tre`() {
        val good = LuckyDrawCandidate("GOOD", lateEarlyCount30d = 0, commendationCount = 2)
        val bad = LuckyDrawCandidate("BAD", lateEarlyCount30d = 4)
        val others = List(60) { LuckyDrawCandidate("O$it", lateEarlyCount30d = 1) }
        var goodWins = 0
        var badWins = 0
        repeat(2000) { seed ->
            val rnd = Random(seed)
            val all = (others + good + bad).shuffled(rnd)
            val remainingPool = all.toMutableList()
            var winsToday = 0
            for (e in all) {
                val win = LuckyDrawEngine.draw(e, aug20, config, true, winsToday, remainingPool.toList(), rnd)
                remainingPool.remove(e)
                if (win != null) {
                    winsToday++
                    if (e === good) goodWins++
                    if (e === bad) badWins++
                }
            }
        }
        // Trọng số 4.0 so với 0.5 thi chênh lệch rõ rệt
        assertTrue("good=$goodWins bad=$badWins", goodWins > badWins * 4)
    }
}

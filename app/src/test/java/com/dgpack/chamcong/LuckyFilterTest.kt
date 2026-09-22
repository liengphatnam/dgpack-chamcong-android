package com.dgpack.chamcong

import com.dgpack.chamcong.ui.luckydraw.LuckyDrawViewModel
import com.dgpack.chamcong.ui.luckydraw.LuckyFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Bộ lọc sổ trúng thưởng: hôm nay / hôm qua / tuần này (từ thứ Hai) / tháng này / tất cả. */
class LuckyFilterTest {
    // 23/09/2026 là thứ Tư
    private val today = LocalDate.of(2026, 9, 23)

    private fun ok(date: String, f: LuckyFilter) = LuckyDrawViewModel.inRange(date, f, today)

    @Test
    fun `hom nay va hom qua`() {
        assertTrue(ok("2026-09-23", LuckyFilter.TODAY))
        assertFalse(ok("2026-09-22", LuckyFilter.TODAY))
        assertTrue(ok("2026-09-22", LuckyFilter.YESTERDAY))
        assertFalse(ok("2026-09-23", LuckyFilter.YESTERDAY))
    }

    @Test
    fun `tuan nay tinh tu thu Hai 21-09 toi hom nay`() {
        assertTrue(ok("2026-09-21", LuckyFilter.THIS_WEEK))
        assertTrue(ok("2026-09-23", LuckyFilter.THIS_WEEK))
        assertFalse(ok("2026-09-20", LuckyFilter.THIS_WEEK)) // Chủ nhật tuần trước
        assertFalse(ok("2026-09-24", LuckyFilter.THIS_WEEK)) // tương lai
    }

    @Test
    fun `thang nay va tat ca`() {
        assertTrue(ok("2026-09-01", LuckyFilter.THIS_MONTH))
        assertFalse(ok("2026-08-31", LuckyFilter.THIS_MONTH))
        assertTrue(ok("2026-08-31", LuckyFilter.ALL))
        assertTrue(ok("khong-phai-ngay", LuckyFilter.ALL))
        assertFalse(ok("khong-phai-ngay", LuckyFilter.TODAY))
    }
}

package com.dgpack.chamcong

import com.dgpack.chamcong.ui.pin.AdminLevel
import com.dgpack.chamcong.ui.pin.AdminPinPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** Mật mã cấp 2 = ngày × tháng × 2 × 3; mã cấp 1 do admin đặt; cấp 2 ưu tiên. */
class AdminPinPolicyTest {

    @Test
    fun `ma cap 2 theo ngay`() {
        assertEquals("1188", AdminPinPolicy.level2Code(LocalDate.of(2026, 9, 22)))
        assertEquals("6", AdminPinPolicy.level2Code(LocalDate.of(2026, 1, 1)))
        assertEquals("2232", AdminPinPolicy.level2Code(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun `resolve - cap 1, cap 2, sai`() {
        val today = LocalDate.of(2026, 9, 22)
        assertEquals(AdminLevel.LEVEL_2, AdminPinPolicy.resolve("1188", "1234", today))
        assertEquals(AdminLevel.LEVEL_1, AdminPinPolicy.resolve("1234", "1234", today))
        assertNull(AdminPinPolicy.resolve("0000", "1234", today))
        // Hôm qua là mã khác -> không còn dùng được
        assertNull(AdminPinPolicy.resolve("1134", "1234", today))
        // Chưa đặt mã cấp 1 -> chỉ mã cấp 2 hợp lệ
        assertNull(AdminPinPolicy.resolve("1234", "", today))
        assertEquals(AdminLevel.LEVEL_2, AdminPinPolicy.resolve("1188", "", today))
        assertNull(AdminPinPolicy.resolve("", "", today))
    }
}

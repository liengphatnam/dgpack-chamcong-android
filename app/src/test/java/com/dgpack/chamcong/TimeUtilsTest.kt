package com.dgpack.chamcong

import com.dgpack.chamcong.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Mục [5.2]/[9]: chỗ dễ sai nhất trong toàn bộ app — verify đúng ví dụ nêu trong tài
 * liệu: 17h chiều giờ VN (UTC+7) PHẢI thành 10h UTC, không phải 00h hôm sau hay lệch
 * múi giờ nào khác.
 */
class TimeUtilsTest {

    private val vnZone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun `17h chieu gio VN phai la 10h UTC cung ngay`() {
        val vnWallClock = LocalDateTime.of(2026, 8, 30, 17, 0, 0)
        val instant = vnWallClock.atZone(vnZone).toInstant()

        val apiString = TimeUtils.instantToApiString(instant)

        assertEquals("2026-08-30T10:00:00", apiString)
    }

    @Test
    fun `gan nua dem gio VN phai chuyen dung sang ngay UTC truoc do`() {
        // 00:30 ngày 31/08 giờ VN = 17:30 ngày 30/08 UTC — nếu code sai sẽ lấy nhầm
        // "hôm nay" của ERP thành 31/08 dù thực tế UTC vẫn là 30/08.
        val vnWallClock = LocalDateTime.of(2026, 8, 31, 0, 30, 0)
        val instant = vnWallClock.atZone(vnZone).toInstant()

        val apiString = TimeUtils.instantToApiString(instant)

        assertEquals("2026-08-30T17:30:00", apiString)
    }

    @Test
    fun `apiStringToInstant va instantToApiString la nghich dao cua nhau`() {
        val original = Instant.parse("2026-08-30T01:00:00Z")
        val roundTrip = TimeUtils.apiStringToInstant(TimeUtils.instantToApiString(original))
        assertEquals(original, roundTrip)
    }

    @Test
    fun `format khong co hau to Z`() {
        val apiString = TimeUtils.instantToApiString(Instant.parse("2026-08-30T01:00:00Z"))
        assertEquals(false, apiString.endsWith("Z"))
    }

    @Test
    fun `apiStringToVnDisplay quy doi dung sang gio Viet Nam de hien thi`() {
        // 10h UTC lưu trong DB phải hiển thị cho người xem là 17h VN cùng ngày.
        val display = TimeUtils.apiStringToVnDisplay("2026-08-30T10:00:00")
        assertEquals("30/08/2026 17:00:00", display)
    }
}

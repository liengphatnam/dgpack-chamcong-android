package com.dgpack.chamcong.ui.pin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Hai cấp quyền khu quản trị:
 *  - LEVEL_1: mật mã 4 số đặt ở Cài đặt — chỉ được đồng bộ ERP và đăng ký khuôn mặt.
 *  - LEVEL_2: mật mã động theo ngày (xem [AdminPinPolicy]) — mọi thứ còn lại: Cài đặt thiết bị,
 *    sổ trúng thưởng, và đổi mật mã cấp 1.
 */
enum class AdminLevel { NONE, LEVEL_1, LEVEL_2 }

/** Quyền của phiên quản trị hiện tại — reset mỗi lần vào lại từ màn camera (AppNavHost). */
object AdminSession {
    private val _level = MutableStateFlow(AdminLevel.NONE)
    val level: StateFlow<AdminLevel> = _level.asStateFlow()

    fun set(level: AdminLevel) {
        _level.value = level
    }

    fun clear() {
        _level.value = AdminLevel.NONE
    }
}

object AdminPinPolicy {
    /**
     * Mật mã cấp 2 = ngày × tháng × 2 × 3 (theo ngày Việt Nam). Ví dụ 22/09 → 22 × 9 × 6 = 1188;
     * 01/01 → 6. Không lưu ở đâu, chỉ người biết công thức mới nhập được. Tối đa 31×12×6 = 2232 (4 số).
     */
    fun level2Code(date: LocalDate): String = (date.dayOfMonth * date.monthValue * 2 * 3).toString()

    /**
     * Xác định cấp quyền từ mã nhập vào. Mã cấp 2 ưu tiên (kể cả khi trùng mã cấp 1).
     * [level1Pin] rỗng = không đặt -> chỉ mã cấp 2 mới hợp lệ ở đây (cấp 1 được vào không cần mã, xem AppNavHost).
     */
    fun resolve(input: String, level1Pin: String, today: LocalDate): AdminLevel? = when {
        input.isNotBlank() && input == level2Code(today) -> AdminLevel.LEVEL_2
        level1Pin.isNotBlank() && input == level1Pin -> AdminLevel.LEVEL_1
        else -> null
    }
}

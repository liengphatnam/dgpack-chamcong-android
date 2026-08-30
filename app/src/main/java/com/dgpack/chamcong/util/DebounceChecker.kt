package com.dgpack.chamcong.util

import java.time.Duration
import java.time.Instant

/**
 * Mục [3]: nếu cùng 1 người được nhận diện nhiều lần liên tiếp (đứng chờ, camera quét
 * lặp), KHÔNG tạo dòng attendance_event_local mới cho mỗi lần nhận diện — chỉ ghi dòng
 * mới nếu cách lần nhận diện gần nhất của ĐÚNG employeeCode đó >= ngưỡng phút.
 */
object DebounceChecker {
    fun shouldRecord(lastEventUtc: Instant?, nowUtc: Instant, thresholdMinutes: Int): Boolean {
        if (lastEventUtc == null) return true
        val elapsed = Duration.between(lastEventUtc, nowUtc)
        return elapsed.toMinutes() >= thresholdMinutes
    }
}

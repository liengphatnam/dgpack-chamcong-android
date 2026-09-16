package com.dgpack.chamcong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object LuckyDrawReason {
    /** Quay ngẫu nhiên có trọng số — 1 lon. */
    const val RANDOM = "Random"
    /** Đúng ngày sinh nhật — chắc chắn trúng, 3 lon, không tính vào quota ngày. */
    const val BIRTHDAY = "Birthday"
}

/**
 * Sổ ghi người trúng thưởng lon nước ngọt (chương trình tháng 8–9/2026). Append-only như
 * attendance_event_local: chỉ được cập nhật claimed/syncStatus/syncedAt, không sửa người/ngày.
 * Đây là bằng chứng để nhân sự phát thưởng nên lưu cả weight + chance lúc quay để kiểm tra
 * tính công bằng khi có thắc mắc.
 */
@Entity(tableName = "lucky_draw_win")
data class LuckyDrawWinEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val employeeCode: String,
    val fullName: String,
    /** Ngày trúng theo giờ Việt Nam, yyyy-MM-dd — dùng để đếm quota/ngày và hiển thị. */
    val drawDate: String,
    /** UTC "yyyy-MM-ddTHH:mm:ss" như eventTimeUtc. */
    val wonAtUtc: String,
    val cans: Int,
    /** [LuckyDrawReason]. */
    val reason: String,
    /** Trọng số của người này lúc quay (xem LuckyDrawEngine.weightOf). */
    val weight: Double,
    /** Xác suất 0..1 tại thời điểm quay (sinh nhật = 1.0). */
    val chance: Double,
    val deviceCode: String,
    /** Nhân sự đã phát thưởng — admin bấm trên màn "Trúng thưởng". */
    val claimed: Boolean = false,
    val syncStatus: String = SyncStatus.PENDING,
    val syncedAt: String? = null
)

/** Kết quả GROUP BY của [LuckyDrawWinDao.winsPerEmployee]. */
data class EmployeeWinCount(val employeeCode: String, val wins: Int)

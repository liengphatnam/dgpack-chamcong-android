package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.AttendanceEventEntity

/**
 * Tách nhỏ phần AttendanceRepository mà SyncEngine cần, để test SyncEngine bằng 1
 * fake in-memory (xem SyncEngineTest) thay vì phải khởi tạo Room thật (cần Robolectric).
 */
interface AttendanceSyncSource {
    suspend fun getPendingBatch(limit: Int): List<AttendanceEventEntity>
    suspend fun markStatus(event: AttendanceEventEntity, newStatus: String, syncedAtNow: Boolean)
    suspend fun markRetryLater(event: AttendanceEventEntity)
}

package com.dgpack.chamcong.data.repository

import com.dgpack.chamcong.data.db.AttendanceEventDao
import com.dgpack.chamcong.data.db.AttendanceEventEntity
import com.dgpack.chamcong.data.db.AttendanceMethod
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.sync.AttendanceSyncSource
import com.dgpack.chamcong.util.DebounceChecker
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant

data class QueueCounts(
    val pending: Int = 0,
    val synced: Int = 0,
    val duplicate: Int = 0,
    val unknownEmployee: Int = 0,
    val failed: Int = 0
)

class AttendanceRepository(private val dao: AttendanceEventDao) : AttendanceSyncSource {

    fun observeAll(): Flow<List<AttendanceEventEntity>> = dao.observeAll()

    fun observeCounts(): Flow<QueueCounts> = combine(
        dao.observeCountByStatus(SyncStatus.PENDING),
        dao.observeCountByStatus(SyncStatus.SYNCED),
        dao.observeCountByStatus(SyncStatus.DUPLICATE),
        dao.observeCountByStatus(SyncStatus.UNKNOWN_EMPLOYEE),
        dao.observeCountByStatus(SyncStatus.FAILED)
    ) { pending, synced, duplicate, unknown, failed ->
        QueueCounts(pending, synced, duplicate, unknown, failed)
    }

    /** Trả về true nếu đã ghi 1 sự kiện mới (không bị debounce chặn). */
    suspend fun recordEventIfNotDebounced(
        employeeCode: String,
        deviceCode: String,
        debounceMinutes: Int,
        method: String = AttendanceMethod.FACE,
        cardId: String? = null
    ): Boolean {
        val last = dao.getLastEventForEmployee(employeeCode)
        val lastInstant = last?.let { TimeUtils.apiStringToInstant(it.eventTimeUtc) }
        val now = Instant.now()
        if (!DebounceChecker.shouldRecord(lastInstant, now, debounceMinutes)) return false

        val nowIso = TimeUtils.instantToApiString(now)
        dao.insert(
            AttendanceEventEntity(
                employeeCode = employeeCode,
                eventTimeUtc = nowIso,
                deviceCode = deviceCode,
                syncStatus = SyncStatus.PENDING,
                createdAt = nowIso,
                method = method,
                cardId = cardId
            )
        )
        return true
    }

    override suspend fun getPendingBatch(limit: Int): List<AttendanceEventEntity> =
        dao.getByStatus(SyncStatus.PENDING, limit)

    /** Mục [5.4]: UnknownEmployee không tự retry vô hạn — chỉ retry khi admin bấm "Thử lại" thủ công. */
    suspend fun getUnknownEmployeeBatch(limit: Int): List<AttendanceEventEntity> =
        dao.getByStatus(SyncStatus.UNKNOWN_EMPLOYEE, limit)

    override suspend fun markStatus(event: AttendanceEventEntity, newStatus: String, syncedAtNow: Boolean) {
        dao.update(
            event.copy(
                syncStatus = newStatus,
                syncedAt = if (syncedAtNow) TimeUtils.nowUtcIso() else event.syncedAt,
                syncAttempts = event.syncAttempts + 1
            )
        )
    }

    /** Lỗi mạng/503 — mục [5.5]: giữ Pending, tăng syncAttempts để debug, KHÔNG chặn retry. */
    override suspend fun markRetryLater(event: AttendanceEventEntity) {
        dao.update(event.copy(syncAttempts = event.syncAttempts + 1))
    }

    /** Admin bấm "Thử lại" cho các dòng UnknownEmployee sau khi đã tạo NV bên ERP. */
    suspend fun resetToPending(event: AttendanceEventEntity) {
        dao.update(event.copy(syncStatus = SyncStatus.PENDING))
    }
}

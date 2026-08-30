package com.dgpack.chamcong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object SyncStatus {
    const val PENDING = "Pending"
    const val SYNCED = "Synced"
    const val DUPLICATE = "Duplicate"
    const val UNKNOWN_EMPLOYEE = "UnknownEmployee"
    const val FAILED = "Failed"
}

/**
 * Ledger append-only, mirror tinh thần hr.AttendanceEvent bên ERP.
 * KHÔNG bao giờ UPDATE employeeCode/eventTimeUtc của 1 dòng đã tạo — chỉ được
 * cập nhật syncStatus/syncedAt/syncAttempts.
 */
@Entity(tableName = "attendance_event_local")
data class AttendanceEventEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val employeeCode: String,
    val eventTimeUtc: String,
    val deviceCode: String,
    val syncStatus: String = SyncStatus.PENDING,
    val createdAt: String,
    val syncedAt: String? = null,
    val syncAttempts: Int = 0
)

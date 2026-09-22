package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.CardAssignmentEntity
import com.dgpack.chamcong.data.db.EmployeeMonthSummaryEntity
import com.dgpack.chamcong.data.db.ForgotCardLogEntity

/**
 * Phần CardRepository mà [EmployeeSyncEngine] cần cho 3 bước đồng bộ thẻ từ (API_FACE_SYNC.md
 * mục 5–7): đẩy thẻ đã gán, đẩy nhật ký quên thẻ (kèm ảnh), kéo chi tiết công tháng.
 */
interface CardSyncSource {
    suspend fun getPendingCardAssignments(limit: Int): List<CardAssignmentEntity>
    suspend fun markCardAssignmentSynced(cardId: String, status: String)

    suspend fun getPendingForgotCardLogs(limit: Int): List<ForgotCardLogEntity>
    suspend fun markForgotCardLogSynced(localId: Long, status: String)

    /** Thay toàn bộ cache chi tiết công của [month] bằng dữ liệu mới kéo về. */
    suspend fun replaceMonthSummary(month: String, rows: List<EmployeeMonthSummaryEntity>)
}

package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.LuckyDrawWinEntity

/**
 * Phần LuckyDrawRepository mà [EmployeeSyncEngine] cần để đẩy sổ trúng thưởng lên ERP
 * (bước 4) — tách interface để test bằng fake in-memory.
 */
interface LuckyDrawSyncSource {
    suspend fun getPendingWins(limit: Int): List<LuckyDrawWinEntity>

    /** [status] là 1 trong SyncStatus.SYNCED / UNKNOWN_EMPLOYEE. */
    suspend fun markWinSynced(localId: Long, status: String)
}

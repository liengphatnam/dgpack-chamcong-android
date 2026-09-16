package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LuckyDrawWinDao {
    @Insert
    suspend fun insert(win: LuckyDrawWinEntity): Long

    @Query("SELECT * FROM lucky_draw_win ORDER BY wonAtUtc DESC")
    fun observeAll(): Flow<List<LuckyDrawWinEntity>>

    @Query("SELECT COUNT(*) FROM lucky_draw_win WHERE drawDate = :drawDate AND reason = :reason")
    suspend fun countByDateAndReason(drawDate: String, reason: String): Int

    @Query("SELECT COUNT(*) FROM lucky_draw_win WHERE employeeCode = :employeeCode AND drawDate = :drawDate")
    suspend fun countForEmployeeOnDate(employeeCode: String, drawDate: String): Int

    /** Số lần trúng ngẫu nhiên của từng NV trong đợt — để giảm trọng số người đã trúng. */
    @Query(
        "SELECT employeeCode, COUNT(*) AS wins FROM lucky_draw_win " +
            "WHERE drawDate BETWEEN :fromDate AND :toDate AND reason = :reason GROUP BY employeeCode"
    )
    suspend fun winsPerEmployee(fromDate: String, toDate: String, reason: String): List<EmployeeWinCount>

    @Query("SELECT * FROM lucky_draw_win WHERE syncStatus = :status ORDER BY wonAtUtc ASC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<LuckyDrawWinEntity>

    @Query("UPDATE lucky_draw_win SET claimed = :claimed WHERE localId = :localId")
    suspend fun setClaimed(localId: Long, claimed: Boolean)

    @Query("UPDATE lucky_draw_win SET syncStatus = :status, syncedAt = :syncedAt WHERE localId = :localId")
    suspend fun markSyncStatus(localId: Long, status: String, syncedAt: String?)
}

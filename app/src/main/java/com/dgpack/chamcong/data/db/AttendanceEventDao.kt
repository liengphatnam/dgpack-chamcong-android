package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceEventDao {
    @Insert
    suspend fun insert(event: AttendanceEventEntity): Long

    @Update
    suspend fun update(event: AttendanceEventEntity)

    /** Lấy thời điểm chấm công gần nhất (bất kỳ trạng thái nào) của 1 mã NV — dùng cho debounce. */
    @Query("SELECT * FROM attendance_event_local WHERE employeeCode = :employeeCode ORDER BY eventTimeUtc DESC LIMIT 1")
    suspend fun getLastEventForEmployee(employeeCode: String): AttendanceEventEntity?

    @Query("SELECT * FROM attendance_event_local WHERE syncStatus = :status ORDER BY eventTimeUtc ASC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<AttendanceEventEntity>

    @Query("SELECT * FROM attendance_event_local ORDER BY eventTimeUtc DESC")
    fun observeAll(): Flow<List<AttendanceEventEntity>>

    @Query("SELECT COUNT(*) FROM attendance_event_local WHERE syncStatus = :status")
    fun observeCountByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM attendance_event_local WHERE syncStatus = :status")
    suspend fun countByStatus(status: String): Int
}

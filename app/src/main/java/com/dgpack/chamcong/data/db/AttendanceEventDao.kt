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

    // ---- Phục vụ quay thưởng (LuckyDrawRepository). Mốc [fromUtc, toUtc) là chuỗi UTC
    // cùng định dạng "yyyy-MM-ddTHH:mm:ss" nên so sánh chuỗi = so sánh thời gian. ----

    /** Số sự kiện của 1 NV trong khoảng — = 1 nghĩa là lần chấm công đầu tiên trong ngày. */
    @Query(
        "SELECT COUNT(*) FROM attendance_event_local " +
            "WHERE employeeCode = :employeeCode AND eventTimeUtc >= :fromUtc AND eventTimeUtc < :toUtc"
    )
    suspend fun countForEmployeeBetween(employeeCode: String, fromUtc: String, toUtc: String): Int

    /** Mã NV đã từng chấm công trên máy này trong khoảng — ước lượng "ai sẽ còn chấm công hôm nay". */
    @Query(
        "SELECT DISTINCT employeeCode FROM attendance_event_local " +
            "WHERE eventTimeUtc >= :fromUtc AND eventTimeUtc < :toUtc"
    )
    suspend fun distinctEmployeesBetween(fromUtc: String, toUtc: String): List<String>
}

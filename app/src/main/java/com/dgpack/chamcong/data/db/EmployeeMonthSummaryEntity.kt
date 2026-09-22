package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Bản sao "chi tiết công trong tháng" của từng NV kéo từ ERP (GET month-summary), để hiện
 * ngay sau khi quét thẻ kể cả khi mất mạng. ERP là nguồn sự thật; máy chỉ cache, thay toàn bộ
 * mỗi lần đồng bộ. Ngày hôm nay chưa tính (ERP chốt tới hết hôm qua).
 */
@Entity(tableName = "employee_month_summary", primaryKeys = ["employeeCode", "month"])
data class EmployeeMonthSummaryEntity(
    val employeeCode: String,
    /** yyyy-MM theo lịch VN. */
    val month: String,
    val workDays: Double,
    val otRegularHours: Double,
    val otSundayHours: Double,
    val otHolidayHours: Double,
    val leaveDays: Double,
    /** Số biên bản phạt trong tháng. */
    val disciplinaryCount: Int,
    val commendationCount: Int,
    /** ERP tổng hợp từ mọi thiết bị; máy tự đếm thêm phần chưa đồng bộ. */
    val forgotCardCount: Int,
    val penaltyAmount: Long,
    val syncedAt: String
)

@Dao
interface EmployeeMonthSummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<EmployeeMonthSummaryEntity>)

    @Query("SELECT * FROM employee_month_summary WHERE employeeCode = :employeeCode AND month = :month LIMIT 1")
    suspend fun get(employeeCode: String, month: String): EmployeeMonthSummaryEntity?

    @Query("DELETE FROM employee_month_summary WHERE month = :month")
    suspend fun deleteMonth(month: String)

    @Query("DELETE FROM employee_month_summary WHERE month < :minMonth")
    suspend fun deleteOlderThan(minMonth: String)
}

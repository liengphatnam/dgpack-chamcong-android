package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Nhật ký "quên mang thẻ": NV nhập mã, xác nhận tên, chớp mắt 2 lần để máy tự chụp ảnh nhỏ
 * làm bằng chứng. Giữ trên máy 2 tháng (LuckyDraw/ForgotCard purge), đẩy lên ERP khi có mạng.
 * Số lần trong tháng quyết định tiền phạt (ForgotCardPolicy).
 */
@Entity(tableName = "forgot_card_log")
data class ForgotCardLogEntity(
    @PrimaryKey(autoGenerate = true)
    val localId: Long = 0,
    val employeeCode: String,
    val fullName: String,
    /** UTC "yyyy-MM-ddTHH:mm:ss". */
    val eventTimeUtc: String,
    /** Ngày VN yyyy-MM-dd, tháng VN yyyy-MM — để đếm theo tháng và dọn dữ liệu cũ. */
    val dateVn: String,
    val monthVn: String,
    /** JPEG nhỏ (~200 px) vùng mặt, đủ nhận ra 1 người. */
    val photoJpeg: ByteArray?,
    val deviceCode: String,
    val syncStatus: String = SyncStatus.PENDING,
    val syncedAt: String? = null
) {
    override fun equals(other: Any?): Boolean =
        other is ForgotCardLogEntity && other.localId == localId && other.employeeCode == employeeCode &&
            other.eventTimeUtc == eventTimeUtc && other.syncStatus == syncStatus

    override fun hashCode(): Int = 31 * localId.hashCode() + eventTimeUtc.hashCode()
}

@Dao
interface ForgotCardLogDao {
    @Insert
    suspend fun insert(log: ForgotCardLogEntity): Long

    @Query("SELECT * FROM forgot_card_log WHERE monthVn = :month ORDER BY eventTimeUtc DESC")
    fun observeByMonth(month: String): Flow<List<ForgotCardLogEntity>>

    @Query("SELECT COUNT(*) FROM forgot_card_log WHERE employeeCode = :employeeCode AND monthVn = :month")
    suspend fun countForEmployeeInMonth(employeeCode: String, month: String): Int

    @Query("SELECT * FROM forgot_card_log WHERE syncStatus = :status ORDER BY eventTimeUtc ASC LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<ForgotCardLogEntity>

    @Query("UPDATE forgot_card_log SET syncStatus = :status, syncedAt = :syncedAt WHERE localId = :localId")
    suspend fun markSyncStatus(localId: Long, status: String, syncedAt: String?)

    /** Dọn dữ liệu cũ hơn [minDateVn] (giữ 2 tháng) — chỉ xoá dòng ĐÃ đồng bộ. */
    @Query("DELETE FROM forgot_card_log WHERE dateVn < :minDateVn AND syncStatus = :syncedStatus")
    suspend fun deleteSyncedOlderThan(minDateVn: String, syncedStatus: String = SyncStatus.SYNCED)
}

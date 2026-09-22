package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Thẻ từ gán cho nhân viên NGAY TRÊN THIẾT BỊ (admin quét thẻ ở màn Nhân viên ERP). Ưu tiên hơn
 * cardId kéo từ ERP; được đẩy lên ERP ở bước đồng bộ (POST cards) để các máy khác cũng biết.
 * [cardId] đã chuẩn hoá (CardScanBus.normalize): chữ hoa, chỉ 0-9A-Z.
 */
@Entity(tableName = "card_assignment")
data class CardAssignmentEntity(
    @PrimaryKey
    val cardId: String,
    val employeeCode: String,
    /** UTC "yyyy-MM-ddTHH:mm:ss". */
    val assignedAt: String,
    val syncStatus: String = SyncStatus.PENDING,
    val syncedAt: String? = null
)

@Dao
interface CardAssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assignment: CardAssignmentEntity)

    @Query("SELECT * FROM card_assignment WHERE cardId = :cardId LIMIT 1")
    suspend fun getByCard(cardId: String): CardAssignmentEntity?

    @Query("SELECT * FROM card_assignment WHERE employeeCode = :employeeCode")
    suspend fun getByEmployee(employeeCode: String): List<CardAssignmentEntity>

    /** 1 NV chỉ giữ 1 thẻ trên máy: gán thẻ mới thì bỏ thẻ cũ của người đó. */
    @Query("DELETE FROM card_assignment WHERE employeeCode = :employeeCode")
    suspend fun deleteByEmployee(employeeCode: String)

    @Query("SELECT * FROM card_assignment ORDER BY assignedAt DESC")
    fun observeAll(): Flow<List<CardAssignmentEntity>>

    @Query("SELECT * FROM card_assignment WHERE syncStatus = :status LIMIT :limit")
    suspend fun getByStatus(status: String, limit: Int): List<CardAssignmentEntity>

    @Query("UPDATE card_assignment SET syncStatus = :status, syncedAt = :syncedAt WHERE cardId = :cardId")
    suspend fun markSyncStatus(cardId: String, status: String, syncedAt: String?)
}

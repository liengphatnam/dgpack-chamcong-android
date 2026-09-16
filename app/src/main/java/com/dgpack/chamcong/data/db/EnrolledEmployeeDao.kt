package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EnrolledEmployeeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(employee: EnrolledEmployeeEntity)

    @Query("SELECT * FROM enrolled_employee ORDER BY fullName ASC")
    fun observeAll(): Flow<List<EnrolledEmployeeEntity>>

    @Query("SELECT * FROM enrolled_employee")
    suspend fun getAllOnce(): List<EnrolledEmployeeEntity>

    @Query("SELECT * FROM enrolled_employee WHERE employeeCode = :employeeCode LIMIT 1")
    suspend fun getByCode(employeeCode: String): EnrolledEmployeeEntity?

    @Delete
    suspend fun delete(employee: EnrolledEmployeeEntity)

    /** Chỉ cập nhật mốc đồng bộ, không đụng embedding/ảnh (tránh đọc-ghi lại BLOB vô ích). */
    @Query("UPDATE enrolled_employee SET faceSyncedAt = :syncedAt WHERE employeeCode = :employeeCode")
    suspend fun markFaceSynced(employeeCode: String, syncedAt: String)
}

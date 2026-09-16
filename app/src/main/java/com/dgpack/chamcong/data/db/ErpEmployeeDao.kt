package com.dgpack.chamcong.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ErpEmployeeDao {
    @Query("SELECT * FROM erp_employee ORDER BY fullName ASC")
    fun observeAll(): Flow<List<ErpEmployeeEntity>>

    @Query("SELECT * FROM erp_employee")
    suspend fun getAllOnce(): List<ErpEmployeeEntity>

    @Query("SELECT * FROM erp_employee WHERE employeeCode = :employeeCode LIMIT 1")
    suspend fun getByCode(employeeCode: String): ErpEmployeeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(employees: List<ErpEmployeeEntity>)

    @Query("DELETE FROM erp_employee")
    suspend fun deleteAll()
}

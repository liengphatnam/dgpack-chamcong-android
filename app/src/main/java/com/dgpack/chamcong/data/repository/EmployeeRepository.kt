package com.dgpack.chamcong.data.repository

import androidx.room.withTransaction
import com.dgpack.chamcong.data.db.AppDatabase
import com.dgpack.chamcong.data.db.ErpEmployeeDao
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.sync.EmployeeSyncSource
import kotlinx.coroutines.flow.Flow

/**
 * Bản sao danh sách dm.Employee kéo từ ERP (API_FACE_SYNC.md mục 1) — dùng để gán thẻ, tra tên
 * khi quét thẻ / quên thẻ, và quay thưởng. ERP là nguồn sự thật; mỗi lần đồng bộ thay toàn bộ bảng.
 */
class EmployeeRepository(
    private val erpDao: ErpEmployeeDao,
    private val database: AppDatabase
) : EmployeeSyncSource {

    fun observeErpEmployees(): Flow<List<ErpEmployeeEntity>> = erpDao.observeAll()

    override suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>) {
        database.withTransaction {
            erpDao.deleteAll()
            erpDao.upsertAll(employees)
        }
    }
}

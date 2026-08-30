package com.dgpack.chamcong

import android.app.Application
import com.dgpack.chamcong.data.db.AppDatabase
import com.dgpack.chamcong.data.prefs.SettingsRepository
import com.dgpack.chamcong.data.repository.AttendanceRepository
import com.dgpack.chamcong.data.repository.EmployeeRepository
import com.dgpack.chamcong.face.FaceEmbedder
import com.dgpack.chamcong.sync.SyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Không dùng dependency-injection framework (Hilt/Koin) — quy mô app nhỏ (4 màn hình,
 * Phase 1), Application đóng vai trò service locator đơn giản cho các singleton dùng
 * chung giữa UI và WorkManager (SyncWorker cần truy cập lại các repository này).
 */
class ChamCongApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.getInstance(this) }
    val employeeRepository: EmployeeRepository by lazy { EmployeeRepository(database.enrolledEmployeeDao()) }
    val attendanceRepository: AttendanceRepository by lazy { AttendanceRepository(database.attendanceEventDao()) }

    // Nặng (nạp model TFLite) — chỉ khởi tạo khi thực sự cần (lúc mở màn hình Camera/Enroll).
    val faceEmbedder: FaceEmbedder by lazy { FaceEmbedder(this) }

    override fun onCreate() {
        super.onCreate()
        SyncManager.schedulePeriodic(this)
        applicationScope.launch {
            employeeRepository.refreshCache()
        }
    }
}

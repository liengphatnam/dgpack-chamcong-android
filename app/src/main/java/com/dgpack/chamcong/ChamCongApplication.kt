package com.dgpack.chamcong

import android.app.Application
import com.dgpack.chamcong.card.CardRepository
import com.dgpack.chamcong.data.db.AppDatabase
import com.dgpack.chamcong.data.prefs.SettingsRepository
import com.dgpack.chamcong.data.repository.AttendanceRepository
import com.dgpack.chamcong.data.repository.EmployeeRepository
import com.dgpack.chamcong.face.FaceEmbedder
import com.dgpack.chamcong.luckydraw.LuckyDrawRepository
import com.dgpack.chamcong.sync.EmployeeSyncCoordinator
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
    val employeeRepository: EmployeeRepository by lazy {
        EmployeeRepository(database.enrolledEmployeeDao(), database.erpEmployeeDao(), database)
    }
    val attendanceRepository: AttendanceRepository by lazy { AttendanceRepository(database.attendanceEventDao()) }

    // Quay thưởng lon nước ngọt sau mỗi lần chấm công (tháng 8–9/2026) + sổ người trúng.
    val luckyDrawRepository: LuckyDrawRepository by lazy {
        LuckyDrawRepository(
            winDao = database.luckyDrawWinDao(),
            attendanceDao = database.attendanceEventDao(),
            erpDao = database.erpEmployeeDao(),
            enrolledDao = database.enrolledEmployeeDao()
        )
    }

    // Thẻ từ: gán thẻ, quên thẻ (+ ảnh bằng chứng), cache chi tiết công tháng.
    val cardRepository: CardRepository by lazy {
        CardRepository(
            cardDao = database.cardAssignmentDao(),
            forgotDao = database.forgotCardLogDao(),
            summaryDao = database.employeeMonthSummaryDao(),
            erpDao = database.erpEmployeeDao(),
            enrolledDao = database.enrolledEmployeeDao()
        )
    }

    // Đồng bộ danh sách NV + embedding với ERP (API_FACE_SYNC.md) — trạng thái chia sẻ cho UI.
    val employeeSyncCoordinator: EmployeeSyncCoordinator by lazy { EmployeeSyncCoordinator(this) }

    // Nặng (nạp model TFLite) — chỉ khởi tạo khi thực sự cần (lúc mở màn hình Camera/Enroll).
    val faceEmbedder: FaceEmbedder by lazy { FaceEmbedder(this) }

    override fun onCreate() {
        super.onCreate()
        SyncManager.schedulePeriodic(this)
        applicationScope.launch {
            employeeRepository.refreshCache()
            // Giữ nhật ký quên thẻ / cache công 2 tháng trên máy.
            runCatching { cardRepository.purgeOld() }
        }
    }
}

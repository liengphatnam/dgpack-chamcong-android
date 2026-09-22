package com.dgpack.chamcong.sync

import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.network.ApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class EmployeeSyncPhase { IDLE, RUNNING, DONE, ERROR }

data class EmployeeSyncState(
    val phase: EmployeeSyncPhase = EmployeeSyncPhase.IDLE,
    val outcome: EmployeeSyncOutcome? = null,
    val atEpochMillis: Long = 0L
)

/**
 * Nối dây cho [EmployeeSyncEngine]: đọc Settings, tạo ApiClient, chạy 1 lần tại 1 thời
 * điểm (Mutex), phát trạng thái cho UI (màn hình Nhân viên/Hàng đợi). Được gọi khi:
 *  - admin vào khu quản trị sau PIN ("khi đăng nhập") — AppNavHost,
 *  - admin bấm "Đồng bộ nhân viên" — EmployeesScreen,
 *  - vừa enroll xong 1 người — EnrollViewModel (đẩy embedding lên ngay nếu có mạng),
 *  - SyncWorker định kỳ 15 phút (kèm đồng bộ sự kiện chấm công).
 */
class EmployeeSyncCoordinator(private val app: ChamCongApplication) {

    private val _state = MutableStateFlow(EmployeeSyncState())
    val state: StateFlow<EmployeeSyncState> = _state.asStateFlow()

    private val mutex = Mutex()

    /** Chạy nền, bỏ qua nếu đang có 1 lần đồng bộ khác chạy dở. */
    fun requestSync() {
        if (mutex.isLocked) return
        app.applicationScope.launch { runSync() }
    }

    /** Chạy đồng bộ và chờ kết quả (SyncWorker dùng). Nếu đang chạy dở thì đợi lượt. */
    suspend fun runSync(): EmployeeSyncOutcome = mutex.withLock {
        _state.value = EmployeeSyncState(EmployeeSyncPhase.RUNNING, null, System.currentTimeMillis())

        val settings = app.settingsRepository.current()
        val outcome: EmployeeSyncOutcome = if (settings.apiKey.isBlank() || settings.deviceCode.isBlank()) {
            EmployeeSyncOutcome.NotConfigured
        } else {
            val api = try {
                ApiClient.create(settings.serverUrl)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (api == null) {
                EmployeeSyncOutcome.NetworkError(app.getString(com.dgpack.chamcong.R.string.loi_url_server))
            } else {
                EmployeeSyncEngine(app.employeeRepository, app.luckyDrawRepository, app.cardRepository)
                    .sync(api, settings.apiKey, settings.deviceCode)
            }
        }

        if (outcome is EmployeeSyncOutcome.Completed && outcome.downloaded > 0) {
            // Có embedding mới tải về -> nạp lại cache RAM để camera nhận diện được ngay.
            app.employeeRepository.refreshCache()
        }

        val phase = if (outcome is EmployeeSyncOutcome.Completed) EmployeeSyncPhase.DONE else EmployeeSyncPhase.ERROR
        _state.value = EmployeeSyncState(phase, outcome, System.currentTimeMillis())
        outcome
    }
}

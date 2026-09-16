package com.dgpack.chamcong.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.db.AttendanceEventEntity
import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.data.repository.QueueCounts
import com.dgpack.chamcong.sync.SyncManager
import com.dgpack.chamcong.util.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueUiState(
    val counts: QueueCounts = QueueCounts(),
    val events: List<AttendanceEventEntity> = emptyList(),
    val enrolledEmployees: List<EnrolledEmployeeEntity> = emptyList(),
    val isOnline: Boolean = false,
    /** NV đang hoạt động bên ERP chưa có khuôn mặt ở máy này lẫn trên server (0 nếu chưa kéo danh sách). */
    val missingFaceCount: Int = 0
)

class QueueViewModel(private val app: ChamCongApplication) : ViewModel() {

    val uiState: StateFlow<QueueUiState> = combine(
        app.attendanceRepository.observeCounts(),
        app.attendanceRepository.observeAll(),
        app.employeeRepository.observeAll(),
        NetworkMonitor.observe(app),
        app.employeeRepository.observeErpEmployees()
    ) { counts, events, employees, online, erpEmployees ->
        val enrolledCodes = employees.map { it.employeeCode }.toSet()
        val missing = erpEmployees.count { it.isActive && !it.hasFaceOnServer && it.employeeCode !in enrolledCodes }
        QueueUiState(counts, events, employees, online, missing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), QueueUiState())

    fun syncNow() {
        SyncManager.syncNow(app)
    }

    /** Mục [2.1]/[5.4]: admin đã tạo NV bên ERP xong, bấm thử lại các dòng UnknownEmployee. */
    fun retryUnknownEmployees() {
        viewModelScope.launch {
            val unknownBatch = app.attendanceRepository.getUnknownEmployeeBatch(500)
            unknownBatch.forEach { app.attendanceRepository.resetToPending(it) }
            SyncManager.syncNow(app)
        }
    }
}

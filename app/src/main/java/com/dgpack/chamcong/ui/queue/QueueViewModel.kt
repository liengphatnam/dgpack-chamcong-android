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
    val isOnline: Boolean = false
)

class QueueViewModel(private val app: ChamCongApplication) : ViewModel() {

    val uiState: StateFlow<QueueUiState> = combine(
        app.attendanceRepository.observeCounts(),
        app.attendanceRepository.observeAll(),
        app.employeeRepository.observeAll(),
        NetworkMonitor.observe(app)
    ) { counts, events, employees, online ->
        QueueUiState(counts, events, employees, online)
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

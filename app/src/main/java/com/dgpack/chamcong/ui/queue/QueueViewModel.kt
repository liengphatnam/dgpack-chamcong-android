package com.dgpack.chamcong.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.db.AttendanceEventEntity
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
    val isOnline: Boolean = false,
    /** NV đang hoạt động bên ERP chưa có thẻ (0 nếu chưa kéo danh sách) — nhắc admin gán thẻ. */
    val missingCardCount: Int = 0
)

class QueueViewModel(private val app: ChamCongApplication) : ViewModel() {

    val uiState: StateFlow<QueueUiState> = combine(
        app.attendanceRepository.observeCounts(),
        app.attendanceRepository.observeAll(),
        NetworkMonitor.observe(app),
        app.employeeRepository.observeErpEmployees(),
        app.cardRepository.observeAssignments()
    ) { counts, events, online, erpEmployees, cards ->
        val localCardCodes = cards.map { it.employeeCode }.toSet()
        val missing = erpEmployees.count { it.isActive && it.cardId == null && it.employeeCode !in localCardCodes }
        QueueUiState(counts, events, online, missing)
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

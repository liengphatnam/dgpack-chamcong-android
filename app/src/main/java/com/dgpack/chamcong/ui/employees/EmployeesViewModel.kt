package com.dgpack.chamcong.ui.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.sync.EmployeeSyncEngine
import com.dgpack.chamcong.sync.EmployeeSyncState
import com.dgpack.chamcong.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class FaceStatus {
    /** NV có trong ERP nhưng chưa có khuôn mặt ở máy này lẫn trên server -> cần enroll. */
    MISSING,
    /** Enroll ở máy này, chưa đẩy lên server (chưa có mạng / server chưa có endpoint). */
    LOCAL_PENDING_UPLOAD,
    /** Enroll ở máy này và server đã có bản này. */
    LOCAL_SYNCED,
    /** Server có (enroll ở tablet khác) nhưng máy này chưa tải về (sẽ về ở lần đồng bộ tới). */
    SERVER_ONLY,
    /** Enroll ở máy này nhưng mã NV không tồn tại trong ERP (sẽ bị UnknownEmployee khi đồng bộ). */
    NOT_IN_ERP
}

data class EmployeeRow(
    val employeeCode: String,
    val fullName: String,
    val faceStatus: FaceStatus
)

data class EmployeesUiState(
    val rows: List<EmployeeRow> = emptyList(),
    val showOnlyMissing: Boolean = true,
    val total: Int = 0,
    val withFace: Int = 0,
    val missing: Int = 0,
    val hasErpList: Boolean = false,
    val sync: EmployeeSyncState = EmployeeSyncState(),
    val isOnline: Boolean = false
)

class EmployeesViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val showOnlyMissing = MutableStateFlow(true)

    val uiState: StateFlow<EmployeesUiState> = combine(
        app.employeeRepository.observeErpEmployees(),
        app.employeeRepository.observeAll(),
        app.employeeSyncCoordinator.state,
        NetworkMonitor.observe(app),
        showOnlyMissing
    ) { erpEmployees, enrolled, sync, online, onlyMissing ->
        val enrolledByCode = enrolled.associateBy { it.employeeCode }
        val erpCodes = erpEmployees.map { it.employeeCode }.toSet()

        val fromErp = erpEmployees.filter { it.isActive }.map { erp ->
            val local = enrolledByCode[erp.employeeCode]
            val status = when {
                local != null && EmployeeSyncEngine.needsUpload(local) -> FaceStatus.LOCAL_PENDING_UPLOAD
                local != null -> FaceStatus.LOCAL_SYNCED
                erp.hasFaceOnServer -> FaceStatus.SERVER_ONLY
                else -> FaceStatus.MISSING
            }
            EmployeeRow(erp.employeeCode, erp.fullName, status)
        }
        // Chỉ cảnh báo "không có trong ERP" khi đã kéo được danh sách ERP ít nhất 1 lần —
        // trước đó chưa biết gì nên không kết luận.
        val localOnly = if (erpEmployees.isEmpty()) emptyList() else enrolled
            .filter { it.employeeCode !in erpCodes }
            .map { EmployeeRow(it.employeeCode, it.fullName.ifBlank { it.employeeCode }, FaceStatus.NOT_IN_ERP) }

        val all = fromErp + localOnly
        val missing = all.count { it.faceStatus == FaceStatus.MISSING }
        EmployeesUiState(
            rows = if (onlyMissing) all.filter { it.faceStatus == FaceStatus.MISSING || it.faceStatus == FaceStatus.NOT_IN_ERP } else all,
            showOnlyMissing = onlyMissing,
            total = fromErp.size,
            withFace = fromErp.size - missing,
            missing = missing,
            hasErpList = erpEmployees.isNotEmpty(),
            sync = sync,
            isOnline = online
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmployeesUiState())

    fun setShowOnlyMissing(value: Boolean) = showOnlyMissing.update { value }

    fun syncNow() = app.employeeSyncCoordinator.requestSync()
}

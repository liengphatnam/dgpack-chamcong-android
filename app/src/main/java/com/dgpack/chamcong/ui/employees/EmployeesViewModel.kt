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
import java.text.Normalizer

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

/** Mục đích mở danh sách: chọn người để gán thẻ từ, hay để đăng ký khuôn mặt. */
enum class EmployeePurpose { ASSIGN_CARD, ENROLL_FACE }

data class EmployeeRow(
    val employeeCode: String,
    val fullName: String,
    val faceStatus: FaceStatus,
    /** Thẻ đang gán (máy này ưu tiên, rồi ERP), null = chưa có thẻ. */
    val cardId: String?
)

data class EmployeesUiState(
    val rows: List<EmployeeRow> = emptyList(),
    val query: String = "",
    val showOnlyMissing: Boolean = true,
    val total: Int = 0,
    val withFace: Int = 0,
    val missing: Int = 0,
    val withCard: Int = 0,
    val hasErpList: Boolean = false,
    val sync: EmployeeSyncState = EmployeeSyncState(),
    val isOnline: Boolean = false
)

class EmployeesViewModel(private val app: ChamCongApplication, purpose: EmployeePurpose) : ViewModel() {

    // Gán thẻ: mặc định xem tất cả (ai cũng cần thẻ); đăng ký mặt: mặc định lọc người chưa có mặt.
    private val showOnlyMissing = MutableStateFlow(purpose == EmployeePurpose.ENROLL_FACE)
    private val query = MutableStateFlow("")

    private val base = combine(
        app.employeeRepository.observeErpEmployees(),
        app.employeeRepository.observeAll(),
        app.employeeSyncCoordinator.state,
        NetworkMonitor.observe(app),
        app.cardRepository.observeAssignments()
    ) { erpEmployees, enrolled, sync, online, cards ->
        val enrolledByCode = enrolled.associateBy { it.employeeCode }
        val erpCodes = erpEmployees.map { it.employeeCode }.toSet()
        val localCardByCode = cards.associate { it.employeeCode to it.cardId }

        val fromErp = erpEmployees.filter { it.isActive }.map { erp ->
            val local = enrolledByCode[erp.employeeCode]
            val status = when {
                local != null && EmployeeSyncEngine.needsUpload(local) -> FaceStatus.LOCAL_PENDING_UPLOAD
                local != null -> FaceStatus.LOCAL_SYNCED
                erp.hasFaceOnServer -> FaceStatus.SERVER_ONLY
                else -> FaceStatus.MISSING
            }
            EmployeeRow(erp.employeeCode, erp.fullName, status, localCardByCode[erp.employeeCode] ?: erp.cardId)
        }
        // Chỉ cảnh báo "không có trong ERP" khi đã kéo được danh sách ERP ít nhất 1 lần —
        // trước đó chưa biết gì nên không kết luận.
        val localOnly = if (erpEmployees.isEmpty()) emptyList() else enrolled
            .filter { it.employeeCode !in erpCodes }
            .map { EmployeeRow(it.employeeCode, it.fullName.ifBlank { it.employeeCode }, FaceStatus.NOT_IN_ERP, localCardByCode[it.employeeCode]) }

        Triple(fromErp + localOnly, sync, online) to erpEmployees.isNotEmpty()
    }

    val uiState: StateFlow<EmployeesUiState> = combine(base, showOnlyMissing, query) { (triple, hasErp), onlyMissing, q ->
        val (all, sync, online) = triple
        val missing = all.count { it.faceStatus == FaceStatus.MISSING }
        val filtered = all
            .filter { !onlyMissing || it.faceStatus == FaceStatus.MISSING || it.faceStatus == FaceStatus.NOT_IN_ERP }
            .filter { matches(it, q) }
        EmployeesUiState(
            rows = filtered,
            query = q,
            showOnlyMissing = onlyMissing,
            total = all.count { it.faceStatus != FaceStatus.NOT_IN_ERP },
            withFace = all.count { it.faceStatus != FaceStatus.MISSING && it.faceStatus != FaceStatus.NOT_IN_ERP },
            missing = missing,
            withCard = all.count { it.cardId != null },
            hasErpList = hasErp,
            sync = sync,
            isOnline = online
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmployeesUiState())

    fun setShowOnlyMissing(value: Boolean) = showOnlyMissing.update { value }

    fun setQuery(value: String) = query.update { value }

    fun syncNow() = app.employeeSyncCoordinator.requestSync()

    private fun matches(row: EmployeeRow, q: String): Boolean {
        if (q.isBlank()) return true
        // Gõ toàn số (bàn phím số trong app): khớp phần số của mã NV, vd "0001" hoặc "1" -> DN0001.
        if (q.all { it.isDigit() }) {
            val codeDigits = row.employeeCode.filter { it.isDigit() }
            return codeDigits.contains(q) || codeDigits.trimStart('0') == q.trimStart('0') ||
                (row.cardId?.contains(q) ?: false)
        }
        val needle = fold(q)
        return fold(row.employeeCode).contains(needle) || fold(row.fullName).contains(needle) ||
            (row.cardId?.let { fold(it).contains(needle) } ?: false)
    }

    companion object {
        /** Bỏ dấu tiếng Việt + chữ thường để gõ "nguyen a" vẫn ra "Nguyễn A". */
        fun fold(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('đ', 'd').replace('Đ', 'D')
                .lowercase()
    }
}

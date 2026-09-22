package com.dgpack.chamcong.ui.employees

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.sync.EmployeeSyncState
import com.dgpack.chamcong.util.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.text.Normalizer

data class EmployeeRow(
    val employeeCode: String,
    val fullName: String,
    /** Thẻ đang gán (máy này ưu tiên, rồi ERP), null = chưa có thẻ. */
    val cardId: String?
)

data class EmployeesUiState(
    val rows: List<EmployeeRow> = emptyList(),
    val query: String = "",
    val total: Int = 0,
    val withCard: Int = 0,
    val hasErpList: Boolean = false,
    val sync: EmployeeSyncState = EmployeeSyncState(),
    val isOnline: Boolean = false
)

/** Danh sách NV ERP để gán thẻ từ: tìm theo phần số của mã (bàn phím số trong app) hoặc số thẻ. */
class EmployeesViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<EmployeesUiState> = combine(
        app.employeeRepository.observeErpEmployees(),
        app.cardRepository.observeAssignments(),
        app.employeeSyncCoordinator.state,
        NetworkMonitor.observe(app),
        query
    ) { erpEmployees, cards, sync, online, q ->
        val localCardByCode = cards.associate { it.employeeCode to it.cardId }
        val all = erpEmployees.filter { it.isActive }
            .map { EmployeeRow(it.employeeCode, it.fullName, localCardByCode[it.employeeCode] ?: it.cardId) }
        EmployeesUiState(
            rows = all.filter { matches(it, q) },
            query = q,
            total = all.size,
            withCard = all.count { it.cardId != null },
            hasErpList = erpEmployees.isNotEmpty(),
            sync = sync,
            isOnline = online
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmployeesUiState())

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
        return fold(row.employeeCode).contains(needle) || fold(row.fullName).contains(needle)
    }

    companion object {
        /** Bỏ dấu tiếng Việt + chữ thường. */
        fun fold(s: String): String =
            Normalizer.normalize(s, Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
                .replace('đ', 'd').replace('Đ', 'D')
                .lowercase()
    }
}

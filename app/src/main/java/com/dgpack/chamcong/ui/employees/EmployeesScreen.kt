package com.dgpack.chamcong.ui.employees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R
import com.dgpack.chamcong.sync.EmployeeSyncOutcome
import com.dgpack.chamcong.sync.EmployeeSyncPhase
import com.dgpack.chamcong.sync.EmployeeSyncState
import com.dgpack.chamcong.ui.appViewModel

/**
 * Màn hình quản trị "Nhân viên ERP": danh sách dm.Employee kéo từ Azure, mặc định lọc
 * những người CHƯA có thông tin nhận dạng để admin enroll (chạm vào 1 dòng -> mở Enroll
 * với mã + tên điền sẵn). Nút đồng bộ chạy [EmployeeSyncCoordinator] (kéo NV, đẩy/tải embedding).
 */
@Composable
fun EmployeesScreen(onBack: () -> Unit, onOpenEnroll: (employeeCode: String, fullName: String) -> Unit) {
    val viewModel = appViewModel { EmployeesViewModel(it) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Text(stringResource(R.string.tieu_de_nhan_vien), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            if (!state.isOnline) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.chua_ket_noi_mang),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = viewModel::syncNow,
                    enabled = state.sync.phase != EmployeeSyncPhase.RUNNING
                ) {
                    Text(stringResource(R.string.nut_dong_bo_nhan_vien))
                }
            }

            SyncStatusLine(state.sync, state.hasErpList)

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = state.showOnlyMissing,
                    onClick = { viewModel.setShowOnlyMissing(true) },
                    label = { Text(stringResource(R.string.loc_chua_co_khuon_mat_format, state.missing)) }
                )
                FilterChip(
                    selected = !state.showOnlyMissing,
                    onClick = { viewModel.setShowOnlyMissing(false) },
                    label = { Text(stringResource(R.string.loc_tat_ca_format, state.total)) }
                )
            }

            Text(
                text = stringResource(R.string.nv_bam_de_dang_ky),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (state.rows.isEmpty()) {
                Text(
                    text = if (!state.hasErpList) stringResource(R.string.nv_chua_dong_bo)
                    else stringResource(R.string.nv_danh_sach_rong),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.employeeCode }) { row ->
                    EmployeeRowItem(row, onClick = { onOpenEnroll(row.employeeCode, row.fullName) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SyncStatusLine(sync: EmployeeSyncState, hasErpList: Boolean) {
    val outcome = sync.outcome
    val text: String? = when (sync.phase) {
        EmployeeSyncPhase.IDLE -> null
        EmployeeSyncPhase.RUNNING -> stringResource(R.string.nv_sync_running)
        EmployeeSyncPhase.DONE -> (outcome as? EmployeeSyncOutcome.Completed)?.let {
            val base = stringResource(
                R.string.nv_sync_done_format, it.employees, it.uploaded, it.downloaded, it.missingFace
            )
            if (it.unknownEmployee > 0) base + "\n" + stringResource(R.string.nv_sync_unknown_format, it.unknownEmployee) else base
        }
        EmployeeSyncPhase.ERROR -> when (outcome) {
            EmployeeSyncOutcome.NotConfigured -> stringResource(R.string.nv_sync_not_configured)
            EmployeeSyncOutcome.Unauthorized -> stringResource(R.string.loi_api_key_sai)
            EmployeeSyncOutcome.ServerMisconfigured -> stringResource(R.string.nv_sync_server_misconfigured)
            EmployeeSyncOutcome.EndpointMissing -> stringResource(R.string.nv_sync_endpoint_missing)
            is EmployeeSyncOutcome.NetworkError -> stringResource(R.string.loi_mang)
            is EmployeeSyncOutcome.UnexpectedHttp -> stringResource(R.string.nv_sync_unexpected_format, outcome.code)
            else -> null
        }
    }
    if (text == null) return
    val isError = sync.phase == EmployeeSyncPhase.ERROR
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(8.dp)
        )
    }
    if (isError && !hasErpList) {
        Text(
            text = stringResource(R.string.nv_chua_dong_bo),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun EmployeeRowItem(row: EmployeeRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.fullName, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.employeeCode, style = MaterialTheme.typography.bodyMedium)
        }
        val (label, isWarning) = when (row.faceStatus) {
            FaceStatus.MISSING -> stringResource(R.string.face_status_missing) to true
            FaceStatus.LOCAL_PENDING_UPLOAD -> stringResource(R.string.face_status_local_pending) to false
            FaceStatus.LOCAL_SYNCED -> stringResource(R.string.face_status_local_synced) to false
            FaceStatus.SERVER_ONLY -> stringResource(R.string.face_status_server_only) to false
            FaceStatus.NOT_IN_ERP -> stringResource(R.string.face_status_not_in_erp) to true
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )
    }
}

package com.dgpack.chamcong.ui.employees

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R
import com.dgpack.chamcong.sync.EmployeeSyncOutcome
import com.dgpack.chamcong.sync.EmployeeSyncPhase
import com.dgpack.chamcong.sync.EmployeeSyncState
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.ui.common.NumericKeypad

/**
 * Gán thẻ từ: danh sách Nhân viên ERP, tìm bằng bàn phím số trong app (phần số của mã / số thẻ).
 * Chạm 1 người -> màn quét thẻ để gán, xong tự quay lại đây chọn người kế tiếp.
 */
@Composable
fun EmployeesScreen(onBack: () -> Unit, onSelect: (employeeCode: String, fullName: String) -> Unit) {
    val viewModel = appViewModel { EmployeesViewModel(it) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Icon(Icons.Filled.CreditCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tieu_de_gan_the), style = MaterialTheme.typography.titleLarge)
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

            // Ô hiển thị số đã gõ + bàn phím số trong app (không bật bàn phím điện thoại).
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = state.query.ifEmpty { stringResource(R.string.nv_tim_theo_so) },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (state.query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = null) }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                NumericKeypad(
                    onDigit = { d -> viewModel.setQuery((state.query + d).take(8)) },
                    onBackspace = { viewModel.setQuery(state.query.dropLast(1)) },
                    compact = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = viewModel::syncNow, enabled = state.sync.phase != EmployeeSyncPhase.RUNNING) {
                    Text(stringResource(R.string.nut_dong_bo_nhan_vien))
                }
                Text(
                    stringResource(R.string.nv_da_co_the_format, state.withCard, state.total),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SyncStatusLine(state.sync, state.hasErpList)

            Text(
                text = stringResource(R.string.nv_bam_de_gan_the),
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
                    EmployeeRowItem(row, onClick = { onSelect(row.employeeCode, row.fullName) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SyncStatusLine(sync: EmployeeSyncState, hasErpList: Boolean) {
    val text = when (sync.phase) {
        EmployeeSyncPhase.RUNNING -> stringResource(R.string.nv_sync_running)
        EmployeeSyncPhase.DONE -> (sync.outcome as? EmployeeSyncOutcome.Completed)?.let {
            stringResource(R.string.nv_sync_done_format, it.employees, it.cardsUploaded, it.forgotCardsUploaded)
        }
        EmployeeSyncPhase.ERROR -> when (val o = sync.outcome) {
            EmployeeSyncOutcome.NotConfigured -> stringResource(R.string.nv_sync_not_configured)
            EmployeeSyncOutcome.Unauthorized -> stringResource(R.string.loi_api_key_sai)
            EmployeeSyncOutcome.ServerMisconfigured -> stringResource(R.string.nv_sync_server_misconfigured)
            EmployeeSyncOutcome.EndpointMissing -> stringResource(R.string.nv_sync_endpoint_missing)
            is EmployeeSyncOutcome.NetworkError -> stringResource(R.string.loi_mang)
            is EmployeeSyncOutcome.UnexpectedHttp -> stringResource(R.string.nv_sync_unexpected_format, o.code)
            else -> null
        }
        EmployeeSyncPhase.IDLE -> if (!hasErpList) stringResource(R.string.nv_chua_dong_bo) else null
    } ?: return
    val isError = sync.phase == EmployeeSyncPhase.ERROR
    Surface(
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp)
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.fullName, style = MaterialTheme.typography.bodyLarge)
            Text(text = row.employeeCode, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            Icons.Filled.CreditCard, contentDescription = null,
            tint = if (row.cardId != null) Color(0xFF2ECC71) else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = row.cardId ?: stringResource(R.string.gan_the_chua_co),
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.cardId != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
        )
    }
}

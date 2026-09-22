@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.dgpack.chamcong.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dgpack.chamcong.data.db.AttendanceEventEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.ui.pin.AdminLevel
import com.dgpack.chamcong.util.TimeUtils

@Composable
fun QueueScreen(
    level: AdminLevel,
    onElevate: () -> Unit,
    onBack: () -> Unit,
    onOpenEnroll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEmployees: () -> Unit,
    onOpenAssignCard: () -> Unit,
    onOpenLuckyDraw: () -> Unit,
    onOpenForgotLog: () -> Unit
) {
    val viewModel = appViewModel { QueueViewModel(it) }
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
                Text(stringResource(R.string.tieu_de_hang_doi), style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "  ·  " + stringResource(R.string.quyen_cap_format, if (level == AdminLevel.LEVEL_2) 2 else 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {

            NetworkBadge(isOnline = state.isOnline)

            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CountChip(stringResource(R.string.trang_thai_pending), state.counts.pending)
                CountChip(stringResource(R.string.trang_thai_synced), state.counts.synced)
                CountChip(stringResource(R.string.trang_thai_unknown_employee), state.counts.unknownEmployee)
                CountChip(stringResource(R.string.trang_thai_failed), state.counts.failed)
            }

            // FlowRow thay vì Row thường — ở màn hình dọc (portrait) hẹp, đủ 4 nút với
            // nhãn tiếng Việt dài sẽ tràn ra ngoài và bị cắt mất (đã xảy ra thực tế:
            // nút "Cài đặt" nằm cuối bị đẩy khuất khỏi màn hình). FlowRow tự xuống dòng.
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = viewModel::syncNow) { Text(stringResource(R.string.nut_dong_bo_ngay)) }
                if (state.counts.unknownEmployee > 0) {
                    OutlinedButton(onClick = viewModel::retryUnknownEmployees) {
                        Text(stringResource(R.string.nut_thu_lai_unknown))
                    }
                }
                Button(onClick = onOpenAssignCard) { Text(stringResource(R.string.nut_gan_the)) }
                OutlinedButton(onClick = onOpenEnroll) { Text(stringResource(R.string.tieu_de_enroll)) }
                OutlinedButton(onClick = onOpenEmployees) {
                    Text(
                        if (state.missingFaceCount > 0) stringResource(R.string.nut_nhan_vien_format, state.missingFaceCount)
                        else stringResource(R.string.tieu_de_nhan_vien)
                    )
                }
                // Cấp 1 chỉ được đồng bộ + đăng ký khuôn mặt; sổ trúng thưởng và cài đặt cần cấp 2.
                if (level == AdminLevel.LEVEL_2) {
                    OutlinedButton(onClick = onOpenForgotLog) { Text(stringResource(R.string.nut_nhat_ky_quen_the)) }
                    OutlinedButton(onClick = onOpenLuckyDraw) { Text(stringResource(R.string.tieu_de_trung_thuong)) }
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.tieu_de_cai_dat)) }
                } else {
                    OutlinedButton(onClick = onElevate) { Text(stringResource(R.string.nut_mat_ma_cap_2)) }
                }
            }

            if (state.counts.unknownEmployee > 0) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.canh_bao_unknown_employee),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.events, key = { it.localId }) { event ->
                    EventRow(event)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun NetworkBadge(isOnline: Boolean) {
    if (!isOnline) {
        Surface(color = MaterialTheme.colorScheme.errorContainer) {
            Text(
                text = stringResource(R.string.chua_ket_noi_mang),
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = count.toString(), style = MaterialTheme.typography.titleLarge)
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EventRow(event: AttendanceEventEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(text = event.employeeCode, style = MaterialTheme.typography.bodyLarge)
            // Lưu/gửi server luôn là UTC (bắt buộc theo mục [5.2]) — chỉ hiển thị quy đổi
            // sang giờ Việt Nam ở đây cho dễ đọc, không đổi giá trị gốc trong DB.
            Text(text = TimeUtils.apiStringToVnDisplay(event.eventTimeUtc), style = MaterialTheme.typography.bodyMedium)
        }
        Text(text = statusLabel(event.syncStatus), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun statusLabel(status: String): String = when (status) {
    SyncStatus.PENDING -> stringResource(R.string.trang_thai_pending)
    SyncStatus.SYNCED -> stringResource(R.string.trang_thai_synced)
    SyncStatus.DUPLICATE -> stringResource(R.string.trang_thai_duplicate)
    SyncStatus.UNKNOWN_EMPLOYEE -> stringResource(R.string.trang_thai_unknown_employee)
    else -> stringResource(R.string.trang_thai_failed)
}

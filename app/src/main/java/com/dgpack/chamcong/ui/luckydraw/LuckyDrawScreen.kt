package com.dgpack.chamcong.ui.luckydraw

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.util.TimeUtils
import kotlin.math.roundToInt

/**
 * Sổ người trúng thưởng lon nước ngọt (khu quản trị) — nhóm theo ngày, nhân sự bấm
 * "Đã phát" sau khi trao thưởng. Dòng nào đã đẩy lên ERP sẽ hiện "Đã đồng bộ".
 */
@Composable
fun LuckyDrawScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { LuckyDrawViewModel(it) }
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
                Text(stringResource(R.string.tieu_de_trung_thuong), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.ld_tong_format, state.totalWins, state.totalCans, state.unclaimed),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(12.dp)
                )
            }

            if (state.days.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.ld_rong), style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                state.days.forEach { day ->
                    item(key = "day-${day.date}") {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.ld_ngay_format,
                                    displayDate(day.date), day.totalCans, day.unclaimed
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(day.wins, key = { it.localId }) { win ->
                        WinRow(win, onToggleClaimed = { viewModel.setClaimed(win, !win.claimed) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun WinRow(win: LuckyDrawWinEntity, onToggleClaimed: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${TimeUtils.apiStringToVnTime(win.wonAtUtc)}  ${win.fullName}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = win.employeeCode + "  ·  " + stringResource(R.string.ld_lon_format, win.cans) +
                    "  ·  " + reasonLabel(win.reason) +
                    "  ·  " + stringResource(R.string.ld_xac_suat_format, (win.chance * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = when (win.syncStatus) {
                    SyncStatus.SYNCED -> stringResource(R.string.trang_thai_synced)
                    SyncStatus.UNKNOWN_EMPLOYEE -> stringResource(R.string.trang_thai_unknown_employee)
                    else -> stringResource(R.string.trang_thai_pending)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (win.claimed) {
            OutlinedButton(onClick = onToggleClaimed) { Text(stringResource(R.string.ld_da_phat)) }
        } else {
            Button(onClick = onToggleClaimed) { Text(stringResource(R.string.ld_chua_phat)) }
        }
    }
}

@Composable
private fun reasonLabel(reason: String): String = when (reason) {
    LuckyDrawReason.BIRTHDAY -> stringResource(R.string.ld_ly_do_sinh_nhat)
    else -> stringResource(R.string.ld_ly_do_ngau_nhien)
}

/** "2026-09-16" -> "16/09/2026" cho người Việt đọc. */
private fun displayDate(isoDate: String): String {
    val parts = isoDate.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else isoDate
}

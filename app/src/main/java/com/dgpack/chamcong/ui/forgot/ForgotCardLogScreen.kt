package com.dgpack.chamcong.ui.forgot

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.R
import com.dgpack.chamcong.card.CardRepository
import com.dgpack.chamcong.data.db.ForgotCardLogEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** Số tháng xem lùi được (dữ liệu chỉ giữ 2 tháng trên máy). */
private const val MAX_MONTHS_BACK = 2

data class ForgotLogUiState(
    val month: String = "",
    val monthsBack: Int = 0,
    val logs: List<ForgotCardLogEntity> = emptyList()
) {
    val unsynced: Int get() = logs.count { it.syncStatus != SyncStatus.SYNCED }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ForgotCardLogViewModel(private val app: ChamCongApplication) : ViewModel() {
    private val monthsBack = MutableStateFlow(0)

    val uiState: StateFlow<ForgotLogUiState> = monthsBack
        .flatMapLatest { back ->
            val month = CardRepository.monthKey(TimeUtils.vnToday().minusMonths(back.toLong()))
            combine(app.cardRepository.observeForgotLogs(month), MutableStateFlow(month)) { logs, m ->
                ForgotLogUiState(month = m, monthsBack = back, logs = logs)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ForgotLogUiState())

    fun previousMonth() = monthsBack.update { (it + 1).coerceAtMost(MAX_MONTHS_BACK) }
    fun nextMonth() = monthsBack.update { (it - 1).coerceAtLeast(0) }
}

/**
 * Nhật ký quên mang thẻ theo tháng: ảnh nhỏ bằng chứng, tên, mã, ngày giờ, trạng thái đồng bộ
 * lên Azure. Dữ liệu giữ 2 tháng trên máy.
 */
@Composable
fun ForgotCardLogScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { ForgotCardLogViewModel(it) }
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
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tieu_de_nhat_ky_quen_the), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = viewModel::previousMonth, enabled = state.monthsBack < MAX_MONTHS_BACK) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.nk_thang_format, monthLabel(state.month)), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.nk_tong_format, state.logs.size, state.unsynced),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = viewModel::nextMonth, enabled = state.monthsBack > 0) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (state.logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.nk_rong), style = MaterialTheme.typography.bodyLarge)
                }
                return@Column
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.logs, key = { it.localId }) { log ->
                    LogRow(log)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: ForgotCardLogEntity) {
    val bitmap = remember(log.localId) {
        log.photoJpeg?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp))
            )
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.padding(18.dp).size(28.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(log.fullName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${log.employeeCode}  ·  ${TimeUtils.apiStringToVnDisplay(log.eventTimeUtc)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (log.syncStatus == SyncStatus.SYNCED) Icons.Filled.CloudDone else Icons.Filled.CloudUpload,
            contentDescription = null,
            tint = if (log.syncStatus == SyncStatus.SYNCED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun monthLabel(month: String): String {
    val p = month.split("-")
    return if (p.size == 2) "${p[1]}/${p[0]}" else month
}

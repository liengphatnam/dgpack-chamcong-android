package com.dgpack.chamcong.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R
import com.dgpack.chamcong.sync.LastSyncKind
import com.dgpack.chamcong.ui.appViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Text(stringResource(R.string.tieu_de_cai_dat), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state.lastSync.kind) {
                LastSyncKind.UNAUTHORIZED -> WarningBanner(stringResource(R.string.loi_api_key_sai))
                LastSyncKind.NETWORK_ERROR -> WarningBanner(stringResource(R.string.loi_mang))
                else -> {}
            }

            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { Text(stringResource(R.string.url_server)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::onApiKeyChange,
                label = { Text(stringResource(R.string.api_key)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.deviceCode,
                onValueChange = viewModel::onDeviceCodeChange,
                label = { Text(stringResource(R.string.ma_thiet_bi)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.minConfidencePercent,
                onValueChange = viewModel::onMinConfidenceChange,
                label = { Text(stringResource(R.string.nguong_do_tin_cay)) },
                supportingText = { Text(stringResource(R.string.nguong_do_tin_cay_goi_y)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.debounceMinutes,
                onValueChange = viewModel::onDebounceChange,
                label = { Text(stringResource(R.string.nguong_debounce_phut)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.adminPin,
                onValueChange = viewModel::onAdminPinChange,
                label = { Text(stringResource(R.string.ma_pin_quan_tri)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = state.pinError,
                supportingText = {
                    if (state.pinError) Text(stringResource(R.string.loi_pin_khong_hop_le))
                },
                modifier = Modifier.fillMaxWidth()
            )

            // ---- Chương trình trúng thưởng lon nước ngọt (tháng 8–9/2026) ----
            Text(stringResource(R.string.ld_tieu_de_cai_dat), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.ld_bat), modifier = Modifier.weight(1f))
                Switch(checked = state.luckyDrawEnabled, onCheckedChange = viewModel::onLuckyDrawEnabledChange)
            }
            OutlinedTextField(
                value = state.luckyDrawStartDate,
                onValueChange = viewModel::onLuckyDrawStartChange,
                label = { Text(stringResource(R.string.ld_ngay_bat_dau)) },
                isError = state.luckyDateError,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.luckyDrawEndDate,
                onValueChange = viewModel::onLuckyDrawEndChange,
                label = { Text(stringResource(R.string.ld_ngay_ket_thuc)) },
                isError = state.luckyDateError,
                supportingText = {
                    if (state.luckyDateError) Text(stringResource(R.string.ld_loi_ngay))
                },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.luckyDrawDailyQuota,
                onValueChange = viewModel::onLuckyDrawQuotaChange,
                label = { Text(stringResource(R.string.ld_quota)) },
                supportingText = { Text(stringResource(R.string.ld_quota_goi_y)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.nut_luu_cai_dat))
            }

            if (state.savedOnce) {
                Text(
                    text = stringResource(R.string.da_luu_cai_dat),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp).fillMaxWidth()
        )
    }
}

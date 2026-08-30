package com.dgpack.chamcong.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.sync.LastSyncInfo
import com.dgpack.chamcong.sync.SyncStatusHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val deviceCode: String = "",
    val similarityThreshold: String = "",
    val debounceMinutes: String = "",
    val adminPin: String = "",
    val savedOnce: Boolean = false,
    val pinError: Boolean = false,
    val lastSync: LastSyncInfo = LastSyncInfo()
)

class SettingsViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _state = MutableStateFlow(toUiState(app.settingsRepository.current(), LastSyncInfo()))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(app.settingsRepository.settings, SyncStatusHolder.lastSync) { settings, lastSync ->
                toUiState(settings, lastSync)
            }.collect { merged ->
                // Giữ lại nội dung người dùng đang gõ dở, chỉ cập nhật lastSync để tránh
                // ghi đè input khi StateFlow phát lại do nguồn khác thay đổi.
                _state.value = _state.value.copy(lastSync = merged.lastSync)
            }
        }
    }

    private fun toUiState(settings: AppSettings, lastSync: LastSyncInfo) = SettingsUiState(
        serverUrl = settings.serverUrl,
        apiKey = settings.apiKey,
        deviceCode = settings.deviceCode,
        similarityThreshold = settings.similarityThreshold.toString(),
        debounceMinutes = settings.debounceMinutes.toString(),
        adminPin = settings.adminPin,
        lastSync = lastSync
    )

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v, savedOnce = false) }
    fun onApiKeyChange(v: String) = _state.update { it.copy(apiKey = v, savedOnce = false) }
    fun onDeviceCodeChange(v: String) = _state.update { it.copy(deviceCode = v, savedOnce = false) }
    fun onSimilarityChange(v: String) = _state.update { it.copy(similarityThreshold = v, savedOnce = false) }
    fun onDebounceChange(v: String) = _state.update { it.copy(debounceMinutes = v, savedOnce = false) }
    fun onAdminPinChange(v: String) {
        // Chỉ nhận số, tối đa 4 ký tự — bàn phím số nên hiếm khi gõ ký tự khác nhưng lọc
        // cho chắc (dán text chẳng hạn).
        val digitsOnly = v.filter { it.isDigit() }.take(4)
        _state.update { it.copy(adminPin = digitsOnly, savedOnce = false, pinError = false) }
    }

    fun save() {
        val current = _state.value
        // PIN phải rỗng (không khoá) hoặc đúng 4 số — không cho lưu PIN nửa chừng (1-3 số)
        // vì sẽ không bao giờ khớp được lúc nhập ở PinEntryScreen.
        if (current.adminPin.isNotEmpty() && current.adminPin.length != 4) {
            _state.value = current.copy(pinError = true)
            return
        }

        val settings = AppSettings(
            serverUrl = current.serverUrl.trim(),
            apiKey = current.apiKey.trim(),
            deviceCode = current.deviceCode.trim(),
            similarityThreshold = current.similarityThreshold.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.6f,
            debounceMinutes = current.debounceMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 5,
            adminPin = current.adminPin
        )
        app.settingsRepository.save(settings)
        _state.value = toUiState(settings, current.lastSync).copy(savedOnce = true)
    }
}

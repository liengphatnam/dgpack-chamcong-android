package com.dgpack.chamcong.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.data.prefs.DEFAULT_LUCKY_DRAW_DAILY_QUOTA
import com.dgpack.chamcong.luckydraw.LuckyDrawEngine
import com.dgpack.chamcong.data.prefs.DEFAULT_MIN_CONFIDENCE_PERCENT
import com.dgpack.chamcong.data.prefs.MAX_ALLOWED_CONFIDENCE_PERCENT
import com.dgpack.chamcong.data.prefs.MIN_ALLOWED_CONFIDENCE_PERCENT
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
    val minConfidencePercent: String = "",
    val debounceMinutes: String = "",
    val adminPin: String = "",
    val luckyDrawEnabled: Boolean = true,
    val luckyDrawStartDate: String = "",
    val luckyDrawEndDate: String = "",
    val luckyDrawDailyQuota: String = "",
    val savedOnce: Boolean = false,
    val pinError: Boolean = false,
    val luckyDateError: Boolean = false,
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
        minConfidencePercent = settings.minConfidencePercent.toString(),
        debounceMinutes = settings.debounceMinutes.toString(),
        adminPin = settings.adminPin,
        luckyDrawEnabled = settings.luckyDrawEnabled,
        luckyDrawStartDate = settings.luckyDrawStartDate,
        luckyDrawEndDate = settings.luckyDrawEndDate,
        luckyDrawDailyQuota = settings.luckyDrawDailyQuota.toString(),
        lastSync = lastSync
    )

    fun onServerUrlChange(v: String) = _state.update { it.copy(serverUrl = v, savedOnce = false) }
    fun onApiKeyChange(v: String) = _state.update { it.copy(apiKey = v, savedOnce = false) }
    fun onDeviceCodeChange(v: String) = _state.update { it.copy(deviceCode = v, savedOnce = false) }
    fun onMinConfidenceChange(v: String) = _state.update { it.copy(minConfidencePercent = v.filter { c -> c.isDigit() }.take(3), savedOnce = false) }
    fun onDebounceChange(v: String) = _state.update { it.copy(debounceMinutes = v, savedOnce = false) }
    fun onLuckyDrawEnabledChange(v: Boolean) = _state.update { it.copy(luckyDrawEnabled = v, savedOnce = false) }
    fun onLuckyDrawStartChange(v: String) = _state.update { it.copy(luckyDrawStartDate = v, savedOnce = false, luckyDateError = false) }
    fun onLuckyDrawEndChange(v: String) = _state.update { it.copy(luckyDrawEndDate = v, savedOnce = false, luckyDateError = false) }
    fun onLuckyDrawQuotaChange(v: String) = _state.update { it.copy(luckyDrawDailyQuota = v.filter { c -> c.isDigit() }.take(3), savedOnce = false) }
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
        // Ngày đợt trúng thưởng phải đúng yyyy-MM-dd và bắt đầu <= kết thúc, nếu không engine sẽ
        // coi là tắt — báo lỗi ngay để admin không tưởng là đang chạy.
        val luckyStart = LuckyDrawEngine.parseDate(current.luckyDrawStartDate)
        val luckyEnd = LuckyDrawEngine.parseDate(current.luckyDrawEndDate)
        if (current.luckyDrawEnabled && (luckyStart == null || luckyEnd == null || luckyStart.isAfter(luckyEnd))) {
            _state.value = current.copy(luckyDateError = true)
            return
        }

        val settings = AppSettings(
            serverUrl = current.serverUrl.trim(),
            apiKey = current.apiKey.trim(),
            deviceCode = current.deviceCode.trim(),
            // Không cho hạ dưới 50% — thấp hơn nữa gần như chắc chắn nhận nhầm người.
            minConfidencePercent = current.minConfidencePercent.toIntOrNull()
                ?.coerceIn(MIN_ALLOWED_CONFIDENCE_PERCENT, MAX_ALLOWED_CONFIDENCE_PERCENT)
                ?: DEFAULT_MIN_CONFIDENCE_PERCENT,
            debounceMinutes = current.debounceMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 5,
            adminPin = current.adminPin,
            luckyDrawEnabled = current.luckyDrawEnabled,
            luckyDrawStartDate = current.luckyDrawStartDate.trim(),
            luckyDrawEndDate = current.luckyDrawEndDate.trim(),
            luckyDrawDailyQuota = current.luckyDrawDailyQuota.toIntOrNull()?.coerceIn(0, 999)
                ?: DEFAULT_LUCKY_DRAW_DAILY_QUOTA
        )
        app.settingsRepository.save(settings)
        _state.value = toUiState(settings, current.lastSync).copy(savedOnce = true)
    }
}

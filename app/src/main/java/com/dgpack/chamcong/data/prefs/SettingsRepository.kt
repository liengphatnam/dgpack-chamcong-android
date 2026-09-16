package com.dgpack.chamcong.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val DEFAULT_MIN_CONFIDENCE_PERCENT = 80
const val MIN_ALLOWED_CONFIDENCE_PERCENT = 50
const val MAX_ALLOWED_CONFIDENCE_PERCENT = 100

data class AppSettings(
    val serverUrl: String = "https://dgperp.azurewebsites.net",
    val apiKey: String = "",
    val deviceCode: String = "",
    // Quy tắc nghiệp vụ: độ tin cậy (cosine similarity quy ra %) phải >= 80% mới coi là
    // nhận diện được; thấp hơn thì hiện "Hệ thống chưa nhận dạng được" và KHÔNG ghi sự kiện.
    val minConfidencePercent: Int = DEFAULT_MIN_CONFIDENCE_PERCENT,
    // Đề xuất trong tài liệu: chỉ ghi sự kiện mới nếu cách lần trước >= 5 phút.
    val debounceMinutes: Int = 5,
    // Phase 2: PIN 4 số khoá màn hình quản trị (Enroll/Cài đặt/Hàng đợi). Rỗng = không khoá.
    val adminPin: String = ""
)

/**
 * Lưu cấu hình thiết bị (API key, server URL, device code, ngưỡng) bằng
 * EncryptedSharedPreferences — KHÔNG hardcode API key trong source (mục [6.1]).
 */
class SettingsRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chamcong_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun load(): AppSettings = AppSettings(
        serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: AppSettings().serverUrl,
        apiKey = prefs.getString(KEY_API_KEY, null) ?: "",
        deviceCode = prefs.getString(KEY_DEVICE_CODE, null) ?: "",
        minConfidencePercent = prefs.getInt(KEY_MIN_CONFIDENCE_PERCENT, AppSettings().minConfidencePercent),
        debounceMinutes = prefs.getInt(KEY_DEBOUNCE_MINUTES, AppSettings().debounceMinutes),
        adminPin = prefs.getString(KEY_ADMIN_PIN, null) ?: ""
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_DEVICE_CODE, settings.deviceCode)
            .putInt(KEY_MIN_CONFIDENCE_PERCENT, settings.minConfidencePercent)
            .putInt(KEY_DEBOUNCE_MINUTES, settings.debounceMinutes)
            .putString(KEY_ADMIN_PIN, settings.adminPin)
            .apply()
        _settings.value = settings
    }

    fun current(): AppSettings = _settings.value

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_CODE = "device_code"
        // Key mới, tách khỏi "similarity_threshold" (float 0..1) của bản cũ để máy đã cài
        // bản cũ tự nhận mặc định 80% thay vì kế thừa ngưỡng 0.6 quá lỏng.
        private const val KEY_MIN_CONFIDENCE_PERCENT = "min_confidence_percent"
        private const val KEY_DEBOUNCE_MINUTES = "debounce_minutes"
        private const val KEY_ADMIN_PIN = "admin_pin"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

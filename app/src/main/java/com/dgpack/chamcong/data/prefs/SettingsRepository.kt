package com.dgpack.chamcong.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val serverUrl: String = "https://dgperp.azurewebsites.net",
    val apiKey: String = "",
    val deviceCode: String = "",
    // Đề xuất trong tài liệu: similarity >= 0.6 mới coi là nhận diện đúng.
    val similarityThreshold: Float = 0.6f,
    // Đề xuất trong tài liệu: chỉ ghi sự kiện mới nếu cách lần trước >= 5 phút.
    val debounceMinutes: Int = 5
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
        similarityThreshold = prefs.getFloat(KEY_SIMILARITY_THRESHOLD, AppSettings().similarityThreshold),
        debounceMinutes = prefs.getInt(KEY_DEBOUNCE_MINUTES, AppSettings().debounceMinutes)
    )

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_SERVER_URL, settings.serverUrl)
            .putString(KEY_API_KEY, settings.apiKey)
            .putString(KEY_DEVICE_CODE, settings.deviceCode)
            .putFloat(KEY_SIMILARITY_THRESHOLD, settings.similarityThreshold)
            .putInt(KEY_DEBOUNCE_MINUTES, settings.debounceMinutes)
            .apply()
        _settings.value = settings
    }

    fun current(): AppSettings = _settings.value

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_DEVICE_CODE = "device_code"
        private const val KEY_SIMILARITY_THRESHOLD = "similarity_threshold"
        private const val KEY_DEBOUNCE_MINUTES = "debounce_minutes"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository =
            instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
    }
}

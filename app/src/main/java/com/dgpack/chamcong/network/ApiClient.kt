package com.dgpack.chamcong.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Mục [6.5]: HTTPS bắt buộc cho mọi request tới server — từ chối tạo client nếu
     * URL không phải https:// thay vì âm thầm thất bại lúc gọi API.
     */
    fun create(baseUrl: String): AttendanceApi {
        val normalized = baseUrl.trim().let { if (it.endsWith("/")) it else "$it/" }
        require(normalized.startsWith("https://")) {
            "Địa chỉ server phải bắt đầu bằng https://"
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AttendanceApi::class.java)
    }
}

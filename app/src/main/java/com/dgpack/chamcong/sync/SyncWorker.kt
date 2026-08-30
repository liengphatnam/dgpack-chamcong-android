package com.dgpack.chamcong.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.network.ApiClient

/**
 * Chạy nền định kỳ qua WorkManager (mục [5.6]). Logic đồng bộ thật nằm ở [SyncEngine]
 * để test độc lập được — worker này chỉ có nhiệm vụ nối dây (đọc settings, tạo
 * ApiClient, gọi SyncEngine, cập nhật SyncStatusHolder cho UI).
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as ChamCongApplication
        val settings = app.settingsRepository.current()

        if (settings.apiKey.isBlank() || settings.deviceCode.isBlank()) {
            // Chưa cấu hình ở màn hình Cài đặt — không có gì để làm, không phải lỗi.
            return Result.success()
        }

        val api = try {
            ApiClient.create(settings.serverUrl)
        } catch (e: IllegalArgumentException) {
            SyncStatusHolder.update(LastSyncKind.UNEXPECTED)
            return Result.failure()
        }

        val engine = SyncEngine(app.attendanceRepository)
        return when (val outcome = engine.syncPending(api, settings.apiKey, settings.deviceCode)) {
            is SyncOutcome.Completed -> {
                SyncStatusHolder.update(LastSyncKind.OK)
                Result.success()
            }
            SyncOutcome.Unauthorized -> {
                SyncStatusHolder.update(LastSyncKind.UNAUTHORIZED)
                Result.failure()
            }
            SyncOutcome.ServerMisconfigured -> {
                SyncStatusHolder.update(LastSyncKind.SERVER_MISCONFIGURED)
                Result.success()
            }
            is SyncOutcome.NetworkError -> {
                SyncStatusHolder.update(LastSyncKind.NETWORK_ERROR)
                Result.success()
            }
            is SyncOutcome.UnexpectedHttp -> {
                SyncStatusHolder.update(LastSyncKind.UNEXPECTED)
                Result.failure()
            }
        }
    }
}

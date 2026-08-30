package com.dgpack.chamcong.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncManager {
    private const val PERIODIC_WORK_NAME = "chamcong_sync_periodic"
    private const val ONE_TIME_WORK_NAME = "chamcong_sync_now"

    /**
     * Mục [5.6] đề xuất 10 phút, nhưng WorkManager của Android ép buộc chu kỳ tối
     * thiểu cho PeriodicWorkRequest là 15 phút (giới hạn nền tảng, không phải lựa
     * chọn của app) — bù lại bằng nút "Đồng bộ ngay" thủ công cho các trường hợp cần
     * gấp. Tài liệu cũng nói rõ không cần real-time, trễ vài phút không sao.
     */
    private const val PERIODIC_INTERVAL_MINUTES = 15L

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Nút "Đồng bộ ngay" ở màn hình quản trị (mục [5.6], [7]). */
    fun syncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

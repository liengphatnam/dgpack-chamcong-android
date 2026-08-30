package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.network.SyncEventRequest
import com.dgpack.chamcong.network.SyncEventResult
import java.io.IOException

sealed class SyncOutcome {
    data class Completed(val inserted: Int, val duplicate: Int, val unknownEmployee: Int) : SyncOutcome()
    object Unauthorized : SyncOutcome()
    object ServerMisconfigured : SyncOutcome()
    data class NetworkError(val message: String?) : SyncOutcome()
    data class UnexpectedHttp(val code: Int) : SyncOutcome()
}

/**
 * Logic đồng bộ thuần (tách khỏi CoroutineWorker) để test được bằng MockWebServer
 * (xem SyncEngineTest) mà không cần khởi tạo WorkManager thật.
 * Tuân thủ đúng contract mục [5] — 3 trạng thái Inserted/Duplicate/UnknownEmployee,
 * và các mã lỗi HTTP ở mục [5.5].
 */
class SyncEngine(private val attendanceRepository: AttendanceSyncSource) {

    suspend fun syncPending(
        api: AttendanceApi,
        apiKey: String,
        deviceCode: String,
        batchSize: Int = 200,
        maxBatches: Int = 20
    ): SyncOutcome {
        var inserted = 0
        var duplicate = 0
        var unknown = 0

        repeat(maxBatches) {
            val batch = attendanceRepository.getPendingBatch(batchSize)
            if (batch.isEmpty()) {
                return SyncOutcome.Completed(inserted, duplicate, unknown)
            }

            val requestBody = batch.map { SyncEventRequest(it.employeeCode, it.eventTimeUtc, deviceCode) }
            val response = try {
                api.syncEvents(apiKey, requestBody)
            } catch (e: IOException) {
                // Lỗi mạng (timeout, không có kết nối) — giữ nguyên Pending, thử lại lần
                // WorkManager chạy tiếp theo (mục [5.5]), KHÔNG chặn bằng số lần thử.
                return SyncOutcome.NetworkError(e.message)
            }

            when (response.code()) {
                200 -> {
                    val results: List<SyncEventResult> = response.body().orEmpty()
                    batch.forEachIndexed { index, event ->
                        when (results.getOrNull(index)?.status) {
                            SyncEventResult.STATUS_INSERTED -> {
                                attendanceRepository.markStatus(event, SyncStatus.SYNCED, syncedAtNow = true)
                                inserted++
                            }
                            SyncEventResult.STATUS_DUPLICATE -> {
                                // Coi như thành công — không phải lỗi (mục [5.4]).
                                attendanceRepository.markStatus(event, SyncStatus.SYNCED, syncedAtNow = true)
                                duplicate++
                            }
                            SyncEventResult.STATUS_UNKNOWN_EMPLOYEE -> {
                                attendanceRepository.markStatus(event, SyncStatus.UNKNOWN_EMPLOYEE, syncedAtNow = false)
                                unknown++
                            }
                            else -> attendanceRepository.markRetryLater(event)
                        }
                    }
                }
                401 -> {
                    // API key sai/thiếu — dừng đồng bộ hẳn, không lặp lại request (mục [5.5]).
                    return SyncOutcome.Unauthorized
                }
                503 -> {
                    // Server chưa cấu hình API key — lỗi tạm thời, retry theo lịch bình thường.
                    return SyncOutcome.ServerMisconfigured
                }
                else -> {
                    batch.forEach { attendanceRepository.markRetryLater(it) }
                    return SyncOutcome.UnexpectedHttp(response.code())
                }
            }
        }
        return SyncOutcome.Completed(inserted, duplicate, unknown)
    }
}

package com.dgpack.chamcong.sync

import com.dgpack.chamcong.card.CardRepository
import com.dgpack.chamcong.card.CardScanBus
import com.dgpack.chamcong.data.db.EmployeeMonthSummaryEntity
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.network.CardAssignmentUploadRequest
import com.dgpack.chamcong.network.CardAssignmentUploadResult
import com.dgpack.chamcong.network.ForgotCardUploadRequest
import com.dgpack.chamcong.network.ForgotCardUploadResult
import com.dgpack.chamcong.network.LuckyDrawUploadRequest
import com.dgpack.chamcong.network.LuckyDrawUploadResult
import com.dgpack.chamcong.util.TimeUtils
import retrofit2.Response
import java.io.IOException

sealed class EmployeeSyncOutcome {
    /**
     * @param employees          số NV kéo về từ ERP
     * @param luckyDrawsUploaded số dòng sổ trúng thưởng vừa đẩy lên
     * @param cardsUploaded      số thẻ gán trên máy vừa đẩy lên
     * @param forgotCardsUploaded số dòng nhật ký quên thẻ vừa đẩy lên
     * @param monthSummaryRows   số NV có chi tiết công tháng kéo về
     */
    data class Completed(
        val employees: Int,
        val luckyDrawsUploaded: Int = 0,
        val cardsUploaded: Int = 0,
        val forgotCardsUploaded: Int = 0,
        val monthSummaryRows: Int = 0
    ) : EmployeeSyncOutcome()

    /** Chưa nhập API key/DeviceCode ở Cài đặt — không gọi mạng. */
    object NotConfigured : EmployeeSyncOutcome()
    object Unauthorized : EmployeeSyncOutcome()
    object ServerMisconfigured : EmployeeSyncOutcome()
    /** Server ERP chưa triển khai endpoint (404) — cần deploy phía DGP.ERP trước, xem API_FACE_SYNC.md. */
    object EndpointMissing : EmployeeSyncOutcome()
    data class NetworkError(val message: String?) : EmployeeSyncOutcome()
    data class UnexpectedHttp(val code: Int) : EmployeeSyncOutcome()
}

/**
 * Đồng bộ NHẸ giữa thiết bị và ERP (API_FACE_SYNC.md):
 *  1. Kéo danh sách dm.Employee về (tên, ngày sinh, thẻ, trễ/sớm, khen thưởng) — bước CHÍNH,
 *     lỗi ở đây trả về ngay.
 *  Các bước phụ (lỗi/404 -> giữ Pending, thử lại lần sau, KHÔNG làm hỏng kết quả):
 *  2. Đẩy sổ trúng thưởng lon nước ngọt.
 *  3. Đẩy thẻ từ gán trên máy.
 *  4. Đẩy nhật ký quên thẻ (kèm ảnh bằng chứng).
 *  5. Kéo chi tiết công tháng hiện tại về cache.
 * Logic thuần, test bằng MockWebServer (EmployeeSyncEngineTest, LuckyDrawSyncTest).
 */
class EmployeeSyncEngine(
    private val source: EmployeeSyncSource,
    private val luckyDrawSource: LuckyDrawSyncSource? = null,
    private val cardSource: CardSyncSource? = null
) {

    suspend fun sync(api: AttendanceApi, apiKey: String, deviceCode: String): EmployeeSyncOutcome {
        // ---- 1. Danh sách nhân viên ERP ----
        val employeesResponse = try {
            api.getEmployees(apiKey)
        } catch (e: IOException) {
            return EmployeeSyncOutcome.NetworkError(e.message)
        }
        httpFailure(employeesResponse)?.let { return it }

        val now = TimeUtils.nowUtcIso()
        val erpEmployees = employeesResponse.body().orEmpty().map { dto ->
            ErpEmployeeEntity(
                employeeCode = dto.employeeCode.trim(),
                fullName = dto.fullName.trim(),
                isActive = dto.isActive,
                hasFaceOnServer = dto.hasFaceEmbedding,
                faceUpdatedAt = dto.faceUpdatedAt,
                syncedAt = now,
                // .NET hay trả "1990-08-20T00:00:00" -> chỉ giữ phần ngày.
                birthDate = dto.birthDate?.trim()?.take(10)?.takeIf { it.length == 10 },
                lateEarlyCount30d = dto.lateEarlyCount30d,
                commendationCount = dto.commendationCount,
                cardId = dto.cardId?.let { CardScanBus.normalize(it) }?.ifEmpty { null }
            )
        }
        source.replaceErpEmployees(erpEmployees)

        // ---- Các bước phụ ----
        val luckyDrawsUploaded = uploadLuckyDraws(api, apiKey, deviceCode)
        val cardsUploaded = uploadCardAssignments(api, apiKey, deviceCode)
        val forgotUploaded = uploadForgotCards(api, apiKey, deviceCode)
        val summaryRows = downloadMonthSummary(api, apiKey)

        return EmployeeSyncOutcome.Completed(
            employees = erpEmployees.size,
            luckyDrawsUploaded = luckyDrawsUploaded,
            cardsUploaded = cardsUploaded,
            forgotCardsUploaded = forgotUploaded,
            monthSummaryRows = summaryRows
        )
    }

    /** Sổ trúng thưởng. Saved/Duplicate = xong; UnknownEmployee = đánh dấu để admin thấy. */
    private suspend fun uploadLuckyDraws(api: AttendanceApi, apiKey: String, deviceCode: String): Int {
        val draws = luckyDrawSource ?: return 0
        val pending = draws.getPendingWins(LUCKY_DRAW_BATCH_SIZE)
        if (pending.isEmpty()) return 0
        val body = pending.map {
            LuckyDrawUploadRequest(
                employeeCode = it.employeeCode,
                drawDate = it.drawDate,
                wonAt = it.wonAtUtc,
                cans = it.cans,
                reason = it.reason,
                chance = it.chance,
                deviceCode = deviceCode
            )
        }
        val response = try {
            api.uploadLuckyDraws(apiKey, body)
        } catch (e: IOException) {
            return 0
        }
        if (response.code() != 200) return 0
        val results = response.body().orEmpty()
        var uploaded = 0
        pending.forEachIndexed { index, win ->
            when (results.getOrNull(index)?.status) {
                LuckyDrawUploadResult.STATUS_SAVED, LuckyDrawUploadResult.STATUS_DUPLICATE -> {
                    draws.markWinSynced(win.localId, SyncStatus.SYNCED)
                    uploaded++
                }
                LuckyDrawUploadResult.STATUS_UNKNOWN_EMPLOYEE ->
                    draws.markWinSynced(win.localId, SyncStatus.UNKNOWN_EMPLOYEE)
                else -> Unit
            }
        }
        return uploaded
    }

    /** Thẻ gán trên thiết bị. */
    private suspend fun uploadCardAssignments(api: AttendanceApi, apiKey: String, deviceCode: String): Int {
        val cards = cardSource ?: return 0
        val pending = cards.getPendingCardAssignments(CARD_BATCH_SIZE)
        if (pending.isEmpty()) return 0
        val body = pending.map { CardAssignmentUploadRequest(it.cardId, it.employeeCode, it.assignedAt, deviceCode) }
        val response = try {
            api.uploadCardAssignments(apiKey, body)
        } catch (e: IOException) {
            return 0
        }
        if (response.code() != 200) return 0
        val results = response.body().orEmpty()
        var uploaded = 0
        pending.forEachIndexed { index, card ->
            when (results.getOrNull(index)?.status) {
                CardAssignmentUploadResult.STATUS_SAVED -> {
                    cards.markCardAssignmentSynced(card.cardId, SyncStatus.SYNCED)
                    uploaded++
                }
                CardAssignmentUploadResult.STATUS_UNKNOWN_EMPLOYEE ->
                    cards.markCardAssignmentSynced(card.cardId, SyncStatus.UNKNOWN_EMPLOYEE)
                else -> Unit
            }
        }
        return uploaded
    }

    /** Nhật ký quên thẻ + ảnh bằng chứng (JPEG base64, ~10 KB/ảnh). */
    private suspend fun uploadForgotCards(api: AttendanceApi, apiKey: String, deviceCode: String): Int {
        val cards = cardSource ?: return 0
        val pending = cards.getPendingForgotCardLogs(FORGOT_BATCH_SIZE)
        if (pending.isEmpty()) return 0
        val body = pending.map {
            ForgotCardUploadRequest(
                employeeCode = it.employeeCode,
                eventTime = it.eventTimeUtc,
                deviceCode = deviceCode,
                photoBase64 = it.photoJpeg?.let { bytes -> java.util.Base64.getEncoder().encodeToString(bytes) }
            )
        }
        val response = try {
            api.uploadForgotCards(apiKey, body)
        } catch (e: IOException) {
            return 0
        }
        if (response.code() != 200) return 0
        val results = response.body().orEmpty()
        var uploaded = 0
        pending.forEachIndexed { index, log ->
            when (results.getOrNull(index)?.status) {
                ForgotCardUploadResult.STATUS_SAVED, ForgotCardUploadResult.STATUS_DUPLICATE -> {
                    cards.markForgotCardLogSynced(log.localId, SyncStatus.SYNCED)
                    uploaded++
                }
                ForgotCardUploadResult.STATUS_UNKNOWN_EMPLOYEE ->
                    cards.markForgotCardLogSynced(log.localId, SyncStatus.UNKNOWN_EMPLOYEE)
                else -> Unit
            }
        }
        return uploaded
    }

    /** Chi tiết công tháng hiện tại (lịch VN) về cache. */
    private suspend fun downloadMonthSummary(api: AttendanceApi, apiKey: String): Int {
        val cards = cardSource ?: return 0
        val month = CardRepository.monthKey(TimeUtils.vnToday())
        val response = try {
            api.getMonthSummary(apiKey, month)
        } catch (e: IOException) {
            return 0
        }
        if (response.code() != 200) return 0
        val now = TimeUtils.nowUtcIso()
        val rows = response.body().orEmpty().map {
            EmployeeMonthSummaryEntity(
                employeeCode = it.employeeCode.trim(),
                month = month,
                workDays = it.workDays,
                otRegularHours = it.otRegularHours,
                otSundayHours = it.otSundayHours,
                otHolidayHours = it.otHolidayHours,
                leaveDays = it.leaveDays,
                disciplinaryCount = it.disciplinaryCount,
                commendationCount = it.commendationCount,
                forgotCardCount = it.forgotCardCount,
                penaltyAmount = it.penaltyAmount,
                syncedAt = now
            )
        }
        cards.replaceMonthSummary(month, rows)
        return rows.size
    }

    private fun httpFailure(response: Response<*>): EmployeeSyncOutcome? = when (response.code()) {
        200 -> null
        401 -> EmployeeSyncOutcome.Unauthorized
        404 -> EmployeeSyncOutcome.EndpointMissing
        503 -> EmployeeSyncOutcome.ServerMisconfigured
        else -> EmployeeSyncOutcome.UnexpectedHttp(response.code())
    }

    companion object {
        private const val LUCKY_DRAW_BATCH_SIZE = 200
        private const val CARD_BATCH_SIZE = 200
        /** Mỗi dòng kèm ảnh ~10 KB -> 50 dòng ~ 0.5 MB/request. */
        private const val FORGOT_BATCH_SIZE = 50
    }
}

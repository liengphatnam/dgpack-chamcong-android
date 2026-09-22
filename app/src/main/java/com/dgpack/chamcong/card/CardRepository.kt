package com.dgpack.chamcong.card

import com.dgpack.chamcong.data.db.CardAssignmentDao
import com.dgpack.chamcong.data.db.CardAssignmentEntity
import com.dgpack.chamcong.data.db.EmployeeMonthSummaryDao
import com.dgpack.chamcong.data.db.EmployeeMonthSummaryEntity
import com.dgpack.chamcong.data.db.EnrolledEmployeeDao
import com.dgpack.chamcong.data.db.ErpEmployeeDao
import com.dgpack.chamcong.data.db.ForgotCardLogDao
import com.dgpack.chamcong.data.db.ForgotCardLogEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.sync.CardSyncSource
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Người giữ thẻ / người nhập mã. */
data class CardHolder(val employeeCode: String, val fullName: String)

/** Kết quả ghi 1 lần quên thẻ: lần thứ mấy trong tháng, tổng đã bị trừ, trừ thêm bao nhiêu ở lần này. */
data class ForgotCardResult(val countInMonth: Int, val totalPenalty: Long, val penaltyIncrement: Long)

/**
 * Thẻ từ + quên thẻ + chi tiết công tháng. Mọi "ngày/tháng" theo lịch Việt Nam.
 * Không có logic UI; các bước đồng bộ nằm ở EmployeeSyncEngine qua [CardSyncSource].
 */
class CardRepository(
    private val cardDao: CardAssignmentDao,
    private val forgotDao: ForgotCardLogDao,
    private val summaryDao: EmployeeMonthSummaryDao,
    private val erpDao: ErpEmployeeDao,
    private val enrolledDao: EnrolledEmployeeDao
) : CardSyncSource {

    // ===== Thẻ -> nhân viên =====

    /** Thẻ gán trên máy ưu tiên, rồi tới cardId kéo từ ERP. null = thẻ lạ. */
    suspend fun resolveCard(rawCardId: String): CardHolder? {
        val id = CardScanBus.normalize(rawCardId)
        if (id.isEmpty()) return null
        cardDao.getByCard(id)?.let { return CardHolder(it.employeeCode, nameOf(it.employeeCode)) }
        erpDao.getByCardId(id)?.let { return CardHolder(it.employeeCode, it.fullName) }
        return null
    }

    /** Tra NV theo mã (luồng quên thẻ): ERP cache trước, rồi danh sách đã đăng ký khuôn mặt. */
    suspend fun findEmployee(rawCode: String): CardHolder? {
        val code = rawCode.trim().uppercase()
        if (code.isEmpty()) return null
        erpDao.getByCode(code)?.let { return CardHolder(it.employeeCode, it.fullName) }
        // Mã ERP thường viết hoa; thử đúng chuỗi người dùng gõ nếu cache lưu khác kiểu.
        erpDao.getByCode(rawCode.trim())?.let { return CardHolder(it.employeeCode, it.fullName) }
        enrolledDao.getByCode(code)?.let { return CardHolder(it.employeeCode, it.fullName) }
        return null
    }

    suspend fun nameOf(employeeCode: String): String =
        erpDao.getByCode(employeeCode)?.fullName
            ?: enrolledDao.getByCode(employeeCode)?.fullName
            ?: employeeCode

    /** Gán thẻ cho NV (1 NV 1 thẻ trên máy; thẻ đã thuộc người khác sẽ chuyển sang người này). */
    suspend fun assignCard(rawCardId: String, employeeCode: String, now: Instant = Instant.now()) {
        val id = CardScanBus.normalize(rawCardId)
        require(id.isNotEmpty()) { "Mã thẻ rỗng" }
        cardDao.deleteByEmployee(employeeCode)
        cardDao.upsert(
            CardAssignmentEntity(
                cardId = id,
                employeeCode = employeeCode,
                assignedAt = TimeUtils.instantToApiString(now)
            )
        )
    }

    fun observeAssignments(): Flow<List<CardAssignmentEntity>> = cardDao.observeAll()

    suspend fun cardOf(employeeCode: String): String? =
        cardDao.getByEmployee(employeeCode).firstOrNull()?.cardId ?: erpDao.getByCode(employeeCode)?.cardId

    // ===== Quên thẻ =====

    suspend fun logForgotCard(
        holder: CardHolder,
        photoJpeg: ByteArray?,
        deviceCode: String,
        policy: ForgotCardPolicy.Config,
        now: Instant = Instant.now()
    ): ForgotCardResult {
        val today = TimeUtils.vnToday(now)
        val month = monthKey(today)
        forgotDao.insert(
            ForgotCardLogEntity(
                employeeCode = holder.employeeCode,
                fullName = holder.fullName,
                eventTimeUtc = TimeUtils.instantToApiString(now),
                dateVn = today.toString(),
                monthVn = month,
                photoJpeg = photoJpeg,
                deviceCode = deviceCode
            )
        )
        val count = forgotCountInMonth(holder.employeeCode, month)
        return ForgotCardResult(
            countInMonth = count,
            totalPenalty = ForgotCardPolicy.totalPenalty(count, policy),
            penaltyIncrement = ForgotCardPolicy.incrementAt(count, policy)
        )
    }

    /**
     * Số lần quên thẻ trong tháng = max(ERP tổng hợp từ mọi máy, máy này tự đếm) — máy này có
     * thể có dòng chưa kịp đồng bộ, ERP có thể có dòng từ cổng khác.
     */
    suspend fun forgotCountInMonth(employeeCode: String, month: String): Int {
        val local = forgotDao.countForEmployeeInMonth(employeeCode, month)
        val server = summaryDao.get(employeeCode, month)?.forgotCardCount ?: 0
        return maxOf(local, server)
    }

    fun observeForgotLogs(month: String): Flow<List<ForgotCardLogEntity>> = forgotDao.observeByMonth(month)

    // ===== Chi tiết công tháng =====

    suspend fun monthSummary(employeeCode: String, month: String): EmployeeMonthSummaryEntity? =
        summaryDao.get(employeeCode, month)

    /** Giữ dữ liệu 2 tháng trên máy: xoá nhật ký ĐÃ đồng bộ và cache công cũ hơn. */
    suspend fun purgeOld(now: Instant = Instant.now()) {
        val today = TimeUtils.vnToday(now)
        val minDate = today.minusMonths(2).withDayOfMonth(1)
        forgotDao.deleteSyncedOlderThan(minDate.toString())
        summaryDao.deleteOlderThan(monthKey(minDate))
    }

    // ===== CardSyncSource =====

    override suspend fun getPendingCardAssignments(limit: Int): List<CardAssignmentEntity> =
        cardDao.getByStatus(SyncStatus.PENDING, limit)

    override suspend fun markCardAssignmentSynced(cardId: String, status: String) {
        val syncedAt = if (status == SyncStatus.SYNCED) TimeUtils.nowUtcIso() else null
        cardDao.markSyncStatus(cardId, status, syncedAt)
    }

    override suspend fun getPendingForgotCardLogs(limit: Int): List<ForgotCardLogEntity> =
        forgotDao.getByStatus(SyncStatus.PENDING, limit)

    override suspend fun markForgotCardLogSynced(localId: Long, status: String) {
        val syncedAt = if (status == SyncStatus.SYNCED) TimeUtils.nowUtcIso() else null
        forgotDao.markSyncStatus(localId, status, syncedAt)
    }

    override suspend fun replaceMonthSummary(month: String, rows: List<EmployeeMonthSummaryEntity>) {
        summaryDao.deleteMonth(month)
        summaryDao.upsertAll(rows)
    }

    companion object {
        /** "yyyy-MM" theo lịch VN. */
        fun monthKey(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)
    }
}

package com.dgpack.chamcong.luckydraw

import com.dgpack.chamcong.data.db.AttendanceEventDao
import com.dgpack.chamcong.data.db.ErpEmployeeDao
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.data.db.LuckyDrawWinDao
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.sync.LuckyDrawSyncSource
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** Quy đổi cài đặt (chuỗi ngày admin nhập) sang cấu hình engine; ngày sai định dạng = tắt. */
fun AppSettings.luckyDrawConfig(): LuckyDrawConfig {
    val start = LuckyDrawEngine.parseDate(luckyDrawStartDate)
    val end = LuckyDrawEngine.parseDate(luckyDrawEndDate)
    return LuckyDrawConfig(
        enabled = luckyDrawEnabled && start != null && end != null,
        startDate = start ?: LocalDate.MIN,
        endDate = end ?: LocalDate.MIN,
        dailyQuota = luckyDrawDailyQuota
    )
}

/**
 * Nối [LuckyDrawEngine] với Room: gom dữ liệu (lần đầu trong ngày? ai đã trúng? ai còn sẽ
 * chấm công?) rồi ghi sổ khi trúng. Mọi "ngày" ở đây là ngày Việt Nam.
 */
class LuckyDrawRepository(
    private val winDao: LuckyDrawWinDao,
    private val attendanceDao: AttendanceEventDao,
    private val erpDao: ErpEmployeeDao
) : LuckyDrawSyncSource {

    fun observeAll(): Flow<List<LuckyDrawWinEntity>> = winDao.observeAll()

    /**
     * Gọi NGAY SAU khi đã ghi 1 sự kiện chấm công mới (recordEventIfNotDebounced trả true).
     * Trả về dòng vừa ghi sổ nếu trúng, null nếu không (hoặc ngoài đợt).
     */
    suspend fun drawAfterCheckIn(
        employeeCode: String,
        fullName: String,
        settings: AppSettings,
        now: Instant = Instant.now()
    ): LuckyDrawWinEntity? {
        val config = settings.luckyDrawConfig()
        val today = TimeUtils.vnToday(now)
        if (!LuckyDrawEngine.isInCampaign(config, today)) return null

        val todayKey = today.toString()
        // Phòng hờ: đã có trong sổ hôm nay (vd sự kiện bị xoá) thì không cho quay lần 2.
        if (winDao.countForEmployeeOnDate(employeeCode, todayKey) > 0) return null

        val dayStart = TimeUtils.vnDayStartUtc(today)
        val dayEnd = TimeUtils.vnDayStartUtc(today.plusDays(1))
        val isFirstScanToday = attendanceDao.countForEmployeeBetween(employeeCode, dayStart, dayEnd) <= 1

        val erpByCode = erpDao.getAllOnce().associateBy { it.employeeCode }
        val winsByCode = winDao
            .winsPerEmployee(config.startDate.toString(), config.endDate.toString(), LuckyDrawReason.RANDOM)
            .associate { it.employeeCode to it.wins }

        fun candidateOf(code: String) = toCandidate(code, erpByCode[code], winsByCode[code] ?: 0)

        // Pool = người dự kiến còn chấm công hôm nay: ai đã chấm công trên máy này trong
        // POOL_LOOKBACK_DAYS ngày qua (máy mới chưa có lịch sử thì lấy toàn bộ NV đang hoạt động bên ERP),
        // trừ những người đã chấm công hôm nay, cộng lại chính người đang quay.
        val lookbackStart = TimeUtils.vnDayStartUtc(today.minusDays(POOL_LOOKBACK_DAYS))
        val expected = attendanceDao.distinctEmployeesBetween(lookbackStart, dayEnd).toSet()
            .ifEmpty { erpByCode.values.filter { it.isActive }.map { it.employeeCode }.toSet() }
        val scannedToday = attendanceDao.distinctEmployeesBetween(dayStart, dayEnd).toSet()
        val pool = (expected - scannedToday + employeeCode).map(::candidateOf)

        val randomWinsToday = winDao.countByDateAndReason(todayKey, LuckyDrawReason.RANDOM)
        val win = LuckyDrawEngine.draw(
            candidate = candidateOf(employeeCode),
            today = today,
            config = config,
            isFirstScanToday = isFirstScanToday,
            randomWinsToday = randomWinsToday,
            pool = pool
        ) ?: return null

        val entity = LuckyDrawWinEntity(
            employeeCode = employeeCode,
            fullName = fullName,
            drawDate = todayKey,
            wonAtUtc = TimeUtils.instantToApiString(now),
            cans = win.cans,
            reason = win.reason,
            weight = win.weight,
            chance = win.chance,
            deviceCode = settings.deviceCode
        )
        val id = winDao.insert(entity)
        return entity.copy(localId = id)
    }

    suspend fun setClaimed(localId: Long, claimed: Boolean) = winDao.setClaimed(localId, claimed)

    // ===== LuckyDrawSyncSource (EmployeeSyncEngine bước 4) =====

    override suspend fun getPendingWins(limit: Int): List<LuckyDrawWinEntity> =
        winDao.getByStatus(SyncStatus.PENDING, limit)

    override suspend fun markWinSynced(localId: Long, status: String) {
        val syncedAt = if (status == SyncStatus.SYNCED) TimeUtils.nowUtcIso() else null
        winDao.markSyncStatus(localId, status, syncedAt)
    }

    companion object {
        private const val POOL_LOOKBACK_DAYS = 14L

        fun toCandidate(code: String, erp: ErpEmployeeEntity?, winsThisCampaign: Int) = LuckyDrawCandidate(
            employeeCode = code,
            birthDate = erp?.birthDate,
            lateEarlyCount30d = erp?.lateEarlyCount30d ?: 0,
            commendationCount = erp?.commendationCount ?: 0,
            winsThisCampaign = winsThisCampaign
        )
    }
}

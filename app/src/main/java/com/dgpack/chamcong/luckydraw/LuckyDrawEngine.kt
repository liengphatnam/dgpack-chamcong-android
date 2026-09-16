package com.dgpack.chamcong.luckydraw

import com.dgpack.chamcong.data.db.LuckyDrawReason
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Thông tin 1 nhân viên đưa vào quay thưởng — lấy từ cache erp_employee + sổ trúng thưởng. */
data class LuckyDrawCandidate(
    val employeeCode: String,
    /** "yyyy-MM-dd" (hoặc "yyyy-MM-ddT..." kiểu .NET), null nếu ERP không có. */
    val birthDate: String? = null,
    val lateEarlyCount30d: Int = 0,
    val commendationCount: Int = 0,
    /** Số lần đã trúng NGẪU NHIÊN trong đợt này (sinh nhật không tính). */
    val winsThisCampaign: Int = 0
)

data class LuckyDrawConfig(
    val enabled: Boolean,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Số người trúng ngẫu nhiên tối đa/ngày (trên thiết bị này). */
    val dailyQuota: Int
)

data class LuckyDrawWin(
    val cans: Int,
    /** [LuckyDrawReason]. */
    val reason: String,
    val weight: Double,
    /** Xác suất 0..1 tại lúc quay. */
    val chance: Double
) {
    val isBirthday: Boolean get() = reason == LuckyDrawReason.BIRTHDAY
}

/**
 * Thuật toán quay thưởng lon nước ngọt (chương trình 8–9/2026), thuần Kotlin để test được.
 *
 * ## Luật
 * 1. Chỉ quay trong khoảng ngày cấu hình và chỉ ở **lần chấm công đầu tiên trong ngày** của
 *    mỗi người (1 người tối đa 1 lượt/ngày, không quay lại lúc về).
 * 2. **Sinh nhật** (trùng ngày-tháng với ngày VN hôm nay) → chắc chắn trúng **3 lon**, không
 *    tính vào quota 8 người/ngày.
 * 3. Còn lại quay ngẫu nhiên có trọng số [weightOf]:
 *    - không đi trễ/về sớm 30 ngày qua ×2; 1–2 lần ×1; từ 3 lần ×0.5,
 *    - mỗi lần được khen thưởng/phối hợp nội quy (Log ERP) +50%, tối đa 4 lần (×3),
 *    - mỗi lần đã trúng trong đợt ×0.5 để lon nước lan đều ra nhiều người (sàn 0.1).
 * 4. Cách chọn đúng ~[dailyQuota] người/ngày dù không biết trước hôm nay ai sẽ chấm công:
 *    *lấy mẫu tuần tự có trọng số* — mỗi lượt quay
 *    `p = min(1, quotaCònLại × w_người / Σ w_nhữngNgườiDựKiếnCònChấmCôngHômNay)`.
 *    Người quay sớm và quay muộn có kỳ vọng như nhau; tổng số trúng không bao giờ vượt quota,
 *    và nếu mọi người trong pool đều đi làm thì kỳ vọng số người trúng đúng bằng quota.
 */
object LuckyDrawEngine {
    const val BIRTHDAY_CANS = 3
    const val RANDOM_CANS = 1
    private const val MIN_WEIGHT = 0.1

    fun isInCampaign(config: LuckyDrawConfig, today: LocalDate): Boolean =
        config.enabled && config.dailyQuota > 0 &&
            !today.isBefore(config.startDate) && !today.isAfter(config.endDate)

    /** So ngày-tháng; sinh 29/02 thì năm thường mừng vào 28/02. */
    fun isBirthday(birthDate: String?, today: LocalDate): Boolean {
        val dob = parseDate(birthDate) ?: return false
        if (dob.monthValue == 2 && dob.dayOfMonth == 29 && !today.isLeapYear) {
            return today.monthValue == 2 && today.dayOfMonth == 28
        }
        return dob.monthValue == today.monthValue && dob.dayOfMonth == today.dayOfMonth
    }

    fun weightOf(c: LuckyDrawCandidate): Double {
        var w = 1.0
        w *= when {
            c.lateEarlyCount30d <= 0 -> 2.0
            c.lateEarlyCount30d <= 2 -> 1.0
            else -> 0.5
        }
        w *= 1.0 + 0.5 * min(max(c.commendationCount, 0), 4)
        repeat(max(c.winsThisCampaign, 0)) { w *= 0.5 }
        return max(w, MIN_WEIGHT)
    }

    /**
     * @param isFirstScanToday lần chấm công đầu tiên trong ngày (VN) của người này
     * @param randomWinsToday  số người đã trúng NGẪU NHIÊN hôm nay trên thiết bị này
     * @param pool             những người dự kiến còn chấm công hôm nay (chưa chấm), kể cả [candidate]
     */
    fun draw(
        candidate: LuckyDrawCandidate,
        today: LocalDate,
        config: LuckyDrawConfig,
        isFirstScanToday: Boolean,
        randomWinsToday: Int,
        pool: List<LuckyDrawCandidate>,
        random: Random = Random.Default
    ): LuckyDrawWin? {
        if (!isInCampaign(config, today)) return null
        if (!isFirstScanToday) return null

        val weight = weightOf(candidate)
        if (isBirthday(candidate.birthDate, today)) {
            return LuckyDrawWin(BIRTHDAY_CANS, LuckyDrawReason.BIRTHDAY, weight, 1.0)
        }

        val remaining = config.dailyQuota - randomWinsToday
        if (remaining <= 0) return null

        var poolSum = pool.sumOf { weightOf(it) }
        if (pool.none { it.employeeCode == candidate.employeeCode }) poolSum += weight
        val chance = min(1.0, remaining * weight / poolSum)
        return if (random.nextDouble() < chance) {
            LuckyDrawWin(RANDOM_CANS, LuckyDrawReason.RANDOM, weight, chance)
        } else {
            null
        }
    }

    fun parseDate(value: String?): LocalDate? {
        val text = value?.trim()?.take(10) ?: return null
        if (text.length != 10) return null
        return try {
            LocalDate.parse(text)
        } catch (e: DateTimeParseException) {
            null
        }
    }
}

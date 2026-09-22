package com.dgpack.chamcong.card

/**
 * Luật phạt quên mang thẻ (trừ vào tiền thưởng nội quy), tính theo THÁNG, tháng sau về 0:
 *  - lần thứ [Config.firstAt] (mặc định 2) trong tháng: trừ [Config.amount] (50.000),
 *  - sau đó cứ thêm [Config.every] lần (mặc định 2) lại trừ thêm [Config.amount].
 * Ví dụ mặc định: lần 1 = 0 đ; lần 2, 3 = 50.000; lần 4, 5 = 100.000; lần 6, 7 = 150.000…
 * Mặc định chỉ là giá trị ban đầu — luật thật đọc từ Cài đặt (AppSettings.forgotPenalty*).
 */
object ForgotCardPolicy {
    const val DEFAULT_FIRST_PENALTY_AT = 2
    const val DEFAULT_PENALTY_EVERY = 2
    const val DEFAULT_PENALTY_AMOUNT = 50_000L

    data class Config(
        val firstAt: Int = DEFAULT_FIRST_PENALTY_AT,
        val every: Int = DEFAULT_PENALTY_EVERY,
        val amount: Long = DEFAULT_PENALTY_AMOUNT
    )

    /** Tổng tiền bị trừ trong tháng khi đã quên [countInMonth] lần. */
    fun totalPenalty(countInMonth: Int, config: Config = Config()): Long {
        if (countInMonth < config.firstAt || config.firstAt <= 0) return 0
        val every = config.every.coerceAtLeast(1)
        return config.amount * (1 + (countInMonth - config.firstAt) / every)
    }

    /** Số tiền bị trừ THÊM đúng ở lần thứ [countInMonth] (để thông báo ngay tại máy). */
    fun incrementAt(countInMonth: Int, config: Config = Config()): Long =
        totalPenalty(countInMonth, config) - totalPenalty(countInMonth - 1, config)
}

package com.dgpack.chamcong

import com.dgpack.chamcong.card.CardScanBus
import com.dgpack.chamcong.card.ForgotCardPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

/** Phạt quên thẻ: lần 3 = 50k, sau đó cứ 2 lần thêm 50k; luật lấy từ Cài đặt (Config). */
class ForgotCardPolicyTest {

    @Test
    fun `mac dinh - lan 1 khong phat, lan 2,3 = 50k, lan 4,5 = 100k, lan 6 = 150k`() {
        val expected = mapOf(0 to 0L, 1 to 0L, 2 to 50_000L, 3 to 50_000L, 4 to 100_000L, 5 to 100_000L, 6 to 150_000L)
        expected.forEach { (n, total) -> assertEquals("lần $n", total, ForgotCardPolicy.totalPenalty(n)) }
    }

    @Test
    fun `tien tru them dung tai lan xay ra`() {
        assertEquals(0L, ForgotCardPolicy.incrementAt(1))
        assertEquals(50_000L, ForgotCardPolicy.incrementAt(2))
        assertEquals(0L, ForgotCardPolicy.incrementAt(3))
        assertEquals(50_000L, ForgotCardPolicy.incrementAt(4))
    }

    @Test
    fun `luat tu cai dat - phat tu lan 2, moi 1 lan, 20k`() {
        val cfg = ForgotCardPolicy.Config(firstAt = 2, every = 1, amount = 20_000)
        assertEquals(0L, ForgotCardPolicy.totalPenalty(1, cfg))
        assertEquals(20_000L, ForgotCardPolicy.totalPenalty(2, cfg))
        assertEquals(40_000L, ForgotCardPolicy.totalPenalty(3, cfg))
        // firstAt = 0 nghĩa là tắt phạt
        assertEquals(0L, ForgotCardPolicy.totalPenalty(9, ForgotCardPolicy.Config(firstAt = 0)))
    }

    @Test
    fun `chuan hoa ma the - chu hoa, bo ky tu la`() {
        assertEquals("04A1B2C3", CardScanBus.normalize(" 04:a1:b2:c3 "))
        assertEquals("0001234567", CardScanBus.normalize("0001234567\n"))
        assertEquals("", CardScanBus.normalize("--"))
    }
}

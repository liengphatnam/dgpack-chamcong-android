package com.dgpack.chamcong

import com.dgpack.chamcong.util.DebounceChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DebounceTest {

    @Test
    fun `luon ghi neu chua co su kien truoc do`() {
        val result = DebounceChecker.shouldRecord(lastEventUtc = null, nowUtc = Instant.now(), thresholdMinutes = 5)
        assertTrue(result)
    }

    @Test
    fun `khong ghi neu cach lan truoc duoi nguong`() {
        val last = Instant.parse("2026-08-30T01:00:00Z")
        val now = last.plusSeconds(4 * 60) // 4 phút sau
        val result = DebounceChecker.shouldRecord(last, now, thresholdMinutes = 5)
        assertFalse(result)
    }

    @Test
    fun `ghi neu cach lan truoc dung bang nguong`() {
        val last = Instant.parse("2026-08-30T01:00:00Z")
        val now = last.plusSeconds(5 * 60) // đúng 5 phút
        val result = DebounceChecker.shouldRecord(last, now, thresholdMinutes = 5)
        assertTrue(result)
    }

    @Test
    fun `ghi neu cach lan truoc vuot nguong`() {
        val last = Instant.parse("2026-08-30T01:00:00Z")
        val now = last.plusSeconds(10 * 60) // 10 phút sau
        val result = DebounceChecker.shouldRecord(last, now, thresholdMinutes = 5)
        assertTrue(result)
    }
}

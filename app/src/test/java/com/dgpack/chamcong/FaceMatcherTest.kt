package com.dgpack.chamcong

import com.dgpack.chamcong.face.EnrolledFace
import com.dgpack.chamcong.face.FaceMatcher
import com.dgpack.chamcong.face.MatchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class FaceMatcherTest {

    @Test
    fun `cosine similarity của 2 vector giống hệt nhau bằng 1`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val sim = FaceMatcher.cosineSimilarity(a, a)
        assertEquals(1f, sim, 1e-5f)
    }

    @Test
    fun `cosine similarity của 2 vector vuông góc bằng 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, FaceMatcher.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `cosine similarity của 2 vector ngược hướng bằng -1`() {
        val a = floatArrayOf(1f, 1f)
        val b = floatArrayOf(-1f, -1f)
        assertEquals(-1f, FaceMatcher.cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `findBestMatch tra ve nguoi co similarity cao nhat khi vuot nguong`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val enrolled = listOf(
            EnrolledFace("NV001", floatArrayOf(0.9f, 0.1f, 0f)),
            EnrolledFace("NV002", floatArrayOf(1f, 0f, 0f)), // giống hệt query
            EnrolledFace("NV003", floatArrayOf(0f, 1f, 0f))
        )
        val result = FaceMatcher.findBestMatch(query, enrolled, threshold = 0.6f)
        assertNotNull(result)
        assertEquals("NV002", result!!.employeeCode)
    }

    @Test
    fun `findBestMatch tra ve null khi khong ai vuot nguong`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val enrolled = listOf(
            EnrolledFace("NV001", floatArrayOf(0f, 1f, 0f)),
            EnrolledFace("NV002", floatArrayOf(0f, 0f, 1f))
        )
        val result = FaceMatcher.findBestMatch(query, enrolled, threshold = 0.6f)
        assertNull(result)
    }

    @Test
    fun `findBestMatch khong nguong luon tra ve nguoi cao nhat de UI hien phan tram`() {
        val query = floatArrayOf(1f, 0f, 0f)
        val enrolled = listOf(
            EnrolledFace("NV001", floatArrayOf(0f, 1f, 0f)),
            EnrolledFace("NV002", floatArrayOf(0.6f, 0.8f, 0f))
        )
        val result = FaceMatcher.findBestMatch(query, enrolled)
        assertNotNull(result)
        assertEquals("NV002", result!!.employeeCode)
        assertEquals(60, result.confidencePercent)
        assertFalse(result.isConfident(80))
    }

    @Test
    fun `findBestMatch tra ve null khi chua enroll ai`() {
        assertNull(FaceMatcher.findBestMatch(floatArrayOf(1f, 0f), emptyList()))
    }

    @Test
    fun `quy tac 80 phan tram - dung 80 la dat, 79 la chua dat`() {
        assertTrue(MatchResult("NV001", 0.80f).isConfident(80))
        assertTrue(MatchResult("NV001", 0.795f).isConfident(80)) // làm tròn 79.5 -> 80
        assertFalse(MatchResult("NV001", 0.79f).isConfident(80))
        assertFalse(MatchResult("NV001", 0.60f).isConfident(80)) // ngưỡng 0.6 cũ giờ không đủ
    }

    @Test
    fun `toConfidencePercent kep trong 0-100 va similarity am thanh 0`() {
        assertEquals(0, FaceMatcher.toConfidencePercent(-0.5f))
        assertEquals(0, FaceMatcher.toConfidencePercent(0f))
        assertEquals(100, FaceMatcher.toConfidencePercent(1f))
        assertEquals(100, FaceMatcher.toConfidencePercent(1.2f))
        assertEquals(87, FaceMatcher.toConfidencePercent(0.874f))
    }

    @Test
    fun `averageEmbedding tra ve vector da duoc L2-normalize`() {
        val samples = listOf(
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f)
        )
        val avg = FaceMatcher.averageEmbedding(samples)
        val norm = sqrt(avg.sumOf { (it * it).toDouble() }).toFloat()
        assertEquals(1f, norm, 1e-5f)
        assertEquals(1f, avg[0], 1e-5f)
    }

    @Test
    fun `findBestMatch tra ve similarity nguoi dung nhi de phat hien giong 2 nguoi`() {
        val enrolled = listOf(
            EnrolledFace("NV001", floatArrayOf(1f, 0f)),
            EnrolledFace("NV002", floatArrayOf(0.98f, 0.2f)),
            EnrolledFace("NV003", floatArrayOf(0f, 1f))
        )
        val result = FaceMatcher.findBestMatch(floatArrayOf(1f, 0f), enrolled)!!
        assertEquals("NV001", result.employeeCode)
        assertEquals(1.0f, result.similarity, 1e-4f)
        // NV002 ~ 0.98 -> chỉ cách 0.02 < 0.05 -> mơ hồ, không được kết luận
        assertTrue(result.isAmbiguous(0.05f))
        assertFalse(result.isAmbiguous(0.01f))

        // Chỉ enroll 1 người -> không có người nhì -> không mơ hồ
        val single = FaceMatcher.findBestMatch(floatArrayOf(1f, 0f), enrolled.take(1))!!
        assertEquals(null, single.runnerUpSimilarity)
        assertFalse(single.isAmbiguous(0.05f))
    }
}

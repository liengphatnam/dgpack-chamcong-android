package com.dgpack.chamcong

import com.dgpack.chamcong.face.EnrolledFace
import com.dgpack.chamcong.face.FaceMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
}

package com.dgpack.chamcong

import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.sync.EmployeeSyncEngine
import com.dgpack.chamcong.sync.EmployeeSyncOutcome
import com.dgpack.chamcong.sync.EmployeeSyncSource
import com.dgpack.chamcong.sync.LuckyDrawSyncSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Bước 4 của EmployeeSyncEngine: đẩy sổ trúng thưởng lên ERP + đọc 3 trường mới
 * (birthDate/lateEarlyCount30d/commendationCount) từ GET employees. Server chưa có
 * endpoint lucky-draws (404) thì 3 bước chính vẫn Completed, sổ giữ Pending.
 */
class LuckyDrawSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var api: AttendanceApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AttendanceApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun enqueueMainSteps(employeesJson: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(employeesJson)) // 1. employees
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))          // 3. GET embeddings (bước 2 không có gì để đẩy)
    }

    @Test
    fun `doc truong sinh nhat, tre som, khen thuong tu ERP va day so trung thuong`() = runBlocking {
        enqueueMainSteps(
            """
            [
              {"employeeCode":"NV001","fullName":"Nguyễn A","isActive":true,"hasFaceEmbedding":true,
               "birthDate":"1990-08-20T00:00:00","lateEarlyCount30d":0,"commendationCount":2},
              {"employeeCode":"NV002","fullName":"Trần B","isActive":true,"hasFaceEmbedding":true}
            ]
            """.trimIndent()
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"employeeCode":"NV001","wonAt":"2026-08-20T01:00:00","status":"Saved"},
                  {"employeeCode":"NVLA","wonAt":"2026-08-20T01:05:00","status":"UnknownEmployee"},
                  {"employeeCode":"NV002","wonAt":"2026-08-21T01:00:00","status":"Duplicate"}
                ]
                """.trimIndent()
            )
        )
        val employees = FakeSource()
        val draws = FakeLuckyDrawSource(
            listOf(
                win(1, "NV001", "2026-08-20", "2026-08-20T01:00:00", 3, LuckyDrawReason.BIRTHDAY),
                win(2, "NVLA", "2026-08-20", "2026-08-20T01:05:00", 1, LuckyDrawReason.RANDOM),
                win(3, "NV002", "2026-08-21", "2026-08-21T01:00:00", 1, LuckyDrawReason.RANDOM)
            )
        )

        val outcome = EmployeeSyncEngine(employees, draws).sync(api, "key", "TABLET-1")

        val completed = outcome as EmployeeSyncOutcome.Completed
        assertEquals(2, completed.luckyDrawsUploaded)
        assertEquals(SyncStatus.SYNCED, draws.status[1L])
        assertEquals(SyncStatus.UNKNOWN_EMPLOYEE, draws.status[2L])
        assertEquals(SyncStatus.SYNCED, draws.status[3L])

        val nv001 = employees.erpEmployees.first { it.employeeCode == "NV001" }
        assertEquals("1990-08-20", nv001.birthDate)
        assertEquals(0, nv001.lateEarlyCount30d)
        assertEquals(2, nv001.commendationCount)
        val nv002 = employees.erpEmployees.first { it.employeeCode == "NV002" }
        assertEquals(null, nv002.birthDate)
        assertEquals(0, nv002.commendationCount)

        // Request thứ 3 là POST lucky-draws với đúng 3 phần tử, đúng deviceCode
        server.takeRequest(); server.takeRequest()
        val post = server.takeRequest()
        assertEquals("POST", post.method)
        assertEquals("/api/v1/attendance/lucky-draws", post.path)
        val body = post.body.readUtf8()
        assertTrue(body, body.contains("\"employeeCode\":\"NV001\""))
        assertTrue(body, body.contains("\"reason\":\"Birthday\""))
        assertTrue(body, body.contains("\"cans\":3"))
        assertTrue(body, body.contains("\"deviceCode\":\"TABLET-1\""))
    }

    @Test
    fun `server chua co endpoint lucky-draws (404) thi van Completed, so giu Pending`() = runBlocking {
        enqueueMainSteps("""[{"employeeCode":"NV001","fullName":"A","isActive":true,"hasFaceEmbedding":true}]""")
        server.enqueue(MockResponse().setResponseCode(404))
        val draws = FakeLuckyDrawSource(listOf(win(1, "NV001", "2026-08-20", "2026-08-20T01:00:00", 1, LuckyDrawReason.RANDOM)))

        val outcome = EmployeeSyncEngine(FakeSource(), draws).sync(api, "key", "TABLET-1")

        assertTrue(outcome is EmployeeSyncOutcome.Completed)
        assertEquals(0, (outcome as EmployeeSyncOutcome.Completed).luckyDrawsUploaded)
        assertTrue(draws.status.isEmpty())
    }

    @Test
    fun `khong co dong Pending thi khong goi endpoint lucky-draws`() = runBlocking {
        enqueueMainSteps("""[]""")
        val outcome = EmployeeSyncEngine(FakeSource(), FakeLuckyDrawSource(emptyList())).sync(api, "key", "TABLET-1")
        assertTrue(outcome is EmployeeSyncOutcome.Completed)
        assertEquals(2, server.requestCount)
    }

    private fun win(id: Long, code: String, date: String, wonAt: String, cans: Int, reason: String) =
        LuckyDrawWinEntity(
            localId = id, employeeCode = code, fullName = code, drawDate = date, wonAtUtc = wonAt,
            cans = cans, reason = reason, weight = 1.0, chance = 0.1, deviceCode = "TABLET-1"
        )
}

private class FakeLuckyDrawSource(private val pending: List<LuckyDrawWinEntity>) : LuckyDrawSyncSource {
    val status = mutableMapOf<Long, String>()
    override suspend fun getPendingWins(limit: Int): List<LuckyDrawWinEntity> =
        pending.filter { status[it.localId] == null }.take(limit)

    override suspend fun markWinSynced(localId: Long, status: String) {
        this.status[localId] = status
    }
}

private class FakeSource : EmployeeSyncSource {
    var erpEmployees: List<ErpEmployeeEntity> = emptyList()
    override suspend fun getAllEnrolled(): List<EnrolledEmployeeEntity> = emptyList()
    override suspend fun getEnrolledByCode(employeeCode: String): EnrolledEmployeeEntity? = null
    override suspend fun markFaceUploaded(employeeCode: String, uploadedAt: String) = Unit
    override suspend fun saveEmbeddingFromServer(employeeCode: String, fullName: String?, embedding: FloatArray, updatedAt: String) = Unit
    override suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>) {
        erpEmployees = employees
    }
}

package com.dgpack.chamcong

import com.dgpack.chamcong.data.db.AttendanceEventEntity
import com.dgpack.chamcong.data.db.SyncStatus
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.sync.AttendanceSyncSource
import com.dgpack.chamcong.sync.SyncEngine
import com.dgpack.chamcong.sync.SyncOutcome
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
 * Test tích hợp luồng đồng bộ (mục [9]) bằng MockWebServer — không cần server ERP thật,
 * nhưng mô phỏng đúng contract mục [5]: Inserted/Duplicate/UnknownEmployee, 401, 503,
 * mất mạng giữa chừng.
 */
class SyncEngineTest {

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

    @Test
    fun `dong bo thanh cong voi ca 3 trang thai Inserted Duplicate UnknownEmployee`() = runBlocking {
        val source = FakeAttendanceSyncSource(
            listOf(
                event(1, "NV001", "2026-08-30T01:00:00"),
                event(2, "NV002", "2026-08-30T01:05:00"),
                event(3, "NVLA", "2026-08-30T01:10:00")
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"employeeCode":"NV001","eventTime":"2026-08-30T01:00:00","status":"Inserted"},
                  {"employeeCode":"NV002","eventTime":"2026-08-30T01:05:00","status":"Duplicate"},
                  {"employeeCode":"NVLA","eventTime":"2026-08-30T01:10:00","status":"UnknownEmployee"}
                ]
                """.trimIndent()
            )
        )

        val outcome = SyncEngine(source).syncPending(api, apiKey = "key123", deviceCode = "Cong-Chinh")

        assertEquals(SyncOutcome.Completed(1, 1, 1), outcome)
        assertEquals(SyncStatus.SYNCED, source.events[0].syncStatus)
        assertEquals(SyncStatus.SYNCED, source.events[1].syncStatus)
        assertEquals(SyncStatus.UNKNOWN_EMPLOYEE, source.events[2].syncStatus)

        val request = server.takeRequest()
        assertEquals("key123", request.getHeader("X-Attendance-Api-Key"))
    }

    @Test
    fun `401 tra ve Unauthorized va khong sua doi trang thai su kien`() = runBlocking {
        val source = FakeAttendanceSyncSource(listOf(event(1, "NV001", "2026-08-30T01:00:00")))
        server.enqueue(MockResponse().setResponseCode(401))

        val outcome = SyncEngine(source).syncPending(api, apiKey = "sai-key", deviceCode = "Cong-Chinh")

        assertEquals(SyncOutcome.Unauthorized, outcome)
        assertEquals(SyncStatus.PENDING, source.events[0].syncStatus)
    }

    @Test
    fun `503 tra ve ServerMisconfigured`() = runBlocking {
        val source = FakeAttendanceSyncSource(listOf(event(1, "NV001", "2026-08-30T01:00:00")))
        server.enqueue(MockResponse().setResponseCode(503))

        val outcome = SyncEngine(source).syncPending(api, apiKey = "key123", deviceCode = "Cong-Chinh")

        assertEquals(SyncOutcome.ServerMisconfigured, outcome)
    }

    @Test
    fun `mat mang giua chung tra ve NetworkError va giu Pending`() = runBlocking {
        val source = FakeAttendanceSyncSource(listOf(event(1, "NV001", "2026-08-30T01:00:00")))
        server.shutdown() // giả lập mất kết nối hoàn toàn

        val outcome = SyncEngine(source).syncPending(api, apiKey = "key123", deviceCode = "Cong-Chinh")

        assertTrue(outcome is SyncOutcome.NetworkError)
        assertEquals(SyncStatus.PENDING, source.events[0].syncStatus)
    }

    @Test
    fun `hang doi rong khong goi API`() = runBlocking {
        val source = FakeAttendanceSyncSource(emptyList())

        val outcome = SyncEngine(source).syncPending(api, apiKey = "key123", deviceCode = "Cong-Chinh")

        assertEquals(SyncOutcome.Completed(0, 0, 0), outcome)
        assertEquals(0, server.requestCount)
    }

    private fun event(id: Long, code: String, eventTime: String) = AttendanceEventEntity(
        localId = id,
        employeeCode = code,
        eventTimeUtc = eventTime,
        deviceCode = "Cong-Chinh",
        syncStatus = SyncStatus.PENDING,
        createdAt = eventTime
    )
}

private class FakeAttendanceSyncSource(initial: List<AttendanceEventEntity>) : AttendanceSyncSource {
    val events: MutableList<AttendanceEventEntity> = initial.toMutableList()

    override suspend fun getPendingBatch(limit: Int): List<AttendanceEventEntity> =
        events.filter { it.syncStatus == SyncStatus.PENDING }.take(limit)

    override suspend fun markStatus(event: AttendanceEventEntity, newStatus: String, syncedAtNow: Boolean) {
        replace(event.copy(syncStatus = newStatus, syncAttempts = event.syncAttempts + 1))
    }

    override suspend fun markRetryLater(event: AttendanceEventEntity) {
        replace(event.copy(syncAttempts = event.syncAttempts + 1))
    }

    private fun replace(updated: AttendanceEventEntity) {
        val index = events.indexOfFirst { it.localId == updated.localId }
        if (index >= 0) events[index] = updated
    }
}

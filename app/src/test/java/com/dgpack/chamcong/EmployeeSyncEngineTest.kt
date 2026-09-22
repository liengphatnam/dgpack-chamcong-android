package com.dgpack.chamcong

import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.sync.EmployeeSyncEngine
import com.dgpack.chamcong.sync.EmployeeSyncOutcome
import com.dgpack.chamcong.sync.EmployeeSyncSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Bước chính của EmployeeSyncEngine: kéo danh sách NV từ ERP (tên, thẻ, ngày sinh, trễ/sớm,
 * khen thưởng) và các mã lỗi 401/404/503/mất mạng.
 */
class EmployeeSyncEngineTest {

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
    fun `keo danh sach NV - chuan hoa ma the, ngay sinh kieu NET, giu NV nghi viec de app tu loc`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"employeeCode":" NV001 ","fullName":"Nguyễn A","isActive":true,"cardId":"04:a1:b2:c3",
                   "birthDate":"1990-08-20T00:00:00","lateEarlyCount30d":1,"commendationCount":2},
                  {"employeeCode":"NV005","fullName":"Đã nghỉ","isActive":false}
                ]
                """.trimIndent()
            )
        )
        val source = FakeSource()

        val outcome = EmployeeSyncEngine(source).sync(api, "key", "TABLET-1")

        assertEquals(EmployeeSyncOutcome.Completed(employees = 2), outcome)
        val nv001 = source.erpEmployees.first { it.employeeCode == "NV001" }
        assertEquals("04A1B2C3", nv001.cardId)
        assertEquals("1990-08-20", nv001.birthDate)
        assertEquals(1, nv001.lateEarlyCount30d)
        assertEquals(2, nv001.commendationCount)
        assertNull(source.erpEmployees.first { it.employeeCode == "NV005" }.cardId)
        assertTrue(server.takeRequest().path!!.startsWith("/api/v1/attendance/employees"))
        assertEquals(1, server.requestCount) // không có bước phụ nào khi thiếu source thẻ/quay thưởng
    }

    @Test
    fun `401 - Unauthorized, 404 - EndpointMissing, 503 - ServerMisconfigured, khac - UnexpectedHttp`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401))
        assertEquals(EmployeeSyncOutcome.Unauthorized, EmployeeSyncEngine(FakeSource()).sync(api, "k", "d"))
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(EmployeeSyncOutcome.EndpointMissing, EmployeeSyncEngine(FakeSource()).sync(api, "k", "d"))
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(EmployeeSyncOutcome.ServerMisconfigured, EmployeeSyncEngine(FakeSource()).sync(api, "k", "d"))
        server.enqueue(MockResponse().setResponseCode(500))
        assertEquals(EmployeeSyncOutcome.UnexpectedHttp(500), EmployeeSyncEngine(FakeSource()).sync(api, "k", "d"))
    }

    @Test
    fun `mat mang - NetworkError, khong dong vao bang NV`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        val source = FakeSource()
        val outcome = EmployeeSyncEngine(source).sync(api, "k", "d")
        assertTrue(outcome is EmployeeSyncOutcome.NetworkError)
        assertTrue(source.erpEmployees.isEmpty())
    }
}

private class FakeSource : EmployeeSyncSource {
    var erpEmployees: List<ErpEmployeeEntity> = emptyList()
    override suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>) {
        erpEmployees = employees
    }
}

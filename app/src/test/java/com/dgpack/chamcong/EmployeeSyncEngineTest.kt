package com.dgpack.chamcong

import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.face.EmbeddingCodec
import com.dgpack.chamcong.face.FaceEmbedder
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.sync.EmployeeSyncEngine
import com.dgpack.chamcong.sync.EmployeeSyncOutcome
import com.dgpack.chamcong.sync.EmployeeSyncSource
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Test luồng đồng bộ nhân viên + embedding (API_FACE_SYNC.md) bằng MockWebServer:
 * kéo danh sách NV, đẩy embedding chưa đồng bộ, tải embedding máy khác về, đếm NV
 * chưa có khuôn mặt, và các mã lỗi 401/404/mất mạng.
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
    fun `dong bo day du - keo NV, day embedding moi, tai embedding may khac ve, dem NV chua co mat`() = runBlocking {
        val source = FakeEmployeeSyncSource(
            listOf(
                enrolled("NV001", enrolledAt = "2026-09-16T01:00:00", faceSyncedAt = null),        // chưa đẩy
                enrolled("NV002", enrolledAt = "2026-09-10T00:00:00", faceSyncedAt = "2026-09-10T00:00:00") // đã đẩy
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"employeeCode":"NV001","fullName":"Nguyễn A","isActive":true,"hasFaceEmbedding":false,"faceUpdatedAt":null},
                  {"employeeCode":"NV002","fullName":"Trần B","isActive":true,"hasFaceEmbedding":true,"faceUpdatedAt":"2026-09-10T00:00:00"},
                  {"employeeCode":"NV003","fullName":"Lê C","isActive":true,"hasFaceEmbedding":true,"faceUpdatedAt":"2026-09-12T00:00:00"},
                  {"employeeCode":"NV004","fullName":"Phạm D","isActive":true,"hasFaceEmbedding":false,"faceUpdatedAt":null},
                  {"employeeCode":"NV005","fullName":"Đã nghỉ","isActive":false,"hasFaceEmbedding":false,"faceUpdatedAt":null}
                ]
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""[{"employeeCode":"NV001","status":"Saved"}]""")
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                [
                  {"employeeCode":"NV002","model":"mobilefacenet-192-align2","dimension":192,"embedding":[${vec(0.5f)}],"updatedAt":"2026-09-10T00:00:00","deviceCode":"Cong-Chinh"},
                  {"employeeCode":"NV003","model":"mobilefacenet-192-align2","dimension":192,"embedding":[${vec(0.25f)}],"updatedAt":"2026-09-12T00:00:00","deviceCode":"Cong-Phu"},
                  {"employeeCode":"NV009","model":"other-model-512","dimension":512,"embedding":[${vec(0.1f)}],"updatedAt":"2026-09-12T00:00:00","deviceCode":null}
                ]
                """.trimIndent()
            )
        )

        val outcome = EmployeeSyncEngine(source).sync(api, apiKey = "key123", deviceCode = "Cong-Chinh")

        assertEquals(
            EmployeeSyncOutcome.Completed(employees = 5, uploaded = 1, downloaded = 1, unknownEmployee = 0, missingFace = 1),
            outcome
        )

        // NV001 đã được đánh dấu đẩy lên, mốc = enrolledAt
        assertEquals("2026-09-16T01:00:00", source.enrolled["NV001"]!!.faceSyncedAt)
        // NV003 tải từ server về, tên lấy từ danh sách ERP
        val nv003 = source.enrolled["NV003"]
        assertNotNull(nv003)
        assertEquals("Lê C", nv003!!.fullName)
        assertEquals("2026-09-12T00:00:00", nv003.enrolledAt)
        assertEquals("2026-09-12T00:00:00", nv003.faceSyncedAt)
        assertEquals(0.25f, EmbeddingCodec.fromByteArray(nv003.embedding)[0], 1e-6f)
        // NV002 server cùng mốc -> không ghi đè, giữ nguyên bản local (toàn 1f)
        assertEquals(1f, EmbeddingCodec.fromByteArray(source.enrolled["NV002"]!!.embedding)[0], 1e-6f)
        // Model lạ (NV009) bị bỏ qua
        assertNull(source.enrolled["NV009"])
        // Danh sách ERP đã thay
        assertEquals(5, source.erpEmployees.size)

        // 3 request đúng thứ tự, đúng header, đúng đường dẫn
        val r1 = server.takeRequest()
        assertEquals("GET", r1.method)
        assertEquals("/api/v1/attendance/employees", r1.path)
        assertEquals("key123", r1.getHeader("X-Attendance-Api-Key"))

        val r2 = server.takeRequest()
        assertEquals("POST", r2.method)
        assertEquals("/api/v1/attendance/face-embeddings", r2.path)
        val body = r2.body.readUtf8()
        assertTrue(body.contains("\"employeeCode\":\"NV001\""))
        assertTrue(body.contains("\"model\":\"mobilefacenet-192-align2\""))
        assertTrue(body.contains("\"dimension\":192"))
        assertTrue(body.contains("\"enrolledAt\":\"2026-09-16T01:00:00\""))
        assertTrue(body.contains("\"deviceCode\":\"Cong-Chinh\""))
        assertFalse(body.contains("NV002")) // đã đồng bộ, không gửi lại

        val r3 = server.takeRequest()
        assertEquals("GET", r3.method)
        assertEquals("/api/v1/attendance/face-embeddings", r3.path)
    }

    @Test
    fun `khong co embedding can day thi bo qua buoc POST`() = runBlocking {
        val source = FakeEmployeeSyncSource(
            listOf(enrolled("NV001", "2026-09-10T00:00:00", faceSyncedAt = "2026-09-10T00:00:00"))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"employeeCode":"NV001","fullName":"A","isActive":true,"hasFaceEmbedding":true}]"""))
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        val outcome = EmployeeSyncEngine(source).sync(api, "key", "dev")

        assertEquals(EmployeeSyncOutcome.Completed(1, 0, 0, 0, 0), outcome)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `UnknownEmployee khi day - giu local, khong bi server ghi de`() = runBlocking {
        val source = FakeEmployeeSyncSource(
            listOf(enrolled("NVLA", "2026-09-16T01:00:00", faceSyncedAt = null))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""[{"employeeCode":"NVLA","status":"UnknownEmployee"}]"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"employeeCode":"NVLA","model":"mobilefacenet-192-align2","dimension":192,"embedding":[${vec(0.9f)}],"updatedAt":"2026-09-20T00:00:00"}]"""
            )
        )

        val outcome = EmployeeSyncEngine(source).sync(api, "key", "dev")

        assertEquals(EmployeeSyncOutcome.Completed(0, 0, 0, 1, 0), outcome)
        assertNull(source.enrolled["NVLA"]!!.faceSyncedAt)
        assertEquals(1f, EmbeddingCodec.fromByteArray(source.enrolled["NVLA"]!!.embedding)[0], 1e-6f)
    }

    @Test
    fun `401 tra ve Unauthorized va dung ngay`() = runBlocking {
        val source = FakeEmployeeSyncSource(listOf(enrolled("NV001", "2026-09-16T01:00:00", null)))
        server.enqueue(MockResponse().setResponseCode(401))

        val outcome = EmployeeSyncEngine(source).sync(api, "sai", "dev")

        assertEquals(EmployeeSyncOutcome.Unauthorized, outcome)
        assertEquals(1, server.requestCount)
        assertNull(source.enrolled["NV001"]!!.faceSyncedAt)
    }

    @Test
    fun `404 nghia la server ERP chua trien khai endpoint`() = runBlocking {
        val source = FakeEmployeeSyncSource(emptyList())
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(EmployeeSyncOutcome.EndpointMissing, EmployeeSyncEngine(source).sync(api, "key", "dev"))
    }

    @Test
    fun `mat mang tra ve NetworkError`() = runBlocking {
        val source = FakeEmployeeSyncSource(emptyList())
        server.shutdown()

        assertTrue(EmployeeSyncEngine(source).sync(api, "key", "dev") is EmployeeSyncOutcome.NetworkError)
    }

    @Test
    fun `needsUpload - chua day, hoac enroll lai sau lan day truoc`() {
        assertTrue(EmployeeSyncEngine.needsUpload(enrolled("A", "2026-09-16T01:00:00", null)))
        assertTrue(EmployeeSyncEngine.needsUpload(enrolled("A", "2026-09-16T01:00:00", "2026-09-15T00:00:00")))
        assertFalse(EmployeeSyncEngine.needsUpload(enrolled("A", "2026-09-16T01:00:00", "2026-09-16T01:00:00")))
        assertFalse(EmployeeSyncEngine.needsUpload(enrolled("A", "2026-09-16T01:00:00", "2026-09-17T00:00:00")))
    }

    private fun vec(value: Float): String = List(FaceEmbedder.EMBEDDING_SIZE) { value }.joinToString(",")

    private fun enrolled(code: String, enrolledAt: String, faceSyncedAt: String?) = EnrolledEmployeeEntity(
        employeeCode = code,
        fullName = "Tên $code",
        embedding = EmbeddingCodec.toByteArray(FloatArray(FaceEmbedder.EMBEDDING_SIZE) { 1f }),
        enrolledAt = enrolledAt,
        photoSample = null,
        faceSyncedAt = faceSyncedAt
    )
}

private class FakeEmployeeSyncSource(initial: List<EnrolledEmployeeEntity>) : EmployeeSyncSource {
    val enrolled: MutableMap<String, EnrolledEmployeeEntity> = initial.associateBy { it.employeeCode }.toMutableMap()
    var erpEmployees: List<ErpEmployeeEntity> = emptyList()

    override suspend fun getAllEnrolled(): List<EnrolledEmployeeEntity> = enrolled.values.toList()

    override suspend fun getEnrolledByCode(employeeCode: String): EnrolledEmployeeEntity? = enrolled[employeeCode]

    override suspend fun markFaceUploaded(employeeCode: String, uploadedAt: String) {
        enrolled[employeeCode] = enrolled.getValue(employeeCode).copy(faceSyncedAt = uploadedAt)
    }

    override suspend fun saveEmbeddingFromServer(employeeCode: String, fullName: String?, embedding: FloatArray, updatedAt: String) {
        val existing = enrolled[employeeCode]
        enrolled[employeeCode] = EnrolledEmployeeEntity(
            employeeCode = employeeCode,
            fullName = fullName ?: existing?.fullName ?: employeeCode,
            embedding = EmbeddingCodec.toByteArray(embedding),
            enrolledAt = updatedAt,
            photoSample = existing?.photoSample,
            faceSyncedAt = updatedAt
        )
    }

    override suspend fun replaceErpEmployees(employees: List<ErpEmployeeEntity>) {
        erpEmployees = employees
    }
}

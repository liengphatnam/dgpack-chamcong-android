package com.dgpack.chamcong.sync

import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.data.db.ErpEmployeeEntity
import com.dgpack.chamcong.face.EmbeddingCodec
import com.dgpack.chamcong.face.FaceEmbedder
import com.dgpack.chamcong.network.AttendanceApi
import com.dgpack.chamcong.network.FaceEmbeddingUploadRequest
import com.dgpack.chamcong.network.FaceEmbeddingUploadResult
import com.dgpack.chamcong.network.FaceModelInfo
import com.dgpack.chamcong.util.TimeUtils
import retrofit2.Response
import java.io.IOException

sealed class EmployeeSyncOutcome {
    /**
     * @param employees      số NV kéo về từ ERP
     * @param uploaded       số embedding vừa tải lên server thành công
     * @param downloaded     số embedding tải về từ server (enroll ở máy khác)
     * @param unknownEmployee số embedding local có mã NV không tồn tại bên ERP
     * @param missingFace    số NV đang hoạt động bên ERP mà CHƯA có khuôn mặt ở đâu cả
     */
    data class Completed(
        val employees: Int,
        val uploaded: Int,
        val downloaded: Int,
        val unknownEmployee: Int,
        val missingFace: Int
    ) : EmployeeSyncOutcome()

    /** Chưa nhập API key/DeviceCode ở Cài đặt — không gọi mạng. */
    object NotConfigured : EmployeeSyncOutcome()
    object Unauthorized : EmployeeSyncOutcome()
    object ServerMisconfigured : EmployeeSyncOutcome()
    /** Server ERP chưa triển khai endpoint (404) — cần deploy phía DGP.ERP trước, xem API_FACE_SYNC.md. */
    object EndpointMissing : EmployeeSyncOutcome()
    data class NetworkError(val message: String?) : EmployeeSyncOutcome()
    data class UnexpectedHttp(val code: Int) : EmployeeSyncOutcome()
}

/**
 * Đồng bộ 2 chiều NHẸ giữa thiết bị và ERP (API_FACE_SYNC.md):
 *  1. Kéo danh sách dm.Employee về (để biết NV nào chưa có khuôn mặt).
 *  2. Đẩy embedding enroll ở máy này mà server chưa có / đã enroll lại sau lần đẩy trước.
 *  3. Tải embedding server đang giữ về (NV enroll ở tablet khác) để máy này cũng nhận diện được.
 *
 * Quy tắc xung đột: bản enroll CHƯA đẩy lên của máy này thắng (sẽ đẩy đè); ngoài ra bản
 * có mốc thời gian mới hơn thắng. Logic thuần, test bằng MockWebServer (EmployeeSyncEngineTest).
 */
class EmployeeSyncEngine(private val source: EmployeeSyncSource) {

    suspend fun sync(
        api: AttendanceApi,
        apiKey: String,
        deviceCode: String,
        uploadBatchSize: Int = UPLOAD_BATCH_SIZE
    ): EmployeeSyncOutcome {
        // ---- 1. Danh sách nhân viên ERP ----
        val employeesResponse = try {
            api.getEmployees(apiKey)
        } catch (e: IOException) {
            return EmployeeSyncOutcome.NetworkError(e.message)
        }
        httpFailure(employeesResponse)?.let { return it }

        val now = TimeUtils.nowUtcIso()
        val erpEmployees = employeesResponse.body().orEmpty().map { dto ->
            ErpEmployeeEntity(
                employeeCode = dto.employeeCode.trim(),
                fullName = dto.fullName.trim(),
                isActive = dto.isActive,
                hasFaceOnServer = dto.hasFaceEmbedding,
                faceUpdatedAt = dto.faceUpdatedAt,
                syncedAt = now
            )
        }
        source.replaceErpEmployees(erpEmployees)
        val erpNameByCode = erpEmployees.associate { it.employeeCode to it.fullName }

        // ---- 2. Đẩy embedding local chưa đồng bộ ----
        var uploaded = 0
        var unknown = 0
        val pending = source.getAllEnrolled().filter { needsUpload(it) }
        for (batch in pending.chunked(uploadBatchSize)) {
            val body = batch.map { entity ->
                FaceEmbeddingUploadRequest(
                    employeeCode = entity.employeeCode,
                    model = FaceModelInfo.MODEL_NAME,
                    dimension = FaceEmbedder.EMBEDDING_SIZE,
                    embedding = EmbeddingCodec.fromByteArray(entity.embedding).toList(),
                    enrolledAt = entity.enrolledAt,
                    deviceCode = deviceCode
                )
            }
            val response = try {
                api.uploadFaceEmbeddings(apiKey, body)
            } catch (e: IOException) {
                return EmployeeSyncOutcome.NetworkError(e.message)
            }
            httpFailure(response)?.let { return it }

            val results = response.body().orEmpty()
            batch.forEachIndexed { index, entity ->
                when (results.getOrNull(index)?.status) {
                    FaceEmbeddingUploadResult.STATUS_SAVED -> {
                        // Server lưu enrolledAt làm updatedAt -> ghi đúng mốc đó để bước 3
                        // và các lần sau so sánh khớp nhau.
                        source.markFaceUploaded(entity.employeeCode, entity.enrolledAt)
                        uploaded++
                    }
                    FaceEmbeddingUploadResult.STATUS_UNKNOWN_EMPLOYEE -> unknown++
                    else -> Unit // để lại, lần sau đẩy tiếp
                }
            }
        }

        // ---- 3. Tải embedding trên server về ----
        val downloadResponse = try {
            api.getFaceEmbeddings(apiKey)
        } catch (e: IOException) {
            return EmployeeSyncOutcome.NetworkError(e.message)
        }
        httpFailure(downloadResponse)?.let { return it }

        var downloaded = 0
        for (remote in downloadResponse.body().orEmpty()) {
            // Khác model/số chiều -> không so khớp được với model đang chạy, bỏ qua.
            if (remote.model != FaceModelInfo.MODEL_NAME || remote.dimension != FaceEmbedder.EMBEDDING_SIZE) continue
            if (remote.embedding.size != FaceEmbedder.EMBEDDING_SIZE) continue

            val local = source.getEnrolledByCode(remote.employeeCode)
            val shouldTake = when {
                local == null -> true
                needsUpload(local) -> false // local mới hơn và chưa đẩy được (vd UnknownEmployee) -> giữ local
                else -> remote.updatedAt > local.enrolledAt
            }
            if (!shouldTake) continue

            source.saveEmbeddingFromServer(
                employeeCode = remote.employeeCode,
                fullName = erpNameByCode[remote.employeeCode] ?: local?.fullName,
                embedding = remote.embedding.toFloatArray(),
                updatedAt = remote.updatedAt
            )
            downloaded++
        }

        // ---- Thống kê NV chưa có khuôn mặt ở đâu cả ----
        val enrolledCodes = source.getAllEnrolled().map { it.employeeCode }.toSet()
        val missingFace = erpEmployees.count { it.isActive && !it.hasFaceOnServer && it.employeeCode !in enrolledCodes }

        return EmployeeSyncOutcome.Completed(
            employees = erpEmployees.size,
            uploaded = uploaded,
            downloaded = downloaded,
            unknownEmployee = unknown,
            missingFace = missingFace
        )
    }

    private fun httpFailure(response: Response<*>): EmployeeSyncOutcome? = when (response.code()) {
        200 -> null
        401 -> EmployeeSyncOutcome.Unauthorized
        404 -> EmployeeSyncOutcome.EndpointMissing
        503 -> EmployeeSyncOutcome.ServerMisconfigured
        else -> EmployeeSyncOutcome.UnexpectedHttp(response.code())
    }

    companion object {
        private const val UPLOAD_BATCH_SIZE = 50

        /**
         * Chưa từng đẩy lên, hoặc đã enroll lại sau lần đẩy gần nhất. So sánh chuỗi ISO
         * cùng định dạng "yyyy-MM-ddTHH:mm:ss" nên so sánh chuỗi = so sánh thời gian.
         */
        fun needsUpload(entity: EnrolledEmployeeEntity): Boolean {
            val synced = entity.faceSyncedAt ?: return true
            return synced < entity.enrolledAt
        }
    }
}

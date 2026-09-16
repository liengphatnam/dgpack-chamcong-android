package com.dgpack.chamcong.network

import kotlinx.serialization.Serializable

/*
 * Contract đồng bộ NHÂN VIÊN + EMBEDDING khuôn mặt với ERP — xem API_FACE_SYNC.md ở repo
 * này. Đây là 3 endpoint MỚI phía ERP (chưa có trong chamcongFaceID.md mục [5]), dùng
 * chung header X-Attendance-Api-Key và cách xử lý 401/503 như sync-events.
 */

/** GET api/v1/attendance/employees — 1 phần tử = 1 dòng dm.Employee. */
@Serializable
data class ErpEmployeeDto(
    val employeeCode: String,
    val fullName: String,
    val isActive: Boolean = true,
    /** Server đã có embedding cho NV này (bảng hr.EmployeeFaceEmbedding) hay chưa. */
    val hasFaceEmbedding: Boolean = false,
    /** ISO-8601 UTC, null nếu chưa có embedding. */
    val faceUpdatedAt: String? = null
)

/** POST api/v1/attendance/face-embeddings — body là mảng, mỗi phần tử 1 NV. */
@Serializable
data class FaceEmbeddingUploadRequest(
    val employeeCode: String,
    /** Định danh model để server/app khác không so khớp nhầm embedding khác model. */
    val model: String,
    val dimension: Int,
    val embedding: List<Float>,
    /** ISO-8601 UTC lúc enroll trên thiết bị — server lưu làm updatedAt. */
    val enrolledAt: String,
    val deviceCode: String?
)

/** Response của POST face-embeddings, cùng thứ tự với request. */
@Serializable
data class FaceEmbeddingUploadResult(
    val employeeCode: String,
    val status: String
) {
    companion object {
        const val STATUS_SAVED = "Saved"
        const val STATUS_UNKNOWN_EMPLOYEE = "UnknownEmployee"
    }
}

/** GET api/v1/attendance/face-embeddings — toàn bộ embedding đang có trên server. */
@Serializable
data class FaceEmbeddingDownloadDto(
    val employeeCode: String,
    val model: String,
    val dimension: Int,
    val embedding: List<Float>,
    /** ISO-8601 UTC — chính là enrolledAt mà thiết bị đã gửi lên. */
    val updatedAt: String,
    val deviceCode: String? = null
)

object FaceModelInfo {
    /** Tên model hiện app dùng (MobileFaceNet, 192 chiều) — đổi model thì đổi tên này. */
    const val MODEL_NAME = "mobilefacenet-192"
}

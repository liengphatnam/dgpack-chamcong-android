package com.dgpack.chamcong.network

import kotlinx.serialization.Serializable

/**
 * Request body theo ĐÚNG contract mục [5.3] — mảng JSON (không bọc object),
 * KHÔNG tự thêm field nào khác ngoài 3 field này.
 */
@Serializable
data class SyncEventRequest(
    val employeeCode: String,
    val eventTime: String,
    val deviceCode: String?
)

/** Response theo mục [5.4]. status là 1 trong: "Inserted" | "Duplicate" | "UnknownEmployee". */
@Serializable
data class SyncEventResult(
    val employeeCode: String,
    val eventTime: String,
    val status: String
) {
    companion object {
        const val STATUS_INSERTED = "Inserted"
        const val STATUS_DUPLICATE = "Duplicate"
        const val STATUS_UNKNOWN_EMPLOYEE = "UnknownEmployee"
    }
}

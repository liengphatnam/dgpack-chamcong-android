package com.dgpack.chamcong.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * employee_code PHẢI khớp dm.Employee.EmployeeCode bên ERP.
 * embedding là vector trung bình (đã L2-normalize) từ 3-5 ảnh chụp lúc enroll.
 */
@Entity(tableName = "enrolled_employee")
data class EnrolledEmployeeEntity(
    @PrimaryKey
    val employeeCode: String,
    val fullName: String,
    val embedding: ByteArray,
    val enrolledAt: String,
    val photoSample: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnrolledEmployeeEntity) return false
        return employeeCode == other.employeeCode &&
            fullName == other.fullName &&
            embedding.contentEquals(other.embedding) &&
            enrolledAt == other.enrolledAt &&
            (photoSample?.contentEquals(other.photoSample ?: ByteArray(0)) ?: (other.photoSample == null))
    }

    override fun hashCode(): Int {
        var result = employeeCode.hashCode()
        result = 31 * result + fullName.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + enrolledAt.hashCode()
        result = 31 * result + (photoSample?.contentHashCode() ?: 0)
        return result
    }
}

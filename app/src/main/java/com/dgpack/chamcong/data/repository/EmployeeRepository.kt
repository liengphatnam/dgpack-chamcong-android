package com.dgpack.chamcong.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dgpack.chamcong.data.db.EnrolledEmployeeDao
import com.dgpack.chamcong.data.db.EnrolledEmployeeEntity
import com.dgpack.chamcong.face.EmbeddingCodec
import com.dgpack.chamcong.face.EnrolledFace
import com.dgpack.chamcong.face.FaceMatcher
import com.dgpack.chamcong.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/**
 * Quản lý danh sách nhân viên đã enroll. Giữ 1 bản cache trong RAM (mục [4.1]: "nạp vào
 * RAM lúc khởi động app") để FaceAnalyzer so khớp nhanh mỗi frame, không query DB liên tục.
 */
class EmployeeRepository(private val dao: EnrolledEmployeeDao) {

    private val _matchCache = MutableStateFlow<List<EnrolledFace>>(emptyList())
    val matchCache: StateFlow<List<EnrolledFace>> get() = _matchCache.asStateFlow()

    // employeeCode -> fullName, chỉ để hiển thị overlay lúc chấm công (mục [3]: full_name
    // KHÔNG bao giờ gửi lên server).
    private val _nameCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val nameCache: StateFlow<Map<String, String>> get() = _nameCache.asStateFlow()

    fun observeAll(): Flow<List<EnrolledEmployeeEntity>> = dao.observeAll()

    suspend fun refreshCache() {
        val all = dao.getAllOnce()
        _matchCache.value = all.map { EnrolledFace(it.employeeCode, EmbeddingCodec.fromByteArray(it.embedding)) }
        _nameCache.value = all.associate { it.employeeCode to it.fullName }
    }

    /** [samples] là 3-5 embedding từ các ảnh chụp góc khác nhau (mục [4.2]). */
    suspend fun enroll(employeeCode: String, fullName: String, samples: List<FloatArray>, photoSample: Bitmap?) {
        val averaged = FaceMatcher.averageEmbedding(samples)
        val entity = EnrolledEmployeeEntity(
            employeeCode = employeeCode.trim(),
            fullName = fullName.trim(),
            embedding = EmbeddingCodec.toByteArray(averaged),
            enrolledAt = TimeUtils.nowUtcIso(),
            photoSample = photoSample?.let { bitmapToJpeg(it) }
        )
        dao.upsert(entity)
        refreshCache()
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    fun jpegToBitmap(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

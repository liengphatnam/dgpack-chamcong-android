package com.dgpack.chamcong.ui.enroll

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val MIN_ENROLL_SAMPLES = 3
const val MAX_ENROLL_SAMPLES = 5

data class EnrollUiState(
    val employeeCode: String = "",
    val fullName: String = "",
    val capturedCount: Int = 0,
    val faceReady: Boolean = false,
    val message: String? = null,
    val saveSuccess: Boolean = false
)

class EnrollViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _state = MutableStateFlow(EnrollUiState())
    val state: StateFlow<EnrollUiState> = _state.asStateFlow()

    private val embeddings = mutableListOf<FloatArray>()

    @Volatile private var lastLiveBitmap: Bitmap? = null
    private var lastCapturedSampleBitmap: Bitmap? = null

    fun onEmployeeCodeChange(value: String) {
        _state.update { it.copy(employeeCode = value, message = null) }
    }

    fun onFullNameChange(value: String) {
        _state.update { it.copy(fullName = value) }
    }

    /** Gọi liên tục từ camera preview (mỗi khi phát hiện 1 khuôn mặt) để giữ khung hình mới nhất. */
    fun onLiveFaceDetected(result: FaceDetectionResult) {
        val previous = lastLiveBitmap
        lastLiveBitmap = result.croppedBitmap
        previous?.recycle()
        if (!_state.value.faceReady) {
            _state.update { it.copy(faceReady = true) }
        }
    }

    fun capturePhoto() {
        if (embeddings.size >= MAX_ENROLL_SAMPLES) return
        val source = lastLiveBitmap
        if (source == null) {
            _state.update { it.copy(message = "khong_thay_mat") }
            return
        }
        val copy = source.copy(source.config ?: Bitmap.Config.ARGB_8888, false)
        viewModelScope.launch(Dispatchers.Default) {
            val embedding = app.faceEmbedder.embed(copy)
            embeddings.add(embedding)
            lastCapturedSampleBitmap?.recycle()
            lastCapturedSampleBitmap = copy
            withContext(Dispatchers.Main) {
                _state.update { it.copy(capturedCount = embeddings.size) }
            }
        }
    }

    fun save() {
        val code = _state.value.employeeCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(message = "thieu_ma") }
            return
        }
        if (embeddings.size < MIN_ENROLL_SAMPLES) {
            _state.update { it.copy(message = "chua_du_anh") }
            return
        }
        viewModelScope.launch {
            app.employeeRepository.enroll(
                employeeCode = code,
                fullName = _state.value.fullName,
                samples = embeddings.toList(),
                photoSample = lastCapturedSampleBitmap
            )
            embeddings.clear()
            lastLiveBitmap?.recycle(); lastLiveBitmap = null
            lastCapturedSampleBitmap = null
            _state.value = EnrollUiState(saveSuccess = true, message = code)
        }
    }

    fun consumeSaveSuccess() {
        _state.update { it.copy(saveSuccess = false, message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        lastLiveBitmap?.recycle()
    }
}

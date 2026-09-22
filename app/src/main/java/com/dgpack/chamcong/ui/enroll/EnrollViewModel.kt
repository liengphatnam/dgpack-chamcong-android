package com.dgpack.chamcong.ui.enroll

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import com.dgpack.chamcong.face.EnrollPose
import com.dgpack.chamcong.face.FaceQualityChecker
import com.dgpack.chamcong.face.QualityHint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Đăng ký cần đủ từng này ảnh ĐẠT CHẤT LƯỢNG, theo đúng thứ tự tư thế [POSE_STEPS]. */
const val REQUIRED_ENROLL_SAMPLES = 5

/** Kịch bản giống eKYC ngân hàng: thẳng → quay nhẹ một bên → quay bên kia → thẳng → thẳng. */
val POSE_STEPS: List<EnrollPose> = listOf(
    EnrollPose.FRONT, EnrollPose.SIDE_A, EnrollPose.SIDE_B, EnrollPose.FRONT, EnrollPose.FRONT
)

/** Số frame liên tiếp đạt 100% trước khi tự chụp — tránh 1 frame "ăn may". */
private const val STABLE_FRAMES_TO_CAPTURE = 2
/** Nghỉ giữa 2 lần tự chụp để người dùng kịp đổi tư thế và thấy tiến độ. */
private const val MIN_MS_BETWEEN_CAPTURES = 900L
/** Không thấy mặt trong khoảng này -> báo "đưa mặt vào khung". */
private const val NO_FACE_TIMEOUT_MS = 1200L

data class EnrollUiState(
    val employeeCode: String = "",
    val fullName: String = "",
    val capturedCount: Int = 0,
    /** 0..100 chất lượng khung hình hiện tại theo FaceQualityChecker. */
    val qualityPercent: Int = 0,
    val hint: QualityHint = QualityHint.NO_FACE,
    /** Tư thế yêu cầu ở bước hiện tại (null khi đã đủ ảnh). */
    val currentPose: EnrollPose? = POSE_STEPS.first(),
    /** Kích thước khung hình phân tích (để vẽ oval đúng vị trí). */
    val frameWidth: Int = 600,
    val frameHeight: Int = 800,
    val message: String? = null,
    val saveSuccess: Boolean = false
) {
    val samplesComplete: Boolean get() = capturedCount >= REQUIRED_ENROLL_SAMPLES
}

/**
 * Đăng ký khuôn mặt kiểu eKYC: camera chạy liên tục, mỗi frame được chấm điểm chất lượng
 * ([FaceQualityChecker]); đạt 100% ổn định thì TỰ chụp, không có nút chụp tay. Ảnh không
 * đạt không bao giờ lọt vào mẫu đăng ký -> loại nguyên nhân nhận nhầm do mẫu mờ/nghiêng/xa.
 */
class EnrollViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _state = MutableStateFlow(EnrollUiState())
    val state: StateFlow<EnrollUiState> = _state.asStateFlow()

    private val embeddings = mutableListOf<FloatArray>()
    private var lastCapturedSampleBitmap: Bitmap? = null

    /** Dấu yaw của ảnh SIDE_A đã chụp; SIDE_B phải ngược dấu (quay sang bên kia). */
    private var sideSign: Int? = null
    private var stableFrames = 0
    private var lastCaptureAt = 0L
    private var noFaceJob: Job? = null

    /**
     * Điền sẵn mã + tên khi mở từ màn Nhân viên ERP. Chỉ áp dụng khi form còn trống để
     * không ghi đè nội dung admin đang gõ dở (recomposition / quay lại màn hình).
     */
    fun prefill(employeeCode: String, fullName: String) {
        if (employeeCode.isBlank()) return
        _state.update {
            if (it.employeeCode.isNotBlank() || it.capturedCount > 0) it
            else it.copy(employeeCode = employeeCode, fullName = fullName, message = null)
        }
    }

    fun onEmployeeCodeChange(value: String) {
        _state.update { it.copy(employeeCode = value, message = null) }
    }

    fun onFullNameChange(value: String) {
        _state.update { it.copy(fullName = value) }
    }

    /** Camera không thấy mặt nào trong frame vừa rồi. */
    fun onNoFace() {
        stableFrames = 0
        scheduleNoFaceHint()
    }

    /** Gọi từ luồng phân tích camera mỗi khi có 1 khuôn mặt (~5 fps). */
    fun onLiveFaceDetected(result: FaceDetectionResult) {
        noFaceJob?.cancel()
        val current = _state.value
        val pose = current.currentPose
        if (pose == null) {
            // Đã đủ ảnh — chỉ cập nhật kích thước khung, không chấm điểm nữa.
            result.croppedBitmap.recycle()
            return
        }

        val requiredSign = if (pose == EnrollPose.SIDE_B) sideSign?.let { -it } else null
        val quality = FaceQualityChecker.evaluate(result.frame, pose, requiredSign)
        _state.update {
            it.copy(
                qualityPercent = quality.percent,
                hint = quality.hint,
                frameWidth = result.frame.imageWidth,
                frameHeight = result.frame.imageHeight
            )
        }

        val now = System.currentTimeMillis()
        if (!quality.passed) {
            stableFrames = 0
            result.croppedBitmap.recycle()
            return
        }
        stableFrames++
        if (stableFrames < STABLE_FRAMES_TO_CAPTURE || now - lastCaptureAt < MIN_MS_BETWEEN_CAPTURES) {
            result.croppedBitmap.recycle()
            return
        }

        // ---- Tự chụp: ảnh đã đạt 100% ở >= 2 frame liên tiếp ----
        stableFrames = 0
        lastCaptureAt = now
        val embedding = app.faceEmbedder.embed(result.croppedBitmap)
        embeddings.add(embedding)
        if (pose == EnrollPose.SIDE_A) sideSign = if (result.frame.yaw >= 0) 1 else -1
        lastCapturedSampleBitmap?.recycle()
        lastCapturedSampleBitmap = result.croppedBitmap // giữ làm ảnh mẫu, không recycle

        val count = embeddings.size
        _state.update {
            it.copy(
                capturedCount = count,
                currentPose = POSE_STEPS.getOrNull(count),
                qualityPercent = if (count >= REQUIRED_ENROLL_SAMPLES) 100 else 0,
                hint = if (count >= REQUIRED_ENROLL_SAMPLES) QualityHint.GOOD else it.hint,
                message = null
            )
        }
    }

    private fun scheduleNoFaceHint() {
        if (noFaceJob?.isActive == true) return
        noFaceJob = viewModelScope.launch {
            delay(NO_FACE_TIMEOUT_MS)
            if (_state.value.currentPose != null) {
                _state.update { it.copy(qualityPercent = 0, hint = QualityHint.NO_FACE) }
            }
        }
    }

    /** Admin muốn chụp lại từ đầu (vd người khác đứng nhầm vào khung). */
    fun resetSamples() {
        embeddings.clear()
        sideSign = null
        stableFrames = 0
        lastCapturedSampleBitmap?.recycle()
        lastCapturedSampleBitmap = null
        _state.update {
            it.copy(capturedCount = 0, currentPose = POSE_STEPS.first(), qualityPercent = 0, message = null)
        }
    }

    fun save() {
        val code = _state.value.employeeCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(message = "thieu_ma") }
            return
        }
        if (embeddings.size < REQUIRED_ENROLL_SAMPLES) {
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
            sideSign = null
            lastCapturedSampleBitmap = null
            _state.value = EnrollUiState(saveSuccess = true, message = code)
            // Đẩy embedding vừa enroll lên ERP ngay (nếu có mạng + đã cấu hình) — xem API_FACE_SYNC.md.
            app.employeeSyncCoordinator.requestSync()
        }
    }

    fun consumeSaveSuccess() {
        _state.update { it.copy(saveSuccess = false, message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        lastCapturedSampleBitmap?.recycle()
    }
}

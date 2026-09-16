package com.dgpack.chamcong.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.face.FaceMatcher
import com.dgpack.chamcong.face.MatchResult
import com.dgpack.chamcong.face.LivenessTracker
import com.dgpack.chamcong.voice.VoiceAnnouncer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val MAX_RECENT_SCANS = 5
private const val LIVENESS_TIMEOUT_MS = 6000L
private const val UNRECOGNIZED_HINT_MS = 1500L
private val TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

data class RecentScan(val fullName: String, val timeLabel: String)

data class CameraUiState(
    val overlayName: String? = null,
    /** % độ tin cậy của lần nhận diện đang hiện overlay — để admin quan sát và hiệu chỉnh ngưỡng. */
    val overlayConfidence: Int? = null,
    val livenessHintName: String? = null,
    /**
     * Có khuôn mặt trước camera nhưng độ tin cậy cao nhất < ngưỡng cấu hình (mặc định 80%)
     * -> hiện "Hệ thống chưa nhận dạng được" kèm % cao nhất đo được. null = ẩn.
     */
    val unrecognizedConfidence: Int? = null,
    val pendingCount: Int = 0,
    val recentScans: List<RecentScan> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hideOverlayJob: Job? = null
    private var hideUnrecognizedJob: Job? = null
    private val voiceAnnouncer = VoiceAnnouncer(app)

    // Xử lý tuần tự trên đúng 1 luồng (limitedParallelism(1)) — tránh 2 frame liên tiếp
    // xử lý chồng chéo làm hỏng trạng thái livenessTracker/candidateCode dùng chung.
    private val faceProcessingDispatcher = Dispatchers.Default.limitedParallelism(1)

    private var candidateCode: String? = null
    private var livenessTracker = LivenessTracker()
    private var livenessResetJob: Job? = null

    init {
        viewModelScope.launch {
            app.attendanceRepository.observeCounts().collect { counts ->
                _uiState.value = _uiState.value.copy(pendingCount = counts.pending)
            }
        }
    }

    /** Gọi từ FaceAnalyzer (luồng background) mỗi khi có 1 khuôn mặt đã crop sẵn 112x112. */
    fun onFaceDetected(result: FaceDetectionResult) {
        viewModelScope.launch(faceProcessingDispatcher) {
            try {
                val embedding = app.faceEmbedder.embed(result.croppedBitmap)
                val settings = app.settingsRepository.current()
                val best = FaceMatcher.findBestMatch(
                    query = embedding,
                    enrolled = app.employeeRepository.matchCache.value
                )
                val minPercent = settings.minConfidencePercent
                when {
                    best != null && best.isConfident(minPercent) -> {
                        clearUnrecognizedHint()
                        handleMatchedFace(best, result.eyeOpenProbability, settings)
                    }
                    // Đang chờ chớp mắt của đúng người này: frame mắt nhắm thường làm
                    // similarity tụt tạm thời. Danh tính đã được xác nhận >= ngưỡng ở frame
                    // trước nên vẫn nạp mẫu mắt để hoàn tất liveness, không báo "chưa nhận dạng".
                    best != null && candidateCode == best.employeeCode -> {
                        handleMatchedFace(best, result.eyeOpenProbability, settings)
                    }
                    else -> {
                        // Quy tắc nghiệp vụ: chưa đạt độ tin cậy tối thiểu -> KHÔNG ghi sự kiện,
                        // hiện rõ "Hệ thống chưa nhận dạng được" để người đứng trước camera biết
                        // cần đứng lại/nhìn thẳng, thay vì im lặng như Phase 1 (mục [4.1]).
                        showUnrecognizedHint(best?.confidencePercent ?: 0)
                    }
                }
            } finally {
                result.croppedBitmap.recycle()
            }
        }
    }

    /** Chạy trên [faceProcessingDispatcher] — KHÔNG gọi trực tiếp từ luồng khác. */
    private suspend fun handleMatchedFace(match: MatchResult, eyeOpenProbability: Float?, settings: AppSettings) {
        val employeeCode = match.employeeCode
        if (candidateCode != employeeCode) {
            candidateCode = employeeCode
            livenessTracker = LivenessTracker()
        }

        val name = app.employeeRepository.nameCache.value[employeeCode] ?: employeeCode

        if (eyeOpenProbability == null) {
            // Thiết bị/tình huống hiếm không có xác suất mắt — bỏ qua kiểm tra liveness,
            // không chặn chấm công vì lý do kỹ thuật ngoài ý muốn.
            clearLivenessHint()
            confirmAttendance(employeeCode, name, match.confidencePercent, settings)
            return
        }

        livenessTracker.onEyeOpenSample(eyeOpenProbability)

        if (livenessTracker.isConfirmed) {
            clearLivenessHint()
            confirmAttendance(employeeCode, name, match.confidencePercent, settings)
            candidateCode = null // lần chấm công tiếp theo (khác lượt) cần chớp mắt lại
        } else {
            showLivenessHint(name)
        }
    }

    private suspend fun confirmAttendance(employeeCode: String, name: String, confidence: Int, settings: AppSettings) {
        // Ghi sự kiện có debounce (mục [3]) — dù bị debounce chặn hay không, vẫn hiện
        // overlay tên để công nhân biết máy đã nhận ra mình.
        val recorded = app.attendanceRepository.recordEventIfNotDebounced(
            employeeCode = employeeCode,
            deviceCode = settings.deviceCode,
            debounceMinutes = settings.debounceMinutes
        )
        showOverlay(name, confidence)
        if (recorded) {
            // Chỉ thêm vào danh sách gần nhất + đọc giọng nói khi THẬT SỰ ghi sự kiện
            // mới — tránh spam nếu 1 người đứng yên trước camera bị debounce chặn nhiều lần.
            addRecentScan(name)
            voiceAnnouncer.speak("Chấm công thành công. Chào $name. Chúc bạn một ngày làm việc vui vẻ.")
        }
    }

    private fun showOverlay(name: String, confidence: Int) {
        _uiState.value = _uiState.value.copy(overlayName = name, overlayConfidence = confidence)
        hideOverlayJob?.cancel()
        hideOverlayJob = viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(overlayName = null, overlayConfidence = null)
        }
    }

    private fun showUnrecognizedHint(confidence: Int) {
        _uiState.value = _uiState.value.copy(unrecognizedConfidence = confidence)
        hideUnrecognizedJob?.cancel()
        hideUnrecognizedJob = viewModelScope.launch {
            // Tự ẩn sau khi người rời đi — mỗi frame chưa đạt ngưỡng sẽ gia hạn lại.
            delay(UNRECOGNIZED_HINT_MS)
            _uiState.value = _uiState.value.copy(unrecognizedConfidence = null)
        }
    }

    private fun clearUnrecognizedHint() {
        hideUnrecognizedJob?.cancel()
        if (_uiState.value.unrecognizedConfidence != null) {
            _uiState.value = _uiState.value.copy(unrecognizedConfidence = null)
        }
    }

    private fun showLivenessHint(name: String) {
        _uiState.value = _uiState.value.copy(livenessHintName = name)
        livenessResetJob?.cancel()
        livenessResetJob = viewModelScope.launch(faceProcessingDispatcher) {
            delay(LIVENESS_TIMEOUT_MS)
            // Hết giờ mà chưa chớp mắt xong (người rời đi hoặc không chớp mắt) -> reset
            // để lần sau bắt đầu lại từ đầu, tránh treo mãi trạng thái chờ.
            candidateCode = null
            livenessTracker = LivenessTracker()
            _uiState.value = _uiState.value.copy(livenessHintName = null)
        }
    }

    private fun clearLivenessHint() {
        livenessResetJob?.cancel()
        _uiState.value = _uiState.value.copy(livenessHintName = null)
    }

    private fun addRecentScan(name: String) {
        val entry = RecentScan(name, LocalTime.now().format(TIME_LABEL_FORMATTER))
        val updated = (listOf(entry) + _uiState.value.recentScans).take(MAX_RECENT_SCANS)
        _uiState.value = _uiState.value.copy(recentScans = updated)
    }

    override fun onCleared() {
        super.onCleared()
        voiceAnnouncer.shutdown()
    }
}

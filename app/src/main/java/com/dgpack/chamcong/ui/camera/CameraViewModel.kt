package com.dgpack.chamcong.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.face.FaceMatcher
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
private val TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

data class RecentScan(val fullName: String, val timeLabel: String)

data class CameraUiState(
    val overlayName: String? = null,
    val livenessHintName: String? = null,
    val pendingCount: Int = 0,
    val recentScans: List<RecentScan> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hideOverlayJob: Job? = null
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
                val match = FaceMatcher.findBestMatch(
                    query = embedding,
                    enrolled = app.employeeRepository.matchCache.value,
                    threshold = settings.similarityThreshold
                )
                if (match != null) {
                    handleMatchedFace(match.employeeCode, result.eyeOpenProbability, settings)
                }
                // Không đạt ngưỡng similarity nào -> không làm gì, không báo lỗi (mục [4.1]).
            } finally {
                result.croppedBitmap.recycle()
            }
        }
    }

    /** Chạy trên [faceProcessingDispatcher] — KHÔNG gọi trực tiếp từ luồng khác. */
    private suspend fun handleMatchedFace(employeeCode: String, eyeOpenProbability: Float?, settings: AppSettings) {
        if (candidateCode != employeeCode) {
            candidateCode = employeeCode
            livenessTracker = LivenessTracker()
        }

        val name = app.employeeRepository.nameCache.value[employeeCode] ?: employeeCode

        if (eyeOpenProbability == null) {
            // Thiết bị/tình huống hiếm không có xác suất mắt — bỏ qua kiểm tra liveness,
            // không chặn chấm công vì lý do kỹ thuật ngoài ý muốn.
            clearLivenessHint()
            confirmAttendance(employeeCode, name, settings)
            return
        }

        livenessTracker.onEyeOpenSample(eyeOpenProbability)

        if (livenessTracker.isConfirmed) {
            clearLivenessHint()
            confirmAttendance(employeeCode, name, settings)
            candidateCode = null // lần chấm công tiếp theo (khác lượt) cần chớp mắt lại
        } else {
            showLivenessHint(name)
        }
    }

    private suspend fun confirmAttendance(employeeCode: String, name: String, settings: AppSettings) {
        // Ghi sự kiện có debounce (mục [3]) — dù bị debounce chặn hay không, vẫn hiện
        // overlay tên để công nhân biết máy đã nhận ra mình.
        val recorded = app.attendanceRepository.recordEventIfNotDebounced(
            employeeCode = employeeCode,
            deviceCode = settings.deviceCode,
            debounceMinutes = settings.debounceMinutes
        )
        showOverlay(name)
        if (recorded) {
            // Chỉ thêm vào danh sách gần nhất + đọc giọng nói khi THẬT SỰ ghi sự kiện
            // mới — tránh spam nếu 1 người đứng yên trước camera bị debounce chặn nhiều lần.
            addRecentScan(name)
            voiceAnnouncer.speak("Chấm công thành công. Chào $name. Chúc bạn một ngày làm việc vui vẻ.")
        }
    }

    private fun showOverlay(name: String) {
        _uiState.value = _uiState.value.copy(overlayName = name)
        hideOverlayJob?.cancel()
        hideOverlayJob = viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(overlayName = null)
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

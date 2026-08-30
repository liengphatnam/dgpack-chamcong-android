package com.dgpack.chamcong.ui.camera

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.face.FaceMatcher
import com.dgpack.chamcong.voice.VoiceAnnouncer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val MAX_RECENT_SCANS = 5
private val TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

data class RecentScan(val fullName: String, val timeLabel: String)

data class CameraUiState(
    val overlayName: String? = null,
    val pendingCount: Int = 0,
    val recentScans: List<RecentScan> = emptyList()
)

class CameraViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hideOverlayJob: Job? = null
    private val voiceAnnouncer = VoiceAnnouncer(app)

    init {
        viewModelScope.launch {
            app.attendanceRepository.observeCounts().collect { counts ->
                _uiState.value = _uiState.value.copy(pendingCount = counts.pending)
            }
        }
    }

    /** Gọi từ FaceAnalyzer (luồng background) mỗi khi có 1 khuôn mặt đã crop sẵn 112x112. */
    fun onFaceDetected(bitmap: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val embedding = app.faceEmbedder.embed(bitmap)
                val settings = app.settingsRepository.current()
                val match = FaceMatcher.findBestMatch(
                    query = embedding,
                    enrolled = app.employeeRepository.matchCache.value,
                    threshold = settings.similarityThreshold
                )
                if (match != null) {
                    // Ghi sự kiện có debounce (mục [3]) — dù bị debounce chặn hay không,
                    // vẫn hiện overlay tên để công nhân biết máy đã nhận ra mình.
                    val recorded = app.attendanceRepository.recordEventIfNotDebounced(
                        employeeCode = match.employeeCode,
                        deviceCode = settings.deviceCode,
                        debounceMinutes = settings.debounceMinutes
                    )
                    val name = app.employeeRepository.nameCache.value[match.employeeCode] ?: match.employeeCode
                    showOverlay(name)
                    if (recorded) {
                        // Chỉ thêm vào danh sách gần nhất + đọc giọng nói khi THẬT SỰ ghi
                        // sự kiện mới — tránh spam nếu 1 người đứng yên trước camera bị
                        // debounce chặn nhiều lần liên tiếp.
                        addRecentScan(name)
                        voiceAnnouncer.speak("Chấm công thành công. Chào $name. Chúc bạn một ngày làm việc vui vẻ.")
                    }
                }
                // Không đạt ngưỡng similarity nào -> không làm gì, không báo lỗi (mục [4.1]).
            } finally {
                bitmap.recycle()
            }
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

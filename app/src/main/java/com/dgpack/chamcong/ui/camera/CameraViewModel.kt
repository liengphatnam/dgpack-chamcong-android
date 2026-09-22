package com.dgpack.chamcong.ui.camera

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
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
/** Mặt nhỏ hơn từng này px trong khung 800x600 = đứng quá xa, ảnh nhoè, không nhận diện. */
private const val MIN_FACE_WIDTH_PX = 60
/** Nhất và nhì phải cách nhau >= 5 điểm similarity, nếu không coi như chưa phân biệt được. */
private const val MIN_MATCH_MARGIN = 0.05f
/**
 * Đang chờ chớp mắt: frame nhắm mắt được phép tụt tối đa từng này % dưới ngưỡng. Thấp hơn
 * nữa là người khác đã bước vào -> không được lấy cái chớp mắt của họ xác nhận cho người trước.
 */
private const val LIVENESS_GRACE_PERCENT = 12
/** Pháo hoa + bảng chúc mừng trúng thưởng hiện bao lâu. */
private const val CELEBRATION_MS = 12_000L
private const val TAG = "CameraViewModel"
private val TIME_LABEL_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

data class RecentScan(val fullName: String, val timeLabel: String)

/** Người vừa trúng thưởng lon nước ngọt — [seed] đổi mỗi lần để pháo hoa bắt đầu lại. */
data class Celebration(val fullName: String, val cans: Int, val isBirthday: Boolean, val seed: Long)

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
    /** Kèm [unrecognizedConfidence]: vượt ngưỡng nhưng giống 2 người quá sát nhau. */
    val ambiguous: Boolean = false,
    /** Có mặt nhưng quá nhỏ (đứng xa) — nhắc đứng gần hơn, không nhận diện. */
    val tooFar: Boolean = false,
    val pendingCount: Int = 0,
    /** "phát hiện 180 ms · nhận diện 60 ms" — để chẩn đoán máy chậm từ xa, hiện cạnh số phiên bản. */
    val perfLabel: String? = null,
    /** Kích thước khung hình phân tích — để vẽ khung tròn đúng vị trí (FaceCircleOverlay). */
    val frameWidth: Int = 600,
    val frameHeight: Int = 800,
    val recentScans: List<RecentScan> = emptyList(),
    /** Khác null = đang bắn pháo hoa chúc mừng người trúng thưởng lon nước ngọt. */
    val celebration: Celebration? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private var hideOverlayJob: Job? = null
    private var hideUnrecognizedJob: Job? = null
    private var hideTooFarJob: Job? = null
    private var hideCelebrationJob: Job? = null
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
                if (result.faceWidthPx < MIN_FACE_WIDTH_PX) {
                    // Mặt quá nhỏ -> ảnh phóng to bị nhoè, embedding không đáng tin (dễ nhận
                    // nhầm). Không so khớp, chỉ nhắc đứng gần hơn.
                    showTooFarHint()
                    return@launch
                }
                clearTooFarHint()

                val embedStart = System.currentTimeMillis()
                val embedding = app.faceEmbedder.embed(result.croppedBitmap)
                val embedMs = System.currentTimeMillis() - embedStart
                _uiState.value = _uiState.value.copy(
                    perfLabel = "phát hiện ${result.detectMs} ms · nhận diện $embedMs ms · mặt ${result.faceWidthPx} px" +
                        " · khung ${result.frame.imageWidth}x${result.frame.imageHeight}",
                    frameWidth = result.frame.imageWidth,
                    frameHeight = result.frame.imageHeight
                )
                val settings = app.settingsRepository.current()
                val best = FaceMatcher.findBestMatch(
                    query = embedding,
                    enrolled = app.employeeRepository.matchCache.value
                )
                val minPercent = settings.minConfidencePercent
                val frameConfident = best != null && best.isConfident(minPercent)
                when {
                    frameConfident && best!!.isAmbiguous(MIN_MATCH_MARGIN) -> {
                        // Vượt ngưỡng nhưng người nhì cũng sát nút -> không dám kết luận.
                        showUnrecognizedHint(best.confidencePercent, ambiguous = true)
                    }
                    frameConfident -> {
                        clearUnrecognizedHint()
                        handleMatchedFace(best!!, result.eyeOpenProbability, settings, frameConfident = true)
                    }
                    // Đang chờ chớp mắt của đúng người này: frame mắt nhắm thường làm
                    // similarity tụt tạm thời. Vẫn nạp mẫu mắt để hoàn tất liveness, nhưng chỉ
                    // trong biên độ LIVENESS_GRACE_PERCENT — tụt sâu hơn nghĩa là người khác đã
                    // đứng vào, không được lấy chớp mắt của họ ghi công cho người trước.
                    best != null && candidateCode == best.employeeCode &&
                        best.confidencePercent >= minPercent - LIVENESS_GRACE_PERCENT -> {
                        handleMatchedFace(best, result.eyeOpenProbability, settings, frameConfident = false)
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

    /**
     * Chạy trên [faceProcessingDispatcher] — KHÔNG gọi trực tiếp từ luồng khác.
     * [frameConfident] = frame hiện tại tự nó đạt ngưỡng (không phải chỉ "đang chờ chớp mắt").
     * Chỉ ghi công ở một frame ĐẠT NGƯỠNG: chớp mắt xong ở frame mờ thì đợi frame rõ mặt kế tiếp.
     */
    private suspend fun handleMatchedFace(
        match: MatchResult,
        eyeOpenProbability: Float?,
        settings: AppSettings,
        frameConfident: Boolean
    ) {
        val employeeCode = match.employeeCode
        if (candidateCode != employeeCode) {
            if (!frameConfident) return // bắt đầu lượt mới phải từ 1 frame đạt ngưỡng
            candidateCode = employeeCode
            livenessTracker = LivenessTracker()
        }

        val name = app.employeeRepository.nameCache.value[employeeCode] ?: employeeCode

        if (eyeOpenProbability == null) {
            // Thiết bị/tình huống hiếm không có xác suất mắt — bỏ qua kiểm tra liveness,
            // không chặn chấm công vì lý do kỹ thuật ngoài ý muốn (nhưng vẫn cần frame đạt ngưỡng).
            if (!frameConfident) return
            clearLivenessHint()
            confirmAttendance(employeeCode, name, match.confidencePercent, settings)
            return
        }

        livenessTracker.onEyeOpenSample(eyeOpenProbability)

        if (livenessTracker.isConfirmed && frameConfident) {
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
            // Quay thưởng lon nước ngọt (tháng 8–9/2026) — chỉ ở lần chấm công đầu trong ngày,
            // xem LuckyDrawEngine. Lỗi ở đây không được làm hỏng luồng chấm công.
            val win = try {
                app.luckyDrawRepository.drawAfterCheckIn(employeeCode, name, settings)
            } catch (e: Exception) {
                Log.w(TAG, "Quay thưởng lỗi, bỏ qua", e)
                null
            }
            if (win != null) {
                showCelebration(name, win)
            } else {
                voiceAnnouncer.speak("Chấm công thành công. Chào $name. Chúc bạn một ngày làm việc vui vẻ.")
            }
        }
    }

    private fun showCelebration(name: String, win: LuckyDrawWinEntity) {
        val isBirthday = win.reason == LuckyDrawReason.BIRTHDAY
        _uiState.value = _uiState.value.copy(
            celebration = Celebration(name, win.cans, isBirthday, System.nanoTime())
        )
        hideCelebrationJob?.cancel()
        hideCelebrationJob = viewModelScope.launch {
            delay(CELEBRATION_MS)
            _uiState.value = _uiState.value.copy(celebration = null)
        }
        val greeting = if (isBirthday) {
            "Chúc mừng sinh nhật $name! Bạn được tặng ${win.cans} lon nước ngọt."
        } else {
            "Chúc mừng $name đã may mắn trúng thưởng ${win.cans} lon nước ngọt!"
        }
        voiceAnnouncer.speak("Chấm công thành công. $greeting Vui lòng liên hệ phòng nhân sự để nhận thưởng.")
    }

    private fun showOverlay(name: String, confidence: Int) {
        _uiState.value = _uiState.value.copy(overlayName = name, overlayConfidence = confidence)
        hideOverlayJob?.cancel()
        hideOverlayJob = viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(overlayName = null, overlayConfidence = null)
        }
    }

    private fun showTooFarHint() {
        if (!_uiState.value.tooFar) _uiState.value = _uiState.value.copy(tooFar = true)
        hideTooFarJob?.cancel()
        hideTooFarJob = viewModelScope.launch {
            delay(UNRECOGNIZED_HINT_MS)
            _uiState.value = _uiState.value.copy(tooFar = false)
        }
    }

    private fun clearTooFarHint() {
        hideTooFarJob?.cancel()
        if (_uiState.value.tooFar) _uiState.value = _uiState.value.copy(tooFar = false)
    }

    private fun showUnrecognizedHint(confidence: Int, ambiguous: Boolean = false) {
        _uiState.value = _uiState.value.copy(unrecognizedConfidence = confidence, ambiguous = ambiguous)
        hideUnrecognizedJob?.cancel()
        hideUnrecognizedJob = viewModelScope.launch {
            // Tự ẩn sau khi người rời đi — mỗi frame chưa đạt ngưỡng sẽ gia hạn lại.
            delay(UNRECOGNIZED_HINT_MS)
            _uiState.value = _uiState.value.copy(unrecognizedConfidence = null, ambiguous = false)
        }
    }

    private fun clearUnrecognizedHint() {
        hideUnrecognizedJob?.cancel()
        if (_uiState.value.unrecognizedConfidence != null) {
            _uiState.value = _uiState.value.copy(unrecognizedConfidence = null, ambiguous = false)
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

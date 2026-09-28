package com.dgpack.chamcong.ui.card

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.camera.FaceDetectionResult
import com.dgpack.chamcong.card.CardHolder
import com.dgpack.chamcong.card.CardRepository
import com.dgpack.chamcong.card.CardScanBus
import com.dgpack.chamcong.card.ForgotCardPolicy
import com.dgpack.chamcong.card.ForgotCardResult
import com.dgpack.chamcong.data.db.AttendanceMethod
import com.dgpack.chamcong.data.db.LuckyDrawReason
import com.dgpack.chamcong.data.db.LuckyDrawWinEntity
import com.dgpack.chamcong.data.prefs.AppSettings
import com.dgpack.chamcong.face.LivenessTracker
import com.dgpack.chamcong.ui.camera.Celebration
import com.dgpack.chamcong.ui.camera.RecentScan
import com.dgpack.chamcong.util.TimeUtils
import com.dgpack.chamcong.voice.VoiceAnnouncer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val TAG = "CardViewModel"
private const val MAX_RECENT_SCANS = 1 // chỉ 1 dòng, để không đè lên nút "Quên mang thẻ"
private const val RESULT_MS = 20_000L
private const val SHORT_NOTICE_MS = 4_000L
private const val FORGOT_DONE_MS = 12_000L
private const val FORGOT_TIMEOUT_MS = 60_000L
private const val CELEBRATION_MS = 12_000L
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")

fun AppSettings.forgotPolicy() = ForgotCardPolicy.Config(forgotPenaltyFirstAt, forgotPenaltyEvery, forgotPenaltyAmount)

enum class CardPhase {
    /** Chờ quét thẻ. */
    IDLE,
    /** Vừa quét xong: tên + nút chi tiết công. */
    RESULT,
    UNKNOWN_CARD,
    /** Đã chấm công trước đó ít phút (debounce). */
    ALREADY_CHECKED,
    FORGOT_CODE,
    FORGOT_CONFIRM,
    FORGOT_CAMERA,
    FORGOT_DONE
}

/** Chi tiết công tháng để hiện sau khi quét (ERP tính tới hết hôm qua; quên thẻ máy tự đếm thêm). */
data class MonthDetail(
    val month: String,
    val workDays: Double,
    val otRegularHours: Double,
    val otSundayHours: Double,
    val otHolidayHours: Double,
    val leaveDays: Double,
    val disciplinaryCount: Int,
    val commendationCount: Int,
    val forgotCardCount: Int,
    val penaltyAmount: Long,
    val hasServerData: Boolean
)

data class CardUiState(
    val phase: CardPhase = CardPhase.IDLE,
    val holder: CardHolder? = null,
    val scanTimeLabel: String? = null,
    val detail: MonthDetail? = null,
    val showDetail: Boolean = false,
    val unknownCardId: String? = null,
    val forgotCodeInput: String = "",
    val forgotNotFound: Boolean = false,
    val blinks: Int = 0,
    val forgotResult: ForgotCardResult? = null,
    val pendingCount: Int = 0,
    val recentScans: List<RecentScan> = emptyList(),
    val celebration: Celebration? = null,
    val frameWidth: Int = 600,
    val frameHeight: Int = 800
)

/**
 * Màn chấm công bằng THẺ TỪ: luôn chờ lượt quét kế tiếp. Quét thẻ -> ghi sự kiện (debounce) ->
 * chào bằng giọng nói -> quay thưởng -> hiện tên + nút "Chi tiết công tháng". Quên thẻ: nhập mã ->
 * xác nhận tên -> chớp mắt 2 lần để chụp bằng chứng -> ghi sự kiện + nhật ký + tính phạt,
 * KHÔNG quay thưởng cho lượt đó.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CardViewModel(private val app: ChamCongApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(CardUiState())
    val uiState: StateFlow<CardUiState> = _uiState.asStateFlow()

    private val voice = VoiceAnnouncer(app)
    private var resetJob: Job? = null
    private var hideCelebrationJob: Job? = null
    private val faceDispatcher = Dispatchers.Default.limitedParallelism(1)
    private var liveness = LivenessTracker(requiredBlinks = 2)
    @Volatile private var capturing = false
    /** Đã chớp mắt đủ, khung hình kế tiếp cần kèm ảnh bằng chứng (FaceAnalyzer hỏi qua [wantEvidence]). */
    @Volatile private var needEvidence = false

    fun wantEvidence(): Boolean = needEvidence

    /**
     * Màn chấm công đang hiển thị hay không. ViewModel vẫn sống khi admin mở màn khác (vd gán thẻ)
     * — lúc đó thẻ quẹt là để gán, KHÔNG được ghi chấm công/quay thưởng.
     */
    @Volatile private var scanActive = true

    fun setScanActive(active: Boolean) {
        scanActive = active
    }

    init {
        viewModelScope.launch {
            app.attendanceRepository.observeCounts().collect { counts ->
                _uiState.update { it.copy(pendingCount = counts.pending) }
            }
        }
        viewModelScope.launch {
            CardScanBus.scans.collect { cardId ->
                if (!scanActive) return@collect
                // 1 lượt quét lỗi (DB...) không được làm chết luồng nhận thẻ cho cả ngày còn lại.
                try {
                    onCardScanned(cardId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Xử lý thẻ $cardId lỗi", e)
                    backToIdle()
                }
            }
        }
    }

    // ===== Quét thẻ =====

    private suspend fun onCardScanned(cardId: String) {
        // Đang trong luồng quên thẻ mà có người quét thẻ -> ưu tiên lượt quét, huỷ luồng dở.
        capturing = false
        val settings = app.settingsRepository.current()
        val holder = app.cardRepository.resolveCard(cardId)
        if (holder == null) {
            _uiState.update { it.copy(phase = CardPhase.UNKNOWN_CARD, unknownCardId = cardId, holder = null) }
            voice.speak("Thẻ này chưa được đăng ký. Vui lòng liên hệ nhân sự.")
            scheduleReset(SHORT_NOTICE_MS)
            return
        }
        val recorded = app.attendanceRepository.recordEventIfNotDebounced(
            employeeCode = holder.employeeCode,
            deviceCode = settings.deviceCode,
            debounceMinutes = settings.debounceMinutes,
            method = AttendanceMethod.CARD,
            cardId = cardId
        )
        val detail = loadDetail(holder.employeeCode, settings)
        _uiState.update {
            it.copy(
                phase = CardPhase.RESULT,
                holder = holder,
                scanTimeLabel = LocalTime.now().format(TIME_FORMATTER),
                detail = detail,
                showDetail = false,
                unknownCardId = null
            )
        }
        scheduleReset(RESULT_MS)

        if (!recorded) {
            voice.speak("Chào ${holder.fullName}. Bạn đã chấm công rồi.")
            return
        }
        addRecentScan(holder.fullName)
        val win = try {
            app.luckyDrawRepository.drawAfterCheckIn(holder.employeeCode, holder.fullName, settings)
        } catch (e: Exception) {
            Log.w(TAG, "Quay thưởng lỗi, bỏ qua", e)
            null
        }
        if (win != null) {
            showCelebration(holder.fullName, win)
        } else {
            voice.speak("Chấm công thành công. Chào ${holder.fullName}. Chúc bạn một ngày làm việc vui vẻ.")
        }
    }

    fun toggleDetail() {
        _uiState.update { it.copy(showDetail = !it.showDetail) }
        scheduleReset(RESULT_MS)
    }

    private suspend fun loadDetail(employeeCode: String, settings: AppSettings): MonthDetail {
        val month = CardRepository.monthKey(TimeUtils.vnToday())
        val s = app.cardRepository.monthSummary(employeeCode, month)
        val forgot = app.cardRepository.forgotCountInMonth(employeeCode, month)
        return MonthDetail(
            month = month,
            workDays = s?.workDays ?: 0.0,
            otRegularHours = s?.otRegularHours ?: 0.0,
            otSundayHours = s?.otSundayHours ?: 0.0,
            otHolidayHours = s?.otHolidayHours ?: 0.0,
            leaveDays = s?.leaveDays ?: 0.0,
            disciplinaryCount = s?.disciplinaryCount ?: 0,
            commendationCount = s?.commendationCount ?: 0,
            forgotCardCount = forgot,
            penaltyAmount = ForgotCardPolicy.totalPenalty(forgot, settings.forgotPolicy()),
            hasServerData = s != null
        )
    }

    // ===== Quên mang thẻ =====

    fun startForgot() {
        capturing = false
        _uiState.update {
            it.copy(phase = CardPhase.FORGOT_CODE, forgotCodeInput = "", forgotNotFound = false, holder = null, showDetail = false)
        }
        scheduleReset(FORGOT_TIMEOUT_MS)
    }

    /** Bàn phím số trong app: chỉ nhận chữ số, tối đa 8 ký tự. */
    fun onForgotDigit(digit: String) {
        _uiState.update {
            it.copy(forgotCodeInput = (it.forgotCodeInput + digit.filter { c -> c.isDigit() }).take(8), forgotNotFound = false)
        }
        scheduleReset(FORGOT_TIMEOUT_MS)
    }

    fun onForgotBackspace() {
        _uiState.update { it.copy(forgotCodeInput = it.forgotCodeInput.dropLast(1), forgotNotFound = false) }
        scheduleReset(FORGOT_TIMEOUT_MS)
    }

    fun lookupForgotCode() {
        val code = _uiState.value.forgotCodeInput.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            // Mã NV kiểu DN0001: người dùng chỉ gõ "0001" trên bàn phím số của app.
            val holder = app.cardRepository.findEmployeeByDigits(code)
            if (holder == null) {
                _uiState.update { it.copy(forgotNotFound = true) }
            } else {
                _uiState.update { it.copy(phase = CardPhase.FORGOT_CONFIRM, holder = holder, forgotNotFound = false) }
            }
            scheduleReset(FORGOT_TIMEOUT_MS)
        }
    }

    fun confirmForgot() {
        liveness = LivenessTracker(requiredBlinks = 2)
        capturing = false
        needEvidence = false
        _uiState.update { it.copy(phase = CardPhase.FORGOT_CAMERA, blinks = 0) }
        scheduleReset(FORGOT_TIMEOUT_MS)
    }

    fun cancelForgot() = backToIdle()

    /** Gọi từ luồng camera (~5 fps) trong bước chụp bằng chứng. */
    fun onForgotFace(result: FaceDetectionResult) {
        viewModelScope.launch(faceDispatcher) {
            try {
                if (_uiState.value.phase != CardPhase.FORGOT_CAMERA || capturing) return@launch
                if (result.imageWidth != _uiState.value.frameWidth || result.imageHeight != _uiState.value.frameHeight) {
                    _uiState.update { it.copy(frameWidth = result.imageWidth, frameHeight = result.imageHeight) }
                }
                // Đã chớp đủ và khung này có ảnh -> chụp. Nếu khung này chưa có ảnh (yêu cầu vừa bật)
                // thì chờ khung kế tiếp, không cần chớp lại.
                if (liveness.isConfirmed) {
                    val bitmap = result.evidenceBitmap ?: run { needEvidence = true; return@launch }
                    capturing = true
                    needEvidence = false
                    finishForgot(toJpeg(bitmap))
                    return@launch
                }
                val eye = result.eyeOpenProbability ?: return@launch
                liveness.onEyeOpenSample(eye)
                if (liveness.blinks != _uiState.value.blinks) {
                    _uiState.update { it.copy(blinks = liveness.blinks) }
                }
                if (liveness.isConfirmed) needEvidence = true
            } finally {
                result.evidenceBitmap?.recycle()
            }
        }
    }

    private suspend fun finishForgot(photoJpeg: ByteArray?) {
        val holder = _uiState.value.holder ?: return backToIdle()
        val settings = app.settingsRepository.current()
        val recorded = app.attendanceRepository.recordEventIfNotDebounced(
            employeeCode = holder.employeeCode,
            deviceCode = settings.deviceCode,
            debounceMinutes = settings.debounceMinutes,
            method = AttendanceMethod.FORGOT_CARD,
            cardId = null
        )
        if (!recorded) {
            // Vừa chấm công cách đây ít phút -> không ghi quên thẻ lần nữa (tránh phạt trùng).
            _uiState.update { it.copy(phase = CardPhase.ALREADY_CHECKED) }
            voice.speak("Chào ${holder.fullName}. Bạn đã chấm công rồi.")
            scheduleReset(SHORT_NOTICE_MS)
            return
        }
        val policy = settings.forgotPolicy()
        val result = app.cardRepository.logForgotCard(holder, photoJpeg, settings.deviceCode, policy)
        addRecentScan(holder.fullName)
        _uiState.update { it.copy(phase = CardPhase.FORGOT_DONE, forgotResult = result, scanTimeLabel = LocalTime.now().format(TIME_FORMATTER)) }
        voice.speak(forgotSpeech(holder.fullName, result, policy))
        scheduleReset(FORGOT_DONE_MS)
    }

    /**
     * Lời nhắc hài hước, đọc đúng luật đang cài ở Cài đặt (số lần bắt đầu phạt, số tiền).
     * Lượt quên thẻ KHÔNG được quay thưởng — nhắc luôn để mọi người nhớ mang thẻ.
     */
    private fun forgotSpeech(name: String, r: ForgotCardResult, policy: ForgotCardPolicy.Config): String {
        val money = formatMoney(policy.amount)
        val rule = if (policy.firstAt > 0) {
            "Quên đủ ${policy.firstAt} lần trong tháng là bị trừ $money tiền thưởng nội quy đó nha."
        } else ""
        return when {
            r.penaltyIncrement > 0 ->
                "Ôi, $name quên thẻ lần thứ ${r.countInMonth} trong tháng rồi. Lần này bị trừ ${formatMoney(r.penaltyIncrement)} " +
                    "tiền thưởng nội quy. Tổng tháng này đã trừ ${formatMoney(r.totalPenalty)}. Mai nhớ mang thẻ nha!"
            r.countInMonth == 1 ->
                "Ôi, $name để quên thẻ ở nhà rồi! Hôm nay không được quay thưởng đâu nha. " +
                    "Lần sau nhớ mang thẻ theo, thẻ nhớ chủ lắm đó. $rule"
            else ->
                "$name lại quên thẻ nữa rồi, lần thứ ${r.countInMonth} trong tháng. Không có thẻ là không được quay thưởng. $rule"
        }
    }

    // ===== Chung =====

    private fun backToIdle() {
        resetJob?.cancel()
        capturing = false
        needEvidence = false
        _uiState.update {
            it.copy(
                phase = CardPhase.IDLE, holder = null, detail = null, showDetail = false,
                unknownCardId = null, forgotCodeInput = "", forgotNotFound = false, blinks = 0, forgotResult = null
            )
        }
    }

    private fun scheduleReset(delayMs: Long) {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(delayMs)
            backToIdle()
        }
    }

    private fun showCelebration(name: String, win: LuckyDrawWinEntity) {
        val isBirthday = win.reason == LuckyDrawReason.BIRTHDAY
        _uiState.update { it.copy(celebration = Celebration(name, win.cans, isBirthday, System.nanoTime())) }
        hideCelebrationJob?.cancel()
        hideCelebrationJob = viewModelScope.launch {
            delay(CELEBRATION_MS)
            _uiState.update { it.copy(celebration = null) }
        }
        val greeting = if (isBirthday) {
            "Chúc mừng sinh nhật $name! Bạn được tặng ${win.cans} lon nước ngọt."
        } else {
            "Chúc mừng $name đã may mắn trúng thưởng ${win.cans} lon nước ngọt!"
        }
        voice.speak("Chấm công thành công. $greeting Vui lòng liên hệ phòng nhân sự để nhận thưởng.")
    }

    private fun addRecentScan(name: String) {
        val entry = RecentScan(name, LocalTime.now().format(TIME_FORMATTER))
        _uiState.update { it.copy(recentScans = (listOf(entry) + it.recentScans).take(MAX_RECENT_SCANS)) }
    }

    private fun toJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        return out.toByteArray()
    }

    override fun onCleared() {
        super.onCleared()
        voice.shutdown()
    }

    companion object {
        /** 50000 -> "50.000 đồng". */
        fun formatMoney(amount: Long): String = "%,d".format(amount).replace(',', '.') + " đồng"
    }
}

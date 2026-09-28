package com.dgpack.chamcong.voice

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Không thử kết nối lại bộ đọc quá dày khi máy thiếu giọng tiếng Việt / bộ đọc lỗi hẳn. */
private const val RETRY_INTERVAL_MS = 5 * 60_000L
private const val INIT_TIMEOUT_MS = 30_000L

/**
 * Đọc thông báo "chấm công thành công" bằng giọng nói (Android TextToSpeech có sẵn,
 * không cần quyền/mạng, hoạt động offline nếu gói giọng đọc tiếng Việt đã cài trên máy).
 * Nếu máy không có gói giọng tiếng Việt, [speak] âm thầm bỏ qua — không crash, không báo lỗi.
 *
 * Kiosk chạy nhiều ngày: bộ đọc (vd Google TTS) có thể bị hệ thống tắt hoặc tự cập nhật qua đêm,
 * khi đó kết nối cũ chết hẳn và speak() trả ERROR mãi mãi -> máy "im lặng" dù vẫn chấm công.
 * [speak] phát hiện lỗi đó, tạo lại kết nối và đọc câu đang dở khi bộ đọc sẵn sàng.
 * Mọi hàm gọi trên main thread (viewModelScope), callback khởi tạo của TTS cũng về main thread.
 */
class VoiceAnnouncer(context: Context) {

    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null
    private var ready = false
    private var initializing = false
    private var generation = 0
    private var lastInitAt = 0L
    /** Câu chờ đọc khi bộ đọc đang (khởi tạo) lại. Chỉ giữ câu mới nhất. */
    private var pending: String? = null

    init {
        connect()
    }

    private fun connect() {
        tts?.let { runCatching { it.shutdown() } }
        tts = null
        ready = false
        initializing = true
        lastInitAt = SystemClock.elapsedRealtime()
        val gen = ++generation
        val created = TextToSpeech(appContext) { status -> onInit(gen, status) }
        // Nếu khởi tạo lỗi ngay trong constructor thì onInit đã chạy và bỏ qua instance này.
        if (gen == generation) tts = created else runCatching { created.shutdown() }
    }

    private fun onInit(gen: Int, status: Int) {
        if (gen != generation) return
        initializing = false
        val engine = tts
        ready = if (status == TextToSpeech.SUCCESS && engine != null) {
            val result = engine.setLanguage(Locale("vi", "VN"))
            result == TextToSpeech.LANG_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        } else {
            false
        }
        val text = pending
        pending = null
        if (ready && text != null) say(engine!!, text)
    }

    fun speak(text: String) {
        if (initializing) {
            pending = text
            // Bộ đọc không bao giờ trả lời khởi tạo (service treo) -> tạo kết nối mới.
            if (SystemClock.elapsedRealtime() - lastInitAt >= INIT_TIMEOUT_MS) connect()
            return
        }
        val engine = tts
        if (!ready || engine == null) {
            // Lần khởi tạo trước thất bại (thiếu giọng, bộ đọc đang cập nhật...) -> thỉnh thoảng thử lại.
            if (SystemClock.elapsedRealtime() - lastInitAt >= RETRY_INTERVAL_MS) {
                pending = text
                connect()
            }
            return
        }
        if (!say(engine, text)) {
            // Kết nối tới bộ đọc đã chết -> kết nối lại rồi đọc câu này.
            pending = text
            connect()
        }
    }

    private fun say(engine: TextToSpeech, text: String): Boolean =
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_ADD, null, "chamcong_${System.currentTimeMillis()}")
        }.getOrDefault(TextToSpeech.ERROR) == TextToSpeech.SUCCESS

    fun shutdown() {
        generation++
        pending = null
        ready = false
        initializing = false
        tts?.let {
            runCatching { it.stop() }
            runCatching { it.shutdown() }
        }
        tts = null
    }
}

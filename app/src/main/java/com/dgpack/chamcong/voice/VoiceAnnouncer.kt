package com.dgpack.chamcong.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Đọc thông báo "chấm công thành công" bằng giọng nói (Android TextToSpeech có sẵn,
 * không cần quyền/mạng, hoạt động offline nếu gói giọng đọc tiếng Việt đã cài trên máy).
 * Nếu máy không có gói giọng tiếng Việt, [speak] âm thầm bỏ qua — không crash, không báo lỗi.
 */
class VoiceAnnouncer(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale("vi", "VN"))
                ready = result == TextToSpeech.LANG_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_AVAILABLE ||
                    result == TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "chamcong_${System.currentTimeMillis()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}

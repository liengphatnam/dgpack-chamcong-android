package com.dgpack.chamcong.card

import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Kênh phát mã thẻ vừa quét, không phụ thuộc loại đầu đọc:
 *  - NFC tích hợp của tablet (MainActivity.enableReaderMode) -> UID thẻ dạng hex,
 *  - đầu đọc thẻ từ USB/Bluetooth kiểu bàn phím (HID) -> gõ mã thẻ + Enter ([HidCardKeyAccumulator]).
 * Màn chấm công thẻ / màn gán thẻ chỉ cần collect [scans].
 */
object CardScanBus {
    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val scans: SharedFlow<String> = _scans.asSharedFlow()

    fun emit(rawCardId: String) {
        val id = normalize(rawCardId)
        if (id.isNotEmpty()) _scans.tryEmit(id)
    }

    /** Chuẩn hoá để so khớp: chữ hoa, bỏ mọi ký tự không phải 0-9 A-Z (khoảng trắng, dấu :, -). */
    fun normalize(raw: String): String = raw.trim().uppercase().replace(Regex("[^0-9A-Z]"), "")

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }
}

/**
 * Đánh thức đầu đọc NFC tích hợp: trên tablet giá rẻ, để kiosk chạy lâu thì reader mode hay "ngủ"
 * (quẹt thẻ không ăn) dù app vẫn ở foreground. MainActivity đăng ký hàm tắt/bật lại reader mode;
 * màn chấm công gọi [wake] khi người dùng bấm vào hình chiếc thẻ.
 */
object NfcReaderControl {
    /** Trả về true nếu đã bật lại reader mode; false nếu máy không có NFC hoặc NFC đang tắt. */
    @Volatile var restarter: (() -> Boolean)? = null

    fun wake(): Boolean = restarter?.invoke() ?: false
}

/**
 * Gom phím từ đầu đọc thẻ kiểu bàn phím: đầu đọc "gõ" mã thẻ rất nhanh (< 250 ms giữa 2 phím)
 * rồi kết thúc bằng Enter. Người gõ tay chậm hơn nên chuỗi bị reset, không nhận nhầm.
 * Chỉ nuốt phím Enter kết thúc mã thẻ; các ký tự vẫn đi tiếp tới ô đang focus (nếu có).
 */
class HidCardKeyAccumulator(
    private val onCard: (String) -> Unit,
    private val maxGapMs: Long = 250,
    private val minLength: Int = 4
) {
    private val buffer = StringBuilder()
    private var lastAt = 0L
    private var swallowNextEnterUp = false

    /** @return true nếu phím đã được tiêu thụ (không chuyển tiếp cho UI). */
    fun onKey(event: KeyEvent): Boolean {
        val isEnter = event.keyCode == KeyEvent.KEYCODE_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            event.keyCode == KeyEvent.KEYCODE_TAB
        if (event.action == KeyEvent.ACTION_UP) {
            if (isEnter && swallowNextEnterUp) {
                swallowNextEnterUp = false
                return true
            }
            return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val now = event.eventTime
        if (now - lastAt > maxGapMs) buffer.setLength(0)
        lastAt = now

        if (isEnter) {
            val text = buffer.toString()
            buffer.setLength(0)
            if (text.length >= minLength) {
                onCard(text)
                swallowNextEnterUp = true
                return true
            }
            return false
        }
        val ch = event.unicodeChar
        if (ch != 0 && !Character.isISOControl(ch)) buffer.append(ch.toChar())
        return false
    }
}

package com.dgpack.chamcong

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.card.CardScanBus
import com.dgpack.chamcong.card.HidCardKeyAccumulator
import com.dgpack.chamcong.card.NfcReaderControl
import com.dgpack.chamcong.ui.nav.AppNavHost
import com.dgpack.chamcong.ui.theme.ChamCongTheme

private const val TAG = "MainActivity"

/** Chu kỳ tự tắt/bật lại reader mode khi app ở foreground (kiosk chạy nhiều ngày không tắt). */
private const val NFC_WATCHDOG_MS = 10 * 60_000L

/** Không restart nếu vừa đọc được thẻ trong khoảng này — tránh cắt ngang lượt quẹt đang diễn ra. */
private const val NFC_RECENT_TAG_MS = 5_000L

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var resumed = false
    @Volatile private var lastTagAt = 0L
    private val handler = Handler(Looper.getMainLooper())

    /** Đầu đọc thẻ từ USB/Bluetooth kiểu bàn phím: gõ mã thẻ + Enter -> CardScanBus. */
    private val hidReader = HidCardKeyAccumulator(onCard = { CardScanBus.emit(it) })

    /**
     * Dịch vụ NFC của hệ thống (com.android.nfc) có thể tự khởi động lại, hoặc NFC bị tắt/bật
     * trong cài đặt, trong khi app vẫn ở foreground: đăng ký reader mode cũ mất theo và
     * onResume KHÔNG được gọi lại -> quẹt thẻ không ăn tới khi mở lại app. Bật lại khi NFC lên.
     */
    private val nfcStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF)
            if (state == NfcAdapter.STATE_ON && resumed) {
                Log.i(TAG, "NFC vừa bật lại -> đăng ký lại reader mode")
                restartNfcReader()
            }
        }
    }

    /** Định kỳ "đánh thức" reader mode: trên nhiều máy để lâu reader mode im lặng dù app vẫn mở. */
    private val nfcWatchdog = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (SystemClock.elapsedRealtime() - lastTagAt > NFC_RECENT_TAG_MS) restartNfcReader()
            handler.postDelayed(this, NFC_WATCHDOG_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Kiosk: giữ màn hình luôn sáng. Màn hình tắt/khoá thì Android ngừng dò thẻ NFC hoàn toàn.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) // null nếu máy không có NFC — vẫn dùng đầu đọc HID
        if (nfcAdapter != null) {
            ContextCompat.registerReceiver(
                this,
                nfcStateReceiver,
                IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        NfcReaderControl.restarter = ::restartNfcReader
        setContent {
            ChamCongApp()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        nfcAdapter?.let(::enableNfcReader)
        handler.removeCallbacks(nfcWatchdog)
        handler.postDelayed(nfcWatchdog, NFC_WATCHDOG_MS)
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(nfcWatchdog)
        nfcAdapter?.let { runCatching { it.disableReaderMode(this) } }
        super.onPause()
    }

    override fun onDestroy() {
        NfcReaderControl.restarter = null
        if (nfcAdapter != null) runCatching { unregisterReceiver(nfcStateReceiver) }
        super.onDestroy()
    }

    /** Reader mode: app nhận UID thẻ NFC trực tiếp, không bật app khác, không tiếng "ting" hệ thống. */
    private fun enableNfcReader(adapter: NfcAdapter) {
        try {
            adapter.enableReaderMode(
                this,
                { tag ->
                    lastTagAt = SystemClock.elapsedRealtime()
                    CardScanBus.emit(CardScanBus.bytesToHex(tag.id))
                },
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
        } catch (e: Exception) {
            // Dịch vụ NFC đang khởi động lại -> bỏ qua, receiver/watchdog sẽ bật lại sau.
            Log.w(TAG, "enableReaderMode lỗi", e)
        }
    }

    /**
     * Tắt rồi bật lại reader mode để "đánh thức" NFC khi kiosk chạy lâu, quẹt thẻ không ăn.
     * @return false nếu máy không có NFC hoặc NFC đang bị tắt trong cài đặt hệ thống.
     */
    private fun restartNfcReader(): Boolean {
        val adapter = nfcAdapter ?: return false
        if (!resumed || !adapter.isEnabled) return false
        runCatching { adapter.disableReaderMode(this) }
        enableNfcReader(adapter)
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hidReader.onKey(event)) return true
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun ChamCongApp() {
    ChamCongTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppNavHost()
        }
    }
}

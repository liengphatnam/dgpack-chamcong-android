package com.dgpack.chamcong

import android.nfc.NfcAdapter
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dgpack.chamcong.card.CardScanBus
import com.dgpack.chamcong.card.HidCardKeyAccumulator
import com.dgpack.chamcong.ui.nav.AppNavHost
import com.dgpack.chamcong.ui.theme.ChamCongTheme

class MainActivity : ComponentActivity() {

    private var nfcAdapter: NfcAdapter? = null

    /** Đầu đọc thẻ từ USB/Bluetooth kiểu bàn phím: gõ mã thẻ + Enter -> CardScanBus. */
    private val hidReader = HidCardKeyAccumulator(onCard = { CardScanBus.emit(it) })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) // null nếu máy không có NFC — vẫn dùng đầu đọc HID
        setContent {
            ChamCongApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Reader mode: app nhận UID thẻ NFC trực tiếp, không bật app khác, không tiếng "ting" hệ thống.
        nfcAdapter?.enableReaderMode(
            this,
            { tag -> CardScanBus.emit(CardScanBus.bytesToHex(tag.id)) },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
    }

    override fun onPause() {
        nfcAdapter?.disableReaderMode(this)
        super.onPause()
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

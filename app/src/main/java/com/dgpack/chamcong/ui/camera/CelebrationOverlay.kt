package com.dgpack.chamcong.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R

/** 1 dòng trong danh sách "vừa chấm công" ở góc màn hình. */
data class RecentScan(val fullName: String, val timeLabel: String)

/** Người vừa trúng thưởng lon nước ngọt — [seed] đổi mỗi lần để pháo hoa bắt đầu lại. */
data class Celebration(val fullName: String, val cans: Int, val isBirthday: Boolean, val seed: Long)

/**
 * Trúng thưởng lon nước ngọt (tháng 8–9/2026): pháo hoa toàn màn hình + bảng chúc mừng giữa
 * màn, hướng dẫn liên hệ nhân sự.
 */
@Composable
fun BoxScope.CelebrationOverlay(celebration: Celebration) {
    FireworksOverlay(seed = celebration.seed, modifier = Modifier.fillMaxSize())
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.align(Alignment.Center).padding(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(
                    if (celebration.isBirthday) R.string.ld_chuc_mung_sinh_nhat else R.string.ld_chuc_mung
                ),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = celebration.fullName,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.ld_trung_format, celebration.cans),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.ld_lien_he_nhan_su),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

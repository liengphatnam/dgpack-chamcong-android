package com.dgpack.chamcong.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bàn phím số trong app (0–9, xoá, OK) — dùng ở mọi chỗ công nhân/admin nhập số (mã NV khi
 * quên thẻ, tìm NV để gán thẻ) để KHÔNG bật bàn phím điện thoại. Chỉ các ô cấu hình
 * (API key, URL…) ở Cài đặt mới dùng bàn phím hệ thống / dán.
 *
 * @param compact 2 hàng (1–5 / 6–0 ⌫) cho chỗ hẹp; mặc định 3x4 kiểu máy tính.
 * @param onOk    null = không có phím OK.
 */
@Composable
fun NumericKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onOk: (() -> Unit)? = null,
    compact: Boolean = false,
    keySize: Dp = if (compact) 52.dp else 64.dp
) {
    val rows = if (compact) {
        listOf(listOf("1", "2", "3", "4", "5"), listOf("6", "7", "8", "9", "0", "⌫") + listOfNotNull(onOk?.let { "OK" }))
    } else {
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", if (onOk != null) "OK" else ""))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Box(modifier = Modifier.size(keySize))
                        "⌫" -> Key(label = key, size = keySize, onClick = onBackspace)
                        "OK" -> Key(label = key, size = keySize, primary = true, onClick = { onOk?.invoke() })
                        else -> Key(label = key, size = keySize, onClick = { onDigit(key) })
                    }
                }
            }
            Box(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Key(label: String, size: Dp, primary: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

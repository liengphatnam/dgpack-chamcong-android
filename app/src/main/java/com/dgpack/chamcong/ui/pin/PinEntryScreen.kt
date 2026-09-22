package com.dgpack.chamcong.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R

private const val MAX_PIN_LENGTH = 4

/**
 * Màn nhập mật mã quản trị. Mã có thể 1–4 số (mã cấp 2 theo ngày có thể ngắn, xem
 * [AdminPinPolicy]): đủ 4 số thì tự gửi, ngắn hơn thì bấm "Xác nhận".
 *
 * @param validate trả về cấp quyền tương ứng với mã nhập, null = sai.
 */
@Composable
fun PinEntryScreen(
    title: String,
    validate: (String) -> AdminLevel?,
    onSuccess: (AdminLevel) -> Unit,
    onCancel: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun submit() {
        if (input.isEmpty()) return
        val level = validate(input)
        if (level != null) {
            onSuccess(level)
        } else {
            error = true
            input = ""
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(MAX_PIN_LENGTH) { index ->
                    val filled = index < input.length
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )
                }
            }

            if (error) {
                Text(
                    text = stringResource(R.string.ma_pin_sai),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            PinKeypad(
                onDigit = { digit ->
                    if (input.length < MAX_PIN_LENGTH) {
                        error = false
                        input += digit
                        if (input.length == MAX_PIN_LENGTH) submit()
                    }
                },
                onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) },
                onOk = { submit() }
            )

            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.quay_lai))
            }
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit, onOk: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("⌫", "0", "OK")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when (key) {
                        "⌫" -> KeypadButton(label = key, onClick = onBackspace)
                        "OK" -> KeypadButton(label = key, onClick = onOk, primary = true)
                        else -> KeypadButton(label = key, onClick = { onDigit(key) })
                    }
                }
            }
            Box(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit, primary: Boolean = false) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = if (primary) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

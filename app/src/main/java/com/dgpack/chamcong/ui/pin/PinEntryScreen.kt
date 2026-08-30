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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.R

private const val PIN_LENGTH = 4

/**
 * Phase 2: khoá màn hình quản trị bằng PIN 4 số (mục [2]/[12]) — tránh công nhân bấm
 * nhầm vào Enroll/Cài đặt/Hàng đợi. [correctPin] rỗng thì màn hình này không nên được
 * hiển thị (xem AppNavHost — bỏ qua thẳng nếu chưa đặt PIN).
 */
@Composable
fun PinEntryScreen(correctPin: String, onSuccess: () -> Unit, onCancel: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(input) {
        if (input.length == PIN_LENGTH) {
            if (input == correctPin) {
                onSuccess()
            } else {
                error = true
                input = ""
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.nhap_ma_pin), style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                repeat(PIN_LENGTH) { index ->
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
                onDigit = { digit -> if (input.length < PIN_LENGTH) { error = false; input += digit } },
                onBackspace = { if (input.isNotEmpty()) input = input.dropLast(1) }
            )

            TextButton(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.quay_lai))
            }
        }
    }
}

@Composable
private fun PinKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { key ->
                    when (key) {
                        "" -> Box(modifier = Modifier.size(64.dp))
                        "⌫" -> KeypadButton(label = key, onClick = onBackspace)
                        else -> KeypadButton(label = key, onClick = { onDigit(key) })
                    }
                }
            }
            Box(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, style = MaterialTheme.typography.headlineMedium)
    }
}

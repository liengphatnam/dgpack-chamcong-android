package com.dgpack.chamcong.ui.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dgpack.chamcong.ChamCongApplication
import com.dgpack.chamcong.R
import com.dgpack.chamcong.card.CardScanBus
import kotlinx.coroutines.delay

/**
 * Gán thẻ từ cho 1 NV (chọn từ danh sách Nhân viên ERP): chờ quét thẻ -> lưu offline ngay ->
 * báo "Đã gán" -> tự quay về danh sách để chọn người kế tiếp. Thẻ được đẩy lên ERP ở lần đồng
 * bộ tới (EmployeeSyncEngine bước 5).
 */
@Composable
fun AssignCardScreen(employeeCode: String, fullName: String, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as ChamCongApplication
    var currentCard by remember { mutableStateOf<String?>(null) }
    var savedCard by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(employeeCode) {
        currentCard = app.cardRepository.cardOf(employeeCode)
    }
    LaunchedEffect(employeeCode) {
        CardScanBus.scans.collect { cardId ->
            if (savedCard != null) return@collect
            app.cardRepository.assignCard(cardId, employeeCode)
            savedCard = cardId
            app.employeeSyncCoordinator.requestSync()
            delay(1200)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Text(stringResource(R.string.tieu_de_gan_the), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val saved = savedCard
            Surface(color = if (saved != null) Color(0xFF2ECC71) else MaterialTheme.colorScheme.primary, shape = CircleShape) {
                Icon(
                    if (saved != null) Icons.Filled.CheckCircle else Icons.Filled.CreditCard,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(32.dp).size(80.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(employeeCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            if (saved != null) {
                Text(
                    stringResource(R.string.gan_the_xong_format, saved),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF2ECC71),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    stringResource(R.string.gan_the_quet),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = currentCard?.let { stringResource(R.string.gan_the_hien_tai_format, it) }
                        ?: stringResource(R.string.gan_the_chua_co),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.dgpack.chamcong.ui.card

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.WorkHistory
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.BuildConfig
import com.dgpack.chamcong.R
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.ui.camera.CelebrationOverlay
import com.dgpack.chamcong.ui.camera.FaceCameraPreview
import com.dgpack.chamcong.ui.camera.FaceCircleOverlay
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CLOCK_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", java.util.Locale("vi", "VN"))
private val GREEN = Color(0xFF2ECC71)
private val AMBER = Color(0xFFF1C40F)
private val RED = Color(0xFFE74C3C)

/**
 * Màn chấm công bằng thẻ từ (kiosk): luôn chờ lượt quét kế tiếp. Camera chỉ bật ở bước
 * "quên thẻ" để chụp bằng chứng. Mọi phần hiển thị nằm trong vùng chừa 10% trên/dưới.
 */
@Composable
fun CardScreen(onOpenAdmin: () -> Unit) {
    val viewModel = appViewModel { CardViewModel(it) }
    val state by viewModel.uiState.collectAsState()

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(1000)
        }
    }

    val gradient = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(gradient)) {
        val edgeInset = maxHeight * 0.10f

        if (state.phase == CardPhase.FORGOT_CAMERA) {
            ForgotCameraLayer(
                frameWidth = state.frameWidth,
                frameHeight = state.frameHeight,
                onFace = viewModel::onForgotFace
            )
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = edgeInset, bottom = edgeInset)) {
            when (state.phase) {
                CardPhase.IDLE -> IdleContent(now = now, onForgot = viewModel::startForgot)
                CardPhase.RESULT -> ResultContent(state, onToggleDetail = viewModel::toggleDetail)
                CardPhase.UNKNOWN_CARD -> NoticeCard(
                    icon = Icons.Filled.ErrorOutline, color = RED,
                    title = stringResource(R.string.card_the_la),
                    subtitle = state.unknownCardId
                )
                CardPhase.ALREADY_CHECKED -> NoticeCard(
                    icon = Icons.Filled.Info, color = AMBER,
                    title = stringResource(R.string.card_da_cham_roi),
                    subtitle = state.holder?.fullName
                )
                CardPhase.FORGOT_CODE -> ForgotCodeContent(state, viewModel)
                CardPhase.FORGOT_CONFIRM -> ForgotConfirmContent(state, viewModel)
                CardPhase.FORGOT_CAMERA -> ForgotCameraHint(state, onCancel = viewModel::cancelForgot)
                CardPhase.FORGOT_DONE -> ForgotDoneContent(state)
            }

            state.celebration?.let { CelebrationOverlay(it) }

            // Góc trên trái: đang chờ đồng bộ + 5 người vừa chấm
            Column(
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.pendingCount > 0) {
                    Chip(text = stringResource(R.string.cho_dong_bo_format, state.pendingCount))
                }
                if (state.recentScans.isNotEmpty() && state.phase == CardPhase.IDLE) {
                    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(stringResource(R.string.vua_cham_cong), style = MaterialTheme.typography.labelLarge)
                            state.recentScans.forEach { scan ->
                                Text("${scan.timeLabel}  ${scan.fullName}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = CircleShape,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                IconButton(onClick = onOpenAdmin) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nut_cai_dat_enroll_hang_doi))
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            ) {
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

// ---------------- IDLE ----------------

@Composable
private fun IdleContent(now: LocalTime, onForgot: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = now.format(CLOCK_FORMATTER),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = LocalDate.now().format(DATE_FORMATTER).replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
        Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape) {
            Icon(
                Icons.Filled.CreditCard, contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(36.dp).size(96.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.card_quet_the),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = onForgot) {
            Icon(Icons.Filled.Badge, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.card_nut_quen_the), style = MaterialTheme.typography.titleMedium)
        }
    }
}

// ---------------- RESULT ----------------

@Composable
private fun ResultContent(state: CardUiState, onToggleDetail: () -> Unit) {
    val holder = state.holder ?: return
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = GREEN, modifier = Modifier.size(72.dp))
                Text(holder.fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("${holder.employeeCode}  ·  ${state.scanTimeLabel ?: ""}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onToggleDetail) {
                    Icon(Icons.Filled.WorkHistory, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.showDetail) stringResource(R.string.card_nut_dong) else stringResource(R.string.card_nut_chi_tiet))
                }
                if (state.showDetail) state.detail?.let { DetailTable(it) }
            }
        }
    }
}

@Composable
private fun DetailTable(d: MonthDetail) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.card_chi_tiet_tieu_de_format, monthLabel(d.month)),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (!d.hasServerData) {
            Text(stringResource(R.string.card_ct_chua_co_du_lieu), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        DetailRow(Icons.Filled.WorkHistory, stringResource(R.string.card_ct_ngay_cong), num(d.workDays))
        DetailRow(Icons.Filled.Schedule, stringResource(R.string.card_ct_tc_thuong), num(d.otRegularHours))
        DetailRow(Icons.Filled.Weekend, stringResource(R.string.card_ct_tc_cn), num(d.otSundayHours))
        DetailRow(Icons.Filled.WbSunny, stringResource(R.string.card_ct_tc_le), num(d.otHolidayHours))
        DetailRow(Icons.Filled.EventBusy, stringResource(R.string.card_ct_nghi_phep), num(d.leaveDays))
        DetailRow(Icons.Filled.Badge, stringResource(R.string.card_ct_quen_the), d.forgotCardCount.toString(), if (d.forgotCardCount > 0) AMBER else null)
        DetailRow(Icons.Filled.MoneyOff, stringResource(R.string.card_ct_tien_tru), moneyLabel(d.penaltyAmount), if (d.penaltyAmount > 0) RED else null)
        DetailRow(Icons.Filled.Gavel, stringResource(R.string.card_ct_bien_ban), d.disciplinaryCount.toString(), if (d.disciplinaryCount > 0) RED else null)
        DetailRow(Icons.Filled.Star, stringResource(R.string.card_ct_khen_thuong), d.commendationCount.toString(), if (d.commendationCount > 0) GREEN else null)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, valueColor: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------- Thông báo ngắn ----------------

@Composable
private fun NoticeCard(icon: ImageVector, color: Color, title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(72.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

// ---------------- Quên thẻ ----------------

@Composable
private fun ForgotCodeContent(state: CardUiState, viewModel: CardViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.card_nut_quen_the), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.forgotCodeInput,
                    onValueChange = viewModel::onForgotCodeChange,
                    label = { Text(stringResource(R.string.forgot_nhap_ma)) },
                    singleLine = true,
                    isError = state.forgotNotFound,
                    supportingText = { if (state.forgotNotFound) Text(stringResource(R.string.forgot_khong_tim_thay)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.lookupForgotCode() }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = viewModel::cancelForgot) { Text(stringResource(R.string.nut_huy)) }
                    Button(onClick = viewModel::lookupForgotCode, enabled = state.forgotCodeInput.isNotBlank()) {
                        Text(stringResource(R.string.forgot_nut_tim))
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgotConfirmContent(state: CardUiState, viewModel: CardViewModel) {
    val holder = state.holder ?: return
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Text(holder.fullName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(holder.employeeCode, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.forgot_xac_nhan), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = viewModel::startForgot) { Text(stringResource(R.string.forgot_nut_sai)) }
                    Button(onClick = viewModel::confirmForgot) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.forgot_nut_dung))
                    }
                }
            }
        }
    }
}

@Composable
private fun ForgotCameraLayer(frameWidth: Int, frameHeight: Int, onFace: (com.dgpack.chamcong.camera.FaceDetectionResult) -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) launcher.launch(Manifest.permission.CAMERA) }
    if (hasPermission) {
        FaceCameraPreview(targetFps = 5, onFaceDetected = onFace, wantEvidence = true)
        FaceCircleOverlay(
            frameWidth = frameWidth, frameHeight = frameHeight,
            ringColor = Color.White.copy(alpha = 0.9f), sweepFraction = 1f, scrimAlpha = 0.45f,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun ForgotCameraHint(state: CardUiState, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Visibility, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.forgot_chop_mat_2_lan), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                        repeat(2) { i ->
                            Box(
                                modifier = Modifier.size(18.dp).background(
                                    if (i < state.blinks) GREEN else MaterialTheme.colorScheme.surfaceVariant, CircleShape
                                )
                            )
                        }
                    }
                    Text(state.holder?.fullName ?: "", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.nut_huy)) }
        }
    }
}

@Composable
private fun ForgotDoneContent(state: CardUiState) {
    val r = state.forgotResult ?: return
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (r.penaltyIncrement > 0) AMBER else GREEN, modifier = Modifier.size(72.dp))
                Text(state.holder?.fullName ?: "", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(stringResource(R.string.forgot_da_ghi_format, r.countInMonth), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(stringResource(R.string.forgot_khong_quay_thuong), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (r.penaltyIncrement > 0) {
                    Text(stringResource(R.string.forgot_tru_them_format, moneyLabel(r.penaltyIncrement)), style = MaterialTheme.typography.titleMedium, color = RED, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = if (r.totalPenalty > 0) stringResource(R.string.forgot_tong_tru_format, moneyLabel(r.totalPenalty))
                    else stringResource(R.string.forgot_khong_tru),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

// ---------------- Helpers ----------------

@Composable
private fun Chip(text: String) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), shape = RoundedCornerShape(8.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

private fun num(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(v)

private fun moneyLabel(amount: Long): String = "%,d".format(amount).replace(',', '.') + " đ"

/** "2026-09" -> "09/2026". */
private fun monthLabel(month: String): String {
    val p = month.split("-")
    return if (p.size == 2) "${p[1]}/${p[0]}" else month
}

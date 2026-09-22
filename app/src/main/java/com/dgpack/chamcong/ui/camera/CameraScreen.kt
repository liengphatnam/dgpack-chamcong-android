package com.dgpack.chamcong.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.BuildConfig
import com.dgpack.chamcong.R
import com.dgpack.chamcong.ui.appViewModel

/**
 * Màn hình chính (mục [7]): camera preview full-screen + overlay tên khi nhận diện
 * thành công, hoàn toàn tự động, không cần công nhân bấm gì. Nút quản trị nhỏ ở góc
 * để vào Enroll/Cài đặt/Hàng đợi.
 */
@Composable
fun CameraScreen(onOpenAdmin: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { CameraViewModel(it) }
    val uiState by viewModel.uiState.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            FaceCameraPreview(targetFps = 3, onFaceDetected = viewModel::onFaceDetected)
        } else {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Ứng dụng cần quyền Camera để nhận diện khuôn mặt chấm công.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Trúng thưởng lon nước ngọt (tháng 8–9/2026): pháo hoa toàn màn hình + bảng chúc mừng
        // giữa màn, hướng dẫn liên hệ nhân sự. Đặt TRƯỚC các nút để nút quản trị vẫn bấm được.
        uiState.celebration?.let { celebration ->
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

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Phase 2 (mục [12]): gợi ý chớp mắt để xác nhận chống giả mạo — hiện trong
            // lúc chờ, trước khi ghi nhận chấm công thành công.
            AnimatedVisibility(
                visible = uiState.livenessHintName != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.goi_y_chop_mat_format, uiState.livenessHintName ?: ""),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                }
            }

            // Mặt quá nhỏ trong khung hình (đứng xa) — không nhận diện, nhắc đứng gần hơn.
            AnimatedVisibility(
                visible = uiState.tooFar && uiState.overlayName == null && uiState.livenessHintName == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dung_gan_hon),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                }
            }

            // Có mặt trước camera nhưng độ tin cậy < ngưỡng (mặc định 80%) — báo rõ để
            // người đứng trước camera biết máy CHƯA nhận, không ghi sự kiện.
            AnimatedVisibility(
                visible = uiState.unrecognizedConfidence != null && uiState.overlayName == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (uiState.ambiguous) R.string.giong_hai_nguoi_format
                            else R.string.chua_nhan_dang_duoc_format,
                            uiState.unrecognizedConfidence ?: 0
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.overlayName != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.overlayName ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        uiState.overlayConfidence?.let { pct ->
                            Text(
                                text = stringResource(R.string.do_tin_cay_format, pct),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (hasCameraPermission && uiState.pendingCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cho_dong_bo_format, uiState.pendingCount),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (uiState.recentScans.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            text = stringResource(R.string.vua_cham_cong),
                            style = MaterialTheme.typography.labelLarge
                        )
                        uiState.recentScans.forEach { scan ->
                            Text(
                                text = "${scan.timeLabel}  ${scan.fullName}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Nút quản trị nhỏ, không nổi bật, tránh công nhân bấm nhầm (mục [7]) — có nền tròn
        // mờ phía sau để luôn thấy được dù camera phía sau sáng hay tối.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            shape = CircleShape,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            IconButton(onClick = onOpenAdmin) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.nut_cai_dat_enroll_hang_doi),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Luôn hiện, không phụ thuộc trạng thái gì — dùng để xác nhận thiết bị đang chạy
        // đúng bản build mới nhất khi debug từ xa.
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
        ) {
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

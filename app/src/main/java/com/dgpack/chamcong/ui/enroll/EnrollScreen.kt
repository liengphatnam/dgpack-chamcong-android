package com.dgpack.chamcong.ui.enroll

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.R
import com.dgpack.chamcong.face.EnrollPose
import com.dgpack.chamcong.face.FaceQualityChecker
import com.dgpack.chamcong.face.QualityHint
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.ui.camera.FaceCameraPreview

/**
 * Màn đăng ký khuôn mặt kiểu eKYC ngân hàng: camera + khung oval, vòng tiến độ % chất lượng,
 * gợi ý từng bước (nhìn thẳng / quay trái / quay phải), tự chụp khi đạt 100%, không có nút
 * chụp tay. Nút Lưu chỉ bật khi đủ 5 ảnh đạt.
 */
@Composable
fun EnrollScreen(onBack: () -> Unit, prefillCode: String = "", prefillName: String = "") {
    val context = LocalContext.current
    val viewModel = appViewModel { EnrollViewModel(it) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(prefillCode, prefillName) {
        viewModel.prefill(prefillCode, prefillName)
    }

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

    LaunchedEffect(state.saveSuccess) {
        // message lúc này đang giữ employeeCode vừa lưu thành công (xem EnrollViewModel.save()).
        if (state.saveSuccess) {
            kotlinx.coroutines.delay(3000)
            viewModel.consumeSaveSuccess()
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Text(stringResource(R.string.tieu_de_enroll), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ---- Camera + khung oval (chiếm phần lớn màn hình dọc) ----
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (hasCameraPermission) {
                    FaceCameraPreview(
                        targetFps = 5,
                        onFaceDetected = viewModel::onLiveFaceDetected,
                        onNoFace = viewModel::onNoFace
                    )
                    FaceOvalOverlay(
                        percent = state.qualityPercent,
                        complete = state.samplesComplete,
                        frameWidth = state.frameWidth,
                        frameHeight = state.frameHeight,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Bước + gợi ý, đặt dưới oval
                    Column(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (state.samplesComplete) "100%" else "${state.qualityPercent}%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = qualityColor(state.qualityPercent, state.samplesComplete)
                                )
                                Text(
                                    text = if (state.samplesComplete) stringResource(R.string.enroll_du_anh)
                                    else stringResource(
                                        R.string.enroll_buoc_format,
                                        state.capturedCount + 1, REQUIRED_ENROLL_SAMPLES, poseLabel(state.currentPose)
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    textAlign = TextAlign.Center
                                )
                                if (!state.samplesComplete) {
                                    Text(
                                        text = hintLabel(state.hint, state.currentPose),
                                        style = MaterialTheme.typography.bodyLarge,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(text = "Cần quyền Camera", modifier = Modifier.padding(24.dp))
                }
            }

            // ---- Form ----
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = state.employeeCode,
                    onValueChange = viewModel::onEmployeeCodeChange,
                    label = { Text(stringResource(R.string.ma_nhan_vien)) },
                    supportingText = { Text(stringResource(R.string.ma_nhan_vien_goi_y)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.fullName,
                    onValueChange = viewModel::onFullNameChange,
                    label = { Text(stringResource(R.string.ho_ten_hien_thi)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.chup_anh_format, state.capturedCount, REQUIRED_ENROLL_SAMPLES),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = viewModel::resetSamples, enabled = state.capturedCount > 0) {
                        Text(stringResource(R.string.nut_chup_lai))
                    }
                }

                Button(
                    onClick = viewModel::save,
                    enabled = state.samplesComplete && state.employeeCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.nut_luu_enroll))
                }

                state.message?.let { msg ->
                    val text = when (msg) {
                        "thieu_ma" -> stringResource(R.string.enroll_loi_thieu_ma)
                        "chua_du_anh" -> stringResource(R.string.enroll_loi_chua_du_anh)
                        else -> if (state.saveSuccess) stringResource(R.string.enroll_thanh_cong, msg) else msg
                    }
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(text = text, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

/**
 * Lớp phủ tối có khoét oval trong suốt + viền/vòng tiến độ đổi màu theo %.
 * Oval vẽ đúng vị trí của khung phân tích (toạ độ chuẩn hoá trong FaceQualityChecker) sau khi
 * quy đổi qua phép co giãn FILL_CENTER mà PreviewView đang dùng — nhờ vậy vị trí kiểm tra
 * "mặt trong oval" và vị trí oval nhìn thấy trùng nhau.
 */
@Composable
private fun FaceOvalOverlay(
    percent: Int,
    complete: Boolean,
    frameWidth: Int,
    frameHeight: Int,
    modifier: Modifier = Modifier
) {
    val ringColor = qualityColor(percent, complete)
    val scrim = Color.Black.copy(alpha = 0.55f)
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
        val ox = (size.width - frameWidth * scale) / 2f
        val oy = (size.height - frameHeight * scale) / 2f
        val cx = ox + FaceQualityChecker.OVAL_CX * frameWidth * scale
        val cy = oy + FaceQualityChecker.OVAL_CY * frameHeight * scale
        val rx = FaceQualityChecker.OVAL_RX * frameWidth * scale
        val ry = FaceQualityChecker.OVAL_RY * frameHeight * scale
        val topLeft = Offset(cx - rx, cy - ry)
        val ovalSize = Size(rx * 2, ry * 2)

        drawRect(scrim)
        drawOval(Color.Transparent, topLeft = topLeft, size = ovalSize, blendMode = BlendMode.Clear)

        val stroke = 6.dp.toPx()
        drawOval(Color.White.copy(alpha = 0.35f), topLeft = topLeft, size = ovalSize, style = Stroke(stroke))
        val sweep = if (complete) 360f else 360f * percent / 100f
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = ovalSize,
            style = Stroke(stroke)
        )
    }
}

@Composable
private fun qualityColor(percent: Int, complete: Boolean): Color = when {
    complete || percent >= 100 -> Color(0xFF2ECC71)
    percent >= 60 -> Color(0xFFF1C40F)
    else -> Color(0xFFE74C3C)
}

@Composable
private fun poseLabel(pose: EnrollPose?): String = when (pose) {
    EnrollPose.SIDE_A -> stringResource(R.string.pose_side_a)
    EnrollPose.SIDE_B -> stringResource(R.string.pose_side_b)
    else -> stringResource(R.string.pose_front)
}

@Composable
private fun hintLabel(hint: QualityHint, pose: EnrollPose?): String = when (hint) {
    QualityHint.NO_FACE -> stringResource(R.string.hint_no_face)
    QualityHint.MULTIPLE_FACES -> stringResource(R.string.hint_multi)
    QualityHint.MOVE_INTO_FRAME -> stringResource(R.string.hint_into_frame)
    QualityHint.MOVE_CLOSER -> stringResource(R.string.hint_closer)
    QualityHint.MOVE_BACK -> stringResource(R.string.hint_back)
    QualityHint.LOOK_STRAIGHT -> stringResource(R.string.hint_straight)
    QualityHint.TURN_SIDE -> if (pose == EnrollPose.SIDE_B) stringResource(R.string.pose_side_b) else stringResource(R.string.pose_side_a)
    QualityHint.TURN_OTHER_WAY -> stringResource(R.string.hint_other_way)
    QualityHint.TURN_LESS -> stringResource(R.string.hint_turn_less)
    QualityHint.TOO_DARK -> stringResource(R.string.hint_dark)
    QualityHint.TOO_BRIGHT -> stringResource(R.string.hint_bright)
    QualityHint.BLURRY -> stringResource(R.string.hint_blurry)
    QualityHint.OPEN_EYES -> stringResource(R.string.hint_eyes)
    QualityHint.GOOD -> stringResource(R.string.hint_good)
}

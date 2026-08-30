package com.dgpack.chamcong.ui.enroll

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.R
import com.dgpack.chamcong.ui.appViewModel
import com.dgpack.chamcong.ui.camera.FaceCameraPreview

@Composable
fun EnrollScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val viewModel = appViewModel { EnrollViewModel(it) }
    val state by viewModel.state.collectAsState()

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
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.quay_lai))
                }
                Text(stringResource(R.string.tieu_de_enroll), style = MaterialTheme.typography.titleLarge)
            }
        }
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (hasCameraPermission) {
                    FaceCameraPreview(targetFps = 5, onFaceDetected = viewModel::onLiveFaceDetected)
                } else {
                    Text(
                        text = "Cần quyền Camera",
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

                Text(
                    text = stringResource(R.string.chup_anh_format, state.capturedCount, MAX_ENROLL_SAMPLES),
                    style = MaterialTheme.typography.bodyLarge
                )

                Button(
                    onClick = viewModel::capturePhoto,
                    enabled = state.capturedCount < MAX_ENROLL_SAMPLES,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.nut_chup_anh))
                }

                Button(
                    onClick = viewModel::save,
                    enabled = state.capturedCount >= MIN_ENROLL_SAMPLES && state.employeeCode.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.nut_luu_enroll))
                }

                state.message?.let { msg ->
                    val text = when (msg) {
                        "thieu_ma" -> stringResource(R.string.enroll_loi_thieu_ma)
                        "chua_du_anh" -> stringResource(R.string.enroll_loi_chua_du_anh)
                        "khong_thay_mat" -> stringResource(R.string.enroll_loi_khong_thay_mat)
                        else -> if (state.saveSuccess) {
                            stringResource(R.string.enroll_thanh_cong, msg)
                        } else msg
                    }
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(text = text, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

package com.dgpack.chamcong.ui.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.dgpack.chamcong.camera.FaceAnalyzer
import com.dgpack.chamcong.camera.FaceDetectionResult
import java.util.concurrent.Executors

/**
 * Camera preview + phát hiện khuôn mặt dùng chung giữa màn hình Camera chính và Enroll.
 * [targetFps] thấp cho màn hình chấm công (throttle mục [4.3]), có thể cao hơn ở Enroll
 * để bắt khoảnh khắc chụp nhanh hơn.
 */
@Composable
fun FaceCameraPreview(
    modifier: Modifier = Modifier,
    targetFps: Int = 3,
    onFaceDetected: (FaceDetectionResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // Mặc định PreviewView dùng SurfaceView (chế độ PERFORMANCE), luôn vẽ đè lên
                // trên MỌI composable khác trong cùng cửa sổ bất kể thứ tự khai báo trong Box
                // — khiến overlay tên/nút cài đặt bị camera preview che mất hoàn toàn. Ép dùng
                // TextureView (COMPATIBLE) để tham gia đúng thứ tự vẽ của Compose.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(analysisExecutor, FaceAnalyzer(targetFps = targetFps, onFaceDetected = onFaceDetected))

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

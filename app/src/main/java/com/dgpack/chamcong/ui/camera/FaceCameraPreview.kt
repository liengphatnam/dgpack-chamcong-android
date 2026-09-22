package com.dgpack.chamcong.ui.camera

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    onFaceDetected: (FaceDetectionResult) -> Unit,
    onNoFace: () -> Unit = {},
    wantEvidence: () -> Boolean = { false },
    minFaceSize: Float = 0.12f
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val providerHolder = remember { arrayOfNulls<ProcessCameraProvider>(1) }

    // Rời màn hình (vd xong bước chụp bằng chứng quên thẻ) -> tắt camera + luồng phân tích ngay,
    // không để chạy ngầm tốn CPU cho tới lần bind kế tiếp.
    DisposableEffect(Unit) {
        onDispose {
            providerHolder[0]?.unbindAll()
            analysisExecutor.shutdown()
        }
    }

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
                if (analysisExecutor.isShutdown) return@addListener // màn hình đã đóng trước khi camera sẵn sàng
                providerHolder[0] = cameraProvider

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // 640x480 hơi thấp (mặt xa bị nhoè), nhưng 1280x720 làm tablet yếu (MFISO B1 PRO)
                // giật hẳn: camera HAL phải xuất thêm 1 luồng 720p song song với preview và ML Kit
                // phải quét gấp 3 lần số pixel. 800x600 (đúng tỉ lệ 4:3 của cảm biến) là mức
                // cân bằng — mặt cách 1 m ~70 px, đủ cho model. Máy không hỗ trợ sẽ tự chọn gần nhất.
                // ResolutionSelector thay cho setTargetResolution: nếu camera KHÔNG hỗ trợ đúng
                // 800x600, chọn cỡ THẤP hơn gần nhất (640x480) thay vì nhảy lên 1280x960/1600x1200
                // — chính là lý do B1 PRO phát hiện mặt mất >1 s.
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(
                                ResolutionStrategy(Size(800, 600), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                            )
                            .build()
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    analysisExecutor,
                    FaceAnalyzer(
                        targetFps = targetFps,
                        onFaceDetected = onFaceDetected,
                        onNoFace = onNoFace,
                        wantEvidence = wantEvidence,
                        minFaceSize = minFaceSize
                    )
                )

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

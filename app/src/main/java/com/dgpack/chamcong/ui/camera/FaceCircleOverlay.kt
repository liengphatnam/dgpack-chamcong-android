package com.dgpack.chamcong.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Vị trí/kích thước khung tròn trong toạ độ CHUẨN HOÁ của khung hình phân tích. */
private const val CIRCLE_CX = 0.5f
private const val CIRCLE_CY = 0.45f
/** Bán kính = tỉ lệ này × bề rộng khung hình. */
private const val CIRCLE_R = 0.32f

/**
 * Khung TRÒN hướng dẫn đặt mặt (bước chụp bằng chứng quên thẻ): lớp phủ tối khoét tròn + viền
 * + vòng màu theo trạng thái. Vẽ theo phép co giãn FILL_CENTER của PreviewView để trùng với
 * vị trí thật trong khung hình camera.
 *
 * @param sweepFraction 0..1 phần vòng màu được vẽ (1 = kín vòng)
 * @param scrimAlpha    độ tối của lớp phủ ngoài vòng (0 = không phủ)
 */
@Composable
fun FaceCircleOverlay(
    frameWidth: Int,
    frameHeight: Int,
    ringColor: Color,
    sweepFraction: Float,
    scrimAlpha: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val scale = maxOf(size.width / frameWidth, size.height / frameHeight)
        val ox = (size.width - frameWidth * scale) / 2f
        val oy = (size.height - frameHeight * scale) / 2f
        val cx = ox + CIRCLE_CX * frameWidth * scale
        val cy = oy + CIRCLE_CY * frameHeight * scale
        val r = CIRCLE_R * frameWidth * scale
        val topLeft = Offset(cx - r, cy - r)
        val circleSize = Size(r * 2, r * 2)

        if (scrimAlpha > 0f) {
            drawRect(Color.Black.copy(alpha = scrimAlpha))
            drawOval(Color.Transparent, topLeft = topLeft, size = circleSize, blendMode = BlendMode.Clear)
        }

        val stroke = 6.dp.toPx()
        drawOval(Color.White.copy(alpha = 0.35f), topLeft = topLeft, size = circleSize, style = Stroke(stroke))
        drawArc(
            color = ringColor,
            startAngle = -90f,
            sweepAngle = 360f * sweepFraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = topLeft,
            size = circleSize,
            style = Stroke(stroke)
        )
    }
}

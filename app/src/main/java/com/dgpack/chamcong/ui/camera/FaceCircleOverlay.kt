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
import com.dgpack.chamcong.face.FaceQualityChecker

/**
 * Khung TRÒN hướng dẫn đặt mặt, dùng chung cho màn Chấm công và màn Đăng ký: lớp phủ tối
 * khoét tròn trong suốt + viền mờ + vòng màu theo trạng thái/tiến độ.
 *
 * Vòng tròn vẽ đúng vị trí của khung phân tích (toạ độ chuẩn hoá trong FaceQualityChecker)
 * sau khi quy đổi qua phép co giãn FILL_CENTER mà PreviewView đang dùng — nhờ vậy chỗ kiểm tra
 * "mặt trong khung" và chỗ người dùng nhìn thấy trùng nhau.
 *
 * @param sweepFraction 0..1 phần vòng màu được vẽ (1 = kín vòng; màn Đăng ký dùng % chất lượng)
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
        val cx = ox + FaceQualityChecker.OVAL_CX * frameWidth * scale
        val cy = oy + FaceQualityChecker.OVAL_CY * frameHeight * scale
        val r = FaceQualityChecker.OVAL_R * frameWidth * scale
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

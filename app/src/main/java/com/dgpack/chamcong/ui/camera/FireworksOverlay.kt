package com.dgpack.chamcong.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pháo hoa chúc mừng trúng thưởng — vẽ thuần bằng Canvas, không cần thư viện ngoài.
 * Toạ độ hạt chuẩn hoá 0..1 theo kích thước màn hình nên xoay dọc/ngang đều đẹp.
 * Canvas không bắt sự kiện chạm, các nút phía trên (quản trị) vẫn bấm được.
 *
 * @param seed đổi giá trị để bắt đầu lại màn pháo hoa mới (mỗi lần trúng thưởng).
 */
@Composable
fun FireworksOverlay(seed: Long, modifier: Modifier = Modifier) {
    val bursts = remember(seed) { mutableListOf<Burst>() }
    var frameNanos by remember { mutableLongStateOf(0L) }

    LaunchedEffect(seed) {
        val rnd = Random(seed)
        var last = withFrameNanos { it }
        var nextBurstAt = last
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now
            if (now >= nextBurstAt) {
                bursts += Burst.random(rnd)
                nextBurstAt = now + rnd.nextLong(220, 550) * 1_000_000
            }
            bursts.forEach { it.update(dt) }
            bursts.removeAll { it.isDead }
            frameNanos = now
        }
    }

    Canvas(modifier = modifier) {
        @Suppress("UNUSED_VARIABLE")
        val tick = frameNanos // đọc state để Canvas vẽ lại mỗi frame
        val minSide = minOf(size.width, size.height)
        for (burst in bursts) {
            for (p in burst.particles) {
                if (p.life <= 0f) continue
                drawCircle(
                    color = burst.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                    radius = p.radius * minSide,
                    center = Offset(p.x * size.width, p.y * size.height)
                )
            }
        }
    }
}

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    var life: Float,
    val decay: Float
)

private class Burst(val color: Color, val particles: List<Particle>) {
    val isDead: Boolean get() = particles.all { it.life <= 0f }

    fun update(dt: Float) {
        for (p in particles) {
            if (p.life <= 0f) continue
            p.vy += GRAVITY * dt
            p.vx *= 1f - DRAG * dt
            p.vy *= 1f - DRAG * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.life -= p.decay * dt
        }
    }

    companion object {
        private const val GRAVITY = 0.35f
        private const val DRAG = 1.2f
        private val PALETTE = listOf(
            Color(0xFFFF5252), Color(0xFFFFD740), Color(0xFF69F0AE),
            Color(0xFF40C4FF), Color(0xFFE040FB), Color(0xFFFFAB40), Color(0xFFFFFFFF)
        )

        fun random(rnd: Random): Burst {
            val cx = 0.15f + rnd.nextFloat() * 0.7f
            val cy = 0.12f + rnd.nextFloat() * 0.45f
            val count = 40 + rnd.nextInt(25)
            val speed = 0.18f + rnd.nextFloat() * 0.25f
            val particles = List(count) { i ->
                val angle = (i.toFloat() / count) * 2f * Math.PI.toFloat() + rnd.nextFloat() * 0.2f
                val s = speed * (0.6f + rnd.nextFloat() * 0.4f)
                Particle(
                    x = cx, y = cy,
                    vx = cos(angle) * s, vy = sin(angle) * s,
                    radius = 0.004f + rnd.nextFloat() * 0.006f,
                    life = 1f,
                    decay = 0.55f + rnd.nextFloat() * 0.35f
                )
            }
            return Burst(PALETTE[rnd.nextInt(PALETTE.size)], particles)
        }
    }
}

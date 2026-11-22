package com.theartist.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theartist.music.platform.decodeImageBitmap
import com.theartist.music.ui.theme.ArtistSplashBlue
import com.theartist.music.ui.theme.ArtistSplashCyan
import com.theartist.music.ui.theme.ArtistSplashPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArtworkBox(
    accentColorHex: Long,
    artworkBytes: ByteArray?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    iconSize: Dp = 22.dp
) {
    val decodedBitmap = remember(artworkBytes) {
        artworkBytes?.let { decodeImageBitmap(it) }
    }
    val baseColor = remember(accentColorHex) { Color(accentColorHex) }
    val darkTint = remember(baseColor) {
        Color(
            red = (baseColor.red * 0.35f).coerceIn(0f, 1f),
            green = (baseColor.green * 0.35f).coerceIn(0f, 1f),
            blue = (baseColor.blue * 0.45f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(baseColor, darkTint)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = "Pochette",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.14f),
                    radius = size.minDimension * 0.50f,
                    center = Offset(size.width * 0.82f, size.height * 0.20f)
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.22f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.18f, size.height * 0.85f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.90f),
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun SplashLogoEmblem(
    modifier: Modifier = Modifier,
    size: Dp = 156.dp
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val r = this.size.minDimension * 0.39f

            // Layered translucent organic aura petals (blue / purple / sky)
            rotate(-22f, pivot = c) {
                drawOval(
                    color = ArtistSplashPurple.copy(alpha = 0.65f),
                    topLeft = Offset(c.x - r * 1.15f, c.y - r * 0.96f),
                    size = androidx.compose.ui.geometry.Size(r * 2.30f, r * 1.92f)
                )
            }
            rotate(28f, pivot = c) {
                drawOval(
                    color = ArtistSplashCyan.copy(alpha = 0.58f),
                    topLeft = Offset(c.x - r * 1.08f, c.y - r * 0.98f),
                    size = androidx.compose.ui.geometry.Size(r * 2.16f, r * 1.96f)
                )
            }
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF6B85FF), ArtistSplashBlue, Color(0xFF3E5CE6))
                ),
                radius = r,
                center = c
            )
        }
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = "The @rtist Music",
            tint = Color.White,
            modifier = Modifier.size(size * 0.52f)
        )
    }
}

@Composable
fun OrbitLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    Canvas(modifier = modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.36f
        val stroke = 3.dp.toPx()

        // Soft outer glow ring
        drawCircle(
            color = Color.White.copy(alpha = 0.16f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke * 2.2f)
        )
        // Main crisp white ring
        drawCircle(
            color = Color.White.copy(alpha = 0.85f),
            radius = radius,
            center = center,
            style = Stroke(width = stroke)
        )

        // Orbiting bright dot
        val rad = (angle - 45f) * (PI / 180.0)
        val orbitR = radius + 6.dp.toPx()
        val dotCenter = Offset(
            x = center.x + (orbitR * cos(rad)).toFloat(),
            y = center.y + (orbitR * sin(rad)).toFloat()
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.78f),
            radius = 3.5.dp.toPx(),
            center = dotCenter
        )
    }
}

@Composable
fun FourDotsMenuIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(20.dp)) {
        val dotR = 2.dp.toPx()
        val offset = 5.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawCircle(tint, dotR, Offset(cx - offset, cy - offset))
        drawCircle(tint, dotR, Offset(cx + offset, cy - offset))
        drawCircle(tint, dotR, Offset(cx - offset, cy + offset))
        drawCircle(tint, dotR, Offset(cx + offset, cy + offset))
    }
}

@Composable
fun PlayingEqualizerBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(360, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    val heights = if (isPlaying) listOf(bar1, bar2, bar3) else listOf(0.35f, 0.55f, 0.35f)

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((14f * fraction).dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun MiniRingPlayButton(
    isPlaying: Boolean,
    progress: Float,
    accentColor: Color,
    trackRingColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(30.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 1.8.dp.toPx()
            drawCircle(
                color = trackRingColor,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

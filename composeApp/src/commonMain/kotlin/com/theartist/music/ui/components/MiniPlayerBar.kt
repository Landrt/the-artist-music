package com.theartist.music.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theartist.music.model.Track
import com.theartist.music.ui.theme.ArtistCrimson

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    isDarkTheme: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onOpenQueue: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val barBg = if (isDarkTheme) Color(0xFF111111) else Color(0xFFF6F6F6)
    val topBorder = if (isDarkTheme) Color(0xFF1C1C1C) else Color(0xFFE4E4E4)
    val ringTrack = if (isDarkTheme) Color(0xFF4B121F) else Color(0xFFFFCCD7)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .clickable(onClick = onClick)
    ) {
        HorizontalDivider(thickness = 0.5.dp, color = topBorder)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: 42dp rounded cover aligned with song list left edge (16dp)
            ArtworkBox(
                accentColorHex = track.accentColorHex,
                artworkBytes = track.artworkBytes,
                cornerRadius = 7.dp,
                iconSize = 19.dp,
                modifier = Modifier.size(42.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Middle: Title + Artist
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: Exact 3 crimson controls with tight clean spacing
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Play/Pause inside thin circular progress ring
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTogglePlayPause
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 1.6.dp.toPx()
                        val inset = stroke / 2f
                        drawArc(
                            color = ringTrack,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke)
                        )
                        if (progress > 0f) {
                            drawArc(
                                color = ArtistCrimson,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke, cap = StrokeCap.Round)
                            )
                        }

                        if (isPlaying) {
                            val barW = 2.4.dp.toPx()
                            val barH = 10.dp.toPx()
                            val gap = 3.2.dp.toPx()
                            val topY = (size.height - barH) / 2f
                            val leftX = (size.width - (barW * 2 + gap)) / 2f
                            drawRoundRect(
                                color = ArtistCrimson,
                                topLeft = Offset(leftX, topY),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                            drawRoundRect(
                                color = ArtistCrimson,
                                topLeft = Offset(leftX + barW + gap, topY),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                            )
                        } else {
                            // Optically centered crimson play triangle
                            val cx = size.width / 2f + 1.dp.toPx()
                            val cy = size.height / 2f
                            val triW = 8.5.dp.toPx()
                            val triH = 10.5.dp.toPx()
                            val path = Path().apply {
                                moveTo(cx - triW * 0.48f, cy - triH * 0.5f)
                                lineTo(cx + triW * 0.56f, cy)
                                lineTo(cx - triW * 0.48f, cy + triH * 0.5f)
                                close()
                            }
                            drawPath(path = path, color = ArtistCrimson)
                        }
                    }
                }

                // 2. Crisp crimson Next Track (▶|)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSkipNext
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val barW = 2.2.dp.toPx()
                        val triRight = size.width - barW - 1.dp.toPx()
                        val path = Path().apply {
                            moveTo(size.width * 0.08f, size.height * 0.12f)
                            lineTo(triRight, size.height * 0.5f)
                            lineTo(size.width * 0.08f, size.height * 0.88f)
                            close()
                        }
                        drawPath(path = path, color = ArtistCrimson)
                        drawRoundRect(
                            color = ArtistCrimson,
                            topLeft = Offset(size.width - barW, size.height * 0.14f),
                            size = Size(barW, size.height * 0.72f),
                            cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx())
                        )
                    }
                }

                // 3. Exact crimson Queue-with-music-note icon (≡♪)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenQueue
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(19.dp)) {
                        val stroke = 1.7.dp.toPx()
                        // Top line (full width)
                        drawLine(
                            color = ArtistCrimson,
                            start = Offset(0f, size.height * 0.22f),
                            end = Offset(size.width * 0.88f, size.height * 0.22f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Middle line (shortened for note)
                        drawLine(
                            color = ArtistCrimson,
                            start = Offset(0f, size.height * 0.50f),
                            end = Offset(size.width * 0.50f, size.height * 0.50f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Bottom line (shortened for note)
                        drawLine(
                            color = ArtistCrimson,
                            start = Offset(0f, size.height * 0.78f),
                            end = Offset(size.width * 0.42f, size.height * 0.78f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Musical note in bottom-right
                        val stemX = size.width * 0.74f
                        val stemTop = size.height * 0.40f
                        val stemBottom = size.height * 0.78f
                        drawLine(
                            color = ArtistCrimson,
                            start = Offset(stemX, stemTop),
                            end = Offset(stemX, stemBottom),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Flag
                        drawLine(
                            color = ArtistCrimson,
                            start = Offset(stemX, stemTop),
                            end = Offset(size.width * 0.96f, size.height * 0.48f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round
                        )
                        // Note head
                        drawCircle(
                            color = ArtistCrimson,
                            radius = 2.4.dp.toPx(),
                            center = Offset(stemX - 1.6.dp.toPx(), stemBottom)
                        )
                    }
                }
            }
        }
    }
}

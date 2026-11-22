package com.theartist.music.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theartist.music.model.PlayerUiState
import com.theartist.music.model.RepeatMode
import com.theartist.music.model.Track
import com.theartist.music.ui.components.ArtworkBox
import com.theartist.music.ui.theme.ArtistCrimson

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    uiState: PlayerUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onPlayNext: (Track) -> Unit = {}
) {
    val track = uiState.currentTrack ?: return
    val isFavorite = track.id in uiState.favoriteIds
    var showLyrics by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    val rawAccent by animateColorAsState(
        targetValue = Color(track.accentColorHex),
        animationSpec = tween(550),
        label = "accent"
    )

    // Faithful desaturated matte slate-mist gradient from reference screenshot
    val topMist = lerp(Color(0xFF646A68), rawAccent, 0.11f)
    val midMist = lerp(Color(0xFF4B514F), rawAccent, 0.08f)
    val bottomMist = lerp(Color(0xFF2D3230), rawAccent, 0.05f)

    val coverScale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1.0f else 0.985f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "coverScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(topMist, midMist, bottomMist)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // 1. Top Bar: Thin Chevron (v) | 2 page dots (● •) | Thin Outlined Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapse
                        ),
                    contentAlignment = Alignment.CenterStart
                ) {
                    ThinDownChevronIcon(tint = Color.White.copy(alpha = 0.82f))
                }

                // 2-dot page indicator (Cover <-> Lyrics)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showLyrics = !showLyrics }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (!showLyrics) 6.5.dp else 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (!showLyrics) 0.95f else 0.42f))
                    )
                    Box(
                        modifier = Modifier
                            .size(if (showLyrics) 6.5.dp else 5.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = if (showLyrics) 0.95f else 0.42f))
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showInfoSheet = true },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    ThinShareTriangleIcon(tint = Color.White.copy(alpha = 0.82f))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Large Square Rounded Album Cover with HQ/SQ bottom-left pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (totalDrag < -50f) showLyrics = true
                                else if (totalDrag > 50f) showLyrics = false
                                totalDrag = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                totalDrag += dragAmount
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!showLyrics) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(coverScale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showLyrics = true }
                    ) {
                        ArtworkBox(
                            accentColorHex = track.accentColorHex,
                            artworkBytes = track.artworkBytes,
                            track = track,
                            cornerRadius = 16.dp,
                            iconSize = 84.dp,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom-left HQ / SQ pill
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.Black.copy(alpha = 0.42f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = qualityBadgeLabel(track.formatInfo),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.24f))
                            .clickable { showLyrics = false }
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (!track.lyrics.isNullOrBlank()) {
                                Text(
                                    text = track.lyrics,
                                    fontSize = 16.sp,
                                    lineHeight = 30.sp,
                                    color = Color.White.copy(alpha = 0.92f),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Text(
                                    text = "Aucune parole .lrc locale",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "${track.album}\n${track.folderPath}",
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Color.White.copy(alpha = 0.62f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 3. Track Title (large, marquee) + Heart icon flush right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = track.title,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.94f),
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .basicMarquee()
                )
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onToggleFavorite(track.id) },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favori",
                        tint = if (isFavorite) ArtistCrimson else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = track.artist,
                fontSize = 14.5.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // 4. Middle 3-icon row aligned flush with cover edges: (↓) | Bell | (⋮)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                        .clickable { showInfoSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = "Audio local",
                        tint = Color.White.copy(alpha = 0.24f),
                        modifier = Modifier.size(14.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showInfoSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsNone,
                        contentDescription = "Infos / Sonnerie",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(23.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .border(1.2.dp, Color.White.copy(alpha = 0.68f), CircleShape)
                        .clickable { showMoreSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // 5. Custom Ultra-Thin White Seekbar (2dp line + 12dp white dot) + 00:10 / 03:19
            val duration = uiState.durationMs.coerceAtLeast(1L)
            ThinWhiteSeekBar(
                positionMs = uiState.positionMs.coerceIn(0L, duration),
                durationMs = duration,
                onSeekTo = onSeekTo,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatPaddedTime(uiState.positionMs.coerceIn(0L, duration)),
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.60f)
                )
                Text(
                    text = formatPaddedTime(duration),
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.60f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Bottom Transport Row aligned flush with cover edges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 26.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle / Repeat mode
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            if (uiState.isShuffleEnabled) {
                                onToggleShuffle()
                            } else if (uiState.repeatMode == RepeatMode.ONE) {
                                onCycleRepeat()
                                onToggleShuffle()
                            } else {
                                onCycleRepeat()
                            }
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    val modeIcon = when {
                        uiState.isShuffleEnabled -> Icons.Rounded.Shuffle
                        uiState.repeatMode == RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    }
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = "Mode lecture",
                        tint = Color.White.copy(alpha = 0.78f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSkipPrevious
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Précédent",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                // Exact organic white pebble Play/Pause button
                OrganicPlayPauseButton(
                    isPlaying = uiState.isPlaying,
                    iconTint = bottomMist,
                    onClick = onTogglePlayPause
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSkipNext
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Suivant",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenQueue
                        ),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "File d'attente",
                        tint = Color.White.copy(alpha = 0.84f),
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
        }
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp)
            ) {
                Text(
                    text = track.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} - ${track.album}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                PlayerOptionRow(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isFavorite) "Retirer des favoris" else "Ajouter aux favoris",
                    tint = if (isFavorite) ArtistCrimson else MaterialTheme.colorScheme.onSurface
                ) {
                    onToggleFavorite(track.id)
                    showMoreSheet = false
                }
                PlayerOptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Lire ensuite dans la file"
                ) {
                    onPlayNext(track)
                    showMoreSheet = false
                }
                PlayerOptionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Afficher la file d'attente (${uiState.queue.size})"
                ) {
                    showMoreSheet = false
                    onOpenQueue()
                }
            }
        }
    }

    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Informations du morceau",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Titre : ${track.title}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Artiste : ${track.artist}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Album : ${track.album}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Qualité : ${track.formatInfo}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Durée : ${formatPaddedTime(track.durationMs)}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "Emplacement : ${track.folderPath}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ThinWhiteSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableStateOf(1f) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    val fraction = dragFraction ?: (positionMs.toFloat() / durationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    val f = (offset.x / widthPx).coerceIn(0f, 1f)
                    onSeekTo((f * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { f -> onSeekTo((f * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = {
                        dragFraction = null
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val stroke = 2.dp.toPx()
            val thumbR = 6.dp.toPx()
            val activeX = (size.width * fraction).coerceIn(thumbR, (size.width - thumbR).coerceAtLeast(thumbR))

            // Inactive thin track
            drawLine(
                color = Color.White.copy(alpha = 0.28f),
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // Active white track
            drawLine(
                color = Color.White,
                start = Offset(0f, cy),
                end = Offset(activeX, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            // Crisp white circle thumb
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = Offset(activeX, cy)
            )
        }
    }
}

@Composable
private fun ThinDownChevronIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val path = Path().apply {
            moveTo(size.width * 0.14f, size.height * 0.34f)
            lineTo(size.width * 0.50f, size.height * 0.68f)
            lineTo(size.width * 0.86f, size.height * 0.34f)
        }
        drawPath(
            path = path,
            color = tint,
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun ThinShareTriangleIcon(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.3.dp.toPx()
        val r = 2.4.dp.toPx()
        val top = Offset(size.width * 0.58f, size.height * 0.18f)
        val bl = Offset(size.width * 0.18f, size.height * 0.80f)
        val br = Offset(size.width * 0.84f, size.height * 0.80f)

        // Connecting thin edges (shortened so they meet the hollow circles cleanly)
        drawLine(tint, Offset(top.x - 1.5f, top.y + r), Offset(bl.x + 1.5f, bl.y - r), stroke)
        drawLine(tint, Offset(bl.x + r, bl.y), Offset(br.x - r, br.y), stroke)

        drawCircle(tint, r, top, style = Stroke(stroke))
        drawCircle(tint, r, bl, style = Stroke(stroke))
        drawCircle(tint, r, br, style = Stroke(stroke))
    }
}

@Composable
private fun OrganicPlayPauseButton(
    isPlaying: Boolean,
    iconTint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(66.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Soft rounded pebble / asymmetrical guitar-pick silhouette from reference
            val path = Path().apply {
                moveTo(w * 0.58f, h * 0.05f)
                cubicTo(w * 0.88f, h * 0.09f, w * 0.99f, h * 0.38f, w * 0.94f, h * 0.67f)
                cubicTo(w * 0.89f, h * 0.92f, w * 0.63f, h * 0.97f, w * 0.37f, h * 0.93f)
                cubicTo(w * 0.12f, h * 0.88f, w * 0.02f, h * 0.66f, w * 0.07f, h * 0.40f)
                cubicTo(w * 0.13f, h * 0.15f, w * 0.34f, h * 0.02f, w * 0.58f, h * 0.05f)
                close()
            }
            drawPath(path = path, color = Color.White)
        }
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Lecture",
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
private fun PlayerOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(text = label, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun qualityBadgeLabel(formatInfo: String): String {
    val upper = formatInfo.uppercase()
    return when {
        "FLAC" in upper || "WAV" in upper -> "SQ"
        else -> "HQ"
    }
}

private fun formatPaddedTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val minStr = if (minutes < 10L) "0$minutes" else "$minutes"
    val secStr = if (seconds < 10L) "0$seconds" else "$seconds"
    return "$minStr:$secStr"
}

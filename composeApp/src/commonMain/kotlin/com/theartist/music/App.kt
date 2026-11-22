package com.theartist.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theartist.music.state.MusicPlayerStateHolder
import com.theartist.music.ui.components.MiniPlayerBar
import com.theartist.music.ui.components.PlayingEqualizerBars
import com.theartist.music.ui.screens.LibraryScreen
import com.theartist.music.ui.screens.NowPlayingScreen
import com.theartist.music.ui.screens.SplashScreen
import com.theartist.music.ui.theme.ArtistCrimson
import com.theartist.music.ui.theme.MutedIconDark
import com.theartist.music.ui.theme.TheArtistMusicTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TheArtistMusicApp(
    stateHolder: MusicPlayerStateHolder,
    onRequestAudioPermission: () -> Unit = {}
) {
    val uiState by stateHolder.uiState.collectAsState()
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(5000L)
        showSplash = false
    }

    TheArtistMusicTheme(darkTheme = uiState.isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            LibraryScreen(
                uiState = uiState,
                bottomPaddingForMiniPlayer = uiState.currentTrack != null,
                onSelectTab = stateHolder::selectTab,
                onCycleSortOrder = stateHolder::cycleSortOrder,
                onOpenCollection = stateHolder::openCollection,
                onOpenNowPlaying = {
                    if (uiState.currentTrack == null && uiState.allTracks.isNotEmpty()) {
                        stateHolder.playTracks(uiState.allTracks, 0, false)
                    }
                    stateHolder.openNowPlaying(true)
                },
                onToggleSearch = stateHolder::toggleSearch,
                onSearchQueryChange = stateHolder::updateSearchQuery,
                onToggleDarkTheme = stateHolder::toggleDarkTheme,
                onRefreshLibrary = stateHolder::refreshLibrary,
                onRequestAudioPermission = onRequestAudioPermission,
                onPlayTracks = stateHolder::playTracks,
                onPlayNext = stateHolder::playNextInQueue,
                onToggleFavorite = stateHolder::toggleFavorite,
                onCreatePlaylist = stateHolder::createPlaylist,
                onDeletePlaylist = stateHolder::deletePlaylist,
                onAddTrackToPlaylist = stateHolder::addTrackToPlaylist
            )

            val currentTrack = uiState.currentTrack
            if (currentTrack != null && !uiState.isNowPlayingOpen) {
                MiniPlayerBar(
                    track = currentTrack,
                    isPlaying = uiState.isPlaying,
                    isDarkTheme = uiState.isDarkTheme,
                    positionMs = uiState.positionMs,
                    durationMs = uiState.durationMs,
                    onTogglePlayPause = stateHolder::togglePlayPause,
                    onSkipNext = stateHolder::skipNext,
                    onOpenQueue = { stateHolder.openQueueSheet(true) },
                    onClick = { stateHolder.openNowPlaying(true) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                )
            }

            AnimatedVisibility(
                visible = uiState.isNowPlayingOpen && currentTrack != null,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(260)
                ) + fadeIn(tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(240)
                ) + fadeOut(tween(160))
            ) {
                NowPlayingScreen(
                    uiState = uiState,
                    onCollapse = {
                        stateHolder.openCollection(null)
                        stateHolder.openNowPlaying(false)
                    },
                    onTogglePlayPause = stateHolder::togglePlayPause,
                    onSeekTo = stateHolder::seekTo,
                    onSkipNext = stateHolder::skipNext,
                    onSkipPrevious = stateHolder::skipPrevious,
                    onToggleShuffle = stateHolder::toggleShuffle,
                    onCycleRepeat = stateHolder::cycleRepeatMode,
                    onToggleFavorite = stateHolder::toggleFavorite,
                    onOpenQueue = { stateHolder.openQueueSheet(true) },
                    onPlayNext = stateHolder::playNextInQueue
                )
            }

            // Shared Queue Bottom Sheet (accessible from both MiniPlayer and NowPlaying)
            if (uiState.isQueueSheetOpen) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ModalBottomSheet(
                    onDismissRequest = { stateHolder.openQueueSheet(false) },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp)
                    ) {
                        Text(
                            text = "File d'attente (${uiState.queue.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        ) {
                            itemsIndexed(
                                items = uiState.queue,
                                key = { idx, item -> "${idx}_${item.id}" }
                            ) { index, item ->
                                val isCurrent = index == uiState.currentQueueIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { stateHolder.playFromQueue(index) }
                                        .padding(vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        PlayingEqualizerBars(
                                            isPlaying = uiState.isPlaying,
                                            color = ArtistCrimson,
                                            modifier = Modifier.padding(end = 10.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontSize = 15.sp,
                                            fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                            color = if (isCurrent) ArtistCrimson else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.artist,
                                            fontSize = 12.5.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { stateHolder.removeFromQueue(index) }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Retirer",
                                            tint = MutedIconDark,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                            }
                        }
                    }
                }
            }

            // Splash overlay with smooth fade-out
            AnimatedVisibility(
                visible = showSplash,
                exit = fadeOut(animationSpec = tween(380))
            ) {
                SplashScreen()
            }
        }
    }
}

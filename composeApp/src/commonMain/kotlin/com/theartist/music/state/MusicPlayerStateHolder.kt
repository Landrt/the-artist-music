package com.theartist.music.state

import androidx.compose.ui.graphics.ImageBitmap
import com.theartist.music.model.LibraryTab
import com.theartist.music.model.PlayerUiState
import com.theartist.music.model.Playlist
import com.theartist.music.model.QuickCollection
import com.theartist.music.model.RepeatMode
import com.theartist.music.model.SortOrder
import com.theartist.music.model.Track
import com.theartist.music.platform.AudioPlayerController
import com.theartist.music.platform.LocalMusicScanner
import com.theartist.music.platform.decodeImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlayerStateHolder(
    private val scanner: LocalMusicScanner,
    private val audioController: AudioPlayerController,
    initialPermissionGranted: Boolean = true
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var playlistCounter = 1
    private val artworkCache = LinkedHashMap<String, ImageBitmap?>()

    suspend fun loadArtworkBitmap(track: Track): ImageBitmap? {
        if (artworkCache.containsKey(track.id)) {
            return artworkCache[track.id]
        }
        val bytes = track.artworkBytes ?: scanner.loadArtworkBytes(track)
        val bitmap = bytes?.let { decodeImageBitmap(it) }
        if (artworkCache.size >= 80) {
            val oldestKey = artworkCache.keys.firstOrNull()
            if (oldestKey != null) artworkCache.remove(oldestKey)
        }
        artworkCache[track.id] = bitmap
        return bitmap
    }

    private fun loadLyricsForCurrentTrack(track: Track) {
        if (!track.lyrics.isNullOrBlank()) return
        scope.launch {
            val loaded = scanner.loadLyrics(track) ?: return@launch
            _uiState.update { state ->
                state.copy(
                    allTracks = state.allTracks.map { if (it.id == track.id) it.copy(lyrics = loaded) else it },
                    queue = state.queue.map { if (it.id == track.id) it.copy(lyrics = loaded) else it }
                )
            }
        }
    }

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            hasAudioPermission = initialPermissionGranted,
            isDarkTheme = true,
            playlists = listOf(
                Playlist(id = "pl_fav_mix", name = "Mes favoris locaux"),
                Playlist(id = "pl_chill", name = "Ambiance Nuit")
            )
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        audioController.setOnTrackCompletedListener {
            scope.launch {
                onTrackCompleted()
            }
        }
        audioController.setOnTrackChangedListener { mediaId ->
            scope.launch {
                onExternalTrackChanged(mediaId)
            }
        }
        audioController.setOnPlayingStateChangedListener { playing ->
            scope.launch {
                _uiState.update { it.copy(isPlaying = playing) }
            }
        }
        audioController.setRepeatMode(_uiState.value.repeatMode)
        startProgressLoop()
        if (initialPermissionGranted) {
            refreshLibrary()
        }
    }

    private fun onExternalTrackChanged(mediaId: String) {
        val state = _uiState.value
        val newIndex = state.queue.indexOfFirst { it.id == mediaId }
        if (newIndex < 0) return
        val target = state.queue[newIndex]
        loadLyricsForCurrentTrack(target)
        _uiState.update {
            it.copy(
                currentQueueIndex = newIndex,
                positionMs = 0L,
                durationMs = target.durationMs,
                recentIds = (listOf(target.id) + it.recentIds.filterNot { id -> id == target.id }).take(30)
            )
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(hasAudioPermission = granted) }
        if (granted) {
            refreshLibrary()
        }
    }

    fun refreshLibrary() {
        scope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val tracks = scanner.scanLocalTracks()
                _uiState.update { state ->
                    val updatedQueue = if (state.queue.isEmpty() && tracks.isNotEmpty()) {
                        tracks
                    } else {
                        state.queue
                    }
                    val updatedIndex = if (state.currentQueueIndex == -1 && updatedQueue.isNotEmpty()) {
                        0
                    } else {
                        state.currentQueueIndex
                    }
                    val seededPlaylists = if (
                        state.playlists.all { it.trackIds.isEmpty() } && tracks.size >= 2
                    ) {
                        state.playlists.mapIndexed { idx, pl ->
                            val slice = if (idx == 0) tracks.take(4) else tracks.drop(2).take(4)
                            pl.copy(trackIds = slice.map { it.id })
                        }
                    } else {
                        state.playlists
                    }
                    val seededFavorites = if (state.favoriteIds.isEmpty() && tracks.isNotEmpty()) {
                        setOf(tracks.first().id)
                    } else {
                        state.favoriteIds
                    }
                    state.copy(
                        allTracks = tracks,
                        isScanning = false,
                        queue = updatedQueue,
                        currentQueueIndex = updatedIndex,
                        durationMs = updatedQueue.getOrNull(updatedIndex)?.durationMs ?: state.durationMs,
                        playlists = seededPlaylists,
                        favoriteIds = seededFavorites
                    )
                }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun selectTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedTab = tab, activeCollection = null) }
    }

    fun setSortOrder(order: SortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
    }

    fun cycleSortOrder() {
        _uiState.update { state ->
            val entries = SortOrder.entries
            val next = entries[(entries.indexOf(state.sortOrder) + 1) % entries.size]
            state.copy(sortOrder = next)
        }
    }

    fun openCollection(collection: QuickCollection?) {
        _uiState.update { it.copy(activeCollection = collection) }
    }

    fun toggleSearch(active: Boolean) {
        _uiState.update {
            it.copy(
                isSearchActive = active,
                searchQuery = if (!active) "" else it.searchQuery
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun openNowPlaying(open: Boolean) {
        _uiState.update { it.copy(isNowPlayingOpen = open) }
    }

    fun openQueueSheet(open: Boolean) {
        _uiState.update { it.copy(isQueueSheetOpen = open) }
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false,
        openPlayer: Boolean = true
    ) {
        if (tracks.isEmpty()) return
        val ordered = if (shuffle) tracks.shuffled() else tracks
        val safeIndex = if (shuffle) 0 else startIndex.coerceIn(0, ordered.lastIndex)
        val target = ordered[safeIndex]

        audioController.setQueueAndPlay(ordered, safeIndex)
        loadLyricsForCurrentTrack(target)
        _uiState.update { state ->
            state.copy(
                queue = ordered,
                currentQueueIndex = safeIndex,
                isPlaying = true,
                isNowPlayingOpen = if (openPlayer) true else state.isNowPlayingOpen,
                positionMs = 0L,
                durationMs = target.durationMs,
                isShuffleEnabled = if (shuffle) true else state.isShuffleEnabled,
                recentIds = (listOf(target.id) + state.recentIds.filterNot { it == target.id }).take(30)
            )
        }
    }

    fun playFromQueue(index: Int) {
        val state = _uiState.value
        val track = state.queue.getOrNull(index) ?: return
        audioController.setQueueAndPlay(state.queue, index)
        loadLyricsForCurrentTrack(track)
        _uiState.update {
            it.copy(
                currentQueueIndex = index,
                isPlaying = true,
                positionMs = 0L,
                durationMs = track.durationMs,
                recentIds = (listOf(track.id) + it.recentIds.filterNot { id -> id == track.id }).take(30)
            )
        }
    }

    fun playNextInQueue(track: Track) {
        _uiState.update { state ->
            if (state.queue.isEmpty()) {
                return@update state.copy(queue = listOf(track), currentQueueIndex = 0)
            }
            val insertIndex = (state.currentQueueIndex + 1).coerceIn(0, state.queue.size)
            val mutable = state.queue.toMutableList()
            mutable.add(insertIndex, track)
            state.copy(queue = mutable)
        }
    }

    fun removeFromQueue(index: Int) {
        val state = _uiState.value
        if (index !in state.queue.indices) return
        val mutable = state.queue.toMutableList()
        mutable.removeAt(index)

        if (mutable.isEmpty()) {
            audioController.pause()
            _uiState.update {
                it.copy(queue = emptyList(), currentQueueIndex = -1, isPlaying = false, positionMs = 0L, durationMs = 0L)
            }
            return
        }

        val newIndex = when {
            index < state.currentQueueIndex -> state.currentQueueIndex - 1
            index == state.currentQueueIndex -> index.coerceAtMost(mutable.lastIndex)
            else -> state.currentQueueIndex
        }
        val currentChanged = index == state.currentQueueIndex
        if (currentChanged && state.isPlaying) {
            audioController.playTrack(mutable[newIndex])
        }
        _uiState.update {
            it.copy(
                queue = mutable,
                currentQueueIndex = newIndex,
                durationMs = mutable[newIndex].durationMs,
                positionMs = if (currentChanged) 0L else it.positionMs
            )
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val current = state.currentTrack
        if (current == null) {
            if (state.allTracks.isNotEmpty()) {
                playTracks(state.allTracks, 0)
            }
            return
        }
        if (state.isPlaying) {
            audioController.pause()
            _uiState.update { it.copy(isPlaying = false) }
        } else {
            if (audioController.getDurationMs() <= 0L) {
                audioController.setQueueAndPlay(state.queue, state.currentQueueIndex)
                if (state.positionMs > 0L) {
                    audioController.seekTo(state.positionMs)
                }
            } else {
                audioController.resume()
            }
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(1L))
        audioController.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (audioController.skipNextNative()) {
            return
        }

        val nextIndex = when {
            state.isShuffleEnabled && state.queue.size > 1 -> {
                val candidates = state.queue.indices.filter { it != state.currentQueueIndex }
                candidates.random()
            }
            state.currentQueueIndex < state.queue.lastIndex -> state.currentQueueIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> {
                audioController.pause()
                _uiState.update { it.copy(isPlaying = false, positionMs = 0L) }
                return
            }
        }
        playFromQueue(nextIndex)
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return

        if (audioController.skipPreviousNative()) {
            return
        }

        if (state.positionMs > 3000L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (state.currentQueueIndex > 0) {
            state.currentQueueIndex - 1
        } else {
            state.queue.lastIndex
        }
        playFromQueue(prevIndex)
    }

    fun toggleShuffle() {
        val next = !_uiState.value.isShuffleEnabled
        audioController.setShuffleEnabled(next)
        _uiState.update { it.copy(isShuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        audioController.setRepeatMode(next)
        _uiState.update { it.copy(repeatMode = next) }
    }

    fun toggleFavorite(trackId: String) {
        _uiState.update { state ->
            val updated = if (trackId in state.favoriteIds) {
                state.favoriteIds - trackId
            } else {
                state.favoriteIds + trackId
            }
            state.copy(favoriteIds = updated)
        }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val id = "pl_custom_${playlistCounter++}"
        _uiState.update { state ->
            state.copy(playlists = state.playlists + Playlist(id = id, name = trimmed))
        }
    }

    fun deletePlaylist(playlistId: String) {
        _uiState.update { state ->
            val updatedCollection = if (
                state.activeCollection is QuickCollection.PlaylistDetail &&
                state.activeCollection.playlistId == playlistId
            ) null else state.activeCollection
            state.copy(
                playlists = state.playlists.filterNot { it.id == playlistId },
                activeCollection = updatedCollection
            )
        }
    }

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        _uiState.update { state ->
            state.copy(
                playlists = state.playlists.map { pl ->
                    if (pl.id == playlistId && trackId !in pl.trackIds) {
                        pl.copy(trackIds = pl.trackIds + trackId)
                    } else {
                        pl
                    }
                }
            )
        }
    }

    private fun onTrackCompleted() {
        val state = _uiState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                val current = state.currentTrack ?: return
                audioController.playTrack(current)
                _uiState.update { it.copy(positionMs = 0L, isPlaying = true) }
            }
            RepeatMode.ALL, RepeatMode.OFF -> skipNext()
        }
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                if (_uiState.value.isPlaying) {
                    val pos = audioController.getCurrentPositionMs()
                    val dur = audioController.getDurationMs().takeIf { it > 0L }
                        ?: _uiState.value.currentTrack?.durationMs
                        ?: 0L
                    _uiState.update {
                        it.copy(
                            positionMs = pos.coerceIn(0L, dur.coerceAtLeast(1L)),
                            durationMs = dur
                        )
                    }
                }
                delay(250L)
            }
        }
    }

    fun dispose() {
        progressJob?.cancel()
        audioController.release()
        scope.cancel()
    }
}

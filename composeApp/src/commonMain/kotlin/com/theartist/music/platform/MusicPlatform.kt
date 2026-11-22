package com.theartist.music.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.theartist.music.model.Playlist
import com.theartist.music.model.RepeatMode
import com.theartist.music.model.SortOrder
import com.theartist.music.model.Track

data class PersistedAppData(
    val favoriteIds: Set<String> = emptySet(),
    val playlists: List<Playlist> = emptyList(),
    val sortOrder: SortOrder = SortOrder.DATE_ADDED_DESC,
    val isDarkTheme: Boolean = true,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val lastTrackId: String? = null
)

interface LocalPreferencesStorage {
    fun load(): PersistedAppData
    fun save(data: PersistedAppData)
}

interface AudioPlayerController {
    fun playTrack(track: Track)
    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        tracks.getOrNull(startIndex)?.let { playTrack(it) }
    }
    /**
     * Replaces the upcoming/preceding items around the currently playing track
     * WITHOUT restarting or glitching audio playback.
     */
    fun updateQueueKeepingCurrent(tracks: List<Track>, currentIndex: Int) {}
    fun skipNextNative(): Boolean = false
    fun skipPreviousNative(): Boolean = false
    fun setShuffleEnabled(enabled: Boolean) {}
    fun setRepeatMode(mode: RepeatMode) {}
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun getCurrentPositionMs(): Long
    fun getDurationMs(): Long
    fun isCurrentlyPlaying(): Boolean
    fun setOnTrackCompletedListener(listener: () -> Unit)
    fun setOnTrackChangedListener(listener: (String) -> Unit) {}
    fun setOnPlayingStateChangedListener(listener: (Boolean) -> Unit) {}
    fun release()
}

interface LocalMusicScanner {
    suspend fun scanLocalTracks(): List<Track>
    suspend fun loadArtworkBytes(track: Track): ByteArray? = track.artworkBytes
    suspend fun loadLyrics(track: Track): String? = track.lyrics
}

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
expect fun appFontFamily(): FontFamily
expect fun brandDisplayFontFamily(): FontFamily

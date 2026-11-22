package com.theartist.music.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontFamily
import com.theartist.music.model.Track

interface AudioPlayerController {
    fun playTrack(track: Track)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun getCurrentPositionMs(): Long
    fun getDurationMs(): Long
    fun isCurrentlyPlaying(): Boolean
    fun setOnTrackCompletedListener(listener: () -> Unit)
    fun release()
}

interface LocalMusicScanner {
    suspend fun scanLocalTracks(): List<Track>
}

expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
expect fun appFontFamily(): FontFamily
expect fun brandDisplayFontFamily(): FontFamily

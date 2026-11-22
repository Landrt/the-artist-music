package com.theartist.music.platform

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.theartist.music.R
import com.theartist.music.model.Track
import com.theartist.music.model.accentColorForKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return runCatching {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}

actual fun appFontFamily(): FontFamily = FontFamily(
    Font(R.font.app_font_regular, FontWeight.Normal),
    Font(R.font.app_font_medium, FontWeight.Medium),
    Font(R.font.app_font_semibold, FontWeight.SemiBold),
    Font(R.font.app_font_bold, FontWeight.Bold)
)

actual fun brandDisplayFontFamily(): FontFamily = FontFamily(
    Font(R.font.brand_display, FontWeight.Bold)
)

class AndroidAudioPlayerController(
    context: Context
) : AudioPlayerController {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private var onCompleted: (() -> Unit)? = null

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    onCompleted?.invoke()
                }
            }
        })
    }

    override fun playTrack(track: Track) {
        val mediaItem = MediaItem.fromUri(Uri.parse(track.uri))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun resume() {
        exoPlayer.play()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
    }

    override fun getCurrentPositionMs(): Long {
        return exoPlayer.currentPosition.coerceAtLeast(0L)
    }

    override fun getDurationMs(): Long {
        return exoPlayer.duration.takeIf { it > 0L } ?: 0L
    }

    override fun isCurrentlyPlaying(): Boolean {
        return exoPlayer.isPlaying
    }

    override fun setOnTrackCompletedListener(listener: () -> Unit) {
        onCompleted = listener
    }

    override fun release() {
        exoPlayer.release()
    }
}

class AndroidLocalMusicScanner(
    private val context: Context
) : LocalMusicScanner {

    override suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Track>()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Titre inconnu"
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Artiste inconnu" else rawArtist
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") "Album inconnu" else rawAlbum
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null

                    val folderName = extractFolderName(relPath, filePath)
                    val displayFolderPath = relPath
                        ?.trimEnd('/')
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "Mémoire interne partagée/$it" }
                        ?: "Mémoire interne partagée/Music/$folderName"
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val artwork = loadEmbeddedArt(contentUri)
                    val sidecarLyrics = readSidecarLrc(filePath)
                    val formatLabel = mime?.substringAfter('/')?.uppercase() ?: "HQ"

                    results += Track(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        folderName = folderName,
                        folderPath = displayFolderPath,
                        durationMs = duration,
                        uri = contentUri.toString(),
                        accentColorHex = accentColorForKey("$album-$artist"),
                        artworkBytes = artwork,
                        lyrics = sidecarLyrics,
                        formatInfo = formatLabel
                    )
                }
            }
        }
        results
    }

    private fun extractFolderName(relativePath: String?, filePath: String?): String {
        val fromRel = relativePath?.trimEnd('/')?.substringAfterLast('/')
        if (!fromRel.isNullOrBlank()) return fromRel
        val fromFile = filePath?.let { File(it).parentFile?.name }
        if (!fromFile.isNullOrBlank()) return fromFile
        return "Musique"
    }

    private fun loadEmbeddedArt(uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun readSidecarLrc(filePath: String?): String? {
        if (filePath.isNullOrBlank()) return null
        return runCatching {
            val audioFile = File(filePath)
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (lrcFile.exists() && lrcFile.canRead()) {
                lrcFile.readText(Charsets.UTF_8)
                    .lines()
                    .map { it.replace(Regex("\\[\\d+:\\d+(?:\\.\\d+)?]"), "").trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("\n")
            } else {
                null
            }
        }.getOrNull()
    }
}

package com.theartist.music.platform

import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.theartist.music.R
import com.theartist.music.model.Track
import com.theartist.music.model.accentColorForKey
import com.theartist.music.service.MusicPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSide / sample > 512) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)?.asImageBitmap()
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
    context: Context,
    private val scanner: AndroidLocalMusicScanner
) : AudioPlayerController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, MusicPlaybackService::class.java)
    )
    private val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
    private var controller: MediaController? = null
    private var pendingTrack: Track? = null
    private var onCompleted: (() -> Unit)? = null

    init {
        controllerFuture.addListener(
            {
                runCatching {
                    val ctrl = controllerFuture.get()
                    controller = ctrl
                    ctrl.addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onCompleted?.invoke()
                            }
                        }
                    })
                    pendingTrack?.let { track ->
                        pendingTrack = null
                        startTrackOnController(ctrl, track)
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    override fun playTrack(track: Track) {
        val ctrl = controller
        if (ctrl == null) {
            pendingTrack = track
            return
        }
        startTrackOnController(ctrl, track)
    }

    private fun startTrackOnController(ctrl: MediaController, track: Track) {
        scope.launch {
            val artBytes = withTimeoutOrNull(180L) {
                scanner.loadArtworkBytes(track)
            }
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
            if (artBytes != null && artBytes.isNotEmpty()) {
                metadataBuilder.setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
            }
            val mediaItem = MediaItem.Builder()
                .setMediaId(track.id)
                .setUri(Uri.parse(track.uri))
                .setMediaMetadata(metadataBuilder.build())
                .build()

            ctrl.setMediaItem(mediaItem)
            ctrl.prepare()
            ctrl.playWhenReady = true
        }
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun getCurrentPositionMs(): Long {
        return controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
    }

    override fun getDurationMs(): Long {
        val d = controller?.duration ?: 0L
        return if (d > 0L) d else 0L
    }

    override fun isCurrentlyPlaying(): Boolean {
        val ctrl = controller ?: return false
        return ctrl.playWhenReady && ctrl.playbackState != Player.STATE_ENDED && ctrl.playbackState != Player.STATE_IDLE
    }

    override fun setOnTrackCompletedListener(listener: () -> Unit) {
        onCompleted = listener
    }

    override fun release() {
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }
}

class AndroidLocalMusicScanner(
    private val context: Context
) : LocalMusicScanner {

    private val excludedPathKeywords = listOf(
        "whatsapp",
        "voice note",
        "voicenote",
        "voice_note",
        "recordings",
        "recording",
        "soundrecorder",
        "sound_recorder",
        "voicerecorder",
        "voice_recorder",
        "callrecord",
        "call_record",
        "call recording",
        "dictaphone",
        "audionote",
        "/record/",
        "/records/",
        "/alarms/",
        "/ringtones/",
        "/notifications/",
        "ptt-",
        "aud-"
    )

    private val excludedExtensions = setOf("amr", "3gp", "3gpp", "awb", "opus")

    override suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Track>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        // Only real songs >= 30 seconds, excluding ringtones, alarms, notifications
        val selection = "${MediaStore.Audio.Media.DURATION} >= 30000 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        runCatching {
            context.contentResolver.query(
                collection,
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
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val filePath = if (dataCol >= 0) cursor.getString(dataCol) else null
                    val relPath = if (relPathCol >= 0) cursor.getString(relPathCol) else null
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Titre inconnu"

                    if (isExcludedVoiceOrWhatsApp(filePath, relPath, title)) {
                        continue
                    }

                    val id = cursor.getLong(idCol)
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (rawArtist.isNullOrBlank() || rawArtist == "<unknown>") "Artiste inconnu" else rawArtist
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (rawAlbum.isNullOrBlank() || rawAlbum == "<unknown>") "Album inconnu" else rawAlbum
                    val duration = cursor.getLong(durationCol).coerceAtLeast(0L)
                    val dateAddedSec = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) else 0L
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) else null

                    val folderName = extractFolderName(relPath, filePath)
                    val displayFolderPath = relPath
                        ?.trimEnd('/')
                        ?.takeIf { it.isNotBlank() }
                        ?.let { "Mémoire interne partagée/$it" }
                        ?: filePath?.let { File(it).parentFile?.path }
                        ?: "Mémoire interne partagée/Music/$folderName"

                    val contentUri = ContentUris.withAppendedId(collection, id)
                    val formatLabel = mime?.substringAfter('/')?.uppercase() ?: "HQ"

                    results += Track(
                        id = id.toString(),
                        title = title,
                        artist = artist,
                        album = album,
                        folderName = folderName,
                        folderPath = displayFolderPath,
                        filePath = filePath,
                        durationMs = duration,
                        dateAddedSec = dateAddedSec,
                        uri = contentUri.toString(),
                        accentColorHex = accentColorForKey("$album-$artist"),
                        artworkBytes = null,
                        lyrics = null,
                        formatInfo = formatLabel
                    )
                }
            }
        }
        results
    }

    private fun isExcludedVoiceOrWhatsApp(
        filePath: String?,
        relPath: String?,
        title: String
    ): Boolean {
        val combined = "${relPath.orEmpty()} ${filePath.orEmpty()} $title".lowercase()
        if (excludedPathKeywords.any { it in combined }) {
            return true
        }
        val ext = filePath?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext in excludedExtensions) {
            return true
        }
        return false
    }

    override suspend fun loadArtworkBytes(track: Track): ByteArray? = withContext(Dispatchers.IO) {
        val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return@withContext null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumbBytes = runCatching {
                val bmp = context.contentResolver.loadThumbnail(uri, Size(400, 400), null)
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                bmp.recycle()
                out.toByteArray()
            }.getOrNull()
            if (thumbBytes != null && thumbBytes.isNotEmpty()) {
                return@withContext thumbBytes
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    override suspend fun loadLyrics(track: Track): String? = withContext(Dispatchers.IO) {
        val filePath = track.filePath ?: return@withContext null
        runCatching {
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

    private fun extractFolderName(relativePath: String?, filePath: String?): String {
        val fromRel = relativePath?.trimEnd('/')?.substringAfterLast('/')
        if (!fromRel.isNullOrBlank()) return fromRel
        val fromFile = filePath?.let { File(it).parentFile?.name }
        if (!fromFile.isNullOrBlank()) return fromFile
        return "Musique"
    }
}

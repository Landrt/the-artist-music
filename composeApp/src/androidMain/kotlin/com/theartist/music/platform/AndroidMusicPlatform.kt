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
import com.theartist.music.model.Playlist
import com.theartist.music.model.RepeatMode
import com.theartist.music.model.SortOrder
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
import org.json.JSONArray
import org.json.JSONObject
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

class AndroidPreferencesStorage(
    context: Context
) : LocalPreferencesStorage {

    private val prefs = context.applicationContext.getSharedPreferences(
        "the_artist_music_prefs",
        Context.MODE_PRIVATE
    )

    override fun load(): PersistedAppData {
        return runCatching {
            val favs = prefs.getStringSet("favorite_ids", emptySet()).orEmpty().toSet()
            val sortName = prefs.getString("sort_order", SortOrder.DATE_ADDED_DESC.name)
            val sortOrder = SortOrder.entries.firstOrNull { it.name == sortName } ?: SortOrder.DATE_ADDED_DESC
            val repeatName = prefs.getString("repeat_mode", RepeatMode.ALL.name)
            val repeatMode = RepeatMode.entries.firstOrNull { it.name == repeatName } ?: RepeatMode.ALL
            val isDark = prefs.getBoolean("is_dark_theme", true)
            val isShuffle = prefs.getBoolean("is_shuffle_enabled", false)
            val lastTrackId = prefs.getString("last_track_id", null)

            val playlistsJson = prefs.getString("playlists_json", null)
            val playlists = if (!playlistsJson.isNullOrBlank()) {
                parsePlaylists(playlistsJson)
            } else {
                emptyList()
            }

            PersistedAppData(
                favoriteIds = favs,
                playlists = playlists,
                sortOrder = sortOrder,
                isDarkTheme = isDark,
                isShuffleEnabled = isShuffle,
                repeatMode = repeatMode,
                lastTrackId = lastTrackId
            )
        }.getOrDefault(PersistedAppData())
    }

    override fun save(data: PersistedAppData) {
        runCatching {
            prefs.edit()
                .putStringSet("favorite_ids", data.favoriteIds)
                .putString("playlists_json", serializePlaylists(data.playlists))
                .putString("sort_order", data.sortOrder.name)
                .putString("repeat_mode", data.repeatMode.name)
                .putBoolean("is_dark_theme", data.isDarkTheme)
                .putBoolean("is_shuffle_enabled", data.isShuffleEnabled)
                .putString("last_track_id", data.lastTrackId)
                .apply()
        }
    }

    private fun serializePlaylists(playlists: List<Playlist>): String {
        val arr = JSONArray()
        playlists.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            val ids = JSONArray()
            pl.trackIds.forEach { ids.put(it) }
            obj.put("trackIds", ids)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parsePlaylists(raw: String): List<Playlist> {
        val arr = JSONArray(raw)
        val out = mutableListOf<Playlist>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) continue
            val idsArr = obj.optJSONArray("trackIds")
            val ids = mutableListOf<String>()
            if (idsArr != null) {
                for (j in 0 until idsArr.length()) {
                    val tId = idsArr.optString(j)
                    if (tId.isNotBlank()) ids += tId
                }
            }
            out += Playlist(id = id, name = name, trackIds = ids)
        }
        return out
    }
}

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
    private var pendingQueue: Pair<List<Track>, Int>? = null
    private var fullQueue: List<Track> = emptyList()
    private var windowEndGlobalIndex: Int = 0
    private var desiredRepeat: RepeatMode = RepeatMode.ALL
    private var onCompleted: (() -> Unit)? = null
    private var onTrackChanged: ((String) -> Unit)? = null
    private var onPlayingStateChanged: ((Boolean) -> Unit)? = null

    init {
        controllerFuture.addListener(
            {
                runCatching {
                    val ctrl = controllerFuture.get()
                    controller = ctrl
                    // SmartShuffleEngine owns the exact queue order, so keep ExoPlayer linear
                    ctrl.shuffleModeEnabled = false
                    ctrl.repeatMode = toExoRepeatMode(desiredRepeat)

                    ctrl.addListener(object : Player.Listener {
                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            val id = mediaItem?.mediaId ?: return
                            onTrackChanged?.invoke(id)
                            maybeAppendMoreTracks(ctrl)
                        }

                        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                            onPlayingStateChanged?.invoke(playWhenReady)
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            val active = isPlaying || (ctrl.playWhenReady && ctrl.playbackState == Player.STATE_BUFFERING)
                            onPlayingStateChanged?.invoke(active)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onPlayingStateChanged?.invoke(false)
                                onCompleted?.invoke()
                            }
                        }
                    })

                    val queued = pendingQueue
                    if (queued != null) {
                        pendingQueue = null
                        startQueueOnController(ctrl, queued.first, queued.second)
                    } else {
                        // Re-attach cleanly if MusicPlaybackService is already playing in background
                        val activeId = ctrl.currentMediaItem?.mediaId
                        if (activeId != null) {
                            onTrackChanged?.invoke(activeId)
                            onPlayingStateChanged?.invoke(ctrl.isPlaying || ctrl.playWhenReady)
                        }
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    override fun playTrack(track: Track) {
        setQueueAndPlay(listOf(track), 0)
    }

    override fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val safeIdx = startIndex.coerceIn(0, tracks.lastIndex)
        fullQueue = tracks
        val ctrl = controller
        if (ctrl == null) {
            pendingQueue = tracks to safeIdx
            return
        }
        startQueueOnController(ctrl, tracks, safeIdx)
    }

    override fun updateQueueKeepingCurrent(tracks: List<Track>, currentIndex: Int) {
        fullQueue = tracks
        val ctrl = controller ?: return
        if (tracks.isEmpty() || currentIndex !in tracks.indices) return

        val playingIdx = ctrl.currentMediaItemIndex
        val playingId = ctrl.currentMediaItem?.mediaId
        val targetTrack = tracks[currentIndex]

        // Only mutate in-place if the same track is currently loaded in ExoPlayer
        if (playingIdx < 0 || playingId != targetTrack.id) {
            return
        }

        // Remove items after current, then items before current (current stays untouched at index 0!)
        if (playingIdx + 1 < ctrl.mediaItemCount) {
            ctrl.removeMediaItems(playingIdx + 1, ctrl.mediaItemCount)
        }
        if (playingIdx > 0) {
            ctrl.removeMediaItems(0, playingIdx)
        }

        val beforeStart = (currentIndex - 50).coerceAtLeast(0)
        val afterEnd = (currentIndex + 101).coerceAtMost(tracks.size)
        windowEndGlobalIndex = afterEnd

        val beforeItems = tracks.subList(beforeStart, currentIndex).map { buildMediaItem(it, null) }
        val afterItems = tracks.subList(currentIndex + 1, afterEnd).map { buildMediaItem(it, null) }

        if (beforeItems.isNotEmpty()) {
            ctrl.addMediaItems(0, beforeItems)
        }
        if (afterItems.isNotEmpty()) {
            ctrl.addMediaItems(afterItems)
        }
        ctrl.shuffleModeEnabled = false
        ctrl.repeatMode = toExoRepeatMode(desiredRepeat)
    }

    private fun startQueueOnController(
        ctrl: MediaController,
        tracks: List<Track>,
        startIndex: Int
    ) {
        val fromIndex = (startIndex - 60).coerceAtLeast(0)
        val toIndex = (startIndex + 120).coerceAtMost(tracks.size)
        windowEndGlobalIndex = toIndex
        val windowTracks = tracks.subList(fromIndex, toIndex)
        val localStartIndex = startIndex - fromIndex
        val activeTrack = windowTracks[localStartIndex]

        scope.launch {
            val activeArtBytes = withTimeoutOrNull(180L) {
                scanner.loadArtworkBytes(activeTrack)
            }

            val mediaItems = windowTracks.mapIndexed { idx, track ->
                buildMediaItem(
                    track = track,
                    embeddedArt = if (idx == localStartIndex) activeArtBytes else null
                )
            }

            ctrl.shuffleModeEnabled = false
            ctrl.repeatMode = toExoRepeatMode(desiredRepeat)
            ctrl.setMediaItems(mediaItems, localStartIndex, 0L)
            ctrl.prepare()
            ctrl.playWhenReady = true
        }
    }

    private fun maybeAppendMoreTracks(ctrl: MediaController) {
        if (fullQueue.isEmpty() || windowEndGlobalIndex >= fullQueue.size) return
        val remainingInWindow = ctrl.mediaItemCount - 1 - ctrl.currentMediaItemIndex
        if (remainingInWindow <= 25) {
            val nextEnd = (windowEndGlobalIndex + 80).coerceAtMost(fullQueue.size)
            val nextBatch = fullQueue.subList(windowEndGlobalIndex, nextEnd)
                .map { buildMediaItem(it, null) }
            windowEndGlobalIndex = nextEnd
            if (nextBatch.isNotEmpty()) {
                ctrl.addMediaItems(nextBatch)
            }
        }
    }

    private fun buildMediaItem(track: Track, embeddedArt: ByteArray?): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)

        track.albumId?.let { albId ->
            val artUri = ContentUris.withAppendedId(ALBUM_ART_URI, albId)
            metadataBuilder.setArtworkUri(artUri)
        }

        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
            metadataBuilder.setArtworkData(embeddedArt, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse(track.uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    override fun skipNextNative(): Boolean {
        val ctrl = controller ?: return false
        if (ctrl.mediaItemCount == 0) return false
        if (ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
            ctrl.playWhenReady = true
            return true
        }
        if (desiredRepeat == RepeatMode.ALL && ctrl.mediaItemCount > 0) {
            ctrl.seekTo(0, 0L)
            ctrl.playWhenReady = true
            return true
        }
        return false
    }

    override fun skipPreviousNative(): Boolean {
        val ctrl = controller ?: return false
        if (ctrl.mediaItemCount == 0) return false
        ctrl.seekToPrevious()
        ctrl.playWhenReady = true
        return true
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        // SmartShuffleEngine reorders the queue directly; ExoPlayer stays linear so UI & Dynamic Island match 100%
        controller?.shuffleModeEnabled = false
    }

    override fun setRepeatMode(mode: RepeatMode) {
        desiredRepeat = mode
        controller?.repeatMode = toExoRepeatMode(mode)
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

    override fun setOnTrackChangedListener(listener: (String) -> Unit) {
        onTrackChanged = listener
    }

    override fun setOnPlayingStateChangedListener(listener: (Boolean) -> Unit) {
        onPlayingStateChanged = listener
    }

    override fun release() {
        scope.cancel()
        MediaController.releaseFuture(controllerFuture)
        controller = null
    }

    private fun toExoRepeatMode(mode: RepeatMode): Int = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    }

    companion object {
        private val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")
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
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

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
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
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
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else null
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
                        albumId = albumId,
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

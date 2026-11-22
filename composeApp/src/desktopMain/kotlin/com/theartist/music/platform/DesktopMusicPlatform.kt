package com.theartist.music.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as DesktopFont
import com.theartist.music.model.Track
import com.theartist.music.model.accentColorForKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? {
    return runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}

actual fun appFontFamily(): FontFamily = FontFamily(
    DesktopFont("fonts/app_font_regular.ttf", FontWeight.Normal),
    DesktopFont("fonts/app_font_medium.ttf", FontWeight.Medium),
    DesktopFont("fonts/app_font_semibold.ttf", FontWeight.SemiBold),
    DesktopFont("fonts/app_font_bold.ttf", FontWeight.Bold)
)

actual fun brandDisplayFontFamily(): FontFamily = FontFamily(
    DesktopFont("fonts/brand_display.ttf", FontWeight.Bold)
)

class DesktopAudioPlayerController : AudioPlayerController {
    private var clip: Clip? = null
    private var currentDurationMs: Long = 0L
    private var accumulatedMs: Long = 0L
    private var playStartWallMs: Long = 0L
    private var playing: Boolean = false
    private var completedDispatched: Boolean = false
    private var onCompleted: (() -> Unit)? = null

    override fun playTrack(track: Track) {
        stopClip()
        currentDurationMs = track.durationMs.coerceAtLeast(1000L)
        accumulatedMs = 0L
        completedDispatched = false

        val file = if (track.uri.startsWith("file://")) {
            File(track.uri.removePrefix("file://"))
        } else {
            null
        }

        if (file != null && file.exists() && file.extension.equals("wav", ignoreCase = true)) {
            runCatching {
                val stream = AudioSystem.getAudioInputStream(file)
                val newClip = AudioSystem.getClip()
                newClip.open(stream)
                currentDurationMs = (newClip.microsecondLength / 1000L).coerceAtLeast(1000L)
                newClip.start()
                clip = newClip
            }
        }

        playStartWallMs = System.currentTimeMillis()
        playing = true
    }

    override fun pause() {
        if (!playing) return
        accumulatedMs = computePositionInternal()
        clip?.stop()
        playing = false
    }

    override fun resume() {
        if (playing) return
        clip?.let { activeClip ->
            activeClip.microsecondPosition = (accumulatedMs * 1000L).coerceIn(0L, activeClip.microsecondLength)
            activeClip.start()
        }
        playStartWallMs = System.currentTimeMillis()
        playing = true
    }

    override fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, currentDurationMs.coerceAtLeast(1L))
        accumulatedMs = clamped
        playStartWallMs = System.currentTimeMillis()
        completedDispatched = false
        clip?.let { activeClip ->
            activeClip.microsecondPosition = (clamped * 1000L).coerceIn(0L, activeClip.microsecondLength)
        }
    }

    override fun getCurrentPositionMs(): Long {
        val pos = computePositionInternal()
        if (playing && currentDurationMs > 0L && pos >= currentDurationMs && !completedDispatched) {
            completedDispatched = true
            playing = false
            stopClip()
            onCompleted?.invoke()
            return currentDurationMs
        }
        return pos.coerceIn(0L, currentDurationMs.coerceAtLeast(1L))
    }

    override fun getDurationMs(): Long = currentDurationMs

    override fun isCurrentlyPlaying(): Boolean = playing

    override fun setOnTrackCompletedListener(listener: () -> Unit) {
        onCompleted = listener
    }

    override fun release() {
        playing = false
        stopClip()
    }

    private fun computePositionInternal(): Long {
        val activeClip = clip
        if (activeClip != null && activeClip.isOpen) {
            return activeClip.microsecondPosition / 1000L
        }
        return if (playing) {
            accumulatedMs + (System.currentTimeMillis() - playStartWallMs)
        } else {
            accumulatedMs
        }
    }

    private fun stopClip() {
        runCatching {
            clip?.stop()
            clip?.close()
        }
        clip = null
    }
}

class DesktopLocalMusicScanner : LocalMusicScanner {
    private val supportedExtensions = setOf("mp3", "wav", "flac", "ogg", "m4a", "aac")

    override suspend fun scanLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val home = System.getProperty("user.home").orEmpty()
        val candidateDirs = listOf(File(home, "Music"), File(home, "Musique"))
            .filter { it.exists() && it.isDirectory }

        val discovered = mutableListOf<Track>()
        for (root in candidateDirs) {
            root.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile && it.extension.lowercase() in supportedExtensions }
                .take(200)
                .forEach { file ->
                    val folder = file.parentFile?.name ?: "Musique"
                    val baseName = file.nameWithoutExtension
                    val parts = baseName.split(" - ", limit = 2)
                    val artist = if (parts.size == 2) parts[0].trim() else "Artiste local"
                    val title = if (parts.size == 2) parts[1].trim() else baseName
                    val lrcFile = File(file.parentFile, "$baseName.lrc")
                    val lyrics = if (lrcFile.exists() && lrcFile.canRead()) {
                        runCatching {
                            lrcFile.readText(Charsets.UTF_8)
                                .lines()
                                .map { it.replace(Regex("\\[\\d+:\\d+(?:\\.\\d+)?]"), "").trim() }
                                .filter { it.isNotEmpty() }
                                .joinToString("\n")
                        }.getOrNull()
                    } else null

                    discovered += Track(
                        id = file.absolutePath,
                        title = title,
                        artist = artist,
                        album = folder,
                        folderName = folder,
                        folderPath = file.parentFile?.absolutePath ?: "Mémoire interne partagée/Music/$folder",
                        durationMs = 210_000L,
                        uri = "file://${file.absolutePath}",
                        accentColorHex = accentColorForKey("$folder-$artist"),
                        lyrics = lyrics,
                        formatInfo = file.extension.uppercase()
                    )
                }
        }

        if (discovered.isNotEmpty()) discovered else samplePreviewTracks()
    }

    private fun samplePreviewTracks(): List<Track> = listOf(
        Track(
            id = "demo_1",
            title = "Minuit sur la Ville",
            artist = "The @rtist",
            album = "Nuits Électriques",
            folderName = "Studio Sessions",
            durationMs = 214_000L,
            uri = "demo://1",
            accentColorHex = 0xFFFF1949L,
            formatInfo = "FLAC • Preview",
            lyrics = "Les néons s'allument sur le boulevard\nLe rythme monte dans le brouillard\nChaque note résonne hors-ligne\nDans le silence d'une nuit divine."
        ),
        Track(
            id = "demo_2",
            title = "Horizon Doré",
            artist = "Lina Sol",
            album = "Éclats d'Été",
            folderName = "Favoris Locaux",
            durationMs = 198_000L,
            uri = "demo://2",
            accentColorHex = 0xFF317AF7L,
            formatInfo = "MP3 320k • Preview",
            lyrics = "Sous la lumière du matin clair\nLa mélodie flotte dans l'air\nPas besoin de réseau pour vibrer\nJuste le son pur à écouter."
        ),
        Track(
            id = "demo_3",
            title = "Onde Nocturne",
            artist = "Kaelen Beats",
            album = "Nuits Électriques",
            folderName = "Studio Sessions",
            durationMs = 242_000L,
            uri = "demo://3",
            accentColorHex = 0xFF7B4DFFL,
            formatInfo = "FLAC • Preview"
        ),
        Track(
            id = "demo_4",
            title = "Écho Acoustique",
            artist = "The @rtist",
            album = "Unplugged Vol. 1",
            folderName = "Acoustique",
            durationMs = 186_000L,
            uri = "demo://4",
            accentColorHex = 0xFF00A896L,
            formatInfo = "WAV • Preview"
        ),
        Track(
            id = "demo_5",
            title = "Vitesse Lumière",
            artist = "Nova Pulse",
            album = "Run & Focus",
            folderName = "Sport",
            durationMs = 205_000L,
            uri = "demo://5",
            accentColorHex = 0xFFFF6B35L,
            formatInfo = "MP3 • Preview"
        ),
        Track(
            id = "demo_6",
            title = "Pluie d'Octobre",
            artist = "Lina Sol",
            album = "Unplugged Vol. 1",
            folderName = "Acoustique",
            durationMs = 229_000L,
            uri = "demo://6",
            accentColorHex = 0xFF2B6CB0L,
            formatInfo = "M4A • Preview"
        ),
        Track(
            id = "demo_7",
            title = "Sérénade Urbaine",
            artist = "Kaelen Beats",
            album = "Éclats d'Été",
            folderName = "Favoris Locaux",
            durationMs = 176_000L,
            uri = "demo://7",
            accentColorHex = 0xFFD946EFL,
            formatInfo = "MP3 • Preview"
        )
    )
}

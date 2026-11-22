package com.theartist.music.model

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class SortOrder(val label: String) {
    TITLE_ASC("Titre (A-Z)"),
    ARTIST_ASC("Artiste (A-Z)"),
    DURATION_DESC("Durée")
}

enum class LibraryTab(val label: String) {
    SONGS("Morceaux"),
    ARTISTS("Artistes"),
    ALBUMS("Albums"),
    FOLDERS("Dossiers")
}

sealed interface QuickCollection {
    data object Favorites : QuickCollection
    data object Recent : QuickCollection
    data object PlaylistsOverview : QuickCollection
    data class AlbumDetail(val albumName: String) : QuickCollection
    data class ArtistDetail(val artistName: String) : QuickCollection
    data class FolderDetail(val folderName: String) : QuickCollection
    data class PlaylistDetail(val playlistId: String) : QuickCollection
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val folderName: String,
    val folderPath: String = "Mémoire interne partagée/Music/$folderName",
    val filePath: String? = null,
    val durationMs: Long,
    val uri: String,
    val accentColorHex: Long,
    val artworkBytes: ByteArray? = null,
    val lyrics: String? = null,
    val formatInfo: String = "HQ"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Track) return false
        return id == other.id &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            folderName == other.folderName &&
            folderPath == other.folderPath &&
            durationMs == other.durationMs &&
            uri == other.uri &&
            accentColorHex == other.accentColorHex &&
            lyrics == other.lyrics &&
            formatInfo == other.formatInfo &&
            (artworkBytes == null && other.artworkBytes == null ||
                (artworkBytes != null && other.artworkBytes != null && artworkBytes.contentEquals(other.artworkBytes)))
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + folderName.hashCode()
        result = 31 * result + folderPath.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + uri.hashCode()
        result = 31 * result + accentColorHex.hashCode()
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + (lyrics?.hashCode() ?: 0)
        result = 31 * result + formatInfo.hashCode()
        return result
    }
}

data class Playlist(
    val id: String,
    val name: String,
    val trackIds: List<String> = emptyList()
)

data class PlayerUiState(
    val allTracks: List<Track> = emptyList(),
    val isScanning: Boolean = false,
    val hasAudioPermission: Boolean = true,
    val selectedTab: LibraryTab = LibraryTab.SONGS,
    val sortOrder: SortOrder = SortOrder.TITLE_ASC,
    val activeCollection: QuickCollection? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isDarkTheme: Boolean = true,
    val favoriteIds: Set<String> = emptySet(),
    val recentIds: List<String> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isNowPlayingOpen: Boolean = false,
    val isQueueSheetOpen: Boolean = false
) {
    val currentTrack: Track?
        get() = queue.getOrNull(currentQueueIndex)
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val secStr = if (seconds < 10L) "0$seconds" else "$seconds"
    return "$minutes:$secStr"
}

private val AccentPalette = listOf(
    0xFFF81E49L,
    0xFF4E6EF2L,
    0xFF8B5CF6L,
    0xFF00A896L,
    0xFFD97706L,
    0xFFDC2626L,
    0xFF2563EBL,
    0xFFDB2777L
)

fun accentColorForKey(key: String): Long {
    val idx = (key.hashCode().let { if (it < 0) -it else it }) % AccentPalette.size
    return AccentPalette[idx]
}

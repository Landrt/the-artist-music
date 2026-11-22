package com.theartist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theartist.music.model.LibraryTab
import com.theartist.music.model.PlayerUiState
import com.theartist.music.model.Playlist
import com.theartist.music.model.QuickCollection
import com.theartist.music.model.SortOrder
import com.theartist.music.model.Track
import com.theartist.music.model.formatDuration
import com.theartist.music.ui.components.ArtworkBox
import com.theartist.music.ui.components.FourDotsMenuIcon
import com.theartist.music.ui.components.OrbitLoadingIndicator
import com.theartist.music.ui.components.PlayingEqualizerBars
import com.theartist.music.ui.theme.ArtistCrimson
import com.theartist.music.ui.theme.MutedIconDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: PlayerUiState,
    bottomPaddingForMiniPlayer: Boolean,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectSortOrder: (SortOrder) -> Unit,
    onOpenCollection: (QuickCollection?) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleDarkTheme: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onPlayTracks: (List<Track>, Int, Boolean) -> Unit,
    onPlayNext: (Track) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddTrackToPlaylist: (String, String) -> Unit
) {
    var trackForOptions by remember { mutableStateOf<Track?>(null) }
    var trackForPlaylistPicker by remember { mutableStateOf<Track?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }

    val sortedAndFilteredTracks = remember(uiState.allTracks, uiState.searchQuery, uiState.sortOrder) {
        val q = uiState.searchQuery.trim()
        val filtered = if (q.isEmpty()) {
            uiState.allTracks
        } else {
            uiState.allTracks.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true) ||
                    it.folderName.contains(q, ignoreCase = true)
            }
        }
        when (uiState.sortOrder) {
            SortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAddedSec }
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> filtered.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> filtered.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION_DESC -> filtered.sortedByDescending { it.durationMs }
        }
    }

    val listBottomPadding = if (bottomPaddingForMiniPlayer) 72.dp else 16.dp
    val activeCollection = uiState.activeCollection

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top App Bar: Tous les morceaux (no back arrow on root)        [Search]  [::]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val showBackArrow = uiState.isSearchActive || activeCollection != null
            if (showBackArrow) {
                IconButton(
                    onClick = {
                        when {
                            uiState.isSearchActive -> onToggleSearch(false)
                            activeCollection != null -> onOpenCollection(null)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Retour",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }

            if (uiState.isSearchActive) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (uiState.searchQuery.isEmpty()) {
                        Text(
                            text = "Rechercher un morceau, artiste...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 15.sp
                        ),
                        cursorBrush = SolidColor(ArtistCrimson),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                IconButton(onClick = { onToggleSearch(false) }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Fermer recherche",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            } else {
                val barTitle = when (activeCollection) {
                    null -> "Tous les morceaux"
                    QuickCollection.Favorites -> "Favoris"
                    QuickCollection.Recent -> "Écoutés récemment"
                    QuickCollection.PlaylistsOverview -> "Playlists"
                    is QuickCollection.AlbumDetail -> activeCollection.albumName
                    is QuickCollection.ArtistDetail -> activeCollection.artistName
                    is QuickCollection.FolderDetail -> activeCollection.folderName
                    is QuickCollection.PlaylistDetail -> {
                        uiState.playlists.firstOrNull { it.id == activeCollection.playlistId }?.name ?: "Playlist"
                    }
                }

                Text(
                    text = barTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if (showBackArrow) 6.dp else 12.dp)
                )

                IconButton(onClick = { onToggleSearch(true) }) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Rechercher",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(25.dp)
                    )
                }

                Box {
                    IconButton(onClick = { showTopMenu = true }) {
                        FourDotsMenuIcon(tint = MaterialTheme.colorScheme.onBackground)
                    }
                    DropdownMenu(
                        expanded = showTopMenu,
                        onDismissRequest = { showTopMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Favoris (${uiState.favoriteIds.size})") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Favorite, contentDescription = null, tint = ArtistCrimson)
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenCollection(QuickCollection.Favorites)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Playlists (${uiState.playlists.size})") },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, contentDescription = null)
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenCollection(QuickCollection.PlaylistsOverview)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Récents (${uiState.recentIds.size})") },
                            leadingIcon = {
                                Icon(Icons.Rounded.History, contentDescription = null)
                            },
                            onClick = {
                                showTopMenu = false
                                onOpenCollection(QuickCollection.Recent)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Scanner la musique locale") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Refresh, contentDescription = null)
                            },
                            onClick = {
                                showTopMenu = false
                                onRefreshLibrary()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (uiState.isDarkTheme) "Mode clair" else "Mode sombre OLED") },
                            leadingIcon = {
                                Icon(
                                    if (uiState.isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                showTopMenu = false
                                onToggleDarkTheme()
                            }
                        )
                    }
                }
            }
        }

        // Audio permission prompt on Android if needed
        if (!uiState.hasAudioPermission) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Accès aux fichiers audio requis",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Autorise l'accès pour lister tes morceaux locaux.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = onRequestAudioPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = ArtistCrimson)
                    ) {
                        Text("Autoriser", color = Color.White)
                    }
                }
            }
        }

        // Exact 4-Tab Bar (only on main view)
        if (activeCollection == null) {
            UnderlinedTabsBar(
                selectedTab = uiState.selectedTab,
                onSelectTab = onSelectTab
            )
        }

        // Loading State matching Screenshot 2
        if (uiState.isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    OrbitLoadingIndicator(size = 58.dp)
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Chargement en cours",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                    )
                }
            }
            return@Column
        }

        // Playlists Overview collection
        if (activeCollection == QuickCollection.PlaylistsOverview) {
            PlaylistsCollectionScreen(
                playlists = uiState.playlists,
                bottomPadding = listBottomPadding,
                onCreateClick = { showCreatePlaylistDialog = true },
                onOpenPlaylist = { onOpenCollection(QuickCollection.PlaylistDetail(it)) },
                onDeletePlaylist = onDeletePlaylist
            )
        } else if (activeCollection != null) {
            val tracks = resolveCollectionTracks(activeCollection, uiState)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = listBottomPadding)
            ) {
                item(key = "play_all_sub") {
                    PlayAllHeaderRow(
                        count = tracks.size,
                        sortOrderLabel = uiState.sortOrder.label,
                        onPlayAll = { onPlayTracks(tracks, 0, false) },
                        onCycleSort = { showSortSheet = true },
                        onOpenPlaylists = { onOpenCollection(QuickCollection.PlaylistsOverview) }
                    )
                }
                if (tracks.isEmpty()) {
                    item { EmptyListHint("Aucun morceau dans cette liste") }
                } else {
                    itemsIndexed(tracks, key = { idx, t -> "${idx}_${t.id}" }) { index, track ->
                        SongRow(
                            track = track,
                            isCurrent = track.id == uiState.currentTrack?.id,
                            isPlaying = uiState.isPlaying,
                            onClick = { onPlayTracks(tracks, index, false) },
                            onAddToPlaylistClick = { trackForPlaylistPicker = track },
                            onMoreClick = { trackForOptions = track }
                        )
                    }
                }
            }
        } else {
            // Main 4 Tabs Content
            when (uiState.selectedTab) {
                LibraryTab.SONGS -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = listBottomPadding)
                    ) {
                        item(key = "play_all_header") {
                            PlayAllHeaderRow(
                                count = sortedAndFilteredTracks.size,
                                sortOrderLabel = uiState.sortOrder.label,
                                onPlayAll = { onPlayTracks(sortedAndFilteredTracks, 0, false) },
                                onCycleSort = { showSortSheet = true },
                                onOpenPlaylists = { onOpenCollection(QuickCollection.PlaylistsOverview) }
                            )
                        }
                        if (sortedAndFilteredTracks.isEmpty()) {
                            item { EmptyListHint("Aucun morceau local trouvé") }
                        } else {
                            itemsIndexed(
                                items = sortedAndFilteredTracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                SongRow(
                                    track = track,
                                    isCurrent = track.id == uiState.currentTrack?.id,
                                    isPlaying = uiState.isPlaying,
                                    onClick = { onPlayTracks(sortedAndFilteredTracks, index, false) },
                                    onAddToPlaylistClick = { trackForPlaylistPicker = track },
                                    onMoreClick = { trackForOptions = track }
                                )
                            }
                        }
                    }
                }

                LibraryTab.ARTISTS -> {
                    val artists = sortedAndFilteredTracks
                        .groupBy { it.artist }
                        .entries
                        .sortedBy { it.key.lowercase() }
                        .toList()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = listBottomPadding)
                    ) {
                        item(key = "artists_count") {
                            CountSubHeaderRow(
                                label = "${artists.size} ${if (artists.size > 1) "artistes" else "artiste"}",
                                onActionClick = { onOpenCollection(QuickCollection.PlaylistsOverview) }
                            )
                        }
                        if (artists.isEmpty()) {
                            item { EmptyListHint("Aucun artiste local") }
                        } else {
                            items(artists, key = { it.key }) { (artistName, artistTracks) ->
                                val rep = artistTracks.first()
                                ArtistRow(
                                    artistName = artistName,
                                    trackCount = artistTracks.size,
                                    repTrack = rep,
                                    onClick = { onOpenCollection(QuickCollection.ArtistDetail(artistName)) }
                                )
                            }
                        }
                    }
                }

                LibraryTab.ALBUMS -> {
                    val albums = sortedAndFilteredTracks
                        .groupBy { it.album }
                        .entries
                        .sortedBy { it.key.lowercase() }
                        .toList()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = listBottomPadding)
                    ) {
                        item(key = "albums_count") {
                            CountSubHeaderRow(
                                label = "${albums.size} ${if (albums.size > 1) "albums" else "album"}",
                                onActionClick = { onOpenCollection(QuickCollection.PlaylistsOverview) }
                            )
                        }
                        if (albums.isEmpty()) {
                            item { EmptyListHint("Aucun album local") }
                        } else {
                            items(albums, key = { it.key }) { (albumName, albumTracks) ->
                                val rep = albumTracks.first()
                                AlbumRow(
                                    albumName = albumName,
                                    artistName = rep.artist,
                                    trackCount = albumTracks.size,
                                    repTrack = rep,
                                    onClick = { onOpenCollection(QuickCollection.AlbumDetail(albumName)) }
                                )
                            }
                        }
                    }
                }

                LibraryTab.FOLDERS -> {
                    val folders = sortedAndFilteredTracks
                        .groupBy { it.folderName }
                        .entries
                        .sortedBy { it.key.lowercase() }
                        .toList()

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = listBottomPadding)
                    ) {
                        item(key = "folders_count") {
                            CountSubHeaderRow(
                                label = "${folders.size} ${if (folders.size > 1) "dossiers" else "dossier"}",
                                onActionClick = { onOpenCollection(QuickCollection.PlaylistsOverview) }
                            )
                        }
                        if (folders.isEmpty()) {
                            item { EmptyListHint("Aucun dossier audio") }
                        } else {
                            items(folders, key = { it.key }) { (folderName, folderTracks) ->
                                val rep = folderTracks.first()
                                FolderRow(
                                    folderName = folderName,
                                    folderPath = rep.folderPath,
                                    trackCount = folderTracks.size,
                                    onClick = { onOpenCollection(QuickCollection.FolderDetail(folderName)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Sort Order Bottom Sheet
    if (showSortSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp)
            ) {
                Text(
                    text = "Trier les morceaux",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                SortOrder.entries.forEach { order ->
                    val isSelected = order == uiState.sortOrder
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSortOrder(order)
                                showSortSheet = false
                            }
                            .padding(vertical = 13.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = order.label,
                            fontSize = 15.5.sp,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) ArtistCrimson else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Sélectionné",
                                tint = ArtistCrimson,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Track Options Sheet
    val selectedTrack = trackForOptions
    if (selectedTrack != null) {
        val isFav = selectedTrack.id in uiState.favoriteIds
        ModalBottomSheet(
            onDismissRequest = { trackForOptions = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 26.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArtworkBox(
                        accentColorHex = selectedTrack.accentColorHex,
                        artworkBytes = selectedTrack.artworkBytes,
                        track = selectedTrack,
                        cornerRadius = 8.dp,
                        iconSize = 20.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedTrack.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${selectedTrack.artist} - ${selectedTrack.album}",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(6.dp))

                SheetActionItem(
                    icon = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    label = if (isFav) "Retirer des favoris" else "Ajouter aux favoris",
                    tint = if (isFav) ArtistCrimson else MaterialTheme.colorScheme.onSurface
                ) {
                    onToggleFavorite(selectedTrack.id)
                    trackForOptions = null
                }

                SheetActionItem(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Lire ensuite"
                ) {
                    onPlayNext(selectedTrack)
                    trackForOptions = null
                }

                SheetActionItem(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    label = "Ajouter à une playlist"
                ) {
                    trackForPlaylistPicker = selectedTrack
                    trackForOptions = null
                }

                SheetActionItem(
                    icon = Icons.Rounded.Shuffle,
                    label = "Lecture aléatoire depuis ce dossier"
                ) {
                    val folderTracks = uiState.allTracks.filter { it.folderName == selectedTrack.folderName }
                    onPlayTracks(folderTracks, 0, true)
                    trackForOptions = null
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${selectedTrack.folderPath} • ${selectedTrack.formatInfo} • ${formatDuration(selectedTrack.durationMs)}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                )
            }
        }
    }

    // Playlist Picker Dialog
    val trackToAssign = trackForPlaylistPicker
    if (trackToAssign != null) {
        AlertDialog(
            onDismissRequest = { trackForPlaylistPicker = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Ajouter à une playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.playlists.forEach { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    onAddTrackToPlaylist(pl.id, trackToAssign.id)
                                    trackForPlaylistPicker = null
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = ArtistCrimson
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${pl.name} (${pl.trackIds.size})",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    TextButton(
                        onClick = {
                            trackForPlaylistPicker = null
                            showCreatePlaylistDialog = true
                        }
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = ArtistCrimson)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Créer une nouvelle playlist", color = ArtistCrimson)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { trackForPlaylistPicker = null }) {
                    Text("Fermer", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // Create Playlist Dialog
    if (showCreatePlaylistDialog) {
        var nameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Nouvelle playlist") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("Nom de la playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreatePlaylist(nameInput)
                        showCreatePlaylistDialog = false
                    },
                    enabled = nameInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ArtistCrimson)
                ) {
                    Text("Créer", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Annuler", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun UnderlinedTabsBar(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LibraryTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clickable { onSelectTab(tab) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.label,
                    fontSize = 15.5.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) ArtistCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) ArtistCrimson else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun PlayAllHeaderRow(
    count: Int,
    sortOrderLabel: String,
    onPlayAll: () -> Unit,
    onCycleSort: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = count > 0, onClick = onPlayAll)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(ArtistCrimson),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Lire tout",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Lire tout ($count)",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        IconButton(onClick = onCycleSort) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = "Trier ($sortOrderLabel)",
                tint = MutedIconDark,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onOpenPlaylists) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                contentDescription = "Playlists",
                tint = MutedIconDark,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun CountSubHeaderRow(
    label: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(onClick = onActionClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.PlaylistAddCheck,
                contentDescription = "Playlists",
                tint = MutedIconDark,
                modifier = Modifier.size(23.dp)
            )
        }
    }
}

@Composable
private fun SongRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        PlayingEqualizerBars(
                            isPlaying = isPlaying,
                            color = ArtistCrimson,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    Text(
                        text = track.title,
                        fontSize = 16.sp,
                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                        color = if (isCurrent) ArtistCrimson else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .border(
                                width = 0.8.dp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(2.5.dp)
                            )
                            .padding(horizontal = 3.dp, vertical = 0.5.dp)
                    ) {
                        Text(
                            text = shortQualityTag(track.formatInfo),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 9.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${track.artist} - ${track.album}",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onAddToPlaylistClick,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = "Ajouter à une playlist",
                    tint = MutedIconDark,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Plus",
                    tint = MutedIconDark,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ArtistRow(
    artistName: String,
    trackCount: Int,
    repTrack: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkBox(
                accentColorHex = repTrack.accentColorHex,
                artworkBytes = repTrack.artworkBytes,
                track = repTrack,
                shape = CircleShape,
                iconSize = 24.dp,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistName,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$trackCount ${if (trackCount > 1) "morceaux" else "morceau"}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 84.dp)
        )
    }
}

@Composable
private fun AlbumRow(
    albumName: String,
    artistName: String,
    trackCount: Int,
    repTrack: Track,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtworkBox(
                accentColorHex = repTrack.accentColorHex,
                artworkBytes = repTrack.artworkBytes,
                track = repTrack,
                cornerRadius = 8.dp,
                iconSize = 24.dp,
                modifier = Modifier.size(52.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$trackCount ${if (trackCount > 1) "morceaux" else "morceau"} $artistName",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 84.dp)
        )
    }
}

@Composable
private fun FolderRow(
    folderName: String,
    folderPath: String,
    trackCount: Int,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Text(
                text = folderName,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$trackCount ${if (trackCount > 1) "morceaux" else "morceau"} $folderPath",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun PlaylistsCollectionScreen(
    playlists: List<Playlist>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onCreateClick: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = bottomPadding)
    ) {
        item(key = "new_playlist") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ArtistCrimson.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = ArtistCrimson)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Nouvelle playlist locale",
                    fontSize = 16.sp,
                    color = ArtistCrimson,
                    fontWeight = FontWeight.Medium
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        items(playlists, key = { it.id }) { pl ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPlaylist(pl.id) }
                    .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = null,
                        tint = ArtistCrimson
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pl.name,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${pl.trackIds.size} ${if (pl.trackIds.size > 1) "morceaux" else "morceau"}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Supprimer",
                        tint = MutedIconDark,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(start = 74.dp)
            )
        }
    }
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyListHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun shortQualityTag(formatInfo: String): String {
    val upper = formatInfo.uppercase()
    return when {
        "FLAC" in upper || "WAV" in upper -> "SQ"
        else -> "HQ"
    }
}

private fun resolveCollectionTracks(
    collection: QuickCollection,
    uiState: PlayerUiState
): List<Track> {
    val byId = uiState.allTracks.associateBy { it.id }
    val raw = when (collection) {
        QuickCollection.Favorites -> uiState.allTracks.filter { it.id in uiState.favoriteIds }
        QuickCollection.Recent -> return uiState.recentIds.mapNotNull { byId[it] }
        QuickCollection.PlaylistsOverview -> emptyList()
        is QuickCollection.AlbumDetail -> uiState.allTracks.filter { it.album == collection.albumName }
        is QuickCollection.ArtistDetail -> uiState.allTracks.filter { it.artist == collection.artistName }
        is QuickCollection.FolderDetail -> uiState.allTracks.filter { it.folderName == collection.folderName }
        is QuickCollection.PlaylistDetail -> {
            val pl = uiState.playlists.firstOrNull { it.id == collection.playlistId }
            return pl?.trackIds?.mapNotNull { byId[it] }.orEmpty()
        }
    }
    return when (uiState.sortOrder) {
        SortOrder.DATE_ADDED_DESC -> raw.sortedByDescending { it.dateAddedSec }
        SortOrder.TITLE_ASC -> raw.sortedBy { it.title.lowercase() }
        SortOrder.TITLE_DESC -> raw.sortedByDescending { it.title.lowercase() }
        SortOrder.ARTIST_ASC -> raw.sortedBy { it.artist.lowercase() }
        SortOrder.DURATION_DESC -> raw.sortedByDescending { it.durationMs }
    }
}

package com.cadence.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.data.Album
import com.cadence.music.data.AlbumSort
import com.cadence.music.data.ArtworkLoader
import com.cadence.music.data.Library
import com.cadence.music.data.Playlist
import com.cadence.music.data.SongSort
import com.cadence.music.data.Track
import com.cadence.music.data.UserData
import com.cadence.music.data.formatTotalDuration
import com.cadence.music.data.prettyPath
import com.cadence.music.data.songCount
import com.cadence.music.data.sortKey
import com.cadence.music.ui.Nav
import com.cadence.music.ui.components.ActionMenu
import com.cadence.music.ui.components.AlbumShelf
import com.cadence.music.ui.components.Artwork
import com.cadence.music.ui.components.EmptyState
import com.cadence.music.ui.components.FooterText
import com.cadence.music.ui.components.LocalTrackActions
import com.cadence.music.ui.components.MenuAction
import com.cadence.music.ui.components.NavRow
import com.cadence.music.ui.components.PlayShuffleRow
import com.cadence.music.ui.components.ScreenList
import com.cadence.music.ui.components.SectionHeader
import com.cadence.music.ui.components.TrackLeading
import com.cadence.music.ui.components.TrackRow
import com.cadence.music.ui.components.albumGrid
import com.cadence.music.ui.theme.Cadence

// ---------------------------------------------------------------------------------------------
// Home ("Listen Now" for a local library)
// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(library: Library, user: UserData, nav: Nav, onChooseFolders: () -> Unit) {
    val actions = LocalTrackActions.current
    val recentAlbums = remember(library, user.recentlyPlayed) {
        user.recentlyPlayed.mapNotNull { library.byId[it]?.albumId }.distinct().mapNotNull { library.albumsById[it] }.take(15)
    }
    val topSongs = remember(library, user.playCounts) {
        user.playCounts.entries.sortedByDescending { it.value }.mapNotNull { library.byId[it.key] }.take(8)
    }
    val recentlyAdded = remember(library) { library.albums.sortedByDescending { it.dateAdded }.take(15) }
    val lossless = remember(library) { library.albums.filter { it.isLossless }.sortedByDescending { it.dateAdded }.take(15) }
    val favorites = remember(library, user.favorites) { user.favorites.reversed().mapNotNull { library.byId[it] } }

    ScreenList("Home") {
        if (library.tracks.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.LibraryMusic, "No Music Yet",
                    "Pick the folders where your music lives and it will show up here.",
                    "Choose Folders", onChooseFolders,
                )
            }
            return@ScreenList
        }
        item { PlayShuffleRow(onPlay = { actions.play(library.tracks, 0) }, onShuffle = { actions.shuffle(library.tracks) }) }

        if (recentAlbums.isNotEmpty()) {
            item { SectionHeader("Recently Played") }
            item { AlbumShelf(recentAlbums, { nav.album(it.id) }) }
        }
        if (topSongs.isNotEmpty()) {
            item { SectionHeader("Top Songs") }
            itemsIndexed(topSongs, key = { _, t -> "top-${t.id}" }) { i, t ->
                TrackRow(t, onClick = { actions.play(topSongs, i) }, showDivider = i != topSongs.lastIndex)
            }
        }
        item { SectionHeader("Recently Added", "See All") { nav.go(com.cadence.music.ui.Routes.ALBUMS) } }
        item { AlbumShelf(recentlyAdded, { nav.album(it.id) }) }

        if (lossless.isNotEmpty()) {
            item { SectionHeader("Lossless") }
            item { AlbumShelf(lossless, { nav.album(it.id) }) }
        }
        if (favorites.isNotEmpty()) {
            item { SectionHeader("Favorite Songs", "See All") { nav.go(com.cadence.music.ui.Routes.FAVORITES) } }
            val shown = favorites.take(5)
            itemsIndexed(shown, key = { _, t -> "fav-${t.id}" }) { i, t ->
                TrackRow(t, onClick = { actions.play(favorites, i) }, showDivider = i != shown.lastIndex)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Library root
// ---------------------------------------------------------------------------------------------

@Composable
fun LibraryScreen(library: Library, nav: Nav) {
    val recent = remember(library) { library.albums.sortedByDescending { it.dateAdded }.take(20) }
    ScreenList("Library") {
        item {
            val categories = libraryCategories(library)
            Column {
                categories.forEachIndexed { i, c ->
                    NavRow(c.icon, c.title, { nav.browse(c.title) }, showDivider = i != categories.lastIndex)
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recently Added") }
            albumGrid("recent", recent, { nav.album(it.id) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Category browser: a pill bar on top and swipe left / right between Library categories
// ---------------------------------------------------------------------------------------------

enum class LibraryCategory(val title: String, val icon: ImageVector) {
    PLAYLISTS("Playlists", Icons.Rounded.PlaylistPlay),
    ARTISTS("Artists", Icons.Rounded.Mic),
    ALBUMS("Albums", Icons.Rounded.Album),
    SONGS("Songs", Icons.Rounded.MusicNote),
    GENRES("Genres", Icons.Rounded.Category),
    FOLDERS("Folders", Icons.Rounded.Folder),
    FAVORITES("Favorites", Icons.Rounded.Star),
}

fun libraryCategories(library: Library): List<LibraryCategory> =
    LibraryCategory.entries.filter { it != LibraryCategory.GENRES || library.genres.isNotEmpty() }

@Composable
fun LibraryBrowserScreen(
    initial: String,
    library: Library,
    nav: Nav,
    onTitleChange: (String) -> Unit,
    page: @Composable (LibraryCategory) -> Unit,
) {
    val categories = libraryCategories(library)
    val pager = rememberPagerState(
        initialPage = categories.indexOfFirst { it.title == initial }.coerceAtLeast(0),
    ) { categories.size }
    val scope = rememberCoroutineScope()
    val pills = rememberLazyListState()
    val current = categories.getOrElse(pager.currentPage) { categories.first() }

    LaunchedEffect(current) {
        onTitleChange(current.title)
        pills.animateScrollToItem((pager.currentPage - 1).coerceAtLeast(0))
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().background(Cadence.colors.bar).statusBarsPadding()) {
            Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
                Row(
                    Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = nav::back)
                        .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.ChevronLeft, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Text(nav.backLabel(), color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                }
                Text(current.title, style = MaterialTheme.typography.titleMedium)
            }
            LazyRow(
                state = pills,
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 10.dp),
            ) {
                itemsIndexed(categories, key = { _, c -> c.name }) { i, c ->
                    val selected = i == pager.currentPage
                    Row(
                        Modifier
                            .height(34.dp)
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Cadence.colors.fill)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            c.icon, null, modifier = Modifier.size(16.dp),
                            tint = if (selected) Color.White else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            c.title, fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            HorizontalDivider(color = Cadence.colors.separator, thickness = 0.5.dp)
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f),
            // Keep the neighbours built so a swipe never waits on a page being created.
            beyondViewportPageCount = 1,
            key = { categories[it].name },
        ) { i ->
            page(categories[i])
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Songs
// ---------------------------------------------------------------------------------------------

@Composable
fun SongsScreen(library: Library, sort: SongSort, onSort: (SongSort) -> Unit, nav: Nav, embedded: Boolean = false) {
    val actions = LocalTrackActions.current
    val sorted = remember(library, sort) { sortSongs(library.tracks, sort) }
    ScreenList("Songs", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded, trailing = {
        SortButton(SongSort.entries.map { it.label }, sort.ordinal) { onSort(SongSort.entries[it]) }
    }) {
        if (sorted.isEmpty()) {
            item { EmptyState(Icons.Rounded.MusicNote, "No Songs", "Songs from your music folders appear here.") }
            return@ScreenList
        }
        item { PlayShuffleRow(onPlay = { actions.play(sorted, 0) }, onShuffle = { actions.shuffle(sorted) }) }
        itemsIndexed(sorted, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, onClick = { actions.play(sorted, i) })
        }
        item { FooterText(songCount(sorted.size)) }
    }
}

fun sortSongs(tracks: List<Track>, sort: SongSort): List<Track> = when (sort) {
    SongSort.TITLE -> tracks.sortedBy { sortKey(it.title) }
    SongSort.ARTIST -> tracks.sortedWith(compareBy({ sortKey(it.artist) }, { sortKey(it.album) }, { it.discNumber }, { it.trackNumber }))
    SongSort.ALBUM -> tracks.sortedWith(compareBy({ sortKey(it.album) }, { it.discNumber }, { it.trackNumber }))
    SongSort.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
    SongSort.DURATION -> tracks.sortedByDescending { it.durationMs }
}

@Composable
fun SortButton(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(14.dp)) {
            Text("Sort By", color = Cadence.colors.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            labels.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    trailingIcon = { if (i == selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) },
                    onClick = {
                        open = false
                        onSelect(i)
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------------------------

@Composable
fun AlbumsScreen(library: Library, sort: AlbumSort, onSort: (AlbumSort) -> Unit, nav: Nav, embedded: Boolean = false) {
    val sorted = remember(library, sort) {
        when (sort) {
            AlbumSort.TITLE -> library.albums
            AlbumSort.ARTIST -> library.albums.sortedWith(compareBy({ sortKey(it.artist) }, { -it.year }))
            AlbumSort.YEAR -> library.albums.sortedByDescending { it.year }
            AlbumSort.DATE_ADDED -> library.albums.sortedByDescending { it.dateAdded }
        }
    }
    ScreenList("Albums", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded, trailing = {
        SortButton(AlbumSort.entries.map { it.label }, sort.ordinal) { onSort(AlbumSort.entries[it]) }
    }) {
        if (sorted.isEmpty()) {
            item { EmptyState(Icons.Rounded.Album, "No Albums", "Albums from your music folders appear here.") }
            return@ScreenList
        }
        albumGrid("albums", sorted, { nav.album(it.id) })
        item { FooterText(if (sorted.size == 1) "1 album" else "${sorted.size} albums") }
    }
}

@Composable
fun AlbumDetailScreen(album: Album?, library: Library, nav: Nav) {
    val actions = LocalTrackActions.current
    if (album == null) {
        ScreenList("Album", backLabel = nav.backLabel(), onBack = nav::back, showLargeTitle = false) {
            item { EmptyState(Icons.Rounded.Album, "Album Unavailable", "This album isn't in your music folders anymore.") }
        }
        return
    }
    var menu by remember { mutableStateOf(false) }
    val moreByArtist = remember(album, library) {
        library.artistsByName[album.artist]?.albums?.filter { it.id != album.id }.orEmpty()
    }

    ScreenList(album.title, backLabel = nav.backLabel(), onBack = nav::back, showLargeTitle = false, trailing = {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.primary)
            }
            ActionMenu(
                menu, { menu = false },
                listOf(
                    MenuAction("Play Next", Icons.Rounded.PlaylistPlay) { actions.playNext(album.tracks) },
                    MenuAction("Play Last", Icons.Rounded.QueueMusic) { actions.playLast(album.tracks) },
                    MenuAction("Add to Playlist…", Icons.Rounded.PlaylistAdd) { actions.addToPlaylist(album.tracks) },
                    MenuAction("Shuffle", Icons.Rounded.Shuffle) { actions.shuffle(album.tracks) },
                ),
            )
        }
    }) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    album.cover,
                    Modifier
                        .padding(horizontal = 52.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(18.dp, RoundedCornerShape(10.dp)),
                    corner = 10.dp,
                    size = ArtworkLoader.LARGE,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    album.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                Text(
                    album.artist, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(6.dp)).clickable { nav.artist(album.artist) },
                )
                val meta = listOfNotNull(
                    album.genre.takeIf { it.isNotBlank() }?.uppercase(),
                    album.year.takeIf { it > 0 }?.toString(),
                    if (album.isLossless) "LOSSLESS" else null,
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cadence.colors.secondaryText, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item { PlayShuffleRow(onPlay = { actions.play(album.tracks, 0) }, onShuffle = { actions.shuffle(album.tracks) }) }
        item { HorizontalDivider(Modifier.padding(start = 20.dp), thickness = 0.5.dp, color = Cadence.colors.separator) }

        val discs = album.tracks.groupBy { it.discNumber }
        discs.forEach { (disc, tracks) ->
            if (album.hasMultipleDiscs) {
                item(key = "disc-$disc") {
                    Text(
                        "Disc $disc", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Cadence.colors.secondaryText,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
            }
            items(tracks, key = { it.id }) { t ->
                TrackRow(
                    t,
                    onClick = { actions.play(album.tracks, album.tracks.indexOf(t)) },
                    leading = TrackLeading.Number,
                    subtitle = t.artist.takeIf { it != album.artist && it != "Unknown Artist" },
                )
            }
        }
        item {
            FooterText(
                listOfNotNull(
                    album.year.takeIf { it > 0 }?.toString(),
                    "${songCount(album.tracks.size)}, ${formatTotalDuration(album.durationMs)}",
                ).joinToString("\n")
            )
        }
        if (moreByArtist.isNotEmpty()) {
            item { SectionHeader("More by ${album.artist}") }
            item { AlbumShelf(moreByArtist, { nav.album(it.id) }, subtitle = { a -> a.year.takeIf { it > 0 }?.toString() ?: a.artist }) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Artists
// ---------------------------------------------------------------------------------------------

@Composable
fun ArtistsScreen(library: Library, nav: Nav, embedded: Boolean = false) {
    ScreenList("Artists", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded) {
        if (library.artists.isEmpty()) {
            item { EmptyState(Icons.Rounded.Mic, "No Artists", "Artists from your music folders appear here.") }
            return@ScreenList
        }
        items(library.artists, key = { it.name }) { artist ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.artist(artist.name) }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(artist.albums.firstOrNull()?.cover, Modifier.size(52.dp), shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(artist.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${artist.albums.size} album${if (artist.albums.size == 1) "" else "s"} · ${songCount(artist.tracks.size)}",
                            fontSize = 13.sp, color = Cadence.colors.secondaryText,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
                }
                HorizontalDivider(Modifier.padding(start = 86.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
            }
        }
    }
}

@Composable
fun ArtistDetailScreen(name: String, library: Library, nav: Nav) {
    val actions = LocalTrackActions.current
    val artist = library.artistsByName[name]
    ScreenList(name, backLabel = nav.backLabel(), onBack = nav::back) {
        if (artist == null) {
            item { EmptyState(Icons.Rounded.Mic, "Artist Unavailable", "This artist isn't in your music folders anymore.") }
            return@ScreenList
        }
        val songs = artist.tracks.sortedBy { sortKey(it.title) }
        item { PlayShuffleRow(onPlay = { actions.play(artist.tracks, 0) }, onShuffle = { actions.shuffle(artist.tracks) }) }
        item { SectionHeader("Albums") }
        albumGrid("artist-albums", artist.albums, { nav.album(it.id) }, subtitle = { a -> a.year.takeIf { it > 0 }?.toString() ?: "" })
        item { SectionHeader("Songs") }
        itemsIndexed(songs, key = { _, t -> "song-${t.id}" }) { i, t ->
            TrackRow(t, onClick = { actions.play(songs, i) }, subtitle = t.album)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Genres and folders
// ---------------------------------------------------------------------------------------------

@Composable
fun GenresScreen(library: Library, nav: Nav, embedded: Boolean = false) {
    ScreenList("Genres", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded) {
        if (library.genres.isEmpty()) {
            item { EmptyState(Icons.Rounded.Category, "No Genres", "Songs tagged with a genre appear here.") }
            return@ScreenList
        }
        items(library.genres, key = { it.name }) { genre ->
            NavRow(Icons.Rounded.Category, genre.name, { nav.genre(genre.name) }, detail = "${genre.tracks.size}  ")
        }
    }
}

@Composable
fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    nav: Nav,
    emptyTitle: String,
    emptyMessage: String,
    subtitle: (Track) -> String? = { "${it.artist} · ${it.album}" },
    embedded: Boolean = false,
) {
    val actions = LocalTrackActions.current
    ScreenList(title, backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded) {
        if (tracks.isEmpty()) {
            item { EmptyState(Icons.Rounded.MusicNote, emptyTitle, emptyMessage) }
            return@ScreenList
        }
        item { PlayShuffleRow(onPlay = { actions.play(tracks, 0) }, onShuffle = { actions.shuffle(tracks) }) }
        itemsIndexed(tracks, key = { _, t -> t.id }) { i, t ->
            TrackRow(t, onClick = { actions.play(tracks, i) }, subtitle = subtitle(t))
        }
        item { FooterText("${songCount(tracks.size)}, ${formatTotalDuration(tracks.sumOf { it.durationMs })}") }
    }
}

@Composable
fun FoldersScreen(library: Library, nav: Nav, embedded: Boolean = false) {
    ScreenList("Folders", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded) {
        if (library.folders.isEmpty()) {
            item { EmptyState(Icons.Rounded.Folder, "No Folders", "Folders from your music library appear here.") }
            return@ScreenList
        }
        items(library.folders, key = { it.path }) { folder ->
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { nav.folder(folder.path) }.padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(folder.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${songCount(folder.tracks.size)} · ${prettyPath(folder.path.substringBeforeLast('/'))}",
                            fontSize = 12.sp, color = Cadence.colors.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
                }
                HorizontalDivider(Modifier.padding(start = 62.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Playlists
// ---------------------------------------------------------------------------------------------

@Composable
fun PlaylistsScreen(library: Library, user: UserData, nav: Nav, embedded: Boolean = false, onCreate: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    ScreenList("Playlists", backLabel = nav.backLabel(), onBack = nav::back, embedded = embedded) {
        item {
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Cadence.colors.fill),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Text("New Playlist…", color = MaterialTheme.colorScheme.primary, fontSize = 17.sp)
                }
                HorizontalDivider(Modifier.padding(start = 90.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
            }
        }
        items(user.playlists.sortedByDescending { it.createdAt }, key = { it.id }) { p ->
            PlaylistRow(p, library) { nav.playlist(p.id) }
        }
    }
    if (creating) {
        TextInputDialog("New Playlist", "", "Create", onConfirm = {
            onCreate(it)
            creating = false
        }, onDismiss = { creating = false })
    }
}

@Composable
private fun PlaylistRow(p: Playlist, library: Library, onClick: () -> Unit) {
    val first = p.trackIds.firstNotNullOfOrNull { library.byId[it] }
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(first, Modifier.size(56.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(songCount(p.trackIds.count { it in library.byId }), fontSize = 13.sp, color = Cadence.colors.secondaryText)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
        }
        HorizontalDivider(Modifier.padding(start = 90.dp), thickness = 0.5.dp, color = Cadence.colors.separator)
    }
}

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    library: Library,
    user: UserData,
    nav: Nav,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val actions = LocalTrackActions.current
    val playlist = user.playlists.firstOrNull { it.id == playlistId }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (playlist == null) {
        ScreenList("Playlist", backLabel = nav.backLabel(), onBack = nav::back, showLargeTitle = false) {
            item { EmptyState(Icons.Rounded.PlaylistPlay, "Playlist Deleted", "This playlist no longer exists.") }
        }
        return
    }
    // Keep each entry's position in the stored list, so removing skips over missing files correctly.
    val entries = playlist.trackIds.mapIndexedNotNull { pos, id -> library.byId[id]?.let { pos to it } }
    val tracks = entries.map { it.second }

    ScreenList(playlist.name, backLabel = nav.backLabel(), onBack = nav::back, showLargeTitle = false, trailing = {
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More", tint = MaterialTheme.colorScheme.primary)
            }
            ActionMenu(
                menu, { menu = false },
                listOf(
                    MenuAction("Rename", Icons.Rounded.Edit) { renaming = true },
                    MenuAction("Play Next", Icons.Rounded.PlaylistPlay) { actions.playNext(tracks) },
                    MenuAction("Delete Playlist", Icons.Rounded.Delete, destructive = true) { confirmDelete = true },
                ),
            )
        }
    }) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Artwork(
                    tracks.firstOrNull(),
                    Modifier.padding(horizontal = 72.dp).fillMaxWidth().aspectRatio(1f).shadow(14.dp, RoundedCornerShape(10.dp)),
                    corner = 10.dp, size = ArtworkLoader.LARGE,
                )
                Spacer(Modifier.height(16.dp))
                Text(playlist.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                Text(
                    "${songCount(tracks.size)}, ${formatTotalDuration(tracks.sumOf { it.durationMs })}",
                    fontSize = 14.sp, color = Cadence.colors.secondaryText,
                )
            }
        }
        if (tracks.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.PlaylistAdd, "Empty Playlist",
                    "Use “Add to Playlist…” from any song's ⋯ menu to add music here.",
                )
            }
            return@ScreenList
        }
        item { PlayShuffleRow(onPlay = { actions.play(tracks, 0) }, onShuffle = { actions.shuffle(tracks) }) }
        itemsIndexed(entries, key = { _, e -> "${e.first}-${e.second.id}" }) { i, (pos, t) ->
            TrackRow(
                t,
                onClick = { actions.play(tracks, i) },
                extraMenu = buildList {
                    if (pos > 0) add(MenuAction("Move Up", Icons.Rounded.ArrowUpward) { onMove(pos, pos - 1) })
                    if (pos < playlist.trackIds.lastIndex) add(MenuAction("Move Down", Icons.Rounded.ArrowDownward) { onMove(pos, pos + 1) })
                    add(MenuAction("Remove from Playlist", Icons.Rounded.RemoveCircleOutline, destructive = true) { onRemoveAt(pos) })
                },
            )
        }
    }

    if (renaming) {
        TextInputDialog("Rename Playlist", playlist.name, "Save", onConfirm = {
            onRename(it)
            renaming = false
        }, onDismiss = { renaming = false })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${playlist.name}”?") },
            text = { Text("The songs stay in your library.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Delete", color = androidx.compose.ui.graphics.Color(0xFFFF3B30)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Name") },
                shape = RoundedCornerShape(10.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Picker shown from "Add to Playlist…". */
@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    count: Int,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog("New Playlist", "", "Create", onConfirm = onCreate, onDismiss = { creating = false })
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Add to Playlist" else "Add $count Songs to Playlist") },
        text = {
            Column {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { creating = true }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text("New Playlist…", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
                playlists.sortedByDescending { it.createdAt }.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.PlaylistPlay, null, tint = Cadence.colors.secondaryText)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

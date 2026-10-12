package com.cadence.music.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cadence.music.data.ArtworkLoader
import com.cadence.music.ui.components.rememberArtwork
import com.cadence.music.ui.nowplaying.ArtworkMorph
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cadence.music.data.AppSettings
import com.cadence.music.data.FolderInfo
import com.cadence.music.data.Library
import com.cadence.music.data.SettingsKeys
import com.cadence.music.data.Track
import com.cadence.music.data.UserData
import com.cadence.music.data.hasAudioPermission
import com.cadence.music.ui.components.LocalTrackActions
import com.cadence.music.ui.components.TrackActions
import com.cadence.music.ui.nowplaying.MiniPlayer
import com.cadence.music.ui.nowplaying.NowPlayingScreen
import com.cadence.music.ui.nowplaying.playerSheet
import com.cadence.music.ui.nowplaying.rememberPlayerSheetState
import com.cadence.music.ui.screens.AddToPlaylistDialog
import com.cadence.music.ui.screens.AlbumDetailScreen
import com.cadence.music.ui.screens.AlbumsScreen
import com.cadence.music.ui.screens.ArtistDetailScreen
import com.cadence.music.ui.screens.ArtistsScreen
import com.cadence.music.ui.screens.FoldersScreen
import com.cadence.music.ui.screens.GenresScreen
import com.cadence.music.ui.screens.HomeScreen
import com.cadence.music.ui.screens.LibraryBrowserScreen
import com.cadence.music.ui.screens.LibraryCategory
import com.cadence.music.ui.screens.LibraryScreen
import com.cadence.music.ui.screens.MusicFoldersScreen
import com.cadence.music.ui.screens.PermissionScreen
import com.cadence.music.ui.screens.PlaylistDetailScreen
import com.cadence.music.ui.screens.PlaylistsScreen
import com.cadence.music.ui.screens.SearchScreen
import com.cadence.music.ui.screens.SettingsScreen
import com.cadence.music.ui.screens.SongsScreen
import com.cadence.music.ui.screens.TrackListScreen
import com.cadence.music.ui.theme.Cadence
import com.cadence.music.ui.theme.CadenceTheme
import kotlinx.coroutines.delay

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME(Routes.HOME, "Home", Icons.Rounded.Home),
    LIBRARY(Routes.LIBRARY, "Library", Icons.Rounded.LibraryMusic),
    SEARCH(Routes.SEARCH, "Search", Icons.Rounded.Search),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
}

@Composable
fun CadenceRoot(vm: MainViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings
    if (s == null) {
        // Settings load in a few milliseconds; avoid flashing onboarding before they arrive.
        Box(Modifier.fillMaxSize())
        return
    }
    CadenceTheme(s.themeMode, s.accentIndex) {
        val context = LocalContext.current
        var hasPermission by remember { mutableStateOf(hasAudioPermission(context)) }
        LifecycleResumeEffect(Unit) {
            val now = hasAudioPermission(context)
            if (now && !hasPermission) vm.onPermissionGranted()
            hasPermission = now
            onPauseOrDispose { }
        }

        when {
            !hasPermission -> {
                SystemBars(lightIcons = Cadence.colors.isDark)
                PermissionScreen(onGranted = {
                    hasPermission = true
                    vm.onPermissionGranted()
                })
            }
            !s.foldersConfigured -> {
                SystemBars(lightIcons = Cadence.colors.isDark)
                val folders by vm.allFolders.collectAsStateWithLifecycle()
                MusicFoldersScreen(
                    settings = s,
                    allFolders = folders,
                    onboarding = true,
                    onSave = { f, all -> vm.setFolders(f, all) },
                    onBack = null,
                )
            }
            else -> MainScaffold(vm, s)
        }
    }
}

@Composable
private fun SystemBars(lightIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !lightIcons
            isAppearanceLightNavigationBars = !lightIcons
        }
    }
}

/** Per-tab back handling: each tab's NavHost talks to its own dispatcher, and only the visible tab's is forwarded. */
private class TabBackOwner(lifecycleOwner: LifecycleOwner) : OnBackPressedDispatcherOwner, LifecycleOwner by lifecycleOwner {
    var canGoBack by mutableStateOf(false)
        private set
    override val onBackPressedDispatcher = OnBackPressedDispatcher(null, Consumer<Boolean> { canGoBack = it })
}

/** Measures [content] always but only places (draws, takes touches) it when [visible]. */
private fun Modifier.showWhen(visible: Boolean) = layout { measurable, constraints ->
    val p = measurable.measure(constraints)
    layout(p.width, p.height) { if (visible) p.place(0, 0) }
}

@Composable
private fun MainScaffold(vm: MainViewModel, settings: AppSettings) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current

    // Every tab keeps its own NavHost and back stack, all kept alive, so switching tabs only
    // swaps which one is drawn: nothing is rebuilt and nothing animates. This is what Apple Music
    // does too (each tab remembers where you were).
    val controllers = Tab.entries.associateWith { rememberNavController() }
    val navs = remember { controllers.mapValues { Nav(it.value) } }
    val backOwners = remember { Tab.entries.associateWith { TabBackOwner(lifecycleOwner) } }

    val library by vm.library.collectAsStateWithLifecycle()
    val user by vm.userData.collectAsStateWithLifecycle()
    val player by vm.playerState.collectAsStateWithLifecycle()
    val sleepTimer by vm.sleepTimer.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val allFolders by vm.allFolders.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(Tab.HOME) }
    // Tabs are built the first time they're needed; the rest are built quietly right after start-up,
    // one per frame, so even the first visit to each tab is instant.
    var builtTabs by rememberSaveable { mutableStateOf(listOf(Tab.HOME.name)) }
    LaunchedEffect(Unit) {
        delay(400)
        Tab.entries.forEach { tab ->
            if (tab.name !in builtTabs) {
                builtTabs = builtTabs + tab.name
                withFrameNanos { }
            }
        }
    }
    var searchFocusRequests by remember { mutableIntStateOf(0) }

    val sheet = rememberPlayerSheetState()
    var playlistTarget by remember { mutableStateOf<List<Track>?>(null) }

    val currentTrack = player.currentId?.let { library.byId[it] }
    // Keeps Now Playing drawn while it slides away after the queue empties.
    var lastTrack by remember { mutableStateOf<Track?>(null) }
    SideEffect { if (currentTrack != null) lastTrack = currentTrack }
    LaunchedEffect(player.queueSize) { if (player.queueSize == 0 && sheet.isOpen) sheet.close() }

    SystemBars(lightIcons = Cadence.colors.isDark || sheet.isOpen)

    // Song progress for the mini player's thin bar; polled only while the app is in front.
    val miniProgress = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(currentTrack?.id, sheet.isOpen) {
        if (currentTrack == null || sheet.isOpen) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val duration = player.durationMs.takeIf { it > 0 } ?: currentTrack.durationMs
                miniProgress.floatValue = if (duration > 0) vm.player.positionMs.toFloat() / duration else 0f
                delay(500)
            }
        }
    }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val currentNav by rememberUpdatedState(navs.getValue(selectedTab))
    val actions = remember(user.favorites, player.currentId, player.isPlaying) {
        TrackActions(
            play = { tracks, index -> vm.player.playTracks(tracks, index) },
            shuffle = { tracks -> vm.player.playTracks(tracks, shuffle = true) },
            playNext = { tracks ->
                vm.player.playNext(tracks)
                toast("Playing Next")
            },
            playLast = { tracks ->
                vm.player.playLast(tracks)
                toast("Added to Queue")
            },
            toggleFavorite = { vm.user.toggleFavorite(it.id) },
            addToPlaylist = { playlistTarget = it },
            openAlbum = { id ->
                sheet.close()
                currentNav.album(id)
            },
            openArtist = { name ->
                sheet.close()
                currentNav.artist(name)
            },
            favorites = user.favorites,
            currentId = player.currentId,
            isPlaying = player.isPlaying,
        )
    }

    // Library pages shown inside the swipeable category browser.
    val libraryPage: @Composable (LibraryCategory, Nav) -> Unit = { category, nav ->
        when (category) {
            LibraryCategory.PLAYLISTS -> PlaylistsScreen(library, user, nav, embedded = true) { name ->
                nav.playlist(vm.user.createPlaylist(name))
            }
            LibraryCategory.ARTISTS -> ArtistsScreen(library, nav, embedded = true)
            LibraryCategory.ALBUMS ->
                AlbumsScreen(library, settings.albumSort, { vm.set(SettingsKeys.ALBUM_SORT, it.name) }, nav, embedded = true)
            LibraryCategory.SONGS ->
                SongsScreen(library, settings.songSort, { vm.set(SettingsKeys.SONG_SORT, it.name) }, nav, embedded = true)
            LibraryCategory.GENRES -> GenresScreen(library, nav, embedded = true)
            LibraryCategory.FOLDERS -> FoldersScreen(library, nav, embedded = true)
            LibraryCategory.FAVORITES -> {
                val favs = remember(library, user.favorites) { user.favorites.reversed().mapNotNull { library.byId[it] } }
                TrackListScreen(
                    "Favorites", favs, nav, "No Favorites", "Tap ☆ on Now Playing or Favorite from any song's ⋯ menu.",
                    embedded = true,
                )
            }
        }
    }

    CompositionLocalProvider(LocalTrackActions provides actions) {
        // Black behind the app, seen only while the app shrinks back under an opening player.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Like iOS: the app recedes slightly while Now Playing rises over it.
                        val open = 1f - sheet.fraction
                        if (open > 0f) {
                            val k = 1f - 0.06f * open
                            scaleX = k
                            scaleY = k
                            shape = RoundedCornerShape(16.dp * open)
                            clip = true
                        }
                    }
                    .background(MaterialTheme.colorScheme.background),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Tab.entries.forEach { tab ->
                        if (tab.name in builtTabs || tab == selectedTab) {
                            key(tab) {
                                Box(Modifier.fillMaxSize().showWhen(tab == selectedTab)) {
                                    CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides backOwners.getValue(tab)) {
                                        TabNavHost(
                                            tab = tab,
                                            navController = controllers.getValue(tab),
                                            nav = navs.getValue(tab),
                                            vm = vm,
                                            settings = settings,
                                            library = library,
                                            user = user,
                                            scanning = scanning,
                                            allFolders = allFolders,
                                            searchFocusRequests = { searchFocusRequests },
                                            libraryPage = libraryPage,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (currentTrack != null) {
                    MiniPlayer(
                        track = currentTrack,
                        isPlaying = player.playWhenReady,
                        sheet = sheet,
                        progress = { miniProgress.floatValue },
                        onTogglePlay = { vm.player.togglePlay() },
                        onNext = { vm.player.next() },
                        onPrevious = { vm.player.previousTrack() },
                    )
                }
                BottomTabs(selectedTab) { tab ->
                    if (tab == selectedTab) {
                        // Second tap: back to the tab's first screen; on Search also jump to the field.
                        navs.getValue(tab).popToRoot()
                        if (tab == Tab.SEARCH) searchFocusRequests++
                    } else {
                        if (tab.name !in builtTabs) builtTabs = builtTabs + tab.name
                        // A hidden tab must not keep the keyboard open (e.g. leaving Search mid-typing).
                        focusManager.clearFocus()
                        selectedTab = tab
                    }
                }
            }

            (currentTrack ?: lastTrack)?.let { track ->
                NowPlayingScreen(
                    track = currentTrack ?: track,
                    player = player,
                    library = library,
                    settings = settings,
                    sleepTimer = sleepTimer,
                    connection = vm.player,
                    sheet = sheet,
                    onOpenAlbum = actions.openAlbum,
                    onOpenArtist = actions.openArtist,
                    modifier = Modifier.playerSheet(sheet),
                )
                if (sheet.isVisible) ArtworkMorph(sheet, rememberArtwork(currentTrack ?: track, ArtworkLoader.LARGE))
            }
        }

        // Back goes: close Now Playing, else back within the current tab, else to the Home tab.
        val backOwner = backOwners.getValue(selectedTab)
        BackHandler(enabled = selectedTab != Tab.HOME && !backOwner.canGoBack) { selectedTab = Tab.HOME }
        BackHandler(enabled = backOwner.canGoBack) { backOwner.onBackPressedDispatcher.onBackPressed() }
        BackHandler(enabled = sheet.isOpen) { sheet.close() }

        playlistTarget?.let { tracks ->
            AddToPlaylistDialog(
                playlists = user.playlists,
                count = tracks.size,
                onPick = { id ->
                    vm.user.addToPlaylist(id, tracks.map { it.id })
                    playlistTarget = null
                    toast("Added to “${user.playlists.firstOrNull { it.id == id }?.name ?: "Playlist"}”")
                },
                onCreate = { name ->
                    vm.user.createPlaylist(name, tracks.map { it.id })
                    playlistTarget = null
                    toast("Created “$name”")
                },
                onDismiss = { playlistTarget = null },
            )
        }
    }
}

@Composable
private fun TabNavHost(
    tab: Tab,
    navController: NavHostController,
    nav: Nav,
    vm: MainViewModel,
    settings: AppSettings,
    library: Library,
    user: UserData,
    scanning: Boolean,
    allFolders: List<FolderInfo>,
    searchFocusRequests: () -> Int,
    libraryPage: @Composable (LibraryCategory, Nav) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = tab.route,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(120)) },
    ) {
        composable(Routes.HOME) { HomeScreen(library, user, nav) { nav.go(Routes.MUSIC_FOLDERS) } }
        composable(Routes.LIBRARY) { LibraryScreen(library, nav) }
        composable(Routes.SEARCH) { SearchScreen(library, nav, searchFocusRequests()) }
        composable(Routes.SETTINGS) { SettingsScreen(settings, vm, library.tracks.size, scanning, nav) }
        composable(Routes.BROWSE, arguments = listOf(navArgument("category") { type = NavType.StringType })) { entry ->
            LibraryBrowserScreen(
                initial = entry.arguments?.getString("category").orEmpty(),
                library = library,
                nav = nav,
                onTitleChange = { entry.savedStateHandle[TITLE_KEY] = it },
            ) { category -> libraryPage(category, nav) }
        }
        composable(Routes.SONGS) {
            SongsScreen(library, settings.songSort, { vm.set(SettingsKeys.SONG_SORT, it.name) }, nav)
        }
        composable(Routes.ALBUMS) {
            AlbumsScreen(library, settings.albumSort, { vm.set(SettingsKeys.ALBUM_SORT, it.name) }, nav)
        }
        composable(Routes.ARTISTS) { ArtistsScreen(library, nav) }
        composable(Routes.GENRES) { GenresScreen(library, nav) }
        composable(Routes.FOLDERS) { FoldersScreen(library, nav) }
        composable(Routes.PLAYLISTS) {
            PlaylistsScreen(library, user, nav) { name -> nav.playlist(vm.user.createPlaylist(name)) }
        }
        composable(Routes.FAVORITES) {
            val favs = remember(library, user.favorites) { user.favorites.reversed().mapNotNull { library.byId[it] } }
            TrackListScreen("Favorites", favs, nav, "No Favorites", "Tap ☆ on Now Playing or Favorite from any song's ⋯ menu.")
        }
        composable(Routes.MUSIC_FOLDERS) {
            MusicFoldersScreen(
                settings = settings,
                allFolders = allFolders,
                onboarding = false,
                onSave = { f, all -> vm.setFolders(f, all) },
                onBack = { nav.back() },
            )
        }
        composable(Routes.ALBUM, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            AlbumDetailScreen(library.albumsById[id], library, nav)
        }
        composable(Routes.ARTIST, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            ArtistDetailScreen(entry.arguments?.getString("name").orEmpty(), library, nav)
        }
        composable(Routes.GENRE, arguments = listOf(navArgument("name") { type = NavType.StringType })) { entry ->
            val name = entry.arguments?.getString("name").orEmpty()
            val genre = library.genres.firstOrNull { it.name == name }
            TrackListScreen(name, genre?.tracks.orEmpty(), nav, "No Songs", "Nothing in this genre right now.")
        }
        composable(Routes.FOLDER, arguments = listOf(navArgument("path") { type = NavType.StringType })) { entry ->
            val path = entry.arguments?.getString("path").orEmpty()
            val folder = library.folders.firstOrNull { it.path == path }
            TrackListScreen(
                path.substringAfterLast('/'), folder?.tracks.orEmpty(), nav,
                "Empty Folder", "This folder has no songs in your library.",
                subtitle = { it.fileName },
            )
        }
        composable(Routes.PLAYLIST, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            PlaylistDetailScreen(
                playlistId = id,
                library = library,
                user = user,
                nav = nav,
                onRename = { vm.user.renamePlaylist(id, it) },
                onDelete = {
                    vm.user.deletePlaylist(id)
                    nav.back()
                },
                onRemoveAt = { vm.user.removeFromPlaylist(id, it) },
                onMove = { from, to -> vm.user.movePlaylistItem(id, from, to) },
            )
        }
    }
}

/** Apple-style tab bar, 20% shorter than the Material default (64 dp instead of 80 dp). */
@Composable
private fun BottomTabs(current: Tab, onSelect: (Tab) -> Unit) {
    val onSelectState = rememberUpdatedState(onSelect)
    Column(Modifier.fillMaxWidth().background(Cadence.colors.bar)) {
        HorizontalDivider(thickness = 0.5.dp, color = Cadence.colors.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = tab == current
                val color = if (isSelected) MaterialTheme.colorScheme.primary else Cadence.colors.secondaryText
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // React on touch-down, so the switch starts before the finger lifts.
                        .pointerInput(tab) {
                            detectTapGestures(onPress = { onSelectState.value(tab) })
                        }
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            selected = isSelected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = color, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color)
                }
            }
        }
    }
}

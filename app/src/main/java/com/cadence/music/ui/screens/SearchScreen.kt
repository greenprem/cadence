@file:OptIn(ExperimentalLayoutApi::class)

package com.cadence.music.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cadence.music.data.Library
import com.cadence.music.ui.Nav
import com.cadence.music.ui.components.AlbumShelf
import com.cadence.music.ui.components.Artwork
import com.cadence.music.ui.components.EmptyState
import com.cadence.music.ui.components.LocalTrackActions
import com.cadence.music.ui.components.ScreenList
import com.cadence.music.ui.components.SectionHeader
import com.cadence.music.ui.components.TrackRow
import com.cadence.music.ui.theme.Cadence

private val TileColors = listOf(
    Color(0xFFE5484D) to Color(0xFFB0233B),
    Color(0xFF6E56CF) to Color(0xFF3E2A8C),
    Color(0xFF0091FF) to Color(0xFF0B5CAD),
    Color(0xFF30A46C) to Color(0xFF1B6B47),
    Color(0xFFF76808) to Color(0xFFB34700),
    Color(0xFFD6409F) to Color(0xFF8E1F66),
    Color(0xFF12A594) to Color(0xFF0B6B60),
    Color(0xFF8E4EC6) to Color(0xFF5B2A86),
)

@Composable
fun SearchScreen(library: Library, nav: Nav, focusRequests: Int = 0) {
    val actions = LocalTrackActions.current
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()

    // Lower-cased once per library instead of for every item on every keystroke.
    val index = remember(library) { SearchIndex(library) }
    val artists = remember(index, q) { if (q.isEmpty()) emptyList() else index.artists.filter { it.second.contains(q) }.map { it.first }.take(6) }
    val albums = remember(index, q) {
        if (q.isEmpty()) emptyList() else index.albums.filter { it.second.contains(q) }.map { it.first }.take(12)
    }
    val songs = remember(index, q) {
        if (q.isEmpty()) emptyList() else index.tracks.asSequence().filter { it.second.contains(q) }.map { it.first }.take(100).toList()
    }

    // Tapping the Search tab again jumps to the field and opens the keyboard; once more closes it.
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    val imeVisible by rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(focusRequests) {
        if (focusRequests == 0) return@LaunchedEffect
        if (focused && imeVisible) {
            keyboard?.hide()
            focusManager.clearFocus()
        } else {
            listState.scrollToItem(0)
            withFrameNanos { }
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
    }

    ScreenList("Search", state = listState) {
        item {
            SearchField(
                query, { query = it },
                Modifier.focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
            )
        }

        if (q.isEmpty()) {
            val tiles = library.genres.ifEmpty { null }
            if (tiles != null) {
                item { SectionHeader("Browse Genres") }
                items(tiles.chunked(2), key = { "g-" + it.first().name }) { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { genre ->
                            val (a, b) = TileColors[Math.floorMod(genre.name.hashCode(), TileColors.size)]
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(a, b)))
                                    .clickable { nav.genre(genre.name) }
                                    .padding(12.dp),
                                contentAlignment = Alignment.BottomStart,
                            ) {
                                Text(genre.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                item { EmptyState(Icons.Rounded.Search, "Search Your Library", "Find songs, albums and artists in your music folders.") }
            }
            return@ScreenList
        }

        if (artists.isEmpty() && albums.isEmpty() && songs.isEmpty()) {
            item { EmptyState(Icons.Rounded.Search, "No Results", "Try a different search.") }
            return@ScreenList
        }
        if (artists.isNotEmpty()) {
            item { SectionHeader("Artists") }
            items(artists, key = { "a-" + it.name }) { artist ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.artist(artist.name) }.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(artist.albums.firstOrNull()?.cover, Modifier.size(48.dp), shape = CircleShape)
                    Spacer(Modifier.width(14.dp))
                    Text(artist.name, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Rounded.ChevronRight, null, tint = Cadence.colors.tertiaryText)
                }
            }
        }
        if (albums.isNotEmpty()) {
            item { SectionHeader("Albums") }
            item { AlbumShelf(albums, { nav.album(it.id) }) }
        }
        if (songs.isNotEmpty()) {
            item { SectionHeader("Songs") }
            itemsIndexed(songs, key = { _, t -> "s-${t.id}" }) { i, t ->
                TrackRow(t, onClick = { actions.play(songs, i) })
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, fieldModifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Cadence.colors.fill)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Cadence.colors.secondaryText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) Text("Artists, Songs, Albums", color = Cadence.colors.secondaryText, fontSize = 16.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }
            if (value.isNotEmpty()) {
                Icon(
                    Icons.Rounded.Cancel, contentDescription = "Clear", tint = Cadence.colors.secondaryText,
                    modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onChange("") },
                )
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 0.dp, color = Color.Transparent)
    }
}

private class SearchIndex(library: Library) {
    val artists = library.artists.map { it to it.name.lowercase() }
    val albums = library.albums.map { it to (it.title + "\u0000" + it.artist).lowercase() }
    val tracks = library.tracks.map { it to (it.title + "\u0000" + it.artist + "\u0000" + it.album).lowercase() }
}

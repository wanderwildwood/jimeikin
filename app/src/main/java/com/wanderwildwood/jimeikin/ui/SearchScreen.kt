package com.wanderwildwood.jimeikin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.jimeikin.R

/** Lightweight UI model for a YouTube Music artist search result. */
data class OnlineArtistUiModel(
    val browseId: String,
    val name: String,
)

/**
 * One list: songs, albums and artists together, the phone's first, then the server's, then
 * YouTube's - see [buildSearchResults]. The library's part is there as soon as the search is
 * made, and with no signal; YouTube's joins it when it comes. The ⋮ on the search bar chooses
 * which of the three are looked in; with YouTube off it is not asked at all.
 */
@Composable
fun SearchScreen(
    isSearching: Boolean,
    errorMessage: String?,
    results: List<SearchResult>,
    /** A plain line in place of results: a link that could not be read, or cannot be played here. */
    notice: String? = null,
    onPlaySongClick: (SongUiModel) -> Unit,
    onAlbumClick: (AlbumUiModel) -> Unit,
    onLibraryArtistClick: (ArtistUiModel) -> Unit,
    onArtistClick: (OnlineArtistUiModel) -> Unit,
    currentSongId: String? = null,
    librarySongIds: Set<String> = emptySet(),
    onAddToPlaylistClick: (SongUiModel) -> Unit = {},
    onPlayNextClick: (SongUiModel) -> Unit = {},
    onAddToQueueClick: (SongUiModel) -> Unit = {},
    onRemoveFromLibraryClick: (SongUiModel) -> Unit = {},
    onDeleteClick: (SongUiModel) -> Unit = {},
    onKeepOnPhoneClick: (SongUiModel) -> Unit = {},
    /** The sources there are to choose from. With fewer than two there is nothing to choose. */
    sourcesOffered: List<SearchSource> = emptyList(),
    sourcesOn: Set<SearchSource> = emptySet(),
    onToggleSource: (SearchSource) -> Unit = {},
) {
    val albumKind = stringResource(R.string.library_search_kind_album)
    // With one source there is nothing to choose, and no ⋮.
    if (sourcesOffered.size > 1) {
        TopBarActions { SearchSourcesMenu(sourcesOffered, sourcesOn, onToggleSource) }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        PagedColumnMMD(contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp)) {
            if (isSearching) {
                item {
                    TextMMD(text = stringResource(R.string.library_search_searching))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (notice != null) {
                item {
                    TextMMD(text = notice)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (errorMessage != null) {
                item {
                    TextMMD(text = stringResource(R.string.library_search_failed))
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(results.size) { index ->
                val showDivider = index < results.lastIndex
                when (val result = results[index]) {
                    is SearchResult.Song -> {
                        val song = result.song
                        SongItem(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            onClick = { onPlaySongClick(song) },
                            onAddToPlaylist = { onAddToPlaylistClick(song) },
                            onPlayNext = { onPlayNextClick(song) },
                            onAddToQueue = { onAddToQueueClick(song) },
                            onRemoveFromLibrary = { onRemoveFromLibraryClick(song) },
                            onDelete = { onDeleteClick(song) },
                            onKeepOnPhone = { onKeepOnPhoneClick(song) },
                            showDivider = showDivider,
                            isInLibrary = librarySongIds.contains(song.id),
                        )
                    }
                    is SearchResult.Album -> AlbumItem(
                        album = result.album,
                        onClick = { onAlbumClick(result.album) },
                        showDivider = showDivider,
                        kindLabel = result.kindLabel ?: albumKind,
                    )
                    is SearchResult.LibraryArtist -> ArtistItem(
                        artist = result.artist,
                        onClick = { onLibraryArtistClick(result.artist) },
                        showDivider = showDivider,
                    )
                    is SearchResult.OnlineArtist -> SearchArtistItem(
                        artist = result.artist,
                        onClick = { onArtistClick(result.artist) },
                        showDivider = showDivider,
                    )
                }
            }

            if (!isSearching && errorMessage == null && notice == null && results.isEmpty()) {
                item {
                    TextMMD(text = stringResource(R.string.library_search_nothing))
                }
            }
        }
    }
}

/**
 * The ⋮ on the search bar and what it opens. The Kompakt has no menu key, so the app brings its
 * own. One item, "Search in…", and behind it a short checklist of the sources there are; each
 * change takes effect at once, and Done only closes it. A row of toggles above the results did
 * this before; it cost a band of the screen on every search for a choice made once.
 */
@Composable
private fun SearchSourcesMenu(
    offered: List<SearchSource>,
    on: Set<SearchSource>,
    onToggle: (SearchSource) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var dialogOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.More,
                contentDescription = stringResource(R.string.search_cd_options),
            )
        }
        DropdownMenuMMD(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItemMMD(
                text = { TextMMD(stringResource(R.string.search_sources_menu)) },
                onClick = {
                    menuOpen = false
                    dialogOpen = true
                },
            )
        }
    }
    if (dialogOpen) {
        EInkDialog(onDismiss = { dialogOpen = false }) {
            TextMMD(
                text = stringResource(R.string.search_sources_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            offered.forEach { source ->
                val isOn = source in on
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = isOn, role = Role.Checkbox, onValueChange = { onToggle(source) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The row takes the press; the box only shows the state.
                    CheckboxMMD(checked = isOn, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    TextMMD(
                        text = stringResource(
                            when (source) {
                                SearchSource.PHONE -> R.string.search_source_phone
                                SearchSource.SERVER -> R.string.search_source_server
                                SearchSource.ONLINE -> R.string.search_source_online
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ButtonMMD(onClick = { dialogOpen = false }, modifier = Modifier.fillMaxWidth()) {
                TextMMD(text = stringResource(R.string.main_button_done))
            }
        }
    }
}

@Composable
private fun SearchArtistItem(
    artist: OnlineArtistUiModel,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        TextMMD(
            text = artist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))
        SubtitleLine(text = stringResource(R.string.library_search_result_artist), origin = Origin.ONLINE_SEARCH)

        Spacer(modifier = Modifier.height(12.dp))

        if (showDivider) {
            DottedRule(start = 0.dp)
        }
    }
}

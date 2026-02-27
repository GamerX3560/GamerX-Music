package com.gamerx.gamerx_music.ui.menu

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.gamerx.innertube.models.SongItem
import com.gamerx.gamerx_music.LocalDatabase
import com.gamerx.gamerx_music.LocalListenTogetherManager
import com.gamerx.gamerx_music.LocalPlayerConnection
import com.gamerx.gamerx_music.R
import com.gamerx.gamerx_music.extensions.toMediaItem
import com.gamerx.gamerx_music.models.toMediaMetadata
import com.gamerx.gamerx_music.playback.queues.ListQueue
import com.gamerx.gamerx_music.ui.component.ListMenu
import com.gamerx.gamerx_music.ui.component.ListMenuItem
import java.time.LocalDateTime

@Composable
fun YouTubeSongSelectionMenu(
    navController: NavController,
    selection: List<SongItem>,
    onDismiss: () -> Unit,
    onExitSelectionMode: () -> Unit,
    showPlayNextButton : Boolean = true,
    onHistoryRemoved: () -> Unit = {}
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    AddToPlaylistDialog(
        navController = navController,
        isVisible = showChoosePlaylistDialog,
        onGetSong = {
            val mediaMetadata = selection.map {
                it.toMediaMetadata()
            }
            database.transaction {
                mediaMetadata.forEach(::insert)
            }
            selection.map { it.id }
        },
        onDismiss = { showChoosePlaylistDialog = false },
    )

    ListMenu(
        contentPadding =
        PaddingValues(
            start = 8.dp,
            top = 8.dp,
            end = 8.dp,
            bottom = 8.dp + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
        ),
    ) {
        if (listenTogetherManager != null && listenTogetherManager.isInRoom && !listenTogetherManager.isHost) {
            ListMenuItem(
                icon = R.drawable.queue_music,
                title = R.string.suggest_to_host
            ) {
                selection.forEach { song ->
                    val durationMs =
                        if ((song.duration ?: return@ListMenuItem) > 0) song.duration?.toLong()
                            ?.times(1000) ?: return@ListMenuItem else 180000L
                    val trackInfo = com.gamerx.gamerx_music.listentogether.TrackInfo(
                        id = song.id,
                        title = song.title,
                        artist = song.artists.joinToString(", ") { it.name },
                        album = song.album?.id,
                        duration = durationMs,
                        thumbnail = song.thumbnail
                    )
                    listenTogetherManager.suggestTrack(trackInfo)
                    onDismiss()
                }
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.play,
            title = R.string.play,
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    items = selection.map { it.toMediaItem() },
                ),
            )
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.shuffle,
            title = R.string.shuffle,
        ) {
            onDismiss()
            playerConnection.playQueue(
                ListQueue(
                    items = selection.shuffled().map { it.toMediaItem() },
                ),
            )
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        if (showPlayNextButton) {
            ListMenuItem(
                icon = R.drawable.playlist_play,
                title = R.string.play_next,
            ) {
                onDismiss()
                playerConnection.playNext(selection.map { it.toMediaItem() })
                onExitSelectionMode()
            }
            item {
                HorizontalDivider()
            }
        }
        ListMenuItem(
            icon = R.drawable.queue_music,
            title = R.string.add_to_queue,
        ) {
            onDismiss()
            playerConnection.addToQueue(selection.map { it.toMediaItem() })
            onExitSelectionMode()
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.playlist_add,
            title = R.string.add_to_playlist,
        ) {
            showChoosePlaylistDialog = true
        }
        item {
            HorizontalDivider()
        }
        ListMenuItem(
            icon = R.drawable.library_add,
            title = R.string.add_to_library,
        ) {
            database.query {
                selection.forEach { song ->
                    insert(song.toMediaMetadata())
                }
                selection.forEach { song ->
                    inLibrary(song.id, LocalDateTime.now())
                }
            }
            onDismiss()
        }
    }
}

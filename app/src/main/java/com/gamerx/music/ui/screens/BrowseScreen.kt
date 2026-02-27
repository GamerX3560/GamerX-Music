package com.gamerx.gamerx_music.ui.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gamerx.gamerx_music.LocalPlayerAwareWindowInsets
import com.gamerx.gamerx_music.LocalPlayerConnection
import com.gamerx.gamerx_music.constants.GridThumbnailHeight
import com.gamerx.gamerx_music.ui.component.IconButton
import com.gamerx.gamerx_music.ui.component.LocalMenuState
import com.gamerx.gamerx_music.ui.component.YouTubeGridItem
import com.gamerx.gamerx_music.ui.component.shimmer.GridItemPlaceHolder
import com.gamerx.gamerx_music.ui.component.shimmer.ShimmerHost
import com.gamerx.gamerx_music.ui.menu.YouTubeAlbumMenu
import com.gamerx.gamerx_music.ui.menu.YouTubeArtistMenu
import com.gamerx.gamerx_music.ui.menu.YouTubePlaylistMenu
import com.gamerx.gamerx_music.ui.utils.backToMain
import com.gamerx.gamerx_music.viewmodels.BrowseViewModel
import com.gamerx.innertube.models.AlbumItem
import com.gamerx.innertube.models.ArtistItem
import com.gamerx.innertube.models.PlaylistItem
import com.gamerx.innertube.models.SongItem
import com.gamerx.gamerx_music.R
import com.gamerx.gamerx_music.constants.AlbumViewTypeKey
import com.gamerx.gamerx_music.constants.GridCellSize
import com.gamerx.gamerx_music.constants.GridCellSizeKey
import com.gamerx.gamerx_music.constants.LibraryViewType
import com.gamerx.gamerx_music.constants.SmallGridThumbnailHeight
import com.gamerx.gamerx_music.ui.component.LazyColumnScrollbar
import com.gamerx.gamerx_music.ui.component.LazyVerticalGridScrollbar
import com.gamerx.gamerx_music.ui.component.YouTubeListItem
import com.gamerx.gamerx_music.ui.component.shimmer.ListItemPlaceHolder
import com.gamerx.gamerx_music.utils.rememberEnumPreference

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    browseId: String?,
    viewModel: BrowseViewModel = hiltViewModel(
        key = browseId,
    ),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val lazyGridState = rememberLazyGridState()
    val gridChecker by remember {
        derivedStateOf {
            lazyGridState.firstVisibleItemIndex > 0
        }
    }

    val lazyListState = rememberLazyListState()

    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val gridCellSize by rememberEnumPreference(GridCellSizeKey, GridCellSize.BIG)
    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)

    val title by viewModel.title.collectAsState()
    val items by viewModel.items.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pullToRefresh(
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                onRefresh = viewModel::refresh
            ),
        contentAlignment = Alignment.TopStart
    ) {
        when (viewType) {
            LibraryViewType.LIST -> {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    items?.let { items ->
                        items(
                            items = items,
                            key = { it.id }
                        ) { item ->
                            YouTubeListItem(
                                item = item,
                                isPlaying = isPlaying,
                                isActive = when (item) {
                                    is SongItem -> mediaMetadata?.id == item.id
                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                    is PlaylistItem -> mediaMetadata?.playlist?.id == item.id
                                    else -> false
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            when (item) {
                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}?author=${item.author?.name}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                else -> {}
                                            }
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                when (item) {
                                                    is AlbumItem ->
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )

                                                    is PlaylistItem -> {
                                                        YouTubePlaylistMenu(
                                                            playlist = item,
                                                            coroutineScope = coroutineScope,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }

                                                    is ArtistItem -> {
                                                        YouTubeArtistMenu(
                                                            artist = item,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }

                                                    else -> {}
                                                }
                                            }
                                        }
                                    )
                            )
                        }
                    }
                    if (items?.isNotEmpty() != true) {
                        item(key = "loading") {
                            ShimmerHost{
                                repeat(8) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }
                }
                LazyColumnScrollbar(
                    visible = lazyChecker,
                    state = lazyListState
                )
            }

            LibraryViewType.GRID -> {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(
                        minSize = when (gridCellSize) {
                            GridCellSize.SMALL -> SmallGridThumbnailHeight
                            GridCellSize.BIG -> GridThumbnailHeight
                        } + 24.dp
                    ),
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
                ) {
                    items?.let { items ->
                        items(
                            items = items,
                            key = { it.id }
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isPlaying = isPlaying,
                                fillMaxWidth = true,
                                isActive = when (item) {
                                    is SongItem -> mediaMetadata?.id == item.id
                                    is AlbumItem -> mediaMetadata?.album?.id == item.id
                                    is PlaylistItem -> mediaMetadata?.playlist?.id == item.id
                                    else -> false
                                },
                                navController = navController,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            when (item) {
                                                is AlbumItem -> navController.navigate("album/${item.id}")
                                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}?author=${item.author?.name}")
                                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                                else -> {}
                                            }
                                        },
                                        onLongClick = {
                                            menuState.show {
                                                when (item) {
                                                    is AlbumItem ->
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )

                                                    is PlaylistItem -> {
                                                        YouTubePlaylistMenu(
                                                            playlist = item,
                                                            coroutineScope = coroutineScope,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }

                                                    is ArtistItem -> {
                                                        YouTubeArtistMenu(
                                                            artist = item,
                                                            onDismiss = menuState::dismiss
                                                        )
                                                    }

                                                    else -> {}
                                                }
                                            }
                                        }
                                    )
                            )
                        }

                        if (items.isEmpty()) {
                            items(8) {
                                ShimmerHost {
                                    GridItemPlaceHolder(fillMaxWidth = true)
                                }
                            }
                        }
                    }
                }
                LazyVerticalGridScrollbar(
                    visible = gridChecker,
                    state = lazyGridState
                )
            }
        }
        if (items?.isEmpty() != true) {
            Indicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
            )
        }
    }

    CenterAlignedTopAppBar(
        title = { Text(title ?: "") },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        },
        actions = {
            if (items?.isEmpty() != true) {
                androidx.compose.material3.IconButton(
                    onClick = {
                        viewType = viewType.toggle()
                    }
                ) {
                    Icon(
                        painter = painterResource(
                            when (viewType) {
                                LibraryViewType.LIST -> R.drawable.list
                                LibraryViewType.GRID -> R.drawable.grid_view
                            }
                        ),
                        contentDescription = null
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

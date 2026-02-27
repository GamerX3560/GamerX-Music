package com.gamerx.gamerx_music.ui.screens.playlist

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastSumBy
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.gamerx.gamerx_music.LocalDatabase
import com.gamerx.gamerx_music.LocalPlayerAwareWindowInsets
import com.gamerx.gamerx_music.LocalPlayerConnection
import com.gamerx.gamerx_music.R
import com.gamerx.gamerx_music.constants.AlbumThumbnailSize
import com.gamerx.gamerx_music.constants.ListItemHeight
import com.gamerx.gamerx_music.constants.SongSortDescendingKey
import com.gamerx.gamerx_music.constants.SongSortType
import com.gamerx.gamerx_music.constants.SongSortTypeKey
import com.gamerx.gamerx_music.constants.ThumbnailCornerRadius
import com.gamerx.gamerx_music.db.entities.Playlist
import com.gamerx.gamerx_music.db.entities.PlaylistEntity
import com.gamerx.gamerx_music.db.entities.Song
import com.gamerx.gamerx_music.extensions.move
import com.gamerx.gamerx_music.extensions.toMediaItem
import com.gamerx.gamerx_music.extensions.togglePlayPause
import com.gamerx.gamerx_music.playback.queues.ListQueue
import com.gamerx.gamerx_music.ui.component.*
import com.gamerx.gamerx_music.ui.menu.AutoPlaylistMenu
import com.gamerx.gamerx_music.ui.menu.SongMenu
import com.gamerx.gamerx_music.ui.menu.SongSelectionMenu
import com.gamerx.gamerx_music.ui.utils.backToMain
import com.gamerx.gamerx_music.utils.makeTimeString
import com.gamerx.gamerx_music.utils.rememberEnumPreference
import com.gamerx.gamerx_music.utils.rememberPreference
import com.gamerx.gamerx_music.utils.rememberVoiceInput
import com.gamerx.gamerx_music.utils.scanLocal
import com.gamerx.gamerx_music.utils.syncDB
import com.gamerx.gamerx_music.viewmodels.AutoPlaylistLocalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPlaylistLocalScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AutoPlaylistLocalViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    var isScannerActive by remember { mutableStateOf(false) }
    val mediaPermissionLevel =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    var refetchIconDegree by remember { mutableFloatStateOf(0f) }

    val rotationAnimation by animateFloatAsState(
        targetValue = refetchIconDegree,
        animationSpec = tween(durationMillis = 800),
        label = ""
    )

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val localSongs by viewModel.localSongs.collectAsState()

    val localPlaylist = Playlist(
        playlist = PlaylistEntity(
            id = "local",
            name = stringResource(R.string.local)
        ),
        songCount = localSongs.size,
        songThumbnails = emptyList()
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()
    val lazyChecker by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        SongSortTypeKey,
        SongSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val mutableSongs = remember {
        mutableStateListOf<Song>()
    }
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            mutableSongs.move(from.index - 2, to.index - 2)
        },
        lazyListState = lazyListState,
        scrollThresholdPadding = WindowInsets.systemBars.add(
            WindowInsets(
                top = ListItemHeight,
                bottom = ListItemHeight
            )
        ).asPaddingValues()
    )

    val likeLength = remember(localSongs) {
        localSongs.fastSumBy { it.song.duration }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf(TextFieldValue("")) }
    val startVoiceInput = rememberVoiceInput(
        onResult = { recognizedText ->
            searchQuery = TextFieldValue(recognizedText)
        }
    )
    val focusRequester = remember { FocusRequester() }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberSaveable(
        saver = listSaver<MutableList<String>, String>(
            save = { it.toList() },
            restore = { it.toMutableStateList() }
        )
    ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    val onExitSearchingMode = {
        isSearching = false
        searchQuery = TextFieldValue("")
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    } else if (isSearching) {
        BackHandler(onBack = onExitSearchingMode)
    }


    LaunchedEffect(inSelectMode) {
        backStackEntry?.savedStateHandle?.set("inSelectMode", inSelectMode)
    }

    LaunchedEffect(Unit) {
        if (isScannerActive) {
            return@LaunchedEffect
        }
        if (context.checkSelfPermission(mediaPermissionLevel)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                context as Activity,
                arrayOf(mediaPermissionLevel), PackageManager.PERMISSION_GRANTED
            )

            return@LaunchedEffect
        }
        isScannerActive = true
        coroutineScope.launch(Dispatchers.IO) {
            val directoryStructure = scanLocal(context).value
            syncDB(database, directoryStructure.toList())
            isScannerActive = false
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    val searchQueryStr = searchQuery.text.trim()
    val filteredSongs = if (searchQueryStr.isEmpty()) {
        localSongs
    } else {
        localSongs.filter { song ->
            song.song.title.contains(searchQueryStr, ignoreCase = true) ||
                    song.artists.joinToString("")
                        .contains(searchQueryStr, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            if (filteredSongs.isEmpty() && !isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.playlist_is_empty)
                    )
                }
            }
            if (filteredSongs.isEmpty() && isSearching) {
                item {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found)
                    )
                }
            } else {
                if (filteredSongs.isNotEmpty() && !isSearching) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(AlbumThumbnailSize)
                                    .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                    .align(alignment = Alignment.CenterHorizontally)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        shape = RoundedCornerShape(ThumbnailCornerRadius)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = null,
                                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(110.dp)
                                        .clip(RoundedCornerShape(ThumbnailCornerRadius))
                                        .align(Alignment.Center)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AutoResizeText(
                                    text = stringResource(R.string.local),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSizeRange = FontSizeRange(16.sp, 22.sp),
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .clickable(
                                            onClick = {
                                                menuState.show {
                                                    AutoPlaylistMenu(
                                                        playlist = localPlaylist,
                                                        navController = navController,
                                                        thumbnail = null,
                                                        iconThumbnail = Icons.Rounded.MusicNote,
                                                        songs = localSongs,
                                                        coroutineScope = coroutineScope,
                                                        onDismiss = menuState::dismiss,
                                                        showSyncLocalSongsButton = true,
                                                        showM3UBackupButton = false,
                                                        syncUtils = null
                                                    )
                                                }
                                            }
                                        )
                                )
                                Text(
                                    text = makeTimeString(likeLength * 1000L),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Normal
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = {
                                            refetchIconDegree -= 360
                                            if (isScannerActive) {
                                                return@Button
                                            }
                                            if (context.checkSelfPermission(mediaPermissionLevel)
                                                != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                requestPermissions(
                                                    context as Activity,
                                                    arrayOf(mediaPermissionLevel),
                                                    PackageManager.PERMISSION_GRANTED
                                                )
                                                return@Button
                                            }
                                            isScannerActive = true
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val directoryStructure =
                                                    scanLocal(context).value
                                                syncDB(
                                                    database,
                                                    directoryStructure.toList(),
                                                )
                                                isScannerActive = false
                                                snackbarHostState.showSnackbar(
                                                    context.getString(
                                                        R.string.sync_local_songs_toast
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.sync),
                                            contentDescription = null,
                                            modifier = Modifier.graphicsLayer(rotationZ = rotationAnimation)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            playerConnection.addToQueue(
                                                items = localSongs.map { it.toMediaItem() },
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.queue_music),
                                            contentDescription = null
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.local),
                                                    items = localSongs.map { it.toMediaItem() }
                                                )
                                            )
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.play),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.play))
                                    }
                                    Button(
                                        onClick = {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.local),
                                                    items = localSongs.shuffled()
                                                        .map { it.toMediaItem() }
                                                )
                                            )
                                        },
                                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.shuffle),
                                            contentDescription = null,
                                            modifier = Modifier.size(ButtonDefaults.IconSize)
                                        )
                                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.shuffle))
                                    }
                                }
                            }
                        }
                    }
                }
                if (filteredSongs.size > 1) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            SortHeader(
                                sortType = sortType,
                                sortDescending = sortDescending,
                                onSortTypeChange = onSortTypeChange,
                                onSortDescendingChange = onSortDescendingChange,
                                sortTypeText = { sortType ->
                                    when (sortType) {
                                        SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                        SongSortType.NAME -> R.string.sort_by_name
                                        SongSortType.ARTIST -> R.string.sort_by_artist
                                        SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                    }
                                }
                            )
                        }
                    }
                }
                if (filteredSongs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = pluralStringResource(
                                    R.plurals.n_song,
                                    filteredSongs.size,
                                    filteredSongs.size
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
                itemsIndexed(
                    filteredSongs,
                    key = { _, song -> song.id }
                ) { _, songWrapper ->
                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(songWrapper.id)
                        } else {
                            selection.remove(songWrapper.id)
                        }
                    }
                    ReorderableItem(
                        state = reorderableState,
                        key = filteredSongs
                    ) {
                        SongListItem(
                            song = songWrapper,
                            isActive = songWrapper.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showLikedIcon = true,
                            showInLibraryIcon = true,
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = selection.contains(songWrapper.id),
                                        onCheckedChange = { checked ->
                                            if (checked) selection.add(songWrapper.id)
                                            else selection.remove(songWrapper.id)
                                        }
                                    )
                                } else {
                                    IconButton(onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = songWrapper,
                                                navController = navController,
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    }) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(songWrapper.id !in selection)
                                        } else if (songWrapper.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.local),
                                                    items = localSongs.map { it.toMediaItem() },
                                                    startIndex = localSongs.indexOfFirst { it.song.id == songWrapper.id }
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    }
                                )
                                .animateItem()
                        )
                    }
                }
            }
        }
        LazyColumnScrollbar(
            visible = lazyChecker,
            state = lazyListState
        )
        HideOnScrollFAB(
            visible = lazyChecker && !isSearching && !inSelectMode,
            lazyListState = lazyListState,
            icon = R.drawable.play,
            onClick = {
                playerConnection.playQueue(
                    ListQueue(
                        title = context.getString(R.string.local),
                        items = localSongs.map { it.toMediaItem() }
                    )
                )
            }
        )
        if (inSelectMode) {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        pluralStringResource(
                            R.plurals.n_selected,
                            selection.size,
                            selection.size
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onExitSelectionMode) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    Checkbox(
                        checked = selection.size == localSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == localSongs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(localSongs.map { it.id })
                            }
                        }
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                SongSelectionMenu(
                                    navController = navController,
                                    selection = selection.mapNotNull { songId ->
                                        localSongs.find { it.id == songId }
                                    },
                                    showDownloadButton = false,
                                    onDismiss = menuState::dismiss,
                                    onExitSelectionMode = onExitSelectionMode
                                )
                            }
                        }
                    ) {
                        Icon(
                            painterResource(R.drawable.more_vert),
                            contentDescription = null
                        )
                    }
                }
            )
        } else {
            CenterAlignedTopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            trailingIcon = {
                                if (searchQuery.text.isEmpty()) {
                                    IconButton(
                                        onClick = startVoiceInput
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.mic),
                                            contentDescription = null
                                        )
                                    }
                                }
                                if (searchQuery.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = TextFieldValue("") }
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                        )
                    } else if (lazyChecker) {
                        Text(stringResource(R.string.local))
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSearching) {
                                isSearching = false
                                searchQuery = TextFieldValue()
                            } else {
                                navController.navigateUp()
                            }
                        },
                        onLongClick = {
                            if (!isSearching) {
                                navController.backToMain()
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.arrow_back
                            ),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (filteredSongs.isNotEmpty() && !isSearching && !inSelectMode) {
                        IconButton(
                            onClick = { isSearching = true }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.search),
                                contentDescription = null
                            )
                        }
                        IconButton(
                            onClick = {
                                menuState.show {
                                    AutoPlaylistMenu(
                                        playlist = localPlaylist,
                                        navController = navController,
                                        thumbnail = null,
                                        iconThumbnail = Icons.Rounded.MusicNote,
                                        songs = localSongs,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                        showSyncLocalSongsButton = true,
                                        showM3UBackupButton = false,
                                        syncUtils = null
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    }
                },
                scrollBehavior = if (!isSearching) scrollBehavior else null
            )
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}
package com.gamerx.innertube.pages

import com.gamerx.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)

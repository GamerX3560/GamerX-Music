package com.gamerx.gamerx_music.models

import com.gamerx.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)

package com.gamerx.innertube.pages

import com.gamerx.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
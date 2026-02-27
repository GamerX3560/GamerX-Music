package com.gamerx.gamerx_music.models

import com.gamerx.innertube.models.YTItem
import com.gamerx.gamerx_music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)

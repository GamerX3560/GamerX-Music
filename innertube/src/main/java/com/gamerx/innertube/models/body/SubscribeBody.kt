package com.gamerx.innertube.models.body

import com.gamerx.innertube.models.Context
import kotlinx.serialization.Serializable
@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
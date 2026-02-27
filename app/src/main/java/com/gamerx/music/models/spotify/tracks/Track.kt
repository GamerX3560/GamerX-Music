package com.gamerx.gamerx_music.models.spotify.tracks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Track(
    @SerialName("track")
    val trackItem: TrackItem
)
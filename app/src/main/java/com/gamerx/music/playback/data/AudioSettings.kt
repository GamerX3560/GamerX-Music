package com.gamerx.gamerx_music.playback.data

import com.gamerx.gamerx_music.db.entities.FormatEntity

data class AudioSettings(
    val volume: Float,
    val muted: Boolean,
    val normalizeAudio: Boolean,
    val format: FormatEntity?
)
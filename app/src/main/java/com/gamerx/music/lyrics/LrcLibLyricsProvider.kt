package com.gamerx.gamerx_music.lyrics

import android.content.Context
import com.gamerx.lrclib.LrcLib
import com.gamerx.gamerx_music.constants.EnableLrcLibKey
import com.gamerx.gamerx_music.utils.dataStore
import com.gamerx.gamerx_music.utils.get

/**
 * Source: https://github.com/Malopieds/GamerX Music
 */
object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore[EnableLrcLibKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
    ): Result<String> = LrcLib.getLyrics(title, artist, duration)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        LrcLib.getAllLyrics(title, artist, duration, null, callback)
    }
}

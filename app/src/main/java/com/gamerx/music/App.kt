package com.gamerx.gamerx_music

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.CachePolicy
import com.gamerx.innertube.YouTube
import com.gamerx.innertube.models.YouTubeLocale
import com.gamerx.kugou.KuGou
import com.gamerx.gamerx_music.constants.ContentCountryKey
import com.gamerx.gamerx_music.constants.ContentLanguageKey
import com.gamerx.gamerx_music.constants.CountryCodeToName
import com.gamerx.gamerx_music.constants.InnerTubeCookieKey
import com.gamerx.gamerx_music.constants.LanguageCodeToName
import com.gamerx.gamerx_music.constants.MaxImageCacheSizeKey
import com.gamerx.gamerx_music.constants.ProxyEnabledKey
import com.gamerx.gamerx_music.constants.ProxyTypeKey
import com.gamerx.gamerx_music.constants.ProxyUrlKey
import com.gamerx.gamerx_music.constants.SYSTEM_DEFAULT
import com.gamerx.gamerx_music.constants.VisitorDataKey
import com.gamerx.gamerx_music.extensions.toEnum
import com.gamerx.gamerx_music.extensions.toInetSocketAddress
import com.gamerx.gamerx_music.utils.dataStore
import com.gamerx.gamerx_music.utils.get
import com.gamerx.gamerx_music.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.lang.Double.min
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {
    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[InnerTubeCookieKey] != null) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" }
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        } ?: YouTube.DEFAULT_VISITOR_DATA
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { rawCookie ->
                    val isLoggedIn: Boolean = rawCookie?.contains("SAPISID") ?: false
                    val cookie = if (isLoggedIn) rawCookie else null
                    try {
                        YouTube.cookie = cookie
                    } catch (e: Exception) {
                        Timber.e("Could not parse cookie. Clearing existing cookie. %s", e.message)
                        dataStore.edit { settings ->
                            settings[InnerTubeCookieKey] = ""
                        }
                    }
                }
        }
    }

    @SuppressLint("UsableSpace")
    override fun newImageLoader(): ImageLoader {
        val cacheSize = dataStore[MaxImageCacheSizeKey]

        return if (cacheSize == 0) {
            ImageLoader.Builder(this).crossfade(true).respectCacheHeaders(false)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                .diskCachePolicy(CachePolicy.DISABLED).build()
        } else {
            val maxSize = when {
                cacheSize == -1 -> {
                    val cacheDir = cacheDir.resolve("coil")
                    val usableSpace = cacheDir.usableSpace
                    min(usableSpace * 0.9, (2L * 1024 * 1024 * 1024).toDouble()).toLong()
                }
                else -> (cacheSize ?: 512) * 1024 * 1024L
            }

            ImageLoader.Builder(this).crossfade(true).respectCacheHeaders(false)
                .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P).diskCache(
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(maxSize)
                        .build()
                ).build()
        }
    }
}
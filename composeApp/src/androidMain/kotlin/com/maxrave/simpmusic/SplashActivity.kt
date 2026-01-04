package com.maxrave.simpmusic

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import com.gamerx.music.R

class SplashActivity : ComponentActivity() {
    private var player: androidx.media3.exoplayer.ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val playerView = findViewById<androidx.media3.ui.PlayerView>(R.id.splashPlayerView)
        player = androidx.media3.exoplayer.ExoPlayer.Builder(this).build()
        playerView.player = player

        val videoPath = "android.resource://" + packageName + "/" + R.raw.gx_music
        val mediaItem = androidx.media3.common.MediaItem.fromUri(Uri.parse(videoPath))
        player?.setMediaItem(mediaItem)
        player?.prepare()

        player?.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    navigateToMain()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                navigateToMain()
            }
        })

        player?.play()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}

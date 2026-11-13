package com.homeai.homecamtv

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.homeai.homecamtv.data.Camera
import com.homeai.homecamtv.data.CameraStore
import com.homeai.homecamtv.player.MjpegView

/**
 * Full-screen live view for a single camera.
 *
 * RTSP cameras play through ExoPlayer; MJPEG cameras render through
 * [MjpegView]. The player reconnects automatically when a stream drops.
 */
class PlaybackActivity : Activity() {

    private lateinit var camera: Camera
    private var player: ExoPlayer? = null
    private var mediaSource: MediaSource? = null

    private lateinit var playerView: PlayerView
    private lateinit var mjpegView: MjpegView
    private lateinit var titleView: TextView
    private lateinit var statusView: TextView

    private var retryAttempt = 0
    private val retryDelayMs = 2500L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playback)

        val id = intent.getStringExtra(EXTRA_CAMERA_ID)
        val found = id?.let { CameraStore.get(this).get(it) }
        if (found == null) {
            finish()
            return
        }
        camera = found

        playerView = findViewById(R.id.player_view)
        mjpegView = findViewById(R.id.mjpeg_view)
        titleView = findViewById(R.id.playback_title)
        statusView = findViewById(R.id.playback_status)

        titleView.text = camera.name.ifBlank { getString(R.string.unnamed_camera) }
        mjpegView.statusListener = { message -> showStatus(message) }

        if (camera.isRtsp) {
            mjpegView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            startRtsp()
        } else {
            playerView.visibility = View.GONE
            mjpegView.visibility = View.VISIBLE
            showStatus(getString(R.string.connecting))
            mjpegView.start(camera.streamUrl(), camera.username, camera.password)
            hideStatus()
        }
    }

    private fun startRtsp() {
        releasePlayer()
        showStatus(getString(R.string.connecting))

        val source: MediaSource = RtspMediaSource.Factory()
            .setForceUseRtpTcp(true)
            .setTimeoutMs(12_000)
            .createMediaSource(
                MediaItem.Builder()
                    .setUri(Uri.parse(camera.streamUrl()))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(camera.name)
                            .build()
                    )
                    .build()
            )
        mediaSource = source

        val newPlayer = ExoPlayer.Builder(this)
            .build()
            .apply {
                setMediaSource(source)
                playWhenReady = true
                addListener(playerListener)
                prepare()
            }
        player = newPlayer
        playerView.player = newPlayer
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    retryAttempt = 0
                    hideStatus()
                }
                Player.STATE_ENDED -> showStatus(getString(R.string.stream_ended))
                Player.STATE_BUFFERING -> if (retryAttempt == 0) showStatus(getString(R.string.buffering))
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            showStatus(getString(R.string.retrying))
            scheduleRetry()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) hideStatus()
        }
    }

    private fun scheduleRetry() {
        if (isFinishing || isDestroyed) return
        retryAttempt++
        playerView.postDelayed(
            { if (!isFinishing && !isDestroyed) startRtsp() },
            retryDelayMs * retryAttempt.coerceAtMost(4)
        )
    }

    private fun showStatus(message: String) {
        statusView.text = message
        statusView.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusView.visibility = View.GONE
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                player?.let {
                    if (it.isPlaying) it.pause() else it.play()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        mjpegView.stop()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        mediaSource = null
    }

    companion object {
        const val EXTRA_CAMERA_ID = "camera_id"
    }
}

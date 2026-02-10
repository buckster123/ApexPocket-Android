package com.apexaurum.pocket.cloud

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.apexaurum.pocket.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "MusicPlayer"

data class PlayerState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isBuffering: Boolean = false,
)

/**
 * Manages ExoPlayer lifecycle for in-app music playback.
 * Exposes reactive state for UI consumption.
 */
class MusicPlayerManager(private val context: Context) {

    private var player: ExoPlayer? = null
    private var positionJob: Job? = null

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    /** Build audio URL with JWT token auth. */
    fun buildAudioUrl(trackId: String, jwt: String): String {
        return "${BuildConfig.CLOUD_URL}/api/v1/music/tasks/$trackId/file?token=$jwt"
    }

    /** Play a track. Initializes player if needed. Must be called on Main thread. */
    @OptIn(UnstableApi::class)
    fun playTrack(track: MusicTrack, jwt: String, localUri: String? = null) {
        val p = player ?: createPlayer()

        _currentTrack.value = track

        val url = localUri ?: buildAudioUrl(track.id, jwt)
        Log.d(TAG, "playTrack: id=${track.id}, title=${track.title}, url=${url.take(120)}...")

        val mediaItem = MediaItem.fromUri(url)
        p.stop()
        p.clearMediaItems()
        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true

        startPositionPolling()
    }

    /** Toggle play/pause. */
    fun togglePlayPause() {
        val p = player ?: return
        if (p.isPlaying) {
            p.pause()
        } else {
            p.play()
        }
    }

    /** Seek to a fraction (0..1) of the track duration. */
    fun seekTo(fraction: Float) {
        val p = player ?: return
        val dur = p.duration
        if (dur > 0 && dur != C.TIME_UNSET) {
            p.seekTo((fraction * dur).toLong().coerceAtLeast(0))
        }
    }

    /** Stop playback and clear current track. */
    fun stop() {
        positionJob?.cancel()
        player?.stop()
        player?.clearMediaItems()
        _currentTrack.value = null
        _playerState.value = PlayerState()
    }

    /** Release player resources. Call from ViewModel.onCleared(). */
    fun release() {
        positionJob?.cancel()
        player?.release()
        player = null
        _currentTrack.value = null
        _playerState.value = PlayerState()
    }

    @OptIn(UnstableApi::class)
    private fun createPlayer(): ExoPlayer {
        Log.d(TAG, "createPlayer: building ExoPlayer")
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val p = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "onIsPlayingChanged: $isPlaying")
                updateState()
                if (isPlaying) startPositionPolling() else positionJob?.cancel()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "onPlaybackStateChanged: $stateName")
                updateState()
                if (playbackState == Player.STATE_ENDED) {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "onPlayerError: ${error.errorCodeName} — ${error.message}", error)
                _playerState.value = PlayerState()
            }
        })

        player = p
        return p
    }

    private fun updateState() {
        val p = player ?: return
        val dur = p.duration
        _playerState.value = PlayerState(
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = if (dur == C.TIME_UNSET) 0 else dur.coerceAtLeast(0),
            isBuffering = p.playbackState == Player.STATE_BUFFERING,
        )
    }

    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                updateState()
                delay(500)
            }
        }
    }
}

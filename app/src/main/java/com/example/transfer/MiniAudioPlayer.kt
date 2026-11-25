package com.example.transfer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.example.data.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class AudioPlayerState(
    val currentTrack: MediaItem? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

class MiniAudioPlayer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _state = MutableStateFlow(AudioPlayerState())
    val state: StateFlow<AudioPlayerState> = _state.asStateFlow()

    fun playOrToggle(track: MediaItem) {
        val current = _state.value.currentTrack
        if (current != null && current.id == track.id && mediaPlayer != null) {
            togglePlayPause()
            return
        }
        startTrack(track)
    }

    private fun startTrack(track: MediaItem) {
        releasePlayer()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, track.uri)
                prepare()
                setOnCompletionListener {
                    _state.update { it.copy(isPlaying = false, positionMs = it.durationMs) }
                }
                start()
            }
            mediaPlayer = player
            _state.value = AudioPlayerState(
                currentTrack = track,
                isPlaying = true,
                positionMs = 0L,
                durationMs = player.duration.toLong().coerceAtLeast(track.durationMs)
            )
            startProgressTicker()
        } catch (_: Exception) {
            _state.value = AudioPlayerState()
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _state.update { it.copy(isPlaying = false) }
        } else {
            player.start()
            _state.update { it.copy(isPlaying = true) }
        }
    }

    fun seekTo(fraction: Float) {
        val player = mediaPlayer ?: return
        val duration = _state.value.durationMs
        if (duration <= 0L) return
        val target = (duration * fraction.coerceIn(0f, 1f)).toInt()
        player.seekTo(target)
        _state.update { it.copy(positionMs = target.toLong()) }
    }

    fun stop() {
        releasePlayer()
        _state.value = AudioPlayerState()
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer
                if (player != null && player.isPlaying) {
                    _state.update { it.copy(positionMs = player.currentPosition.toLong()) }
                }
                delay(350L)
            }
        }
    }

    private fun releasePlayer() {
        progressJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }
}

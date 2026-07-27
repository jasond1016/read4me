package com.read4me.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioSegmentPlayer(context: Context? = null) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context?.getSystemService(AudioManager::class.java)
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = audioManager?.let {
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(::onAudioFocusChanged, handler)
            .build()
    }
    private var player: MediaPlayer? = null
    private var segmentStartMs = 0L
    private var segmentEndMs = 0L
    private var onFinished: (() -> Unit)? = null
    private var prepared = false
    private var pauseRequested = false
    private var onInterrupted: (() -> Unit)? = null
    private var onFocusAvailable: (() -> Unit)? = null
    private val finishRunnable = Runnable { finish() }

    fun setInterruptionListener(onInterrupted: () -> Unit, onFocusAvailable: () -> Unit) {
        this.onInterrupted = onInterrupted
        this.onFocusAvailable = onFocusAvailable
    }

    fun clearInterruptionListener() {
        onInterrupted = null
        onFocusAvailable = null
    }

    fun play(file: File, startMs: Long, endMs: Long, onFinished: () -> Unit = {}) {
        stop()
        if (!file.exists() || endMs <= startMs) return

        segmentStartMs = startMs
        segmentEndMs = endMs
        this.onFinished = onFinished
        val nextPlayer = MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                seekTo(startMs.toInt())
                prepared = true
                if (!pauseRequested && requestAudioFocus()) {
                    start()
                    scheduleFinish(endMs - startMs)
                } else if (!pauseRequested) {
                    pauseRequested = true
                    this@AudioSegmentPlayer.onInterrupted?.invoke()
                }
            }
            setOnCompletionListener { finish() }
            prepareAsync()
        }
        player = nextPlayer
    }

    fun pause(): Boolean {
        val activePlayer = player ?: return false
        pauseRequested = true
        handler.removeCallbacks(finishRunnable)
        if (prepared && activePlayer.isPlaying) activePlayer.pause()
        return true
    }

    fun resume(): Boolean {
        val activePlayer = player ?: return false
        if (!requestAudioFocus()) return false
        pauseRequested = false
        if (prepared && !activePlayer.isPlaying) {
            activePlayer.start()
            scheduleFinish((segmentEndMs - activePlayer.currentPosition).coerceAtLeast(0L))
        }
        return true
    }

    fun progress(): Float? {
        val activePlayer = player ?: return null
        if (!prepared || segmentEndMs <= segmentStartMs) return null
        return runCatching {
            ((activePlayer.currentPosition - segmentStartMs).toFloat() / (segmentEndMs - segmentStartMs))
                .coerceIn(0f, 1f)
        }.getOrNull()
    }

    fun stop() {
        handler.removeCallbacks(finishRunnable)
        abandonAudioFocus()
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        segmentStartMs = 0L
        segmentEndMs = 0L
        onFinished = null
        prepared = false
        pauseRequested = false
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return true
        val request = focusRequest ?: return true
        return manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        focusRequest?.let(manager::abandonAudioFocusRequest)
    }

    private fun onAudioFocusChanged(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusAvailable?.invoke()
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (!pauseRequested && pause()) onInterrupted?.invoke()
            }
        }
    }

    private fun scheduleFinish(delayMs: Long) {
        handler.removeCallbacks(finishRunnable)
        handler.postDelayed(finishRunnable, delayMs)
    }

    private fun finish() {
        val callback = onFinished
        stop()
        callback?.invoke()
    }
}

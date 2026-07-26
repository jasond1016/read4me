package com.read4me.app.audio

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

class AudioSegmentPlayer {
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var segmentEndMs = 0L
    private var onFinished: (() -> Unit)? = null
    private var prepared = false
    private var pauseRequested = false
    private val finishRunnable = Runnable { finish() }

    fun play(file: File, startMs: Long, endMs: Long, onFinished: () -> Unit = {}) {
        stop()
        if (!file.exists() || endMs <= startMs) return

        segmentEndMs = endMs
        this.onFinished = onFinished
        val nextPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener {
                seekTo(startMs.toInt())
                prepared = true
                if (!pauseRequested) {
                    start()
                    scheduleFinish(endMs - startMs)
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
        pauseRequested = false
        if (prepared && !activePlayer.isPlaying) {
            activePlayer.start()
            scheduleFinish((segmentEndMs - activePlayer.currentPosition).coerceAtLeast(0L))
        }
        return true
    }

    fun stop() {
        handler.removeCallbacks(finishRunnable)
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        segmentEndMs = 0L
        onFinished = null
        prepared = false
        pauseRequested = false
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

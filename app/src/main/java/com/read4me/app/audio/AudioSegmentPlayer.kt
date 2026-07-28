package com.read4me.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.read4me.app.model.NarrationSegment
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
    private var queue: List<NarrationSegment> = emptyList()
    private var queueIndex = 0
    private var completedDurationMs = 0L
    private var totalDurationMs = 0L
    private var onFinished: (() -> Unit)? = null
    private var onError: (() -> Unit)? = null
    private var prepared = false
    private var rangeReady = false
    private var pauseRequested = false
    private var onInterrupted: (() -> Unit)? = null
    private var onFocusAvailable: (() -> Unit)? = null
    private var generation = 0L
    private var finishRunnable: Runnable? = null

    fun setInterruptionListener(onInterrupted: () -> Unit, onFocusAvailable: () -> Unit) {
        this.onInterrupted = onInterrupted
        this.onFocusAvailable = onFocusAvailable
    }

    fun clearInterruptionListener() {
        onInterrupted = null
        onFocusAvailable = null
    }

    fun play(
        segments: List<NarrationSegment>,
        onError: () -> Unit = {},
        onFinished: () -> Unit = {},
    ): Boolean {
        stop()
        if (segments.isEmpty() || segments.any { !it.file.isFile || it.endMs <= it.startMs }) return false
        queue = segments
        totalDurationMs = segments.sumOf(NarrationSegment::durationMs)
        this.onFinished = onFinished
        this.onError = onError
        return playCurrent()
    }

    fun play(
        file: File,
        startMs: Long,
        endMs: Long,
        onError: () -> Unit = {},
        onFinished: () -> Unit = {},
    ) = if (endMs > startMs) play(listOf(NarrationSegment(file, startMs, endMs)), onError, onFinished) else false

    private fun playCurrent(): Boolean {
        val segment = queue.getOrNull(queueIndex) ?: run { finish(); return true }
        val file = segment.file
        val startMs = segment.startMs
        val endMs = segment.endMs
        segmentStartMs = startMs
        segmentEndMs = endMs
        val token = generation
        val nextPlayer = runCatching { MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(file.absolutePath)
            setOnPreparedListener { preparedPlayer ->
                if (token != generation || preparedPlayer !== player) return@setOnPreparedListener
                prepared = true
                fun startRange() {
                    if (token != generation || preparedPlayer !== player) return
                    rangeReady = true
                    if (pauseRequested) return
                    if (requestAudioFocus()) {
                        preparedPlayer.start()
                        scheduleFinish(endMs - preparedPlayer.currentPosition, token, preparedPlayer)
                    } else {
                        pauseRequested = true
                        this@AudioSegmentPlayer.onInterrupted?.invoke()
                    }
                }
                if (startMs > 0) {
                    preparedPlayer.setOnSeekCompleteListener { seekingPlayer ->
                        seekingPlayer.setOnSeekCompleteListener(null)
                        startRange()
                    }
                    preparedPlayer.seekTo(startMs.toInt())
                } else { rangeReady = true; startRange() }
            }
            setOnCompletionListener { completed -> advance(token, completed) }
            setOnErrorListener { failed, _, _ ->
                fail(token, failed)
                true
            }
            prepareAsync()
        } }.getOrElse {
            fail(token)
            return false
        }
        player = nextPlayer
        return true
    }

    fun pause(): Boolean {
        val activePlayer = player ?: return false
        pauseRequested = true
        finishRunnable?.let(handler::removeCallbacks)
        if (prepared && activePlayer.isPlaying) activePlayer.pause()
        return true
    }

    fun resume(): Boolean {
        val activePlayer = player ?: return false
        if (!requestAudioFocus()) return false
        pauseRequested = false
        if (prepared && rangeReady && !activePlayer.isPlaying) {
            activePlayer.start()
            scheduleFinish((segmentEndMs - activePlayer.currentPosition).coerceAtLeast(0L), generation, activePlayer)
        }
        return true
    }

    fun progress(): Float? {
        val activePlayer = player ?: return null
        if (!prepared || segmentEndMs <= segmentStartMs) return null
        return runCatching {
            ((completedDurationMs + activePlayer.currentPosition - segmentStartMs).toFloat() / totalDurationMs)
                .coerceIn(0f, 1f)
        }.getOrNull()
    }

    fun stop() {
        generation++
        finishRunnable?.let(handler::removeCallbacks)
        finishRunnable = null
        abandonAudioFocus()
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        segmentStartMs = 0L
        segmentEndMs = 0L
        queue = emptyList()
        queueIndex = 0
        completedDurationMs = 0L
        totalDurationMs = 0L
        onFinished = null
        onError = null
        prepared = false
        rangeReady = false
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

    private fun scheduleFinish(delayMs: Long, token: Long, expectedPlayer: MediaPlayer) {
        finishRunnable?.let(handler::removeCallbacks)
        finishRunnable = Runnable { advance(token, expectedPlayer) }.also { handler.postDelayed(it, delayMs) }
    }

    private fun advance(token: Long, expectedPlayer: MediaPlayer) {
        if (token != generation || player !== expectedPlayer) return
        generation++ // makes completion + timer mutually exclusive
        finishRunnable?.let(handler::removeCallbacks)
        finishRunnable = null
        player?.release()
        player = null
        prepared = false
        rangeReady = false
        completedDurationMs += (segmentEndMs - segmentStartMs)
        queueIndex++
        if (queueIndex >= queue.size) finish() else playCurrent()
    }

    private fun fail(token: Long, expectedPlayer: MediaPlayer? = null) {
        if (token != generation || expectedPlayer != null && player !== expectedPlayer) return
        val callback = onError
        stop()
        callback?.invoke()
    }

    private fun finish() {
        val callback = onFinished
        stop()
        callback?.invoke()
    }
}

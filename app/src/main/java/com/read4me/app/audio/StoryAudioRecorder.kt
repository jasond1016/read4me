package com.read4me.app.audio

import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class StoryAudioRecorder(private val context: Context) {
    data class StopResult(val durationMs: Long, val successful: Boolean)
    private var recorder: MediaRecorder? = null
    private var startedAtMs: Long = 0
    private var outputFile: File? = null

    val elapsedMs: Long
        get() = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAtMs

    val amplitude: Int
        get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(outputFile: File) {
        check(recorder == null) { "Recorder is already running" }
        // Never let a background story leak through the speaker into a new parent recording.
        context.stopService(Intent(context, AudioBookPlaybackService::class.java))
        outputFile.parentFile?.mkdirs()
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            nextRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
                setAudioChannels(1)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
        } catch (failure: Exception) {
            runCatching { nextRecorder.release() }
            throw failure
        }
        recorder = nextRecorder
        this.outputFile = outputFile
        startedAtMs = SystemClock.elapsedRealtime()
    }

    fun stop(): StopResult {
        val activeRecorder = recorder ?: return StopResult(0, false)
        val finalizedFile = outputFile
        recorder = null
        outputFile = null
        val stopped = try {
            activeRecorder.stop()
            true
        } catch (_: RuntimeException) {
            false
        } finally {
            runCatching { activeRecorder.release() }
        }
        val duration = if (stopped && finalizedFile != null) audioDurationMs(finalizedFile) else null
        return StopResult(duration ?: 0L, duration != null)
    }

    fun release() {
        val activeRecorder = recorder ?: return
        recorder = null
        outputFile = null
        try {
            activeRecorder.stop()
        } catch (_: RuntimeException) {
            // A too-short or interrupted recording may not have a valid container yet.
        } finally {
            runCatching { activeRecorder.release() }
        }
    }
}

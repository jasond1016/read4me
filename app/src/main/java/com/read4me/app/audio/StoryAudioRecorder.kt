package com.read4me.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class StoryAudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAtMs: Long = 0

    val elapsedMs: Long
        get() = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAtMs

    val amplitude: Int
        get() = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)

    fun start(outputFile: File) {
        check(recorder == null) { "Recorder is already running" }
        outputFile.parentFile?.mkdirs()
        val nextRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
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
        recorder = nextRecorder
        startedAtMs = SystemClock.elapsedRealtime()
    }

    fun stop(): Long {
        val duration = elapsedMs
        val activeRecorder = recorder ?: return 0
        recorder = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset()
        activeRecorder.release()
        return duration
    }

    fun release() {
        val activeRecorder = recorder ?: return
        recorder = null
        runCatching { activeRecorder.stop() }
        activeRecorder.reset()
        activeRecorder.release()
    }
}

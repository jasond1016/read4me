package com.read4me.app.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/** Decodes only an interval and retains one peak per bucket; no decoded PCM file is created. */
class AudioWaveformExtractor {
    fun extract(file: File, startMs: Long, endMs: Long, bucketCount: Int = 150): FloatArray {
        require(bucketCount > 0)
        if (!file.isFile || endMs <= startMs) return FloatArray(bucketCount)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return FloatArray(bucketCount)
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return FloatArray(bucketCount)
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            codec = MediaCodec.createDecoderByType(mime).also { it.configure(format, null, null, 0); it.start() }
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val rawPeaks = FloatArray(bucketCount)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val input = codec.getInputBuffer(index)!!
                        val time = extractor.sampleTime
                        if (time < 0 || time >= endUs) {
                            codec.queueInputBuffer(index, 0, 0, maxOf(time, 0), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val size = extractor.readSampleData(input, 0)
                            codec.queueInputBuffer(index, 0, maxOf(size, 0), time, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val index = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val output = codec.outputFormat
                        if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = output.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        channelCount = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> if (inputDone) Unit
                    else -> if (index >= 0) {
                        codec.getOutputBuffer(index)?.let { buffer ->
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val bytesPerSample = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val count = info.size / bytesPerSample
                            buffer.order(ByteOrder.nativeOrder())
                            for (sampleIndex in 0 until count) {
                                val frameIndex = sampleIndex / channelCount.coerceAtLeast(1)
                                val timeUs = info.presentationTimeUs + frameIndex * 1_000_000L / sampleRate
                                if (timeUs in startUs until endUs) {
                                    val value = if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                                        abs(buffer.float)
                                    } else {
                                        abs(buffer.short.toInt() / 32768f)
                                    }
                                    val bucket = (((timeUs - startUs) * bucketCount) / (endUs - startUs))
                                        .toInt().coerceIn(0, bucketCount - 1)
                                    rawPeaks[bucket] = maxOf(rawPeaks[bucket], value)
                                } else {
                                    buffer.position(buffer.position() + bytesPerSample)
                                }
                            }
                        }
                        codec.releaseOutputBuffer(index, false)
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    }
                }
            }
            return WaveformMath.normalize(rawPeaks)
        } finally {
            codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
            extractor.release()
        }
    }
}

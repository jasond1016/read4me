package com.read4me.app.audio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.read4me.app.data.StoryRepository

@OptIn(UnstableApi::class)
class AudioBookPlaybackService : MediaSessionService(), Player.Listener {
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var session: MediaSession
    private var plan: AudioBookPlan? = null
    private var lastSpreadIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val persistTick = object : Runnable {
        override fun run() {
            persistProgress()
            handler.postDelayed(this, 5_000)
        }
    }
    private val timerTick = object : Runnable {
        override fun run() {
            val deadline = prefs.getLong(KEY_TIMER_DEADLINE, 0)
            if (deadline > 0 && System.currentTimeMillis() >= deadline) {
                exoPlayer.pause()
                clearTimer()
            } else if (deadline > 0) handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(this@AudioBookPlaybackService)
        }
        val logicalPlayer = object : ForwardingPlayer(exoPlayer) {
            private fun seekLogicalNext() {
                val target = plan?.nextSpreadItem(currentMediaItemIndex) ?: return super.seekToNextMediaItem()
                if (target != currentMediaItemIndex) seekTo(target, 0)
            }

            private fun seekLogicalPrevious() {
                val target = plan?.previousSpreadItem(currentMediaItemIndex) ?: return super.seekToPreviousMediaItem()
                seekTo(target, 0)
            }

            override fun seekToNext() = seekLogicalNext()
            override fun seekToNextMediaItem() = seekLogicalNext()
            override fun seekToPrevious() = seekLogicalPrevious()
            override fun seekToPreviousMediaItem() = seekLogicalPrevious()
        }
        session = MediaSession.Builder(this, logicalPlayer).build()
        handler.post(persistTick)
        restoreTimer()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_BOOK -> intent.getStringExtra(EXTRA_BOOK_ID)?.let { bookId ->
                openBook(bookId, intent.getBooleanExtra(EXTRA_PLAY_WHEN_READY, true))
            }
            ACTION_SLEEP -> setTimer(intent.getStringExtra(EXTRA_SLEEP_MODE) ?: SLEEP_CANCEL)
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    private fun openBook(bookId: String, playWhenReady: Boolean) {
        val book = StoryRepository(this).loadAll().firstOrNull { it.id == bookId }
        val nextPlan = book?.let(AudioBookPlan::from)
        if (nextPlan == null || nextPlan.entries.isEmpty() || nextPlan.entries.any { !it.file.isFile }) {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            plan = null
            return
        }
        if (plan == nextPlan && exoPlayer.mediaItemCount > 0) {
            if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0, 0)
            if (playWhenReady) exoPlayer.play() else exoPlayer.pause()
            return
        }
        persistProgress()
        plan = nextPlan
        val items = nextPlan.entries.map { entry ->
            MediaItem.Builder()
                .setMediaId("${book.id}:${entry.queueIndex}")
                .setUri(Uri.fromFile(entry.file))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(entry.sourceStartMs)
                        .setEndPositionMs(entry.sourceEndMs)
                        .build(),
                )
                .setMediaMetadata(MediaMetadata.Builder()
                    .setTitle(book.title)
                    .setArtist("书面 ${entry.spreadIndex + 1}/${nextPlan.spreadCount}")
                    .setExtras(Bundle().apply {
                        putString(EXTRA_BOOK_ID, book.id)
                        putString(EXTRA_SPREAD_ID, entry.spreadId)
                        putInt(EXTRA_SPREAD_INDEX, entry.spreadIndex)
                        putInt(EXTRA_SEGMENT_INDEX, entry.segmentIndex)
                    }).build())
                .build()
        }
        val savedSpread = prefs.getString("$bookId.spread", null)
        val savedOffset = prefs.getLong("$bookId.offset", 0)
        val savedIndex = prefs.getInt("$bookId.index", -1).takeIf { it >= 0 }
        val resume = nextPlan.resume(savedSpread, savedOffset, savedIndex)
        lastSpreadIndex = nextPlan.entries[resume.queueIndex].spreadIndex
        exoPlayer.setMediaItems(items, resume.queueIndex, resume.itemPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        persistProgress()
        val currentSpread = plan?.entries?.getOrNull(exoPlayer.currentMediaItemIndex)?.spreadIndex ?: return
        if (prefs.getString(KEY_TIMER_MODE, null) == SLEEP_SPREAD && lastSpreadIndex >= 0 && currentSpread != lastSpreadIndex) {
            exoPlayer.pause()
            clearTimer()
        }
        lastSpreadIndex = currentSpread
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (!isPlaying) persistProgress()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            plan?.bookId?.let { id ->
                prefs.edit().putString("$id.spread", plan?.entries?.firstOrNull()?.spreadId)
                    .putInt("$id.index", 0).putLong("$id.offset", 0).apply()
            }
            clearTimer()
        }
    }

    override fun onPlayerError(error: PlaybackException) = persistProgress()

    private fun persistProgress() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) return
        val currentPlan = plan ?: return
        val entry = currentPlan.entries.getOrNull(exoPlayer.currentMediaItemIndex) ?: return
        val offset = entry.spreadOffsetMs + exoPlayer.currentPosition.coerceIn(0, entry.durationMs)
        prefs.edit().putString("${currentPlan.bookId}.spread", entry.spreadId)
            .putInt("${currentPlan.bookId}.index", entry.spreadIndex)
            .putLong("${currentPlan.bookId}.offset", offset).apply()
    }

    private fun setTimer(mode: String) {
        if (mode == SLEEP_CANCEL || mode == SLEEP_BOOK) {
            clearTimer()
            if (mode == SLEEP_BOOK) prefs.edit().putString(KEY_TIMER_MODE, mode).apply()
            return
        }
        val minutes = mode.toLongOrNull()
        val deadline = minutes?.let { System.currentTimeMillis() + it * 60_000 } ?: 0
        prefs.edit().putString(KEY_TIMER_MODE, mode).putLong(KEY_TIMER_DEADLINE, deadline).apply()
        handler.removeCallbacks(timerTick)
        if (deadline > 0) handler.post(timerTick)
    }

    private fun restoreTimer() {
        if (prefs.getLong(KEY_TIMER_DEADLINE, 0) > 0) handler.post(timerTick)
    }

    private fun clearTimer() {
        prefs.edit().remove(KEY_TIMER_MODE).remove(KEY_TIMER_DEADLINE).apply()
        handler.removeCallbacks(timerTick)
    }

    override fun onDestroy() {
        persistProgress()
        handler.removeCallbacksAndMessages(null)
        session.release()
        exoPlayer.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY_BOOK = "com.read4me.app.PLAY_AUDIO_BOOK"
        const val ACTION_SLEEP = "com.read4me.app.AUDIO_BOOK_SLEEP"
        const val EXTRA_BOOK_ID = "bookId"
        const val EXTRA_PLAY_WHEN_READY = "playWhenReady"
        const val EXTRA_SPREAD_ID = "spreadId"
        const val EXTRA_SPREAD_INDEX = "spreadIndex"
        const val EXTRA_SEGMENT_INDEX = "segmentIndex"
        const val EXTRA_SLEEP_MODE = "sleepMode"
        const val SLEEP_SPREAD = "spread"
        const val SLEEP_BOOK = "book"
        const val SLEEP_CANCEL = "cancel"
        const val PREFS = "audiobook_progress"
        const val KEY_TIMER_MODE = "timer.mode"
        const val KEY_TIMER_DEADLINE = "timer.deadline"
    }
}

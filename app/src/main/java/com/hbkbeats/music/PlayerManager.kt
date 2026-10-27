package com.hbkbeats.music

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/** One shared libVLC player for music and video. */
object PlayerManager {
    private lateinit var appContext: Context
    private var libVLC: LibVLC? = null
    private var player: MediaPlayer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var stopped = true
    private val main = Handler(Looper.getMainLooper())

    var queue by mutableStateOf<List<Track>>(emptyList()); private set
    var index by mutableIntStateOf(-1); private set
    var isPlaying by mutableStateOf(false); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var speed by mutableFloatStateOf(1f); private set

    val current: Track? get() = queue.getOrNull(index)

    fun init(ctx: Context) {
        if (player != null) return
        appContext = ctx.applicationContext
        libVLC = LibVLC(appContext, arrayListOf("--audio-time-stretch"))
        player = MediaPlayer(libVLC).apply { setEventListener { onEvent(it) } }
    }

    private fun onEvent(e: MediaPlayer.Event) {
        when (e.type) {
            MediaPlayer.Event.Playing -> { isPlaying = true; main.post { refreshService() } }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                isPlaying = false; main.post { refreshService() }
            }
            MediaPlayer.Event.EndReached -> main.post { next() }
            MediaPlayer.Event.TimeChanged -> position = e.timeChanged
            MediaPlayer.Event.LengthChanged -> duration = e.lengthChanged
        }
    }

    fun play(list: List<Track>, start: Int) {
        queue = list
        index = start
        load()
    }

    private fun load() {
        val t = current ?: return
        val p = player ?: return
        try {
            pfd?.close()
            pfd = appContext.contentResolver.openFileDescriptor(t.uri, "r")
            val media = Media(libVLC, pfd!!.fileDescriptor)
            p.media = media
            media.release()
            stopped = false
            position = 0
            duration = t.durationMs
            p.play()
            p.rate = speed
            refreshService()
        } catch (_: Exception) {
            next()
        }
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pause() { player?.pause() }

    fun next() {
        if (index < queue.lastIndex) { index++; load() } else stop()
    }

    fun prev() {
        if (position > 3000 || index <= 0) seekTo(0) else { index--; load() }
    }

    fun seekTo(ms: Long) { player?.time = ms }

    fun cycleSpeed() {
        val steps = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        speed = steps[(steps.indexOf(speed) + 1) % steps.size]
        player?.rate = speed
    }

    fun stop() {
        stopped = true
        player?.stop()
        isPlaying = false
        try { appContext.stopService(Intent(appContext, PlaybackService::class.java)) } catch (_: Exception) {}
    }

    fun attachVideo(layout: VLCVideoLayout) { player?.attachViews(layout, null, true, false) }
    fun detachVideo() { player?.detachViews() }

    private fun refreshService() {
        if (stopped || current == null) return
        try {
            ContextCompat.startForegroundService(
                appContext,
                Intent(appContext, PlaybackService::class.java).setAction(PlaybackService.ACTION_REFRESH)
            )
        } catch (_: Exception) {}
    }
}

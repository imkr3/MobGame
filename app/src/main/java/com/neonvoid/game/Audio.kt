package com.neonvoid.game

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Anything that can play a sound effect. Keeps [World] free of Android audio. */
interface SoundBus {
    fun sfx(id: Int)
}

/**
 * Streams [Synth] output to an [AudioTrack] on its own thread. Audio is a
 * nicety - every failure path here degrades to silence rather than throwing.
 */
class Audio(private val prefs: Prefs) : SoundBus {

    private val rate = 22050
    private val synth = Synth(rate)

    @Volatile private var running = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    /**
     * Pulls the toggles off SharedPreferences. Called when a setting changes,
     * never from inside the render loop: SharedPreferences takes a lock on
     * every read, and the audio thread was doing two of them per 23ms buffer.
     */
    fun applyPrefs() {
        synth.musicOn = prefs.musicOn
        synth.sfxOn = prefs.sfxOn
    }

    override fun sfx(id: Int) {
        synth.trigger(id)
    }

    fun setTrack(index: Int) {
        synth.setTrack(index)
    }

    fun setIntense(v: Boolean) {
        synth.intense = v
    }

    fun start() {
        if (running) return
        applyPrefs()
        running = true
        thread = Thread({ loop() }, "NeonVoidAudio").also {
            it.priority = Thread.NORM_PRIORITY + 2
            it.start()
        }
    }

    fun stop() {
        running = false
        val t = thread
        thread = null
        try {
            t?.join(900)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun loop() {
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return
            val bufBytes = maxOf(minBuf, 4096)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track = t
            t.play()

            // Small chunks keep effect latency low; the blocking write paces the loop.
            val chunk = ShortArray(512)
            while (running) {
                synth.render(chunk, chunk.size)
                val written = t.write(chunk, 0, chunk.size)
                if (written < 0) break
            }
            t.stop()
            t.release()
        } catch (_: Exception) {
            // No audio device, denied focus, or an odd OEM stack: play on in silence.
        } catch (_: UnsatisfiedLinkError) {
        } finally {
            track = null
        }
    }
}

/** Volume/soundtrack settings that never touch the audio device (used in tests). */
object SilentBus : SoundBus {
    override fun sfx(id: Int) {}
}

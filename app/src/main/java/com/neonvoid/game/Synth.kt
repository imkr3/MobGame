package com.neonvoid.game

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** Sound effect ids. */
object Sfx {
    const val SHOOT = 0
    const val HIT = 1
    const val EXPLODE = 2
    const val BIG_EXPLODE = 3
    const val PICKUP = 4
    const val POWERUP = 5
    const val HURT = 6
    const val OVERDRIVE = 7
    const val UI = 8
    const val WARN = 9
    const val WAVE_CLEAR = 10
    const val LASER = 11
    const val SUMMON = 12
}

private object Wave {
    const val SQUARE = 0
    const val SAW = 1
    const val TRI = 2
    const val NOISE = 3
    const val SINE = 4
}

private class Voice {
    var active = false
    var wave = Wave.SQUARE
    var freq = 220f
    var freqSlide = 1f          // per-sample multiplier
    var phase = 0f
    var amp = 0f
    var target = 0f
    var decay = 0.9999f
    var duty = 0.5f
    var attack = 0f             // 0..1 ramp position
    var attackRate = 1f
    var noiseState = 12345
}

/**
 * A tiny software synth: a step sequencer for the music plus a pool of voices
 * for effects. Deliberately free of Android imports so the exact same audio can
 * be rendered offline for inspection.
 */
class Synth(private val rate: Int = 22050) {

    companion object {
        const val VOICES = 14
        const val STEPS = 16

        /** Natural minor - everything here stays in it. */
        private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)

        private fun midiHz(m: Float): Float = 440f * 2f.pow((m - 69f) / 12f)
    }

    private class Song(
        val bpm: Float,
        val root: Int,
        val chords: IntArray,
        val lead: IntArray,
        val bass: IntArray,
        val drums: IntArray,
        val leadWave: Int,
        val bassWave: Int
    )

    // 1 = kick, 2 = snare, 4 = hat
    private val fourFloor = intArrayOf(1, 0, 4, 0, 3, 0, 4, 0, 1, 0, 4, 0, 3, 0, 4, 4)
    private val drivingBeat = intArrayOf(1, 0, 4, 4, 3, 0, 4, 0, 1, 4, 4, 0, 3, 0, 4, 5)
    private val sparseBeat = intArrayOf(1, 0, 0, 4, 3, 0, 4, 0, 1, 0, 0, 4, 3, 0, 4, 0)

    private val songs = arrayOf(
        // 0 menu - slow and wide
        Song(
            bpm = 92f, root = 45, chords = intArrayOf(0, -4, 3, 7),
            lead = intArrayOf(0, -1, 4, -1, 2, -1, -1, 4, -1, 2, -1, 0, -1, -1, 4, -1),
            bass = intArrayOf(1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 1, 0),
            drums = sparseBeat, leadWave = Wave.TRI, bassWave = Wave.TRI
        ),
        // 1 NEON REACH
        Song(
            bpm = 126f, root = 45, chords = intArrayOf(0, -4, -2, 3),
            lead = intArrayOf(0, 2, 4, 2, 7, 4, 2, 4, 0, 2, 4, 7, 9, 7, 4, 2),
            bass = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 1),
            drums = fourFloor, leadWave = Wave.SQUARE, bassWave = Wave.SAW
        ),
        // 2 CRIMSON BELT
        Song(
            bpm = 140f, root = 38, chords = intArrayOf(0, -4, 3, -2),
            lead = intArrayOf(0, 0, 3, 4, 3, 0, -1, 7, 7, 4, 3, 4, -1, 3, 0, -1),
            bass = intArrayOf(1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 0, 1, 1),
            drums = drivingBeat, leadWave = Wave.SAW, bassWave = Wave.SAW
        ),
        // 3 VIOLET DEPTHS
        Song(
            bpm = 118f, root = 36, chords = intArrayOf(0, 5, -4, 3),
            lead = intArrayOf(7, -1, 4, -1, 2, -1, 4, -1, 0, -1, 2, -1, 4, 7, -1, 4),
            bass = intArrayOf(1, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, 1, 0, 0),
            drums = sparseBeat, leadWave = Wave.TRI, bassWave = Wave.SQUARE
        ),
        // 4 GOLD CIRCUIT
        Song(
            bpm = 144f, root = 40, chords = intArrayOf(0, -4, 3, -2),
            lead = intArrayOf(0, 4, 7, 4, 9, 7, 4, 7, 11, 9, 7, 4, 7, 4, 2, 0),
            bass = intArrayOf(1, 0, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0, 1, 1, 0),
            drums = drivingBeat, leadWave = Wave.SQUARE, bassWave = Wave.SAW
        ),
        // 5 VOID CORE
        Song(
            bpm = 152f, root = 41, chords = intArrayOf(0, -4, 3, -1),
            lead = intArrayOf(0, 3, 7, 10, 7, 3, 0, 3, 7, 10, 12, 10, 7, 3, 0, -1),
            bass = intArrayOf(1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1),
            drums = drivingBeat, leadWave = Wave.SAW, bassWave = Wave.SAW
        )
    )

    private val voices = Array(VOICES) { Voice() }
    private var musicVoiceCursor = 0

    private var songIndex = 1
    private var stepAcc = 0f
    private var step = 0
    private var bar = 0
    private var lp = 0f
    private var noiseState = 987654321

    var musicOn = true
    var sfxOn = true
    /** Boss waves double the drive: extra octave lead and busier hats. */
    var intense = false

    private val pending = ArrayDeque<Int>()
    private var shootCooldown = 0

    fun setTrack(index: Int) {
        val i = index.coerceIn(0, songs.size - 1)
        if (i != songIndex) {
            songIndex = i
            step = 0
            bar = 0
            stepAcc = 0f
        }
    }

    fun trigger(sfx: Int) {
        if (!sfxOn) return
        synchronized(pending) {
            if (pending.size < 24) pending.addLast(sfx)
        }
    }

    private fun freeVoice(reserveMusic: Boolean): Voice {
        // music uses the low half of the pool, effects the high half, so a busy
        // firefight can never silence the track
        val from = if (reserveMusic) 0 else 6
        val to = if (reserveMusic) 6 else VOICES
        var quietest = from
        var quietestAmp = Float.MAX_VALUE
        for (i in from until to) {
            val v = voices[i]
            if (!v.active) return v
            if (v.amp < quietestAmp) { quietestAmp = v.amp; quietest = i }
        }
        return voices[quietest]
    }

    private fun note(
        wave: Int, hz: Float, amp: Float, decaySeconds: Float,
        music: Boolean, duty: Float = 0.5f, slidePerSec: Float = 1f, attackMs: Float = 3f
    ) {
        val v = freeVoice(music)
        v.active = true
        v.wave = wave
        v.freq = hz
        v.phase = 0f
        v.amp = 0f
        v.target = amp
        v.attack = 0f
        v.attackRate = 1f / (rate * (attackMs / 1000f)).coerceAtLeast(1f)
        v.decay = exp(-1f / (rate * decaySeconds.coerceAtLeast(0.01f))).toFloat()
        v.duty = duty
        v.freqSlide = slidePerSec.pow(1f / rate)
        v.noiseState = (noiseState * 1103515245 + 12345)
        noiseState = v.noiseState
    }

    // ------------------------------------------------------------- sequencer

    private fun advanceStep() {
        val song = songs[songIndex]
        val chord = song.chords[bar % song.chords.size]

        if (musicOn) {
            // bass
            if (song.bass[step] == 1) {
                val hz = midiHz((song.root + chord - 12).toFloat())
                note(song.bassWave, hz, 0.30f, 0.16f, true, 0.35f)
            }
            // lead
            val deg = song.lead[step]
            if (deg >= 0) {
                val oct = deg / MINOR.size
                val semi = MINOR[deg % MINOR.size] + oct * 12
                val hz = midiHz((song.root + chord + semi + 12).toFloat())
                note(song.leadWave, hz, if (intense) 0.20f else 0.15f, 0.13f, true, 0.28f)
                if (intense) note(song.leadWave, hz * 2f, 0.07f, 0.09f, true, 0.2f)
            }
            // pad on the bar
            if (step == 0) {
                val base = (song.root + chord).toFloat()
                note(Wave.TRI, midiHz(base), 0.10f, 1.4f, true, 0.5f, attackMs = 40f)
                note(Wave.TRI, midiHz(base + 3f), 0.08f, 1.4f, true, 0.5f, attackMs = 40f)
                note(Wave.TRI, midiHz(base + 7f), 0.07f, 1.4f, true, 0.5f, attackMs = 40f)
            }
            // drums
            val d = song.drums[step]
            if (d and 1 != 0) note(Wave.SINE, 110f, 0.42f, 0.11f, true, 0.5f, slidePerSec = 0.02f)
            if (d and 2 != 0) note(Wave.NOISE, 1f, 0.20f, 0.09f, true)
            if (d and 4 != 0 || (intense && step % 2 == 1)) note(Wave.NOISE, 1f, 0.07f, 0.03f, true)
        }

        step++
        if (step >= STEPS) {
            step = 0
            bar++
        }
    }

    private fun fireSfx(id: Int) {
        when (id) {
            Sfx.SHOOT -> {
                if (shootCooldown > 0) return
                shootCooldown = rate / 22
                note(Wave.SQUARE, 900f, 0.10f, 0.035f, false, 0.2f, slidePerSec = 0.25f)
            }
            Sfx.LASER -> {
                note(Wave.SAW, 320f, 0.20f, 0.35f, false, 0.5f, slidePerSec = 3.5f)
                note(Wave.SQUARE, 640f, 0.10f, 0.30f, false, 0.15f, slidePerSec = 3.0f)
            }
            Sfx.HIT -> note(Wave.NOISE, 1f, 0.09f, 0.035f, false)
            Sfx.EXPLODE -> {
                note(Wave.NOISE, 1f, 0.30f, 0.28f, false)
                note(Wave.SINE, 160f, 0.22f, 0.24f, false, slidePerSec = 0.12f)
            }
            Sfx.BIG_EXPLODE -> {
                note(Wave.NOISE, 1f, 0.46f, 0.75f, false)
                note(Wave.SINE, 90f, 0.40f, 0.7f, false, slidePerSec = 0.2f)
                note(Wave.SAW, 200f, 0.20f, 0.5f, false, slidePerSec = 0.25f)
            }
            Sfx.PICKUP -> {
                note(Wave.SQUARE, 880f, 0.15f, 0.09f, false, 0.35f, slidePerSec = 6f)
            }
            Sfx.POWERUP -> {
                note(Wave.SQUARE, 523f, 0.15f, 0.14f, false, 0.4f)
                note(Wave.SQUARE, 659f, 0.14f, 0.18f, false, 0.4f)
                note(Wave.SQUARE, 784f, 0.14f, 0.26f, false, 0.4f)
            }
            Sfx.HURT -> {
                note(Wave.SAW, 260f, 0.30f, 0.34f, false, 0.5f, slidePerSec = 0.18f)
                note(Wave.NOISE, 1f, 0.22f, 0.3f, false)
            }
            Sfx.OVERDRIVE -> {
                note(Wave.SAW, 180f, 0.26f, 0.6f, false, 0.5f, slidePerSec = 7f)
                note(Wave.SQUARE, 440f, 0.16f, 0.5f, false, 0.3f, slidePerSec = 2.2f)
                note(Wave.NOISE, 1f, 0.18f, 0.4f, false)
            }
            Sfx.UI -> note(Wave.SQUARE, 660f, 0.10f, 0.05f, false, 0.25f)
            Sfx.WARN -> {
                note(Wave.SQUARE, 330f, 0.20f, 0.5f, false, 0.5f, slidePerSec = 0.85f)
                note(Wave.SQUARE, 331.5f, 0.18f, 0.5f, false, 0.5f, slidePerSec = 0.85f)
            }
            Sfx.WAVE_CLEAR -> {
                note(Wave.SQUARE, 523f, 0.14f, 0.16f, false, 0.4f)
                note(Wave.SQUARE, 659f, 0.14f, 0.22f, false, 0.4f)
                note(Wave.SQUARE, 784f, 0.14f, 0.28f, false, 0.4f)
                note(Wave.SQUARE, 1047f, 0.13f, 0.45f, false, 0.4f)
            }
            Sfx.SUMMON -> {
                note(Wave.SAW, 120f, 0.24f, 1.1f, false, 0.5f, slidePerSec = 5.5f)
                note(Wave.SQUARE, 300f, 0.14f, 1.0f, false, 0.3f, slidePerSec = 4.0f)
            }
        }
    }

    // ---------------------------------------------------------------- render

    fun render(out: ShortArray, count: Int) {
        val song = songs[songIndex]
        val samplesPerStep = (rate * 60f / song.bpm / 4f)

        synchronized(pending) {
            while (pending.isNotEmpty()) fireSfx(pending.removeFirst())
        }

        for (i in 0 until count) {
            if (shootCooldown > 0) shootCooldown--
            stepAcc += 1f
            if (stepAcc >= samplesPerStep) {
                stepAcc -= samplesPerStep
                advanceStep()
            }

            var mix = 0f
            for (v in voices) {
                if (!v.active) continue
                if (v.attack < 1f) {
                    v.attack += v.attackRate
                    if (v.attack > 1f) v.attack = 1f
                    v.amp = v.target * v.attack
                } else {
                    v.amp *= v.decay
                    if (v.amp < 0.0006f) { v.active = false; continue }
                }
                v.freq *= v.freqSlide
                if (v.freq < 18f) v.freq = 18f

                val s: Float = when (v.wave) {
                    Wave.SQUARE -> if (v.phase < v.duty) 1f else -1f
                    Wave.SAW -> v.phase * 2f - 1f
                    Wave.TRI -> if (v.phase < 0.5f) v.phase * 4f - 1f else 3f - v.phase * 4f
                    Wave.SINE -> sin(v.phase * TAU)
                    else -> {
                        v.noiseState = v.noiseState * 1103515245 + 12345
                        ((v.noiseState shr 16) and 0x7FFF) / 16384f - 1f
                    }
                }
                mix += s * v.amp
                v.phase += v.freq / rate
                if (v.phase >= 1f) v.phase -= v.phase.toInt().toFloat()
            }

            // gentle one-pole low pass takes the edge off the square waves
            lp += (mix - lp) * 0.45f
            // soft clip
            val shaped = lp / (1f + abs(lp) * 0.7f)
            out[i] = (shaped * 20000f).toInt().coerceIn(-32000, 32000).toShort()
        }
    }

    /** Resets sequencer position - used when the track changes on a hard cut. */
    fun rewind() {
        step = 0; bar = 0; stepAcc = 0f
        for (v in voices) v.active = false
    }
}

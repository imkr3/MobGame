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
    const val LEVEL_UP = 13
    const val ALARM = 14
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
        const val VOICES = 22
        const val STEPS = 16

        /** Natural minor - everything here stays in it. */
        private val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)

        private fun midiHz(m: Float): Float = 440f * 2f.pow((m - 69f) / 12f)
    }

    /** One bar of the arrangement. Songs cycle through several of these. */
    private class Section(
        val lead: IntArray,
        val bass: IntArray,       // 0 rest, 1 root, 2 octave up, 3 fifth
        val drums: IntArray,
        val arp: Int              // 0 off, 1 eighths, 2 sixteenths
    )

    private class Song(
        val bpm: Float,
        val root: Int,
        val chords: IntArray,
        val sections: Array<Section>,
        val leadWave: Int,
        val bassWave: Int,
        val arpWave: Int
    )

    // 1 = kick, 2 = snare, 4 = hat, 8 = open hat
    private val fourFloor = intArrayOf(1, 0, 4, 0, 3, 0, 4, 0, 1, 0, 4, 0, 3, 0, 4, 12)
    private val drivingBeat = intArrayOf(1, 0, 4, 4, 3, 0, 4, 0, 1, 4, 4, 0, 3, 0, 4, 13)
    private val sparseBeat = intArrayOf(1, 0, 0, 4, 3, 0, 4, 0, 1, 0, 0, 4, 3, 0, 4, 8)
    private val hardBeat = intArrayOf(1, 4, 4, 4, 3, 4, 4, 5, 1, 4, 4, 4, 3, 4, 5, 13)
    /** Played on the last bar of every four - keeps long loops from flattening out. */
    private val fillBeat = intArrayOf(1, 0, 2, 2, 3, 0, 2, 2, 1, 2, 2, 2, 3, 2, 3, 15)

    private val bassStraight = intArrayOf(1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 2)
    private val bassOctave = intArrayOf(1, 0, 2, 1, 0, 1, 2, 0, 1, 0, 2, 1, 0, 1, 2, 3)
    private val bassDriving = intArrayOf(1, 1, 0, 1, 1, 0, 1, 2, 1, 1, 0, 1, 1, 0, 2, 2)
    private val bassSlow = intArrayOf(1, 0, 0, 0, 0, 0, 3, 0, 1, 0, 0, 0, 2, 0, 0, 0)
    private val bassPulse = intArrayOf(1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 0, 2, 2, 1, 1)

    private val songs = arrayOf(
        // 0 MENU - wide and patient, but it moves
        Song(
            bpm = 100f, root = 45, chords = intArrayOf(0, -4, 3, 7),
            sections = arrayOf(
                Section(intArrayOf(0, -1, 4, -1, 2, -1, -1, 4, -1, 2, -1, 0, -1, -1, 4, -1), bassSlow, sparseBeat, 1),
                Section(intArrayOf(7, -1, 4, 2, -1, 4, -1, 7, 9, -1, 7, 4, -1, 2, -1, -1), bassSlow, sparseBeat, 2)
            ),
            leadWave = Wave.TRI, bassWave = Wave.TRI, arpWave = Wave.SQUARE
        ),
        // 1 NEON REACH
        Song(
            bpm = 132f, root = 45, chords = intArrayOf(0, -4, -2, 3),
            sections = arrayOf(
                Section(intArrayOf(0, 2, 4, 2, 7, 4, 2, 4, 0, 2, 4, 7, 9, 7, 4, 2), bassStraight, fourFloor, 2),
                Section(intArrayOf(7, 9, 11, 9, 7, 4, 7, 9, 11, 9, 7, 9, 14, 11, 9, 7), bassOctave, fourFloor, 2),
                Section(intArrayOf(0, -1, 4, -1, 7, -1, 4, -1, 2, -1, 7, -1, 9, 7, 4, 2), bassStraight, drivingBeat, 1),
                Section(intArrayOf(11, 9, 7, 9, 11, 14, 11, 9, 7, 4, 7, 9, 11, 9, 7, 4), bassOctave, drivingBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.SAW, arpWave = Wave.SQUARE
        ),
        // 2 CRIMSON BELT - fast and mean
        Song(
            bpm = 146f, root = 38, chords = intArrayOf(0, -4, 3, -2),
            sections = arrayOf(
                Section(intArrayOf(0, 0, 3, 4, 3, 0, -1, 7, 7, 4, 3, 4, -1, 3, 0, -1), bassDriving, drivingBeat, 2),
                Section(intArrayOf(7, 7, 9, 7, 4, 3, 4, 7, 11, 9, 7, 4, 3, 4, 0, 0), bassDriving, hardBeat, 2),
                Section(intArrayOf(0, 3, 7, 3, 0, 3, 7, 10, 7, 3, 0, 3, 7, 10, 12, 10), bassPulse, hardBeat, 2),
                Section(intArrayOf(12, 10, 7, 10, 12, 14, 12, 10, 7, 3, 7, 10, 12, 10, 7, 3), bassDriving, drivingBeat, 2)
            ),
            leadWave = Wave.SAW, bassWave = Wave.SAW, arpWave = Wave.SQUARE
        ),
        // 3 VIOLET DEPTHS - moody, then it opens up
        Song(
            bpm = 124f, root = 36, chords = intArrayOf(0, 5, -4, 3),
            sections = arrayOf(
                Section(intArrayOf(7, -1, 4, -1, 2, -1, 4, -1, 0, -1, 2, -1, 4, 7, -1, 4), bassSlow, sparseBeat, 1),
                Section(intArrayOf(0, 2, 4, 7, 4, 2, 0, 2, 4, 7, 9, 7, 4, 2, 0, -1), bassStraight, fourFloor, 2),
                Section(intArrayOf(9, -1, 7, -1, 4, -1, 7, 9, 11, -1, 9, 7, 4, -1, 2, -1), bassSlow, fourFloor, 1),
                Section(intArrayOf(11, 9, 7, 4, 7, 9, 11, 14, 11, 9, 7, 4, 2, 4, 7, 9), bassOctave, drivingBeat, 2)
            ),
            leadWave = Wave.TRI, bassWave = Wave.SQUARE, arpWave = Wave.TRI
        ),
        // 4 GOLD CIRCUIT - bright arpeggio runs
        Song(
            bpm = 150f, root = 40, chords = intArrayOf(0, -4, 3, -2),
            sections = arrayOf(
                Section(intArrayOf(0, 4, 7, 4, 9, 7, 4, 7, 11, 9, 7, 4, 7, 4, 2, 0), bassDriving, fourFloor, 2),
                Section(intArrayOf(7, 11, 14, 11, 9, 7, 4, 7, 11, 14, 16, 14, 11, 7, 4, 2), bassOctave, drivingBeat, 2),
                Section(intArrayOf(0, -1, 7, -1, 4, -1, 9, -1, 7, -1, 4, -1, 2, 4, 7, 9), bassPulse, hardBeat, 2),
                Section(intArrayOf(14, 11, 9, 11, 14, 16, 14, 11, 9, 7, 9, 11, 14, 11, 9, 7), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.SAW, arpWave = Wave.SQUARE
        ),
        // 5 VOID CORE - relentless
        Song(
            bpm = 158f, root = 41, chords = intArrayOf(0, -4, 3, -1),
            sections = arrayOf(
                Section(intArrayOf(0, 3, 7, 10, 7, 3, 0, 3, 7, 10, 12, 10, 7, 3, 0, -1), bassPulse, hardBeat, 2),
                Section(intArrayOf(12, 10, 7, 10, 12, 14, 12, 10, 7, 10, 12, 14, 16, 14, 12, 10), bassDriving, hardBeat, 2),
                Section(intArrayOf(0, 0, 3, 0, 7, 0, 3, 0, 10, 0, 7, 0, 3, 0, 0, -1), bassPulse, drivingBeat, 2),
                Section(intArrayOf(16, 14, 12, 10, 12, 14, 16, 19, 16, 14, 12, 10, 7, 10, 12, 14), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SAW, bassWave = Wave.SAW, arpWave = Wave.SQUARE
        ),
        // 6 EMERALD DRIFT - bright and rolling
        Song(
            bpm = 128f, root = 43, chords = intArrayOf(0, 5, -2, 3),
            sections = arrayOf(
                Section(intArrayOf(0, 2, 4, 7, 4, 2, 4, 7, 9, 7, 4, 2, 4, 7, 9, 11), bassStraight, fourFloor, 2),
                Section(intArrayOf(7, 9, 11, 9, 7, 4, 2, 4, 7, 9, 11, 14, 11, 9, 7, 4), bassOctave, fourFloor, 2),
                Section(intArrayOf(0, -1, 4, -1, 7, -1, 9, -1, 7, -1, 4, -1, 2, 4, 7, -1), bassSlow, sparseBeat, 1),
                Section(intArrayOf(11, 9, 7, 9, 11, 9, 7, 4, 7, 9, 11, 9, 7, 4, 2, 0), bassDriving, drivingBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.TRI, arpWave = Wave.SQUARE
        ),
        // 7 ASH REACH - heavy and trudging
        Song(
            bpm = 138f, root = 37, chords = intArrayOf(0, -2, -4, 3),
            sections = arrayOf(
                Section(intArrayOf(0, -1, 0, 3, -1, 3, 4, -1, 0, -1, 3, 4, 7, -1, 4, 3), bassPulse, hardBeat, 1),
                Section(intArrayOf(7, -1, 4, 3, 4, -1, 7, 9, 7, -1, 4, 3, 0, -1, 3, 4), bassDriving, hardBeat, 2),
                Section(intArrayOf(0, 0, 3, 3, 4, 4, 7, 7, 4, 4, 3, 3, 0, 0, -1, -1), bassPulse, drivingBeat, 2),
                Section(intArrayOf(10, 9, 7, 4, 3, 4, 7, 9, 10, 9, 7, 4, 3, 0, 3, 4), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SAW, bassWave = Wave.SAW, arpWave = Wave.TRI
        ),
        // 8 AZURE SPIRE - crystalline, wide
        Song(
            bpm = 120f, root = 47, chords = intArrayOf(0, 3, -4, 5),
            sections = arrayOf(
                Section(intArrayOf(7, -1, 9, -1, 11, -1, 9, -1, 7, -1, 4, -1, 7, -1, 9, -1), bassSlow, sparseBeat, 2),
                Section(intArrayOf(0, 4, 7, 11, 9, 7, 4, 7, 11, 14, 11, 7, 4, 2, 4, 7), bassStraight, fourFloor, 2),
                Section(intArrayOf(14, -1, 11, -1, 9, -1, 11, 14, 16, -1, 14, 11, 9, -1, 7, -1), bassSlow, fourFloor, 1),
                Section(intArrayOf(11, 9, 7, 9, 11, 14, 16, 14, 11, 9, 7, 4, 7, 9, 11, 14), bassOctave, drivingBeat, 2)
            ),
            leadWave = Wave.TRI, bassWave = Wave.TRI, arpWave = Wave.SQUARE
        ),
        // 9 ROSE NEBULA - lush and busy
        Song(
            bpm = 134f, root = 44, chords = intArrayOf(0, -4, 5, 3),
            sections = arrayOf(
                Section(intArrayOf(0, 2, 4, 5, 4, 2, 0, 2, 4, 5, 7, 5, 4, 2, 0, -1), bassStraight, fourFloor, 2),
                Section(intArrayOf(7, 5, 4, 5, 7, 9, 11, 9, 7, 5, 4, 2, 4, 5, 7, 9), bassOctave, drivingBeat, 2),
                Section(intArrayOf(11, -1, 9, -1, 7, -1, 9, 11, 12, -1, 11, 9, 7, -1, 5, -1), bassSlow, fourFloor, 1),
                Section(intArrayOf(12, 11, 9, 7, 9, 11, 12, 14, 12, 11, 9, 7, 5, 4, 2, 0), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.SAW, arpWave = Wave.TRI
        ),
        // 10 THE HOLLOW - sparse, then it never lets up
        Song(
            bpm = 160f, root = 39, chords = intArrayOf(0, -1, -4, 3),
            sections = arrayOf(
                Section(intArrayOf(0, -1, -1, 3, -1, -1, 7, -1, 0, -1, -1, 3, -1, 7, -1, -1), bassSlow, sparseBeat, 0),
                Section(intArrayOf(0, 3, 7, 3, 0, 3, 7, 10, 12, 10, 7, 3, 0, 3, 7, 10), bassPulse, hardBeat, 2),
                Section(intArrayOf(12, 12, 10, 10, 7, 7, 3, 3, 0, 0, 3, 3, 7, 7, 10, 10), bassDriving, hardBeat, 2),
                Section(intArrayOf(16, 14, 12, 10, 12, 14, 16, 17, 16, 14, 12, 10, 7, 3, 0, -1), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SAW, bassWave = Wave.SAW, arpWave = Wave.SQUARE
        ),
        // 11 STORM LINE - restless, with the lead cracking overhead
        Song(
            bpm = 150f, root = 41, chords = intArrayOf(0, 3, -2, 5),
            sections = arrayOf(
                Section(intArrayOf(0, -1, 3, 5, -1, 3, 7, -1, 5, 3, -1, 0, 3, -1, 7, 5), bassPulse, drivingBeat, 2),
                Section(intArrayOf(7, 10, 12, 10, 7, 5, 3, 5, 7, 10, 12, 14, 12, 10, 7, 5), bassDriving, hardBeat, 2),
                Section(intArrayOf(0, 0, 7, 7, 3, 3, 10, 10, 5, 5, 12, 12, 7, 3, 0, -1), bassOctave, drivingBeat, 1),
                Section(intArrayOf(14, 12, 10, 7, 10, 12, 14, 17, 14, 12, 10, 7, 5, 3, 0, 3), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.SAW, arpWave = Wave.SAW
        ),
        // 12 TIDAL REEF - buoyant, drifting, a long swell
        Song(
            bpm = 118f, root = 43, chords = intArrayOf(0, 5, 3, -4),
            sections = arrayOf(
                Section(intArrayOf(0, -1, 2, -1, 5, -1, 7, -1, 5, -1, 2, -1, 0, -1, -1, 2), bassSlow, sparseBeat, 1),
                Section(intArrayOf(7, 9, 7, 5, 3, 5, 7, 9, 12, 9, 7, 5, 3, 2, 0, -1), bassStraight, fourFloor, 2),
                Section(intArrayOf(0, 3, 5, 7, 9, 7, 5, 3, 0, 3, 5, 9, 12, 9, 5, 3), bassOctave, fourFloor, 2),
                Section(intArrayOf(12, -1, 9, -1, 7, 9, 12, 14, 12, -1, 9, 7, 5, 3, 2, 0), bassSlow, drivingBeat, 1)
            ),
            leadWave = Wave.TRI, bassWave = Wave.TRI, arpWave = Wave.SQUARE
        ),
        // 13 THE BONEYARD - heavy, dragging, unwilling
        Song(
            bpm = 108f, root = 34, chords = intArrayOf(0, -2, 3, -4),
            sections = arrayOf(
                Section(intArrayOf(0, -1, -1, 3, -1, -1, -1, 0, -1, 3, -1, -1, 5, -1, 3, -1), bassSlow, sparseBeat, 0),
                Section(intArrayOf(3, -1, 5, -1, 7, -1, 5, 3, 0, -1, 3, -1, 5, 7, -1, 5), bassStraight, sparseBeat, 1),
                Section(intArrayOf(7, 5, 3, 0, 3, 5, 7, 10, 7, 5, 3, 0, -1, 3, 5, 7), bassOctave, fourFloor, 2),
                Section(intArrayOf(10, -1, 7, -1, 5, 3, 5, 7, 10, 12, 10, 7, 5, 3, 0, -1), bassDriving, drivingBeat, 1)
            ),
            leadWave = Wave.SAW, bassWave = Wave.SAW, arpWave = Wave.TRI
        ),
        // 14 AURORA GATE - the last light, and it is beautiful and fast
        Song(
            bpm = 168f, root = 45, chords = intArrayOf(0, 7, 3, 5),
            sections = arrayOf(
                Section(intArrayOf(0, 3, 7, 12, 7, 3, 7, 12, 14, 12, 7, 3, 0, 3, 7, 12), bassDriving, hardBeat, 2),
                Section(intArrayOf(12, 14, 16, 14, 12, 7, 12, 14, 16, 19, 16, 14, 12, 7, 3, 0), bassOctave, hardBeat, 2),
                Section(intArrayOf(0, -1, 7, -1, 12, -1, 7, -1, 3, -1, 10, -1, 14, 12, 7, 3), bassPulse, drivingBeat, 1),
                Section(intArrayOf(19, 16, 14, 12, 14, 16, 19, 21, 19, 16, 14, 12, 7, 3, 0, 7), bassDriving, hardBeat, 2)
            ),
            leadWave = Wave.SQUARE, bassWave = Wave.SAW, arpWave = Wave.SQUARE
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
    private var arpStep = 0
    private var filterPhase = 0f

    var musicOn = true
    var sfxOn = true
    /** Boss waves double the drive: extra octave lead and busier hats. */
    var intense = false

    private val pending = ArrayDeque<Int>()
    private var shootCooldown = 0

    /** How many arrangements exist: the menu plus one per sector. */
    val trackCount: Int get() = songs.size

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
        val from = if (reserveMusic) 0 else 12
        val to = if (reserveMusic) 12 else VOICES
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

    /** Scale degree -> hz, wrapping octaves so long runs keep climbing. */
    private fun degHz(root: Int, chord: Int, degree: Int, octaveShift: Int): Float {
        val oct = degree / MINOR.size
        val semi = MINOR[degree % MINOR.size] + oct * 12
        return midiHz((root + chord + semi + octaveShift).toFloat())
    }

    private fun advanceStep() {
        val song = songs[songIndex]
        val chord = song.chords[bar % song.chords.size]
        val section = song.sections[bar % song.sections.size]
        val isFill = (bar % 4) == 3

        if (musicOn) {
            // ---- bass: root, octave and fifth give the line some shape
            when (section.bass[step]) {
                1 -> note(song.bassWave, midiHz((song.root + chord - 12).toFloat()), 0.32f, 0.16f, true, 0.35f)
                2 -> note(song.bassWave, midiHz((song.root + chord).toFloat()), 0.26f, 0.13f, true, 0.3f)
                3 -> note(song.bassWave, midiHz((song.root + chord - 5).toFloat()), 0.26f, 0.15f, true, 0.35f)
            }

            // ---- lead, doubled and slightly detuned for width
            val deg = section.lead[step]
            if (deg >= 0) {
                val hz = degHz(song.root, chord, deg, 12)
                val amp = if (intense) 0.19f else 0.15f
                note(song.leadWave, hz, amp, 0.14f, true, 0.28f)
                note(song.leadWave, hz * 1.0045f, amp * 0.55f, 0.12f, true, 0.32f)
                if (intense) note(song.leadWave, hz * 2f, 0.06f, 0.09f, true, 0.2f)
            }

            // ---- arpeggio running through the chord
            if (section.arp > 0) {
                val every = if (section.arp == 2) 1 else 2
                if (step % every == 0) {
                    val tone = intArrayOf(0, 2, 4, 7)[arpStep % 4]
                    note(song.arpWave, degHz(song.root, chord, tone, 24), 0.055f, 0.075f, true, 0.2f)
                    arpStep++
                }
            }

            // ---- pad swell on the bar
            if (step == 0) {
                val base = (song.root + chord).toFloat()
                note(Wave.TRI, midiHz(base), 0.10f, 1.5f, true, 0.5f, attackMs = 45f)
                note(Wave.TRI, midiHz(base + 3f), 0.08f, 1.5f, true, 0.5f, attackMs = 45f)
                note(Wave.TRI, midiHz(base + 7f), 0.07f, 1.5f, true, 0.5f, attackMs = 45f)
                note(Wave.TRI, midiHz(base + 10f), 0.05f, 1.4f, true, 0.5f, attackMs = 60f)
            }

            // ---- drums, with a fill closing every fourth bar
            val d = if (isFill) fillBeat[step] else section.drums[step]
            if (d and 1 != 0) note(Wave.SINE, 118f, 0.46f, 0.12f, true, 0.5f, slidePerSec = 0.02f)
            if (d and 2 != 0) {
                note(Wave.NOISE, 1f, 0.22f, 0.10f, true)
                note(Wave.TRI, 210f, 0.10f, 0.06f, true, 0.5f, slidePerSec = 0.2f)
            }
            if (d and 4 != 0) note(Wave.NOISE, 1f, 0.075f, 0.028f, true)
            if (d and 8 != 0) note(Wave.NOISE, 1f, 0.085f, 0.14f, true)
            if (intense && step % 2 == 1 && d and 4 == 0) note(Wave.NOISE, 1f, 0.05f, 0.025f, true)
        }

        step++
        if (step >= STEPS) {
            step = 0
            bar++
            arpStep = 0
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
            Sfx.LEVEL_UP -> {
                note(Wave.SQUARE, 392f, 0.16f, 0.18f, false, 0.4f)
                note(Wave.SQUARE, 523f, 0.16f, 0.24f, false, 0.4f)
                note(Wave.SQUARE, 659f, 0.15f, 0.32f, false, 0.4f)
                note(Wave.SQUARE, 784f, 0.15f, 0.45f, false, 0.4f)
                note(Wave.SQUARE, 1047f, 0.14f, 0.7f, false, 0.4f)
                note(Wave.SAW, 196f, 0.18f, 0.8f, false, 0.5f, slidePerSec = 2.2f)
            }
            // Two-tone klaxon under a falling siren: the overload warning.
            Sfx.ALARM -> {
                note(Wave.SQUARE, 880f, 0.20f, 0.30f, false, 0.5f)
                note(Wave.SQUARE, 660f, 0.20f, 0.55f, false, 0.5f, slidePerSec = 0.55f)
                note(Wave.SAW, 220f, 0.22f, 1.5f, false, 0.5f, slidePerSec = 0.42f)
                note(Wave.NOISE, 900f, 0.14f, 0.9f, false, 0.5f, slidePerSec = 0.5f)
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

            // one-pole low pass with a slow sweep - keeps long loops moving
            filterPhase += 1f / rate
            val k = 0.34f + 0.18f * sin(filterPhase * 0.18f * TAU)
            lp += (mix - lp) * k
            // soft clip
            val shaped = lp / (1f + abs(lp) * 0.7f)
            out[i] = (shaped * 20000f).toInt().coerceIn(-32000, 32000).toShort()
        }
    }

    /** Resets sequencer position - used when the track changes on a hard cut. */
    fun rewind() {
        step = 0; bar = 0; stepAcc = 0f; arpStep = 0
        for (v in voices) v.active = false
    }
}

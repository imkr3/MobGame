package com.neonvoid.game

import kotlin.math.abs
import kotlin.random.Random

/**
 * How a group enters the screen. Formation is deliberately separate from what
 * the group is made of, so the same roster can be dealt out a dozen ways.
 */
object Form {
    const val LINE = 0        // an even rank across the top, arriving together
    const val WEDGE = 1       // arrowhead, centre first, spreading outward
    const val COLUMN = 2      // one lane, single file
    const val TWIN = 3        // two lanes down the flanks
    const val PINCER = 4      // two knots at the far edges
    const val ARC = 5         // a curve, the edges lagging the centre
    const val TRICKLE = 6     // wide and slow: pressure rather than a burst
    const val CLUSTER = 7     // a tight knot at one point
    const val SWEEP = 8       // a diagonal wall crossing the screen
    const val CROSS = 9       // alternating edges, working inward
    const val COUNT = 10
}

/** One formation of one enemy kind, ready for the world to lay down. */
class GroupPlan {
    var kind = EK.DRIFTER
    var form = Form.LINE
    var count = 4
    var gap = 0.16f
    var flip = false
    var lane = 0.5f
    var elite = false
}

/** The shape of a whole wave. */
object Arch {
    const val MIXED = 0
    const val ASSAULT = 1
    const val SWARM = 2
    const val SIEGE = 3
    const val AMBUSH = 4
    const val GAUNTLET = 5
    const val VANGUARD = 6
    const val COUNT = 7

    val names = arrayOf("PATROL", "ASSAULT", "SWARM", "SIEGE", "AMBUSH", "GAUNTLET", "VANGUARD")
}

/**
 * The wave director. Rather than dealing one group per roster entry in a fixed
 * order - which made every wave feel the same - a wave now picks an archetype,
 * and the archetype picks kinds by weight class and gives each one a formation.
 * The randomness is fenced in: archetypes never repeat back to back, the kinds
 * always come from the sector's own roster, and the group budget still follows
 * the same curve it always did, so variety costs nothing in balance.
 */
object Waves {

    // ------------------------------------------------------------- classes

    const val LIGHT = 0       // fast, fragile, arrives in numbers
    const val BRAWLER = 1     // mid-weight, closes distance
    const val HEAVY = 2       // slow emplacements and support

    fun weightClass(kind: Int): Int = when (kind) {
        EK.SWARMER, EK.DRIFTER, EK.WEAVER, EK.WISP, EK.POD -> LIGHT
        EK.CHARGER, EK.ORBITER, EK.SPLITTER, EK.STALKER -> BRAWLER
        else -> HEAVY
    }

    /** How many of [kind] a single group is worth at this depth. */
    fun baseCount(kind: Int, scale: Int): Int = when (kind) {
        EK.SWARMER -> (6 + scale * 2).coerceAtMost(14)
        EK.DRIFTER -> (5 + scale).coerceAtMost(9)
        EK.WEAVER -> (4 + scale / 2).coerceAtMost(8)
        EK.CHARGER -> (2 + scale).coerceAtMost(6)
        EK.ORBITER -> (2 + scale / 2).coerceAtMost(5)
        EK.WISP -> (2 + scale / 2).coerceAtMost(5)
        EK.SPLITTER -> (2 + scale / 3).coerceAtMost(4)
        EK.SHIELDER -> (2 + scale / 3).coerceAtMost(4)
        EK.LANCER -> (1 + scale / 3).coerceAtMost(3)
        EK.TURRET -> (1 + scale / 2).coerceAtMost(3)
        EK.MINELAYER -> (1 + scale / 4).coerceAtMost(2)
        EK.CARRIER -> (1 + scale / 5).coerceAtMost(2)
        EK.PYLON -> 2 * (1 + scale / 5).coerceAtMost(2)
        EK.STALKER -> (1 + scale / 2).coerceAtMost(4)
        EK.HOWLER -> (1 + scale / 3).coerceAtMost(3)
        EK.SEEDER -> (1 + scale / 3).coerceAtMost(3)
        EK.MENDER -> (1 + scale / 4).coerceAtMost(2)
        else -> 3
    }

    /** Pylons are only lethal in pairs, so they ignore whatever form was picked. */
    private fun formFor(kind: Int, want: Int): Int = if (kind == EK.PYLON) Form.PINCER else want

    // ---------------------------------------------------------- placement

    /** Where the [i]th member of a [count]-strong formation enters. */
    fun spawnX(p: GroupPlan, i: Int, w: Float): Float {
        val n = p.count.coerceAtLeast(1)
        val f = (i + 0.5f) / n
        val dir = if (p.flip) -1f else 1f
        return when (p.form) {
            Form.LINE, Form.ARC -> w * (0.10f + 0.80f * f)
            Form.SWEEP -> w * (if (p.flip) 0.90f - 0.80f * f else 0.10f + 0.80f * f)
            Form.WEDGE -> {
                val step = (i + 1) / 2
                val side = if (i % 2 == 0) 1f else -1f
                val reach = 0.36f / ((n + 1) / 2).coerceAtLeast(1)
                clamp(0.5f + side * step * reach, 0.08f, 0.92f) * w
            }
            Form.COLUMN -> w * p.lane
            Form.TWIN -> w * (if (i % 2 == 0) 0.22f else 0.78f)
            Form.PINCER -> w * (if (i % 2 == 0) 0.11f else 0.89f)
            Form.CROSS -> {
                val step = i / 2
                val inset = 0.10f + step * 0.13f
                w * clamp(if (i % 2 == 0) inset else 1f - inset, 0.08f, 0.92f)
            }
            Form.CLUSTER -> w * clamp(p.lane + rnd(-0.06f, 0.06f), 0.10f, 0.90f)
            else -> w * clamp(p.lane + dir * (f - 0.5f) * 0.8f + rnd(-0.05f, 0.05f), 0.10f, 0.90f)
        }
    }

    /** How long after the group starts the [i]th member arrives. */
    fun spawnDelay(p: GroupPlan, i: Int): Float {
        val n = p.count.coerceAtLeast(1)
        return when (p.form) {
            Form.LINE -> i * p.gap * 0.45f
            Form.WEDGE -> ((i + 1) / 2) * p.gap * 1.1f
            Form.COLUMN -> i * p.gap * 1.7f
            Form.TWIN -> (i / 2) * p.gap * 1.5f
            Form.PINCER -> (i / 2) * p.gap * 1.3f
            Form.ARC -> abs(i - (n - 1) * 0.5f) * p.gap * 1.4f
            Form.TRICKLE -> i * p.gap * 2.6f
            Form.CLUSTER -> i * p.gap * 0.5f
            Form.SWEEP -> i * p.gap * 1.5f
            else -> i * p.gap * 1.15f
        }
    }

    /** Seconds this group occupies the timeline before the next one starts. */
    fun hold(p: GroupPlan): Float {
        var last = 0f
        for (i in 0 until p.count) last = maxOf(last, spawnDelay(p, i))
        val tail = when (weightClass(p.kind)) {
            HEAVY -> 1.5f
            BRAWLER -> 1.1f
            else -> 0.9f
        }
        return last + tail
    }

    // ------------------------------------------------------------ planning

    private fun pick(pool: List<Int>): Int = pool[Random.nextInt(pool.size)]

    /** Kinds already dealt this wave, so one roster entry cannot fill it alone. */
    private val used = HashSet<Int>(8)

    /**
     * Falls back through the classes so every roster can answer every request,
     * preferring something this wave has not fielded yet.
     */
    private fun preferring(roster: IntArray, vararg classes: Int): Int {
        for (pass in 0..1) {
            for (c in classes) {
                val opts = roster.filter { weightClass(it) == c && (pass == 1 || it !in used) }
                if (opts.isNotEmpty()) {
                    val k = pick(opts)
                    used.add(k)
                    return k
                }
            }
        }
        val k = roster[Random.nextInt(roster.size)]
        used.add(k)
        return k
    }

    /**
     * The whole wave gets a mob ceiling, so an archetype that likes many groups
     * cannot quietly field twice the enemies of one that likes few. Groups are
     * thinned proportionally rather than dropped, which keeps the shape.
     */
    private fun trim(out: MutableList<GroupPlan>, ceiling: Int) {
        var total = 0
        for (p in out) total += p.count
        if (total <= ceiling || total <= 0) return
        val f = ceiling.toFloat() / total
        for (p in out) p.count = (p.count * f).toInt().coerceAtLeast(1)
    }

    /** Mobs one wave may field, tracking the curve the old director produced. */
    fun mobCeiling(wave: Int, overload: Int): Int =
        (18 + (wave * 2.2f).toInt().coerceAtMost(70)) + overload * 10

    private fun group(
        kind: Int, form: Int, count: Int, gap: Float, elite: Boolean = false
    ): GroupPlan = GroupPlan().apply {
        this.kind = kind
        this.form = formFor(kind, form)
        this.count = count.coerceAtLeast(1)
        this.gap = gap
        this.elite = elite
        flip = chance(0.5f)
        lane = rnd(0.24f, 0.76f)
    }

    /** Archetype weights: what a wave is likely to be at this depth. */
    private fun weights(wave: Int, avoid: Int): IntArray {
        val out = IntArray(Arch.COUNT)
        out[Arch.MIXED] = 3
        out[Arch.ASSAULT] = 4
        out[Arch.SWARM] = if (wave < 12) 4 else 3
        out[Arch.AMBUSH] = 2 + (wave / 8).coerceAtMost(3)
        out[Arch.SIEGE] = 1 + (wave / 6).coerceAtMost(3)
        out[Arch.GAUNTLET] = 3
        out[Arch.VANGUARD] = if (wave < 8) 0 else 1 + (wave / 10).coerceAtMost(2)
        if (avoid in 0 until Arch.COUNT) out[avoid] = 0
        return out
    }

    private fun rollArchetype(wave: Int, avoid: Int): Int {
        val wts = weights(wave, avoid)
        var total = 0
        for (v in wts) total += v
        if (total <= 0) return Arch.MIXED
        var r = Random.nextInt(total)
        for (i in wts.indices) {
            r -= wts[i]
            if (r < 0) return i
        }
        return Arch.MIXED
    }

    /**
     * Fills [out] with the groups for one wave and returns the archetype used,
     * so the next wave can avoid repeating it.
     */
    fun plan(
        wave: Int,
        roster: IntArray,
        budget: Int,
        scale: Int,
        overload: Int,
        avoid: Int,
        out: MutableList<GroupPlan>
    ): Int {
        out.clear()
        used.clear()
        // The opening waves stay on the gentle half of the roster and the
        // plainest formations, so the first minute is still readable.
        if (wave <= 3) {
            for (g in 0 until budget) {
                val kind = roster[g % 2]
                out.add(group(kind, if (g % 2 == 0) Form.LINE else Form.ARC, baseCount(kind, scale), 0.20f))
            }
            trim(out, mobCeiling(wave, overload))
            return Arch.MIXED
        }

        val arch = rollArchetype(wave, avoid)
        when (arch) {
            Arch.ASSAULT -> {
                val forms = intArrayOf(Form.LINE, Form.WEDGE, Form.ARC, Form.SWEEP)
                for (g in 0 until budget) {
                    val kind = preferring(roster, if (g % 2 == 0) LIGHT else BRAWLER, BRAWLER, HEAVY)
                    out.add(group(kind, forms[Random.nextInt(forms.size)], baseCount(kind, scale), 0.15f))
                }
            }
            Arch.SWARM -> {
                val forms = intArrayOf(Form.TRICKLE, Form.SWEEP, Form.CLUSTER, Form.LINE)
                val n = (budget - 1).coerceAtLeast(2)
                for (g in 0 until n) {
                    val kind = preferring(roster, LIGHT, BRAWLER, HEAVY)
                    val count = (baseCount(kind, scale) * 5) / 4
                    out.add(group(kind, forms[Random.nextInt(forms.size)], count, 0.11f))
                }
            }
            Arch.SIEGE -> {
                val forms = intArrayOf(Form.CLUSTER, Form.TWIN, Form.COLUMN)
                val heavies = (budget / 2).coerceAtLeast(1)
                for (g in 0 until heavies) {
                    val kind = preferring(roster, HEAVY, BRAWLER, LIGHT)
                    out.add(group(kind, forms[Random.nextInt(forms.size)], baseCount(kind, scale), 0.34f))
                }
                for (g in 0 until (budget - heavies).coerceAtLeast(1)) {
                    val kind = preferring(roster, LIGHT, BRAWLER, HEAVY)
                    out.add(group(kind, Form.LINE, baseCount(kind, scale), 0.16f))
                }
            }
            Arch.AMBUSH -> {
                val forms = intArrayOf(Form.PINCER, Form.CROSS, Form.TWIN)
                for (g in 0 until budget) {
                    val kind = preferring(roster, if (g % 2 == 0) BRAWLER else LIGHT, LIGHT, HEAVY)
                    out.add(group(kind, forms[Random.nextInt(forms.size)], baseCount(kind, scale), 0.13f))
                }
            }
            Arch.GAUNTLET -> {
                // one kind, over and over, in a different shape each time
                val kind = roster[Random.nextInt(roster.size)]
                val forms = intArrayOf(Form.LINE, Form.WEDGE, Form.PINCER, Form.SWEEP, Form.ARC, Form.CROSS)
                val n = (budget + 1).coerceAtMost(6)
                val each = ((baseCount(kind, scale) * 2) / 3).coerceAtLeast(2)
                for (g in 0 until n) {
                    out.add(group(kind, forms[(g + Random.nextInt(forms.size)) % forms.size], each, 0.17f))
                }
            }
            Arch.VANGUARD -> {
                // a small elite spearhead, then the ordinary wave behind it
                val lead = preferring(roster, BRAWLER, HEAVY, LIGHT)
                out.add(group(lead, Form.WEDGE, (baseCount(lead, scale) / 2).coerceAtLeast(2), 0.26f, elite = true))
                for (g in 0 until (budget - 1).coerceAtLeast(1)) {
                    val kind = preferring(roster, LIGHT, BRAWLER, HEAVY)
                    out.add(group(kind, if (g % 2 == 0) Form.LINE else Form.TRICKLE, baseCount(kind, scale), 0.16f))
                }
            }
            else -> {
                // the old dealer: one group per roster entry, in order
                for (g in 0 until budget) {
                    val kind = roster[(g + wave) % roster.size]
                    val form = when (weightClass(kind)) {
                        HEAVY -> Form.CLUSTER
                        BRAWLER -> Form.WEDGE
                        else -> Form.LINE
                    }
                    out.add(group(kind, form, baseCount(kind, scale), 0.18f))
                }
            }
        }
        trim(out, mobCeiling(wave, overload))
        return arch
    }
}

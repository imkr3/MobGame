package com.neonvoid.game

import kotlin.random.Random

/** Everything one run contributed, handed to the meta layer when it ends. */
class RunTally {
    var score = 0
    var wave = 0
    var kills = 0
    var levels = 0
    var bosses = 0
    var grazes = 0
    var overdrives = 0
    var gems = 0
    var augments = 0

    fun reset() {
        score = 0; wave = 0; kills = 0; levels = 0
        bosses = 0; grazes = 0; overdrives = 0; gems = 0; augments = 0
    }
}

/**
 * Pilot rank. Every run pays experience; ranks pay cores back and gate the
 * later half of the shop, so there is a reason to fly beyond the scoreboard.
 */
object Rank {
    const val MAX = 40

    val titles = arrayOf(
        "ROOKIE", "CADET", "PILOT", "AVIATOR", "LANCER", "RAIDER", "HUNTER",
        "VETERAN", "ACE", "WARDEN", "VANGUARD", "PARAGON", "LEGEND", "VOIDBORNE"
    )

    /** Experience needed to climb out of [rank] into the next one. */
    fun toNext(rank: Int): Int = if (rank >= MAX) 0 else 160 + (rank - 1).coerceAtLeast(0) * 140

    fun title(rank: Int): String = titles[((rank - 1) / 3).coerceIn(0, titles.size - 1)]

    /** Cores paid out for reaching [rank]. */
    fun reward(rank: Int): Int = 120 + rank * 40

    fun xpFor(t: RunTally): Int =
        t.score / 600 + t.wave * 10 + t.kills / 5 + t.levels * 60
}

/** What a contract asks for, and how its progress is counted. */
object MK {
    const val KILLS = 0
    const val SCORE = 1
    const val WAVE = 2
    const val BOSS = 3
    const val GRAZE = 4
    const val OVERDRIVE = 5
    const val GEMS = 6
    const val LEVELS = 7
    const val AUGMENTS = 8
    const val COUNT = 9

    /** SCORE and WAVE want the best single run; the rest accumulate. */
    fun isBest(kind: Int): Boolean = kind == SCORE || kind == WAVE

    fun target(kind: Int, rank: Int): Int = when (kind) {
        KILLS -> 150 + rank * 60
        SCORE -> 40_000 + rank * 22_000
        WAVE -> 8 + rank
        BOSS -> 3 + rank / 2
        GRAZE -> 120 + rank * 50
        OVERDRIVE -> 6 + rank * 2
        GEMS -> 80 + rank * 40
        LEVELS -> 1 + rank / 4
        else -> 15 + rank * 5
    }

    fun text(kind: Int, target: Int): String = when (kind) {
        KILLS -> "DESTROY ${formatScore(target)} ENEMIES"
        SCORE -> "SCORE ${formatScore(target)} IN ONE RUN"
        WAVE -> "REACH WAVE $target IN ONE RUN"
        BOSS -> "BRING DOWN $target BOSSES"
        GRAZE -> "GRAZE ${formatScore(target)} SHOTS"
        OVERDRIVE -> "FIRE OVERDRIVE $target TIMES"
        GEMS -> "COLLECT ${formatScore(target)} GEMS"
        LEVELS -> if (target == 1) "CLEAR A SECTOR" else "CLEAR $target SECTORS"
        else -> "INSTALL $target AUGMENTS"
    }

    fun color(kind: Int): Int = when (kind) {
        KILLS -> Palette.CYAN
        SCORE -> Palette.AMBER
        WAVE -> Palette.VIOLET
        BOSS -> Palette.RED
        GRAZE -> Palette.MAGENTA
        OVERDRIVE -> Palette.AMBER
        GEMS -> Palette.LIME
        LEVELS -> Palette.SKY
        else -> Palette.ROSE
    }

    fun contribution(kind: Int, t: RunTally): Int = when (kind) {
        KILLS -> t.kills
        SCORE -> t.score
        WAVE -> t.wave
        BOSS -> t.bosses
        GRAZE -> t.grazes
        OVERDRIVE -> t.overdrives
        GEMS -> t.gems
        LEVELS -> t.levels
        else -> t.augments
    }
}

/** One contract in a slot: what it wants, how far along it is, what it pays. */
class Mission(val kind: Int, val target: Int, val progress: Int, val reward: Int) {
    val done: Boolean get() = progress >= target
    val text: String get() = MK.text(kind, target)
    val ratio: Float get() = clamp(progress.toFloat() / target.coerceAtLeast(1), 0f, 1f)
}

/**
 * Three rolling contracts. They are the reason to keep flying once the
 * scoreboard stops moving: each pays cores and experience, and a finished one
 * is replaced by a fresh objective the moment it is claimed.
 */
object Missions {
    const val SLOTS = 3

    fun reward(kind: Int, rank: Int): Int = when (kind) {
        MK.SCORE, MK.WAVE -> 240 + rank * 55
        MK.LEVELS, MK.BOSS -> 280 + rank * 60
        else -> 190 + rank * 45
    }

    /** Rolls a contract that is not already running in another slot. */
    fun roll(prefs: Prefs, slot: Int) {
        val taken = (0 until SLOTS).filter { it != slot }.map { prefs.missionKind(it) }.toSet()
        var kind = Random.nextInt(MK.COUNT)
        var guard = 0
        while (kind in taken && guard < 40) {
            kind = Random.nextInt(MK.COUNT)
            guard++
        }
        val rank = prefs.rank
        prefs.setMission(slot, kind, MK.target(kind, rank), 0, reward(kind, rank))
    }

    fun ensureRolled(prefs: Prefs) {
        for (i in 0 until SLOTS) if (prefs.missionTarget(i) <= 0) roll(prefs, i)
    }

    fun read(prefs: Prefs, slot: Int): Mission = Mission(
        prefs.missionKind(slot),
        prefs.missionTarget(slot),
        prefs.missionProgress(slot),
        prefs.missionReward(slot)
    )

    /** Folds a finished run into every open contract. */
    fun apply(prefs: Prefs, t: RunTally) {
        ensureRolled(prefs)
        for (i in 0 until SLOTS) {
            val kind = prefs.missionKind(i)
            val add = MK.contribution(kind, t)
            val was = prefs.missionProgress(i)
            val now = if (MK.isBest(kind)) maxOf(was, add) else was + add
            prefs.setMissionProgress(i, now.coerceAtMost(prefs.missionTarget(i)))
        }
    }

    /** Pays out a finished contract and rolls its replacement. Returns the cores. */
    fun claim(prefs: Prefs, slot: Int): Int {
        val m = read(prefs, slot)
        if (!m.done) return 0
        val paid = (m.reward * Shop.contractMultiplier(prefs)).toInt()
        prefs.cores = prefs.cores + paid
        prefs.totalCores = prefs.totalCores + paid
        prefs.addXp(paid / 3)
        prefs.missionsDone = prefs.missionsDone + 1
        roll(prefs, slot)
        return paid
    }
}

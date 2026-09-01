package com.neonvoid.game

/** Boss archetypes. Levels draw from a pool of these. */
object BT {
    const val GUARDIAN = 0
    const val WARDEN = 1
    const val HIVE = 2
    const val FORGE = 3
    const val NULLIFIER = 4

    val names = arrayOf("GUARDIAN", "WARDEN", "HIVE", "FORGE", "NULLIFIER")
}

/**
 * A themed chapter of the run: its own palette, enemy roster, boss pool and
 * music. Levels change the look and the cast, never the difficulty - that is
 * driven purely by the wave number, so the run keeps escalating across them.
 */
/** Which backdrop furniture a sector flies through. */
object Terrain {
    const val GRID = 0        // outer grid: a city skyline on the horizon
    const val BELT = 1        // asteroid lanes: tumbling rock at three depths
    const val STATION = 2     // dead station: girder frames scrolling past
    const val FOUNDRY = 3     // foundry ring: pipe bands and gear wheels
    const val VOID = 4        // no signal: drifting wireframe hulks and static
    const val OVERGROWN = 5   // overgrown yard: fronds and drifting spores
    const val ASH = 6         // burnt corridor: rising embers, smoke, pillars
    const val ICE = 7         // frozen array: crystal spires and falling shards
    const val BLOOM = 8       // bloom field: petals and soft gas
    const val HOLLOW = 9      // nothing answers: near-empty, glitching
    const val STORM = 10      // thunderhead: rain streaks and lightning
    const val REEF = 11       // drowned garden: coral arches and bubbles
    const val WRECK = 12      // boneyard: dead hulls hanging in the dark
    const val AURORA = 13     // last light: curtains of colour
}

class LevelTheme(
    val name: String,
    val subtitle: String,
    val accent: Int,
    val skyTop: Int,
    val skyMid: Int,
    val skyBottom: Int,
    val sunStops: IntArray,
    val grid: Int,
    val nebula: Int,
    val roster: IntArray,
    val bossPool: IntArray,
    val musicKey: Int,
    val terrain: Int = Terrain.GRID
)

/** What a level asks of you before it opens up. */
class LevelReq(val text: String, val test: (Prefs) -> Boolean)

object Levels {

    /** Unlock conditions, in level order. The first is always open. */
    val unlocks = arrayOf(
        LevelReq("", { true }),
        LevelReq("REACH WAVE 8", { it.bestWave >= 8 }),
        LevelReq("REACH WAVE 16", { it.bestWave >= 16 }),
        LevelReq("REACH WAVE 24", { it.bestWave >= 24 }),
        LevelReq("SCORE 150,000", { it.bestScore >= 150_000 }),
        LevelReq("CLEAR A LEVEL", { it.bestLevel >= 1 }),
        LevelReq("1,500 TOTAL KILLS", { it.totalKills >= 1500 }),
        LevelReq("REACH WAVE 45", { it.bestWave >= 45 }),
        LevelReq("OWN 8 HULLS", { ShipDex.ownedCount(it.ownedShips) >= 8 }),
        LevelReq("CLEAR 2 LEVELS", { it.bestLevel >= 2 }),
        LevelReq("REACH WAVE 60", { it.bestWave >= 60 }),
        LevelReq("SCORE 1,200,000", { it.bestScore >= 1_200_000 }),
        LevelReq("5,000 TOTAL KILLS", { it.totalKills >= 5000 }),
        LevelReq("CLEAR 4 LEVELS", { it.bestLevel >= 4 })
    )

    /**
     * Requirements sit on different axes - waves, score, kills, hulls - so on
     * their own a later sector can fall open while an earlier one is still
     * shut. A sector needs its own goal *and* the one before it, which keeps
     * the varied goals but makes the map open in order.
     */
    fun unlocked(index: Int, prefs: Prefs): Boolean {
        if (index !in unlocks.indices) return false
        for (i in 0..index) if (!unlocks[i].test(prefs)) return false
        return true
    }

    fun requirement(index: Int): String = unlocks.getOrNull(index)?.text ?: ""

    /** Bitmask of every level currently open, so new unlocks can be spotted. */
    fun unlockedMask(prefs: Prefs): Int {
        var m = 0
        for (i in unlocks.indices) {
            if (!unlocks[i].test(prefs)) break
            m = m or (1 shl i)
        }
        return m
    }


    /**
     * Waves in one level. Bosses land every fifth wave, so six per level, and
     * the last of them closes the sector out.
     */
    const val WAVES_PER_LEVEL = 30

    val list = arrayOf(
        LevelTheme(
            name = "NEON REACH", subtitle = "OUTER GRID", accent = Palette.MAGENTA,
            skyTop = 0xFF1A0838.toInt(), skyMid = 0xFF0D0422.toInt(), skyBottom = 0xFF05020C.toInt(),
            sunStops = intArrayOf(0xCCFFE07A.toInt(), 0xC6FFB13D.toInt(), 0xBFFF4FA3.toInt(), 0xB3B13BFF.toInt()),
            grid = Palette.CYAN, nebula = Palette.VIOLET,
            roster = intArrayOf(EK.DRIFTER, EK.WEAVER, EK.CHARGER, EK.SWARMER),
            bossPool = intArrayOf(BT.GUARDIAN, BT.WARDEN), musicKey = 1, terrain = Terrain.GRID
        ),
        LevelTheme(
            name = "CRIMSON BELT", subtitle = "ASTEROID LANES", accent = 0xFFFF5C3D.toInt(),
            skyTop = 0xFF3A0A18.toInt(), skyMid = 0xFF1A0410.toInt(), skyBottom = 0xFF0A0206.toInt(),
            sunStops = intArrayOf(0xCCFFF0A0.toInt(), 0xC6FF8A3D.toInt(), 0xBFFF3B4F.toInt(), 0xB3A81E5A.toInt()),
            grid = 0xFFFF6B5C.toInt(), nebula = 0xFFFF4A5C.toInt(),
            roster = intArrayOf(EK.DRIFTER, EK.CHARGER, EK.SWARMER, EK.LANCER, EK.STALKER),
            bossPool = intArrayOf(BT.WARDEN, BT.GUARDIAN, BT.FORGE), musicKey = 2, terrain = Terrain.BELT
        ),
        LevelTheme(
            name = "VIOLET DEPTHS", subtitle = "DEAD STATION", accent = Palette.VIOLET,
            skyTop = 0xFF120B44.toInt(), skyMid = 0xFF08052A.toInt(), skyBottom = 0xFF03020F.toInt(),
            sunStops = intArrayOf(0xCCB0E8FF.toInt(), 0xC67AA2FF.toInt(), 0xBF6B4FFF.toInt(), 0xB33B1EA8.toInt()),
            grid = 0xFF7A5CFF.toInt(), nebula = 0xFF5C7AFF.toInt(),
            roster = intArrayOf(EK.WEAVER, EK.TURRET, EK.ORBITER, EK.MINELAYER, EK.PYLON, EK.HOWLER),
            bossPool = intArrayOf(BT.HIVE, BT.NULLIFIER), musicKey = 3, terrain = Terrain.STATION
        ),
        LevelTheme(
            name = "GOLD CIRCUIT", subtitle = "FOUNDRY RING", accent = Palette.AMBER,
            skyTop = 0xFF2A2408.toInt(), skyMid = 0xFF141004.toInt(), skyBottom = 0xFF060502.toInt(),
            sunStops = intArrayOf(0xCCFFFFC0.toInt(), 0xC6FFD93D.toInt(), 0xBF9BFF57.toInt(), 0xB33DBF7A.toInt()),
            grid = Palette.LIME, nebula = 0xFFBFA030.toInt(),
            roster = intArrayOf(EK.SPLITTER, EK.SHIELDER, EK.TURRET, EK.LANCER, EK.CARRIER, EK.MENDER),
            bossPool = intArrayOf(BT.FORGE, BT.WARDEN), musicKey = 4, terrain = Terrain.FOUNDRY
        ),
        LevelTheme(
            name = "VOID CORE", subtitle = "NO SIGNAL", accent = Palette.WHITE,
            skyTop = 0xFF14142A.toInt(), skyMid = 0xFF0A0A18.toInt(), skyBottom = 0xFF040408.toInt(),
            sunStops = intArrayOf(0xCCFFFFFF.toInt(), 0xC6D0D0FF.toInt(), 0xBF9B5CFF.toInt(), 0xB3402080.toInt()),
            grid = 0xFFB0B0FF.toInt(), nebula = 0xFF8080FF.toInt(),
            roster = intArrayOf(EK.WISP, EK.ORBITER, EK.SPLITTER, EK.LANCER, EK.PYLON, EK.SHIELDER, EK.CHARGER),
            bossPool = intArrayOf(BT.NULLIFIER, BT.FORGE, BT.HIVE), musicKey = 5, terrain = Terrain.VOID
        ),
        LevelTheme(
            name = "EMERALD DRIFT", subtitle = "OVERGROWN YARD", accent = 0xFF57FFB0.toInt(),
            skyTop = 0xFF06301F.toInt(), skyMid = 0xFF031A11.toInt(), skyBottom = 0xFF010A07.toInt(),
            sunStops = intArrayOf(0xCCE8FFD0.toInt(), 0xC67AFFA0.toInt(), 0xBF2ED08A.toInt(), 0xB31A6B7A.toInt()),
            grid = 0xFF57FFB0.toInt(), nebula = 0xFF2EA88A.toInt(),
            roster = intArrayOf(EK.SPLITTER, EK.CARRIER, EK.SWARMER, EK.ORBITER, EK.SEEDER),
            bossPool = intArrayOf(BT.HIVE, BT.FORGE), musicKey = 6, terrain = Terrain.OVERGROWN
        ),
        LevelTheme(
            name = "ASH REACH", subtitle = "BURNT CORRIDOR", accent = 0xFFFF9A4A.toInt(),
            skyTop = 0xFF2E2620.toInt(), skyMid = 0xFF17120F.toInt(), skyBottom = 0xFF070605.toInt(),
            sunStops = intArrayOf(0xCCFFD8B0.toInt(), 0xC6FF9A4A.toInt(), 0xBFC4552A.toInt(), 0xB35A2A20.toInt()),
            grid = 0xFFC08060.toInt(), nebula = 0xFF8A4A30.toInt(),
            roster = intArrayOf(EK.LANCER, EK.SHIELDER, EK.MINELAYER, EK.CHARGER, EK.DRIFTER),
            bossPool = intArrayOf(BT.WARDEN, BT.FORGE, BT.GUARDIAN), musicKey = 7, terrain = Terrain.ASH
        ),
        LevelTheme(
            name = "AZURE SPIRE", subtitle = "FROZEN ARRAY", accent = 0xFF8AE6FF.toInt(),
            skyTop = 0xFF0A2440.toInt(), skyMid = 0xFF051424.toInt(), skyBottom = 0xFF02080F.toInt(),
            sunStops = intArrayOf(0xCCFFFFFF.toInt(), 0xC6BDF0FF.toInt(), 0xBF5AB8E8.toInt(), 0xB32A5A8A.toInt()),
            grid = 0xFF8AE6FF.toInt(), nebula = 0xFF4A9AD0.toInt(),
            roster = intArrayOf(EK.WISP, EK.PYLON, EK.ORBITER, EK.TURRET, EK.WEAVER),
            bossPool = intArrayOf(BT.NULLIFIER, BT.GUARDIAN), musicKey = 8, terrain = Terrain.ICE
        ),
        LevelTheme(
            name = "ROSE NEBULA", subtitle = "BLOOM FIELD", accent = 0xFFFF8ACF.toInt(),
            skyTop = 0xFF3A0E30.toInt(), skyMid = 0xFF1C061A.toInt(), skyBottom = 0xFF0A0209.toInt(),
            sunStops = intArrayOf(0xCCFFE8F6.toInt(), 0xC6FF8ACF.toInt(), 0xBFD04AA0.toInt(), 0xB3702060.toInt()),
            grid = 0xFFFF8ACF.toInt(), nebula = 0xFFC04A9A.toInt(),
            roster = intArrayOf(EK.WEAVER, EK.SWARMER, EK.WISP, EK.SPLITTER, EK.ORBITER),
            bossPool = intArrayOf(BT.GUARDIAN, BT.HIVE, BT.NULLIFIER), musicKey = 9, terrain = Terrain.BLOOM
        ),
        LevelTheme(
            name = "THE HOLLOW", subtitle = "NOTHING ANSWERS", accent = 0xFFE0E0E0.toInt(),
            skyTop = 0xFF101014.toInt(), skyMid = 0xFF08080A.toInt(), skyBottom = 0xFF020203.toInt(),
            sunStops = intArrayOf(0xCCFFFFFF.toInt(), 0xC6A0A0A8.toInt(), 0xBF505058.toInt(), 0xB31A1A20.toInt()),
            grid = 0xFF9090A0.toInt(), nebula = 0xFF505060.toInt(),
            roster = intArrayOf(
                EK.LANCER, EK.SHIELDER, EK.CARRIER, EK.PYLON, EK.WISP,
                EK.SPLITTER, EK.MINELAYER, EK.ORBITER, EK.STALKER, EK.HOWLER
            ),
            bossPool = intArrayOf(BT.NULLIFIER, BT.FORGE, BT.WARDEN, BT.HIVE), musicKey = 10, terrain = Terrain.HOLLOW
        ),
        LevelTheme(
            name = "STORM LINE", subtitle = "THUNDERHEAD", accent = 0xFF9AD8FF.toInt(),
            skyTop = 0xFF161C34.toInt(), skyMid = 0xFF0B1020.toInt(), skyBottom = 0xFF04060C.toInt(),
            sunStops = intArrayOf(0xCCE8F4FF.toInt(), 0xC69AD8FF.toInt(), 0xBF5A78C8.toInt(), 0xB32A3A70.toInt()),
            grid = 0xFF7FA8E0.toInt(), nebula = 0xFF4A5F9A.toInt(),
            roster = intArrayOf(EK.STALKER, EK.WEAVER, EK.LANCER, EK.WISP, EK.CHARGER),
            bossPool = intArrayOf(BT.WARDEN, BT.NULLIFIER), musicKey = 11, terrain = Terrain.STORM
        ),
        LevelTheme(
            name = "TIDAL REEF", subtitle = "DROWNED GARDEN", accent = 0xFF3FE0C8.toInt(),
            skyTop = 0xFF04303A.toInt(), skyMid = 0xFF021C24.toInt(), skyBottom = 0xFF010A0E.toInt(),
            sunStops = intArrayOf(0xCCD8FFF4.toInt(), 0xC63FE0C8.toInt(), 0xBF1E9AA8.toInt(), 0xB3104A66.toInt()),
            grid = 0xFF3FE0C8.toInt(), nebula = 0xFF1E7A98.toInt(),
            roster = intArrayOf(EK.SEEDER, EK.ORBITER, EK.SWARMER, EK.SPLITTER, EK.WEAVER),
            bossPool = intArrayOf(BT.HIVE, BT.GUARDIAN, BT.FORGE), musicKey = 12, terrain = Terrain.REEF
        ),
        LevelTheme(
            name = "THE BONEYARD", subtitle = "NOTHING GETS SALVAGED", accent = 0xFFC8B48A.toInt(),
            skyTop = 0xFF241E1A.toInt(), skyMid = 0xFF121010.toInt(), skyBottom = 0xFF050404.toInt(),
            sunStops = intArrayOf(0xCCFFF0D0.toInt(), 0xC6C8B48A.toInt(), 0xBF8A7050.toInt(), 0xB34A3828.toInt()),
            grid = 0xFF9A8A6A.toInt(), nebula = 0xFF6A5A44.toInt(),
            roster = intArrayOf(EK.MENDER, EK.SHIELDER, EK.TURRET, EK.HOWLER, EK.CARRIER, EK.MINELAYER),
            bossPool = intArrayOf(BT.FORGE, BT.WARDEN, BT.NULLIFIER), musicKey = 13, terrain = Terrain.WRECK
        ),
        LevelTheme(
            name = "AURORA GATE", subtitle = "LAST LIGHT", accent = 0xFFB07AFF.toInt(),
            skyTop = 0xFF1E0A3A.toInt(), skyMid = 0xFF100522.toInt(), skyBottom = 0xFF04020A.toInt(),
            sunStops = intArrayOf(0xCCFFFFFF.toInt(), 0xC6C8A0FF.toInt(), 0xBF7A4AE0.toInt(), 0xB33A1A80.toInt()),
            grid = 0xFFB07AFF.toInt(), nebula = 0xFF6A3AC8.toInt(),
            roster = intArrayOf(
                EK.STALKER, EK.HOWLER, EK.SEEDER, EK.MENDER, EK.PYLON,
                EK.LANCER, EK.WISP, EK.SPLITTER, EK.CARRIER, EK.SHIELDER
            ),
            bossPool = intArrayOf(BT.NULLIFIER, BT.HIVE, BT.FORGE, BT.WARDEN, BT.GUARDIAN),
            musicKey = 14, terrain = Terrain.AURORA
        )
    )

    /** Zero-based level index for a wave, looping once the last level is passed. */
    fun index(wave: Int): Int = ((wave - 1).coerceAtLeast(0) / WAVES_PER_LEVEL) % list.size

    /** One-based level number, continuing to count past the last theme. */
    fun number(wave: Int): Int = (wave - 1).coerceAtLeast(0) / WAVES_PER_LEVEL + 1

    fun forWave(wave: Int): LevelTheme = list[index(wave)]

    /** Extra scaling once the themes have looped all the way round. */
    fun tier(wave: Int): Int = (wave - 1).coerceAtLeast(0) / (WAVES_PER_LEVEL * list.size)

    fun isBossWave(wave: Int): Boolean = wave % 5 == 0

    /** True on the first wave of a level. */
    fun isLevelStart(wave: Int): Boolean = (wave - 1) % WAVES_PER_LEVEL == 0

    /** Which boss archetype this wave fields. */
    fun bossFor(wave: Int): Int {
        val theme = forWave(wave)
        val within = ((wave - 1) % WAVES_PER_LEVEL) / 5
        return theme.bossPool[within % theme.bossPool.size]
    }

    fun bossName(wave: Int): String = BT.names[bossFor(wave)]
}

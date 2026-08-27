package com.neonvoid.game

/** Boss archetypes, one per sector. */
object BT {
    const val GUARDIAN = 0
    const val WARDEN = 1
    const val HIVE = 2
    const val FORGE = 3
    const val NULLIFIER = 4
}

/**
 * A themed run of five waves: its own palette, enemy roster, boss and music.
 * After the last sector the list loops with a rising difficulty tier.
 */
class Sector(
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
    val boss: Int,
    val bossName: String,
    val musicKey: Int
)

object Sectors {

    val list = arrayOf(
        Sector(
            name = "NEON REACH",
            subtitle = "OUTER GRID",
            accent = Palette.MAGENTA,
            skyTop = 0xFF1A0838.toInt(), skyMid = 0xFF0D0422.toInt(), skyBottom = 0xFF05020C.toInt(),
            sunStops = intArrayOf(0xCCFFE07A.toInt(), 0xC6FFB13D.toInt(), 0xBFFF4FA3.toInt(), 0xB3B13BFF.toInt()),
            grid = Palette.CYAN,
            nebula = Palette.VIOLET,
            roster = intArrayOf(EK.DRIFTER, EK.WEAVER, EK.CHARGER),
            boss = BT.GUARDIAN,
            bossName = "GUARDIAN",
            musicKey = 0
        ),
        Sector(
            name = "CRIMSON BELT",
            subtitle = "ASTEROID LANES",
            accent = 0xFFFF5C3D.toInt(),
            skyTop = 0xFF3A0A18.toInt(), skyMid = 0xFF1A0410.toInt(), skyBottom = 0xFF0A0206.toInt(),
            sunStops = intArrayOf(0xCCFFF0A0.toInt(), 0xC6FF8A3D.toInt(), 0xBFFF3B4F.toInt(), 0xB3A81E5A.toInt()),
            grid = 0xFFFF6B5C.toInt(),
            nebula = 0xFFFF4A5C.toInt(),
            roster = intArrayOf(EK.CHARGER, EK.SWARMER, EK.LANCER, EK.DRIFTER),
            boss = BT.WARDEN,
            bossName = "WARDEN",
            musicKey = 1
        ),
        Sector(
            name = "VIOLET DEPTHS",
            subtitle = "DEAD STATION",
            accent = Palette.VIOLET,
            skyTop = 0xFF120B44.toInt(), skyMid = 0xFF08052A.toInt(), skyBottom = 0xFF03020F.toInt(),
            sunStops = intArrayOf(0xCCB0E8FF.toInt(), 0xC67AA2FF.toInt(), 0xBF6B4FFF.toInt(), 0xB33B1EA8.toInt()),
            grid = 0xFF7A5CFF.toInt(),
            nebula = 0xFF5C7AFF.toInt(),
            roster = intArrayOf(EK.TURRET, EK.ORBITER, EK.WEAVER, EK.MINELAYER),
            boss = BT.HIVE,
            bossName = "HIVE",
            musicKey = 2
        ),
        Sector(
            name = "GOLD CIRCUIT",
            subtitle = "FOUNDRY RING",
            accent = Palette.AMBER,
            skyTop = 0xFF2A2408.toInt(), skyMid = 0xFF141004.toInt(), skyBottom = 0xFF060502.toInt(),
            sunStops = intArrayOf(0xCCFFFFC0.toInt(), 0xC6FFD93D.toInt(), 0xBF9BFF57.toInt(), 0xB33DBF7A.toInt()),
            grid = Palette.LIME,
            nebula = 0xFFBFA030.toInt(),
            roster = intArrayOf(EK.SPLITTER, EK.TURRET, EK.LANCER, EK.SWARMER),
            boss = BT.FORGE,
            bossName = "FORGE",
            musicKey = 3
        ),
        Sector(
            name = "VOID CORE",
            subtitle = "NO SIGNAL",
            accent = Palette.WHITE,
            skyTop = 0xFF14142A.toInt(), skyMid = 0xFF0A0A18.toInt(), skyBottom = 0xFF040408.toInt(),
            sunStops = intArrayOf(0xCCFFFFFF.toInt(), 0xC6D0D0FF.toInt(), 0xBF9B5CFF.toInt(), 0xB3402080.toInt()),
            grid = 0xFFB0B0FF.toInt(),
            nebula = 0xFF8080FF.toInt(),
            roster = intArrayOf(EK.ORBITER, EK.SPLITTER, EK.LANCER, EK.MINELAYER, EK.CHARGER, EK.TURRET),
            boss = BT.NULLIFIER,
            bossName = "NULLIFIER",
            musicKey = 4
        )
    )

    const val WAVES_PER_SECTOR = 5

    fun index(wave: Int): Int = ((wave - 1).coerceAtLeast(0) / WAVES_PER_SECTOR) % list.size

    fun forWave(wave: Int): Sector = list[index(wave)]

    /** How many full passes through the sector list have been made - drives late-game scaling. */
    fun tier(wave: Int): Int = (wave - 1).coerceAtLeast(0) / (WAVES_PER_SECTOR * list.size)

    fun isBossWave(wave: Int): Boolean = wave % WAVES_PER_SECTOR == 0

    /** True on the first wave of a sector, when the name card should be shown. */
    fun isSectorStart(wave: Int): Boolean = (wave - 1) % WAVES_PER_SECTOR == 0
}

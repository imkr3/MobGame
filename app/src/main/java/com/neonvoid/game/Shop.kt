package com.neonvoid.game

/** A permanent upgrade bought with cores. */
class ShopItem(
    val id: Int,
    val name: String,
    val desc: String,
    val maxLevel: Int,
    val costs: IntArray,
    val color: Int
)

/**
 * The core sink beyond the hangar: permanent, modest upgrades that carry into
 * every run. Deliberately weighted towards smoothing the opening and the
 * economy rather than raw late-game power.
 */
object Shop {
    const val PLATING = 0
    const val PRIMED = 1
    const val BAY = 2
    const val GENERATOR = 3
    const val MAGNETISM = 4
    const val PRIMER = 5
    const val CONTRACT = 6
    const val COUNT = 7

    val items = arrayOf(
        ShopItem(PLATING, "HULL PLATING", "Start every run with an extra hull segment.",
            2, intArrayOf(450, 1100), Palette.ROSE),
        ShopItem(PRIMED, "PRIMED GUNS", "Launch with the main cannon already upgraded.",
            2, intArrayOf(350, 850), Palette.CYAN),
        ShopItem(BAY, "BAY EXPANSION", "One more augment slot before the bay fills.",
            2, intArrayOf(700, 1600), Palette.VIOLET),
        ShopItem(GENERATOR, "SHIELD GENERATOR", "Start each run with a shield up.",
            2, intArrayOf(500, 1200), Palette.LIME),
        ShopItem(MAGNETISM, "CORE MAGNETISM", "+15% cores from every run.",
            3, intArrayOf(300, 650, 1100), Palette.AMBER),
        ShopItem(PRIMER, "OVERDRIVE PRIMER", "Begin with the overdrive meter part charged.",
            2, intArrayOf(400, 950), Palette.AMBER),
        ShopItem(CONTRACT, "SALVAGE CONTRACT", "+10% score from everything.",
            3, intArrayOf(350, 750, 1300), Palette.SKY)
    )

    fun cost(id: Int, currentLevel: Int): Int {
        val item = items[id]
        return if (currentLevel >= item.maxLevel) -1 else item.costs[currentLevel]
    }

    /** Reads the purchased levels into the bonuses a run starts with. */
    fun meta(prefs: Prefs): Meta = Meta(
        extraLives = prefs.shopLevel(PLATING),
        startWeapon = 1 + prefs.shopLevel(PRIMED),
        extraSlots = prefs.shopLevel(BAY),
        startShield = prefs.shopLevel(GENERATOR),
        startOverdrive = clamp(0.3f * prefs.shopLevel(PRIMER), 0f, 0.75f),
        scoreMul = 1f + 0.10f * prefs.shopLevel(CONTRACT)
    )

    fun coreMultiplier(prefs: Prefs): Float = 1f + 0.15f * prefs.shopLevel(MAGNETISM)
}

/** Permanent bonuses a run begins with, assembled from [Shop] purchases. */
class Meta(
    val extraLives: Int = 0,
    val startWeapon: Int = 1,
    val extraSlots: Int = 0,
    val startShield: Int = 0,
    val startOverdrive: Float = 0f,
    val scoreMul: Float = 1f
)

package com.neonvoid.game

/** A permanent upgrade bought with cores. */
class ShopItem(
    val id: Int,
    val name: String,
    val desc: String,
    val maxLevel: Int,
    val costs: IntArray,
    val color: Int,
    /** Pilot rank the item stays hidden behind, so the list opens up over time. */
    val rank: Int = 0
)

/**
 * The core sink beyond the hangar: permanent, modest upgrades that carry into
 * every run. Deliberately weighted towards smoothing the opening and the
 * economy rather than raw late-game power, with the two run-changing items
 * (a fourth draft card, a free revive) priced as long-term goals.
 */
object Shop {
    const val PLATING = 0
    const val PRIMED = 1
    const val BAY = 2
    const val GENERATOR = 3
    const val MAGNETISM = 4
    const val PRIMER = 5
    const val CONTRACT = 6
    const val TARGETING = 7
    const val COOLANT = 8
    const val SCAVENGER = 9
    const val GRAZE_FIELD = 10
    const val DRAFT = 11
    const val REVIVE = 12
    const val FORTUNE = 13
    const val RESERVE = 14
    const val PODS = 15
    const val CANOPY = 16
    const val ESCORT = 17
    const val BROKER = 18
    const val COUNT = 19

    val items = arrayOf(
        ShopItem(PLATING, "HULL PLATING", "Start every run with an extra hull segment.",
            2, intArrayOf(450, 1100), Palette.ROSE),
        ShopItem(PRIMED, "PRIMED GUNS", "Launch with the main cannon already upgraded.",
            2, intArrayOf(350, 850), Palette.CYAN),
        ShopItem(BAY, "BAY EXPANSION", "One more augment slot before the bay fills.",
            2, intArrayOf(700, 1600), Palette.VIOLET, rank = 3),
        ShopItem(GENERATOR, "SHIELD GENERATOR", "Start each run with a shield up.",
            2, intArrayOf(500, 1200), Palette.LIME),
        ShopItem(MAGNETISM, "CORE MAGNETISM", "+15% cores from every run.",
            3, intArrayOf(300, 650, 1100), Palette.AMBER),
        ShopItem(PRIMER, "OVERDRIVE PRIMER", "Begin with the overdrive meter part charged.",
            2, intArrayOf(400, 950), Palette.AMBER),
        ShopItem(CONTRACT, "SALVAGE CONTRACT", "+10% score from everything.",
            3, intArrayOf(350, 750, 1300), Palette.SKY),
        ShopItem(TARGETING, "TARGETING RIG", "+6% damage from every source.",
            3, intArrayOf(500, 1050, 1900), Palette.RED, rank = 2),
        ShopItem(COOLANT, "COOLANT LINE", "-7% cooldown on every ability system.",
            2, intArrayOf(600, 1400), Palette.SKY, rank = 4),
        ShopItem(SCAVENGER, "SCAVENGER", "+22% chance of a pickup from a kill.",
            2, intArrayOf(450, 1050), Palette.LIME, rank = 2),
        ShopItem(GRAZE_FIELD, "GRAZE FIELD", "+30% graze radius. Overdrive comes faster.",
            2, intArrayOf(400, 900), Palette.MAGENTA, rank = 5),
        ShopItem(DRAFT, "WIDE DRAFT", "Every upgrade offers a fourth card.",
            1, intArrayOf(2400), Palette.VIOLET, rank = 8),
        ShopItem(REVIVE, "EMERGENCY CORE", "Once a run, come back instead of going down.",
            1, intArrayOf(2800), Palette.WHITE, rank = 10),
        ShopItem(FORTUNE, "FORTUNE CIRCUIT", "Summons are likelier to land a rare or better.",
            2, intArrayOf(900, 2000), Palette.AMBER, rank = 6),
        ShopItem(RESERVE, "RESERVE TANK", "+0.8s of overdrive every time you fire it.",
            3, intArrayOf(420, 900, 1600), Palette.AMBER, rank = 3),
        ShopItem(PODS, "SALVAGE PODS", "+30% score from every gem you collect.",
            3, intArrayOf(380, 820, 1500), Palette.LIME, rank = 1),
        ShopItem(CANOPY, "REINFORCED CANOPY", "+0.35s of mercy after a hit lands.",
            2, intArrayOf(520, 1250), Palette.ROSE, rank = 4),
        ShopItem(ESCORT, "ESCORT CONTRACT", "Launch with a wingman already flying.",
            1, intArrayOf(2200), Palette.WHITE, rank = 7),
        ShopItem(BROKER, "CORE BROKER", "+25% cores from every contract you claim.",
            2, intArrayOf(700, 1550), Palette.AMBER, rank = 5)
    )

    fun cost(id: Int, currentLevel: Int): Int {
        val item = items[id]
        return if (currentLevel >= item.maxLevel) -1 else item.costs[currentLevel]
    }

    /** True once the pilot has ranked far enough for the item to be on sale. */
    fun available(id: Int, prefs: Prefs): Boolean = prefs.rank >= items[id].rank

    /** Reads the purchased levels into the bonuses a run starts with. */
    fun meta(prefs: Prefs): Meta = Meta(
        extraLives = prefs.shopLevel(PLATING),
        startWeapon = 1 + prefs.shopLevel(PRIMED),
        extraSlots = prefs.shopLevel(BAY),
        startShield = prefs.shopLevel(GENERATOR),
        startOverdrive = clamp(0.3f * prefs.shopLevel(PRIMER), 0f, 0.75f),
        scoreMul = 1f + 0.10f * prefs.shopLevel(CONTRACT),
        damageMul = 1f + 0.06f * prefs.shopLevel(TARGETING),
        cooldownMul = clamp(1f - 0.07f * prefs.shopLevel(COOLANT), 0.5f, 1f),
        dropMul = 1f + 0.22f * prefs.shopLevel(SCAVENGER),
        grazeMul = 1f + 0.30f * prefs.shopLevel(GRAZE_FIELD),
        draftCards = 3 + prefs.shopLevel(DRAFT),
        revives = prefs.shopLevel(REVIVE),
        overdriveBonus = 0.8f * prefs.shopLevel(RESERVE),
        gemMul = 1f + 0.30f * prefs.shopLevel(PODS),
        mercyBonus = 0.35f * prefs.shopLevel(CANOPY),
        freeWing = prefs.shopLevel(ESCORT)
    )

    fun coreMultiplier(prefs: Prefs): Float = 1f + 0.15f * prefs.shopLevel(MAGNETISM)

    /** Extra weight FORTUNE puts behind the rarer hulls in the gacha. */
    fun fortune(prefs: Prefs): Int = prefs.shopLevel(FORTUNE)

    /** What CORE BROKER adds to a claimed contract. */
    fun contractMultiplier(prefs: Prefs): Float = 1f + 0.25f * prefs.shopLevel(BROKER)
}

/** Permanent bonuses a run begins with, assembled from [Shop] purchases. */
class Meta(
    val extraLives: Int = 0,
    val startWeapon: Int = 1,
    val extraSlots: Int = 0,
    val startShield: Int = 0,
    val startOverdrive: Float = 0f,
    val scoreMul: Float = 1f,
    val damageMul: Float = 1f,
    val cooldownMul: Float = 1f,
    val dropMul: Float = 1f,
    val grazeMul: Float = 1f,
    val draftCards: Int = 3,
    val revives: Int = 0,
    /** Extra seconds of overdrive per activation, from RESERVE TANK. */
    val overdriveBonus: Float = 0f,
    val gemMul: Float = 1f,
    /** Extra mercy seconds after a hit, from REINFORCED CANOPY. */
    val mercyBonus: Float = 0f,
    /** WING levels installed free at launch, from ESCORT CONTRACT. */
    val freeWing: Int = 0
)

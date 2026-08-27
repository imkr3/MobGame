package com.neonvoid.game

import kotlin.random.Random

/**
 * Augment catalogue. Six abilities that each branch into one of two evolutions,
 * plus a set of repeatable stat modules. The player is offered a choice after
 * every wave.
 */
object Aug {
    // abilities
    const val SPREAD = 0
    const val LANCE = 1
    const val SWARM = 2
    const val ORBIT = 3
    const val ARC = 4
    const val PULSE = 5
    const val ABILITIES = 6

    // stat modules
    const val RAPID = 6
    const val POWER = 7
    const val VELOCITY = 8
    const val AGILITY = 9
    const val MAGNET = 10
    const val GRAZE = 11
    const val ARMOR = 12
    const val SALVAGE = 13
    const val REPAIR = 14
    const val COUNT = 15

    /** Abilities cap at 3 before they must evolve, then run to 5 down the chosen branch. */
    const val BASE_MAX = 3
    const val EVOLVED_MAX = 5

    /** Keeps runs specialised: you cannot hoard every ability in one run. */
    const val MAX_ABILITIES = 4

    // branch ids
    const val A = 1
    const val B = 2

    val names = arrayOf(
        "SPREAD", "LANCE", "SWARM", "ORBIT", "ARC", "PULSE",
        "RAPID", "POWER", "VELOCITY", "AGILITY", "MAGNET", "GRAZE", "ARMOR", "SALVAGE", "REPAIR"
    )

    val statMax = intArrayOf(0, 0, 0, 0, 0, 0, 5, 4, 3, 3, 3, 3, 2, 3, 3)

    val colors = intArrayOf(
        Palette.CYAN, Palette.VIOLET, Palette.LIME, Palette.AMBER, Palette.SKY, Palette.MAGENTA,
        Palette.CYAN, Palette.RED, Palette.SKY, Palette.LIME, Palette.AMBER, Palette.MAGENTA,
        Palette.LIME, Palette.AMBER, Palette.ROSE
    )

    /** Three-letter badge codes for the HUD. */
    val codes = arrayOf(
        "SPR", "LNC", "SWM", "ORB", "ARC", "PLS",
        "RPD", "PWR", "VEL", "AGI", "MAG", "GRZ", "ARM", "SLV", "REP"
    )

    val branchNames = arrayOf(
        arrayOf("FAN", "PHALANX"),
        arrayOf("PRISM", "SIEGE"),
        arrayOf("HORNETS", "WARHEAD"),
        arrayOf("AEGIS", "SENTRY"),
        arrayOf("TEMPEST", "RAILGUN"),
        arrayOf("NOVA", "REPULSOR")
    )

    private val abilityBlurb = arrayOf(
        "Angled side cannons fire with your main gun.",
        "A piercing beam sweeps the lane ahead of you.",
        "Homing missiles hunt down whatever is closest.",
        "Nodes orbit your hull and shred what they touch.",
        "Lightning leaps from target to target.",
        "A shockwave detonates outward on a timer."
    )

    private val branchBlurb = arrayOf(
        arrayOf("Eight-shot arc. Wide, fast, relentless.", "Four heavy bolts that punch through ranks."),
        arrayOf("Three beams fan out across the screen.", "One colossal beam. Ruinous damage."),
        arrayOf("A fast swarm of light seekers.", "One heavy warhead with a blast radius."),
        arrayOf("Bigger nodes that also eat enemy fire.", "Nodes gain their own forward guns."),
        arrayOf("Storms across seven targets at once.", "One devastating bolt that pierces a line."),
        arrayOf("Huge blast that banks enemy fire as score.", "A constant field that repels enemy fire.")
    )

    private val statBlurb = arrayOf(
        "", "", "", "", "", "",
        "+10% fire rate.",
        "+1 damage on every shot.",
        "+15% projectile speed.",
        "+6% handling. The ship tracks your thumb harder.",
        "+50% pickup magnet radius.",
        "+50% overdrive charge from grazing.",
        "+1 shield capacity, and a shield right now.",
        "+15% score from everything.",
        "Repair one hull segment."
    )

    fun isAbility(id: Int): Boolean = id < ABILITIES

    fun tierName(id: Int, level: Int, branch: Int): String = when {
        !isAbility(id) -> names[id]
        branch == 0 -> names[id]
        else -> branchNames[id][branch - 1]
    }

    fun blurb(id: Int, branchPick: Int, nextLevel: Int, currentBranch: Int): String {
        if (!isAbility(id)) return statBlurb[id]
        if (branchPick != 0) return branchBlurb[id][branchPick - 1]
        if (currentBranch != 0) return "Level $nextLevel ${branchNames[id][currentBranch - 1]}. Stronger, faster, meaner."
        if (nextLevel == 1) return abilityBlurb[id]
        return when (id) {
            SPREAD -> "Level $nextLevel: ${if (nextLevel == 2) "four" else "six"} side cannons."
            LANCE -> "Level $nextLevel: wider beam, shorter cooldown."
            SWARM -> "Level $nextLevel: $nextLevel missiles per volley."
            ORBIT -> "Level $nextLevel: $nextLevel nodes, wider orbit."
            ARC -> "Level $nextLevel: ${nextLevel + 1} targets per strike."
            else -> "Level $nextLevel: bigger blast, shorter fuse."
        }
    }
}

/** One offered card. */
class AugCard(
    val id: Int,
    val branchPick: Int,
    val title: String,
    val tag: String,
    val body: String,
    val color: Int
)

/** The run's acquired augments and every stat they derive. */
class Loadout {
    val lvl = IntArray(Aug.COUNT)
    val branch = IntArray(Aug.ABILITIES)

    fun reset() {
        lvl.fill(0)
        branch.fill(0)
    }

    fun has(id: Int): Boolean = lvl[id] > 0

    fun ownedAbilities(): Int = (0 until Aug.ABILITIES).count { lvl[it] > 0 }

    fun canEvolve(id: Int): Boolean =
        Aug.isAbility(id) && lvl[id] >= Aug.BASE_MAX && branch[id] == 0

    private fun canLevel(id: Int): Boolean = when {
        !Aug.isAbility(id) -> lvl[id] < Aug.statMax[id]
        branch[id] != 0 -> lvl[id] < Aug.EVOLVED_MAX
        else -> lvl[id] < Aug.BASE_MAX
    }

    // ------------------------------------------------------------- derived

    fun fireIntervalMul(): Float = clamp(1f - 0.10f * lvl[Aug.RAPID], 0.5f, 1f)
    fun damageBonus(): Int = lvl[Aug.POWER]
    fun bulletSpeedMul(): Float = 1f + 0.15f * lvl[Aug.VELOCITY]
    fun handling(): Float = clamp(0.42f + 0.055f * lvl[Aug.AGILITY], 0.42f, 0.72f)
    fun magnetRadius(): Float = 130f * (1f + 0.5f * lvl[Aug.MAGNET])
    fun grazeCharge(): Float = 0.035f * (1f + 0.5f * lvl[Aug.GRAZE])
    fun maxShield(): Int = 2 + lvl[Aug.ARMOR]
    fun scoreMul(): Float = 1f + 0.15f * lvl[Aug.SALVAGE]

    // -------------------------------------------------------------- cards

    private fun card(id: Int, branchPick: Int): AugCard {
        val next = lvl[id] + 1
        val tag = when {
            branchPick != 0 -> "EVOLUTION"
            lvl[id] == 0 -> "NEW SYSTEM"
            else -> "LEVEL $next"
        }
        val title = when {
            branchPick != 0 -> Aug.branchNames[id][branchPick - 1]
            else -> Aug.tierName(id, next, branch.getOrElse(id) { 0 })
        }
        return AugCard(
            id = id,
            branchPick = branchPick,
            title = title,
            tag = tag,
            body = Aug.blurb(id, branchPick, next, if (Aug.isAbility(id)) branch[id] else 0),
            color = if (branchPick != 0) Palette.AMBER else Aug.colors[id]
        )
    }

    /**
     * Builds the offer. When an ability is ready to evolve, both of its branches are
     * always on the table together, so the split is an explicit fork rather than a
     * card you might never see.
     */
    fun rollOffers(count: Int): List<AugCard> {
        val out = ArrayList<AugCard>(count)

        val ready = (0 until Aug.ABILITIES).filter { canEvolve(it) }
        if (ready.isNotEmpty()) {
            val id = ready[Random.nextInt(ready.size)]
            out.add(card(id, Aug.A))
            out.add(card(id, Aug.B))
        }

        val pool = ArrayList<Int>()
        val abilityRoom = ownedAbilities() < Aug.MAX_ABILITIES
        for (id in 0 until Aug.COUNT) {
            if (out.any { it.id == id }) continue
            if (!canLevel(id)) continue
            if (Aug.isAbility(id) && lvl[id] == 0 && !abilityRoom) continue
            val weight = when {
                Aug.isAbility(id) && lvl[id] == 0 -> 4
                Aug.isAbility(id) -> 5
                else -> 3
            }
            repeat(weight) { pool.add(id) }
        }

        while (out.size < count && pool.isNotEmpty()) {
            val pick = pool[Random.nextInt(pool.size)]
            pool.removeAll { it == pick }
            out.add(card(pick, 0))
        }
        return out
    }

    /** Returns a short line describing what was taken, for the pickup banner. */
    fun apply(c: AugCard): String {
        if (c.branchPick != 0) {
            branch[c.id] = c.branchPick
            lvl[c.id] = Aug.BASE_MAX
            return Aug.branchNames[c.id][c.branchPick - 1]
        }
        lvl[c.id]++
        return "${Aug.tierName(c.id, lvl[c.id], if (Aug.isAbility(c.id)) branch[c.id] else 0)} ${lvl[c.id]}"
    }

    /** Ability ids currently owned, for HUD badges. */
    fun ownedList(out: MutableList<Int>) {
        out.clear()
        for (id in 0 until Aug.COUNT) if (lvl[id] > 0) out.add(id)
    }
}

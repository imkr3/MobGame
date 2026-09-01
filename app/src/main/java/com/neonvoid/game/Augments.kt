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
    const val FLAK = 6
    const val TETHER = 7
    const val WING = 8
    const val VORTEX = 9
    const val SENTINEL = 10
    const val CHRONO = 11
    const val RICOCHET = 12
    const val FRACTURE = 13
    const val HARVEST = 14
    const val MIRROR = 15
    const val QUAKE = 16
    const val ABILITIES = 17

    // stat modules
    const val RAPID = 17
    const val POWER = 18
    const val VELOCITY = 19
    const val AGILITY = 20
    const val MAGNET = 21
    const val GRAZE = 22
    const val ARMOR = 23
    const val SALVAGE = 24
    const val REPAIR = 25
    const val COOLANT = 26
    const val PIERCE = 27
    const val CRIT = 28
    const val RECLAIM = 29
    const val HARDPOINT = 30
    const val EVASION = 31
    const val BOUNTY = 32
    const val MOMENTUM = 33
    const val VENGEANCE = 34
    const val OVERCLOCK = 35
    const val AFTERBURN = 36
    const val RECOVERY = 37
    const val FOCUS = 38
    const val CASCADE = 39
    const val BULWARK = 40
    const val AFTERSHOCK = 41
    const val COUNT = 42

    /** Abilities cap at 3 before they must evolve, then run to 5 down the chosen branch. */
    const val BASE_MAX = 3
    const val EVOLVED_MAX = 5

    /** Keeps runs specialised: you cannot hoard every ability in one run. */
    const val MAX_ABILITIES = 3

    /**
     * Hard cap on distinct augments installed. Once the bay is full the only
     * offers are level-ups of what you already carry, so early picks are
     * commitments rather than a shopping list.
     */
    const val MAX_SLOTS = 8

    // branch ids
    const val A = 1
    const val B = 2

    val names = arrayOf(
        "SPREAD", "LANCE", "SWARM", "ORBIT", "ARC", "PULSE", "FLAK", "TETHER", "WING",
        "VORTEX", "SENTINEL", "CHRONO", "RICOCHET", "FRACTURE", "HARVEST", "MIRROR", "QUAKE",
        "RAPID", "POWER", "VELOCITY", "AGILITY", "MAGNET", "GRAZE", "ARMOR", "SALVAGE",
        "REPAIR", "COOLANT", "PIERCE", "CRIT", "RECLAIM", "HARDPOINT", "EVASION", "BOUNTY",
        "MOMENTUM", "VENGEANCE", "OVERCLOCK", "AFTERBURN", "RECOVERY",
        "FOCUS", "CASCADE", "BULWARK", "AFTERSHOCK"
    )

    val statMax = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        5, 4, 3, 3, 3, 3, 2, 3, 2, 3, 2, 3, 3, 2, 3, 3,
        3, 3, 3, 3, 3,
        3, 3, 2, 3
    )

    val colors = intArrayOf(
        Palette.CYAN, Palette.VIOLET, Palette.LIME, Palette.AMBER, Palette.SKY, Palette.MAGENTA,
        Palette.RED, Palette.ROSE, Palette.WHITE, Palette.VIOLET, Palette.SKY,
        Palette.SKY, Palette.LIME, Palette.ROSE, Palette.LIME, Palette.WHITE, Palette.AMBER,
        Palette.CYAN, Palette.RED, Palette.SKY, Palette.LIME, Palette.AMBER, Palette.MAGENTA,
        Palette.LIME, Palette.AMBER, Palette.ROSE, Palette.SKY, Palette.VIOLET, Palette.RED,
        Palette.CYAN, Palette.WHITE, Palette.LIME, Palette.AMBER,
        Palette.CYAN, Palette.RED, Palette.AMBER, Palette.RED, Palette.LIME,
        Palette.SKY, Palette.MAGENTA, Palette.LIME, Palette.RED
    )

    /** Three-letter badge codes for the HUD. */
    val codes = arrayOf(
        "SPR", "LNC", "SWM", "ORB", "ARC", "PLS", "FLK", "TTH", "WNG", "VTX", "SNT",
        "CHR", "RIC", "FRC", "HRV", "MIR", "QKE",
        "RPD", "PWR", "VEL", "AGI", "MAG", "GRZ", "ARM", "SLV", "REP", "COL", "PRC", "CRT",
        "RCL", "HRD", "EVA", "BTY", "MOM", "VNG", "OVC", "AFB", "RCV",
        "FCS", "CSC", "BWK", "AFS"
    )

    val branchNames = arrayOf(
        arrayOf("FAN", "PHALANX"),
        arrayOf("PRISM", "SIEGE"),
        arrayOf("HORNETS", "WARHEAD"),
        arrayOf("AEGIS", "SENTRY"),
        arrayOf("TEMPEST", "RAILGUN"),
        arrayOf("NOVA", "REPULSOR"),
        arrayOf("CLUSTER", "AIRBURST"),
        arrayOf("LEECH", "SIPHON"),
        arrayOf("ESCORT", "STRIKE"),
        arrayOf("SINGULARITY", "IMPLOSION"),
        arrayOf("BATTERY", "MORTAR"),
        arrayOf("STASIS", "BACKLASH"),
        arrayOf("CAROM", "DEMOLISHER"),
        arrayOf("SHATTER", "RUPTURE"),
        arrayOf("PYRE", "BLOOM"),
        arrayOf("TWIN", "PHANTOM"),
        arrayOf("FAULT", "TREMOR")
    )

    private val abilityBlurb = arrayOf(
        "Angled side cannons fire with your main gun.",
        "A piercing beam sweeps the lane ahead of you.",
        "Homing missiles hunt down whatever is closest.",
        "Nodes orbit your hull and shred what they touch.",
        "Lightning leaps from target to target.",
        "A shockwave detonates outward on a timer.",
        "Lobbed shells burst into shrapnel mid-flight.",
        "A cutting beam latches onto the nearest target.",
        "Two wingmen fly your flanks and fire with you.",
        "A singularity drags everything nearby into it.",
        "Drops a turret that holds position and fires.",
        "A time field around you drags enemy fire to a crawl.",
        "A heavy orb bounces around the screen, mauling anything it meets.",
        "Your main gun shots shatter into shards on impact.",
        "Everything you kill leaves a burning pool where it fell.",
        "A mirrored ghost of your ship fires with you from the far side.",
        "A shockwave rolls up the screen from beneath you."
    )

    private val branchBlurb = arrayOf(
        arrayOf("Eight-shot arc. Wide, fast, relentless.", "Four heavy bolts that punch through ranks."),
        arrayOf("Three beams fan out across the screen.", "One colossal beam. Ruinous damage."),
        arrayOf("A fast swarm of light seekers.", "One heavy warhead with a blast radius."),
        arrayOf("Bigger nodes that also eat enemy fire.", "Nodes gain their own forward guns."),
        arrayOf("Storms across seven targets at once.", "One devastating bolt that pierces a line."),
        arrayOf("Huge blast that banks enemy fire as score.", "A constant field that repels enemy fire."),
        arrayOf("Three shells per volley, wider spread.", "One shell, enormous burst and blast damage."),
        arrayOf("The beam feeds your overdrive as it cuts.", "Cuts far harder and drags the target in."),
        arrayOf("Wingmen soak incoming fire for you.", "Wingmen carry missiles of their own."),
        arrayOf("Wider pull that banks caught fire as score.", "Collapses into a devastating detonation."),
        arrayOf("Two turrets, and they fire faster.", "The turret lobs shells instead of bolts."),
        arrayOf(
            "A huge, deeper field. Enemies caught in it cannot shoot.",
            "Fire that lingers in the field turns and flies back at them."
        ),
        arrayOf(
            "Three fast orbs carving the screen at once.",
            "One colossal orb that detonates on every bounce."
        ),
        arrayOf(
            "Many more shards, thrown far wider.",
            "Fewer shards, but heavy ones that seek a target."
        ),
        arrayOf(
            "Pools burn far hotter and spread as they are fed.",
            "Pools drag pickups in and pay out as score."
        ),
        arrayOf(
            "Two ghosts, mirrored and offset, both firing.",
            "One ghost that also eats a shot meant for you."
        ),
        arrayOf(
            "Two waves, one behind the other, the whole width.",
            "One slow wall that grinds up the screen and holds."
        )
    )

    private val statBlurb = arrayOf(
        "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "",
        "+10% fire rate.",
        "+1 damage on every shot.",
        "+15% projectile speed.",
        "+6% handling. The ship tracks your thumb harder.",
        "+50% pickup magnet radius.",
        "+45% overdrive charge from grazing.",
        "+1 shield capacity, and a shield right now.",
        "+15% score from everything.",
        "Repair one hull segment.",
        "-10% cooldown on every ability system.",
        "Main gun shots punch through one more enemy.",
        "+12% chance for a shot to hit twice.",
        "Pickups top up your overdrive meter.",
        "Raises the main gun ceiling, and one level right now.",
        "+0.35s of mercy after taking a hit.",
        "+40% gem drops, and they are worth more.",
        "+14% damage while you are moving at speed.",
        "Taking a hit leaves you furious: +25% damage and fire rate for 5s.",
        "+20% longer overdrive, and it charges 20% faster.",
        "Main gun hits set the target alight.",
        "A spent shield grows back on its own.",
        "+16% damage while you hold still. Reward for standing your ground.",
        "Every kill speeds the next shot. It stacks, and it decays.",
        "Each shield soaks two hits instead of one.",
        "A kill can take its neighbours with it."
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
            FLAK -> "Level $nextLevel: more shrapnel, shorter fuse."
            TETHER -> "Level $nextLevel: the beam bites deeper."
            WING -> "Level $nextLevel: wingmen fire faster."
            VORTEX -> "Level $nextLevel: wider pull, harder bite."
            SENTINEL -> "Level $nextLevel: the turret lasts longer."
            HARVEST -> "Level $nextLevel: bigger pools that burn for longer."
            MIRROR -> "Level $nextLevel: the ghost fires faster and hits harder."
            QUAKE -> "Level $nextLevel: a taller wave on a shorter fuse."
            CHRONO -> "Level $nextLevel: a wider field, and a deeper crawl."
            RICOCHET -> "Level $nextLevel: the orb hits harder and lives longer."
            FRACTURE -> "Level $nextLevel: ${nextLevel + 2} shards, each one meaner."
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
    /** Extra bay slots bought in the shop. */
    var bonusSlots = 0

    fun reset() {
        lvl.fill(0)
        branch.fill(0)
    }

    fun has(id: Int): Boolean = lvl[id] > 0

    fun ownedAbilities(): Int = (0 until Aug.ABILITIES).count { lvl[it] > 0 }

    fun slotsUsed(): Int = (0 until Aug.COUNT).count { lvl[it] > 0 }

    fun maxSlots(): Int = Aug.MAX_SLOTS + bonusSlots

    fun slotsFull(): Boolean = slotsUsed() >= maxSlots()

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
    fun grazeCharge(): Float = 0.020f * (1f + 0.45f * lvl[Aug.GRAZE])
    fun maxShield(): Int = 1 + lvl[Aug.ARMOR]
    fun scoreMul(): Float = 1f + 0.15f * lvl[Aug.SALVAGE]
    fun cooldownMul(): Float = clamp(1f - 0.10f * lvl[Aug.COOLANT], 0.6f, 1f)
    fun extraPierce(): Int = lvl[Aug.PIERCE]
    fun critChance(): Float = 0.12f * lvl[Aug.CRIT]
    fun reclaimCharge(): Float = 0.06f * lvl[Aug.RECLAIM]
    fun maxWeapon(): Int = 5 + lvl[Aug.HARDPOINT]
    fun mercyBonus(): Float = 0.35f * lvl[Aug.EVASION]
    fun gemBonus(): Float = 0.40f * lvl[Aug.BOUNTY]

    /** Damage multiplier when the ship is not moving; FOCUS is the still hand. */
    fun focusBonus(): Float = 0.16f * lvl[Aug.FOCUS]
    /** Fire-rate cut per stack of CASCADE, and how many stacks it can hold. */
    fun cascadeStep(): Float = if (lvl[Aug.CASCADE] > 0) 0.035f else 0f
    fun cascadeMax(): Int = lvl[Aug.CASCADE] * 4
    /** Hits one shield pip absorbs. */
    fun shieldDepth(): Int = 1 + lvl[Aug.BULWARK]
    /** Chance a kill detonates, and the blast it leaves. */
    fun aftershockChance(): Float = 0.14f * lvl[Aug.AFTERSHOCK]
    fun aftershockRadius(): Float = 54f + 16f * lvl[Aug.AFTERSHOCK]

    /** Damage multiplier at full throttle; scales with how hard you are moving. */
    fun momentumBonus(): Float = 0.14f * lvl[Aug.MOMENTUM]
    fun revengeSeconds(): Float = if (lvl[Aug.VENGEANCE] > 0) 5f else 0f
    fun revengeMul(): Float = 1f + 0.25f * lvl[Aug.VENGEANCE]
    fun overdriveSeconds(): Float = 1f + 0.20f * lvl[Aug.OVERCLOCK]
    fun overdriveCharge(): Float = 1f + 0.20f * lvl[Aug.OVERCLOCK]
    /** Damage per second an ignited enemy takes; zero when the module is absent. */
    fun burnDps(): Float = if (lvl[Aug.AFTERBURN] > 0) 3f + 2.5f * lvl[Aug.AFTERBURN] else 0f
    /** Seconds to regrow one spent shield pip, or zero when never. */
    fun shieldRegen(): Float = when (lvl[Aug.RECOVERY]) {
        0 -> 0f
        1 -> 24f
        2 -> 17f
        else -> 12f
    }

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
        val bayFull = slotsFull()
        val abilityRoom = ownedAbilities() < Aug.MAX_ABILITIES && !bayFull
        for (id in 0 until Aug.COUNT) {
            if (out.any { it.id == id }) continue
            if (!canLevel(id)) continue
            if (lvl[id] == 0 && bayFull) continue
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

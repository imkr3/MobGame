package com.neonvoid.game

import android.graphics.Path
import kotlin.random.Random

object Rarity {
    const val COMMON = 0
    const val RARE = 1
    const val EPIC = 2
    const val LEGENDARY = 3

    val names = arrayOf("COMMON", "RARE", "EPIC", "LEGENDARY")
    val colors = intArrayOf(Palette.SKY, Palette.CYAN, Palette.VIOLET, Palette.AMBER)
    /** Pull weights, roughly 60 / 27 / 10.5 / 2.5 percent. */
    val weights = intArrayOf(240, 108, 42, 10)
    val dupeRefund = intArrayOf(35, 60, 120, 250)
}

/**
 * A flyable hull. Stats are multipliers on the baseline ship, and a signature
 * augment (if any) is installed for free at the start of every run.
 */
class Ship(
    val id: Int,
    val name: String,
    val rarity: Int,
    val hull: Int,
    val color: Int,
    val blurb: String,
    val lives: Int = 3,
    val hitR: Float = 5.5f,
    val handlingMul: Float = 1f,
    val fireMul: Float = 1f,
    val damageBonus: Int = 0,
    val startShield: Int = 0,
    val grazeMul: Float = 1f,
    val scoreMul: Float = 1f,
    val magnetMul: Float = 1f,
    val signature: Int = -1,
    val signatureLevel: Int = 0
) {
    /** Short stat lines for the hangar panel. */
    fun statLines(out: MutableList<String>) {
        out.clear()
        out.add("HULL $lives")
        if (hitR < 5.4f) out.add("CORE ${oneDecimal(hitR)}")
        if (handlingMul != 1f) out.add("HANDLING ${signed((handlingMul - 1f) * 100f)}%")
        if (fireMul != 1f) out.add("FIRE RATE ${signed((1f / fireMul - 1f) * 100f)}%")
        if (damageBonus != 0) out.add("DAMAGE +$damageBonus")
        if (startShield > 0) out.add("SHIELD +$startShield")
        if (grazeMul != 1f) out.add("GRAZE ${signed((grazeMul - 1f) * 100f)}%")
        if (scoreMul != 1f) out.add("SCORE ${signed((scoreMul - 1f) * 100f)}%")
        if (magnetMul != 1f) out.add("MAGNET ${signed((magnetMul - 1f) * 100f)}%")
    }

    val signatureText: String
        get() = if (signature < 0) "NO SIGNATURE SYSTEM"
        else "SIGNATURE: ${Aug.names[signature]} LV$signatureLevel"
}

private fun signed(v: Float): String {
    val r = (v + if (v >= 0) 0.5f else -0.5f).toInt()
    return if (r >= 0) "+$r" else "$r"
}

object ShipDex {

    val list = arrayOf(
        Ship(0, "VECTOR", Rarity.COMMON, 0, Palette.CYAN,
            "The standard interceptor. No tricks, no weaknesses."),
        Ship(1, "PIKE", Rarity.COMMON, 1, Palette.RED,
            "Heavier rounds, heavier stick.",
            damageBonus = 1, handlingMul = 0.88f, fireMul = 1.06f),
        Ship(2, "KITE", Rarity.COMMON, 2, Palette.LIME,
            "Light frame. Turns on nothing, hits like it.",
            handlingMul = 1.16f, hitR = 5.0f, fireMul = 1.08f),
        Ship(3, "BULWARK", Rarity.RARE, 3, Palette.LIME,
            "Armoured picket. Slow guns, deep hull.",
            lives = 4, startShield = 1, fireMul = 1.14f, handlingMul = 0.92f),
        Ship(4, "VOLT", Rarity.RARE, 1, Palette.CYAN,
            "Ships with side cannons already bolted on.",
            fireMul = 0.95f, signature = Aug.SPREAD, signatureLevel = 1),
        Ship(5, "LANTERN", Rarity.RARE, 2, Palette.AMBER,
            "Salvage rig. Pulls in everything worth money.",
            magnetMul = 2.0f, scoreMul = 1.15f),
        Ship(6, "SABRE", Rarity.EPIC, 4, Palette.VIOLET,
            "Beam-tuned reactor. Cuts a lane and holds it.",
            fireMul = 0.9f, signature = Aug.LANCE, signatureLevel = 1),
        Ship(7, "HALO", Rarity.EPIC, 3, Palette.LIME,
            "Flies inside its own escort of nodes.",
            startShield = 1, signature = Aug.ORBIT, signatureLevel = 1),
        Ship(8, "WRAITH", Rarity.EPIC, 1, Palette.MAGENTA,
            "A sliver of a target. One mistake and it is over.",
            lives = 2, hitR = 3.8f, grazeMul = 1.7f, handlingMul = 1.1f),
        Ship(9, "NOVA-9", Rarity.LEGENDARY, 4, Palette.AMBER,
            "Detonation core. Clears the room and bills for it.",
            damageBonus = 1, scoreMul = 1.25f, signature = Aug.PULSE, signatureLevel = 2),
        Ship(10, "ARCLIGHT", Rarity.LEGENDARY, 0, Palette.SKY,
            "Storm coil primed before you even launch.",
            damageBonus = 2, fireMul = 1.05f, signature = Aug.ARC, signatureLevel = 2),
        Ship(11, "SPUR", Rarity.COMMON, 5, Palette.SKY,
            "Racing frame. Quick everywhere except the trigger.",
            handlingMul = 1.22f, hitR = 5.0f, fireMul = 1.12f),
        Ship(12, "EMBER", Rarity.RARE, 6, Palette.RED,
            "Mortar rig. Lobs its answer over the front line.",
            damageBonus = 1, fireMul = 1.06f, signature = Aug.FLAK, signatureLevel = 1),
        Ship(13, "GLASS", Rarity.RARE, 5, Palette.WHITE,
            "One hull segment. Enormous guns. Good luck.",
            lives = 1, damageBonus = 2, fireMul = 0.9f, hitR = 4.6f),
        Ship(14, "VESPER", Rarity.EPIC, 7, Palette.ROSE,
            "Cutting beam on a short leash. Fly close.",
            grazeMul = 1.3f, signature = Aug.TETHER, signatureLevel = 1),
        Ship(15, "TITAN", Rarity.EPIC, 3, Palette.AMBER,
            "Five segments of hull and a very slow trigger.",
            lives = 5, startShield = 1, handlingMul = 0.8f, fireMul = 1.22f, damageBonus = 2),
        Ship(16, "ORACLE", Rarity.LEGENDARY, 7, Palette.CYAN,
            "Launches with its own escort already flying.",
            scoreMul = 1.2f, magnetMul = 1.5f, signature = Aug.WING, signatureLevel = 2),
        Ship(17, "PHANTOM", Rarity.LEGENDARY, 5, Palette.MAGENTA,
            "Barely there. Grazes charge it almost instantly.",
            lives = 2, hitR = 3.4f, grazeMul = 2.0f, handlingMul = 1.2f,
            signature = Aug.ARC, signatureLevel = 1),
        Ship(18, "QUILL", Rarity.COMMON, 5, Palette.ROSE,
            "Light, twitchy, and quicker on the trigger than it looks.",
            handlingMul = 1.08f, fireMul = 0.94f, hitR = 5.2f),
        Ship(19, "CINDER", Rarity.RARE, 6, Palette.AMBER,
            "Carries a deployable gun in the bay.",
            fireMul = 1.04f, signature = Aug.SENTINEL, signatureLevel = 1),
        Ship(20, "MAW", Rarity.RARE, 3, Palette.VIOLET,
            "Runs a collapsing field. Heavy, and it pulls.",
            handlingMul = 0.9f, lives = 4, signature = Aug.VORTEX, signatureLevel = 1),
        Ship(21, "SPECTRE", Rarity.EPIC, 7, Palette.WHITE,
            "Slips between shots and gets paid for it.",
            hitR = 4.2f, grazeMul = 1.5f, scoreMul = 1.1f, handlingMul = 1.05f),
        Ship(22, "JUGGERNAUT", Rarity.EPIC, 6, Palette.RED,
            "Enormous guns bolted to an enormous frame.",
            lives = 4, damageBonus = 3, fireMul = 1.3f, handlingMul = 0.78f),
        Ship(23, "ZENITH", Rarity.LEGENDARY, 4, Palette.LIME,
            "Opens with a singularity already spun up.",
            magnetMul = 1.6f, scoreMul = 1.15f, signature = Aug.VORTEX, signatureLevel = 2)
    )

    const val PULL_COST = 100
    const val STARTER = 0

    fun byId(id: Int): Ship = list.getOrElse(id) { list[STARTER] }

    fun isOwned(mask: Int, id: Int): Boolean = (mask shr id) and 1 == 1

    fun withOwned(mask: Int, id: Int): Int = mask or (1 shl id)

    fun ownedCount(mask: Int): Int = list.count { isOwned(mask, it.id) }

    /** Pull restricted to a minimum rarity - the ten-pull guarantee. */
    fun rollAtLeast(minRarity: Int): Ship {
        val pool = list.filter { it.rarity >= minRarity }
        if (pool.isEmpty()) return roll()
        var total = 0
        for (s in pool) total += Rarity.weights[s.rarity]
        var pick = Random.nextInt(total)
        for (s in pool) {
            pick -= Rarity.weights[s.rarity]
            if (pick < 0) return s
        }
        return pool[0]
    }

    /** Weighted pull: rarity first, then a uniform pick inside that rarity. */
    /** The pull weight of one rarity band at a given FORTUNE CIRCUIT level. */
    fun weightOf(rarity: Int, fortune: Int): Int {
        val w = Rarity.weights[rarity]
        return if (rarity == Rarity.COMMON) (w * (1f - 0.16f * fortune)).toInt().coerceAtLeast(60)
        else (w * (1f + 0.30f * fortune)).toInt()
    }

    /** Per-rarity pull chance, 0..1, for the hangar's odds strip. */
    fun odds(fortune: Int): FloatArray {
        val out = FloatArray(Rarity.names.size)
        var total = 0
        for (s in list) total += weightOf(s.rarity, fortune)
        for (s in list) out[s.rarity] += weightOf(s.rarity, fortune).toFloat()
        for (i in out.indices) out[i] /= total.coerceAtLeast(1)
        return out
    }

    /**
     * A pull. [fortune] is the FORTUNE CIRCUIT level, which leans the weights
     * towards the rarer end without ever guaranteeing anything.
     */
    fun roll(fortune: Int = 0): Ship {
        fun weight(rarity: Int): Int = weightOf(rarity, fortune)
        var total = 0
        for (s in list) total += weight(s.rarity)
        var pick = Random.nextInt(total)
        for (s in list) {
            pick -= weight(s.rarity)
            if (pick < 0) return s
        }
        return list[0]
    }
}

/** Hull silhouettes, unit sized. Index matches [Ship.hull]. */
object Hulls {
    val paths: Array<Path> = arrayOf(
        // 0 ARROW - the original
        Shapes.player,
        // 1 DART - narrow and forward-swept
        Path().apply {
            moveTo(0f, -1.3f)
            lineTo(0.34f, 0.15f)
            lineTo(0.52f, 0.62f)
            lineTo(0.16f, 0.4f)
            lineTo(0f, 0.66f)
            lineTo(-0.16f, 0.4f)
            lineTo(-0.52f, 0.62f)
            lineTo(-0.34f, 0.15f)
            close()
        },
        // 2 DELTA - wide glider
        Path().apply {
            moveTo(0f, -1.0f)
            lineTo(0.95f, 0.55f)
            lineTo(0.4f, 0.36f)
            lineTo(0f, 0.62f)
            lineTo(-0.4f, 0.36f)
            lineTo(-0.95f, 0.55f)
            close()
        },
        // 3 BASTION - blunt and armoured
        Path().apply {
            moveTo(0f, -0.95f)
            lineTo(0.5f, -0.5f)
            lineTo(0.72f, 0.3f)
            lineTo(0.36f, 0.62f)
            lineTo(-0.36f, 0.62f)
            lineTo(-0.72f, 0.3f)
            lineTo(-0.5f, -0.5f)
            close()
        },
        // 4 TALON - hooked wings
        Path().apply {
            moveTo(0f, -1.2f)
            lineTo(0.28f, -0.2f)
            lineTo(0.85f, 0.1f)
            lineTo(0.6f, 0.66f)
            lineTo(0.22f, 0.3f)
            lineTo(0f, 0.7f)
            lineTo(-0.22f, 0.3f)
            lineTo(-0.6f, 0.66f)
            lineTo(-0.85f, 0.1f)
            lineTo(-0.28f, -0.2f)
            close()
        },
        // 5 NEEDLE - almost nothing to hit
        Path().apply {
            moveTo(0f, -1.35f)
            lineTo(0.2f, 0.1f)
            lineTo(0.44f, 0.7f)
            lineTo(0.1f, 0.5f)
            lineTo(0f, 0.75f)
            lineTo(-0.1f, 0.5f)
            lineTo(-0.44f, 0.7f)
            lineTo(-0.2f, 0.1f)
            close()
        },
        // 6 CROSS - cruciform bomber
        Path().apply {
            moveTo(0f, -1.05f)
            lineTo(0.24f, -0.35f)
            lineTo(1.0f, -0.2f)
            lineTo(1.0f, 0.16f)
            lineTo(0.24f, 0.2f)
            lineTo(0.4f, 0.75f)
            lineTo(0f, 0.55f)
            lineTo(-0.4f, 0.75f)
            lineTo(-0.24f, 0.2f)
            lineTo(-1.0f, 0.16f)
            lineTo(-1.0f, -0.2f)
            lineTo(-0.24f, -0.35f)
            close()
        },
        // 7 ORB - rounded body with swept fins
        Path().apply {
            moveTo(0f, -1.0f)
            lineTo(0.42f, -0.62f)
            lineTo(0.56f, 0.05f)
            lineTo(0.86f, 0.62f)
            lineTo(0.3f, 0.45f)
            lineTo(0f, 0.7f)
            lineTo(-0.3f, 0.45f)
            lineTo(-0.86f, 0.62f)
            lineTo(-0.56f, 0.05f)
            lineTo(-0.42f, -0.62f)
            close()
        }
    )

    fun of(ship: Ship): Path = paths.getOrElse(ship.hull) { paths[0] }
}

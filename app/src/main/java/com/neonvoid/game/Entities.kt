package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object EK {
    const val DRIFTER = 0
    const val WEAVER = 1
    const val CHARGER = 2
    const val TURRET = 3
    const val BOSS = 4
    const val LANCER = 5
    const val ORBITER = 6
    const val SPLITTER = 7
    const val MINELAYER = 8
    const val SWARMER = 9
    const val MINE = 10
    const val SHIELDER = 11
    const val WISP = 12
    const val CARRIER = 13
    const val PYLON = 14
    const val STALKER = 15
    const val HOWLER = 16
    const val SEEDER = 17
    const val MENDER = 18
    const val POD = 19
    const val COUNT = 20

    val displayNames = arrayOf(
        "DRIFTER", "WEAVER", "CHARGER", "TURRET", "BOSS",
        "LANCER", "ORBITER", "SPLITTER", "MINELAYER", "SWARMER", "MINE",
        "SHIELDER", "WISP", "CARRIER", "PYLON",
        "STALKER", "HOWLER", "SEEDER", "MENDER", "POD"
    )
}

object PK {
    const val WEAPON = 0
    const val SHIELD = 1
    const val LIFE = 2
    const val GEM = 3
}

class Bullet {
    var active = false
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var r = 4f
    var damage = 1
    var hostile = false
    var color = Palette.CYAN
    var style = 0            // 0 = orb, 1 = lance, 2 = heavy, 3 = missile
    var life = 0f
    var grazed = false
    var spin = 0f
    var pierce = 0           // extra enemies this shot can pass through
    var hitCd = 0f           // re-hit lockout so a piercing shot cannot chew one target
    var homing = false
    var turn = 0f            // radians per second
    var target = -1
    var splash = 0f          // blast radius on impact
    var fuse = 0f            // seconds until it bursts on its own
    var shrapnel = 0         // fragments produced when it bursts
    var dwell = 0f           // seconds spent inside a CHRONO field
    var fracture = 0         // shards thrown when this shot lands
    var shardDamage = 0      // damage each of those shards carries
    var shardHoming = false
    var burn = 0f            // damage per second this shot leaves behind
}

fun Enemy.seedPhase(): Float = (x + y) * 0.05f

class Enemy {
    var active = false
    var kind = EK.DRIFTER
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var hp = 1f; var maxHp = 1f
    var r = 16f
    var t = 0f
    var fireT = 0f
    var fireEvery = 2f
    var amp = 0f
    var freq = 1f
    var baseX = 0f
    var holdY = 0f
    var angle = 0f
    var score = 100
    var hitFlash = 0f
    var state = 0
    var stateT = 0f
    var color = Palette.SKY
    var phase = 0
    var patternT = 0f
    var spiral = 0f
    var dropBias = 1f
    var bossType = BT.GUARDIAN
    var seed = 0f            // stable per-enemy phase offset
    var tier = 0             // splitter generation
    var elite = false
    var aux = 0f             // per-kind scratch (charge timers, orbit centres)
    var aux2 = 0f
    var telegraph = 0f       // 0..1 wind-up indicator
    var link = -1            // partner index, for paired enemies
    var burn = 0f            // seconds left alight
    var burnDps = 0f
}

class PowerUp {
    var active = false
    var kind = PK.GEM
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 60f
    var t = 0f
    var r = 13f
    var life = 12f
}

class Player {
    var x = 0f; var y = 0f
    var tx = 0f; var ty = 0f          // finger-driven target
    var lives = 3
    var weapon = 1
    var shield = 0
    var invuln = 0f
    var fireT = 0f
    var overdrive = 0f                // charge 0..1
    var odTime = 0f                   // seconds of overdrive left
    var alive = true
    var respawnT = 0f
    var bank = 0f                     // visual roll from lateral movement
    var thrust = 0f
    var shipId = 0
    var hitR = 5.5f                   // small hitbox: grazing is the point
    var revenge = 0f                  // seconds of VENGEANCE fury left
    var cascade = 0                   // CASCADE stacks from recent kills
    var cascadeT = 0f
    var shieldHits = 1                // hits the current shield pip absorbs
    var regenT = 0f                   // countdown to the next regrown shield
    val bodyR = 15f
}

/** Unit-space silhouettes (radius 1), drawn with canvas transforms. */
object Shapes {
    val player: Path = Path().apply {
        moveTo(0f, -1.15f)
        lineTo(0.62f, 0.5f)
        lineTo(0.30f, 0.34f)
        lineTo(0f, 0.72f)
        lineTo(-0.30f, 0.34f)
        lineTo(-0.62f, 0.5f)
        close()
    }
    val playerWing: Path = Path().apply {
        moveTo(-0.95f, 0.18f)
        lineTo(-0.34f, -0.12f)
        moveTo(0.95f, 0.18f)
        lineTo(0.34f, -0.12f)
    }
    val drifter: Path = Path().apply {
        moveTo(0f, 1f)
        lineTo(0.85f, -0.25f)
        lineTo(0.35f, -0.8f)
        lineTo(-0.35f, -0.8f)
        lineTo(-0.85f, -0.25f)
        close()
    }
    val weaver: Path = Path().apply {
        moveTo(0f, 0.95f)
        lineTo(1.15f, -0.15f)
        lineTo(0.35f, -0.15f)
        lineTo(0.15f, -0.9f)
        lineTo(-0.15f, -0.9f)
        lineTo(-0.35f, -0.15f)
        lineTo(-1.15f, -0.15f)
        close()
    }
    val charger: Path = Path().apply {
        moveTo(0f, 1.25f)
        lineTo(0.55f, -0.35f)
        lineTo(0f, -0.9f)
        lineTo(-0.55f, -0.35f)
        close()
    }
    val turret: Path = Path().apply {
        for (i in 0 until 6) {
            val a = i * TAU / 6f + TAU / 12f
            val px = cos(a); val py = sin(a)
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    /** STALKER: a narrow dart, all forward, with swept-back barbs. */
    val stalker: Path = Path().apply {
        moveTo(0f, 1.3f)
        lineTo(0.30f, 0.10f)
        lineTo(0.80f, -0.55f)
        lineTo(0.34f, -0.42f)
        lineTo(0.16f, -0.95f)
        lineTo(-0.16f, -0.95f)
        lineTo(-0.34f, -0.42f)
        lineTo(-0.80f, -0.55f)
        lineTo(-0.30f, 0.10f)
        close()
    }

    /** HOWLER: a wide open horn, the mouth facing you. */
    val howler: Path = Path().apply {
        moveTo(0f, 0.55f)
        lineTo(0.95f, 0.85f)
        lineTo(1.05f, 0.10f)
        lineTo(0.55f, -0.55f)
        lineTo(0f, -0.75f)
        lineTo(-0.55f, -0.55f)
        lineTo(-1.05f, 0.10f)
        lineTo(-0.95f, 0.85f)
        close()
    }

    /** SEEDER: a bulbous carrier with a split underside. */
    val seeder: Path = Path().apply {
        moveTo(0f, 0.95f)
        lineTo(0.52f, 0.60f)
        lineTo(0.90f, -0.10f)
        lineTo(0.60f, -0.80f)
        lineTo(0.20f, -0.45f)
        lineTo(-0.20f, -0.45f)
        lineTo(-0.60f, -0.80f)
        lineTo(-0.90f, -0.10f)
        lineTo(-0.52f, 0.60f)
        close()
    }

    /** MENDER: a cross-braced support frame. */
    val mender: Path = Path().apply {
        moveTo(0f, 1.0f)
        lineTo(0.42f, 0.42f)
        lineTo(1.0f, 0f)
        lineTo(0.42f, -0.42f)
        lineTo(0f, -1.0f)
        lineTo(-0.42f, -0.42f)
        lineTo(-1.0f, 0f)
        lineTo(-0.42f, 0.42f)
        close()
    }

    /** POD: the seed a seeder drops, before it blooms. */
    val pod: Path = Path().apply {
        moveTo(0f, 1f)
        lineTo(0.72f, 0.30f)
        lineTo(0.45f, -0.85f)
        lineTo(-0.45f, -0.85f)
        lineTo(-0.72f, 0.30f)
        close()
    }

    val boss: Path = Path().apply {
        moveTo(0f, 1.0f)
        lineTo(0.55f, 0.45f)
        lineTo(1.35f, 0.30f)
        lineTo(1.05f, -0.30f)
        lineTo(0.45f, -0.20f)
        lineTo(0.30f, -0.85f)
        lineTo(-0.30f, -0.85f)
        lineTo(-0.45f, -0.20f)
        lineTo(-1.05f, -0.30f)
        lineTo(-1.35f, 0.30f)
        lineTo(-0.55f, 0.45f)
        close()
    }
    val bossCore: Path = Path().apply {
        moveTo(0f, 0.42f)
        lineTo(0.32f, 0f)
        lineTo(0f, -0.42f)
        lineTo(-0.32f, 0f)
        close()
    }
    val lancer: Path = Path().apply {
        moveTo(0f, 1.25f)
        lineTo(0.30f, 0.35f)
        lineTo(0.75f, 0.05f)
        lineTo(0.45f, -0.55f)
        lineTo(0.18f, -0.30f)
        lineTo(-0.18f, -0.30f)
        lineTo(-0.45f, -0.55f)
        lineTo(-0.75f, 0.05f)
        lineTo(-0.30f, 0.35f)
        close()
    }
    val orbiter: Path = Path().apply {
        moveTo(0f, 1f)
        lineTo(0.7f, 0.35f)
        lineTo(0.5f, -0.5f)
        lineTo(0f, -0.85f)
        lineTo(-0.5f, -0.5f)
        lineTo(-0.7f, 0.35f)
        close()
    }
    val splitter: Path = Path().apply {
        moveTo(0f, 0.95f)
        lineTo(0.62f, 0.62f)
        lineTo(0.9f, 0f)
        lineTo(0.62f, -0.62f)
        lineTo(0f, -0.5f)
        lineTo(-0.62f, -0.62f)
        lineTo(-0.9f, 0f)
        lineTo(-0.62f, 0.62f)
        close()
    }
    val minelayer: Path = Path().apply {
        moveTo(0f, 0.7f)
        lineTo(1.15f, 0.45f)
        lineTo(1.0f, -0.35f)
        lineTo(0.4f, -0.7f)
        lineTo(-0.4f, -0.7f)
        lineTo(-1.0f, -0.35f)
        lineTo(-1.15f, 0.45f)
        close()
    }
    val swarmer: Path = Path().apply {
        moveTo(0f, 1.15f)
        lineTo(0.6f, -0.3f)
        lineTo(0f, -0.6f)
        lineTo(-0.6f, -0.3f)
        close()
    }
    val mine: Path = Path().apply {
        for (i in 0 until 8) {
            val a = i * TAU / 8f
            val r = if (i % 2 == 0) 1f else 0.52f
            val px = cos(a) * r
            val py = sin(a) * r
            if (i == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    val shielder: Path = Path().apply {
        moveTo(0f, 0.55f)
        lineTo(0.9f, 0.25f)
        lineTo(0.75f, -0.5f)
        lineTo(0.3f, -0.85f)
        lineTo(-0.3f, -0.85f)
        lineTo(-0.75f, -0.5f)
        lineTo(-0.9f, 0.25f)
        close()
    }
    val wisp: Path = Path().apply {
        moveTo(0f, 1f)
        lineTo(0.45f, 0.15f)
        lineTo(0.28f, -0.75f)
        lineTo(-0.28f, -0.75f)
        lineTo(-0.45f, 0.15f)
        close()
    }
    val carrier: Path = Path().apply {
        moveTo(0f, 0.95f)
        lineTo(0.7f, 0.6f)
        lineTo(1.2f, -0.1f)
        lineTo(0.8f, -0.75f)
        lineTo(-0.8f, -0.75f)
        lineTo(-1.2f, -0.1f)
        lineTo(-0.7f, 0.6f)
        close()
    }
    val pylon: Path = Path().apply {
        moveTo(0f, 1.1f)
        lineTo(0.5f, 0.2f)
        lineTo(0.34f, -0.9f)
        lineTo(-0.34f, -0.9f)
        lineTo(-0.5f, 0.2f)
        close()
    }
    val diamond: Path = Path().apply {
        moveTo(0f, -1f)
        lineTo(1f, 0f)
        lineTo(0f, 1f)
        lineTo(-1f, 0f)
        close()
    }
}

object Draw {

    fun player(c: Canvas, p: Player, timeNow: Float) {
        if (!p.alive) return
        val blink = p.invuln > 0f && ((timeNow * 22f).toInt() % 2 == 0)
        val od = p.odTime > 0f
        val ship = ShipDex.byId(p.shipId)
        val baseColor = when {
            od -> Palette.AMBER
            p.shield > 0 -> Palette.LIME
            else -> ship.color
        }
        val hull = Hulls.of(ship)
        val a = if (blink) 0.42f else 1f
        val s = p.bodyR

        // engine plume: a wide soft wash, a bright core and a tapering trail
        val plume = (0.55f + p.thrust * 0.75f) * (0.85f + 0.15f * sin(timeNow * 40f))
        val plumeCol = if (od) Palette.ROSE else Palette.MAGENTA
        Neon.softDisc(c, p.x, p.y + s * 1.0f, s * 0.85f * plume, fade(plumeCol, 0.20f * a))
        Neon.orb(c, p.x, p.y + s * 0.85f, s * 0.30f * plume, fade(plumeCol, 0.75f * a), 1.1f)
        Neon.orb(c, p.x, p.y + s * 1.25f * plume, s * 0.16f * plume, fade(Palette.VIOLET, 0.5f * a), 0.9f)
        Neon.orb(c, p.x, p.y + s * 1.7f * plume, s * 0.08f * plume, fade(Palette.VIOLET, 0.3f * a), 0.7f)

        c.save()
        c.translate(p.x, p.y)
        c.rotate(p.bank * 16f)
        c.scale(s, s)
        Neon.fillPath(c, hull, fade(baseColor, 0.16f * a))
        Neon.path(c, hull, fade(baseColor, a), 1.7f / s, 1f, 0.9f)
        // rim light down the nose
        c.save()
        c.translate(0f, -0.06f)
        c.scale(0.9f, 0.9f)
        Neon.path(c, hull, fade(lighten(baseColor, 0.6f), 0.55f * a), 1.1f / s, 0f, 0.6f)
        c.restore()
        Neon.path(c, Shapes.playerWing, fade(lighten(baseColor, 0.3f), 0.8f * a), 1.1f / s, 0.7f, 0.6f)
        // panel seams and a lit canopy
        Neon.hairline(c, -0.30f, 0.30f, 0f, -0.55f, fade(baseColor, 0.45f * a), 0.9f / s)
        Neon.hairline(c, 0.30f, 0.30f, 0f, -0.55f, fade(baseColor, 0.45f * a), 0.9f / s)
        val canopy = 0.7f + 0.3f * sin(timeNow * 3.2f)
        Neon.orb(c, 0f, -0.30f, 0.17f, fade(lighten(baseColor, 0.8f), 0.9f * a * canopy), 0.9f)
        c.restore()

        if (p.shield > 0) {
            val pulse = 0.8f + 0.2f * sin(timeNow * 6f)
            Neon.ring(c, p.x, p.y, s * 1.9f, fade(Palette.LIME, 0.55f * pulse), 1.6f, 0.9f)
        }
        if (od) {
            val r = s * (2.2f + 0.35f * sin(timeNow * 12f))
            Neon.ring(c, p.x, p.y, r, fade(Palette.AMBER, 0.5f), 2.2f, 1f)
            Neon.ring(c, p.x, p.y, r * 1.35f, fade(Palette.ROSE, 0.25f), 1.4f, 0.8f)
        }
    }

    /** P1 / P2 marker above a ship, only drawn in co-op. */
    fun pilotTag(c: Canvas, p: Player, index: Int, timeNow: Float) {
        val col = if (index == 0) Palette.CYAN else Palette.LIME
        val bob = sin(timeNow * 3f + index) * 1.5f
        Neon.label(
            c, if (index == 0) "P1" else "P2", p.x, p.y - p.bodyR - 12f + bob, 11f,
            fade(col, 0.85f), Paint.Align.CENTER, 0.5f, 0.16f, Neon.FONT_BODY
        )
    }

    fun enemy(c: Canvas, e: Enemy, timeNow: Float) {
        val flash = e.hitFlash
        val col = if (flash > 0f) mixColor(e.color, Palette.WHITE, clamp(flash * 3f, 0f, 0.85f)) else e.color
        val s = e.r

        // exhaust, in world space so it trails behind the hull rather than
        // rotating with it
        val sp = len(e.vx, e.vy)
        if (sp > 30f && e.kind != EK.MINE && e.kind != EK.PYLON && e.kind != EK.BOSS) {
            val ux = e.vx / sp
            val uy = e.vy / sp
            val puff = (0.55f + 0.45f * sin(timeNow * 26f + e.seedPhase())) * clamp(sp / 220f, 0.35f, 1.4f)
            Neon.softDisc(c, e.x - ux * s * 1.0f, e.y - uy * s * 1.0f, s * 0.62f * puff, fade(col, 0.20f))
            Neon.softDisc(c, e.x - ux * s * 1.7f, e.y - uy * s * 1.7f, s * 0.34f * puff, fade(lighten(col, 0.5f), 0.16f))
        }

        c.save()
        c.translate(e.x, e.y)
        c.rotate(e.angle)
        c.scale(s, s)
        val shape = when (e.kind) {
            EK.DRIFTER -> Shapes.drifter
            EK.WEAVER -> Shapes.weaver
            EK.CHARGER -> Shapes.charger
            EK.TURRET -> Shapes.turret
            EK.LANCER -> Shapes.lancer
            EK.ORBITER -> Shapes.orbiter
            EK.SPLITTER -> Shapes.splitter
            EK.MINELAYER -> Shapes.minelayer
            EK.SWARMER -> Shapes.swarmer
            EK.MINE -> Shapes.mine
            EK.SHIELDER -> Shapes.shielder
            EK.WISP -> Shapes.wisp
            EK.CARRIER -> Shapes.carrier
            EK.PYLON -> Shapes.pylon
            EK.STALKER -> Shapes.stalker
            EK.HOWLER -> Shapes.howler
            EK.SEEDER -> Shapes.seeder
            EK.MENDER -> Shapes.mender
            EK.POD -> Shapes.pod
            else -> Shapes.boss
        }
        Neon.fillPath(c, shape, fade(col, 0.24f))
        Neon.path(c, shape, col, 2.1f / s, 1f, 0.85f)
        // a rim light along the leading edge, which is what stops the hulls
        // reading as flat cut-outs against the backdrop
        c.save()
        c.translate(0f, 0.07f)
        c.scale(0.93f, 0.93f)
        Neon.path(c, shape, fade(lighten(col, 0.55f), 0.5f), 1.1f / s, 0f, 0.55f)
        c.restore()
        detail(c, e, col, s, timeNow)
        if (e.kind == EK.TURRET) {
            Neon.ring(c, 0f, 0f, 0.42f, fade(lighten(col, 0.4f), 0.9f), 1.4f / s, 0.8f)
        }
        if (e.kind == EK.BOSS) {
            c.save()
            c.rotate(timeNow * 55f)
            Neon.path(c, Shapes.bossCore, fade(Palette.WHITE, 0.9f), 2.2f / s, 1.2f, 1f)
            c.restore()
        }
        c.restore()

        if (e.elite) {
            val a2 = 0.35f + 0.2f * sin(timeNow * 4f)
            Neon.ring(c, e.x, e.y, s * 1.5f, fade(Palette.WHITE, a2), 1.4f, 0.8f)
            // three ticks turning round the halo, so an elite is unmistakable
            for (i in 0 until 3) {
                val ang = timeNow * 1.6f + i * TAU / 3f
                val r0 = s * 1.5f
                Neon.line(
                    c, e.x + cos(ang) * r0, e.y + sin(ang) * r0,
                    e.x + cos(ang) * (r0 + 5f), e.y + sin(ang) * (r0 + 5f),
                    fade(Palette.WHITE, a2 + 0.2f), 2f, 0.7f
                )
            }
        }
        if (e.kind == EK.CHARGER && e.state == 1) {
            // telegraph: locked-on glow before the dive
            Neon.ring(c, e.x, e.y, s * (1.7f + 0.3f * sin(timeNow * 20f)), fade(Palette.RED, 0.5f), 1.6f, 0.9f)
        }
        if (e.kind == EK.MINE) {
            val pulse = 0.45f + 0.55f * sin(timeNow * 7f + e.seedPhase())
            Neon.ring(c, e.x, e.y, s * (1.4f + 0.25f * pulse), fade(Palette.RED, 0.4f * pulse), 1.4f, 0.8f)
        }
    }

    /**
     * Panel lines, intakes and a lit cockpit, drawn in the hull's own unit space
     * so one routine covers every silhouette. Widths are divided by the scale
     * the caller already applied. As an enemy takes damage the same routine
     * cracks it open, so a nearly-dead hull looks nearly dead.
     */
    private fun detail(c: Canvas, e: Enemy, col: Int, s: Float, timeNow: Float) {
        val line = 1.0f / s
        val hot = lighten(col, 0.5f)
        val dim = fade(col, 0.55f)
        when (e.kind) {
            EK.DRIFTER -> {
                Neon.hairline(c, -0.42f, -0.30f, 0.42f, -0.30f, dim, line)
                Neon.hairline(c, -0.24f, 0.40f, -0.55f, -0.16f, dim, line)
                Neon.hairline(c, 0.24f, 0.40f, 0.55f, -0.16f, dim, line)
            }
            EK.WEAVER -> {
                Neon.hairline(c, 0f, 0.72f, 0f, -0.70f, dim, line)
                Neon.hairline(c, -0.70f, -0.15f, -0.20f, 0.18f, dim, line)
                Neon.hairline(c, 0.70f, -0.15f, 0.20f, 0.18f, dim, line)
            }
            EK.CHARGER -> {
                Neon.hairline(c, 0f, 1.05f, 0f, -0.55f, fade(hot, 0.7f), line * 1.3f)
                Neon.hairline(c, -0.34f, -0.10f, -0.14f, 0.40f, dim, line)
                Neon.hairline(c, 0.34f, -0.10f, 0.14f, 0.40f, dim, line)
            }
            EK.TURRET -> {
                for (i in 0 until 3) {
                    val a = i * TAU / 3f + timeNow * 0.6f
                    Neon.hairline(c, cos(a) * 0.44f, sin(a) * 0.44f, cos(a) * 0.92f, sin(a) * 0.92f, dim, line * 1.4f)
                }
            }
            EK.LANCER -> {
                Neon.hairline(c, 0f, 1.05f, 0f, -0.20f, fade(Palette.RED, 0.8f), line * 1.6f)
                Neon.hairline(c, -0.55f, -0.05f, -0.30f, 0.28f, dim, line)
                Neon.hairline(c, 0.55f, -0.05f, 0.30f, 0.28f, dim, line)
            }
            EK.ORBITER -> {
                Neon.ring(c, 0f, 0f, 0.40f, dim, line * 1.2f, 0.4f)
                Neon.hairline(c, -0.55f, 0.20f, 0.55f, 0.20f, dim, line)
            }
            EK.SPLITTER -> {
                // the seam it will break along
                Neon.hairline(c, 0f, 0.90f, 0f, -0.48f, fade(hot, 0.75f), line * 1.5f)
                Neon.hairline(c, -0.72f, 0f, 0.72f, 0f, dim, line)
            }
            EK.MINELAYER -> {
                Neon.hairline(c, -0.6f, -0.42f, 0.6f, -0.42f, dim, line)
                Neon.hairline(c, -0.28f, -0.42f, -0.28f, -0.66f, fade(Palette.RED, 0.6f), line * 1.3f)
                Neon.hairline(c, 0.28f, -0.42f, 0.28f, -0.66f, fade(Palette.RED, 0.6f), line * 1.3f)
            }
            EK.SWARMER -> Neon.hairline(c, 0f, 1.0f, 0f, -0.42f, dim, line)
            EK.SHIELDER -> {
                Neon.hairline(c, -0.45f, 0.20f, 0.45f, 0.20f, dim, line * 1.3f)
                Neon.hairline(c, -0.45f, -0.10f, 0.45f, -0.10f, dim, line)
            }
            EK.WISP -> {
                Neon.hairline(c, -0.36f, 0.10f, 0f, 0.46f, dim, line)
                Neon.hairline(c, 0.36f, 0.10f, 0f, 0.46f, dim, line)
            }
            EK.CARRIER -> {
                Neon.hairline(c, -0.7f, -0.30f, 0.7f, -0.30f, dim, line * 1.3f)
                Neon.hairline(c, -0.35f, 0.55f, -0.35f, -0.30f, dim, line)
                Neon.hairline(c, 0.35f, 0.55f, 0.35f, -0.30f, dim, line)
            }
            EK.PYLON -> {
                Neon.ring(c, 0f, 0f, 0.52f, dim, line * 1.2f, 0.4f)
                Neon.ring(c, 0f, 0f, 0.30f, fade(hot, 0.6f), line, 0.4f)
            }
            EK.STALKER -> {
                Neon.hairline(c, 0f, 1.15f, 0f, -0.70f, fade(hot, 0.8f), line * 1.4f)
                Neon.hairline(c, -0.52f, -0.40f, -0.22f, 0.16f, dim, line)
                Neon.hairline(c, 0.52f, -0.40f, 0.22f, 0.16f, dim, line)
            }
            EK.HOWLER -> {
                // concentric mouth rings, the shape it fires
                Neon.ring(c, 0f, 0.20f, 0.34f, dim, line * 1.2f, 0.4f)
                Neon.ring(c, 0f, 0.20f, 0.60f, fade(dim, 0.6f), line, 0.3f)
                Neon.hairline(c, -0.80f, 0.55f, 0.80f, 0.55f, dim, line)
            }
            EK.SEEDER -> {
                Neon.hairline(c, -0.55f, -0.35f, 0.55f, -0.35f, dim, line * 1.3f)
                Neon.hairline(c, 0f, -0.35f, 0f, -0.72f, fade(Palette.LIME, 0.7f), line * 1.4f)
                Neon.ring(c, 0f, 0.25f, 0.34f, dim, line, 0.35f)
            }
            EK.MENDER -> {
                Neon.hairline(c, -0.62f, 0f, 0.62f, 0f, fade(Palette.LIME, 0.8f), line * 1.5f)
                Neon.hairline(c, 0f, -0.62f, 0f, 0.62f, fade(Palette.LIME, 0.8f), line * 1.5f)
            }
            EK.POD -> Neon.ring(c, 0f, 0f, 0.42f, fade(hot, 0.7f), line * 1.2f, 0.4f)
            EK.BOSS -> {
                Neon.hairline(c, -1.0f, 0f, 1.0f, 0f, dim, line * 1.6f)
                Neon.hairline(c, -0.42f, 0.72f, -0.42f, -0.70f, dim, line * 1.2f)
                Neon.hairline(c, 0.42f, 0.72f, 0.42f, -0.70f, dim, line * 1.2f)
            }
            else -> {}
        }

        // a lit cockpit on anything that reads as a craft
        if (e.kind != EK.MINE && e.kind != EK.PYLON && e.kind != EK.TURRET && e.kind != EK.POD) {
            val glow = 0.6f + 0.4f * sin(timeNow * 4f + e.seedPhase())
            Neon.orb(c, 0f, 0.34f, 0.13f, fade(lighten(col, 0.75f), 0.85f * glow), 0.8f)
        }

        // battle damage: cracks that widen as the hull gives out
        val frac = if (e.maxHp > 0f) clamp(e.hp / e.maxHp, 0f, 1f) else 1f
        if (frac < 0.62f && e.kind != EK.MINE) {
            val hurt = 1f - frac / 0.62f
            val n = 1 + (hurt * 2.4f).toInt()
            for (i in 0 until n) {
                val a = e.seedPhase() * 3.1f + i * 2.4f
                val r0 = 0.18f + 0.1f * i
                val r1 = 0.55f + 0.30f * hurt
                Neon.hairline(
                    c, cos(a) * r0, sin(a) * r0, cos(a + 0.5f) * r1, sin(a + 0.5f) * r1,
                    fade(Palette.RED, 0.35f + 0.45f * hurt), line * 1.4f
                )
            }
            if (frac < 0.3f) {
                val flick = 0.4f + 0.6f * sin(timeNow * 17f + e.seedPhase() * 5f)
                Neon.orb(c, cos(e.seedPhase()) * 0.35f, sin(e.seedPhase()) * 0.35f, 0.10f,
                    fade(0xFFFFA040.toInt(), 0.8f * flick), 1f)
            }
        }
    }

    /** The plate a shielder holds towards you - shots from below just bounce. */
    fun shielderPlate(c: Canvas, e: Enemy) {
        if (e.kind != EK.SHIELDER) return
        val r = e.r * 1.55f
        val steps = 12
        val half = EnemyAI.SHIELD_ARC
        var px = e.x + cos(e.aux - half) * r
        var py = e.y + sin(e.aux - half) * r
        for (i in 1..steps) {
            val a = e.aux - half + 2f * half * (i / steps.toFloat())
            val nx = e.x + cos(a) * r
            val ny = e.y + sin(a) * r
            Neon.line(c, px, py, nx, ny, fade(Palette.SKY, 0.9f), 3.2f, 1f)
            px = nx; py = ny
        }
    }

    /** The lethal line strung between a live pylon pair. */
    fun pylonTether(c: Canvas, a: Enemy, b: Enemy, live: Boolean, warm: Float) {
        val col = if (live) Palette.RED else Palette.AMBER
        val alpha = if (live) 1f else 0.25f + 0.5f * warm
        Neon.line(c, a.x, a.y, b.x, b.y, fade(col, alpha), if (live) 3.4f else 1.4f, 1.2f)
        if (live) Neon.line(c, a.x, a.y, b.x, b.y, fade(Palette.WHITE, 0.7f), 1.2f, 0.6f)
    }

    /**
     * The beam a mender holds on whatever it is repairing. Without it the
     * player has no way to see why something has stopped dying.
     */
    fun menderBeam(c: Canvas, a: Enemy, b: Enemy, timeNow: Float) {
        val pulse = 0.55f + 0.45f * sin(timeNow * 9f + a.seedPhase())
        Neon.line(c, a.x, a.y, b.x, b.y, fade(Palette.LIME, 0.30f * pulse), 4.5f, 0.8f)
        Neon.line(c, a.x, a.y, b.x, b.y, fade(Palette.WHITE, 0.55f * pulse), 1.4f, 0.5f)
        // beads running along the beam towards the patient
        val d = len(b.x - a.x, b.y - a.y)
        if (d < 1f) return
        val n = (d / 26f).toInt().coerceIn(1, 8)
        for (i in 0 until n) {
            val t = ((timeNow * 0.9f + i.toFloat() / n) % 1f)
            Neon.orb(c, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, 2.4f, fade(Palette.LIME, 0.8f), 0.9f)
        }
        Neon.ring(c, b.x, b.y, b.r * 1.35f, fade(Palette.LIME, 0.35f * pulse), 1.6f, 0.7f)
    }

    /** A howler winding up: the ring it is about to throw, drawn as a warning. */
    fun howlerTelegraph(c: Canvas, e: Enemy, timeNow: Float) {
        if (e.kind != EK.HOWLER || e.telegraph <= 0f) return
        val t = clamp(e.telegraph, 0f, 1f)
        val r = e.r * (1.6f + 5.5f * t)
        Neon.ring(c, e.x, e.y, r, fade(Palette.AMBER, 0.10f + 0.30f * t), 1.6f + 2f * t, 0.8f)
        Neon.ring(c, e.x, e.y, r * 0.6f, fade(Palette.AMBER, 0.16f * t), 1.2f, 0.5f)
    }

    /** A pod about to bloom. The last half-second is the one that matters. */
    fun podTelegraph(c: Canvas, e: Enemy, timeNow: Float) {
        if (e.kind != EK.POD || e.telegraph <= 0f) return
        val t = clamp(e.telegraph, 0f, 1f)
        val flick = 0.5f + 0.5f * sin(timeNow * (12f + 20f * t))
        Neon.ring(c, e.x, e.y, e.r * (1.4f + 1.8f * t), fade(Palette.LIME, 0.35f * t * flick), 1.8f, 0.8f)
    }

    /** The wind-up beam a lancer shows before it fires. */
    fun lancerTelegraph(c: Canvas, e: Enemy, screenH: Float) {
        if (e.telegraph <= 0f) return
        val t = clamp(e.telegraph, 0f, 1f)
        val w = 2f + 10f * t
        Neon.fillRect(c, e.x - w, e.y, e.x + w, screenH, fade(Palette.RED, 0.10f + 0.22f * t))
        Neon.line(c, e.x, e.y, e.x, screenH, fade(Palette.RED, 0.5f + 0.4f * t), 1.4f, 0.7f)
    }

    fun bullet(c: Canvas, b: Bullet) {
        // a short wake along the flight path: it reads as speed and makes a
        // dense screen easier to parse than a field of identical dots
        val n0 = len(b.vx, b.vy)
        if (n0 > 1f && b.style != 1) {
            val ux = b.vx / n0
            val uy = b.vy / n0
            Neon.softDisc(c, b.x - ux * b.r * 1.9f, b.y - uy * b.r * 1.9f, b.r * 0.9f, fade(b.color, 0.18f))
        }
        when (b.style) {
            1 -> {
                val n = len(b.vx, b.vy)
                val ux = if (n > 0f) b.vx / n else 0f
                val uy = if (n > 0f) b.vy / n else -1f
                Neon.line(c, b.x - ux * b.r * 2.6f, b.y - uy * b.r * 2.6f, b.x + ux * b.r * 1.2f, b.y + uy * b.r * 1.2f, b.color, b.r * 0.95f, 0.9f)
            }
            2 -> {
                Neon.orb(c, b.x, b.y, b.r, b.color, 1.2f)
                Neon.ring(c, b.x, b.y, b.r * 1.5f, fade(lighten(b.color, 0.3f), 0.6f), 1.2f, 0.6f)
            }
            3 -> {
                val n = len(b.vx, b.vy)
                val ux = if (n > 0f) b.vx / n else 0f
                val uy = if (n > 0f) b.vy / n else -1f
                Neon.line(c, b.x - ux * b.r * 3.4f, b.y - uy * b.r * 3.4f, b.x, b.y, fade(b.color, 0.5f), b.r * 0.7f, 0.7f)
                Neon.orb(c, b.x, b.y, b.r, b.color, 1.1f)
                if (b.splash > 0f) Neon.ring(c, b.x, b.y, b.r * 1.9f, fade(Palette.WHITE, 0.55f), 1.3f, 0.6f)
            }
            else -> {
                Neon.orb(c, b.x, b.y, b.r, b.color, 1f)
                Neon.softDisc(c, b.x, b.y, b.r * 0.42f, fade(Palette.WHITE, 0.55f))
            }
        }
    }

    fun powerUp(c: Canvas, u: PowerUp) {
        val color = when (u.kind) {
            PK.WEAPON -> Palette.CYAN
            PK.SHIELD -> Palette.LIME
            PK.LIFE -> Palette.ROSE
            else -> Palette.AMBER
        }
        val blink = u.life < 3f && ((u.life * 8f).toInt() % 2 == 0)
        val a = if (blink) 0.35f else 1f
        val pulse = 1f + 0.12f * sin(u.t * 7f)
        if (u.kind == PK.GEM) {
            c.save()
            c.translate(u.x, u.y)
            c.rotate(u.t * 130f)
            c.scale(u.r * 0.55f * pulse, u.r * 0.55f * pulse)
            Neon.fillPath(c, Shapes.diamond, fade(color, 0.35f * a))
            Neon.path(c, Shapes.diamond, fade(color, a), 1.8f / (u.r * 0.55f), 1f, 0.9f)
            c.restore()
            return
        }
        c.save()
        c.translate(u.x, u.y)
        c.rotate(sin(u.t * 2.2f) * 14f)
        c.scale(u.r * pulse, u.r * pulse)
        Neon.fillPath(c, Shapes.diamond, fade(color, 0.20f * a))
        Neon.path(c, Shapes.diamond, fade(color, a), 1.7f / u.r, 1f, 0.8f)
        c.restore()
        val glyph = when (u.kind) {
            PK.WEAPON -> "W"
            PK.SHIELD -> "S"
            else -> "+"
        }
        Neon.label(c, glyph, u.x, u.y + u.r * 0.38f, u.r * 1.05f, fade(color, a), Paint.Align.CENTER, 0.6f, 0f, Neon.FONT_TITLE)
    }

    fun aimAngle(fromX: Float, fromY: Float, toX: Float, toY: Float): Float =
        atan2(toY - fromY, toX - fromX)
}

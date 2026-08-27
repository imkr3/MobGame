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
}

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
    val hitR = 5.5f                   // small hitbox: grazing is the point
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
        val baseColor = when {
            od -> Palette.AMBER
            p.shield > 0 -> Palette.LIME
            else -> Palette.CYAN
        }
        val a = if (blink) 0.42f else 1f
        val s = p.bodyR

        // engine plume
        val plume = (0.55f + p.thrust * 0.75f) * (0.85f + 0.15f * sin(timeNow * 40f))
        Neon.orb(c, p.x, p.y + s * 0.85f, s * 0.30f * plume, fade(if (od) Palette.ROSE else Palette.MAGENTA, 0.75f * a), 1.1f)
        Neon.orb(c, p.x, p.y + s * 1.25f * plume, s * 0.16f * plume, fade(Palette.VIOLET, 0.5f * a), 0.9f)

        c.save()
        c.translate(p.x, p.y)
        c.rotate(p.bank * 16f)
        c.scale(s, s)
        Neon.fillPath(c, Shapes.player, fade(baseColor, 0.16f * a))
        Neon.path(c, Shapes.player, fade(baseColor, a), 1.7f / s, 1f, 0.9f)
        Neon.path(c, Shapes.playerWing, fade(lighten(baseColor, 0.3f), 0.8f * a), 1.1f / s, 0.7f, 0.6f)
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

    fun enemy(c: Canvas, e: Enemy, timeNow: Float) {
        val flash = e.hitFlash
        val col = if (flash > 0f) mixColor(e.color, Palette.WHITE, clamp(flash * 3f, 0f, 0.85f)) else e.color
        val s = e.r
        c.save()
        c.translate(e.x, e.y)
        c.rotate(e.angle)
        c.scale(s, s)
        val shape = when (e.kind) {
            EK.DRIFTER -> Shapes.drifter
            EK.WEAVER -> Shapes.weaver
            EK.CHARGER -> Shapes.charger
            EK.TURRET -> Shapes.turret
            else -> Shapes.boss
        }
        Neon.fillPath(c, shape, fade(col, 0.24f))
        Neon.path(c, shape, col, 2.1f / s, 1f, 0.85f)
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

        if (e.kind == EK.CHARGER && e.state == 1) {
            // telegraph: locked-on glow before the dive
            Neon.ring(c, e.x, e.y, s * (1.7f + 0.3f * sin(timeNow * 20f)), fade(Palette.RED, 0.5f), 1.6f, 0.9f)
        }
    }

    fun bullet(c: Canvas, b: Bullet) {
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
            else -> Neon.orb(c, b.x, b.y, b.r, b.color, 1f)
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

package com.neonvoid.game

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Boss behaviour. All five share an entry, three hp-gated phases and a
 * transition beat; everything after that is per-archetype.
 */
object BossAI {

    /** Late bosses cycle their patterns faster. */
    private fun tempo(w: World): Float = clamp(1f - w.wave * 0.017f, 0.45f, 1f)

    /** From the second sector loop onwards, every boss brings friends. */
    private fun escortCheck(w: World, e: Enemy, dt: Float) {
        if (w.wave < 5) return
        e.aux2 -= dt
        if (e.aux2 > 0f) return
        e.aux2 = clamp(8f - w.wave * 0.14f, 3.5f, 8f)
        val kind = when (w.themeIndex(w.wave)) {
            0 -> EK.DRIFTER
            1 -> EK.CHARGER
            2 -> EK.ORBITER
            3 -> EK.SPLITTER
            else -> EK.LANCER
        }
        val n = 1 + (w.wave / 9).coerceAtMost(3)
        for (i in 0 until n) w.spawnMinion(kind, rnd(60f, w.w - 60f), -30f)
        w.fx.shockwave(e.x, e.y, e.r * 2f, e.color, 0.4f, 2.5f)
    }

    fun update(w: World, e: Enemy, dt: Float) {
        w.setBossHp(clamp(e.hp / e.maxHp, 0f, 1f))
        val ratio = w.bossHpRatio
        val wantPhase = when {
            ratio > 0.66f -> 0
            ratio > 0.33f -> 1
            else -> 2
        }
        if (wantPhase != e.phase && e.state != 0) {
            e.phase = wantPhase
            e.state = 3
            e.stateT = 1.1f
            // The boss stops firing for the transition, so the screen clears on
            // its own - wiping live bullets robbed the moment of all its weight.
            w.fx.shockwave(e.x, e.y, w.w * 1.1f, Palette.RED, 0.7f, 5f)
            w.fx.flash(Palette.RED, 0.35f)
            w.fx.shake(0.45f)
            w.fx.burst(e.x, e.y, 40, Palette.RED, 320f, 3f, 0.7f, true)
            w.haptic().medium()
            w.setBanner("PHASE ${e.phase + 1}", "", 1.1f)
            return
        }

        when (e.state) {
            0 -> {
                e.y += e.vy * dt
                if (e.y >= e.holdY) { e.y = e.holdY; e.state = 1; e.patternT = 1.2f }
            }
            3 -> {
                e.stateT -= dt
                e.x = lerp(e.x, w.w * 0.5f, clamp(dt * 2.2f, 0f, 1f))
                if (chance(0.6f)) {
                    w.fx.burst(e.x + rnd(-e.r, e.r), e.y + rnd(-e.r * 0.6f, e.r * 0.6f), 2, Palette.AMBER, 120f, 2.4f, 0.5f)
                }
                if (e.stateT <= 0f) { e.state = 1; e.patternT = 0.6f }
            }
            else -> {
                escortCheck(w, e, dt)
                val pace = 1f / tempo(w)
                e.patternT -= dt * pace
                e.spiral += dt * pace
                when (e.bossType) {
                    BT.GUARDIAN -> guardian(w, e, dt)
                    BT.WARDEN -> warden(w, e, dt)
                    BT.HIVE -> hive(w, e, dt)
                    BT.FORGE -> forge(w, e, dt)
                    else -> nullifier(w, e, dt)
                }
            }
        }
    }

    /** FORGE spins armour plates that eat shots from the covered arcs. */
    fun blocksHit(e: Enemy, bx: Float, by: Float): Boolean {
        if (e.bossType != BT.FORGE || e.phase >= 2) return false
        val plates = 3
        val a = atan2(by - e.y, bx - e.x) - e.aux
        val slice = TAU / plates
        var m = a % slice
        if (m < 0f) m += slice
        return m < slice * 0.5f
    }

    private fun hover(w: World, e: Enemy, speed: Float) {
        e.x = w.w * 0.5f + sin(e.t * e.freq * speed) * e.amp
        e.y = e.holdY + sin(e.t * 0.9f) * 14f
        e.angle = -sin(e.t * e.freq * speed) * 7f
    }

    private fun pods(e: Enemy, fn: (Float, Float) -> Unit) {
        val a = e.angle * DEG
        val dx = cos(a) * e.r * 1.25f
        val dy = sin(a) * e.r * 1.25f
        fn(e.x - dx, e.y + 6f - dy)
        fn(e.x + dx, e.y + 6f + dy)
    }

    private fun aimAt(w: World, e: Enemy): Float = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y)

    // ------------------------------------------------------------- GUARDIAN

    private fun guardian(w: World, e: Enemy, dt: Float) {
        hover(w, e, 1f)
        when (e.phase) {
            0 -> {
                if (e.patternT <= 0f) {
                    pods(e) { px, py ->
                        val a = Draw.aimAngle(px, py, w.player.x, w.player.y)
                        for (i in -1..1) {
                            w.hostileShot(px, py, a + i * 11f * DEG, w.enemyBulletSpeed() * 0.95f, 5.5f, Palette.ROSE, 0)
                        }
                    }
                    w.fx.shake(0.08f)
                    e.patternT = 1.35f
                }
                if (e.spiral > 0.22f) {
                    e.spiral = 0f
                    val sweep = sin(e.t * 1.1f) * 55f * DEG
                    pods(e) { px, py ->
                        w.hostileShot(px, py, TAU * 0.25f + sweep, w.enemyBulletSpeed() * 0.8f, 4.6f, Palette.MAGENTA, 0)
                    }
                }
            }
            1 -> {
                if (e.patternT <= 0f) {
                    val n = 20
                    val off = rnd(TAU)
                    for (i in 0 until n) {
                        w.hostileShot(e.x, e.y, off + i * TAU / n, w.enemyBulletSpeed() * 0.72f, 5f, Palette.MAGENTA, 0)
                    }
                    w.fx.shockwave(e.x, e.y, e.r * 3f, Palette.MAGENTA, 0.4f, 3f)
                    w.fx.shake(0.18f)
                    e.patternT = 2.0f
                }
                if (e.spiral > 0.8f) {
                    e.spiral = 0f
                    val a = aimAt(w, e)
                    for (i in -2..2) {
                        w.hostileShot(e.x, e.y + e.r * 0.5f, a + i * 9f * DEG, w.enemyBulletSpeed() * 1.05f, 5.5f, Palette.ROSE, 0)
                    }
                }
            }
            else -> {
                if (e.spiral > 0.08f) {
                    e.spiral = 0f
                    e.stateT += 15f * DEG
                    for (i in 0 until 2) {
                        w.hostileShot(e.x, e.y, e.stateT + i * TAU / 2f, w.enemyBulletSpeed() * 0.66f, 4.8f, Palette.VIOLET, 0)
                    }
                }
                if (e.patternT <= 0f) {
                    val a = aimAt(w, e)
                    w.hostileShot(e.x, e.y, a, w.enemyBulletSpeed() * 1.15f, 9f, Palette.RED, 2)
                    for (i in -1..1) {
                        if (i != 0) w.hostileShot(e.x, e.y, a + i * 15f * DEG, w.enemyBulletSpeed() * 0.9f, 5.5f, Palette.ROSE, 0)
                    }
                    w.fx.shake(0.12f)
                    e.patternT = 1.5f
                }
            }
        }
    }

    // --------------------------------------------------------------- WARDEN

    /** A brawler: dashes across the arena trailing fire, and calls in chargers. */
    private fun warden(w: World, e: Enemy, dt: Float) {
        // state 1 = reposition, state 4 = dashing
        if (e.state == 4) {
            e.x += e.vx * dt
            e.stateT -= dt
            if (chance(0.8f)) {
                w.hostileShot(e.x, e.y + e.r * 0.5f, TAU * 0.25f + rnd(-0.3f, 0.3f), w.enemyBulletSpeed() * 0.65f, 5f, Palette.RED, 0)
            }
            w.fx.cone(e.x, e.y, 2, if (e.vx > 0) TAU * 0.5f else 0f, 0.5f, Palette.RED, 180f, 2.4f, 0.3f)
            if (e.x < e.r + 10f || e.x > w.w - e.r - 10f || e.stateT <= 0f) {
                e.state = 1
                e.patternT = 0.9f
                w.fx.shake(0.25f)
            }
            return
        }

        e.y = lerp(e.y, e.holdY, clamp(dt * 1.6f, 0f, 1f))
        e.x += sin(e.t * 0.8f) * 26f * dt
        e.angle = sin(e.t * 0.8f) * 5f

        if (e.patternT <= 0f) {
            when (e.phase) {
                0 -> {
                    // cross volley
                    val a = aimAt(w, e)
                    for (i in 0 until 4) {
                        w.hostileShot(e.x, e.y, a + i * TAU / 4f, w.enemyBulletSpeed() * 0.9f, 6f, Palette.RED, 0)
                        w.hostileShot(e.x, e.y, a + i * TAU / 4f + TAU / 8f, w.enemyBulletSpeed() * 0.7f, 5f, Palette.ROSE, 0)
                    }
                    e.patternT = 1.5f
                }
                1 -> {
                    // dash
                    val dir = if (w.player.x > e.x) 1f else -1f
                    e.vx = dir * 560f
                    e.state = 4
                    e.stateT = 2.2f
                    w.setBanner("", "", 0f)
                    w.fx.flash(Palette.RED, 0.18f)
                    w.haptic().medium()
                }
                else -> {
                    // summon wing then dash
                    for (i in 0 until 2) {
                        w.spawnMinion(EK.CHARGER, rnd(60f, w.w - 60f), -30f)
                    }
                    val dir = if (w.player.x > e.x) 1f else -1f
                    e.vx = dir * 640f
                    e.state = 4
                    e.stateT = 2.0f
                    w.fx.flash(Palette.RED, 0.2f)
                }
            }
        }
        if (e.spiral > 0.5f) {
            e.spiral = 0f
            val a = aimAt(w, e)
            for (i in -1..1) {
                w.hostileShot(e.x, e.y, a + i * 12f * DEG, w.enemyBulletSpeed(), 5.5f, Palette.ROSE, 0)
            }
        }
    }

    // ----------------------------------------------------------------- HIVE

    /** Keeps the screen full of swarmers while pulsing rings outward. */
    private fun hive(w: World, e: Enemy, dt: Float) {
        hover(w, e, 0.7f)
        e.angle += 22f * dt

        if (e.patternT <= 0f) {
            // cap the brood: an unbounded swarm just soaks shots and stalls the fight
            val live = w.countActive(EK.SWARMER)
            val brood = (2 + e.phase).coerceAtMost((8 - live).coerceAtLeast(0))
            for (i in 0 until brood) {
                val a = rnd(TAU)
                val m = w.spawnMinion(EK.SWARMER, e.x + cos(a) * e.r, e.y + sin(a) * e.r)
                if (m != null) {
                    m.state = 0
                    m.stateT = 0.35f
                }
            }
            w.fx.shockwave(e.x, e.y, e.r * 2f, Palette.LIME, 0.4f, 2.5f)
            e.patternT = when (e.phase) { 0 -> 3.2f; 1 -> 2.6f; else -> 2.0f }
        }

        val ringEvery = when (e.phase) { 0 -> 2.4f; 1 -> 1.9f; else -> 1.5f }
        if (e.spiral > ringEvery) {
            e.spiral = 0f
            val n = 14 + e.phase * 4
            val off = rnd(TAU)
            for (i in 0 until n) {
                w.hostileShot(e.x, e.y, off + i * TAU / n, w.enemyBulletSpeed() * 0.62f, 5f, Palette.LIME, 0)
            }
            w.fx.shake(0.14f)
        }
        if (e.phase >= 1 && chance(0.02f)) {
            val a = aimAt(w, e)
            w.hostileShot(e.x, e.y, a, w.enemyBulletSpeed() * 1.1f, 7f, Palette.AMBER, 2)
        }
    }

    // ---------------------------------------------------------------- FORGE

    /** Armour plates block two thirds of its arc; sweeping beams punish standing still. */
    private fun forge(w: World, e: Enemy, dt: Float) {
        e.aux += dt * (0.9f + e.phase * 0.35f)
        hover(w, e, 0.55f)

        if (e.patternT <= 0f) {
            when (e.phase) {
                0 -> {
                    // slow sweeping rake
                    val base = TAU * 0.25f - 40f * DEG
                    for (i in 0 until 9) {
                        w.hostileShot(e.x, e.y, base + i * 10f * DEG, w.enemyBulletSpeed() * 0.7f, 5.5f, Palette.AMBER, 0)
                    }
                    e.patternT = 1.9f
                }
                1 -> {
                    // wall with a gap the player has to find
                    val gap = rnd(1f)
                    val cols = 11
                    for (i in 0 until cols) {
                        val f = i / (cols - 1f)
                        if (abs(f - gap) < 0.14f) continue
                        w.hostileShot(30f + f * (w.w - 60f), e.y, TAU * 0.25f, w.enemyBulletSpeed() * 0.62f, 6f, Palette.AMBER, 0)
                    }
                    w.fx.shake(0.2f)
                    e.patternT = 2.3f
                }
                else -> {
                    // plates drop: heavy aimed shells plus a double wall
                    val gap = rnd(1f)
                    val cols = 13
                    for (i in 0 until cols) {
                        val f = i / (cols - 1f)
                        if (abs(f - gap) < 0.12f) continue
                        w.hostileShot(30f + f * (w.w - 60f), e.y, TAU * 0.25f, w.enemyBulletSpeed() * 0.72f, 6f, Palette.RED, 0)
                    }
                    val a = aimAt(w, e)
                    w.hostileShot(e.x, e.y, a, w.enemyBulletSpeed() * 1.2f, 10f, Palette.WHITE, 2)
                    w.fx.shake(0.3f)
                    e.patternT = 1.7f
                }
            }
        }
        if (e.spiral > 0.9f) {
            e.spiral = 0f
            val a = aimAt(w, e)
            w.hostileShot(e.x, e.y, a, w.enemyBulletSpeed() * 0.95f, 6f, Palette.LIME, 0)
        }
    }

    /** Draws FORGE's rotating armour so the safe arc is readable. */
    fun drawArmour(c: android.graphics.Canvas, e: Enemy) {
        if (e.bossType != BT.FORGE || e.phase >= 2) return
        val plates = 3
        val slice = TAU / plates
        for (i in 0 until plates) {
            val a0 = e.aux + i * slice
            val steps = 7
            var px = e.x + cos(a0) * e.r * 1.5f
            var py = e.y + sin(a0) * e.r * 1.5f
            for (s in 1..steps) {
                val a = a0 + slice * 0.5f * (s / steps.toFloat())
                val nx = e.x + cos(a) * e.r * 1.5f
                val ny = e.y + sin(a) * e.r * 1.5f
                Neon.line(c, px, py, nx, ny, fade(Palette.AMBER, 0.85f), 3.4f, 0.9f)
                px = nx; py = ny
            }
        }
    }

    // ----------------------------------------------------------- NULLIFIER

    /** Blinks around the arena and answers with mirrored spirals. */
    private fun nullifier(w: World, e: Enemy, dt: Float) {
        e.y = lerp(e.y, e.holdY, clamp(dt * 2f, 0f, 1f))
        e.angle = sin(e.t * 2f) * 10f

        if (e.patternT <= 0f) {
            // blink
            w.fx.burst(e.x, e.y, 30, Palette.WHITE, 300f, 3f, 0.5f, true)
            w.fx.shockwave(e.x, e.y, e.r * 2.5f, Palette.WHITE, 0.4f, 3f)
            e.x = rnd(w.w * 0.2f, w.w * 0.8f)
            e.holdY = rnd(w.h * 0.14f, w.h * 0.3f)
            w.fx.burst(e.x, e.y, 30, Palette.VIOLET, 300f, 3f, 0.5f, true)
            w.haptic().light()

            when (e.phase) {
                0 -> {
                    val a = aimAt(w, e)
                    for (i in -3..3) {
                        w.hostileShot(e.x, e.y, a + i * 8f * DEG, w.enemyBulletSpeed() * 0.9f, 5.5f, Palette.WHITE, 0)
                    }
                    e.patternT = 2.0f
                }
                1 -> {
                    val n = 24
                    val off = rnd(TAU)
                    for (i in 0 until n) {
                        val ang = off + i * TAU / n
                        w.hostileShot(e.x, e.y, ang, w.enemyBulletSpeed() * 0.66f, 5f, Palette.VIOLET, 0)
                        w.hostileShot(w.w - e.x, e.y, -ang, w.enemyBulletSpeed() * 0.66f, 5f, Palette.WHITE, 0)
                    }
                    e.patternT = 2.4f
                }
                else -> {
                    val a = aimAt(w, e)
                    for (i in 0 until 3) {
                        w.hostileShot(e.x, e.y, a + i * TAU / 3f, w.enemyBulletSpeed() * 1.25f, 9f, Palette.RED, 2)
                    }
                    e.patternT = 1.6f
                }
            }
            w.fx.shake(0.22f)
        }

        val spiralEvery = when (e.phase) { 0 -> 0.16f; 1 -> 0.12f; else -> 0.075f }
        if (e.spiral > spiralEvery) {
            e.spiral = 0f
            e.stateT += 18f * DEG
            w.hostileShot(e.x, e.y, e.stateT, w.enemyBulletSpeed() * 0.6f, 4.6f, Palette.VIOLET, 0)
            w.hostileShot(e.x, e.y, -e.stateT + TAU * 0.5f, w.enemyBulletSpeed() * 0.6f, 4.6f, Palette.WHITE, 0)
        }
    }
}

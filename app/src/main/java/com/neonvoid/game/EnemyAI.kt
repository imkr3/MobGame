package com.neonvoid.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Per-kind enemy behaviour. Bosses live in [BossAI]. */
object EnemyAI {

    fun update(w: World, e: Enemy, dt: Float) {
        when (e.kind) {
            EK.DRIFTER -> drifter(w, e, dt)
            EK.WEAVER -> weaver(w, e, dt)
            EK.CHARGER -> charger(w, e, dt)
            EK.TURRET -> turret(w, e, dt)
            EK.LANCER -> lancer(w, e, dt)
            EK.ORBITER -> orbiter(w, e, dt)
            EK.SPLITTER -> splitter(w, e, dt)
            EK.MINELAYER -> minelayer(w, e, dt)
            EK.SWARMER -> swarmer(w, e, dt)
            EK.MINE -> mine(w, e, dt)
            EK.BOSS -> BossAI.update(w, e, dt)
        }
    }

    private fun aimed(w: World, e: Enemy, spreadDeg: Float = 0f, speedMul: Float = 1f, r: Float = 5.5f, style: Int = 0) {
        if (!w.player.alive) return
        val a = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y) + spreadDeg * DEG
        w.hostileShot(e.x, e.y, a, w.enemyBulletSpeed() * speedMul, r, Palette.ROSE, style)
    }

    // ------------------------------------------------------------ originals

    private fun drifter(w: World, e: Enemy, dt: Float) {
        e.y += e.vy * dt
        e.x += sin(e.t * 0.8f) * 22f * dt
        e.fireT -= dt
        if (e.fireT <= 0f && e.y > 40f && e.y < w.h * 0.75f) {
            aimed(w, e)
            e.fireT = e.fireEvery
            w.fx.cone(e.x, e.y + e.r * 0.6f, 3, TAU * 0.25f, 0.4f, Palette.ROSE, 90f, 1.6f, 0.2f)
        }
    }

    private fun weaver(w: World, e: Enemy, dt: Float) {
        e.y += e.vy * dt
        e.x = e.baseX + sin(e.t * e.freq) * e.amp
        e.angle = sin(e.t * e.freq) * 16f
        e.fireT -= dt
        if (e.fireT <= 0f && e.y > 40f && e.y < w.h * 0.75f) {
            aimed(w, e, -9f)
            aimed(w, e, 9f)
            e.fireT = e.fireEvery
        }
    }

    private fun charger(w: World, e: Enemy, dt: Float) {
        when (e.state) {
            0 -> {
                e.y += e.vy * dt
                if (e.y >= e.holdY) { e.state = 1; e.stateT = 0.7f }
            }
            1 -> {
                e.stateT -= dt
                e.y += 8f * dt
                if (e.stateT <= 0f) {
                    val a = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y)
                    val sp = 470f
                    e.vx = cos(a) * sp
                    e.vy = sin(a) * sp
                    e.angle = a / DEG - 90f
                    e.state = 2
                    w.fx.cone(e.x, e.y, 10, a, 0.5f, Palette.RED, 220f, 2.4f, 0.3f)
                    w.haptic().light()
                }
            }
            else -> {
                e.x += e.vx * dt
                e.y += e.vy * dt
                if (chance(0.6f)) {
                    w.fx.cone(e.x, e.y, 1, atan2(-e.vy, -e.vx), 0.3f, Palette.RED, 90f, 2f, 0.3f)
                }
            }
        }
    }

    private fun turret(w: World, e: Enemy, dt: Float) {
        if (e.state == 0) {
            e.y += e.vy * dt
            if (e.y >= e.holdY) { e.state = 1; e.stateT = 16f }
        } else if (e.state == 2) {
            e.y += e.vy * 1.6f * dt
            e.angle += 40f * dt
        } else {
            e.stateT -= dt
            if (e.stateT <= 0f) { e.state = 2; return }
            e.x = e.baseX + sin(e.t * e.freq) * e.amp
            e.y += sin(e.t * 0.7f) * 6f * dt
            e.angle += 26f * dt
            e.fireT -= dt
            if (e.fireT <= 0f) {
                val n = if (e.elite) 12 else 8
                val off = e.t * 0.7f
                for (i in 0 until n) {
                    w.hostileShot(e.x, e.y, off + i * TAU / n, w.enemyBulletSpeed() * 0.75f, 5f, Palette.AMBER, 0)
                }
                w.fx.shockwave(e.x, e.y, e.r * 2.4f, Palette.AMBER, 0.35f, 2f)
                e.fireT = e.fireEvery
            }
        }
    }

    // ----------------------------------------------------------------- new

    /**
     * Holds a lane, winds up with a visible beam, then fires a dense column
     * straight down it. Move out of the lane or eat all of it.
     */
    private fun lancer(w: World, e: Enemy, dt: Float) {
        when (e.state) {
            0 -> {
                e.y += e.vy * dt
                if (e.y >= e.holdY) { e.state = 1; e.fireT = 1.1f }
            }
            else -> {
                e.x += e.vx * dt
                if (e.x < 40f || e.x > w.w - 40f) e.vx = -e.vx
                e.fireT -= dt
                if (e.fireT <= 0.85f) {
                    e.telegraph = clamp(1f - e.fireT / 0.85f, 0f, 1f)
                } else {
                    e.telegraph = 0f
                }
                if (e.fireT <= 0f) {
                    val n = if (e.elite) 7 else 5
                    for (i in 0 until n) {
                        w.hostileShot(
                            e.x, e.y + i * 16f, TAU * 0.25f,
                            w.enemyBulletSpeed() * 1.45f, 6f, Palette.RED, 2
                        )
                    }
                    w.fx.cone(e.x, e.y + e.r, 12, TAU * 0.25f, 0.25f, Palette.RED, 300f, 2.6f, 0.35f)
                    w.fx.shake(0.12f)
                    e.telegraph = 0f
                    e.fireT = e.fireEvery
                }
            }
        }
    }

    /** Circles a fixed point and fires along its tangent. */
    private fun orbiter(w: World, e: Enemy, dt: Float) {
        if (e.state == 0) {
            e.y += e.vy * dt
            if (e.y >= e.holdY) {
                e.state = 1
                e.aux = e.x            // orbit centre
                e.aux2 = e.y
            }
            return
        }
        val spin = e.freq * 1.6f
        val ang = e.seed + e.t * spin
        e.x = e.aux + cos(ang) * e.amp
        e.y = e.aux2 + sin(ang) * e.amp * 0.6f
        e.angle = ang / DEG + 90f
        e.fireT -= dt
        if (e.fireT <= 0f) {
            val tangent = ang + TAU * 0.25f
            w.hostileShot(e.x, e.y, tangent, w.enemyBulletSpeed() * 0.85f, 5f, Palette.VIOLET, 0)
            w.hostileShot(e.x, e.y, tangent + TAU * 0.5f, w.enemyBulletSpeed() * 0.85f, 5f, Palette.VIOLET, 0)
            e.fireT = e.fireEvery
        }
    }

    /** Tough and slow; breaks into smaller copies when destroyed (handled on kill). */
    private fun splitter(w: World, e: Enemy, dt: Float) {
        e.y += e.vy * dt
        e.x += e.vx * dt
        if (e.x < 30f || e.x > w.w - 30f) e.vx = -e.vx
        e.angle += 18f * dt * (1f + e.tier)
        e.fireT -= dt
        if (e.fireT <= 0f && e.y > 40f && e.y < w.h * 0.8f) {
            val n = 3 + e.tier
            val base = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y)
            for (i in 0 until n) {
                w.hostileShot(e.x, e.y, base + (i - (n - 1) * 0.5f) * 14f * DEG, w.enemyBulletSpeed() * 0.8f, 5f, Palette.LIME, 0)
            }
            e.fireT = e.fireEvery
        }
    }

    /** Crosses the top of the screen seeding mines behind it. */
    private fun minelayer(w: World, e: Enemy, dt: Float) {
        if (e.state == 0) {
            e.y += e.vy * dt
            if (e.y >= e.holdY) { e.state = 1; e.fireT = 0.6f }
            return
        }
        e.x += e.vx * dt
        e.y += sin(e.t * 0.8f) * 10f * dt
        if (e.x < 34f || e.x > w.w - 34f) e.vx = -e.vx
        e.fireT -= dt
        if (e.fireT <= 0f) {
            w.spawnMinion(EK.MINE, e.x, e.y + e.r * 0.8f)
            w.fx.burst(e.x, e.y + e.r * 0.8f, 6, Palette.AMBER, 110f, 1.8f, 0.35f)
            e.fireT = e.fireEvery
        }
    }

    /** Fast, fragile, and aimed at wherever you were a moment ago. */
    private fun swarmer(w: World, e: Enemy, dt: Float) {
        if (e.state == 0) {
            // brief drop-in, then lock a heading towards the player
            e.y += e.vy * dt
            e.stateT -= dt
            if (e.stateT <= 0f) {
                val a = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y)
                e.vx = cos(a) * e.vy * 1.5f
                e.vy = sin(a) * e.vy * 1.5f
                e.state = 1
            }
            return
        }
        // gentle steering so a standing target cannot be ignored
        if (w.player.alive) {
            val want = Draw.aimAngle(e.x, e.y, w.player.x, w.player.y)
            val cur = atan2(e.vy, e.vx)
            var diff = want - cur
            while (diff > TAU / 2f) diff -= TAU
            while (diff < -TAU / 2f) diff += TAU
            val step = clamp(diff, -1.1f * dt, 1.1f * dt)
            val sp = len(e.vx, e.vy)
            e.vx = cos(cur + step) * sp
            e.vy = sin(cur + step) * sp
        }
        e.x += e.vx * dt
        e.y += e.vy * dt
        e.angle = atan2(e.vy, e.vx) / DEG - 90f
        if (chance(0.35f)) w.fx.cone(e.x, e.y, 1, atan2(-e.vy, -e.vx), 0.4f, e.color, 70f, 1.5f, 0.22f)
    }

    /** Sits in the lane until shot, approached, or its fuse runs out. */
    private fun mine(w: World, e: Enemy, dt: Float) {
        e.y += e.vy * dt
        e.angle += 30f * dt
        e.stateT -= dt
        val close = w.player.alive && len(w.player.x - e.x, w.player.y - e.y) < 78f
        if (e.stateT <= 0f || close) {
            w.hit(e, 9999f, e.x, e.y, false)     // detonation is handled on death
        }
    }

    /** Radial burst a mine leaves behind. Called from the kill path. */
    fun detonateMine(w: World, e: Enemy) {
        val n = 10
        val off = rnd(TAU)
        for (i in 0 until n) {
            w.hostileShot(e.x, e.y, off + i * TAU / n, w.enemyBulletSpeed() * 0.7f, 5f, Palette.RED, 0)
        }
        w.fx.shockwave(e.x, e.y, 90f, Palette.RED, 0.4f, 3f)
        w.fx.burst(e.x, e.y, 18, Palette.RED, 280f, 2.6f, 0.5f, true)
        w.fx.shake(0.15f)
    }
}

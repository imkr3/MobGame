package com.neonvoid.game

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class Beam {
    var active = false
    var x = 0f
    var halfW = 6f
    var life = 0f
    var maxLife = 0.35f
    var dps = 30f
    var color = Palette.VIOLET
    var drift = 0f
}

class Nova {
    var active = false
    var x = 0f; var y = 0f
    var r = 0f; var maxR = 200f
    var life = 0f; var maxLife = 0.55f
    var damage = 12f
    var color = Palette.MAGENTA
    var banksBullets = false
    val hit = BooleanArray(World.ENEMY_CAP)
}

class Bolt {
    var active = false
    var n = 0
    val px = FloatArray(10)
    val py = FloatArray(10)
    var life = 0f
    var maxLife = 0.22f
    var color = Palette.SKY
    var width = 2.4f
}

/**
 * Every augment-driven weapon system: beams, homing swarms, orbital nodes,
 * chain lightning and shockwave novas. Driven entirely by the run's [Loadout].
 */
class Arsenal(private val fx: Fx) {

    private val beams = Array(8) { Beam() }
    private val novas = Array(6) { Nova() }
    private val bolts = Array(10) { Bolt() }
    private val nodeCd = FloatArray(6)

    private var lanceT = 0f
    private var swarmT = 0f
    private var arcT = 0f
    private var pulseT = 0f
    private var sentryT = 0f
    var orbitAngle = 0f
        private set

    fun reset() {
        for (b in beams) b.active = false
        for (n in novas) n.active = false
        for (b in bolts) b.active = false
        nodeCd.fill(0f)
        lanceT = 2.5f; swarmT = 1.2f; arcT = 1.4f; pulseT = 4f; sentryT = 0.9f
        orbitAngle = 0f
    }

    // ------------------------------------------------------------ spawning

    private fun obtainBeam(): Beam? = beams.firstOrNull { !it.active }
    private fun obtainNova(): Nova? = novas.firstOrNull { !it.active }
    private fun obtainBolt(): Bolt? = bolts.firstOrNull { !it.active }

    private fun beam(x: Float, halfW: Float, dps: Float, life: Float, color: Int, drift: Float) {
        val b = obtainBeam() ?: return
        b.active = true
        b.x = x; b.halfW = halfW; b.dps = dps
        b.maxLife = life; b.life = life
        b.color = color; b.drift = drift
    }

    // -------------------------------------------------------------- update

    fun update(dt: Float, world: World) {
        val lo = world.loadout
        val p = world.player
        orbitAngle += dt * 2.1f
        if (orbitAngle > TAU) orbitAngle -= TAU

        updateBeams(dt, world)
        updateNovas(dt, world)
        for (b in bolts) {
            if (!b.active) continue
            b.life -= dt
            if (b.life <= 0f) b.active = false
        }

        if (!p.alive) return

        if (lo.has(Aug.LANCE)) tickLance(dt, world)
        if (lo.has(Aug.SWARM)) tickSwarm(dt, world)
        if (lo.has(Aug.ORBIT)) tickOrbit(dt, world)
        if (lo.has(Aug.ARC)) tickArc(dt, world)
        if (lo.has(Aug.PULSE)) tickPulse(dt, world)
    }

    private fun updateBeams(dt: Float, world: World) {
        for (b in beams) {
            if (!b.active) continue
            b.life -= dt
            if (b.life <= 0f) { b.active = false; continue }
            b.x += b.drift * dt
            for (e in world.enemies) {
                if (!e.active) continue
                if (abs(e.x - b.x) <= b.halfW + e.r && e.y < world.player.y) {
                    world.hit(e, b.dps * dt, e.x, e.y + e.r * 0.4f, chance(0.25f))
                }
            }
        }
    }

    private fun updateNovas(dt: Float, world: World) {
        for (n in novas) {
            if (!n.active) continue
            n.life -= dt
            if (n.life <= 0f) { n.active = false; continue }
            val t = 1f - n.life / n.maxLife
            n.r = n.maxR * (1f - (1f - t) * (1f - t))
            for (i in world.enemies.indices) {
                val e = world.enemies[i]
                if (!e.active || n.hit[i]) continue
                val d = len(e.x - n.x, e.y - n.y)
                if (d <= n.r + e.r) {
                    n.hit[i] = true
                    world.hit(e, n.damage, e.x, e.y, true)
                }
            }
            if (n.banksBullets) world.bankBulletsWithin(n.x, n.y, n.r)
        }
    }

    // --------------------------------------------------------------- LANCE

    private fun tickLance(dt: Float, world: World) {
        val lo = world.loadout
        val l = lo.lvl[Aug.LANCE]
        val b = lo.branch[Aug.LANCE]
        lanceT -= dt
        if (lanceT > 0f) return
        val p = world.player
        when (b) {
            Aug.A -> { // PRISM: three fanning beams
                lanceT = clamp(2.3f - 0.15f * (l - 3), 1.6f, 2.3f)
                val dps = 34f + 12f * l
                beam(p.x, 7f, dps, 0.4f, Palette.CYAN, 0f)
                beam(p.x - 26f, 6f, dps * 0.8f, 0.4f, Palette.VIOLET, -150f)
                beam(p.x + 26f, 6f, dps * 0.8f, 0.4f, Palette.VIOLET, 150f)
                fx.shake(0.1f)
            }
            Aug.B -> { // SIEGE: one ruinous column
                lanceT = clamp(3.4f - 0.2f * (l - 3), 2.6f, 3.4f)
                beam(p.x, 24f, 150f + 34f * (l - 3), 0.55f, Palette.WHITE, 0f)
                fx.shake(0.42f)
                fx.flash(Palette.VIOLET, 0.22f)
                world.haptic().medium()
            }
            else -> {
                lanceT = 3.4f - 0.5f * l
                beam(p.x, 5f + 2.5f * l, 24f + 14f * l, 0.35f, Palette.VIOLET, 0f)
                fx.shake(0.12f)
            }
        }
        fx.cone(p.x, p.y - 18f, 8, -TAU * 0.25f, 0.5f, Palette.WHITE, 240f, 2.2f, 0.25f)
    }

    // --------------------------------------------------------------- SWARM

    private fun tickSwarm(dt: Float, world: World) {
        val lo = world.loadout
        val l = lo.lvl[Aug.SWARM]
        val b = lo.branch[Aug.SWARM]
        swarmT -= dt
        if (swarmT > 0f) return
        val p = world.player
        val bonus = lo.damageBonus()
        when (b) {
            Aug.A -> { // HORNETS
                swarmT = clamp(1.25f - 0.08f * (l - 3), 0.9f, 1.25f)
                for (i in 0 until 5) {
                    val m = world.missile(p.x, p.y - 8f, rnd(-260f, 260f), rnd(-380f, -180f), 3.4f, 4 + bonus, Palette.LIME)
                    m.turn = 7.5f
                }
            }
            Aug.B -> { // WARHEAD
                swarmT = clamp(2.1f - 0.12f * (l - 3), 1.6f, 2.1f)
                val m = world.missile(p.x, p.y - 12f, rnd(-40f, 40f), -230f, 7f, 16 + bonus * 2, Palette.AMBER)
                m.turn = 2.6f
                m.splash = 92f
            }
            else -> {
                swarmT = 2.0f - 0.2f * l
                for (i in 0 until l) {
                    val spread = (i - (l - 1) * 0.5f) * 130f
                    val m = world.missile(p.x, p.y - 8f, spread, -260f, 4.2f, 5 + l + bonus, Palette.LIME)
                    m.turn = 4.2f
                }
            }
        }
        fx.cone(p.x, p.y, 5, TAU * 0.25f, 1.2f, Palette.LIME, 150f, 2f, 0.3f)
    }

    // --------------------------------------------------------------- ORBIT

    fun nodeCount(lo: Loadout): Int {
        if (!lo.has(Aug.ORBIT)) return 0
        val l = lo.lvl[Aug.ORBIT]
        return when (lo.branch[Aug.ORBIT]) {
            Aug.A -> 4 + (l - 3).coerceAtLeast(0)
            Aug.B -> 3 + (l - 3).coerceAtLeast(0) / 2
            else -> l
        }.coerceAtMost(6)
    }

    fun nodeOrbit(lo: Loadout): Float {
        val l = lo.lvl[Aug.ORBIT]
        return when (lo.branch[Aug.ORBIT]) {
            Aug.A -> 62f + 4f * l
            Aug.B -> 58f + 4f * l
            else -> 46f + 6f * l
        }
    }

    fun nodeRadius(lo: Loadout): Float = when (lo.branch[Aug.ORBIT]) {
        Aug.A -> 13f
        Aug.B -> 9f
        else -> 8f
    }

    private fun tickOrbit(dt: Float, world: World) {
        val lo = world.loadout
        val p = world.player
        val count = nodeCount(lo)
        val orbit = nodeOrbit(lo)
        val nodeR = nodeRadius(lo)
        val aegis = lo.branch[Aug.ORBIT] == Aug.A
        val sentry = lo.branch[Aug.ORBIT] == Aug.B
        val dmg = when {
            aegis -> 7f + 2f * lo.lvl[Aug.ORBIT]
            else -> 6f + 2.6f * lo.lvl[Aug.ORBIT]
        }

        if (sentry) {
            sentryT -= dt
            if (sentryT <= 0f) {
                sentryT = 0.7f
                for (i in 0 until count) {
                    val a = orbitAngle + i * TAU / count
                    val nx = p.x + cos(a) * orbit
                    val ny = p.y + sin(a) * orbit
                    world.allyBullet(nx, ny, 0f, -780f, 3.6f, 4 + lo.damageBonus(), Palette.AMBER, 1)
                }
            }
        }

        for (i in 0 until count) {
            if (nodeCd[i] > 0f) nodeCd[i] -= dt
            val a = orbitAngle + i * TAU / count
            val nx = p.x + cos(a) * orbit
            val ny = p.y + sin(a) * orbit

            if (nodeCd[i] <= 0f) {
                for (e in world.enemies) {
                    if (!e.active) continue
                    val rr = e.r + nodeR
                    if (len(e.x - nx, e.y - ny) <= rr) {
                        world.hit(e, dmg, nx, ny, true)
                        nodeCd[i] = 0.32f
                        fx.burst(nx, ny, 5, Palette.AMBER, 180f, 2f, 0.3f)
                        break
                    }
                }
            }
            if (aegis) world.eatBulletsWithin(nx, ny, nodeR + 5f)
        }
    }

    // ----------------------------------------------------------------- ARC

    private fun tickArc(dt: Float, world: World) {
        val lo = world.loadout
        val l = lo.lvl[Aug.ARC]
        val b = lo.branch[Aug.ARC]
        arcT -= dt
        if (arcT > 0f) return
        val p = world.player

        if (b == Aug.B) { // RAILGUN: a line straight up the lane
            arcT = clamp(2.1f - 0.15f * (l - 3), 1.5f, 2.1f)
            val dmg = 42f + 14f * (l - 3)
            val bolt = obtainBolt() ?: return
            bolt.active = true
            bolt.color = Palette.WHITE
            bolt.width = 4.5f
            bolt.maxLife = 0.3f; bolt.life = 0.3f
            bolt.n = 5
            for (i in 0 until 5) {
                bolt.px[i] = p.x + if (i == 0 || i == 4) 0f else rnd(-14f, 14f)
                bolt.py[i] = p.y - 18f - i * (p.y / 4f)
            }
            for (e in world.enemies) {
                if (!e.active || e.y > p.y) continue
                if (abs(e.x - p.x) <= e.r + 22f) world.hit(e, dmg, e.x, e.y, true)
            }
            fx.shake(0.35f)
            fx.flash(Palette.SKY, 0.2f)
            world.haptic().medium()
            return
        }

        val targets: Int
        val dmg: Float
        if (b == Aug.A) { // TEMPEST
            arcT = clamp(0.95f - 0.05f * (l - 3), 0.7f, 0.95f)
            targets = 7
            dmg = 6f + 1.6f * (l - 3)
        } else {
            arcT = 1.8f - 0.2f * l
            targets = l + 1
            dmg = 5f + 2f * l
        }

        val bolt = obtainBolt() ?: return
        bolt.active = true
        bolt.color = if (b == Aug.A) Palette.VIOLET else Palette.SKY
        bolt.width = 2.6f
        bolt.maxLife = 0.24f; bolt.life = 0.24f
        bolt.n = 0
        bolt.px[0] = p.x; bolt.py[0] = p.y - 12f; bolt.n = 1

        var fromX = p.x
        var fromY = p.y - 12f
        val used = BooleanArray(World.ENEMY_CAP)
        var chained = 0
        while (chained < targets && bolt.n < bolt.px.size) {
            var best = -1
            var bestD = if (chained == 0) 420f else 300f
            for (i in world.enemies.indices) {
                val e = world.enemies[i]
                if (!e.active || used[i]) continue
                val d = len(e.x - fromX, e.y - fromY)
                if (d < bestD) { bestD = d; best = i }
            }
            if (best < 0) break
            used[best] = true
            val e = world.enemies[best]
            bolt.px[bolt.n] = e.x
            bolt.py[bolt.n] = e.y
            bolt.n++
            world.hit(e, dmg, e.x, e.y, true)
            fx.burst(e.x, e.y, 5, bolt.color, 180f, 2f, 0.3f)
            fromX = e.x; fromY = e.y
            chained++
        }
        if (chained == 0) {
            bolt.active = false
            arcT = 0.35f     // nothing in range: retry shortly
        } else {
            world.haptic().light()
        }
    }

    // --------------------------------------------------------------- PULSE

    private fun tickPulse(dt: Float, world: World) {
        val lo = world.loadout
        val l = lo.lvl[Aug.PULSE]
        val b = lo.branch[Aug.PULSE]
        val p = world.player

        if (b == Aug.B) { // REPULSOR: a constant field, not a timed blast
            val r = 104f + 9f * l
            world.pushBulletsWithin(p.x, p.y, r, 340f)
            pulseT -= dt
            if (pulseT <= 0f) {
                pulseT = 0.25f
                for (e in world.enemies) {
                    if (!e.active) continue
                    if (len(e.x - p.x, e.y - p.y) <= r + e.r) {
                        world.hit(e, 2.2f + 0.7f * l, e.x, e.y, chance(0.4f))
                    }
                }
            }
            return
        }

        pulseT -= dt
        if (pulseT > 0f) return
        val n = obtainNova() ?: return
        n.active = true
        n.x = p.x; n.y = p.y
        n.r = 0f
        n.hit.fill(false)
        if (b == Aug.A) { // NOVA
            pulseT = clamp(4.3f - 0.3f * (l - 3), 3.2f, 4.3f)
            n.maxR = 330f + 22f * (l - 3)
            n.damage = 26f + 7f * (l - 3)
            n.maxLife = 0.75f
            n.color = Palette.AMBER
            n.banksBullets = true
            fx.shake(0.4f)
            fx.flash(Palette.AMBER, 0.25f)
            world.haptic().medium()
        } else {
            pulseT = 8f - l
            n.maxR = 110f + 46f * l
            n.damage = 6f + 4f * l
            n.maxLife = 0.6f
            n.color = Palette.MAGENTA
            n.banksBullets = false
            fx.shake(0.2f)
        }
        n.life = n.maxLife
    }

    // ----------------------------------------------------------------- draw

    fun draw(c: Canvas, world: World) {
        val lo = world.loadout
        val p = world.player

        for (b in beams) {
            if (!b.active) continue
            val t = b.life / b.maxLife
            val a = clamp(t * 1.4f, 0f, 1f)
            val hw = b.halfW * (0.55f + 0.45f * t)
            Neon.fillRect(c, b.x - hw * 2.6f, 0f, b.x + hw * 2.6f, p.y, fade(b.color, 0.07f * a))
            Neon.fillRect(c, b.x - hw, 0f, b.x + hw, p.y, fade(b.color, 0.34f * a))
            Neon.fillRect(c, b.x - hw * 0.32f, 0f, b.x + hw * 0.32f, p.y, fade(Palette.WHITE, 0.85f * a))
            Neon.orb(c, b.x, p.y - 14f, hw * 1.2f, fade(Palette.WHITE, a), 1.3f)
        }

        for (n in novas) {
            if (!n.active) continue
            val t = n.life / n.maxLife
            Neon.ring(c, n.x, n.y, n.r, fade(n.color, t * 0.95f), 4.5f * t + 1f, 1.2f)
            Neon.ring(c, n.x, n.y, n.r * 0.82f, fade(Palette.WHITE, t * 0.45f), 2f * t + 0.5f, 0.8f)
        }

        if (lo.branch[Aug.PULSE] == Aug.B && p.alive) {
            val r = 104f + 9f * lo.lvl[Aug.PULSE]
            val pulse = 0.55f + 0.45f * sin(orbitAngle * 1.7f)
            Neon.ring(c, p.x, p.y, r, fade(Palette.MAGENTA, 0.30f * pulse), 2.2f, 0.9f)
            Neon.ring(c, p.x, p.y, r * 0.93f, fade(Palette.VIOLET, 0.14f), 1.2f, 0.5f)
        }

        for (b in bolts) {
            if (!b.active || b.n < 2) continue
            val t = b.life / b.maxLife
            for (i in 0 until b.n - 1) {
                Neon.line(c, b.px[i], b.py[i], b.px[i + 1], b.py[i + 1], fade(b.color, t), b.width * t + 0.8f, 1.2f)
            }
        }

        if (lo.has(Aug.ORBIT) && p.alive) {
            val count = nodeCount(lo)
            val orbit = nodeOrbit(lo)
            val nodeR = nodeRadius(lo)
            val aegis = lo.branch[Aug.ORBIT] == Aug.A
            val col = if (aegis) Palette.LIME else Palette.AMBER
            for (i in 0 until count) {
                val a = orbitAngle + i * TAU / count
                val nx = p.x + cos(a) * orbit
                val ny = p.y + sin(a) * orbit
                if (aegis) {
                    Neon.ring(c, nx, ny, nodeR, fade(col, 0.9f), 2.2f, 1.1f)
                    Neon.orb(c, nx, ny, nodeR * 0.32f, fade(Palette.WHITE, 0.8f), 0.9f)
                } else {
                    Neon.orb(c, nx, ny, nodeR, col, 1.1f)
                }
            }
        }
    }

    /** Highest-priority aim assist for the swarm: nearest live enemy to a point. */
    fun nearestEnemy(world: World, x: Float, y: Float): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in world.enemies.indices) {
            val e = world.enemies[i]
            if (!e.active) continue
            val d = len(e.x - x, e.y - y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}

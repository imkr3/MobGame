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

/** A singularity that drags everything nearby into itself. */
class Vortex {
    var active = false
    var x = 0f; var y = 0f
    var r = 90f
    var life = 0f; var maxLife = 3f
    var dps = 14f
    var pull = 220f
    var banks = false
    var implodes = false
    var damage = 0f
}

/** A heavy shot that ricochets around the arena instead of leaving it. */
class Orb {
    var active = false
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var r = 12f
    var life = 0f; var maxLife = 6f
    var damage = 6f
    var hitCd = 0f
    var explodes = false
    var blast = 0f
    var color = Palette.LIME
    var spin = 0f
}

/** A deployed gun that holds position and fires up the screen. */
class Turret {
    var active = false
    var x = 0f; var y = 0f
    var life = 0f; var maxLife = 6f
    var fireT = 0f
    var every = 0.5f
    var damage = 4
    var mortar = false
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

    /** The pilot this arsenal belongs to; set by [PlayerSlot]. */
    lateinit var slot: PlayerSlot

    private val beams = Array(8) { Beam() }
    private val novas = Array(6) { Nova() }
    private val bolts = Array(10) { Bolt() }
    private val nodeCd = FloatArray(6)
    private val vortices = Array(4) { Vortex() }
    private val turrets = Array(4) { Turret() }
    private val orbs = Array(6) { Orb() }

    private var lanceT = 0f
    private var swarmT = 0f
    private var arcT = 0f
    private var pulseT = 0f
    private var sentryT = 0f
    private var flakT = 0f
    private var wingT = 0f
    private var vortexT = 0f
    private var sentinelT = 0f
    private var ricochetT = 0f
    /** Radius of the live CHRONO field, or zero when it is not running. */
    var chronoR = 0f
        private set
    private var tetherIdx = -1
    private var tetherTick = 0f
    var orbitAngle = 0f
        private set

    fun reset() {
        for (b in beams) b.active = false
        for (n in novas) n.active = false
        for (b in bolts) b.active = false
        nodeCd.fill(0f)
        lanceT = 2.5f; swarmT = 1.2f; arcT = 1.4f; pulseT = 4f; sentryT = 0.9f
        flakT = 1.6f; wingT = 0.5f; tetherIdx = -1; tetherTick = 0f
        vortexT = 3f; sentinelT = 2.5f; ricochetT = 1.4f
        chronoR = 0f
        for (v in vortices) v.active = false
        for (t in turrets) t.active = false
        for (o in orbs) o.active = false
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
        val lo = slot.loadout
        val p = slot.player
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
        if (lo.has(Aug.FLAK)) tickFlak(dt, world)
        if (lo.has(Aug.TETHER)) tickTether(dt, world) else tetherIdx = -1
        if (lo.has(Aug.WING)) tickWing(dt, world)
        if (lo.has(Aug.VORTEX)) tickVortex(dt, world)
        if (lo.has(Aug.SENTINEL)) tickSentinel(dt, world)
        if (lo.has(Aug.CHRONO)) tickChrono(dt, world) else chronoR = 0f
        if (lo.has(Aug.RICOCHET)) tickRicochet(dt, world)
        updateVortices(dt, world)
        updateTurrets(dt, world)
        updateOrbs(dt, world)
    }

    // -------------------------------------------------------------- CHRONO

    /**
     * A bubble of dilated time around the ship. Rather than tracking a velocity
     * scale per bullet, each frame drags whatever is inside back along a slice
     * of the step it just took - the same result, and it survives bullets being
     * recycled underneath us.
     */
    private fun tickChrono(dt: Float, world: World) {
        val lo = slot.loadout
        val p = slot.player
        val l = lo.lvl[Aug.CHRONO]
        val br = lo.branch[Aug.CHRONO]
        val r = when (br) {
            Aug.A -> 150f + 14f * l
            Aug.B -> 116f + 11f * l
            else -> 86f + 13f * l
        }
        val slow = when (br) {
            Aug.A -> clamp(0.60f + 0.05f * l, 0f, 0.94f)
            Aug.B -> clamp(0.50f + 0.05f * l, 0f, 0.9f)
            else -> clamp(0.28f + 0.07f * l, 0f, 0.85f)
        }
        chronoR = r
        val reflect = 0.85f - 0.06f * l
        val payload = 6 + 4 * l
        for (b in world.bullets) {
            if (!b.active || !b.hostile) continue
            if (len(b.x - p.x, b.y - p.y) > r + b.r) {
                b.dwell = 0f
                continue
            }
            b.x -= b.vx * dt * slow
            b.y -= b.vy * dt * slow
            b.dwell += dt
            when (br) {
                // held long enough, the shot simply gives up and pays out
                Aug.A -> if (b.dwell >= 1.5f) world.bankBullet(b)
                Aug.B -> if (b.dwell >= reflect) world.reflectBullet(b, payload)
                else -> if (b.dwell >= 1.1f) {
                    // the base field cannot kill, so it banks charge instead
                    p.overdrive = clamp(p.overdrive + 0.11f + 0.03f * l, 0f, 1f)
                    b.dwell = 0f
                }
            }
        }
        if (br == Aug.A) {
            // STASIS also jams triggers: nothing caught in the field can shoot
            for (e in world.enemies) {
                if (!e.active) continue
                if (len(e.x - p.x, e.y - p.y) > r + e.r) continue
                if (e.fireT < 0.4f) e.fireT = 0.4f
            }
        }
    }

    // ------------------------------------------------------------ RICOCHET

    private fun tickRicochet(dt: Float, world: World) {
        val lo = slot.loadout
        val p = slot.player
        val l = lo.lvl[Aug.RICOCHET]
        val br = lo.branch[Aug.RICOCHET]
        ricochetT -= dt
        if (ricochetT > 0f) return
        val count = if (br == Aug.A) 3 else 1
        val live = orbs.count { it.active }
        if (live >= (if (br == Aug.A) 5 else 2)) {
            ricochetT = 0.6f
            return
        }
        val bonus = lo.damageBonus() + slot.ship.damageBonus
        for (i in 0 until count) {
            val o = orbs.firstOrNull { !it.active } ?: break
            val speed = when (br) {
                Aug.A -> 560f
                Aug.B -> 330f
                else -> 430f + 14f * l
            }
            val a = -TAU * 0.25f + (i - (count - 1) * 0.5f) * 0.42f + rnd(-0.1f, 0.1f)
            o.active = true
            o.x = p.x; o.y = p.y - 18f
            o.vx = cos(a) * speed
            o.vy = sin(a) * speed
            o.hitCd = 0f
            o.spin = 0f
            o.maxLife = when (br) {
                Aug.A -> 5.5f
                Aug.B -> 8f + 0.5f * (l - 3)
                else -> 5f + 0.5f * l
            }
            o.life = o.maxLife
            o.r = when (br) {
                Aug.A -> 9f
                Aug.B -> 20f
                else -> 12f + l
            }
            o.damage = when (br) {
                Aug.A -> 7f + 2f * l + bonus
                Aug.B -> 16f + 5f * l + bonus
                else -> 6f + 2.5f * l + bonus
            }
            o.explodes = br == Aug.B
            o.blast = if (br == Aug.B) 78f + 6f * l else 0f
            o.color = if (br == Aug.B) Palette.AMBER else Palette.LIME
        }
        ricochetT = cd(world, if (br == Aug.A) 3.6f else 4.4f - 0.2f * l)
    }

    private fun updateOrbs(dt: Float, world: World) {
        for (o in orbs) {
            if (!o.active) continue
            o.life -= dt
            if (o.life <= 0f) {
                o.active = false
                fx.burst(o.x, o.y, 10, o.color, 220f, 2.2f, 0.4f)
                continue
            }
            if (o.hitCd > 0f) o.hitCd -= dt
            o.spin += dt * 7f
            o.x += o.vx * dt
            o.y += o.vy * dt
            var bounced = false
            if (o.x < o.r) { o.x = o.r; o.vx = -o.vx; bounced = true }
            if (o.x > world.w - o.r) { o.x = world.w - o.r; o.vx = -o.vx; bounced = true }
            if (o.y < o.r + 40f) { o.y = o.r + 40f; o.vy = -o.vy; bounced = true }
            if (o.y > world.h - o.r) { o.y = world.h - o.r; o.vy = -o.vy; bounced = true }
            if (bounced) {
                fx.burst(o.x, o.y, 6, o.color, 200f, 2f, 0.3f)
                if (o.explodes) {
                    fx.shockwave(o.x, o.y, o.blast, o.color, 0.4f, 3f)
                    fx.shake(0.14f)
                    world.splashDamage(o.x, o.y, o.blast, o.damage * 0.7f)
                }
            }
            if (o.hitCd > 0f) continue
            for (e in world.enemies) {
                if (!e.active) continue
                val rr = e.r + o.r
                if (len(e.x - o.x, e.y - o.y) > rr) continue
                world.hit(e, o.damage, o.x, o.y, true)
                o.hitCd = 0.14f
                // bounce off what it just mauled, so it keeps working the field
                val a = atan2(o.y - e.y, o.x - e.x)
                val sp = len(o.vx, o.vy)
                o.vx = cos(a) * sp
                o.vy = sin(a) * sp
                if (o.explodes) {
                    fx.shockwave(o.x, o.y, o.blast, o.color, 0.4f, 3f)
                    world.splashDamage(o.x, o.y, o.blast, o.damage * 0.7f)
                }
                break
            }
        }
    }

    // -------------------------------------------------------------- VORTEX

    private fun tickVortex(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.VORTEX]
        val br = lo.branch[Aug.VORTEX]
        vortexT -= dt
        if (vortexT > 0f) return
        val p = slot.player
        val v = vortices.firstOrNull { !it.active } ?: return
        v.active = true
        v.x = clamp(p.x + rnd(-60f, 60f), 60f, world.w - 60f)
        v.y = clamp(p.y - 250f, world.h * 0.12f, world.h * 0.7f)
        v.banks = false
        v.implodes = false
        when (br) {
            Aug.A -> {                                   // SINGULARITY
                vortexT = cd(world, clamp(5.4f - 0.2f * (l - 3), 4.2f, 5.4f))
                v.r = 128f + 8f * (l - 3); v.dps = 20f + 5f * (l - 3)
                v.maxLife = 3.6f; v.pull = 300f; v.banks = true
            }
            Aug.B -> {                                   // IMPLOSION
                vortexT = cd(world, clamp(5.0f - 0.2f * (l - 3), 3.8f, 5.0f))
                v.r = 124f; v.dps = 20f
                v.maxLife = 2.2f; v.pull = 380f; v.implodes = true
                v.damage = 125f + 32f * (l - 3)
            }
            else -> {
                vortexT = cd(world, 6f - 0.4f * l)
                v.r = 84f + 15f * l; v.dps = 18f + 8f * l
                v.maxLife = 2.8f + 0.25f * l; v.pull = 220f + 30f * l
            }
        }
        v.life = v.maxLife
        fx.shockwave(v.x, v.y, v.r, Palette.VIOLET, 0.4f, 3f)
        world.sound?.sfx(Sfx.PICKUP)
    }

    private fun updateVortices(dt: Float, world: World) {
        for (v in vortices) {
            if (!v.active) continue
            v.life -= dt
            if (v.life <= 0f) {
                if (v.implodes) {
                    for (e in world.enemies) {
                        if (!e.active) continue
                        if (len(e.x - v.x, e.y - v.y) <= v.r * 1.2f + e.r) world.hit(e, v.damage, e.x, e.y, true)
                    }
                    fx.shockwave(v.x, v.y, v.r * 1.8f, Palette.WHITE, 0.5f, 4f)
                    fx.burst(v.x, v.y, 32, Palette.VIOLET, 380f, 3f, 0.7f, true)
                    fx.shake(0.3f)
                    world.sound?.sfx(Sfx.EXPLODE)
                }
                v.active = false
                continue
            }
            for (e in world.enemies) {
                if (!e.active || e.kind == EK.BOSS) continue
                val d = len(e.x - v.x, e.y - v.y)
                if (d < v.r && d > 0.01f) {
                    val k = (1f - d / v.r) * v.pull * dt
                    e.x += (v.x - e.x) / d * k
                    e.y += (v.y - e.y) / d * k
                    world.hit(e, v.dps * dt, e.x, e.y, chance(0.08f))
                }
            }
            world.pushBulletsWithin(v.x, v.y, v.r, -v.pull * 1.4f)
            if (v.banks) world.bankBulletsWithin(v.x, v.y, v.r * 0.35f)
        }
    }

    // ------------------------------------------------------------ SENTINEL

    private fun tickSentinel(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.SENTINEL]
        val br = lo.branch[Aug.SENTINEL]
        sentinelT -= dt
        if (sentinelT > 0f) return
        val p = slot.player
        if (!p.alive) return
        val count = if (br == Aug.A) 2 else 1
        val bonus = lo.damageBonus() + slot.ship.damageBonus
        for (i in 0 until count) {
            val t = turrets.firstOrNull { !it.active } ?: return
            t.active = true
            t.x = clamp(p.x + (i - (count - 1) * 0.5f) * 54f, 30f, world.w - 30f)
            t.y = p.y + 10f
            t.mortar = br == Aug.B
            t.maxLife = when (br) {
                Aug.A -> 6.5f + 0.4f * (l - 3)
                Aug.B -> 7.5f + 0.4f * (l - 3)
                else -> 5f + 0.6f * l
            }
            t.life = t.maxLife
            t.every = when (br) {
                Aug.A -> 0.28f
                Aug.B -> 1.0f
                else -> clamp(0.5f - 0.05f * l, 0.3f, 0.5f)
            }
            t.damage = when (br) {
                Aug.A -> 5 + l + bonus
                Aug.B -> 7 + l + bonus
                else -> 4 + 2 * l + bonus
            }
            t.fireT = 0.2f
            fx.shockwave(t.x, t.y, 40f, Palette.SKY, 0.35f, 2.5f)
        }
        sentinelT = cd(world, if (br == Aug.A) 6.6f else 6.2f - 0.3f * l)
    }

    private fun updateTurrets(dt: Float, world: World) {
        for (t in turrets) {
            if (!t.active) continue
            t.life -= dt
            if (t.life <= 0f) {
                t.active = false
                fx.burst(t.x, t.y, 10, Palette.SKY, 180f, 2f, 0.4f)
                continue
            }
            t.fireT -= dt
            if (t.fireT <= 0f) {
                t.fireT = t.every
                if (t.mortar) {
                    world.flakShell(t.x, t.y - 8f, rnd(-50f, 50f), -500f, 6f, t.damage, 7, 0.55f, 0f)
                } else {
                    world.allyBullet(t.x, t.y - 8f, 0f, -840f, 3.4f, t.damage, Palette.SKY, 1)
                }
            }
        }
    }

    /** Ability cooldowns, shortened by the COOLANT module. */
    private fun cd(world: World, seconds: Float): Float =
        seconds * slot.loadout.cooldownMul() * world.meta.cooldownMul

    // ---------------------------------------------------------------- FLAK

    private fun tickFlak(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.FLAK]
        val br = lo.branch[Aug.FLAK]
        flakT -= dt
        if (flakT > 0f) return
        val p = slot.player
        val bonus = lo.damageBonus() + slot.ship.damageBonus
        when (br) {
            Aug.A -> { // CLUSTER
                flakT = cd(world, clamp(2.0f - 0.1f * (l - 3), 1.5f, 2.0f))
                for (i in -1..1) {
                    world.flakShell(p.x + i * 13f, p.y - 12f, i * 105f, -560f, 5.5f, 3 + bonus, 7, 0.5f, 0f)
                }
            }
            Aug.B -> { // AIRBURST
                flakT = cd(world, clamp(2.7f - 0.1f * (l - 3), 2.1f, 2.7f))
                world.flakShell(p.x, p.y - 12f, rnd(-30f, 30f), -520f, 9f, 6 + bonus, 18, 0.62f, 115f)
            }
            else -> {
                flakT = cd(world, 2.7f - 0.25f * l)
                world.flakShell(p.x, p.y - 12f, rnd(-40f, 40f), -540f, 7f, 3 + l + bonus, 6 + 2 * l, 0.62f - 0.04f * l, 0f)
            }
        }
        fx.cone(p.x, p.y - 14f, 5, -TAU * 0.25f, 0.35f, Palette.RED, 200f, 2f, 0.22f)
    }

    // -------------------------------------------------------------- TETHER

    private fun tickTether(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.TETHER]
        val br = lo.branch[Aug.TETHER]
        val p = slot.player
        if (!p.alive) { tetherIdx = -1; return }
        val range = 230f + 22f * l

        val cur = tetherIdx
        val keep = cur >= 0 && world.enemies[cur].active &&
            len(world.enemies[cur].x - p.x, world.enemies[cur].y - p.y) <= range * 1.15f
        if (!keep) {
            var best = -1
            var bestD = range
            for (i in world.enemies.indices) {
                val e = world.enemies[i]
                if (!e.active) continue
                val d = len(e.x - p.x, e.y - p.y)
                if (d < bestD) { bestD = d; best = i }
            }
            tetherIdx = best
        }
        val idx = tetherIdx
        if (idx < 0) return
        val e = world.enemies[idx]

        val dps = when (br) {
            Aug.A -> 24f + 9f * l
            Aug.B -> 46f + 15f * l
            else -> 26f + 13f * l
        }
        world.hit(e, dps * dt, e.x, e.y, false)
        tetherTick -= dt
        if (tetherTick <= 0f) {
            tetherTick = 0.1f
            fx.cone(e.x, e.y, 2, Draw.aimAngle(e.x, e.y, p.x, p.y), 0.6f, Palette.ROSE, 130f, 1.6f, 0.22f)
        }
        when (br) {
            Aug.A -> p.overdrive = clamp(p.overdrive + 0.014f * dt * l, 0f, 1f)   // LEECH
            Aug.B -> {                                                            // SIPHON drags it in
                val a = Draw.aimAngle(e.x, e.y, p.x, p.y)
                e.x += cos(a) * 46f * dt
                e.y += sin(a) * 46f * dt
            }
        }
    }

    // ---------------------------------------------------------------- WING

    private fun wingCount(lo: Loadout): Int = if (lo.lvl[Aug.WING] >= 5) 3 else 2

    private fun wingOffset(lo: Loadout, i: Int): Float {
        val spread = 30f + 5f * lo.lvl[Aug.WING]
        val n = wingCount(lo)
        return (i - (n - 1) * 0.5f) * spread * 2f
    }

    private fun tickWing(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.WING]
        val br = lo.branch[Aug.WING]
        val p = slot.player
        if (!p.alive) return
        val n = wingCount(lo)
        val bonus = lo.damageBonus() + slot.ship.damageBonus

        if (br == Aug.A) {
            for (i in 0 until n) {
                world.eatBulletsWithin(p.x + wingOffset(lo, i), p.y + 6f, 11f)
            }
        }
        wingT -= dt
        if (wingT > 0f) return
        wingT = cd(world, if (br == Aug.B) clamp(1.85f - 0.1f * (l - 3), 1.45f, 1.85f) else clamp(0.7f - 0.045f * l, 0.42f, 0.7f))
        for (i in 0 until n) {
            val wx = p.x + wingOffset(lo, i)
            val wy = p.y + 6f
            if (br == Aug.B) {
                val m = world.missile(wx, wy, rnd(-90f, 90f), -300f, 4f, 3 + l + bonus, Palette.WHITE)
                m.turn = 4.5f
            } else {
                world.allyBullet(wx, wy, 0f, -880f, 3.4f, 2 + l + bonus, Palette.WHITE, 1)
            }
        }
    }

    private fun updateBeams(dt: Float, world: World) {
        for (b in beams) {
            if (!b.active) continue
            b.life -= dt
            if (b.life <= 0f) { b.active = false; continue }
            b.x += b.drift * dt
            for (e in world.enemies) {
                if (!e.active) continue
                if (abs(e.x - b.x) <= b.halfW + e.r && e.y < slot.player.y) {
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
        val lo = slot.loadout
        val l = lo.lvl[Aug.LANCE]
        val b = lo.branch[Aug.LANCE]
        lanceT -= dt
        if (lanceT > 0f) return
        val p = slot.player
        when (b) {
            Aug.A -> { // PRISM: three fanning beams
                lanceT = cd(world, clamp(2.3f - 0.15f * (l - 3), 1.6f, 2.3f))
                val dps = 34f + 12f * l
                beam(p.x, 7f, dps, 0.4f, Palette.CYAN, 0f)
                beam(p.x - 26f, 6f, dps * 0.8f, 0.4f, Palette.VIOLET, -150f)
                beam(p.x + 26f, 6f, dps * 0.8f, 0.4f, Palette.VIOLET, 150f)
                fx.shake(0.1f)
            }
            Aug.B -> { // SIEGE: one ruinous column
                lanceT = cd(world, clamp(3.4f - 0.2f * (l - 3), 2.6f, 3.4f))
                beam(p.x, 24f, 150f + 34f * (l - 3), 0.55f, Palette.WHITE, 0f)
                fx.shake(0.42f)
                fx.flash(Palette.VIOLET, 0.22f)
                world.sound?.sfx(Sfx.LASER)
                world.haptic().medium()
            }
            else -> {
                lanceT = cd(world, 3.4f - 0.5f * l)
                beam(p.x, 5f + 2.5f * l, 24f + 14f * l, 0.35f, Palette.VIOLET, 0f)
                world.sound?.sfx(Sfx.LASER)
                fx.shake(0.12f)
            }
        }
        fx.cone(p.x, p.y - 18f, 8, -TAU * 0.25f, 0.5f, Palette.WHITE, 240f, 2.2f, 0.25f)
    }

    // --------------------------------------------------------------- SWARM

    private fun tickSwarm(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.SWARM]
        val b = lo.branch[Aug.SWARM]
        swarmT -= dt
        if (swarmT > 0f) return
        val p = slot.player
        val bonus = lo.damageBonus()
        when (b) {
            Aug.A -> { // HORNETS
                swarmT = cd(world, clamp(1.45f - 0.08f * (l - 3), 1.05f, 1.45f))
                for (i in 0 until 3) {
                    val m = world.missile(p.x, p.y - 8f, rnd(-260f, 260f), rnd(-380f, -180f), 3.4f, 3 + bonus, Palette.LIME)
                    m.turn = 7.5f
                }
            }
            Aug.B -> { // WARHEAD
                swarmT = cd(world, clamp(2.6f - 0.12f * (l - 3), 2.1f, 2.6f))
                val m = world.missile(p.x, p.y - 12f, rnd(-40f, 40f), -230f, 7f, 8 + bonus, Palette.AMBER)
                m.turn = 2.6f
                m.splash = 38f
            }
            else -> {
                swarmT = cd(world, 2.0f - 0.2f * l)
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
        val lo = slot.loadout
        val p = slot.player
        val count = nodeCount(lo)
        val orbit = nodeOrbit(lo)
        val nodeR = nodeRadius(lo)
        val aegis = lo.branch[Aug.ORBIT] == Aug.A
        val sentry = lo.branch[Aug.ORBIT] == Aug.B
        val dmg = when {
            aegis -> 16f + 5f * lo.lvl[Aug.ORBIT]
            else -> 17f + 6f * lo.lvl[Aug.ORBIT]
        }

        if (sentry) {
            sentryT -= dt
            if (sentryT <= 0f) {
                sentryT = cd(world, 0.7f)
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
                        nodeCd[i] = 0.20f
                        fx.burst(nx, ny, 5, Palette.AMBER, 180f, 2f, 0.3f)
                        break
                    }
                }
            }
            if (aegis) world.eatBulletsWithin(nx, ny, nodeR + 1f)
        }
    }

    // ----------------------------------------------------------------- ARC

    private fun tickArc(dt: Float, world: World) {
        val lo = slot.loadout
        val l = lo.lvl[Aug.ARC]
        val b = lo.branch[Aug.ARC]
        arcT -= dt
        if (arcT > 0f) return
        val p = slot.player

        if (b == Aug.B) { // RAILGUN: a line straight up the lane
            arcT = cd(world, clamp(2.1f - 0.15f * (l - 3), 1.5f, 2.1f))
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
            arcT = cd(world, clamp(0.95f - 0.05f * (l - 3), 0.7f, 0.95f))
            targets = 7
            dmg = 6f + 1.6f * (l - 3)
        } else {
            arcT = cd(world, 1.8f - 0.2f * l)
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
        val lo = slot.loadout
        val l = lo.lvl[Aug.PULSE]
        val b = lo.branch[Aug.PULSE]
        val p = slot.player

        if (b == Aug.B) { // REPULSOR: a constant field, not a timed blast
            val r = 104f + 9f * l
            world.pushBulletsWithin(p.x, p.y, r, 250f)
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
            pulseT = cd(world, clamp(4.3f - 0.3f * (l - 3), 3.2f, 4.3f))
            n.maxR = 330f + 22f * (l - 3)
            n.damage = 26f + 7f * (l - 3)
            n.maxLife = 0.75f
            n.color = Palette.AMBER
            n.banksBullets = true
            fx.shake(0.4f)
            fx.flash(Palette.AMBER, 0.25f)
            world.haptic().medium()
        } else {
            pulseT = cd(world, 8f - l)
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
        val lo = slot.loadout
        val p = slot.player

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

        // CHRONO field: a slow, breathing lens with a ticking rim
        if (chronoR > 0f && p.alive) {
            val br = lo.branch[Aug.CHRONO]
            val col = if (br == Aug.B) Palette.SKY else Palette.WHITE
            val breathe = 0.85f + 0.15f * sin(orbitAngle * 0.9f)
            val r = chronoR * breathe
            Neon.softDisc(c, p.x, p.y, r, fade(col, 0.07f))
            Neon.ring(c, p.x, p.y, r, fade(col, 0.42f), 1.8f, 0.8f)
            Neon.ring(c, p.x, p.y, r * 0.72f, fade(col, 0.16f), 1.1f, 0.4f)
            for (i in 0 until 12) {
                val a = orbitAngle * 0.35f + i * TAU / 12f
                val x0 = p.x + cos(a) * r
                val y0 = p.y + sin(a) * r
                Neon.line(c, x0, y0, p.x + cos(a) * (r - 7f), p.y + sin(a) * (r - 7f), fade(col, 0.5f), 1.4f, 0.5f)
            }
        }

        for (o in orbs) {
            if (!o.active) continue
            val t = clamp(o.life / o.maxLife, 0f, 1f)
            val a = clamp(t * 3f, 0f, 1f)
            Neon.softDisc(c, o.x, o.y, o.r * 2.1f, fade(o.color, 0.16f * a))
            Neon.orb(c, o.x, o.y, o.r, fade(o.color, a), 1.4f)
            Neon.ring(c, o.x, o.y, o.r * 1.5f, fade(Palette.WHITE, 0.45f * a), 1.6f, 0.8f)
            for (i in 0 until 3) {
                val ang = o.spin + i * TAU / 3f
                Neon.line(
                    c, o.x + cos(ang) * o.r * 1.1f, o.y + sin(ang) * o.r * 1.1f,
                    o.x + cos(ang) * o.r * 1.9f, o.y + sin(ang) * o.r * 1.9f,
                    fade(o.color, 0.7f * a), 1.6f, 0.7f
                )
            }
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

        // vortices
        for (v in vortices) {
            if (!v.active) continue
            val t = clamp(v.life / v.maxLife, 0f, 1f)
            val col = if (v.implodes) Palette.RED else Palette.VIOLET
            val spin = orbitAngle * 2.4f
            for (i in 0 until 3) {
                val rr = v.r * (0.4f + 0.3f * i) * (0.85f + 0.15f * sin(spin + i))
                Neon.ring(c, v.x, v.y, rr, fade(col, 0.5f * t), 2.2f, 1f)
            }
            Neon.ring(c, v.x, v.y, v.r, fade(col, 0.28f * t), 1.6f, 0.8f)
            Neon.orb(c, v.x, v.y, 7f + 4f * sin(spin), fade(Palette.WHITE, 0.85f * t), 1.3f)
        }

        // sentinel turrets
        for (t in turrets) {
            if (!t.active) continue
            val k = clamp(t.life / t.maxLife, 0f, 1f)
            val col = if (t.mortar) Palette.AMBER else Palette.SKY
            Neon.ring(c, t.x, t.y, 11f, fade(col, 0.9f * k), 2.2f, 1f)
            Neon.line(c, t.x, t.y - 4f, t.x, t.y - 16f, fade(col, 0.9f * k), 2.6f, 0.9f)
            Neon.orb(c, t.x, t.y, 3.6f, fade(Palette.WHITE, 0.8f * k), 0.8f)
        }

        // tether beam
        val ti = tetherIdx
        if (lo.has(Aug.TETHER) && ti >= 0 && world.enemies[ti].active && p.alive) {
            val e = world.enemies[ti]
            val col = if (lo.branch[Aug.TETHER] == Aug.B) Palette.RED else Palette.ROSE
            val n = 6
            var px = p.x
            var py = p.y - 10f
            for (i in 1..n) {
                val f = i / n.toFloat()
                val jitter = if (i == n) 0f else rnd(-7f, 7f)
                val nx = lerp(p.x, e.x, f) + jitter
                val ny = lerp(p.y - 10f, e.y, f) + rnd(-5f, 5f)
                Neon.line(c, px, py, nx, ny, fade(col, 0.85f), 2.4f, 1.1f)
                px = nx; py = ny
            }
            Neon.ring(c, e.x, e.y, e.r * 1.25f, fade(col, 0.6f), 1.8f, 0.9f)
        }

        // wingmen
        if (lo.has(Aug.WING) && p.alive) {
            val escort = lo.branch[Aug.WING] == Aug.A
            val col = if (escort) Palette.LIME else Palette.WHITE
            for (i in 0 until wingCount(lo)) {
                val wx = p.x + wingOffset(lo, i)
                val wy = p.y + 6f
                c.save()
                c.translate(wx, wy)
                c.scale(8f, 8f)
                Neon.fillPath(c, Shapes.player, fade(col, 0.2f))
                Neon.path(c, Shapes.player, fade(col, 0.95f), 1.6f / 8f, 0.9f, 0.8f)
                c.restore()
                if (escort) Neon.ring(c, wx, wy, 11f, fade(Palette.LIME, 0.45f), 1.2f, 0.6f)
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

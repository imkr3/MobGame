package com.neonvoid.game

import android.graphics.Canvas
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private class Spawn(
    var time: Float = 0f,
    var kind: Int = EK.DRIFTER,
    var x: Float = 0f,
    var y: Float = -40f,
    var hpMul: Float = 1f
)

/**
 * The simulation: player, bullets, enemies, pickups, the wave director and scoring.
 * Rendering of these objects lives here too so the draw order stays obvious.
 */
class World(internal val fx: Fx, private val haptics: Haptics) {

    companion object {
        const val ENEMY_CAP = 56
        const val GRAZE_R = 26f
        const val OD_DURATION = 3.2f
        const val COMBO_WINDOW = 3.2f
        const val MAX_WEAPON = 5
    }

    var w = 540f
        private set
    var h = 1000f
        private set

    val player = Player()
    val loadout = Loadout()
    /** The hull chosen in the hangar; set before [reset]. */
    var ship: Ship = ShipDex.byId(ShipDex.STARTER)
    /** Optional sound sink; null in headless tests. */
    var sound: SoundBus? = null
    val arsenal = Arsenal(fx)
    internal val bullets = Array(620) { Bullet() }
    private var bIdx = 0
    internal val enemies = Array(ENEMY_CAP) { Enemy() }
    private val pickups = Array(28) { PowerUp() }

    private val script = ArrayList<Spawn>()
    private var scriptIdx = 0
    private var waveT = 0f

    var score = 0
        private set
    var combo = 0
        private set
    var maxCombo = 0
        private set
    private var comboT = 0f
    var wave = 0
        private set
    var kills = 0
        private set
    private var killsSinceWeapon = 0
    var gameOver = false
        private set

    /** True while the run is waiting for the player to pick an augment. */
    var pendingAugment = false
        private set

    var banner: String = ""
        internal set
    var bannerSub: String = ""
        internal set
    var bannerT = 0f
        internal set

    private var time = 0f
    private var waveClearT = 0f
    private var augmentDelay = 0f
    private var awaitingNextWave = false
    private var boss: Enemy? = null
    var bossHpRatio = 0f
        internal set

    private fun scoreMultiplier(): Float = loadout.scoreMul() * ship.scoreMul

    val multiplier: Float
        get() = clamp(1f + combo * 0.1f, 1f, 9.9f)

    /** 0..1 pacing signal the background uses to speed up over time. */
    val intensity: Float
        get() = clamp((wave - 1) / 14f, 0f, 1f)

    fun resize(width: Float, height: Float) {
        w = width; h = height
        if (player.x == 0f && player.y == 0f) centerPlayer()
        player.x = clamp(player.x, 20f, w - 20f)
        player.y = clamp(player.y, h * 0.25f, h - 60f)
        player.tx = player.x; player.ty = player.y
    }

    private fun centerPlayer() {
        player.x = w * 0.5f
        player.y = h - h * 0.18f
        player.tx = player.x
        player.ty = player.y
    }

    fun reset() {
        for (b in bullets) b.active = false
        for (e in enemies) e.active = false
        for (u in pickups) u.active = false
        script.clear(); scriptIdx = 0
        score = 0; combo = 0; maxCombo = 0; comboT = 0f
        wave = 0; kills = 0; killsSinceWeapon = 4; gameOver = false
        loadout.reset()
        arsenal.reset()
        pendingAugment = false
        boss = null; bossHpRatio = 0f
        awaitingNextWave = true
        waveClearT = 0.9f
        augmentDelay = 0f
        time = 0f
        banner = ""; bannerSub = ""; bannerT = 0f
        player.shipId = ship.id
        player.lives = ship.lives
        player.hitR = ship.hitR
        player.weapon = 1
        player.shield = ship.startShield
        if (ship.signature >= 0) loadout.lvl[ship.signature] = ship.signatureLevel
        player.invuln = 2f
        player.overdrive = 0f
        player.odTime = 0f
        player.alive = true
        player.respawnT = 0f
        player.bank = 0f
        player.fireT = 0f
        centerPlayer()
        fx.reset()
    }

    // ---------------------------------------------------------------- input

    fun moveBy(dx: Float, dy: Float) {
        player.tx = clamp(player.tx + dx, 18f, w - 18f)
        player.ty = clamp(player.ty + dy, h * 0.14f, h - 34f)
    }

    internal fun haptic(): Haptics = haptics

    /** The wave-clear banner would otherwise bleed through the augment screen. */
    internal fun hideBanner() {
        bannerT = 0f
    }

    fun canOverdrive(): Boolean = player.overdrive >= 1f && player.odTime <= 0f && player.alive

    fun triggerOverdrive(): Boolean {
        if (!canOverdrive()) return false
        player.odTime = OD_DURATION
        player.overdrive = 0f
        val gained = clearHostileBullets(true)
        fx.shockwave(player.x, player.y, w * 1.3f, Palette.AMBER, 0.6f, 5f)
        fx.shockwave(player.x, player.y, w * 0.8f, Palette.WHITE, 0.4f, 3f)
        fx.flash(Palette.AMBER, 0.55f)
        fx.shake(0.5f)
        fx.freeze(0.09f)
        fx.burst(player.x, player.y, 46, Palette.AMBER, 460f, 3.4f, 0.7f, true)
        sound?.sfx(Sfx.OVERDRIVE)
        if (gained > 0) fx.popText(player.x, player.y - 70f, "+$gained", Palette.AMBER, 24f, 1.1f)
        haptics.heavy()
        return true
    }

    /** Offers for the between-wave choice. */
    fun rollAugments(count: Int): List<AugCard> = loadout.rollOffers(count)

    fun applyAugment(c: AugCard) {
        val label = loadout.apply(c)
        when (c.id) {
            Aug.REPAIR -> player.lives = (player.lives + 1).coerceAtMost(5)
            Aug.ARMOR -> player.shield = (player.shield + 1).coerceAtMost(loadout.maxShield())
        }
        pendingAugment = false
        banner = if (c.branchPick != 0) "EVOLVED" else "AUGMENT ONLINE"
        bannerSub = label
        bannerT = 1.8f
        fx.shockwave(player.x, player.y, 190f, c.color, 0.7f, 4f)
        fx.burst(player.x, player.y, 30, c.color, 300f, 2.8f, 0.8f, true)
        fx.flash(c.color, 0.3f)
        sound?.sfx(Sfx.POWERUP)
        haptics.medium()
    }

    // ------------------------------------------------------------- spawning

    private fun obtainBullet(): Bullet {
        for (i in bullets.indices) {
            bIdx = (bIdx + 1) % bullets.size
            if (!bullets[bIdx].active) return bullets[bIdx]
        }
        return bullets[bIdx]
    }

    private fun fire(
        x: Float, y: Float, vx: Float, vy: Float, r: Float, damage: Int,
        hostile: Boolean, color: Int, style: Int
    ): Bullet {
        val b = obtainBullet()
        b.active = true
        b.x = x; b.y = y; b.vx = vx; b.vy = vy
        b.r = r; b.damage = damage; b.hostile = hostile
        b.color = color; b.style = style
        b.life = 6f; b.grazed = false
        b.pierce = 0; b.hitCd = 0f; b.homing = false; b.turn = 0f; b.target = -1; b.splash = 0f
        b.fuse = 0f; b.shrapnel = 0
        return b
    }

    private fun fireAngle(
        x: Float, y: Float, angle: Float, speed: Float, r: Float, damage: Int,
        hostile: Boolean, color: Int, style: Int
    ): Bullet = fire(x, y, cos(angle) * speed, sin(angle) * speed, r, damage, hostile, color, style)

    internal fun allyBullet(x: Float, y: Float, vx: Float, vy: Float, r: Float, damage: Int, color: Int, style: Int): Bullet {
        val b = fire(x, y, vx, vy, r, damage, false, color, style)
        b.pierce = 0; b.hitCd = 0f; b.homing = false; b.turn = 0f; b.target = -1; b.splash = 0f
        b.fuse = 0f; b.shrapnel = 0
        return b
    }

    /** A lobbed shell that bursts into shrapnel on its fuse or on impact. */
    internal fun flakShell(
        x: Float, y: Float, vx: Float, vy: Float, r: Float,
        damage: Int, fragments: Int, fuse: Float, blast: Float
    ): Bullet {
        val b = allyBullet(x, y, vx, vy, r, damage, Palette.RED, 3)
        b.fuse = fuse
        b.shrapnel = fragments
        b.splash = blast
        b.life = fuse + 0.6f
        return b
    }

    /** Scatters a shell's fragments outward. */
    internal fun burstShell(b: Bullet) {
        val n = b.shrapnel
        if (n <= 0) return
        val off = rnd(TAU)
        for (i in 0 until n) {
            val a = off + i * TAU / n
            allyBullet(b.x, b.y, cos(a) * 560f, sin(a) * 560f, 3.4f, b.damage, Palette.AMBER, 1)
        }
        if (b.splash > 0f) {
            for (e in enemies) {
                if (!e.active) continue
                if (len(e.x - b.x, e.y - b.y) <= b.splash + e.r) hit(e, b.damage * 1.6f, e.x, e.y, false)
            }
            fx.shockwave(b.x, b.y, b.splash, Palette.AMBER, 0.4f, 3f)
        }
        fx.burst(b.x, b.y, 16, Palette.AMBER, 320f, 2.6f, 0.5f, true)
        fx.shake(0.12f)
        b.active = false
    }

    internal fun missile(x: Float, y: Float, vx: Float, vy: Float, r: Float, damage: Int, color: Int): Bullet {
        val b = allyBullet(x, y, vx, vy, r, damage, color, 3)
        b.homing = true
        b.turn = 4f
        b.life = 4.5f
        return b
    }

    /** AEGIS nodes delete incoming fire. */
    internal fun eatBulletsWithin(x: Float, y: Float, r: Float) {
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            if (len(b.x - x, b.y - y) <= r + b.r) {
                b.active = false
                player.overdrive = clamp(player.overdrive + 0.004f, 0f, 1f)
                fx.burst(b.x, b.y, 3, Palette.LIME, 140f, 1.8f, 0.3f)
            }
        }
    }

    /** NOVA converts caught fire into score. */
    internal fun bankBulletsWithin(x: Float, y: Float, r: Float) {
        var payout = 0
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            if (len(b.x - x, b.y - y) <= r + b.r) {
                b.active = false
                payout += (12 * multiplier).toInt()
                fx.burst(b.x, b.y, 3, Palette.AMBER, 150f, 1.8f, 0.3f)
            }
        }
        if (payout > 0) score += (payout * scoreMultiplier()).toInt()
    }

    /** REPULSOR shoves incoming fire back out of the field. */
    internal fun pushBulletsWithin(x: Float, y: Float, r: Float, force: Float) {
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            val dx = b.x - x
            val dy = b.y - y
            val d = len(dx, dy)
            if (d < r && d > 0.01f) {
                val k = (1f - d / r) * force
                b.vx += dx / d * k * 0.016f
                b.vy += dy / d * k * 0.016f
            }
        }
    }

    private fun obtainEnemy(): Enemy? = enemies.firstOrNull { !it.active }

    private fun spawnEnemy(kind: Int, x: Float, y: Float, hpMul: Float): Enemy? {
        val e = obtainEnemy() ?: return null
        val speedMul = clamp(0.9f + (wave - 1) * 0.05f, 0.9f, 1.95f)
        // >1 means slower firing: the first waves deliberately shoot less
        val rateMul = clamp(1.28f - (wave - 1) * 0.048f, 0.42f, 1.28f)
        e.active = true
        e.kind = kind
        e.x = x; e.y = y
        e.baseX = x
        e.t = rnd(TAU)
        e.hitFlash = 0f
        e.state = 0
        e.stateT = 0f
        e.angle = 0f
        e.vx = 0f
        e.phase = 0
        e.patternT = 0f
        e.spiral = 0f
        e.dropBias = 1f
        e.link = -1
        e.telegraph = 0f
        e.elite = false
        when (kind) {
            EK.DRIFTER -> {
                e.r = 16f; e.hp = 4f * hpMul; e.vy = 96f * speedMul
                e.fireEvery = 1.9f * rateMul; e.fireT = rnd(0.4f, 1.5f)
                e.score = 100; e.color = Palette.SKY
            }
            EK.WEAVER -> {
                e.r = 15f; e.hp = 3f * hpMul; e.vy = 126f * speedMul
                e.amp = rnd(60f, 110f); e.freq = rnd(1.5f, 2.4f)
                e.fireEvery = 2.3f * rateMul; e.fireT = rnd(0.5f, 1.8f)
                e.score = 130; e.color = Palette.MAGENTA
            }
            EK.CHARGER -> {
                e.r = 17f; e.hp = 6f * hpMul; e.vy = 150f * speedMul
                e.holdY = rnd(h * 0.16f, h * 0.34f)
                e.score = 200; e.color = Palette.RED
                e.dropBias = 1.3f
            }
            EK.TURRET -> {
                e.r = 21f; e.hp = 13f * hpMul; e.vy = 78f * speedMul
                e.holdY = rnd(h * 0.14f, h * 0.30f)
                e.fireEvery = 2.5f * rateMul; e.fireT = 1.4f
                e.amp = rnd(40f, 90f); e.freq = rnd(0.5f, 0.9f)
                e.score = 300; e.color = Palette.AMBER
                e.dropBias = 2.2f
            }
            EK.LANCER -> {
                e.r = 18f; e.hp = 9f * hpMul; e.vy = 92f * speedMul
                e.holdY = rnd(h * 0.12f, h * 0.26f)
                e.vx = if (chance(0.5f)) 46f else -46f
                e.fireEvery = 2.7f * rateMul; e.fireT = rnd(1f, 2f)
                e.score = 260; e.color = Palette.RED
                e.dropBias = 1.6f
            }
            EK.ORBITER -> {
                e.r = 15f; e.hp = 5f * hpMul; e.vy = 118f * speedMul
                e.holdY = rnd(h * 0.16f, h * 0.34f)
                e.amp = rnd(62f, 104f); e.freq = rnd(0.8f, 1.3f)
                e.seed = rnd(TAU)
                e.fireEvery = 1.5f * rateMul; e.fireT = rnd(0.4f, 1.2f)
                e.score = 190; e.color = Palette.VIOLET
            }
            EK.SPLITTER -> {
                e.r = 20f; e.hp = 11f * hpMul; e.vy = 72f * speedMul
                e.vx = rnd(-38f, 38f)
                e.fireEvery = 2.5f * rateMul; e.fireT = rnd(0.8f, 2f)
                e.score = 220; e.color = Palette.LIME
                e.dropBias = 1.4f
            }
            EK.MINELAYER -> {
                e.r = 20f; e.hp = 13f * hpMul; e.vy = 82f * speedMul
                e.holdY = rnd(h * 0.10f, h * 0.20f)
                e.vx = if (chance(0.5f)) 96f else -96f
                e.fireEvery = 1.35f * rateMul; e.fireT = 0.7f
                e.score = 320; e.color = Palette.AMBER
                e.dropBias = 2.0f
            }
            EK.SWARMER -> {
                e.r = 9f; e.hp = 2f * hpMul; e.vy = 210f * speedMul
                e.stateT = rnd(0.2f, 0.45f)
                e.score = 70; e.color = Palette.ROSE
                e.dropBias = 0.35f
            }
            EK.MINE -> {
                e.r = 11f; e.hp = 2f; e.vy = 20f
                e.stateT = 9f
                e.score = 60; e.color = Palette.RED
                e.dropBias = 0.2f
            }
            EK.SHIELDER -> {
                e.r = 18f; e.hp = 12f * hpMul; e.vy = 82f * speedMul
                e.aux = TAU * 0.25f
                e.fireEvery = 2.2f * rateMul; e.fireT = rnd(0.6f, 1.6f)
                e.score = 240; e.color = Palette.SKY
                e.dropBias = 1.5f
            }
            EK.WISP -> {
                e.r = 13f; e.hp = 4f * hpMul; e.vy = 122f * speedMul
                e.holdY = rnd(h * 0.12f, h * 0.34f)
                e.fireEvery = 1.9f * rateMul; e.fireT = rnd(0.5f, 1.4f)
                e.score = 210; e.color = Palette.VIOLET
                e.dropBias = 1.2f
            }
            EK.CARRIER -> {
                e.r = 24f; e.hp = 24f * hpMul; e.vy = 62f * speedMul
                e.holdY = rnd(h * 0.10f, h * 0.22f)
                e.vx = if (chance(0.5f)) 62f else -62f
                e.fireEvery = 2.7f * rateMul; e.fireT = 1.5f
                e.score = 420; e.color = Palette.LIME
                e.dropBias = 2.6f
            }
            EK.PYLON -> {
                e.r = 15f; e.hp = 9f * hpMul; e.vy = 94f * speedMul
                e.holdY = rnd(h * 0.18f, h * 0.32f)
                e.score = 200; e.color = Palette.AMBER
                e.dropBias = 1.3f
                // pair up with a waiting pylon so the tether has two ends
                val mine = enemies.indexOf(e)
                val partner = enemies.indexOfFirst { it.active && it.kind == EK.PYLON && it.link < 0 && it !== e }
                if (partner >= 0) {
                    e.link = partner
                    enemies[partner].link = mine
                }
            }
            EK.BOSS -> {
                val sector = Sectors.forWave(wave)
                e.bossType = sector.boss
                val typeMul = when (sector.boss) {
                    BT.WARDEN -> 0.92f
                    BT.HIVE -> 0.95f
                    BT.FORGE -> 1.3f
                    BT.NULLIFIER -> 1.15f
                    else -> 1f
                }
                e.r = if (sector.boss == BT.FORGE) 58f else 52f
                // quadratic term is what stops a maxed loadout melting late bosses;
                // the tier bonus stays modest so deep loops do not become a slog
                e.hp = (120f + wave * 42f + wave * wave * 1.9f) * typeMul * (1f + Sectors.tier(wave) * 0.35f)
                e.vy = 90f
                e.holdY = h * 0.20f
                e.fireEvery = 1f
                e.score = 3000 + wave * 600
                e.color = sector.accent
                e.amp = w * 0.26f
                e.freq = 0.55f
                e.dropBias = 6f
                boss = e
            }
        }

        // Elites appear once the run is deep enough: tougher, worth more.
        if (kind != EK.BOSS && kind != EK.MINE && wave >= 8 && chance(clamp(0.03f + wave * 0.012f, 0f, 0.45f))) {
            e.elite = true
            e.hp *= 1.6f + clamp(wave * 0.02f, 0f, 0.8f)
            e.r *= 1.12f
            e.score = (e.score * 2.2f).toInt()
            e.dropBias *= 1.8f
            e.fireEvery *= 0.8f
        }
        e.maxHp = e.hp
        return e
    }

    // -------------------------------------------------------- wave director

    private fun addSpawn(t: Float, kind: Int, x: Float, hpMul: Float = 1f) {
        script.add(Spawn(t, kind, x, -40f - rnd(0f, 30f), hpMul))
    }

    private fun buildWave(n: Int) {
        script.clear()
        scriptIdx = 0
        waveT = 0f
        val sector = Sectors.forWave(n)
        val tier = Sectors.tier(n)
        // linear early, quadratic late: the opening stays readable while a
        // fully-augmented ship still meets something that can kill it
        val hpMul = 1f + (n - 1) * 0.22f + (n - 1) * (n - 1) * 0.014f + tier * 0.9f

        if (Sectors.isBossWave(n)) {
            script.add(Spawn(1.9f, EK.BOSS, w * 0.5f, -110f, hpMul))
            return
        }

        var t = 0.4f
        val waveInSector = (n - 1) % Sectors.WAVES_PER_SECTOR
        // grows with the run overall, with a small breather at each sector start
        val groups = 2 + waveInSector.coerceAtMost(3) + (n / 6).coerceAtMost(4) + tier
        for (g in 0 until groups) {
            // the first two waves of the run stick to the gentler half of the roster
            val kind = if (n <= 3) sector.roster[g % 2] else sector.roster[(g + n) % sector.roster.size]
            t += addGroup(kind, t, n, hpMul)
        }
    }

    /** Lays down one formation of [kind] and returns how long it occupies the timeline. */
    private fun addGroup(kind: Int, t: Float, n: Int, hpMul: Float): Float {
        val scale = 1 + n / 6
        when (kind) {
            EK.DRIFTER -> {
                val count = (5 + scale).coerceAtMost(9)
                for (i in 0 until count) {
                    val f = (i + 0.5f) / count
                    addSpawn(t + i * 0.12f, EK.DRIFTER, w * (0.12f + 0.76f * f), hpMul)
                }
                return 2.4f
            }
            EK.WEAVER -> {
                val count = (4 + scale / 2).coerceAtMost(7)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.26f, EK.WEAVER, w * 0.22f, hpMul)
                    addSpawn(t + 0.13f + i * 0.26f, EK.WEAVER, w * 0.78f, hpMul)
                }
                return 2.7f
            }
            EK.CHARGER -> {
                val count = (2 + scale).coerceAtMost(6)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.4f, EK.CHARGER, w * rnd(0.18f, 0.82f), hpMul)
                }
                return 2.8f
            }
            EK.TURRET -> {
                val count = (1 + scale / 2).coerceAtMost(3)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.8f, EK.TURRET, w * rnd(0.22f, 0.78f), hpMul)
                }
                return 3.2f
            }
            EK.LANCER -> {
                val count = (1 + scale / 3).coerceAtMost(3)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.7f, EK.LANCER, w * (0.25f + 0.5f * i / count.coerceAtLeast(1)), hpMul)
                }
                return 3.4f
            }
            EK.ORBITER -> {
                val count = (2 + scale / 2).coerceAtMost(5)
                val cx = w * rnd(0.3f, 0.7f)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.3f, EK.ORBITER, cx, hpMul)
                }
                return 2.9f
            }
            EK.SPLITTER -> {
                val count = (2 + scale / 3).coerceAtMost(4)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.65f, EK.SPLITTER, w * rnd(0.2f, 0.8f), hpMul)
                }
                return 3.3f
            }
            EK.MINELAYER -> {
                addSpawn(t, EK.MINELAYER, w * rnd(0.25f, 0.75f), hpMul)
                if (n > 12) addSpawn(t + 1.4f, EK.MINELAYER, w * rnd(0.25f, 0.75f), hpMul)
                return 3.6f
            }
            EK.SWARMER -> {
                val count = (6 + scale * 2).coerceAtMost(14)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.1f, EK.SWARMER, w * rnd(0.12f, 0.88f), hpMul)
                }
                return 2.6f
            }
            EK.SHIELDER -> {
                val count = (2 + scale / 3).coerceAtMost(4)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.5f, EK.SHIELDER, w * (0.2f + 0.6f * (i + 0.5f) / count), hpMul)
                }
                return 3.0f
            }
            EK.WISP -> {
                val count = (2 + scale / 2).coerceAtMost(5)
                for (i in 0 until count) {
                    addSpawn(t + i * 0.4f, EK.WISP, w * rnd(0.15f, 0.85f), hpMul)
                }
                return 2.8f
            }
            EK.CARRIER -> {
                addSpawn(t, EK.CARRIER, w * rnd(0.3f, 0.7f), hpMul)
                if (n > 16) addSpawn(t + 1.6f, EK.CARRIER, w * rnd(0.3f, 0.7f), hpMul)
                return 3.8f
            }
            EK.PYLON -> {
                val pairs = if (n > 14) 2 else 1
                for (p in 0 until pairs) {
                    val y = t + p * 1.5f
                    addSpawn(y, EK.PYLON, w * rnd(0.12f, 0.3f), hpMul)
                    addSpawn(y + 0.05f, EK.PYLON, w * rnd(0.7f, 0.88f), hpMul)
                }
                return 3.4f
            }
            else -> return 1.5f
        }
    }

    private fun startWave(n: Int) {
        wave = n
        buildWave(n)
        awaitingNextWave = false
        val sector = Sectors.forWave(n)
        when {
            Sectors.isBossWave(n) -> {
                banner = "WARNING"
                bannerSub = sector.bossName
                bannerT = 2.4f
                fx.flash(Palette.RED, 0.3f)
                sound?.sfx(Sfx.WARN)
                haptics.medium()
            }
            Sectors.isSectorStart(n) -> {
                banner = sector.name
                bannerSub = sector.subtitle
                bannerT = 2.4f
                fx.flash(sector.accent, 0.22f)
                haptics.light()
            }
            else -> {
                banner = "WAVE $n"
                bannerSub = if (n == 1) "DRAG TO FLY" else ""
                bannerT = 1.8f
            }
        }
    }

    private fun activeEnemies(): Int = enemies.count { it.active }

    // ---------------------------------------------------------------- update

    fun update(dtRaw: Float) {
        fx.update(dtRaw)
        val dt = if (fx.hitStop > 0f) 0f else dtRaw
        if (dt <= 0f) return

        time += dt
        if (bannerT > 0f) bannerT -= dt

        updateWaveDirector(dt)
        updatePlayer(dt)
        arsenal.update(dt, this)
        updateBullets(dt)
        updateEnemies(dt)
        updatePickups(dt)
        collide()

        if (comboT > 0f) {
            comboT -= dt
            if (comboT <= 0f) combo = 0
        }
    }

    private fun updateWaveDirector(dt: Float) {
        if (awaitingNextWave) {
            // let the WAVE CLEAR banner land before the cards take over
            if (augmentDelay > 0f) {
                augmentDelay -= dt
                if (augmentDelay <= 0f) pendingAugment = true
                return
            }
            if (pendingAugment) return
            waveClearT -= dt
            if (waveClearT <= 0f) startWave(wave + 1)
            return
        }
        waveT += dt
        while (scriptIdx < script.size && script[scriptIdx].time <= waveT) {
            val s = script[scriptIdx]
            spawnEnemy(s.kind, s.x, s.y, s.hpMul)
            scriptIdx++
        }
        if (scriptIdx >= script.size && activeEnemies() == 0 && !gameOver) {
            val bonus = ((400 * wave + player.lives * 150) * scoreMultiplier()).toInt()
            score += bonus
            banner = "WAVE $wave CLEAR"
            bannerSub = "+$bonus   -   UPGRADE READY"
            bannerT = 1.9f
            sound?.sfx(Sfx.WAVE_CLEAR)
            awaitingNextWave = true
            waveClearT = 1.4f
            augmentDelay = 1.15f
            boss = null
            bossHpRatio = 0f
        }
    }

    private fun updatePlayer(dt: Float) {
        if (!player.alive) {
            player.respawnT -= dt
            if (player.respawnT <= 0f) {
                player.alive = true
                player.invuln = 1.5f
                centerPlayer()
                fx.shockwave(player.x, player.y, 90f, Palette.CYAN, 0.5f, 2.5f)
            }
            return
        }

        val px = player.x
        val handling = clamp(loadout.handling() * ship.handlingMul, 0.3f, 0.86f)
        player.x = approach(player.x, player.tx, handling, dt)
        player.y = approach(player.y, player.ty, handling, dt)
        val vx = (player.x - px) / dt.coerceAtLeast(0.0001f)
        player.bank = approach(player.bank, clamp(vx / 420f, -1f, 1f), 0.18f, dt)
        player.thrust = approach(player.thrust, clamp(abs(vx) / 300f, 0f, 1f), 0.2f, dt)

        if (player.invuln > 0f) player.invuln -= dt
        if (player.odTime > 0f) {
            player.odTime -= dt
            if (player.odTime <= 0f) {
                fx.shockwave(player.x, player.y, 120f, Palette.AMBER, 0.4f, 2f)
            }
        }

        // engine trail
        if (chance(clamp(0.35f + player.thrust * 0.5f, 0f, 1f))) {
            fx.cone(
                player.x, player.y + player.bodyR * 0.9f, 1, TAU * 0.25f, 0.35f,
                if (player.odTime > 0f) Palette.AMBER else Palette.VIOLET, 130f, 2.2f, 0.35f
            )
        }

        player.fireT -= dt
        if (player.fireT <= 0f) {
            playerFire()
            var base = when (player.weapon) {
                1 -> 0.155f; 2 -> 0.145f; 3 -> 0.135f; 4 -> 0.125f; else -> 0.115f
            }
            base *= loadout.fireIntervalMul() * ship.fireMul
            if (loadout.branch[Aug.SPREAD] == Aug.A) base *= 0.82f
            player.fireT = base * (if (player.odTime > 0f) 0.55f else 1f)
        }
    }

    private fun playerShot(offX: Float, offY: Float, angleDeg: Float, r: Float, dmg: Int): Bullet {
        val od = player.odTime > 0f
        val a = (-90f + angleDeg) * DEG
        val speed = 1000f * loadout.bulletSpeedMul()
        val color = if (od) Palette.AMBER else Palette.CYAN
        var d = dmg.coerceAtLeast(1)
        if (loadout.critChance() > 0f && chance(loadout.critChance())) d *= 2
        val b = fireAngle(player.x + offX, player.y + offY, a, speed, r, d, false, color, 1)
        b.pierce += loadout.extraPierce()
        return b
    }

    private fun playerFire() {
        val od = player.odTime > 0f
        val d = (if (od) 4 else 2) + loadout.damageBonus() + ship.damageBonus
        when (player.weapon) {
            1 -> playerShot(0f, -14f, 0f, 4.4f, d)
            2 -> { playerShot(-7f, -12f, 0f, 4f, d); playerShot(7f, -12f, 0f, 4f, d) }
            3 -> { playerShot(-9f, -10f, 0f, 3.8f, d); playerShot(9f, -10f, 0f, 3.8f, d); playerShot(0f, -16f, 0f, 4.6f, d) }
            4 -> {
                playerShot(-10f, -10f, -7f, 3.8f, d); playerShot(10f, -10f, 7f, 3.8f, d)
                playerShot(0f, -16f, 0f, 4.6f, d)
            }
            else -> {
                playerShot(-11f, -9f, -9f, 3.8f, d); playerShot(11f, -9f, 9f, 3.8f, d)
                playerShot(-5f, -14f, -3f, 4.2f, d); playerShot(5f, -14f, 3f, 4.2f, d)
                playerShot(0f, -17f, 0f, 4.8f, d)
            }
        }
        if (od) {
            playerShot(-17f, -4f, -34f, 3.6f, d)
            playerShot(17f, -4f, 34f, 3.6f, d)
        }
        spreadFire(d)
        sound?.sfx(Sfx.SHOOT)
        fx.cone(player.x, player.y - 14f, 2, -TAU * 0.25f, 0.5f, if (od) Palette.AMBER else Palette.CYAN, 190f, 1.9f, 0.14f)
    }

    private fun spreadFire(d: Int) {
        val lvl = loadout.lvl[Aug.SPREAD]
        if (lvl <= 0) return
        when (loadout.branch[Aug.SPREAD]) {
            Aug.A -> { // FAN: a wide, fast curtain
                val extra = lvl - 3
                val n = 8 + extra
                for (i in 0 until n) {
                    val ang = -66f + i * (132f / (n - 1))
                    playerShot(0f, -8f, ang, 3.3f, (d * 0.8f).toInt())
                }
            }
            Aug.B -> { // PHALANX: heavy piercing bolts
                val extra = lvl - 3
                for (i in 0 until 4) {
                    val off = (i - 1.5f) * 14f
                    val b = playerShot(off, -10f, 0f, 5.6f, (d * 1.6f).toInt() + extra)
                    b.pierce = 2 + extra
                }
            }
            else -> {
                val angles = when (lvl) {
                    1 -> floatArrayOf(-22f, 22f)
                    2 -> floatArrayOf(-22f, 22f, -40f, 40f)
                    else -> floatArrayOf(-18f, 18f, -34f, 34f, -52f, 52f)
                }
                for (a in angles) playerShot(0f, -8f, a, 3.6f, d)
            }
        }
    }

    private fun steerMissile(b: Bullet, dt: Float) {
        if (b.target < 0 || !enemies[b.target].active) b.target = arsenal.nearestEnemy(this, b.x, b.y)
        val ti = b.target
        if (ti >= 0) {
            val e = enemies[ti]
            val want = atan2(e.y - b.y, e.x - b.x)
            val cur = atan2(b.vy, b.vx)
            var diff = want - cur
            while (diff > TAU / 2f) diff -= TAU
            while (diff < -TAU / 2f) diff += TAU
            val step = clamp(diff, -b.turn * dt, b.turn * dt)
            val sp = clamp(len(b.vx, b.vy) + 420f * dt, 120f, 560f)
            val na = cur + step
            b.vx = cos(na) * sp
            b.vy = sin(na) * sp
        }
        if (chance(0.7f)) {
            fx.cone(b.x, b.y, 1, atan2(-b.vy, -b.vx), 0.5f, b.color, 90f, 1.7f, 0.28f)
        }
    }

    private fun updateBullets(dt: Float) {
        val margin = 70f
        for (b in bullets) {
            if (!b.active) continue
            if (b.hitCd > 0f) b.hitCd -= dt
            if (b.fuse > 0f) {
                b.fuse -= dt
                if (b.fuse <= 0f) { burstShell(b); continue }
            }
            if (b.homing) steerMissile(b, dt)
            b.x += b.vx * dt
            b.y += b.vy * dt
            b.life -= dt
            if (b.life <= 0f || b.x < -margin || b.x > w + margin || b.y < -margin || b.y > h + margin) {
                b.active = false
            }
        }
    }

    internal fun enemyBulletSpeed(): Float =
        clamp(155f + wave * 11f + Sectors.tier(wave) * 45f, 155f, 470f)

    /** Fire an enemy bullet. Used by [EnemyAI] and [BossAI]. */
    internal fun hostileShot(x: Float, y: Float, angle: Float, speed: Float, r: Float, color: Int, style: Int): Bullet =
        fireAngle(x, y, angle, speed, r, 1, true, color, style)

    internal fun setBossHp(v: Float) {
        bossHpRatio = v
    }

    internal fun setBanner(main: String, sub: String, time: Float) {
        banner = main
        bannerSub = sub
        bannerT = time
    }

    internal fun clearHostileFire() {
        clearHostileBullets(false)
    }

    /** Spawn a support enemy mid-wave (mines, broods, summoned wings). */
    internal fun countActive(kind: Int): Int = enemies.count { it.active && it.kind == kind }

    internal fun spawnMinion(kind: Int, x: Float, y: Float): Enemy? =
        spawnEnemy(kind, x, y, 1f + (wave - 1) * 0.3f)

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.active) continue
            e.t += dt
            if (e.hitFlash > 0f) e.hitFlash -= dt

            if (e.telegraph > 0f && e.kind != EK.LANCER) e.telegraph = 0f
            EnemyAI.update(this, e, dt)

            if (e.kind != EK.BOSS && (e.y > h + 90f || e.x < -160f || e.x > w + 160f || e.y < -260f)) {
                e.active = false
            }
        }
    }

    private fun updatePickups(dt: Float) {
        for (u in pickups) {
            if (!u.active) continue
            u.t += dt
            u.life -= dt
            if (u.life <= 0f) { u.active = false; continue }
            // magnetised towards the ship when close
            if (player.alive) {
                val dx = player.x - u.x
                val dy = player.y - u.y
                val d = len(dx, dy)
                val magnet = loadout.magnetRadius() * ship.magnetMul
                if (d < magnet && d > 0.001f) {
                    val pull = (1f - d / magnet) * 620f
                    u.vx += dx / d * pull * dt
                    u.vy += dy / d * pull * dt
                }
            }
            u.vx *= 1f - 1.1f * dt
            u.x += u.vx * dt
            u.y += u.vy * dt
            if (u.y > h + 40f) u.active = false
        }
    }

    private fun dropLoot(x: Float, y: Float, bias: Float, fromTough: Boolean = false) {
        val roll = rnd(1f)
        // Weapon drops thin out as the gun grows, so the last levels are earned
        // rather than handed over; a long pity timer stops a cold streak dead.
        val starving = player.weapon < MAX_WEAPON && killsSinceWeapon >= 14 + player.weapon * 6
        val weaponChance = 0.042f * bias * clamp(1f - player.weapon * 0.13f, 0.3f, 1f)
        val lifeChance = if (fromTough) 0.012f * bias else 0f
        val shieldChance = if (player.shield >= loadout.maxShield()) 0f else 0.018f * bias
        // Cumulative windows - each kind needs its own slice of the roll, or the
        // later branches are simply unreachable.
        val wEnd = weaponChance
        val lEnd = wEnd + lifeChance
        val sEnd = lEnd + shieldChance
        val gEnd = sEnd + 0.34f
        val kind = when {
            starving -> PK.WEAPON
            roll < wEnd -> PK.WEAPON
            roll < lEnd -> PK.LIFE
            roll < sEnd -> PK.SHIELD
            roll < gEnd -> PK.GEM
            else -> return
        }
        val u = pickups.firstOrNull { !it.active } ?: return
        if (kind == PK.WEAPON) killsSinceWeapon = 0
        u.active = true
        u.kind = kind
        u.x = x; u.y = y
        u.vx = rnd(-40f, 40f)
        u.vy = rnd(45f, 85f)
        u.t = 0f
        u.life = if (kind == PK.GEM) 9f else 13f
        u.r = if (kind == PK.GEM) 9f else 13f
    }

    private fun collide() {
        // player shots vs enemies
        for (b in bullets) {
            if (!b.active || b.hostile || b.hitCd > 0f) continue
            for (e in enemies) {
                if (!e.active) continue
                val dx = e.x - b.x
                val dy = e.y - b.y
                val rr = e.r + b.r
                if (dx * dx + dy * dy <= rr * rr) {
                    if ((e.kind == EK.BOSS && BossAI.blocksHit(e, b.x, b.y)) ||
                        EnemyAI.blocksHit(e, b.x, b.y)
                    ) {
                        b.active = false
                        fx.burst(b.x, b.y, 4, Palette.AMBER, 190f, 2f, 0.28f)
                        break
                    }
                    if (b.shrapnel > 0) {
                        hit(e, b.damage.toFloat(), b.x, b.y, true)
                        burstShell(b)
                        break
                    }
                    if (b.splash > 0f) detonate(b)
                    hit(e, b.damage.toFloat(), b.x, b.y, true)
                    if (b.pierce > 0) {
                        b.pierce--
                        b.hitCd = 0.09f
                    } else {
                        b.active = false
                    }
                    break
                }
            }
        }

        if (player.alive) {
            val invulnerable = player.invuln > 0f || player.odTime > 0f
            // hostile shots vs player (small hitbox, generous graze)
            for (b in bullets) {
                if (!b.active || !b.hostile) continue
                val dx = b.x - player.x
                val dy = b.y - player.y
                val d2 = dx * dx + dy * dy
                val hitR = player.hitR + b.r
                if (d2 <= hitR * hitR) {
                    b.active = false
                    if (!invulnerable) { hurtPlayer(); return }
                    fx.burst(b.x, b.y, 4, Palette.AMBER, 150f, 2f, 0.3f)
                } else if (!b.grazed && d2 <= GRAZE_R * GRAZE_R) {
                    b.grazed = true
                    player.overdrive = clamp(player.overdrive + loadout.grazeCharge() * ship.grazeMul, 0f, 1f)
                    score += (15 * scoreMultiplier()).toInt()
                    fx.cone(b.x, b.y, 2, atan2(-dy, -dx), 0.8f, Palette.CYAN, 140f, 1.6f, 0.25f)
                }
            }
            // pylon tethers
            for (i in enemies.indices) {
                val a = enemies[i]
                if (!a.active || a.kind != EK.PYLON || a.link <= i) continue
                val b = enemies[a.link]
                if (!b.active || b.kind != EK.PYLON) continue
                if (a.state != 2 || b.state != 2) continue
                if (player.invuln <= 0f && player.odTime <= 0f &&
                    distToSegment(player.x, player.y, a.x, a.y, b.x, b.y) < 9f + player.hitR
                ) {
                    hurtPlayer()
                    return
                }
            }
            // ramming
            for (e in enemies) {
                if (!e.active) continue
                val dx = e.x - player.x
                val dy = e.y - player.y
                val rr = e.r * 0.8f + player.hitR
                if (dx * dx + dy * dy <= rr * rr) {
                    if (player.odTime > 0f) {
                        hit(e, 6f, player.x, player.y)
                    } else if (player.invuln <= 0f) {
                        if (e.kind != EK.BOSS) hit(e, 999f, e.x, e.y)
                        hurtPlayer()
                        return
                    }
                }
            }
            // pickups
            for (u in pickups) {
                if (!u.active) continue
                val dx = u.x - player.x
                val dy = u.y - player.y
                val rr = u.r + player.bodyR
                if (dx * dx + dy * dy <= rr * rr) {
                    u.active = false
                    collect(u)
                }
            }
        }
    }

    private fun collect(u: PowerUp) {
        when (u.kind) {
            PK.WEAPON -> {
                if (player.weapon < MAX_WEAPON) {
                    player.weapon++
                    fx.popText(player.x, player.y - 46f, "WEAPON ${player.weapon}", Palette.CYAN, 20f)
                } else {
                    score += 1500
                    fx.popText(player.x, player.y - 46f, "+1500", Palette.CYAN, 20f)
                }
                fx.shockwave(player.x, player.y, 70f, Palette.CYAN, 0.4f, 2.5f)
            }
            PK.SHIELD -> {
                player.shield = (player.shield + 1).coerceAtMost(loadout.maxShield())
                fx.popText(player.x, player.y - 46f, "SHIELD", Palette.LIME, 20f)
                fx.shockwave(player.x, player.y, 70f, Palette.LIME, 0.4f, 2.5f)
            }
            PK.LIFE -> {
                player.lives = (player.lives + 1).coerceAtMost(4)
                fx.popText(player.x, player.y - 46f, "1UP", Palette.ROSE, 24f)
                fx.flash(Palette.ROSE, 0.25f)
            }
            else -> {
                val v = (200 * multiplier * scoreMultiplier()).toInt()
                score += v
                fx.popText(u.x, u.y, "+$v", Palette.AMBER, 16f, 0.7f)
            }
        }
        if (loadout.reclaimCharge() > 0f) {
            player.overdrive = clamp(player.overdrive + loadout.reclaimCharge(), 0f, 1f)
        }
        fx.burst(u.x, u.y, 10, Palette.WHITE, 190f, 2f, 0.4f)
        sound?.sfx(if (u.kind == PK.GEM) Sfx.PICKUP else Sfx.POWERUP)
        haptics.light()
    }

    internal fun hit(e: Enemy, dmg: Float, hx: Float, hy: Float, spark: Boolean = true) {
        e.hp -= dmg
        e.hitFlash = 0.12f
        if (spark) fx.cone(hx, hy, 3, -TAU * 0.25f + rnd(-0.6f, 0.6f), 0.9f, Palette.WHITE, 170f, 1.8f, 0.22f)
        if (e.hp > 0f) return
        killEnemy(e)
    }

    /** Warhead blast: everything inside the radius takes a share. */
    private fun detonate(b: Bullet) {
        fx.burst(b.x, b.y, 26, Palette.AMBER, 380f, 3.2f, 0.7f, true)
        fx.shockwave(b.x, b.y, b.splash, Palette.AMBER, 0.45f, 3f)
        fx.shake(0.22f)
        for (e in enemies) {
            if (!e.active) continue
            if (len(e.x - b.x, e.y - b.y) <= b.splash + e.r) {
                hit(e, b.damage * 0.7f, e.x, e.y, false)
            }
        }
    }

    private fun killEnemy(e: Enemy) {
        e.active = false
        kills++
        killsSinceWeapon++
        combo++
        comboT = COMBO_WINDOW
        if (combo > maxCombo) maxCombo = combo
        player.overdrive = clamp(player.overdrive + 0.005f, 0f, 1f)

        val gained = (e.score * multiplier * scoreMultiplier()).toInt()
        score += gained

        if (e.kind == EK.BOSS) {
            boss = null
            bossHpRatio = 0f
            fx.freeze(0.22f)
            fx.flash(Palette.WHITE, 0.8f)
            fx.shake(1f)
            for (i in 0 until 9) {
                fx.burst(e.x + rnd(-e.r, e.r), e.y + rnd(-e.r * 0.7f, e.r * 0.7f), 26, if (i % 2 == 0) Palette.AMBER else Palette.MAGENTA, 460f, 4f, 1.1f, true)
            }
            fx.shockwave(e.x, e.y, w * 1.6f, Palette.WHITE, 0.9f, 6f)
            fx.shockwave(e.x, e.y, w * 1.1f, Palette.MAGENTA, 0.7f, 4f)
            fx.popText(e.x, e.y - 30f, "+$gained", Palette.WHITE, 30f, 1.6f)
            haptics.heavy()
            sound?.sfx(Sfx.BIG_EXPLODE)
            for (i in 0 until 4) dropLoot(e.x + rnd(-50f, 50f), e.y + rnd(-20f, 20f), 9f, true)
            clearHostileBullets(true)
        } else {
            if (e.kind == EK.MINE) EnemyAI.detonateMine(this, e)
            if (e.kind == EK.SPLITTER && e.tier < 2) splitInto(e)
            fx.burst(e.x, e.y, 16, e.color, 300f, 2.8f, 0.55f, true)
            fx.burst(e.x, e.y, 8, Palette.WHITE, 180f, 2.2f, 0.35f)
            fx.shockwave(e.x, e.y, e.r * 3.2f, e.color, 0.32f, 2.4f)
            fx.shake(0.07f)
            fx.popText(e.x, e.y - 12f, "+$gained", if (combo > 8) Palette.AMBER else Palette.WHITE, 15f, 0.6f)
            sound?.sfx(Sfx.EXPLODE)
            if (combo > 0 && combo % 10 == 0) {
                fx.popText(player.x, player.y - 76f, "COMBO x${combo}", Palette.MAGENTA, 22f, 1.1f)
                haptics.light()
            }
            dropLoot(e.x, e.y, e.dropBias, e.elite || e.kind == EK.TURRET || e.kind == EK.MINELAYER)
        }
    }

    private fun distToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax
        val vy = by - ay
        val lenSq = vx * vx + vy * vy
        if (lenSq < 0.0001f) return len(px - ax, py - ay)
        var t = ((px - ax) * vx + (py - ay) * vy) / lenSq
        t = clamp(t, 0f, 1f)
        return len(px - (ax + vx * t), py - (ay + vy * t))
    }

    /** A destroyed splitter leaves two faster, smaller halves. */
    private fun splitInto(parent: Enemy) {
        for (i in 0 until 2) {
            val child = spawnEnemy(EK.SPLITTER, parent.x, parent.y, 1f) ?: return
            child.tier = parent.tier + 1
            child.hp = parent.maxHp * 0.32f
            child.maxHp = child.hp
            child.r = parent.r * 0.62f
            child.vx = if (i == 0) -105f else 105f
            child.vy = parent.vy * 1.45f
            child.score = (parent.score * 0.4f).toInt()
            child.fireEvery = parent.fireEvery * 0.85f
            child.fireT = rnd(0.4f, 1f)
            child.elite = false
            child.dropBias = 0.4f
        }
        fx.shockwave(parent.x, parent.y, parent.r * 2.5f, Palette.LIME, 0.35f, 2.5f)
    }

    /** Wipes enemy fire. When [convert] is set each bullet pays out score. Returns the payout. */
    private fun clearHostileBullets(convert: Boolean): Int {
        var payout = 0
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            b.active = false
            fx.burst(b.x, b.y, 3, Palette.AMBER, 130f, 1.8f, 0.35f)
            if (convert) payout += (25 * multiplier).toInt()
        }
        if (payout > 0) score += payout
        return payout
    }

    private fun hurtPlayer() {
        if (player.shield > 0) {
            player.shield--
            player.invuln = 1.4f
            fx.shockwave(player.x, player.y, 110f, Palette.LIME, 0.5f, 3f)
            fx.burst(player.x, player.y, 20, Palette.LIME, 280f, 2.6f, 0.5f, true)
            fx.flash(Palette.LIME, 0.22f)
            fx.shake(0.3f)
            fx.popText(player.x, player.y - 50f, "SHIELD DOWN", Palette.LIME, 18f)
            sound?.sfx(Sfx.HURT)
            haptics.medium()
            return
        }

        player.lives--
        player.alive = false
        player.respawnT = 1.3f
        player.weapon = (player.weapon - 1).coerceAtLeast(1)
        combo = 0
        comboT = 0f
        fx.burst(player.x, player.y, 44, Palette.CYAN, 420f, 3.6f, 0.9f, true)
        fx.burst(player.x, player.y, 22, Palette.WHITE, 240f, 3f, 0.6f)
        fx.shockwave(player.x, player.y, 200f, Palette.CYAN, 0.7f, 4f)
        fx.flash(Palette.RED, 0.5f)
        fx.shake(0.85f)
        fx.freeze(0.16f)
        sound?.sfx(Sfx.HURT)
        haptics.heavy()
        clearHostileBullets(false)

        if (player.lives <= 0) {
            gameOver = true
            player.respawnT = 999f
        }
    }

    // ----------------------------------------------------------------- draw

    fun draw(c: Canvas) {
        for (u in pickups) if (u.active) Draw.powerUp(c, u)
        for (e in enemies) if (e.active && e.kind == EK.LANCER) Draw.lancerTelegraph(c, e, h)
        for (i in enemies.indices) {
            val a = enemies[i]
            if (!a.active || a.kind != EK.PYLON || a.link <= i) continue
            val b = enemies[a.link]
            if (!b.active || b.kind != EK.PYLON) continue
            if (a.state < 1 || b.state < 1 || a.state > 2 || b.state > 2) continue
            Draw.pylonTether(c, a, b, a.state == 2 && b.state == 2, minOf(a.telegraph, b.telegraph))
        }
        for (e in enemies) if (e.active) {
            Draw.enemy(c, e, time)
            if (e.kind == EK.BOSS) BossAI.drawArmour(c, e)
            if (e.kind == EK.SHIELDER) Draw.shielderPlate(c, e)
        }
        for (b in bullets) if (b.active && !b.hostile) Draw.bullet(c, b)
        arsenal.draw(c, this)
        Draw.player(c, player, time)
        for (b in bullets) if (b.active && b.hostile) Draw.bullet(c, b)
        fx.drawParticles(c)
        fx.drawTexts(c)
    }

    fun bossPresent(): Boolean = boss != null && (boss?.active == true)
}

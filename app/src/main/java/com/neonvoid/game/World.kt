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
    var hpMul: Float = 1f,
    var elite: Boolean = false
)

/**
 * The simulation: player, bullets, enemies, pickups, the wave director and scoring.
 * Rendering of these objects lives here too so the draw order stays obvious.
 */
class World(internal val fx: Fx, private val haptics: Haptics) {

    companion object {
        const val ENEMY_CAP = 56
        const val BULLET_CAP = 620

        /** Seconds a boss fight runs at full toughness before it starts giving. */
        const val BOSS_PATIENCE = 60f

        /** Kills deep an AFTERSHOCK chain may run before it stops spreading. */
        const val AFTERSHOCK_CHAIN = 2

        /** How long the overload klaxon runs before settling to a low hum. */
        const val ALARM_TIME = 3.6f

        /** Seconds an enemy may hold position before it is pushed off screen. */
        const val HOLD_LIMIT = 26f
        const val GRAZE_R = 26f
        const val OD_DURATION = 3.2f
        const val COMBO_WINDOW = 3.2f
        const val MAX_WEAPON = 5
    }

    var w = 540f
        private set
    var h = 1000f
        private set

    /** Slot 0 is always the local pilot; slot 1 is the co-op partner. */
    val slots = arrayOf(PlayerSlot(0, fx), PlayerSlot(1, fx))

    val player: Player get() = slots[0].player
    val loadout: Loadout get() = slots[0].loadout
    val arsenal: Arsenal get() = slots[0].arsenal

    /** The hull chosen in the hangar; set before [reset]. */
    var ship: Ship
        get() = slots[0].ship
        set(v) { slots[0].ship = v }

    /**
     * Which themed level the run begins in, zero-based. Difficulty still comes
     * from the wave number alone; this only picks where the tour starts.
     */
    var levelOffset = 0

    fun themeIndex(wave: Int): Int =
        (levelOffset + Levels.index(wave)) % Levels.list.size

    fun theme(wave: Int): LevelTheme = Levels.list[themeIndex(wave)]

    fun levelNumber(wave: Int): Int = themeIndex(wave) + 1

    fun bossTypeFor(wave: Int): Int {
        val t = theme(wave)
        val within = ((wave - 1) % Levels.WAVES_PER_LEVEL) / 5
        return t.bossPool[within % t.bossPool.size]
    }

    /** Permanent shop bonuses; set before [reset]. */
    var meta: Meta
        get() = slots[0].meta
        set(v) { slots[0].meta = v }

    /** True when a second pilot is in the run. */
    val coop: Boolean get() = slots[1].joined

    fun joinedSlots(): List<PlayerSlot> = slots.filter { it.joined }

    /** Optional sound sink; null in headless tests. */
    var sound: SoundBus? = null
    internal val bullets = Array(BULLET_CAP) { Bullet() }
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
    /** How many themed levels the run has completed. */
    var levelsCleared = 0
        private set

    /** What this run contributed, read by the contracts when it ends. */
    val tally = RunTally()

    /**
     * Killscreen tier: one for every 30-wave level survived. Everything that
     * can be made faster is, permanently, for the rest of the run.
     */
    var overload = 0
        private set

    /** Seconds of alarm left on the overload transition. */
    var overloadAlarm = 0f
        private set

    private var lastArch = -1
    private val planBuf = ArrayList<GroupPlan>(8)

    /** Free respawns left, bought in the shop. */
    private var revives = 0
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

    private fun scoreMultiplier(): Float = loadout.scoreMul() * ship.scoreMul * meta.scoreMul

    // Killscreen scaling. Speed is what makes it feel like a wall, so the
    // fire rate and projectile speed move hardest and health least.
    val overloadSpeed: Float get() = clamp(1f + 0.14f * overload, 1f, 1.85f)
    val overloadRate: Float get() = clamp(1f - 0.17f * overload, 0.34f, 1f)
    // capped so a shot still crosses the screen slowly enough to be read
    val overloadBullet: Float get() = clamp(1f + 0.17f * overload, 1f, 1.85f)
    private val overloadHp: Float get() = 1f + 0.18f * overload

    /** GRAZE FIELD widens the window that charges overdrive. */
    private val grazeRadius: Float get() = GRAZE_R * meta.grazeMul

    val multiplier: Float
        get() = clamp(1f + combo * 0.1f, 1f, 9.9f)

    /** 0..1 pacing signal the background uses to speed up over time. */
    val intensity: Float
        get() = clamp((wave - 1) / 14f, 0f, 1f)

    fun resize(width: Float, height: Float) {
        w = width; h = height
        for (s in slots) {
            val p = s.player
            if (p.x == 0f && p.y == 0f) s.home(w, h)
            p.x = clamp(p.x, 20f, w - 20f)
            p.y = clamp(p.y, h * 0.25f, h - 60f)
            p.tx = p.x; p.ty = p.y
        }
    }

    fun reset() {
        for (b in bullets) b.active = false
        for (e in enemies) e.active = false
        for (u in pickups) u.active = false
        script.clear(); scriptIdx = 0
        score = 0; combo = 0; maxCombo = 0; comboT = 0f
        wave = 0; kills = 0; killsSinceWeapon = 4; gameOver = false
        levelsCleared = 0
        overload = 0
        overloadAlarm = 0f
        lastArch = -1
        tally.reset()
        revives = meta.revives
        pendingAugment = false
        boss = null; bossHpRatio = 0f
        awaitingNextWave = true
        waveClearT = 0.9f
        augmentDelay = 0f
        time = 0f
        banner = ""; bannerSub = ""; bannerT = 0f
        slots[0].joined = true
        for (s in slots) if (s.joined) s.resetFor(w, h)
        spreadStartPositions()
        fx.reset()
    }

    /** Give the two pilots a little room at the start of a co-op run. */
    private fun spreadStartPositions() {
        if (!coop) return
        for (s in slots) {
            if (!s.joined) continue
            val off = if (s.index == 0) -60f else 60f
            s.player.x = clamp(w * 0.5f + off, 20f, w - 20f)
            s.player.tx = s.player.x
        }
    }

    /** Adds a partner to the run. Call before [reset]. */
    fun joinPartner(partnerShip: Ship, partnerMeta: Meta, partnerName: String) {
        slots[1].joined = true
        slots[1].ship = partnerShip
        slots[1].meta = partnerMeta
        slots[1].name = partnerName
    }

    fun dropPartner() {
        slots[1].joined = false
        slots[1].awaitingAugment = false
    }

    // ---------------------------------------------------------------- input

    fun moveBy(dx: Float, dy: Float) = moveBy(0, dx, dy)

    fun moveBy(slotIndex: Int, dx: Float, dy: Float) {
        val p = slots[slotIndex].player
        p.tx = clamp(p.tx + dx, 18f, w - 18f)
        p.ty = clamp(p.ty + dy, h * 0.14f, h - 34f)
    }

    internal fun haptic(): Haptics = haptics

    /** The wave-clear banner would otherwise bleed through the augment screen. */
    internal fun hideBanner() {
        bannerT = 0f
    }

    fun canOverdrive(): Boolean = canOverdrive(0)

    fun canOverdrive(slotIndex: Int): Boolean {
        val s = slots[slotIndex]
        return s.joined && s.player.overdrive >= 1f && s.player.odTime <= 0f && s.player.alive
    }

    fun triggerOverdrive(): Boolean = triggerOverdrive(0)

    fun triggerOverdrive(slotIndex: Int): Boolean {
        if (!canOverdrive(slotIndex)) return false
        val p = slots[slotIndex].player
        p.odTime = OD_DURATION * slots[slotIndex].loadout.overdriveSeconds() + meta.overdriveBonus
        p.overdrive = 0f
        tally.overdrives++
        val gained = clearHostileBullets(true)
        fx.shockwave(p.x, p.y, w * 1.3f, Palette.AMBER, 0.6f, 5f)
        fx.shockwave(p.x, p.y, w * 0.8f, Palette.WHITE, 0.4f, 3f)
        fx.flash(Palette.AMBER, 0.55f)
        fx.shake(0.5f)
        fx.freeze(0.09f)
        fx.burst(p.x, p.y, 46, Palette.AMBER, 460f, 3.4f, 0.7f, true)
        sound?.sfx(Sfx.OVERDRIVE)
        if (gained > 0) fx.popText(p.x, p.y - 70f, "+$gained", Palette.AMBER, 24f, 1.1f)
        haptics.heavy()
        return true
    }

    /** Which pilot owes the current pick. */
    var augmentSlot = 0
        private set

    /** Offers for the between-wave choice, for whoever is picking. */
    /** WIDE DRAFT buys a fourth card, so the caller asks the world, not itself. */
    val draftCards: Int get() = meta.draftCards

    fun rollAugments(count: Int): List<AugCard> = slots[augmentSlot].loadout.rollOffers(count)

    /**
     * Nothing left to install and no room to add anything: pay the draft out in
     * score rather than forcing a card in, which used to push the bay past its
     * own cap and leave the HUD reading 9/8.
     */
    fun skipDraft() {
        val s = slots[augmentSlot]
        val bonus = (2500 * wave * scoreMultiplier()).toInt()
        score += bonus
        s.awaitingAugment = false
        val next = slots.indexOfFirst { it.awaitingAugment }
        if (next >= 0) {
            augmentSlot = next
        } else {
            pendingAugment = false
            augmentSlot = 0
        }
        banner = "BAY FULL"
        bannerSub = "+$bonus"
        bannerT = 1.8f
        fx.popText(s.player.x, s.player.y - 46f, "+$bonus", Palette.AMBER, 20f)
        sound?.sfx(Sfx.POWERUP)
    }

    private fun openDraft() {
        for (s in slots) s.awaitingAugment = s.joined
        augmentSlot = slots.indexOfFirst { it.awaitingAugment }.coerceAtLeast(0)
        pendingAugment = true
    }

    fun applyAugment(c: AugCard) {
        val s = slots[augmentSlot]
        val player = s.player
        val loadout = s.loadout
        val label = loadout.apply(c)
        when (c.id) {
            Aug.REPAIR -> player.lives = (player.lives + 1).coerceAtMost(5)
            Aug.ARMOR -> {
                player.shield = (player.shield + 1).coerceAtMost(loadout.maxShield())
                player.shieldHits = loadout.shieldDepth()
            }
            Aug.BULWARK -> player.shieldHits = loadout.shieldDepth()
            Aug.HARDPOINT -> player.weapon = (player.weapon + 1).coerceAtMost(loadout.maxWeapon())
        }
        s.awaitingAugment = false
        tally.augments++
        val next = slots.indexOfFirst { it.awaitingAugment }
        if (next >= 0) {
            augmentSlot = next
        } else {
            pendingAugment = false
            augmentSlot = 0
        }
        // Finishing a system is the loudest moment in a run's progression, so
        // it gets its own banner and a heavier flash than an ordinary level.
        val mastery = Aug.masteryName(c.id, loadout.lvl[c.id], loadout.branch.getOrElse(c.id) { 0 })
        val capstone = mastery.isNotEmpty() && c.branchPick == 0
        banner = when {
            capstone -> "MASTERED"
            c.branchPick != 0 -> "EVOLVED"
            else -> "AUGMENT ONLINE"
        }
        bannerSub = if (capstone) mastery else label
        bannerT = if (capstone) 2.6f else 1.8f
        fx.shockwave(player.x, player.y, if (capstone) 300f else 190f, c.color, if (capstone) 0.9f else 0.7f, 4f)
        fx.burst(player.x, player.y, if (capstone) 48 else 30, c.color, if (capstone) 420f else 300f, 2.8f, 0.8f, true)
        fx.flash(c.color, if (capstone) 0.5f else 0.3f)
        if (capstone) fx.shake(0.5f)
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
        b.pierce = 0; b.hitCd = 0f; b.homing = false; b.turn = 0f; b.target = -1; b.splash = 0f; b.reseed = 0
        b.fuse = 0f; b.shrapnel = 0; b.reburst = 0
        b.dwell = 0f; b.fracture = 0; b.shardDamage = 0; b.shardHoming = false; b.burn = 0f; b.reshatter = 0
        return b
    }

    private fun fireAngle(
        x: Float, y: Float, angle: Float, speed: Float, r: Float, damage: Int,
        hostile: Boolean, color: Int, style: Int
    ): Bullet = fire(x, y, cos(angle) * speed, sin(angle) * speed, r, damage, hostile, color, style)

    internal fun allyBullet(x: Float, y: Float, vx: Float, vy: Float, r: Float, damage: Int, color: Int, style: Int): Bullet {
        val b = fire(x, y, vx, vy, r, damage, false, color, style)
        b.pierce = 0; b.hitCd = 0f; b.homing = false; b.turn = 0f; b.target = -1; b.splash = 0f; b.reseed = 0
        b.fuse = 0f; b.shrapnel = 0; b.reburst = 0
        b.dwell = 0f; b.fracture = 0; b.shardDamage = 0; b.shardHoming = false; b.burn = 0f; b.reshatter = 0
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
            // DOUBLE FUSE: each fragment is itself a small shell with one
            // burst left. reburst only ever counts down, so it terminates.
            if (b.reburst > 0) {
                val f = flakShell(
                    b.x, b.y, cos(a) * 470f, sin(a) * 470f, 3.4f,
                    b.damage, 4, 0.34f, 0f
                )
                f.reburst = b.reburst - 1
                f.color = Palette.AMBER
            } else {
                allyBullet(b.x, b.y, cos(a) * 560f, sin(a) * 560f, 3.4f, b.damage, Palette.AMBER, 1)
            }
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

    /** FRACTURE: a landed shot throws its shards outward. */
    internal fun shatter(b: Bullet) {
        val n = b.fracture
        if (n <= 0) return
        val off = rnd(TAU)
        val dmg = b.shardDamage.coerceAtLeast(1)
        for (i in 0 until n) {
            val a = off + i * TAU / n + rnd(-0.12f, 0.12f)
            val sh = allyBullet(b.x, b.y, cos(a) * 430f, sin(a) * 430f, 2.8f, dmg, Palette.ROSE, 0)
            if (b.shardHoming) {
                sh.homing = true
                sh.turn = 6f
                sh.life = 1.2f
            } else {
                sh.life = 0.42f
            }
            // a mastered shard carries one more break in it. reshatter only
            // ever counts down, so the cascade is finite.
            if (b.reshatter > 0) {
                sh.fracture = if (b.shardHoming) 2 else 3
                sh.shardDamage = (dmg / 3).coerceAtLeast(1)
                sh.shardHoming = b.shardHoming
                sh.reshatter = b.reshatter - 1
                sh.life = maxOf(sh.life, 0.7f)
            }
        }
        fx.burst(b.x, b.y, 5, Palette.ROSE, 230f, 2f, 0.26f)
        b.active = false
    }

    /** STASIS: a shot held long enough dissolves and pays out. */
    internal fun bankBullet(b: Bullet) {
        b.active = false
        val paid = (30 * multiplier * scoreMultiplier()).toInt()
        score += paid
        fx.popText(b.x, b.y, "+$paid", Palette.WHITE, 13f, 0.5f)
        fx.burst(b.x, b.y, 4, Palette.WHITE, 150f, 1.8f, 0.3f)
    }

    /** BACKLASH: a shot caught in the time field turns and flies home. */
    internal fun reflectBullet(b: Bullet, damage: Int) {
        b.hostile = false
        b.dwell = 0f
        b.grazed = true                 // it already paid out on the way in
        b.color = Palette.SKY
        b.damage = damage
        b.life = 3f
        val speed = len(b.vx, b.vy).coerceAtLeast(240f) * 1.7f
        val ti = arsenal.nearestEnemy(this, b.x, b.y)
        if (ti >= 0) {
            val e = enemies[ti]
            val a = atan2(e.y - b.y, e.x - b.x)
            b.vx = cos(a) * speed
            b.vy = sin(a) * speed
        } else {
            b.vx = -b.vx * 1.7f
            b.vy = -b.vy * 1.7f
        }
        fx.burst(b.x, b.y, 5, Palette.SKY, 200f, 2f, 0.3f)
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

    /** BLOOM pools drag loose pickups towards them. */
    internal fun magnetiseWithin(x: Float, y: Float, r: Float, dt: Float) {
        for (u in pickups) {
            if (!u.active) continue
            val dx = x - u.x
            val dy = y - u.y
            val d = len(dx, dy)
            if (d > r || d < 0.001f) continue
            val pull = (1f - d / r) * 480f
            u.vx += dx / d * pull * dt
            u.vy += dy / d * pull * dt
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

    /** A mastered REPULSOR stops shoving and sends the fire home instead. */
    internal fun reflectBulletsWithin(x: Float, y: Float, r: Float) {
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            if (len(b.x - x, b.y - y) <= r + b.r) {
                reflectBullet(b, 8 + wave / 2)
                fx.burst(b.x, b.y, 3, Palette.MAGENTA, 150f, 1.8f, 0.3f)
            }
        }
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
        // The opening curve is unchanged; past the point where it used to stop,
        // both dials keep creeping so a deep wave is genuinely more dangerous
        // and not merely tougher to chew through.
        val speedMul =
            (clamp(0.9f + (wave - 1) * 0.05f, 0.9f, 1.95f) +
                (wave - 22).coerceAtLeast(0) * 0.006f).coerceAtMost(2.5f) * overloadSpeed
        // >1 means slower firing: the first waves deliberately shoot less
        val rateMul =
            (clamp(1.28f - (wave - 1) * 0.048f, 0.42f, 1.28f) -
                (wave - 19).coerceAtLeast(0) * 0.003f).coerceAtLeast(0.24f) * overloadRate
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
        e.burn = 0f
        e.burnDps = 0f
        e.slow = 1f
        e.dilated = 0f
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
            EK.STALKER -> {
                e.r = 16f; e.hp = 8f * hpMul; e.vy = 132f * speedMul
                e.holdY = rnd(h * 0.10f, h * 0.22f)
                e.fireEvery = 1.5f * rateMul; e.fireT = rnd(0.6f, 1.4f)
                e.score = 260; e.color = Palette.RED
                e.dropBias = 1.4f
            }
            EK.HOWLER -> {
                e.r = 20f; e.hp = 14f * hpMul; e.vy = 82f * speedMul
                e.holdY = rnd(h * 0.14f, h * 0.28f)
                e.amp = rnd(50f, 110f); e.freq = rnd(0.6f, 1.1f)
                e.fireEvery = 3.4f * rateMul; e.fireT = rnd(1.6f, 2.6f)
                e.score = 340; e.color = Palette.AMBER
                e.dropBias = 2.0f
            }
            EK.SEEDER -> {
                e.r = 19f; e.hp = 12f * hpMul; e.vy = 86f * speedMul
                e.holdY = rnd(h * 0.12f, h * 0.26f)
                e.vx = if (chance(0.5f)) 52f else -52f
                e.fireEvery = 2.4f * rateMul; e.fireT = rnd(0.8f, 1.8f)
                e.score = 300; e.color = Palette.LIME
                e.dropBias = 1.8f
            }
            EK.POD -> {
                e.r = 9f; e.hp = 2f * hpMul.coerceAtMost(4f); e.vy = 58f
                e.stateT = 2f
                e.score = 60; e.color = Palette.LIME
                e.dropBias = 0.4f
            }
            EK.MENDER -> {
                e.r = 15f; e.hp = 10f * hpMul; e.vy = 104f * speedMul
                e.holdY = rnd(h * 0.14f, h * 0.30f)
                e.vx = if (chance(0.5f)) 64f else -64f
                e.score = 380; e.color = Palette.LIME
                e.dropBias = 2.4f
            }
            EK.BOSS -> {
                val theme = theme(wave)
                val bossType = bossTypeFor(wave)
                e.bossType = bossType
                val typeMul = when (bossType) {
                    BT.WARDEN -> 0.92f
                    BT.HIVE -> 0.95f
                    BT.FORGE -> 1.3f
                    BT.NULLIFIER -> 1.15f
                    else -> 1f
                }
                e.r = if (bossType == BT.FORGE) 58f else 52f
                // Bosses are the wall of the run: a big flat base, a steep linear
                // climb and a capped quadratic on top so late fights stay fights.
                // The cap lands earlier than it used to: past wave 45 the extra
                // health only turned a fight into a two-minute chore, and the
                // curve below wave 45 is untouched.
                val quad = minOf(wave.toFloat() * wave, 2000f) * 2.6f
                e.hp = (185f + wave * 70f + quad) * typeMul * (1f + Levels.tier(wave) * 0.4f)
                e.vy = 90f
                e.holdY = h * 0.20f
                e.fireEvery = 1f
                e.score = 3000 + wave * 600
                e.color = theme.accent
                e.amp = w * 0.26f
                e.freq = 0.55f
                e.dropBias = 6f
                boss = e
            }
        }

        // Elites appear once the run is deep enough: tougher, worth more.
        if (kind != EK.BOSS && kind != EK.MINE && wave >= 8 && chance(clamp(0.03f + wave * 0.012f, 0f, 0.72f))) {
            promoteElite(e)
        }
        e.maxHp = e.hp
        return e
    }

    /** Turns one enemy into an elite, whether by chance or because a plan said so. */
    private fun promoteElite(e: Enemy) {
        if (e.kind == EK.BOSS || e.kind == EK.MINE || e.elite) return
        e.elite = true
        e.hp *= 1.6f + clamp(wave * 0.02f, 0f, 0.8f)
        e.maxHp = e.hp
        e.r *= 1.12f
        e.score = (e.score * 2.2f).toInt()
        e.dropBias *= 1.8f
        e.fireEvery *= 0.8f
    }

    // -------------------------------------------------------- wave director

    private fun buildWave(n: Int) {
        script.clear()
        scriptIdx = 0
        waveT = 0f
        val sector = theme(n)
        val tier = Levels.tier(n)
        // Health used to be the only dial still climbing past wave 35, which
        // turned the late game into a grind rather than a fight: every other
        // threat had already hit its cap. The quadratic is now much gentler and
        // the danger dials below carry the difficulty instead.
        val hpMul = (1f + (n - 1) * 0.26f + (n - 1) * (n - 1) * 0.0075f + tier * 0.9f) * overloadHp

        if (Levels.isBossWave(n)) {
            script.add(Spawn(1.9f, EK.BOSS, w * 0.5f, -110f, hpMul))
            return
        }

        val waveInSector = (n - 1) % Levels.WAVES_PER_LEVEL
        // grows with the run overall, with a small breather at each sector start
        val budget = 2 + waveInSector.coerceAtMost(3) + (n / 6).coerceAtMost(4) + tier + overload
        val scale = 1 + n / 6
        lastArch = Waves.plan(n, sector.roster, budget, scale, overload, lastArch, planBuf)

        // Groups used to be laid out strictly single file however deep the run
        // got, so a finished build deleted each trickle and then waited: at
        // wave 60 the screen sat nearly empty for two minutes. The schedule
        // compresses with depth instead, so the late game is a packed screen
        // rather than dead air - which is what a killscreen should feel like.
        val pace = clamp(1f - (n - 20) * 0.014f - overload * 0.06f, 0.28f, 1f)
        var t = 0.4f
        for (p in planBuf) {
            for (i in 0 until p.count) {
                script.add(
                    Spawn(
                        t + Waves.spawnDelay(p, i) * pace, p.kind, Waves.spawnX(p, i, w),
                        -40f - rnd(0f, 30f), hpMul, p.elite
                    )
                )
            }
            t += Waves.hold(p) * pace
        }
        script.sortBy { it.time }
    }

    /** Test hook: drop the run straight into a deep wave to measure pacing. */
    internal fun jumpToWave(n: Int, overloadTier: Int) {
        overload = overloadTier
        levelsCleared = overloadTier
        startWave(n)
        overloadAlarm = 0f
        bannerT = 0f
    }

    private fun startWave(n: Int) {
        wave = n
        buildWave(n)
        awaitingNextWave = false
        val theme = theme(n)
        when {
            // A finished level is a milestone, not a stopping point - the run
            // rolls straight on into the next theme.
            // A cleared sector is not a finish line, it is the point where the
            // grid stops holding back. Everything that can be sped up is, for
            // the rest of the run, and the alarm makes sure you know it.
            Levels.isLevelStart(n) && n > 1 -> {
                val cleared = Levels.number(n) - 1
                val bonus = (6000 * cleared * scoreMultiplier()).toInt()
                score += bonus
                levelsCleared = cleared
                overload = cleared
                overloadAlarm = ALARM_TIME
                banner = "SECTOR $cleared CLEARED"
                bannerSub = "OVERLOAD x$overload   +$bonus"
                bannerT = 4.2f
                fx.flash(Palette.RED, 0.75f)
                fx.shockwave(player.x, player.y, w * 1.8f, Palette.RED, 1.1f, 7f)
                fx.shockwave(player.x, player.y, w * 1.2f, Palette.WHITE, 0.8f, 4f)
                fx.shake(0.9f)
                fx.freeze(0.16f)
                sound?.sfx(Sfx.ALARM)
                haptics.heavy()
            }
            Levels.isBossWave(n) -> {
                banner = "WARNING"
                bannerSub = BT.names[bossTypeFor(n)]
                bannerT = 2.4f
                fx.flash(Palette.RED, 0.3f)
                sound?.sfx(Sfx.WARN)
                haptics.medium()
            }
            Levels.isLevelStart(n) -> {
                banner = theme.name
                bannerSub = theme.subtitle
                bannerT = 2.4f
                fx.flash(theme.accent, 0.22f)
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
        if (overloadAlarm > 0f) {
            overloadAlarm -= dt
            // a couple of extra klaxon beats while the banner is up
            if (chance(dt * 3.4f)) {
                fx.flash(Palette.RED, 0.30f)
                fx.shake(0.22f)
            }
        }

        updateWaveDirector(dt)
        for (s in slots) if (s.joined) updatePlayer(s, dt)
        for (s in slots) if (s.joined) s.arsenal.update(dt, this)
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
                if (augmentDelay <= 0f) openDraft()
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
            val e = spawnEnemy(s.kind, s.x, s.y, s.hpMul)
            if (s.elite && e != null && !e.elite) promoteElite(e)
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
            // the breather between waves shrinks with every overload tier
            waveClearT = clamp(1.4f - 0.25f * overload, 0.5f, 1.4f)
            augmentDelay = clamp(1.15f - 0.2f * overload, 0.5f, 1.15f)
            boss = null
            bossHpRatio = 0f
        }
    }

    private fun updatePlayer(s: PlayerSlot, dt: Float) {
        val player = s.player
        val loadout = s.loadout
        val ship = s.ship
        if (!player.alive) {
            if (player.lives <= 0) return          // out for good; the partner plays on
            player.respawnT -= dt
            if (player.respawnT <= 0f) {
                player.alive = true
                player.invuln = 1.5f + loadout.mercyBonus() + meta.mercyBonus
                s.home(w, h)
                fx.shockwave(player.x, player.y, 90f, Palette.CYAN, 0.5f, 2.5f)
            }
            return
        }

        val px = player.x
        val handling = handlingOf(s)
        player.x = approach(player.x, player.tx, handling, dt)
        player.y = approach(player.y, player.ty, handling, dt)
        val vx = (player.x - px) / dt.coerceAtLeast(0.0001f)
        player.bank = approach(player.bank, clamp(vx / 420f, -1f, 1f), 0.18f, dt)
        player.thrust = approach(player.thrust, clamp(abs(vx) / 300f, 0f, 1f), 0.2f, dt)

        if (player.invuln > 0f) player.invuln -= dt
        if (player.revenge > 0f) player.revenge -= dt
        if (player.cascadeT > 0f) {
            player.cascadeT -= dt
            if (player.cascadeT <= 0f) player.cascade = 0
        }
        val regen = loadout.shieldRegen()
        if (regen > 0f && player.shield < loadout.maxShield()) {
            player.regenT -= dt
            if (player.regenT <= 0f) {
                player.shield++
                player.regenT = regen
                fx.shockwave(player.x, player.y, 70f, Palette.LIME, 0.4f, 2f)
                fx.popText(player.x, player.y - 46f, "SHIELD", Palette.LIME, 16f)
                sound?.sfx(Sfx.POWERUP)
            }
        } else {
            player.regenT = regen
        }
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
            playerFire(s)
            var base = when (player.weapon) {
                1 -> 0.155f; 2 -> 0.145f; 3 -> 0.135f; 4 -> 0.125f; else -> 0.115f
            }
            base *= loadout.fireIntervalMul() * ship.fireMul
            if (loadout.branch[Aug.SPREAD] == Aug.A) base *= 0.88f
            if (player.revenge > 0f) base /= loadout.revengeMul()
            base *= clamp(1f - loadout.cascadeStep() * player.cascade, 0.4f, 1f)
            player.fireT = base * (if (player.odTime > 0f) 0.55f else 1f)
        }
    }

    private fun playerShot(s: PlayerSlot, offX: Float, offY: Float, angleDeg: Float, r: Float, dmg: Int): Bullet {
        val player = s.player
        val loadout = s.loadout
        val od = player.odTime > 0f
        val a = (-90f + angleDeg) * DEG
        val speed = 1000f * loadout.bulletSpeedMul()
        val color = if (od) Palette.AMBER else Palette.CYAN
        var d = dmg.coerceAtLeast(1)
        if (loadout.critChance() > 0f && chance(loadout.critChance())) d *= 2
        val b = fireAngle(player.x + offX, player.y + offY, a, speed, r, d, false, color, 1)
        b.pierce += loadout.extraPierce()
        b.burn = loadout.burnDps()
        val fl = loadout.lvl[Aug.FRACTURE]
        if (fl > 0) {
            when (loadout.branch[Aug.FRACTURE]) {
                Aug.A -> {                                   // SHATTER: a wall of glass
                    b.fracture = 4 + fl / 2
                    b.shardDamage = 1 + fl / 2
                }
                Aug.B -> {                                   // RUPTURE: few, heavy, seeking
                    b.fracture = 2
                    b.shardDamage = 1 + (fl * 3) / 4
                    b.shardHoming = true
                }
                else -> {
                    b.fracture = 2
                    b.shardDamage = 1 + fl / 2
                }
            }
            // CHAIN BREAK / SPLINTER: mastered, the shards shatter once more
            if (loadout.mastered(Aug.FRACTURE)) b.reshatter = 1
        }
        return b
    }

    private fun playerFire(s: PlayerSlot) {
        val player = s.player
        val loadout = s.loadout
        val ship = s.ship
        val od = player.odTime > 0f
        var d = (if (od) 4 else 2) + loadout.damageBonus() + ship.damageBonus
        // MOMENTUM rewards flying hard, FOCUS rewards holding still, and
        // VENGEANCE rewards having just been hit
        val still = clamp(1f - player.thrust * 2.5f, 0f, 1f)
        val fury = (1f + loadout.momentumBonus() * player.thrust) *
            (1f + loadout.focusBonus() * still) *
            (if (player.revenge > 0f) loadout.revengeMul() else 1f)
        if (fury > 1f) d = (d * fury + 0.5f).toInt()
        when (player.weapon) {
            1 -> playerShot(s, 0f, -14f, 0f, 4.4f, d)
            2 -> { playerShot(s, -7f, -12f, 0f, 4f, d); playerShot(s, 7f, -12f, 0f, 4f, d) }
            3 -> { playerShot(s, -9f, -10f, 0f, 3.8f, d); playerShot(s, 9f, -10f, 0f, 3.8f, d); playerShot(s, 0f, -16f, 0f, 4.6f, d) }
            4 -> {
                playerShot(s, -10f, -10f, -7f, 3.8f, d); playerShot(s, 10f, -10f, 7f, 3.8f, d)
                playerShot(s, 0f, -16f, 0f, 4.6f, d)
            }
            else -> {
                playerShot(s, -11f, -9f, -9f, 3.8f, d); playerShot(s, 11f, -9f, 9f, 3.8f, d)
                playerShot(s, -5f, -14f, -3f, 4.2f, d); playerShot(s, 5f, -14f, 3f, 4.2f, d)
                playerShot(s, 0f, -17f, 0f, 4.8f, d)
            }
        }
        if (od) {
            playerShot(s, -17f, -4f, -34f, 3.6f, d)
            playerShot(s, 17f, -4f, 34f, 3.6f, d)
        }
        spreadFire(s, d)
        sound?.sfx(Sfx.SHOOT)
        fx.cone(player.x, player.y - 14f, 2, -TAU * 0.25f, 0.5f, if (od) Palette.AMBER else Palette.CYAN, 190f, 1.9f, 0.14f)
    }

    private fun spreadFire(s: PlayerSlot, d: Int) {
        val loadout = s.loadout
        val lvl = loadout.lvl[Aug.SPREAD]
        if (lvl <= 0) return
        when (loadout.branch[Aug.SPREAD]) {
            Aug.A -> { // FAN: a wide, fast curtain
                val extra = lvl - 3
                val n = 6 + extra
                // SATURATION: mastered, every shot in the curtain punches through
                val saturated = loadout.mastered(Aug.SPREAD)
                for (i in 0 until n) {
                    val ang = -66f + i * (132f / (n - 1))
                    val b = playerShot(s, 0f, -8f, ang, if (saturated) 3.9f else 3.3f, (d * 0.46f).toInt())
                    if (saturated) b.pierce += 1
                }
            }
            Aug.B -> { // PHALANX: heavy piercing bolts
                val extra = lvl - 3
                // BREACH: mastered, the bolts run the whole rank and grow
                val breach = loadout.mastered(Aug.SPREAD)
                for (i in 0 until 4) {
                    val off = (i - 1.5f) * 14f
                    val b = playerShot(s, off, -10f, 0f, if (breach) 7f else 5.6f, (d * 1.1f).toInt() + extra)
                    b.pierce = 2 + extra + (if (breach) 4 else 0)
                }
            }
            else -> {
                val angles = when (lvl) {
                    1 -> floatArrayOf(-22f, 22f)
                    2 -> floatArrayOf(-24f, 24f, -42f, 42f)
                    else -> floatArrayOf(-20f, 20f, -38f, 38f)
                }
                for (a in angles) playerShot(s, 0f, -8f, a, 3.6f, (d * 0.62f).toInt())
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
        clamp(155f + wave * 11f + Levels.tier(wave) * 45f, 155f, 470f) * overloadBullet

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
    /** Movement responsiveness for a pilot - shared with the client so its
     *  prediction uses the same number the host does. */
    internal fun handlingOf(s: PlayerSlot): Float =
        clamp(s.loadout.handling() * s.ship.handlingMul, 0.3f, 0.86f)

    internal fun pickupSnapshotCount(): Int = pickups.count { it.active }

    internal fun writePickups(o: java.io.DataOutputStream) {
        for (u in pickups) {
            if (!u.active) continue
            o.writeShort(Proto.packPos(u.x).toInt())
            o.writeShort(Proto.packPos(u.y).toInt())
            o.writeByte(u.kind)
            o.writeByte((u.t * 8f).toInt().coerceIn(0, 127))
        }
    }

    /** True once an enemy has held the field long enough that it is leaving. */
    internal fun retiring(e: Enemy): Boolean = e.kind != EK.BOSS && e.t > HOLD_LIMIT

    internal fun countActive(kind: Int): Int = enemies.count { it.active && it.kind == kind }

    internal fun spawnMinion(kind: Int, x: Float, y: Float): Enemy? =
        spawnEnemy(kind, x, y, 1f + (wave - 1) * 0.3f)

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (!e.active) continue
            e.t += dt
            if (e.hitFlash > 0f) e.hitFlash -= dt
            if (e.dilated > 0f) e.dilated -= dt

            if (e.telegraph > 0f && e.kind != EK.LANCER) e.telegraph = 0f

            // Nothing but a boss may hold the field forever. A wave only ends
            // once the screen is clear, so a hovering enemy the player cannot
            // finish off - a carrier, a minelayer, a lancer - would otherwise
            // soft-lock the run with no way out but dying. Turrets and pylons
            // already retire on their own; this catches every other kind.
            if (retiring(e)) {
                val push = (40f + (e.t - HOLD_LIMIT) * 60f) * dt
                e.y += push
                // an orbiter rebuilds its position from a stored centre every
                // frame, so the centre has to move or the push is overwritten
                if (e.kind == EK.ORBITER) e.aux2 += push
            }
            if (e.burn > 0f) {
                e.burn -= dt
                if (chance(dt * 14f)) {
                    fx.burst(e.x + rnd(-e.r, e.r), e.y + rnd(-e.r, e.r), 1, Palette.RED, 60f, 1.6f, 0.4f)
                }
                hit(e, e.burnDps * dt, e.x, e.y, false)
                if (!e.active) continue
                if (e.burn <= 0f) e.burnDps = 0f
            }
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
            // magnetised towards whichever pilot is closest
            for (s in slots) {
                if (!s.joined || !s.player.alive) continue
                val dx = s.player.x - u.x
                val dy = s.player.y - u.y
                val d = len(dx, dy)
                val magnet = s.loadout.magnetRadius() * s.ship.magnetMul
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
        val starving = player.weapon < loadout.maxWeapon() && killsSinceWeapon >= 14 + player.weapon * 6
        val luck = meta.dropMul
        val weaponChance = 0.042f * bias * luck * clamp(1f - player.weapon * 0.13f, 0.3f, 1f)
        val lifeChance = if (fromTough) 0.012f * bias * luck else 0f
        val shieldChance = if (player.shield >= loadout.maxShield()) 0f else 0.018f * bias * luck
        // Cumulative windows - each kind needs its own slice of the roll, or the
        // later branches are simply unreachable.
        val wEnd = weaponChance
        val lEnd = wEnd + lifeChance
        val sEnd = lEnd + shieldChance
        val gEnd = sEnd + 0.34f * (1f + loadout.gemBonus())
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
                    if (b.burn > 0f && e.active) {
                        e.burn = maxOf(e.burn, 2.6f)
                        e.burnDps = maxOf(e.burnDps, b.burn)
                    }
                    if (b.shrapnel > 0) {
                        hit(e, b.damage.toFloat(), b.x, b.y, true)
                        burstShell(b)
                        break
                    }
                    if (b.fracture > 0) {
                        hit(e, b.damage.toFloat(), b.x, b.y, true)
                        shatter(b)
                        break
                    }
                    if (b.splash > 0f) detonate(b)
                    val before = e.hp
                    hit(e, b.damage.toFloat(), b.x, b.y, true)
                    // SWARM LOGIC: a seeker that finishes something hands its
                    // place to a fresh one. reseed only counts down, so the
                    // chain is finite however many kills it strings together.
                    if (b.reseed > 0 && before > 0f && !e.active) {
                        val m = missile(
                            b.x, b.y, rnd(-240f, 240f), rnd(-340f, -160f),
                            b.r, b.damage, b.color
                        )
                        m.turn = b.turn
                        m.reseed = b.reseed - 1
                    }
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

        for (s in slots) {
            if (!s.joined) continue
            if (collidePlayer(s)) return
        }
    }

    /** Returns true when the pilot was hit, so the caller stops this frame. */
    private fun collidePlayer(s: PlayerSlot): Boolean {
        val player = s.player
        val loadout = s.loadout
        val ship = s.ship
        if (!player.alive) return false
        val invulnerable = player.invuln > 0f || player.odTime > 0f

        // hostile shots vs pilot (small hitbox, generous graze)
        for (b in bullets) {
            if (!b.active || !b.hostile) continue
            val dx = b.x - player.x
            val dy = b.y - player.y
            val d2 = dx * dx + dy * dy
            val hitR = player.hitR + b.r
            if (d2 <= hitR * hitR) {
                b.active = false
                if (!invulnerable) { hurtPlayer(s); return true }
                fx.burst(b.x, b.y, 4, Palette.AMBER, 150f, 2f, 0.3f)
            } else if (!b.grazed && d2 <= grazeRadius * grazeRadius) {
                b.grazed = true
                tally.grazes++
                player.overdrive = clamp(
                    player.overdrive + loadout.grazeCharge() * ship.grazeMul * loadout.overdriveCharge(),
                    0f, 1f
                )
                score += (15 * scoreMultiplier()).toInt()
                fx.cone(b.x, b.y, 2, atan2(-dy, -dx), 0.8f, Palette.CYAN, 140f, 1.6f, 0.25f)
            }
        }
        // pylon tethers
        for (i in enemies.indices) {
            val a = enemies[i]
            if (!a.active || a.kind != EK.PYLON || a.link <= i) continue
            val b = enemies[a.link]
            // the link is an index, and the slot may have been recycled by a
            // different pylon since; only a mutual link is a real pair
            if (!b.active || b.kind != EK.PYLON || b.link != i) continue
            if (a.state != 2 || b.state != 2) continue
            if (player.invuln <= 0f && player.odTime <= 0f &&
                distToSegment(player.x, player.y, a.x, a.y, b.x, b.y) < 9f + player.hitR
            ) {
                hurtPlayer(s)
                return true
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
                    hurtPlayer(s)
                    return true
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
                collect(s, u)
            }
        }
        return false
    }

    private fun collect(s: PlayerSlot, u: PowerUp) {
        val player = s.player
        val loadout = s.loadout
        when (u.kind) {
            PK.WEAPON -> {
                if (player.weapon < loadout.maxWeapon()) {
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
                player.shieldHits = loadout.shieldDepth()
                fx.popText(player.x, player.y - 46f, "SHIELD", Palette.LIME, 20f)
                fx.shockwave(player.x, player.y, 70f, Palette.LIME, 0.4f, 2.5f)
            }
            PK.LIFE -> {
                player.lives = (player.lives + 1).coerceAtMost(4)
                fx.popText(player.x, player.y - 46f, "1UP", Palette.ROSE, 24f)
                fx.flash(Palette.ROSE, 0.25f)
            }
            else -> {
                tally.gems++
                val v = (200 * multiplier * scoreMultiplier() * meta.gemMul * (1f + loadout.gemBonus() * 0.5f)).toInt()
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

    internal fun hit(e: Enemy, dmgRaw: Float, hx: Float, hy: Float, spark: Boolean = true) {
        val dmg = dmgRaw * meta.damageMul
        // Bosses are meant to be a wall, not a wall you can be stuck against:
        // past a minute the fight starts giving, so a weak build still ends it.
        val impatience =
            if (e.kind == EK.BOSS && e.t > BOSS_PATIENCE) 1f + minOf((e.t - BOSS_PATIENCE) * 0.03f, 2.2f)
            else 1f
        val dilated = if (e.dilated > 0f) 1.8f else 1f
        e.hp -= dmg * impatience * dilated
        e.hitFlash = 0.12f
        if (spark) fx.cone(hx, hy, 3, -TAU * 0.25f + rnd(-0.6f, 0.6f), 0.9f, Palette.WHITE, 170f, 1.8f, 0.22f)
        if (e.hp > 0f) return
        killEnemy(e)
    }

    /** Damage everything inside a radius, without the warhead's fanfare. */
    internal fun splashDamage(x: Float, y: Float, radius: Float, dmg: Float) {
        for (e in enemies) {
            if (!e.active) continue
            if (len(e.x - x, e.y - y) <= radius + e.r) hit(e, dmg, e.x, e.y, false)
        }
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

    /**
     * How many kills deep the current chain reaction is. AFTERSHOCK damages
     * neighbours from inside a kill, and those neighbours can die too, so
     * without a ceiling a dense wave recurses until the stack gives out.
     */
    private var killDepth = 0

    /**
     * A mine or a pod that runs its own fuse out still has to blow up, but it
     * was not shot down: paying score, combo and weapon progress for it would
     * turn parking next to a seeder into free progression.
     */
    internal fun expire(e: Enemy) = killEnemy(e, credited = false)

    private fun killEnemy(e: Enemy, credited: Boolean = true) {
        e.active = false
        if (e.kind == EK.POD) {
            // it always blooms; killing it early only decides where
            val n = 8 + (wave / 8).coerceAtMost(6)
            val off = rnd(TAU)
            for (i in 0 until n) {
                hostileShot(e.x, e.y, off + i * TAU / n, enemyBulletSpeed() * 0.55f, 4.6f, Palette.LIME, 0)
            }
            fx.shockwave(e.x, e.y, 62f, Palette.LIME, 0.4f, 3f)
        }
        if (e.kind == EK.PYLON && e.link >= 0) {
            val partner = enemies[e.link]
            if (partner.link == enemies.indexOf(e)) partner.link = -1
            e.link = -1
        }
        if (credited) {
            kills++
            killsSinceWeapon++
            combo++
            comboT = COMBO_WINDOW
            if (combo > maxCombo) maxCombo = combo
            player.overdrive = clamp(player.overdrive + 0.005f, 0f, 1f)
        }

        val gained = if (credited) (e.score * multiplier * scoreMultiplier()).toInt() else 0
        score += gained

        if (credited && e.kind != EK.POD && e.kind != EK.MINE) {
            if (loadout.has(Aug.HARVEST)) arsenal.spillPool(this, e.x, e.y, e.r)
            val boom = loadout.aftershockChance()
            if (boom > 0f && killDepth < AFTERSHOCK_CHAIN && chance(boom)) {
                val r = loadout.aftershockRadius()
                fx.shockwave(e.x, e.y, r, Palette.RED, 0.45f, 3f)
                fx.burst(e.x, e.y, 12, Palette.RED, 260f, 2.4f, 0.5f, true)
                killDepth++
                splashDamage(e.x, e.y, r, 8f + wave * 1.4f + loadout.damageBonus() * 3f)
                killDepth--
            }
            // CASCADE: a kill briefly speeds the next shot, and it stacks
            if (loadout.cascadeStep() > 0f) {
                player.cascade = (player.cascade + 1).coerceAtMost(loadout.cascadeMax())
                player.cascadeT = 2.2f
            }
        }

        if (e.kind == EK.BOSS) {
            tally.bosses++
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
            if (credited) {
                fx.popText(e.x, e.y - 12f, "+$gained", if (combo > 8) Palette.AMBER else Palette.WHITE, 15f, 0.6f)
                if (combo > 0 && combo % 10 == 0) {
                    fx.popText(player.x, player.y - 76f, "COMBO x${combo}", Palette.MAGENTA, 22f, 1.1f)
                    haptics.light()
                }
                dropLoot(e.x, e.y, e.dropBias, e.elite || e.kind == EK.TURRET || e.kind == EK.MINELAYER)
            }
            sound?.sfx(Sfx.EXPLODE)
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

    private fun hurtPlayer(s: PlayerSlot) {
        val player = s.player
        val loadout = s.loadout
        player.revenge = loadout.revengeSeconds()
        // PHANTOM: the mirrored ghost steps in front of one shot
        if (s.arsenal.mirrorGuard(this)) {
            player.invuln = 0.9f + loadout.mercyBonus() + meta.mercyBonus
            fx.shockwave(player.x, player.y, 130f, Palette.VIOLET, 0.5f, 3f)
            fx.popText(player.x, player.y - 50f, "PHANTOM", Palette.VIOLET, 18f)
            sound?.sfx(Sfx.HURT)
            return
        }
        if (player.shield > 0 && player.shieldHits > 1) {
            // BULWARK: this pip has another hit left in it
            player.shieldHits--
            player.invuln = 1.0f + loadout.mercyBonus() + meta.mercyBonus
            fx.shockwave(player.x, player.y, 90f, Palette.LIME, 0.4f, 2.5f)
            fx.popText(player.x, player.y - 50f, "SHIELD HOLDS", Palette.LIME, 16f)
            sound?.sfx(Sfx.HURT)
            haptics.light()
            return
        }
        if (player.shield > 0) {
            player.shield--
            player.shieldHits = loadout.shieldDepth()
            player.invuln = 1.4f + loadout.mercyBonus() + meta.mercyBonus
            fx.shockwave(player.x, player.y, 110f, Palette.LIME, 0.5f, 3f)
            fx.burst(player.x, player.y, 20, Palette.LIME, 280f, 2.6f, 0.5f, true)
            fx.flash(Palette.LIME, 0.22f)
            fx.shake(0.3f)
            fx.popText(player.x, player.y - 50f, "SHIELD DOWN", Palette.LIME, 18f)
            sound?.sfx(Sfx.HURT)
            haptics.medium()
            return
        }

        if (player.lives <= 1 && revives > 0) {
            // EMERGENCY CORE: one comeback per run instead of the last life
            revives--
            player.invuln = 3f + loadout.mercyBonus() + meta.mercyBonus
            player.shield = loadout.maxShield()
            clearHostileBullets(false)
            fx.shockwave(player.x, player.y, w * 1.2f, Palette.WHITE, 0.7f, 5f)
            fx.flash(Palette.WHITE, 0.6f)
            fx.shake(0.5f)
            fx.popText(player.x, player.y - 60f, "EMERGENCY CORE", Palette.WHITE, 22f, 1.4f)
            sound?.sfx(Sfx.OVERDRIVE)
            haptics.heavy()
            combo = 0
            comboT = 0f
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
            player.respawnT = 999f
            if (slots.none { it.joined && it.player.lives > 0 }) {
                gameOver = true
            } else {
                fx.popText(player.x, player.y - 60f, "PILOT DOWN", Palette.RED, 20f, 1.4f)
                banner = "PILOT DOWN"
                bannerSub = "PARTNER STILL FLYING"
                bannerT = 1.6f
            }
        }
    }

    // ----------------------------------------------------------------- draw

    fun draw(c: Canvas) {
        for (u in pickups) if (u.active) Draw.powerUp(c, u)
        for (e in enemies) if (e.active && e.kind == EK.LANCER) Draw.lancerTelegraph(c, e, h)
        for (e in enemies) {
            if (!e.active) continue
            when (e.kind) {
                EK.HOWLER -> Draw.howlerTelegraph(c, e, time)
                EK.POD -> Draw.podTelegraph(c, e, time)
                EK.MENDER -> {
                    val t = e.link
                    if (t in enemies.indices && enemies[t].active) Draw.menderBeam(c, e, enemies[t], time)
                }
            }
        }
        for (i in enemies.indices) {
            val a = enemies[i]
            if (!a.active || a.kind != EK.PYLON || a.link <= i) continue
            val b = enemies[a.link]
            // the link is an index, and the slot may have been recycled by a
            // different pylon since; only a mutual link is a real pair
            if (!b.active || b.kind != EK.PYLON || b.link != i) continue
            if (a.state < 1 || b.state < 1 || a.state > 2 || b.state > 2) continue
            Draw.pylonTether(c, a, b, a.state == 2 && b.state == 2, minOf(a.telegraph, b.telegraph))
        }
        for (e in enemies) if (e.active) {
            Draw.enemy(c, e, time)
            if (e.kind == EK.BOSS) BossAI.drawArmour(c, e)
            if (e.kind == EK.SHIELDER) Draw.shielderPlate(c, e)
        }
        for (b in bullets) if (b.active && !b.hostile) Draw.bullet(c, b)
        for (s in slots) if (s.joined) s.arsenal.draw(c, this)
        for (s in slots) {
            if (!s.joined) continue
            Draw.player(c, s.player, time)
            if (coop && s.player.alive) Draw.pilotTag(c, s.player, s.index, time)
        }
        for (b in bullets) if (b.active && b.hostile) Draw.bullet(c, b)
        fx.drawParticles(c)
        fx.drawTexts(c)
    }

    /** Set from snapshots when this world is a client-side mirror. */
    private var netBoss = false

    /** Freezes the run's headline numbers into the tally. */
    fun sealTally() {
        tally.score = score
        tally.wave = wave
        tally.kills = kills
        tally.levels = levelsCleared
    }

    fun bossPresent(): Boolean = netBoss || (boss != null && boss?.active == true)

    /**
     * Client-side: replace this world's contents with the host's authoritative
     * frame. The local pilot is always mapped to slot 0 so every HUD and draw
     * path keeps working unchanged.
     */
    fun applySnapshot(s: Snapshot, mySlot: Int, localX: Float, localY: Float) {
        score = s.score
        combo = s.combo
        wave = s.wave
        levelsCleared = s.levelsCleared
        // overload is a pure function of sectors cleared, so the mirror needs
        // no extra field on the wire to show the same killscreen
        if (s.levelsCleared > overload) overloadAlarm = ALARM_TIME
        overload = s.levelsCleared
        bossHpRatio = s.bossHpRatio
        netBoss = s.bossPresent
        banner = s.banner
        bannerSub = s.bannerSub
        bannerT = s.bannerT
        gameOver = s.gameOver

        for (i in 0..1) {
            val src = s.players[if (i == 0) mySlot else 1 - mySlot]
            val dst = slots[i]
            dst.joined = src.joined
            val p = dst.player
            p.alive = src.alive
            // the local ship draws at its predicted position so it answers the thumb
            p.x = if (i == 0) localX else src.x
            p.y = if (i == 0) localY else src.y
            p.lives = src.lives
            p.shield = src.shield
            p.weapon = src.weapon
            p.overdrive = src.overdrive
            p.odTime = src.odTime
            p.shipId = src.shipId
            p.bank = src.bank
            p.invuln = src.invuln
            dst.ship = ShipDex.byId(src.shipId)
        }

        for (i in bullets.indices) bullets[i].active = false
        for (i in 0 until s.bulletCount) {
            val b = bullets[i]
            b.active = true
            b.x = s.bulletX[i]; b.y = s.bulletY[i]; b.r = s.bulletR[i]
            b.style = s.bulletStyle[i]; b.hostile = s.bulletHostile[i]; b.color = s.bulletColor[i]
            b.vx = 0f; b.vy = if (b.hostile) 1f else -1f     // only used for lance orientation
        }
        for (i in enemies.indices) enemies[i].active = false
        for (i in 0 until s.enemyCount) {
            val e = enemies[i]
            e.active = true
            e.x = s.enemyX[i]; e.y = s.enemyY[i]; e.r = s.enemyR[i]
            e.kind = s.enemyKind[i]; e.angle = s.enemyAngle[i]
            e.hitFlash = s.enemyFlash[i]; e.elite = s.enemyElite[i]; e.color = s.enemyColor[i]
            e.telegraph = s.enemyTelegraph[i]
            // the host renumbered links into this list, so they index straight in
            e.link = s.enemyLink[i].let { if (it in 0 until s.enemyCount) it else -1 }
            e.burn = 0f; e.burnDps = 0f
        }
        for (u in pickups) u.active = false
        for (i in 0 until minOf(s.pickupCount, pickups.size)) {
            val u = pickups[i]
            u.active = true
            u.x = s.pickupX[i]; u.y = s.pickupY[i]; u.kind = s.pickupKind[i]; u.t = s.pickupT[i]
            u.r = if (u.kind == PK.GEM) 9f else 13f
            u.life = 10f
        }
    }

    /** Client-side: the host's closing numbers for the game-over panel. */
    fun applyFinal(finalScore: Int, finalWave: Int, finalKills: Int, finalCombo: Int, finalLevels: Int) {
        score = finalScore
        wave = finalWave
        kills = finalKills
        maxCombo = finalCombo
        levelsCleared = finalLevels
        gameOver = true
    }

    /** Prepares this world as a client-side mirror rather than a simulation. */
    fun prepareMirror(localShip: Ship, partnerShip: Ship) {
        slots[0].joined = true
        slots[0].ship = localShip
        slots[1].joined = true
        slots[1].ship = partnerShip
        gameOver = false
        netBoss = false
        pendingAugment = false
    }
}

package com.neonvoid.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

private class TouchEv {
    var action = 0
    var id = 0
    var x = 0f
    var y = 0f
}

/**
 * Owns the state machine, the virtual coordinate system and input routing.
 * Touch events arrive on the UI thread and are queued; everything else runs on
 * the render thread inside [update] / [draw].
 */
class Game(context: Context) {

    companion object {
        const val VIRTUAL_W = 540f
        const val DOWN = 0
        const val MOVE = 1
        const val UP = 2
    }

    enum class State { MENU, PLAYING, PAUSED, GAME_OVER, AUGMENT, HANGAR, REVEAL, SHOP, RECORDS, COOP }

    val prefs = Prefs(context)
    private val haptics = Haptics(context).also { it.enabled = prefs.hapticsOn }
    private val fx = Fx()
    private val world = World(fx, haptics)
    private val bg = Background()
    private val hud = Hud(prefs)
    private val audio = Audio(prefs)

    var state = State.MENU
        private set

    init {
        world.sound = audio
        world.ship = ShipDex.byId(prefs.selectedShip)
    }

    private var scale = 1f
    private var vw = VIRTUAL_W
    private var vh = 1000f
    private var topInsetPx = 0f
    private var bottomInsetPx = 0f
    private var time = 0f
    private var newBest = false
    private var lockoutT = 0f
    private var offers: List<AugCard> = emptyList()
    private var sectorShown = -1
    private var revealShip: Ship = ShipDex.byId(ShipDex.STARTER)
    private var revealNew = false
    private var revealRefund = 0
    private var coresEarned = 0
    private var revealMulti: List<Ship> = emptyList()

    // ---- co-op ----------------------------------------------------------
    private var netRole = NetRole.NONE
    private val host = CoopHost()
    private val client = CoopClient()
    /** 0 = choose a side, 1 = hosting, 2 = joining. */
    private var coopStage = 0
    private var typedAddress = ""
    private var coopStatus = ""
    private var partnerPicking = false
    private var revealMultiNew: List<Boolean> = emptyList()

    private val vignette = Paint(Paint.ANTI_ALIAS_FLAG)
    private var vignetteReady = false

    // ------------------------------------------------------------ lifecycle

    fun resize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        scale = widthPx / VIRTUAL_W
        vw = VIRTUAL_W
        vh = heightPx / scale
        bg.resize(vw, vh)
        world.resize(vw, vh)
        layoutHud()
        vignette.shader = RadialGradient(
            vw * 0.5f, vh * 0.5f, maxOf(vw, vh) * 0.72f,
            intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        vignette.style = Paint.Style.FILL
        vignetteReady = true
    }

    fun setInsets(topPx: Float, bottomPx: Float) {
        topInsetPx = topPx
        bottomInsetPx = bottomPx
        layoutHud()
    }

    private fun layoutHud() {
        hud.layout(vw, vh, topInsetPx / scale, bottomInsetPx / scale)
    }

    fun onAppPause() {
        if (state == State.PLAYING) state = State.PAUSED
        audio.stop()
    }

    fun onAppResume() {
        audio.start()
    }

    fun onDestroy() {
        audio.stop()
    }

    /** Music follows the sector; boss waves switch the arrangement up a gear. */
    private fun syncAudio() {
        val inRun = state == State.PLAYING || state == State.AUGMENT || state == State.PAUSED
        if (inRun) {
            val idx = Levels.index(world.wave.coerceAtLeast(1))
            audio.setTrack(1 + idx)
            audio.setIntense(world.bossPresent())
        } else {
            audio.setTrack(0)
            audio.setIntense(false)
        }
    }

    /** Repaint the backdrop when the run crosses into a new sector. */
    private fun syncSector() {
        val inRun = state == State.PLAYING || state == State.AUGMENT || state == State.PAUSED ||
            state == State.GAME_OVER
        val idx = if (!inRun) 0 else Levels.index(world.wave.coerceAtLeast(1))
        if (idx != sectorShown) {
            sectorShown = idx
            bg.applyTheme(Levels.list[idx])
        }
    }

    private fun openAugmentChoice() {
        if (partnerPicking) return
        offers = world.rollAugments(3)
        if (offers.isEmpty()) {          // everything maxed: skip the screen
            world.applyAugment(AugCard(Aug.SALVAGE, 0, "SALVAGE", "BONUS", "", Palette.AMBER))
            return
        }
        // In co-op the host drafts first, then hands the choice to the partner.
        if (netRole == NetRole.HOST && world.augmentSlot == 1) {
            host.sendOffer(1, offers)
            host.pendingPick = -1
            partnerPicking = true
            world.hideBanner()
            return
        }
        hud.prepareCards(offers)
        world.hideBanner()
        state = State.AUGMENT
        lockoutT = 0.3f
        clearPressed()
    }

    /** Returns true when the game consumed the back gesture. */
    fun onBack(): Boolean = when (state) {
        State.AUGMENT -> true          // a choice has to be made
        State.COOP -> { leaveCoop(); state = State.MENU; true }
        State.REVEAL -> { state = State.HANGAR; true }
        State.HANGAR, State.SHOP, State.RECORDS -> { state = State.MENU; true }
        State.PLAYING -> { state = State.PAUSED; true }
        State.PAUSED -> { state = State.MENU; true }
        State.GAME_OVER -> { state = State.MENU; true }
        State.MENU -> false
    }

    private fun startRun() {
        world.ship = ShipDex.byId(prefs.selectedShip)
        world.meta = Shop.meta(prefs)
        world.reset()
        newBest = false
        state = State.PLAYING
        clearPressed()
    }

    // ---------------------------------------------------------------- input

    private val inputLock = Any()
    private val queue = ArrayList<TouchEv>()
    private val pool = ArrayList<TouchEv>()
    private var moveId = -1
    private var lastX = 0f
    private var lastY = 0f
    private var downButton: Button? = null
    private var downPointer = -1

    fun postTouch(action: Int, id: Int, xPx: Float, yPx: Float) {
        synchronized(inputLock) {
            val e = if (pool.isEmpty()) TouchEv() else pool.removeAt(pool.size - 1)
            e.action = action
            e.id = id
            e.x = xPx / scale
            e.y = yPx / scale
            queue.add(e)
        }
    }

    private fun drainInput() {
        val batch: List<TouchEv>
        synchronized(inputLock) {
            if (queue.isEmpty()) return
            batch = ArrayList(queue)
            queue.clear()
        }
        for (e in batch) {
            when (e.action) {
                DOWN -> onDown(e.id, e.x, e.y)
                MOVE -> onMove(e.id, e.x, e.y)
                else -> onUp(e.id, e.x, e.y)
            }
        }
        synchronized(inputLock) { pool.addAll(batch) }
    }

    private fun buttonsFor(): List<Button> = when (state) {
        State.MENU -> listOf(hud.play, hud.hangar, hud.shop, hud.records, hud.coop, hud.music, hud.sfx, hud.haptic)
        State.PLAYING -> listOf(hud.pause)
        State.PAUSED -> listOf(hud.resume, hud.restart, hud.quit)
        State.GAME_OVER -> listOf(hud.retry, hud.toMenu)
        State.AUGMENT -> (0 until hud.cardCount).map { hud.cards[it].btn }
        State.HANGAR -> ArrayList<Button>(hud.shipCells.size + 3).apply {
            add(hud.summon); add(hud.summon10); add(hud.back); addAll(hud.shipCells)
        }
        State.SHOP -> ArrayList<Button>(hud.shopRows.size + 1).apply {
            add(hud.back); addAll(hud.shopRows)
        }
        State.RECORDS -> listOf(hud.back)
        State.COOP -> when (coopStage) {
            0 -> listOf(hud.back, hud.hostGame, hud.joinGame)
            1 -> listOf(hud.back, hud.startCoop)
            else -> ArrayList<Button>(hud.keypad.size + 2).apply {
                add(hud.back); add(hud.connectBtn); addAll(hud.keypad)
            }
        }
        State.REVEAL -> emptyList()
    }

    private fun clearPressed() {
        downButton?.pressed = false
        downButton = null
        downPointer = -1
    }

    private fun onDown(id: Int, x: Float, y: Float) {
        if (lockoutT > 0f) return

        // Chrome wins over gameplay gestures, otherwise the second-finger overdrive
        // shortcut would swallow taps on the pause button.
        for (b in buttonsFor()) {
            if (b.contains(x, y)) {
                b.pressed = true
                downButton = b
                downPointer = id
                haptics.light()
                return
            }
        }

        if (state == State.REVEAL) {
            state = State.HANGAR
            audio.sfx(Sfx.UI)
            return
        }
        if (state != State.PLAYING) return

        // Overdrive fires on press - either on its button or with a second finger.
        if (hud.overdrive.contains(x, y) || moveId != -1) {
            if (netRole == NetRole.CLIENT) client.queueOverdrive() else world.triggerOverdrive()
            if (hud.overdrive.contains(x, y)) return
        }

        if (moveId == -1) {
            moveId = id
            lastX = x
            lastY = y
        }
    }

    private fun onMove(id: Int, x: Float, y: Float) {
        if (state == State.PLAYING && id == moveId) {
            val dx = x - lastX
            val dy = y - lastY
            if (netRole == NetRole.CLIENT) client.queueInput(dx, dy) else world.moveBy(dx, dy)
            lastX = x
            lastY = y
        }
        val b = downButton
        if (b != null && id == downPointer && !b.contains(x, y)) {
            b.pressed = false
        }
    }

    private fun onUp(id: Int, x: Float, y: Float) {
        if (id == moveId) moveId = -1
        val b = downButton
        if (b != null && id == downPointer) {
            val fired = b.pressed && b.contains(x, y)
            b.pressed = false
            downButton = null
            downPointer = -1
            if (fired) activate(b)
        }
    }

    private fun activate(b: Button) {
        audio.sfx(Sfx.UI)
        if (state == State.SHOP) {
            if (b === hud.back) { state = State.MENU; return }
            val idx = hud.shopRows.indexOfFirst { it === b }
            if (idx >= 0) buyUpgrade(idx)
            return
        }
        if (state == State.RECORDS) {
            state = State.MENU
            return
        }
        if (state == State.COOP) {
            handleCoopButton(b)
            return
        }
        if (state == State.HANGAR) {
            when (b) {
                hud.summon -> summon()
                hud.summon10 -> summonTen()
                hud.back -> state = State.MENU
                else -> {
                    val idx = hud.shipCells.indexOfFirst { it === b }
                    if (idx >= 0 && ShipDex.isOwned(prefs.ownedShips, idx)) {
                        prefs.selectedShip = idx
                        world.ship = ShipDex.byId(idx)
                    }
                }
            }
            return
        }
        if (state == State.AUGMENT) {
            for (i in 0 until hud.cardCount) {
                if (hud.cards[i].btn === b) {
                    val card = hud.cards[i].card ?: return
                    if (netRole == NetRole.CLIENT) {
                        // the host owns the loadout; mirror it locally for the HUD
                        client.sendPick(i)
                        world.slots[0].loadout.apply(card)
                    } else {
                        world.applyAugment(card)
                    }
                    state = State.PLAYING
                    return
                }
            }
            return
        }
        when (b) {
            hud.play -> startRun()
            hud.haptic -> {
                prefs.hapticsOn = !prefs.hapticsOn
                haptics.enabled = prefs.hapticsOn
                haptics.medium()
            }
            hud.hangar -> state = State.HANGAR
            hud.shop -> state = State.SHOP
            hud.records -> state = State.RECORDS
            hud.coop -> { state = State.COOP; coopStage = 0; coopStatus = ""; typedAddress = prefs.lastHost }
            hud.music -> { prefs.musicOn = !prefs.musicOn; audio.applyPrefs() }
            hud.sfx -> { prefs.sfxOn = !prefs.sfxOn; audio.applyPrefs() }
            hud.pause -> state = State.PAUSED
            hud.resume -> state = State.PLAYING
            hud.restart -> startRun()
            hud.quit -> { leaveCoop(); state = State.MENU }
            hud.retry -> { leaveCoop(); startRun() }
            hud.toMenu -> { leaveCoop(); state = State.MENU }
        }
    }

    // ------------------------------------------------------------- co-op

    private fun handleCoopButton(b: Button) {
        when {
            b === hud.back -> {
                leaveCoop()
                state = State.MENU
            }
            b === hud.hostGame -> {
                netRole = NetRole.HOST
                coopStage = 1
                coopStatus = if (host.start()) "WAITING FOR PARTNER" else host.message
            }
            b === hud.joinGame -> {
                netRole = NetRole.CLIENT
                coopStage = 2
                coopStatus = "ENTER THE HOST ADDRESS"
            }
            b === hud.startCoop -> startCoopRun()
            b === hud.connectBtn -> {
                if (typedAddress.isNotEmpty()) {
                    prefs.lastHost = typedAddress
                    client.connect(typedAddress, prefs.selectedShip, "PARTNER")
                    coopStatus = "CONNECTING"
                }
            }
            else -> {
                val k = hud.keypad.indexOfFirst { it === b }
                if (k >= 0) {
                    typedAddress = when (k) {
                        11 -> typedAddress.dropLast(1)
                        9 -> if (typedAddress.length < 15) "$typedAddress." else typedAddress
                        10 -> if (typedAddress.length < 15) typedAddress + "0" else typedAddress
                        else -> if (typedAddress.length < 15) typedAddress + (k + 1) else typedAddress
                    }
                }
            }
        }
    }

    private fun startCoopRun() {
        world.ship = ShipDex.byId(prefs.selectedShip)
        world.meta = Shop.meta(prefs)
        world.joinPartner(ShipDex.byId(host.partnerShipId), Meta(), host.partnerName)
        world.reset()
        host.beginRun()
        newBest = false
        partnerPicking = false
        state = State.PLAYING
        clearPressed()
    }

    private fun leaveCoop() {
        host.stop()
        client.stop()
        netRole = NetRole.NONE
        world.dropPartner()
        partnerPicking = false
    }

    private fun buyUpgrade(index: Int) {
        val item = Shop.items[index]
        val level = prefs.shopLevel(item.id)
        val cost = Shop.cost(item.id, level)
        if (cost < 0 || prefs.cores < cost) return
        prefs.cores = prefs.cores - cost
        prefs.setShopLevel(item.id, level + 1)
        audio.sfx(Sfx.POWERUP)
        haptics.medium()
        fx.flash(item.color, 0.25f)
    }

    /** Ten pulls at once, with the usual guarantee of a rare or better. */
    private fun summonTen() {
        val cost = ShipDex.PULL_COST * 10
        if (prefs.cores < cost) return
        prefs.cores = prefs.cores - cost
        prefs.pulls = prefs.pulls + 10
        val results = ArrayList<Ship>(10)
        val flags = ArrayList<Boolean>(10)
        var owned = prefs.ownedShips
        var refund = 0
        var bestRarity = 0
        for (i in 0 until 10) {
            val ship = if (i == 9 && bestRarity < Rarity.RARE) ShipDex.rollAtLeast(Rarity.RARE) else ShipDex.roll()
            if (ship.rarity > bestRarity) bestRarity = ship.rarity
            val had = ShipDex.isOwned(owned, ship.id)
            results.add(ship)
            flags.add(!had)
            if (had) refund += Rarity.dupeRefund[ship.rarity] else owned = ShipDex.withOwned(owned, ship.id)
        }
        prefs.ownedShips = owned
        prefs.cores = prefs.cores + refund
        revealMulti = results
        revealMultiNew = flags
        revealRefund = refund
        audio.sfx(Sfx.SUMMON)
        haptics.heavy()
        fx.flash(Rarity.colors[bestRarity], 0.4f)
        state = State.REVEAL
        lockoutT = 0.5f
    }

    private fun summon() {
        revealMulti = emptyList()
        if (prefs.cores < ShipDex.PULL_COST) return
        prefs.cores = prefs.cores - ShipDex.PULL_COST
        prefs.pulls = prefs.pulls + 1
        val ship = ShipDex.roll()
        val had = ShipDex.isOwned(prefs.ownedShips, ship.id)
        revealShip = ship
        revealNew = !had
        revealRefund = if (had) Rarity.dupeRefund[ship.rarity] else 0
        if (had) {
            prefs.cores = prefs.cores + revealRefund
        } else {
            prefs.ownedShips = ShipDex.withOwned(prefs.ownedShips, ship.id)
        }
        audio.sfx(Sfx.SUMMON)
        haptics.heavy()
        fx.flash(Rarity.colors[ship.rarity], 0.35f)
        state = State.REVEAL
        lockoutT = 0.45f
    }

    // --------------------------------------------------------------- update

    fun update(dtRaw: Float) {
        val dt = clamp(dtRaw, 0f, 0.05f)
        time += dt
        if (lockoutT > 0f) lockoutT -= dt
        drainInput()

        syncSector()
        syncAudio()

        when (state) {
            State.MENU, State.HANGAR, State.REVEAL, State.SHOP, State.RECORDS -> {
                bg.update(dt, 0.12f)
                fx.update(dt)
            }
            State.AUGMENT -> {
                bg.update(dt, world.intensity * 0.35f)
                fx.update(dt)
            }
            State.COOP -> {
                bg.update(dt, 0.12f)
                fx.update(dt)
                pumpLobby(dt)
            }
            State.PLAYING -> {
                bg.update(dt, world.intensity)
                if (netRole == NetRole.CLIENT) {
                    updateAsClient(dt)
                    return
                }
                world.update(dt)
                if (netRole == NetRole.HOST) host.pumpRun(world, dt)
                if (netRole == NetRole.HOST && host.stage == NetStage.FAILED) {
                    netRole = NetRole.NONE
                    world.dropPartner()
                }
                if (world.pendingAugment) openAugmentChoice()
                if (partnerPicking && host.pendingPick >= 0) {
                    val idx = host.pendingPick.coerceIn(0, offers.size - 1)
                    if (offers.isNotEmpty()) world.applyAugment(offers[idx])
                    host.pendingPick = -1
                    partnerPicking = false
                    if (world.pendingAugment) openAugmentChoice()
                }
                if (world.gameOver) {
                    if (netRole == NetRole.HOST) host.sendOver(world)
                    state = State.GAME_OVER
                    newBest = prefs.submit(world.score, world.wave, world.maxCombo)
                    coresEarned = (prefs.coresFor(world.score, world.wave) * Shop.coreMultiplier(prefs)).toInt()
                    prefs.cores = prefs.cores + coresEarned
                    prefs.totalCores = prefs.totalCores + coresEarned
                    prefs.totalKills = prefs.totalKills + world.kills
                    if (world.levelsCleared > prefs.bestLevel) prefs.bestLevel = world.levelsCleared
                    lockoutT = 0.9f
                    clearPressed()
                }
            }
            State.PAUSED -> { /* frozen */ }
            State.GAME_OVER -> {
                bg.update(dt, world.intensity * 0.4f)
                fx.update(dt)
            }
        }
    }

    private fun pumpLobby(dt: Float) {
        when (netRole) {
            NetRole.HOST -> {
                host.pumpLobby(prefs.selectedShip, "HOST")
                coopStatus = when {
                    host.stage == NetStage.FAILED -> host.message
                    host.partnerName.isNotEmpty() -> "PARTNER READY - LAUNCH WHEN YOU ARE"
                    else -> "WAITING FOR PARTNER"
                }
            }
            NetRole.CLIENT -> {
                client.pump(dt)
                coopStatus = when (client.stage) {
                    NetStage.FAILED -> client.message
                    NetStage.LOBBY -> "CONNECTED - WAITING FOR HOST"
                    NetStage.RUNNING -> "LAUNCHING"
                    else -> client.message
                }
                if (client.stage == NetStage.RUNNING) {
                    world.ship = ShipDex.byId(prefs.selectedShip)
                    world.prepareMirror(ShipDex.byId(prefs.selectedShip), ShipDex.byId(client.hostShipId))
                    client.setBounds(vw, vh)
                    state = State.PLAYING
                    clearPressed()
                }
            }
            else -> {}
        }
    }

    /** The client never simulates: it sends input and mirrors what comes back. */
    private fun updateAsClient(dt: Float) {
        val live = client.pump(dt)
        client.predict(dt)
        fx.update(dt)
        if (client.haveSnapshot) {
            world.applySnapshot(client.snapshot, client.mySlot, client.localX, client.localY)
        }
        if (client.offer != null && state == State.PLAYING) {
            offers = client.offer!!.cards
            hud.prepareCards(offers)
            world.hideBanner()
            state = State.AUGMENT
            lockoutT = 0.3f
            clearPressed()
        }
        if (!live || client.stage == NetStage.ENDED || client.stage == NetStage.FAILED) {
            if (client.finalScore > 0) {
                world.applyFinal(client.finalScore, client.finalWave, 0, 0, 0)
            }
            newBest = prefs.submit(world.score, world.wave, world.maxCombo)
            netRole = NetRole.NONE
            state = State.GAME_OVER
            lockoutT = 0.9f
            clearPressed()
        }
    }

    // ----------------------------------------------------------------- draw

    fun draw(c: Canvas) {
        c.save()
        c.scale(scale, scale)

        bg.draw(c)

        if (state != State.MENU && state != State.HANGAR && state != State.REVEAL &&
            state != State.SHOP && state != State.RECORDS
        ) {
            c.save()
            c.translate(fx.shakeX, fx.shakeY)
            world.draw(c)
            c.restore()
        }

        when (state) {
            State.MENU -> hud.drawMenu(c, time)
            State.PLAYING -> hud.drawGame(c, world, time)
            State.PAUSED -> { hud.drawGame(c, world, time, false); hud.drawPause(c, world, time) }
            State.GAME_OVER -> { hud.drawGame(c, world, time, false); hud.drawGameOver(c, world, newBest, time) }
            State.AUGMENT -> { hud.drawGame(c, world, time, false); hud.drawAugment(c, world, time) }
            State.PLAYING -> if (partnerPicking) {
                hud.drawGame(c, world, time, false); hud.drawPartnerPicking(c, time)
            } else {
                hud.drawGame(c, world, time)
            }
            State.HANGAR -> hud.drawHangar(c, prefs.selectedShip, time)
            State.SHOP -> hud.drawShop(c, time)
            State.RECORDS -> hud.drawRecords(c, time)
            State.COOP -> hud.drawCoop(
                c, coopStage,
                if (netRole == NetRole.HOST) "HOSTING" else "JOINING",
                coopStatus, host.localAddress(), typedAddress,
                netRole == NetRole.HOST && host.partnerName.isNotEmpty(), time
            )
            State.REVEAL ->
                if (revealMulti.isEmpty()) hud.drawReveal(c, revealShip, revealNew, revealRefund, time)
                else hud.drawRevealMulti(c, revealMulti, revealMultiNew, revealRefund, time)
        }

        scanlines(c)
        if (vignetteReady) c.drawRect(0f, 0f, vw, vh, vignette)
        // Fx is frozen while paused, so a flash raised on the last live frame
        // would otherwise stay lit for the whole pause.
        if (state != State.PAUSED) fx.drawFlash(c, vw, vh)
        c.restore()
    }

    private fun scanlines(c: Canvas) {
        var y = 0f
        val color = 0x14000000
        while (y < vh) {
            Neon.hairline(c, 0f, y, vw, y, color, 1f)
            y += 5f
        }
    }
}

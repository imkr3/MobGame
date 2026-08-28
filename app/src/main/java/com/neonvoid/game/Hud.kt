package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin

class Button(var label: String, var color: Int) {
    var cx = 0f; var cy = 0f
    var w = 0f; var h = 0f
    var enabled = true
    var pressed = false

    fun place(cx: Float, cy: Float, w: Float, h: Float) {
        this.cx = cx; this.cy = cy; this.w = w; this.h = h
    }

    fun contains(x: Float, y: Float): Boolean {
        val pad = 8f
        return enabled && x >= cx - w / 2 - pad && x <= cx + w / 2 + pad &&
                y >= cy - h / 2 - pad && y <= cy + h / 2 + pad
    }
}

/** Locale-independent one-decimal formatting - String.format would follow device digits. */
fun oneDecimal(v: Float): String {
    val scaled = (v * 10f + 0.5f).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

fun formatScore(n: Int): String {
    val s = n.toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    var c = 0
    for (i in s.length - 1 downTo 0) {
        sb.append(s[i])
        c++
        if (c % 3 == 0 && i > 0) sb.append(',')
    }
    return sb.reverse().toString()
}

/** All chrome: in-run HUD, title screen, pause and game-over panels. */
class Hud(private val prefs: Prefs) {

    private companion object {
        /** Vertical pitch of the level-select grid. */
        const val LEVEL_ROW = 100f
    }

    val play = Button("PLAY", Palette.CYAN)
    val haptic = Button("HAPTICS ON", Palette.VIOLET)
    val pause = Button("", Palette.DIM)
    val overdrive = Button("OD", Palette.AMBER)
    val resume = Button("RESUME", Palette.CYAN)
    val restart = Button("RESTART", Palette.MAGENTA)
    val quit = Button("QUIT", Palette.DIM)
    val retry = Button("FLY AGAIN", Palette.CYAN)
    val toMenu = Button("MAIN MENU", Palette.DIM)
    val hangar = Button("HANGAR", Palette.AMBER)
    val music = Button("MUSIC", Palette.VIOLET)
    val sfx = Button("SFX", Palette.VIOLET)
    val summon = Button("SUMMON", Palette.AMBER)
    val summon10 = Button("SUMMON x10", Palette.AMBER)
    val back = Button("BACK", Palette.DIM)
    val shop = Button("SHOP", Palette.LIME)
    val records = Button("RECORDS", Palette.SKY)
    val shopRows = Array(Shop.COUNT) { Button("", Palette.DIM) }
    val coop = Button("CO-OP", Palette.ROSE)
    val hostGame = Button("HOST GAME", Palette.CYAN)
    val joinGame = Button("JOIN GAME", Palette.LIME)
    val startCoop = Button("LAUNCH", Palette.AMBER)
    val connectBtn = Button("CONNECT", Palette.LIME)
    /** Digits 0-9, then dot, then backspace. */
    val keypad = Array(12) { Button("", Palette.SKY) }
    val shipCells = Array(ShipDex.list.size) { Button("", Palette.DIM) }
    val levels = Button("LEVELS", Palette.MAGENTA)
    val launch = Button("LAUNCH", Palette.CYAN)
    val levelCells = Array(Levels.list.size) { Button("", Palette.DIM) }

    /** One offered augment: its card geometry, hit target and pre-wrapped copy. */
    class CardView {
        var card: AugCard? = null
        val lines = ArrayList<String>(3)
        val btn = Button("", Palette.CYAN)
    }

    val cards = Array(3) { CardView() }
    var cardCount = 0
        private set
    private val badgeIds = ArrayList<Int>(Aug.COUNT)

    private var w = 540f
    private var h = 1000f
    private var top = 20f
    private var bottom = 20f

    private val arc = RectF()
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shipPath = Path()

    fun layout(width: Float, height: Float, topInset: Float, bottomInset: Float) {
        w = width; h = height
        top = topInset + 16f
        bottom = bottomInset + 16f

        val cx = w * 0.5f
        val menuTop = h * 0.545f
        play.place(cx, menuTop, w * 0.62f, 56f)
        levels.place(cx, menuTop + 62f, w * 0.62f, 44f)
        val halfW = w * 0.305f
        hangar.place(cx - halfW / 2 - 5f, menuTop + 114f, halfW, 44f)
        shop.place(cx + halfW / 2 + 5f, menuTop + 114f, halfW, 44f)
        records.place(cx - halfW / 2 - 5f, menuTop + 162f, halfW, 40f)
        coop.place(cx + halfW / 2 + 5f, menuTop + 162f, halfW, 40f)

        // level select: two columns of cards, launch button under them
        val cardW = w * 0.44f
        val gridTop = h * 0.19f
        for (i in levelCells.indices) {
            levelCells[i].place(
                cx + (if (i % 2 == 0) -1f else 1f) * (cardW * 0.5f + 6f),
                gridTop + (i / 2) * LEVEL_ROW,
                cardW, 88f
            )
        }
        // hung off the grid rather than the screen, so it never drifts away
        // from the last row on tall phones
        launch.place(cx, gridTop + 4 * LEVEL_ROW + 100f, w * 0.52f, 54f)

        hostGame.place(cx, h * 0.34f, w * 0.62f, 58f)
        joinGame.place(cx, h * 0.34f + 76f, w * 0.62f, 58f)
        startCoop.place(cx, h * 0.62f, w * 0.62f, 58f)
        connectBtn.place(cx, h * 0.70f, w * 0.5f, 52f)
        for (i in keypad.indices) {
            val col = i % 3
            val row = i / 3
            keypad[i].place(cx + (col - 1) * (w * 0.22f), h * 0.40f + row * 60f, w * 0.20f, 52f)
        }
        val tw = w * 0.185f
        music.place(cx - tw - 8f, menuTop + 214f, tw, 36f)
        sfx.place(cx, menuTop + 214f, tw, 36f)
        haptic.place(cx + tw + 8f, menuTop + 214f, tw, 36f)

        summon.place(cx - w * 0.16f, h * 0.795f, w * 0.30f, 54f)
        summon10.place(cx + w * 0.16f, h * 0.795f, w * 0.30f, 54f)
        back.place(cx, h * 0.875f, w * 0.4f, 46f)
        for (i in shopRows.indices) {
            shopRows[i].place(cx, h * 0.175f + i * 96f, w * 0.9f, 84f)
        }
        val cols = 6
        val cellW = w * 0.148f
        val cellH = 66f
        for (i in shipCells.indices) {
            val col = i % cols
            val row = i / cols
            shipCells[i].place(
                w * 0.5f + (col - (cols - 1) / 2f) * (cellW + 8f),
                h * 0.45f + row * (cellH + 8f),
                cellW, cellH
            )
        }

        pause.place(w - 34f, top + 22f, 44f, 44f)
        overdrive.place(w - 56f, h - bottom - 62f, 68f, 68f)

        resume.place(cx, h * 0.58f, w * 0.58f, 58f)
        restart.place(cx, h * 0.58f + 76f, w * 0.58f, 52f)
        quit.place(cx, h * 0.58f + 144f, w * 0.58f, 52f)

        retry.place(cx, h * 0.66f, w * 0.60f, 58f)
        toMenu.place(cx, h * 0.66f + 76f, w * 0.50f, 48f)
    }

    // --------------------------------------------------------------- pieces

    private fun drawButton(c: Canvas, b: Button, time: Float, filled: Boolean = false) {
        if (!b.enabled) return
        val pulse = if (b.pressed) 1f else 0.75f + 0.25f * sin(time * 3.2f)
        val l = b.cx - b.w / 2
        val r = b.cx + b.w / 2
        val t = b.cy - b.h / 2
        val bt = b.cy + b.h / 2
        val fill = if (b.pressed) fade(b.color, 0.30f) else fade(b.color, if (filled) 0.14f else 0.07f)
        Neon.panel(c, l, t, r, bt, b.h * 0.28f, fill, fade(b.color, pulse), 2f, 1f)
        Neon.label(c, b.label, b.cx, b.cy + b.h * 0.17f, b.h * 0.42f, b.color, Paint.Align.CENTER, 0.9f, 0.16f)
    }

    private fun shipIcon(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        Neon.fillPath(c, Shapes.player, fade(color, 0.25f))
        Neon.path(c, Shapes.player, color, 1.6f / s, 0.7f, 0.7f)
        c.restore()
    }

    // ------------------------------------------------------------- in-game

    fun drawGame(c: Canvas, world: World, time: Float, showBanner: Boolean = true) {
        val p = world.player

        // score
        Neon.label(c, formatScore(world.score), 18f, top + 34f, 34f, Palette.WHITE, Paint.Align.LEFT, 0.7f, 0.06f, Neon.FONT_NUM)
        Neon.label(c, "BEST ${formatScore(maxOf(prefs.bestScore, world.score))}", 20f, top + 56f, 15f, Palette.DIM, Paint.Align.LEFT, 0.4f, 0.1f)

        // multiplier
        if (world.combo > 1) {
            val m = world.multiplier
            val col = when {
                m >= 5f -> Palette.AMBER
                m >= 2.5f -> Palette.MAGENTA
                else -> Palette.CYAN
            }
            val bump = 1f + 0.12f * sin(time * 9f) * clamp((m - 1f) / 4f, 0f, 1f)
            Neon.label(c, "x" + oneDecimal(m), 20f, top + 92f, 26f * bump, col, Paint.Align.LEFT, 0.9f, 0.04f, Neon.FONT_NUM)
        }

        // wave + where you are + what clearing it buys you
        val sector = world.theme(world.wave.coerceAtLeast(1))
        Neon.label(c, "WAVE ${world.wave}", w * 0.5f, top + 26f, 18f, Palette.VIOLET, Paint.Align.CENTER, 0.6f, 0.22f)
        if (!world.bossPresent()) {
            Neon.label(c, sector.name, w * 0.5f, top + 42f, 11f, fade(sector.accent, 0.8f), Paint.Align.CENTER, 0.4f, 0.3f)
            val hint = if (world.loadout.slotsFull()) "CLEAR WAVE - UPGRADE EXISTING" else "CLEAR WAVE - NEW UPGRADE"
            Neon.label(c, hint, w * 0.5f, top + 58f, 10f, fade(Palette.DIM, 0.9f), Paint.Align.CENTER, 0.25f, 0.24f, Neon.FONT_BODY)
        }

        // pause button
        val px = pause.cx
        val py = pause.cy
        Neon.panel(c, px - 20f, py - 20f, px + 20f, py + 20f, 11f, fade(Palette.DIM, 0.10f), fade(Palette.DIM, 0.85f), 1.6f, 0.6f)
        Neon.line(c, px - 6f, py - 9f, px - 6f, py + 9f, Palette.SKY, 2.6f, 0.6f)
        Neon.line(c, px + 6f, py - 9f, px + 6f, py + 9f, Palette.SKY, 2.6f, 0.6f)

        // boss health
        if (world.bossPresent()) {
            val bw = w * 0.72f
            val bx = w * 0.5f - bw / 2
            val by = top + 52f
            Neon.panel(c, bx, by, bx + bw, by + 12f, 6f, fade(Palette.RED, 0.10f), fade(Palette.RED, 0.7f), 1.4f, 0.7f)
            val fillW = bw * world.bossHpRatio
            if (fillW > 2f) {
                Neon.fillRect(c, bx + 1.5f, by + 1.5f, bx + 1.5f + (fillW - 3f).coerceAtLeast(0f), by + 10.5f, fade(Palette.RED, 0.9f))
            }
            Neon.label(c, "GUARDIAN", w * 0.5f, by - 6f, 13f, Palette.RED, Paint.Align.CENTER, 0.5f, 0.3f)
        }

        // lives, then the shield bank right beside them
        var lx = 24f
        val ly = h - bottom - 22f
        for (i in 0 until p.lives.coerceAtMost(6)) {
            shipIcon(c, lx, ly, 9f, Palette.CYAN)
            lx += 24f
        }
        val maxShield = world.loadout.maxShield()
        if (maxShield > 0) {
            lx += 8f
            for (i in 0 until maxShield) {
                val filled = i < p.shield
                Neon.ring(c, lx, ly, 7f, fade(Palette.LIME, if (filled) 0.95f else 0.22f), 1.8f, if (filled) 0.9f else 0.2f)
                if (filled) Neon.orb(c, lx, ly, 2.6f, fade(Palette.LIME, 0.9f), 0.7f)
                lx += 18f
            }
        }

        // weapon pips
        for (i in 0 until world.loadout.maxWeapon()) {
            val on = i < p.weapon
            val x = 24f + i * 15f
            val y = h - bottom - 48f
            Neon.fillRect(c, x - 5f, y - 3f, x + 5f, y + 3f, fade(if (on) Palette.CYAN else Palette.DIM, if (on) 0.95f else 0.25f))
        }


        if (world.coop) {
            val partner = world.slots[1].player
            Neon.label(
                c, if (partner.lives > 0) "CO-OP" else "PARTNER DOWN", w - 20f, top + 44f, 11f,
                if (partner.lives > 0) Palette.ROSE else Palette.RED,
                Paint.Align.RIGHT, 0.4f, 0.2f, Neon.FONT_BODY
            )
        }
        drawBadges(c, world)
        drawOverdrive(c, world, time)
        if (showBanner) drawBanner(c, world, time)
    }

    private fun drawOverdrive(c: Canvas, world: World, time: Float) {
        val p = world.player
        val x = overdrive.cx
        val y = overdrive.cy
        val r = 30f
        val ready = world.canOverdrive()
        val active = p.odTime > 0f
        val charge = if (active) p.odTime / World.OD_DURATION else p.overdrive
        val col = if (active) Palette.ROSE else if (ready) Palette.AMBER else Palette.VIOLET

        Neon.ring(c, x, y, r, fade(col, 0.35f), 2f, 0.5f)
        arc.set(x - r, y - r, x + r, y + r)
        arcPaint.reset()
        arcPaint.isAntiAlias = true
        arcPaint.style = Paint.Style.STROKE
        arcPaint.strokeCap = Paint.Cap.ROUND
        arcPaint.strokeWidth = 6f
        arcPaint.color = fade(col, 0.25f)
        c.drawArc(arc, -90f, 360f * clamp(charge, 0f, 1f), false, arcPaint)
        arcPaint.strokeWidth = 3.4f
        arcPaint.color = col
        c.drawArc(arc, -90f, 360f * clamp(charge, 0f, 1f), false, arcPaint)

        if (ready || active) {
            val pulse = 0.6f + 0.4f * sin(time * 8f)
            Neon.ring(c, x, y, r * (1.18f + 0.06f * pulse), fade(col, 0.5f * pulse), 1.6f, 0.9f)
        }
        Neon.label(c, if (active) "!!" else "OD", x, y + 7f, 21f, if (ready || active) col else fade(col, 0.5f), Paint.Align.CENTER, if (ready) 1f else 0.3f, 0.14f)
    }

    private fun drawBanner(c: Canvas, world: World, time: Float) {
        if (world.bannerT <= 0f) return
        val t = clamp(world.bannerT, 0f, 1f)
        val y = h * 0.34f
        val alarm = world.banner == "WARNING"
        val col = if (alarm) Palette.RED else Palette.CYAN
        val a = if (alarm) (0.6f + 0.4f * sin(time * 14f)) * t else t
        // shrink to fit rather than running off both edges
        var size = 44f
        val maxW = w * 0.88f
        val measured = Neon.textWidth(world.banner, size, 0.25f)
        if (measured > maxW) size *= maxW / measured
        Neon.label(c, world.banner, w * 0.5f, y, size, fade(col, a), Paint.Align.CENTER, 1f, 0.25f)
        if (world.bannerSub.isNotEmpty()) {
            Neon.label(c, world.bannerSub, w * 0.5f, y + 32f, 18f, fade(Palette.VIOLET, a), Paint.Align.CENTER, 0.7f, 0.28f)
        }
    }

    // --------------------------------------------------------------- menus

    fun drawMenu(c: Canvas, time: Float) {
        val cx = w * 0.5f
        val menuTop = h * 0.545f
        val float = sin(time * 1.4f) * 5f

        shipIcon(c, cx, h * 0.30f + float, 26f, Palette.CYAN)

        Neon.label(c, "NEON", cx, h * 0.42f, 62f, Palette.MAGENTA, Paint.Align.CENTER, 1f, 0.32f)
        Neon.label(c, "VOID", cx, h * 0.42f + 58f, 62f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.32f)
        Neon.label(c, "SURVIVE THE GRID", cx, h * 0.42f + 90f, 15f, Palette.VIOLET, Paint.Align.CENTER, 0.6f, 0.4f)

        haptic.label = if (prefs.hapticsOn) "HAPTIC" else "HAPTIC"
        haptic.color = if (prefs.hapticsOn) Palette.VIOLET else Palette.DIM
        music.label = "MUSIC"
        music.color = if (prefs.musicOn) Palette.VIOLET else Palette.DIM
        sfx.label = "SFX"
        sfx.color = if (prefs.sfxOn) Palette.VIOLET else Palette.DIM
        hangar.label = "HANGAR"
        var open = 0
        for (i in Levels.list.indices) if (Levels.unlocked(i, prefs)) open++
        levels.label = "LEVELS  $open/${Levels.list.size}"
        drawButton(c, play, time, true)
        drawButton(c, levels, time)
        drawButton(c, hangar, time)
        drawButton(c, shop, time)
        drawButton(c, records, time)
        drawButton(c, coop, time)
        drawButton(c, music, time)
        drawButton(c, sfx, time)
        drawButton(c, haptic, time)

        val start = Levels.list[prefs.startLevel.coerceIn(0, Levels.list.size - 1)]
        Neon.label(
            c, "FLYING  ${ShipDex.byId(prefs.selectedShip).name}", cx, menuTop + 246f, 11f,
            ShipDex.byId(prefs.selectedShip).color, Paint.Align.CENTER, 0.4f, 0.2f, Neon.FONT_BODY
        )
        Neon.label(
            c, "LAUNCHING FROM  ${start.name}", cx, menuTop + 262f, 11f,
            fade(start.accent, 0.9f), Paint.Align.CENTER, 0.4f, 0.2f, Neon.FONT_BODY
        )

        val statY = h * 0.845f
        Neon.label(c, "BEST  ${formatScore(prefs.bestScore)}", cx, statY, 20f, Palette.AMBER, Paint.Align.CENTER, 0.7f, 0.14f, Neon.FONT_NUM)
        Neon.label(c, "WAVE ${prefs.bestWave}   LEVEL ${prefs.bestLevel}   ${prefs.cores} CORES", cx, statY + 22f, 12.5f, Palette.DIM, Paint.Align.CENTER, 0.4f, 0.14f)

        Neon.label(c, "DRAG ANYWHERE TO FLY  -  AUTO FIRE", cx, h - bottom - 40f, 13f, Palette.SKY, Paint.Align.CENTER, 0.5f, 0.2f)
        Neon.label(c, "GRAZE BULLETS TO CHARGE OVERDRIVE", cx, h - bottom - 20f, 13f, Palette.AMBER, Paint.Align.CENTER, 0.5f, 0.2f)
    }

    fun drawPause(c: Canvas, world: World, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xCC05020C.toInt())
        Neon.label(c, "PAUSED", w * 0.5f, h * 0.26f, 46f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.3f)
        drawLoadout(c, world)
        drawButton(c, resume, time, true)
        drawButton(c, restart, time)
        drawButton(c, quit, time)
    }

    fun drawGameOver(
        c: Canvas, world: World, newBest: Boolean, time: Float,
        unlocked: List<Int> = emptyList()
    ) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xE005020C.toInt())
        val cx = w * 0.5f
        Neon.label(c, "SIGNAL LOST", cx, h * 0.24f, 44f, Palette.RED, Paint.Align.CENTER, 1f, 0.24f)

        if (newBest) {
            val a = 0.55f + 0.45f * sin(time * 7f)
            Neon.label(c, "NEW RECORD", cx, h * 0.30f, 20f, fade(Palette.AMBER, a), Paint.Align.CENTER, 1f, 0.4f)
        }

        Neon.label(c, formatScore(world.score), cx, h * 0.40f, 56f, Palette.WHITE, Paint.Align.CENTER, 1f, 0.05f, Neon.FONT_NUM)
        Neon.label(c, "SCORE", cx, h * 0.40f + 24f, 13f, Palette.DIM, Paint.Align.CENTER, 0.4f, 0.35f)

        val rowY = h * 0.50f
        statCell(c, cx - w * 0.26f, rowY, "WAVE", world.wave.toString(), Palette.VIOLET)
        statCell(c, cx, rowY, "KILLS", world.kills.toString(), Palette.CYAN)
        statCell(c, cx + w * 0.26f, rowY, "COMBO", "x${world.maxCombo}", Palette.MAGENTA)

        Neon.label(c, "BEST  ${formatScore(prefs.bestScore)}", cx, h * 0.575f, 16f, Palette.AMBER, Paint.Align.CENTER, 0.6f, 0.16f, Neon.FONT_NUM)

        if (unlocked.isNotEmpty()) {
            val a = 0.6f + 0.4f * sin(time * 5f)
            Neon.label(c, "SECTOR UNLOCKED", cx, h * 0.615f, 13f, fade(Palette.LIME, a), Paint.Align.CENTER, 0.6f, 0.34f)
            var y = h * 0.615f + 20f
            for (i in unlocked.take(3)) {
                val theme = Levels.list[i]
                Neon.label(c, "${i + 1}. ${theme.name}", cx, y, 15f, theme.accent, Paint.Align.CENTER, 0.6f, 0.12f)
                y += 19f
            }
        }

        drawButton(c, retry, time, true)
        drawButton(c, toMenu, time)
    }

    private fun statCell(c: Canvas, x: Float, y: Float, label: String, value: String, color: Int) {
        Neon.label(c, value, x, y, 28f, color, Paint.Align.CENTER, 0.8f, 0.04f, Neon.FONT_NUM)
        Neon.label(c, label, x, y + 20f, 12f, Palette.DIM, Paint.Align.CENTER, 0.35f, 0.3f)
    }

    // -------------------------------------------------------------- hangar

    private val statBuf = ArrayList<String>(8)

    private fun hullIcon(c: Canvas, ship: Ship, x: Float, y: Float, s: Float, alpha: Float) {
        val path = Hulls.of(ship)
        c.save()
        c.translate(x, y)
        c.scale(s, s)
        Neon.fillPath(c, path, fade(ship.color, 0.18f * alpha))
        Neon.path(c, path, fade(ship.color, alpha), 1.7f / s, 0.9f, 0.8f)
        c.restore()
    }

    fun drawHangar(c: Canvas, selected: Int, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF203010A.toInt())
        val cx = w * 0.5f
        val ship = ShipDex.byId(selected)
        val owned = prefs.ownedShips

        Neon.label(c, "HANGAR", cx, h * 0.075f, 34f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.3f)
        Neon.label(c, "${prefs.cores} CORES", w - 20f, h * 0.075f, 16f, Palette.AMBER, Paint.Align.RIGHT, 0.6f, 0.12f, Neon.FONT_NUM)
        Neon.label(c, "${ShipDex.ownedCount(owned)}/${ShipDex.list.size} HULLS", 20f, h * 0.075f, 13f, Palette.DIM, Paint.Align.LEFT, 0.3f, 0.16f, Neon.FONT_BODY)

        // selected hull panel
        val py = h * 0.215f
        Neon.panel(c, 18f, h * 0.115f, w - 18f, h * 0.40f, 14f, fade(ship.color, 0.07f), fade(ship.color, 0.75f), 1.8f, 0.8f)
        hullIcon(c, ship, w * 0.24f, py + 6f + sin(time * 1.6f) * 4f, 34f, 1f)
        Neon.label(c, ship.name, w * 0.44f, py - 22f, 28f, ship.color, Paint.Align.LEFT, 0.9f, 0.1f)
        Neon.label(c, Rarity.names[ship.rarity], w * 0.44f, py - 2f, 12f, Rarity.colors[ship.rarity], Paint.Align.LEFT, 0.6f, 0.3f)

        ship.statLines(statBuf)
        var sy = py + 22f
        var col = 0
        for (line in statBuf) {
            val lx = if (col == 0) w * 0.44f else w * 0.72f
            Neon.label(c, line, lx, sy, 12f, Palette.SKY, Paint.Align.LEFT, 0.3f, 0.06f, Neon.FONT_BODY)
            if (col == 1) sy += 17f
            col = 1 - col
        }
        Neon.label(c, ship.signatureText, w * 0.5f, h * 0.355f, 12.5f,
            if (ship.signature >= 0) Palette.AMBER else Palette.DIM, Paint.Align.CENTER, 0.5f, 0.16f)
        Neon.label(c, ship.blurb, w * 0.5f, h * 0.378f, 12f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.02f, Neon.FONT_BODY)

        // roster grid
        for (i in shipCells.indices) {
            val cell = shipCells[i]
            val s2 = ShipDex.list[i]
            val have = ShipDex.isOwned(owned, s2.id)
            val isSel = s2.id == selected
            val edge = when {
                isSel -> Palette.WHITE
                have -> Rarity.colors[s2.rarity]
                else -> Palette.DIM
            }
            val l = cell.cx - cell.w / 2
            val t = cell.cy - cell.h / 2
            Neon.panel(c, l, t, l + cell.w, t + cell.h, 9f,
                fade(if (have) s2.color else Palette.DIM, if (isSel) 0.20f else 0.06f),
                fade(edge, if (have) 0.9f else 0.35f), if (isSel) 2.2f else 1.3f, if (isSel) 1f else 0.4f)
            if (have) {
                hullIcon(c, s2, cell.cx, cell.cy - 4f, 11.5f, 1f)
                Neon.label(c, s2.name, cell.cx, t + cell.h - 6f, 7f, s2.color, Paint.Align.CENTER, 0.2f, 0f, Neon.FONT_BODY)
            } else {
                Neon.label(c, "?", cell.cx, cell.cy + 5f, 19f, fade(Palette.DIM, 0.7f), Paint.Align.CENTER, 0.3f, 0f)
                Neon.label(c, Rarity.names[s2.rarity].take(4), cell.cx, t + cell.h - 6f, 6.5f, fade(Rarity.colors[s2.rarity], 0.5f), Paint.Align.CENTER, 0.2f, 0.06f, Neon.FONT_BODY)
            }
        }

        val canPull = prefs.cores >= ShipDex.PULL_COST
        val canPull10 = prefs.cores >= ShipDex.PULL_COST * 10
        summon.label = "x1  ${ShipDex.PULL_COST}"
        summon.color = if (canPull) Palette.AMBER else Palette.DIM
        summon10.label = "x10  ${ShipDex.PULL_COST * 10}"
        summon10.color = if (canPull10) Palette.AMBER else Palette.DIM
        drawButton(c, summon, time, canPull)
        drawButton(c, summon10, time, canPull10)
        drawButton(c, back, time)
        Neon.label(c, "A TEN-PULL GUARANTEES RARE OR BETTER", cx, h * 0.755f, 10.5f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.12f, Neon.FONT_BODY)
        Neon.label(c, "EARN CORES BY FLYING - SCORE AND WAVES BOTH PAY", cx, h * 0.745f, 11f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY)
    }

    /** Lobby for LAN co-op: pick a side, then host or dial in. */
    fun drawCoop(
        c: Canvas,
        stage: Int,
        statusTitle: String,
        statusLine: String,
        address: String,
        typed: String,
        canLaunch: Boolean,
        time: Float
    ) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF203010A.toInt())
        val cx = w * 0.5f
        val pulse = 0.6f + 0.4f * sin(time * 4f)
        Neon.label(c, "CO-OP", cx, h * 0.10f, 34f, Palette.ROSE, Paint.Align.CENTER, 1f, 0.32f)
        Neon.label(c, "TWO SHIPS, ONE RUN, SAME WI-FI", cx, h * 0.10f + 24f, 11f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.18f, Neon.FONT_BODY)

        when (stage) {
            0 -> {   // choose a side
                drawButton(c, hostGame, time, true)
                drawButton(c, joinGame, time, true)
                Neon.label(c, "THE HOST RUNS THE GAME.", cx, h * 0.56f, 12f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.08f, Neon.FONT_BODY)
                Neon.label(c, "BOTH PHONES MUST BE ON THE SAME NETWORK.", cx, h * 0.56f + 18f, 12f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.08f, Neon.FONT_BODY)
            }
            1 -> {   // hosting
                Neon.label(c, statusTitle, cx, h * 0.30f, 22f, fade(Palette.CYAN, pulse), Paint.Align.CENTER, 0.8f, 0.2f)
                Neon.panel(c, w * 0.12f, h * 0.36f, w * 0.88f, h * 0.46f, 12f, fade(Palette.CYAN, 0.08f), fade(Palette.CYAN, 0.7f), 1.8f, 0.8f)
                Neon.label(c, "YOUR ADDRESS", cx, h * 0.39f, 11f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.24f, Neon.FONT_BODY)
                Neon.label(c, address, cx, h * 0.435f, 26f, Palette.WHITE, Paint.Align.CENTER, 0.7f, 0.06f, Neon.FONT_NUM)
                Neon.label(c, statusLine, cx, h * 0.52f, 14f, Palette.LIME, Paint.Align.CENTER, 0.5f, 0.16f)
                startCoop.color = if (canLaunch) Palette.AMBER else Palette.DIM
                drawButton(c, startCoop, time, canLaunch)
            }
            else -> { // joining
                Neon.label(c, "HOST ADDRESS", cx, h * 0.26f, 13f, Palette.DIM, Paint.Align.CENTER, 0.3f, 0.24f, Neon.FONT_BODY)
                Neon.panel(c, w * 0.14f, h * 0.285f, w * 0.86f, h * 0.345f, 10f, fade(Palette.LIME, 0.07f), fade(Palette.LIME, 0.7f), 1.6f, 0.6f)
                Neon.label(c, if (typed.isEmpty()) "___.___.___.___" else typed, cx, h * 0.328f, 24f, Palette.WHITE, Paint.Align.CENTER, 0.6f, 0.06f, Neon.FONT_NUM)
                for (i in keypad.indices) {
                    val b = keypad[i]
                    b.label = when (i) {
                        9 -> "."
                        10 -> "0"
                        11 -> "DEL"
                        else -> (i + 1).toString()
                    }
                    b.color = if (i == 11) Palette.RED else Palette.SKY
                    drawButton(c, b, time)
                }
                drawButton(c, connectBtn, time, typed.isNotEmpty())
                Neon.label(c, statusLine, cx, h * 0.775f, 14f,
                    if (statusLine.startsWith("C")) Palette.LIME else Palette.RED,
                    Paint.Align.CENTER, 0.5f, 0.16f)
            }
        }
        drawButton(c, back, time)
    }

    /** Overlay while the partner is choosing their augment. */
    fun drawPartnerPicking(c: Canvas, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xE603010A.toInt())
        val cx = w * 0.5f
        val pulse = 0.55f + 0.45f * sin(time * 4f)
        Neon.label(c, "PARTNER IS CHOOSING", cx, h * 0.45f, 26f, fade(Palette.ROSE, pulse), Paint.Align.CENTER, 1f, 0.24f)
        Neon.label(c, "YOUR UPGRADE IS ALREADY INSTALLED", cx, h * 0.45f + 30f, 13f, Palette.DIM, Paint.Align.CENTER, 0.3f, 0.18f, Neon.FONT_BODY)
        for (i in 0 until 3) {
            val a = time * 2.4f + i * 0.6f
            Neon.orb(c, cx + (i - 1) * 26f, h * 0.53f, 4.5f + 2.5f * sin(a), fade(Palette.ROSE, 0.5f + 0.5f * sin(a)), 1f)
        }
    }

    fun drawShop(c: Canvas, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF203010A.toInt())
        val cx = w * 0.5f
        Neon.label(c, "SHOP", cx, h * 0.075f, 34f, Palette.LIME, Paint.Align.CENTER, 1f, 0.3f)
        Neon.label(c, "${prefs.cores} CORES", w - 20f, h * 0.075f, 16f, Palette.AMBER, Paint.Align.RIGHT, 0.6f, 0.12f, Neon.FONT_NUM)
        Neon.label(c, "PERMANENT - CARRIES INTO EVERY RUN", cx, h * 0.075f + 22f, 11f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.16f, Neon.FONT_BODY)

        for (i in Shop.items.indices) {
            val item = Shop.items[i]
            val b = shopRows[i]
            val level = prefs.shopLevel(item.id)
            val cost = Shop.cost(item.id, level)
            val maxed = cost < 0
            val affordable = !maxed && prefs.cores >= cost
            b.enabled = !maxed
            val l = b.cx - b.w / 2
            val t = b.cy - b.h / 2
            val r = b.cx + b.w / 2
            val bot = b.cy + b.h / 2
            val edge = when {
                maxed -> Palette.DIM
                affordable -> item.color
                else -> Palette.DIM
            }
            Neon.panel(c, l, t, r, bot, 12f, fade(item.color, if (b.pressed) 0.24f else 0.07f),
                fade(edge, if (affordable) 0.9f else 0.4f), 1.7f, if (affordable) 0.9f else 0.3f)
            Neon.label(c, item.name, l + 16f, t + 24f, 17f, item.color, Paint.Align.LEFT, 0.7f, 0.12f)
            Neon.label(c, item.desc, l + 16f, t + 44f, 11.5f, Palette.DIM, Paint.Align.LEFT, 0.25f, 0.01f, Neon.FONT_BODY)
            for (pip in 0 until item.maxLevel) {
                val px = l + 16f + pip * 15f
                Neon.fillRect(c, px, t + 56f, px + 11f, t + 63f,
                    fade(if (pip < level) item.color else Palette.DIM, if (pip < level) 0.95f else 0.2f))
            }
            if (maxed) {
                Neon.label(c, "MAX", r - 20f, b.cy + 6f, 16f, Palette.DIM, Paint.Align.RIGHT, 0.4f, 0.2f)
            } else {
                Neon.label(c, cost.toString(), r - 20f, b.cy + 2f, 20f,
                    if (affordable) Palette.AMBER else Palette.DIM, Paint.Align.RIGHT, 0.6f, 0.06f, Neon.FONT_NUM)
                Neon.label(c, "CORES", r - 20f, b.cy + 18f, 9f, Palette.DIM, Paint.Align.RIGHT, 0.25f, 0.16f, Neon.FONT_BODY)
            }
        }
        drawButton(c, back, time)
    }

    fun drawRecords(c: Canvas, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF203010A.toInt())
        val cx = w * 0.5f
        Neon.label(c, "RECORDS", cx, h * 0.10f, 34f, Palette.SKY, Paint.Align.CENTER, 1f, 0.3f)

        Neon.label(c, formatScore(prefs.bestScore), cx, h * 0.20f, 50f, Palette.AMBER, Paint.Align.CENTER, 1f, 0.05f, Neon.FONT_NUM)
        Neon.label(c, "BEST SCORE", cx, h * 0.20f + 22f, 12f, Palette.DIM, Paint.Align.CENTER, 0.35f, 0.34f)

        val rows = arrayOf(
            "FURTHEST WAVE" to prefs.bestWave.toString(),
            "LEVELS CLEARED" to prefs.bestLevel.toString(),
            "BEST COMBO" to "x${prefs.bestCombo}",
            "RUNS FLOWN" to prefs.runs.toString(),
            "TOTAL KILLS" to formatScore(prefs.totalKills),
            "CORES EARNED" to formatScore(prefs.totalCores),
            "CORES HELD" to formatScore(prefs.cores),
            "HULLS OWNED" to "${ShipDex.ownedCount(prefs.ownedShips)}/${ShipDex.list.size}",
            "SUMMONS" to prefs.pulls.toString()
        )
        var y = h * 0.30f
        for ((label, value) in rows) {
            Neon.label(c, label, cx - 14f, y, 14f, Palette.DIM, Paint.Align.RIGHT, 0.3f, 0.14f, Neon.FONT_BODY)
            Neon.label(c, value, cx + 18f, y, 16f, Palette.SKY, Paint.Align.LEFT, 0.5f, 0.04f, Neon.FONT_NUM)
            y += 30f
        }

        Neon.label(c, "LEVELS", cx, y + 18f, 12f, Palette.VIOLET, Paint.Align.CENTER, 0.4f, 0.3f)
        y += 40f
        for (i in Levels.list.indices) {
            val theme = Levels.list[i]
            val seen = prefs.bestLevel > i
            val col = if (seen) theme.accent else Palette.DIM
            val row = i / 2
            val col2 = i % 2
            val lx = if (col2 == 0) cx - w * 0.24f else cx + w * 0.02f
            Neon.label(c, "${i + 1}. ${if (seen) theme.name else "- - - -"}", lx, y + row * 22f, 12.5f,
                fade(col, if (seen) 0.95f else 0.35f), Paint.Align.LEFT, 0.3f, 0.08f, Neon.FONT_BODY)
        }
        drawButton(c, back, time)
    }

    /**
     * Level select. Every sector is listed from the start so the run ahead is
     * visible; the locked ones show what they are waiting for rather than
     * hiding behind a blank.
     */
    fun drawLevels(c: Canvas, selected: Int, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF203010A.toInt())
        val cx = w * 0.5f
        Neon.label(c, "SECTORS", cx, h * 0.075f, 34f, Palette.MAGENTA, Paint.Align.CENTER, 1f, 0.3f)
        Neon.label(
            c, "PICK WHERE THE RUN BEGINS - IT NEVER GETS EASIER", cx, h * 0.075f + 22f, 10.5f,
            Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY
        )

        for (i in levelCells.indices) {
            val theme = Levels.list[i]
            val b = levelCells[i]
            val open = Levels.unlocked(i, prefs)
            val chosen = open && i == selected
            b.enabled = open
            val l = b.cx - b.w / 2
            val t = b.cy - b.h / 2
            val r = b.cx + b.w / 2
            val bot = b.cy + b.h / 2
            val edge = if (open) theme.accent else Palette.DIM
            val glow = when {
                b.pressed -> 0.30f
                chosen -> 0.20f
                open -> 0.07f
                else -> 0.04f
            }
            Neon.panel(
                c, l, t, r, bot, 12f, fade(theme.accent, glow),
                fade(edge, if (chosen) 1f else if (open) 0.7f else 0.3f),
                if (chosen) 2.4f else 1.6f, if (open) 0.9f else 0.25f
            )
            Neon.label(
                c, "0${i + 1}".takeLast(2), l + 12f, t + 26f, 17f,
                fade(edge, if (open) 0.95f else 0.4f), Paint.Align.LEFT, 0.5f, 0.06f, Neon.FONT_NUM
            )
            Neon.label(
                c, theme.name, l + 44f, t + 25f, 14f,
                fade(theme.accent, if (open) 1f else 0.3f), Paint.Align.LEFT, if (open) 0.6f else 0.2f, 0.06f
            )
            if (open) {
                Neon.label(
                    c, theme.subtitle, l + 44f, t + 40f, 9.5f, Palette.DIM,
                    Paint.Align.LEFT, 0.25f, 0.06f, Neon.FONT_BODY
                )
                Neon.label(
                    c, "${theme.roster.size} ENEMY TYPES", l + 12f, bot - 28f, 9.5f,
                    fade(theme.accent, 0.55f), Paint.Align.LEFT, 0.2f, 0.08f, Neon.FONT_BODY
                )
                Neon.label(
                    c, theme.bossPool.joinToString("  ") { BT.names[it] }, l + 12f, bot - 14f, 9.5f,
                    fade(Palette.RED, 0.6f), Paint.Align.LEFT, 0.2f, 0.08f, Neon.FONT_BODY
                )
                if (chosen) {
                    Neon.label(
                        c, "SELECTED", r - 12f, bot - 14f, 9.5f, Palette.WHITE,
                        Paint.Align.RIGHT, 0.45f, 0.22f, Neon.FONT_BODY
                    )
                }
            } else {
                lockIcon(c, r - 20f, t + 21f, fade(Palette.DIM, 0.7f))
                Neon.label(
                    c, "LOCKED", l + 44f, t + 40f, 9.5f, Palette.DIM,
                    Paint.Align.LEFT, 0.25f, 0.22f, Neon.FONT_BODY
                )
                Neon.label(
                    c, Levels.requirement(i), l + 12f, bot - 16f, 11f, fade(Palette.AMBER, 0.8f),
                    Paint.Align.LEFT, 0.35f, 0.06f, Neon.FONT_BODY
                )
            }
        }

        val picked = Levels.list[selected.coerceIn(0, Levels.list.size - 1)]
        launch.label = "LAUNCH"
        launch.color = picked.accent
        drawButton(c, launch, time, true)
        Neon.label(
            c, "BEGINS AT WAVE 1 OF ${picked.name}", cx, launch.cy + 44f, 10.5f,
            Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY
        )
        drawButton(c, back, time)
    }

    private fun lockIcon(c: Canvas, x: Float, y: Float, color: Int) {
        // shackle first, then the body covers its lower half
        Neon.ring(c, x, y - 1f, 4.2f, color, 1.8f, 0.4f)
        Neon.fillRect(c, x - 6f, y - 1f, x + 6f, y + 8f, color)
    }

    fun drawReveal(c: Canvas, ship: Ship, isNew: Boolean, refund: Int, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF003010A.toInt())
        val cx = w * 0.5f
        val pulse = 0.6f + 0.4f * sin(time * 5f)
        val rc = Rarity.colors[ship.rarity]

        for (i in 0 until 3) {
            Neon.ring(c, cx, h * 0.4f, 90f + i * 46f + sin(time * 2f + i) * 8f, fade(rc, (0.32f - i * 0.08f) * pulse), 2.2f, 1f)
        }
        Neon.label(c, Rarity.names[ship.rarity], cx, h * 0.24f, 20f, fade(rc, pulse), Paint.Align.CENTER, 1f, 0.4f)
        hullIcon(c, ship, cx, h * 0.4f + sin(time * 1.8f) * 6f, 52f, 1f)
        Neon.label(c, ship.name, cx, h * 0.56f, 40f, ship.color, Paint.Align.CENTER, 1f, 0.16f)

        if (isNew) {
            Neon.label(c, "NEW HULL UNLOCKED", cx, h * 0.61f, 16f, Palette.LIME, Paint.Align.CENTER, 0.8f, 0.26f)
            Neon.label(c, ship.signatureText, cx, h * 0.65f, 13f, if (ship.signature >= 0) Palette.AMBER else Palette.DIM, Paint.Align.CENTER, 0.5f, 0.16f)
            Neon.label(c, ship.blurb, cx, h * 0.685f, 12f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.02f, Neon.FONT_BODY)
        } else {
            Neon.label(c, "DUPLICATE", cx, h * 0.61f, 16f, Palette.DIM, Paint.Align.CENTER, 0.5f, 0.26f)
            Neon.label(c, "+$refund CORES", cx, h * 0.655f, 22f, Palette.AMBER, Paint.Align.CENTER, 0.8f, 0.12f, Neon.FONT_NUM)
        }
        Neon.label(c, "TAP TO CONTINUE", cx, h * 0.85f, 13f, fade(Palette.SKY, pulse), Paint.Align.CENTER, 0.5f, 0.3f, Neon.FONT_BODY)
    }

    /** Results grid for a ten-pull. */
    fun drawRevealMulti(c: Canvas, results: List<Ship>, newFlags: List<Boolean>, refund: Int, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xF003010A.toInt())
        val cx = w * 0.5f
        val pulse = 0.6f + 0.4f * sin(time * 5f)
        val newCount = newFlags.count { it }
        val best = results.maxOfOrNull { it.rarity } ?: 0

        Neon.label(c, "TEN-PULL", cx, h * 0.10f, 32f, fade(Rarity.colors[best], pulse), Paint.Align.CENTER, 1f, 0.32f)
        Neon.label(c, if (newCount > 0) "$newCount NEW HULLS" else "NO NEW HULLS",
            cx, h * 0.10f + 26f, 14f, if (newCount > 0) Palette.LIME else Palette.DIM, Paint.Align.CENTER, 0.6f, 0.24f)

        val cols = 2
        val cellW = w * 0.42f
        val cellH = 74f
        for (i in results.indices) {
            val s2 = results[i]
            val isNew = newFlags.getOrElse(i) { false }
            val col = i % cols
            val row = i / cols
            val bx = cx + (col - (cols - 1) / 2f) * (cellW + 12f)
            val by = h * 0.22f + row * (cellH + 10f)
            val rc = Rarity.colors[s2.rarity]
            Neon.panel(c, bx - cellW / 2, by - cellH / 2, bx + cellW / 2, by + cellH / 2, 10f,
                fade(s2.color, if (isNew) 0.18f else 0.05f), fade(rc, if (isNew) 1f else 0.45f),
                if (isNew) 2.2f else 1.2f, if (isNew) 1f else 0.35f)
            hullIcon(c, s2, bx - cellW * 0.32f, by, 15f, if (isNew) 1f else 0.5f)
            Neon.label(c, s2.name, bx - cellW * 0.16f, by - 2f, 15f, fade(s2.color, if (isNew) 1f else 0.6f), Paint.Align.LEFT, 0.4f, 0.06f)
            Neon.label(c, if (isNew) "NEW" else Rarity.names[s2.rarity], bx - cellW * 0.16f, by + 16f, 9.5f,
                fade(if (isNew) Palette.LIME else rc, 0.8f), Paint.Align.LEFT, 0.3f, 0.14f, Neon.FONT_BODY)
        }
        if (refund > 0) {
            Neon.label(c, "+$refund CORES REFUNDED", cx, h * 0.66f, 17f, Palette.AMBER, Paint.Align.CENTER, 0.6f, 0.16f, Neon.FONT_NUM)
        }
        Neon.label(c, "TAP TO CONTINUE", cx, h * 0.74f, 13f, fade(Palette.SKY, pulse), Paint.Align.CENTER, 0.5f, 0.3f, Neon.FONT_BODY)
    }

    fun setPressed(b: Button?, value: Boolean) {
        b?.pressed = value
    }

    // ------------------------------------------------------------ augments

    private fun wrap(text: String, size: Float, maxW: Float, out: MutableList<String>) {
        out.clear()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (Neon.textWidth(candidate, size, 0.02f, Neon.FONT_BODY) > maxW && line.isNotEmpty()) {
                out.add(line.toString())
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
            if (out.size >= 2) break
        }
        if (line.isNotEmpty() && out.size < 3) out.add(line.toString())
    }

    /** Called once when the offer is rolled - lays the cards out and wraps their copy. */
    fun prepareCards(offers: List<AugCard>) {
        cardCount = minOf(offers.size, cards.size)
        val cardW = w * 0.84f
        val cardH = 128f
        val gap = 18f
        val total = cardCount * cardH + (cardCount - 1) * gap
        var y = h * 0.5f - total * 0.5f + cardH * 0.5f
        for (i in 0 until cardCount) {
            val v = cards[i]
            v.card = offers[i]
            v.btn.place(w * 0.5f, y, cardW, cardH)
            v.btn.color = offers[i].color
            v.btn.enabled = true
            v.btn.pressed = false
            wrap(offers[i].body, 15f, cardW - 118f, v.lines)
            y += cardH + gap
        }
        for (i in cardCount until cards.size) {
            cards[i].card = null
            cards[i].btn.enabled = false
        }
    }

    fun drawAugment(c: Canvas, world: World, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xE603010A.toInt())

        val cx = w * 0.5f
        Neon.label(c, "SYSTEM UPGRADE", cx, h * 0.16f, 30f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.28f)
        val hasEvolution = (0 until cardCount).any { cards[it].card?.branchPick != 0 }
        val sub = if (hasEvolution) "AN AUGMENT IS READY TO SPLIT - CHOOSE A PATH" else "WAVE ${world.wave} CLEARED - TAP A CARD TO INSTALL"
        Neon.label(c, sub, cx, h * 0.16f + 26f, 13f, if (hasEvolution) Palette.AMBER else Palette.VIOLET, Paint.Align.CENTER, 0.6f, 0.2f)
        val used = world.loadout.slotsUsed()
        val slotText = if (world.loadout.slotsFull()) "BAY FULL $used/${Aug.MAX_SLOTS} - LEVEL-UPS ONLY" else "BAY $used/${Aug.MAX_SLOTS}"
        Neon.label(c, slotText, cx, h * 0.16f + 48f, 12f, if (world.loadout.slotsFull()) Palette.AMBER else Palette.DIM, Paint.Align.CENTER, 0.4f, 0.24f, Neon.FONT_BODY)

        for (i in 0 until cardCount) {
            val v = cards[i]
            val card = v.card ?: continue
            val b = v.btn
            val l = b.cx - b.w / 2
            val r = b.cx + b.w / 2
            val t = b.cy - b.h / 2
            val bot = b.cy + b.h / 2
            val evo = card.branchPick != 0
            val pulse = if (b.pressed) 1f else 0.7f + 0.3f * sin(time * 3f + i * 0.7f)

            Neon.panel(c, l, t, r, bot, 16f, fade(card.color, if (b.pressed) 0.26f else 0.10f), fade(card.color, pulse), if (evo) 2.6f else 1.9f, 1f)
            if (evo) {
                Neon.panel(c, l + 5f, t + 5f, r - 5f, bot - 5f, 12f, 0, fade(Palette.AMBER, 0.35f * pulse), 1f, 0.6f)
            }

            Neon.label(c, card.tag, l + 22f, t + 26f, 12f, fade(card.color, 0.85f), Paint.Align.LEFT, 0.5f, 0.26f)
            Neon.label(c, card.title, l + 22f, t + 58f, 28f, card.color, Paint.Align.LEFT, 0.9f, 0.1f)
            var ty = t + 82f
            for (line in v.lines) {
                Neon.label(c, line, l + 22f, ty, 15f, Palette.DIM, Paint.Align.LEFT, 0.3f, 0.02f, Neon.FONT_BODY)
                ty += 19f
            }

            // level pips on the right edge
            val maxPips = if (Aug.isAbility(card.id)) Aug.EVOLVED_MAX else Aug.statMax[card.id]
            val have = world.loadout.lvl[card.id]
            val next = if (evo) have else have + 1
            for (pip in 0 until maxPips) {
                val py = t + 34f + pip * 15f
                val on = pip < next
                val fresh = pip == next - 1
                Neon.fillRect(
                    c, r - 34f, py, r - 20f, py + 8f,
                    fade(if (on) card.color else Palette.DIM, if (fresh) 1f else if (on) 0.55f else 0.18f)
                )
            }
        }
    }

    // -------------------------------------------------------------- badges

    private fun drawBadges(c: Canvas, world: World) {
        world.loadout.ownedList(badgeIds)
        if (badgeIds.isEmpty()) return
        val size = 26f
        val step = 28f
        var x = 20f
        val y = h - bottom - 74f
        Neon.label(
            c, "AUGMENTS ${world.loadout.slotsUsed()}/${Aug.MAX_SLOTS}", 20f, y - 6f, 10f,
            if (world.loadout.slotsFull()) Palette.AMBER else Palette.DIM,
            Paint.Align.LEFT, 0.3f, 0.22f, Neon.FONT_BODY
        )
        for (id in badgeIds) {
            if (x + size > w - 108f) break
            val col = Aug.colors[id]
            val evolved = Aug.isAbility(id) && world.loadout.branch[id] != 0
            Neon.panel(c, x, y, x + size, y + size, 6f, fade(col, if (evolved) 0.28f else 0.12f), fade(col, if (evolved) 1f else 0.6f), 1.3f, 0.5f)
            Neon.label(c, Aug.codes[id], x + size * 0.5f, y + 13f, 10.5f, col, Paint.Align.CENTER, 0.35f, 0.02f, Neon.FONT_BODY)
            Neon.label(c, world.loadout.lvl[id].toString(), x + size * 0.5f, y + 24f, 11f, fade(Palette.WHITE, 0.9f), Paint.Align.CENTER, 0.25f, 0f, Neon.FONT_NUM)
            x += step
        }
    }

    /** Full loadout readout, shown on the pause panel. */
    private fun drawLoadout(c: Canvas, world: World) {
        world.loadout.ownedList(badgeIds)
        val cx = w * 0.5f
        Neon.label(c, "LOADOUT", cx, h * 0.36f, 14f, Palette.VIOLET, Paint.Align.CENTER, 0.5f, 0.3f)
        if (badgeIds.isEmpty()) {
            Neon.label(c, "NO AUGMENTS INSTALLED", cx, h * 0.36f + 26f, 14f, Palette.DIM, Paint.Align.CENTER, 0.3f, 0.14f, Neon.FONT_BODY)
            return
        }
        var y = h * 0.36f + 28f
        for (id in badgeIds) {
            val lo = world.loadout
            val name = Aug.tierName(id, lo.lvl[id], if (Aug.isAbility(id)) lo.branch[id] else 0)
            Neon.label(c, name, cx - 12f, y, 15f, Aug.colors[id], Paint.Align.RIGHT, 0.4f, 0.12f)
            Neon.label(c, "Lv ${lo.lvl[id]}", cx + 16f, y, 15f, fade(Palette.WHITE, 0.75f), Paint.Align.LEFT, 0.3f, 0.05f, Neon.FONT_NUM)
            y += 20f
            if (y > h * 0.55f) break
        }
    }
}

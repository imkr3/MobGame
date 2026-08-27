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
    val back = Button("BACK", Palette.DIM)
    val shipCells = Array(ShipDex.list.size) { Button("", Palette.DIM) }

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
        play.place(cx, h * 0.575f, w * 0.62f, 62f)
        hangar.place(cx, h * 0.575f + 76f, w * 0.52f, 50f)
        val tw = w * 0.185f
        music.place(cx - tw - 8f, h * 0.575f + 140f, tw, 40f)
        sfx.place(cx, h * 0.575f + 140f, tw, 40f)
        haptic.place(cx + tw + 8f, h * 0.575f + 140f, tw, 40f)

        summon.place(cx, h * 0.795f, w * 0.62f, 56f)
        back.place(cx, h * 0.875f, w * 0.4f, 46f)
        val cols = 4
        val cellW = w * 0.215f
        val cellH = 86f
        for (i in shipCells.indices) {
            val col = i % cols
            val row = i / cols
            shipCells[i].place(
                w * 0.5f + (col - (cols - 1) / 2f) * (cellW + 8f),
                h * 0.47f + row * (cellH + 10f),
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
        val sector = Sectors.forWave(world.wave.coerceAtLeast(1))
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

        // lives
        var lx = 24f
        val ly = h - bottom - 22f
        for (i in 0 until p.lives.coerceAtMost(5)) {
            shipIcon(c, lx, ly, 9f, Palette.CYAN)
            lx += 26f
        }

        // weapon pips
        for (i in 0 until World.MAX_WEAPON) {
            val on = i < p.weapon
            val x = 24f + i * 15f
            val y = h - bottom - 48f
            Neon.fillRect(c, x - 5f, y - 3f, x + 5f, y + 3f, fade(if (on) Palette.CYAN else Palette.DIM, if (on) 0.95f else 0.25f))
        }
        if (p.shield > 0) {
            Neon.label(c, "SHIELD x${p.shield}", 24f, h - bottom - 62f, 13f, Palette.LIME, Paint.Align.LEFT, 0.5f, 0.14f)
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
        hangar.label = "HANGAR - ${ShipDex.byId(prefs.selectedShip).name}"
        drawButton(c, play, time, true)
        drawButton(c, hangar, time)
        drawButton(c, music, time)
        drawButton(c, sfx, time)
        drawButton(c, haptic, time)

        val statY = h * 0.795f
        Neon.label(c, "BEST  ${formatScore(prefs.bestScore)}", cx, statY, 22f, Palette.AMBER, Paint.Align.CENTER, 0.7f, 0.14f, Neon.FONT_NUM)
        Neon.label(c, "WAVE ${prefs.bestWave}   COMBO x${prefs.bestCombo}   RUNS ${prefs.runs}", cx, statY + 24f, 13f, Palette.DIM, Paint.Align.CENTER, 0.4f, 0.16f)
        Neon.label(c, "${prefs.cores} CORES", cx, statY + 46f, 15f, Palette.AMBER, Paint.Align.CENTER, 0.6f, 0.2f, Neon.FONT_NUM)

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

    fun drawGameOver(c: Canvas, world: World, newBest: Boolean, time: Float) {
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
                hullIcon(c, s2, cell.cx, cell.cy - 6f, 15f, 1f)
                Neon.label(c, s2.name, cell.cx, t + cell.h - 9f, 9.5f, s2.color, Paint.Align.CENTER, 0.3f, 0.04f, Neon.FONT_BODY)
            } else {
                Neon.label(c, "?", cell.cx, cell.cy + 8f, 26f, fade(Palette.DIM, 0.7f), Paint.Align.CENTER, 0.3f, 0f)
                Neon.label(c, Rarity.names[s2.rarity], cell.cx, t + cell.h - 9f, 8f, fade(Rarity.colors[s2.rarity], 0.5f), Paint.Align.CENTER, 0.2f, 0.1f, Neon.FONT_BODY)
            }
        }

        val canPull = prefs.cores >= ShipDex.PULL_COST
        summon.label = "SUMMON  ${ShipDex.PULL_COST}"
        summon.color = if (canPull) Palette.AMBER else Palette.DIM
        drawButton(c, summon, time, canPull)
        drawButton(c, back, time)
        Neon.label(c, "EARN CORES BY FLYING - SCORE AND WAVES BOTH PAY", cx, h * 0.745f, 11f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY)
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

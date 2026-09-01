package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
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
        /** The persistent shell: a resource strip on top, a tab bar below. */
        const val TOP_BAR = 54f
        const val NAV_BAR = 76f
        /** Header strip each tab keeps clear above its content. */
        const val HEADER = 34f
    }

    /** Tab order, left to right, with BATTLE raised in the middle. */
    object Tab {
        const val SHOP = 0
        const val HANGAR = 1
        const val BATTLE = 2
        const val SECTORS = 3
        const val PILOT = 4
        const val COUNT = 5
        val labels = arrayOf("SHOP", "HANGAR", "BATTLE", "SECTORS", "PILOT")
        val colors = intArrayOf(Palette.LIME, Palette.CYAN, Palette.MAGENTA, Palette.VIOLET, Palette.AMBER)
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
    val music = Button("MUSIC", Palette.VIOLET)
    val sfx = Button("SFX", Palette.VIOLET)
    val summon = Button("SUMMON", Palette.AMBER)
    val summon10 = Button("SUMMON x10", Palette.AMBER)
    val back = Button("BACK", Palette.DIM)
    val shopRows = Array(Shop.COUNT) { Button("", Palette.DIM) }
    val coop = Button("CO-OP", Palette.ROSE)
    val hostGame = Button("HOST GAME", Palette.CYAN)
    val joinGame = Button("JOIN GAME", Palette.LIME)
    val startCoop = Button("LAUNCH", Palette.AMBER)
    val connectBtn = Button("CONNECT", Palette.LIME)
    /** Digits 0-9, then dot, then backspace. */
    val keypad = Array(12) { Button("", Palette.SKY) }
    val shipCells = Array(ShipDex.list.size) { Button("", Palette.DIM) }
    val launch = Button("LAUNCH", Palette.CYAN)
    val levelCells = Array(Levels.list.size) { Button("", Palette.DIM) }
    /** The five bottom tabs, always live on every menu screen. */
    val navCells = Array(Tab.COUNT) { Button(Tab.labels[it], Tab.colors[it]) }
    /** One per contract slot; tapping a finished one claims it. */
    val missionRows = Array(Missions.SLOTS) { Button("", Palette.AMBER) }

    /** One offered augment: its card geometry, hit target and pre-wrapped copy. */
    class CardView {
        var card: AugCard? = null
        val lines = ArrayList<String>(3)
        val btn = Button("", Palette.CYAN)
    }

    val cards = Array(4) { CardView() }
    var cardCount = 0
        private set
    private val badgeIds = ArrayList<Int>(Aug.COUNT)
    private val wrapBuf = ArrayList<String>(4)

    private var w = 540f
    private var h = 1000f
    private var top = 20f
    private var bottom = 20f

    /** The band between the resource strip and the tab bar. */
    private var contentTop = 0f
    private var contentBot = 0f
    private val contentH: Float get() = contentBot - contentTop

    // Both long lists scroll: there are more shop lines and more sectors than
    // fit a phone, and paging them would hide what is on the other page.
    private var shopScroll = 0f
    private var shopSpan = 0f
    private var levelScroll = 0f
    private var levelSpan = 0f

    private val arc = RectF()
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shipPath = Path()

    fun layout(width: Float, height: Float, topInset: Float, bottomInset: Float) {
        w = width; h = height
        top = topInset + 16f
        bottom = bottomInset + 16f

        val cx = w * 0.5f
        contentTop = top + TOP_BAR + 12f
        contentBot = h - bottom - NAV_BAR - 10f
        val ch = contentH

        // ---- the tab bar ------------------------------------------------
        val navY = h - bottom - NAV_BAR * 0.5f
        val cellW = w / Tab.COUNT
        for (i in navCells.indices) {
            val mid = i == Tab.BATTLE
            navCells[i].place(
                cellW * (i + 0.5f),
                if (mid) navY - 10f else navY + 4f,
                if (mid) cellW * 1.06f else cellW * 0.92f,
                if (mid) 82f else 62f
            )
        }

        // ---- BATTLE tab -------------------------------------------------
        play.place(cx, contentTop + ch * 0.38f, w * 0.66f, 66f)
        coop.place(cx, contentTop + ch * 0.475f, w * 0.46f, 46f)
        for (i in missionRows.indices) {
            missionRows[i].place(cx, contentTop + ch * (0.605f + i * 0.122f), w * 0.90f, ch * 0.092f)
        }

        // ---- PILOT tab: the setting toggles live under the record sheet --
        val tw = w * 0.185f
        val toggleY = contentBot - 26f
        music.place(cx - tw - 8f, toggleY, tw, 38f)
        sfx.place(cx, toggleY, tw, 38f)
        haptic.place(cx + tw + 8f, toggleY, tw, 38f)

        layoutShop()
        layoutSectors()

        // ---- HANGAR tab -------------------------------------------------
        summon.place(cx - w * 0.165f, contentBot - 34f, w * 0.29f, 54f)
        summon10.place(cx + w * 0.165f, contentBot - 34f, w * 0.29f, 54f)
        val cols = 6
        val cellW2 = w * 0.148f
        val cellH = 66f
        for (i in shipCells.indices) {
            val col = i % cols
            val row = i / cols
            shipCells[i].place(
                cx + (col - (cols - 1) / 2f) * (cellW2 + 8f),
                contentTop + ch * 0.375f + row * (cellH + 8f),
                cellW2, cellH
            )
        }

        // ---- co-op lobby, which keeps its own full-screen layout --------
        back.place(cx, h - bottom - 40f, w * 0.4f, 46f)
        hostGame.place(cx, h * 0.34f, w * 0.62f, 58f)
        joinGame.place(cx, h * 0.34f + 76f, w * 0.62f, 58f)
        startCoop.place(cx, h * 0.62f, w * 0.62f, 58f)
        connectBtn.place(cx, h * 0.70f, w * 0.5f, 52f)
        for (i in keypad.indices) {
            val col = i % 3
            val row = i / 3
            keypad[i].place(cx + (col - 1) * (w * 0.22f), h * 0.40f + row * 60f, w * 0.20f, 52f)
        }

        pause.place(w - 34f, top + 22f, 44f, 44f)
        overdrive.place(w - 56f, h - bottom - 62f, 68f, 68f)

        resume.place(cx, h * 0.58f, w * 0.58f, 58f)
        restart.place(cx, h * 0.58f + 76f, w * 0.58f, 52f)
        quit.place(cx, h * 0.58f + 144f, w * 0.58f, 52f)

        retry.place(cx, h * 0.66f, w * 0.60f, 58f)
        toMenu.place(cx, h * 0.66f + 76f, w * 0.50f, 48f)
    }

    /** Where the scrollable part of a tab starts and how tall it is. */
    private val listTop: Float get() = contentTop + HEADER
    private val sectorListBot: Float get() = contentBot - 76f

    private fun layoutShop() {
        val cx = w * 0.5f
        val rows = (shopRows.size + 1) / 2
        val row = 118f
        shopSpan = rows * row
        shopScroll = clamp(shopScroll, 0f, (shopSpan - (contentBot - listTop)).coerceAtLeast(0f))
        for (i in shopRows.indices) {
            shopRows[i].place(
                cx + (if (i % 2 == 0) -1f else 1f) * (w * 0.235f),
                listTop + row * (i / 2 + 0.5f) - shopScroll,
                w * 0.455f, row - 12f
            )
        }
    }

    private fun layoutSectors() {
        val cx = w * 0.5f
        val cardW = w * 0.44f
        val rows = (levelCells.size + 1) / 2
        val row = 104f
        levelSpan = rows * row + 12f
        levelScroll = clamp(levelScroll, 0f, (levelSpan - (sectorListBot - listTop)).coerceAtLeast(0f))
        for (i in levelCells.indices) {
            levelCells[i].place(
                cx + (if (i % 2 == 0) -1f else 1f) * (cardW * 0.5f + 6f),
                listTop + row * (i / 2 + 0.5f) - levelScroll,
                cardW, row - 14f
            )
        }
        // pinned under the list, so it is always reachable however far you scroll
        launch.place(cx, contentBot - 30f, w * 0.52f, 52f)
    }

    /**
     * The rows of a scrolling list keep their places when they scroll out of
     * the clip, so a card that is no longer drawn would still swallow taps -
     * including taps on the nav bar it now sits behind. Only the rows actually
     * inside the viewport are live.
     */
    private fun visibleIn(rows: Array<Button>, top: Float, bot: Float): List<Button> =
        rows.filter { it.cy + it.h * 0.5f > top && it.cy - it.h * 0.5f < bot }

    fun liveShopRows(): List<Button> = visibleIn(shopRows, listTop, contentBot)

    fun liveLevelCells(): List<Button> = visibleIn(levelCells, listTop, sectorListBot)

    /** Opening a tab shows the top of its list rather than wherever it was left. */
    fun resetScroll() {
        shopScroll = 0f
        levelScroll = 0f
        layoutShop()
        layoutSectors()
    }

    /** Drag on a long list. Positive [dy] moves the content with the finger. */
    fun scrollShop(dy: Float) {
        shopScroll = clamp(shopScroll - dy, 0f, (shopSpan - (contentBot - listTop)).coerceAtLeast(0f))
        layoutShop()
    }

    fun scrollSectors(dy: Float) {
        levelScroll = clamp(levelScroll - dy, 0f, (levelSpan - (sectorListBot - listTop)).coerceAtLeast(0f))
        layoutSectors()
    }

    /** A thin rail on the right showing how far down a long list you are. */
    private fun scrollRail(c: Canvas, scroll: Float, span: Float, top: Float, bot: Float, color: Int) {
        val view = bot - top
        if (span <= view + 1f) return
        val railH = view - 12f
        val thumb = (railH * view / span).coerceAtLeast(24f)
        val t = clamp(scroll / (span - view), 0f, 1f)
        val x = w - 7f
        Neon.fillRect(c, x - 1.5f, top + 6f, x + 1.5f, top + 6f + railH, fade(Palette.DIM, 0.25f))
        val y = top + 6f + (railH - thumb) * t
        Neon.fillRect(c, x - 2f, y, x + 2f, y + thumb, fade(color, 0.8f))
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
        // shrink rather than spill: labels change with the run, boxes do not
        var size = b.h * 0.42f
        val room = b.w - 22f
        while (size > 8f && Neon.textWidth(b.label, size, 0.16f) > room) size -= 0.5f
        Neon.label(c, b.label, b.cx, b.cy + size * 0.36f, size, b.color, Paint.Align.CENTER, 0.9f, 0.16f)
    }

    // ----------------------------------------------------------- the shell

    /** Resource strip: rank and its progress on the left, cores on the right. */
    private fun drawTopBar(c: Canvas, time: Float) {
        val y = top + TOP_BAR * 0.5f
        Neon.fillRect(c, 0f, top - 6f, w, top + TOP_BAR, 0x66000000)
        Neon.hairline(c, 0f, top + TOP_BAR, w, top + TOP_BAR, fade(Palette.VIOLET, 0.5f), 1.2f)

        val rank = prefs.rank
        rankBadge(c, 30f, y, 17f, rank, time)
        Neon.label(c, Rank.title(rank), 54f, y - 4f, 13f, Palette.WHITE, Paint.Align.LEFT, 0.5f, 0.16f)

        // experience towards the next rank
        val need = Rank.toNext(rank)
        val ratio = if (need <= 0) 1f else clamp(prefs.xp.toFloat() / need, 0f, 1f)
        val bx = 54f
        val bw = w * 0.34f
        val by = y + 6f
        Neon.panel(c, bx, by, bx + bw, by + 9f, 4.5f, fade(Palette.VIOLET, 0.12f), fade(Palette.VIOLET, 0.55f), 1.1f, 0.4f)
        if (ratio > 0.01f) {
            Neon.fillRect(c, bx + 1.5f, by + 1.5f, bx + 1.5f + (bw - 3f) * ratio, by + 7.5f, fade(Palette.VIOLET, 0.95f))
        }
        val xpText = if (need <= 0) "MAX" else "${prefs.xp}/$need"
        Neon.label(c, xpText, bx + bw + 8f, by + 8f, 9.5f, Palette.DIM, Paint.Align.LEFT, 0.2f, 0.06f, Neon.FONT_NUM)

        coreChip(c, w - 20f, y, prefs.cores)
    }

    private fun rankBadge(c: Canvas, x: Float, y: Float, r: Float, rank: Int, time: Float) {
        val pulse = 0.75f + 0.25f * sin(time * 2.2f)
        Neon.softDisc(c, x, y, r * 1.5f, fade(Palette.VIOLET, 0.16f))
        Neon.ring(c, x, y, r, fade(Palette.VIOLET, pulse), 2f, 0.9f)
        Neon.ring(c, x, y, r * 0.72f, fade(Palette.WHITE, 0.30f), 1f, 0.4f)
        Neon.label(c, rank.toString(), x, y + 6f, 17f, Palette.WHITE, Paint.Align.CENTER, 0.5f, 0f, Neon.FONT_NUM)
    }

    private fun coreChip(c: Canvas, right: Float, y: Float, cores: Int) {
        val text = formatScore(cores)
        val tw = Neon.textWidth(text, 17f, 0.06f, Neon.FONT_NUM)
        val l = right - tw - 34f
        Neon.panel(c, l, y - 15f, right, y + 15f, 15f, fade(Palette.AMBER, 0.10f), fade(Palette.AMBER, 0.6f), 1.4f, 0.5f)
        Neon.orb(c, l + 15f, y, 6f, fade(Palette.AMBER, 0.95f), 1.2f)
        Neon.label(c, text, right - 12f, y + 6f, 17f, Palette.AMBER, Paint.Align.RIGHT, 0.5f, 0.06f, Neon.FONT_NUM)
    }

    /** The tab bar. The middle cell is raised, the way a battle button should be. */
    fun drawNav(c: Canvas, active: Int, time: Float) {
        val barTop = h - bottom - NAV_BAR
        Neon.fillRect(c, 0f, barTop, w, h, 0xD905020C.toInt())
        Neon.hairline(c, 0f, barTop, w, barTop, fade(Palette.VIOLET, 0.55f), 1.4f)

        for (i in navCells.indices) {
            val b = navCells[i]
            val on = i == active
            val mid = i == Tab.BATTLE
            val col = b.color
            if (mid) {
                val pulse = if (b.pressed) 1f else 0.72f + 0.28f * sin(time * 3f)
                Neon.panel(
                    c, b.cx - b.w / 2, b.cy - b.h / 2, b.cx + b.w / 2, b.cy + b.h / 2, 18f,
                    fade(col, if (b.pressed) 0.34f else 0.20f), fade(col, pulse), 2.6f, 1.2f
                )
                navIcon(c, i, b.cx, b.cy - 12f, 15f, Palette.WHITE)
                Neon.label(c, b.label, b.cx, b.cy + 26f, 12f, Palette.WHITE, Paint.Align.CENTER, 0.7f, 0.16f)
            } else {
                if (on) {
                    Neon.panel(
                        c, b.cx - b.w / 2, b.cy - b.h / 2, b.cx + b.w / 2, b.cy + b.h / 2, 12f,
                        fade(col, 0.16f), fade(col, 0.8f), 1.6f, 0.8f
                    )
                }
                val a = if (on) 1f else if (b.pressed) 0.9f else 0.45f
                navIcon(c, i, b.cx, b.cy - 9f, 12f, fade(col, a))
                Neon.label(
                    c, b.label, b.cx, b.cy + 22f, 10f, fade(if (on) col else Palette.DIM, a),
                    Paint.Align.CENTER, if (on) 0.5f else 0.2f, 0.14f, Neon.FONT_BODY
                )
            }
        }
    }

    /** Small procedural glyphs, one per tab. */
    private fun navIcon(c: Canvas, tab: Int, x: Float, y: Float, s: Float, color: Int) {
        when (tab) {
            Tab.SHOP -> {
                Neon.panel(c, x - s * 0.7f, y - s * 0.35f, x + s * 0.7f, y + s * 0.7f, s * 0.2f,
                    fade(color, 0.18f), color, 1.8f, 0.7f)
                Neon.ring(c, x, y - s * 0.35f, s * 0.42f, fade(color, 0.9f), 1.6f, 0.5f)
                Neon.fillRect(c, x - s * 0.8f, y - s * 0.35f, x + s * 0.8f, y - s * 0.1f, fade(color, 0.22f))
            }
            Tab.HANGAR -> shipIcon(c, x, y, s * 0.62f, color)
            Tab.BATTLE -> {
                for (i in 0 until 4) {
                    val a = i * TAU / 4f + TAU / 8f
                    Neon.line(c, x + cos(a) * s * 0.28f, y + sin(a) * s * 0.28f,
                        x + cos(a) * s, y + sin(a) * s, color, 2.4f, 0.9f)
                }
                Neon.orb(c, x, y, s * 0.32f, color, 1.3f)
            }
            Tab.SECTORS -> {
                for (i in 0 until 4) {
                    val ox = if (i % 2 == 0) -s * 0.42f else s * 0.42f
                    val oy = if (i < 2) -s * 0.42f else s * 0.42f
                    Neon.panel(c, x + ox - s * 0.3f, y + oy - s * 0.3f, x + ox + s * 0.3f, y + oy + s * 0.3f,
                        s * 0.12f, fade(color, 0.2f), color, 1.5f, 0.6f)
                }
            }
            else -> {
                Neon.ring(c, x, y - s * 0.25f, s * 0.42f, color, 1.8f, 0.7f)
                Neon.line(c, x - s * 0.62f, y + s * 0.75f, x, y + s * 0.15f, color, 2f, 0.7f)
                Neon.line(c, x + s * 0.62f, y + s * 0.75f, x, y + s * 0.15f, color, 2f, 0.7f)
            }
        }
    }

    /** Shared backdrop wash plus the strip and bar every tab screen carries. */
    fun drawShell(c: Canvas, active: Int, time: Float, wash: Int = 0xE603010A.toInt()) {
        Neon.fillRect(c, 0f, 0f, w, h, wash)
        drawTopBar(c, time)
        drawNav(c, active, time)
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
        // Seven- and eight-digit scores used to run under the wave readout, so
        // the budget is measured against where that readout actually starts
        // rather than against a fixed fraction of the screen.
        val waveText = "WAVE ${world.wave}"
        val waveHalf = Neon.textWidth(waveText, 18f, 0.22f) * 0.5f
        val scoreRoom = (w * 0.5f - waveHalf - 30f).coerceAtLeast(w * 0.28f)
        val scoreText = formatScore(world.score)
        var scoreSize = 34f
        while (scoreSize > 16f && Neon.textWidth(scoreText, scoreSize, 0.06f, Neon.FONT_NUM) > scoreRoom) scoreSize -= 1f
        Neon.label(c, scoreText, 18f, top + 34f, scoreSize, Palette.WHITE, Paint.Align.LEFT, 0.7f, 0.06f, Neon.FONT_NUM)
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
        Neon.label(c, waveText, w * 0.5f, top + 26f, 18f, Palette.VIOLET, Paint.Align.CENTER, 0.6f, 0.22f)
        if (!world.bossPresent()) {
            Neon.label(c, sector.name, w * 0.5f, top + 42f, 11f, fade(sector.accent, 0.8f), Paint.Align.CENTER, 0.4f, 0.3f)
            if (world.overload > 0) {
                val a = 0.6f + 0.4f * sin(time * 7f)
                Neon.label(
                    c, "OVERLOAD x${world.overload}", w * 0.5f, top + 58f, 11f,
                    fade(Palette.RED, a), Paint.Align.CENTER, 0.7f, 0.3f
                )
            } else {
                val hint = if (world.loadout.slotsFull()) "CLEAR WAVE - UPGRADE EXISTING" else "CLEAR WAVE - NEW UPGRADE"
                Neon.label(c, hint, w * 0.5f, top + 58f, 10f, fade(Palette.DIM, 0.9f), Paint.Align.CENTER, 0.25f, 0.24f, Neon.FONT_BODY)
            }
        }
        drawOverload(c, world, time)

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
                // BULWARK: the pip taking the next hit shows how deep it is
                if (filled && i == p.shield - 1 && p.shieldHits > 1) {
                    Neon.ring(c, lx, ly, 4f, fade(Palette.WHITE, 0.7f), 1.4f, 0.6f)
                }
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

    /**
     * The killscreen dressing: a hazard frame that never goes away once the
     * grid has overloaded, and a klaxon panel for the few seconds after it
     * happens. The frame is deliberately loud - the run genuinely is different
     * from here on and the screen should say so.
     */
    private fun drawOverload(c: Canvas, world: World, time: Float) {
        val tier = world.overload
        if (tier <= 0) return
        val alarm = clamp(world.overloadAlarm / 3.6f, 0f, 1f)
        val pulse = 0.5f + 0.5f * sin(time * (5f + tier))
        val edge = clamp(0.10f + 0.05f * tier, 0f, 0.30f) * (0.55f + 0.45f * pulse) + alarm * 0.5f
        val band = 5f + 3f * tier.coerceAtMost(4) + alarm * 14f

        // hazard frame
        Neon.fillRect(c, 0f, 0f, w, band, fade(Palette.RED, edge))
        Neon.fillRect(c, 0f, h - band, w, h, fade(Palette.RED, edge))
        Neon.fillRect(c, 0f, 0f, band * 0.6f, h, fade(Palette.RED, edge * 0.8f))
        Neon.fillRect(c, w - band * 0.6f, 0f, w, h, fade(Palette.RED, edge * 0.8f))

        // diagonal hazard stripes along the top band while the klaxon runs
        if (alarm > 0.01f) {
            var x = -h
            while (x < w + band * 2f) {
                Neon.line(c, x, 0f, x + band * 2f, band, fade(Palette.AMBER, 0.55f * alarm), 3f, 0.4f)
                x += band * 4f
            }
            val flash = 0.78f + 0.22f * sin(time * 16f)
            // sits below the wave banner, and shrinks rather than running off
            var size = 38f
            while (size > 14f && Neon.textWidth("SYSTEM OVERLOAD", size, 0.20f) > w - 72f) size -= 1f
            Neon.fillRect(c, 0f, h * 0.50f, w, h * 0.60f, fade(0xFF000000.toInt(), 0.55f * alarm))
            Neon.hairline(c, 0f, h * 0.50f, w, h * 0.50f, fade(Palette.RED, 0.8f * alarm), 1.5f)
            Neon.hairline(c, 0f, h * 0.60f, w, h * 0.60f, fade(Palette.RED, 0.8f * alarm), 1.5f)
            // red on red disappears, so the core is white over a red glow
            Neon.label(
                c, "SYSTEM OVERLOAD", w * 0.5f, h * 0.555f, size,
                fade(Palette.RED, flash * alarm), Paint.Align.CENTER, 1.6f, 0.20f
            )
            Neon.label(
                c, "SYSTEM OVERLOAD", w * 0.5f, h * 0.555f, size,
                fade(Palette.WHITE, flash * alarm), Paint.Align.CENTER, 0f, 0.20f
            )
            Neon.label(
                c, "THE GRID STOPS HOLDING BACK", w * 0.5f, h * 0.585f, 13f,
                fade(Palette.AMBER, alarm), Paint.Align.CENTER, 0.7f, 0.3f
            )
        }
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

    /**
     * The BATTLE tab: who you are flying, where you are flying, the one big
     * button, and the contracts that give a run a point beyond the scoreboard.
     */
    fun drawMenu(c: Canvas, time: Float) {
        drawShell(c, Tab.BATTLE, time, 0xC003010A.toInt())
        val cx = w * 0.5f
        val ch = contentH
        val float = sin(time * 1.4f) * 4f

        Neon.label(c, "NEON", cx, contentTop + ch * 0.085f, 44f, Palette.MAGENTA, Paint.Align.CENTER, 1f, 0.32f)
        Neon.label(c, "VOID", cx, contentTop + ch * 0.085f + 42f, 44f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.32f)

        // the hull you will launch in, and the sector it launches into
        val ship = ShipDex.byId(prefs.selectedShip)
        val sector = Levels.list[prefs.startLevel.coerceIn(0, Levels.list.size - 1)]
        val py = contentTop + ch * 0.265f
        Neon.panel(c, 22f, py - 40f, w - 22f, py + 40f, 14f,
            fade(ship.color, 0.07f), fade(ship.color, 0.6f), 1.6f, 0.6f)
        hullIcon(c, ship, 62f, py + float, 21f, 1f)
        Neon.label(c, ship.name, 100f, py - 8f, 20f, ship.color, Paint.Align.LEFT, 0.7f, 0.1f)
        Neon.label(c, Rarity.names[ship.rarity], 100f, py + 9f, 9.5f,
            Rarity.colors[ship.rarity], Paint.Align.LEFT, 0.4f, 0.24f, Neon.FONT_BODY)
        Neon.label(c, sector.name, w - 34f, py - 8f, 15f, sector.accent, Paint.Align.RIGHT, 0.6f, 0.1f)
        Neon.label(c, "LAUNCH SECTOR", w - 34f, py + 9f, 9f, Palette.DIM, Paint.Align.RIGHT, 0.25f, 0.24f, Neon.FONT_BODY)
        Neon.label(c, sector.subtitle, w - 34f, py + 24f, 9f, fade(sector.accent, 0.6f), Paint.Align.RIGHT, 0.25f, 0.2f, Neon.FONT_BODY)

        play.label = "PLAY"
        drawButton(c, play, time, true)
        coop.label = "CO-OP ON WI-FI"
        drawButton(c, coop, time)

        // ---- contracts ---------------------------------------------------
        val ready = (0 until Missions.SLOTS).count { Missions.read(prefs, it).done }
        Neon.label(c, "CONTRACTS", 28f, contentTop + ch * 0.545f, 14f, Palette.AMBER, Paint.Align.LEFT, 0.6f, 0.3f)
        if (ready > 0) {
            val a = 0.55f + 0.45f * sin(time * 6f)
            Neon.label(c, "$ready READY TO CLAIM", w - 28f, contentTop + ch * 0.545f, 11f,
                fade(Palette.LIME, a), Paint.Align.RIGHT, 0.5f, 0.2f, Neon.FONT_BODY)
        }
        for (i in missionRows.indices) {
            val m = Missions.read(prefs, i)
            val b = missionRows[i]
            val l = b.cx - b.w / 2
            val t = b.cy - b.h / 2
            val r = b.cx + b.w / 2
            val bot = b.cy + b.h / 2
            val col = MK.color(m.kind)
            val edge = if (m.done) Palette.LIME else col
            val glow = if (m.done) 0.75f + 0.25f * sin(time * 6f) else 0.55f
            Neon.panel(c, l, t, r, bot, 11f, fade(if (m.done) Palette.LIME else col, if (b.pressed) 0.26f else 0.07f),
                fade(edge, glow), if (m.done) 2.2f else 1.4f, if (m.done) 0.9f else 0.4f)
            Neon.label(c, m.text, l + 14f, t + 20f, 12.5f, if (m.done) Palette.LIME else col,
                Paint.Align.LEFT, 0.45f, 0.08f)

            // progress rail
            val rx = l + 14f
            val rw = b.w - 118f
            val ry = bot - 17f
            Neon.panel(c, rx, ry, rx + rw, ry + 8f, 4f, fade(col, 0.10f), fade(col, 0.4f), 1f, 0.3f)
            if (m.ratio > 0.01f) {
                Neon.fillRect(c, rx + 1.5f, ry + 1.5f, rx + 1.5f + (rw - 3f) * m.ratio, ry + 6.5f,
                    fade(if (m.done) Palette.LIME else col, 0.95f))
            }
            Neon.label(c, "${formatScore(m.progress)} / ${formatScore(m.target)}", rx + rw + 8f, ry + 8f,
                9.5f, Palette.DIM, Paint.Align.LEFT, 0.2f, 0.04f, Neon.FONT_NUM)

            if (m.done) {
                Neon.label(c, "CLAIM", r - 16f, b.cy - 2f, 15f, Palette.LIME, Paint.Align.RIGHT, 0.7f, 0.18f)
                Neon.label(c, "+${m.reward}", r - 16f, b.cy + 14f, 11f, Palette.AMBER, Paint.Align.RIGHT, 0.4f, 0.06f, Neon.FONT_NUM)
            } else {
                Neon.label(c, "${m.reward}", r - 16f, t + 20f, 14f, Palette.AMBER, Paint.Align.RIGHT, 0.45f, 0.06f, Neon.FONT_NUM)
                Neon.label(c, "CORES", r - 16f, t + 32f, 8f, Palette.DIM, Paint.Align.RIGHT, 0.2f, 0.2f, Neon.FONT_BODY)
            }
        }

        Neon.label(c, "DRAG ANYWHERE TO FLY  -  GRAZE TO CHARGE OVERDRIVE", cx, contentBot - 6f, 10.5f,
            Palette.SKY, Paint.Align.CENTER, 0.35f, 0.16f, Neon.FONT_BODY)
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
        unlocked: List<Int> = emptyList(), coresPaid: Int = 0, ranksGained: Int = 0
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

        Neon.label(c, "BEST  ${formatScore(prefs.bestScore)}", cx, h * 0.555f, 15f, Palette.AMBER, Paint.Align.CENTER, 0.6f, 0.16f, Neon.FONT_NUM)

        if (coresPaid > 0) {
            Neon.label(c, "+${formatScore(coresPaid)} CORES", cx, h * 0.585f, 17f, Palette.AMBER,
                Paint.Align.CENTER, 0.7f, 0.1f, Neon.FONT_NUM)
        }
        if (ranksGained > 0) {
            val a = 0.6f + 0.4f * sin(time * 6f)
            Neon.label(
                c, if (ranksGained == 1) "RANK UP - ${Rank.title(prefs.rank)} ${prefs.rank}"
                else "$ranksGained RANKS - ${Rank.title(prefs.rank)} ${prefs.rank}",
                cx, h * 0.61f, 14f, fade(Palette.VIOLET, a), Paint.Align.CENTER, 0.7f, 0.2f
            )
        }

        if (unlocked.isNotEmpty()) {
            val a = 0.6f + 0.4f * sin(time * 5f)
            Neon.label(c, "SECTOR UNLOCKED", cx, h * 0.638f, 13f, fade(Palette.LIME, a), Paint.Align.CENTER, 0.6f, 0.34f)
            var y = h * 0.638f + 20f
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
        drawShell(c, Tab.HANGAR, time)
        val cx = w * 0.5f
        val ch = contentH
        val ship = ShipDex.byId(selected)
        val owned = prefs.ownedShips

        Neon.label(c, "HANGAR", cx, contentTop + 14f, 21f, Palette.CYAN, Paint.Align.CENTER, 0.9f, 0.3f)
        Neon.label(c, "${ShipDex.ownedCount(owned)}/${ShipDex.list.size} HULLS", cx, contentTop + 29f, 9.5f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.16f, Neon.FONT_BODY)

        // selected hull panel
        val panelTop = contentTop + HEADER + 8f
        val py = panelTop + ch * 0.105f
        Neon.panel(c, 18f, panelTop, w - 18f, contentTop + ch * 0.335f, 14f, fade(ship.color, 0.07f), fade(ship.color, 0.75f), 1.8f, 0.8f)
        hullIcon(c, ship, w * 0.22f, py + 4f + sin(time * 1.6f) * 4f, 30f, 1f)
        Neon.label(c, ship.name, w * 0.42f, py - 14f, 25f, ship.color, Paint.Align.LEFT, 0.9f, 0.1f)
        Neon.label(c, Rarity.names[ship.rarity], w * 0.42f, py + 4f, 11.5f, Rarity.colors[ship.rarity], Paint.Align.LEFT, 0.6f, 0.3f)

        ship.statLines(statBuf)
        var sy = py + 26f
        var col = 0
        for (line in statBuf) {
            val lx = if (col == 0) w * 0.42f else w * 0.70f
            Neon.label(c, line, lx, sy, 12f, Palette.SKY, Paint.Align.LEFT, 0.3f, 0.06f, Neon.FONT_BODY)
            if (col == 1) sy += 17f
            col = 1 - col
        }
        Neon.label(c, ship.signatureText, cx, contentTop + ch * 0.275f, 12f,
            if (ship.signature >= 0) Palette.AMBER else Palette.DIM, Paint.Align.CENTER, 0.5f, 0.16f)
        Neon.label(c, ship.blurb, cx, contentTop + ch * 0.305f, 11.5f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.02f, Neon.FONT_BODY)

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

        // pull odds, so the gacha is legible rather than a shrug
        val fortuneLv = Shop.fortune(prefs)
        val odds = ShipDex.odds(fortuneLv)
        val oy = contentTop + ch * 0.80f
        Neon.label(c, "PULL ODDS", cx, oy - 22f, 10.5f, Palette.DIM, Paint.Align.CENTER, 0.3f, 0.3f)
        val chipW = w * 0.21f
        for (r in odds.indices) {
            val chipX = cx + (r - 1.5f) * (chipW + 6f)
            val col = Rarity.colors[r]
            Neon.panel(c, chipX - chipW / 2, oy - 6f, chipX + chipW / 2, oy + 32f, 8f,
                fade(col, 0.08f), fade(col, 0.6f), 1.3f, 0.5f)
            Neon.label(c, Rarity.names[r].take(4), chipX, oy + 8f, 9f, col, Paint.Align.CENTER, 0.3f, 0.16f, Neon.FONT_BODY)
            Neon.label(c, "${oneDecimal(odds[r] * 100f)}%", chipX, oy + 26f, 13f, col,
                Paint.Align.CENTER, 0.45f, 0.02f, Neon.FONT_NUM)
        }

        val canPull = prefs.cores >= ShipDex.PULL_COST
        val canPull10 = prefs.cores >= ShipDex.PULL_COST * 10
        summon.label = "x1  ${ShipDex.PULL_COST}"
        summon.color = if (canPull) Palette.AMBER else Palette.DIM
        summon10.label = "x10  ${ShipDex.PULL_COST * 10}"
        summon10.color = if (canPull10) Palette.AMBER else Palette.DIM
        drawButton(c, summon, time, canPull)
        drawButton(c, summon10, time, canPull10)
        val fortune = Shop.fortune(prefs)
        Neon.label(
            c,
            if (fortune > 0) "TEN-PULL GUARANTEES RARE+ - FORTUNE CIRCUIT LV$fortune ACTIVE"
            else "A TEN-PULL GUARANTEES RARE OR BETTER",
            cx, summon.cy - 42f, 10f, Palette.DIM, Paint.Align.CENTER, 0.25f, 0.12f, Neon.FONT_BODY
        )
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
        drawShell(c, Tab.SHOP, time)
        val cx = w * 0.5f
        Neon.label(c, "WORKSHOP", cx, contentTop + 14f, 21f, Palette.LIME, Paint.Align.CENTER, 0.9f, 0.3f)
        Neon.label(c, "PERMANENT - CARRIES INTO EVERY RUN", cx, contentTop + 29f, 9.5f,
            Palette.DIM, Paint.Align.CENTER, 0.25f, 0.16f, Neon.FONT_BODY)

        c.save()
        c.clipRect(0f, listTop - 4f, w, contentBot)
        for (i in Shop.items.indices) {
            val item = Shop.items[i]
            val b = shopRows[i]
            val level = prefs.shopLevel(item.id)
            val cost = Shop.cost(item.id, level)
            val open = Shop.available(item.id, prefs)
            val maxed = cost < 0
            val affordable = open && !maxed && prefs.cores >= cost
            b.enabled = open && !maxed
            val l = b.cx - b.w / 2
            val t = b.cy - b.h / 2
            val r = b.cx + b.w / 2
            val bot = b.cy + b.h / 2
            val edge = when {
                !open -> Palette.DIM
                maxed -> Palette.LIME
                affordable -> item.color
                else -> Palette.DIM
            }
            Neon.panel(c, l, t, r, bot, 11f,
                fade(item.color, if (b.pressed) 0.24f else if (open) 0.07f else 0.03f),
                fade(edge, if (affordable || maxed) 0.9f else 0.35f), 1.5f, if (affordable) 0.9f else 0.3f)

            if (!open) {
                lockIcon(c, b.cx, t + 24f, fade(Palette.DIM, 0.7f))
                Neon.label(c, item.name, b.cx, t + 52f, 11.5f, fade(Palette.DIM, 0.8f), Paint.Align.CENTER, 0.3f, 0.08f)
                Neon.label(c, "RANK ${item.rank}", b.cx, bot - 14f, 11f, fade(Palette.AMBER, 0.75f),
                    Paint.Align.CENTER, 0.35f, 0.16f, Neon.FONT_BODY)
                continue
            }

            Neon.label(c, item.name, l + 11f, t + 20f, 11.5f, item.color, Paint.Align.LEFT, 0.5f, 0.06f)
            wrap(item.desc, 8.5f, b.w - 22f, wrapBuf)
            var dy = t + 35f
            for (line in wrapBuf.take(3)) {
                Neon.label(c, line, l + 11f, dy, 8.5f, Palette.DIM, Paint.Align.LEFT, 0.2f, 0.01f, Neon.FONT_BODY)
                dy += 11f
            }
            for (pip in 0 until item.maxLevel) {
                val px = l + 11f + pip * 13f
                Neon.fillRect(c, px, bot - 26f, px + 9f, bot - 20f,
                    fade(if (pip < level) item.color else Palette.DIM, if (pip < level) 0.95f else 0.2f))
            }
            if (maxed) {
                Neon.label(c, "MAX", r - 12f, bot - 12f, 13f, Palette.LIME, Paint.Align.RIGHT, 0.5f, 0.2f)
            } else {
                Neon.label(c, cost.toString(), r - 12f, bot - 12f, 15f,
                    if (affordable) Palette.AMBER else Palette.DIM, Paint.Align.RIGHT, 0.5f, 0.06f, Neon.FONT_NUM)
            }
        }
        c.restore()
        scrollRail(c, shopScroll, shopSpan, listTop, contentBot, Palette.LIME)
    }

    /** The PILOT tab: rank, the record sheet, and the audio settings. */
    fun drawRecords(c: Canvas, time: Float) {
        drawShell(c, Tab.PILOT, time)
        val cx = w * 0.5f
        val ch = contentH
        val rank = prefs.rank

        // rank panel
        val py = contentTop + 52f
        Neon.panel(c, 22f, contentTop + 8f, w - 22f, contentTop + 100f, 14f,
            fade(Palette.VIOLET, 0.08f), fade(Palette.VIOLET, 0.7f), 1.7f, 0.7f)
        rankBadge(c, 68f, py, 30f, rank, time)
        Neon.label(c, Rank.title(rank), 112f, py - 6f, 24f, Palette.WHITE, Paint.Align.LEFT, 0.8f, 0.14f)
        val need = Rank.toNext(rank)
        Neon.label(
            c, if (need <= 0) "MAXIMUM RANK" else "${need - prefs.xp} XP TO RANK ${rank + 1}",
            112f, py + 14f, 11f, Palette.DIM, Paint.Align.LEFT, 0.3f, 0.1f, Neon.FONT_BODY
        )
        Neon.label(c, "BEST", w - 40f, py - 12f, 10f, Palette.DIM, Paint.Align.RIGHT, 0.25f, 0.24f, Neon.FONT_BODY)
        Neon.label(c, formatScore(prefs.bestScore), w - 40f, py + 12f, 24f, Palette.AMBER,
            Paint.Align.RIGHT, 0.7f, 0.04f, Neon.FONT_NUM)

        val rows = arrayOf(
            "FURTHEST WAVE" to prefs.bestWave.toString(),
            "SECTORS CLEARED" to prefs.bestLevel.toString(),
            "BEST COMBO" to "x${prefs.bestCombo}",
            "RUNS FLOWN" to prefs.runs.toString(),
            "TOTAL KILLS" to formatScore(prefs.totalKills),
            "CONTRACTS DONE" to prefs.missionsDone.toString(),
            "CORES EARNED" to formatScore(prefs.totalCores),
            "HULLS OWNED" to "${ShipDex.ownedCount(prefs.ownedShips)}/${ShipDex.list.size}",
            "SUMMONS" to prefs.pulls.toString()
        )
        var y = contentTop + 140f
        val step = (ch * 0.052f)
        for ((label, value) in rows) {
            Neon.label(c, label, cx - 14f, y, 12.5f, Palette.DIM, Paint.Align.RIGHT, 0.3f, 0.14f, Neon.FONT_BODY)
            Neon.label(c, value, cx + 18f, y, 14f, Palette.SKY, Paint.Align.LEFT, 0.45f, 0.04f, Neon.FONT_NUM)
            y += step
        }

        // sector checklist
        Neon.label(c, "SECTORS SEEN", cx, y + 16f, 11f, Palette.VIOLET, Paint.Align.CENTER, 0.4f, 0.3f)
        y += 42f
        for (i in Levels.list.indices) {
            val theme = Levels.list[i]
            val seen = prefs.bestLevel > i || Levels.unlocked(i, prefs)
            val lx = if (i % 2 == 0) cx - w * 0.24f else cx + w * 0.02f
            Neon.label(c, "${i + 1}. ${if (seen) theme.name else "- - - -"}", lx, y + (i / 2) * 24f, 11.5f,
                fade(if (seen) theme.accent else Palette.DIM, if (seen) 0.95f else 0.35f),
                Paint.Align.LEFT, 0.3f, 0.08f, Neon.FONT_BODY)
        }

        music.label = "MUSIC"
        music.color = if (prefs.musicOn) Palette.VIOLET else Palette.DIM
        sfx.label = "SFX"
        sfx.color = if (prefs.sfxOn) Palette.VIOLET else Palette.DIM
        haptic.label = "HAPTIC"
        haptic.color = if (prefs.hapticsOn) Palette.VIOLET else Palette.DIM
        drawButton(c, music, time)
        drawButton(c, sfx, time)
        drawButton(c, haptic, time)
    }

    /**
     * Level select. Every sector is listed from the start so the run ahead is
     * visible; the locked ones show what they are waiting for rather than
     * hiding behind a blank.
     */
    fun drawLevels(c: Canvas, selected: Int, time: Float) {
        drawShell(c, Tab.SECTORS, time)
        val cx = w * 0.5f
        Neon.label(c, "SECTORS", cx, contentTop + 14f, 21f, Palette.MAGENTA, Paint.Align.CENTER, 0.9f, 0.3f)
        Neon.label(
            c, "PICK WHERE THE RUN BEGINS - IT NEVER GETS EASIER", cx, contentTop + 29f, 9.5f,
            Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY
        )

        c.save()
        c.clipRect(0f, listTop - 4f, w, sectorListBot)
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
                c, "0${i + 1}".takeLast(2), l + 12f, t + 25f, 17f,
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

        c.restore()
        scrollRail(c, levelScroll, levelSpan, listTop, sectorListBot, Palette.MAGENTA)

        val picked = Levels.list[selected.coerceIn(0, Levels.list.size - 1)]
        launch.label = "LAUNCH"
        launch.color = picked.accent
        drawButton(c, launch, time, true)
        Neon.label(
            c, "BEGINS AT WAVE 1 OF ${picked.name}", cx, launch.cy - 34f, 10.5f,
            Palette.DIM, Paint.Align.CENTER, 0.25f, 0.14f, Neon.FONT_BODY
        )
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
        // a fourth card has to come out of the height, not off the screen
        val cardH = if (cardCount >= 4) 106f else 128f
        val gap = if (cardCount >= 4) 13f else 18f
        val total = cardCount * cardH + (cardCount - 1) * gap
        var y = h * 0.5f - total * 0.5f + cardH * 0.5f
        for (i in 0 until cardCount) {
            val v = cards[i]
            v.card = offers[i]
            v.btn.place(w * 0.5f, y, cardW, cardH)
            v.btn.color = offers[i].color
            v.btn.enabled = true
            v.btn.pressed = false
            wrap(offers[i].body, cardH * 0.117f, cardW - 118f, v.lines)
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
        val cap = world.loadout.maxSlots()
        val slotText = if (world.loadout.slotsFull()) "BAY FULL $used/$cap - LEVEL-UPS ONLY" else "BAY $used/$cap"
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

            val hh = b.h
            Neon.label(c, card.tag, l + 22f, t + hh * 0.20f, hh * 0.094f, fade(card.color, 0.85f), Paint.Align.LEFT, 0.5f, 0.26f)
            Neon.label(c, card.title, l + 22f, t + hh * 0.45f, hh * 0.22f, card.color, Paint.Align.LEFT, 0.9f, 0.1f)
            var ty = t + hh * 0.64f
            val body = hh * 0.117f
            for (line in v.lines) {
                Neon.label(c, line, l + 22f, ty, body, Palette.DIM, Paint.Align.LEFT, 0.3f, 0.02f, Neon.FONT_BODY)
                ty += body * 1.27f
            }

            // level pips on the right edge
            val maxPips = if (Aug.isAbility(card.id)) Aug.EVOLVED_MAX else Aug.statMax[card.id]
            val have = world.loadout.lvl[card.id]
            val next = if (evo) have else have + 1
            for (pip in 0 until maxPips) {
                val py = t + hh * 0.26f + pip * (hh * 0.117f)
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
            c, "AUGMENTS ${world.loadout.slotsUsed()}/${world.loadout.maxSlots()}", 20f, y - 6f, 10f,
            if (world.loadout.slotsFull()) Palette.AMBER else Palette.DIM,
            Paint.Align.LEFT, 0.3f, 0.22f, Neon.FONT_BODY
        )
        for (id in badgeIds) {
            if (x + size > w - 108f) break
            val col = Aug.colors[id]
            val evolved = Aug.isAbility(id) && world.loadout.branch[id] != 0
            val mastered = world.loadout.mastered(id)
            Neon.panel(c, x, y, x + size, y + size, 6f, fade(col, if (evolved) 0.28f else 0.12f), fade(col, if (evolved) 1f else 0.6f), 1.3f, 0.5f)
            // a capstoned system wears a bright rim, so a finished build reads
            // at a glance without opening the pause panel
            if (mastered) {
                Neon.panel(c, x - 2f, y - 2f, x + size + 2f, y + size + 2f, 8f, 0, fade(Palette.WHITE, 0.85f), 1.1f, 0.9f)
            }
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
            val br = if (Aug.isAbility(id)) lo.branch[id] else 0
            val name = Aug.tierName(id, lo.lvl[id], br)
            Neon.label(c, name, cx - 12f, y, 15f, Aug.colors[id], Paint.Align.RIGHT, 0.4f, 0.12f)
            val mastery = Aug.masteryName(id, lo.lvl[id], br)
            if (mastery.isEmpty()) {
                Neon.label(c, "Lv ${lo.lvl[id]}", cx + 16f, y, 15f, fade(Palette.WHITE, 0.75f), Paint.Align.LEFT, 0.3f, 0.05f, Neon.FONT_NUM)
            } else {
                Neon.label(c, mastery, cx + 16f, y, 11f, fade(Palette.WHITE, 0.9f), Paint.Align.LEFT, 0.3f, 0.18f, Neon.FONT_BODY)
            }
            y += 20f
            if (y > h * 0.55f) break
        }
    }
}

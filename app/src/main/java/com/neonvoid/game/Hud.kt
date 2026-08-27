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
        play.place(cx, h * 0.60f, w * 0.62f, 62f)
        haptic.place(cx, h * 0.60f + 88f, w * 0.52f, 44f)

        pause.place(w - 34f, top + 22f, 44f, 44f)
        overdrive.place(w - 56f, h - bottom - 62f, 68f, 68f)

        resume.place(cx, h * 0.44f, w * 0.58f, 58f)
        restart.place(cx, h * 0.44f + 78f, w * 0.58f, 52f)
        quit.place(cx, h * 0.44f + 148f, w * 0.58f, 52f)

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

    fun drawGame(c: Canvas, world: World, time: Float) {
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

        // wave
        Neon.label(c, "WAVE ${world.wave}", w * 0.5f, top + 26f, 18f, Palette.VIOLET, Paint.Align.CENTER, 0.6f, 0.22f)

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

        drawOverdrive(c, world, time)
        drawBanner(c, world, time)
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
        Neon.label(c, world.banner, w * 0.5f, y, 44f, fade(col, a), Paint.Align.CENTER, 1f, 0.25f)
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

        haptic.label = if (prefs.hapticsOn) "HAPTICS  ON" else "HAPTICS  OFF"
        haptic.color = if (prefs.hapticsOn) Palette.VIOLET else Palette.DIM
        drawButton(c, play, time, true)
        drawButton(c, haptic, time)

        val statY = h * 0.80f
        Neon.label(c, "BEST  ${formatScore(prefs.bestScore)}", cx, statY, 22f, Palette.AMBER, Paint.Align.CENTER, 0.7f, 0.14f, Neon.FONT_NUM)
        Neon.label(c, "WAVE ${prefs.bestWave}   COMBO x${prefs.bestCombo}   RUNS ${prefs.runs}", cx, statY + 26f, 13f, Palette.DIM, Paint.Align.CENTER, 0.4f, 0.16f)

        Neon.label(c, "DRAG ANYWHERE TO FLY  -  AUTO FIRE", cx, h - bottom - 40f, 13f, Palette.SKY, Paint.Align.CENTER, 0.5f, 0.2f)
        Neon.label(c, "GRAZE BULLETS TO CHARGE OVERDRIVE", cx, h - bottom - 20f, 13f, Palette.AMBER, Paint.Align.CENTER, 0.5f, 0.2f)
    }

    fun drawPause(c: Canvas, time: Float) {
        Neon.fillRect(c, 0f, 0f, w, h, 0xCC05020C.toInt())
        Neon.label(c, "PAUSED", w * 0.5f, h * 0.30f, 46f, Palette.CYAN, Paint.Align.CENTER, 1f, 0.3f)
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

    fun setPressed(b: Button?, value: Boolean) {
        b?.pressed = value
    }
}

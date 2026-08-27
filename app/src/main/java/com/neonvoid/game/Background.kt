package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Synthwave backdrop: vertical haze, a banded sun on the horizon, a scrolling perspective
 * grid below it and parallax starfield above. Shaders are rebuilt only on resize.
 */
class Background {

    private class Star {
        var x = 0f; var y = 0f; var speed = 0f; var size = 0f; var color = Palette.WHITE
    }

    private var theme: Sector = Sectors.list[0]
    private var w = 0f
    private var h = 0f
    private var horizon = 0f
    private var sunR = 0f
    private var sunCy = 0f

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var nebulaR = 1f

    private val stars = Array(120) { Star() }

    private var gridPhase = 0f
    private var starPhase = 0f
    private var time = 0f

    private val gridCols = 15

    /** Swap the sector palette; rebuilds the shaders in place. */
    fun applyTheme(sector: Sector) {
        if (theme === sector) return
        theme = sector
        if (w > 0f && h > 0f) resize(w, h)
    }

    fun themeAccent(): Int = theme.accent

    fun resize(width: Float, height: Float) {
        w = width; h = height
        horizon = h * 0.30f

        bgPaint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(theme.skyTop, theme.skyMid, theme.skyBottom),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        bgPaint.style = Paint.Style.FILL

        sunR = w * 0.27f
        sunCy = horizon - sunR * 0.42f
        // Alpha is baked into the stops so the sun stays a backdrop and never
        // out-shouts the ships crossing in front of it.
        sunPaint.shader = LinearGradient(
            0f, sunCy - sunR, 0f, horizon,
            theme.sunStops,
            floatArrayOf(0f, 0.35f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        sunPaint.style = Paint.Style.FILL

        nebulaR = w * 0.85f
        nebulaPaint.shader = RadialGradient(
            0f, 0f, nebulaR,
            intArrayOf(fade(theme.nebula, 0.30f), fade(theme.accent, 0.10f), fade(theme.accent, 0f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        nebulaPaint.style = Paint.Style.FILL

        for (s in stars) {
            s.x = rnd(w)
            s.y = rnd(horizon)
            s.speed = rnd(6f, 46f)
            s.size = rnd(0.7f, 2.2f)
            s.color = when {
                chance(0.18f) -> theme.accent
                chance(0.30f) -> theme.grid
                else -> Palette.WHITE
            }
        }
    }

    /** [speed] is a 0..1-ish intensity that ramps up with the wave number. */
    fun update(dt: Float, speed: Float) {
        time += dt
        gridPhase += dt * (0.45f + speed * 0.7f)
        if (gridPhase > 1f) gridPhase -= 1f
        starPhase += dt
        val boost = 1f + speed * 1.6f
        for (s in stars) {
            s.y += s.speed * boost * dt
            if (s.y > horizon) {
                s.y -= horizon
                s.x = rnd(w)
            }
        }
    }

    fun draw(c: Canvas) {
        c.drawRect(0f, 0f, w, h, bgPaint)

        // drifting nebulae
        c.save()
        c.translate(w * 0.22f + sin(time * 0.07f) * w * 0.06f, h * 0.14f)
        c.drawCircle(0f, 0f, nebulaR, nebulaPaint)
        c.restore()
        c.save()
        c.translate(w * 0.82f + cos(time * 0.05f) * w * 0.05f, h * 0.60f)
        c.scale(0.8f, 0.8f)
        c.drawCircle(0f, 0f, nebulaR, nebulaPaint)
        c.restore()

        // stars
        for (s in stars) {
            val tw = 0.55f + 0.45f * sin(starPhase * 3f + s.x * 0.05f)
            Neon.softDisc(c, s.x, s.y, s.size * 2.2f, fade(s.color, 0.10f * tw))
            Neon.softDisc(c, s.x, s.y, s.size, fade(s.color, 0.75f * tw))
        }

        // sun: solid crown, then horizontal bands that thin out towards the horizon.
        // Each slice is a clipped draw of the same disc, so the gaps stay transparent
        // and the stars and nebulae keep showing through.
        val cx = w * 0.5f
        val bandStart = sunCy - sunR * 0.12f
        c.save()
        c.clipRect(cx - sunR - 2f, sunCy - sunR - 2f, cx + sunR + 2f, bandStart)
        c.drawCircle(cx, sunCy, sunR, sunPaint)
        c.restore()
        var y = bandStart
        var band = sunR * 0.125f
        var gap = sunR * 0.028f
        while (y < horizon) {
            val bh = minOf(band, horizon - y)
            if (bh > 0.4f) {
                c.save()
                c.clipRect(cx - sunR - 2f, y, cx + sunR + 2f, y + bh)
                c.drawCircle(cx, sunCy, sunR, sunPaint)
                c.restore()
            }
            y += bh + gap
            band *= 0.80f
            gap *= 1.24f
        }
        Neon.hairline(c, 0f, horizon, w, horizon, fade(theme.accent, 0.55f), 2.2f)
        Neon.hairline(c, 0f, horizon, w, horizon, fade(theme.accent, 0.16f), 8f)

        // perspective grid
        val depth = h - horizon
        val gridColor = fade(theme.grid, 0.15f)
        val vpX = cx
        for (i in -gridCols..gridCols) {
            val bx = cx + i * (w * 0.135f)
            Neon.hairline(c, vpX, horizon, bx, h, fade(theme.nebula, 0.13f), 1.2f)
        }
        val rows = 13
        for (i in 0 until rows) {
            var t = (i + gridPhase) / rows
            t *= t
            val ly = horizon + depth * t
            val a = 0.06f + 0.20f * t
            Neon.hairline(c, 0f, ly, w, ly, fade(gridColor, a * 4f), 1.1f + t * 1.6f)
        }
    }

    fun horizonY(): Float = horizon
}

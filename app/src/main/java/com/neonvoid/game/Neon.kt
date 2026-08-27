package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.sqrt
import kotlin.random.Random

const val TAU = 6.2831855f
const val DEG = 0.017453292f

fun len(x: Float, y: Float): Float = sqrt(x * x + y * y)

fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

fun rnd(b: Float): Float = Random.nextFloat() * b

fun chance(p: Float): Boolean = Random.nextFloat() < p

fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun clamp(v: Float, lo: Float, hi: Float): Float = if (v < lo) lo else if (v > hi) hi else v

/** Frame-rate independent exponential approach towards [target]. */
fun approach(cur: Float, target: Float, rate: Float, dt: Float): Float {
    val t = 1f - Math.pow((1f - rate).toDouble(), (dt * 60f).toDouble()).toFloat()
    return cur + (target - cur) * t
}

fun fade(color: Int, a: Float): Int {
    val alpha = ((color ushr 24) * clamp(a, 0f, 1f)).toInt() and 0xFF
    return (color and 0x00FFFFFF) or (alpha shl 24)
}

/** Blend [color] towards white by [t] (0..1), keeping alpha. */
fun lighten(color: Int, t: Float): Int {
    val a = color ushr 24 and 0xFF
    val r = color ushr 16 and 0xFF
    val g = color ushr 8 and 0xFF
    val b = color and 0xFF
    val nr = (r + (255 - r) * t).toInt().coerceIn(0, 255)
    val ng = (g + (255 - g) * t).toInt().coerceIn(0, 255)
    val nb = (b + (255 - b) * t).toInt().coerceIn(0, 255)
    return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
}

fun mixColor(c1: Int, c2: Int, t: Float): Int {
    val u = clamp(t, 0f, 1f)
    val a = lerp((c1 ushr 24 and 0xFF).toFloat(), (c2 ushr 24 and 0xFF).toFloat(), u).toInt()
    val r = lerp((c1 ushr 16 and 0xFF).toFloat(), (c2 ushr 16 and 0xFF).toFloat(), u).toInt()
    val g = lerp((c1 ushr 8 and 0xFF).toFloat(), (c2 ushr 8 and 0xFF).toFloat(), u).toInt()
    val b = lerp((c1 and 0xFF).toFloat(), (c2 and 0xFF).toFloat(), u).toInt()
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

object Palette {
    val CYAN = 0xFF3DF5FF.toInt()
    val MAGENTA = 0xFFFF3DC1.toInt()
    val VIOLET = 0xFF9B5CFF.toInt()
    val LIME = 0xFF8CFF57.toInt()
    val AMBER = 0xFFFFC53D.toInt()
    val RED = 0xFFFF4A5C.toInt()
    val ROSE = 0xFFFF7BAC.toInt()
    val SKY = 0xFF7AA2FF.toInt()
    val WHITE = 0xFFFFFFFF.toInt()
    val DIM = 0xFF6C5C99.toInt()
    val BG_TOP = 0xFF1A0838.toInt()
    val BG_MID = 0xFF0D0422.toInt()
    val BG_BOTTOM = 0xFF05020C.toInt()
}

/**
 * Neon look is built from stacked strokes rather than blur filters: three passes of the
 * same geometry (wide + faint, medium, tight) plus a near-white core. It costs a few extra
 * draw calls but works identically on hardware and software canvases.
 */
object Neon {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val tmp = Path()

    val FONT_TITLE: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    val FONT_BODY: Typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    val FONT_NUM: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    private fun prepStroke(color: Int, w: Float) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        p.color = color
        p.strokeWidth = w
    }

    fun path(c: Canvas, path: Path, color: Int, w: Float, glow: Float = 1f, core: Float = 0.85f) {
        if (glow > 0f) {
            prepStroke(fade(color, 0.10f * glow), w * 5f); c.drawPath(path, p)
            prepStroke(fade(color, 0.22f * glow), w * 2.6f); c.drawPath(path, p)
        }
        prepStroke(color, w); c.drawPath(path, p)
        if (core > 0f) {
            prepStroke(fade(lighten(color, 0.85f), core), w * 0.4f); c.drawPath(path, p)
        }
    }

    fun fillPath(c: Canvas, path: Path, color: Int) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
    }

    fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float, glow: Float = 1f) {
        if (glow > 0f) {
            prepStroke(fade(color, 0.10f * glow), w * 5f); c.drawLine(x1, y1, x2, y2, p)
            prepStroke(fade(color, 0.22f * glow), w * 2.6f); c.drawLine(x1, y1, x2, y2, p)
        }
        prepStroke(color, w); c.drawLine(x1, y1, x2, y2, p)
        prepStroke(fade(lighten(color, 0.85f), 0.8f), w * 0.4f); c.drawLine(x1, y1, x2, y2, p)
    }

    /** Thin, cheap line with no glow passes - for backgrounds. */
    fun hairline(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, color: Int, w: Float) {
        prepStroke(color, w)
        c.drawLine(x1, y1, x2, y2, p)
    }

    fun ring(c: Canvas, x: Float, y: Float, r: Float, color: Int, w: Float, glow: Float = 1f) {
        if (r <= 0f) return
        if (glow > 0f) {
            prepStroke(fade(color, 0.10f * glow), w * 5f); c.drawCircle(x, y, r, p)
            prepStroke(fade(color, 0.22f * glow), w * 2.6f); c.drawCircle(x, y, r, p)
        }
        prepStroke(color, w); c.drawCircle(x, y, r, p)
    }

    /** A filled glowing dot: soft halo, body, white-hot core. */
    fun orb(c: Canvas, x: Float, y: Float, r: Float, color: Int, glow: Float = 1f) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        if (glow > 0f) {
            p.color = fade(color, 0.13f * glow); c.drawCircle(x, y, r * 3.1f, p)
            p.color = fade(color, 0.22f * glow); c.drawCircle(x, y, r * 1.9f, p)
        }
        p.color = color; c.drawCircle(x, y, r, p)
        p.color = fade(lighten(color, 0.9f), 0.95f); c.drawCircle(x, y, r * 0.45f, p)
    }

    fun softDisc(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(x, y, r, p)
    }

    fun fillRect(c: Canvas, l: Float, t0: Float, r: Float, b: Float, color: Int) {
        p.reset()
        p.isAntiAlias = true
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRect(l, t0, r, b, p)
    }

    fun panel(c: Canvas, l: Float, t0: Float, r: Float, b: Float, rad: Float, fillColor: Int, edge: Int, w: Float, glow: Float = 1f) {
        rect.set(l, t0, r, b)
        if (fillColor != 0) {
            p.reset(); p.isAntiAlias = true; p.style = Paint.Style.FILL; p.color = fillColor
            c.drawRoundRect(rect, rad, rad, p)
        }
        tmp.reset()
        tmp.addRoundRect(rect, rad, rad, Path.Direction.CW)
        path(c, tmp, edge, w, glow, 0f)
    }

    fun textWidth(s: String, size: Float, spacing: Float = 0.08f, tf: Typeface = FONT_TITLE): Float {
        t.reset(); t.isAntiAlias = true
        t.typeface = tf; t.textSize = size; t.letterSpacing = spacing
        return t.measureText(s)
    }

    fun label(
        c: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.CENTER,
        glow: Float = 1f,
        spacing: Float = 0.08f,
        tf: Typeface = FONT_TITLE
    ) {
        t.reset(); t.isAntiAlias = true
        t.typeface = tf; t.textSize = size; t.textAlign = align; t.letterSpacing = spacing
        if (glow > 0f) {
            t.style = Paint.Style.STROKE
            t.strokeJoin = Paint.Join.ROUND
            t.color = fade(color, 0.16f * glow); t.strokeWidth = size * 0.26f
            c.drawText(s, x, y, t)
            t.color = fade(color, 0.30f * glow); t.strokeWidth = size * 0.12f
            c.drawText(s, x, y, t)
        }
        t.style = Paint.Style.FILL
        t.color = lighten(color, 0.55f)
        c.drawText(s, x, y, t)
    }
}

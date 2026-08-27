package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

class Particle {
    var active = false
    var x = 0f; var y = 0f
    var vx = 0f; var vy = 0f
    var life = 0f; var maxLife = 1f
    var size = 2f
    var color = Palette.CYAN
    var drag = 1.6f
    var streak = false
    var spin = 0f
}

class Shockwave {
    var active = false
    var x = 0f; var y = 0f
    var r = 0f; var maxR = 100f
    var life = 0f; var maxLife = 0.5f
    var color = Palette.CYAN
    var width = 3f
}

class FloatText {
    var active = false
    var x = 0f; var y = 0f
    var vy = -40f
    var life = 0f; var maxLife = 0.9f
    var text = ""
    var size = 18f
    var color = Palette.AMBER
}

/**
 * All of the "juice": particles, shockwaves, floating score text, camera trauma,
 * full-screen flashes and hit-stop. Everything is pooled so a heavy wave does not
 * allocate mid-frame.
 */
class Fx {
    private val particles = Array(760) { Particle() }
    private var pIdx = 0
    private val waves = Array(48) { Shockwave() }
    private var wIdx = 0
    private val texts = Array(28) { FloatText() }
    private var tIdx = 0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    var trauma = 0f
        private set
    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    private var flashAmount = 0f
    private var flashColor = Palette.WHITE

    /** Seconds of frozen simulation left - used for impact on big hits. */
    var hitStop = 0f
        private set

    fun reset() {
        for (p in particles) p.active = false
        for (w in waves) w.active = false
        for (t in texts) t.active = false
        trauma = 0f; shakeX = 0f; shakeY = 0f
        flashAmount = 0f; hitStop = 0f
    }

    private fun nextParticle(): Particle {
        for (i in particles.indices) {
            pIdx = (pIdx + 1) % particles.size
            if (!particles[pIdx].active) return particles[pIdx]
        }
        return particles[pIdx]
    }

    fun shake(amount: Float) {
        trauma = clamp(trauma + amount, 0f, 1f)
    }

    fun freeze(seconds: Float) {
        if (seconds > hitStop) hitStop = seconds
    }

    fun flash(color: Int, amount: Float) {
        if (amount > flashAmount) {
            flashAmount = clamp(amount, 0f, 1f)
            flashColor = color
        }
    }

    fun burst(x: Float, y: Float, count: Int, color: Int, speed: Float, size: Float, life: Float, streak: Boolean = false) {
        for (i in 0 until count) {
            val p = nextParticle()
            val a = rnd(TAU)
            val s = speed * rnd(0.25f, 1f)
            p.active = true
            p.x = x; p.y = y
            p.vx = cos(a) * s; p.vy = sin(a) * s
            p.maxLife = life * rnd(0.6f, 1.2f); p.life = p.maxLife
            p.size = size * rnd(0.6f, 1.35f)
            p.color = color
            p.drag = if (streak) 0.9f else 1.9f
            p.streak = streak
            p.spin = 0f
        }
    }

    fun cone(x: Float, y: Float, count: Int, angle: Float, spread: Float, color: Int, speed: Float, size: Float, life: Float) {
        for (i in 0 until count) {
            val p = nextParticle()
            val a = angle + rnd(-spread, spread)
            val s = speed * rnd(0.4f, 1f)
            p.active = true
            p.x = x; p.y = y
            p.vx = cos(a) * s; p.vy = sin(a) * s
            p.maxLife = life * rnd(0.6f, 1.2f); p.life = p.maxLife
            p.size = size * rnd(0.6f, 1.3f)
            p.color = color
            p.drag = 2.6f
            p.streak = false
        }
    }

    fun shockwave(x: Float, y: Float, maxR: Float, color: Int, life: Float = 0.45f, width: Float = 3f) {
        for (i in waves.indices) {
            wIdx = (wIdx + 1) % waves.size
            val w = waves[wIdx]
            if (!w.active) {
                w.active = true
                w.x = x; w.y = y; w.r = 0f; w.maxR = maxR
                w.maxLife = life; w.life = life
                w.color = color; w.width = width
                return
            }
        }
    }

    fun popText(x: Float, y: Float, s: String, color: Int, size: Float = 18f, life: Float = 0.9f) {
        for (i in texts.indices) {
            tIdx = (tIdx + 1) % texts.size
            val t = texts[tIdx]
            if (!t.active) {
                t.active = true
                t.x = x; t.y = y; t.vy = -46f
                t.maxLife = life; t.life = life
                t.text = s; t.color = color; t.size = size
                return
            }
        }
    }

    fun update(dt: Float) {
        if (hitStop > 0f) hitStop = (hitStop - dt).coerceAtLeast(0f)

        trauma = (trauma - dt * 1.7f).coerceAtLeast(0f)
        val amp = trauma * trauma
        shakeX = rnd(-1f, 1f) * amp * 22f
        shakeY = rnd(-1f, 1f) * amp * 22f
        flashAmount = (flashAmount - dt * 3.2f).coerceAtLeast(0f)

        for (p in particles) {
            if (!p.active) continue
            p.life -= dt
            if (p.life <= 0f) { p.active = false; continue }
            p.x += p.vx * dt
            p.y += p.vy * dt
            val d = 1f - p.drag * dt
            val k = if (d < 0f) 0f else d
            p.vx *= k; p.vy *= k
        }
        for (w in waves) {
            if (!w.active) continue
            w.life -= dt
            if (w.life <= 0f) { w.active = false; continue }
            val t = 1f - w.life / w.maxLife
            w.r = w.maxR * (1f - (1f - t) * (1f - t))
        }
        for (t in texts) {
            if (!t.active) continue
            t.life -= dt
            if (t.life <= 0f) { t.active = false; continue }
            t.y += t.vy * dt
            t.vy *= 1f - 2.2f * dt
        }
    }

    fun drawParticles(c: Canvas) {
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.ROUND
        for (p in particles) {
            if (!p.active) continue
            val t = p.life / p.maxLife
            val a = t * t
            if (p.streak) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = p.size * t
                paint.color = fade(p.color, a)
                c.drawLine(p.x, p.y, p.x - p.vx * 0.035f, p.y - p.vy * 0.035f, paint)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = fade(p.color, a * 0.25f)
                c.drawCircle(p.x, p.y, p.size * t * 2.4f, paint)
                paint.color = fade(lighten(p.color, 0.4f), a)
                c.drawCircle(p.x, p.y, p.size * t, paint)
            }
        }
        for (w in waves) {
            if (!w.active) continue
            val t = w.life / w.maxLife
            Neon.ring(c, w.x, w.y, w.r, fade(w.color, t * 0.9f), w.width * t + 0.6f, t)
        }
    }

    fun drawTexts(c: Canvas) {
        for (t in texts) {
            if (!t.active) continue
            val k = t.life / t.maxLife
            val s = t.size * (1f + (1f - k) * 0.25f)
            Neon.label(c, t.text, t.x, t.y, s, fade(t.color, clamp(k * 1.6f, 0f, 1f)), Paint.Align.CENTER, 0.8f, 0.05f, Neon.FONT_NUM)
        }
    }

    /** Full-screen additive-ish flash, drawn last. */
    fun drawFlash(c: Canvas, w: Float, h: Float) {
        if (flashAmount <= 0.001f) return
        Neon.fillRect(c, 0f, 0f, w, h, fade(flashColor, flashAmount * 0.55f))
    }
}

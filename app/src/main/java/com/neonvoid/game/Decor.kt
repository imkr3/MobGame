package com.neonvoid.game

import android.graphics.Canvas
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * One piece of parallax furniture. The same pool serves every sector - what it
 * is drawn as, and how it moves, is decided by the terrain, so ten very
 * different-looking backdrops cost one array of sixty structs.
 */
class Mote {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var r = 6f
    var spin = 0f
    var rate = 0f
    /** 0 = far and slow, 1 = near and fast. Drives size, speed and alpha. */
    var depth = 0f
    var seed = 0f
    var sides = 5
}

/**
 * Everything that makes a sector look like a place rather than a palette. Each
 * terrain gets a horizon silhouette built once on resize, plus a per-frame
 * layer of motes and hand-drawn furniture. All of it lives behind the play
 * field and is deliberately low-contrast: the ships have to stay readable.
 */
object Decor {

    // ------------------------------------------------------------ silhouette

    /**
     * Builds the horizon profile for a terrain into [into]. The path is closed
     * down to [floorY] so it fills as a solid silhouette.
     */
    fun buildRidge(into: Path, terrain: Int, w: Float, horizon: Float, floorY: Float, seed: Int) {
        into.reset()
        val rng = Random(seed)
        into.moveTo(0f, floorY)
        when (terrain) {
            Terrain.GRID -> {
                // a synthwave skyline: blocks of varying height, flat tops
                var x = 0f
                into.lineTo(0f, horizon)
                while (x < w) {
                    val bw = 12f + rng.nextFloat() * 34f
                    val bh = 6f + rng.nextFloat() * rng.nextFloat() * 74f
                    into.lineTo(x, horizon - bh)
                    into.lineTo(x + bw, horizon - bh)
                    x += bw
                }
                into.lineTo(w, horizon)
            }
            Terrain.ICE -> {
                // crystal spires: sharp, uneven, occasionally very tall
                var x = 0f
                into.lineTo(0f, horizon)
                while (x < w) {
                    val bw = 16f + rng.nextFloat() * 30f
                    val bh = 10f + rng.nextFloat() * rng.nextFloat() * 118f
                    into.lineTo(x + bw * 0.5f, horizon - bh)
                    into.lineTo(x + bw, horizon - rng.nextFloat() * 14f)
                    x += bw
                }
            }
            Terrain.ASH -> {
                // burnt pillars: broken columns of very different heights
                var x = 0f
                into.lineTo(0f, horizon)
                while (x < w) {
                    val bw = 9f + rng.nextFloat() * 20f
                    val tall = rng.nextFloat() < 0.28f
                    val bh = if (tall) 40f + rng.nextFloat() * 90f else 4f + rng.nextFloat() * 22f
                    into.lineTo(x, horizon - bh)
                    into.lineTo(x + bw, horizon - bh * (0.7f + rng.nextFloat() * 0.3f))
                    x += bw
                }
                into.lineTo(w, horizon)
            }
            Terrain.OVERGROWN -> {
                // a soft, rolling canopy
                var x = 0f
                into.lineTo(0f, horizon)
                while (x < w) {
                    val bw = 26f + rng.nextFloat() * 30f
                    val bh = 12f + rng.nextFloat() * 46f
                    into.quadTo(x + bw * 0.5f, horizon - bh, x + bw, horizon - 6f)
                    x += bw
                }
            }
            Terrain.STATION, Terrain.FOUNDRY -> {
                // a hard machine skyline: towers, masts and blocky housings
                var x = 0f
                into.lineTo(0f, horizon)
                while (x < w) {
                    val bw = 18f + rng.nextFloat() * 26f
                    val bh = 8f + rng.nextFloat() * 62f
                    into.lineTo(x, horizon - bh)
                    if (rng.nextFloat() < 0.35f) {
                        // a thin mast on top
                        into.lineTo(x + bw * 0.45f, horizon - bh)
                        into.lineTo(x + bw * 0.45f, horizon - bh - 18f - rng.nextFloat() * 26f)
                        into.lineTo(x + bw * 0.55f, horizon - bh - 18f - rng.nextFloat() * 26f)
                        into.lineTo(x + bw * 0.55f, horizon - bh)
                    }
                    into.lineTo(x + bw, horizon - bh)
                    x += bw
                }
                into.lineTo(w, horizon)
            }
            else -> {
                // VOID, BLOOM and HOLLOW keep an open horizon
                into.lineTo(0f, horizon)
                into.lineTo(w, horizon)
            }
        }
        into.lineTo(w, floorY)
        into.close()
    }

    /** True when the terrain wants the perspective grid under its horizon. */
    fun hasGrid(terrain: Int): Boolean =
        terrain != Terrain.VOID && terrain != Terrain.BLOOM

    // ----------------------------------------------------------------- motes

    /** Seeds the mote pool for a terrain across the whole screen. */
    fun seedMotes(motes: Array<Mote>, terrain: Int, w: Float, h: Float, horizon: Float) {
        for (m in motes) {
            m.depth = rnd(0.15f, 1f)
            m.seed = rnd(TAU)
            m.x = rnd(-w * 0.1f, w * 1.1f)
            m.y = rnd(-h * 0.1f, h)
            m.spin = rnd(TAU)
            m.sides = 5 + Random.nextInt(3)
            when (terrain) {
                Terrain.BELT -> {
                    m.r = 4f + m.depth * 26f
                    m.vy = 18f + m.depth * 92f
                    m.vx = rnd(-14f, 14f)
                    m.rate = rnd(-1.1f, 1.1f)
                    m.sides = 5 + Random.nextInt(4)
                }
                Terrain.VOID -> {
                    m.r = 3f + m.depth * 44f
                    m.vy = 6f + m.depth * 26f
                    m.vx = rnd(-6f, 6f)
                    m.rate = rnd(-0.35f, 0.35f)
                    m.sides = 3 + Random.nextInt(4)
                }
                Terrain.OVERGROWN -> {
                    m.r = 1.6f + m.depth * 4.5f
                    m.vy = -rnd(8f, 30f) * m.depth      // spores drift upward
                    m.vx = rnd(-16f, 16f)
                    m.rate = rnd(-0.6f, 0.6f)
                }
                Terrain.ASH -> {
                    m.r = 1.2f + m.depth * 3.4f
                    m.vy = -rnd(24f, 86f) * m.depth     // embers rise
                    m.vx = rnd(-20f, 20f)
                    m.rate = rnd(-1.4f, 1.4f)
                }
                Terrain.ICE -> {
                    m.r = 2.5f + m.depth * 9f
                    m.vy = 26f + m.depth * 74f
                    m.vx = rnd(-30f, 10f)
                    m.rate = rnd(-2f, 2f)
                    m.sides = 4
                }
                Terrain.BLOOM -> {
                    m.r = 3f + m.depth * 11f
                    m.vy = 12f + m.depth * 44f
                    m.vx = rnd(-22f, 22f)
                    m.rate = rnd(-1.6f, 1.6f)
                    m.sides = 6
                }
                Terrain.STATION, Terrain.FOUNDRY -> {
                    m.r = 1.6f + m.depth * 6f
                    m.vy = 30f + m.depth * 120f
                    m.vx = rnd(-6f, 6f)
                    m.rate = rnd(-2.4f, 2.4f)
                    m.sides = 4
                }
                Terrain.HOLLOW -> {
                    m.r = 1f + m.depth * 2.6f
                    m.vy = 10f + m.depth * 34f
                    m.vx = 0f
                    m.rate = 0f
                }
                else -> {
                    m.r = 1.4f + m.depth * 3f
                    m.vy = 20f + m.depth * 70f
                    m.vx = rnd(-4f, 4f)
                    m.rate = rnd(-0.5f, 0.5f)
                }
            }
        }
    }

    fun updateMotes(motes: Array<Mote>, dt: Float, boost: Float, w: Float, h: Float) {
        for (m in motes) {
            m.x += m.vx * dt * boost
            m.y += m.vy * dt * boost
            m.spin += m.rate * dt
            val pad = m.r + 20f
            if (m.vy >= 0f && m.y - pad > h) { m.y = -pad; m.x = rnd(-w * 0.05f, w * 1.05f) }
            if (m.vy < 0f && m.y + pad < 0f) { m.y = h + pad; m.x = rnd(-w * 0.05f, w * 1.05f) }
            if (m.x < -pad) m.x = w + pad
            if (m.x > w + pad) m.x = -pad
        }
    }

    /**
     * Distant motes drop to a single hairline stroke per edge. There are dozens
     * of these every frame, and the glow stack is not worth paying for on
     * something the eye reads as a speck.
     */
    private fun polygon(c: Canvas, m: Mote, color: Int, fillA: Float, edgeA: Float, wide: Float) {
        val near = m.depth > 0.45f
        val n = if (near) m.sides.coerceIn(3, 8) else m.sides.coerceIn(3, 5)
        var px = m.x + cos(m.spin) * m.r
        var py = m.y + sin(m.spin) * m.r
        val col = fade(color, edgeA)
        for (i in 1..n) {
            val a = m.spin + i * TAU / n
            // a touch of per-vertex wobble so rocks are not regular polygons
            val rr = m.r * (0.78f + 0.28f * sin(m.seed + i * 2.1f))
            val nx = m.x + cos(a) * rr
            val ny = m.y + sin(a) * rr
            if (near) Neon.line(c, px, py, nx, ny, col, wide, 0.5f)
            else Neon.hairline(c, px, py, nx, ny, col, wide)
            px = nx; py = ny
        }
        if (fillA > 0f && near) Neon.softDisc(c, m.x, m.y, m.r * 0.9f, fade(color, fillA))
    }

    // -------------------------------------------------------------- terrains

    /**
     * The layer between the sky and the play field. [phase] is the scrolling
     * grid phase, reused so furniture stays in step with the ground.
     */
    fun draw(
        c: Canvas, terrain: Int, theme: LevelTheme, motes: Array<Mote>,
        w: Float, h: Float, horizon: Float, time: Float, phase: Float
    ) {
        when (terrain) {
            Terrain.BELT -> {
                for (m in motes) polygon(c, m, theme.nebula, 0.10f * m.depth, 0.30f + 0.35f * m.depth, 1f + m.depth)
                // a debris band cutting across the sun
                val y = horizon - h * 0.10f
                var x = -(time * 26f) % 90f
                while (x < w) {
                    Neon.hairline(c, x, y + sin(x * 0.03f) * 5f, x + 44f, y + sin((x + 44f) * 0.03f) * 5f,
                        fade(theme.accent, 0.16f), 1.4f)
                    x += 90f
                }
            }
            Terrain.VOID -> {
                // slow wireframe hulks and a field of dead pixels
                for (m in motes) {
                    if (m.depth > 0.55f) polygon(c, m, theme.grid, 0.05f, 0.22f, 1.2f)
                    else Neon.softDisc(c, m.x, m.y, m.r * 0.35f, fade(Palette.WHITE, 0.35f))
                }
                val rr = w * (0.30f + 0.05f * sin(time * 0.21f))
                ringPoly(c, w * 0.5f, h * 0.42f, rr, 7, time * 0.06f, fade(theme.grid, 0.22f), 2f)
                ringPoly(c, w * 0.5f, h * 0.42f, rr * 0.62f, 5, -time * 0.09f, fade(theme.accent, 0.26f), 1.6f)
                ringPoly(c, w * 0.5f, h * 0.42f, rr * 0.30f, 4, time * 0.14f, fade(Palette.WHITE, 0.14f), 1.2f)
            }
            Terrain.STATION -> {
                girders(c, w, h, horizon, time, theme)
                for (m in motes) {
                    Neon.fillRect(c, m.x, m.y, m.x + m.r * 1.6f, m.y + m.r * 0.5f, fade(theme.grid, 0.22f * m.depth))
                }
            }
            Terrain.FOUNDRY -> {
                // gear wheels behind the sun, then pipe bands across the floor
                ringPoly(c, w * 0.22f, horizon - h * 0.13f, w * 0.16f, 12, time * 0.5f, fade(theme.accent, 0.18f), 2f)
                ringPoly(c, w * 0.22f, horizon - h * 0.13f, w * 0.10f, 8, -time * 0.7f, fade(theme.accent, 0.14f), 1.5f)
                ringPoly(c, w * 0.80f, horizon - h * 0.07f, w * 0.11f, 10, -time * 0.6f, fade(theme.grid, 0.16f), 1.8f)
                pipes(c, w, h, horizon, phase, theme)
                for (m in motes) Neon.softDisc(c, m.x, m.y, m.r * 0.7f, fade(Palette.AMBER, 0.30f * m.depth))
            }
            Terrain.OVERGROWN -> {
                fronds(c, w, h, horizon, time, theme)
                for (m in motes) {
                    Neon.softDisc(c, m.x, m.y, m.r * 2.2f, fade(theme.accent, 0.07f * m.depth))
                    Neon.softDisc(c, m.x, m.y, m.r * 0.7f, fade(Palette.WHITE, 0.34f * m.depth))
                }
            }
            Terrain.ASH -> {
                smoke(c, w, h, horizon, time, theme)
                for (m in motes) {
                    val flick = 0.5f + 0.5f * sin(time * 9f + m.seed)
                    Neon.softDisc(c, m.x, m.y, m.r * 2.4f, fade(0xFFFF7A2A.toInt(), 0.10f * m.depth * flick))
                    Neon.softDisc(c, m.x, m.y, m.r * 0.8f, fade(0xFFFFC46A.toInt(), 0.65f * m.depth * flick))
                }
            }
            Terrain.ICE -> {
                for (m in motes) {
                    polygon(c, m, theme.accent, 0.06f, 0.34f * m.depth + 0.12f, 1f)
                }
                // cold shafts of light down from the spires
                for (i in 0 until 5) {
                    val x = w * (0.12f + i * 0.19f) + sin(time * 0.2f + i) * 12f
                    Neon.fillRect(c, x - 10f, horizon - 30f, x + 10f, h, fade(theme.accent, 0.035f))
                }
            }
            Terrain.BLOOM -> {
                for (m in motes) {
                    Neon.softDisc(c, m.x, m.y, m.r * 3.2f, fade(theme.nebula, 0.10f * m.depth))
                    petal(c, m, theme.accent)
                }
            }
            Terrain.HOLLOW -> {
                for (m in motes) Neon.softDisc(c, m.x, m.y, m.r, fade(Palette.WHITE, 0.30f * m.depth))
                // far-off wrecks, barely lit, drifting sideways
                for (i in 0 until 4) {
                    val dx = ((time * (5f + i * 3f) + i * 190f) % (w + 300f)) - 150f
                    val dy = horizon * (0.30f + i * 0.19f)
                    val sz = 16f + i * 9f
                    Neon.hairline(c, dx - sz, dy, dx + sz, dy - sz * 0.3f, fade(theme.grid, 0.16f), 1.4f)
                    Neon.hairline(c, dx + sz, dy - sz * 0.3f, dx + sz * 0.4f, dy + sz * 0.5f, fade(theme.grid, 0.13f), 1.2f)
                    Neon.hairline(c, dx * 1f, dy + sz * 0.5f, dx - sz, dy, fade(theme.grid, 0.10f), 1.1f)
                }
                // the signal drops out every few seconds
                val g = sin(time * 0.7f) * sin(time * 3.1f)
                if (g > 0.86f) {
                    val y = (time * 940f) % h
                    Neon.fillRect(c, 0f, y, w, y + 3f, fade(Palette.WHITE, 0.10f))
                    Neon.fillRect(c, 0f, y + 12f, w, y + 14f, fade(theme.accent, 0.08f))
                }
            }
            else -> {
                // GRID: lit windows crawling along the skyline
                for (m in motes) Neon.softDisc(c, m.x, m.y, m.r, fade(theme.grid, 0.35f * m.depth))
                windows(c, w, horizon, time, theme)
            }
        }
    }

    // ------------------------------------------------------------- furniture

    private fun ringPoly(c: Canvas, cx: Float, cy: Float, r: Float, n: Int, rot: Float, color: Int, wide: Float) {
        var px = cx + cos(rot) * r
        var py = cy + sin(rot) * r
        for (i in 1..n) {
            val a = rot + i * TAU / n
            val nx = cx + cos(a) * r
            val ny = cy + sin(a) * r
            Neon.line(c, px, py, nx, ny, color, wide, 0.5f)
            px = nx; py = ny
        }
    }

    /** VIOLET DEPTHS: broken frames sliding past on both edges. */
    private fun girders(c: Canvas, w: Float, h: Float, horizon: Float, time: Float, theme: LevelTheme) {
        val pitch = 190f
        var y = horizon + ((time * 62f) % pitch) - pitch
        var i = 0
        while (y < h + pitch) {
            val inset = w * 0.085f
            val depth = clamp((y - horizon) / (h - horizon), 0f, 1f)
            val a = 0.22f + 0.46f * depth
            val col = fade(theme.grid, a)
            val bar = 20f + depth * 26f
            // two uprights and a cross member, left and right
            Neon.fillRect(c, -4f, y, inset, y + bar, fade(theme.nebula, a * 0.55f))
            Neon.fillRect(c, w - inset, y, w + 4f, y + bar, fade(theme.nebula, a * 0.55f))
            Neon.line(c, 0f, y, inset, y, col, 2.2f, 0.5f)
            Neon.line(c, w - inset, y, w, y, col, 2.2f, 0.5f)
            Neon.line(c, inset, y - 26f, inset, y + bar + 26f, col, 1.8f, 0.5f)
            Neon.line(c, w - inset, y - 26f, w - inset, y + bar + 26f, col, 1.8f, 0.5f)
            // diagonal bracing between frames
            Neon.hairline(c, 0f, y, inset, y - 26f, fade(theme.grid, a * 0.5f), 1.2f)
            Neon.hairline(c, w, y, w - inset, y - 26f, fade(theme.grid, a * 0.5f), 1.2f)
            // a hazard light on alternating frames
            if (i % 2 == 0) {
                val blink = 0.35f + 0.65f * (0.5f + 0.5f * sin(time * 4f + i))
                Neon.orb(c, inset * 0.5f, y + 8f, 3.2f, fade(Palette.RED, blink * (0.4f + depth)), 1f)
                Neon.orb(c, w - inset * 0.5f, y + 8f, 3.2f, fade(Palette.RED, blink * (0.4f + depth)), 1f)
            }
            y += pitch
            i++
        }
    }

    /** GOLD CIRCUIT: pipe bands running with the grid. */
    private fun pipes(c: Canvas, w: Float, h: Float, horizon: Float, phase: Float, theme: LevelTheme) {
        val depth = h - horizon
        for (i in 0 until 5) {
            var t = (i + phase) / 5f
            t *= t
            val y = horizon + depth * t
            val a = 0.05f + 0.20f * t
            Neon.hairline(c, 0f, y, w, y, fade(theme.accent, a), 3f + t * 5f)
            Neon.hairline(c, 0f, y + 5f + t * 6f, w, y + 5f + t * 6f, fade(Palette.AMBER, a * 0.6f), 1.4f)
            // rivets
            var x = 20f
            while (x < w) {
                Neon.softDisc(c, x, y, 1.5f + t * 2.5f, fade(Palette.AMBER, a * 1.4f))
                x += 60f
            }
        }
    }

    /** EMERALD DRIFT: fronds leaning in from both sides, swaying. */
    private fun fronds(c: Canvas, w: Float, h: Float, horizon: Float, time: Float, theme: LevelTheme) {
        val span = h - horizon
        for (i in 0 until 8) {
            val left = i % 2 == 0
            val dir = if (left) 1f else -1f
            val baseX = if (left) -18f else w + 18f
            // spread the anchors down the side of the play field
            val baseY = horizon + span * (0.16f + (i / 2) * 0.24f) + (if (left) 0f else span * 0.11f)
            val sway = sin(time * 0.55f + i * 1.3f) * 0.16f
            val len = w * (0.30f + 0.12f * ((i * 3) % 4) / 3f)
            var px = baseX
            var py = baseY
            for (k in 1..8) {
                val f = k / 8f
                val nx = baseX + dir * len * f
                val ny = baseY - len * 0.62f * f * f + sin(time * 0.55f + i + f * 2.2f) * 12f * f
                Neon.line(c, px, py, nx, ny, fade(theme.accent, 0.26f), 6f - f * 4.4f, 0.5f)
                // leaflets fanning back along the stem
                val lf = 26f * (1f - f * 0.5f)
                Neon.hairline(c, nx, ny, nx - dir * lf, ny - lf * 0.7f, fade(theme.accent, 0.18f), 1.8f)
                Neon.hairline(c, nx, ny, nx - dir * lf * 0.9f, ny + lf * 0.6f, fade(theme.accent, 0.18f), 1.8f)
                px = nx; py = ny
            }
            Neon.softDisc(c, px, py, 9f + sway * 10f, fade(theme.accent, 0.16f))
        }
    }

    /** ASH REACH: slow horizontal smoke bands drifting across the sky. */
    private fun smoke(c: Canvas, w: Float, h: Float, horizon: Float, time: Float, theme: LevelTheme) {
        for (i in 0 until 6) {
            val y = horizon * (0.12f + i * 0.15f) + sin(time * 0.25f + i * 1.7f) * 8f
            val x = ((time * (9f + i * 5f) + i * 220f) % (w + 460f)) - 230f
            val bw = 170f + i * 46f
            Neon.softDisc(c, x, y, bw * 0.5f, fade(0xFF6B4A3A.toInt(), 0.10f))
            Neon.softDisc(c, x + bw * 0.4f, y + 6f, bw * 0.34f, fade(0xFF4A3830.toInt(), 0.12f))
        }
    }

    /** NEON REACH: windows blinking on and off in the skyline. */
    private fun windows(c: Canvas, w: Float, horizon: Float, time: Float, theme: LevelTheme) {
        var x = 6f
        var i = 0
        while (x < w) {
            val col = if (i % 3 == 0) theme.accent else theme.grid
            val lit = sin(time * 0.8f + i * 2.3f) > 0.15f
            if (lit) {
                val hgt = 4f + (i * 13 % 7)
                Neon.fillRect(c, x, horizon - 6f - hgt, x + 2.6f, horizon - 6f, fade(col, 0.35f))
            }
            x += 11f
            i++
        }
    }

    private fun petal(c: Canvas, m: Mote, color: Int) {
        val a = m.spin
        val hx = cos(a) * m.r
        val hy = sin(a) * m.r
        val wx = -sin(a) * m.r * 0.45f
        val wy = cos(a) * m.r * 0.45f
        Neon.line(c, m.x - hx, m.y - hy, m.x + wx, m.y + wy, fade(color, 0.42f), 1.4f, 0.4f)
        Neon.line(c, m.x + wx, m.y + wy, m.x + hx, m.y + hy, fade(color, 0.42f), 1.4f, 0.4f)
        Neon.line(c, m.x + hx, m.y + hy, m.x - wx, m.y - wy, fade(color, 0.42f), 1.4f, 0.4f)
        Neon.line(c, m.x - wx, m.y - wy, m.x - hx, m.y - hy, fade(color, 0.42f), 1.4f, 0.4f)
    }
}

package com.neonvoid.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowInsets

/** SurfaceView with a dedicated render thread. */
class GameView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    private val game = Game(context)
    private val renderLock = Any()

    @Volatile private var running = false
    private var thread: Thread? = null
    private var lastNanos = 0L

    init {
        holder.addCallback(this)
        isFocusable = true
        keepScreenOn = true
    }

    // ------------------------------------------------------------- surface

    override fun surfaceCreated(holder: SurfaceHolder) {
        startLoop()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        synchronized(renderLock) { game.resize(width, height) }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopLoop()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val top: Int
        val bottom: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val i = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            top = i.top
            bottom = i.bottom
        } else {
            @Suppress("DEPRECATION")
            top = insets.systemWindowInsetTop
            @Suppress("DEPRECATION")
            bottom = insets.systemWindowInsetBottom
        }
        synchronized(renderLock) { game.setInsets(top.toFloat(), bottom.toFloat()) }
        return super.onApplyWindowInsets(insets)
    }

    // -------------------------------------------------------------- thread

    fun onResumeGame() {
        startLoop()
    }

    fun onPauseGame() {
        game.onAppPause()
        stopLoop()
    }

    fun handleBack(): Boolean = game.onBack()

    private fun startLoop() {
        if (running || !holder.surface.isValid) return
        running = true
        lastNanos = 0L
        thread = Thread(this, "NeonVoidLoop").also { it.start() }
    }

    private fun stopLoop() {
        running = false
        val t = thread
        thread = null
        if (t != null) {
            try {
                t.join(1500)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    override fun run() {
        while (running) {
            val frameStart = System.nanoTime()
            if (lastNanos == 0L) lastNanos = frameStart
            val dt = (frameStart - lastNanos) / 1_000_000_000f
            lastNanos = frameStart

            val h = holder
            if (!h.surface.isValid) {
                sleepQuietly(6)
                continue
            }

            var canvas: Canvas? = null
            try {
                canvas = lockCanvasCompat(h)
                if (canvas != null) {
                    synchronized(renderLock) {
                        game.update(dt)
                        game.draw(canvas)
                    }
                }
            } catch (_: IllegalStateException) {
                // Surface went away mid-frame; the next iteration re-checks validity.
            } finally {
                if (canvas != null) {
                    try {
                        h.unlockCanvasAndPost(canvas)
                    } catch (_: IllegalStateException) {
                    }
                }
            }

            // Cap at roughly 120 fps so we never spin the CPU on high-refresh panels.
            val elapsedMs = (System.nanoTime() - frameStart) / 1_000_000L
            if (elapsedMs < 8L) sleepQuietly(8L - elapsedMs)
        }
    }

    private fun lockCanvasCompat(h: SurfaceHolder): Canvas? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) h.lockHardwareCanvas() else h.lockCanvas()

    private fun sleepQuietly(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    // --------------------------------------------------------------- touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                game.postTouch(Game.DOWN, event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    game.postTouch(Game.MOVE, event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = event.actionIndex
                game.postTouch(Game.UP, event.getPointerId(i), event.getX(i), event.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in 0 until event.pointerCount) {
                    game.postTouch(Game.UP, event.getPointerId(i), event.getX(i), event.getY(i))
                }
            }
        }
        return true
    }
}

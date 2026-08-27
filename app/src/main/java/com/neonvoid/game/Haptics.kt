package com.neonvoid.game

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/** Thin vibration wrapper. Amplitude control is used where the device supports it. */
class Haptics(context: Context) {

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? =
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

    var enabled = true

    private val supported: Boolean = vibrator?.hasVibrator() ?: false

    @Suppress("DEPRECATION")
    fun tap(ms: Long, amplitude: Int = -1) {
        if (!enabled || !supported) return
        val v = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (amplitude in 1..255 && v.hasAmplitudeControl()) amplitude
                else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                v.vibrate(ms)
            }
        } catch (_: Exception) {
            // Vibration is a nicety; never let it take the game down.
        }
    }

    fun light() = tap(10, 70)
    fun medium() = tap(22, 140)
    fun heavy() = tap(48, 230)
}

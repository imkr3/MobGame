package com.neonvoid.game

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("neon_void", Context.MODE_PRIVATE)

    var bestScore: Int
        get() = sp.getInt("best_score", 0)
        set(v) = sp.edit().putInt("best_score", v).apply()

    var bestWave: Int
        get() = sp.getInt("best_wave", 0)
        set(v) = sp.edit().putInt("best_wave", v).apply()

    var bestCombo: Int
        get() = sp.getInt("best_combo", 0)
        set(v) = sp.edit().putInt("best_combo", v).apply()

    var runs: Int
        get() = sp.getInt("runs", 0)
        set(v) = sp.edit().putInt("runs", v).apply()

    var hapticsOn: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Returns true when this run set a new personal best. */
    fun submit(score: Int, wave: Int, combo: Int): Boolean {
        val newBest = score > bestScore
        if (newBest) bestScore = score
        if (wave > bestWave) bestWave = wave
        if (combo > bestCombo) bestCombo = combo
        runs += 1
        return newBest
    }
}

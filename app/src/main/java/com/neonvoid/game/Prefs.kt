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

    var cores: Int
        get() = sp.getInt("cores", 0)
        set(v) = sp.edit().putInt("cores", v.coerceAtLeast(0)).apply()

    /** Bitmask of unlocked hulls; the starter is always available. */
    var ownedShips: Int
        get() = sp.getInt("ships", 0) or (1 shl ShipDex.STARTER)
        set(v) = sp.edit().putInt("ships", v).apply()

    var selectedShip: Int
        get() = sp.getInt("ship", ShipDex.STARTER)
        set(v) = sp.edit().putInt("ship", v).apply()

    var pulls: Int
        get() = sp.getInt("pulls", 0)
        set(v) = sp.edit().putInt("pulls", v).apply()

    var musicOn: Boolean
        get() = sp.getBoolean("music", true)
        set(v) = sp.edit().putBoolean("music", v).apply()

    var sfxOn: Boolean
        get() = sp.getBoolean("sfx", true)
        set(v) = sp.edit().putBoolean("sfx", v).apply()

    var hapticsOn: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    /** Cores earned from a finished run, the gacha currency. */
    fun coresFor(score: Int, wave: Int): Int = score / 400 + wave * 12 + 20

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

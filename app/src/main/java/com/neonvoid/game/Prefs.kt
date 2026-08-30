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

    fun shopLevel(id: Int): Int = sp.getInt("shop_$id", 0)

    fun setShopLevel(id: Int, level: Int) {
        sp.edit().putInt("shop_$id", level).apply()
    }

    var bestLevel: Int
        get() = sp.getInt("best_level", 0)
        set(v) = sp.edit().putInt("best_level", v).apply()

    var totalCores: Int
        get() = sp.getInt("total_cores", 0)
        set(v) = sp.edit().putInt("total_cores", v).apply()

    var totalKills: Int
        get() = sp.getInt("total_kills", 0)
        set(v) = sp.edit().putInt("total_kills", v).apply()

    /** Last host address typed in the co-op lobby. */
    var lastHost: String
        get() = sp.getString("last_host", "") ?: ""
        set(v) = sp.edit().putString("last_host", v).apply()

    /** Level the next run launches from, as a zero-based theme index. */
    var startLevel: Int
        get() = sp.getInt("start_level", 0)
        set(v) = sp.edit().putInt("start_level", v).apply()

    var hapticsOn: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()

    // ---------------------------------------------------------- pilot rank

    var rank: Int
        get() = sp.getInt("rank", 1).coerceIn(1, Rank.MAX)
        set(v) = sp.edit().putInt("rank", v.coerceIn(1, Rank.MAX)).apply()

    /** Experience banked towards the next rank, never the lifetime total. */
    var xp: Int
        get() = sp.getInt("xp", 0)
        set(v) = sp.edit().putInt("xp", v.coerceAtLeast(0)).apply()

    var missionsDone: Int
        get() = sp.getInt("missions_done", 0)
        set(v) = sp.edit().putInt("missions_done", v).apply()

    /**
     * Banks experience and climbs as far as it carries. Returns the cores the
     * rank-ups paid, so the caller can show them.
     */
    fun addXp(amount: Int): Int {
        if (amount <= 0) return 0
        var r = rank
        var x = xp + amount
        var paid = 0
        while (r < Rank.MAX && x >= Rank.toNext(r)) {
            x -= Rank.toNext(r)
            r++
            paid += Rank.reward(r)
        }
        if (r >= Rank.MAX) x = 0
        rank = r
        xp = x
        if (paid > 0) {
            cores += paid
            totalCores += paid
        }
        return paid
    }

    // ------------------------------------------------------------ contracts

    fun missionKind(slot: Int): Int = sp.getInt("m_kind_$slot", 0)
    fun missionTarget(slot: Int): Int = sp.getInt("m_target_$slot", 0)
    fun missionProgress(slot: Int): Int = sp.getInt("m_prog_$slot", 0)
    fun missionReward(slot: Int): Int = sp.getInt("m_reward_$slot", 0)

    fun setMission(slot: Int, kind: Int, target: Int, progress: Int, reward: Int) {
        sp.edit()
            .putInt("m_kind_$slot", kind)
            .putInt("m_target_$slot", target)
            .putInt("m_prog_$slot", progress)
            .putInt("m_reward_$slot", reward)
            .apply()
    }

    fun setMissionProgress(slot: Int, progress: Int) {
        sp.edit().putInt("m_prog_$slot", progress).apply()
    }

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

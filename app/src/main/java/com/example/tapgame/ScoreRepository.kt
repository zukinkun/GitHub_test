package com.example.tapgame

import android.content.Context

interface ScoreStore {
    fun highScore(): Int
    fun saveHighScore(score: Int)
}

class SharedPrefsScoreStore(context: Context) : ScoreStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("tapgame", Context.MODE_PRIVATE)

    override fun highScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    override fun saveHighScore(score: Int) {
        prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    private companion object {
        const val KEY_HIGH_SCORE = "high_score"
    }
}

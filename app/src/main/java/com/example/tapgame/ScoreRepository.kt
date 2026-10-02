package com.example.tapgame

import android.content.Context

/**
 * スコアを端末に保存・読み込みするための設計図（インターフェース）。
 * 本番では SharedPreferences を使い、テスト時はテスト用の偽物（Fake）を差し替えることができます。
 */
interface ScoreStore {
    /** 保存されているハイスコアを取得する */
    fun highScore(): Int
    /** 新しいハイスコアを保存する */
    fun saveHighScore(score: Int)
}

/**
 * Android の SharedPreferences（アプリ専用の小型Key-Valueストレージ）を使って
 * ハイスコアをアプリ再起動後も保持するクラス。
 */
class SharedPrefsScoreStore(context: Context) : ScoreStore {
    // "tapgame" という名前の保存領域を取得
    private val prefs = context.applicationContext
        .getSharedPreferences("tapgame", Context.MODE_PRIVATE)

    /** 保存されているハイスコアを読み出す（未保存の場合は 0） */
    override fun highScore(): Int = prefs.getInt(KEY_HIGH_SCORE, 0)

    /** ハイスコアを書き込んで保存する */
    override fun saveHighScore(score: Int) {
        prefs.edit().putInt(KEY_HIGH_SCORE, score).apply()
    }

    private companion object {
        // 保存キーの名前
        const val KEY_HIGH_SCORE = "high_score"
    }
}

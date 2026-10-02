package com.example.tapgame

import androidx.annotation.StringRes

/**
 * ゲームの状態（フェーズ）を表す列挙型
 * - Idle: 開始前・待機中
 * - Countdown: 「3, 2, 1, GO!」のカウントダウン中
 * - Playing: 制限時間内のプレイ中
 * - Finished: タイムアップ後の結果表示中
 */
enum class Phase { Idle, Countdown, Playing, Finished }

/**
 * スコアに応じた称号（ランク）
 * minScore 以上で最も条件を満たす高いものが選ばれます。
 *
 * 【★カスタマイズのヒント★】
 * ここで必要なスコアの基準（minScore）を変えたり、新しい称号を追加してみよう！
 * 例: SuperGodspeed(100, ...) などを足してみるなど
 */
enum class Rank(val minScore: Int, @StringRes val labelRes: Int) {
    Beginner(0, R.string.rank_beginner),       // 初心者: 0点以上
    Apprentice(30, R.string.rank_apprentice),  // 見習い: 30点以上
    Expert(50, R.string.rank_expert),          // 達人: 50点以上
    Godspeed(70, R.string.rank_godspeed);      // 神速: 70点以上

    companion object {
        /** スコアから該当する称号を判定して返す関数 */
        fun of(score: Int): Rank = entries.last { score >= it.minScore }
    }
}

/**
 * 画面（UI）に表示するために必要なデータをひとまとめにしたクラス（UI状態）
 * ViewModel からこのデータが発行され、MainActivity が受け取って画面を描画します。
 */
data class GameUiState(
    /** 現在のゲーム状態（待機、カウントダウン、プレイ中、終了） */
    val phase: Phase = Phase.Idle,

    /** カウントダウンの残り秒数（3 → 2 → 1 → 0。0 は「GO!」を表す） */
    val countdown: Int = 0,

    /** 現在のタップ回数（スコア） */
    val score: Int = 0,

    /** 残り時間（ミリ秒単位。1秒 = 1000ms） */
    val remainingMs: Long = GameViewModel.GAME_DURATION_MS,

    /** 連続タップのコンボ数 */
    val combo: Int = 0,

    /** これまでの最高記録（ハイスコア） */
    val highScore: Int = 0,

    /** 今回のスコアがハイスコアを更新したかどうか */
    val isNewRecord: Boolean = false,

    /** 1秒あたりのタップ速度（スコア ÷ 制限時間秒） */
    val tapsPerSec: Float = 0f,

    /** 獲得した称号 */
    val rank: Rank = Rank.Beginner,

    /** アプリを途中で閉じたりして中断された直後かどうか */
    val interrupted: Boolean = false,
) {
    /**
     * 残り時間の割合 (開始時 1.0 → 終了時 0.0)
     * プログレスバーの長さ計算に使用します。
     */
    val progress: Float
        get() = remainingMs.toFloat() / GameViewModel.GAME_DURATION_MS
}

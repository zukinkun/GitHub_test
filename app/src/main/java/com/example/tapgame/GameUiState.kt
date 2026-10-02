package com.example.tapgame

import androidx.annotation.StringRes

enum class Phase { Idle, Countdown, Playing, Finished }

/** スコアに応じた称号。minScore 以上で最も高いものが選ばれる。 */
enum class Rank(val minScore: Int, @StringRes val labelRes: Int) {
    Beginner(0, R.string.rank_beginner),
    Apprentice(30, R.string.rank_apprentice),
    Expert(50, R.string.rank_expert),
    Godspeed(70, R.string.rank_godspeed);

    companion object {
        fun of(score: Int): Rank = entries.last { score >= it.minScore }
    }
}

data class GameUiState(
    val phase: Phase = Phase.Idle,
    /** カウントダウン表示値。0 は「GO!」。 */
    val countdown: Int = 0,
    val score: Int = 0,
    val remainingMs: Long = GameViewModel.GAME_DURATION_MS,
    val combo: Int = 0,
    val highScore: Int = 0,
    val isNewRecord: Boolean = false,
    val tapsPerSec: Float = 0f,
    val rank: Rank = Rank.Beginner,
    /** バックグラウンド移行などでゲームが中断された直後だけ true。 */
    val interrupted: Boolean = false,
) {
    /** 残り時間の割合 (1.0 → 0.0)。 */
    val progress: Float
        get() = remainingMs.toFloat() / GameViewModel.GAME_DURATION_MS
}

package com.example.tapgame

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ゲームの進行や計算ロジック（タイマー、スコア、コンボ判定）を管理する ViewModel。
 * 画面（Activity）が回転・再生成されてもデータが消えずに保持されます。
 */
class GameViewModel(
    private val store: ScoreStore,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    // 画面に公開するUI状態（外部からは読み取り専用の StateFlow として公開）
    private val _state = MutableStateFlow(GameUiState(highScore = store.highScore()))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    // カウントダウンやゲームタイマーを動かすコルーチン（非同期処理）の参照
    private var gameJob: Job? = null
    // 直前にタップした時刻（ミリ秒）
    private var lastTapAt = NO_TAP

    /**
     * ゲームを開始する処理
     * 「ゲームスタート」または「もう一度」ボタンを押したときに呼ばれます。
     */
    fun start() {
        val phase = _state.value.phase
        // すでにカウントダウン中またはプレイ中なら二重起動しない
        if (phase == Phase.Countdown || phase == Phase.Playing) return

        lastTapAt = NO_TAP
        // スコアなどを初期化してカウントダウン開始
        _state.value = GameUiState(highScore = store.highScore())
        gameJob = viewModelScope.launch {
            // 3, 2, 1, 0(GO!) とカウントダウンを進める
            for (n in COUNTDOWN_FROM downTo 0) {
                _state.update { it.copy(phase = Phase.Countdown, countdown = n) }
                // 0（GO!）のときは短めに表示し、それ以外は1秒（1000ms）待つ
                delay(if (n == 0) GO_DISPLAY_MS else 1000L)
            }
            // カウントダウン終了後、本番プレイへ移行
            play()
        }
    }

    /**
     * 制限時間をカウントダウンしながらゲームを進行する処理
     */
    private suspend fun play() {
        val startedAt = now()
        _state.update { it.copy(phase = Phase.Playing, remainingMs = GAME_DURATION_MS) }

        // 残り時間がなくなるまでループ
        while (true) {
            val remaining = GAME_DURATION_MS - (now() - startedAt)
            if (remaining <= 0) break
            _state.update { it.copy(remainingMs = remaining) }
            delay(FRAME_MS) // 画面更新間隔（50ミリ秒ごとに残り時間を更新）
        }
        // タイムアップ
        finish()
    }

    /**
     * タイムアップ時の結果集計・保存処理
     */
    private fun finish() {
        val score = _state.value.score
        val previousBest = store.highScore()
        val isNewRecord = score > previousBest

        // ハイスコア更新なら端末（SharedPreferences）に保存
        if (isNewRecord) store.saveHighScore(score)

        _state.update {
            it.copy(
                phase = Phase.Finished,
                remainingMs = 0,
                combo = 0,
                highScore = maxOf(previousBest, score),
                isNewRecord = isNewRecord,
                tapsPerSec = score * 1000f / GAME_DURATION_MS, // 1秒あたりのタップ数
                rank = Rank.of(score),                        // スコアに応じた称号
            )
        }
    }

    /**
     * タップボタンを押したときの処理
     *
     * 【★カスタマイズのヒント★】
     * 1タップあたりの加算スコアを変更したり、
     * 「10コンボ以上なら2点加算されるボーナス」などを追加してみよう！
     * 例: val bonus = if (it.combo >= 10) 2 else 1
     */
    fun onTap() {
        // プレイ中でなければタップを無視
        if (_state.value.phase != Phase.Playing) return

        val t = now()
        // 前回のタップから指定時間（COMBO_WINDOW_MS）以内ならコンボ継続
        val chained = lastTapAt != NO_TAP && t - lastTapAt <= COMBO_WINDOW_MS
        lastTapAt = t

        _state.update {
            it.copy(
                score = it.score + 1, // ここでスコアを加算
                combo = if (chained) it.combo + 1 else 1 // コンボ数の計算
            )
        }
    }

    /**
     * アプリがバックグラウンドに移動した際（別アプリ起動やホームボタンなど）の処理。
     * 不正や予期せぬ時間経過を防ぐため、進行中のゲームを破棄してスコアを無効にします。
     */
    fun interrupt() {
        val phase = _state.value.phase
        if (phase != Phase.Countdown && phase != Phase.Playing) return
        gameJob?.cancel() // 実行中のタイマーを停止
        _state.value = GameUiState(highScore = store.highScore(), interrupted = true)
    }

    /**
     * 【★生徒向けカスタマイズポイント（設定値の変更）★】
     * ゲームバランスを調整したいときは、ここの数値を書き換えてみよう！
     */
    companion object {
        /**
         * ゲームの制限時間（ミリ秒単位）
         * 10_000L = 10秒。 5_000Lにすると「5秒連打勝負」、30_000Lにすると「30秒耐久」になります。
         */
        const val GAME_DURATION_MS = 10_000L

        /**
         * コンボが繋がる猶予時間（ミリ秒単位）
         * 500L = 0.5秒。
         * 300Lにすると「激ムズ（すばやく連打が必要）」、800Lにすると「やさしめ」になります。
         */
        const val COMBO_WINDOW_MS = 500L

        /** カウントダウン開始秒数（通常は3秒前スタート） */
        private const val COUNTDOWN_FROM = 3

        /** 「GO!」の表示時間（ミリ秒。500ms = 0.5秒） */
        private const val GO_DISPLAY_MS = 500L

        /** タイマー表示を更新する頻度（ミリ秒単位。50ms = 1秒間に約20回更新） */
        private const val FRAME_MS = 50L

        /** 未タップ状態を表す定数 */
        private const val NO_TAP = -1L
    }
}

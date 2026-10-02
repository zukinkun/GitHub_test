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

class GameViewModel(
    private val store: ScoreStore,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
) : ViewModel() {

    private val _state = MutableStateFlow(GameUiState(highScore = store.highScore()))
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var gameJob: Job? = null
    private var lastTapAt = NO_TAP

    fun start() {
        val phase = _state.value.phase
        if (phase == Phase.Countdown || phase == Phase.Playing) return

        lastTapAt = NO_TAP
        _state.value = GameUiState(highScore = store.highScore())
        gameJob = viewModelScope.launch {
            for (n in COUNTDOWN_FROM downTo 0) {
                _state.update { it.copy(phase = Phase.Countdown, countdown = n) }
                delay(if (n == 0) GO_DISPLAY_MS else 1000L)
            }
            play()
        }
    }

    private suspend fun play() {
        val startedAt = now()
        _state.update { it.copy(phase = Phase.Playing, remainingMs = GAME_DURATION_MS) }
        while (true) {
            val remaining = GAME_DURATION_MS - (now() - startedAt)
            if (remaining <= 0) break
            _state.update { it.copy(remainingMs = remaining) }
            delay(FRAME_MS)
        }
        finish()
    }

    private fun finish() {
        val score = _state.value.score
        val previousBest = store.highScore()
        val isNewRecord = score > previousBest
        if (isNewRecord) store.saveHighScore(score)
        _state.update {
            it.copy(
                phase = Phase.Finished,
                remainingMs = 0,
                combo = 0,
                highScore = maxOf(previousBest, score),
                isNewRecord = isNewRecord,
                tapsPerSec = score * 1000f / GAME_DURATION_MS,
                rank = Rank.of(score),
            )
        }
    }

    fun onTap() {
        if (_state.value.phase != Phase.Playing) return
        val t = now()
        val chained = lastTapAt != NO_TAP && t - lastTapAt <= COMBO_WINDOW_MS
        lastTapAt = t
        _state.update {
            it.copy(score = it.score + 1, combo = if (chained) it.combo + 1 else 1)
        }
    }

    /** バックグラウンド移行など。進行中のゲームを破棄してスコアを無効にする。 */
    fun interrupt() {
        val phase = _state.value.phase
        if (phase != Phase.Countdown && phase != Phase.Playing) return
        gameJob?.cancel()
        _state.value = GameUiState(highScore = store.highScore(), interrupted = true)
    }

    companion object {
        const val GAME_DURATION_MS = 10_000L
        const val COMBO_WINDOW_MS = 500L
        private const val COUNTDOWN_FROM = 3
        private const val GO_DISPLAY_MS = 500L
        private const val FRAME_MS = 50L
        private const val NO_TAP = -1L
    }
}

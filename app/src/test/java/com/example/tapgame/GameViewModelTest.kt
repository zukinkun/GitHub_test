package com.example.tapgame

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GameViewModel の動作を検証する単体テスト（Unit Test）クラス。
 * アプリを画面上で実際に動かさなくても、ロジックが正しく動いているか瞬時に自動テストできます。
 *
 * 【★注意★】
 * GameViewModel や GameUiState の設定値（制限時間、称号のスコア基準など）を改造した場合は、
 * ここにあるテストの期待値（assertEquals など）も合わせて修正するとテストが成功するようになります。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    // テスト用のダミーのスコア保存クラス（本物の SharedPreferences を使わずにメモリ上で管理）
    private class FakeStore(var best: Int = 0) : ScoreStore {
        override fun highScore() = best
        override fun saveHighScore(score: Int) {
            best = score
        }
    }

    /** カウントダウン(3+2+1+GO 0.5秒)が終わってプレイ開始になるまでの時間。 */
    private val countdownMs = 3_500L

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.newViewModel(store: ScoreStore = FakeStore()): GameViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        return GameViewModel(store) { testScheduler.currentTime }
    }

    private fun TestScope.passTime(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun start_runsCountdownThenPlaying() = runTest {
        val vm = newViewModel()
        assertEquals(Phase.Idle, vm.state.value.phase)

        vm.start()
        passTime(0)
        assertEquals(Phase.Countdown, vm.state.value.phase)
        assertEquals(3, vm.state.value.countdown)

        passTime(1_000)
        assertEquals(2, vm.state.value.countdown)

        passTime(countdownMs - 1_000)
        assertEquals(Phase.Playing, vm.state.value.phase)
    }

    @Test
    fun tapsAreIgnoredOutsidePlaying() = runTest {
        val vm = newViewModel()
        vm.onTap()
        assertEquals(0, vm.state.value.score)

        vm.start()
        passTime(1_000) // カウントダウン中
        vm.onTap()
        assertEquals(0, vm.state.value.score)
    }

    @Test
    fun fullGame_finishesWithScoreAndSavesNewRecord() = runTest {
        val store = FakeStore(best = 3)
        val vm = newViewModel(store)
        vm.start()
        passTime(countdownMs)
        repeat(5) { vm.onTap() }

        passTime(GameViewModel.GAME_DURATION_MS + 100)
        val s = vm.state.value
        assertEquals(Phase.Finished, s.phase)
        assertEquals(5, s.score)
        assertTrue(s.isNewRecord)
        assertEquals(5, s.highScore)
        assertEquals(5, store.best)
        assertEquals(0.5f, s.tapsPerSec, 0.001f)
    }

    @Test
    fun lowerScore_isNotNewRecord() = runTest {
        val store = FakeStore(best = 10)
        val vm = newViewModel(store)
        vm.start()
        passTime(countdownMs)
        vm.onTap()
        passTime(GameViewModel.GAME_DURATION_MS + 100)

        assertFalse(vm.state.value.isNewRecord)
        assertEquals(10, vm.state.value.highScore)
        assertEquals(10, store.best)
    }

    @Test
    fun zeroScore_isNeverNewRecord() = runTest {
        val vm = newViewModel(FakeStore(best = 0))
        vm.start()
        passTime(countdownMs + GameViewModel.GAME_DURATION_MS + 100)
        assertFalse(vm.state.value.isNewRecord)
    }

    @Test
    fun combo_chainsWithinWindowAndResetsAfter() = runTest {
        val vm = newViewModel()
        vm.start()
        passTime(countdownMs)

        vm.onTap()
        passTime(GameViewModel.COMBO_WINDOW_MS)
        vm.onTap()
        passTime(100)
        vm.onTap()
        assertEquals(3, vm.state.value.combo)

        passTime(GameViewModel.COMBO_WINDOW_MS + 1)
        vm.onTap()
        assertEquals(1, vm.state.value.combo)
        assertEquals(4, vm.state.value.score)
    }

    @Test
    fun interrupt_discardsGameAndInvalidatesScore() = runTest {
        val store = FakeStore()
        val vm = newViewModel(store)
        vm.start()
        passTime(countdownMs)
        repeat(20) { vm.onTap() }

        vm.interrupt()
        passTime(GameViewModel.GAME_DURATION_MS + 100)

        val s = vm.state.value
        assertEquals(Phase.Idle, s.phase)
        assertEquals(0, s.score)
        assertTrue(s.interrupted)
        assertEquals(0, store.best)
    }

    @Test
    fun restart_afterFinish_resetsState() = runTest {
        val vm = newViewModel()
        vm.start()
        passTime(countdownMs)
        vm.onTap()
        passTime(GameViewModel.GAME_DURATION_MS + 100)
        assertEquals(Phase.Finished, vm.state.value.phase)

        vm.start()
        passTime(0)
        assertEquals(Phase.Countdown, vm.state.value.phase)
        assertEquals(0, vm.state.value.score)
        assertEquals(1, vm.state.value.highScore)
    }

    @Test
    fun rank_boundaries() {
        assertEquals(Rank.Beginner, Rank.of(0))
        assertEquals(Rank.Beginner, Rank.of(29))
        assertEquals(Rank.Apprentice, Rank.of(30))
        assertEquals(Rank.Apprentice, Rank.of(49))
        assertEquals(Rank.Expert, Rank.of(50))
        assertEquals(Rank.Expert, Rank.of(69))
        assertEquals(Rank.Godspeed, Rank.of(70))
        assertEquals(Rank.Godspeed, Rank.of(200))
    }
}

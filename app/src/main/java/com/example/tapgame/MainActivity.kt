package com.example.tapgame

import android.animation.ArgbEvaluator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tapgame.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val viewModel: GameViewModel by viewModels {
        viewModelFactory {
            initializer { GameViewModel(SharedPrefsScoreStore(applicationContext)) }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private var lastState: GameUiState? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.startButton.setOnClickListener { viewModel.start() }
        binding.playAgainButton.setOnClickListener { viewModel.start() }

        binding.tapButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start()
                    if (viewModel.state.value.phase == Phase.Playing) {
                        viewModel.onTap()
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        spawnPlusOne(event.rawX, event.rawY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 画面回転では中断しない（ViewModel が状態を保持する）
        if (!isChangingConfigurations) viewModel.interrupt()
    }

    private fun spawnPlusOne(rawX: Float, rawY: Float) {
        val combo = viewModel.state.value.combo
        FloatingText.show(
            binding.effectLayer,
            getString(R.string.plus_one),
            rawX + Random.nextInt(-40, 41),
            rawY - 40,
            comboColor(combo),
            (22 + minOf(combo, COMBO_MAX_STYLE) / 2).toFloat(),
        )
    }

    private fun comboColor(combo: Int): Int {
        val f = combo.coerceAtMost(COMBO_MAX_STYLE) / COMBO_MAX_STYLE.toFloat()
        return ArgbEvaluator().evaluate(
            f,
            ContextCompat.getColor(this, R.color.combo_start),
            ContextCompat.getColor(this, R.color.combo_end),
        ) as Int
    }

    private fun render(state: GameUiState) {
        val prev = lastState
        lastState = state
        val animate = prev != null

        val playing = state.phase == Phase.Playing
        val countdown = state.phase == Phase.Countdown
        val finished = state.phase == Phase.Finished

        // 背景のなめらかな切り替え
        val targetAlpha = if (playing) 1f else 0f
        if (animate) binding.bgPlaying.animate().alpha(targetAlpha).setDuration(400).start()
        else binding.bgPlaying.alpha = targetAlpha

        // 時間バーと残り時間（緑 → 黄 → 赤、残り3秒で点滅）
        val timeColor = ContextCompat.getColor(
            this,
            when {
                state.progress > 0.5f -> R.color.timer_green
                state.progress > 0.25f -> R.color.timer_amber
                else -> R.color.timer_red
            },
        )
        binding.timeBar.setProgressCompat((state.progress * 1000).toInt(), false)
        binding.timeBar.setIndicatorColor(timeColor)
        binding.timerText.setTextColor(timeColor)
        binding.timerText.text = getString(R.string.timer_format, state.remainingMs / 1000f)
        val blink = playing && state.remainingMs <= 3000 && (state.remainingMs / 250) % 2 == 0L
        binding.timerText.alpha = if (blink) 0.35f else 1f

        // スコア（加算のたびに跳ねる）
        binding.scoreText.text = getString(R.string.score_format, state.score)
        if (prev != null && state.score > prev.score) bounce(binding.scoreText)

        // コンボ
        val showCombo = playing && state.combo >= COMBO_MIN_SHOW
        binding.comboText.visibility = if (showCombo) View.VISIBLE else View.INVISIBLE
        if (showCombo) {
            binding.comboText.text = getString(R.string.combo_format, state.combo)
            binding.comboText.setTextColor(comboColor(state.combo))
            binding.comboText.textSize = (20 + minOf(state.combo, COMBO_MAX_STYLE)).toFloat()
        }

        // ボタン類
        binding.tapButton.alpha = if (playing) 1f else 0.5f
        binding.startButton.isEnabled = !playing && !countdown
        binding.hintText.visibility = if (state.phase == Phase.Idle) View.VISIBLE else View.INVISIBLE

        // カウントダウン
        binding.countdownText.visibility = if (countdown) View.VISIBLE else View.GONE
        if (countdown && (prev?.phase != Phase.Countdown || prev.countdown != state.countdown)) {
            binding.countdownText.text =
                if (state.countdown == 0) getString(R.string.countdown_go) else state.countdown.toString()
            binding.countdownText.scaleX = 2f
            binding.countdownText.scaleY = 2f
            binding.countdownText.alpha = 0f
            binding.countdownText.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()
        }

        // 結果カード
        binding.resultOverlay.visibility = if (finished) View.VISIBLE else View.GONE
        if (finished) {
            binding.resultScore.text = getString(R.string.result_score, state.score)
            binding.resultBest.text = getString(R.string.result_best, state.highScore)
            binding.resultTps.text = getString(R.string.result_tps, state.tapsPerSec)
            binding.resultRank.text = getString(R.string.result_rank, getString(state.rank.labelRes))
            binding.resultNewRecord.visibility = if (state.isNewRecord) View.VISIBLE else View.GONE
            if (animate && prev?.phase != Phase.Finished) {
                binding.resultCard.scaleX = 0.6f
                binding.resultCard.scaleY = 0.6f
                binding.resultCard.alpha = 0f
                binding.resultCard.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start()
            }
        }

        if (state.interrupted && prev?.interrupted != true) {
            Toast.makeText(this, R.string.interrupted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bounce(v: View) {
        v.animate().cancel()
        v.scaleX = 1.25f
        v.scaleY = 1.25f
        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
    }

    private companion object {
        const val COMBO_MIN_SHOW = 3
        const val COMBO_MAX_STYLE = 20
    }
}

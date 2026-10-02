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

/**
 * ゲームのメイン画面（UI）を担当する Activity。
 * ViewModel の状態（GameUiState）を受け取って画面に反映し、
 * ユーザーのボタン操作やタッチイベントを ViewModel に伝えます。
 */
class MainActivity : AppCompatActivity() {

    // ViewModel の初期化。スコア保存用の SharedPreferences を渡します。
    private val viewModel: GameViewModel by viewModels {
        viewModelFactory {
            initializer { GameViewModel(SharedPrefsScoreStore(applicationContext)) }
        }
    }

    // ViewBinding（レイアウト内の各部品に簡単にアクセスするための仕組み）
    private lateinit var binding: ActivityMainBinding
    // 前回描画時の状態（状態が変わったときにアニメーションを実行するために保持）
    private var lastState: GameUiState? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // レイアウトを読み込んで画面にセット
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ステータスバーやナビゲーションバーの領域を考慮してパディングを設定（Edge-to-Edge対応）
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // 「ゲームスタート」ボタンが押されたらゲーム開始
        binding.startButton.setOnClickListener { viewModel.start() }
        // 結果画面の「もう一度」ボタンが押されたら再スタート
        binding.playAgainButton.setOnClickListener { viewModel.start() }

        // 【★カスタマイズのヒント: タップボタンの演出★】
        // 押し込んだときに少し縮み、離したときに元に戻る気持ちいい操作感を作っています。
        binding.tapButton.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // 指が触れた瞬間: ボタンを 0.9倍 に縮小
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).start()
                    if (viewModel.state.value.phase == Phase.Playing) {
                        viewModel.onTap()
                        // 端末をブルッと震わせる（触覚フィードバック）
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        // 指のタップ位置に「+1」を浮かび上がらせる
                        spawnPlusOne(event.rawX, event.rawY)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 指を離した瞬間: 元の大きさに戻す
                    v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }

        // ViewModel の状態変化（StateFlow）を監視し、変化があるたびに画面を描画（render）
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { render(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // 画面回転時以外（ホームに戻った、別アプリを開いた等）は不正防止のため中断
        if (!isChangingConfigurations) viewModel.interrupt()
    }

    /**
     * 【★カスタマイズのヒント: タップ演出★】
     * 指の位置の近くに「+1」をふわっと浮かび上がらせる演出。
     * 表示テキスト（+1 や NICE! など）や散らばり具合（Random値）を変更できます。
     */
    private fun spawnPlusOne(rawX: Float, rawY: Float) {
        val combo = viewModel.state.value.combo
        FloatingText.show(
            binding.effectLayer,
            getString(R.string.plus_one),
            rawX + Random.nextInt(-40, 41), // 左右に少しランダムにずらす
            rawY - 40,                      // 指の少し上に表示
            comboColor(combo),              // コンボ数に応じて色を変化
            (22 + minOf(combo, COMBO_MAX_STYLE) / 2).toFloat(), // コンボで文字を少し大きく
        )
    }

    /**
     * コンボ数に応じた文字色（黄色〜赤・ピンク系のグラデーション）を計算する関数
     */
    private fun comboColor(combo: Int): Int {
        val f = combo.coerceAtMost(COMBO_MAX_STYLE) / COMBO_MAX_STYLE.toFloat()
        return ArgbEvaluator().evaluate(
            f,
            ContextCompat.getColor(this, R.color.combo_start),
            ContextCompat.getColor(this, R.color.combo_end),
        ) as Int
    }

    /**
     * ViewModel から渡された最新のゲーム状態（state）をもとに、画面上の各表示を更新する関数
     */
    private fun render(state: GameUiState) {
        val prev = lastState
        lastState = state
        val animate = prev != null

        val playing = state.phase == Phase.Playing
        val countdown = state.phase == Phase.Countdown
        val finished = state.phase == Phase.Finished

        // プレイ中の背景（オレンジ系）のフェードイン・フェードアウト
        val targetAlpha = if (playing) 1f else 0f
        if (animate) binding.bgPlaying.animate().alpha(targetAlpha).setDuration(400).start()
        else binding.bgPlaying.alpha = targetAlpha

        // 【★カスタマイズのヒント: タイマー演出★】
        // 残り時間に応じてゲージと文字の色を切り替え（50%超: 緑、25%〜50%: 黄、25%未満: 赤）
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

        // 残り3秒（3000ms）以下になったら文字を点滅させる
        val blink = playing && state.remainingMs <= 3000 && (state.remainingMs / 250) % 2 == 0L
        binding.timerText.alpha = if (blink) 0.35f else 1f

        // スコア表示（タップされるたびにピョンと跳ねる演出）
        binding.scoreText.text = getString(R.string.score_format, state.score)
        if (prev != null && state.score > prev.score) bounce(binding.scoreText)

        // コンボ表示（一定数以上のコンボで表示）
        val showCombo = playing && state.combo >= COMBO_MIN_SHOW
        binding.comboText.visibility = if (showCombo) View.VISIBLE else View.INVISIBLE
        if (showCombo) {
            binding.comboText.text = getString(R.string.combo_format, state.combo)
            binding.comboText.setTextColor(comboColor(state.combo))
            binding.comboText.textSize = (20 + minOf(state.combo, COMBO_MAX_STYLE)).toFloat()
        }

        // ボタンの有効／無効と見た目の制御
        binding.tapButton.alpha = if (playing) 1f else 0.5f
        binding.startButton.isEnabled = !playing && !countdown
        binding.hintText.visibility = if (state.phase == Phase.Idle) View.VISIBLE else View.INVISIBLE

        // カウントダウン表示（3, 2, 1, GO! を大きくズームイン）
        binding.countdownText.visibility = if (countdown) View.VISIBLE else View.GONE
        if (countdown && (prev?.phase != Phase.Countdown || prev.countdown != state.countdown)) {
            binding.countdownText.text =
                if (state.countdown == 0) getString(R.string.countdown_go) else state.countdown.toString()
            binding.countdownText.scaleX = 2f
            binding.countdownText.scaleY = 2f
            binding.countdownText.alpha = 0f
            binding.countdownText.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(350).start()
        }

        // 結果カードの表示（タイムアップ時）
        binding.resultOverlay.visibility = if (finished) View.VISIBLE else View.GONE
        if (finished) {
            binding.resultScore.text = getString(R.string.result_score, state.score)
            binding.resultBest.text = getString(R.string.result_best, state.highScore)
            binding.resultTps.text = getString(R.string.result_tps, state.tapsPerSec)
            binding.resultRank.text = getString(R.string.result_rank, getString(state.rank.labelRes))
            binding.resultNewRecord.visibility = if (state.isNewRecord) View.VISIBLE else View.GONE

            // 初回表示時にポップアップアニメーション
            if (animate && prev?.phase != Phase.Finished) {
                binding.resultCard.scaleX = 0.6f
                binding.resultCard.scaleY = 0.6f
                binding.resultCard.alpha = 0f
                binding.resultCard.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(300).start()
            }
        }

        // 中断された場合のトースト通知
        if (state.interrupted && prev?.interrupted != true) {
            Toast.makeText(this, R.string.interrupted, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 【★カスタマイズのヒント: バウンス演出★】
     * スコアが増えたときに文字をポンッと弾ませるアニメーション。
     * scaleX / scaleY の倍率（現在1.25倍）を大きくすると、より派手に跳ねます。
     */
    private fun bounce(v: View) {
        v.animate().cancel()
        v.scaleX = 1.25f
        v.scaleY = 1.25f
        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
    }

    /**
     * 【★生徒向けカスタマイズポイント（UI表示の設定）★】
     */
    private companion object {
        /** 何コンボ以上で画面に「×○ COMBO!」を表示するか（3コンボから表示） */
        const val COMBO_MIN_SHOW = 3

        /** コンボによる文字拡大や色変化の上限値（20コンボで最大スタイル） */
        const val COMBO_MAX_STYLE = 20
    }
}

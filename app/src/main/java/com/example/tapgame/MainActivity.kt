package com.example.tapgame

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var score = 0
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val timerText: TextView = findViewById(R.id.timerText)
        val scoreText: TextView = findViewById(R.id.scoreText)
        val tapButton: Button = findViewById(R.id.tapButton)
        val startButton: Button = findViewById(R.id.startButton)

        // タイマーの設定（10秒間、1秒ごとに更新）
        val timer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // 文字列の連結ではなく、getString を使用
                timerText.text = getString(R.string.timer_format, millisUntilFinished / 1000)
            }

            override fun onFinish() {
                timerText.text = "終了！"
                isPlaying = false
                startButton.isEnabled = true
                Toast.makeText(this@MainActivity, "スコア: $score", Toast.LENGTH_LONG).show()
            }
        }

        // スタートボタン
        startButton.setOnClickListener {
            score = 0
            // 初期表示も getString を使用
            scoreText.text = getString(R.string.score_format, score)
            isPlaying = true
            startButton.isEnabled = false
            timer.start()
        }

        // タップボタン
        tapButton.setOnClickListener {
            if (isPlaying) {
                score++
                // 更新時も getString を使用
                scoreText.text = getString(R.string.score_format, score)
            } else {
                Toast.makeText(this, "スタートを押してね！", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
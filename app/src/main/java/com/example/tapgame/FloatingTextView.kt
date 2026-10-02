package com.example.tapgame

import android.graphics.Color
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.TextView

/**
 * タップした画面上の位置に「+1」などの文字を生成し、
 * ふわっと上へ浮かび上がらせながらフェードアウトして消す演出クラス。
 */
object FloatingText {
    /**
     * 指定した座標に一時的な TextView を生成してアニメーションを実行する
     *
     * @param layer エフェクトを描画する親レイヤー（FrameLayout）
     * @param text 表示する文字（例: "+1"）
     * @param rawX タップされた画面全体のX座標
     * @param rawY タップされた画面全体のY座標
     * @param color 文字色
     * @param sizeSp 文字サイズ（sp単位）
     */
    fun show(layer: FrameLayout, text: String, rawX: Float, rawY: Float, color: Int, sizeSp: Float) {
        // 動的に TextView を生成してスタイルを設定
        val tv = TextView(layer.context).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD
            // 文字にうっすら影をつけて見やすくする
            setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0))
            measure(0, 0)
        }
        // レイヤーに追加
        layer.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        // 親レイヤーの画面上の位置を取得し、タップ位置が TextView の中央上になるよう計算
        val origin = IntArray(2).also { layer.getLocationOnScreen(it) }
        tv.x = rawX - origin[0] - tv.measuredWidth / 2f
        tv.y = rawY - origin[1] - tv.measuredHeight

        // 【★カスタマイズのヒント★】
        // rise: 浮かび上がる高さ（120dp分）。数字を大きくすると高く飛びます。
        val rise = 120f * layer.resources.displayMetrics.density

        // アニメーション: 上に移動しながら透明度を 0 にしてフェードアウト
        tv.animate()
            .translationYBy(-rise)
            .alpha(0f)
            .setDuration(600) // アニメーション時間（ミリ秒。600ms = 0.6秒）
            .withEndAction { layer.removeView(tv) } // 演出が終わったらメモリ解放のため削除
            .start()
    }
}

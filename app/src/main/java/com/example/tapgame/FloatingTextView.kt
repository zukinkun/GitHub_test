package com.example.tapgame

import android.graphics.Color
import android.graphics.Typeface
import android.widget.FrameLayout
import android.widget.TextView

/** タップ位置に「+1」などを浮かび上がらせて消す演出。 */
object FloatingText {
    fun show(layer: FrameLayout, text: String, rawX: Float, rawY: Float, color: Int, sizeSp: Float) {
        val tv = TextView(layer.context).apply {
            this.text = text
            setTextColor(color)
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 0f, 2f, Color.argb(120, 0, 0, 0))
            measure(0, 0)
        }
        layer.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        val origin = IntArray(2).also { layer.getLocationOnScreen(it) }
        tv.x = rawX - origin[0] - tv.measuredWidth / 2f
        tv.y = rawY - origin[1] - tv.measuredHeight

        val rise = 120f * layer.resources.displayMetrics.density
        tv.animate()
            .translationYBy(-rise)
            .alpha(0f)
            .setDuration(600)
            .withEndAction { layer.removeView(tv) }
            .start()
    }
}

package com.lumavun.lumation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.util.Locale
import kotlin.math.roundToLong

/** A working single-clip video timeline and seek surface for the first APK. */
class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val d = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var durationMs: Long = 0L
        set(value) { field = value; invalidate() }
    var cursorMs: Long = 0L
        set(value) { field = value; invalidate() }
    var trimStartMs: Long = 0L
        set(value) { field = value; invalidate() }
    var trimEndMs: Long = 0L
        set(value) { field = value; invalidate() }
    var clipName: String = "VIDEO"
        set(value) { field = value; invalidate() }
    var onSeek: ((Long) -> Unit)? = null

    private fun px(v: Float) = v * d
    private fun drawText(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.textSize = px(size)
        paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText(value, x, y, paint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = px(14f)
        val right = width.toFloat() - left
        val trackTop = px(49f)
        val trackBottom = px(98f)
        val fullWidth = right - left
        val white = Color.parseColor("#F1ECFF")
        val muted = Color.parseColor("#A0A1B7")
        val accent = Color.parseColor("#9E86FF")
        val track = Color.parseColor("#483A81")

        for (i in 0..4) {
            val x = left + fullWidth * i / 4f
            paint.color = Color.parseColor("#414052")
            paint.strokeWidth = px(1f)
            canvas.drawLine(x, px(30f), x, px(106f), paint)
            val seconds = durationMs * i / 4000.0
            drawText(canvas, String.format(Locale.US, "%.1fs", seconds), x.coerceAtMost(right - px(28f)), px(23f), 10f, muted)
        }

        if (durationMs <= 0L) {
            drawText(canvas, "Belum ada klip di timeline", left + px(8f), px(76f), 13f, muted)
            return
        }

        paint.style = Paint.Style.FILL
        paint.color = track
        canvas.drawRoundRect(RectF(left, trackTop, right, trackBottom), px(8f), px(8f), paint)

        val startX = left + fullWidth * (trimStartMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val endX = left + fullWidth * (trimEndMs.toFloat() / durationMs).coerceIn(0f, 1f)
        paint.color = Color.parseColor("#876BFB")
        canvas.drawRoundRect(RectF(startX, trackTop, endX, trackBottom), px(6f), px(6f), paint)
        drawText(canvas, "▶  VIDEO 01", startX + px(8f), trackTop + px(27f), 12f, white)

        val cursorX = left + fullWidth * (cursorMs.toFloat() / durationMs).coerceIn(0f, 1f)
        paint.color = accent
        paint.strokeWidth = px(2f)
        canvas.drawLine(cursorX, px(31f), cursorX, px(116f), paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cursorX, px(31f), px(5f), paint)
        drawText(canvas, "Geser penanda untuk mengatur posisi", left, px(125f), 9f, muted)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            val left = px(14f)
            val fraction = ((event.x - left) / (width - 2f * left)).coerceIn(0f, 1f)
            val target = (durationMs * fraction).roundToLong()
            cursorMs = target
            onSeek?.invoke(target)
            return true
        }
        return event.action == MotionEvent.ACTION_UP
    }
}

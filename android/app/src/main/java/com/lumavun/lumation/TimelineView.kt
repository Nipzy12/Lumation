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

/** Lightweight two-track timeline: trimmed video and a looping soundtrack. */
class TimelineView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
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
    var musicTrackName: String? = null
        set(value) { field = value; invalidate() }
    var onSeek: ((Long) -> Unit)? = null

    private fun px(value: Float) = value * density
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, color: Int) {
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
        val span = right - left
        val light = Color.parseColor("#F1ECFF")
        val muted = Color.parseColor("#A0A1B7")
        val accent = Color.parseColor("#9E86FF")

        for (tick in 0..4) {
            val x = left + span * tick / 4f
            paint.color = Color.parseColor("#343446")
            paint.strokeWidth = px(1f)
            canvas.drawLine(x, px(30f), x, px(166f), paint)
            text(
                canvas,
                String.format(Locale.US, "%.1fs", durationMs * tick / 4000.0),
                x.coerceAtMost(right - px(32f)), px(22f), 10f, muted
            )
        }

        if (durationMs <= 0L) {
            text(canvas, "Impor video untuk membuat timeline", left, px(72f), 13f, muted)
            text(canvas, "Track audio opsional dapat ditambahkan", left, px(125f), 11f, muted)
            return
        }

        val videoTop = px(43f)
        val videoBottom = px(96f)
        val audioTop = px(105f)
        val audioBottom = px(150f)
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#3C335F")
        canvas.drawRoundRect(RectF(left, videoTop, right, videoBottom), px(7f), px(7f), paint)

        val selectedLeft = left + span * (trimStartMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val selectedRight = left + span * (trimEndMs.toFloat() / durationMs).coerceIn(0f, 1f)
        paint.color = Color.parseColor("#8267EC")
        canvas.drawRoundRect(
            RectF(selectedLeft, videoTop, selectedRight.coerceAtLeast(selectedLeft), videoBottom),
            px(6f), px(6f), paint
        )
        text(canvas, "▶  VIDEO 01", selectedLeft + px(7f), videoTop + px(30f), 12f, light)

        val music = musicTrackName
        paint.color = if (music != null) Color.parseColor("#1C7F87") else Color.parseColor("#292937")
        canvas.drawRoundRect(RectF(left, audioTop, right, audioBottom), px(7f), px(7f), paint)
        text(
            canvas,
            if (music == null) "＋ AUDIO 02  •  empty" else "♫  AUDIO 02  •  ${music.take(19)}",
            left + px(9f), audioTop + px(26f), 11f,
            if (music != null) light else muted
        )

        val cursorX = left + span * (cursorMs.toFloat() / durationMs).coerceIn(0f, 1f)
        paint.color = accent
        paint.strokeWidth = px(2f)
        canvas.drawLine(cursorX, px(28f), cursorX, px(162f), paint)
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cursorX, px(29f), px(5f), paint)
        text(canvas, "Geser untuk seek • Trim ada di bawah", left, px(171f), 9f, muted)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val left = px(14f)
                val fraction = ((event.x - left) / (width - 2f * left)).coerceIn(0f, 1f)
                val target = (durationMs * fraction).roundToLong()
                cursorMs = target
                onSeek?.invoke(target)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

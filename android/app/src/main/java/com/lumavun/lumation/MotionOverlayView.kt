package com.lumavun.lumation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.lumavun.lumation.ffmpeg.LayerMotion
import kotlin.math.min
import kotlin.math.max

data class PreviewRasterLayer(val bitmap: Bitmap, val motion: LayerMotion)

/** Shared normalized-space renderer for Android preview of FFmpeg raster layers. */
class MotionOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    var renderWidth: Int = 720
        set(v) { field = v; invalidate() }
    var renderHeight: Int = 1280
        set(v) { field = v; invalidate() }
    var layers: List<PreviewRasterLayer> = emptyList()
        set(v) { field = v; invalidate() }
    var playheadMs: Long = 0L
        set(v) { field = v; invalidate() }
    var trimStartMs: Long = 0L
        set(v) { field = v; invalidate() }
    var trimEndMs: Long = 1L
        set(v) { field = v; invalidate() }

    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderWidth <= 0 || renderHeight <= 0) return
        val factor = min(width.toFloat() / renderWidth, height.toFloat() / renderHeight)
        if (!factor.isFinite() || factor <= 0f) return
        val canvasW = factor * renderWidth
        val canvasH = factor * renderHeight
        val left = (width - canvasW) / 2f
        val top = (height - canvasH) / 2f

        for (layer in layers) {
            if (layer.bitmap.isRecycled) continue
            val drawW = layer.bitmap.width * factor
            val drawH = layer.bitmap.height * factor
            val (x, y) = layer.motion.positionAt(playheadMs, trimStartMs, trimEndMs)
            val drawX = left + (canvasW - drawW).coerceAtLeast(0f) * x
            val drawY = top + (canvasH - drawH).coerceAtLeast(0f) * y
            canvas.drawBitmap(layer.bitmap, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), drawPaint)
        }
    }
}

/** Generates small transparent PNG-compatible bitmaps, avoiding FFmpeg drawtext/font requirements. */
object OverlayAssets {
    fun outputDimensions(sourceWidth: Int, sourceHeight: Int): Pair<Int, Int> {
        val w = sourceWidth.coerceAtLeast(2)
        val h = sourceHeight.coerceAtLeast(2)
        val ratio = min(1f, min(720f / w, 1280f / h))
        fun even(value: Float) = max(2, ((value / 2f).toInt()) * 2)
        return even(w * ratio) to even(h * ratio)
    }

    fun textBitmap(content: String, frameWidth: Int, scalePercent: Int): Bitmap? {
        val message = content.trim().take(64)
        if (message.isEmpty()) return null
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textSize = frameWidth * 0.066f * (scalePercent.coerceIn(40, 180) / 100f)
        }
        val maximum = frameWidth * 0.88f
        val measured = text.measureText(message)
        if (measured > maximum && measured > 0f) {
            text.textSize *= maximum / measured
        }
        val padding = max(8f, frameWidth * 0.016f)
        val metrics = text.fontMetrics
        val glyphHeight = metrics.descent - metrics.ascent
        val bitmapWidth = min(frameWidth - 2, (text.measureText(message) + padding * 2).toInt().coerceAtLeast(4))
        val bitmapHeight = (glyphHeight + padding * 2).toInt().coerceAtLeast(4)
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val backdrop = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(135, 9, 8, 23) }
        canvas.drawRoundRect(
            RectF(0f, 0f, bitmapWidth.toFloat(), bitmapHeight.toFloat()),
            padding, padding, backdrop
        )
        text.setShadowLayer(2f, 0f, 2f, Color.BLACK)
        canvas.drawText(message, padding, padding - metrics.ascent, text)
        return bitmap
    }

    fun imageBitmap(path: String, frameWidth: Int, frameHeight: Int, scalePercent: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Gambar tidak dikenali" }
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val original = BitmapFactory.decodeFile(path, options)
            ?: throw IllegalArgumentException("Gambar tidak dapat dibaca")
        try {
            val baseScale = scalePercent.coerceIn(40, 180) / 100f
            val maxW = frameWidth * 0.5f * baseScale
            val maxH = frameHeight * 0.5f * baseScale
            val ratio = min(maxW / original.width, maxH / original.height)
                .coerceAtMost(1f).coerceAtLeast(0.01f)
            val targetW = (original.width * ratio).toInt().coerceIn(2, frameWidth - 2)
            val targetH = (original.height * ratio).toInt().coerceIn(2, frameHeight - 2)
            return Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } finally {
            original.recycle()
        }
    }
}

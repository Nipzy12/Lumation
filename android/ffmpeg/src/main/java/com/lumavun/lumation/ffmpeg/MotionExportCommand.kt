package com.lumavun.lumation.ffmpeg

import java.io.File

/** Two editable keyframes over the *trimmed* clip's duration. */
data class LayerMotion(
    val startX: Float = 0.5f,
    val startY: Float = 0.5f,
    val endX: Float = 0.5f,
    val endY: Float = 0.5f
) {
    fun positionAt(timeMs: Long, trimStartMs: Long, trimEndMs: Long): Pair<Float, Float> {
        val duration = (trimEndMs - trimStartMs).coerceAtLeast(1L)
        val p = ((timeMs - trimStartMs).toFloat() / duration).coerceIn(0f, 1f)
        return (startX + (endX - startX) * p) to (startY + (endY - startY) * p)
    }
}

/** An already-rasterized, transparent PNG with the editable layer's motion. */
data class RasterLayer(val image: File, val motion: LayerMotion)

/** Pure Android-independent builder for the FFmpeg timeline graph. */
internal object MotionExportCommand {
    fun build(
        video: File,
        music: File?,
        overlays: List<RasterLayer>,
        output: File,
        trimStartMs: Long,
        trimEndMs: Long,
        frameWidth: Int,
        frameHeight: Int,
        sourceHasAudio: Boolean,
        musicVolume: Float,
        encoder: String
    ): Array<String> = emptyArray()
}

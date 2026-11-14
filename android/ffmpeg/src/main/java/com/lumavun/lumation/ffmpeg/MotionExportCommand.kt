package com.lumavun.lumation.ffmpeg

import java.io.File
import java.util.Locale

/** Two editable position keyframes interpolated over the trimmed clip duration. */
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

/** A pre-rasterized transparent PNG with a start and end position. */
data class RasterLayer(val image: File, val motion: LayerMotion)

/**
 * Builds a single FFmpeg filter graph for video, animated layers, and optional soundtrack.
 * All data is passed as individual arguments. No raw user text is put in FFmpeg expressions.
 */
internal object MotionExportCommand {
    private fun number(v: Number) = String.format(Locale.US, "%.3f", v.toDouble())

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
    ): Array<String> {
        require(trimStartMs >= 0 && trimEndMs > trimStartMs) { "Invalid clip bounds" }
        require(frameWidth in 2..2160 && frameHeight in 2..3840 &&
            frameWidth % 2 == 0 && frameHeight % 2 == 0) { "Invalid output dimensions" }
        require(overlays.isNotEmpty() && overlays.size <= 8) { "Expected 1 to 8 overlays" }
        require(overlays.all { layer ->
            listOf(layer.motion.startX, layer.motion.startY, layer.motion.endX, layer.motion.endY)
                .all { it.isFinite() && it in 0f..1f }
        }) { "Overlay keyframe is outside 0..1" }
        require(musicVolume.isFinite() && musicVolume in 0f..1f) { "Invalid music volume" }
        require(encoder == "h264_mediacodec" || encoder == "mpeg4") { "Unsupported encoder" }

        val duration = number((trimEndMs - trimStartMs) / 1000.0)
        val args = mutableListOf(
            "-hide_banner", "-y", "-ss", number(trimStartMs / 1000.0),
            "-i", video.absolutePath
        )

        if (music != null) args.addAll(listOf("-stream_loop", "-1", "-i", music.absolutePath))
        overlays.forEach { layer ->
            args.addAll(listOf("-loop", "1", "-framerate", "30", "-i", layer.image.absolutePath))
        }

        val filters = mutableListOf(
            "[0:v:0]scale=$frameWidth:$frameHeight:flags=bicubic,setsar=1,setpts=PTS-STARTPTS[base]"
        )

        val imageOffset = if (music == null) 1 else 2
        var previous = "base"
        overlays.forEachIndexed { index, layer ->
            val motion = layer.motion
            fun movingAxis(start: Float, end: Float): String =
                "(W-w)*(${number(start)}+(${number(end)}-${number(start)})*min(max(t/$duration,0),1))"
            val x = movingAxis(motion.startX, motion.endX)
            // Overlay's vertical coordinate variables are H and h, unlike W/w.
            val y = movingAxis(motion.startY, motion.endY).replace("W-w", "H-h")
            val next = if (index == overlays.lastIndex) "vout" else "overlay$index"
            filters.add(
                "[$previous][${imageOffset + index}:v:0]overlay=" +
                    "x='$x':y='$y':format=auto:shortest=1:eval=frame[$next]"
            )
            previous = next
        }

        if (music != null) {
            val vol = number(musicVolume.toDouble())
            if (sourceHasAudio) {
                filters.add("[0:a:0]volume=0.85[original]")
                filters.add("[1:a:0]volume=$vol[music]")
                filters.add(
                    "[original][music]amix=inputs=2:duration=longest:" +
                        "dropout_transition=0:normalize=1[aout]"
                )
            } else {
                filters.add("[1:a:0]volume=$vol[aout]")
            }
        }

        args.addAll(listOf("-filter_complex", filters.joinToString(";")))
        args.addAll(listOf("-map", "[vout]"))
        if (music != null) args.addAll(listOf("-map", "[aout]"))
        else args.addAll(listOf("-map", "0:a:0?"))
        args.addAll(
            listOf(
                "-t", duration,
                "-c:v", encoder, "-b:v", "4M", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart", output.absolutePath
            )
        )
        return args.toTypedArray()
    }
}

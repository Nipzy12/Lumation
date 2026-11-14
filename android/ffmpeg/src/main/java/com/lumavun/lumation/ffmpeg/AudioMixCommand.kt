package com.lumavun.lumation.ffmpeg

import java.io.File
import java.util.Locale

/**
 * Pure FFmpeg argument construction, isolated from Android and FFmpegKit for unit tests.
 * All paths are passed as individual arguments, never shell-escaped command strings.
 */
internal object AudioMixCommand {
    fun build(
        video: File,
        soundtrack: File,
        output: File,
        startMs: Long,
        endMs: Long,
        sourceHasAudio: Boolean,
        musicVolume: Float,
        videoCodec: String
    ): Array<String> {
        require(startMs >= 0L && endMs > startMs) { "Invalid trim bounds" }
        require(musicVolume in 0f..1f) { "Music volume must be between 0 and 1" }
        require(videoCodec == "h264_mediacodec" || videoCodec == "mpeg4") {
            "Unsupported video encoder"
        }
        val start = String.format(Locale.US, "%.3f", startMs / 1000.0)
        val duration = String.format(Locale.US, "%.3f", (endMs - startMs) / 1000.0)
        val gain = String.format(Locale.US, "%.3f", musicVolume)

        val filter = if (sourceHasAudio) {
            "[0:a:0]volume=0.85[original];" +
                "[1:a:0]volume=$gain[music];" +
                "[original][music]amix=inputs=2:duration=longest:dropout_transition=0:normalize=1[aout]"
        } else {
            "[1:a:0]volume=$gain[aout]"
        }

        return arrayOf(
            "-hide_banner", "-y",
            "-ss", start, "-i", video.absolutePath,
            "-stream_loop", "-1", "-i", soundtrack.absolutePath,
            "-filter_complex", filter,
            "-map", "0:v:0", "-map", "[aout]",
            "-t", duration,
            "-c:v", videoCodec, "-b:v", "4M", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            output.absolutePath
        )
    }
}

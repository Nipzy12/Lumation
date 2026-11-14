package com.lumavun.lumation.ffmpeg

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Initial offline media-processing bridge for Lumation.
 *
 * Input and output must be paths inside the app's accessible file storage.
 * Use ContentResolver / Android's document picker to import and export SAF URIs.
 * Completion is invoked from an FFmpeg worker thread, not the UI thread.
 */
object LumationFfmpeg {
    fun exportH264Mp4(
        input: File,
        output: File,
        onComplete: (Result<File>) -> Unit
    ) {
        if (!input.isFile) {
            onComplete(Result.failure(IllegalArgumentException("Input file not found")))
            return
        }
        if (input.canonicalPath == output.canonicalPath) {
            onComplete(Result.failure(IllegalArgumentException("Input and output must differ")))
            return
        }

        output.parentFile?.mkdirs()

        // Pass separate arguments, not a shell string, so spaces in file paths work.
        // The h264_mediacodec encoder requires working hardware codec support.
        val args = arrayOf(
            "-hide_banner", "-y",
            "-i", input.absolutePath,
            "-map", "0:v:0", "-map", "0:a?",
            "-c:v", "h264_mediacodec",
            "-b:v", "4M", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "128k",
            "-movflags", "+faststart",
            output.absolutePath
        )

        FFmpegKit.executeWithArgumentsAsync(args) { session ->
            if (ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0) {
                onComplete(Result.success(output))
            } else {
                onComplete(
                    Result.failure(
                        IllegalStateException(
                            "FFmpeg export failed (code: ${session.returnCode}); " +
                                (session.failStackTrace ?: "check FFmpeg log")
                        )
                    )
                )
            }
        }
    }
    /**
     * Trim a local video and encode an MP4 without network access.
     * Hardware H.264 is attempted first; MPEG-4 Part 2 is the compatibility fallback.
     * The latter is NOT H.264, even though it is packaged in an MP4 container.
     */
    fun exportTrimmedMp4(
        input: File,
        output: File,
        startMs: Long,
        endMs: Long,
        onComplete: (Result<File>) -> Unit
    ) {
        if (!input.isFile || startMs < 0L || endMs <= startMs) {
            onComplete(Result.failure(IllegalArgumentException("Invalid input file or trim interval")))
            return
        }
        if (input.canonicalPath == output.canonicalPath) {
            onComplete(Result.failure(IllegalArgumentException("Input and output must differ")))
            return
        }
        output.parentFile?.mkdirs()
        val offset = String.format(java.util.Locale.US, "%.3f", startMs / 1000.0)
        val seconds = String.format(java.util.Locale.US, "%.3f", (endMs - startMs) / 1000.0)

        fun runEncoder(encoder: String) {
            val args = arrayOf(
                "-hide_banner", "-y", "-ss", offset,
                "-i", input.absolutePath, "-t", seconds,
                "-map", "0:v:0", "-map", "0:a:0?",
                "-c:v", encoder, "-b:v", "4M", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "128k",
                "-movflags", "+faststart", output.absolutePath
            )
            FFmpegKit.executeWithArgumentsAsync(args) { session ->
                if (ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L) {
                    onComplete(Result.success(output))
                } else if (encoder == "h264_mediacodec") {
                    output.delete()
                    runEncoder("mpeg4")
                } else {
                    onComplete(Result.failure(IllegalStateException(
                        "FFmpeg failed after hardware and software attempts (code: ${session.returnCode}). " +
                            (session.failStackTrace ?: "Inspect FFmpeg logs.")
                    )))
                }
            }
        }
        runEncoder("h264_mediacodec")
    }

    /**
     * Merges one separate audio track into the trimmed video.
     *
     * When the source video includes sound, the soundtrack is mixed with it.
     * For muted source videos, the soundtrack becomes the sole audio stream.
     * The external track loops to fill the selected trim interval.
     * Real-device encoder and playback verification is still required.
     */
    fun exportWithSoundtrack(
        video: File,
        soundtrack: File,
        output: File,
        startMs: Long,
        endMs: Long,
        sourceHasAudio: Boolean,
        musicVolume: Float,
        onComplete: (Result<File>) -> Unit
    ) {
        if (!video.isFile || !soundtrack.isFile || video.length() == 0L || soundtrack.length() == 0L) {
            onComplete(Result.failure(IllegalArgumentException("Missing video or audio track")))
            return
        }
        if (video.canonicalPath == output.canonicalPath || soundtrack.canonicalPath == output.canonicalPath) {
            onComplete(Result.failure(IllegalArgumentException("Input and output must differ")))
            return
        }
        if (startMs < 0L || endMs <= startMs || musicVolume !in 0f..1f) {
            onComplete(Result.failure(IllegalArgumentException("Invalid trim interval or music volume")))
            return
        }
        output.parentFile?.mkdirs()

        fun encode(codec: String) {
            val args = AudioMixCommand.build(
                video, soundtrack, output, startMs, endMs, sourceHasAudio, musicVolume, codec
            )
            FFmpegKit.executeWithArgumentsAsync(args) { session ->
                if (ReturnCode.isSuccess(session.returnCode) && output.isFile && output.length() > 0L) {
                    onComplete(Result.success(output))
                } else if (codec == "h264_mediacodec") {
                    output.delete()
                    encode("mpeg4")
                } else {
                    output.delete()
                    onComplete(Result.failure(IllegalStateException(
                        "Audio mix export failed, code: ${session.returnCode}. " +
                            (session.failStackTrace ?: "Check FFmpeg logs.")
                    )))
                }
            }
        }
        encode("h264_mediacodec")
    }

}

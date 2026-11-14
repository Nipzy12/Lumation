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
}

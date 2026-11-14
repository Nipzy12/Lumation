package com.lumavun.lumation.ffmpeg

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class MotionExportCommandTest {
    private val video = File("/test/video shot.mp4")
    private val overlay = File("/test/overlay title.png")
    private val out = File("/test/final video.mp4")

    @Test fun rendersOneAnimatedOverlayWithoutSoundtrack() {
        val args = MotionExportCommand.build(
            video, null,
            listOf(RasterLayer(overlay, LayerMotion(0.1f, 0.8f, 0.8f, 0.2f))),
            out, 1000L, 5000L, 720, 1280, false, 0.5f, "mpeg4"
        )
        assertTrue(args.contains("-filter_complex"))
        val filters = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filters.contains("scale=720:1280"))
        assertTrue(filters.contains("[0:v:0]"))
        assertTrue(filters.contains("[1:v:0]overlay="))
        assertTrue(filters.contains("t/4.000"))
        assertTrue(filters.contains("[vout]"))
        assertTrue(args.contains(overlay.absolutePath))
        assertEquals("[vout]", args[args.indexOf("-map") + 1])
        assertEquals("4.000", args[args.indexOf("-t") + 1])
        assertEquals("mpeg4", args[args.indexOf("-c:v") + 1])
    }

    @Test fun handlesMusicAndTwoOverlayLayers() {
        val args = MotionExportCommand.build(
            video, File("/test/song.mp3"),
            listOf(RasterLayer(overlay, LayerMotion()),
                RasterLayer(File("/test/sticker.png"), LayerMotion())),
            out, 0L, 2200L, 640, 360, true, 0.45f, "h264_mediacodec"
        )
        val filters = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filters.contains("[2:v:0]overlay="))
        assertTrue(filters.contains("[3:v:0]overlay="))
        assertTrue(filters.contains("amix=inputs=2"))
        assertTrue(filters.contains("volume=0.450"))
        assertTrue(args.contains("-stream_loop"))
        assertEquals("h264_mediacodec", args[args.indexOf("-c:v") + 1])
    }

    @Test fun linearlyInterpolatesKeyframesWithClamping() {
        val motion = LayerMotion(0f, 1f, 1f, 0f)
        assertEquals(0f, motion.positionAt(100L, 100L, 1100L).first, 0.001f)
        assertEquals(0.5f, motion.positionAt(600L, 100L, 1100L).first, 0.001f)
        assertEquals(1f, motion.positionAt(9999L, 100L, 1100L).first, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsafeFrameDimensions() {
        MotionExportCommand.build(
            video, null, listOf(RasterLayer(overlay, LayerMotion())),
            out, 0L, 1000L, 0, 720, false, 0f, "mpeg4"
        )
    }
}

package com.lumavun.lumation.ffmpeg

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AudioMixCommandTest {
    @Test fun videoWithOriginalAudioCreatesTwoInputMix() {
        val args = AudioMixCommand.build(
            File("/tmp/video input.mp4"), File("/tmp/song.wav"), File("/tmp/output.mp4"),
            1250L, 4250L, sourceHasAudio = true, musicVolume = 0.55f,
            videoCodec = "h264_mediacodec"
        )
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertTrue(filter.contains("[0:a:0]"))
        assertTrue(filter.contains("[1:a:0]"))
        assertTrue(filter.contains("amix=inputs=2"))
        assertTrue(filter.contains("volume=0.550"))
        assertEquals("1.250", args[args.indexOf("-ss") + 1])
        assertEquals("3.000", args[args.indexOf("-t") + 1])
        assertTrue(args.contains("/tmp/video input.mp4"))
        assertEquals("h264_mediacodec", args[args.indexOf("-c:v") + 1])
    }

    @Test fun silentVideoMapsOnlyExternalSoundtrack() {
        val args = AudioMixCommand.build(
            File("video.mp4"), File("music.mp3"), File("output.mp4"),
            0L, 1800L, sourceHasAudio = false, musicVolume = 1f,
            videoCodec = "mpeg4"
        )
        val filter = args[args.indexOf("-filter_complex") + 1]
        assertFalse(filter.contains("[0:a:0]"))
        assertTrue(filter.contains("[1:a:0]volume=1.000[aout]"))
        assertEquals("mpeg4", args[args.indexOf("-c:v") + 1])
        assertEquals("-1", args[args.indexOf("-stream_loop") + 1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveDuration() {
        AudioMixCommand.build(
            File("video"), File("music"), File("output"),
            100L, 100L, false, 0.5f, "mpeg4"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedEncoder() {
        AudioMixCommand.build(
            File("video"), File("music"), File("output"),
            0L, 5000L, false, 0.5f, "unknown"
        )
    }
}

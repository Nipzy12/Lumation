package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private fun media(name: String, data: String): File {
        val f = temp.newFile(name)
        f.writeText(data)
        return f
    }

    @Test fun savesAndLoadsMediaAndKeyframeState() {
        val video = media("video.mp4", "VIDEO-BYTES")
        val audio = media("music.mp3", "AUDIO-BYTES")
        val image = media("logo.png", "IMAGE-BYTES")
        val store = ProjectStore(File(temp.root, "projects"))
        val expected = ProjectDocument(
            video = video, videoName = "My holiday.mp4",
            audio = audio, audioName = "theme.mp3", volume = 42,
            durationMs = 10_000L, trimStartMs = 1500L, trimEndMs = 8300L,
            sourceHasAudio = true,
            layers = MotionProjectState(
                text = "LUMATION", imageFile = image, imageName = "cover.png",
                textMotion = LayerMotion(0f, 0.2f, 1f, 0.8f),
                imageMotion = LayerMotion(1f, 0.5f, 0f, 0.5f),
                textScale = 115, imageScale = 85, selected = "IMAGE",
                frameWidth = 720, frameHeight = 1280
            )
        )
        store.save(expected)
        video.writeText("CHANGED SOURCE")
        image.delete()
        val restored = requireNotNull(store.load())
        assertEquals("VIDEO-BYTES", restored.video.readText())
        assertEquals("AUDIO-BYTES", restored.audio?.readText())
        assertEquals("IMAGE-BYTES", restored.layers.imageFile?.readText())
        assertEquals(expected.videoName, restored.videoName)
        assertEquals(expected.trimStartMs, restored.trimStartMs)
        assertEquals(expected.trimEndMs, restored.trimEndMs)
        assertEquals(42, restored.volume)
        assertEquals("LUMATION", restored.layers.text)
        assertEquals(expected.layers.textMotion, restored.layers.textMotion)
        assertEquals(expected.layers.imageMotion, restored.layers.imageMotion)
        assertEquals("IMAGE", restored.layers.selected)
        assertEquals(115, restored.layers.textScale)
        assertEquals(720, restored.layers.frameWidth)
    }

    @Test fun saveReplacesPreviousProjectAndRemovesUnusedMedia() {
        val store = ProjectStore(File(temp.root, "projects"))
        val first = ProjectDocument(
            video = media("a.mp4", "a"), audio = media("audio.mp3", "audio"),
            videoName = "a.mp4", durationMs = 1000, trimStartMs = 0, trimEndMs = 1000,
            sourceHasAudio = false
        )
        store.save(first)
        store.save(first.copy(
            video = media("b.mp4", "b"), videoName = "b.mp4", audio = null
        ))
        val restored = requireNotNull(store.load())
        assertEquals("b", restored.video.readText())
        assertNull(restored.audio)
        assertEquals("b.mp4", restored.videoName)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingInputDoesNotSave() {
        val store = ProjectStore(File(temp.root, "projects"))
        store.save(
            ProjectDocument(
                video = File(temp.root, "missing.mp4"), videoName = "missing",
                durationMs = 1000, trimStartMs = 0, trimEndMs = 1000,
                sourceHasAudio = false
            )
        )
    }

    @Test fun emptyProjectReturnsNull() {
        assertNull(ProjectStore(File(temp.root, "empty")).load())
    }
}

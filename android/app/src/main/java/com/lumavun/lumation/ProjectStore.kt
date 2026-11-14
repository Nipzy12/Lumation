package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion
import java.io.File

/** Editable visual layer state, not the pre-rendered compositing output. */
data class MotionProjectState(
    val text: String = "",
    val imageFile: File? = null,
    val imageName: String = "",
    val textMotion: LayerMotion = LayerMotion(),
    val imageMotion: LayerMotion = LayerMotion(),
    val textScale: Int = 100,
    val imageScale: Int = 100,
    val selected: String = "TEXT",
    val frameWidth: Int = 720,
    val frameHeight: Int = 1280
)

data class ProjectDocument(
    val video: File,
    val videoName: String,
    val audio: File? = null,
    val audioName: String = "",
    val volume: Int = 60,
    val durationMs: Long,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val sourceHasAudio: Boolean,
    val layers: MotionProjectState = MotionProjectState()
)

/** Stores a self-contained local project with media copies in app-private storage. */
class ProjectStore(private val root: File) {
    fun save(project: ProjectDocument) {
        throw UnsupportedOperationException("Not implemented")
    }

    fun load(): ProjectDocument? = null
}

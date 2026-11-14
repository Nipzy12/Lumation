package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion
import java.io.File
import org.json.JSONObject

/** Editable visual layer state, including original image media. */
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

/**
 * Saves a fully self-contained editable project under root/current.
 * All media is copied, not referenced via transient gallery URIs.
 * The original project survives failures while copying a new snapshot.
 */
class ProjectStore(private val root: File) {
    private val slot get() = File(root, "current")
    private val staging get() = File(root, "staging")
    private val backup get() = File(root, "previous")

    private fun ext(file: File): String {
        val raw = file.extension.lowercase()
        return raw.takeIf { it.matches(Regex("[a-z0-9]{1,6}")) } ?: "bin"
    }

    private fun motionJson(m: LayerMotion) = JSONObject()
        .put("startX", m.startX.toDouble()).put("startY", m.startY.toDouble())
        .put("endX", m.endX.toDouble()).put("endY", m.endY.toDouble())

    private fun motionRead(o: JSONObject): LayerMotion {
        fun number(key: String): Float {
            val n = o.getDouble(key)
            require(n.isFinite() && n in 0.0..1.0) { "Invalid keyframe" }
            return n.toFloat()
        }
        return LayerMotion(number("startX"), number("startY"), number("endX"), number("endY"))
    }

    private fun copyMedia(from: File?, label: String): String? {
        if (from == null) return null
        require(from.isFile && from.length() > 0L) { "Missing input: $label" }
        val fileName = "$label.${ext(from)}"
        from.copyTo(File(staging, fileName), overwrite = true)
        return fileName
    }

    fun save(project: ProjectDocument) {
        require(project.video.isFile && project.video.length() > 0L) { "Missing video input" }
        require(project.audio == null || (project.audio.isFile && project.audio.length() > 0L))
        require(project.layers.imageFile == null ||
            (project.layers.imageFile.isFile && project.layers.imageFile.length() > 0L))
        require(project.durationMs > 0 && project.trimStartMs >= 0 &&
            project.trimEndMs > project.trimStartMs && project.trimEndMs <= project.durationMs)
        require(project.volume in 0..100 && project.layers.textScale in 40..180 &&
            project.layers.imageScale in 40..180)
        require(project.layers.frameWidth in 2..2160 && project.layers.frameHeight in 2..3840)
        root.mkdirs()
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Unable to create temporary project directory" }
        try {
            val videoName = copyMedia(project.video, "video")
            val audioName = copyMedia(project.audio, "audio")
            val imageName = copyMedia(project.layers.imageFile, "image")
            val layer = project.layers
            val manifest = JSONObject()
                .put("version", 1)
                .put("video", videoName)
                .put("videoName", project.videoName.take(128))
                .put("audio", audioName)
                .put("audioName", project.audioName.take(128))
                .put("volume", project.volume)
                .put("durationMs", project.durationMs)
                .put("trimStartMs", project.trimStartMs)
                .put("trimEndMs", project.trimEndMs)
                .put("sourceHasAudio", project.sourceHasAudio)
                .put("layers", JSONObject()
                    .put("text", layer.text.take(64))
                    .put("image", imageName)
                    .put("imageName", layer.imageName.take(128))
                    .put("textMotion", motionJson(layer.textMotion))
                    .put("imageMotion", motionJson(layer.imageMotion))
                    .put("textScale", layer.textScale)
                    .put("imageScale", layer.imageScale)
                    .put("selected", layer.selected)
                    .put("frameWidth", layer.frameWidth)
                    .put("frameHeight", layer.frameHeight))
            File(staging, "project.json").writeText(manifest.toString(2), Charsets.UTF_8)
            // Complete the disk write before swapping the existing project slot.
            backup.deleteRecursively()
            var previousMoved = false
            if (slot.exists()) {
                check(slot.renameTo(backup)) { "Cannot back up previous saved project" }
                previousMoved = true
            }
            if (!staging.renameTo(slot)) {
                if (previousMoved) backup.renameTo(slot)
                throw IllegalStateException("Could not publish new project snapshot")
            }
            backup.deleteRecursively()
        } catch (e: Exception) {
            staging.deleteRecursively()
            throw e
        }
    }

    private fun readMedia(json: JSONObject, key: String, expectedPrefix: String): File? {
        if (json.isNull(key)) return null
        val name = json.getString(key)
        require(name.matches(Regex("${expectedPrefix}\\.[a-z0-9]{1,6}"))) {
            "Unsafe project media reference"
        }
        val file = File(slot, name)
        require(file.isFile && file.length() > 0L &&
            file.canonicalFile.parentFile == slot.canonicalFile) { "Saved media missing" }
        return file
    }

    fun load(): ProjectDocument? {
        val manifest = File(slot, "project.json")
        if (!manifest.isFile) return null
        require(manifest.length() < 100_000L) { "Project manifest too large" }
        val json = JSONObject(manifest.readText(Charsets.UTF_8))
        require(json.getInt("version") == 1) { "Unsupported project version" }
        val layer = json.getJSONObject("layers")
        val doc = ProjectDocument(
            video = requireNotNull(readMedia(json, "video", "video")),
            videoName = json.getString("videoName").take(128),
            audio = readMedia(json, "audio", "audio"),
            audioName = json.getString("audioName").take(128),
            volume = json.getInt("volume"),
            durationMs = json.getLong("durationMs"),
            trimStartMs = json.getLong("trimStartMs"),
            trimEndMs = json.getLong("trimEndMs"),
            sourceHasAudio = json.getBoolean("sourceHasAudio"),
            layers = MotionProjectState(
                text = layer.getString("text").take(64),
                imageFile = readMedia(layer, "image", "image"),
                imageName = layer.getString("imageName").take(128),
                textMotion = motionRead(layer.getJSONObject("textMotion")),
                imageMotion = motionRead(layer.getJSONObject("imageMotion")),
                textScale = layer.getInt("textScale"),
                imageScale = layer.getInt("imageScale"),
                selected = layer.getString("selected"),
                frameWidth = layer.getInt("frameWidth"),
                frameHeight = layer.getInt("frameHeight")
            )
        )
        require(doc.durationMs > 0 && doc.trimStartMs >= 0 &&
            doc.trimEndMs > doc.trimStartMs && doc.trimEndMs <= doc.durationMs) {
            "Saved trim interval is invalid"
        }
        require(doc.volume in 0..100 && doc.layers.textScale in 40..180 &&
            doc.layers.imageScale in 40..180 && doc.layers.selected in setOf("TEXT", "IMAGE")) {
            "Saved layer parameters invalid"
        }
        require(doc.layers.frameWidth in 2..2160 && doc.layers.frameHeight in 2..3840)
        return doc
    }
}

package com.lumavun.lumation

import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.TextView
import com.lumavun.lumation.ffmpeg.LayerMotion
import com.lumavun.lumation.ffmpeg.LumationFfmpeg
import com.lumavun.lumation.ffmpeg.RasterLayer
import java.io.File
import java.util.Locale

/**
 * v0.3 editor: one editable text layer and one image layer with two position keyframes
 * each. Frame coordinates are relative to remaining free space (frame minus asset size).
 */
class MotionLayerController(
    private val activity: Activity,
    private val overlay: MotionOverlayView,
    private val timeline: TimelineView,
    private val pickImage: () -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val textInput: EditText = activity.findViewById(R.id.overlayTextInput)
    private val applyText: Button = activity.findViewById(R.id.applyTextButton)
    private val removeText: Button = activity.findViewById(R.id.textRemoveButton)
    private val addImage: Button = activity.findViewById(R.id.imageImportButton)
    private val removeImage: Button = activity.findViewById(R.id.imageRemoveButton)
    private val selectText: Button = activity.findViewById(R.id.selectTextLayer)
    private val selectImage: Button = activity.findViewById(R.id.selectImageLayer)
    private val valueLabel: TextView = activity.findViewById(R.id.layerValueLabel)
    private val startX: SeekBar = activity.findViewById(R.id.layerStartX)
    private val startY: SeekBar = activity.findViewById(R.id.layerStartY)
    private val endX: SeekBar = activity.findViewById(R.id.layerEndX)
    private val endY: SeekBar = activity.findViewById(R.id.layerEndY)
    private val layerScale: SeekBar = activity.findViewById(R.id.layerScale)

    private var frameWidth = 720
    private var frameHeight = 1280
    private var textContent = ""
    private var imageFile: File? = null
    private var imageName = ""
    private var textBitmap: Bitmap? = null
    private var imageBitmap: Bitmap? = null
    private var textMotion = LayerMotion()
    private var imageMotion = LayerMotion()
    private var textScale = 100
    private var imageScale = 100
    private var selected = "TEXT"
    private var busy = false

    init {
        applyText.setOnClickListener {
            if (busy) return@setOnClickListener
            textContent = textInput.text.toString().trim().take(64)
            regenerate()
            onMessage(if (textContent.isEmpty()) "Text layer dihapus." else "Text layer aktif.")
        }
        removeText.setOnClickListener {
            if (busy) return@setOnClickListener
            textInput.setText("")
            textContent = ""
            regenerate()
            onMessage("Text layer dihapus.")
        }
        addImage.setOnClickListener { if (!busy) pickImage() }
        removeImage.setOnClickListener {
            if (!busy) {
                imageFile?.delete()
                imageFile = null
                imageName = ""
                regenerate()
                onMessage("Image layer dihapus.")
            }
        }
        activity.findViewById<Button>(R.id.presetSlideButton).setOnClickListener {
            if (!busy) applyPreset("slide")
        }
        activity.findViewById<Button>(R.id.presetRiseButton).setOnClickListener {
            if (!busy) applyPreset("rise")
        }
        activity.findViewById<Button>(R.id.presetStillButton).setOnClickListener {
            if (!busy) applyPreset("still")
        }
        selectText.setOnClickListener { if (!busy) changeSelection("TEXT") }
        selectImage.setOnClickListener { if (!busy) changeSelection("IMAGE") }

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || busy) return
                val motion = LayerMotion(
                    startX.progress / 100f, startY.progress / 100f,
                    endX.progress / 100f, endY.progress / 100f
                )
                if (selected == "TEXT") {
                    textMotion = motion
                    val newScale = layerScale.progress
                    val scaleChanged = newScale != textScale
                    textScale = newScale
                    if (scaleChanged) regenerate() else syncPreview()
                } else {
                    imageMotion = motion
                    val newScale = layerScale.progress
                    val scaleChanged = newScale != imageScale
                    imageScale = newScale
                    if (scaleChanged) regenerate() else syncPreview()
                }
                updateLabel()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        listOf(startX, startY, endX, endY, layerScale).forEach {
            it.setOnSeekBarChangeListener(seekListener)
        }
        changeSelection("TEXT")
        syncPreview()
    }

    /** Snapshot editable properties and the original image file for ProjectStore. */
    fun snapshot(): MotionProjectState = MotionProjectState(
        text = textContent,
        imageFile = imageFile,
        imageName = imageName,
        textMotion = textMotion,
        imageMotion = imageMotion,
        textScale = textScale,
        imageScale = imageScale,
        selected = selected,
        frameWidth = frameWidth,
        frameHeight = frameHeight
    )

    /** Restore from a working copy of a saved project, never from the snapshot master files. */
    fun restore(snapshot: MotionProjectState) {
        imageFile?.takeIf { it != snapshot.imageFile }?.delete()
        imageFile = snapshot.imageFile
        imageName = snapshot.imageName
        textContent = snapshot.text.take(64)
        textInput.setText(textContent)
        textMotion = snapshot.textMotion
        imageMotion = snapshot.imageMotion
        textScale = snapshot.textScale.coerceIn(40, 180)
        imageScale = snapshot.imageScale.coerceIn(40, 180)
        frameWidth = snapshot.frameWidth
        frameHeight = snapshot.frameHeight
        overlay.renderWidth = frameWidth
        overlay.renderHeight = frameHeight
        regenerate()
        changeSelection(if (snapshot.selected == "IMAGE") "IMAGE" else "TEXT")
    }

    fun applyPreset(kind: String) {
        if (busy) return
        val motion = when (kind) {
            "slide" -> LayerMotion(0f, 0.5f, 1f, 0.5f)
            "rise" -> LayerMotion(0.5f, 1f, 0.5f, 0f)
            "still" -> LayerMotion()
            else -> throw IllegalArgumentException("Unknown motion preset")
        }
        if (selected == "TEXT") textMotion = motion else imageMotion = motion
        changeSelection(selected)
        syncPreview()
        onMessage("Preset ${kind.uppercase(Locale.US)} diterapkan ke layer $selected")
    }

    fun hasLayers(): Boolean = textBitmap != null || imageBitmap != null

    private fun changeSelection(which: String) {
        selected = which
        val motion = if (selected == "TEXT") textMotion else imageMotion
        startX.progress = (motion.startX * 100).toInt()
        startY.progress = (motion.startY * 100).toInt()
        endX.progress = (motion.endX * 100).toInt()
        endY.progress = (motion.endY * 100).toInt()
        layerScale.progress = if (selected == "TEXT") textScale else imageScale
        selectText.alpha = if (selected == "TEXT") 1f else 0.7f
        selectImage.alpha = if (selected == "IMAGE") 1f else 0.7f
        updateLabel()
    }

    private fun updateLabel() {
        val motion = if (selected == "TEXT") textMotion else imageMotion
        val scale = if (selected == "TEXT") textScale else imageScale
        valueLabel.text = String.format(
            Locale.US, "%s • Start (%.0f%%, %.0f%%) → End (%.0f%%, %.0f%%) • Scale %d%%",
            selected, motion.startX * 100, motion.startY * 100,
            motion.endX * 100, motion.endY * 100, scale
        )
    }

    fun setFrameDimensions(sourceWidth: Int, sourceHeight: Int) {
        val (w, h) = OverlayAssets.outputDimensions(sourceWidth, sourceHeight)
        frameWidth = w
        frameHeight = h
        overlay.renderWidth = w
        overlay.renderHeight = h
        regenerate()
    }

    fun updatePlayback(timeMs: Long, trimStartMs: Long, trimEndMs: Long) {
        overlay.playheadMs = timeMs
        overlay.trimStartMs = trimStartMs
        overlay.trimEndMs = trimEndMs.coerceAtLeast(trimStartMs + 1L)
    }

    private fun regenerate() {
        try {
            val newText = OverlayAssets.textBitmap(textContent, frameWidth, textScale)
            val newImage = imageFile?.let {
                OverlayAssets.imageBitmap(it.absolutePath, frameWidth, frameHeight, imageScale)
            }
            textBitmap?.recycle()
            imageBitmap?.recycle()
            textBitmap = newText
            imageBitmap = newImage
            timeline.textTrackName = textContent.takeIf { it.isNotEmpty() }
            timeline.imageTrackName = imageName.takeIf { it.isNotEmpty() }
            removeImage.isEnabled = !busy && imageFile != null
            syncPreview()
        } catch (e: Exception) {
            onMessage("Gagal membuat layer: ${e.message}")
        }
    }

    private fun syncPreview() {
        overlay.layers = listOfNotNull(
            textBitmap?.let { PreviewRasterLayer(it, textMotion) },
            imageBitmap?.let { PreviewRasterLayer(it, imageMotion) }
        )
    }

    fun importImage(uri: Uri) {
        if (busy) return
        setBusy(true)
        Thread {
            var file: File? = null
            try {
                val label = activity.contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    } else null
                } ?: "image"
                val directory = File(activity.filesDir, "imports").apply { mkdirs() }
                val destination = File(directory, "overlay-${System.currentTimeMillis()}.png")
                file = destination
                val source = activity.contentResolver.openInputStream(uri)
                    ?: throw IllegalArgumentException("Gambar tidak dapat dibaca")
                source.use { src -> destination.outputStream().use { dst -> src.copyTo(dst) } }
                if (destination.length() == 0L) throw IllegalArgumentException("File gambar kosong")
                // Validate the image on the worker before replacing the previous overlay.
                val probe = OverlayAssets.imageBitmap(destination.absolutePath, frameWidth, frameHeight, imageScale)
                activity.runOnUiThread {
                    imageBitmap?.recycle()
                    imageBitmap = probe
                    imageFile?.delete()
                    imageFile = destination
                    imageName = label
                    file = null
                    timeline.imageTrackName = imageName
                    setBusy(false)
                    syncPreview()
                    changeSelection("IMAGE")
                    onMessage("Image layer siap. Atur keyframe awal dan akhir.")
                }
            } catch (e: Exception) {
                file?.delete()
                activity.runOnUiThread {
                    setBusy(false)
                    onMessage("Gagal impor gambar: ${e.message}")
                }
            }
        }.start()
    }

    fun setBusy(value: Boolean) {
        busy = value
        listOf(
            applyText, removeText, addImage, selectText, selectImage,
            activity.findViewById<Button>(R.id.presetSlideButton),
            activity.findViewById<Button>(R.id.presetRiseButton),
            activity.findViewById<Button>(R.id.presetStillButton)
        ).forEach { it.isEnabled = !value }
        removeImage.isEnabled = !value && imageFile != null
        textInput.isEnabled = !value
        listOf(startX, startY, endX, endY, layerScale).forEach { it.isEnabled = !value }
    }

    /**
     * Snapshots the actual preview bitmaps as transparent PNG files and exports
     * the matching FFmpeg filter graph. No text is interpreted as FFmpeg syntax.
     */
    fun exportComposited(
        video: File,
        soundtrack: File?,
        output: File,
        startMs: Long,
        endMs: Long,
        sourceHasAudio: Boolean,
        musicVolume: Float,
        onComplete: (Result<File>) -> Unit
    ) {
        val sources = listOfNotNull(
            textBitmap?.let { it to textMotion },
            imageBitmap?.let { it to imageMotion }
        )
        if (sources.isEmpty()) {
            onComplete(Result.failure(IllegalArgumentException("No visual layers to render")))
            return
        }
        val dir = File(activity.cacheDir, "layers-${System.currentTimeMillis()}").apply { mkdirs() }
        Thread {
            try {
                val layerFiles = sources.mapIndexed { index, (bitmap, motion) ->
                    val file = File(dir, "layer-$index.png")
                    file.outputStream().use { stream ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            "PNG encoding failed"
                        }
                    }
                    RasterLayer(file, motion)
                }
                LumationFfmpeg.exportWithMotionLayers(
                    video, soundtrack, layerFiles, output,
                    startMs, endMs, frameWidth, frameHeight,
                    sourceHasAudio, musicVolume
                ) { result ->
                    dir.listFiles()?.forEach { it.delete() }
                    dir.delete()
                    onComplete(result)
                }
            } catch (e: Exception) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
                onComplete(Result.failure(e))
            }
        }.start()
    }

    fun dispose() {
        textBitmap?.recycle()
        imageBitmap?.recycle()
        textBitmap = null
        imageBitmap = null
        overlay.layers = emptyList()
    }
}

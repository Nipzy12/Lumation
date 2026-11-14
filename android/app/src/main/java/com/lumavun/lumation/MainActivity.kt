package com.lumavun.lumation

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import com.lumavun.lumation.ffmpeg.LumationFfmpeg
import java.io.File
import java.util.Locale

/**
 * First installable Lumation demo.
 * This native shell exercises gallery import, single-track trim preview,
 * FFmpeg export and Android's Movies/Lumation output folder.
 * Remotion compositions and local AI are separate, future milestones.
 */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_VIDEO = 201
        private const val REQUEST_AUDIO = 202
        private const val REQUEST_IMAGE = 203
    }

    private lateinit var preview: VideoView
    private lateinit var emptyPreview: TextView
    private lateinit var fileName: TextView
    private lateinit var timeLabel: TextView
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var audioImportButton: Button
    private lateinit var audioRemoveButton: Button
    private lateinit var audioFileName: TextView
    private lateinit var audioVolumeLabel: TextView
    private lateinit var audioVolumeSeek: SeekBar
    private lateinit var playButton: Button
    private lateinit var exportButton: Button
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var timeline: TimelineView
    private lateinit var motionController: MotionLayerController
    private lateinit var exportProgress: ProgressBar
    private lateinit var saveProjectButton: Button
    private lateinit var loadProjectButton: Button
    private lateinit var projectStore: ProjectStore

    private val handler = Handler(Looper.getMainLooper())
    private var source: File? = null
    private var soundtrack: File? = null
    private var soundtrackName: String? = null
    private var soundtrackPlayer: MediaPlayer? = null
    private var soundtrackDurationMs = 0L
    private var sourceHasAudio = false
    private var durationMs = 0L
    private var trimStartMs = 0L
    private var trimEndMs = 0L
    private var busy = false
    private var playbackReady = false
    private var lastExport: Uri? = null
    private val refreshPlayback = object : Runnable {
        override fun run() {
            if (playbackReady && preview.isPlaying) {
                val now = preview.currentPosition.toLong().coerceAtLeast(0L)
                timeline.cursorMs = now
                motionController.updatePlayback(now, trimStartMs, trimEndMs)
                timeLabel.text = "${clock(now)} / ${clock(durationMs)}"
                if (now >= trimEndMs) {
                    preview.pause()
                    preview.seekTo(trimStartMs.toInt())
                    timeline.cursorMs = trimStartMs
                    motionController.updatePlayback(trimStartMs, trimStartMs, trimEndMs)
                    playButton.text = "▶ Play"
                    pauseSoundtrack()
                }
            } else if (soundtrackPlayer?.isPlaying == true) {
                pauseSoundtrack()
            }
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#0C0E19")
        window.navigationBarColor = android.graphics.Color.parseColor("#0C0E19")
        setContentView(R.layout.activity_main)
        projectStore = ProjectStore(File(filesDir, "projects"))

        preview = findViewById(R.id.videoPreview)
        emptyPreview = findViewById(R.id.emptyPreview)
        fileName = findViewById(R.id.fileName)
        timeLabel = findViewById(R.id.timeLabel)
        startLabel = findViewById(R.id.startLabel)
        endLabel = findViewById(R.id.endLabel)
        status = findViewById(R.id.status)
        importButton = findViewById(R.id.importButton)
        audioImportButton = findViewById(R.id.audioImportButton)
        audioRemoveButton = findViewById(R.id.audioRemoveButton)
        audioFileName = findViewById(R.id.audioFileName)
        audioVolumeLabel = findViewById(R.id.audioVolumeLabel)
        audioVolumeSeek = findViewById(R.id.audioVolumeSeek)
        playButton = findViewById(R.id.playButton)
        exportButton = findViewById(R.id.exportButton)
        startSeek = findViewById(R.id.startSeek)
        endSeek = findViewById(R.id.endSeek)
        timeline = findViewById(R.id.timeline)
        exportProgress = findViewById(R.id.exportProgress)
        saveProjectButton = findViewById(R.id.saveProjectButton)
        loadProjectButton = findViewById(R.id.loadProjectButton)
        motionController = MotionLayerController(
            this, findViewById(R.id.motionPreview), timeline,
            pickImage = { openImagePicker() },
            onMessage = { message -> status.text = message }
        )

        saveProjectButton.setOnClickListener { saveCurrentProject() }
        loadProjectButton.setOnClickListener { openSavedProject() }
        importButton.setOnClickListener { openGallery() }
        audioImportButton.setOnClickListener { openAudioPicker() }
        audioRemoveButton.setOnClickListener { clearSoundtrack() }
        audioVolumeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, value: Int, fromUser: Boolean) {
                audioVolumeLabel.text = "Volume musik: $value%"
                soundtrackPlayer?.setVolume(value / 100f, value / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        playButton.setOnClickListener { togglePlayback() }
        exportButton.setOnClickListener { exportCurrentTrim() }
        preview.setOnPreparedListener { player ->
            playbackReady = true
            player.isLooping = false
            playButton.isEnabled = !busy
            status.text = "Preview siap. Geser timeline atau trim klip."
        }
        preview.setOnErrorListener { _, _, _ ->
            playbackReady = false
            playButton.isEnabled = false
            pauseSoundtrack()
            status.text = "Preview tidak didukung oleh decoder HP. Ekspor FFmpeg tetap dapat dicoba."
            true
        }
        preview.setOnCompletionListener {
            pauseSoundtrack()
            playButton.text = "▶ Play"
            timeline.cursorMs = trimStartMs
            motionController.updatePlayback(trimStartMs, trimStartMs, trimEndMs)
            preview.seekTo(trimStartMs.toInt())
        }
        timeline.onSeek = { ms ->
            motionController.updatePlayback(ms.coerceIn(trimStartMs, trimEndMs), trimStartMs, trimEndMs)
            if (playbackReady) {
                preview.seekTo(ms.coerceIn(trimStartMs, trimEndMs).toInt())
                timeLabel.text = "${clock(ms)} / ${clock(durationMs)}"
                if (preview.isPlaying) startSoundtrackAt(ms)
            }
        }
        startSeek.setOnSeekBarChangeListener(seekListener(isStart = true))
        endSeek.setOnSeekBarChangeListener(seekListener(isStart = false))
        handler.post(refreshPlayback)
    }

    private fun seekListener(isStart: Boolean) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (durationMs == 0L || !fromUser) return
            if (isStart && progress >= endSeek.progress) {
                startSeek.progress = (endSeek.progress - 1).coerceAtLeast(0)
                return
            }
            if (!isStart && progress <= startSeek.progress) {
                endSeek.progress = (startSeek.progress + 1).coerceAtMost(1000)
                return
            }
            updateTrim()
            if (isStart && playbackReady) {
                preview.pause()
                pauseSoundtrack()
                preview.seekTo(trimStartMs.toInt())
                playButton.text = "▶ Play"
                timeline.cursorMs = trimStartMs
            }
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateTrim() {
        if (durationMs <= 0L) return
        trimStartMs = durationMs * startSeek.progress / 1000L
        trimEndMs = durationMs * endSeek.progress / 1000L
        timeline.trimStartMs = trimStartMs
        timeline.trimEndMs = trimEndMs
        startLabel.text = "Mulai: ${clock(trimStartMs)}"
        endLabel.text = "Selesai: ${clock(trimEndMs)}"
        if (::motionController.isInitialized) {
            motionController.updatePlayback(timeline.cursorMs, trimStartMs, trimEndMs)
        }
    }

    private fun openImagePicker() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQUEST_IMAGE)
    }

    private fun openAudioPicker() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
        }
        startActivityForResult(intent, REQUEST_AUDIO)
    }

    private fun openGallery() {
        if (busy) return
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Android Activity result callback; migrate to Activity Result API in a later release")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_VIDEO) data?.data?.let(::importVideo)
            if (requestCode == REQUEST_AUDIO) data?.data?.let(::importAudio)
            if (requestCode == REQUEST_IMAGE) data?.data?.let(motionController::importImage)
        }
    }

    private fun importVideo(uri: Uri) {
        setBusy(true, "Mengimpor video dari galeri...")
        val label = displayName(uri) ?: "video"
        Thread {
            try {
                val suffix = label.substringAfterLast('.', "mp4")
                    .lowercase(Locale.US).takeIf { it.matches(Regex("[a-z0-9]{1,6}")) } ?: "mp4"
                val directory = File(filesDir, "imports").apply { mkdirs() }
                val imported = File(directory, "clip-${System.currentTimeMillis()}.$suffix")
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Galeri tidak menyediakan data video")
                stream.use { src -> imported.outputStream().use { dst -> src.copyTo(dst) } }
                if (imported.length() == 0L) throw IllegalStateException("File video kosong")

                val retriever = MediaMetadataRetriever()
                var containsAudio = false
                var frameW = 720
                var frameH = 1280
                val length = try {
                    retriever.setDataSource(imported.absolutePath)
                    containsAudio = retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                    ).equals("yes", ignoreCase = true)
                    frameW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 720
                    frameH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 1280
                    val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                        ?.toIntOrNull() ?: 0
                    if (rotation % 180 != 0) {
                        val swap = frameW
                        frameW = frameH
                        frameH = swap
                    }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
                if (length < 100L || length > Int.MAX_VALUE.toLong()) {
                    throw IllegalArgumentException("Durasi video tidak didukung dalam demo")
                }
                runOnUiThread {
                    source?.takeIf { it != imported }?.delete()
                    source = imported
                    sourceHasAudio = containsAudio
                    durationMs = length
                    trimStartMs = 0L
                    trimEndMs = length
                    timeline.durationMs = length
                    timeline.cursorMs = 0L
                    timeline.clipName = label
                    motionController.setFrameDimensions(frameW, frameH)
                    startSeek.progress = 0
                    endSeek.progress = 1000
                    updateTrim()
                    fileName.text = label
                    timeLabel.text = "${clock(0)} / ${clock(length)}"
                    pauseSoundtrack()
                    preview.stopPlayback()
                    playbackReady = false
                    preview.setVideoPath(imported.absolutePath)
                    preview.seekTo(1)
                    emptyPreview.visibility = View.GONE
                    exportButton.isEnabled = true
                    playButton.isEnabled = false
                    setBusy(false, "Memuat preview...")
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "Gagal impor video: ${e.message}") }
            }
        }.start()
    }

    private fun togglePlayback() {
        if (!playbackReady || busy) return
        if (preview.isPlaying) {
            preview.pause()
            pauseSoundtrack()
            playButton.text = "▶ Play"
        } else {
            if (preview.currentPosition.toLong() >= trimEndMs ||
                preview.currentPosition.toLong() < trimStartMs) {
                preview.seekTo(trimStartMs.toInt())
            }
            preview.start()
            motionController.updatePlayback(preview.currentPosition.toLong(), trimStartMs, trimEndMs)
            startSoundtrackAt(preview.currentPosition.toLong())
            playButton.text = "Ⅱ Pause"
        }
    }

    private fun exportCurrentTrim() {
        val input = source ?: return
        if (busy || trimEndMs - trimStartMs < 100L) {
            status.text = "Pilih klip dengan durasi minimal 0,1 detik."
            return
        }
        if (playbackReady && preview.isPlaying) {
            preview.pause()
            pauseSoundtrack()
            playButton.text = "▶ Play"
        }
        setBusy(true, "Encoding MP4 offline lewat FFmpeg...")
        val temp = File(cacheDir, "lumation-${System.currentTimeMillis()}.mp4")
        val callback: (Result<File>) -> Unit = { result ->
            result.fold(
                onSuccess = { file ->
                    Thread {
                        try {
                            val uri = saveToGallery(file)
                            runOnUiThread {
                                lastExport = uri
                                setBusy(false, "Ekspor selesai! Video tersimpan di Movies/Lumation.")
                            }
                        } catch (e: Exception) {
                            runOnUiThread { setBusy(false, "Gagal menyimpan video: ${e.message}") }
                        } finally {
                            file.delete()
                        }
                    }.start()
                },
                onFailure = { error ->
                    temp.delete()
                    runOnUiThread { setBusy(false, "Ekspor gagal: ${error.message}") }
                }
            )
        }
        val music = soundtrack
        if (motionController.hasLayers()) {
            motionController.exportComposited(
                input, music, temp, trimStartMs, trimEndMs,
                sourceHasAudio, audioVolumeSeek.progress / 100f, callback
            )
        } else if (music != null) {
            LumationFfmpeg.exportWithSoundtrack(
                input, music, temp, trimStartMs, trimEndMs,
                sourceHasAudio, audioVolumeSeek.progress / 100f, callback
            )
        } else {
            LumationFfmpeg.exportTrimmedMp4(input, temp, trimStartMs, trimEndMs, callback)
        }
    }


    /** Import selected audio into private app storage and prepare local music preview. */
    private fun importAudio(uri: Uri) {
        if (busy) return
        setBusy(true, "Mengimpor audio...")
        val display = displayName(uri) ?: "soundtrack"
        Thread {
            var imported: File? = null
            var player: MediaPlayer? = null
            try {
                val ext = display.substringAfterLast('.', "mp3")
                    .lowercase(Locale.US).takeIf { it.matches(Regex("[a-z0-9]{1,6}")) } ?: "mp3"
                val directory = File(filesDir, "imports").apply { mkdirs() }
                val file = File(directory, "music-${System.currentTimeMillis()}.$ext")
                imported = file
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("File audio tidak dapat dibuka")
                stream.use { src -> file.outputStream().use { dst -> src.copyTo(dst) } }
                if (file.length() == 0L) throw IllegalStateException("Audio kosong")
                val prepared = MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    isLooping = true
                    prepare()
                }
                player = prepared
                val duration = prepared.duration.toLong()
                if (duration <= 0L) throw IllegalStateException("Durasi audio tidak didukung")
                val newPlayer = prepared
                val newFile = file
                runOnUiThread {
                    pauseSoundtrack()
                    soundtrackPlayer?.release()
                    soundtrack?.takeIf { it != newFile }?.delete()
                    soundtrack = newFile
                    soundtrackName = display
                    soundtrackPlayer = newPlayer
                    soundtrackDurationMs = duration
                    timeline.musicTrackName = display
                    audioFileName.text = "♫ $display"
                    val gain = audioVolumeSeek.progress / 100f
                    newPlayer.setVolume(gain, gain)
                    player = null
                    imported = null
                    setBusy(false, "Audio track ditambahkan. Musik akan diputar berulang saat preview.")
                }
            } catch (e: Exception) {
                player?.release()
                imported?.delete()
                runOnUiThread { setBusy(false, "Gagal impor audio: ${e.message}") }
            }
        }.start()
    }

    private fun clearSoundtrack() {
        if (busy) return
        pauseSoundtrack()
        soundtrackPlayer?.release()
        soundtrackPlayer = null
        soundtrack?.delete()
        soundtrack = null
        soundtrackName = null
        soundtrackDurationMs = 0L
        timeline.musicTrackName = null
        audioFileName.text = "Audio track belum ditambahkan"
        setBusy(false, "Audio track dihapus.")
    }

    /** Sync music position to the trimmed clip, repeating when audio is shorter. */
    private fun startSoundtrackAt(videoTimeMs: Long) {
        val player = soundtrackPlayer ?: return
        if (soundtrackDurationMs <= 0L || busy) return
        try {
            val relative = (videoTimeMs - trimStartMs).coerceAtLeast(0L)
            player.seekTo((relative % soundtrackDurationMs).toInt())
            if (!player.isPlaying) player.start()
        } catch (e: IllegalStateException) {
            status.text = "Audio preview tidak dapat diputar: ${e.message}"
        }
    }

    private fun pauseSoundtrack() {
        try {
            if (soundtrackPlayer?.isPlaying == true) soundtrackPlayer?.pause()
        } catch (_: IllegalStateException) {
            // Playback can already be released while the activity is closing.
        }
    }


    /**
     * Persist one named default project slot. Source media is copied by ProjectStore
     * so editing another project won't invalidate a saved snapshot.
     */
    private fun saveCurrentProject() {
        if (busy) return
        val clip = source ?: run {
            status.text = "Tambahkan video terlebih dahulu."
            return
        }
        val document = ProjectDocument(
            video = clip,
            videoName = fileName.text.toString(),
            audio = soundtrack,
            audioName = soundtrackName ?: "",
            volume = audioVolumeSeek.progress,
            durationMs = durationMs,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            sourceHasAudio = sourceHasAudio,
            layers = motionController.snapshot()
        )
        setBusy(true, "Menyimpan project dan menyalin media lokal...")
        Thread {
            try {
                projectStore.save(document)
                runOnUiThread {
                    setBusy(false, "Project tersimpan. Tombol Open Project dapat memulihkannya.")
                }
            } catch (e: Exception) {
                runOnUiThread { setBusy(false, "Gagal menyimpan project: ${e.message}") }
            }
        }.start()
    }

    /**
     * Projects contain separate private copies of media. Copy them again into
     * working imports before restoring: delete/replace commands must not touch
     * the saved project's master files.
     */
    private fun openSavedProject() {
        if (busy) return
        setBusy(true, "Membuka project dan menyiapkan media...")
        Thread {
            val copiedFiles = mutableListOf<File>()
            var newAudioPlayer: MediaPlayer? = null
            try {
                val project = projectStore.load()
                    ?: throw IllegalStateException("Belum ada project tersimpan")
                val directory = File(filesDir, "imports").apply { mkdirs() }

                fun workingCopy(original: File?): File? {
                    if (original == null) return null
                    val safeName = original.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                    val target = File(
                        directory, "restored-${java.util.UUID.randomUUID()}-$safeName"
                    )
                    original.copyTo(target)
                    copiedFiles.add(target)
                    return target
                }

                val videoCopy = requireNotNull(workingCopy(project.video))
                val audioCopy = workingCopy(project.audio)
                val imageCopy = workingCopy(project.layers.imageFile)
                if (audioCopy != null) {
                    newAudioPlayer = MediaPlayer().apply {
                        setDataSource(audioCopy.absolutePath)
                        isLooping = true
                        prepare()
                    }
                }
                val loaded = project.copy(
                    video = videoCopy,
                    audio = audioCopy,
                    layers = project.layers.copy(imageFile = imageCopy)
                )
                val preparedPlayer = newAudioPlayer
                runOnUiThread {
                    try {
                        pauseSoundtrack()
                        soundtrackPlayer?.release()
                        preview.stopPlayback()
                        val previousVideo = source
                        val previousAudio = soundtrack

                        // Restore visual-layer assets before the new media is assigned.
                        motionController.restore(loaded.layers)
                        source = loaded.video
                        soundtrack = loaded.audio
                        soundtrackPlayer = preparedPlayer
                        soundtrackDurationMs = preparedPlayer?.duration?.toLong() ?: 0L
                        soundtrackName = loaded.audioName.takeIf { loaded.audio != null }
                        sourceHasAudio = loaded.sourceHasAudio
                        durationMs = loaded.durationMs
                        trimStartMs = loaded.trimStartMs
                        trimEndMs = loaded.trimEndMs

                        timeline.durationMs = durationMs
                        timeline.trimStartMs = trimStartMs
                        timeline.trimEndMs = trimEndMs
                        timeline.cursorMs = trimStartMs
                        timeline.clipName = loaded.videoName
                        fileName.text = loaded.videoName
                        audioFileName.text = if (loaded.audio != null) {
                            "♫ ${loaded.audioName}"
                        } else {
                            "Audio track belum ditambahkan"
                        }
                        timeline.musicTrackName = loaded.audioName.takeIf { loaded.audio != null }
                        audioVolumeSeek.progress = loaded.volume
                        startSeek.progress = (loaded.trimStartMs * 1000L / durationMs)
                            .toInt().coerceIn(0, 1000)
                        endSeek.progress = (loaded.trimEndMs * 1000L / durationMs)
                            .toInt().coerceIn(0, 1000)
                        startLabel.text = "Mulai: ${clock(trimStartMs)}"
                        endLabel.text = "Selesai: ${clock(trimEndMs)}"
                        timeLabel.text = "${clock(trimStartMs)} / ${clock(durationMs)}"
                        motionController.updatePlayback(trimStartMs, trimStartMs, trimEndMs)
                        emptyPreview.visibility = View.GONE
                        playbackReady = false
                        preview.setVideoPath(loaded.video.absolutePath)
                        preview.seekTo(trimStartMs.toInt())
                        previousVideo?.delete()
                        previousAudio?.delete()
                        setBusy(false, "Project dibuka dari penyimpanan lokal.")
                    } catch (e: Exception) {
                        preparedPlayer?.release()
                        copiedFiles.forEach { it.delete() }
                        setBusy(false, "Gagal memulihkan project: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                newAudioPlayer?.release()
                copiedFiles.forEach { it.delete() }
                runOnUiThread { setBusy(false, "Gagal membuka project: ${e.message}") }
            }
        }.start()
    }

    private fun saveToGallery(file: File): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Lumation-${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Lumation")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore tidak dapat membuat file")
        try {
            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException("Tidak bisa membuka file tujuan")
            output.use { dst -> file.inputStream().use { src -> src.copyTo(dst) } }
            contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            return uri
        } catch (e: Exception) {
            contentResolver.delete(uri, null, null)
            throw e
        }
    }

    private fun displayName(uri: Uri): String? {
        return contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) null
                else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
    }

    private fun setBusy(value: Boolean, message: String) {
        busy = value
        status.text = message
        exportProgress.visibility = if (value) View.VISIBLE else View.GONE
        importButton.isEnabled = !value
        saveProjectButton.isEnabled = !value && source != null
        loadProjectButton.isEnabled = !value
        audioImportButton.isEnabled = !value
        audioRemoveButton.isEnabled = !value && soundtrack != null
        audioVolumeSeek.isEnabled = !value && soundtrack != null
        playButton.isEnabled = !value && playbackReady
        exportButton.isEnabled = !value && source != null
        startSeek.isEnabled = !value
        endSeek.isEnabled = !value
        if (::motionController.isInitialized) motionController.setBusy(value)
    }

    private fun clock(ms: Long): String {
        val minutes = ms / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        val tenths = (ms % 1_000L) / 100L
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshPlayback)
        if (::motionController.isInitialized) motionController.dispose()
        pauseSoundtrack()
        soundtrackPlayer?.release()
        soundtrackPlayer = null
        preview.stopPlayback()
        super.onDestroy()
    }
}

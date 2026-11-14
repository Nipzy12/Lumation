package com.lumavun.lumation

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
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
    }

    private lateinit var preview: VideoView
    private lateinit var emptyPreview: TextView
    private lateinit var fileName: TextView
    private lateinit var timeLabel: TextView
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var playButton: Button
    private lateinit var exportButton: Button
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var timeline: TimelineView
    private lateinit var exportProgress: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var source: File? = null
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
                timeLabel.text = "${clock(now)} / ${clock(durationMs)}"
                if (now >= trimEndMs) {
                    preview.pause()
                    preview.seekTo(trimStartMs.toInt())
                    timeline.cursorMs = trimStartMs
                    playButton.text = "▶ Play"
                }
            }
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.parseColor("#0C0E19")
        window.navigationBarColor = android.graphics.Color.parseColor("#0C0E19")
        setContentView(R.layout.activity_main)

        preview = findViewById(R.id.videoPreview)
        emptyPreview = findViewById(R.id.emptyPreview)
        fileName = findViewById(R.id.fileName)
        timeLabel = findViewById(R.id.timeLabel)
        startLabel = findViewById(R.id.startLabel)
        endLabel = findViewById(R.id.endLabel)
        status = findViewById(R.id.status)
        importButton = findViewById(R.id.importButton)
        playButton = findViewById(R.id.playButton)
        exportButton = findViewById(R.id.exportButton)
        startSeek = findViewById(R.id.startSeek)
        endSeek = findViewById(R.id.endSeek)
        timeline = findViewById(R.id.timeline)
        exportProgress = findViewById(R.id.exportProgress)

        importButton.setOnClickListener { openGallery() }
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
            status.text = "Preview tidak didukung oleh decoder HP. Ekspor FFmpeg tetap dapat dicoba."
            true
        }
        preview.setOnCompletionListener {
            playButton.text = "▶ Play"
            timeline.cursorMs = trimStartMs
            preview.seekTo(trimStartMs.toInt())
        }
        timeline.onSeek = { ms ->
            if (playbackReady) {
                preview.seekTo(ms.coerceIn(trimStartMs, trimEndMs).toInt())
                timeLabel.text = "${clock(ms)} / ${clock(durationMs)}"
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
        if (requestCode == REQUEST_VIDEO && resultCode == RESULT_OK) {
            data?.data?.let(::importVideo)
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
                val length = try {
                    retriever.setDataSource(imported.absolutePath)
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
                    durationMs = length
                    trimStartMs = 0L
                    trimEndMs = length
                    timeline.durationMs = length
                    timeline.cursorMs = 0L
                    timeline.clipName = label
                    startSeek.progress = 0
                    endSeek.progress = 1000
                    updateTrim()
                    fileName.text = label
                    timeLabel.text = "${clock(0)} / ${clock(length)}"
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
            playButton.text = "▶ Play"
        } else {
            if (preview.currentPosition.toLong() >= trimEndMs ||
                preview.currentPosition.toLong() < trimStartMs) {
                preview.seekTo(trimStartMs.toInt())
            }
            preview.start()
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
            playButton.text = "▶ Play"
        }
        setBusy(true, "Encoding MP4 offline lewat FFmpeg...")
        val temp = File(cacheDir, "lumation-${System.currentTimeMillis()}.mp4")
        LumationFfmpeg.exportTrimmedMp4(input, temp, trimStartMs, trimEndMs) { result ->
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
        playButton.isEnabled = !value && playbackReady
        exportButton.isEnabled = !value && source != null
        startSeek.isEnabled = !value
        endSeek.isEnabled = !value
    }

    private fun clock(ms: Long): String {
        val minutes = ms / 60_000L
        val seconds = (ms % 60_000L) / 1_000L
        val tenths = (ms % 1_000L) / 100L
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshPlayback)
        preview.stopPlayback()
        super.onDestroy()
    }
}

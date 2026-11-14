package com.lumavun.lumation

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import java.io.File
import java.util.Locale

/**
 * UI controller for local GGUF model lifecycle. No network permission or cloud service needed.
 * Workers never touch UI views directly; all editing is locked for native model operations.
 */
class LocalAiController(
    private val activity: Activity,
    private val selectDocument: () -> Unit,
    private val setEditingBusy: (Boolean, String) -> Unit,
    private val isEditingBusy: () -> Boolean
) {
    private val storage = GgufModelStore(File(activity.filesDir, "ai-models"))
    private val modelLabel: TextView = activity.findViewById(R.id.ggufModelLabel)
    private val responseLabel: TextView = activity.findViewById(R.id.aiResponseLabel)
    private val commandInput: EditText = activity.findViewById(R.id.offlineCommandInput)
    private val importButton: Button = activity.findViewById(R.id.importGgufButton)
    private val loadButton: Button = activity.findViewById(R.id.loadGgufButton)
    private val unloadButton: Button = activity.findViewById(R.id.unloadGgufButton)
    private val deleteButton: Button = activity.findViewById(R.id.deleteGgufButton)
    private val suggestButton: Button = activity.findViewById(R.id.aiSuggestButton)

    private var modelLoaded = false
    private var modelOpBusy = false
    private var parentBusy = false

    init {
        importButton.setOnClickListener { if (!isEditingBusy()) selectDocument() }
        loadButton.setOnClickListener { loadModel() }
        unloadButton.setOnClickListener { unloadModel() }
        deleteButton.setOnClickListener {
            if (!isEditingBusy()) {
                AlertDialog.Builder(activity)
                    .setTitle("Hapus model GGUF?")
                    .setMessage("Model lokal akan dihapus dari HP. Proyek video tidak terpengaruh.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Hapus") { _, _ -> removeModel() }
                    .show()
            }
        }
        suggestButton.setOnClickListener { suggest() }
        refresh()
    }

    /** Called by the activity whenever an editor operation changes its busy state. */
    fun syncBusy(value: Boolean) {
        parentBusy = value
        refresh()
    }

    private fun refresh() {
        val model = storage.current()
        val available = LocalLlamaBridge.isAvailable
        modelLabel.text = when {
            !available -> "llama.cpp library tidak tersedia: ${LocalLlamaBridge.loadFailureMessage ?: "JNI gagal"}"
            model == null -> "Model GGUF belum dipasang. Offline Quick Command tetap tersedia."
            else -> {
                val mb = model.length() / 1_000_000f
                "GGUF: ${String.format(Locale.US, "%.1f", mb)} MB · " +
                    if (modelLoaded) "Loaded (CPU)" else "Belum di-load"
            }
        }
        val enable = !parentBusy && !modelOpBusy
        importButton.isEnabled = enable
        loadButton.isEnabled = enable && available && model != null && !modelLoaded
        unloadButton.isEnabled = enable && available && modelLoaded
        deleteButton.isEnabled = enable && model != null
        suggestButton.isEnabled = enable && available && modelLoaded
    }

    fun onDocumentSelected(uri: Uri) {
        if (isEditingBusy() || modelOpBusy) return
        runWorker("Mengimpor GGUF ke penyimpanan privat...") {
            if (modelLoaded) LocalLlamaBridge.nativeUnload()
            modelLoaded = false
            val result = storage.importFrom(activity.contentResolver, uri)
            "GGUF tersimpan di HP (${result.length() / 1_000_000} MB). Tekan Load Model."
        }
    }

    private fun loadModel() {
        if (isEditingBusy() || modelOpBusy) return
        val model = storage.current() ?: return
        if (!LocalLlamaBridge.isAvailable) return
        runWorker("Memuat model GGUF ke RAM, CPU inference...") {
            val success = LocalLlamaBridge.nativeLoad(model.absolutePath)
            modelLoaded = success
            if (!success) error("Model tidak cocok atau RAM tidak cukup untuk memuat GGUF.")
            "Model berhasil dimuat. Ketik prompt lalu tekan AI Suggest."
        }
    }

    private fun unloadModel() {
        if (isEditingBusy() || modelOpBusy) return
        runWorker("Melepas model dari RAM...") {
            if (LocalLlamaBridge.isAvailable) LocalLlamaBridge.nativeUnload()
            modelLoaded = false
            "Model dilepas dari RAM. GGUF tetap tersimpan."
        }
    }

    private fun removeModel() {
        if (isEditingBusy() || modelOpBusy) return
        runWorker("Menghapus model GGUF...") {
            if (LocalLlamaBridge.isAvailable) LocalLlamaBridge.nativeUnload()
            modelLoaded = false
            check(storage.delete()) { "Gagal menghapus file model." }
            "Model dihapus. Editor dan proyek tidak terpengaruh."
        }
    }

    private fun suggest() {
        if (isEditingBusy() || modelOpBusy || !modelLoaded) return
        val prompt = commandInput.text.toString().trim()
        if (prompt.isBlank() || prompt.length > 256) {
            responseLabel.text = "Ketik permintaan animasi (maksimum 256 karakter)."
            return
        }
        runWorker("AI lokal membuat saran (tanpa internet)...") {
            val response = LocalLlamaBridge.nativeInfer(prompt)
            val command = AiSuggestion.commandFromModel(response)
                ?: throw IllegalArgumentException(
                    "Keluaran AI tidak sesuai format perintah. Coba prompt lebih sederhana. " +
                        response.take(160)
                )
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    commandInput.setText(command)
                    responseLabel.text = "Saran AI: $command"
                }
            }
            "Saran AI siap. Periksa hasil lalu tekan Apply (manual)."
        }
    }

    /** All native work runs on a background thread with global editor controls locked. */
    private fun runWorker(title: String, action: () -> String) {
        if (modelOpBusy || isEditingBusy()) return
        modelOpBusy = true
        setEditingBusy(true, title)
        refresh()
        Thread {
            val message = try { action() } catch (error: Exception) {
                "AI lokal gagal: ${error.message ?: "Kesalahan model"}"
            } catch (error: LinkageError) {
                "Runtime llama.cpp tidak cocok: ${error.message}"
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                modelOpBusy = false
                setEditingBusy(false, message)
                refresh()
            }
        }.start()
    }
}

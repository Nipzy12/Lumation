package com.lumavun.lumation

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Import one user-selected GGUF into private storage.
 * Models are NEVER included with the APK and are never uploaded to a server.
 */
class GgufModelStore(private val directory: File) {
    companion object {
        const val MAX_BYTES = 1_610_612_736L // 1.5 GiB: bounded device/storage usage.
        const val MIN_BYTES = 32_000_000L
    }
    val activeFile get() = File(directory, "active.gguf")

    fun current(): File? = activeFile.takeIf {
        it.isFile && ModelValidation.validSize(it.length()) &&
            ModelValidation.hasMagic(it.inputStream().use { input ->
                ByteArray(4).also { input.read(it) }
            })
    }

    @Throws(IOException::class)
    fun importFrom(resolver: ContentResolver, uri: Uri): File {
        check(directory.mkdirs() || directory.isDirectory) { "Model directory is unavailable" }
        val temp = File(directory, "incoming-${UUID.randomUUID()}.gguf")
        try {
            var copied = 0L
            val input = resolver.openInputStream(uri)
                ?: throw IOException("Document provider did not open the model")
            input.use { source ->
                temp.outputStream().buffered(128 * 1024).use { output ->
                    val bytes = ByteArray(128 * 1024)
                    while (true) {
                        val n = source.read(bytes)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_BYTES) {
                            throw IOException("GGUF exceeds the 1.5 GiB limit")
                        }
                        output.write(bytes, 0, n)
                    }
                }
            }
            if (!ModelValidation.validSize(copied)) {
                throw IOException("GGUF file is too small or too large")
            }
            if (!ModelValidation.hasMagic(temp.inputStream().use { it.readNBytes(4) })) {
                throw IOException("Not a GGUF file (invalid magic)")
            }
            // Swapping in a new imported model is only allowed once the previous
            // native model has been unloaded by the UI controller.
            if (activeFile.exists() && !activeFile.delete()) {
                throw IOException("Cannot remove previously imported model")
            }
            if (!temp.renameTo(activeFile)) {
                throw IOException("Unable to commit imported GGUF")
            }
            return activeFile
        } finally {
            temp.delete()
        }
    }

    fun delete(): Boolean = !activeFile.exists() || activeFile.delete()
}

object ModelValidation {
    fun hasMagic(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'G'.code.toByte() &&
            bytes[2] == 'U'.code.toByte() &&
            bytes[3] == 'F'.code.toByte()

    fun validSize(size: Long): Boolean =
        size in GgufModelStore.MIN_BYTES..GgufModelStore.MAX_BYTES
}

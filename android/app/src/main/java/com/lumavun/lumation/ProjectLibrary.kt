package com.lumavun.lumation

import java.io.File
import java.util.UUID
import org.json.JSONObject

data class ProjectEntry(val id: String, val title: String, val updatedAtMillis: Long)

/**
 * Multiple project slots, each backed by the tested atomic ProjectStore.
 * Names and IDs are always separate. No untrusted title ever becomes a path.
 *
 * Layout:
 * <root>/current/             legacy v0.4 saved project, not modified
 * <root>/library/<uuid>/current/...
 * <root>/library/<uuid>/meta.json
 */
class ProjectLibrary(private val root: File) {
    private val library get() = File(root, "library")
    private val legacyMarker get() = File(root, "legacy-imported")
    private val legacyId = "legacy-v04"

    private fun verifiedId(id: String): String {
        require(id.matches(Regex("[a-z0-9][a-z0-9-]{0,63}"))) { "Invalid project ID" }
        return id
    }

    private fun folder(id: String): File {
        val dir = File(library, verifiedId(id))
        require(dir.canonicalFile.parentFile == library.canonicalFile) {
            "Unsafe project path"
        }
        return dir
    }

    private fun metadata(entry: ProjectEntry): JSONObject = JSONObject()
        .put("version", 1)
        .put("id", entry.id)
        .put("title", entry.title)
        .put("updatedAtMillis", entry.updatedAtMillis)

    private fun readEntry(dir: File): ProjectEntry? {
        val file = File(dir, "meta.json")
        if (!file.isFile) return null
        if (file.length() !in 2L..32_768L) return null
        val data = try { JSONObject(file.readText(Charsets.UTF_8)) } catch (_: Exception) {
            return null
        }
        if (data.optInt("version", 0) != 1) return null
        val id = data.optString("id")
        if (id != dir.name) return null
        val title = data.optString("title").trim()
        if (title.isEmpty()) return null
        val updatedAt = data.optLong("updatedAtMillis", 0)
        if (updatedAt < 0) return null
        return ProjectEntry(id, title.take(64), updatedAt)
    }

    private fun writeEntry(dir: File, entry: ProjectEntry) {
        val temporary = File(dir, "meta.new")
        val target = File(dir, "meta.json")
        temporary.writeText(metadata(entry).toString(2), Charsets.UTF_8)
        // Renaming replaces an existing small metadata file on typical Android filesystems.
        if (!temporary.renameTo(target)) {
            target.delete()
            check(temporary.renameTo(target)) { "Unable to publish project metadata" }
        }
    }

    private fun saveRecord(
        id: String, title: String, document: ProjectDocument
    ): ProjectEntry {
        val dir = folder(id)
        val existed = dir.exists()
        if (!existed) check(dir.mkdirs()) { "Unable to create project directory" }
        try {
            ProjectStore(dir).save(document)
            val entry = ProjectEntry(id, title, System.currentTimeMillis())
            writeEntry(dir, entry)
            return entry
        } catch (e: Exception) {
            if (!existed) dir.deleteRecursively()
            throw e
        }
    }

    /**
     * v0.4 stored a single project at root/current. Import it into a stable
     * named slot once while leaving the source untouched as a backup.
     */
    private fun migrateLegacy() {
        if (legacyMarker.exists()) return
        val original = ProjectStore(root).load() ?: return
        if (readEntry(folder(legacyId)) == null) {
            saveRecord(legacyId, "Proyek lama v0.4", original)
        }
        legacyMarker.writeText("migrated", Charsets.UTF_8)
    }

    @Synchronized
    fun list(): List<ProjectEntry> {
        migrateLegacy()
        val projects = library.listFiles()?.asSequence().orEmpty()
        return projects.filter { it.isDirectory }
            .mapNotNull { readEntry(it) }
            .sortedWith(compareByDescending<ProjectEntry> { it.updatedAtMillis }.thenBy { it.id })
            .toList()
    }

    /**
     * id=null creates a new independent project; otherwise updates only
     * a known entry. All asset copies are private to that project.
     */
    @Synchronized
    fun save(document: ProjectDocument, title: String, id: String? = null): ProjectEntry {
        migrateLegacy()
        val name = title.trim().take(64)
        require(name.isNotEmpty()) { "Project name is required" }
        val projectId = id?.let(::verifiedId) ?: UUID.randomUUID().toString()
        if (id != null) require(readEntry(folder(projectId)) != null) { "Unknown project ID" }
        return saveRecord(projectId, name, document)
    }

    @Synchronized
    fun load(id: String): ProjectDocument? {
        migrateLegacy()
        val dir = folder(id)
        if (readEntry(dir) == null) return null
        return ProjectStore(dir).load()
    }

    @Synchronized
    fun delete(id: String): Boolean {
        migrateLegacy()
        val dir = folder(id)
        if (readEntry(dir) == null) return false
        check(dir.deleteRecursively()) { "Could not delete selected project" }
        return true
    }
}

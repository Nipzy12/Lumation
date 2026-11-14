package com.lumavun.lumation

import java.io.File

data class ProjectEntry(val id: String, val title: String, val updatedAtMillis: Long)

/** Storage for independent editable project snapshots. */
class ProjectLibrary(private val root: File) {
    fun list(): List<ProjectEntry> = emptyList()

    fun save(document: ProjectDocument, title: String, id: String? = null): ProjectEntry {
        throw UnsupportedOperationException("not implemented")
    }

    fun load(id: String): ProjectDocument? = null

    fun delete(id: String): Boolean = false
}

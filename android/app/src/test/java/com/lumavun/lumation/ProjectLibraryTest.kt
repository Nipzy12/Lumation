package com.lumavun.lumation

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectLibraryTest {
    @get:Rule val temp = TemporaryFolder()
    private fun media(name: String, data: String): File =
        temp.newFile(name).apply { writeText(data) }

    private fun document(name: String, contents: String): ProjectDocument =
        ProjectDocument(
            video = media(name, contents),
            videoName = name,
            durationMs = 6_000,
            trimStartMs = 500,
            trimEndMs = 5_000,
            sourceHasAudio = false,
            layers = MotionProjectState(text = "Text $name")
        )

    @Test fun independentlyStoresMultipleProjects() {
        val library = ProjectLibrary(File(temp.root, "projects"))
        val first = library.save(document("first.mp4", "AAAA"), "My first motion")
        val second = library.save(document("second.mp4", "BBBB"), "Second cut")
        assertNotEquals(first.id, second.id)
        assertEquals(2, library.list().size)
        assertEquals("AAAA", library.load(first.id)?.video?.readText())
        assertEquals("BBBB", library.load(second.id)?.video?.readText())
        assertEquals("Text first.mp4", library.load(first.id)?.layers?.text)
        assertEquals("My first motion", library.list().first { it.id == first.id }.title)
    }

    @Test fun savingSameIdOverwritesOnlySelectedProject() {
        val library = ProjectLibrary(File(temp.root, "projects"))
        val first = library.save(document("first.mp4", "OLD"), "First")
        val second = library.save(document("second.mp4", "OTHER"), "Other")
        val updated = library.save(document("revision.mp4", "NEW"), "First revised", first.id)
        assertEquals(first.id, updated.id)
        assertEquals(2, library.list().size)
        assertEquals("NEW", library.load(first.id)?.video?.readText())
        assertEquals("OTHER", library.load(second.id)?.video?.readText())
        assertEquals("First revised", library.list().first { it.id == first.id }.title)
    }

    @Test fun deleteOnlySelectedProject() {
        val library = ProjectLibrary(File(temp.root, "projects"))
        val first = library.save(document("a.mp4", "a"), "A")
        val second = library.save(document("b.mp4", "b"), "B")
        assertTrue(library.delete(first.id))
        assertNull(library.load(first.id))
        assertEquals("b", library.load(second.id)?.video?.readText())
        assertFalse(library.delete(first.id))
    }

    @Test fun importsLegacySingleSlotOnlyOnce() {
        val root = File(temp.root, "projects")
        ProjectStore(root).save(document("old.mp4", "legacy"))
        val library = ProjectLibrary(root)
        val imported = library.list()
        assertEquals(1, imported.size)
        assertEquals("legacy", library.load(imported.single().id)?.video?.readText())
        assertTrue(library.delete(imported.single().id))
        assertTrue(library.list().isEmpty())
        assertEquals("legacy", ProjectStore(root).load()?.video?.readText())
    }

    @Test fun rejectsPathTraversalAndInvalidUnknownId() {
        val library = ProjectLibrary(File(temp.root, "projects"))
        val entry = library.save(document("safe.mp4", "safe"), "Safe")
        assertEquals(null, library.load("not-present"))
        try {
            library.load("../outside")
            fail("Path traversal must be rejected")
        } catch (_: IllegalArgumentException) { }
        try {
            library.delete("../outside")
            fail("Path traversal must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals("safe", library.load(entry.id)?.video?.readText())
    }
}

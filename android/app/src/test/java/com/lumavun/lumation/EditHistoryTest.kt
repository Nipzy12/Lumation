package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion
import org.junit.Assert.*
import org.junit.Test

class EditHistoryTest {
    @Test fun restoresTextAndKeyframeChangesInOrder() {
        val original = LayerEditState()
        val history = EditHistory(original)
        val withText = original.copy(text = "Lumation")
        val withMotion = withText.copy(textMotion = LayerMotion(0f, 1f, 1f, 0f))
        history.commit(withText)
        history.commit(withMotion)
        assertTrue(history.canUndo)
        assertEquals(withText, history.undo())
        assertEquals(original, history.undo())
        assertFalse(history.canUndo)
        assertEquals(withText, history.redo())
        assertEquals(withMotion, history.redo())
        assertFalse(history.canRedo)
    }

    @Test fun redoBranchIsDiscardedByNewEdit() {
        val h = EditHistory(LayerEditState())
        h.commit(LayerEditState(text = "A"))
        h.commit(LayerEditState(text = "B"))
        assertEquals("A", h.undo().text)
        h.commit(LayerEditState(text = "C"))
        assertFalse(h.canRedo)
        assertEquals("C", h.current.text)
    }

    @Test fun duplicateCommitDoesNotCreateHistoryStep() {
        val h = EditHistory(LayerEditState())
        h.commit(LayerEditState())
        assertFalse(h.canUndo)
    }

    @Test fun capsUndoDepthAndResetClearsHistory() {
        val h = EditHistory(LayerEditState(), capacity = 2)
        (1..5).forEach { h.commit(LayerEditState(text = it.toString())) }
        assertEquals("4", h.undo().text)
        assertEquals("3", h.undo().text)
        assertFalse(h.canUndo)
        h.reset(LayerEditState(text = "loaded"))
        assertFalse(h.canRedo)
        assertFalse(h.canUndo)
        assertEquals("loaded", h.current.text)
    }
}

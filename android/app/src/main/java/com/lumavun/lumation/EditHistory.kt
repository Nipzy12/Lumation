package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion
import java.util.ArrayDeque

/** Undoable visual edit properties; media import and project selection are excluded. */
data class LayerEditState(
    val text: String = "",
    val textMotion: LayerMotion = LayerMotion(),
    val imageMotion: LayerMotion = LayerMotion(),
    val textScale: Int = 100,
    val imageScale: Int = 100
)

/** Bounded, in-memory undo/redo snapshots for immutable property values. */
class EditHistory<T>(initial: T, private val capacity: Int = 40) {
    init { require(capacity > 0) { "History capacity must be positive" } }

    private val past = ArrayDeque<T>()
    private val future = ArrayDeque<T>()

    var current: T = initial
        private set

    val canUndo: Boolean get() = past.isNotEmpty()
    val canRedo: Boolean get() = future.isNotEmpty()

    fun commit(next: T) {
        if (current == next) return
        if (past.size == capacity) past.removeFirst()
        past.addLast(current)
        current = next
        future.clear()
    }

    fun undo(): T {
        if (past.isNotEmpty()) {
            future.addLast(current)
            current = past.removeLast()
        }
        return current
    }

    fun redo(): T {
        if (future.isNotEmpty()) {
            if (past.size == capacity) past.removeFirst()
            past.addLast(current)
            current = future.removeLast()
        }
        return current
    }

    fun reset(value: T) {
        past.clear()
        future.clear()
        current = value
    }
}

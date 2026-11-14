package com.lumavun.lumation

import com.lumavun.lumation.ffmpeg.LayerMotion

/** Visual edit state. Media import and project selection are outside v0.6 undo history. */
data class LayerEditState(
    val text: String = "",
    val textMotion: LayerMotion = LayerMotion(),
    val imageMotion: LayerMotion = LayerMotion(),
    val textScale: Int = 100,
    val imageScale: Int = 100
)

/** Bounded undo/redo snapshots of immutable, small editing properties. */
class EditHistory<T>(initial: T, private val capacity: Int = 40) {
    var current: T = initial
        private set
    val canUndo: Boolean get() = false
    val canRedo: Boolean get() = false

    fun commit(next: T) {
        current = next
    }
    fun undo(): T = current
    fun redo(): T = current
    fun reset(value: T) {
        current = value
    }
}

package com.lumavun.lumation

/** Deterministic offline motion commands; this is not an AI or a language model. */
sealed class MotionInstruction {
    data class SetText(val value: String): MotionInstruction()
    data class Preset(val name: String): MotionInstruction()
    data class SetScale(val percent: Int): MotionInstruction()
}

object OfflineMotionCommands {
    fun parse(raw: String): MotionInstruction? = null
}

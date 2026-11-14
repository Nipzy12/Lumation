package com.lumavun.lumation

/**
 * Model output is untrusted; only a recognized single-line MotionInstruction
 * can be offered to the editor. No model text is executed directly.
 */
object AiSuggestion {
    fun commandFromModel(raw: String): String? {
        if (raw.isBlank() || raw.length > 1024 || raw.startsWith("ERROR:")) return null
        val lines = raw.lines().filter { it.isNotBlank() }
        if (lines.size != 1) return null
        val line = lines.single().trim()
        return line.takeIf { OfflineMotionCommands.parse(it) != null }
    }
}

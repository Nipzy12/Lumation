package com.lumavun.lumation

import java.util.Locale

/**
 * Local command interpreter: a deterministic parser, NOT a language model.
 * A future llama.cpp adapter can produce the same typed instructions.
 */
sealed class MotionInstruction {
    data class SetText(val value: String): MotionInstruction()
    data class Preset(val name: String): MotionInstruction()
    data class SetScale(val percent: Int): MotionInstruction()
}

interface MotionInstructionInterpreter {
    fun parse(raw: String): MotionInstruction?
}

object OfflineMotionCommands : MotionInstructionInterpreter {
    private val scalePattern = Regex("^(skala|scale)\\s+(\\d{2,3})%?$")

    override fun parse(raw: String): MotionInstruction? {
        if (raw.isBlank() || raw.length > 256) return null
        val clean = raw.trim()
        val normalized = clean.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

        val textPrefix = when {
            normalized.startsWith("teks:") -> 5
            normalized.startsWith("text:") -> 5
            else -> null
        }
        if (textPrefix != null) {
            val text = clean.substring(textPrefix).trim()
            if (text.isEmpty() || text.length > 64) return null
            return MotionInstruction.SetText(text)
        }

        scalePattern.matchEntire(normalized)?.let { match ->
            val n = match.groupValues[2].toIntOrNull() ?: return null
            if (n in 40..180) return MotionInstruction.SetScale(n)
            return null
        }
        val preset = when (normalized) {
            "geser kanan", "slide", "slide kanan", "move right" -> "slide"
            "geser kiri", "slide kiri", "move left" -> "slide-reverse"
            "gerak naik", "naik", "rise", "move up" -> "rise"
            "turun", "gerak turun", "fall", "move down" -> "fall"
            "diam", "static", "still", "tetap" -> "still"
            else -> null
        }
        return preset?.let(MotionInstruction::Preset)
    }
}

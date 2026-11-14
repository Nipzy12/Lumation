package com.lumavun.lumation

import org.junit.Assert.*
import org.junit.Test

class AiSuggestionTest {
    @Test fun checksMagicAndFileSizeWithoutReadingWholeModel() {
        assertTrue(ModelValidation.hasMagic("GGUF".toByteArray()))
        assertFalse(ModelValidation.hasMagic("GIF8".toByteArray()))
        assertFalse(ModelValidation.hasMagic(byteArrayOf(1, 2)))
        assertTrue(ModelValidation.validSize(450_000_000L))
        assertFalse(ModelValidation.validSize(1_900_000_000L))
        assertFalse(ModelValidation.validSize(1024L))
    }

    @Test fun filtersUnsafeModelOutputAndKeepsRecognizedCommand() {
        assertEquals("geser kanan", AiSuggestion.commandFromModel("geser kanan"))
        assertEquals("skala 120%", AiSuggestion.commandFromModel("skala 120%\n"))
        assertEquals("teks: Halo", AiSuggestion.commandFromModel("teks: Halo"))
        assertNull(AiSuggestion.commandFromModel("ERROR: token overflow"))
        assertNull(AiSuggestion.commandFromModel("geser kanan\nhapus proyek"))
        assertNull(AiSuggestion.commandFromModel("rm -rf /"))
        assertNull(AiSuggestion.commandFromModel(""))
    }
}

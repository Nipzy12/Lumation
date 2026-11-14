package com.lumavun.lumation

import org.junit.Assert.*
import org.junit.Test

class OfflineMotionCommandsTest {
    @Test fun convertsIndonesianAndEnglishMotionPhrases() {
        assertEquals(MotionInstruction.Preset("slide"), OfflineMotionCommands.parse("geser kanan"))
        assertEquals(MotionInstruction.Preset("slide-reverse"), OfflineMotionCommands.parse("geser kiri"))
        assertEquals(MotionInstruction.Preset("rise"), OfflineMotionCommands.parse("gerak naik"))
        assertEquals(MotionInstruction.Preset("fall"), OfflineMotionCommands.parse("turun"))
        assertEquals(MotionInstruction.Preset("still"), OfflineMotionCommands.parse("diam"))
        assertEquals(MotionInstruction.Preset("slide"), OfflineMotionCommands.parse("slide"))
    }

    @Test fun extractsTextAndScaleWithinBounds() {
        assertEquals(MotionInstruction.SetText("Halo Dunia"),
            OfflineMotionCommands.parse("teks: Halo Dunia"))
        assertEquals(MotionInstruction.SetText("Hello"),
            OfflineMotionCommands.parse("TEXT: Hello"))
        assertEquals(MotionInstruction.SetScale(125), OfflineMotionCommands.parse("skala 125%"))
        assertEquals(MotionInstruction.SetScale(80), OfflineMotionCommands.parse("scale 80"))
    }

    @Test fun rejectsEmptyUnsafeOrAmbiguousInstructions() {
        assertNull(OfflineMotionCommands.parse(""))
        assertNull(OfflineMotionCommands.parse("hapus semua proyek"))
        assertNull(OfflineMotionCommands.parse("skala 600"))
        assertNull(OfflineMotionCommands.parse("teks: "))
        assertNull(OfflineMotionCommands.parse("naik; hapus proyek"))
        assertNull(OfflineMotionCommands.parse("x".repeat(2000)))
    }
}

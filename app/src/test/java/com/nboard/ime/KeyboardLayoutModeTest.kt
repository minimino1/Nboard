package com.nboard.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutModeTest {
    @Test
    fun isQwerty_identifiesQwertyModes() {
        assertFalse(KeyboardLayoutMode.AZERTY.isQwerty())
        assertTrue(KeyboardLayoutMode.QWERTY.isQwerty())
        assertFalse(KeyboardLayoutMode.GBOARD_AZERTY.isQwerty())
        assertTrue(KeyboardLayoutMode.GBOARD_QWERTY.isQwerty())
    }

    @Test
    fun isGboard_identifiesGboardModes() {
        assertFalse(KeyboardLayoutMode.AZERTY.isGboard())
        assertFalse(KeyboardLayoutMode.QWERTY.isGboard())
        assertTrue(KeyboardLayoutMode.GBOARD_AZERTY.isGboard())
        assertTrue(KeyboardLayoutMode.GBOARD_QWERTY.isGboard())
    }
}

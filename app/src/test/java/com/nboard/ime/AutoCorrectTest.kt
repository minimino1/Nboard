package com.nboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoCorrectTest {
    private val englishFrequencies = mapOf(
        "the" to 10_000,
        "you" to 8_000,
        "i" to 7_500,
        "hello" to 6_000,
        "receive" to 5_000,
        "weird" to 4_000,
        "occurred" to 4_500,
        "is" to 9_000,
        "are" to 8_500,
        "have" to 8_200,
        "has" to 7_200
    )

    private val frenchFrequencies = mapOf(
        "je" to 9_000,
        "de" to 9_500,
        "bonjour" to 6_500,
        "merci" to 6_200,
        "toujours" to 4_800,
        "aujourd'hui" to 4_600,
        "est" to 7_000,
        "sont" to 5_200,
        "le" to 8_200,
        "la" to 8_000
    )

    private fun createEngine(mode: AutoCorrect.AutoCorrectMode): AutoCorrect {
        return AutoCorrect(
            frenchFrequencies = frenchFrequencies,
            englishFrequencies = englishFrequencies,
            mode = mode
        )
    }

    @Test
    fun englishCorrections_work() {
        val engine = createEngine(AutoCorrect.AutoCorrectMode.ENGLISH_ONLY)
        assertEquals("hello", engine.correct("helo", null))
        assertEquals("receive", engine.correct("recieve", null))
        assertEquals("the", engine.correct("teh", null))
        assertEquals("weird", engine.correct("wierd", null))
        assertEquals("occurred", engine.correct("occured", null))
    }

    @Test
    fun frenchCorrections_work() {
        val engine = createEngine(AutoCorrect.AutoCorrectMode.FRENCH_ONLY)
        assertEquals("bonjour", engine.correct("bonjur", null))
        assertEquals("merci", engine.correct("mersi", null))
        assertEquals("toujours", engine.correct("toujour", null))
        assertEquals("aujourd'hui", engine.correct("aujourdhui", null))
    }

    @Test
    fun bilingualContext_prefersLanguageIndicators() {
        val engine = createEngine(AutoCorrect.AutoCorrectMode.BILINGUAL)
        assertEquals("hello", engine.correct("helo", "I"))
        assertEquals("merci", engine.correct("mersi", "Je"))
    }

    @Test
    fun doesNotOverCorrectKnownWords_orInvalidInput() {
        val engine = createEngine(AutoCorrect.AutoCorrectMode.BILINGUAL)
        assertNull(engine.correct("hello", "i"))
        assertNull(engine.correct("bonjour", "je"))
        assertNull(engine.correct("", "je"))
        assertNull(engine.correct("a", "je"))
    }
}

package com.nboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BigramPredictorTest {
    private val englishUnigrams = mapOf(
        "we" to 9_000L,
        "with" to 8_000L,
        "was" to 7_800L,
        "want" to 7_000L,
        "will" to 6_900L,
        "would" to 6_800L,
        "go" to 6_700L,
        "get" to 6_600L,
        "give" to 6_500L
    )

    private val frenchUnigrams = mapOf(
        "vais" to 8_000L,
        "veux" to 7_900L,
        "voudrais" to 7_700L,
        "merci" to 7_200L
    )

    private val englishBigrams = mapOf(
        "i" to mapOf(
            "want" to 4_000L,
            "will" to 3_900L,
            "would" to 3_800L,
            "was" to 500L
        ),
        "to" to mapOf(
            "go" to 5_000L,
            "get" to 4_200L,
            "give" to 4_000L
        )
    )

    private val frenchBigrams = mapOf(
        "je" to mapOf(
            "vais" to 4_000L,
            "veux" to 3_900L,
            "voudrais" to 3_500L,
            "merci" to 400L
        )
    )

    private fun createPredictor(mode: BigramPredictor.PredictionMode): BigramPredictor {
        return BigramPredictor(
            frenchUnigrams = frenchUnigrams,
            englishUnigrams = englishUnigrams,
            frenchBigrams = frenchBigrams,
            englishBigrams = englishBigrams,
            mode = mode
        )
    }

    @Test
    fun englishContext_prefersBigrams() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.ENGLISH_ONLY)
        val predictions = predictor.predictWords("w", "I")
        assertEquals(listOf("want", "will", "would"), predictions)
    }

    @Test
    fun frenchContext_prefersBigrams() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.FRENCH_ONLY)
        val predictions = predictor.predictWords("v", "je")
        assertEquals(listOf("vais", "veux", "voudrais"), predictions)
    }

    @Test
    fun withoutContext_usesUnigrams() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.ENGLISH_ONLY)
        val predictions = predictor.predictWords("w", null)
        assertEquals(listOf("we", "with", "was"), predictions)
    }

    @Test
    fun fallbackToUnigrams_whenNoBigramMatch() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.ENGLISH_ONLY)
        val predictions = predictor.predictWords("g", "unknown")
        assertEquals(listOf("go", "get", "give"), predictions)
    }

    @Test
    fun bilingual_usesLanguageIndicators() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.BILINGUAL)
        assertEquals(listOf("want", "will", "would"), predictor.predictWords("w", "I"))
        assertEquals(listOf("vais", "veux", "voudrais"), predictor.predictWords("v", "je"))
    }

    @Test
    fun setModeFromKeyboardMode_updatesPredictionLanguage() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.BILINGUAL)

        predictor.setModeFromKeyboardMode(KeyboardLanguageMode.FRENCH)
        assertEquals(emptyList<String>(), predictor.predictWords("w", "I"))

        predictor.setModeFromKeyboardMode(KeyboardLanguageMode.ENGLISH)
        assertEquals(listOf("want", "will", "would"), predictor.predictWords("w", "I"))

        predictor.setModeFromKeyboardMode(KeyboardLanguageMode.DISABLED)
        assertEquals(listOf("vais", "veux", "voudrais"), predictor.predictWords("v", "je"))
    }

    @Test
    fun normalization_handlesUppercaseAndCurlyApostrophe() {
        val predictor = BigramPredictor(
            frenchUnigrams = emptyMap(),
            englishUnigrams = mapOf(
                "we're" to 100L,
                "well" to 90L,
                "west" to 80L
            ),
            frenchBigrams = emptyMap(),
            englishBigrams = emptyMap(),
            mode = BigramPredictor.PredictionMode.ENGLISH_ONLY
        )

        assertEquals(listOf("we're"), predictor.predictWords("WE’", null))
    }

    @Test
    fun longPrefix_returnsNoPredictions() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.ENGLISH_ONLY)
        assertEquals(emptyList<String>(), predictor.predictWords("a".repeat(25), null))
    }

    @Test
    fun exactPrefix_isFilteredOutFromResults() {
        val predictor = createPredictor(BigramPredictor.PredictionMode.ENGLISH_ONLY)
        val predictions = predictor.predictWords("we", null)
        assertTrue(predictions.isEmpty())
    }

    @Test
    fun bilingualWithoutHint_mergesByFrequencyAcrossLanguages() {
        val predictor = BigramPredictor(
            frenchUnigrams = mapOf(
                "alors" to 30L,
                "aller" to 20L
            ),
            englishUnigrams = mapOf(
                "alpha" to 10L,
                "also" to 8L
            ),
            frenchBigrams = emptyMap(),
            englishBigrams = emptyMap(),
            mode = BigramPredictor.PredictionMode.BILINGUAL
        )

        assertEquals(listOf("alors", "aller", "alpha"), predictor.predictWords("a", "unknown"))
    }
}

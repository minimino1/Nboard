package com.nboard.ime

import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCorrectPerformanceTest {
    @Test
    fun autocorrect_staysUnder50msPerWord() {
        val english = HashMap<String, Int>(25_000)
        val french = HashMap<String, Int>(25_000)

        for (index in 0 until 20_000) {
            english["word$index"] = 1 + (index % 4)
            french["mot$index"] = 1 + (index % 4)
        }

        english.putAll(
            mapOf(
                "hello" to 10_000,
                "receive" to 9_000,
                "the" to 20_000,
                "weird" to 8_000,
                "occurred" to 7_500
            )
        )
        french.putAll(
            mapOf(
                "bonjour" to 10_000,
                "merci" to 9_000,
                "toujours" to 8_500,
                "aujourd'hui" to 8_000
            )
        )

        val engine = AutoCorrect(
            frenchFrequencies = french,
            englishFrequencies = english,
            mode = AutoCorrect.AutoCorrectMode.BILINGUAL
        )

        val probes = listOf(
            "helo" to "i",
            "recieve" to "i",
            "teh" to "you",
            "wierd" to "is",
            "occured" to "was",
            "bonjur" to "je",
            "mersi" to "je",
            "toujour" to "je",
            "aujourdhui" to "je"
        )

        // Warm up JVM/JIT and cache paths.
        repeat(2) {
            probes.forEach { (word, previousWord) ->
                engine.correct(word, previousWord)
            }
        }

        var maxMs = 0L
        repeat(5) {
            probes.forEach { (word, previousWord) ->
                val startNanos = System.nanoTime()
                engine.correct(word, previousWord)
                val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000L
                if (elapsedMs > maxMs) {
                    maxMs = elapsedMs
                }
            }
        }

        println("Autocorrect benchmark max=${maxMs}ms")
        assertTrue("Autocorrect exceeded 50ms (max=${maxMs}ms)", maxMs < 50L)
    }
}

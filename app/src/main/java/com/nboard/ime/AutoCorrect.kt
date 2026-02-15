package com.nboard.ime

import android.content.Context
import java.util.LinkedHashMap
import java.util.Locale

private enum class BilingualLanguageHint {
    FRENCH,
    ENGLISH,
    UNKNOWN
}

class AutoCorrect(
    private val context: Context?,
    private var mode: AutoCorrectMode = AutoCorrectMode.BILINGUAL,
    private val frenchAssetPath: String = "dictionaries/french_50k.txt",
    private val englishAssetPath: String = "dictionaries/english_50k.txt",
    private val maxEditDistance: Int = 1,
    private val cacheCapacity: Int = 1200
) {
    enum class AutoCorrectMode {
        FRENCH_ONLY,
        ENGLISH_ONLY,
        BILINGUAL
    }

    private data class FrequencyDictionary(
        val frequencies: Map<String, Int>,
        val trie: DictionaryTrie
    ) {
        fun frequency(word: String): Int = trie.frequency(word) ?: 0

        fun contains(word: String): Boolean = trie.contains(word)

        companion object {
            fun empty() = FrequencyDictionary(emptyMap(), DictionaryTrie())
        }
    }

    private data class RankedCandidate(
        val word: String,
        val frequency: Int,
        val editDistance: Int
    )

    private val lock = Any()

    @Volatile
    private var loaded = false

    private var frenchDictionary: FrequencyDictionary = FrequencyDictionary.empty()
    private var englishDictionary: FrequencyDictionary = FrequencyDictionary.empty()

    private val correctionCache = object : LinkedHashMap<String, String?>(cacheCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean {
            return size > cacheCapacity
        }
    }

    internal constructor(
        frenchFrequencies: Map<String, Int>,
        englishFrequencies: Map<String, Int>,
        mode: AutoCorrectMode = AutoCorrectMode.BILINGUAL,
        maxEditDistance: Int = 1,
        cacheCapacity: Int = 1200
    ) : this(
        context = null,
        mode = mode,
        frenchAssetPath = "",
        englishAssetPath = "",
        maxEditDistance = maxEditDistance,
        cacheCapacity = cacheCapacity
    ) {
        synchronized(lock) {
            frenchDictionary = buildFrequencyDictionary(normalizeFrequencyMap(frenchFrequencies))
            englishDictionary = buildFrequencyDictionary(normalizeFrequencyMap(englishFrequencies))
            correctionCache.clear()
            loaded = true
        }
    }

    fun preload() {
        ensureLoaded()
    }

    fun setMode(newMode: AutoCorrectMode) {
        synchronized(lock) {
            if (mode == newMode) {
                return
            }
            mode = newMode
            correctionCache.clear()
        }
    }

    fun setModeFromKeyboardMode(languageMode: KeyboardLanguageMode) {
        val resolved = when (languageMode) {
            KeyboardLanguageMode.FRENCH -> AutoCorrectMode.FRENCH_ONLY
            KeyboardLanguageMode.ENGLISH -> AutoCorrectMode.ENGLISH_ONLY
            KeyboardLanguageMode.BOTH -> AutoCorrectMode.BILINGUAL
            KeyboardLanguageMode.DISABLED -> AutoCorrectMode.BILINGUAL
        }
        setMode(resolved)
    }

    fun correct(word: String, previousWord: String?): String? {
        ensureLoaded()

        val normalizedWord = normalizeWord(word)
        if (normalizedWord.length < MIN_WORD_LENGTH || normalizedWord.length > MAX_WORD_LENGTH) {
            return null
        }

        val activeMode = synchronized(lock) { mode }
        val hint = languageHint(previousWord)
        val cacheKey = "$activeMode|$hint|${normalizeWord(previousWord.orEmpty())}|$normalizedWord"

        synchronized(lock) {
            if (correctionCache.containsKey(cacheKey)) {
                return correctionCache[cacheKey]
            }
        }

        val suggestion = when (activeMode) {
            AutoCorrectMode.FRENCH_ONLY ->
                findBestSuggestion(normalizedWord, frenchDictionary, FRENCH_ALPHABET)

            AutoCorrectMode.ENGLISH_ONLY ->
                findBestSuggestion(normalizedWord, englishDictionary, ENGLISH_ALPHABET)

            AutoCorrectMode.BILINGUAL ->
                correctBilingual(normalizedWord, hint)
        }

        val result = suggestion?.takeIf { it != normalizedWord }
        synchronized(lock) {
            correctionCache[cacheKey] = result
        }
        return result
    }

    private fun correctBilingual(word: String, hint: BilingualLanguageHint): String? {
        if (frenchDictionary.contains(word) || englishDictionary.contains(word)) {
            return null
        }

        return when (hint) {
            BilingualLanguageHint.FRENCH -> {
                findBestSuggestion(word, frenchDictionary, FRENCH_ALPHABET)
                    ?: findBestSuggestion(word, englishDictionary, ENGLISH_ALPHABET)
            }

            BilingualLanguageHint.ENGLISH -> {
                findBestSuggestion(word, englishDictionary, ENGLISH_ALPHABET)
                    ?: findBestSuggestion(word, frenchDictionary, FRENCH_ALPHABET)
            }

            BilingualLanguageHint.UNKNOWN -> {
                val french = findBestCandidate(word, frenchDictionary, FRENCH_ALPHABET)
                val english = findBestCandidate(word, englishDictionary, ENGLISH_ALPHABET)
                when {
                    french == null -> english?.word
                    english == null -> french.word
                    french.frequency == english.frequency -> {
                        if (french.editDistance <= english.editDistance) french.word else english.word
                    }

                    french.frequency > english.frequency -> french.word
                    else -> english.word
                }
            }
        }?.takeIf { it != word }
    }

    private fun findBestSuggestion(
        word: String,
        dictionary: FrequencyDictionary,
        alphabet: CharArray
    ): String? {
        if (dictionary.contains(word)) {
            return null
        }
        return findBestCandidate(word, dictionary, alphabet)?.word
    }

    private fun findBestCandidate(
        word: String,
        dictionary: FrequencyDictionary,
        alphabet: CharArray
    ): RankedCandidate? {
        var best: RankedCandidate? = null
        edits1(word, alphabet).forEach { candidate ->
            val frequency = dictionary.frequency(candidate)
            if (frequency <= 0) {
                return@forEach
            }
            val ranked = RankedCandidate(
                word = candidate,
                frequency = frequency,
                editDistance = 1
            )
            if (isBetterCandidate(ranked, best)) {
                best = ranked
            }
        }

        if (best != null) {
            return best
        }

        // Distance-2 is intentionally disabled for real-time keyboard performance.
        if (maxEditDistance <= 1) {
            return null
        }

        return null
    }

    private fun isBetterCandidate(incoming: RankedCandidate, current: RankedCandidate?): Boolean {
        if (current == null) {
            return true
        }
        if (incoming.frequency != current.frequency) {
            return incoming.frequency > current.frequency
        }
        if (incoming.editDistance != current.editDistance) {
            return incoming.editDistance < current.editDistance
        }
        if (incoming.word.length != current.word.length) {
            return incoming.word.length < current.word.length
        }
        return incoming.word < current.word
    }

    private fun edits1(word: String, alphabet: CharArray): Set<String> {
        if (word.isBlank() || word.length > MAX_WORD_LENGTH) {
            return emptySet()
        }

        val edits = LinkedHashSet<String>(word.length * (alphabet.size * 2 + 8))

        // Deletions.
        for (index in word.indices) {
            val candidate = word.removeRange(index, index + 1)
            if (candidate.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
                edits.add(candidate)
            }
        }

        // Transpositions.
        for (index in 0 until word.length - 1) {
            val chars = word.toCharArray()
            val current = chars[index]
            chars[index] = chars[index + 1]
            chars[index + 1] = current
            edits.add(String(chars))
        }

        // Replacements.
        for (index in word.indices) {
            val chars = word.toCharArray()
            alphabet.forEach { letter ->
                if (letter == chars[index]) {
                    return@forEach
                }
                chars[index] = letter
                edits.add(String(chars))
            }
        }

        // Insertions.
        for (index in 0..word.length) {
            val left = word.substring(0, index)
            val right = word.substring(index)
            alphabet.forEach { letter ->
                val candidate = left + letter + right
                if (candidate.length in MIN_WORD_LENGTH..MAX_WORD_LENGTH) {
                    edits.add(candidate)
                }
            }
        }

        return edits
    }

    private fun languageHint(previousWord: String?): BilingualLanguageHint {
        val normalized = normalizeWord(previousWord.orEmpty())
        if (normalized.isBlank()) {
            return BilingualLanguageHint.UNKNOWN
        }
        if (FRENCH_INDICATORS.contains(normalized)) {
            return BilingualLanguageHint.FRENCH
        }
        if (ENGLISH_INDICATORS.contains(normalized)) {
            return BilingualLanguageHint.ENGLISH
        }
        return BilingualLanguageHint.UNKNOWN
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }

        synchronized(lock) {
            if (loaded) {
                return
            }

            frenchDictionary = loadFrequencyDictionary(frenchAssetPath)
            englishDictionary = loadFrequencyDictionary(englishAssetPath)
            correctionCache.clear()
            loaded = true
        }
    }

    private fun loadFrequencyDictionary(path: String): FrequencyDictionary {
        val appContext = context ?: return FrequencyDictionary.empty()
        return try {
            val map = HashMap<String, Int>(60_000)
            appContext.assets.open(path).bufferedReader().useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isBlank()) {
                        return@forEach
                    }

                    val parts = line.split(WHITESPACE_REGEX)
                    if (parts.isEmpty()) {
                        return@forEach
                    }

                    val (wordPart, frequencyPart) = when {
                        parts.size >= 3 && parts[0].all(Char::isDigit) -> parts[1] to parts[2]
                        parts.size >= 2 -> parts[0] to parts[1]
                        else -> parts[0] to "1"
                    }

                    val word = normalizeWord(wordPart)
                    if (word.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH || !WORD_PATTERN.matches(word)) {
                        return@forEach
                    }

                    val frequency = frequencyPart
                        .toLongOrNull()
                        ?.coerceAtLeast(1L)
                        ?.coerceAtMost(Int.MAX_VALUE.toLong())
                        ?.toInt()
                        ?: 1
                    val existing = map[word] ?: 0
                    map[word] = maxOf(existing, frequency)
                }
            }
            buildFrequencyDictionary(map)
        } catch (_: Exception) {
            FrequencyDictionary.empty()
        }
    }

    private fun buildFrequencyDictionary(frequencies: Map<String, Int>): FrequencyDictionary {
        if (frequencies.isEmpty()) {
            return FrequencyDictionary.empty()
        }

        val trie = DictionaryTrie()
        frequencies.forEach { (word, frequency) ->
            trie.insert(word, frequency)
        }
        return FrequencyDictionary(
            frequencies = frequencies,
            trie = trie
        )
    }

    private fun normalizeWord(value: String): String {
        return value
            .trim()
            .replace('’', '\'')
            .lowercase(Locale.US)
    }

    companion object {
        private const val MIN_WORD_LENGTH = 2
        private const val MAX_WORD_LENGTH = 24

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val WORD_PATTERN = Regex("[a-zàâäæçéèêëîïôöùûüÿœ'\\-]{2,24}")

        private val ENGLISH_ALPHABET = "abcdefghijklmnopqrstuvwxyz'".toCharArray()
        private val FRENCH_ALPHABET = "abcdefghijklmnopqrstuvwxyzàâäæçéèêëîïôöùûüÿœ'".toCharArray()

        private val FRENCH_INDICATORS = setOf(
            "je", "tu", "il", "elle", "nous", "vous", "le", "la", "les", "un", "une", "des",
            "de", "du", "à", "au", "et", "est", "sont"
        )

        private val ENGLISH_INDICATORS = setOf(
            "i", "you", "he", "she", "we", "they", "the", "a", "an", "is", "are", "was", "were",
            "have", "has"
        )

        private fun normalizeFrequencyMap(raw: Map<String, Int>): Map<String, Int> {
            if (raw.isEmpty()) {
                return emptyMap()
            }

            val normalized = HashMap<String, Int>(raw.size)
            raw.forEach { (wordRaw, freqRaw) ->
                val word = wordRaw.trim().replace('’', '\'').lowercase(Locale.US)
                if (word.length !in MIN_WORD_LENGTH..MAX_WORD_LENGTH || !WORD_PATTERN.matches(word)) {
                    return@forEach
                }
                val frequency = freqRaw.coerceAtLeast(1)
                val existing = normalized[word] ?: 0
                normalized[word] = maxOf(existing, frequency)
            }
            return normalized
        }
    }
}

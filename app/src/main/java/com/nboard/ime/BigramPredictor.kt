package com.nboard.ime

import android.content.Context
import java.util.LinkedHashMap
import java.util.Locale

private enum class BigramLanguageHint {
    FRENCH,
    ENGLISH,
    UNKNOWN
}

class BigramPredictor(
    private val context: Context?,
    private var mode: PredictionMode = PredictionMode.BILINGUAL,
    private val frenchUnigramAssetPath: String = "dictionaries/french_50k.txt",
    private val englishUnigramAssetPath: String = "dictionaries/english_50k.txt",
    private val frenchBigramAssetPath: String = "dictionaries/french_bigrams.txt",
    private val englishBigramAssetPath: String = "dictionaries/english_bigrams.txt",
    private val cacheCapacity: Int = 600
) {
    enum class PredictionMode {
        FRENCH_ONLY,
        ENGLISH_ONLY,
        BILINGUAL
    }

    private data class FrequencyEntry(
        val word: String,
        val frequency: Long
    )

    private data class LanguageModel(
        val unigrams: Map<String, Long>,
        val topUnigrams: List<FrequencyEntry>,
        val topUnigramsByFirst: Map<Char, List<FrequencyEntry>>,
        val bigramsByPrevious: Map<String, List<FrequencyEntry>>
    ) {
        fun topUnigramMatches(prefix: String, limit: Int): List<FrequencyEntry> {
            val normalizedPrefix = normalizeToken(prefix)
            val source = if (normalizedPrefix.isBlank()) {
                topUnigrams
            } else {
                topUnigramsByFirst[normalizedPrefix.first()].orEmpty()
            }

            if (source.isEmpty()) {
                return emptyList()
            }

            val results = ArrayList<FrequencyEntry>(limit)
            source.forEach { entry ->
                if (results.size >= limit) {
                    return@forEach
                }
                if (normalizedPrefix.isBlank() || entry.word.startsWith(normalizedPrefix)) {
                    results.add(entry)
                }
            }
            return results
        }

        fun topBigramMatches(previousWord: String, prefix: String, limit: Int): List<FrequencyEntry> {
            val previous = normalizeToken(previousWord)
            if (previous.isBlank()) {
                return emptyList()
            }

            val normalizedPrefix = normalizeToken(prefix)
            val source = bigramsByPrevious[previous].orEmpty()
            if (source.isEmpty()) {
                return emptyList()
            }

            val results = ArrayList<FrequencyEntry>(limit)
            source.forEach { entry ->
                if (results.size >= limit) {
                    return@forEach
                }
                if (normalizedPrefix.isBlank() || entry.word.startsWith(normalizedPrefix)) {
                    results.add(entry)
                }
            }
            return results
        }

        companion object {
            fun empty() = LanguageModel(
                unigrams = emptyMap(),
                topUnigrams = emptyList(),
                topUnigramsByFirst = emptyMap(),
                bigramsByPrevious = emptyMap()
            )
        }
    }

    private val lock = Any()

    @Volatile
    private var loaded = false

    private var frenchModel = LanguageModel.empty()
    private var englishModel = LanguageModel.empty()

    private val predictionCache = object : LinkedHashMap<String, List<String>>(cacheCapacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean {
            return size > cacheCapacity
        }
    }

    internal constructor(
        frenchUnigrams: Map<String, Long>,
        englishUnigrams: Map<String, Long>,
        frenchBigrams: Map<String, Map<String, Long>>,
        englishBigrams: Map<String, Map<String, Long>>,
        mode: PredictionMode = PredictionMode.BILINGUAL,
        cacheCapacity: Int = 600
    ) : this(
        context = null,
        mode = mode,
        frenchUnigramAssetPath = "",
        englishUnigramAssetPath = "",
        frenchBigramAssetPath = "",
        englishBigramAssetPath = "",
        cacheCapacity = cacheCapacity
    ) {
        synchronized(lock) {
            frenchModel = buildLanguageModel(normalizeFrequencyMap(frenchUnigrams), normalizeBigramMap(frenchBigrams))
            englishModel = buildLanguageModel(normalizeFrequencyMap(englishUnigrams), normalizeBigramMap(englishBigrams))
            predictionCache.clear()
            loaded = true
        }
    }

    fun preload() {
        ensureLoaded()
    }

    fun setMode(newMode: PredictionMode) {
        synchronized(lock) {
            if (mode == newMode) {
                return
            }
            mode = newMode
            predictionCache.clear()
        }
    }

    fun setModeFromKeyboardMode(languageMode: KeyboardLanguageMode) {
        val resolved = when (languageMode) {
            KeyboardLanguageMode.FRENCH -> PredictionMode.FRENCH_ONLY
            KeyboardLanguageMode.ENGLISH -> PredictionMode.ENGLISH_ONLY
            KeyboardLanguageMode.BOTH -> PredictionMode.BILINGUAL
            KeyboardLanguageMode.DISABLED -> PredictionMode.BILINGUAL
        }
        setMode(resolved)
    }

    fun predictWords(currentInput: String, previousWord: String?): List<String> {
        ensureLoaded()

        val prefix = normalizeToken(currentInput)
        if (prefix.length > MAX_INPUT_LENGTH) {
            return emptyList()
        }

        val normalizedPrevious = normalizeToken(previousWord.orEmpty())
        val hint = languageHint(normalizedPrevious)
        val activeMode = synchronized(lock) { mode }
        val cacheKey = "$activeMode|$hint|$normalizedPrevious|$prefix"

        synchronized(lock) {
            predictionCache[cacheKey]?.let { return it }
        }

        val predictions = when (activeMode) {
            PredictionMode.FRENCH_ONLY -> predictSingleLanguage(
                model = frenchModel,
                prefix = prefix,
                previousWord = normalizedPrevious
            )

            PredictionMode.ENGLISH_ONLY -> predictSingleLanguage(
                model = englishModel,
                prefix = prefix,
                previousWord = normalizedPrevious
            )

            PredictionMode.BILINGUAL -> predictBilingual(
                prefix = prefix,
                previousWord = normalizedPrevious,
                hint = hint
            )
        }

        synchronized(lock) {
            predictionCache[cacheKey] = predictions
        }
        return predictions
    }

    private fun predictBilingual(
        prefix: String,
        previousWord: String,
        hint: BigramLanguageHint
    ): List<String> {
        return when (hint) {
            BigramLanguageHint.FRENCH -> {
                mergePredictionLists(
                    primary = predictSingleLanguage(frenchModel, prefix, previousWord),
                    secondary = predictSingleLanguage(englishModel, prefix, previousWord),
                    limit = MAX_PREDICTIONS
                )
            }

            BigramLanguageHint.ENGLISH -> {
                mergePredictionLists(
                    primary = predictSingleLanguage(englishModel, prefix, previousWord),
                    secondary = predictSingleLanguage(frenchModel, prefix, previousWord),
                    limit = MAX_PREDICTIONS
                )
            }

            BigramLanguageHint.UNKNOWN -> {
                if (previousWord.isNotBlank()) {
                    val combinedBigram = mergeByFrequency(
                        frenchModel.topBigramMatches(previousWord, prefix, MAX_LANGUAGE_CANDIDATES),
                        englishModel.topBigramMatches(previousWord, prefix, MAX_LANGUAGE_CANDIDATES),
                        MAX_PREDICTIONS
                    )
                    if (combinedBigram.isNotEmpty()) {
                        return combinedBigram
                    }
                }

                mergeByFrequency(
                    frenchModel.topUnigramMatches(prefix, MAX_LANGUAGE_CANDIDATES),
                    englishModel.topUnigramMatches(prefix, MAX_LANGUAGE_CANDIDATES),
                    MAX_PREDICTIONS
                )
            }
        }
    }

    private fun predictSingleLanguage(
        model: LanguageModel,
        prefix: String,
        previousWord: String
    ): List<String> {
        if (model.unigrams.isEmpty()) {
            return emptyList()
        }

        val bigramMatches = if (previousWord.isBlank()) {
            emptyList()
        } else {
            model.topBigramMatches(previousWord, prefix, MAX_LANGUAGE_CANDIDATES)
        }

        val entries = if (bigramMatches.isNotEmpty()) {
            bigramMatches
        } else {
            model.topUnigramMatches(prefix, MAX_LANGUAGE_CANDIDATES)
        }

        return entries
            .map { it.word }
            .filter { candidate ->
                prefix.isBlank() || (candidate != prefix && candidate.startsWith(prefix))
            }
            .distinct()
            .take(MAX_PREDICTIONS)
    }

    private fun mergeByFrequency(
        first: List<FrequencyEntry>,
        second: List<FrequencyEntry>,
        limit: Int
    ): List<String> {
        if (first.isEmpty() && second.isEmpty()) {
            return emptyList()
        }

        val merged = HashMap<String, Long>(first.size + second.size)
        first.forEach { entry ->
            val existing = merged[entry.word]
            if (existing == null || entry.frequency > existing) {
                merged[entry.word] = entry.frequency
            }
        }
        second.forEach { entry ->
            val existing = merged[entry.word]
            if (existing == null || entry.frequency > existing) {
                merged[entry.word] = entry.frequency
            }
        }

        return merged.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key.length }
                    .thenBy { it.key }
            )
            .map { it.key }
            .take(limit)
    }

    private fun mergePredictionLists(primary: List<String>, secondary: List<String>, limit: Int): List<String> {
        val merged = ArrayList<String>(limit)
        primary.forEach { word ->
            if (merged.size < limit && merged.none { it.equals(word, ignoreCase = true) }) {
                merged.add(word)
            }
        }
        secondary.forEach { word ->
            if (merged.size < limit && merged.none { it.equals(word, ignoreCase = true) }) {
                merged.add(word)
            }
        }
        return merged
    }

    private fun ensureLoaded() {
        if (loaded) {
            return
        }
        synchronized(lock) {
            if (loaded) {
                return
            }

            val assets = context?.assets
            if (assets == null) {
                frenchModel = LanguageModel.empty()
                englishModel = LanguageModel.empty()
                loaded = true
                return
            }

            val frenchUnigrams = loadFrequencyAsset(frenchUnigramAssetPath)
            val englishUnigrams = loadFrequencyAsset(englishUnigramAssetPath)
            val frenchBigrams = loadBigramAsset(frenchBigramAssetPath)
            val englishBigrams = loadBigramAsset(englishBigramAssetPath)

            frenchModel = buildLanguageModel(frenchUnigrams, frenchBigrams)
            englishModel = buildLanguageModel(englishUnigrams, englishBigrams)
            loaded = true
        }
    }

    private fun buildLanguageModel(
        unigramFrequencies: Map<String, Long>,
        bigramFrequencies: Map<String, Map<String, Long>>
    ): LanguageModel {
        if (unigramFrequencies.isEmpty() && bigramFrequencies.isEmpty()) {
            return LanguageModel.empty()
        }

        val topUnigrams = unigramFrequencies
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Long>> { it.value }
                    .thenBy { it.key.length }
                    .thenBy { it.key }
            )
            .map { FrequencyEntry(it.key, it.value) }

        val byFirst = HashMap<Char, MutableList<FrequencyEntry>>()
        topUnigrams.forEach { entry ->
            val first = entry.word.firstOrNull() ?: return@forEach
            byFirst.getOrPut(first) { mutableListOf() }.add(entry)
        }

        val sortedBigrams = HashMap<String, List<FrequencyEntry>>(bigramFrequencies.size)
        bigramFrequencies.forEach { (previous, nextMap) ->
            val sorted = nextMap.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Long>> { it.value }
                        .thenBy { it.key.length }
                        .thenBy { it.key }
                )
                .map { FrequencyEntry(it.key, it.value) }
            if (sorted.isNotEmpty()) {
                sortedBigrams[previous] = sorted
            }
        }

        return LanguageModel(
            unigrams = unigramFrequencies,
            topUnigrams = topUnigrams,
            topUnigramsByFirst = byFirst.mapValues { it.value.toList() },
            bigramsByPrevious = sortedBigrams
        )
    }

    private fun loadFrequencyAsset(path: String): Map<String, Long> {
        if (path.isBlank()) {
            return emptyMap()
        }

        return runCatching {
            context?.assets?.open(path)?.bufferedReader()?.useLines { lines ->
                val frequencies = HashMap<String, Long>()
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        return@forEach
                    }
                    val parts = trimmed.split(WHITESPACE_REGEX)
                    if (parts.size < 2) {
                        return@forEach
                    }
                    val word = normalizeToken(parts[0])
                    val frequency = parts[1].toLongOrNull() ?: return@forEach
                    if (word.length in 1..MAX_WORD_LENGTH && frequency > 0L) {
                        frequencies[word] = maxOf(frequencies[word] ?: 0L, frequency)
                    }
                }
                frequencies
            } ?: emptyMap()
        }.getOrElse { emptyMap() }
    }

    private fun loadBigramAsset(path: String): Map<String, Map<String, Long>> {
        if (path.isBlank()) {
            return emptyMap()
        }

        return runCatching {
            context?.assets?.open(path)?.bufferedReader()?.useLines { lines ->
                val bigrams = HashMap<String, MutableMap<String, Long>>()
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        return@forEach
                    }
                    val parts = trimmed.split(WHITESPACE_REGEX)
                    if (parts.size < 3) {
                        return@forEach
                    }
                    val previous = normalizeToken(parts[0])
                    val current = normalizeToken(parts[1])
                    val frequency = parts[2].toLongOrNull() ?: return@forEach
                    if (previous.length !in 1..MAX_WORD_LENGTH || current.length !in 1..MAX_WORD_LENGTH || frequency <= 0L) {
                        return@forEach
                    }
                    val currentMap = bigrams.getOrPut(previous) { HashMap() }
                    val existing = currentMap[current] ?: 0L
                    if (frequency > existing) {
                        currentMap[current] = frequency
                    }
                }
                bigrams.mapValues { it.value.toMap() }
            } ?: emptyMap()
        }.getOrElse { emptyMap() }
    }

    private fun languageHint(previousWord: String): BigramLanguageHint {
        if (previousWord.isBlank()) {
            return BigramLanguageHint.UNKNOWN
        }

        return when {
            FRENCH_INDICATORS.contains(previousWord) -> BigramLanguageHint.FRENCH
            ENGLISH_INDICATORS.contains(previousWord) -> BigramLanguageHint.ENGLISH
            else -> BigramLanguageHint.UNKNOWN
        }
    }

    companion object {
        private const val MAX_PREDICTIONS = 3
        private const val MAX_LANGUAGE_CANDIDATES = 120
        private const val MAX_WORD_LENGTH = 32
        private const val MAX_INPUT_LENGTH = 24
        private val WHITESPACE_REGEX = Regex("\\s+")

        private val FRENCH_INDICATORS = setOf(
            "je", "tu", "il", "elle", "nous", "vous", "le", "la", "les", "un", "une", "des",
            "de", "du", "à", "au", "et", "est", "sont"
        )
        private val ENGLISH_INDICATORS = setOf(
            "i", "you", "he", "she", "we", "they", "the", "a", "an", "is", "are", "was", "were",
            "have", "has"
        )

        private fun normalizeToken(raw: String): String {
            return raw.lowercase(Locale.US).replace('’', '\'')
        }

        private fun normalizeFrequencyMap(source: Map<String, Long>): Map<String, Long> {
            val normalized = HashMap<String, Long>(source.size)
            source.forEach { (word, frequency) ->
                val token = normalizeToken(word)
                if (token.length in 1..MAX_WORD_LENGTH && frequency > 0L) {
                    val existing = normalized[token] ?: 0L
                    if (frequency > existing) {
                        normalized[token] = frequency
                    }
                }
            }
            return normalized
        }

        private fun normalizeBigramMap(source: Map<String, Map<String, Long>>): Map<String, Map<String, Long>> {
            if (source.isEmpty()) {
                return emptyMap()
            }

            val normalized = HashMap<String, MutableMap<String, Long>>()
            source.forEach { (previous, currentMap) ->
                val normalizedPrevious = normalizeToken(previous)
                if (normalizedPrevious.length !in 1..MAX_WORD_LENGTH) {
                    return@forEach
                }

                val map = normalized.getOrPut(normalizedPrevious) { HashMap() }
                currentMap.forEach { (current, frequency) ->
                    val normalizedCurrent = normalizeToken(current)
                    if (normalizedCurrent.length !in 1..MAX_WORD_LENGTH || frequency <= 0L) {
                        return@forEach
                    }
                    val existing = map[normalizedCurrent] ?: 0L
                    if (frequency > existing) {
                        map[normalizedCurrent] = frequency
                    }
                }
            }
            return normalized.mapValues { it.value.toMap() }
        }
    }
}

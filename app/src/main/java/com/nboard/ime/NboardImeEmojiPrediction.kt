package com.nboard.ime

import android.content.Context
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.text.Editable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal fun NboardImeService.onEmojiChosen(emoji: String) {
    currentInputConnection?.commitText(emoji, 1)
    pendingAutoCorrection = null
    refreshAutoShiftFromContextAndRerender()
    recordEmojiUsage(emoji)
}

internal fun NboardImeService.filterEmojiCandidates(text: Editable?): List<String> {
    val query = text?.toString()?.trim().orEmpty().lowercase(Locale.US)
    return if (query.isBlank()) {
        allEmojiCatalog
    } else {
        allEmojiCatalog.filter { emoji ->
            emoji.contains(query) || emojiSearchBlob(emoji).contains(query)
        }
    }
}

internal fun NboardImeService.renderEmojiGrid() {
    if (!isEmojiGridInitialized()) {
        return
    }
    emojiRecentColumn.removeAllViews()

    val recentColumn = (emojiRecents + DEFAULT_TOP_EMOJIS)
        .distinct()
        .take(3)

    recentColumn.forEachIndexed { index, emoji ->
        emojiRecentColumn.addView(
            buildEmojiGridKey(
                emoji = emoji,
                widthDp = 52,
                heightDp = 42,
                marginEndDp = 0,
                marginBottomDp = if (index < recentColumn.lastIndex) 4 else 0
            )
        )
    }

    emojiRecentDivider.isVisible = recentColumn.isNotEmpty()

    if (emojiGridLoadedCount <= 0 ||
        emojiGridLoadedCount > allEmojiCatalog.size ||
        emojiGridRow1.childCount == 0 && emojiGridRow2.childCount == 0 && emojiGridRow3.childCount == 0
    ) {
        emojiGridRow1.removeAllViews()
        emojiGridRow2.removeAllViews()
        emojiGridRow3.removeAllViews()
        emojiGridLoadedCount = 0
        appendEmojiGridChunk(EMOJI_GRID_INITIAL_BATCH)
    }
}

internal fun NboardImeService.appendEmojiGridChunk(batchSize: Int) {
    if (!isEmojiGridInitialized() || batchSize <= 0) {
        return
    }
    if (emojiGridLoadedCount >= allEmojiCatalog.size) {
        return
    }

    val rows = arrayOf(emojiGridRow1, emojiGridRow2, emojiGridRow3)
    val end = (emojiGridLoadedCount + batchSize).coerceAtMost(allEmojiCatalog.size)
    for (index in emojiGridLoadedCount until end) {
        val emoji = allEmojiCatalog[index]
        rows[index % rows.size].addView(buildEmojiGridKey(emoji))
    }
    emojiGridLoadedCount = end
}

internal fun NboardImeService.buildEmojiGridKey(
    emoji: String,
    widthDp: Int = 52,
    heightDp: Int = 42,
    marginEndDp: Int = 4,
    marginBottomDp: Int = 0
): AppCompatButton {
    return AppCompatButton(this).apply {
        text = emoji
        setAllCaps(false)
        textSize = 18f
        background = uiDrawable(R.drawable.bg_key)
        setTextColor(uiColor(R.color.key_text))
        gravity = Gravity.CENTER
        flattenView(this)
        bindPressAction(this) { onEmojiChosen(emoji) }
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), dp(heightDp)).also {
            if (marginEndDp > 0) {
                it.marginEnd = dp(marginEndDp)
            }
            if (marginBottomDp > 0) {
                it.bottomMargin = dp(marginBottomDp)
            }
        }
    }
}

internal fun NboardImeService.renderEmojiSuggestions() {
    if (!isEmojiMostUsedRowInitialized()) {
        return
    }
    emojiMostUsedRow.removeAllViews()
    if (!isEmojiSearchMode) {
        return
    }

    val query = emojiSearchInput.text?.toString()?.trim().orEmpty()
    val candidates = if (query.isBlank()) {
        (emojiRecents + DEFAULT_TOP_EMOJIS).distinct()
    } else {
        filterEmojiCandidates(emojiSearchInput.text)
    }.take(MAX_EMOJI_SEARCH_SUGGESTIONS)

    candidates.forEachIndexed { index, emoji ->
        val key = AppCompatButton(this).apply {
            text = emoji
            setAllCaps(false)
            textSize = 19f
            background = uiDrawable(R.drawable.bg_key)
            gravity = Gravity.CENTER
            setTextColor(uiColor(R.color.key_text))
            flattenView(this)
            bindPressAction(this) { onEmojiChosen(emoji) }
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(42)).also {
                if (index < candidates.lastIndex) {
                    it.marginEnd = dp(6)
                }
            }
        }
        emojiMostUsedRow.addView(key)
    }
}

internal fun NboardImeService.emojiSearchBlob(emoji: String): String {
    return emojiSearchIndex.getOrPut(emoji) {
        buildString {
            append(EMOJI_KEYWORDS[emoji].orEmpty())
            var offset = 0
            while (offset < emoji.length) {
                val codePoint = emoji.codePointAt(offset)
                if (codePoint != 0x200D && codePoint != 0xFE0F) {
                    val name = Character.getName(codePoint)
                    if (!name.isNullOrBlank()) {
                        append(' ')
                        append(name.lowercase(Locale.US))
                    }
                }
                offset += Character.charCount(codePoint)
            }
        }
    }
}

internal fun NboardImeService.buildEmojiCatalog(): List<String> {
    val catalog = LinkedHashSet<String>()
    catalog.addAll(ALL_EMOJIS)

    EMOJI_SCAN_RANGES.forEach { range ->
        for (codePoint in range) {
            if (!Character.isValidCodePoint(codePoint) || !Character.isDefined(codePoint)) {
                continue
            }
            if (!isEmojiCodePoint(codePoint)) {
                continue
            }
            catalog.add(String(Character.toChars(codePoint)))
        }
    }

    Locale.getISOCountries().forEach { code ->
        if (code.length != 2) {
            return@forEach
        }
        val first = code[0].uppercaseChar()
        val second = code[1].uppercaseChar()
        if (first !in 'A'..'Z' || second !in 'A'..'Z') {
            return@forEach
        }

        val firstIndicator = 0x1F1E6 + (first.code - 'A'.code)
        val secondIndicator = 0x1F1E6 + (second.code - 'A'.code)
        catalog.add(String(intArrayOf(firstIndicator, secondIndicator), 0, 2))
    }

    catalog.add("🇪🇺")
    catalog.add("🇺🇳")
    catalog.addAll(KEYCAP_EMOJIS)
    return catalog.toList()
}

internal fun NboardImeService.isEmojiCodePoint(codePoint: Int): Boolean {
    return try {
        UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)
    } catch (_: Throwable) {
        false
    }
}

internal fun NboardImeService.preloadExtendedEmojiCatalog() {
    serviceScope.launch(Dispatchers.Default) {
        val extended = buildEmojiCatalog()
        launch(Dispatchers.Main) {
            if (extended.size <= allEmojiCatalog.size) {
                return@launch
            }
            allEmojiCatalog.clear()
            allEmojiCatalog.addAll(extended)
            emojiGridLoadedCount = 0
            if (isEmojiGridInitialized()) {
                renderEmojiGrid()
                if (isEmojiSearchMode) {
                    renderEmojiSuggestions()
                }
            }
        }
    }
}

internal fun NboardImeService.recordEmojiUsage(emoji: String) {
    emojiUsageCounts[emoji] = (emojiUsageCounts[emoji] ?: 0) + 1
    emojiRecents.remove(emoji)
    emojiRecents.addFirst(emoji)
    while (emojiRecents.size > MAX_RECENT_EMOJIS) {
        emojiRecents.removeLast()
    }
    saveEmojiUsage()
    if (isEmojiSearchMode) {
        renderEmojiSuggestions()
    }
}

internal fun NboardImeService.loadEmojiUsage() {
    val prefs = getSharedPreferences(KeyboardModeSettings.PREFS_NAME, Context.MODE_PRIVATE)

    val countsRaw = prefs.getString(KEY_EMOJI_COUNTS_JSON, null)
    if (!countsRaw.isNullOrBlank()) {
        try {
            val json = JSONObject(countsRaw)
            json.keys().forEach { key ->
                emojiUsageCounts[key] = json.optInt(key, 0)
            }
        } catch (_: Exception) {
            emojiUsageCounts.clear()
        }
    }

    val recentsRaw = prefs.getString(KEY_EMOJI_RECENTS_JSON, null)
    if (!recentsRaw.isNullOrBlank()) {
        try {
            val array = JSONArray(recentsRaw)
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) {
                    emojiRecents.add(value)
                }
            }
        } catch (_: Exception) {
            emojiRecents.clear()
        }
    }
}

internal fun NboardImeService.saveEmojiUsage() {
    val countsJson = JSONObject().apply {
        emojiUsageCounts.forEach { (emoji, count) -> put(emoji, count) }
    }
    val recentsJson = JSONArray().apply {
        emojiRecents.forEach { put(it) }
    }

    getSharedPreferences(KeyboardModeSettings.PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_EMOJI_COUNTS_JSON, countsJson.toString())
        .putString(KEY_EMOJI_RECENTS_JSON, recentsJson.toString())
        .apply()
}

internal fun NboardImeService.renderPredictionRow() {
    if (!isPredictionRowInitialized()) {
        return
    }
    if (!shouldShowPredictionRow()) {
        setPredictionWords(emptyList())
        return
    }

    val inputConnection = currentInputConnection ?: run {
        setPredictionWords(emptyList())
        return
    }
    val beforeCursor = inputConnection
        .getTextBeforeCursor(PREDICTION_CONTEXT_WINDOW, 0)
        ?.toString()
        .orEmpty()
    val sentenceContext = extractPredictionSentenceContext(beforeCursor)
    val fragment = extractCurrentWordFragment(beforeCursor)
    val normalizedFragment = normalizeWord(fragment)
    val contextLanguage = detectContextLanguage(sentenceContext.ifBlank { beforeCursor })
    val previousWord = extractPreviousWordForPrediction(sentenceContext, fragment)

    val bigramPredictions = if (isBigramPredictorInitialized()) {
        bigramPredictor.predictWords(
            currentInput = normalizedFragment,
            previousWord = previousWord
        )
    } else {
        emptyList()
    }

    val localPredictions = mergePrimaryPredictionCandidates(
        primary = bigramPredictions,
        fallback = buildWordPredictions(
            prefix = normalizedFragment,
            previousWord = previousWord,
            contextLanguage = contextLanguage,
            sentenceContext = sentenceContext
        )
    )

    val predictions = localPredictions
        .map { candidate ->
            if (fragment.isBlank()) {
                if (isAutoShiftEnabled) {
                    candidate.replaceFirstChar { it.uppercase(Locale.US) }
                } else {
                    candidate
                }
            } else {
                applyWordCase(candidate, fragment)
            }
        }
        .filter { it.isNotBlank() && !it.equals(fragment, ignoreCase = true) }
        .distinctBy { it.lowercase(Locale.US) }
        .take(MAX_PREDICTION_CANDIDATES)

    setPredictionWords(predictions)
}

internal fun NboardImeService.mergePrimaryPredictionCandidates(primary: List<String>, fallback: List<String>): List<String> {
    if (primary.isEmpty()) {
        return fallback.take(MAX_PREDICTION_CANDIDATES)
    }
    val merged = ArrayList<String>(MAX_PREDICTION_CANDIDATES)
    primary.forEach { candidate ->
        if (merged.size < MAX_PREDICTION_CANDIDATES && merged.none { it.equals(candidate, ignoreCase = true) }) {
            merged.add(candidate)
        }
    }
    fallback.forEach { candidate ->
        if (merged.size < MAX_PREDICTION_CANDIDATES && merged.none { it.equals(candidate, ignoreCase = true) }) {
            merged.add(candidate)
        }
    }
    return merged
}

internal fun NboardImeService.setPredictionWords(words: List<String>) {
    val slotValues = when (words.size) {
        0 -> listOf("", "", "")
        1 -> listOf("", words[0], "")
        2 -> listOf(words[1], words[0], "")
        else -> listOf(words[1], words[0], words[2])
    }
    val slots = listOf(predictionWord1Button, predictionWord2Button, predictionWord3Button)
    slots.forEachIndexed { index, button ->
        val value = slotValues.getOrNull(index).orEmpty()
        button.text = value
        button.isEnabled = value.isNotBlank()
        button.alpha = if (value.isNotBlank()) 1f else 0f
    }
    predictionSeparator1.alpha = if (slotValues[0].isNotBlank() && slotValues[1].isNotBlank()) 0.9f else 0f
    predictionSeparator2.alpha = if (slotValues[2].isNotBlank() && slotValues[1].isNotBlank()) 0.9f else 0f
    hasPredictionSuggestions = words.isNotEmpty()
}

internal fun NboardImeService.shouldShowPredictionRow(): Boolean {
    if (!wordPredictionEnabled) {
        return false
    }
    if (isAiMode || isClipboardOpen || isEmojiMode || isNumbersMode || isGenerating) {
        return false
    }
    return !shouldShowRecentClipboardRow()
}

internal fun NboardImeService.buildWordPredictions(
    prefix: String,
    previousWord: String?,
    contextLanguage: KeyboardLanguageMode?,
    sentenceContext: String
): List<String> {
    val normalizedPrefix = normalizeWord(prefix)
    val foldedPrefix = foldWord(normalizedPrefix)
    val rawSentenceTokens = extractPredictionTokens(sentenceContext)
    val sentenceTokens = if (
        normalizedPrefix.isNotBlank() &&
        rawSentenceTokens.lastOrNull() == normalizedPrefix
    ) {
        rawSentenceTokens.dropLast(1)
    } else {
        rawSentenceTokens
    }
    val fallback = defaultPredictionWords(contextLanguage)

    val scored = HashMap<String, Int>()
    val (previousTwoFromContext, previousOneFromContext) =
        extractPreviousWordsForPrediction(sentenceContext, prefix)
    val normalizedPrevious = previousWord?.let(::normalizeWord)?.takeIf { it.length >= 2 }
        ?: previousOneFromContext

    if (!previousTwoFromContext.isNullOrBlank() && !normalizedPrevious.isNullOrBlank()) {
        addContextualTrigramCandidates(previousTwoFromContext, normalizedPrevious, foldedPrefix, scored)
    }
    if (normalizedPrevious != null) {
        addContextualBigramCandidates(normalizedPrevious, foldedPrefix, scored)
    }
    addSentenceContextBigramCandidates(sentenceTokens, foldedPrefix, scored)
    addStaticContextHintCandidates(
        previousTwo = previousTwoFromContext,
        previousOne = normalizedPrevious,
        foldedPrefix = foldedPrefix,
        contextLanguage = contextLanguage,
        scored = scored
    )
    addLearnedWordCandidates(foldedPrefix, scored)

    listOf(
        KeyboardLanguageMode.FRENCH to frenchLexicon,
        KeyboardLanguageMode.ENGLISH to englishLexicon
    ).forEach { (language, lexicon) ->
        if (!isLanguageEnabled(language)) {
            return@forEach
        }

        val source = when {
            foldedPrefix.isBlank() -> staticLanguageFallbackWords(language, contextLanguage)
            foldedPrefix.length >= 2 -> lexicon.byPrefix2[foldedPrefix.take(2)].orEmpty()
            else -> lexicon.byFirst[foldedPrefix.first()].orEmpty()
        }
        if (source.isEmpty()) {
            return@forEach
        }

        val languagePenalty = languageBiasPenalty(language, contextLanguage) * 26
        var scanned = 0
        source.forEach { candidateWord ->
            if (scanned >= WORD_PREDICTION_SCAN_LIMIT) {
                return@forEach
            }
            scanned++

            addPredictionCandidate(
                scored = scored,
                candidateWord = candidateWord,
                foldedPrefix = foldedPrefix,
                language = language,
                languagePenalty = languagePenalty,
                previousWord = normalizedPrevious
            )
        }
    }

    val ranked = scored
        .entries
        .sortedWith(
            compareBy<Map.Entry<String, Int>> { it.value }
                .thenBy { it.key.length }
                .thenBy { it.key }
        )
        .map { it.key }
        .filterNot { candidate ->
            foldedPrefix.isBlank() &&
                !normalizedPrevious.isNullOrBlank() &&
                candidate.equals(normalizedPrevious, ignoreCase = true)
        }
        .toMutableList()

    fallback.forEach { candidate ->
        if (ranked.size >= MAX_PREDICTION_CANDIDATES) {
            return@forEach
        }
        if (candidate.equals(normalizedPrefix, ignoreCase = true)) {
            return@forEach
        }
        if (ranked.none { it.equals(candidate, ignoreCase = true) }) {
            ranked.add(candidate)
        }
    }

    return ranked.take(MAX_PREDICTION_CANDIDATES)
}

internal fun NboardImeService.staticLanguageFallbackWords(
    language: KeyboardLanguageMode,
    contextLanguage: KeyboardLanguageMode?
): List<String> {
    return when (language) {
        KeyboardLanguageMode.FRENCH ->
            if (contextLanguage == KeyboardLanguageMode.ENGLISH) FRENCH_DEFAULT_PREDICTIONS.take(4) else FRENCH_DEFAULT_PREDICTIONS
        KeyboardLanguageMode.ENGLISH ->
            if (contextLanguage == KeyboardLanguageMode.FRENCH) ENGLISH_DEFAULT_PREDICTIONS.take(4) else ENGLISH_DEFAULT_PREDICTIONS
        KeyboardLanguageMode.BOTH -> MIXED_DEFAULT_PREDICTIONS
        KeyboardLanguageMode.DISABLED -> MIXED_DEFAULT_PREDICTIONS
    }
}

internal fun NboardImeService.addContextualTrigramCandidates(
    previousTwo: String,
    previousOne: String,
    foldedPrefix: String,
    scored: MutableMap<String, Int>
) {
    val prefix = "${normalizeWord(previousTwo)}|${normalizeWord(previousOne)}|"
    learnedTrigramFrequency
        .asSequence()
        .filter { (key, _) -> key.startsWith(prefix) }
        .sortedByDescending { it.value }
        .take(MAX_PREDICTION_TRIGRAM_CANDIDATES)
        .forEach { (key, count) ->
            val candidate = key.substringAfterLast('|')
            if (candidate.isBlank()) {
                return@forEach
            }
            val foldedCandidate = foldWord(candidate)
            if (foldedPrefix.isNotBlank() && !foldedCandidate.startsWith(foldedPrefix)) {
                return@forEach
            }
            val score = 72 - minOf(MAX_PREDICTION_TRIGRAM_BOOST, count * 24)
            val existing = scored[candidate]
            if (existing == null || score < existing) {
                scored[candidate] = score
            }
        }
}

internal fun NboardImeService.addSentenceContextBigramCandidates(
    sentenceTokens: List<String>,
    foldedPrefix: String,
    scored: MutableMap<String, Int>
) {
    if (sentenceTokens.isEmpty()) {
        return
    }

    sentenceTokens
        .takeLast(PREDICTION_CONTEXT_CHAIN_WINDOW)
        .reversed()
        .forEachIndexed { distance, contextWord ->
            val keyPrefix = "${normalizeWord(contextWord)}|"
            val distancePenalty = distance * 22
            learnedBigramFrequency
                .asSequence()
                .filter { (key, _) -> key.startsWith(keyPrefix) }
                .sortedByDescending { it.value }
                .take(MAX_PREDICTION_CONTEXT_CHAIN_CANDIDATES)
                .forEach { (key, count) ->
                    val candidate = key.substringAfter('|')
                    if (candidate.isBlank()) {
                        return@forEach
                    }
                    val foldedCandidate = foldWord(candidate)
                    if (foldedPrefix.isNotBlank() && !foldedCandidate.startsWith(foldedPrefix)) {
                        return@forEach
                    }
                    val score = 128 + distancePenalty - minOf(MAX_PREDICTION_BIGRAM_BOOST, count * 18)
                    val existing = scored[candidate]
                    if (existing == null || score < existing) {
                        scored[candidate] = score
                    }
                }
        }
}

internal fun NboardImeService.addStaticContextHintCandidates(
    previousTwo: String?,
    previousOne: String?,
    foldedPrefix: String,
    contextLanguage: KeyboardLanguageMode?,
    scored: MutableMap<String, Int>
) {
    val hints = staticContextHintWords(previousTwo, previousOne, contextLanguage)
    hints.forEachIndexed { index, candidate ->
        if (candidate.length < 2) {
            return@forEachIndexed
        }
        val foldedCandidate = foldWord(candidate)
        if (foldedPrefix.isNotBlank() && !foldedCandidate.startsWith(foldedPrefix)) {
            return@forEachIndexed
        }
        val score = 170 + index * 6
        val existing = scored[candidate]
        if (existing == null || score < existing) {
            scored[candidate] = score
        }
    }
}

internal fun NboardImeService.staticContextHintWords(
    previousTwo: String?,
    previousOne: String?,
    contextLanguage: KeyboardLanguageMode?
): List<String> {
    val keys = mutableListOf<String>()
    if (!previousTwo.isNullOrBlank() && !previousOne.isNullOrBlank()) {
        keys += "${normalizeWord(previousTwo)} ${normalizeWord(previousOne)}"
    }
    if (!previousOne.isNullOrBlank()) {
        keys += normalizeWord(previousOne)
    }

    val candidateLanguages = when (keyboardLanguageMode) {
        KeyboardLanguageMode.FRENCH -> listOf(KeyboardLanguageMode.FRENCH)
        KeyboardLanguageMode.ENGLISH -> listOf(KeyboardLanguageMode.ENGLISH)
        KeyboardLanguageMode.BOTH -> when (contextLanguage) {
            KeyboardLanguageMode.FRENCH -> listOf(KeyboardLanguageMode.FRENCH, KeyboardLanguageMode.ENGLISH)
            KeyboardLanguageMode.ENGLISH -> listOf(KeyboardLanguageMode.ENGLISH, KeyboardLanguageMode.FRENCH)
            else -> listOf(KeyboardLanguageMode.FRENCH, KeyboardLanguageMode.ENGLISH)
        }
        KeyboardLanguageMode.DISABLED -> listOf(KeyboardLanguageMode.FRENCH, KeyboardLanguageMode.ENGLISH)
    }

    val results = LinkedHashSet<String>()
    candidateLanguages.forEach { language ->
        val source = when (language) {
            KeyboardLanguageMode.FRENCH -> FRENCH_CONTEXT_HINTS
            KeyboardLanguageMode.ENGLISH -> ENGLISH_CONTEXT_HINTS
            KeyboardLanguageMode.BOTH -> emptyMap<String, List<String>>()
            KeyboardLanguageMode.DISABLED -> emptyMap<String, List<String>>()
        }
        keys.forEach { key ->
            source[key]?.forEach { results.add(it) }
        }
        source["*"]?.forEach { results.add(it) }
    }
    return results.take(24)
}

internal fun NboardImeService.addPredictionCandidate(
    scored: MutableMap<String, Int>,
    candidateWord: String,
    foldedPrefix: String,
    language: KeyboardLanguageMode?,
    languagePenalty: Int,
    previousWord: String?
) {
    val normalizedCandidate = normalizeWord(candidateWord)
    if (normalizedCandidate.length < 2) {
        return
    }
    val foldedCandidate = foldWord(normalizedCandidate)
    if (foldedPrefix.isNotBlank()) {
        if (!foldedCandidate.startsWith(foldedPrefix) || foldedCandidate == foldedPrefix) {
            return
        }
    }

    val suffixGap = (foldedCandidate.length - foldedPrefix.length).coerceAtLeast(0)
    var score = languagePenalty + suffixGap * 4 + normalizedCandidate.length * 2
    if (foldedPrefix.isBlank()) {
        // For next-word prediction, prioritize contextual models over generic lexicon fallback.
        score += 220
    }

    val unigram = learnedWordFrequency[normalizedCandidate] ?: 0
    if (unigram > 0) {
        score -= minOf(MAX_PREDICTION_UNIGRAM_BOOST, unigram * 8)
    }

    if (!previousWord.isNullOrBlank()) {
        val bigram = learnedBigramFrequency[predictionBigramKey(previousWord, normalizedCandidate)] ?: 0
        if (bigram > 0) {
            score -= minOf(MAX_PREDICTION_BIGRAM_BOOST, bigram * 18)
        }
    }

    if (language == KeyboardLanguageMode.FRENCH && FRENCH_WORDS.contains(normalizedCandidate)) {
        score -= 10
    }
    if (language == KeyboardLanguageMode.ENGLISH && ENGLISH_WORDS.contains(normalizedCandidate)) {
        score -= 10
    }

    val existing = scored[normalizedCandidate]
    if (existing == null || score < existing) {
        scored[normalizedCandidate] = score
    }
}

internal fun NboardImeService.addContextualBigramCandidates(
    previousWord: String,
    foldedPrefix: String,
    scored: MutableMap<String, Int>
) {
    val prefix = "$previousWord|"
    learnedBigramFrequency
        .asSequence()
        .filter { (key, _) -> key.startsWith(prefix) }
        .sortedByDescending { it.value }
        .take(MAX_PREDICTION_BIGRAM_CANDIDATES)
        .forEach { (key, count) ->
            val candidate = key.substringAfter('|')
            if (candidate.isBlank()) {
                return@forEach
            }
            val foldedCandidate = foldWord(candidate)
            if (foldedPrefix.isNotBlank() && !foldedCandidate.startsWith(foldedPrefix)) {
                return@forEach
            }
            val score = 120 - minOf(MAX_PREDICTION_BIGRAM_BOOST, count * 20)
            val existing = scored[candidate]
            if (existing == null || score < existing) {
                scored[candidate] = score
            }
        }
}

internal fun NboardImeService.addLearnedWordCandidates(foldedPrefix: String, scored: MutableMap<String, Int>) {
    learnedWordFrequency
        .asSequence()
        .filter { (word, _) ->
            if (foldedPrefix.isBlank()) {
                true
            } else {
                foldWord(word).startsWith(foldedPrefix)
            }
        }
        .sortedByDescending { it.value }
        .take(MAX_PREDICTION_LEARNED_CANDIDATES)
        .forEach { (word, count) ->
            val base = 180 - minOf(MAX_PREDICTION_UNIGRAM_BOOST, count * 10)
            val existing = scored[word]
            if (existing == null || base < existing) {
                scored[word] = base
            }
        }
}

internal fun NboardImeService.defaultPredictionWords(contextLanguage: KeyboardLanguageMode?): List<String> {
    return when (keyboardLanguageMode) {
        KeyboardLanguageMode.FRENCH -> FRENCH_DEFAULT_PREDICTIONS
        KeyboardLanguageMode.ENGLISH -> ENGLISH_DEFAULT_PREDICTIONS
        KeyboardLanguageMode.BOTH -> when (contextLanguage) {
            KeyboardLanguageMode.FRENCH -> FRENCH_DEFAULT_PREDICTIONS + ENGLISH_DEFAULT_PREDICTIONS
            KeyboardLanguageMode.ENGLISH -> ENGLISH_DEFAULT_PREDICTIONS + FRENCH_DEFAULT_PREDICTIONS
            else -> MIXED_DEFAULT_PREDICTIONS
        }
        KeyboardLanguageMode.DISABLED -> MIXED_DEFAULT_PREDICTIONS
    }
}

internal fun NboardImeService.extractPreviousWordForPrediction(beforeCursor: String, currentFragment: String): String? {
    return extractPreviousWordsForPrediction(beforeCursor, currentFragment).second
}

internal fun NboardImeService.extractPreviousWordsForPrediction(
    beforeCursor: String,
    currentFragment: String
): Pair<String?, String?> {
    if (beforeCursor.isBlank()) {
        return null to null
    }
    val reduced = if (
        currentFragment.isNotBlank() &&
        beforeCursor.endsWith(currentFragment, ignoreCase = true)
    ) {
        beforeCursor.dropLast(currentFragment.length)
    } else {
        beforeCursor
    }
    val tokens = extractPredictionTokens(reduced)
    if (tokens.isEmpty()) {
        return null to null
    }
    val previous1 = tokens.lastOrNull()
    val previous2 = if (tokens.size >= 2) tokens[tokens.lastIndex - 1] else null
    return previous2 to previous1
}

internal fun NboardImeService.extractPredictionSentenceContext(beforeCursor: String): String {
    if (beforeCursor.isBlank()) {
        return ""
    }
    val lastBoundary = maxOf(
        beforeCursor.lastIndexOf('.'),
        beforeCursor.lastIndexOf('!'),
        beforeCursor.lastIndexOf('?'),
        beforeCursor.lastIndexOf('\n')
    )
    val startIndex = if (lastBoundary >= 0) lastBoundary + 1 else 0
    return beforeCursor.substring(startIndex).trimStart()
}

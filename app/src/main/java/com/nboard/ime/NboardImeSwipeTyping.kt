package com.nboard.ime

import android.os.SystemClock
import android.view.KeyEvent
import android.view.View

internal fun NboardImeService.moveCursorLeft() {
    val inputConnection = currentInputConnection ?: return
    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
}

internal fun NboardImeService.moveCursorRight() {
    val inputConnection = currentInputConnection ?: return
    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
    inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
}

internal fun NboardImeService.shouldHandleSwipeTyping(): Boolean {
    if (!swipeTypingEnabled) {
        return false
    }
    if (isNumbersMode || isEmojiMode || isClipboardOpen || isAiMode || isGenerating || isVoiceListening || isVoiceStopping) {
        return false
    }
    return true
}

internal fun NboardImeService.isVoiceInputLongPressAvailable(): Boolean {
    if (!voiceInputEnabled) {
        return false
    }
    if (isNumbersMode || isEmojiMode || isClipboardOpen || isAiMode || isGenerating) {
        return false
    }
    return currentInputConnection != null
}

internal fun NboardImeService.beginSwipeTyping(anchorView: View, token: String, rawX: Float, rawY: Float): Boolean {
    if (token.isBlank()) {
        return false
    }
    if (!shouldHandleSwipeTyping()) {
        return false
    }
    val now = SystemClock.elapsedRealtime()
    activeSwipeTypingSession = SwipeTypingSession(
        ownerView = anchorView,
        rawStartX = rawX,
        rawStartY = rawY,
        tokens = mutableListOf(token),
        dwellDurationsMs = mutableListOf(0L),
        trailPoints = mutableListOf(),
        lastTokenEnteredAtMs = now,
        isSwiping = false
    )
    if (swipeTrailEnabled) {
        appendSwipeTrailPoint(rawX, rawY, force = true)
    } else if (isSwipeTrailViewInitialized()) {
        swipeTrailView.fadeOutTrail()
    }
    return true
}

internal fun NboardImeService.cancelSwipeTyping() {
    if (isSwipeTrailViewInitialized()) {
        swipeTrailView.fadeOutTrail()
    }
    activeSwipeTypingSession = null
}

internal fun NboardImeService.updateSwipeTyping(rawX: Float, rawY: Float): Boolean {
    val session = activeSwipeTypingSession ?: return false
    if (!shouldHandleSwipeTyping()) {
        cancelSwipeTyping()
        return false
    }
    val dx = rawX - session.rawStartX
    val dy = rawY - session.rawStartY
    val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (!session.isSwiping && distance >= dp(SWIPE_TYPING_DEADZONE_DP).toFloat()) {
        session.isSwiping = true
    }
    if (!session.isSwiping) {
        return false
    }

    appendSwipeTrailPoint(rawX, rawY, force = false)

    val token = findSwipeTokenAt(rawX, rawY) ?: return true
    val last = session.tokens.lastOrNull()
    if (token != last) {
        val now = SystemClock.elapsedRealtime()
        val lastIndex = session.dwellDurationsMs.lastIndex
        if (lastIndex >= 0) {
            val delta = (now - session.lastTokenEnteredAtMs).coerceAtLeast(0L)
            session.dwellDurationsMs[lastIndex] = session.dwellDurationsMs[lastIndex] + delta
        }
        session.tokens.add(token)
        session.dwellDurationsMs.add(0L)
        session.lastTokenEnteredAtMs = now
        performKeyHaptic(session.ownerView)
    }
    return true
}

internal fun NboardImeService.finishSwipeTypingAndCommit(): Boolean {
    val session = activeSwipeTypingSession ?: return false
    activeSwipeTypingSession = null
    if (isSwipeTrailViewInitialized()) {
        swipeTrailView.fadeOutTrail()
    }
    if (!session.isSwiping) {
        return false
    }
    val now = SystemClock.elapsedRealtime()
    val lastIndex = session.dwellDurationsMs.lastIndex
    if (lastIndex >= 0) {
        val delta = (now - session.lastTokenEnteredAtMs).coerceAtLeast(0L)
        session.dwellDurationsMs[lastIndex] = session.dwellDurationsMs[lastIndex] + delta
    }

    val intentTokens = extractSwipeIntentTokens(session)
    if (intentTokens.size < 2) {
        return false
    }
    val resolved = resolveSwipeWord(intentTokens, session).orEmpty()
    if (resolved.isBlank()) {
        return false
    }
    commitSwipeWord(resolved)
    return true
}

internal fun NboardImeService.appendSwipeTrailPoint(rawX: Float, rawY: Float, force: Boolean) {
    if (!swipeTrailEnabled) {
        return
    }
    val session = activeSwipeTypingSession ?: return
    if (!isKeyRowsContainerInitialized() || !isSwipeTrailViewInitialized()) {
        return
    }
    val location = IntArray(2)
    keyRowsContainer.getLocationOnScreen(location)
    val localX = rawX - location[0]
    val localY = rawY - location[1]
    if (!force) {
        val last = session.trailPoints.lastOrNull()
        if (last != null) {
            val dx = localX - last.x
            val dy = localY - last.y
            val minDistance = dp(SWIPE_TRAIL_MIN_STEP_DP).toFloat()
            if ((dx * dx + dy * dy) < (minDistance * minDistance)) {
                return
            }
        }
    }
    session.trailPoints.add(
        SwipeTrailView.TrailPoint(
            x = localX,
            y = localY,
            timestampMs = SystemClock.elapsedRealtime()
        )
    )
    if (session.trailPoints.size > SWIPE_TRAIL_MAX_POINTS) {
        session.trailPoints.removeAt(0)
    }
    swipeTrailView.updateTrail(session.trailPoints)
}

internal fun NboardImeService.findSwipeTokenAt(rawX: Float, rawY: Float): String? {
    swipeLetterKeyByView.forEach { (view, token) ->
        if (!view.isShown || view.width <= 0 || view.height <= 0) {
            return@forEach
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val left = location[0].toFloat()
        val top = location[1].toFloat()
        val right = left + view.width
        val bottom = top + view.height
        if (rawX in left..right && rawY in top..bottom) {
            return token
        }
    }
    return null
}

internal fun NboardImeService.extractSwipeIntentTokens(session: SwipeTypingSession): List<String> {
    if (session.tokens.isEmpty()) {
        return emptyList()
    }
    val reduced = mutableListOf<String>()
    val lastIndex = session.tokens.lastIndex
    session.tokens.forEachIndexed { index, rawToken ->
        val token = normalizeWord(rawToken)
        if (token.length != 1 || !token.first().isLetter()) {
            return@forEachIndexed
        }
        val dwell = session.dwellDurationsMs.getOrNull(index) ?: 0L
        val keep = index == 0 || index == lastIndex || dwell >= SWIPE_DWELL_COMMIT_MS
        if (keep) {
            if (reduced.lastOrNull() != token) {
                reduced.add(token)
            }
        }
    }

    if (reduced.size < 3 && session.tokens.size >= 3) {
        val middleRange = 1 until session.tokens.lastIndex
        val bestMiddle = middleRange
            .maxByOrNull { session.dwellDurationsMs.getOrNull(it) ?: 0L }
            ?.let { session.tokens[it] }
            ?.let(::normalizeWord)
            ?.takeIf { it.length == 1 && it.first().isLetter() }
        if (!bestMiddle.isNullOrBlank()) {
            val first = reduced.firstOrNull()
            val last = reduced.lastOrNull()
            if (first != null && last != null && bestMiddle != first && bestMiddle != last) {
                reduced.clear()
                reduced.add(first)
                reduced.add(bestMiddle)
                reduced.add(last)
            }
        }
    }
    return reduced
}

internal fun NboardImeService.resolveSwipeWord(tokens: List<String>, session: SwipeTypingSession): String? {
    if (tokens.isEmpty()) {
        return null
    }
    val normalizedPath = tokens
        .map { normalizeWord(it) }
        .filter { it.length == 1 && it.first().isLetter() }
        .joinToString("")
    if (normalizedPath.length < 2) {
        return null
    }

    val foldedPath = foldWord(normalizedPath)
    val collapsedPath = collapseRepeats(foldedPath, maxRepeat = 1)
    val pathFirst = collapsedPath.firstOrNull() ?: return null
    val pathLast = collapsedPath.lastOrNull() ?: return null

    val inputConnection = currentInputConnection
    val beforeCursor = inputConnection
        ?.getTextBeforeCursor(PREDICTION_CONTEXT_WINDOW, 0)
        ?.toString()
        .orEmpty()
    val sentenceContext = extractPredictionSentenceContext(beforeCursor)
    val (previousWord2, previousWord1) = extractPreviousWordsForPrediction(sentenceContext, "")
    val contextLanguage = detectContextLanguage(beforeCursor)

    val candidates = LinkedHashSet<String>()
    learnedWordFrequency.entries
        .asSequence()
        .filter { (word, _) -> word.firstOrNull() == pathFirst }
        .sortedByDescending { it.value }
        .take(SWIPE_LEARNED_SCAN_LIMIT)
        .forEach { (word, _) -> candidates.add(word) }

    listOf(
        KeyboardLanguageMode.FRENCH to frenchLexicon,
        KeyboardLanguageMode.ENGLISH to englishLexicon
    ).forEach { (language, lexicon) ->
        if (!isLanguageEnabled(language)) {
            return@forEach
        }
        lexicon.byFirst[pathFirst]
            .orEmpty()
            .asSequence()
            .take(SWIPE_LEXICON_SCAN_LIMIT)
            .forEach { candidates.add(it) }
    }

    var bestWord: String? = null
    var bestScore = Int.MAX_VALUE
    var secondBestScore = Int.MAX_VALUE

    candidates.forEach { candidate ->
        val normalizedCandidate = normalizeWord(candidate)
        if (normalizedCandidate.length < 2) {
            return@forEach
        }
        val foldedCandidate = foldWord(normalizedCandidate)
        if (foldedCandidate.isBlank() || foldedCandidate.firstOrNull() != pathFirst) {
            return@forEach
        }

        val collapsedCandidate = collapseRepeats(foldedCandidate, maxRepeat = 1)
        val distanceLimit = (SWIPE_DISTANCE_BASE_LIMIT + collapsedPath.length / 2).coerceAtMost(10)
        val shapeDistance = levenshteinDistanceBounded(collapsedPath, collapsedCandidate, distanceLimit)
        if (shapeDistance == Int.MAX_VALUE) {
            return@forEach
        }

        var score = shapeDistance * 14
        score += kotlin.math.abs(collapsedCandidate.length - collapsedPath.length) * 3
        val rawDistanceLimit = (distanceLimit + 2).coerceAtMost(12)
        val rawDistance = levenshteinDistanceBounded(foldedPath, foldedCandidate, rawDistanceLimit)
        score += if (rawDistance == Int.MAX_VALUE) 28 else rawDistance * 7
        score += swipeBigramMismatchPenalty(collapsedPath, collapsedCandidate)
        score -= commonPrefixLength(collapsedPath, collapsedCandidate) * 3
        if (collapsedCandidate.lastOrNull() != pathLast) {
            score += 12
        }
        if (!isSubsequence(collapsedPath, collapsedCandidate)) {
            score += 16
        }

        val dominantMiddle = dominantSwipeMiddleToken(session)
        if (!dominantMiddle.isNullOrBlank() && !collapsedCandidate.contains(dominantMiddle)) {
            score += 8
        }

        val unigram = learnedWordFrequency[normalizedCandidate] ?: 0
        if (unigram > 0) {
            score -= minOf(90, unigram * 8)
        }

        if (!previousWord1.isNullOrBlank()) {
            val bigram = learnedBigramFrequency[predictionBigramKey(previousWord1, normalizedCandidate)] ?: 0
            if (bigram > 0) {
                score -= minOf(120, bigram * 20)
            }
        }
        if (!previousWord2.isNullOrBlank() && !previousWord1.isNullOrBlank()) {
            val trigram = learnedTrigramFrequency[
                predictionTrigramKey(previousWord2, previousWord1, normalizedCandidate)
            ] ?: 0
            if (trigram > 0) {
                score -= minOf(170, trigram * 24)
            }
        }

        if (FRENCH_WORDS.contains(normalizedCandidate) || ENGLISH_WORDS.contains(normalizedCandidate)) {
            score -= 6
        }
        detectWordLanguage(normalizedCandidate)?.let { language ->
            score += languageBiasPenalty(language, contextLanguage) * 6
        }

        if (score < bestScore) {
            secondBestScore = bestScore
            bestScore = score
            bestWord = normalizedCandidate
        } else if (score < secondBestScore) {
            secondBestScore = score
        }
    }

    if (!bestWord.isNullOrBlank()) {
        val margin = secondBestScore - bestScore
        val confident = when {
            bestScore <= SWIPE_CONFIDENT_SCORE -> true
            margin >= SWIPE_MIN_SCORE_MARGIN -> true
            else -> false
        }
        if (confident) {
            return bestWord
        }
    }
    return null
}

internal fun NboardImeService.dominantSwipeMiddleToken(session: SwipeTypingSession): String? {
    if (session.tokens.size < 3) {
        return null
    }
    val middleRange = 1 until session.tokens.lastIndex
    val bestMiddleIndex = middleRange.maxByOrNull { index ->
        session.dwellDurationsMs.getOrNull(index) ?: 0L
    } ?: return null
    return normalizeWord(session.tokens[bestMiddleIndex])
        .takeIf { it.length == 1 && it.first().isLetter() }
}

internal fun NboardImeService.swipeBigramMismatchPenalty(path: String, candidate: String): Int {
    if (path.length < 2 || candidate.length < 2) {
        return 0
    }
    var penalty = 0
    for (index in 0 until path.lastIndex) {
        val from = path[index]
        val to = path[index + 1]
        val firstPos = candidate.indexOf(from)
        if (firstPos < 0) {
            penalty += 6
            continue
        }
        val secondPos = candidate.indexOf(to, firstPos + 1)
        if (secondPos < 0) {
            penalty += 5
            continue
        }
        val gap = secondPos - firstPos - 1
        penalty += (gap * 2).coerceAtMost(6)
    }
    return penalty
}

internal fun NboardImeService.detectWordLanguage(word: String): KeyboardLanguageMode? {
    val folded = foldWord(word)
    val inFrench = frenchLexicon.words.contains(word) || frenchLexicon.foldedWords.contains(folded)
    val inEnglish = englishLexicon.words.contains(word) || englishLexicon.foldedWords.contains(folded)
    return when {
        inFrench && !inEnglish -> KeyboardLanguageMode.FRENCH
        inEnglish && !inFrench -> KeyboardLanguageMode.ENGLISH
        else -> null
    }
}

internal fun NboardImeService.isSubsequence(pattern: String, source: String): Boolean {
    if (pattern.isEmpty()) {
        return true
    }
    var patternIndex = 0
    source.forEach { char ->
        if (patternIndex < pattern.length && pattern[patternIndex] == char) {
            patternIndex++
        }
    }
    return patternIndex == pattern.length
}


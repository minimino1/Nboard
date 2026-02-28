package com.nboard.ime

import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible

internal fun NboardImeService.toggleAiMode() {
    if (!isAiAllowedInCurrentContext()) {
        return
    }
    dismissActivePopup()
    isEmojiMode = false
    isEmojiSearchMode = false
    isGifMode = false
    gifSearchJob?.cancel()
    isClipboardOpen = false
    isAiMode = !isAiMode
    if (isAiMode) {
        inlineInputTarget = InlineInputTarget.NONE
    } else {
        clearInlinePromptFocus()
        aiPromptInput.text?.clear()
        setGenerating(false)
    }
    refreshUi()
}

internal fun NboardImeService.toggleEmojiMode() {
    dismissActivePopup()
    isEmojiMode = !isEmojiMode
    if (isEmojiMode) {
        isAiMode = false
        isClipboardOpen = false
        isNumbersMode = false
        isSymbolsSubmenuOpen = false
        isEmojiSearchMode = false
        setGenerating(false)
        emojiSearchInput.text?.clear()
        clearInlinePromptFocus()
        renderEmojiGrid()
        renderEmojiSuggestions()
    } else {
        isEmojiSearchMode = false
        isGifMode = false
        gifSearchJob?.cancel()
        if (::gifSearchInput.isInitialized) {
            gifSearchInput.text?.clear()
        }
        if (::gifResultsRow.isInitialized) {
            gifResultsRow.removeAllViews()
        }
        emojiSearchInput.text?.clear()
        clearInlinePromptFocus()
    }
    renderKeyRows()
    refreshUi()
}

internal fun NboardImeService.performBottomModeTap(mode: BottomKeyMode) {
    when (mode) {
        BottomKeyMode.AI -> {
            if (isAiAllowedInCurrentContext()) {
                toggleAiMode()
            }
        }
        BottomKeyMode.CLIPBOARD -> toggleClipboardMode()
        BottomKeyMode.EMOJI -> toggleEmojiMode()
    }
}

internal fun NboardImeService.toggleClipboardMode() {
    dismissActivePopup()
    isEmojiMode = false
    isEmojiSearchMode = false
    isGifMode = false
    gifSearchJob?.cancel()
    isAiMode = false
    setGenerating(false)
    clearInlinePromptFocus()
    isClipboardOpen = !isClipboardOpen
    renderClipboardItems()
    refreshUi()
}

internal fun NboardImeService.refreshUi() {
    renderRecentClipboardRow()
    renderPredictionRow()
    renderEmojiSuggestions()
    if (isVoiceListening && !isVoiceInputLongPressAvailable()) {
        stopVoiceInput(forceCancel = true)
    }
    val aiAllowed = isAiAllowedInCurrentContext()
    if (!aiAllowed && isAiMode) {
        isAiMode = false
        aiPromptInput.text?.clear()
        clearInlinePromptFocus()
        setGenerating(false)
    }

    refreshAutoShiftFromContext()
    refreshGboardPunctuationLabels()
    setVisibleAnimated(aiQuickActionsRow, isAiMode)
    setVisibleAnimated(aiPromptRow, isAiMode)
    setVisibleAnimated(clipboardPanel, isClipboardOpen && !isEmojiMode)
    setVisibleAnimated(emojiPanel, isEmojiMode && !isGifMode)
    setVisibleAnimated(gifPanel, isGifMode && isEmojiMode)
    setVisibleAnimated(recentClipboardRow, shouldShowRecentClipboardRow())
    setVisibleAnimated(predictionRow, shouldShowPredictionRow() && hasPredictionSuggestions)
    setVisibleAnimated(keyRowsContainer, !isClipboardOpen && (!isEmojiMode || isEmojiSearchMode || isGifMode))

    val gboardLayout = isGboardLayoutActive()
    setVisibleAnimated(modeSwitchButton, gboardLayout || !isClipboardOpen)
    setVisibleAnimated(leftPunctuationButton, gboardLayout && !isClipboardOpen)
    setVisibleAnimated(aiModeButton, gboardLayout || !isClipboardOpen)
    setVisibleAnimated(rightPunctuationButton, gboardLayout && !isClipboardOpen)
    setVisibleAnimated(clipboardButton, !gboardLayout)
    setVisibleAnimated(actionButton, true)
    applyBottomRowLayoutForClipboard(isClipboardOpen)

    modeSwitchButton.text = if (isNumbersMode || isEmojiMode) "ABC" else "123"
    emojiSearchPill.isVisible = isEmojiMode && isEmojiSearchMode && !isGifMode
    emojiSuggestionsScroll.isVisible = isEmojiMode && isEmojiSearchMode && !isGifMode
    emojiGridScroll.isVisible = isEmojiMode && !isEmojiSearchMode && !isGifMode

    if (gboardLayout) {
        aiModeButton.alpha = if (isEmojiMode || isBottomModeSelected(leftBottomMode)) 1f else 0.84f
    } else if (isEmojiMode) {
        aiModeButton.alpha = 1f
        clipboardButton.alpha = 1f
    } else {
        aiModeButton.alpha = if (isBottomModeSelected(leftBottomMode)) 1f else 0.84f
        clipboardButton.alpha = if (isBottomModeSelected(rightBottomMode)) 1f else 0.84f
    }

    updateBottomModeIcons()
    updateModeSelectionVisuals()
    if (isEmojiMode) {
        setIcon(aiModeButton, R.drawable.ic_search_lucide, R.color.ai_text)
        if (!gboardLayout) {
            if (isGifMode) {
                setIcon(clipboardButton, R.drawable.ic_smile_lucide, R.color.key_text)
            } else {
                setIcon(clipboardButton, R.drawable.ic_gif_lucide, R.color.key_text)
            }
        }
    }

    if (isVoiceListening || isVoiceStopping) {
        actionButton.background = uiDrawable(R.drawable.bg_ai_button)
        setIcon(actionButton, R.drawable.ic_mic_lucide, R.color.ai_text)
    } else if (isAiMode) {
        actionButton.background = uiDrawable(R.drawable.bg_ai_button)
        setIcon(actionButton, R.drawable.ic_circle_arrow_right_lucide, R.color.ai_text)
    } else if (isClipboardOpen) {
        actionButton.background = uiDrawable(R.drawable.bg_special_key)
        setIcon(actionButton, R.drawable.ic_move_left_lucide, R.color.key_text)
    } else if (isEmojiMode) {
        actionButton.background = uiDrawable(R.drawable.bg_special_key)
        setIcon(actionButton, R.drawable.ic_move_left_lucide, R.color.key_text)
    } else {
        actionButton.background = uiDrawable(R.drawable.bg_special_key)
        val iconRes = resolveContextualActionIcon()
        setIcon(actionButton, iconRes, R.color.key_text)
    }

    aiModeButton.isEnabled = !isGenerating && !(leftBottomMode == BottomKeyMode.AI && !aiAllowed)
    leftPunctuationButton.isEnabled = !isGenerating && gboardLayout
    rightPunctuationButton.isEnabled = !isGenerating && gboardLayout
    clipboardButton.isEnabled = !gboardLayout && !isGenerating && !(rightBottomMode == BottomKeyMode.AI && !aiAllowed)
    aiPromptToggleButton.isEnabled = aiAllowed && !isGenerating
    aiPromptInput.isEnabled = aiAllowed && !isGenerating
    aiSummarizeButton.isEnabled = aiAllowed && !isGenerating
    aiFixGrammarButton.isEnabled = aiAllowed && !isGenerating
    aiExpandButton.isEnabled = aiAllowed && !isGenerating

    setGenerating(isGenerating)
}

internal fun NboardImeService.applyBottomRowLayoutForClipboard(clipboardOpen: Boolean) {
    if (isGboardLayoutActive()) {
        updateBottomKeyLayout(modeSwitchButton, 1.18f, marginEndDp = 4)
        updateBottomKeyLayout(aiModeButton, 1f, marginEndDp = 4)
        updateBottomKeyLayout(leftPunctuationButton, 1f, marginEndDp = 4)
        updateBottomKeyLayout(spaceButton, 4.41f, marginEndDp = 4)
        updateBottomKeyLayout(rightPunctuationButton, 1f, marginEndDp = 4)
        updateBottomKeyLayout(actionButton, 1.75f, marginEndDp = 0)
        return
    }
    updateBottomKeyLayout(modeSwitchButton, 1.2f, marginEndDp = 4)
    updateBottomKeyLayout(aiModeButton, 1f, marginEndDp = 4)
    updateBottomKeyLayout(spaceButton, if (clipboardOpen) 7.8f else 5f, marginEndDp = 4)
    updateBottomKeyLayout(clipboardButton, 1f, marginEndDp = 4)
    updateBottomKeyLayout(actionButton, if (clipboardOpen) 1.35f else 1.9f, marginEndDp = 0)
}

internal fun NboardImeService.updateBottomKeyLayout(view: View, weight: Float, marginEndDp: Int) {
    val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
    if (params.weight == weight && params.marginEnd == dp(marginEndDp)) {
        return
    }
    params.weight = weight
    params.marginEnd = dp(marginEndDp)
    view.layoutParams = params
}

internal fun NboardImeService.updateBottomModeIcons() {
    setIcon(aiModeButton, iconResForBottomMode(leftBottomMode), R.color.key_text)
    if (isGboardLayoutActive()) {
        return
    }
    setIcon(clipboardButton, iconResForBottomMode(rightBottomMode), R.color.key_text)
}

internal fun NboardImeService.iconResForBottomMode(mode: BottomKeyMode): Int {
    return when (mode) {
        BottomKeyMode.AI -> R.drawable.ic_ai_custom
        BottomKeyMode.CLIPBOARD -> R.drawable.ic_clipboard_lucide
        BottomKeyMode.EMOJI -> R.drawable.ic_smile_lucide
    }
}

internal fun NboardImeService.updateModeSelectionVisuals() {
    if (isGboardLayoutActive()) {
        val aiSelected = if (isEmojiMode) isEmojiSearchMode else isBottomModeSelected(leftBottomMode)
        styleModeButton(
            button = aiModeButton,
            selected = aiSelected,
            selectedBackgroundRes = if (leftBottomMode == BottomKeyMode.AI) {
                R.drawable.bg_mode_ai_selected
            } else {
                R.drawable.bg_mode_special_selected
            },
            normalBackgroundRes = if (leftBottomMode == BottomKeyMode.AI) {
                R.drawable.bg_ai_button
            } else {
                R.drawable.bg_special_key
            }
        )
        leftPunctuationButton.background = uiDrawable(R.drawable.bg_key)
        rightPunctuationButton.background = uiDrawable(R.drawable.bg_key)
        return
    }

    if (isEmojiMode) {
        styleModeButton(
            button = aiModeButton,
            selected = isEmojiSearchMode,
            selectedBackgroundRes = R.drawable.bg_mode_ai_selected,
            normalBackgroundRes = R.drawable.bg_ai_button
        )
        styleModeButton(
            button = clipboardButton,
            selected = isGifMode,
            selectedBackgroundRes = R.drawable.bg_mode_special_selected,
            normalBackgroundRes = R.drawable.bg_special_key
        )
        return
    }

    val aiSelected = isBottomModeSelected(leftBottomMode)
    val clipboardSelected = isBottomModeSelected(rightBottomMode)

    styleModeButton(
        button = aiModeButton,
        selected = aiSelected,
        selectedBackgroundRes = if (leftBottomMode == BottomKeyMode.AI) {
            R.drawable.bg_mode_ai_selected
        } else {
            R.drawable.bg_mode_special_selected
        },
        normalBackgroundRes = if (leftBottomMode == BottomKeyMode.AI) {
            R.drawable.bg_ai_button
        } else {
            R.drawable.bg_special_key
        }
    )
    styleModeButton(
        button = clipboardButton,
        selected = clipboardSelected,
        selectedBackgroundRes = if (rightBottomMode == BottomKeyMode.AI) {
            R.drawable.bg_mode_ai_selected
        } else {
            R.drawable.bg_mode_special_selected
        },
        normalBackgroundRes = if (rightBottomMode == BottomKeyMode.AI) {
            R.drawable.bg_ai_button
        } else {
            R.drawable.bg_special_key
        }
    )
}

internal fun NboardImeService.styleModeButton(
    button: ImageButton,
    selected: Boolean,
    selectedBackgroundRes: Int,
    normalBackgroundRes: Int
) {
    button.background = uiDrawable(if (selected) selectedBackgroundRes else normalBackgroundRes)
    applyReducedKeyIconOffset(button, selected)
}

internal fun NboardImeService.applyReducedKeyIconOffset(button: ImageButton, selected: Boolean) {
    if (selected) {
        button.setPadding(dp(8), dp(11), dp(8), dp(5))
    } else {
        button.setPadding(dp(8), dp(8), dp(8), dp(8))
    }
}

internal fun NboardImeService.isBottomModeSelected(mode: BottomKeyMode): Boolean {
    return when (mode) {
        BottomKeyMode.AI -> isAiMode && isAiAllowedInCurrentContext()
        BottomKeyMode.CLIPBOARD -> isClipboardOpen
        BottomKeyMode.EMOJI -> isEmojiMode
    }
}

internal fun NboardImeService.setGenerating(generating: Boolean) {
    isGenerating = generating
    val aiAllowed = isAiAllowedInCurrentContext()
    val gboardLayout = isGboardLayoutActive()
    aiPromptInput.isEnabled = aiAllowed && !generating
    actionButton.isEnabled = !generating
    modeSwitchButton.isEnabled = !generating
    leftPunctuationButton.isEnabled = !generating && gboardLayout
    aiModeButton.isEnabled = !generating && !(leftBottomMode == BottomKeyMode.AI && !aiAllowed)
    rightPunctuationButton.isEnabled = !generating && gboardLayout
    clipboardButton.isEnabled = !gboardLayout && !generating && !(rightBottomMode == BottomKeyMode.AI && !aiAllowed)
    aiPromptToggleButton.isEnabled = aiAllowed && !generating
    aiSummarizeButton.isEnabled = aiAllowed && !generating
    aiFixGrammarButton.isEnabled = aiAllowed && !generating
    aiExpandButton.isEnabled = aiAllowed && !generating
    emojiSearchInput.isEnabled = !generating
    emojiSearchIconButton.isEnabled = !generating
    if (::gifSearchInput.isInitialized) {
        gifSearchInput.isEnabled = !generating
    }
    recentClipboardChip.isEnabled = !generating
    recentClipboardChevronButton.isEnabled = !generating
    syncAiProcessingAnimations()
    syncVoiceInputGlowAnimation()
}

internal fun NboardImeService.setVisibleAnimated(view: View, visible: Boolean) {
    if (visible) {
        if (view.isVisible) {
            return
        }
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(4).toFloat()
        view.isVisible = true
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(120L)
            .start()
    } else {
        if (!view.isVisible) {
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(dp(4).toFloat())
            .setDuration(90L)
            .withEndAction {
                view.isVisible = false
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }
}

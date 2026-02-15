package com.nboard.ime

import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo

internal fun NboardImeService.sendOrEnter() {
    val inputConnection = currentInputConnection ?: return
    val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
        ?: EditorInfo.IME_ACTION_NONE

    val actionHandled = when (action) {
        EditorInfo.IME_ACTION_DONE,
        EditorInfo.IME_ACTION_GO,
        EditorInfo.IME_ACTION_NEXT,
        EditorInfo.IME_ACTION_PREVIOUS,
        EditorInfo.IME_ACTION_SEARCH,
        EditorInfo.IME_ACTION_SEND -> inputConnection.performEditorAction(action)
        else -> false
    }

    if (!actionHandled) {
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
}

internal fun NboardImeService.isQwertyLayoutActive(): Boolean = keyboardLayoutMode.isQwerty()

internal fun NboardImeService.isGboardLayoutActive(): Boolean = keyboardLayoutMode.isGboard()

internal fun NboardImeService.resolveLeadingPunctuationLabel(): String {
    val info = currentInputEditorInfo ?: return ","
    val inputType = info.inputType
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    if (inputClass != InputType.TYPE_CLASS_TEXT) {
        return ","
    }
    return when (variation) {
        InputType.TYPE_TEXT_VARIATION_URI -> "/"
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> "@"
        else -> ","
    }
}

internal fun NboardImeService.refreshGboardPunctuationLabels() {
    if (!arePunctuationButtonsInitialized()) {
        return
    }
    val leading = resolveLeadingPunctuationLabel()
    leftPunctuationButton.text = leading
    leftPunctuationButton.contentDescription = "Insert $leading"
    rightPunctuationButton.text = "."
}

internal fun NboardImeService.resolveContextualActionIcon(): Int {
    if (isEmojiSearchActive()) {
        return R.drawable.ic_search_lucide
    }
    val info = currentInputEditorInfo
    val inputType = info?.inputType ?: 0
    if (isPasswordInputType(inputType)) {
        return R.drawable.ic_key_lucide
    }

    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    if (
        inputClass == InputType.TYPE_CLASS_TEXT &&
        (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS)
    ) {
        return R.drawable.ic_at_sign_lucide
    }
    if (inputClass == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI) {
        return R.drawable.ic_globe_lucide
    }
    if (inputClass == InputType.TYPE_CLASS_PHONE) {
        return R.drawable.ic_phone_lucide
    }
    if (inputClass == InputType.TYPE_CLASS_NUMBER) {
        return R.drawable.ic_hash_lucide
    }

    val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
    return when (action) {
        EditorInfo.IME_ACTION_SEND -> R.drawable.ic_send_lucide
        EditorInfo.IME_ACTION_SEARCH -> R.drawable.ic_search_lucide
        EditorInfo.IME_ACTION_GO -> R.drawable.ic_arrow_right_lucide
        EditorInfo.IME_ACTION_DONE -> R.drawable.ic_check_lucide
        EditorInfo.IME_ACTION_NEXT -> R.drawable.ic_chevron_right_lucide
        EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.ic_chevron_left_lucide
        EditorInfo.IME_ACTION_NONE,
        EditorInfo.IME_ACTION_UNSPECIFIED -> R.drawable.ic_corner_down_left_lucide
        else -> R.drawable.ic_corner_down_left_lucide
    }
}

internal fun NboardImeService.isAiAllowedInCurrentContext(): Boolean {
    return !isPasswordInputType(currentInputEditorInfo?.inputType ?: 0)
}

internal fun NboardImeService.isPasswordInputType(inputType: Int): Boolean {
    val inputClass = inputType and InputType.TYPE_MASK_CLASS
    val variation = inputType and InputType.TYPE_MASK_VARIATION

    val textPassword = inputClass == InputType.TYPE_CLASS_TEXT &&
        (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)
    val numberPassword = inputClass == InputType.TYPE_CLASS_NUMBER &&
        variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD

    return textPassword || numberPassword
}


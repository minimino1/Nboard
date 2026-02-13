package com.nboard.ime

import android.content.Context

enum class BottomKeyMode(val value: String) {
    AI("ai"),
    CLIPBOARD("clipboard"),
    EMOJI("emoji")
}

enum class KeyboardLayoutMode(val value: String) {
    AZERTY("azerty"),
    QWERTY("qwerty")
}

enum class KeyboardLanguageMode(val value: String) {
    FRENCH("french"),
    ENGLISH("english"),
    BOTH("both"),
    DISABLED("disabled")
}

enum class AppThemeMode(val value: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    DARK_CLASSIC("dark_classic")
}

enum class KeyboardFontMode(val value: String) {
    INTER("inter"),
    ROBOTO("roboto")
}

object KeyboardModeSettings {
    const val PREFS_NAME = "nboard_settings"
    private const val KEY_LEFT_MODE = "left_bottom_mode"
    private const val KEY_RIGHT_MODE = "right_bottom_mode"
    private const val KEY_LEFT_OPTION_PRIMARY = "left_bottom_option_primary"
    private const val KEY_LEFT_OPTION_SECONDARY = "left_bottom_option_secondary"
    private const val KEY_RIGHT_OPTION_PRIMARY = "right_bottom_option_primary"
    private const val KEY_RIGHT_OPTION_SECONDARY = "right_bottom_option_secondary"
    private const val KEY_LAYOUT_MODE = "keyboard_layout_mode"
    private const val KEY_LANGUAGE_MODE = "keyboard_language_mode"
    private const val KEY_API_KEY = "gemini_api_key"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_FONT_MODE = "font_mode"
    private const val KEY_WORD_PREDICTION_ENABLED = "word_prediction_enabled"
    private const val KEY_SWIPE_TYPING_ENABLED = "swipe_typing_enabled"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    fun load(context: Context): Pair<BottomKeyMode, BottomKeyMode> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val (leftPrimary, leftSecondary) = loadBottomSlotOptionsInternal(prefs, true)
        val (rightPrimary, rightSecondary) = loadBottomSlotOptionsInternal(prefs, false)
        val leftOptions = setOf(leftPrimary, leftSecondary)
        val rightOptions = setOf(rightPrimary, rightSecondary)

        val left = parseBottomKeyMode(prefs.getString(KEY_LEFT_MODE, null), leftPrimary)
            .let { mode -> if (mode in leftOptions) mode else leftPrimary }

        val right = parseBottomKeyMode(prefs.getString(KEY_RIGHT_MODE, null), rightPrimary)
            .let { mode -> if (mode in rightOptions) mode else rightPrimary }

        return left to right
    }

    fun save(context: Context, left: BottomKeyMode, right: BottomKeyMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LEFT_MODE, left.value)
            .putString(KEY_RIGHT_MODE, right.value)
            .apply()
    }

    fun loadBottomSlotOptions(context: Context): Pair<List<BottomKeyMode>, List<BottomKeyMode>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val left = loadBottomSlotOptionsInternal(prefs, true)
        val right = loadBottomSlotOptionsInternal(prefs, false)
        return listOf(left.first, left.second) to listOf(right.first, right.second)
    }

    fun saveLeftSlotOptions(context: Context, first: BottomKeyMode, second: BottomKeyMode) {
        saveBottomSlotOptions(context, isLeftSlot = true, first = first, second = second)
    }

    fun saveRightSlotOptions(context: Context, first: BottomKeyMode, second: BottomKeyMode) {
        saveBottomSlotOptions(context, isLeftSlot = false, first = first, second = second)
    }

    private fun saveBottomSlotOptions(
        context: Context,
        isLeftSlot: Boolean,
        first: BottomKeyMode,
        second: BottomKeyMode
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val normalized = normalizeOptionPair(first, second, isLeftSlot)
        val (primaryKey, secondaryKey, currentModeKey) = if (isLeftSlot) {
            Triple(KEY_LEFT_OPTION_PRIMARY, KEY_LEFT_OPTION_SECONDARY, KEY_LEFT_MODE)
        } else {
            Triple(KEY_RIGHT_OPTION_PRIMARY, KEY_RIGHT_OPTION_SECONDARY, KEY_RIGHT_MODE)
        }

        val currentMode = parseBottomKeyMode(
            prefs.getString(currentModeKey, null),
            normalized.first
        )
        val adjustedMode = if (currentMode == normalized.first || currentMode == normalized.second) {
            currentMode
        } else {
            normalized.first
        }

        prefs.edit()
            .putString(primaryKey, normalized.first.value)
            .putString(secondaryKey, normalized.second.value)
            .putString(currentModeKey, adjustedMode.value)
            .apply()
    }

    fun loadLayoutMode(context: Context): KeyboardLayoutMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAYOUT_MODE, KeyboardLayoutMode.AZERTY.value)
        return if (raw == KeyboardLayoutMode.QWERTY.value) KeyboardLayoutMode.QWERTY else KeyboardLayoutMode.AZERTY
    }

    fun saveLayoutMode(context: Context, mode: KeyboardLayoutMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAYOUT_MODE, mode.value)
            .apply()
    }

    fun loadLanguageMode(context: Context): KeyboardLanguageMode {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE_MODE, KeyboardLanguageMode.FRENCH.value)
        return when (raw) {
            KeyboardLanguageMode.ENGLISH.value -> KeyboardLanguageMode.ENGLISH
            KeyboardLanguageMode.BOTH.value -> KeyboardLanguageMode.BOTH
            KeyboardLanguageMode.DISABLED.value -> KeyboardLanguageMode.DISABLED
            else -> KeyboardLanguageMode.FRENCH
        }
    }

    fun saveLanguageMode(context: Context, mode: KeyboardLanguageMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE_MODE, mode.value)
            .apply()
    }

    fun loadGeminiApiKey(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_API_KEY, "")
            .orEmpty()
    }

    fun saveGeminiApiKey(context: Context, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_API_KEY, value.trim())
            .apply()
    }

    fun loadThemeMode(context: Context): AppThemeMode {
        return when (
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.value)
        ) {
            AppThemeMode.LIGHT.value -> AppThemeMode.LIGHT
            AppThemeMode.DARK_CLASSIC.value -> AppThemeMode.DARK_CLASSIC
            AppThemeMode.DARK.value -> AppThemeMode.DARK
            else -> AppThemeMode.SYSTEM
        }
    }

    fun saveThemeMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.value)
            .apply()
    }

    fun loadFontMode(context: Context): KeyboardFontMode {
        return when (
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_FONT_MODE, KeyboardFontMode.INTER.value)
        ) {
            KeyboardFontMode.ROBOTO.value -> KeyboardFontMode.ROBOTO
            else -> KeyboardFontMode.INTER
        }
    }

    fun saveFontMode(context: Context, mode: KeyboardFontMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONT_MODE, mode.value)
            .apply()
    }

    fun loadWordPredictionEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_WORD_PREDICTION_ENABLED, true)
    }

    fun saveWordPredictionEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_WORD_PREDICTION_ENABLED, enabled)
            .apply()
    }

    fun loadSwipeTypingEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SWIPE_TYPING_ENABLED, true)
    }

    fun saveSwipeTypingEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SWIPE_TYPING_ENABLED, enabled)
            .apply()
    }

    fun loadOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun saveOnboardingCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }

    private fun loadBottomSlotOptionsInternal(
        prefs: android.content.SharedPreferences,
        isLeftSlot: Boolean
    ): Pair<BottomKeyMode, BottomKeyMode> {
        val defaultFirst = if (isLeftSlot) BottomKeyMode.AI else BottomKeyMode.CLIPBOARD
        val defaultSecond = BottomKeyMode.EMOJI
        val primaryKey = if (isLeftSlot) KEY_LEFT_OPTION_PRIMARY else KEY_RIGHT_OPTION_PRIMARY
        val secondaryKey = if (isLeftSlot) KEY_LEFT_OPTION_SECONDARY else KEY_RIGHT_OPTION_SECONDARY

        val rawFirst = parseBottomKeyMode(prefs.getString(primaryKey, null), defaultFirst)
        val rawSecond = parseBottomKeyMode(prefs.getString(secondaryKey, null), defaultSecond)
        return normalizeOptionPair(rawFirst, rawSecond, isLeftSlot)
    }

    private fun normalizeOptionPair(
        first: BottomKeyMode,
        second: BottomKeyMode,
        isLeftSlot: Boolean
    ): Pair<BottomKeyMode, BottomKeyMode> {
        if (first != second) {
            return first to second
        }
        val defaultSecond = if (isLeftSlot) BottomKeyMode.EMOJI else BottomKeyMode.EMOJI
        if (defaultSecond != first) {
            return first to defaultSecond
        }
        val fallback = BottomKeyMode.values().firstOrNull { it != first } ?: BottomKeyMode.EMOJI
        return first to fallback
    }

    private fun parseBottomKeyMode(raw: String?, fallback: BottomKeyMode): BottomKeyMode {
        return when (raw) {
            BottomKeyMode.AI.value -> BottomKeyMode.AI
            BottomKeyMode.CLIPBOARD.value -> BottomKeyMode.CLIPBOARD
            BottomKeyMode.EMOJI.value -> BottomKeyMode.EMOJI
            else -> fallback
        }
    }
}

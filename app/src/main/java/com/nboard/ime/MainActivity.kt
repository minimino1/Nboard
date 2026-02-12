package com.nboard.ime

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var languageValue: TextView
    private lateinit var keyboardValue: TextView
    private lateinit var wordPredictionValue: TextView
    private lateinit var swipeTypingValue: TextView
    private lateinit var leftKeyModesValue: TextView
    private lateinit var rightKeyModesValue: TextView
    private lateinit var themeValue: TextView
    private lateinit var fontValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemePreference(KeyboardModeSettings.loadThemeMode(this))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        languageValue = findViewById(R.id.languageValue)
        keyboardValue = findViewById(R.id.keyboardValue)
        wordPredictionValue = findViewById(R.id.wordPredictionValue)
        swipeTypingValue = findViewById(R.id.swipeTypingValue)
        leftKeyModesValue = findViewById(R.id.leftKeyModesValue)
        rightKeyModesValue = findViewById(R.id.rightKeyModesValue)
        themeValue = findViewById(R.id.themeValue)
        fontValue = findViewById(R.id.fontValue)

        applyStatusBarInset()
        bindActions()
        refreshValues()
        maybeShowFirstLaunchOnboarding()
    }

    override fun onResume() {
        super.onResume()
        refreshValues()
    }

    private fun applyStatusBarInset() {
        val content = findViewById<View>(R.id.settingsContent)
        val baseTop = content.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, baseTop + topInset, view.paddingRight, view.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    private fun bindActions() {
        findViewById<View>(R.id.makeDefaultRow).setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        findViewById<View>(R.id.replayOnboardingRow).setOnClickListener {
            startActivity(Intent(this, OnboardingActivity::class.java).apply {
                putExtra(OnboardingActivity.EXTRA_REPLAY, true)
            })
        }

        findViewById<View>(R.id.keyboardRow).setOnClickListener {
            showKeyboardLayoutDialog()
        }

        findViewById<View>(R.id.languageRow).setOnClickListener {
            showLanguageDialog()
        }

        findViewById<View>(R.id.apiKeyRow).setOnClickListener {
            showApiKeyDialog()
        }

        findViewById<View>(R.id.wordPredictionRow).setOnClickListener {
            showWordPredictionDialog()
        }

        findViewById<View>(R.id.swipeTypingRow).setOnClickListener {
            showSwipeTypingDialog()
        }

        findViewById<View>(R.id.leftKeyModesRow).setOnClickListener {
            showBottomKeyOptionsDialog(isLeftSlot = true)
        }

        findViewById<View>(R.id.rightKeyModesRow).setOnClickListener {
            showBottomKeyOptionsDialog(isLeftSlot = false)
        }

        findViewById<View>(R.id.themeRow).setOnClickListener {
            showThemeDialog()
        }

        findViewById<View>(R.id.fontRow).setOnClickListener {
            showFontDialog()
        }

        findViewById<View>(R.id.koFiButton).setOnClickListener {
            openLink("https://ko-fi.com/dotslimy")
        }

        findViewById<View>(R.id.librariesRow).setOnClickListener {
            showLibrariesDialog()
        }

        findViewById<View>(R.id.authorRow).setOnClickListener {
            openLink("https://github.com/MathieuDvv")
        }
    }

    private fun showKeyboardLayoutDialog() {
        val current = KeyboardModeSettings.loadLayoutMode(this)
        val options = arrayOf("AZERTY", "QWERTY")
        val selected = if (current == KeyboardLayoutMode.QWERTY) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Keyboard layout")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = if (which == 1) KeyboardLayoutMode.QWERTY else KeyboardLayoutMode.AZERTY
                KeyboardModeSettings.saveLayoutMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val current = KeyboardModeSettings.loadLanguageMode(this)
        val options = arrayOf("French", "English", "French + English")
        val selected = when (current) {
            KeyboardLanguageMode.FRENCH -> 0
            KeyboardLanguageMode.ENGLISH -> 1
            KeyboardLanguageMode.BOTH -> 2
        }

        AlertDialog.Builder(this)
            .setTitle("Basic autocorrect")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> KeyboardLanguageMode.ENGLISH
                    2 -> KeyboardLanguageMode.BOTH
                    else -> KeyboardLanguageMode.FRENCH
                }
                KeyboardModeSettings.saveLanguageMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            setText(KeyboardModeSettings.loadGeminiApiKey(this@MainActivity))
            hint = "Paste Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            setSelection(text.length)
        }

        AlertDialog.Builder(this)
            .setTitle("Set Gemini API key")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                KeyboardModeSettings.saveGeminiApiKey(this, input.text?.toString().orEmpty())
                refreshValues()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeDialog() {
        val current = KeyboardModeSettings.loadThemeMode(this)
        val options = arrayOf("System", "Light", "Dark")
        val selected = when (current) {
            AppThemeMode.SYSTEM -> 0
            AppThemeMode.LIGHT -> 1
            AppThemeMode.DARK -> 2
        }

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> AppThemeMode.LIGHT
                    2 -> AppThemeMode.DARK
                    else -> AppThemeMode.SYSTEM
                }
                KeyboardModeSettings.saveThemeMode(this, mode)
                applyThemePreference(mode)
                refreshValues()
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBottomKeyOptionsDialog(isLeftSlot: Boolean) {
        val options = listOf(
            BottomKeyPairOption(BottomKeyMode.AI, BottomKeyMode.EMOJI),
            BottomKeyPairOption(BottomKeyMode.AI, BottomKeyMode.CLIPBOARD),
            BottomKeyPairOption(BottomKeyMode.CLIPBOARD, BottomKeyMode.EMOJI)
        )
        val labels = options.map { formatBottomModePairLabel(it.first, it.second) }.toTypedArray()
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        val currentSet = if (isLeftSlot) leftOptions.toSet() else rightOptions.toSet()
        val selected = options.indexOfFirst { setOf(it.first, it.second) == currentSet }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle(if (isLeftSlot) "Left key options" else "Right key options")
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                val selectedPair = options[which]
                if (isLeftSlot) {
                    KeyboardModeSettings.saveLeftSlotOptions(this, selectedPair.first, selectedPair.second)
                } else {
                    KeyboardModeSettings.saveRightSlotOptions(this, selectedPair.first, selectedPair.second)
                }
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWordPredictionDialog() {
        val enabled = KeyboardModeSettings.loadWordPredictionEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Word prediction")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveWordPredictionEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSwipeTypingDialog() {
        val enabled = KeyboardModeSettings.loadSwipeTypingEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Swipe typing")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveSwipeTypingEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFontDialog() {
        val current = KeyboardModeSettings.loadFontMode(this)
        val options = arrayOf("Inter", "Roboto")
        val selected = if (current == KeyboardFontMode.ROBOTO) 1 else 0

        AlertDialog.Builder(this)
            .setTitle("Keyboard font")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = if (which == 1) KeyboardFontMode.ROBOTO else KeyboardFontMode.INTER
                KeyboardModeSettings.saveFontMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshValues() {
        statusText.text = maskApiKeyForDisplay(KeyboardModeSettings.loadGeminiApiKey(this))
        languageValue.text = when (KeyboardModeSettings.loadLanguageMode(this)) {
            KeyboardLanguageMode.FRENCH -> "French"
            KeyboardLanguageMode.ENGLISH -> "English"
            KeyboardLanguageMode.BOTH -> "French + English"
        }
        keyboardValue.text = when (KeyboardModeSettings.loadLayoutMode(this)) {
            KeyboardLayoutMode.AZERTY -> "AZERTY"
            KeyboardLayoutMode.QWERTY -> "QWERTY"
        }
        wordPredictionValue.text = if (KeyboardModeSettings.loadWordPredictionEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        swipeTypingValue.text = if (KeyboardModeSettings.loadSwipeTypingEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        leftKeyModesValue.text = formatBottomModePairLabel(leftOptions[0], leftOptions[1])
        rightKeyModesValue.text = formatBottomModePairLabel(rightOptions[0], rightOptions[1])
        themeValue.text = when (KeyboardModeSettings.loadThemeMode(this)) {
            AppThemeMode.SYSTEM -> "System"
            AppThemeMode.LIGHT -> "Light"
            AppThemeMode.DARK -> "Dark"
        }
        fontValue.text = when (KeyboardModeSettings.loadFontMode(this)) {
            KeyboardFontMode.INTER -> "Inter"
            KeyboardFontMode.ROBOTO -> "Roboto"
        }
    }

    private fun maskApiKeyForDisplay(key: String): String {
        if (key.isBlank()) {
            return "Not set"
        }
        val visible = key.takeLast(4)
        return "••••••••••••$visible"
    }

    private fun formatBottomModePairLabel(first: BottomKeyMode, second: BottomKeyMode): String {
        return "${formatBottomModeLabel(first)} + ${formatBottomModeLabel(second)}"
    }

    private fun formatBottomModeLabel(mode: BottomKeyMode): String {
        return when (mode) {
            BottomKeyMode.AI -> "AI"
            BottomKeyMode.CLIPBOARD -> "Clipboard"
            BottomKeyMode.EMOJI -> "Emoji"
        }
    }

    private fun showLibrariesDialog() {
        val message = """
            • AndroidX Core KTX — Apache License 2.0
            • AndroidX AppCompat — Apache License 2.0
            • AndroidX RecyclerView — Apache License 2.0
            • Material Components for Android — Apache License 2.0
            • Kotlin Coroutines (Android) — Apache License 2.0
            • OkHttp — Apache License 2.0
            • Kotlin Standard Library — Apache License 2.0

            Design note:
            Nboard is heavily inspired by Nothing and its aesthetic (Nothing Technology Limited).
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Libraries & licences")
            .setMessage(message)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun applyThemePreference(mode: AppThemeMode) {
        val nightMode = when (mode) {
            AppThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            AppThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        if (AppCompatDelegate.getDefaultNightMode() != nightMode) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    private fun openLink(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun maybeShowFirstLaunchOnboarding() {
        if (!KeyboardModeSettings.loadOnboardingCompleted(this)) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }
}

private data class BottomKeyPairOption(
    val first: BottomKeyMode,
    val second: BottomKeyMode
)

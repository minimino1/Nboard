package com.nboard.ime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var languageValue: TextView
    private lateinit var keyboardValue: TextView
    private lateinit var numberRowSettingValue: TextView
    private lateinit var autoSpaceAfterPunctuationValue: TextView
    private lateinit var autoCapitalizeAfterPunctuationValue: TextView
    private lateinit var returnToLettersAfterNumberSpaceValue: TextView
    private lateinit var wordPredictionValue: TextView
    private lateinit var swipeTypingValue: TextView
    private lateinit var swipeTrailValue: TextView
    private lateinit var voiceInputValue: TextView
    private lateinit var swipeTrailRow: View
    private lateinit var swipeTrailDivider: View
    private lateinit var leftKeyModesRow: View
    private lateinit var rightKeyModesRow: View
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
        numberRowSettingValue = findViewById(R.id.numberRowSettingValue)
        autoSpaceAfterPunctuationValue = findViewById(R.id.autoSpaceAfterPunctuationValue)
        autoCapitalizeAfterPunctuationValue = findViewById(R.id.autoCapitalizeAfterPunctuationValue)
        returnToLettersAfterNumberSpaceValue = findViewById(R.id.returnToLettersAfterNumberSpaceValue)
        wordPredictionValue = findViewById(R.id.wordPredictionValue)
        swipeTypingValue = findViewById(R.id.swipeTypingValue)
        swipeTrailValue = findViewById(R.id.swipeTrailValue)
        voiceInputValue = findViewById(R.id.voiceInputValue)
        swipeTrailRow = findViewById(R.id.swipeTrailRow)
        swipeTrailDivider = findViewById(R.id.swipeTrailDivider)
        leftKeyModesRow = findViewById(R.id.leftKeyModesRow)
        rightKeyModesRow = findViewById(R.id.rightKeyModesRow)
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

        findViewById<View>(R.id.numberRowSettingRow).setOnClickListener {
            showNumberRowDialog()
        }

        findViewById<View>(R.id.autoSpaceAfterPunctuationRow).setOnClickListener {
            showAutoSpaceAfterPunctuationDialog()
        }

        findViewById<View>(R.id.autoCapitalizeAfterPunctuationRow).setOnClickListener {
            showAutoCapitalizeAfterPunctuationDialog()
        }

        findViewById<View>(R.id.returnToLettersAfterNumberSpaceRow).setOnClickListener {
            showReturnToLettersAfterNumberSpaceDialog()
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

        swipeTrailRow.setOnClickListener {
            showSwipeTrailDialog()
        }

        findViewById<View>(R.id.voiceInputRow).setOnClickListener {
            showVoiceInputDialog()
        }

        leftKeyModesRow.setOnClickListener {
            if (KeyboardModeSettings.loadLayoutMode(this).isGboard()) {
                Toast.makeText(this, "Tool slots are managed by Gboard layout", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showBottomKeyOptionsDialog(isLeftSlot = true)
        }

        rightKeyModesRow.setOnClickListener {
            if (KeyboardModeSettings.loadLayoutMode(this).isGboard()) {
                Toast.makeText(this, "Tool slots are managed by Gboard layout", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
        val options = arrayOf("AZERTY", "QWERTY", "Gboard AZERTY", "Gboard QWERTY")
        val selected = when (current) {
            KeyboardLayoutMode.AZERTY -> 0
            KeyboardLayoutMode.QWERTY -> 1
            KeyboardLayoutMode.GBOARD_AZERTY -> 2
            KeyboardLayoutMode.GBOARD_QWERTY -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Keyboard layout")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> KeyboardLayoutMode.QWERTY
                    2 -> KeyboardLayoutMode.GBOARD_AZERTY
                    3 -> KeyboardLayoutMode.GBOARD_QWERTY
                    else -> KeyboardLayoutMode.AZERTY
                }
                KeyboardModeSettings.saveLayoutMode(this, mode)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLanguageDialog() {
        val current = KeyboardModeSettings.loadLanguageMode(this)
        val options = arrayOf("French", "English", "French + English", "Disabled")
        val selected = when (current) {
            KeyboardLanguageMode.FRENCH -> 0
            KeyboardLanguageMode.ENGLISH -> 1
            KeyboardLanguageMode.BOTH -> 2
            KeyboardLanguageMode.DISABLED -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Basic autocorrect")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> KeyboardLanguageMode.ENGLISH
                    2 -> KeyboardLanguageMode.BOTH
                    3 -> KeyboardLanguageMode.DISABLED
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
        val options = arrayOf("System", "Light", "Dark", "Dark (Classic)")
        val selected = when (current) {
            AppThemeMode.SYSTEM -> 0
            AppThemeMode.LIGHT -> 1
            AppThemeMode.DARK -> 2
            AppThemeMode.DARK_CLASSIC -> 3
        }

        AlertDialog.Builder(this)
            .setTitle("Theme")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val mode = when (which) {
                    1 -> AppThemeMode.LIGHT
                    2 -> AppThemeMode.DARK
                    3 -> AppThemeMode.DARK_CLASSIC
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

    private fun showNumberRowDialog() {
        val enabled = KeyboardModeSettings.loadNumberRowEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Number row")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveNumberRowEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoSpaceAfterPunctuationDialog() {
        val enabled = KeyboardModeSettings.loadAutoSpaceAfterPunctuationEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Auto-space after punctuation")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveAutoSpaceAfterPunctuationEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAutoCapitalizeAfterPunctuationDialog() {
        val enabled = KeyboardModeSettings.loadAutoCapitalizeAfterPunctuationEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Auto-capitalize after punctuation")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveAutoCapitalizeAfterPunctuationEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showReturnToLettersAfterNumberSpaceDialog() {
        val enabled = KeyboardModeSettings.loadReturnToLettersAfterNumberSpaceEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Return to letters after numbers")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveReturnToLettersAfterNumberSpaceEnabled(this, which == 0)
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

    private fun showVoiceInputDialog() {
        val enabled = KeyboardModeSettings.loadVoiceInputEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Voice input")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                val shouldEnable = which == 0
                KeyboardModeSettings.saveVoiceInputEnabled(this, shouldEnable)
                if (shouldEnable && !hasRecordAudioPermission()) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.RECORD_AUDIO),
                        REQUEST_RECORD_AUDIO_PERMISSION
                    )
                }
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSwipeTrailDialog() {
        val enabled = KeyboardModeSettings.loadSwipeTrailEnabled(this)
        val options = arrayOf("Enabled", "Disabled")
        val selected = if (enabled) 0 else 1

        AlertDialog.Builder(this)
            .setTitle("Showing the trail")
            .setSingleChoiceItems(options, selected) { dialog, which ->
                KeyboardModeSettings.saveSwipeTrailEnabled(this, which == 0)
                refreshValues()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshValues() {
        val layoutMode = KeyboardModeSettings.loadLayoutMode(this)
        val isGboardLayout = layoutMode.isGboard()
        statusText.text = maskApiKeyForDisplay(KeyboardModeSettings.loadGeminiApiKey(this))
        languageValue.text = when (KeyboardModeSettings.loadLanguageMode(this)) {
            KeyboardLanguageMode.FRENCH -> "French"
            KeyboardLanguageMode.ENGLISH -> "English"
            KeyboardLanguageMode.BOTH -> "French + English"
            KeyboardLanguageMode.DISABLED -> "Disabled"
        }
        keyboardValue.text = when (layoutMode) {
            KeyboardLayoutMode.AZERTY -> "AZERTY"
            KeyboardLayoutMode.QWERTY -> "QWERTY"
            KeyboardLayoutMode.GBOARD_AZERTY -> "Gboard AZERTY"
            KeyboardLayoutMode.GBOARD_QWERTY -> "Gboard QWERTY"
        }
        numberRowSettingValue.text = if (KeyboardModeSettings.loadNumberRowEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        autoSpaceAfterPunctuationValue.text = if (KeyboardModeSettings.loadAutoSpaceAfterPunctuationEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        autoCapitalizeAfterPunctuationValue.text =
            if (KeyboardModeSettings.loadAutoCapitalizeAfterPunctuationEnabled(this)) {
                "Enabled"
            } else {
                "Disabled"
            }
        returnToLettersAfterNumberSpaceValue.text =
            if (KeyboardModeSettings.loadReturnToLettersAfterNumberSpaceEnabled(this)) {
                "Enabled"
            } else {
                "Disabled"
            }
        wordPredictionValue.text = if (KeyboardModeSettings.loadWordPredictionEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        val swipeTypingEnabled = KeyboardModeSettings.loadSwipeTypingEnabled(this)
        swipeTypingValue.text = if (swipeTypingEnabled) {
            "Enabled"
        } else {
            "Disabled"
        }
        swipeTrailValue.text = if (KeyboardModeSettings.loadSwipeTrailEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        swipeTrailRow.visibility = if (swipeTypingEnabled) View.VISIBLE else View.GONE
        swipeTrailDivider.visibility = if (swipeTypingEnabled) View.VISIBLE else View.GONE
        voiceInputValue.text = if (KeyboardModeSettings.loadVoiceInputEnabled(this)) {
            "Enabled"
        } else {
            "Disabled"
        }
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        leftKeyModesValue.text = if (isGboardLayout) {
            "Single key (hold AI for AI/Clipboard/Emoji)"
        } else {
            formatBottomModePairLabel(leftOptions[0], leftOptions[1])
        }
        rightKeyModesValue.text = if (isGboardLayout) {
            "Disabled in Gboard layout"
        } else {
            formatBottomModePairLabel(rightOptions[0], rightOptions[1])
        }
        leftKeyModesRow.isEnabled = !isGboardLayout
        rightKeyModesRow.isEnabled = !isGboardLayout
        leftKeyModesRow.alpha = if (isGboardLayout) 0.5f else 1f
        rightKeyModesRow.alpha = if (isGboardLayout) 0.5f else 1f
        themeValue.text = when (KeyboardModeSettings.loadThemeMode(this)) {
            AppThemeMode.SYSTEM -> "System"
            AppThemeMode.LIGHT -> "Light"
            AppThemeMode.DARK -> "Dark"
            AppThemeMode.DARK_CLASSIC -> "Dark (Classic)"
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
            AppThemeMode.DARK,
            AppThemeMode.DARK_CLASSIC -> AppCompatDelegate.MODE_NIGHT_YES
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

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_RECORD_AUDIO_PERMISSION) {
            return
        }
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            KeyboardModeSettings.saveVoiceInputEnabled(this, false)
            refreshValues()
            Toast.makeText(
                this,
                "Microphone permission denied. Voice input disabled.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 1004
    }
}

private data class BottomKeyPairOption(
    val first: BottomKeyMode,
    val second: BottomKeyMode
)

package com.nboard.ime

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputContentInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.nboard.ime.ai.GeminiClient
import com.nboard.ime.clipboard.ClipboardHistoryStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

class NboardImeService : InputMethodService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var keyboardUiContext: Context

    private var clipboardManager: ClipboardManager? = null
    private var vibrator: Vibrator? = null
    private lateinit var clipboardHistoryStore: ClipboardHistoryStore
    private lateinit var geminiClient: GeminiClient
    private var interTypeface: Typeface? = null

    private lateinit var keyboardRoot: LinearLayout
    private lateinit var aiQuickActionsRow: LinearLayout
    private lateinit var aiSummarizeButton: Button
    private lateinit var aiFixGrammarButton: Button
    private lateinit var aiExpandButton: Button
    private lateinit var aiPromptRow: FrameLayout
    private lateinit var aiPromptToggleButton: ImageButton
    private lateinit var aiPromptInput: EditText
    private lateinit var aiPromptShimmer: View

    private lateinit var clipboardPanel: LinearLayout
    private lateinit var clipboardItemsContainer: LinearLayout

    private lateinit var emojiPanel: LinearLayout
    private lateinit var emojiSearchPill: LinearLayout
    private lateinit var emojiSearchIconButton: ImageButton
    private lateinit var emojiSuggestionsScroll: HorizontalScrollView
    private lateinit var emojiMostUsedRow: LinearLayout
    private lateinit var emojiSearchInput: EditText
    private lateinit var emojiGridScroll: HorizontalScrollView
    private lateinit var emojiRecentColumn: LinearLayout
    private lateinit var emojiRecentDivider: View
    private lateinit var emojiGridRow1: LinearLayout
    private lateinit var emojiGridRow2: LinearLayout
    private lateinit var emojiGridRow3: LinearLayout

    private lateinit var recentClipboardRow: LinearLayout
    private lateinit var recentClipboardChip: AppCompatButton
    private lateinit var recentClipboardChevronButton: ImageButton

    private lateinit var keyRowsContainer: LinearLayout
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout
    private lateinit var row3: LinearLayout
    private lateinit var bottomRow: LinearLayout

    private lateinit var modeSwitchButton: Button
    private lateinit var aiModeButton: ImageButton
    private lateinit var spaceButton: Button
    private lateinit var clipboardButton: ImageButton
    private lateinit var actionButton: ImageButton

    private var isAiMode = false
    private var isNumbersMode = false
    private var manualShiftMode = ShiftMode.OFF
    private var isAutoShiftEnabled = true
    private var lastShiftTapAtMs = 0L
    private var isClipboardOpen = false
    private var isGenerating = false
    private var isSymbolsSubmenuOpen = false
    private var isEmojiMode = false
    private var isEmojiSearchMode = false

    private var leftBottomMode = BottomKeyMode.AI
    private var rightBottomMode = BottomKeyMode.CLIPBOARD
    private var leftBottomModeOptions = listOf(BottomKeyMode.AI, BottomKeyMode.EMOJI)
    private var rightBottomModeOptions = listOf(BottomKeyMode.CLIPBOARD, BottomKeyMode.EMOJI)
    private var keyboardLayoutMode = KeyboardLayoutMode.AZERTY
    private var keyboardLanguageMode = KeyboardLanguageMode.FRENCH
    private var keyboardFontMode = KeyboardFontMode.INTER

    private val emojiUsageCounts = mutableMapOf<String, Int>()
    private val emojiRecents = ArrayDeque<String>()
    private val emojiSearchIndex = mutableMapOf<String, String>()
    private val allEmojiCatalog = mutableListOf<String>()
    private var emojiGridLoadedCount = 0
    private val rejectedCorrections = mutableMapOf<String, Int>()
    @Volatile
    private var englishLexicon = Lexicon.empty()
    @Volatile
    private var frenchLexicon = Lexicon.empty()

    private var activePopupWindow: PopupWindow? = null
    private var activeVariantSession: VariantSelectionSession? = null
    private var activeSwipePopupSession: SwipePopupSession? = null
    private var pendingAutoCorrection: AutoCorrectionUndo? = null
    private var aiPillShimmerAnimator: ValueAnimator? = null
    private var aiTextPulseAnimator: ValueAnimator? = null
    private var latestClipboardText: String? = null
    private var latestClipboardImageUri: Uri? = null
    private var latestClipboardImageMimeType: String? = null
    private var latestClipboardImagePreview: Bitmap? = null
    private var latestClipboardAtMs: Long = 0L
    private var latestClipboardDismissed = false
    private var recentClipboardExpiryJob: Job? = null
    private var activeEditorPackage: String? = null

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureClipboardPrimary()
    }

    override fun onCreate() {
        super.onCreate()
        keyboardUiContext = this
        clipboardHistoryStore = ClipboardHistoryStore(this)
        reloadTypingSettings()

        reloadBottomModesFromSettings()

        loadEmojiUsage()
        loadRejectedCorrections()
        resetLexicons()
        preloadLexiconsFromAssets()
        allEmojiCatalog.clear()
        allEmojiCatalog.addAll(ALL_EMOJIS)
        preloadExtendedEmojiCatalog()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        }

        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
        captureClipboardPrimary()
    }

    override fun onDestroy() {
        dismissActivePopup()
        stopAiProcessingAnimations()
        recentClipboardExpiryJob?.cancel()
        clipboardManager?.removePrimaryClipChangedListener(clipboardListener)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        return try {
            reloadTypingSettings()
            reloadBottomModesFromSettings()
            keyboardUiContext = createKeyboardUiContext()
            val root = LayoutInflater.from(keyboardUiContext).inflate(R.layout.keyboard_view, null)
            bindViews(root)
            applyTypographyAndIcons()
            setupEmojiPanel()
            bindListeners()
            renderKeyRows()
            renderClipboardItems()
            renderEmojiGrid()
            renderEmojiSuggestions()
            renderRecentClipboardRow()
            refreshUi()
            root
        } catch (error: Exception) {
            Log.e(TAG, "Failed to create input view", error)
            toast("Nboard failed to load full keyboard, using fallback")
            buildFallbackInputView()
        }
    }

    override fun onStartInput(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInput(editorInfo, restarting)
        manualShiftMode = ShiftMode.OFF
        isAutoShiftEnabled = true
        lastShiftTapAtMs = 0L
        isGenerating = false
        stopAiProcessingAnimations()
        pendingAutoCorrection = null
        val newPackage = editorInfo?.packageName
        if (newPackage != activeEditorPackage) {
            isEmojiSearchMode = false
        }
        activeEditorPackage = newPackage
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        reloadTypingSettings()
        reloadBottomModesFromSettings()
        keyboardUiContext = createKeyboardUiContext()
        refreshAutoShiftFromContext()
        if (::aiModeButton.isInitialized) {
            applyTypographyAndIcons()
            renderKeyRows()
            renderEmojiGrid()
            renderEmojiSuggestions()
            renderRecentClipboardRow()
            refreshUi()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        dismissActivePopup()
        stopAiProcessingAnimations()
        setGenerating(false)
        isAiMode = false
        isClipboardOpen = false
        isNumbersMode = false
        isSymbolsSubmenuOpen = false
        isEmojiMode = false
        isEmojiSearchMode = false
        manualShiftMode = ShiftMode.OFF
        isAutoShiftEnabled = true
        lastShiftTapAtMs = 0L
        pendingAutoCorrection = null
        if (::emojiSearchInput.isInitialized) {
            emojiSearchInput.text?.clear()
        }
        if (::aiPromptInput.isInitialized) {
            aiPromptInput.text?.clear()
        }
    }

    private fun reloadTypingSettings() {
        keyboardLayoutMode = KeyboardModeSettings.loadLayoutMode(this)
        keyboardLanguageMode = KeyboardModeSettings.loadLanguageMode(this)
        keyboardFontMode = KeyboardModeSettings.loadFontMode(this)
        interTypeface = when (keyboardFontMode) {
            KeyboardFontMode.INTER -> ResourcesCompat.getFont(this, R.font.inter_variable)
            KeyboardFontMode.ROBOTO -> Typeface.create("sans-serif", Typeface.NORMAL)
        }

        val storedKey = KeyboardModeSettings.loadGeminiApiKey(this)
        val apiKey = storedKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        geminiClient = GeminiClient(apiKey)
    }

    private fun createKeyboardUiContext(): Context {
        val themeMode = KeyboardModeSettings.loadThemeMode(this)
        if (themeMode == AppThemeMode.SYSTEM) {
            return this
        }

        val targetNightMode = if (themeMode == AppThemeMode.DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }

        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or targetNightMode
        return createConfigurationContext(config)
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )
        if (::row1.isInitialized) {
            refreshAutoShiftFromContextAndRerender()
        }
    }

    private fun reloadBottomModesFromSettings() {
        val (leftOptions, rightOptions) = KeyboardModeSettings.loadBottomSlotOptions(this)
        leftBottomModeOptions = leftOptions.distinct().ifEmpty { listOf(BottomKeyMode.AI, BottomKeyMode.EMOJI) }
        rightBottomModeOptions = rightOptions.distinct().ifEmpty { listOf(BottomKeyMode.CLIPBOARD, BottomKeyMode.EMOJI) }

        val (left, right) = KeyboardModeSettings.load(this)
        leftBottomMode = if (left in leftBottomModeOptions) left else leftBottomModeOptions.first()
        rightBottomMode = if (right in rightBottomModeOptions) right else rightBottomModeOptions.first()
        if (left != leftBottomMode || right != rightBottomMode) {
            KeyboardModeSettings.save(this, leftBottomMode, rightBottomMode)
        }

        if (leftBottomMode != BottomKeyMode.AI) {
            isAiMode = false
        }
        if (leftBottomMode != BottomKeyMode.CLIPBOARD && rightBottomMode != BottomKeyMode.CLIPBOARD) {
            isClipboardOpen = false
        }
        if (!hasEmojiSlot()) {
            isEmojiMode = false
        }
    }

    private fun bindViews(root: View) {
        keyboardRoot = root.findViewById(R.id.keyboardRoot)

        aiQuickActionsRow = root.findViewById(R.id.aiQuickActionsRow)
        aiSummarizeButton = root.findViewById(R.id.aiSummarizeButton)
        aiFixGrammarButton = root.findViewById(R.id.aiFixGrammarButton)
        aiExpandButton = root.findViewById(R.id.aiExpandButton)
        aiPromptRow = root.findViewById(R.id.aiPromptRow)
        aiPromptToggleButton = root.findViewById(R.id.aiPromptToggleButton)
        aiPromptInput = root.findViewById(R.id.aiPromptInput)
        aiPromptShimmer = root.findViewById(R.id.aiPromptShimmer)

        clipboardPanel = root.findViewById(R.id.clipboardPanel)
        clipboardItemsContainer = root.findViewById(R.id.clipboardItemsContainer)

        emojiPanel = root.findViewById(R.id.emojiPanel)
        emojiSearchPill = root.findViewById(R.id.emojiSearchPill)
        emojiSearchIconButton = root.findViewById(R.id.emojiSearchIconButton)
        emojiSuggestionsScroll = root.findViewById(R.id.emojiSuggestionsScroll)
        emojiMostUsedRow = root.findViewById(R.id.emojiMostUsedRow)
        emojiSearchInput = root.findViewById(R.id.emojiSearchInput)
        emojiGridScroll = root.findViewById(R.id.emojiGridScroll)
        emojiRecentColumn = root.findViewById(R.id.emojiRecentColumn)
        emojiRecentDivider = root.findViewById(R.id.emojiRecentDivider)
        emojiGridRow1 = root.findViewById(R.id.emojiGridRow1)
        emojiGridRow2 = root.findViewById(R.id.emojiGridRow2)
        emojiGridRow3 = root.findViewById(R.id.emojiGridRow3)

        recentClipboardRow = root.findViewById(R.id.recentClipboardRow)
        recentClipboardChip = root.findViewById(R.id.recentClipboardChip)
        recentClipboardChevronButton = root.findViewById(R.id.recentClipboardChevronButton)

        keyRowsContainer = root.findViewById(R.id.keyRowsContainer)
        row1 = root.findViewById(R.id.row1)
        row2 = root.findViewById(R.id.row2)
        row3 = root.findViewById(R.id.row3)
        bottomRow = root.findViewById(R.id.bottomRow)

        modeSwitchButton = root.findViewById(R.id.modeSwitchButton)
        aiModeButton = root.findViewById(R.id.aiModeButton)
        spaceButton = root.findViewById(R.id.spaceButton)
        clipboardButton = root.findViewById(R.id.clipboardButton)
        actionButton = root.findViewById(R.id.actionButton)

        keyboardRoot.clipChildren = false
        keyRowsContainer.clipChildren = false
        row1.clipChildren = false
        row2.clipChildren = false
        row3.clipChildren = false
        bottomRow.clipChildren = false

        keyRowsContainer.isMotionEventSplittingEnabled = true
        row1.isMotionEventSplittingEnabled = true
        row2.isMotionEventSplittingEnabled = true
        row3.isMotionEventSplittingEnabled = true
        bottomRow.isMotionEventSplittingEnabled = true

        emojiGridLoadedCount = 0
    }

    private fun applyTypographyAndIcons() {
        keyboardRoot.background = uiDrawable(R.drawable.bg_keyboard_container)
        aiPromptRow.background = uiDrawable(R.drawable.bg_ai_pill)
        aiQuickActionsRow.background = null
        aiSummarizeButton.background = uiDrawable(R.drawable.bg_ai_quick_action)
        aiFixGrammarButton.background = uiDrawable(R.drawable.bg_ai_quick_action)
        aiExpandButton.background = uiDrawable(R.drawable.bg_ai_quick_action)
        aiPromptToggleButton.background = uiDrawable(R.drawable.bg_ai_button)
        modeSwitchButton.background = uiDrawable(R.drawable.bg_special_key)
        aiModeButton.background = uiDrawable(R.drawable.bg_ai_button)
        spaceButton.background = uiDrawable(R.drawable.bg_space_key)
        clipboardButton.background = uiDrawable(R.drawable.bg_special_key)
        actionButton.background = uiDrawable(R.drawable.bg_special_key)
        emojiSearchPill.background = uiDrawable(R.drawable.bg_ai_pill)
        emojiSearchIconButton.background = null
        recentClipboardChip.background = uiDrawable(R.drawable.bg_chip)
        recentClipboardChevronButton.background = null

        applySerifTypeface(modeSwitchButton)
        applyInterTypeface(spaceButton)
        applySerifTypeface(aiSummarizeButton)
        applySerifTypeface(aiFixGrammarButton)
        applySerifTypeface(aiExpandButton)
        applyInterTypeface(aiPromptInput)
        applyInterTypeface(emojiSearchInput)
        applyInterTypeface(recentClipboardChip)
        aiPromptInput.filters = arrayOf(InputFilter.LengthFilter(AI_PILL_CHAR_LIMIT))
        aiSummarizeButton.setTextColor(uiColor(R.color.ai_text))
        aiFixGrammarButton.setTextColor(uiColor(R.color.ai_text))
        aiExpandButton.setTextColor(uiColor(R.color.ai_text))
        modeSwitchButton.setTextColor(uiColor(R.color.key_text))
        spaceButton.setTextColor(uiColor(R.color.space_text))
        recentClipboardChip.setTextColor(uiColor(R.color.key_text))
        aiPromptInput.setTextColor(uiColor(R.color.key_text))
        aiPromptInput.setHintTextColor(uiColor(R.color.ai_hint))
        emojiSearchInput.setTextColor(uiColor(R.color.key_text))
        emojiSearchInput.setHintTextColor(uiColor(R.color.ai_hint))

        modeSwitchButton.contentDescription = "Switch layout"
        aiModeButton.contentDescription = "Left mode key"
        clipboardButton.contentDescription = "Right mode key"
        actionButton.contentDescription = "Action"
        aiPromptToggleButton.contentDescription = "Toggle AI mode"
        emojiSearchIconButton.contentDescription = "Search emoji"
        recentClipboardChip.contentDescription = "Paste recent clipboard"
        recentClipboardChevronButton.contentDescription = "Dismiss recent clipboard"

        setIcon(aiPromptToggleButton, R.drawable.ic_ai_custom, R.color.key_text)
        setIcon(emojiSearchIconButton, R.drawable.ic_smile_lucide, R.color.ai_text)
        setIcon(recentClipboardChevronButton, R.drawable.ic_chevron_down_lucide, R.color.key_text)
        recentClipboardChevronButton.setPadding(0, 0, 0, 0)
        val clipboardIcon = uiDrawable(R.drawable.ic_clipboard_lucide)?.mutate()
        clipboardIcon?.setTint(uiColor(R.color.key_text))
        recentClipboardChip.setCompoundDrawablesRelativeWithIntrinsicBounds(clipboardIcon, null, null, null)
        recentClipboardChip.compoundDrawablePadding = dp(6)
        recentClipboardChip.isSingleLine = true
        recentClipboardChip.ellipsize = TextUtils.TruncateAt.END
        recentClipboardChip.minWidth = 0
        recentClipboardChip.minimumWidth = 0
        updateBottomModeIcons()

        flattenView(aiQuickActionsRow)
        flattenView(aiSummarizeButton)
        flattenView(aiFixGrammarButton)
        flattenView(aiExpandButton)
        flattenView(modeSwitchButton)
        flattenView(spaceButton)
        flattenView(aiPromptInput)
        flattenView(aiPromptToggleButton)
        flattenView(aiModeButton)
        flattenView(clipboardButton)
        flattenView(actionButton)
        flattenView(emojiSearchIconButton)
        flattenView(recentClipboardChip)
        flattenView(recentClipboardChevronButton)
    }

    private fun setupEmojiPanel() {
        emojiSearchInput.doAfterTextChanged {
            if (isEmojiSearchMode) {
                renderEmojiSuggestions()
            }
        }
        emojiGridScroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            val body = emojiGridScroll.getChildAt(0) ?: return@setOnScrollChangeListener
            if (scrollX + emojiGridScroll.width >= body.width - dp(96)) {
                appendEmojiGridChunk(EMOJI_GRID_CHUNK_SIZE)
            }
        }

        bindPressAction(recentClipboardChip) {
            val text = latestClipboardText.orEmpty()
            val imageUri = latestClipboardImageUri
            val pasted = when {
                text.isNotBlank() -> {
                    currentInputConnection?.commitText(text, 1)
                    true
                }
                imageUri != null -> commitRecentClipboardImage(imageUri, latestClipboardImageMimeType)
                else -> false
            }

            if (pasted) {
                latestClipboardDismissed = true
                recentClipboardExpiryJob?.cancel()
                renderRecentClipboardRow()
                refreshUi()
            }
        }

        bindPressAction(recentClipboardChevronButton) {
            latestClipboardDismissed = true
            recentClipboardExpiryJob?.cancel()
            renderRecentClipboardRow()
            refreshUi()
        }

        renderEmojiGrid()
        renderEmojiSuggestions()
        renderRecentClipboardRow()
    }

    private fun bindListeners() {
        bindPressAction(modeSwitchButton) {
            dismissActivePopup()
            if (isEmojiMode) {
                isEmojiMode = false
                isEmojiSearchMode = false
                isNumbersMode = false
                isSymbolsSubmenuOpen = false
                emojiSearchInput.text?.clear()
            } else if (isNumbersMode) {
                isNumbersMode = false
                isSymbolsSubmenuOpen = false
            } else {
                isNumbersMode = true
            }
            renderKeyRows()
            refreshUi()
        }

        configureKeyTouch(
            view = aiModeButton,
            repeatOnHold = false,
            longPressAction = { anchor, x, y ->
                if (!isEmojiMode) {
                    showBottomModePopup(anchor, x, y, true)
                }
            },
            tapOnDown = false
        ) {
            if (isEmojiMode) {
                isEmojiSearchMode = !isEmojiSearchMode
                if (isEmojiSearchMode) {
                    emojiSearchInput.requestFocus()
                    emojiSearchInput.setSelection(emojiSearchInput.text?.length ?: 0)
                } else {
                    emojiSearchInput.text?.clear()
                }
                renderEmojiSuggestions()
                refreshUi()
                return@configureKeyTouch
            }
            performBottomModeTap(leftBottomMode)
        }

        bindPressAction(aiPromptToggleButton) {
            if (!isAiAllowedInCurrentContext()) {
                return@bindPressAction
            }
            toggleAiMode()
        }

        bindPressAction(aiSummarizeButton) {
            if (!isAiAllowedInCurrentContext()) {
                return@bindPressAction
            }
            runQuickAiAction(QuickAiAction.SUMMARIZE)
        }

        bindPressAction(aiFixGrammarButton) {
            if (!isAiAllowedInCurrentContext()) {
                return@bindPressAction
            }
            runQuickAiAction(QuickAiAction.FIX_GRAMMAR)
        }

        bindPressAction(aiExpandButton) {
            if (!isAiAllowedInCurrentContext()) {
                return@bindPressAction
            }
            runQuickAiAction(QuickAiAction.EXPAND)
        }

        configureSpacebarTouch()

        configureKeyTouch(
            view = clipboardButton,
            repeatOnHold = false,
            longPressAction = { anchor, x, y ->
                if (!isEmojiMode) {
                    showBottomModePopup(anchor, x, y, false)
                }
            },
            tapOnDown = false
        ) {
            if (isEmojiMode) {
                toggleEmojiMode()
                return@configureKeyTouch
            }
            performBottomModeTap(rightBottomMode)
        }

        bindPressAction(actionButton) {
            if (isAiMode) {
                submitAiPrompt()
            } else if (isClipboardOpen) {
                deleteOneCharacter()
            } else if (isEmojiMode) {
                deleteOneCharacter()
            } else {
                sendOrEnter()
            }
        }

        aiPromptInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submitAiPrompt()
                true
            } else {
                false
            }
        }
    }

    private fun toggleAiMode() {
        if (!isAiAllowedInCurrentContext()) {
            return
        }
        dismissActivePopup()
        isEmojiMode = false
        isClipboardOpen = false
        isAiMode = !isAiMode
        if (!isAiMode) {
            aiPromptInput.text?.clear()
            setGenerating(false)
        }
        refreshUi()
    }

    private fun toggleEmojiMode() {
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
            renderEmojiGrid()
            renderEmojiSuggestions()
        } else {
            isEmojiSearchMode = false
            emojiSearchInput.text?.clear()
        }
        renderKeyRows()
        refreshUi()
    }

    private fun performBottomModeTap(mode: BottomKeyMode) {
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

    private fun toggleClipboardMode() {
        dismissActivePopup()
        isEmojiMode = false
        isEmojiSearchMode = false
        isAiMode = false
        setGenerating(false)
        isClipboardOpen = !isClipboardOpen
        renderClipboardItems()
        refreshUi()
    }

    private fun refreshUi() {
        renderRecentClipboardRow()
        renderEmojiSuggestions()
        val aiAllowed = isAiAllowedInCurrentContext()
        if (!aiAllowed && isAiMode) {
            isAiMode = false
            aiPromptInput.text?.clear()
            setGenerating(false)
        }

        refreshAutoShiftFromContext()
        setVisibleAnimated(aiQuickActionsRow, isAiMode)
        setVisibleAnimated(aiPromptRow, isAiMode)
        setVisibleAnimated(clipboardPanel, isClipboardOpen && !isEmojiMode)
        setVisibleAnimated(emojiPanel, isEmojiMode)
        setVisibleAnimated(recentClipboardRow, shouldShowRecentClipboardRow())
        setVisibleAnimated(keyRowsContainer, !isClipboardOpen && (!isEmojiMode || isEmojiSearchMode))

        setVisibleAnimated(modeSwitchButton, !isClipboardOpen)
        setVisibleAnimated(aiModeButton, !isClipboardOpen)
        setVisibleAnimated(actionButton, true)
        applyBottomRowLayoutForClipboard(isClipboardOpen)

        modeSwitchButton.text = if (isNumbersMode || isEmojiMode) "ABC" else "123"
        emojiSearchPill.isVisible = isEmojiMode && isEmojiSearchMode
        emojiSuggestionsScroll.isVisible = isEmojiMode && isEmojiSearchMode
        emojiGridScroll.isVisible = isEmojiMode && !isEmojiSearchMode

        if (isEmojiMode) {
            aiModeButton.alpha = 1f
            clipboardButton.alpha = 1f
        } else {
            aiModeButton.alpha = if (isBottomModeSelected(leftBottomMode)) 1f else 0.84f
            clipboardButton.alpha = if (isBottomModeSelected(rightBottomMode)) 1f else 0.84f
        }

        updateBottomModeIcons()
        updateModeSelectionVisuals()
        if (isEmojiMode) {
            setIcon(aiModeButton, R.drawable.ic_search_lucide, R.color.key_text)
            setIcon(clipboardButton, R.drawable.ic_smile_lucide, R.color.key_text)
        }

        if (isAiMode) {
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
        clipboardButton.isEnabled = !isGenerating && !(rightBottomMode == BottomKeyMode.AI && !aiAllowed)
        aiPromptToggleButton.isEnabled = aiAllowed && !isGenerating
        aiPromptInput.isEnabled = aiAllowed && !isGenerating
        aiSummarizeButton.isEnabled = aiAllowed && !isGenerating
        aiFixGrammarButton.isEnabled = aiAllowed && !isGenerating
        aiExpandButton.isEnabled = aiAllowed && !isGenerating

        setGenerating(isGenerating)
    }

    private fun applyBottomRowLayoutForClipboard(clipboardOpen: Boolean) {
        updateBottomKeyLayout(spaceButton, if (clipboardOpen) 7.8f else 5f, marginEndDp = 4)
        updateBottomKeyLayout(clipboardButton, 1f, marginEndDp = 4)
        updateBottomKeyLayout(actionButton, if (clipboardOpen) 1.35f else 1.9f, marginEndDp = 0)
    }

    private fun updateBottomKeyLayout(view: View, weight: Float, marginEndDp: Int) {
        val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
        if (params.weight == weight && params.marginEnd == dp(marginEndDp)) {
            return
        }
        params.weight = weight
        params.marginEnd = dp(marginEndDp)
        view.layoutParams = params
    }

    private fun updateBottomModeIcons() {
        setIcon(aiModeButton, iconResForBottomMode(leftBottomMode), R.color.key_text)
        setIcon(clipboardButton, iconResForBottomMode(rightBottomMode), R.color.key_text)
    }

    private fun iconResForBottomMode(mode: BottomKeyMode): Int {
        return when (mode) {
            BottomKeyMode.AI -> R.drawable.ic_ai_custom
            BottomKeyMode.CLIPBOARD -> R.drawable.ic_clipboard_lucide
            BottomKeyMode.EMOJI -> R.drawable.ic_smile_lucide
        }
    }

    private fun updateModeSelectionVisuals() {
        if (isEmojiMode) {
            styleModeButton(
                button = aiModeButton,
                selected = isEmojiSearchMode,
                selectedBackgroundRes = R.drawable.bg_mode_ai_selected,
                normalBackgroundRes = R.drawable.bg_ai_button
            )
            styleModeButton(
                button = clipboardButton,
                selected = true,
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

    private fun styleModeButton(
        button: ImageButton,
        selected: Boolean,
        selectedBackgroundRes: Int,
        normalBackgroundRes: Int
    ) {
        button.background = uiDrawable(if (selected) selectedBackgroundRes else normalBackgroundRes)
        applyReducedKeyIconOffset(button, selected)
    }

    private fun applyReducedKeyIconOffset(button: ImageButton, selected: Boolean) {
        if (selected) {
            button.setPadding(dp(8), dp(11), dp(8), dp(5))
        } else {
            button.setPadding(dp(8), dp(8), dp(8), dp(8))
        }
    }

    private fun isBottomModeSelected(mode: BottomKeyMode): Boolean {
        return when (mode) {
            BottomKeyMode.AI -> isAiMode && isAiAllowedInCurrentContext()
            BottomKeyMode.CLIPBOARD -> isClipboardOpen
            BottomKeyMode.EMOJI -> isEmojiMode
        }
    }

    private fun setGenerating(generating: Boolean) {
        isGenerating = generating
        val aiAllowed = isAiAllowedInCurrentContext()
        aiPromptInput.isEnabled = aiAllowed && !generating
        actionButton.isEnabled = !generating
        modeSwitchButton.isEnabled = !generating
        aiModeButton.isEnabled = !generating && !(leftBottomMode == BottomKeyMode.AI && !aiAllowed)
        clipboardButton.isEnabled = !generating && !(rightBottomMode == BottomKeyMode.AI && !aiAllowed)
        aiPromptToggleButton.isEnabled = aiAllowed && !generating
        aiSummarizeButton.isEnabled = aiAllowed && !generating
        aiFixGrammarButton.isEnabled = aiAllowed && !generating
        aiExpandButton.isEnabled = aiAllowed && !generating
        emojiSearchInput.isEnabled = !generating
        emojiSearchIconButton.isEnabled = !generating
        recentClipboardChip.isEnabled = !generating
        recentClipboardChevronButton.isEnabled = !generating
        syncAiProcessingAnimations()
    }

    private fun setVisibleAnimated(view: View, visible: Boolean) {
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

    private fun syncAiProcessingAnimations() {
        val shouldAnimate = isGenerating && isAiMode && aiPromptRow.isVisible
        if (shouldAnimate) {
            startAiProcessingAnimations()
        } else {
            stopAiProcessingAnimations()
        }
    }

    private fun startAiProcessingAnimations() {
        if (!::aiPromptShimmer.isInitialized || !::aiPromptInput.isInitialized) {
            return
        }

        if (aiPillShimmerAnimator == null) {
            aiPromptShimmer.post {
                val stripWidth = aiPromptShimmer.width.toFloat().takeIf { it > 0f } ?: dp(84).toFloat()
                val travel = aiPromptRow.width.toFloat().takeIf { it > 0f } ?: return@post
                aiPromptShimmer.layoutParams = aiPromptShimmer.layoutParams.apply {
                    height = aiPromptRow.height
                }
                aiPromptShimmer.isVisible = true
                aiPillShimmerAnimator?.cancel()
                aiPillShimmerAnimator = ValueAnimator.ofFloat(-stripWidth, travel + stripWidth).apply {
                    duration = 1100L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    addUpdateListener { animator ->
                        aiPromptShimmer.translationX = animator.animatedValue as Float
                    }
                    start()
                }
            }
        }

        if (aiTextPulseAnimator == null) {
            val baseColor = uiColor(R.color.key_text)
            val pulseColor = uiColor(R.color.ai_text_shine)
            aiTextPulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), baseColor, pulseColor, baseColor).apply {
                duration = 900L
                repeatCount = ValueAnimator.INFINITE
                addUpdateListener { animator ->
                    aiPromptInput.setTextColor((animator.animatedValue as Int))
                }
                start()
            }
        }
    }

    private fun stopAiProcessingAnimations() {
        aiPillShimmerAnimator?.cancel()
        aiPillShimmerAnimator = null
        if (::aiPromptShimmer.isInitialized) {
            aiPromptShimmer.isVisible = false
            aiPromptShimmer.translationX = 0f
        }

        aiTextPulseAnimator?.cancel()
        aiTextPulseAnimator = null
        if (::aiPromptInput.isInitialized) {
            aiPromptInput.setTextColor(uiColor(R.color.key_text))
        }
    }

    private fun animateAiResultText() {
        if (!::aiPromptInput.isInitialized) {
            return
        }

        aiTextPulseAnimator?.cancel()
        aiTextPulseAnimator = null

        val baseColor = uiColor(R.color.key_text)
        val flashColor = uiColor(R.color.ai_text_shine)
        ValueAnimator.ofObject(ArgbEvaluator(), flashColor, baseColor).apply {
            duration = 420L
            addUpdateListener { animator ->
                aiPromptInput.setTextColor((animator.animatedValue as Int))
            }
            start()
        }
    }

    private fun sendOrEnter() {
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

    private fun resolveContextualActionIcon(): Int {
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

    private fun isAiAllowedInCurrentContext(): Boolean {
        return !isPasswordInputType(currentInputEditorInfo?.inputType ?: 0)
    }

    private fun isPasswordInputType(inputType: Int): Boolean {
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

    private fun submitAiPrompt() {
        if (isGenerating) {
            return
        }
        if (!isAiAllowedInCurrentContext()) {
            return
        }

        val prompt = aiPromptInput.text?.toString()?.trim().orEmpty()
        if (prompt.isBlank()) {
            toast("Enter a prompt first")
            return
        }

        if (!geminiClient.isConfigured) {
            toast("Gemini API key missing. AI is disabled")
            return
        }

        aiPromptInput.error = null
        setGenerating(true)
        serviceScope.launch {
            val result = geminiClient.generateText(
                prompt = prompt,
                systemInstruction = AI_PROMPT_SYSTEM_INSTRUCTION,
                outputCharLimit = AI_REPLY_CHAR_LIMIT
            )
            setGenerating(false)
            result
                .onSuccess { responseText ->
                    val connection = currentInputConnection
                    if (connection != null) {
                        connection.commitText(responseText, 1)
                    } else {
                        aiPromptInput.error = "No text field focused"
                    }
                    aiPromptInput.text?.clear()
                    animateAiResultText()
                }
                .onFailure { error ->
                    val message = error.message ?: "AI request failed"
                    aiPromptInput.error = message
                    toast(message)
                }
        }
    }

    private fun runQuickAiAction(action: QuickAiAction) {
        if (isGenerating) {
            return
        }
        if (!isAiAllowedInCurrentContext()) {
            return
        }

        if (!geminiClient.isConfigured) {
            toast("Gemini API key missing. AI is disabled")
            return
        }

        val sourceText = currentInputConnection
            ?.getSelectedText(0)
            ?.toString()
            ?.trim()
            .orEmpty()

        if (sourceText.isBlank()) {
            toast("Select text first")
            return
        }

        aiPromptInput.error = null
        setGenerating(true)
        serviceScope.launch {
            val prompt = when (action) {
                QuickAiAction.SUMMARIZE -> "Summarize this text:\n\n$sourceText"
                QuickAiAction.FIX_GRAMMAR -> "Fix grammar and spelling while keeping meaning:\n\n$sourceText"
                QuickAiAction.EXPAND -> "Expand this text with more detail but keep same meaning:\n\n$sourceText"
            }

            val result = geminiClient.generateText(
                prompt = prompt,
                systemInstruction = AI_QUICK_ACTION_SYSTEM_INSTRUCTION,
                outputCharLimit = AI_PILL_CHAR_LIMIT
            )
            setGenerating(false)
            result
                .onSuccess { responseText ->
                    aiPromptInput.setText(responseText)
                    aiPromptInput.setSelection(aiPromptInput.text?.length ?: 0)
                    animateAiResultText()

                    val inputConnection = currentInputConnection
                    inputConnection?.commitText(responseText, 1)
                }
                .onFailure { error ->
                    val message = error.message ?: "AI request failed"
                    aiPromptInput.error = message
                    toast(message)
                }
        }
    }

    private fun renderKeyRows() {
        dismissActivePopup()

        row1.removeAllViews()
        row2.removeAllViews()
        row3.removeAllViews()

        if (isNumbersMode) {
            if (isSymbolsSubmenuOpen) {
                addTextKeys(row1, listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆"))
                addTextKeys(row2, listOf("£", "¥", "₩", "^", "°", "{", "}", "\\", "%", "©"))
                addSpecialKey(
                    row = row3,
                    label = "=/<",
                    iconRes = null,
                    iconTintRes = R.color.key_text,
                    backgroundRes = R.drawable.bg_special_key,
                    weight = 1.25f,
                    useSerifTypeface = true,
                    isLast = false
                ) {
                    isSymbolsSubmenuOpen = false
                    renderKeyRows()
                }
                addTextKeys(row3, listOf("<", ">", "[", "]", "_", "+", "!", "?"), includeEndSpacing = true)
            } else {
                addTextKeys(row1, listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"))
                addTextKeys(row2, listOf("@", "#", "€", "-", "&", "_", "+", "(", ")", "/"))
                addSpecialKey(
                    row = row3,
                    label = "=/<",
                    iconRes = null,
                    iconTintRes = R.color.key_text,
                    backgroundRes = R.drawable.bg_special_key,
                    weight = 1.25f,
                    useSerifTypeface = true,
                    isLast = false
                ) {
                    isSymbolsSubmenuOpen = true
                    renderKeyRows()
                }
                addTextKeys(row3, listOf("*", "\"", "'", ";", ":", ",", ".", "="), includeEndSpacing = true)
            }

            addSpecialKey(
                row = row3,
                label = null,
                iconRes = R.drawable.ic_move_left_lucide,
                iconTintRes = R.color.key_text,
                backgroundRes = R.drawable.bg_special_key,
                weight = 1.2f,
                repeatOnHold = true,
                isLast = true
            ) {
                deleteOneCharacter()
            }
            return
        }

        val alphaRow1 = when (keyboardLayoutMode) {
            KeyboardLayoutMode.AZERTY -> AZERTY_ROW_1
            KeyboardLayoutMode.QWERTY -> QWERTY_ROW_1
        }
        val alphaRow2 = when (keyboardLayoutMode) {
            KeyboardLayoutMode.AZERTY -> AZERTY_ROW_2
            KeyboardLayoutMode.QWERTY -> QWERTY_ROW_2
        }
        val alphaRow3 = when (keyboardLayoutMode) {
            KeyboardLayoutMode.AZERTY -> AZERTY_ROW_3
            KeyboardLayoutMode.QWERTY -> QWERTY_ROW_3
        }
        val alphaRow3Weight = if (keyboardLayoutMode == KeyboardLayoutMode.QWERTY) 0.875f else 1f

        val topNumberVariants = alphaRow1.mapIndexed { index, label ->
            label to ((index + 1) % 10).toString()
        }.toMap()

        addTextKeys(row1, alphaRow1, shiftAware = true, topNumberVariants = topNumberVariants)
        addTextKeys(row2, alphaRow2, shiftAware = true)

        addSpecialKey(
            row = row3,
            label = null,
            iconRes = if (isShiftActive()) R.drawable.ic_shift_custom_flipped else R.drawable.ic_shift_custom,
            iconTintRes = R.color.key_text,
            backgroundRes = if (manualShiftMode == ShiftMode.CAPS_LOCK) {
                R.drawable.bg_mode_special_selected
            } else {
                R.drawable.bg_special_key
            },
            weight = 1.2f,
            isLast = false
        ) {
            handleShiftTap()
            renderKeyRows()
        }

        addTextKeys(
            row = row3,
            labels = alphaRow3,
            shiftAware = true,
            includeEndSpacing = true,
            keyWeight = alphaRow3Weight
        )

        addSpecialKey(
            row = row3,
            label = null,
            iconRes = R.drawable.ic_move_left_lucide,
            iconTintRes = R.color.key_text,
            backgroundRes = R.drawable.bg_special_key,
            weight = 1.2f,
            repeatOnHold = true,
            isLast = true
        ) {
            deleteOneCharacter()
        }
    }

    private fun addTextKeys(
        row: LinearLayout,
        labels: List<String>,
        shiftAware: Boolean = false,
        includeEndSpacing: Boolean = false,
        topNumberVariants: Map<String, String> = emptyMap(),
        keyWeight: Float = 1f
    ) {
        labels.forEachIndexed { index, original ->
            val isLast = if (includeEndSpacing) false else index == labels.lastIndex
            val displayLabel = if (shiftAware && isShiftActive()) original.uppercase(Locale.US) else original
            val longPress = buildVariantLongPressAction(
                original = original,
                shiftAware = shiftAware,
                forcedVariant = topNumberVariants[original.lowercase(Locale.US)]
            )

            addSpecialKey(
                row = row,
                label = displayLabel,
                iconRes = null,
                iconTintRes = R.color.key_text,
                backgroundRes = R.drawable.bg_key,
                weight = keyWeight,
                longPressAction = longPress,
                tapOnDown = longPress == null,
                isLast = isLast
            ) {
                val committed = if (shiftAware && isShiftActive()) {
                    original.uppercase(Locale.US)
                } else {
                    original
                }
                commitKeyText(committed)
            }
        }
    }

    private fun buildVariantLongPressAction(
        original: String,
        shiftAware: Boolean,
        forcedVariant: String? = null
    ): ((View, Float, Float) -> Unit)? {
        val key = original.lowercase(Locale.US)
        val variants = linkedSetOf<String>()
        forcedVariant?.let { variants.add(it) }
        VARIANT_MAP[key]?.forEach { variants.add(it) }
        if (variants.isEmpty()) {
            return null
        }
        return { anchor, rawX, rawY ->
            showVariantPopup(
                anchor,
                variants.toList(),
                shiftAware,
                replacePreviousChar = false,
                touchRawX = rawX,
                touchRawY = rawY
            )
        }
    }

    private fun showVariantPopup(
        anchor: View,
        variants: List<String>,
        shiftAware: Boolean,
        replacePreviousChar: Boolean,
        touchRawX: Float,
        touchRawY: Float
    ) {
        dismissActivePopup()

        val options = if (shiftAware && isShiftActive()) {
            variants.map { value ->
                if (value.firstOrNull()?.isLetter() == true) value.uppercase(Locale.US) else value
            }
        } else {
            variants
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_variant_popup)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipChildren = false
        }

        val optionViews = mutableListOf<AppCompatTextView>()
        options.forEach { value ->
            val option = AppCompatTextView(this).apply {
                text = value
                gravity = Gravity.CENTER
                textSize = 17f
                applyInterTypeface(this)
                setTextColor(uiColor(R.color.key_text))
                background = uiDrawable(R.drawable.bg_popup_option)
                minWidth = dp(40)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).also {
                    it.marginEnd = dp(4)
                }
            }
            row.addView(option)
            optionViews.add(option)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(0x00000000))
            elevation = dp(6).toFloat()
            setOnDismissListener {
                if (activePopupWindow === this) {
                    activePopupWindow = null
                    activeVariantSession = null
                }
            }
        }

        activePopupWindow = popup
        activeVariantSession = VariantSelectionSession(
            options = options,
            optionViews = optionViews,
            replacePreviousChar = replacePreviousChar,
            shiftAware = shiftAware,
            selectedIndex = 0
        )

        showPopupNearTouch(anchor, popup, row, touchRawX, touchRawY)
        highlightVariantSelection(0)
    }

    private fun updateVariantSelection(rawX: Float, rawY: Float) {
        val session = activeVariantSession ?: return

        var selected = -1
        session.optionViews.forEachIndexed { index, view ->
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val left = loc[0]
            val top = loc[1]
            val right = left + view.width
            val bottom = top + view.height
            if (rawX.toInt() in left..right && rawY.toInt() in top..bottom) {
                selected = index
            }
        }

        if (selected == -1) {
            var nearestDistance = Float.MAX_VALUE
            session.optionViews.forEachIndexed { index, view ->
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                val centerX = loc[0] + view.width / 2f
                val distance = kotlin.math.abs(centerX - rawX)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    selected = index
                }
            }
        }

        if (selected >= 0 && selected != session.selectedIndex) {
            session.selectedIndex = selected
            highlightVariantSelection(selected)
        }
    }

    private fun highlightVariantSelection(selectedIndex: Int) {
        val session = activeVariantSession ?: return
        session.optionViews.forEachIndexed { index, view ->
            val bg = if (index == selectedIndex) {
                R.drawable.bg_popup_option_selected
            } else {
                R.drawable.bg_popup_option
            }
            view.background = uiDrawable(bg)
        }
    }

    private fun commitSelectedVariant() {
        val session = activeVariantSession ?: return
        val selected = session.options.getOrNull(session.selectedIndex) ?: return

        if (session.replacePreviousChar) {
            deleteOneCharacter()
        }
        commitKeyText(selected)
        dismissActivePopup()
    }

    private fun updateSwipePopupSelection(rawX: Float, rawY: Float) {
        val session = activeSwipePopupSession ?: return

        fun isEnabled(index: Int): Boolean {
            return session.optionEnabled.getOrNull(index) == true
        }

        var selected = -1
        session.optionViews.forEachIndexed { index, view ->
            if (!isEnabled(index)) {
                return@forEachIndexed
            }
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            val left = loc[0]
            val top = loc[1]
            val right = left + view.width
            val bottom = top + view.height
            if (rawX.toInt() in left..right && rawY.toInt() in top..bottom) {
                selected = index
            }
        }

        if (selected == -1) {
            var nearestDistance = Float.MAX_VALUE
            session.optionViews.forEachIndexed { index, view ->
                if (!isEnabled(index)) {
                    return@forEachIndexed
                }
                val loc = IntArray(2)
                view.getLocationOnScreen(loc)
                val centerX = loc[0] + view.width / 2f
                val distance = kotlin.math.abs(centerX - rawX)
                if (distance < nearestDistance) {
                    nearestDistance = distance
                    selected = index
                }
            }
        }

        if (selected >= 0 && selected != session.selectedIndex) {
            session.selectedIndex = selected
            highlightSwipePopupSelection(selected)
        }
    }

    private fun highlightSwipePopupSelection(selectedIndex: Int) {
        val session = activeSwipePopupSession ?: return
        session.optionViews.forEachIndexed { index, view ->
            val selected = index == selectedIndex && session.optionEnabled.getOrNull(index) == true
            view.background = uiDrawable(
                if (selected) R.drawable.bg_popup_option_selected else R.drawable.bg_popup_option
            )
        }
    }

    private fun commitSelectedSwipePopup() {
        val session = activeSwipePopupSession ?: return
        val index = session.selectedIndex
        if (session.optionEnabled.getOrNull(index) == true) {
            session.optionActions.getOrNull(index)?.invoke()
        }
        dismissActivePopup()
    }

    private fun showBottomModePopup(anchor: View, touchRawX: Float, touchRawY: Float, isLeftSlot: Boolean) {
        dismissActivePopup()

        val modeOptions = if (isLeftSlot) leftBottomModeOptions else rightBottomModeOptions
        val options = modeOptions.map { mode ->
            ModeOption(mode, iconResForBottomMode(mode))
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_variant_popup)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val optionViews = mutableListOf<View>()
        val optionActions = mutableListOf<(() -> Unit)?>()
        val optionEnabled = mutableListOf<Boolean>()

        options.forEach { option ->
            val isSelected = if (isLeftSlot) {
                leftBottomMode == option.mode
            } else {
                rightBottomMode == option.mode
            }
            val item = AppCompatImageButton(this).apply {
                background = uiDrawable(
                    if (isSelected) R.drawable.bg_popup_option_selected else R.drawable.bg_popup_option
                )
                setIcon(this, option.iconRes, R.color.key_text)
                flattenView(this)
                contentDescription = option.mode.value
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).also {
                    if (option != options.last()) {
                        it.marginEnd = dp(6)
                    }
                }
            }
            val action: () -> Unit = {
                if (isLeftSlot) {
                    leftBottomMode = option.mode
                    if (leftBottomMode != BottomKeyMode.AI) {
                        isAiMode = false
                    }
                } else {
                    rightBottomMode = option.mode
                }

                if (leftBottomMode != BottomKeyMode.CLIPBOARD && rightBottomMode != BottomKeyMode.CLIPBOARD) {
                    isClipboardOpen = false
                }

                if (!hasEmojiSlot()) {
                    isEmojiMode = false
                }

                KeyboardModeSettings.save(this, leftBottomMode, rightBottomMode)
                refreshUi()
            }
            optionViews.add(item)
            optionActions.add(action)
            optionEnabled.add(true)

            row.addView(item)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(0x00000000))
            elevation = dp(6).toFloat()
            setOnDismissListener {
                if (activePopupWindow === this) {
                    activePopupWindow = null
                    activeSwipePopupSession = null
                }
            }
        }

        activePopupWindow = popup
        showPopupNearTouch(anchor, popup, row, touchRawX, touchRawY)
        activeSwipePopupSession = SwipePopupSession(
            optionViews = optionViews,
            optionActions = optionActions,
            optionEnabled = optionEnabled,
            selectedIndex = 0
        )
        highlightSwipePopupSelection(0)
    }

    private fun showPopupNearTouch(
        anchor: View,
        popup: PopupWindow,
        content: View,
        touchRawX: Float,
        touchRawY: Float
    ) {
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = content.measuredWidth
        val popupHeight = content.measuredHeight

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val anchorLeft = anchorLoc[0]
        val anchorTop = anchorLoc[1]

        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels

        val minX = dp(4)
        val maxX = (screenWidth - popupWidth - dp(4)).coerceAtLeast(minX)
        val absoluteX = (touchRawX.toInt() - popupWidth / 2).coerceIn(minX, maxX)

        val minY = dp(4)
        val maxY = (screenHeight - popupHeight - dp(4)).coerceAtLeast(minY)
        val preferredAbove = touchRawY.toInt() - popupHeight - dp(10)
        val preferredBelow = touchRawY.toInt() + dp(10)
        val absoluteY = when {
            preferredAbove >= minY -> preferredAbove
            preferredBelow <= maxY -> preferredBelow
            else -> preferredAbove.coerceIn(minY, maxY)
        }

        val xOff = absoluteX - anchorLeft
        val yOff = absoluteY - (anchorTop + anchor.height)
        popup.showAsDropDown(anchor, xOff, yOff, Gravity.START)
    }

    private fun dismissActivePopup() {
        activePopupWindow?.dismiss()
        activePopupWindow = null
        activeVariantSession = null
        activeSwipePopupSession = null
    }

    private fun hasEmojiSlot(): Boolean {
        return leftBottomMode == BottomKeyMode.EMOJI || rightBottomMode == BottomKeyMode.EMOJI
    }

    private fun onEmojiChosen(emoji: String) {
        commitKeyText(emoji)
        recordEmojiUsage(emoji)
    }

    private fun filterEmojiCandidates(text: Editable?): List<String> {
        val query = text?.toString()?.trim().orEmpty().lowercase(Locale.US)
        return if (query.isBlank()) {
            allEmojiCatalog
        } else {
            allEmojiCatalog.filter { emoji ->
                emoji.contains(query) || emojiSearchBlob(emoji).contains(query)
            }
        }
    }

    private fun renderEmojiGrid() {
        if (!::emojiGridRow1.isInitialized) {
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

    private fun appendEmojiGridChunk(batchSize: Int) {
        if (!::emojiGridRow1.isInitialized || batchSize <= 0) {
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

    private fun buildEmojiGridKey(
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

    private fun renderEmojiSuggestions() {
        if (!::emojiMostUsedRow.isInitialized) {
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

    private fun emojiSearchBlob(emoji: String): String {
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

    private fun buildEmojiCatalog(): List<String> {
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

    private fun isEmojiCodePoint(codePoint: Int): Boolean {
        return try {
            UCharacter.hasBinaryProperty(codePoint, UProperty.EMOJI)
        } catch (_: Throwable) {
            false
        }
    }

    private fun preloadExtendedEmojiCatalog() {
        serviceScope.launch(Dispatchers.Default) {
            val extended = buildEmojiCatalog()
            launch(Dispatchers.Main) {
                if (extended.size <= allEmojiCatalog.size) {
                    return@launch
                }
                allEmojiCatalog.clear()
                allEmojiCatalog.addAll(extended)
                emojiGridLoadedCount = 0
                if (::emojiGridRow1.isInitialized) {
                    renderEmojiGrid()
                    if (isEmojiSearchMode) {
                        renderEmojiSuggestions()
                    }
                }
            }
        }
    }

    private fun recordEmojiUsage(emoji: String) {
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

    private fun loadEmojiUsage() {
        val prefs = getSharedPreferences(KeyboardModeSettings.PREFS_NAME, MODE_PRIVATE)

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

    private fun saveEmojiUsage() {
        val countsJson = JSONObject().apply {
            emojiUsageCounts.forEach { (emoji, count) -> put(emoji, count) }
        }
        val recentsJson = JSONArray().apply {
            emojiRecents.forEach { put(it) }
        }

        getSharedPreferences(KeyboardModeSettings.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_EMOJI_COUNTS_JSON, countsJson.toString())
            .putString(KEY_EMOJI_RECENTS_JSON, recentsJson.toString())
            .apply()
    }

    private fun renderRecentClipboardRow() {
        if (!::recentClipboardChip.isInitialized) {
            return
        }
        val text = latestClipboardText.orEmpty().replace('\n', ' ')
        val imageUri = latestClipboardImageUri
        if (text.isBlank() && imageUri == null) {
            return
        }

        val isImage = imageUri != null && text.isBlank()
        val display = if (isImage) {
            "Image copied"
        } else if (text.length > RECENT_CLIPBOARD_PREVIEW_CHAR_LIMIT) {
            "${text.take(RECENT_CLIPBOARD_PREVIEW_CHAR_LIMIT - 1)}…"
        } else {
            text
        }
        recentClipboardChip.text = display
        recentClipboardChip.contentDescription = if (isImage) {
            "Paste recent clipboard image"
        } else {
            "Paste recent clipboard text"
        }

        val chipDrawable = if (isImage) {
            latestClipboardImagePreview?.let { bitmap ->
                BitmapDrawable(resources, bitmap)
            } ?: uiDrawable(R.drawable.ic_clipboard_lucide)?.mutate()?.apply {
                setTint(uiColor(R.color.key_text))
            }
        } else {
            uiDrawable(R.drawable.ic_clipboard_lucide)?.mutate()?.apply {
                setTint(uiColor(R.color.key_text))
            }
        }
        recentClipboardChip.setCompoundDrawablesRelativeWithIntrinsicBounds(chipDrawable, null, null, null)
        recentClipboardChip.compoundDrawablePadding = dp(6)
        recentClipboardChip.minWidth = 0
        recentClipboardChip.minimumWidth = 0
        (recentClipboardChip.layoutParams as? LinearLayout.LayoutParams)?.width = ViewGroup.LayoutParams.WRAP_CONTENT
        if (::keyboardRoot.isInitialized && keyboardRoot.width > 0) {
            recentClipboardChip.maxWidth = keyboardRoot.width - dp(58)
        } else {
            recentClipboardChip.maxWidth = dp(280)
        }
    }

    private fun shouldShowRecentClipboardRow(): Boolean {
        if (isAiMode || isClipboardOpen || isEmojiMode) {
            return false
        }
        val hasText = latestClipboardText.orEmpty().isNotBlank()
        val hasImage = latestClipboardImageUri != null
        if ((!hasText && !hasImage) || latestClipboardDismissed) {
            return false
        }
        return (System.currentTimeMillis() - latestClipboardAtMs) <= RECENT_CLIPBOARD_WINDOW_MS
    }

    private fun isEmojiSearchActive(): Boolean {
        return isEmojiMode && isEmojiSearchMode
    }

    private fun isShiftActive(): Boolean {
        return manualShiftMode != ShiftMode.OFF || isAutoShiftEnabled
    }

    private fun handleShiftTap() {
        val now = System.currentTimeMillis()
        val isDoubleTap = lastShiftTapAtMs != 0L && (now - lastShiftTapAtMs) <= SHIFT_DOUBLE_TAP_TIMEOUT_MS

        when {
            manualShiftMode == ShiftMode.CAPS_LOCK -> {
                manualShiftMode = ShiftMode.OFF
                lastShiftTapAtMs = 0L
                refreshAutoShiftFromContext()
            }

            manualShiftMode == ShiftMode.OFF && isAutoShiftEnabled -> {
                if (isDoubleTap) {
                    manualShiftMode = ShiftMode.CAPS_LOCK
                    isAutoShiftEnabled = false
                    lastShiftTapAtMs = 0L
                } else {
                    isAutoShiftEnabled = false
                    manualShiftMode = ShiftMode.OFF
                    lastShiftTapAtMs = now
                }
            }

            isDoubleTap -> {
                manualShiftMode = ShiftMode.CAPS_LOCK
                isAutoShiftEnabled = false
                lastShiftTapAtMs = 0L
            }

            manualShiftMode == ShiftMode.ONE_SHOT -> {
                manualShiftMode = ShiftMode.OFF
                lastShiftTapAtMs = now
                refreshAutoShiftFromContext()
            }

            else -> {
                manualShiftMode = ShiftMode.ONE_SHOT
                isAutoShiftEnabled = false
                lastShiftTapAtMs = now
            }
        }
    }

    private fun consumeOneShotShiftIfNeeded(committedText: String): Boolean {
        if (manualShiftMode == ShiftMode.ONE_SHOT && committedText.any { it.isLetter() }) {
            manualShiftMode = ShiftMode.OFF
            lastShiftTapAtMs = 0L
            return true
        }
        return false
    }

    private fun refreshAutoShiftFromContext() {
        if (manualShiftMode != ShiftMode.OFF || isNumbersMode || isEmojiMode || isClipboardOpen || isAiMode) {
            isAutoShiftEnabled = false
            return
        }

        val textBeforeCursor = currentInputConnection
            ?.getTextBeforeCursor(AUTO_SHIFT_CONTEXT_WINDOW, 0)
            ?.toString()
            .orEmpty()

        val trimmed = textBeforeCursor.trimEnd()
        isAutoShiftEnabled = when {
            trimmed.isEmpty() -> true
            else -> AUTO_SHIFT_SENTENCE_ENDERS.contains(trimmed.last())
        }
    }

    private fun refreshAutoShiftFromContextAndRerender(forceRerender: Boolean = false) {
        val previous = isAutoShiftEnabled
        refreshAutoShiftFromContext()
        if ((forceRerender || previous != isAutoShiftEnabled) && !isNumbersMode && !isEmojiMode && !isClipboardOpen) {
            renderKeyRows()
        }
    }

    private fun applyInterTypeface(view: TextView) {
        interTypeface?.let { view.typeface = it }
    }

    private fun applySerifTypeface(view: TextView) {
        view.typeface = Typeface.SERIF
    }

    private fun uiColor(colorRes: Int): Int {
        return ContextCompat.getColor(keyboardUiContext, colorRes)
    }

    private fun uiDrawable(drawableRes: Int) =
        AppCompatResources.getDrawable(keyboardUiContext, drawableRes)

    private fun bindPressAction(view: View, onTap: () -> Unit) {
        configureKeyTouch(view, repeatOnHold = false, longPressAction = null, tapOnDown = false, onTap = onTap)
    }

    private fun configureSpacebarTouch() {
        var lastRawX = 0f
        var downRawX = 0f
        var downRawY = 0f
        var accumulatedDx = 0f
        var movedCursor = false
        var cursorDragEnabled = false
        var cursorModeActive = false
        var baseAlpha = 1f
        val stepPx = dp(SPACEBAR_CURSOR_STEP_DP).toFloat()
        val deadzonePx = dp(SPACEBAR_CURSOR_DEADZONE_DP).toFloat()

        spaceButton.setOnTouchListener { touchedView, event ->
            if (!touchedView.isEnabled) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cursorDragEnabled = !isAiMode && !isEmojiSearchActive() && !isClipboardOpen
                    cursorModeActive = false
                    lastRawX = event.rawX
                    downRawX = event.rawX
                    downRawY = event.rawY
                    accumulatedDx = 0f
                    movedCursor = false
                    baseAlpha = touchedView.alpha

                    touchedView.isPressed = true
                    touchedView.alpha = (baseAlpha * KEY_PRESSED_ALPHA).coerceAtLeast(MIN_PRESSED_ALPHA)
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(KEY_PRESS_SCALE)
                        .scaleY(KEY_PRESS_SCALE)
                        .setDuration(KEY_PRESS_ANIM_MS)
                        .start()
                    performKeyHaptic(touchedView)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (cursorDragEnabled) {
                        if (!cursorModeActive) {
                            val totalDx = event.rawX - downRawX
                            val totalDy = event.rawY - downRawY
                            if (kotlin.math.abs(totalDx) >= deadzonePx &&
                                kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)
                            ) {
                                cursorModeActive = true
                                lastRawX = event.rawX
                                accumulatedDx = 0f
                            }
                        } else {
                            val dx = event.rawX - lastRawX
                            lastRawX = event.rawX
                            accumulatedDx += dx

                            while (accumulatedDx >= stepPx) {
                                moveCursorRight()
                                accumulatedDx -= stepPx
                                movedCursor = true
                            }
                            while (accumulatedDx <= -stepPx) {
                                moveCursorLeft()
                                accumulatedDx += stepPx
                                movedCursor = true
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    touchedView.isPressed = false
                    touchedView.alpha = baseAlpha
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(KEY_RELEASE_ANIM_MS)
                        .start()

                    if (!movedCursor) {
                        handleSpaceTap()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchedView.isPressed = false
                    touchedView.alpha = baseAlpha
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(KEY_RELEASE_ANIM_MS)
                        .start()
                    true
                }

                else -> false
            }
        }
    }

    private fun handleSpaceTap() {
        if (isAiMode) {
            appendPromptText(" ")
        } else if (isEmojiSearchActive()) {
            appendEmojiSearchText(" ")
        } else {
            commitKeyText(" ")
        }
    }

    private fun moveCursorLeft() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
    }

    private fun moveCursorRight() {
        val inputConnection = currentInputConnection ?: return
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
        inputConnection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    private fun setIcon(button: ImageButton, drawableRes: Int, tintColorRes: Int) {
        val drawable = uiDrawable(drawableRes)?.mutate() ?: return
        drawable.setTint(uiColor(tintColorRes))
        button.setImageDrawable(drawable)
        button.scaleType = ImageView.ScaleType.CENTER_INSIDE
        button.setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    private fun configureKeyTouch(
        view: View,
        repeatOnHold: Boolean,
        longPressAction: ((View, Float, Float) -> Unit)?,
        tapOnDown: Boolean = true,
        onTap: () -> Unit
    ) {
        var longPressTriggered = false
        var currentRawX = 0f
        var currentRawY = 0f
        var longPressStartX = 0f
        var longPressStartY = 0f
        var baseAlpha = 1f
        var repeatRunnable: Runnable? = null
        var longPressRunnable: Runnable? = null

        view.setOnTouchListener { touchedView, event ->
            if (!touchedView.isEnabled) {
                return@setOnTouchListener false
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    currentRawX = event.rawX
                    currentRawY = event.rawY
                    longPressStartX = event.rawX
                    longPressStartY = event.rawY
                    longPressTriggered = false
                    baseAlpha = touchedView.alpha

                    touchedView.isPressed = true
                    touchedView.alpha = (baseAlpha * KEY_PRESSED_ALPHA).coerceAtLeast(MIN_PRESSED_ALPHA)
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(KEY_PRESS_SCALE)
                        .scaleY(KEY_PRESS_SCALE)
                        .setDuration(KEY_PRESS_ANIM_MS)
                        .start()
                    performKeyHaptic(touchedView)

                    if (repeatOnHold) {
                        onTap()
                        repeatRunnable = object : Runnable {
                            override fun run() {
                                onTap()
                                touchedView.postDelayed(this, KEY_REPEAT_INTERVAL_MS)
                            }
                        }
                        touchedView.postDelayed(repeatRunnable!!, KEY_REPEAT_START_DELAY_MS)
                    } else if (longPressAction != null) {
                        if (tapOnDown) {
                            onTap()
                        }
                        longPressRunnable = Runnable {
                            longPressTriggered = true
                            longPressAction.invoke(touchedView, currentRawX, currentRawY)
                        }
                        val longPressDelay = minOf(
                            ViewConfiguration.getLongPressTimeout().toLong(),
                            VARIANT_LONG_PRESS_TIMEOUT_MS
                        )
                        touchedView.postDelayed(longPressRunnable!!, longPressDelay)
                    } else if (tapOnDown) {
                        onTap()
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    currentRawX = event.rawX
                    currentRawY = event.rawY
                    if (longPressTriggered) {
                        val dx = currentRawX - longPressStartX
                        val dy = currentRawY - longPressStartY
                        val distance = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (distance < dp(HOLD_SELECTION_DEADZONE_DP).toFloat()) {
                            return@setOnTouchListener true
                        }
                        if (activeVariantSession != null) {
                            updateVariantSelection(currentRawX, currentRawY)
                        } else if (activeSwipePopupSession != null) {
                            updateSwipePopupSelection(currentRawX, currentRawY)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    touchedView.isPressed = false
                    touchedView.alpha = baseAlpha
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(KEY_RELEASE_ANIM_MS)
                        .start()

                    repeatRunnable?.let { touchedView.removeCallbacks(it) }
                    repeatRunnable = null
                    longPressRunnable?.let { touchedView.removeCallbacks(it) }
                    longPressRunnable = null

                    if (longPressTriggered) {
                        if (activeVariantSession != null) {
                            commitSelectedVariant()
                            return@setOnTouchListener true
                        }
                        if (activeSwipePopupSession != null) {
                            commitSelectedSwipePopup()
                            return@setOnTouchListener true
                        }
                    }

                    if (!repeatOnHold && !tapOnDown && !longPressTriggered) {
                        onTap()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchedView.isPressed = false
                    touchedView.alpha = baseAlpha
                    touchedView.animate().cancel()
                    touchedView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(KEY_RELEASE_ANIM_MS)
                        .start()

                    repeatRunnable?.let { touchedView.removeCallbacks(it) }
                    repeatRunnable = null
                    longPressRunnable?.let { touchedView.removeCallbacks(it) }
                    longPressRunnable = null
                    if (longPressTriggered) {
                        if (activeVariantSession != null) {
                            commitSelectedVariant()
                        } else if (activeSwipePopupSession != null) {
                            commitSelectedSwipePopup()
                        } else {
                            dismissActivePopup()
                        }
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun performKeyHaptic(view: View) {
        val performed = view.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
        if (!performed) {
            try {
                val deviceVibrator = vibrator
                if (deviceVibrator?.hasVibrator() == true) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        deviceVibrator.vibrate(
                            VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        deviceVibrator.vibrate(10L)
                    }
                }
            } catch (_: Exception) {
                // Keep typing responsive.
            }
        }
    }

    private fun addSpecialKey(
        row: LinearLayout,
        label: String?,
        iconRes: Int?,
        iconTintRes: Int,
        backgroundRes: Int,
        weight: Float,
        iconRotation: Float = 0f,
        repeatOnHold: Boolean = false,
        longPressAction: ((View, Float, Float) -> Unit)? = null,
        tapOnDown: Boolean = true,
        keyHeightDp: Int = 52,
        useSerifTypeface: Boolean = false,
        isLast: Boolean,
        onTap: () -> Unit
    ) {
        val keyView: View = if (iconRes != null) {
            AppCompatImageButton(this).apply {
                background = uiDrawable(backgroundRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setIcon(this, iconRes, iconTintRes)
                applyReducedKeyIconOffset(this, backgroundRes == R.drawable.bg_mode_special_selected)
                rotation = iconRotation
                flattenView(this)
            }
        } else {
            AppCompatButton(this).apply {
                text = label.orEmpty()
                textSize = 15f
                if (useSerifTypeface) {
                    applySerifTypeface(this)
                } else {
                    applyInterTypeface(this)
                }
                setTextColor(uiColor(R.color.key_text))
                background = uiDrawable(backgroundRes)
                setAllCaps(false)
                gravity = Gravity.CENTER
                flattenView(this)
            }
        }

        configureKeyTouch(
            view = keyView,
            repeatOnHold = repeatOnHold,
            longPressAction = longPressAction,
            tapOnDown = tapOnDown,
            onTap = onTap
        )

        val params = LinearLayout.LayoutParams(0, dp(keyHeightDp), weight)
        if (!isLast) {
            params.marginEnd = dp(4)
        }
        keyView.layoutParams = params
        row.addView(keyView)
    }

    private fun flattenView(view: View) {
        view.stateListAnimator = null
        view.elevation = 0f
        view.translationZ = 0f
        view.isSoundEffectsEnabled = false
        ViewCompat.setBackgroundTintList(view, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.foreground = null
        }
        if (view is TextView) {
            view.includeFontPadding = false
            view.setShadowLayer(0f, 0f, 0f, 0)
        }
    }

    private fun deleteOneCharacter() {
        if (isAiMode && ::aiPromptInput.isInitialized) {
            val editable = aiPromptInput.text
            val start = aiPromptInput.selectionStart
            val end = aiPromptInput.selectionEnd
            if (!editable.isNullOrEmpty() && start >= 0 && end >= 0 && start != end) {
                val min = minOf(start, end)
                val max = maxOf(start, end)
                editable.delete(min, max)
            } else if (!editable.isNullOrEmpty()) {
                editable.delete(editable.length - 1, editable.length)
            }
            return
        }
        if (isEmojiSearchActive()) {
            val editable = emojiSearchInput.text
            val start = emojiSearchInput.selectionStart
            val end = emojiSearchInput.selectionEnd
            if (!editable.isNullOrEmpty() && start >= 0 && end >= 0 && start != end) {
                val min = minOf(start, end)
                val max = maxOf(start, end)
                editable.delete(min, max)
            } else if (!editable.isNullOrEmpty()) {
                editable.delete(editable.length - 1, editable.length)
            }
            return
        }
        if (tryRevertLastAutoCorrection()) {
            refreshAutoShiftFromContextAndRerender()
            return
        }
        val inputConnection = currentInputConnection ?: return
        val selectedText = inputConnection.getSelectedText(0)
        if (!selectedText.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            deletePreviousGrapheme(inputConnection)
        }
        pendingAutoCorrection = null
        refreshAutoShiftFromContextAndRerender()
    }

    private fun deletePreviousGrapheme(inputConnection: InputConnection) {
        val beforeCursor = inputConnection
            .getTextBeforeCursor(GRAPHEME_DELETE_CONTEXT_WINDOW, 0)
            ?.toString()
            .orEmpty()
        if (beforeCursor.isEmpty()) {
            return
        }

        val charsToDelete = previousGraphemeSize(beforeCursor)
        inputConnection.deleteSurroundingText(charsToDelete, 0)
    }

    private fun previousGraphemeSize(text: String): Int {
        if (text.isEmpty()) {
            return 1
        }
        return try {
            val breaker = BreakIterator.getCharacterInstance(Locale.getDefault())
            breaker.setText(text)
            val end = breaker.last()
            val start = breaker.previous()
            when {
                end == BreakIterator.DONE -> 1
                start == BreakIterator.DONE -> end.coerceAtLeast(1)
                else -> (end - start).coerceAtLeast(1)
            }
        } catch (_: Throwable) {
            val codePoint = text.codePointBefore(text.length)
            Character.charCount(codePoint).coerceAtLeast(1)
        }
    }

    private fun commitKeyText(text: String) {
        if (isAiMode) {
            appendPromptText(text)
            return
        }
        if (isEmojiSearchActive()) {
            appendEmojiSearchText(text)
            return
        }

        val inputConnection = currentInputConnection ?: return
        var autoCorrection: AutoCorrectionResult? = null
        if (text.length == 1 && AUTOCORRECT_TRIGGER_DELIMITERS.contains(text[0])) {
            autoCorrection = applyAutoCorrectionBeforeDelimiter(inputConnection)
        }

        inputConnection.commitText(text, 1)
        var committedSuffix = text
        if (text.length == 1 && AUTO_SPACE_PUNCTUATION.contains(text[0])) {
            val beforeCursor = inputConnection.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (!beforeCursor.endsWith(" ")) {
                inputConnection.commitText(" ", 1)
                committedSuffix += " "
            }
        }
        pendingAutoCorrection = if (autoCorrection != null) {
            AutoCorrectionUndo(
                originalWord = autoCorrection.originalWord,
                correctedWord = autoCorrection.correctedWord,
                committedSuffix = committedSuffix
            )
        } else {
            null
        }
        val consumedOneShot = consumeOneShotShiftIfNeeded(text)
        refreshAutoShiftFromContextAndRerender(consumedOneShot)
    }

    private fun tryRevertLastAutoCorrection(): Boolean {
        val correction = pendingAutoCorrection ?: return false
        val inputConnection = currentInputConnection ?: return false

        val probeSize = correction.correctedWord.length + correction.committedSuffix.length + 8
        val beforeCursor = inputConnection.getTextBeforeCursor(probeSize, 0)?.toString().orEmpty()
        if (!beforeCursor.endsWith(correction.correctedWord + correction.committedSuffix)) {
            pendingAutoCorrection = null
            return false
        }

        inputConnection.deleteSurroundingText(
            correction.correctedWord.length + correction.committedSuffix.length,
            0
        )
        inputConnection.commitText(correction.originalWord + correction.committedSuffix, 1)
        recordRejectedCorrection(correction.originalWord, correction.correctedWord)
        pendingAutoCorrection = null
        return true
    }

    private fun resetLexicons() {
        englishLexicon = buildLexicon(ENGLISH_WORDS)
        frenchLexicon = buildLexicon(FRENCH_WORDS)
    }

    private fun preloadLexiconsFromAssets() {
        serviceScope.launch(Dispatchers.Default) {
            val englishExtra = loadWordsFromAsset("dictionaries/en_words.txt")
            if (englishExtra.isNotEmpty()) {
                englishLexicon = buildLexicon(englishLexicon.words + englishExtra)
            }

            val frenchExtra = loadWordsFromAsset("dictionaries/fr_words.txt")
            if (frenchExtra.isNotEmpty()) {
                frenchLexicon = buildLexicon(frenchLexicon.words + frenchExtra)
            }
        }
    }

    private fun loadWordsFromAsset(path: String): Set<String> {
        return try {
            assets.open(path).bufferedReader().useLines { sequence ->
                sequence
                    .map { it.trim().lowercase(Locale.US) }
                    .filter { it.length in 2..24 }
                    .filter { ASSET_WORD_REGEX.matches(it) }
                    .toSet()
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun buildLexicon(words: Collection<String>): Lexicon {
        if (words.isEmpty()) {
            return Lexicon.empty()
        }

        val normalizedWords = LinkedHashSet<String>(words.size)
        val foldedWords = HashSet<String>(words.size)
        val byFirst = HashMap<Char, MutableList<String>>()
        val foldedToWord = HashMap<String, String>()

        words.forEach { raw ->
            val word = raw.trim().lowercase(Locale.US)
            if (word.length !in 2..24 || !ASSET_WORD_REGEX.matches(word)) {
                return@forEach
            }
            normalizedWords.add(word)

            val folded = foldWord(word)
            if (folded.isBlank()) {
                return@forEach
            }
            foldedWords.add(folded)
            byFirst.getOrPut(folded.first()) { mutableListOf() }.add(word)

            val existing = foldedToWord[folded]
            if (existing == null || word.length < existing.length) {
                foldedToWord[folded] = word
            }
        }

        val indexed = byFirst.mapValues { (_, list) ->
            list.distinct().sortedWith(compareBy<String> { it.length }.thenBy { it })
        }

        return Lexicon(
            words = normalizedWords,
            foldedWords = foldedWords,
            byFirst = indexed,
            foldedToWord = foldedToWord
        )
    }

    private fun applyAutoCorrectionBeforeDelimiter(inputConnection: InputConnection): AutoCorrectionResult? {
        val beforeCursor = inputConnection.getTextBeforeCursor(AUTOCORRECT_CONTEXT_WINDOW, 0)?.toString().orEmpty()
        val sourceWord = extractTrailingWord(beforeCursor) ?: return null
        val normalizedSource = normalizeWord(sourceWord)
        if (normalizedSource.length < 2) {
            return null
        }

        if (isKnownWord(normalizedSource)) {
            return null
        }

        val contextLanguage = detectContextLanguage(beforeCursor)

        // Apply explicit typo overrides first for high-confidence mistakes.
        var directFix: String? = null
        var directFixScore = Int.MAX_VALUE
        listOf(
            KeyboardLanguageMode.FRENCH to FRENCH_TYPOS,
            KeyboardLanguageMode.ENGLISH to ENGLISH_TYPOS
        ).forEach { (language, typoMap) ->
            if (!isLanguageEnabled(language)) {
                return@forEach
            }
            val candidate = typoMap[normalizedSource] ?: return@forEach
            val normalizedCandidate = normalizeWord(candidate)
            if (isCorrectionSuppressed(normalizedSource, normalizedCandidate)) {
                return@forEach
            }
            val score = languageBiasPenalty(language, contextLanguage)
            if (score < directFixScore) {
                directFixScore = score
                directFix = candidate
            }
        }

        directFix?.let { value ->
            val correctedWord = applyWordCase(value, sourceWord)
            if (!correctedWord.equals(sourceWord, ignoreCase = true)) {
                inputConnection.deleteSurroundingText(sourceWord.length, 0)
                inputConnection.commitText(correctedWord, 1)
                return AutoCorrectionResult(sourceWord, correctedWord)
            }
        }

        val rawBest = findBestDictionaryCorrection(normalizedSource, contextLanguage) ?: return null
        val correctedWord = applyWordCase(rawBest, sourceWord)
        if (correctedWord.equals(sourceWord, ignoreCase = true)) {
            return null
        }

        inputConnection.deleteSurroundingText(sourceWord.length, 0)
        inputConnection.commitText(correctedWord, 1)
        return AutoCorrectionResult(sourceWord, correctedWord)
    }

    private fun findBestDictionaryCorrection(
        source: String,
        contextLanguage: KeyboardLanguageMode?
    ): String? {
        val normalizedSource = normalizeWord(source)
        val foldedSource = foldWord(normalizedSource)
        if (foldedSource.length < 2) {
            return null
        }

        val variants = buildAutoCorrectionVariants(normalizedSource)
        if (variants.isEmpty()) {
            return null
        }

        var best: DictionaryCorrectionCandidate? = null
        var secondBest: DictionaryCorrectionCandidate? = null

        fun considerCandidate(candidate: DictionaryCorrectionCandidate) {
            if (isBetterDictionaryCandidate(candidate, best)) {
                if (best?.word != candidate.word) {
                    secondBest = best
                }
                best = candidate
                return
            }
            if (best?.word == candidate.word) {
                return
            }
            if (isBetterDictionaryCandidate(candidate, secondBest)) {
                secondBest = candidate
            }
        }

        // Fast-path for repeated letter noise.
        findRepeatedLetterCorrection(normalizedSource, contextLanguage)?.let { quick ->
            if (!isCorrectionSuppressed(normalizedSource, quick)) {
                val foldedQuick = foldWord(quick)
                val rawDistance = levenshteinDistanceBounded(
                    foldedSource,
                    foldedQuick,
                    computeRawDistanceLimit(normalizedSource.length, 2)
                )
                val normalizedRaw = if (rawDistance == Int.MAX_VALUE) 6 else rawDistance
                val candidate = DictionaryCorrectionCandidate(
                    word = quick,
                    score = 130 + normalizedRaw * 8 + quick.length,
                    language = null,
                    variantPenalty = 2,
                    editDistance = normalizedRaw,
                    prefixLength = commonPrefixLength(foldedSource, foldedQuick)
                )
                considerCandidate(candidate)
            }
        }

        listOf(
            KeyboardLanguageMode.FRENCH to frenchLexicon,
            KeyboardLanguageMode.ENGLISH to englishLexicon
        ).forEach { (language, lexicon) ->
            if (!isLanguageEnabled(language)) {
                return@forEach
            }
            val languagePenalty = languageBiasPenalty(language, contextLanguage)

            variants.forEach { variant ->
                val foldedVariant = foldWord(variant.word)
                if (foldedVariant.length < 2) {
                    return@forEach
                }

                val exactWord = lexicon.foldedToWord[foldedVariant]
                if (!exactWord.isNullOrBlank() && !isCorrectionSuppressed(normalizedSource, exactWord)) {
                    val rawDistance = levenshteinDistanceBounded(
                        foldedSource,
                        foldWord(exactWord),
                        computeRawDistanceLimit(normalizedSource.length, variant.penalty)
                    )
                    val normalizedRaw = if (rawDistance == Int.MAX_VALUE) 8 else rawDistance
                    val score = languagePenalty * 80 +
                        variant.penalty * 20 +
                        normalizedRaw * 9 +
                        coreLexiconPenalty(language, exactWord)
                    val candidate = DictionaryCorrectionCandidate(
                        word = exactWord,
                        score = score,
                        language = language,
                        variantPenalty = variant.penalty,
                        editDistance = normalizedRaw,
                        prefixLength = commonPrefixLength(foldedSource, foldWord(exactWord))
                    )
                    considerCandidate(candidate)
                }

                val bucket = lexicon.byFirst[foldedVariant.first()] ?: return@forEach
                val editLimit = computeVariantDistanceLimit(variant.word.length, variant.penalty)
                bucket.forEach { candidateWord ->
                    if (isCorrectionSuppressed(normalizedSource, candidateWord)) {
                        return@forEach
                    }

                    val lengthGap = kotlin.math.abs(candidateWord.length - variant.word.length)
                    if (lengthGap > editLimit + 1) {
                        return@forEach
                    }

                    val foldedCandidate = foldWord(candidateWord)
                    val prefixLength = commonPrefixLength(foldedVariant, foldedCandidate)
                    if (foldedVariant.length >= 6 && prefixLength < 1) {
                        return@forEach
                    }
                    if (foldedVariant.length >= 8 && prefixLength < 2) {
                        return@forEach
                    }

                    val variantDistance = levenshteinDistanceBounded(
                        foldedVariant,
                        foldedCandidate,
                        editLimit
                    )
                    if (variantDistance == Int.MAX_VALUE) {
                        return@forEach
                    }

                    val rawDistance = levenshteinDistanceBounded(
                        foldedSource,
                        foldedCandidate,
                        computeRawDistanceLimit(normalizedSource.length, variant.penalty)
                    )
                    val normalizedRawDistance = if (rawDistance == Int.MAX_VALUE) {
                        computeRawDistanceLimit(normalizedSource.length, variant.penalty) + 2
                    } else {
                        rawDistance
                    }

                    val score = languagePenalty * 90 +
                        variant.penalty * 18 +
                        variantDistance * 17 +
                        normalizedRawDistance * 7 +
                        lengthGap * 4 -
                        prefixLength * 6 +
                        coreLexiconPenalty(language, candidateWord)

                    val candidate = DictionaryCorrectionCandidate(
                        word = candidateWord,
                        score = score,
                        language = language,
                        variantPenalty = variant.penalty,
                        editDistance = variantDistance,
                        prefixLength = prefixLength
                    )
                    considerCandidate(candidate)
                }
            }
        }

        val topCandidate = best ?: return null
        if (!isDictionaryCandidateConfident(normalizedSource, topCandidate, secondBest)) {
            return null
        }

        val selected = topCandidate.word
        return if (selected.equals(normalizedSource, ignoreCase = true)) {
            null
        } else {
            selected
        }
    }

    private fun buildAutoCorrectionVariants(source: String): List<AutoCorrectionVariant> {
        val variants = linkedMapOf<String, Int>()

        fun addVariant(word: String, penalty: Int) {
            val normalized = normalizeWord(word)
            if (normalized.length < 2) {
                return
            }
            val existing = variants[normalized]
            if (existing == null || penalty < existing) {
                variants[normalized] = penalty
            }
        }

        addVariant(source, 0)

        val collapsedTwo = collapseRepeats(source, 2)
        val collapsedOne = collapseRepeats(source, 1)
        if (collapsedTwo != source) {
            addVariant(collapsedTwo, 1)
        }
        if (collapsedOne != source) {
            addVariant(collapsedOne, 2)
        }

        val firstPass = variants.entries.map { AutoCorrectionVariant(it.key, it.value) }
        firstPass.forEach { seed ->
            expandSuffixRepairVariants(seed.word, seed.penalty, ::addVariant)
        }

        val secondPass = variants.entries.map { AutoCorrectionVariant(it.key, it.value) }
        secondPass.forEach { seed ->
            if (seed.penalty <= 3) {
                expandSuffixRepairVariants(seed.word, seed.penalty + 1, ::addVariant)
            }
        }

        return variants.entries
            .sortedWith(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key.length })
            .take(MAX_AUTOCORRECT_VARIANTS)
            .map { AutoCorrectionVariant(it.key, it.value) }
    }

    private fun expandSuffixRepairVariants(
        source: String,
        penalty: Int,
        addVariant: (String, Int) -> Unit
    ) {
        if (source.length < 4) {
            return
        }
        if (source.endsWith("eauxe")) {
            addVariant(source.dropLast(5) + "eau", penalty)
        }
        if (source.endsWith("auxe")) {
            addVariant(source.dropLast(4) + "au", penalty)
        }
        if (source.endsWith("eaux")) {
            addVariant(source.dropLast(4) + "eau", penalty + 1)
        }
        if (source.endsWith("aux")) {
            addVariant(source.dropLast(3) + "au", penalty + 1)
        }
        if (source.endsWith("xe")) {
            addVariant(source.dropLast(2), penalty + 1)
        }
        if (source.endsWith("es") && source.length > 4) {
            addVariant(source.dropLast(2), penalty + 2)
        }
        if (source.endsWith("s") && source.length > 4) {
            addVariant(source.dropLast(1), penalty + 2)
        }
        if (source.endsWith("e") && source.length > 4) {
            addVariant(source.dropLast(1), penalty + 2)
        }
    }

    private fun computeVariantDistanceLimit(wordLength: Int, variantPenalty: Int): Int {
        val base = when {
            wordLength <= 4 -> 1
            wordLength <= 7 -> 2
            wordLength <= 10 -> 3
            else -> 4
        }
        val penaltyBonus = if (variantPenalty >= 2) 1 else 0
        return (base + penaltyBonus).coerceAtMost(5)
    }

    private fun computeRawDistanceLimit(wordLength: Int, variantPenalty: Int): Int {
        return (computeVariantDistanceLimit(wordLength, variantPenalty) + 2).coerceAtMost(8)
    }

    private fun coreLexiconPenalty(language: KeyboardLanguageMode, candidateWord: String): Int {
        return when (language) {
            KeyboardLanguageMode.FRENCH -> if (FRENCH_WORDS.contains(candidateWord)) -16 else 0
            KeyboardLanguageMode.ENGLISH -> if (ENGLISH_WORDS.contains(candidateWord)) -16 else 0
            else -> 0
        }
    }

    private fun isDictionaryCandidateConfident(
        source: String,
        best: DictionaryCorrectionCandidate,
        secondBest: DictionaryCorrectionCandidate?
    ): Boolean {
        val foldedSource = foldWord(source)
        val foldedBest = foldWord(best.word)

        val absoluteDistanceLimit = when {
            source.length <= 4 -> 1
            source.length <= 7 -> 2
            source.length <= 10 -> 3
            else -> 4
        } + if (best.variantPenalty >= 2) 1 else 0

        val absoluteDistance = levenshteinDistanceBounded(
            foldedSource,
            foldedBest,
            absoluteDistanceLimit.coerceAtMost(6)
        )
        if (absoluteDistance == Int.MAX_VALUE) {
            return false
        }

        val minPrefix = when {
            source.length <= 4 -> 1
            source.length <= 7 -> 2
            else -> 2
        }
        if (best.prefixLength < minPrefix && best.variantPenalty <= 1) {
            return false
        }

        val margin = if (secondBest == null) Int.MAX_VALUE else secondBest.score - best.score
        if (source.length <= 5 && margin < 8) {
            return false
        }
        if (source.length <= 7 && best.editDistance >= 2 && margin < 6) {
            return false
        }

        return true
    }

    private fun isBetterDictionaryCandidate(
        incoming: DictionaryCorrectionCandidate,
        current: DictionaryCorrectionCandidate?
    ): Boolean {
        if (current == null) {
            return true
        }
        if (incoming.score != current.score) {
            return incoming.score < current.score
        }
        if (incoming.variantPenalty != current.variantPenalty) {
            return incoming.variantPenalty < current.variantPenalty
        }
        if (incoming.editDistance != current.editDistance) {
            return incoming.editDistance < current.editDistance
        }
        if (incoming.prefixLength != current.prefixLength) {
            return incoming.prefixLength > current.prefixLength
        }
        if (incoming.word.length != current.word.length) {
            return incoming.word.length < current.word.length
        }
        return incoming.word < current.word
    }

    private fun detectContextLanguage(beforeCursor: String): KeyboardLanguageMode? {
        if (beforeCursor.isBlank()) {
            return null
        }

        val words = Regex("[\\p{L}’']+")
            .findAll(beforeCursor)
            .map { normalizeWord(it.value) }
            .toList()
            .takeLast(CONTEXT_LANGUAGE_WORD_WINDOW)

        if (words.isEmpty()) {
            return null
        }

        var frenchScore = 0
        var englishScore = 0
        words.forEach { word ->
            if (frenchLexicon.words.contains(word) || frenchLexicon.foldedWords.contains(foldWord(word)) || FRENCH_TYPOS.containsKey(word)) {
                frenchScore++
            }
            if (englishLexicon.words.contains(word) || englishLexicon.foldedWords.contains(foldWord(word)) || ENGLISH_TYPOS.containsKey(word)) {
                englishScore++
            }
        }

        return when {
            frenchScore >= englishScore + 1 -> KeyboardLanguageMode.FRENCH
            englishScore >= frenchScore + 1 -> KeyboardLanguageMode.ENGLISH
            keyboardLanguageMode == KeyboardLanguageMode.BOTH -> null
            else -> keyboardLanguageMode
        }
    }

    private fun isLanguageEnabled(language: KeyboardLanguageMode): Boolean {
        return keyboardLanguageMode == KeyboardLanguageMode.BOTH || keyboardLanguageMode == language
    }

    private fun languageBiasPenalty(
        candidateLanguage: KeyboardLanguageMode,
        contextLanguage: KeyboardLanguageMode?
    ): Int {
        return when {
            keyboardLanguageMode == KeyboardLanguageMode.BOTH && contextLanguage == null -> 0
            contextLanguage != null && candidateLanguage == contextLanguage -> 0
            candidateLanguage == keyboardLanguageMode -> 1
            else -> 2
        }
    }

    private fun isKnownWord(word: String): Boolean {
        val folded = foldWord(word)
        if (keyboardLanguageMode != KeyboardLanguageMode.ENGLISH) {
            if (frenchLexicon.words.contains(word) || frenchLexicon.foldedWords.contains(folded)) {
                return true
            }
        }
        if (keyboardLanguageMode != KeyboardLanguageMode.FRENCH) {
            if (englishLexicon.words.contains(word) || englishLexicon.foldedWords.contains(folded)) {
                return true
            }
        }
        return false
    }

    private fun findRepeatedLetterCorrection(
        source: String,
        contextLanguage: KeyboardLanguageMode?
    ): String? {
        if (!hasRunAtLeast(source, 3)) {
            return null
        }

        val collapsedOne = collapseRepeats(source, 1)
        val collapsedTwo = collapseRepeats(source, 2)
        val orderedCandidates = linkedSetOf<String>()
        if (hasVowelRunAtLeast(source, 3)) {
            orderedCandidates.add(collapsedOne)
            orderedCandidates.add(collapsedTwo)
        } else {
            orderedCandidates.add(collapsedTwo)
            orderedCandidates.add(collapsedOne)
        }

        var bestWord: String? = null
        var bestScore = Int.MAX_VALUE
        listOf(
            KeyboardLanguageMode.FRENCH to frenchLexicon,
            KeyboardLanguageMode.ENGLISH to englishLexicon
        ).forEach { (language, lexicon) ->
            if (!isLanguageEnabled(language)) {
                return@forEach
            }
            orderedCandidates.forEach { candidate ->
                val foldedCandidate = foldWord(candidate)
                val word = lexicon.foldedToWord[foldedCandidate] ?: return@forEach
                if (isCorrectionSuppressed(source, word)) {
                    return@forEach
                }
                val score = languageBiasPenalty(language, contextLanguage) * 10 + word.length
                if (score < bestScore) {
                    bestScore = score
                    bestWord = word
                }
            }
        }
        if (!bestWord.isNullOrBlank()) {
            return bestWord
        }

        // Conservative fallback when dictionaries do not contain the word yet.
        val fallback = if (hasVowelRunAtLeast(source, 3)) collapsedOne else collapsedTwo
        return if (
            fallback.length >= 3 &&
            fallback != source &&
            !isCorrectionSuppressed(source, fallback)
        ) {
            fallback
        } else {
            null
        }
    }

    private fun collapseRepeats(value: String, maxRepeat: Int): String {
        if (value.isBlank()) {
            return value
        }
        val builder = StringBuilder(value.length)
        var previous: Char? = null
        var count = 0
        value.forEach { char ->
            if (char == previous) {
                count++
            } else {
                previous = char
                count = 1
            }
            if (count <= maxRepeat) {
                builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun hasRunAtLeast(value: String, size: Int): Boolean {
        if (value.isBlank()) {
            return false
        }
        var previous: Char? = null
        var count = 0
        value.forEach { char ->
            if (char == previous) {
                count++
            } else {
                previous = char
                count = 1
            }
            if (count >= size) {
                return true
            }
        }
        return false
    }

    private fun hasVowelRunAtLeast(value: String, size: Int): Boolean {
        if (value.isBlank()) {
            return false
        }
        var previous: Char? = null
        var count = 0
        value.forEach { char ->
            if (!isVowel(char)) {
                previous = null
                count = 0
                return@forEach
            }
            if (char == previous) {
                count++
            } else {
                previous = char
                count = 1
            }
            if (count >= size) {
                return true
            }
        }
        return false
    }

    private fun isVowel(char: Char): Boolean {
        val base = foldWord(char.toString())
        return base.firstOrNull() in VOWELS_FOR_REPEAT
    }

    private fun foldWord(word: String): String {
        val normalized = Normalizer.normalize(word, Normalizer.Form.NFD)
        return DIACRITIC_REGEX.replace(normalized, "")
            .replace('’', '\'')
            .lowercase(Locale.US)
    }

    private fun correctionPairKey(source: String, corrected: String): String {
        return "$source->$corrected"
    }

    private fun isCorrectionSuppressed(source: String, corrected: String): Boolean {
        val key = correctionPairKey(normalizeWord(source), normalizeWord(corrected))
        val count = rejectedCorrections[key] ?: 0
        return count >= AUTOCORRECT_REVERT_DISABLE_THRESHOLD
    }

    private fun recordRejectedCorrection(source: String, corrected: String) {
        val key = correctionPairKey(normalizeWord(source), normalizeWord(corrected))
        val next = (rejectedCorrections[key] ?: 0) + 1
        rejectedCorrections[key] = next
        saveRejectedCorrections()
    }

    private fun loadRejectedCorrections() {
        val prefs = getSharedPreferences(KeyboardModeSettings.PREFS_NAME, MODE_PRIVATE)
        val raw = prefs.getString(KEY_AUTOCORRECT_REJECTED_JSON, null) ?: return
        try {
            val json = JSONObject(raw)
            rejectedCorrections.clear()
            json.keys().forEach { key ->
                val value = json.optInt(key, 0)
                if (value > 0) {
                    rejectedCorrections[key] = value
                }
            }
        } catch (_: Exception) {
            rejectedCorrections.clear()
        }
    }

    private fun saveRejectedCorrections() {
        val json = JSONObject().apply {
            rejectedCorrections.forEach { (key, value) ->
                if (value > 0) {
                    put(key, value)
                }
            }
        }

        getSharedPreferences(KeyboardModeSettings.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(KEY_AUTOCORRECT_REJECTED_JSON, json.toString())
            .apply()
    }

    private fun commonPrefixLength(source: String, target: String): Int {
        val max = minOf(source.length, target.length)
        var index = 0
        while (index < max && source[index] == target[index]) {
            index++
        }
        return index
    }

    private fun extractTrailingWord(value: String): String? {
        if (value.isBlank()) {
            return null
        }
        var end = value.length - 1
        while (end >= 0 && !isWordChar(value[end])) {
            end--
        }
        if (end < 0) {
            return null
        }

        var start = end
        while (start >= 0 && isWordChar(value[start])) {
            start--
        }
        return value.substring(start + 1, end + 1)
    }

    private fun isWordChar(char: Char): Boolean {
        return char.isLetter() || char == '\'' || char == '’'
    }

    private fun normalizeWord(word: String): String {
        return word.lowercase(Locale.US).replace('’', '\'')
    }

    private fun applyWordCase(base: String, source: String): String {
        if (source.isEmpty()) {
            return base
        }
        return when {
            source.all { !it.isLetter() || it.isUpperCase() } -> base.uppercase(Locale.US)
            source.first().isUpperCase() -> base.replaceFirstChar { it.uppercase(Locale.US) }
            else -> base
        }
    }

    private fun levenshteinDistanceBounded(source: String, target: String, limit: Int): Int {
        if (kotlin.math.abs(source.length - target.length) > limit) {
            return Int.MAX_VALUE
        }

        val prev = IntArray(target.length + 1) { it }
        val curr = IntArray(target.length + 1)

        for (i in 1..source.length) {
            curr[0] = i
            var rowBest = curr[0]
            for (j in 1..target.length) {
                val cost = if (source[i - 1] == target[j - 1]) 0 else 1
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + cost
                )
                if (curr[j] < rowBest) {
                    rowBest = curr[j]
                }
            }
            if (rowBest > limit) {
                return Int.MAX_VALUE
            }
            for (j in prev.indices) {
                prev[j] = curr[j]
            }
        }

        return prev[target.length]
    }

    private fun appendPromptText(text: String) {
        if (!::aiPromptInput.isInitialized) {
            return
        }
        val editable = aiPromptInput.text ?: return
        editable.append(text)
        aiPromptInput.setSelection(editable.length)
    }

    private fun appendEmojiSearchText(text: String) {
        val editable = emojiSearchInput.text ?: return
        editable.append(text)
        emojiSearchInput.setSelection(editable.length)
    }

    private fun renderClipboardItems() {
        if (!::clipboardItemsContainer.isInitialized) {
            return
        }

        clipboardItemsContainer.removeAllViews()
        val items = clipboardHistoryStore.getItems()

        if (items.isEmpty()) {
            val row = buildClipboardRow()
            row.addView(buildClipboardButton("Clipboard is empty", pinned = false, enabled = false) {
                // no-op
            })
            row.addView(buildClipboardSpacer())
            clipboardItemsContainer.addView(row)
            return
        }

        val gridItems = items.take(MAX_CLIPBOARD_GRID_ITEMS)
        val rows = gridItems.chunked(2)
        rows.forEachIndexed { rowIndex, rowItems ->
            val row = buildClipboardRow()
            rowItems.forEach { item ->
                val text = item.text
                val button = buildClipboardButton(text, pinned = item.pinned, enabled = true) {
                    if (isAiMode) {
                        appendPromptText(text)
                    } else {
                        currentInputConnection?.commitText(text, 1)
                    }
                    isClipboardOpen = false
                    refreshUi()
                }
                row.addView(button)
            }
            if (rowItems.size == 1) {
                row.addView(buildClipboardSpacer())
            }
            if (rowIndex < rows.lastIndex) {
                (row.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(8)
            }
            clipboardItemsContainer.addView(row)
        }
    }

    private fun buildClipboardRow(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun buildClipboardSpacer(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).also {
                it.marginStart = dp(5)
            }
        }
    }

    private fun buildClipboardButton(
        text: String,
        pinned: Boolean,
        enabled: Boolean,
        onClick: () -> Unit
    ): Button {
        return AppCompatButton(this).apply {
            this.text = if (pinned) "• $text" else text
            setAllCaps(false)
            applyInterTypeface(this)
            isEnabled = enabled
            maxLines = 2
            textSize = 13f
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = uiDrawable(R.drawable.bg_chip)
            setTextColor(uiColor(R.color.key_text))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            flattenView(this)
            configureKeyTouch(
                view = this,
                repeatOnHold = false,
                longPressAction = { anchor, rawX, rawY ->
                    showClipboardItemActionsPopup(anchor, text, pinned, rawX, rawY)
                },
                tapOnDown = false,
                onTap = onClick
            )
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).also {
                it.marginEnd = dp(5)
            }
        }
    }

    private fun showClipboardItemActionsPopup(
        anchor: View,
        text: String,
        pinned: Boolean,
        touchRawX: Float,
        touchRawY: Float
    ) {
        dismissActivePopup()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundResource(R.drawable.bg_variant_popup)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }

        val optionViews = mutableListOf<View>()
        val optionActions = mutableListOf<(() -> Unit)?>()
        val optionEnabled = mutableListOf<Boolean>()

        fun addAction(iconRes: Int, selected: Boolean = false, enabled: Boolean = true, onAction: () -> Unit) {
            val action = AppCompatImageButton(this).apply {
                background = uiDrawable(
                    if (selected) R.drawable.bg_popup_option_selected else R.drawable.bg_popup_option
                )
                setIcon(this, iconRes, R.color.key_text)
                isEnabled = enabled
                alpha = if (enabled) 1f else 0.42f
                flattenView(this)
                layoutParams = LinearLayout.LayoutParams(
                    dp(40),
                    dp(40)
                ).also {
                    it.marginEnd = dp(4)
                }
            }
            optionViews.add(action)
            optionActions.add {
                onAction()
                renderClipboardItems()
                refreshUi()
            }
            optionEnabled.add(enabled)
            row.addView(action)
        }

        val aiEnabled = isAiAllowedInCurrentContext()
        addAction(R.drawable.ic_ai_custom, enabled = aiEnabled) {
            if (!aiEnabled) {
                return@addAction
            }
            isEmojiMode = false
            isClipboardOpen = false
            isAiMode = true
            refreshUi()
            aiPromptInput.setText(text)
            aiPromptInput.setSelection(aiPromptInput.text?.length ?: 0)
        }
        addAction(R.drawable.ic_pin_lucide, selected = pinned) {
            clipboardHistoryStore.setPinned(text, !pinned)
        }
        addAction(R.drawable.ic_trash_lucide) {
            clipboardHistoryStore.removeItem(text)
        }

        val popup = PopupWindow(
            row,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        ).apply {
            isOutsideTouchable = false
            isTouchable = false
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(0x00000000))
            elevation = dp(6).toFloat()
            setOnDismissListener {
                if (activePopupWindow === this) {
                    activePopupWindow = null
                    activeSwipePopupSession = null
                }
            }
        }

        activePopupWindow = popup
        showPopupNearTouch(anchor, popup, row, touchRawX, touchRawY)
        activeSwipePopupSession = SwipePopupSession(
            optionViews = optionViews,
            optionActions = optionActions,
            optionEnabled = optionEnabled,
            selectedIndex = 0
        )
        highlightSwipePopupSelection(0)
    }

    private fun commitRecentClipboardImage(uri: Uri, mimeType: String?): Boolean {
        val inputConnection = currentInputConnection ?: return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
            return false
        }

        return try {
            val resolvedMime = mimeType ?: contentResolver.getType(uri) ?: "image/*"
            val description = ClipDescription("Nboard clipboard image", arrayOf(resolvedMime))
            val contentInfo = InputContentInfo(uri, description, null)
            val committed = inputConnection.commitContent(
                contentInfo,
                InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null
            )
            if (!committed) {
                toast("Image paste isn't supported in this field")
            }
            committed
        } catch (_: Throwable) {
            toast("Unable to paste this image")
            false
        }
    }

    private fun resolveClipboardImageMimeType(
        description: ClipDescription?,
        uri: Uri?
    ): String? {
        var foundImage: String? = null
        if (description != null) {
            for (index in 0 until description.mimeTypeCount) {
                val mime = description.getMimeType(index) ?: continue
                if (mime.startsWith("image/")) {
                    foundImage = mime
                    break
                }
            }
        }

        if (foundImage != null) {
            return foundImage
        }
        if (uri == null) {
            return null
        }

        val resolverMime = contentResolver.getType(uri)
        return if (!resolverMime.isNullOrBlank() && resolverMime.startsWith("image/")) {
            resolverMime
        } else {
            null
        }
    }

    private fun loadRecentClipboardImagePreview(uri: Uri) {
        val previewSizePx = dp(24).coerceAtLeast(24)
        serviceScope.launch(Dispatchers.IO) {
            val decoded = decodeClipboardImagePreview(uri, previewSizePx)
            launch(Dispatchers.Main) {
                if (latestClipboardImageUri != uri) {
                    return@launch
                }
                latestClipboardImagePreview = decoded
                renderRecentClipboardRow()
                if (::aiModeButton.isInitialized) {
                    refreshUi()
                }
            }
        }
    }

    private fun decodeClipboardImagePreview(uri: Uri, targetSizePx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            return null
        }

        var sampleSize = 1
        while ((width / sampleSize) > targetSizePx * 2 || (height / sampleSize) > targetSizePx * 2) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val scaledWidth = ((bitmap.width * targetSizePx.toFloat() / bitmap.height).toInt()).coerceAtLeast(targetSizePx)
        return Bitmap.createScaledBitmap(bitmap, scaledWidth, targetSizePx, true)
    }

    private fun scheduleRecentClipboardExpiry() {
        recentClipboardExpiryJob?.cancel()
        if (latestClipboardDismissed) {
            return
        }
        val remaining = RECENT_CLIPBOARD_WINDOW_MS - (System.currentTimeMillis() - latestClipboardAtMs)
        if (remaining <= 0L) {
            if (::aiModeButton.isInitialized) {
                refreshUi()
            }
            return
        }
        recentClipboardExpiryJob = serviceScope.launch {
            delay(remaining)
            if (!latestClipboardDismissed && ::aiModeButton.isInitialized) {
                refreshUi()
            }
        }
    }

    private fun captureClipboardPrimary() {
        val manager = clipboardManager ?: return
        try {
            val clipData = manager.primaryClip ?: return
            if (clipData.itemCount <= 0) {
                return
            }

            val item = clipData.getItemAt(0)
            val description = manager.primaryClipDescription ?: clipData.description
            val itemUri = item.uri
            val imageMimeType = resolveClipboardImageMimeType(description, itemUri)

            if (itemUri != null && imageMimeType != null) {
                latestClipboardText = null
                latestClipboardImageUri = itemUri
                latestClipboardImageMimeType = imageMimeType
                latestClipboardImagePreview = null
                latestClipboardAtMs = System.currentTimeMillis()
                latestClipboardDismissed = false
                scheduleRecentClipboardExpiry()
                loadRecentClipboardImagePreview(itemUri)
                renderRecentClipboardRow()
                if (::aiModeButton.isInitialized) {
                    refreshUi()
                }
                return
            }

            val itemText = item.coerceToText(this)?.toString()?.trim().orEmpty()
            if (itemText.isBlank()) {
                return
            }
            clipboardHistoryStore.addItem(itemText)
            latestClipboardText = itemText
            latestClipboardImageUri = null
            latestClipboardImageMimeType = null
            latestClipboardImagePreview = null
            latestClipboardAtMs = System.currentTimeMillis()
            latestClipboardDismissed = false
            scheduleRecentClipboardExpiry()
            renderClipboardItems()
            renderRecentClipboardRow()
            if (::aiModeButton.isInitialized) {
                refreshUi()
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "Clipboard access denied", error)
        } catch (error: Exception) {
            Log.w(TAG, "Clipboard read failed", error)
        }
    }

    private fun buildFallbackInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(uiColor(R.color.keyboard_bg))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipChildren = false
        }

        listOf("A", "B", "C", "Space", "<-").forEachIndexed { index, key ->
            val button = AppCompatButton(this).apply {
                text = key
                setAllCaps(false)
                applyInterTypeface(this)
                background = uiDrawable(R.drawable.bg_key)
                flattenView(this)
            }
            configureKeyTouch(
                view = button,
                repeatOnHold = key == "<-",
                longPressAction = null,
                tapOnDown = true
            ) {
                when (key) {
                    "Space" -> currentInputConnection?.commitText(" ", 1)
                    "<-" -> currentInputConnection?.let { deletePreviousGrapheme(it) }
                    else -> currentInputConnection?.commitText(key.lowercase(Locale.US), 1)
                }
            }
            button.layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).also {
                if (index < 4) {
                    it.marginEnd = dp(4)
                }
            }
            root.addView(button)
        }
        return root
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "NboardImeService"

        private const val KEY_REPEAT_START_DELAY_MS = 260L
        private const val KEY_REPEAT_INTERVAL_MS = 45L
        private const val KEY_PRESS_SCALE = 1.05f
        private const val KEY_PRESSED_ALPHA = 0.74f
        private const val MIN_PRESSED_ALPHA = 0.4f
        private const val HOLD_SELECTION_DEADZONE_DP = 10
        private const val KEY_PRESS_ANIM_MS = 55L
        private const val KEY_RELEASE_ANIM_MS = 70L
        private const val VARIANT_LONG_PRESS_TIMEOUT_MS = 240L
        private const val SHIFT_DOUBLE_TAP_TIMEOUT_MS = 320L
        private const val AUTO_SHIFT_CONTEXT_WINDOW = 80
        private const val AUTOCORRECT_CONTEXT_WINDOW = 40
        private const val CONTEXT_LANGUAGE_WORD_WINDOW = 6
        private const val MAX_EMOJI_SEARCH_SUGGESTIONS = 5
        private const val RECENT_CLIPBOARD_WINDOW_MS = 45_000L
        private const val RECENT_CLIPBOARD_PREVIEW_CHAR_LIMIT = 30
        private const val GRAPHEME_DELETE_CONTEXT_WINDOW = 64
        private const val EMOJI_GRID_INITIAL_BATCH = 120
        private const val EMOJI_GRID_CHUNK_SIZE = 90
        private const val SPACEBAR_CURSOR_STEP_DP = 14
        private const val SPACEBAR_CURSOR_DEADZONE_DP = 18
        private const val AUTOCORRECT_REVERT_DISABLE_THRESHOLD = 2
        private const val MAX_AUTOCORRECT_VARIANTS = 14

        private const val MAX_CLIPBOARD_GRID_ITEMS = 4
        private const val MAX_RECENT_EMOJIS = 30
        private const val AI_PILL_CHAR_LIMIT = 320
        private const val AI_REPLY_CHAR_LIMIT = 420

        private const val KEY_EMOJI_COUNTS_JSON = "emoji_usage_counts"
        private const val KEY_EMOJI_RECENTS_JSON = "emoji_recents"
        private const val KEY_AUTOCORRECT_REJECTED_JSON = "autocorrect_rejected"

        private const val AI_PROMPT_SYSTEM_INSTRUCTION =
            "You are a concise writing assistant. Reply only with the final text. Keep responses short and practical."

        private const val AI_QUICK_ACTION_SYSTEM_INSTRUCTION =
            "You are a concise text-rewrite assistant. Return only the rewritten output without explanation. Keep it brief."

        private val DEFAULT_TOP_EMOJIS = listOf("😀", "😂", "❤️", "🔥", "😭", "👍", "🥳", "✨")
        private val EMOJI_SCAN_RANGES = listOf(
            0x203C..0x3299,
            0x1F000..0x1FAFF
        )
        private val KEYCAP_EMOJIS = listOf(
            "#️⃣", "*️⃣", "0️⃣", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣", "9️⃣"
        )

        private val AZERTY_ROW_1 = listOf("a", "z", "e", "r", "t", "y", "u", "i", "o", "p")
        private val AZERTY_ROW_2 = listOf("q", "s", "d", "f", "g", "h", "j", "k", "l", "m")
        private val AZERTY_ROW_3 = listOf("w", "x", "c", "v", "b", "n", ",")

        private val QWERTY_ROW_1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        private val QWERTY_ROW_2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l", "'")
        private val QWERTY_ROW_3 = listOf("z", "x", "c", "v", "b", "n", "m", ",")

        private val VARIANT_MAP = mapOf(
            "a" to listOf("à", "â", "ä", "æ", "á", "ã", "å"),
            "e" to listOf("é", "è", "ê", "ë", "€", "ē"),
            "i" to listOf("î", "ï", "ì", "í", "ī"),
            "o" to listOf("ô", "ö", "œ", "ò", "ó", "õ", "ø"),
            "u" to listOf("ù", "û", "ü", "ú", "ū"),
            "c" to listOf("ç"),
            "n" to listOf("ñ", "ń"),
            "y" to listOf("ÿ", "ý"),
            "'" to listOf("’", "ʼ", "`", "´"),
            "\"" to listOf("«", "»", "“", "”"),
            "." to listOf("…", "•", "·"),
            "," to listOf(".", ";", ":", "…", "!", "?", "'"),
            "-" to listOf("-", "–", "—", "•")
        )

        private val AUTO_SPACE_PUNCTUATION = setOf('.', '!', '?', ':', ';')
        private val AUTO_SHIFT_SENTENCE_ENDERS = setOf('.', '!', '?', ':', ';', '\n')
        private val AUTOCORRECT_TRIGGER_DELIMITERS = setOf(' ', '.', ',', '!', '?', ';', ':', '\n')
        private val VOWELS_FOR_REPEAT = setOf('a', 'e', 'i', 'o', 'u', 'y')
        private val DIACRITIC_REGEX = Regex("\\p{M}+")
        private val ASSET_WORD_REGEX = Regex("[a-zàâäéèêëîïôöùûüçœæÿ'\\-]{2,24}")

        private val ENGLISH_WORDS = setOf(
            "a","about","after","again","all","also","always","am","an","and","any","are","around","as","at",
            "back","be","because","been","before","being","best","better","both","but","by","can","could",
            "day","did","do","does","doing","done","dont","down","each","even","every","for","from","get","go",
            "good","great","had","has","have","he","hello","help","her","here","him","his","how","i","if","in",
            "into","is","it","its","just","know","language","last","let","like","little","long","look","make",
            "many","me","more","most","much","my","need","new","next","no","not","now","of","on","one","only",
            "or","other","our","out","over","people","please","right","same","say","see","she","should","small",
            "so","some","something","start","still","such","take","text","than","thank","that","the","their",
            "them","then","there","these","they","thing","this","time","to","today","too","try","two","up","us",
            "use","very","want","was","way","we","well","were","what","when","where","which","who","why","will",
            "with","word","work","would","write","yes","you","your","yours"
        )

        private val FRENCH_WORDS = setOf(
            "a","à","abord","afin","ai","aie","ainsi","alors","apres","après","au","aucun","aussi","autre","aux",
            "avoir","avec","beaucoup","bien","bon","bonjour","car","ce","cela","celle","celui","ces","cet","cette",
            "chaque","chez","comme","comment","dans","de","des","deux","devant","donc","du","elle","elles","en",
            "encore","entre","est","et","ete","été","etre","être","fait","faire","faut","grand","gros","ici","il",
            "ils","je","jour","juste","la","le","les","leur","leurs","lui","ma","mais","me","mes","mieux","moins",
            "mon","mot","mots","ne","ni","non","nos","notre","nous","nouveau","ou","où","par","parce","pas","peu",
            "plus","pour","pourquoi","premier","quand","que","quel","quelle","quelles","quels","qui","quoi","sa",
            "sans","se","ses","si","son","sont","sur","ta","te","tes","text","texte","tes","toi","ton","toujours",
            "tout","tous","tres","très","tu","un","une","votre","vous","vu","y","salut",
            "fleur","fleurs","fleurir","jolie","magnifique","maison","chat","chien","amour","merci"
        )

        private val FRENCH_TYPOS = mapOf(
            "salot" to "salut",
            "bjr" to "bonjour",
            "stp" to "s'il te plaît",
            "svp" to "s'il vous plaît"
        )

        private val ENGLISH_TYPOS = mapOf(
            "teh" to "the",
            "woudl" to "would",
            "dont" to "don't"
        )

        private val ALL_EMOJIS = listOf(
            "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃","😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
            "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🫢","🤫","🤔","🫡","🤐","🤨","😐","😑","😶","🫥","😶‍🌫️","😏",
            "😒","🙄","😬","😮‍💨","🤥","😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵",
            "🤯","🤠","🥳","🥸","😎","🤓","🧐","😕","🫤","😟","🙁","☹️","😮","😯","😲","😳","🥺","🥹","😦","😧",
            "😨","😰","😥","😢","😭","😱","😖","😣","😞","😓","😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀",
            "☠️","💩","🤡","👹","👺","👻","👽","👾","🤖","😺","😸","😹","😻","😼","😽","🙀","😿","😾",
            "🙈","🙉","🙊","💋","💌","💘","💝","💖","💗","💓","💞","💕","💟","❣️","💔","❤️","🧡","💛","💚","💙",
            "💜","🤎","🖤","🤍","💯","💢","💥","💫","💦","💨","🕳️","💣","💬","🗨️","🗯️","💭","💤",
            "👋","🤚","🖐️","✋","🖖","🫱","🫲","🫳","🫴","👌","🤌","🤏","✌️","🤞","🫰","🤟","🤘","🤙","👈","👉",
            "👆","🖕","👇","☝️","🫵","👍","👎","✊","👊","🤛","🤜","👏","🙌","🫶","👐","🤲","🤝","🙏","✍️","💅",
            "🤳","💪","🦾","🦿","🦵","🦶","👂","🦻","👃","🧠","🫀","🫁","🦷","🦴","👀","👁️","👅","👄",
            "👶","🧒","👦","👧","🧑","👱","👨","🧔","🧔‍♂️","🧔‍♀️","👨‍🦰","👨‍🦱","👨‍🦳","👨‍🦲","👩","👩‍🦰","👩‍🦱","👩‍🦳","👩‍🦲","🧓",
            "👴","👵","🙍","🙍‍♂️","🙍‍♀️","🙎","🙎‍♂️","🙎‍♀️","🙅","🙅‍♂️","🙅‍♀️","🙆","🙆‍♂️","🙆‍♀️","💁","💁‍♂️","💁‍♀️","🙋","🙋‍♂️","🙋‍♀️",
            "🧏","🧏‍♂️","🧏‍♀️","🙇","🙇‍♂️","🙇‍♀️","🤦","🤦‍♂️","🤦‍♀️","🤷","🤷‍♂️","🤷‍♀️","👨‍⚕️","👩‍⚕️","👨‍🎓","👩‍🎓","👨‍🏫","👩‍🏫","👨‍💻","👩‍💻",
            "👨‍🔧","👩‍🔧","👨‍🍳","👩‍🍳","👨‍🚀","👩‍🚀","👨‍⚖️","👩‍⚖️","👮","👮‍♂️","👮‍♀️","🕵️","🕵️‍♂️","🕵️‍♀️","💂","💂‍♂️","💂‍♀️","🥷","👷","👷‍♂️",
            "👷‍♀️","👸","🤴","👳","👳‍♂️","👳‍♀️","👲","🧕","🤵","🤵‍♂️","🤵‍♀️","👰","👰‍♂️","👰‍♀️","🤰","🫃","🫄","🤱","👩‍🍼","👨‍🍼",
            "🎉","🎊","🎈","🎁","🎂","🍰","🧁","🍾","🥂","🍻","🍺","🍷","🥃","🍸","🍹","🧉","☕","🫖","🍫","🍿",
            "🍎","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒",
            "🌶️","🫑","🌽","🥕","🫒","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🥓","🥩","🍗",
            "🍖","🌭","🍔","🍟","🍕","🌮","🌯","🥙","🧆","🥪","🌭","🍜","🍝","🍣","🍱","🍛","🍤","🍙","🍚","🍘",
            "⚽","🏀","🏈","⚾","🥎","🎾","🏐","🏉","🥏","🎱","🏓","🏸","🥅","🏒","🏑","🥍","🏏","🪃","🥊","🥋",
            "🎮","🕹️","🎲","♟️","🧩","🎯","🎳","🎭","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🎸","🎻","🎺","🪗","🎷",
            "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🚚","🚛","🚜","🏍️","🛵","🚲","🛴","🚨","✈️","🛫",
            "🛬","🚀","🛸","🚁","⛵","🚤","🛥️","🚢","⛴️","🚂","🚆","🚇","🚝","🚟","🚡","🚠","🗽","🗼","🗿","🗺️",
            "🌍","🌎","🌏","🗻","🏕️","🏖️","🏜️","🏝️","🏛️","🏟️","🏠","🏡","🏢","🏣","🏤","🏥","🏦","🏨","🏪","🏫",
            "⌚","📱","💻","⌨️","🖥️","🖨️","🖱️","🧮","📷","📸","📹","🎥","📞","☎️","📟","📠","📺","📻","🎙️","🔋",
            "🔌","💡","🔦","🕯️","🧯","🧲","💸","💰","💳","🪙","💎","🔑","🗝️","🔒","🔓","🔐","🧰","🛠️","⚙️","🔧",
            "✅","☑️","✔️","❌","❎","➕","➖","➗","✖️","♾️","‼️","⁉️","❓","❔","❕","❗","〰️","➰","➿","⭕",
            "🌟","✨","⚡","🔥","💧","🌈","☀️","🌤️","⛅","☁️","🌧️","⛈️","🌩️","❄️","☃️","🌊","🍀","🌸","🌹","🌻"
        )

        private val EMOJI_KEYWORDS = mapOf(
            "😀" to "grin happy smile",
            "😂" to "laugh lol",
            "😭" to "cry tears",
            "❤️" to "heart love",
            "🔥" to "fire hot",
            "👍" to "thumbs up yes ok",
            "🙏" to "pray thanks please",
            "👏" to "clap applause",
            "🎉" to "party celebration",
            "✨" to "sparkles",
            "😡" to "angry mad",
            "🤔" to "thinking",
            "😴" to "sleep",
            "🥳" to "party hat",
            "🤝" to "handshake",
            "✅" to "check valid",
            "💡" to "idea",
            "⚽" to "football sport",
            "🍕" to "pizza food",
            "🍔" to "burger food",
            "🍟" to "fries food",
            "☕" to "coffee",
            "🚗" to "car",
            "✈️" to "plane travel",
            "🌧️" to "rain",
            "🌞" to "sun",
            "🌈" to "rainbow",
            "🧠" to "brain",
            "💻" to "computer",
            "📱" to "phone",
            "📸" to "camera",
            "💯" to "hundred perfect",
            "💸" to "money",
            "🕘" to "latest recent"
        )
    }
}

private enum class ShiftMode {
    OFF,
    ONE_SHOT,
    CAPS_LOCK
}

private enum class QuickAiAction {
    SUMMARIZE,
    FIX_GRAMMAR,
    EXPAND
}

private data class ModeOption(val mode: BottomKeyMode, val iconRes: Int)

private data class Lexicon(
    val words: Set<String>,
    val foldedWords: Set<String>,
    val byFirst: Map<Char, List<String>>,
    val foldedToWord: Map<String, String>
) {
    companion object {
        fun empty() = Lexicon(
            words = emptySet(),
            foldedWords = emptySet(),
            byFirst = emptyMap(),
            foldedToWord = emptyMap()
        )
    }
}

private data class VariantSelectionSession(
    val options: List<String>,
    val optionViews: List<AppCompatTextView>,
    val replacePreviousChar: Boolean,
    val shiftAware: Boolean,
    var selectedIndex: Int
)

private data class SwipePopupSession(
    val optionViews: List<View>,
    val optionActions: List<(() -> Unit)?>,
    val optionEnabled: List<Boolean>,
    var selectedIndex: Int
)

private data class AutoCorrectionVariant(
    val word: String,
    val penalty: Int
)

private data class DictionaryCorrectionCandidate(
    val word: String,
    val score: Int,
    val language: KeyboardLanguageMode?,
    val variantPenalty: Int,
    val editDistance: Int,
    val prefixLength: Int
)

private data class AutoCorrectionResult(
    val originalWord: String,
    val correctedWord: String
)

private data class AutoCorrectionUndo(
    val originalWord: String,
    val correctedWord: String,
    val committedSuffix: String
)

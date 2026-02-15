package com.nboard.ime

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

internal fun NboardImeService.syncVoiceInputGlowAnimation() {
        stopVoiceInputGlowAnimation()
        syncVoiceActionPulseAnimation()
    }

internal fun NboardImeService.syncVoiceActionPulseAnimation() {
    if (!isActionButtonInitialized()) {
            return
        }
        val shouldPulse = isVoiceListening || isVoiceStopping
        if (!shouldPulse) {
            stopVoiceActionPulseAnimation()
            return
        }
        startVoiceActionPulseAnimation(stopping = isVoiceStopping)
    }

internal fun NboardImeService.startVoiceActionPulseAnimation(stopping: Boolean) {
        if (voiceActionPulseAnimator != null && voiceActionPulseForStopping == stopping) {
            return
        }
        stopVoiceActionPulseAnimation()
        voiceActionPulseForStopping = stopping
        val minScale = if (stopping) 1.01f else 1.03f
        val maxScale = if (stopping) 1.06f else 1.1f
        val minAlpha = if (stopping) 0.92f else 0.96f
        voiceActionPulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (stopping) 720L else 560L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                val scale = minScale + ((maxScale - minScale) * progress)
                actionButton.scaleX = scale
                actionButton.scaleY = scale
                actionButton.alpha = minAlpha + ((1f - minAlpha) * progress)
            }
            start()
        }
    }

internal fun NboardImeService.stopVoiceActionPulseAnimation() {
        voiceActionPulseAnimator?.cancel()
        voiceActionPulseAnimator = null
    if (isActionButtonInitialized()) {
            actionButton.scaleX = 1f
            actionButton.scaleY = 1f
            actionButton.alpha = 1f
        }
    }

internal fun NboardImeService.stopVoiceInputGlowAnimation() {
        voiceGlowAnimator?.cancel()
        voiceGlowAnimator = null
    if (isVoiceInputGlowInitialized()) {
            voiceInputGlow.isVisible = false
            voiceInputGlow.alpha = 0f
        }
    }

internal fun NboardImeService.refreshVoiceUiState() {
    if (isActionButtonInitialized()) {
            refreshUi()
        } else {
            syncVoiceInputGlowAnimation()
        }
    }

internal fun NboardImeService.startVoiceInput() {
        if (isVoiceListening || isVoiceStopping || !isVoiceInputLongPressAvailable()) {
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            toast("Voice input is not available on this device")
            return
        }
        if (!hasRecordAudioPermission()) {
            toast("Enable microphone permission for Nboard in app settings")
            return
        }

        val recognizer = ensureVoiceRecognizer() ?: return
        cancelVoiceStopJobs()
        resetVoiceTranscriptState()
        voiceLeadingPrefix = computeVoiceLeadingPrefix()
        voiceShouldAutoRestart = true
        isVoiceStopping = false
        isVoiceListening = true
        refreshVoiceUiState()
        runCatching {
            recognizer.startListening(buildVoiceRecognizerIntent())
        }.onFailure {
            Log.w(TAG, "Failed to start voice input", it)
            voiceShouldAutoRestart = false
            isVoiceListening = false
            isVoiceStopping = false
            refreshVoiceUiState()
        }
    }

internal fun NboardImeService.stopVoiceInput(forceCancel: Boolean) {
        val recognizer = activeVoiceRecognizer
        if (!forceCancel && !isVoiceListening && !isVoiceStopping) {
            return
        }
        cancelVoiceStopJobs()
        voiceShouldAutoRestart = false

        if (forceCancel) {
            isVoiceListening = false
            isVoiceStopping = false
            runCatching {
                recognizer?.cancel()
            }.onFailure {
                Log.w(TAG, "Failed to cancel voice input", it)
            }
            finalizeVoiceComposition()
            resetVoiceTranscriptState()
            refreshVoiceUiState()
            return
        }

        if (isVoiceStopping) {
            return
        }

        if (recognizer == null) {
            if (voiceLastTranscript.isNotBlank()) {
                commitVoiceTranscript(voiceLastTranscript, isFinal = true)
            } else {
                finalizeVoiceComposition()
            }
            isVoiceListening = false
            isVoiceStopping = false
            resetVoiceTranscriptState()
            refreshVoiceUiState()
            return
        }

        isVoiceStopping = true
        refreshVoiceUiState()
        voiceReleaseStopJob = serviceScope.launch(Dispatchers.Main) {
            delay(VOICE_RELEASE_GRACE_MS)
            runCatching {
                recognizer.stopListening()
            }.onFailure {
                Log.w(TAG, "Failed to stop voice input", it)
            }
            isVoiceListening = false
            syncVoiceInputGlowAnimation()
            scheduleVoiceFinalizeFallback()
        }
    }

internal fun NboardImeService.cancelVoiceStopJobs() {
        voiceReleaseStopJob?.cancel()
        voiceReleaseStopJob = null
        voiceFinalizeFallbackJob?.cancel()
        voiceFinalizeFallbackJob = null
    }

internal fun NboardImeService.scheduleVoiceFinalizeFallback() {
        voiceFinalizeFallbackJob?.cancel()
        voiceFinalizeFallbackJob = serviceScope.launch(Dispatchers.Main) {
            delay(VOICE_FINALIZE_FALLBACK_MS)
            if (!isVoiceStopping) {
                return@launch
            }
            if (voiceLastTranscript.isNotBlank()) {
                commitVoiceTranscript(voiceLastTranscript, isFinal = true)
            } else {
                finalizeVoiceComposition()
            }
            isVoiceListening = false
            isVoiceStopping = false
            voiceShouldAutoRestart = false
            resetVoiceTranscriptState()
            refreshVoiceUiState()
        }
    }

internal fun NboardImeService.ensureVoiceRecognizer(): SpeechRecognizer? {
        activeVoiceRecognizer?.let { return it }
        return runCatching {
            val recognizer = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            } else {
                SpeechRecognizer.createSpeechRecognizer(this)
            }

            recognizer.also {
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit

                    override fun onPartialResults(partialResults: Bundle?) {
                        serviceScope.launch(Dispatchers.Main.immediate) {
                            if (!isVoiceListening && !isVoiceStopping) {
                                return@launch
                            }
                            val transcript = extractVoiceTranscript(partialResults)
                            if (transcript.isBlank()) {
                                return@launch
                            }
                            commitVoiceTranscript(transcript, isFinal = false)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        serviceScope.launch(Dispatchers.Main.immediate) {
                            cancelVoiceStopJobs()
                            val transcript = extractVoiceTranscript(results)
                            if (transcript.isNotBlank()) {
                                commitVoiceTranscript(transcript, isFinal = true)
                            } else if (voiceLastTranscript.isNotBlank()) {
                                commitVoiceTranscript(voiceLastTranscript, isFinal = true)
                            } else {
                                finalizeVoiceComposition()
                            }
                            val continueListening = isVoiceListening && voiceShouldAutoRestart && !isVoiceStopping
                            resetVoiceTranscriptState()
                            if (continueListening) {
                                voiceLeadingPrefix = computeVoiceLeadingPrefix()
                                restartVoiceListening()
                            } else {
                                isVoiceListening = false
                                isVoiceStopping = false
                                voiceShouldAutoRestart = false
                                syncVoiceInputGlowAnimation()
                                refreshVoiceUiState()
                            }
                        }
                    }

                    override fun onError(error: Int) {
                        serviceScope.launch(Dispatchers.Main.immediate) {
                            cancelVoiceStopJobs()
                            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                                Log.w(TAG, "Voice input permission denied by recognizer")
                                voiceShouldAutoRestart = false
                                isVoiceListening = false
                                isVoiceStopping = false
                                finalizeVoiceComposition()
                                resetVoiceTranscriptState()
                                refreshVoiceUiState()
                                toast("Microphone permission is required for voice input")
                                return@launch
                            }
                            val canRestart = isVoiceListening && voiceShouldAutoRestart && !isVoiceStopping
                            if (!canRestart) {
                                if (voiceLastTranscript.isNotBlank()) {
                                    commitVoiceTranscript(voiceLastTranscript, isFinal = true)
                                } else {
                                    finalizeVoiceComposition()
                                }
                                voiceShouldAutoRestart = false
                                isVoiceListening = false
                                isVoiceStopping = false
                                resetVoiceTranscriptState()
                                syncVoiceInputGlowAnimation()
                                refreshVoiceUiState()
                                return@launch
                            }
                            Log.w(TAG, "Voice input error: $error")
                            finalizeVoiceComposition()
                            resetVoiceTranscriptState()
                            voiceLeadingPrefix = computeVoiceLeadingPrefix()
                            restartVoiceListening()
                        }
                    }
                })
            }
        }.onFailure {
            Log.e(TAG, "Failed to initialize voice recognizer", it)
            toast("Voice input failed to initialize")
        }.getOrNull()
            ?.also { activeVoiceRecognizer = it }
    }

internal fun NboardImeService.restartVoiceListening() {
        serviceScope.launch(Dispatchers.Main) {
            delay(VOICE_RESTART_DELAY_MS)
            if (!isVoiceListening || !voiceShouldAutoRestart || isVoiceStopping) {
                return@launch
            }
            val recognizer = activeVoiceRecognizer ?: return@launch
            runCatching {
                recognizer.startListening(buildVoiceRecognizerIntent())
            }.onFailure {
                Log.w(TAG, "Failed to restart voice input", it)
                voiceShouldAutoRestart = false
                isVoiceListening = false
                isVoiceStopping = false
                refreshVoiceUiState()
            }
        }
    }

internal fun NboardImeService.destroyVoiceRecognizer() {
        cancelVoiceStopJobs()
        activeVoiceRecognizer?.let { recognizer ->
            runCatching { recognizer.destroy() }
        }
        activeVoiceRecognizer = null
    }

internal fun NboardImeService.buildVoiceRecognizerIntent(): Intent {
        val languageTag = when (keyboardLanguageMode) {
            KeyboardLanguageMode.FRENCH -> "fr-FR"
            KeyboardLanguageMode.ENGLISH -> "en-US"
            else -> Locale.getDefault().toLanguageTag()
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 950L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 0L)
        }
    }

internal fun NboardImeService.extractVoiceTranscript(bundle: Bundle?): String {
        val result = bundle
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
        return result.trim()
    }

internal fun NboardImeService.commitVoiceTranscript(transcript: String, isFinal: Boolean) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            serviceScope.launch(Dispatchers.Main.immediate) {
                commitVoiceTranscript(transcript, isFinal)
            }
            return
        }
        val normalized = transcript.trim()
        if (normalized.isBlank()) {
            if (isFinal) {
                finalizeVoiceComposition()
            }
            return
        }
        if (normalized == voiceLastTranscript && !isFinal) {
            return
        }
        val inputConnection = currentInputConnection ?: return
        val composingText = voiceLeadingPrefix + normalized
        if (composingText.isNotEmpty()) {
            inputConnection.setComposingText(composingText, 1)
            voiceHasActiveComposition = true
        }
        if (isFinal) {
            inputConnection.finishComposingText()
            voiceHasActiveComposition = false
        }
        voiceLastTranscript = normalized
        pendingAutoCorrection = null
        refreshAutoShiftFromContextAndRerender()
    }

internal fun NboardImeService.finalizeVoiceComposition() {
        if (!voiceHasActiveComposition) {
            return
        }
        currentInputConnection?.finishComposingText()
        voiceHasActiveComposition = false
    }

internal fun NboardImeService.computeVoiceLeadingPrefix(): String {
        val beforeCursor = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (beforeCursor.isBlank()) {
            return ""
        }
        return if (beforeCursor.last().isWhitespace()) "" else " "
    }

internal fun NboardImeService.resetVoiceTranscriptState() {
        voiceLastTranscript = ""
        voiceLeadingPrefix = ""
        voiceHasActiveComposition = false
    }

internal fun NboardImeService.hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

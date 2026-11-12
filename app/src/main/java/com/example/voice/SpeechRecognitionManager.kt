package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.model.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "SpeechRecognitionMgr"

sealed interface SpeechState {
    data object Idle : SpeechState
    data object Preparing : SpeechState
    data object Listening : SpeechState
    data class PartialResult(val text: String) : SpeechState
    data class FinalResult(val text: String) : SpeechState
    data class NoSpeechDetected(val message: String = "Didn't catch that. Please speak again.") : SpeechState
    data class Error(val message: String, val errorCode: Int = -1) : SpeechState
}

class SpeechRecognitionManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun createSystemSpeechIntent(language: AssistantLanguage): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Rathod AI (${language.displayName})")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    fun startListening(
        language: AssistantLanguage,
        onFinalResult: (String) -> Unit
    ) {
        if (!isAvailable()) {
            _speechState.value = SpeechState.Error("Speech recognition is not available on this device.")
            return
        }

        mainHandler.post {
            stopListeningInternal()

            try {
                // Prefer modern on-device recognizer if available on API 33+ for low latency and offline capabilities
                val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
                ) {
                    Log.d(TAG, "Initializing on-device SpeechRecognizer")
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    Log.d(TAG, "Initializing standard SpeechRecognizer")
                    SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer = recognizer.apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            Log.d(TAG, "SpeechRecognizer onReadyForSpeech")
                            _speechState.value = SpeechState.Listening
                        }

                        override fun onBeginningOfSpeech() {
                            Log.d(TAG, "SpeechRecognizer onBeginningOfSpeech")
                            _speechState.value = SpeechState.Listening
                        }

                        override fun onRmsChanged(rmsdB: Float) {
                            // Normalize -2dB..10dB into 0..1 range
                            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                            _rmsLevel.value = normalized
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            Log.d(TAG, "SpeechRecognizer onEndOfSpeech")
                            _rmsLevel.value = 0f
                        }

                        override fun onError(error: Int) {
                            _rmsLevel.value = 0f
                            val isNoMatchOrTimeout = error == SpeechRecognizer.ERROR_NO_MATCH ||
                                    error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

                            val message = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "Audio recording issue. Please check microphone."
                                SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network connection timed out."
                                SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please speak again."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy, please tap again."
                                SpeechRecognizer.ERROR_SERVER -> "Recognition server error."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please tap mic to speak."
                                else -> "Speech recognition notice ($error)"
                            }

                            Log.d(TAG, "SpeechRecognizer status: $error -> $message")

                            if (isNoMatchOrTimeout) {
                                _speechState.value = SpeechState.NoSpeechDetected(message)
                            } else {
                                _speechState.value = SpeechState.Error(message, error)
                            }
                        }

                        override fun onResults(results: Bundle?) {
                            _rmsLevel.value = 0f
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim().orEmpty()
                            Log.d(TAG, "SpeechRecognizer onResults: $text")
                            if (text.isNotEmpty()) {
                                _speechState.value = SpeechState.FinalResult(text)
                                onFinalResult(text)
                            } else {
                                _speechState.value = SpeechState.NoSpeechDetected("Didn't catch that. Please speak again.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val partial = matches?.firstOrNull()?.trim().orEmpty()
                            if (partial.isNotEmpty()) {
                                Log.d(TAG, "SpeechRecognizer onPartialResults: $partial")
                                _speechState.value = SpeechState.PartialResult(partial)
                            }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.code)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.code)
                    // Real-time streaming partial results
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_CONFIDENCE_SCORES, true)
                    // Generous silence buffers so recognition does not abort prematurely
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                }

                _speechState.value = SpeechState.Preparing
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start speech recognition", e)
                _speechState.value = SpeechState.Error("Failed to start speech: ${e.localizedMessage}")
            }
        }
    }

    private fun stopListeningInternal() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping SpeechRecognizer", e)
        } finally {
            speechRecognizer = null
            _rmsLevel.value = 0f
            if (_speechState.value is SpeechState.Listening || _speechState.value is SpeechState.Preparing) {
                _speechState.value = SpeechState.Idle
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            stopListeningInternal()
        }
    }

    fun resetState() {
        _speechState.value = SpeechState.Idle
        _rmsLevel.value = 0f
    }
}

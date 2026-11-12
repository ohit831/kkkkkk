package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.model.AssistantLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

private const val TAG = "TextToSpeechMgr"

sealed interface TtsState {
    data object Uninitialized : TtsState
    data object Ready : TtsState
    data class Speaking(val utteranceId: String, val text: String) : TtsState
    data object Paused : TtsState
    data class Error(val message: String) : TtsState
}

class TextToSpeechManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val _ttsState = MutableStateFlow<TtsState>(TtsState.Uninitialized)
    val ttsState: StateFlow<TtsState> = _ttsState.asStateFlow()

    private val _isFemaleVoiceActive = MutableStateFlow(true)
    val isFemaleVoiceActive: StateFlow<Boolean> = _isFemaleVoiceActive.asStateFlow()

    private val _pitch = MutableStateFlow(1.15f) // Default 1.15f produces feminine tone
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _speechRate = MutableStateFlow(1.0f)
    val speechRate: StateFlow<Float> = _speechRate.asStateFlow()

    private var lastSpokenText: String = ""
    private var lastLanguage: AssistantLanguage = AssistantLanguage.ENGLISH

    init {
        initialize()
    }

    private fun initialize() {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to instantiate TextToSpeech", e)
            _ttsState.value = TtsState.Error("TTS initialization failed: ${e.localizedMessage}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.d(TAG, "TextToSpeech initialized successfully")
            setupUtteranceListener()
            _ttsState.value = TtsState.Ready
            applyVoiceSettings(lastLanguage)
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status $status")
            _ttsState.value = TtsState.Error("TTS engine failed to initialize.")
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                Log.d(TAG, "TTS started utterance: $utteranceId")
                _ttsState.value = TtsState.Speaking(utteranceId.orEmpty(), lastSpokenText)
            }

            override fun onDone(utteranceId: String?) {
                Log.d(TAG, "TTS done utterance: $utteranceId")
                _ttsState.value = TtsState.Ready
            }

            override fun onError(utteranceId: String?) {
                Log.e(TAG, "TTS error utterance: $utteranceId")
                _ttsState.value = TtsState.Ready
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                Log.e(TAG, "TTS error utterance: $utteranceId code: $errorCode")
                _ttsState.value = TtsState.Ready
            }
        })
    }

    fun setFemaleVoicePreference(enabled: Boolean) {
        _isFemaleVoiceActive.value = enabled
        // Female voice timbre benefit: higher pitch if female engine voice is active or fallback
        _pitch.value = if (enabled) 1.15f else 1.0f
        applyVoiceSettings(lastLanguage)
    }

    fun setPitch(newPitch: Float) {
        _pitch.value = newPitch.coerceIn(0.5f, 2.0f)
        tts?.setPitch(_pitch.value)
    }

    fun setSpeechRate(rate: Float) {
        _speechRate.value = rate.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(_speechRate.value)
    }

    fun applyVoiceSettings(language: AssistantLanguage) {
        val engine = tts ?: return
        lastLanguage = language

        try {
            val availability = engine.isLanguageAvailable(language.locale)
            if (availability == TextToSpeech.LANG_MISSING_DATA || availability == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Language ${language.code} not fully supported by TTS engine (code: $availability)")
            } else {
                engine.language = language.locale
            }

            engine.setPitch(_pitch.value)
            engine.setSpeechRate(_speechRate.value)

            if (_isFemaleVoiceActive.value) {
                selectBestFemaleVoice(language)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error applying voice settings", e)
        }
    }

    private fun selectBestFemaleVoice(language: AssistantLanguage) {
        val engine = tts ?: return
        try {
            val voices = engine.voices ?: return
            val matchingVoices = voices.filter { voice ->
                voice.locale.language == language.locale.language
            }

            // Look for female voice identifiers
            val femaleVoice = matchingVoices.firstOrNull { voice ->
                val nameLower = voice.name.lowercase()
                nameLower.contains("female") ||
                        nameLower.contains("f00") ||
                        nameLower.contains("fem") ||
                        nameLower.contains("woman") ||
                        voice.features.any { it.contains("female", ignoreCase = true) }
            } ?: matchingVoices.firstOrNull()

            if (femaleVoice != null) {
                engine.voice = femaleVoice
                Log.d(TAG, "Selected TTS voice: ${femaleVoice.name} for ${language.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection could not query voices list", e)
        }
    }

    fun speak(
        text: String,
        language: AssistantLanguage,
        onStart: (() -> Unit)? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "TTS not ready, re-initializing")
            initialize()
            return
        }

        if (text.isBlank()) return

        lastSpokenText = text
        lastLanguage = language
        applyVoiceSettings(language)

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        onStart?.invoke()
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "engine.speak returned error code: $result")
            _ttsState.value = TtsState.Error("Speech output failed (code: $result)")
        }
    }

    fun replay() {
        if (lastSpokenText.isNotBlank()) {
            speak(lastSpokenText, lastLanguage)
        }
    }

    fun stop() {
        try {
            tts?.stop()
            _ttsState.value = TtsState.Ready
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop TTS", e)
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {
            Log.e(TAG, "Error shutting down TTS", e)
        }
    }
}

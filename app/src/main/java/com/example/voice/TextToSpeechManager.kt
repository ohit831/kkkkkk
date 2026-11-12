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

    private val _activeVoiceName = MutableStateFlow("Default Female")
    val activeVoiceName: StateFlow<String> = _activeVoiceName.asStateFlow()

    private val _pitch = MutableStateFlow(1.15f) // Default 1.15f produces a natural feminine tone
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
            val languageCode = language.locale.language

            // Score voices based on matching locale, female markers, and voice quality
            val scoredVoices = voices.filter { it.locale.language == languageCode }.map { voice ->
                var score = 0
                val nameLower = voice.name.lowercase()

                // Language match
                if (voice.locale == language.locale) score += 1000
                if (voice.locale.language == languageCode) score += 500

                // Female markers
                val isFemaleNamed = nameLower.contains("female") ||
                        nameLower.contains("#female") ||
                        nameLower.contains("-female") ||
                        nameLower.contains("woman") ||
                        nameLower.contains("fem") ||
                        nameLower.contains("f00") ||
                        nameLower.contains("f01") ||
                        nameLower.contains("girl") ||
                        voice.features.any { it.contains("female", ignoreCase = true) }

                if (isFemaleNamed) score += 800

                // High quality preference
                if (voice.quality == Voice.QUALITY_VERY_HIGH) score += 200
                else if (voice.quality == Voice.QUALITY_HIGH) score += 100

                // Penalize not-installed network voices if local exists
                if (voice.isNetworkConnectionRequired) score -= 50

                voice to score
            }.sortedByDescending { it.second }

            val bestVoice = scoredVoices.firstOrNull()?.first
            if (bestVoice != null) {
                engine.voice = bestVoice
                _activeVoiceName.value = bestVoice.name
                Log.d(TAG, "Selected TTS voice: ${bestVoice.name} for ${language.code}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection could not query voices list", e)
        }
    }

    /**
     * Sanitizes raw Gemini responses by removing markdown syntax, code fences,
     * bullet asterisks, and stray symbols so TTS speaks naturally without pronouncing formatting.
     */
    fun sanitizeForSpeech(rawText: String): String {
        return rawText
            .replace(Regex("```[\\s\\S]*?```"), "")          // Code blocks
            .replace(Regex("`[^`]*`"), "")                   // Inline code
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")        // Bold
            .replace(Regex("\\*(.*?)\\*"), "$1")              // Italic
            .replace(Regex("(?m)^[\\s]*[-*•]\\s+"), "")      // Bullet list items
            .replace(Regex("(?m)^[\\s]*#+\\s+"), "")          // Headers
            .replace(Regex("\\[Notice:.*?\\]"), "")           // Debug notices
            .replace(Regex("https?://\\S+"), "link")          // URLs
            .replace(Regex("[#_~>|]"), "")                   // Markdown chars
            .replace(Regex("\\s+"), " ")                     // Normalize whitespace
            .trim()
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

        val speechCleanText = sanitizeForSpeech(text)
        if (speechCleanText.isBlank()) return

        lastSpokenText = speechCleanText
        lastLanguage = language
        applyVoiceSettings(language)

        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        onStart?.invoke()
        val result = engine.speak(speechCleanText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
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

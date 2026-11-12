package com.example.model

import java.util.Locale

enum class AssistantLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val locale: Locale,
    val voiceGreeting: String
) {
    ENGLISH(
        code = "en-IN",
        displayName = "English",
        nativeLabel = "EN",
        locale = Locale.forLanguageTag("en-IN"),
        voiceGreeting = "Hello Rohit! I am Rathod, your personal AI assistant. How may I assist you today?"
    ),
    HINDI(
        code = "hi-IN",
        displayName = "हिन्दी",
        nativeLabel = "HI",
        locale = Locale.forLanguageTag("hi-IN"),
        voiceGreeting = "नमस्ते रोहित! मैं राठौड़ हूँ, आपकी निजी AI सहायक। आज मैं आपकी क्या सहायता कर सकती हूँ?"
    ),
    KANNADA(
        code = "kn-IN",
        displayName = "ಕನ್ನಡ",
        nativeLabel = "KN",
        locale = Locale.forLanguageTag("kn-IN"),
        voiceGreeting = "ನಮಸ್ಕಾರ ರೋಹಿತ್! ನಾನು ರಾಥೋಡ್, ನಿಮ್ಮ ವೈಯಕ್ತಿಕ AI ಸಹಾಯಕ. ಇಂದು ನಾನು ನಿಮಗೆ ಹೇಗೆ ಸಹಾಯ ಮಾಡಲಿ?"
    );

    companion object {
        fun fromCode(code: String): AssistantLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

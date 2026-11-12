package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiAiService
import com.example.commands.AppCommand
import com.example.commands.CommandExecutionResult
import com.example.commands.CommandRouter
import com.example.data.NoteItem
import com.example.data.NotesRepository
import com.example.model.AssistantLanguage
import com.example.ui.components.ChatMessage
import com.example.ui.components.OrbState
import com.example.voice.SpeechRecognitionManager
import com.example.voice.SpeechState
import com.example.voice.TextToSpeechManager
import com.example.voice.TtsState
import com.example.wakeword.WakeWordManager
import com.example.wakeword.WakeWordManagerImpl
import com.example.wakeword.WakeWordStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "MainViewModel"

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    // Core managers
    val speechManager = SpeechRecognitionManager(context)
    val ttsManager = TextToSpeechManager(context)
    val geminiService = GeminiAiService()
    val notesRepository = NotesRepository(context)
    val commandRouter = CommandRouter(context, notesRepository)
    val wakeWordManager: WakeWordManager = WakeWordManagerImpl(context)

    // Language selection (Kannada, Hindi, English)
    private val _activeLanguage = MutableStateFlow(AssistantLanguage.ENGLISH)
    val activeLanguage: StateFlow<AssistantLanguage> = _activeLanguage.asStateFlow()

    // Orb visual state
    private val _orbState = MutableStateFlow(OrbState.IDLE)
    val orbState: StateFlow<OrbState> = _orbState.asStateFlow()

    // Status banner message
    private val _statusText = MutableStateFlow("Tap microphone or say 'Hey Rathod'")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    // Realtime recognized words display
    private val _liveSpeechText = MutableStateFlow("")
    val liveSpeechText: StateFlow<String> = _liveSpeechText.asStateFlow()

    // Conversation history
    private val _transcript = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                isUser = false,
                text = "Hello Rohit! I am Rathod, your personal AI assistant. How may I assist you today?",
                languageLabel = "EN"
            )
        )
    )
    val transcript: StateFlow<List<ChatMessage>> = _transcript.asStateFlow()

    // Pending intent event (e.g. launching YouTube, SMS, Alarm, Dialer)
    private val _pendingIntentEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val pendingIntentEvent: SharedFlow<Intent> = _pendingIntentEvent.asSharedFlow()

    // Confirmation dialog state for sensitive operations
    private val _pendingConfirmation = MutableStateFlow<CommandExecutionResult.ConfirmationRequired?>(null)
    val pendingConfirmation: StateFlow<CommandExecutionResult.ConfirmationRequired?> = _pendingConfirmation.asStateFlow()

    // Notes list state
    private val _notesList = MutableStateFlow<List<NoteItem>>(emptyList())
    val notesList: StateFlow<List<NoteItem>> = _notesList.asStateFlow()

    init {
        refreshNotes()
        observeSpeechState()
        observeTtsState()
    }

    private fun refreshNotes() {
        _notesList.value = notesRepository.getAllNotes()
    }

    private fun observeSpeechState() {
        viewModelScope.launch {
            speechManager.speechState.collect { state ->
                when (state) {
                    is SpeechState.Idle -> {
                        if (_orbState.value == OrbState.LISTENING) {
                            _orbState.value = OrbState.IDLE
                            _statusText.value = "Tap microphone to speak"
                        }
                    }

                    is SpeechState.Preparing -> {
                        _orbState.value = OrbState.LISTENING
                        _statusText.value = "Listening initialized..."
                        _liveSpeechText.value = ""
                    }

                    is SpeechState.Listening -> {
                        _orbState.value = OrbState.LISTENING
                        _statusText.value = "Listening to you, Rohit..."
                    }

                    is SpeechState.PartialResult -> {
                        _liveSpeechText.value = state.text
                    }

                    is SpeechState.FinalResult -> {
                        _liveSpeechText.value = state.text
                        handleRecognizedText(state.text)
                    }

                    is SpeechState.NoSpeechDetected -> {
                        _orbState.value = OrbState.IDLE
                        _statusText.value = state.message
                        _liveSpeechText.value = ""
                    }

                    is SpeechState.Error -> {
                        if (state.errorCode == 7 || state.errorCode == 8) {
                            _orbState.value = OrbState.IDLE
                            _statusText.value = state.message
                            _liveSpeechText.value = ""
                        } else {
                            _orbState.value = OrbState.ERROR
                            _statusText.value = state.message
                            addAssistantMessage(state.message, isError = true)
                        }
                    }
                }
            }
        }
    }

    private fun observeTtsState() {
        viewModelScope.launch {
            ttsManager.ttsState.collect { state ->
                when (state) {
                    is TtsState.Speaking -> {
                        _orbState.value = OrbState.SPEAKING
                        _statusText.value = "Rathod is speaking..."
                    }

                    is TtsState.Ready -> {
                        if (_orbState.value == OrbState.SPEAKING) {
                            _orbState.value = OrbState.IDLE
                            _statusText.value = "Ready for your next command"
                        }
                    }

                    is TtsState.Error -> {
                        _statusText.value = state.message
                    }

                    else -> {}
                }
            }
        }
    }

    fun setLanguage(language: AssistantLanguage) {
        _activeLanguage.value = language
        ttsManager.applyVoiceSettings(language)
        _statusText.value = "Language set to ${language.displayName}"
    }

    fun startListeningWithPermission() {
        ttsManager.stop()
        _liveSpeechText.value = ""
        speechManager.startListening(_activeLanguage.value) { recognizedText ->
            // Callback handled in observeSpeechState via SpeechState.FinalResult
        }
    }

    fun stopListening() {
        speechManager.stopListening()
        _orbState.value = OrbState.IDLE
    }

    fun handleRecognizedText(text: String) {
        if (text.isBlank()) return

        addUserMessage(text, _activeLanguage.value.nativeLabel)
        _orbState.value = OrbState.THINKING
        _statusText.value = "Rathod is processing..."

        viewModelScope.launch {
            try {
                val assistantResponse = geminiService.processUserCommand(text, _activeLanguage.value)
                processAssistantResponse(assistantResponse)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error processing AI response", e)
                val errorMessage = "Error connecting to Rathod AI: ${e.localizedMessage ?: "Unknown network failure"}"
                _orbState.value = OrbState.ERROR
                _statusText.value = "Error: Check connection"
                addAssistantMessage(errorMessage, isError = true)
            }
        }
    }

    fun executeQuickCommand(commandText: String) {
        handleRecognizedText(commandText)
    }

    private fun processAssistantResponse(response: com.example.ai.AssistantResponse) {
        val result = commandRouter.executeCommand(response.command, userConfirmed = false)

        when (result) {
            is CommandExecutionResult.Executed -> {
                addAssistantMessage(
                    text = response.spokenText,
                    commandAction = response.command.rawAction,
                    commandFeedback = result.feedback
                )
                refreshNotes()

                // Speak response aloud
                ttsManager.speak(response.spokenText, _activeLanguage.value)

                // Launch pending action intent if available (e.g. YouTube, Maps, SMS, Alarm)
                result.pendingIntent?.let { intent ->
                    _pendingIntentEvent.tryEmit(intent)
                }
            }

            is CommandExecutionResult.ConfirmationRequired -> {
                _pendingConfirmation.value = result
                addAssistantMessage(
                    text = result.prompt,
                    commandAction = response.command.rawAction,
                    commandFeedback = "Awaiting confirmation"
                )
                ttsManager.speak(result.prompt, _activeLanguage.value)
            }

            is CommandExecutionResult.Failed -> {
                addAssistantMessage(
                    text = "${response.spokenText}\n[Notice: ${result.reason}]",
                    commandAction = response.command.rawAction,
                    commandFeedback = result.reason,
                    isError = true
                )
                ttsManager.speak(response.spokenText, _activeLanguage.value)
            }
        }
    }

    fun confirmPendingAction() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null

        val result = commandRouter.executeCommand(pending.command, userConfirmed = true)
        if (result is CommandExecutionResult.Executed) {
            addAssistantMessage(
                text = "Confirmed. ${result.feedback}",
                commandAction = pending.command.rawAction,
                commandFeedback = result.feedback
            )
            ttsManager.speak(result.feedback, _activeLanguage.value)
            result.pendingIntent?.let { intent ->
                _pendingIntentEvent.tryEmit(intent)
            }
        }
    }

    fun cancelPendingAction() {
        _pendingConfirmation.value = null
        addAssistantMessage(
            text = "Cancelled operation, Rohit.",
            commandAction = "CANCELLED",
            commandFeedback = "Operation cancelled by user"
        )
        ttsManager.speak("Operation cancelled.", _activeLanguage.value)
    }

    fun replayLastAssistantMessage() {
        ttsManager.replay()
    }

    fun stopSpeaking() {
        ttsManager.stop()
        _orbState.value = OrbState.IDLE
        _statusText.value = "Speech stopped"
    }

    fun testVoiceOutput() {
        val sample = _activeLanguage.value.voiceGreeting
        ttsManager.speak(sample, _activeLanguage.value)
    }

    fun deleteNote(id: String) {
        notesRepository.deleteNote(id)
        refreshNotes()
    }

    private fun addUserMessage(text: String, langLabel: String) {
        val msg = ChatMessage(
            isUser = true,
            text = text,
            languageLabel = langLabel
        )
        _transcript.value = _transcript.value + msg
    }

    private fun addAssistantMessage(
        text: String,
        commandAction: String? = null,
        commandFeedback: String? = null,
        isError: Boolean = false
    ) {
        val msg = ChatMessage(
            isUser = false,
            text = text,
            languageLabel = _activeLanguage.value.nativeLabel,
            commandAction = commandAction,
            commandFeedback = commandFeedback,
            isError = isError
        )
        _transcript.value = _transcript.value + msg
    }

    override fun onCleared() {
        super.onCleared()
        speechManager.stopListening()
        ttsManager.shutdown()
    }
}

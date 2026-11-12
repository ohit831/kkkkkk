package com.example.ai

import android.util.Log
import com.example.commands.AppCommand
import com.example.model.AssistantLanguage
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAiService"

data class AssistantResponse(
    val spokenText: String,
    val command: AppCommand
)

class GeminiAiService {

    // Structured JSON Schema for typed intelligent command routing
    private val commandSchema = Schema.obj(
        properties = mapOf(
            "action" to Schema.string(
                description = "Primary intent: OPEN_APP, MESSAGE_DRAFT, SET_ALARM, CREATE_NOTE, CALL_CONTACT, CREATE_REMINDER, OPEN_MAPS, DEVICE_SETTING, GENERAL_QUESTION, UNSUPPORTED"
            ),
            "spokenResponse" to Schema.string(
                description = "Natural, conversational spoken reply addressing Rohit in the requested language (Kannada, Hindi, or English)"
            ),
            "appName" to Schema.string(description = "Clean target application name for OPEN_APP (e.g., YouTube, WhatsApp, Camera, Settings, Chrome)"),
            "recipient" to Schema.string(description = "Recipient name or phone number for MESSAGE_DRAFT"),
            "messageBody" to Schema.string(description = "Message body content for MESSAGE_DRAFT"),
            "alarmHour" to Schema.integer(description = "Hour in 24-hour format (0-23) for SET_ALARM"),
            "alarmMinute" to Schema.integer(description = "Minute (0-59) for SET_ALARM"),
            "alarmLabel" to Schema.string(description = "Alarm label or purpose for SET_ALARM"),
            "noteTitle" to Schema.string(description = "Descriptive title for CREATE_NOTE"),
            "noteContent" to Schema.string(description = "Body of the note for CREATE_NOTE"),
            "contactName" to Schema.string(description = "Contact name for CALL_CONTACT"),
            "phoneNumber" to Schema.string(description = "Phone number for CALL_CONTACT"),
            "locationQuery" to Schema.string(description = "Destination or search query for OPEN_MAPS"),
            "settingType" to Schema.string(description = "Target device setting: FLASHLIGHT, WIFI, BLUETOOTH, VOLUME, DISPLAY, AIRPLANE_MODE, BATTERY"),
            "settingEnable" to Schema.boolean(description = "Optional state boolean (true to turn on, false to turn off, or null to open)")
        ),
        optionalProperties = listOf(
            "appName",
            "recipient",
            "messageBody",
            "alarmHour",
            "alarmMinute",
            "alarmLabel",
            "noteTitle",
            "noteContent",
            "contactName",
            "phoneNumber",
            "locationQuery",
            "settingType",
            "settingEnable"
        )
    )

    private val structuredConfig = generationConfig {
        responseMimeType = "application/json"
        responseSchema = commandSchema
        temperature = 0.2f
    }

    private val structuredModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = structuredConfig,
            systemInstruction = content {
                text(
                    """
                    You are Rathod, a futuristic, loyal, and highly capable native AI voice assistant built for your owner, Rohit.
                    Owner greeting: Address the user as 'Rohit'.
                    Languages: You are completely fluent in Kannada (ಕನ್ನಡ), Hindi (हिन्दी), and English.
                    
                    Voice Transcript Understanding Rules:
                    1. The user's input comes from Android SpeechRecognizer transcripts. It may include spoken vernacular phrasing, transliterations, numbers in words, or colloquial expressions in Kannada, Hindi, or English.
                    2. Identify intent intelligently:
                       - App Launching ('Open YouTube', 'YouTube kholo', 'YouTube open madi'):
                         action = 'OPEN_APP', appName = 'YouTube'.
                       - Message Drafting ('Draft message to Rohit', 'Rohit ge message madi: meeting starts at 4'):
                         action = 'MESSAGE_DRAFT', recipient = 'Rohit', messageBody = extracted message.
                       - Setting Alarm ('Set alarm for 7:30 AM', 'beligge 7 gantege alarm idu', 'subah 7 baje ka alarm lagao'):
                         action = 'SET_ALARM', convert to 24-hr alarmHour (0-23) and alarmMinute (0-59), alarmLabel = descriptive label.
                       - Taking Notes ('Take a note: Buy groceries', 'Ek note banao: Call electrician', 'Ondu note madi'):
                         action = 'CREATE_NOTE', noteTitle = concise title, noteContent = full note text.
                       - Phone Calls ('Call Rohit', 'Rohit ge call madi'):
                         action = 'CALL_CONTACT', contactName = 'Rohit', phoneNumber = extracted number if given.
                       - Navigation / Maps ('Where is the nearest cafe?', 'Open maps for Bangalore', 'Dastagi hoguvudu hege'):
                         action = 'OPEN_MAPS', locationQuery = destination.
                       - Reminders ('Remind me tomorrow at 9 AM to submit report'):
                         action = 'CREATE_REMINDER', noteTitle = reminder text, locationQuery = time expression.
                       - Questions, Knowledge, Casual Chat, or Explanations:
                         action = 'GENERAL_QUESTION'.
                    3. Spoken Response:
                       - Provide an articulate, polite, direct spokenResponse in the active language (Kannada, Hindi, or English).
                       - Keep spoken responses conversational, friendly, and suitable for Text-to-Speech (avoid markdown asterisks or bullet characters).
                    4. Always output strictly valid JSON conforming to the schema.
                    """.trimIndent()
                )
            }
        )
    }

    private val conversationalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are Rathod, a futuristic, loyal, and highly intelligent native AI voice assistant built for Rohit.
                    You converse fluently in Kannada, Hindi, and English.
                    Keep spoken responses natural, crisp, helpful, and friendly.
                    Address the user as Rohit.
                    """.trimIndent()
                )
            }
        )
    }

    // Keep conversation history in memory for multi-turn session
    private val conversationHistory = mutableListOf<Content>()

    suspend fun processUserCommand(
        userInput: String,
        activeLanguage: AssistantLanguage
    ): AssistantResponse {
        val userPrompt = "(Language: ${activeLanguage.displayName} [${activeLanguage.code}]) Rohit says: $userInput"

        return try {
            // Start multi-turn chat session with recent conversation context
            val chat = structuredModel.startChat(
                history = conversationHistory.takeLast(8)
            )

            val response = withTimeout(60_000L) {
                chat.sendMessage(userPrompt)
            }

            val rawJson = response.text
            if (rawJson.isNullOrBlank()) {
                throw IllegalStateException("Gemini model returned empty response.")
            }

            val parsed = parseJsonResponse(rawJson, userInput, activeLanguage)

            // Update multi-turn history
            conversationHistory.add(content("user") { text(userInput) })
            conversationHistory.add(content("model") { text(parsed.spokenText) })

            parsed
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini command processing timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI Gemini inference error", e)
            throw e
        }
    }

    suspend fun streamConversationalReply(
        prompt: String,
        language: AssistantLanguage
    ): Flow<String> = flow {
        val enhancedPrompt = "(Language: ${language.displayName}) Rohit asks: $prompt"
        try {
            withTimeout(60_000L) {
                conversationalModel.generateContentStream(enhancedPrompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    if (text.isNotEmpty()) {
                        emit(text)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Streaming failed", e)
            throw e
        }
    }

    private fun parseJsonResponse(
        rawJson: String,
        originalInput: String,
        activeLanguage: AssistantLanguage
    ): AssistantResponse {
        val cleanJson = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(cleanJson)
        val action = json.optString("action", "GENERAL_QUESTION").uppercase()
        val spoken = json.optString(
            "spokenResponse",
            when (activeLanguage) {
                AssistantLanguage.KANNADA -> "ರೋಹಿತ್, ನಿಮ್ಮ ವಿನಂತಿಯನ್ನು ಪ್ರಕ್ರಿಯೆಗೊಳಿಸಲಾಗಿದೆ."
                AssistantLanguage.HINDI -> "रोहित, आपका अनुरोध पूरा किया गया।"
                AssistantLanguage.ENGLISH -> "Understood, Rohit. Working on that for you."
            }
        )

        val command: AppCommand = when (action) {
            "OPEN_APP" -> {
                val appName = json.optString("appName").ifBlank { originalInput }
                AppCommand.OpenApp(appName = appName)
            }

            "MESSAGE_DRAFT" -> {
                val recipient = json.optString("recipient", "")
                val body = json.optString("messageBody", originalInput)
                AppCommand.MessageDraft(recipient = recipient, messageBody = body)
            }

            "SET_ALARM" -> {
                val hour = json.optInt("alarmHour", 7)
                val minute = json.optInt("alarmMinute", 0)
                val label = json.optString("alarmLabel", "Rathod AI Alarm")
                AppCommand.SetAlarm(hour = hour, minute = minute, label = label)
            }

            "CREATE_NOTE" -> {
                val title = json.optString("noteTitle", "Note from Voice")
                val content = json.optString("noteContent", originalInput)
                AppCommand.CreateNote(title = title, content = content)
            }

            "CALL_CONTACT" -> {
                val name = json.optString("contactName", "")
                val phone = json.optString("phoneNumber", "")
                AppCommand.CallContact(contactName = name, phoneNumber = phone)
            }

            "CREATE_REMINDER" -> {
                val title = json.optString("noteTitle", originalInput)
                val time = json.optString("locationQuery", "today")
                AppCommand.CreateReminder(title = title, timeDesc = time)
            }

            "OPEN_MAPS" -> {
                val query = json.optString("locationQuery", originalInput)
                AppCommand.OpenMaps(query = query)
            }

            "DEVICE_SETTING" -> {
                val typeStr = json.optString("settingType", "WIFI").uppercase()
                val settingType = try {
                    com.example.commands.SettingType.valueOf(typeStr)
                } catch (_: Exception) {
                    com.example.commands.SettingType.WIFI
                }
                val enable = if (json.has("settingEnable")) json.optBoolean("settingEnable") else null
                AppCommand.DeviceSetting(settingType, enable)
            }

            "GENERAL_QUESTION" -> {
                AppCommand.GeneralQuestion(question = originalInput)
            }

            else -> {
                AppCommand.Unsupported(rawAction = action, reason = "Unrecognized command action")
            }
        }

        return AssistantResponse(
            spokenText = spoken,
            command = command
        )
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}

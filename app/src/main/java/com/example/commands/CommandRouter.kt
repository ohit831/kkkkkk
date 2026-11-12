package com.example.commands

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.NotesRepository
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.regex.Pattern

private const val TAG = "CommandRouter"

enum class SettingType {
    FLASHLIGHT,
    WIFI,
    BLUETOOTH,
    VOLUME,
    AIRPLANE_MODE,
    DISPLAY,
    BATTERY
}

sealed interface AppCommand {
    val rawAction: String

    data class OpenApp(
        val appName: String,
        val packageName: String? = null
    ) : AppCommand {
        override val rawAction = "OPEN_APP"
    }

    data class MessageDraft(
        val recipient: String,
        val messageBody: String
    ) : AppCommand {
        override val rawAction = "MESSAGE_DRAFT"
    }

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String
    ) : AppCommand {
        override val rawAction = "SET_ALARM"
    }

    data class CreateNote(
        val title: String,
        val content: String
    ) : AppCommand {
        override val rawAction = "CREATE_NOTE"
    }

    data class CallContact(
        val contactName: String,
        val phoneNumber: String
    ) : AppCommand {
        override val rawAction = "CALL_CONTACT"
    }

    data class CreateReminder(
        val title: String,
        val timeDesc: String
    ) : AppCommand {
        override val rawAction = "CREATE_REMINDER"
    }

    data class OpenMaps(
        val query: String
    ) : AppCommand {
        override val rawAction = "OPEN_MAPS"
    }

    data class DeviceSetting(
        val settingType: SettingType,
        val enable: Boolean? = null
    ) : AppCommand {
        override val rawAction = "DEVICE_SETTING"
    }

    data class GeneralQuestion(
        val question: String
    ) : AppCommand {
        override val rawAction = "GENERAL_QUESTION"
    }

    data class Unsupported(
        override val rawAction: String,
        val reason: String
    ) : AppCommand
}

sealed interface CommandExecutionResult {
    data class Executed(
        val feedback: String,
        val pendingIntent: Intent? = null
    ) : CommandExecutionResult

    data class ConfirmationRequired(
        val prompt: String,
        val command: AppCommand
    ) : CommandExecutionResult

    data class Failed(
        val reason: String
    ) : CommandExecutionResult
}

class CommandRouter(
    private val context: Context,
    private val notesRepository: NotesRepository
) {

    private var isTorchOn = false

    private val commonPackageMap = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "maps" to "com.google.android.apps.maps",
        "google maps" to "com.google.android.apps.maps",
        "camera" to "camera_intent",
        "settings" to "settings_intent",
        "calculator" to "calculator_intent",
        "clock" to "com.google.android.deskclock",
        "gmail" to "com.google.android.gm",
        "spotify" to "com.spotify.music",
        "telegram" to "org.telegram.messenger"
    )

    fun executeCommand(command: AppCommand, userConfirmed: Boolean = false): CommandExecutionResult {
        Log.d(TAG, "Routing command: ${command.rawAction}")
        return when (command) {
            is AppCommand.OpenApp -> handleOpenApp(command)
            is AppCommand.MessageDraft -> handleMessageDraft(command)
            is AppCommand.SetAlarm -> handleSetAlarm(command)
            is AppCommand.CreateNote -> handleCreateNote(command)
            is AppCommand.CallContact -> handleCallContact(command, userConfirmed)
            is AppCommand.CreateReminder -> handleCreateReminder(command)
            is AppCommand.OpenMaps -> handleOpenMaps(command)
            is AppCommand.DeviceSetting -> handleDeviceSetting(command)
            is AppCommand.GeneralQuestion -> CommandExecutionResult.Executed("Answering question")
            is AppCommand.Unsupported -> CommandExecutionResult.Failed(
                "Command '${command.rawAction}' is not supported in this stage: ${command.reason}"
            )
        }
    }

    /**
     * Parses raw text or natural language responses into typed AppCommands
     */
    fun parseCommandFromText(rawText: String): AppCommand {
        val trimmed = rawText.trim()

        // 1. Check if input is a JSON string
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val action = json.optString("action", "").uppercase()
                when (action) {
                    "OPEN_APP" -> return AppCommand.OpenApp(json.optString("appName", "App"))
                    "MESSAGE_DRAFT" -> return AppCommand.MessageDraft(
                        recipient = json.optString("recipient", ""),
                        messageBody = json.optString("messageBody", "")
                    )
                    "SET_ALARM" -> return AppCommand.SetAlarm(
                        hour = json.optInt("alarmHour", 7),
                        minute = json.optInt("alarmMinute", 0),
                        label = json.optString("alarmLabel", "Alarm")
                    )
                    "CREATE_NOTE" -> return AppCommand.CreateNote(
                        title = json.optString("noteTitle", "Note"),
                        content = json.optString("noteContent", "")
                    )
                    "CALL_CONTACT" -> return AppCommand.CallContact(
                        contactName = json.optString("contactName", ""),
                        phoneNumber = json.optString("phoneNumber", "")
                    )
                    "OPEN_MAPS" -> return AppCommand.OpenMaps(json.optString("locationQuery", ""))
                    "DEVICE_SETTING" -> {
                        val typeStr = json.optString("settingType", "WIFI").uppercase()
                        val settingType = try { SettingType.valueOf(typeStr) } catch (_: Exception) { SettingType.WIFI }
                        val enable = if (json.has("settingEnable")) json.optBoolean("settingEnable") else null
                        return AppCommand.DeviceSetting(settingType, enable)
                    }
                }
            } catch (_: Exception) {}
        }

        val lower = trimmed.lowercase(Locale.ROOT)

        // 2. Natural language parsing for device settings toggles
        if (lower.contains("flashlight") || lower.contains("torch")) {
            val enable = if (lower.contains("off") || lower.contains("band") || lower.contains("stop")) {
                false
            } else if (lower.contains("on") || lower.contains("chalu") || lower.contains("start") || lower.contains("shuru")) {
                true
            } else {
                null
            }
            return AppCommand.DeviceSetting(SettingType.FLASHLIGHT, enable)
        }

        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return AppCommand.DeviceSetting(SettingType.WIFI)
        }

        if (lower.contains("bluetooth")) {
            return AppCommand.DeviceSetting(SettingType.BLUETOOTH)
        }

        if (lower.contains("volume") || lower.contains("sound setting") || lower.contains("mute")) {
            return AppCommand.DeviceSetting(SettingType.VOLUME)
        }

        if (lower.contains("airplane mode") || lower.contains("flight mode")) {
            return AppCommand.DeviceSetting(SettingType.AIRPLANE_MODE)
        }

        if (lower.contains("brightness") || lower.contains("display setting")) {
            return AppCommand.DeviceSetting(SettingType.DISPLAY)
        }

        if (lower.contains("battery") || lower.contains("power saver")) {
            return AppCommand.DeviceSetting(SettingType.BATTERY)
        }

        // 3. Natural language alarm parsing
        if (lower.contains("alarm")) {
            val timeMatcher = Pattern.compile("(\\d{1,2})[:.]?(\\d{2})?\\s*(am|pm)?").matcher(lower)
            if (timeMatcher.find()) {
                var hour = timeMatcher.group(1)?.toIntOrNull() ?: 7
                val minute = timeMatcher.group(2)?.toIntOrNull() ?: 0
                val amPm = timeMatcher.group(3)
                if (amPm == "pm" && hour < 12) hour += 12
                if (amPm == "am" && hour == 12) hour = 0
                return AppCommand.SetAlarm(hour = hour.coerceIn(0, 23), minute = minute.coerceIn(0, 59), label = "Rathod AI Alarm")
            }
            return AppCommand.SetAlarm(hour = 7, minute = 0, label = "Rathod AI Alarm")
        }

        // 4. Natural language note parsing
        if (lower.startsWith("note") || lower.contains("take a note") || lower.contains("create note") || lower.contains("note madi")) {
            val cleanContent = trimmed.replace(Regex("(?i)^(take a note[:]?|create note[:]?|note madi[:]?|note[:]?)\\s*"), "")
            return AppCommand.CreateNote(title = "Voice Note", content = cleanContent.ifBlank { trimmed })
        }

        // 5. Natural language app opening
        if (lower.startsWith("open ") || lower.contains(" kholo") || lower.contains(" open madi")) {
            val app = trimmed
                .replace(Regex("(?i)^open\\s+"), "")
                .replace(Regex("(?i)\\s+kholo$"), "")
                .replace(Regex("(?i)\\s+open madi$"), "")
                .trim()
            return AppCommand.OpenApp(appName = app.ifBlank { "App" })
        }

        // 6. Natural language message draft
        if (lower.startsWith("message ") || lower.startsWith("draft message") || lower.contains("message madi")) {
            val clean = trimmed.replace(Regex("(?i)^(draft message|message madi|message)\\s*"), "")
            val parts = clean.split(":", limit = 2)
            val recipient = parts.firstOrNull()?.trim().orEmpty()
            val body = if (parts.size > 1) parts[1].trim() else clean
            return AppCommand.MessageDraft(recipient = recipient, messageBody = body)
        }

        // 7. Natural language maps
        if (lower.contains("map") || lower.contains("directions to") || lower.contains("navigate to")) {
            val query = trimmed.replace(Regex("(?i)^(open maps for|directions to|navigate to|map of)\\s*"), "")
            return AppCommand.OpenMaps(query = query)
        }

        return AppCommand.GeneralQuestion(trimmed)
    }

    private fun handleOpenApp(cmd: AppCommand.OpenApp): CommandExecutionResult {
        val appNameClean = cmd.appName.trim().lowercase()

        // 1. Check intent shortcuts
        if (appNameClean == "camera" || appNameClean.contains("camera")) {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return if (isIntentAvailable(intent)) {
                CommandExecutionResult.Executed("Opening Camera for you, Rohit.", intent)
            } else {
                CommandExecutionResult.Failed("Camera application not available.")
            }
        }

        if (appNameClean == "settings" || appNameClean.contains("settings")) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return CommandExecutionResult.Executed("Opening system settings.", intent)
        }

        // 2. Check mapped package
        val targetPackage = cmd.packageName ?: commonPackageMap[appNameClean]

        if (!targetPackage.isNullOrBlank()) {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                return CommandExecutionResult.Executed("Opening ${cmd.appName}...", launchIntent)
            }
        }

        // 3. Search installed apps by name match
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in packages) {
                val label = pm.getApplicationLabel(appInfo).toString().lowercase()
                if (label == appNameClean || label.contains(appNameClean)) {
                    val intent = pm.getLaunchIntentForPackage(appInfo.packageName)
                    if (intent != null) {
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        return CommandExecutionResult.Executed("Opening ${pm.getApplicationLabel(appInfo)}...", intent)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up installed apps", e)
        }

        return CommandExecutionResult.Failed(
            "Could not find an app named '${cmd.appName}' installed on this device."
        )
    }

    private fun handleDeviceSetting(cmd: AppCommand.DeviceSetting): CommandExecutionResult {
        return when (cmd.settingType) {
            SettingType.FLASHLIGHT -> handleToggleFlashlight(cmd.enable)

            SettingType.WIFI -> {
                val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Intent(Settings.Panel.ACTION_WIFI)
                } else {
                    Intent(Settings.ACTION_WIFI_SETTINGS)
                }.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                CommandExecutionResult.Executed("Opening Wi-Fi settings for you, Rohit.", intent)
            }

            SettingType.BLUETOOTH -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                CommandExecutionResult.Executed("Opening Bluetooth settings for you, Rohit.", intent)
            }

            SettingType.VOLUME -> {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                CommandExecutionResult.Executed("Opening Sound & Volume settings.", intent)
            }

            SettingType.AIRPLANE_MODE -> {
                val intent = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                CommandExecutionResult.Executed("Opening Airplane Mode settings.", intent)
            }

            SettingType.DISPLAY -> {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                CommandExecutionResult.Executed("Opening Display & Brightness settings.", intent)
            }

            SettingType.BATTERY -> {
                val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                CommandExecutionResult.Executed("Opening Battery settings.", intent)
            }
        }
    }

    private fun handleToggleFlashlight(enable: Boolean?): CommandExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraManager != null && cameraId != null) {
                val shouldEnable = enable ?: !isTorchOn
                cameraManager.setTorchMode(cameraId, shouldEnable)
                isTorchOn = shouldEnable
                val stateText = if (shouldEnable) "turned on" else "turned off"
                CommandExecutionResult.Executed("Flashlight $stateText for you, Rohit.")
            } else {
                CommandExecutionResult.Failed("Flashlight hardware not available on this device.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling flashlight", e)
            CommandExecutionResult.Failed("Could not toggle flashlight: ${e.localizedMessage}")
        }
    }

    private fun handleMessageDraft(cmd: AppCommand.MessageDraft): CommandExecutionResult {
        if (cmd.messageBody.isBlank()) {
            return CommandExecutionResult.Failed("Message draft body cannot be empty.")
        }

        val recipient = cmd.recipient.trim()
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(recipient)}")
            putExtra("sms_body", cmd.messageBody)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return if (isIntentAvailable(intent)) {
            val targetDesc = if (recipient.isNotBlank()) " to $recipient" else ""
            CommandExecutionResult.Executed(
                "Drafted message$targetDesc. Ready to send.",
                intent
            )
        } else {
            // Fallback to generic ACTION_SEND
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cmd.messageBody)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            CommandExecutionResult.Executed(
                "Drafted message: '${cmd.messageBody}'. Select an app to send.",
                sendIntent
            )
        }
    }

    private fun handleSetAlarm(cmd: AppCommand.SetAlarm): CommandExecutionResult {
        if (cmd.hour !in 0..23 || cmd.minute !in 0..59) {
            return CommandExecutionResult.Failed(
                "Invalid alarm time: ${cmd.hour}:${String.format("%02d", cmd.minute)}. Hour must be 0-23 and minute 0-59."
            )
        }

        val label = if (cmd.label.isBlank()) "Rathod AI Alarm" else cmd.label
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, cmd.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, cmd.minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        val formattedTime = String.format("%02d:%02d", cmd.hour, cmd.minute)
        return CommandExecutionResult.Executed(
            "Setting alarm for $formattedTime with label '$label'.",
            intent
        )
    }

    private fun handleCreateNote(cmd: AppCommand.CreateNote): CommandExecutionResult {
        if (cmd.content.isBlank()) {
            return CommandExecutionResult.Failed("Note content cannot be empty.")
        }

        val title = if (cmd.title.isBlank()) "Quick Note" else cmd.title
        val note = notesRepository.addNote(title, cmd.content)

        return CommandExecutionResult.Executed(
            "Note '${note.title}' saved successfully in Rathod AI."
        )
    }

    private fun handleCallContact(cmd: AppCommand.CallContact, userConfirmed: Boolean): CommandExecutionResult {
        if (cmd.phoneNumber.isBlank()) {
            return CommandExecutionResult.Failed("Phone number or contact is required to initiate call.")
        }

        if (!userConfirmed) {
            return CommandExecutionResult.ConfirmationRequired(
                prompt = "Do you want Rathod to prepare a phone call to ${cmd.contactName.ifBlank { cmd.phoneNumber }}?",
                command = cmd
            )
        }

        // Safe dial intent: does NOT auto-call, user confirms on dialer
        val dialIntent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:${Uri.encode(cmd.phoneNumber)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return CommandExecutionResult.Executed(
            "Opening dialer for ${cmd.contactName.ifBlank { cmd.phoneNumber }}.",
            dialIntent
        )
    }

    private fun handleCreateReminder(cmd: AppCommand.CreateReminder): CommandExecutionResult {
        val calIntent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, cmd.title)
            putExtra(CalendarContract.Events.DESCRIPTION, "Reminder created by Rathod AI")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return if (isIntentAvailable(calIntent)) {
            CommandExecutionResult.Executed(
                "Opening Calendar to schedule reminder: '${cmd.title}'.",
                calIntent
            )
        } else {
            // Save as note fallback
            notesRepository.addNote("Reminder: ${cmd.title}", "Time: ${cmd.timeDesc}")
            CommandExecutionResult.Executed(
                "Calendar app not found. Saved reminder as a note: '${cmd.title}'."
            )
        }
    }

    private fun handleOpenMaps(cmd: AppCommand.OpenMaps): CommandExecutionResult {
        val encoded = URLEncoder.encode(cmd.query, "UTF-8")
        val mapUri = Uri.parse("geo:0,0?q=$encoded")
        val mapIntent = Intent(Intent.ACTION_VIEW, mapUri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        return if (isIntentAvailable(mapIntent)) {
            CommandExecutionResult.Executed("Searching '${cmd.query}' on Maps...", mapIntent)
        } else {
            CommandExecutionResult.Failed("No Maps application found.")
        }
    }

    private fun isIntentAvailable(intent: Intent): Boolean {
        val list = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        return list.isNotEmpty()
    }
}

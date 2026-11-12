package com.example.commands

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import com.example.data.NotesRepository
import java.net.URLEncoder
import java.util.Calendar

private const val TAG = "CommandRouter"

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
            is AppCommand.GeneralQuestion -> CommandExecutionResult.Executed("Answering question")
            is AppCommand.Unsupported -> CommandExecutionResult.Failed(
                "Command '${command.rawAction}' is not supported in this stage: ${command.reason}"
            )
        }
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

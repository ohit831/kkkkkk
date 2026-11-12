package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.commands.AppCommand
import com.example.commands.CommandExecutionResult
import com.example.commands.CommandRouter
import com.example.data.NotesRepository
import com.example.model.AssistantLanguage
import com.example.voice.SpeechRecognitionManager
import com.example.voice.SpeechState
import com.example.voice.TextToSpeechManager
import com.example.voice.TtsState
import com.example.wakeword.WakeWordManagerImpl
import com.example.wakeword.WakeWordService
import com.example.wakeword.WakeWordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var shadowApp: ShadowApplication
    private lateinit var notesRepository: NotesRepository
    private lateinit var commandRouter: CommandRouter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowApp = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        notesRepository = NotesRepository(context)
        commandRouter = CommandRouter(context, notesRepository)
    }

    // TEST 1: Microphone Permission
    @Test
    fun test1_microphonePermissionHandling() {
        // Initially no runtime permission
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)
        val hasMicInitial = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        assertFalse("Microphone permission should initially be denied", hasMicInitial)

        // Grant permission
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        val hasMicGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        assertTrue("Microphone permission should be granted", hasMicGranted)
    }

    // TEST 2: Kannada Speech Recognition configuration
    @Test
    fun test2_kannadaSpeechRecognitionConfig() {
        val kannada = AssistantLanguage.KANNADA
        assertEquals("kn-IN", kannada.code)
        assertEquals("ಕನ್ನಡ", kannada.displayName)
        assertEquals("KN", kannada.nativeLabel)
        assertEquals("kn", kannada.locale.language)
        assertEquals("IN", kannada.locale.country)
        assertTrue(kannada.voiceGreeting.contains("ನಮಸ್ಕಾರ ರೋಹಿತ್"))
    }

    // TEST 3: Hindi Speech Recognition configuration
    @Test
    fun test3_hindiSpeechRecognitionConfig() {
        val hindi = AssistantLanguage.HINDI
        assertEquals("hi-IN", hindi.code)
        assertEquals("हिन्दी", hindi.displayName)
        assertEquals("HI", hindi.nativeLabel)
        assertEquals("hi", hindi.locale.language)
        assertEquals("IN", hindi.locale.country)
        assertTrue(hindi.voiceGreeting.contains("नमस्ते रोहित"))
    }

    // TEST 4: English Speech Recognition configuration
    @Test
    fun test4_englishSpeechRecognitionConfig() {
        val english = AssistantLanguage.ENGLISH
        assertEquals("en-IN", english.code)
        assertEquals("English", english.displayName)
        assertEquals("EN", english.nativeLabel)
        assertEquals("en", english.locale.language)
        assertTrue(english.voiceGreeting.contains("Hello Rohit"))

        val speechMgr = SpeechRecognitionManager(context)
        val intent = speechMgr.createSystemSpeechIntent(english)
        assertEquals("en-IN", intent.getStringExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE))
        assertTrue(intent.getBooleanExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, false))
    }

    // TEST 5: Gemini Response & Command Router
    @Test
    fun test5_geminiResponseAndCommandExecution() {
        // Test SET_ALARM command
        val alarmCmd = AppCommand.SetAlarm(hour = 7, minute = 30, label = "Morning Workout")
        val alarmResult = commandRouter.executeCommand(alarmCmd)
        assertTrue(alarmResult is CommandExecutionResult.Executed)
        val executedAlarm = alarmResult as CommandExecutionResult.Executed
        assertTrue(executedAlarm.feedback.contains("07:30"))
        assertNotNull(executedAlarm.pendingIntent)

        // Test CREATE_NOTE command
        val noteCmd = AppCommand.CreateNote(title = "AI Studio Task", content = "Build Rathod AI voice engine")
        val noteResult = commandRouter.executeCommand(noteCmd)
        assertTrue(noteResult is CommandExecutionResult.Executed)
        val notes = notesRepository.getAllNotes()
        assertTrue(notes.any { it.title == "AI Studio Task" })

        // Test Sensitive Command CALL_CONTACT (requires confirmation first!)
        val callCmd = AppCommand.CallContact(contactName = "Rohit", phoneNumber = "+919876543210")
        val unconfirmedResult = commandRouter.executeCommand(callCmd, userConfirmed = false)
        assertTrue("CALL_CONTACT must require confirmation", unconfirmedResult is CommandExecutionResult.ConfirmationRequired)

        // After user confirmation
        val confirmedResult = commandRouter.executeCommand(callCmd, userConfirmed = true)
        assertTrue(confirmedResult is CommandExecutionResult.Executed)

        // Test DEVICE_SETTING commands (Wi-Fi, Bluetooth)
        val wifiCmd = AppCommand.DeviceSetting(com.example.commands.SettingType.WIFI)
        val wifiResult = commandRouter.executeCommand(wifiCmd)
        assertTrue(wifiResult is CommandExecutionResult.Executed)
        assertNotNull((wifiResult as CommandExecutionResult.Executed).pendingIntent)

        // Test Text Parsing from raw Gemini responses
        val parsedApp = commandRouter.parseCommandFromText("Open YouTube")
        assertTrue(parsedApp is AppCommand.OpenApp)
        assertEquals("YouTube", (parsedApp as AppCommand.OpenApp).appName)

        val parsedTorch = commandRouter.parseCommandFromText("Turn on flashlight")
        assertTrue(parsedTorch is AppCommand.DeviceSetting)
        assertEquals(com.example.commands.SettingType.FLASHLIGHT, (parsedTorch as AppCommand.DeviceSetting).settingType)
        assertEquals(true, (parsedTorch as AppCommand.DeviceSetting).enable)

        val parsedAlarm = commandRouter.parseCommandFromText("Set alarm for 06:45 am")
        assertTrue(parsedAlarm is AppCommand.SetAlarm)
        assertEquals(6, (parsedAlarm as AppCommand.SetAlarm).hour)
        assertEquals(45, (parsedAlarm as AppCommand.SetAlarm).minute)
    }

    // TEST 6: Female Voice Output settings
    @Test
    fun test6_femaleVoiceOutputSettings() {
        val ttsManager = TextToSpeechManager(context)
        ttsManager.setFemaleVoicePreference(true)
        assertTrue("Female voice profile should be active", ttsManager.isFemaleVoiceActive.value)
        assertEquals(1.15f, ttsManager.pitch.value, 0.01f)

        ttsManager.setPitch(1.3f)
        assertEquals(1.3f, ttsManager.pitch.value, 0.01f)

        ttsManager.setSpeechRate(1.1f)
        assertEquals(1.1f, ttsManager.speechRate.value, 0.01f)
    }

    // TEST 7: Stop Speaking logic
    @Test
    fun test7_stopSpeakingLogic() {
        val ttsManager = TextToSpeechManager(context)
        ttsManager.stop()
        assertEquals(TtsState.Ready, ttsManager.ttsState.value)
    }

    // TEST 8: Network Failure Handling
    @Test
    fun test8_networkFailureAndErrorReporting() {
        val errorState = SpeechState.Error("Network error during speech recognition", 2)
        assertEquals("Network error during speech recognition", errorState.message)
        assertEquals(2, errorState.errorCode)

        // Unsupported action error report
        val unsupported = AppCommand.Unsupported("FLY_DRONE", "Drone hardware not connected")
        val result = commandRouter.executeCommand(unsupported)
        assertTrue(result is CommandExecutionResult.Failed)
        assertTrue((result as CommandExecutionResult.Failed).reason.contains("FLY_DRONE"))
    }

    // TEST 9: Wake-Word Service Permission and Notification Lifecycle
    @Test
    fun test9_wakeWordServicePermissionAndNotificationLifecycle() {
        val wakeWordManager = WakeWordManagerImpl(context)

        // When permission is denied
        shadowApp.denyPermissions(Manifest.permission.RECORD_AUDIO)
        wakeWordManager.startListening(context)
        assertTrue(
            "Should require RECORD_AUDIO",
            wakeWordManager.status.value is WakeWordStatus.PermissionRequired
        )

        // When permission is granted
        shadowApp.grantPermissions(Manifest.permission.RECORD_AUDIO)
        shadowApp.grantPermissions(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)

        val serviceController = Robolectric.buildService(WakeWordService::class.java)
        val service = serviceController.create().get()

        val startIntent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_START_LISTENING
        }
        service.onStartCommand(startIntent, 0, 1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull("NotificationManager should be present", notificationManager)

        // Stop command
        val stopIntent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_STOP_LISTENING
        }
        service.onStartCommand(stopIntent, 0, 2)
        serviceController.destroy()
    }
}

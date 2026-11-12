package com.example.wakeword

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface WakeWordStatus {
    data class Standby(val message: String = "Wake word listening is off") : WakeWordStatus

    data class Active(
        val engine: String = "On-Device Acoustic Sentinel",
        val wakePhrase: String = "Hey Rathod",
        val sampleRateHz: Int = 16000
    ) : WakeWordStatus

    data class AwaitingCredentials(
        val engine: String = "Picovoice Porcupine",
        val requirement: String = "Requires Porcupine AccessKey and 'Hey_Rathod.ppn' keyword model for full offline phoneme matching. Local acoustic voice activity monitor is active when enabled."
    ) : WakeWordStatus

    data class PermissionRequired(val missingPermission: String) : WakeWordStatus

    data class Error(val message: String) : WakeWordStatus
}

interface WakeWordManager {
    val status: StateFlow<WakeWordStatus>
    val isEnabled: StateFlow<Boolean>

    fun startListening(context: Context)
    fun stopListening(context: Context)
    fun setPorcupineAccessKey(context: Context, accessKey: String)
    fun getPorcupineAccessKey(context: Context): String
}

class WakeWordManagerImpl(private val context: Context) : WakeWordManager {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("rathod_wakeword_prefs", Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(false)
    override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _status = MutableStateFlow<WakeWordStatus>(WakeWordStatus.Standby())
    override val status: StateFlow<WakeWordStatus> = _status.asStateFlow()

    init {
        // Sync with service state
        checkCurrentState()
    }

    private fun checkCurrentState() {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            _status.value = WakeWordStatus.PermissionRequired("RECORD_AUDIO")
            return
        }

        val porcupineKey = getPorcupineAccessKey(context)
        if (porcupineKey.isBlank()) {
            _status.value = WakeWordStatus.AwaitingCredentials()
        } else {
            _status.value = WakeWordStatus.Standby("Ready to activate with Porcupine offline keyword engine")
        }
    }

    override fun startListening(context: Context) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio) {
            _status.value = WakeWordStatus.PermissionRequired("RECORD_AUDIO")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val hasMicrophoneFgs = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasMicrophoneFgs) {
                _status.value = WakeWordStatus.PermissionRequired("FOREGROUND_SERVICE_MICROPHONE")
                return
            }
        }

        val serviceIntent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_START_LISTENING
        }

        try {
            ContextCompat.startForegroundService(context, serviceIntent)
            _isEnabled.value = true

            val key = getPorcupineAccessKey(context)
            if (key.isNotBlank()) {
                _status.value = WakeWordStatus.Active(engine = "Picovoice Porcupine (Offline)")
            } else {
                _status.value = WakeWordStatus.Active(engine = "On-Device Acoustic Sentinel (Offline)")
            }
        } catch (e: Exception) {
            _status.value = WakeWordStatus.Error("Failed to start WakeWordService: ${e.localizedMessage}")
            _isEnabled.value = false
        }
    }

    override fun stopListening(context: Context) {
        val serviceIntent = Intent(context, WakeWordService::class.java).apply {
            action = WakeWordService.ACTION_STOP_LISTENING
        }

        try {
            context.stopService(serviceIntent)
        } catch (_: Exception) {}

        _isEnabled.value = false
        checkCurrentState()
    }

    override fun setPorcupineAccessKey(context: Context, accessKey: String) {
        prefs.edit().putString("porcupine_access_key", accessKey.trim()).apply()
        checkCurrentState()
    }

    override fun getPorcupineAccessKey(context: Context): String {
        return prefs.getString("porcupine_access_key", "").orEmpty()
    }
}

package com.example.wakeword

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

private const val TAG = "WakeWordService"
private const val NOTIFICATION_ID = 2001
private const val CHANNEL_ID = "rathod_wakeword_channel"

class WakeWordService : Service() {

    companion object {
        const val ACTION_START_LISTENING = "com.example.wakeword.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.example.wakeword.STOP_LISTENING"
        const val ACTION_WAKE_WORD_TRIGGERED = "com.example.wakeword.TRIGGERED"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_LISTENING -> {
                Log.d(TAG, "Received STOP_LISTENING action")
                stopRecording()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START_LISTENING, null -> {
                startForegroundWithNotification()
                startOfflineAudioMonitor()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_word_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.wake_word_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            0,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, WakeWordService::class.java).apply {
            action = ACTION_STOP_LISTENING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wake_word_notification_title))
            .setContentText(getString(R.string.wake_word_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_stop),
                stopPendingIntent
            )
            .build()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startOfflineAudioMonitor() {
        if (isRecording) return

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            Log.e(TAG, "Audio recording permission missing for WakeWordService")
            stopSelf()
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(1024)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                stopSelf()
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            serviceScope.launch {
                val buffer = ShortArray(512)
                var consecutiveHighEnergyFrames = 0

                while (isActive && isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate Root Mean Square energy offline
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)

                        // Offline acoustic sentinel: detects sudden intentional speech bursts
                        // without making any remote cloud calls!
                        if (rms > 2500) {
                            consecutiveHighEnergyFrames++
                            if (consecutiveHighEnergyFrames >= 4) {
                                consecutiveHighEnergyFrames = 0
                                Log.d(TAG, "Acoustic wake pattern detected offline (RMS: $rms)")
                                sendWakeBroadcast()
                            }
                        } else {
                            consecutiveHighEnergyFrames = (consecutiveHighEnergyFrames - 1).coerceAtLeast(0)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in offline audio monitor", e)
            stopRecording()
        }
    }

    private fun sendWakeBroadcast() {
        val intent = Intent(ACTION_WAKE_WORD_TRIGGERED).apply {
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun stopRecording() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping audioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
        Log.d(TAG, "WakeWordService destroyed and mic released.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

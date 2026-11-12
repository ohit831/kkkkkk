package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.firebase.configureAppCheck
import com.example.ui.HomeScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.wakeword.WakeWordService
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WakeWordService.ACTION_WAKE_WORD_TRIGGERED) {
                Log.d(TAG, "Wake word trigger received in MainActivity!")
                Toast.makeText(this@MainActivity, "Activation phrase detected: Hey Rathod!", Toast.LENGTH_SHORT).show()
                viewModel.startListeningWithPermission()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        // Register wake word broadcast receiver
        val filter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_TRIGGERED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                wakeWordReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            registerReceiver(wakeWordReceiver, filter)
        }

        // Listen for external app / system action intents (Alarm, SMS, YouTube, Maps, etc.)
        lifecycleScope.launch {
            viewModel.pendingIntentEvent.collect { actionIntent ->
                try {
                    startActivity(actionIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch intent: ${e.localizedMessage}", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Could not launch requested action: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        setContent {
            MyApplicationTheme {
                HomeScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (_: Exception) {}
    }
}

package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AssistantLanguage
import com.example.ui.components.GlowingOrb
import com.example.ui.components.NotesDialog
import com.example.ui.components.OrbState
import com.example.ui.components.TranscriptCard
import com.example.ui.components.VoiceSettingsDialog
import com.example.ui.components.WaveformVisualizer
import com.example.ui.components.WakeWordDialog
import com.example.ui.theme.CyanVioletGradient
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FuturisticBorder
import com.example.ui.theme.FuturisticDarkBg
import com.example.ui.theme.FuturisticSurfaceCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.voice.SpeechState
import com.example.wakeword.WakeWordStatus
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val activeLanguage by viewModel.activeLanguage.collectAsState()
    val orbState by viewModel.orbState.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val liveSpeechText by viewModel.liveSpeechText.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val speechState by viewModel.speechManager.speechState.collectAsState()
    val rmsLevel by viewModel.speechManager.rmsLevel.collectAsState()
    val femaleVoiceActive by viewModel.ttsManager.isFemaleVoiceActive.collectAsState()
    val pitch by viewModel.ttsManager.pitch.collectAsState()
    val speechRate by viewModel.ttsManager.speechRate.collectAsState()
    val wakeWordStatus by viewModel.wakeWordManager.status.collectAsState()
    val wakeWordEnabled by viewModel.wakeWordManager.isEnabled.collectAsState()
    val notesList by viewModel.notesList.collectAsState()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsState()

    var showVoiceSettings by remember { mutableStateOf(false) }
    var showWakeWordDialog by remember { mutableStateOf(false) }
    var showNotesDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    // System speech recognition activity launcher for seamless fallback
    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim().orEmpty()
            if (spokenText.isNotEmpty()) {
                viewModel.handleRecognizedText(spokenText)
            }
        }
    }

    // Permission launcher for microphone
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (recordAudioGranted) {
            viewModel.startListeningWithPermission()
        } else {
            Toast.makeText(context, "Microphone permission is required to listen", Toast.LENGTH_SHORT).show()
        }
    }

    fun checkAndStartListening(forceSystemDialog: Boolean = false) {
        val hasAudio = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasAudio) {
            if (forceSystemDialog || !viewModel.speechManager.isAvailable()) {
                val intent = viewModel.speechManager.createSystemSpeechIntent(activeLanguage)
                try {
                    systemSpeechLauncher.launch(intent)
                } catch (e: Exception) {
                    viewModel.startListeningWithPermission()
                }
            } else {
                if (speechState is SpeechState.Listening || speechState is SpeechState.Preparing) {
                    viewModel.stopListening()
                } else {
                    viewModel.startListeningWithPermission()
                }
            }
        } else {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(perms.toTypedArray())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FuturisticDarkBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(FuturisticDarkBg),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TOP HEADER & NAVIGATION
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Brand Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(Color(0xFF14102B), RoundedCornerShape(20.dp))
                            .border(1.dp, FuturisticBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .shadow(elevation = 4.dp, shape = CircleShape, ambientColor = NeonCyan, spotColor = NeonCyan)
                                .background(NeonCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RATHOD AI",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 1.5.sp
                        )
                    }

                    // Action Icons: Notes, Wake Word, Voice Settings
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Notes Button
                        IconButton(
                            onClick = { showNotesDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF161132), CircleShape)
                                .testTag("open_notes_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.NoteAlt,
                                contentDescription = "Notes",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Wake Word Button
                        IconButton(
                            onClick = { showWakeWordDialog = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (wakeWordEnabled) ElectricViolet.copy(alpha = 0.25f) else Color(0xFF161132),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (wakeWordEnabled) ElectricViolet else FuturisticBorder,
                                    CircleShape
                                )
                                .testTag("open_wake_word_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Hearing,
                                contentDescription = "Wake Word Settings",
                                tint = if (wakeWordEnabled) ElectricViolet else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Voice Settings Button
                        IconButton(
                            onClick = { showVoiceSettings = true },
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF161132), CircleShape)
                                .testTag("open_voice_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = "Female Voice Settings",
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // 2. LANGUAGE SELECTOR PILLS
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AssistantLanguage.entries.forEach { lang ->
                        val isSelected = activeLanguage == lang
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) CyanVioletGradient else Brush.linearGradient(
                                        listOf(Color(0xFF171233), Color(0xFF120E29))
                                    )
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) NeonCyan else FuturisticBorder,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable { viewModel.setLanguage(lang) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("language_chip_${lang.code}")
                        ) {
                            Text(
                                text = lang.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF00141D) else TextSecondary
                            )
                        }
                    }
                }

                // 3. HERO GREETING & GLOWING ORB
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hello Rohit",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Glowing AI Orb with dynamic RMS & breathing animation
                GlowingOrb(
                    orbState = orbState,
                    rmsLevel = rmsLevel,
                    size = 190.dp,
                    modifier = Modifier.padding(vertical = 4.dp).testTag("glowing_ai_orb")
                )

                // Status banner
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when (orbState) {
                        OrbState.LISTENING -> NeonCyan
                        OrbState.THINKING -> ElectricViolet
                        OrbState.SPEAKING -> NeonCyan
                        OrbState.ERROR -> Color(0xFFFF5277)
                        OrbState.IDLE -> TextSecondary
                    }
                )

                // 4. AUDIO SPECTRUM WAVEFORM
                WaveformVisualizer(
                    isActive = orbState == OrbState.LISTENING || orbState == OrbState.SPEAKING,
                    rmsLevel = rmsLevel,
                    height = 36.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )

                // Real-time voice input live preview
                val isCurrentlyListening = speechState is SpeechState.Listening || speechState is SpeechState.Preparing
                AnimatedVisibility(
                    visible = isCurrentlyListening || liveSpeechText.isNotBlank(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF140E31)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isCurrentlyListening) StatusActive else NeonCyan,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (liveSpeechText.isNotBlank()) "\"$liveSpeechText\"" else "Listening in ${activeLanguage.displayName}... Speak now",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (liveSpeechText.isNotBlank()) NeonCyan else TextSecondary,
                                fontWeight = if (liveSpeechText.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 5. PRIMARY CONTROLS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stop Speaking button
                    IconButton(
                        onClick = { viewModel.stopSpeaking() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF1B1538), CircleShape)
                            .border(1.dp, FuturisticBorder, CircleShape)
                            .testTag("stop_speaking_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "Stop Speaking",
                            tint = Color(0xFFFF5277),
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Glowing Microphone FAB
                    val isListening = speechState is SpeechState.Listening || speechState is SpeechState.Preparing
                    FloatingActionButton(
                        onClick = { checkAndStartListening() },
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(
                                elevation = if (isListening) 16.dp else 8.dp,
                                shape = CircleShape,
                                ambientColor = NeonCyan,
                                spotColor = ElectricViolet
                            )
                            .testTag("microphone_fab_button"),
                        shape = CircleShape,
                        containerColor = if (isListening) ElectricViolet else NeonCyan,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                            tint = Color(0xFF001620),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Replay button
                    IconButton(
                        onClick = { viewModel.replayLastAssistantMessage() },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color(0xFF1B1538), CircleShape)
                            .border(1.dp, FuturisticBorder, CircleShape)
                            .testTag("replay_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay,
                            contentDescription = "Replay Response",
                            tint = NeonCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 6. QUICK COMMANDS SCROLL ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quickCommands = listOf(
                        "🔦 Toggle Flashlight" to "Turn on flashlight",
                        "📶 Wi-Fi Settings" to "Open Wi-Fi settings",
                        "⏰ Alarm 7:00 AM" to "Set alarm for 7:00 AM",
                        "📝 Take a note" to "Take a note: Prepare project report for tomorrow",
                        "▶️ Open YouTube" to "Open YouTube",
                        "💬 Draft message" to "Draft message to Rohit: Hey, meeting is at 4 PM",
                        "🗺️ Open Maps" to "Open Maps for nearest coffee shop",
                        "ಕನ್ನಡ Kannada Fact" to "Tell me a fascinating space fact in Kannada",
                        "हिन्दी Hindi Fact" to "Tell me an interesting science fact in Hindi"
                    )

                    quickCommands.forEach { (label, commandText) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF15102F))
                                .border(1.dp, FuturisticBorder, RoundedCornerShape(12.dp))
                                .clickable { viewModel.executeQuickCommand(commandText) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("quick_command_${label.take(6)}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // 7. CONVERSATION TRANSCRIPT LIST
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(transcript, key = { it.id }) { msg ->
                        TranscriptCard(
                            message = msg,
                            onReplay = { viewModel.replayLastAssistantMessage() }
                        )
                    }
                }

                // 8. TEXT INPUT BAR (as shown in mockups: ask anything...)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualInputText,
                        onValueChange = { manualInputText = it },
                        placeholder = { Text("Ask Rathod or type command...", color = Color(0xFF6B658A)) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_command_input"),
                        trailingIcon = {
                            IconButton(
                                onClick = { checkAndStartListening(forceSystemDialog = false) },
                                modifier = Modifier.testTag("input_mic_button")
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyListening) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Voice Input",
                                    tint = if (isCurrentlyListening) StatusActive else NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = FuturisticBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedContainerColor = Color(0xFF130F2C),
                            unfocusedContainerColor = Color(0xFF130F2C)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (manualInputText.isNotBlank()) {
                                val text = manualInputText
                                manualInputText = ""
                                viewModel.handleRecognizedText(text)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(NeonCyan, CircleShape)
                            .testTag("send_manual_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = Color(0xFF001822),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Sensitive Actions
    pendingConfirmation?.let { conf ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPendingAction() },
            title = {
                Text(
                    text = "Confirm Operation",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = conf.prompt,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    modifier = Modifier.testTag("confirm_action_dialog_button")
                ) {
                    Text("Proceed", color = Color(0xFF001A24), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.cancelPendingAction() },
                    modifier = Modifier.testTag("cancel_action_dialog_button")
                ) {
                    Text("Cancel", color = Color(0xFFFF5277))
                }
            },
            containerColor = FuturisticSurfaceCard
        )
    }

    // Voice Settings Dialog
    VoiceSettingsDialog(
        isOpen = showVoiceSettings,
        isFemaleVoice = femaleVoiceActive,
        pitch = pitch,
        speechRate = speechRate,
        activeLanguage = activeLanguage,
        onFemaleVoiceChange = { viewModel.ttsManager.setFemaleVoicePreference(it) },
        onPitchChange = { viewModel.ttsManager.setPitch(it) },
        onSpeechRateChange = { viewModel.ttsManager.setSpeechRate(it) },
        onTestVoice = { viewModel.testVoiceOutput() },
        onDismiss = { showVoiceSettings = false }
    )

    // Wake Word Dialog
    WakeWordDialog(
        isOpen = showWakeWordDialog,
        status = wakeWordStatus,
        isEnabled = wakeWordEnabled,
        currentAccessKey = viewModel.wakeWordManager.getPorcupineAccessKey(context),
        onToggleListening = { enable ->
            if (enable) {
                viewModel.wakeWordManager.startListening(context)
            } else {
                viewModel.wakeWordManager.stopListening(context)
            }
        },
        onSaveAccessKey = { key ->
            viewModel.wakeWordManager.setPorcupineAccessKey(context, key)
            Toast.makeText(context, "Porcupine configuration saved", Toast.LENGTH_SHORT).show()
        },
        onDismiss = { showWakeWordDialog = false }
    )

    // Notes Dialog
    NotesDialog(
        isOpen = showNotesDialog,
        notes = notesList,
        onDeleteNote = { viewModel.deleteNote(it) },
        onDismiss = { showNotesDialog = false }
    )
}

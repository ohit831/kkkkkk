package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.model.AssistantLanguage
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FuturisticBorder
import com.example.ui.theme.FuturisticSurfaceCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VoiceSettingsDialog(
    isOpen: Boolean,
    isFemaleVoice: Boolean,
    pitch: Float,
    speechRate: Float,
    activeLanguage: AssistantLanguage,
    onFemaleVoiceChange: (Boolean) -> Unit,
    onPitchChange: (Float) -> Unit,
    onSpeechRateChange: (Float) -> Unit,
    onTestVoice: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, FuturisticBorder, RoundedCornerShape(24.dp))
                .testTag("voice_settings_dialog"),
            colors = CardDefaults.cardColors(containerColor = FuturisticSurfaceCard)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = "Voice Settings",
                            tint = NeonCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Female Voice Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("close_voice_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Preferred Female Voice toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F0B20), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Female Voice Profile",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Selects female TTS voice with feminine frequency modulation",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isFemaleVoice,
                        onCheckedChange = onFemaleVoiceChange,
                        modifier = Modifier.testTag("female_voice_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = NeonCyan,
                            uncheckedTrackColor = Color(0xFF261D45)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pitch slider
                Text(
                    text = "Vocal Pitch (${String.format("%.2f", pitch)}x)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Slider(
                    value = pitch,
                    onValueChange = onPitchChange,
                    valueRange = 0.8f..1.5f,
                    modifier = Modifier.testTag("voice_pitch_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = Color(0xFF2A2050)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Speed slider
                Text(
                    text = "Speech Rate (${String.format("%.2f", speechRate)}x)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Slider(
                    value = speechRate,
                    onValueChange = onSpeechRateChange,
                    valueRange = 0.8f..1.5f,
                    modifier = Modifier.testTag("voice_speed_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricViolet,
                        activeTrackColor = ElectricViolet,
                        inactiveTrackColor = Color(0xFF2A2050)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Test voice button
                Button(
                    onClick = onTestVoice,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("test_voice_output_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Test Voice",
                        tint = Color(0xFF001B22)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Test Voice (${activeLanguage.displayName})",
                        color = Color(0xFF001B22),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

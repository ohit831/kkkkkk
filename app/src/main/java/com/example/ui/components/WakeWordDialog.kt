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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FuturisticBorder
import com.example.ui.theme.FuturisticSurfaceCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.wakeword.WakeWordStatus

@Composable
fun WakeWordDialog(
    isOpen: Boolean,
    status: WakeWordStatus,
    isEnabled: Boolean,
    currentAccessKey: String,
    onToggleListening: (Boolean) -> Unit,
    onSaveAccessKey: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var keyInput by remember(currentAccessKey) { mutableStateOf(currentAccessKey) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, FuturisticBorder, RoundedCornerShape(24.dp))
                .testTag("wake_word_dialog"),
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
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Wake Word",
                            tint = ElectricViolet,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Wake Word: Hey Rathod",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("close_wake_word_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status chip
                val (statusColor, statusTitle, statusSub) = when (status) {
                    is WakeWordStatus.Active -> Triple(
                        StatusActive,
                        "ACTIVE LISTENING (OFFLINE)",
                        "Foreground service active with microphone notification."
                    )
                    is WakeWordStatus.Standby -> Triple(
                        Color(0xFF8E89AA),
                        "STANDBY (OFF)",
                        status.message
                    )
                    is WakeWordStatus.AwaitingCredentials -> Triple(
                        StatusWarning,
                        "ENGINE PREPARATION",
                        status.requirement
                    )
                    is WakeWordStatus.PermissionRequired -> Triple(
                        Color(0xFFFF3366),
                        "PERMISSION REQUIRED",
                        "Missing ${status.missingPermission} permission."
                    )
                    is WakeWordStatus.Error -> Triple(
                        Color(0xFFFF3366),
                        "ERROR",
                        status.message
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D091F), RoundedCornerShape(12.dp))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                        Text(
                            text = statusSub,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Activation Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF130F2A), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Wake Word Listener",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Starts compliant microphone foreground service",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggleListening,
                        modifier = Modifier.testTag("wake_word_toggle_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = ElectricViolet,
                            uncheckedTrackColor = Color(0xFF261D45)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Optional Picovoice Porcupine AccessKey field
                Text(
                    text = "Picovoice Porcupine Key (Optional)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("Paste Porcupine AccessKey...", color = Color(0xFF5B5478)) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Key", tint = ElectricViolet)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("porcupine_key_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricViolet,
                        unfocusedBorderColor = FuturisticBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = Color(0xFF0F0B22),
                        unfocusedContainerColor = Color(0xFF0F0B22)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { onSaveAccessKey(keyInput) },
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("save_porcupine_key_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E1C59))
                ) {
                    Text("Save Key & Update Engine", color = Color.White, fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Privacy guarantee note
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0718), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Info",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zero Cloud Streaming: Audio is processed 100% locally on-device. Microphones are never sent to external servers for wake-word detection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF8F88AE)
                    )
                }
            }
        }
    }
}

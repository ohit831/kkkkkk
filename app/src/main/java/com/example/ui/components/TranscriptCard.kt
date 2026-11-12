package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FuturisticBorder
import com.example.ui.theme.FuturisticSurfaceCard
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val languageLabel: String = "EN",
    val commandAction: String? = null,
    val commandFeedback: String? = null,
    val isError: Boolean = false,
    val timestamp: String = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date())
)

@Composable
fun TranscriptCard(
    message: ChatMessage,
    onReplay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                1.dp,
                if (message.isUser) NeonCyan.copy(alpha = 0.3f) else FuturisticBorder,
                RoundedCornerShape(18.dp)
            )
            .testTag(if (message.isUser) "user_message_card" else "assistant_message_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isUser) Color(0xFF0F1527) else FuturisticSurfaceCard
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (message.isUser) NeonCyan.copy(alpha = 0.2f) else ElectricViolet.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (message.isUser) Icons.Default.Mic else Icons.Default.SmartToy,
                            contentDescription = if (message.isUser) "User" else "Assistant",
                            tint = if (message.isUser) NeonCyan else ElectricViolet,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (message.isUser) "Rohit" else "Rathod AI",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (message.isUser) NeonCyan else ElectricViolet
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF20183B), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = message.languageLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF6B658A)
                    )
                    if (!message.isUser) {
                        IconButton(
                            onClick = onReplay,
                            modifier = Modifier.size(30.dp).testTag("replay_message_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Replay",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Body
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (message.isError) StatusError else TextPrimary,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            // Command feedback banner if any
            if (!message.commandFeedback.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (message.isError) StatusError.copy(alpha = 0.15f) else StatusActive.copy(alpha = 0.12f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (message.isError) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        contentDescription = "Status",
                        tint = if (message.isError) StatusError else StatusActive,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${message.commandAction?.let { "[$it] " }.orEmpty()}${message.commandFeedback}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isError) StatusError else StatusActive,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

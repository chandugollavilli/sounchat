package com.soundchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.data.models.ChatMessageEntity
import com.soundchat.app.data.models.MessageDeliveryState
import com.soundchat.app.data.models.TransmissionStatus
import com.soundchat.app.discovery.ActiveDeviceSession
import com.soundchat.app.protocol.PacketHeader
import com.soundchat.app.ui.theme.AmberWarning
import com.soundchat.app.ui.theme.CoralError
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SurfaceCardBorder
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.SurfaceElevatedGrey
import com.soundchat.app.ui.theme.TextPrimaryWhite
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import com.soundchat.app.ui.theme.UltrasonicEmerald
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    localDeviceHex: String,
    audioConfig: AudioConfiguration,
    transmissionStatus: TransmissionStatus,
    isListening: Boolean,
    activeSession: ActiveDeviceSession?,
    messages: List<ChatMessageEntity>,
    onToggleListening: () -> Unit,
    onSendMessage: (String) -> Unit,
    onRetryMessage: (ChatMessageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Acoustic Link Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("chat_header_card"),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (activeSession != null) {
                            "SESSION: ${activeSession.peerDeviceName} (${activeSession.peerHexId})"
                        } else {
                            "BROADCAST ACOUSTIC CHANNEL (0xFFFF)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (activeSession != null) UltrasonicEmerald else UltrasonicCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Status: ${transmissionStatus.displayTitle} · ${audioConfig.freqBit0Hz.toInt()}/${audioConfig.freqBit1Hz.toInt()} Hz",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                }

                Button(
                    onClick = onToggleListening,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isListening) UltrasonicEmerald.copy(alpha = 0.2f) else UltrasonicCyan,
                        contentColor = if (isListening) UltrasonicEmerald else ObsidianBlack
                    ),
                    modifier = Modifier.testTag("chat_receive_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Hearing,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isListening) "RX ON" else "Enable RX",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Quick Test Payload Chips (Tests 1, 2, 3 from Section 11)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("HI", "HELLO WORLD", "SC-2026 #42!").forEach { preset ->
                AssistChip(
                    onClick = { onSendMessage(preset) },
                    label = {
                        Text(
                            text = "TX \"$preset\"",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = SurfaceDarkGrey,
                        labelColor = UltrasonicCyan
                    ),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.testTag("quick_chip_${preset.lowercase().replace(" ", "_")}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message History List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = UltrasonicCyan.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Acoustic Messages Yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryWhite
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enable RX Mode on Phone B, then type \"HI\" below and press SEND on Phone A. The message travels through the air as 18.75–19.35 kHz ultrasonic BFSK audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryMuted
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("chat_messages_list"),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatMessageBubble(
                            message = msg,
                            onRetry = { onRetryMessage(msg) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Message Composer Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = {
                    if (it.toByteArray(Charsets.UTF_8).size <= PacketHeader.MAX_PAYLOAD_BYTES) {
                        inputText = it
                    }
                },
                placeholder = {
                    Text(
                        text = "Type UTF-8 message (e.g. HI)…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UltrasonicCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedContainerColor = SurfaceDarkGrey,
                    unfocusedContainerColor = SurfaceDarkGrey,
                    focusedTextColor = TextPrimaryWhite,
                    unfocusedTextColor = TextPrimaryWhite
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_message_input")
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                enabled = inputText.isNotBlank() &&
                    transmissionStatus != TransmissionStatus.TRANSMITTING &&
                    transmissionStatus != TransmissionStatus.RETRYING,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UltrasonicEmerald,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Acoustic Message",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SEND",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageEntity,
    onRetry: () -> Unit
) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isOutgoing) SurfaceElevatedGrey else SurfaceDarkGrey
    val borderColor = when {
        message.deliveryState.contains("FAILED") -> CoralError.copy(alpha = 0.6f)
        message.deliveryState.contains("DELIVERED") || message.deliveryState.contains("RECEIVED") ->
            UltrasonicEmerald.copy(alpha = 0.5f)
        else -> UltrasonicCyan.copy(alpha = 0.4f)
    }

    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestampEpochMs))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (message.isOutgoing) 16.dp else 4.dp,
                bottomEnd = if (message.isOutgoing) 4.dp else 16.dp
            ),
            color = bubbleColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.widthIn(min = 160.dp, max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (message.isOutgoing) {
                            "TX -> ${message.receiverHex} (#${message.packetMessageId})"
                        } else {
                            "RX <- ${message.senderHex} (#${message.packetMessageId})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isOutgoing) UltrasonicCyan else UltrasonicEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                }

                if (message.isLoopbackTest) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "[LOCAL ACOUSTIC LOOPBACK TEST]",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberWarning
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimaryWhite,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(ObsidianBlack)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = message.deliveryState,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                message.deliveryState.contains("FAILED") -> CoralError
                                message.deliveryState.contains("DELIVERED") ||
                                    message.deliveryState.contains("RECEIVED") -> UltrasonicEmerald
                                else -> AmberWarning
                            },
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CRC-16: 0x${message.crcHex} · ${message.carrierFreqHz} Hz · ${message.durationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                    }

                    if (message.isOutgoing &&
                        (message.deliveryState == MessageDeliveryState.FAILED.label ||
                            message.deliveryState == MessageDeliveryState.SENT_UNACKNOWLEDGED.label)
                    ) {
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("retry_message_${message.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry acoustic transmission",
                                tint = AmberWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

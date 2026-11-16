package com.soundchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HearingDisabled
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.data.models.TransmissionStatus
import com.soundchat.app.discovery.ActiveDeviceSession
import com.soundchat.app.service.DiagnosticTelemetryState
import com.soundchat.app.ui.SoundChatDestination
import com.soundchat.app.ui.components.CapabilitySummaryCard
import com.soundchat.app.ui.components.LiveAcousticOscilloscope
import com.soundchat.app.ui.components.TransmissionStatusBanner
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SurfaceCardBorder
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.SurfaceElevatedGrey
import com.soundchat.app.ui.theme.TextPrimaryWhite
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import com.soundchat.app.ui.theme.UltrasonicEmerald

@Composable
fun HomeScreen(
    localDeviceHex: String,
    localDeviceName: String,
    audioConfig: AudioConfiguration,
    capabilityReport: DeviceCapabilityReport,
    transmissionStatus: TransmissionStatus,
    isListening: Boolean,
    activeSession: ActiveDeviceSession?,
    diagnostics: DiagnosticTelemetryState,
    onToggleListening: () -> Unit,
    onQuickSendHi: () -> Unit,
    onNavigate: (SoundChatDestination) -> Unit,
    onSwitchBandMode: (AcousticBandMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // SoundChat Brand Hero Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("brand_hero_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    painter = painterResource(id = R.drawable.img_ultrasonic_hero),
                    contentDescription = "Ultrasonic acoustic waveform banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(148.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    ObsidianBlack.copy(alpha = 0.45f),
                                    ObsidianBlack.copy(alpha = 0.92f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(UltrasonicCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "SoundChat Logo",
                                    tint = UltrasonicCyan,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "SoundChat",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = TextPrimaryWhite
                                )
                                Text(
                                    text = "Communication Beyond Connectivity",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UltrasonicCyan
                                )
                            }
                        }

                        Surface(
                            color = ObsidianBlack.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, UltrasonicEmerald.copy(alpha = 0.45f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Offline Node ID",
                                    tint = UltrasonicEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "NODE $localDeviceHex",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = UltrasonicEmerald
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "100% OFFLINE · ZERO RADIO · BFSK ${audioConfig.freqBit0Hz.toInt()}/${audioConfig.freqBit1Hz.toInt()} Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = if (activeSession != null) {
                                "PAIRED: ${activeSession.peerHexId}"
                            } else {
                                "BROADCAST READY"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (activeSession != null) UltrasonicEmerald else UltrasonicCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Real-time Transmission Status Banner
        TransmissionStatusBanner(
            status = transmissionStatus,
            txFreqHz = diagnostics.transmittedFrequencyHz,
            rxFreqHz = diagnostics.receivedFrequencyHz,
            snrDb = diagnostics.snrDb
        )

        // Live Waveform & Ultrasonic Spectrum Visualization
        LiveAcousticOscilloscope(
            waveformPoints = diagnostics.waveformPoints,
            spectrumBars = diagnostics.spectrumBars,
            isListening = isListening,
            isTransmitting = diagnostics.transmittedFrequencyHz > 0.0,
            bandLabel = if (audioConfig.bandMode == AcousticBandMode.NEAR_ULTRASONIC) {
                "18.5–20.0 kHz"
            } else {
                "2.4–3.3 kHz DEV"
            },
            peakFreqHz = diagnostics.receivedFrequencyHz,
            signalDbFs = diagnostics.detectedSignalStrengthDbFs
        )

        // Primary Receive & First-Milestone "Send HI" Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onToggleListening,
                modifier = Modifier
                    .weight(1.1f)
                    .height(54.dp)
                    .testTag("home_toggle_listening_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) Color(0xFF1A3A2A) else UltrasonicCyan,
                    contentColor = if (isListening) UltrasonicEmerald else ObsidianBlack
                )
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.HearingDisabled else Icons.Default.Hearing,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isListening) "Listening Active" else "Start Listening",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onQuickSendHi,
                modifier = Modifier
                    .weight(0.9f)
                    .height(54.dp)
                    .testTag("home_quick_send_hi_button"),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UltrasonicEmerald,
                    contentColor = ObsidianBlack
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TX \"HI\"",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Main Screen Navigation Grid (Phase 1 Lab, Discover Nearby, Chat, Settings)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavigationActionCard(
                title = "Phase 1 Lab",
                subtitle = "Speaker & Mic Freq Test",
                icon = Icons.Default.Science,
                accentColor = UltrasonicCyan,
                testTag = "nav_card_diagnostics",
                onClick = { onNavigate(SoundChatDestination.DIAGNOSTICS) },
                modifier = Modifier.weight(1f)
            )
            NavigationActionCard(
                title = "Discover Nearby",
                subtitle = "Acoustic Beacon & Pair",
                icon = Icons.Default.Radar,
                accentColor = UltrasonicEmerald,
                testTag = "nav_card_discovery",
                onClick = { onNavigate(SoundChatDestination.DEVICES) },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavigationActionCard(
                title = "Acoustic Chat",
                subtitle = "Send & Receive UTF-8",
                icon = Icons.AutoMirrored.Filled.Chat,
                accentColor = UltrasonicCyan,
                testTag = "nav_card_chat",
                onClick = { onNavigate(SoundChatDestination.CHAT) },
                modifier = Modifier.weight(1f)
            )
            NavigationActionCard(
                title = "Settings & DSP",
                subtitle = "${audioConfig.sampleRate} Hz · ${audioConfig.symbolDurationMs}ms/bit",
                icon = Icons.Default.Settings,
                accentColor = TextPrimaryWhite,
                testTag = "nav_card_settings",
                onClick = { onNavigate(SoundChatDestination.SETTINGS) },
                modifier = Modifier.weight(1f)
            )
        }

        // Ultrasonic Hardware Capability Status Card
        CapabilitySummaryCard(
            report = capabilityReport,
            isAudibleFallbackActive = audioConfig.bandMode == AcousticBandMode.AUDIBLE_DEV_MODE,
            onSwitchToAudibleMode = { onSwitchBandMode(AcousticBandMode.AUDIBLE_DEV_MODE) },
            onSwitchToUltrasonicMode = { onSwitchBandMode(AcousticBandMode.NEAR_ULTRASONIC) }
        )

        // Two-Phone Milestone Quick Instructions Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("milestone_guide_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "TWO-DEVICE ACOUSTIC MILESTONE (\"HI\" TEST)",
                    style = MaterialTheme.typography.labelLarge,
                    color = UltrasonicEmerald
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "1. Turn ON Airplane Mode (disable Wi-Fi, Cellular & Bluetooth) on both Phone A and Phone B.\n" +
                        "2. Place phones 10–40 cm apart with speaker facing microphone, media volume at 80–90%.\n" +
                        "3. On Phone B: Tap \"Start Listening\" (grants mic permission).\n" +
                        "4. On Phone A: Tap \"TX HI\" or open Acoustic Chat and press SEND.\n" +
                        "5. Phone B demodulates the 18.75/19.35 kHz BFSK signal, verifies CRC-16, displays \"HI\", and transmits an acoustic ACK back to Phone A!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onNavigate(SoundChatDestination.DIAGNOSTICS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_phase1_diagnostics_button"),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, UltrasonicCyan.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "Open Phase 1 Hardware Validation & Loopback Lab",
                        color = UltrasonicCyan,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceDarkGrey,
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceElevatedGrey),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimaryWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
            }
        }
    }
}

package com.soundchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.audio.TransmissionSpeedPreset
import com.soundchat.app.ui.SoundChatDestination
import com.soundchat.app.ui.theme.AmberWarning
import com.soundchat.app.ui.theme.CoralError
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SurfaceCardBorder
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.TextPrimaryWhite
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import com.soundchat.app.ui.theme.UltrasonicEmerald

@Composable
fun SettingsScreen(
    audioConfig: AudioConfiguration,
    capabilityReport: DeviceCapabilityReport,
    onBandModeSelected: (AcousticBandMode) -> Unit,
    onSampleRateSelected: (Int) -> Unit,
    onSpeedPresetSelected: (TransmissionSpeedPreset) -> Unit,
    onCustomFrequenciesChanged: (Double, Double, Double) -> Unit,
    onRequireAckChanged: (Boolean) -> Unit,
    onBandPassChanged: (Boolean) -> Unit,
    onMaxRetriesChanged: (Int) -> Unit,
    onClearChatHistory: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "DSP & PROTOCOL CONFIGURATION",
            style = MaterialTheme.typography.titleLarge,
            color = UltrasonicCyan
        )

        // 1. Acoustic Frequency Band Mode
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_band_mode_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = UltrasonicCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACOUSTIC FREQUENCY BAND",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryWhite
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                AcousticBandMode.entries.forEach { mode ->
                    val selected = audioConfig.bandMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onBandModeSelected(mode) },
                        label = {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltrasonicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = UltrasonicCyan
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("band_mode_${mode.name.lowercase()}")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Bit 0 Carrier: ${audioConfig.freqBit0Hz.toInt()} Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = UltrasonicCyan
                )
                Slider(
                    value = audioConfig.freqBit0Hz.toFloat(),
                    onValueChange = { newBit0 ->
                        val step = 50.0
                        val rounded0 = kotlin.math.round(newBit0 / step) * step
                        onCustomFrequenciesChanged(rounded0, audioConfig.freqBit1Hz, audioConfig.freqSyncHz)
                    },
                    valueRange = if (audioConfig.bandMode == AcousticBandMode.NEAR_ULTRASONIC) {
                        18200f..19200f
                    } else {
                        1800f..2800f
                    },
                    colors = SliderDefaults.colors(thumbColor = UltrasonicCyan, activeTrackColor = UltrasonicCyan)
                )

                Text(
                    text = "Bit 1 Carrier: ${audioConfig.freqBit1Hz.toInt()} Hz",
                    style = MaterialTheme.typography.labelMedium,
                    color = UltrasonicEmerald
                )
                Slider(
                    value = audioConfig.freqBit1Hz.toFloat(),
                    onValueChange = { newBit1 ->
                        val step = 50.0
                        val rounded1 = kotlin.math.round(newBit1 / step) * step
                        onCustomFrequenciesChanged(audioConfig.freqBit0Hz, rounded1, audioConfig.freqSyncHz)
                    },
                    valueRange = if (audioConfig.bandMode == AcousticBandMode.NEAR_ULTRASONIC) {
                        19000f..19900f
                    } else {
                        2700f..3600f
                    },
                    colors = SliderDefaults.colors(thumbColor = UltrasonicEmerald, activeTrackColor = UltrasonicEmerald)
                )
            }
        }

        // 2. Sample Rate & Transmission Speed
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_speed_and_rate_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = UltrasonicEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "PCM SAMPLE RATE & SYMBOL SPEED",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryWhite
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "PCM Sample Rate (Baseline: 48,000 Hz)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    capabilityReport.supportedSampleRates.forEach { rate ->
                        FilterChip(
                            selected = audioConfig.sampleRate == rate,
                            onClick = { onSampleRateSelected(rate) },
                            label = {
                                Text(
                                    text = "$rate Hz",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = UltrasonicEmerald.copy(alpha = 0.22f),
                                selectedLabelColor = UltrasonicEmerald
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "BFSK Symbol Timing / Bit Rate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
                TransmissionSpeedPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = audioConfig.speedPreset == preset,
                        onClick = { onSpeedPresetSelected(preset) },
                        label = {
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltrasonicCyan.copy(alpha = 0.2f),
                            selectedLabelColor = UltrasonicCyan
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. Error Correction, ACK, and Biquad Band-Pass Filter
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_error_correction_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "PROTOCOL RELIABILITY & DSP FILTERING",
                    style = MaterialTheme.typography.labelLarge,
                    color = UltrasonicCyan
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require Acoustic ACK & Auto-Retransmit",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Only marks MESSAGE DELIVERED after validating CRC-16 ACK from receiver",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                    Switch(
                        checked = audioConfig.requireAcknowledgement,
                        onCheckedChange = onRequireAckChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = UltrasonicEmerald
                        ),
                        modifier = Modifier.testTag("switch_require_ack")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cascaded Biquad Band-Pass Filter",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Rejects room speech & noise outside ${audioConfig.bandMode.passBandLowHz.toInt()}–${audioConfig.bandMode.passBandHighHz.toInt()} Hz",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondaryMuted
                        )
                    }
                    Switch(
                        checked = audioConfig.bandPassFilterEnabled,
                        onCheckedChange = onBandPassChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = UltrasonicCyan
                        ),
                        modifier = Modifier.testTag("switch_bandpass_filter")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Maximum Automatic Retries: ${audioConfig.maxRetries}",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberWarning
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 1, 2, 3).forEach { count ->
                            FilterChip(
                                selected = audioConfig.maxRetries == count,
                                onClick = { onMaxRetriesChanged(count) },
                                label = { Text("$count") }
                            )
                        }
                    }
                }
            }
        }

        // 4. Hardware Tests, Offline Guarantee & Clear Local Storage
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = UltrasonicEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OFFLINE PRIVACY & DIAGNOSTICS",
                        style = MaterialTheme.typography.labelLarge,
                        color = UltrasonicEmerald
                    )
                }

                Text(
                    text = "Verified Zero-Network Manifest: This APK does not declare android.permission.INTERNET, BLUETOOTH, or ACCESS_WIFI_STATE. All DSP demodulation and Room SQLite storage run 100% locally on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )

                OutlinedButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_open_diagnostics_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, UltrasonicCyan)
                ) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = UltrasonicCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Launch Speaker, Microphone & Loopback Test Suite",
                        style = MaterialTheme.typography.labelLarge,
                        color = UltrasonicCyan
                    )
                }

                Button(
                    onClick = onClearChatHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clear_chat_history_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CoralError.copy(alpha = 0.18f),
                        contentColor = CoralError
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clear Local Chat History",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

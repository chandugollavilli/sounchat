package com.soundchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.service.DiagnosticTelemetryState
import com.soundchat.app.service.ValidationExecutionTier
import com.soundchat.app.ui.components.DistanceTestLogCard
import com.soundchat.app.ui.components.LiveAcousticOscilloscope
import com.soundchat.app.ui.components.MessageLengthStressCard
import com.soundchat.app.ui.components.PhysicalValidationStatusCard
import com.soundchat.app.ui.components.RepeatedReliabilityTestCard
import com.soundchat.app.ui.theme.AmberWarning
import com.soundchat.app.ui.theme.CoralError
import com.soundchat.app.ui.theme.JetBrainsMonoFontFamily
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SurfaceCardBorder
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.SurfaceElevatedGrey
import com.soundchat.app.ui.theme.TextPrimaryWhite
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import com.soundchat.app.ui.theme.UltrasonicEmerald
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiagnosticScreen(
    audioConfig: AudioConfiguration,
    capabilityReport: DeviceCapabilityReport,
    diagnostics: DiagnosticTelemetryState,
    isListening: Boolean,
    onToggleListening: () -> Unit,
    onSpeakerTestFreqChange: (Double) -> Unit,
    onStartSpeakerTone: (Double) -> Unit,
    onStopSpeakerTone: () -> Unit,
    onRunLoopbackTest: (String) -> Unit,
    onRunDistanceProbe: (Int, ValidationExecutionTier, String) -> Unit,
    onRecordDistanceSnapshot: (Int, ValidationExecutionTier, String) -> Unit,
    onClearDistanceLogs: () -> Unit,
    onRunLengthStressTest: (ValidationExecutionTier) -> Unit,
    onRunReliabilityTest: (Int, ValidationExecutionTier) -> Unit,
    onCancelValidationSuites: () -> Unit,
    onResetValidationStatus: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Diagnostic Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "LIVE ACOUSTIC DIAGNOSTICS LAB",
                    style = MaterialTheme.typography.titleLarge,
                    color = UltrasonicCyan
                )
                Text(
                    text = "TX/RX Carriers (${audioConfig.freqBit0Hz.toInt()}/${audioConfig.freqBit1Hz.toInt()}/${audioConfig.freqSyncHz.toInt()} Hz) · SNR · Sync · Phase 7 Suite",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
            }
            OutlinedButton(
                onClick = onRefreshCapabilities,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, SurfaceCardBorder),
                modifier = Modifier.testTag("refresh_capabilities_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Re-probe Hardware",
                    tint = UltrasonicCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Phase 7 Honest Physical-Device Validation Status Banner
        PhysicalValidationStatusCard(
            diagnostics = diagnostics,
            onResetValidationStatus = onResetValidationStatus
        )

        // Live Oscilloscope & Spectrum
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

        // 1. SPEAKER FREQUENCY TEST CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("speaker_frequency_test_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SPEAKER FREQUENCY GENERATOR TEST",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimaryWhite
                        )
                    }
                    Text(
                        text = "${diagnostics.speakerTestFreqHz.toInt()} Hz",
                        style = MaterialTheme.typography.labelLarge,
                        color = AmberWarning,
                        fontFamily = JetBrainsMonoFontFamily
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select a target ultrasonic or audible carrier tone and emit on Phone A while monitoring Phone B's Microphone Detector below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )

                Spacer(modifier = Modifier.height(8.dp))
                val presetFreqs = listOf(
                    18500.0 to "18.5 kHz",
                    18750.0 to "18.75 kHz (Bit 0)",
                    19350.0 to "19.35 kHz (Bit 1)",
                    19750.0 to "19.75 kHz (Sync)",
                    2400.0 to "2.4 kHz (Audible)"
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetFreqs.forEach { (freq, label) ->
                        val selected = kotlin.math.abs(diagnostics.speakerTestFreqHz - freq) < 25.0
                        FilterChip(
                            selected = selected,
                            onClick = { onSpeakerTestFreqChange(freq) },
                            label = {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AmberWarning.copy(alpha = 0.22f),
                                selectedLabelColor = AmberWarning
                            )
                        )
                    }
                }

                Slider(
                    value = diagnostics.speakerTestFreqHz.toFloat(),
                    onValueChange = { onSpeakerTestFreqChange(it.toDouble()) },
                    valueRange = 1000f..20500f,
                    colors = SliderDefaults.colors(
                        thumbColor = AmberWarning,
                        activeTrackColor = AmberWarning
                    ),
                    modifier = Modifier.testTag("speaker_freq_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (diagnostics.isSpeakerToneRunning) {
                                onStopSpeakerTone()
                            } else {
                                onStartSpeakerTone(diagnostics.speakerTestFreqHz)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("speaker_test_emit_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (diagnostics.isSpeakerToneRunning) CoralError else AmberWarning,
                            contentColor = ObsidianBlack
                        )
                    ) {
                        Icon(
                            imageVector = if (diagnostics.isSpeakerToneRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (diagnostics.isSpeakerToneRunning) {
                                "Stop Speaker Tone (${diagnostics.speakerTestFreqHz.toInt()} Hz)"
                            } else {
                                "Emit 3.5s Speaker Test Tone (${diagnostics.speakerTestFreqHz.toInt()} Hz)"
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        // 2. MICROPHONE FREQUENCY DETECTION & GOERTZEL TELEMETRY CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("microphone_detection_test_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (isListening) UltrasonicEmerald else TextSecondaryMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MICROPHONE FREQUENCY DETECTOR",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimaryWhite
                        )
                    }

                    Button(
                        onClick = onToggleListening,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isListening) SurfaceElevatedGrey else UltrasonicEmerald,
                            contentColor = if (isListening) UltrasonicEmerald else ObsidianBlack
                        ),
                        modifier = Modifier.testTag("mic_test_toggle_button")
                    ) {
                        Text(
                            text = if (isListening) "Stop Mic Test" else "Start Mic Test",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3x3 Real-time Diagnostic Metrics Grid (Section 11 requirements)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticMetricBox(
                            label = "TX FREQUENCY",
                            value = if (diagnostics.transmittedFrequencyHz > 0) {
                                "${diagnostics.transmittedFrequencyHz.toInt()} Hz"
                            } else {
                                "0 Hz (Idle)"
                            },
                            highlight = diagnostics.transmittedFrequencyHz > 0,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticMetricBox(
                            label = "RX PEAK FREQ",
                            value = String.format(Locale.US, "%.1f Hz", diagnostics.receivedFrequencyHz),
                            highlight = diagnostics.receivedFrequencyHz >= 18000.0,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticMetricBox(
                            label = "SAMPLE RATE",
                            value = "${diagnostics.actualSampleRateHz} Hz",
                            highlight = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticMetricBox(
                            label = "SIGNAL STRENGTH",
                            value = String.format(Locale.US, "%.1f dBFS", diagnostics.detectedSignalStrengthDbFs),
                            highlight = diagnostics.detectedSignalStrengthDbFs > -55.0,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticMetricBox(
                            label = "SNR (IN-BAND)",
                            value = String.format(Locale.US, "%.1f dB", diagnostics.snrDb),
                            highlight = diagnostics.snrDb >= 6.5,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticMetricBox(
                            label = "NOISE FLOOR",
                            value = String.format(Locale.US, "%.1f dB", diagnostics.noiseFloorDb),
                            highlight = diagnostics.snrDb >= 6.5,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DiagnosticMetricBox(
                            label = "PACKETS / CRC",
                            value = "${diagnostics.crcPassCount} OK / ${diagnostics.crcFailCount} ERR",
                            highlight = diagnostics.crcPassCount > 0,
                            modifier = Modifier.weight(1f)
                        )
                        DiagnosticMetricBox(
                            label = "SYNC / CARRIER",
                            value = if (diagnostics.carrierDetected) "CARRIER ACTIVE" else "QUIET",
                            highlight = diagnostics.carrierDetected,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Goertzel Carrier Bin Energy Bars (f0, f1, fSync)
                Text(
                    text = "GOERTZEL MATCHED-FILTER CARRIER BINS",
                    style = MaterialTheme.typography.labelSmall,
                    color = UltrasonicCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                GoertzelCarrierBar(
                    label = "Bit 0 (${audioConfig.freqBit0Hz.toInt()} Hz)",
                    powerDb = diagnostics.bit0PowerDb,
                    color = UltrasonicCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                GoertzelCarrierBar(
                    label = "Bit 1 (${audioConfig.freqBit1Hz.toInt()} Hz)",
                    powerDb = diagnostics.bit1PowerDb,
                    color = UltrasonicEmerald
                )
                Spacer(modifier = Modifier.height(4.dp))
                GoertzelCarrierBar(
                    label = "Sync (${audioConfig.freqSyncHz.toInt()} Hz)",
                    powerDb = diagnostics.syncPowerDb,
                    color = AmberWarning
                )
            }
        }

        // 3. PACKET DECODING TELEMETRY & LOCAL ACOUSTIC LOOPBACK TEST
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("loopback_and_packet_telemetry_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = UltrasonicEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PACKET FRAMING, CRC-16 & LOOPBACK",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimaryWhite
                        )
                    }
                    Surface(
                        color = ObsidianBlack,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "TX Duration: ${diagnostics.lastTransmissionDurationMs} ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = UltrasonicCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: Local Loopback tests single-device Speaker->Mic + DSP framing. Real verification requires two separate physical phones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AmberWarning
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onRunLoopbackTest("HI") },
                        enabled = !diagnostics.isLoopbackRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loopback_test_hi_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltrasonicEmerald,
                            contentColor = ObsidianBlack
                        )
                    ) {
                        Text(
                            text = "Loopback \"HI\"",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    OutlinedButton(
                        onClick = { onRunLoopbackTest("HELLO WORLD") },
                        enabled = !diagnostics.isLoopbackRunning,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("loopback_test_hello_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, UltrasonicCyan)
                    ) {
                        Text(
                            text = "Loopback \"HELLO\"",
                            style = MaterialTheme.typography.labelMedium,
                            color = UltrasonicCyan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Packet Sync: ${diagnostics.lastSyncResult}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (diagnostics.lastSyncResult.startsWith("LOCKED")) UltrasonicEmerald else TextPrimaryWhite,
                        modifier = Modifier.testTag("diagnostic_sync_status_text")
                    )
                    Text(
                        text = "CRC-16 Status: ${diagnostics.lastCrcResult}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (diagnostics.lastCrcResult.startsWith("VALID")) UltrasonicEmerald else TextPrimaryWhite,
                        modifier = Modifier.testTag("diagnostic_crc_status_text")
                    )
                    Text(
                        text = "ACK Status: ${diagnostics.lastAckResult}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (diagnostics.lastAckResult.startsWith("ACK VERIFIED")) UltrasonicEmerald else AmberWarning
                    )
                    Text(
                        text = "Decoded Output: ${diagnostics.lastDecodedResult}",
                        style = MaterialTheme.typography.labelMedium,
                        color = UltrasonicCyan,
                        modifier = Modifier.testTag("diagnostic_decoded_output_text")
                    )
                    Text(
                        text = "Loopback Summary: ${diagnostics.lastLoopbackReport}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                    Text(
                        text = "Raw Frame Hex: ${diagnostics.lastRawFrameHex}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                }
            }
        }

        // PHASE 7 VALIDATION TEST SUITES
        DistanceTestLogCard(
            diagnostics = diagnostics,
            onRunDistanceProbe = onRunDistanceProbe,
            onRecordLiveSnapshot = onRecordDistanceSnapshot,
            onClearDistanceLogs = onClearDistanceLogs
        )

        MessageLengthStressCard(
            diagnostics = diagnostics,
            onRunStressTest = onRunLengthStressTest,
            onCancelSuites = onCancelValidationSuites
        )

        RepeatedReliabilityTestCard(
            diagnostics = diagnostics,
            onRunReliabilityTest = onRunReliabilityTest,
            onCancelSuites = onCancelValidationSuites
        )

        // 4. PHYSICAL TEST MODE DIAGNOSTIC REPORT (PHASE 6)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("physical_test_report_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, UltrasonicCyan.copy(alpha = 0.45f))
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "PHYSICAL TEST MODE · DIAGNOSTIC REPORT",
                    style = MaterialTheme.typography.labelLarge,
                    color = UltrasonicCyan
                )
                Text(
                    text = "Strictly distinguishes local DSP/loopback checks from two-device over-the-air physical validation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )

                Spacer(modifier = Modifier.height(2.dp))
                CapabilityRow("Device Model", diagnostics.deviceModel)
                CapabilityRow("Android Version", diagnostics.androidVersion)
                CapabilityRow(
                    "Actual Recording Sample Rate",
                    if (diagnostics.actualRecordingSampleRateHz > 0) {
                        "${diagnostics.actualRecordingSampleRateHz} Hz (AudioRecord)"
                    } else {
                        "Not measured yet (Start Mic Test)"
                    }
                )
                CapabilityRow(
                    "Actual Playback Sample Rate",
                    if (diagnostics.actualPlaybackSampleRateHz > 0) {
                        "${diagnostics.actualPlaybackSampleRateHz} Hz (AudioTrack)"
                    } else {
                        "Not measured yet (Emit Tone / Send)"
                    }
                )
                CapabilityRow(
                    "Detected Input Devices",
                    capabilityReport.inputDeviceNames.joinToString(", ")
                )
                CapabilityRow(
                    "Detected Output Devices",
                    capabilityReport.outputDeviceNames.joinToString(", ")
                )
                CapabilityRow(
                    "Measured Carrier Frequency",
                    String.format(Locale.US, "%.1f Hz", diagnostics.receivedFrequencyHz)
                )
                CapabilityRow(
                    "Received Signal Level",
                    String.format(Locale.US, "%.1f dBFS", diagnostics.detectedSignalStrengthDbFs)
                )
                CapabilityRow(
                    "Measured In-Band SNR",
                    String.format(Locale.US, "%.1f dB", diagnostics.snrDb)
                )
                CapabilityRow("Packet Sync Result", diagnostics.lastSyncResult)
                CapabilityRow("CRC-16 Result", diagnostics.lastCrcResult)
                CapabilityRow("Remote Sender ID", diagnostics.lastRemoteSenderIdHex)
                CapabilityRow("ACK Transaction Result", diagnostics.lastAckResult)
                CapabilityRow("Transmission Duration", "${diagnostics.lastTransmissionDurationMs} ms")

                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "VALIDATION LEVEL STATUS",
                        style = MaterialTheme.typography.labelSmall,
                        color = UltrasonicCyan
                    )
                    ValidationTierRow(
                        tier = "A. Unit & DSP Simulation",
                        statusText = "Local JVM / Robolectric Suite (No Physical Air Channel)",
                        verified = true,
                        highlightColor = UltrasonicCyan
                    )
                    ValidationTierRow(
                        tier = "B. Local Acoustic Loopback",
                        statusText = if (diagnostics.localLoopbackVerified) {
                            "PASSED (Single-Device Speaker -> Mic)"
                        } else {
                            "PENDING (Run Loopback Test above)"
                        },
                        verified = diagnostics.localLoopbackVerified,
                        highlightColor = AmberWarning
                    )
                    ValidationTierRow(
                        tier = "C. Physical Two-Device Transfer",
                        statusText = when {
                            diagnostics.physicalTwoDeviceRxVerified && diagnostics.physicalTwoDeviceAckVerified ->
                                "PASSED (Remote RX + Remote ACK Confirmed)"
                            diagnostics.physicalTwoDeviceRxVerified ->
                                "PARTIAL (Remote RX Confirmed · Awaiting Remote ACK)"
                            diagnostics.physicalTwoDeviceAckVerified ->
                                "PARTIAL (Remote ACK Confirmed · Awaiting Remote RX)"
                            else ->
                                "NOT VERIFIED (Requires 2 Physical Phones)"
                        },
                        verified = diagnostics.physicalTwoDeviceRxVerified && diagnostics.physicalTwoDeviceAckVerified,
                        highlightColor = UltrasonicEmerald
                    )
                }
            }
        }

        // 5. HARDWARE CAPABILITY BREAKDOWN CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AUDIOMANAGER CAPABILITY INSPECTION",
                    style = MaterialTheme.typography.labelLarge,
                    color = UltrasonicCyan
                )
                CapabilityRow("PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND", capabilityReport.micNearUltrasoundRawProperty)
                CapabilityRow("PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND", capabilityReport.speakerNearUltrasoundRawProperty)
                CapabilityRow("UNPROCESSED Audio Source Support", capabilityReport.unprocessedAudioSourceSupported.toString())
                CapabilityRow("Active Capture AudioSource", diagnostics.activeAudioSource)
                CapabilityRow("Native Output Sample Rate", "${capabilityReport.nativeSampleRateHz} Hz")
                CapabilityRow("Supported PCM Sample Rates", capabilityReport.supportedSampleRates.joinToString(", ") { "$it Hz" })
                CapabilityRow("Detected Input Hardware", capabilityReport.inputDeviceNames.joinToString(", "))
                CapabilityRow("Detected Output Hardware", capabilityReport.outputDeviceNames.joinToString(", "))
            }
        }

        // 5. LIVE DSP & PROTOCOL EVENT LOG
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "REAL-TIME DSP & PROTOCOL LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = UltrasonicEmerald
                    )
                    Surface(
                        onClick = onClearLogs,
                        color = SurfaceElevatedGrey,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (diagnostics.eventLogs.isEmpty()) {
                    Text(
                        text = "No events logged yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                } else {
                    diagnostics.eventLogs.take(14).forEach { logLine ->
                        Text(
                            text = logLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryMuted,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticMetricBox(
    label: String,
    value: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBlack)
            .padding(10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryMuted
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (highlight) UltrasonicEmerald else TextPrimaryWhite,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun GoertzelCarrierBar(
    label: String,
    powerDb: Double,
    color: Color
) {
    val progress = ((powerDb + 100.0) / 85.0).toFloat().coerceIn(0.02f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondaryMuted)
            Text(
                text = String.format(Locale.US, "%.1f dB", powerDb),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = ObsidianBlack
        )
    }
}

@Composable
private fun CapabilityRow(key: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryMuted,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimaryWhite,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ValidationTierRow(
    tier: String,
    statusText: String,
    verified: Boolean,
    highlightColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tier,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimaryWhite,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = if (verified) highlightColor else AmberWarning,
            modifier = Modifier.weight(1.3f)
        )
    }
}

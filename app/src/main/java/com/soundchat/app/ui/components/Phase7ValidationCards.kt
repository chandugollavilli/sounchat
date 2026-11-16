package com.soundchat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.service.DiagnosticTelemetryState
import com.soundchat.app.service.PhysicalValidationStatus
import com.soundchat.app.service.ValidationExecutionTier
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

@Composable
fun PhysicalValidationStatusCard(
    diagnostics: DiagnosticTelemetryState,
    onResetValidationStatus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = diagnostics.physicalValidationStatus
    val accent = when (status) {
        PhysicalValidationStatus.PASSED_TWO_DEVICE -> UltrasonicEmerald
        PhysicalValidationStatus.FAILED_NO_REMOTE_ACK -> CoralError
        PhysicalValidationStatus.IN_PROGRESS -> AmberWarning
        PhysicalValidationStatus.PARTIAL_RX_ONLY,
        PhysicalValidationStatus.LOOPBACK_ONLY,
        PhysicalValidationStatus.SIMULATED_ONLY -> AmberWarning
        PhysicalValidationStatus.NOT_RUN -> UltrasonicCyan
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("phase7_validation_status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.5.dp, accent.copy(alpha = 0.65f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = when (status) {
                            PhysicalValidationStatus.PASSED_TWO_DEVICE -> Icons.Default.CheckCircle
                            PhysicalValidationStatus.FAILED_NO_REMOTE_ACK -> Icons.Default.ErrorOutline
                            PhysicalValidationStatus.NOT_RUN -> Icons.Default.Shield
                            else -> Icons.Default.WarningAmber
                        },
                        contentDescription = status.badgeText,
                        tint = accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PHASE 7 · PHYSICAL VALIDATION STATUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextPrimaryWhite
                        )
                        Text(
                            text = status.badgeText,
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("physical_validation_badge_text")
                        )
                    }
                }

                OutlinedButton(
                    onClick = onResetValidationStatus,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SurfaceCardBorder),
                    modifier = Modifier.testTag("reset_validation_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset validation status",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = status.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryMuted
            )

            // Explicit three-tier proof matrix
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBlack)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ValidationMatrixLine(
                    tier = "A. Simulated DSP (In-Memory)",
                    passed = diagnostics.simulatedDspVerified,
                    detail = if (diagnostics.simulatedDspVerified) "PASSED (DSP math only)" else "Not run",
                    passColor = UltrasonicCyan
                )
                ValidationMatrixLine(
                    tier = "B. Local Loopback (1 Phone)",
                    passed = diagnostics.localLoopbackVerified,
                    detail = if (diagnostics.localLoopbackVerified) "PASSED (Speaker -> Mic)" else "Not verified",
                    passColor = AmberWarning
                )
                ValidationMatrixLine(
                    tier = "C. Physical Two-Device + ACK",
                    passed = diagnostics.physicalTwoDeviceAckVerified,
                    detail = when {
                        diagnostics.physicalTwoDeviceAckVerified -> "PASSED (Remote RX + ACK)"
                        diagnostics.physicalTwoDeviceRxVerified -> "PARTIAL (Remote RX only)"
                        else -> "NOT VERIFIED"
                    },
                    passColor = UltrasonicEmerald
                )
            }
        }
    }
}

@Composable
private fun ValidationMatrixLine(
    tier: String,
    passed: Boolean,
    detail: String,
    passColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tier,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimaryWhite,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = if (passed) passColor else TextSecondaryMuted,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DistanceTestLogCard(
    diagnostics: DiagnosticTelemetryState,
    onRunDistanceProbe: (distanceCm: Int, tier: ValidationExecutionTier, notes: String) -> Unit,
    onRecordLiveSnapshot: (distanceCm: Int, tier: ValidationExecutionTier, notes: String) -> Unit,
    onClearDistanceLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDistanceCm by rememberSaveable { mutableIntStateOf(30) }
    var selectedTierName by rememberSaveable { mutableStateOf(ValidationExecutionTier.TWO_DEVICE_PHYSICAL.name) }
    var notes by rememberSaveable { mutableStateOf("") }
    val selectedTier = ValidationExecutionTier.entries.firstOrNull { it.name == selectedTierName }
        ?: ValidationExecutionTier.TWO_DEVICE_PHYSICAL

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("distance_test_log_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Straighten,
                        contentDescription = null,
                        tint = UltrasonicCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "TRANSMISSION DISTANCE TEST LOG",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryWhite
                    )
                }
                if (diagnostics.distanceTestLogs.isNotEmpty()) {
                    Surface(
                        onClick = onClearDistanceLogs,
                        color = SurfaceElevatedGrey,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("clear_distance_logs_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Clear distance logs",
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondaryMuted
                            )
                        }
                    }
                }
            }

            Text(
                text = "Measure physical SNR, carrier lock, CRC-16, and remote ACK reliability across real speaker-to-mic distances.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryMuted
            )

            // Distance presets (cm)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(10, 30, 50, 100, 200).forEach { cm ->
                    FilterChip(
                        selected = selectedDistanceCm == cm,
                        onClick = { selectedDistanceCm = cm },
                        label = { Text("$cm cm", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltrasonicCyan.copy(alpha = 0.22f),
                            selectedLabelColor = UltrasonicCyan
                        )
                    )
                }
            }

            // Tier selector
            TierSelectorRow(
                selectedTier = selectedTier,
                onSelect = { selectedTierName = it.name },
                tagPrefix = "distance_tier"
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { if (it.length <= 48) notes = it },
                placeholder = {
                    Text(
                        text = "Optional env note (e.g. Quiet room, Vol 90%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryMuted
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UltrasonicCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedContainerColor = ObsidianBlack,
                    unfocusedContainerColor = ObsidianBlack,
                    focusedTextColor = TextPrimaryWhite,
                    unfocusedTextColor = TextPrimaryWhite
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("distance_notes_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onRunDistanceProbe(selectedDistanceCm, selectedTier, notes) },
                    enabled = !diagnostics.isDistanceProbeRunning &&
                        !diagnostics.lengthStressReport.isRunning &&
                        !diagnostics.reliabilityReport.isRunning,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UltrasonicEmerald,
                        contentColor = ObsidianBlack
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("run_distance_probe_button")
                ) {
                    Text(
                        text = if (diagnostics.isDistanceProbeRunning) "Probing…" else "Run ${selectedDistanceCm}cm Probe",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = { onRecordLiveSnapshot(selectedDistanceCm, selectedTier, notes) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, UltrasonicCyan.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("log_distance_snapshot_button")
                ) {
                    Text(
                        text = "Log RX Snapshot",
                        style = MaterialTheme.typography.labelMedium,
                        color = UltrasonicCyan
                    )
                }
            }

            if (diagnostics.distanceTestLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "No distance measurements recorded yet.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryMuted
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianBlack)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    diagnostics.distanceTestLogs.take(8).forEachIndexed { idx, entry ->
                        if (idx > 0) {
                            HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.5f))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[${entry.timestampFormatted}] ${entry.distanceCm} cm · ${entry.tier.shortLabel}",
                                style = MaterialTheme.typography.labelSmall,
                                color = UltrasonicCyan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.outcomeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    entry.ackVerifiedByRemote -> UltrasonicEmerald
                                    entry.crcPassed -> AmberWarning
                                    else -> CoralError
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "Peak %.0f Hz · Level %.1f dBFS · SNR %.1f dB · %dms%s",
                                entry.measuredPeakFreqHz,
                                entry.signalLevelDbFs,
                                entry.snrDb,
                                entry.latencyMs,
                                if (entry.notes.isNotBlank()) " · ${entry.notes}" else ""
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondaryMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageLengthStressCard(
    diagnostics: DiagnosticTelemetryState,
    onRunStressTest: (ValidationExecutionTier) -> Unit,
    onCancelSuites: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTierName by rememberSaveable { mutableStateOf(ValidationExecutionTier.SIMULATED_DSP.name) }
    val selectedTier = ValidationExecutionTier.entries.firstOrNull { it.name == selectedTierName }
        ?: ValidationExecutionTier.SIMULATED_DSP
    val report = diagnostics.lengthStressReport

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("length_stress_test_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MESSAGE-LENGTH STRESS TEST (2 B -> 64 B)",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryWhite
                    )
                }
                Text(
                    text = "${report.currentStepIndex}/${report.totalSteps}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = AmberWarning
                )
            }

            Text(
                text = "Validates symbol timing drift and CRC-16 integrity from short 2-byte frames up to 64-byte max UTF-8 packets.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryMuted
            )

            TierSelectorRow(
                selectedTier = selectedTier,
                onSelect = { selectedTierName = it.name },
                tagPrefix = "stress_tier"
            )

            if (report.isRunning) {
                LinearProgressIndicator(
                    progress = { (report.currentStepIndex.toFloat() / report.totalSteps.coerceAtLeast(1)).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AmberWarning,
                    trackColor = ObsidianBlack
                )
            }

            Button(
                onClick = {
                    if (report.isRunning) onCancelSuites() else onRunStressTest(selectedTier)
                },
                enabled = !diagnostics.reliabilityReport.isRunning && !diagnostics.isDistanceProbeRunning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (report.isRunning) CoralError else UltrasonicCyan,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_length_stress_button")
            ) {
                Icon(
                    imageVector = if (report.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (report.isRunning) "Stop Length Stress Test" else "Run 2B -> 64B Stress Suite (${selectedTier.shortLabel})",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBlack)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Status: ${report.summaryStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = UltrasonicCyan,
                    modifier = Modifier.testTag("length_stress_status_text")
                )
                report.items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${item.payloadBytes}B (${item.totalFrameBytes}B frm) · ${item.airDurationMs}ms",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondaryMuted
                        )
                        Text(
                            text = item.outcomeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                item.remoteAckVerified -> UltrasonicEmerald
                                item.crcPassed -> if (report.tier == ValidationExecutionTier.SIMULATED_DSP) UltrasonicCyan else AmberWarning
                                else -> CoralError
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RepeatedReliabilityTestCard(
    diagnostics: DiagnosticTelemetryState,
    onRunReliabilityTest: (repetitions: Int, tier: ValidationExecutionTier) -> Unit,
    onCancelSuites: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedReps by rememberSaveable { mutableIntStateOf(5) }
    var selectedTierName by rememberSaveable { mutableStateOf(ValidationExecutionTier.SIMULATED_DSP.name) }
    val selectedTier = ValidationExecutionTier.entries.firstOrNull { it.name == selectedTierName }
        ?: ValidationExecutionTier.SIMULATED_DSP
    val report = diagnostics.reliabilityReport

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("reliability_test_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = null,
                        tint = UltrasonicEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "REPEATED TRANSMISSION RELIABILITY TEST",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimaryWhite
                    )
                }
                Text(
                    text = "${report.successRatePercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = if (report.completedCount == 0) TextSecondaryMuted else UltrasonicEmerald,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "Sends sequential bursts to measure CRC pass rate, average latency, SNR stability, and real remote ACK delivery.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryMuted
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(3, 5, 10).forEach { count ->
                    FilterChip(
                        selected = selectedReps == count,
                        onClick = { selectedReps = count },
                        label = { Text("${count}x Trials", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = UltrasonicEmerald.copy(alpha = 0.22f),
                            selectedLabelColor = UltrasonicEmerald
                        )
                    )
                }
            }

            TierSelectorRow(
                selectedTier = selectedTier,
                onSelect = { selectedTierName = it.name },
                tagPrefix = "reliability_tier"
            )

            if (report.isRunning) {
                LinearProgressIndicator(
                    progress = { (report.completedCount.toFloat() / report.totalPlanned.coerceAtLeast(1)).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = UltrasonicEmerald,
                    trackColor = ObsidianBlack
                )
            }

            Button(
                onClick = {
                    if (report.isRunning) onCancelSuites() else onRunReliabilityTest(selectedReps, selectedTier)
                },
                enabled = !diagnostics.lengthStressReport.isRunning && !diagnostics.isDistanceProbeRunning,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (report.isRunning) CoralError else UltrasonicEmerald,
                    contentColor = ObsidianBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("run_reliability_test_button")
            ) {
                Icon(
                    imageVector = if (report.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (report.isRunning) "Stop Reliability Burst" else "Run ${selectedReps}x Reliability Test (${selectedTier.shortLabel})",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianBlack)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = report.summaryStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = UltrasonicCyan,
                    modifier = Modifier.testTag("reliability_status_text")
                )
                if (report.completedCount > 0) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "Done %d/%d · CRC OK: %d · Remote ACKs: %d · Timeouts/Fail: %d · Avg %dms · Avg SNR %.1f dB",
                            report.completedCount,
                            report.totalPlanned,
                            report.crcPassCount,
                            report.remoteAckSuccessCount,
                            report.failedOrTimeoutCount,
                            report.averageLatencyMs,
                            report.averageSnrDb
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFontFamily,
                        color = TextSecondaryMuted
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TierSelectorRow(
    selectedTier: ValidationExecutionTier,
    onSelect: (ValidationExecutionTier) -> Unit,
    tagPrefix: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ValidationExecutionTier.entries.forEach { tier ->
            val selected = selectedTier == tier
            val chipColor = when (tier) {
                ValidationExecutionTier.SIMULATED_DSP -> UltrasonicCyan
                ValidationExecutionTier.LOCAL_LOOPBACK -> AmberWarning
                ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> UltrasonicEmerald
            }
            FilterChip(
                selected = selected,
                onClick = { onSelect(tier) },
                label = {
                    Text(
                        text = tier.shortLabel,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipColor.copy(alpha = 0.22f),
                    selectedLabelColor = chipColor
                ),
                modifier = Modifier.testTag("${tagPrefix}_${tier.name.lowercase()}")
            )
        }
    }
}

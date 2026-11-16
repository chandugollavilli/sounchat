package com.soundchat.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.data.models.TransmissionStatus
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
fun TransmissionStatusBanner(
    status: TransmissionStatus,
    txFreqHz: Double,
    rxFreqHz: Double,
    snrDb: Double,
    modifier: Modifier = Modifier
) {
    val accentColor by animateColorAsState(
        targetValue = when (status) {
            TransmissionStatus.IDLE -> TextSecondaryMuted
            TransmissionStatus.LISTENING -> UltrasonicCyan
            TransmissionStatus.DISCOVERING,
            TransmissionStatus.DEVICE_DETECTED,
            TransmissionStatus.PAIRING -> UltrasonicCyan
            TransmissionStatus.TRANSMITTING,
            TransmissionStatus.WAITING_FOR_ACKNOWLEDGEMENT,
            TransmissionStatus.RETRYING -> AmberWarning
            TransmissionStatus.MESSAGE_DELIVERED -> UltrasonicEmerald
            TransmissionStatus.TRANSMISSION_FAILED -> CoralError
        },
        label = "status_accent"
    )

    val isActiveStatus = status != TransmissionStatus.IDLE
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("transmission_status_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                accentColor.copy(alpha = if (isActiveStatus) pulseAlpha else 0.7f)
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = status.displayTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = accentColor,
                        modifier = Modifier.testTag("status_title_text")
                    )
                }

                Icon(
                    imageVector = when (status) {
                        TransmissionStatus.MESSAGE_DELIVERED -> Icons.Default.CheckCircle
                        TransmissionStatus.TRANSMISSION_FAILED -> Icons.Default.ErrorOutline
                        TransmissionStatus.DISCOVERING,
                        TransmissionStatus.DEVICE_DETECTED -> Icons.Default.Radar
                        TransmissionStatus.TRANSMITTING,
                        TransmissionStatus.RETRYING -> Icons.Default.GraphicEq
                        TransmissionStatus.WAITING_FOR_ACKNOWLEDGEMENT,
                        TransmissionStatus.PAIRING -> Icons.Default.Sync
                        else -> Icons.Default.Hearing
                    },
                    contentDescription = status.displayTitle,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = status.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryMuted
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TelemetryChip(
                    label = "TX",
                    value = if (txFreqHz > 0) "${txFreqHz.toInt()} Hz" else "OFF",
                    color = if (txFreqHz > 0) AmberWarning else TextSecondaryMuted
                )
                TelemetryChip(
                    label = "RX PEAK",
                    value = if (rxFreqHz > 0) "${rxFreqHz.toInt()} Hz" else "— Hz",
                    color = if (rxFreqHz > 18000) UltrasonicCyan else TextPrimaryWhite
                )
                TelemetryChip(
                    label = "SNR",
                    value = String.format(Locale.US, "%.1f dB", snrDb),
                    color = if (snrDb >= 6.5) UltrasonicEmerald else TextSecondaryMuted
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    label: String,
    value: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Dual-mode live Ultrasonic Oscilloscope (Time-domain PCM waveform + 64-bin FFT Spectrum).
 */
@Composable
fun LiveAcousticOscilloscope(
    waveformPoints: List<Float>,
    spectrumBars: List<Float>,
    isListening: Boolean,
    isTransmitting: Boolean,
    bandLabel: String,
    peakFreqHz: Double,
    signalDbFs: Double,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_oscilloscope_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianBlack),
        border = BorderStroke(1.dp, SurfaceCardBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ACOUSTIC DSP OSCILLOSCOPE",
                        style = MaterialTheme.typography.labelMedium,
                        color = UltrasonicCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = SurfaceElevatedGrey,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = bandLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = UltrasonicEmerald,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "%.0f Hz · %.1f dBFS",
                        peakFreqHz,
                        signalDbFs
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background( Color(0xFF05080B) )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Subtle oscilloscope grid
                    val gridColor = Color(0xFF13202B)
                    for (r in 1..3) {
                        val y = (h / 4f) * r
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                    }
                    for (c in 1..7) {
                        val x = (w / 8f) * c
                        drawLine(
                            color = gridColor,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                    }

                    // Draw FFT spectrum bars in lower portion
                    if (spectrumBars.isNotEmpty()) {
                        val barCount = spectrumBars.size
                        val slotWidth = w / barCount
                        val barWidth = (slotWidth * 0.68f).coerceAtLeast(2f)

                        for (i in spectrumBars.indices) {
                            val norm = spectrumBars[i].coerceIn(0.03f, 1f)
                            val barH = norm * (h * 0.65f)
                            val x = i * slotWidth + (slotWidth - barWidth) / 2f
                            val y = h - barH
                            val isHighBand = i >= (barCount * 0.72f)
                            val barColor = when {
                                isTransmitting && isHighBand -> AmberWarning.copy(alpha = 0.72f)
                                isHighBand -> UltrasonicEmerald.copy(alpha = 0.68f)
                                else -> UltrasonicCyan.copy(alpha = 0.35f)
                            }
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, y),
                                size = Size(barWidth, barH),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }

                    // Draw time-domain PCM waveform across center
                    if (waveformPoints.size >= 2) {
                        val path = Path()
                        val midY = h * 0.42f
                        val scaleY = h * 0.36f
                        val stepX = w / (waveformPoints.size - 1).coerceAtLeast(1)

                        for (i in waveformPoints.indices) {
                            val x = i * stepX
                            val amp = if (isListening || isTransmitting) {
                                (waveformPoints[i] * 3.2f).coerceIn(-1f, 1f)
                            } else {
                                0f
                            }
                            val y = midY - amp * scaleY
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            brush = Brush.horizontalGradient(
                                listOf(UltrasonicCyan, UltrasonicEmerald, UltrasonicCyan)
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CapabilitySummaryCard(
    report: DeviceCapabilityReport,
    isAudibleFallbackActive: Boolean,
    onSwitchToAudibleMode: () -> Unit,
    onSwitchToUltrasonicMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (report.isFullyUltrasonicVerified) {
        UltrasonicEmerald.copy(alpha = 0.5f)
    } else {
        AmberWarning.copy(alpha = 0.6f)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("capability_summary_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (report.isFullyUltrasonicVerified) {
                            Icons.Default.CheckCircle
                        } else {
                            Icons.Default.WarningAmber
                        },
                        contentDescription = "Hardware Capability",
                        tint = if (report.isFullyUltrasonicVerified) UltrasonicEmerald else AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (report.isFullyUltrasonicVerified) {
                            "ULTRASONIC HARDWARE VERIFIED"
                        } else {
                            "ULTRASONIC CAPABILITY REPORT"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (report.isFullyUltrasonicVerified) UltrasonicEmerald else AmberWarning
                    )
                }

                Text(
                    text = "${report.nativeSampleRateHz} Hz Native",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFontFamily,
                    color = UltrasonicCyan
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwarePropertyBadge(
                    title = "MIC ULTRASOUND",
                    value = report.micNearUltrasoundRawProperty.uppercase(),
                    supported = report.micNearUltrasoundSupported,
                    modifier = Modifier.weight(1f)
                )
                HardwarePropertyBadge(
                    title = "SPK ULTRASOUND",
                    value = report.speakerNearUltrasoundRawProperty.uppercase(),
                    supported = report.speakerNearUltrasoundSupported,
                    modifier = Modifier.weight(1f)
                )
                HardwarePropertyBadge(
                    title = "NYQUIST LIMIT",
                    value = "${report.nyquistFrequencyHz} Hz",
                    supported = report.canSampleUltrasonicBand,
                    modifier = Modifier.weight(1f)
                )
            }

            if (report.hardwareWarnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = report.hardwareWarnings.first(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryMuted
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isAudibleFallbackActive) {
                            "Active: Audible Dev Mode (2.4–3.3 kHz)"
                        } else {
                            "Active: Near-Ultrasonic (18.75–19.75 kHz)"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = UltrasonicCyan
                    )
                    Surface(
                        onClick = {
                            if (isAudibleFallbackActive) onSwitchToUltrasonicMode()
                            else onSwitchToAudibleMode()
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = SurfaceElevatedGrey,
                        border = BorderStroke(1.dp, UltrasonicCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.testTag("toggle_band_mode_button")
                    ) {
                        Text(
                            text = if (isAudibleFallbackActive) {
                                "Switch to Ultrasonic"
                            } else {
                                "Try Audible Dev Mode"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = UltrasonicCyan,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwarePropertyBadge(
    title: String,
    value: String,
    supported: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianBlack)
            .padding(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryMuted
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = if (supported) UltrasonicEmerald else AmberWarning,
            fontWeight = FontWeight.Bold
        )
    }
}

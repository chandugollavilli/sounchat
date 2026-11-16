package com.soundchat.app.service

/**
 * Strictly separates the three acoustic validation domains so simulated or
 * single-device tests are never confused with independent two-device transfer.
 */
enum class ValidationExecutionTier(
    val shortLabel: String,
    val fullTitle: String,
    val description: String
) {
    SIMULATED_DSP(
        shortLabel = "A. Simulated DSP",
        fullTitle = "Tier A · Simulated DSP Pipeline (In-Memory)",
        description = "Modulator -> BandPassFilter -> Goertzel Demodulator -> CRC-16 without speaker or mic."
    ),
    LOCAL_LOOPBACK(
        shortLabel = "B. Local Loopback",
        fullTitle = "Tier B · Local Acoustic Loopback (Single Device)",
        description = "Local speaker -> room air -> local mic on one phone. Cannot verify two-device ACK."
    ),
    TWO_DEVICE_PHYSICAL(
        shortLabel = "C. Two-Device Air",
        fullTitle = "Tier C · Physical Two-Device Transfer + Remote ACK",
        description = "Phone A speaker -> air -> Phone B mic -> Phone B decodes -> Phone B transmits real acoustic ACK back."
    )
}

/**
 * Honest physical-device validation state. Never transitions to [PASSED_TWO_DEVICE]
 * automatically; requires real remote CRC-valid decoding + a verified remote ACK.
 */
enum class PhysicalValidationStatus(
    val badgeText: String,
    val summary: String
) {
    NOT_RUN(
        badgeText = "NOT VERIFIED",
        summary = "No physical two-device message + remote ACK has been verified yet."
    ),
    SIMULATED_ONLY(
        badgeText = "SIMULATED DSP ONLY",
        summary = "In-memory DSP checks passed, but physical speaker/mic hardware is NOT verified."
    ),
    LOOPBACK_ONLY(
        badgeText = "LOCAL LOOPBACK ONLY",
        summary = "Single-device speaker -> mic passed. Independent two-device transfer is NOT verified."
    ),
    IN_PROGRESS(
        badgeText = "TESTING IN PROGRESS",
        summary = "Transmitting physical acoustic frames; waiting for an independent peer ACK…"
    ),
    PARTIAL_RX_ONLY(
        badgeText = "REMOTE RX DECODED",
        summary = "Decoded a real packet from a remote device & sent ACK. Verify ACK on sender phone."
    ),
    FAILED_NO_REMOTE_ACK(
        badgeText = "FAILED · NO REMOTE ACK",
        summary = "Physical transmission timed out without a valid acoustic ACK from a remote device."
    ),
    PASSED_TWO_DEVICE(
        badgeText = "TWO-DEVICE PASSED",
        summary = "Independent receiving device decoded the message and returned a valid CRC-16 acoustic ACK."
    )
}

data class DistanceTestEntry(
    val id: Long,
    val timestampFormatted: String,
    val distanceCm: Int,
    val tier: ValidationExecutionTier,
    val bandLabel: String,
    val measuredPeakFreqHz: Double,
    val signalLevelDbFs: Double,
    val snrDb: Double,
    val syncStatus: String,
    val crcPassed: Boolean,
    val ackVerifiedByRemote: Boolean,
    val latencyMs: Long,
    val outcomeLabel: String,
    val notes: String = ""
)

data class LengthStressItemResult(
    val payloadBytes: Int,
    val totalFrameBytes: Int,
    val samplePayloadPreview: String,
    val airDurationMs: Long,
    val measuredSnrDb: Double,
    val crcPassed: Boolean,
    val remoteAckVerified: Boolean,
    val outcomeLabel: String
)

data class LengthStressTestReport(
    val isRunning: Boolean = false,
    val tier: ValidationExecutionTier = ValidationExecutionTier.SIMULATED_DSP,
    val currentStepIndex: Int = 0,
    val totalSteps: Int = 5,
    val summaryStatus: String = "Not run yet",
    val items: List<LengthStressItemResult> = emptyList()
)

data class ReliabilityTrialEntry(
    val trialNumber: Int,
    val messageId: Int,
    val crcPassed: Boolean,
    val remoteAckVerified: Boolean,
    val measuredSnrDb: Double,
    val latencyMs: Long,
    val outcomeLabel: String
)

data class RepeatedReliabilityReport(
    val isRunning: Boolean = false,
    val tier: ValidationExecutionTier = ValidationExecutionTier.SIMULATED_DSP,
    val totalPlanned: Int = 5,
    val completedCount: Int = 0,
    val crcPassCount: Int = 0,
    val remoteAckSuccessCount: Int = 0,
    val failedOrTimeoutCount: Int = 0,
    val averageLatencyMs: Long = 0L,
    val averageSnrDb: Double = 0.0,
    val summaryStatus: String = "Not run yet",
    val trials: List<ReliabilityTrialEntry> = emptyList()
) {
    val successRatePercent: Int
        get() = if (completedCount <= 0) 0 else {
            val numerator = if (tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL) {
                remoteAckSuccessCount
            } else {
                crcPassCount
            }
            ((numerator * 100.0) / completedCount.toDouble()).toInt().coerceIn(0, 100)
        }
}

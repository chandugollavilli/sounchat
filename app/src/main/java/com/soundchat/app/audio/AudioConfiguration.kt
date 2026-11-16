package com.soundchat.app.audio

/**
 * Defines the operating frequency band for acoustic communication.
 * NEAR_ULTRASONIC is the primary 18.5 kHz – 20.0 kHz mode.
 * AUDIBLE_DEV_MODE is an optional fallback/development mode (2.4 kHz – 3.3 kHz)
 * for devices whose hardware filters out >18 kHz signals.
 */
enum class AcousticBandMode(
    val displayName: String,
    val description: String,
    val defaultBit0Hz: Double,
    val defaultBit1Hz: Double,
    val defaultSyncHz: Double,
    val passBandLowHz: Double,
    val passBandHighHz: Double
) {
    NEAR_ULTRASONIC(
        displayName = "Near-Ultrasonic (18.5–20.0 kHz)",
        description = "Silent/near-inaudible acoustic transmission using 48 kHz PCM sampling",
        defaultBit0Hz = 18750.0,
        defaultBit1Hz = 19350.0,
        defaultSyncHz = 19750.0,
        passBandLowHz = 18300.0,
        passBandHighHz = 20200.0
    ),
    AUDIBLE_DEV_MODE(
        displayName = "Audible Dev Mode (2.4–3.3 kHz)",
        description = "Audible fallback for testing on hardware without ultrasonic support",
        defaultBit0Hz = 2400.0,
        defaultBit1Hz = 3000.0,
        defaultSyncHz = 3350.0,
        passBandLowHz = 2000.0,
        passBandHighHz = 3700.0
    )
}

enum class TransmissionSpeedPreset(
    val label: String,
    val symbolDurationMs: Int,
    val guardIntervalMs: Int,
    val description: String
) {
    ROBUST(
        label = "Robust (45 ms/bit · ~22 bps)",
        symbolDurationMs = 45,
        guardIntervalMs = 5,
        description = "Best for longer distance (1–3m) or reverberant rooms"
    ),
    BALANCED(
        label = "Balanced (32 ms/bit · ~31 bps)",
        symbolDurationMs = 32,
        guardIntervalMs = 4,
        description = "Recommended balance of speed and ultrasonic SNR"
    ),
    FAST(
        label = "Fast (20 ms/bit · ~50 bps)",
        symbolDurationMs = 20,
        guardIntervalMs = 3,
        description = "Close-range high speed (<50 cm between phones)"
    )
}

data class AudioConfiguration(
    val sampleRate: Int = 48000,
    val bandMode: AcousticBandMode = AcousticBandMode.NEAR_ULTRASONIC,
    val freqBit0Hz: Double = bandMode.defaultBit0Hz,
    val freqBit1Hz: Double = bandMode.defaultBit1Hz,
    val freqSyncHz: Double = bandMode.defaultSyncHz,
    val speedPreset: TransmissionSpeedPreset = TransmissionSpeedPreset.BALANCED,
    val symbolDurationMs: Int = speedPreset.symbolDurationMs,
    val guardIntervalMs: Int = speedPreset.guardIntervalMs,
    val bandPassFilterEnabled: Boolean = true,
    val requireAcknowledgement: Boolean = true,
    val maxRetries: Int = 2,
    val ackTimeoutMs: Long = 5200L,
    val txAmplitude: Float = 0.92f,
    val minDetectionSnrDb: Double = 6.5
) {
    val samplesPerSymbol: Int
        get() = ((sampleRate * symbolDurationMs) / 1000.0).toInt().coerceAtLeast(64)

    val guardSamples: Int
        get() = ((sampleRate * guardIntervalMs) / 1000.0).toInt().coerceAtLeast(8)

    val bitRateBps: Double
        get() = 1000.0 / symbolDurationMs.toDouble()

    /**
     * Computes a realistic ACK wait timeout covering the 17-byte ACK frame duration
     * (136 bits + 3 sync symbols + lead/tail silence) plus half-duplex turnaround and HAL latency.
     */
    val effectiveAckTimeoutMs: Long
        get() {
            val ackTotalSymbols = (17 * 8) + 5
            val ackAirTimeMs = ackTotalSymbols.toLong() * symbolDurationMs.toLong()
            return maxOf(ackTimeoutMs, ackAirTimeMs + 2500L)
        }

    fun withBandMode(mode: AcousticBandMode): AudioConfiguration {
        return copy(
            bandMode = mode,
            freqBit0Hz = mode.defaultBit0Hz,
            freqBit1Hz = mode.defaultBit1Hz,
            freqSyncHz = mode.defaultSyncHz
        )
    }

    fun withSpeedPreset(preset: TransmissionSpeedPreset): AudioConfiguration {
        return copy(
            speedPreset = preset,
            symbolDurationMs = preset.symbolDurationMs,
            guardIntervalMs = preset.guardIntervalMs
        )
    }
}

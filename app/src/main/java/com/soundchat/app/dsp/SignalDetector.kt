package com.soundchat.app.dsp

import com.soundchat.app.audio.AudioConfiguration
import kotlin.math.log10
import kotlin.math.max

data class SignalDetectionSnapshot(
    val syncToneDetected: Boolean,
    val carrierActive: Boolean,
    val bit0PowerDb: Double,
    val bit1PowerDb: Double,
    val syncPowerDb: Double,
    val estimatedSnrDb: Double,
    val dominantFreqHz: Double
)

/**
 * Monitors incoming filtered audio samples for the presence of SoundChat's
 * synchronization carrier (`freqSyncHz`) or active BFSK symbol energy (`freqBit0Hz` / `freqBit1Hz`).
 */
class SignalDetector(private var config: AudioConfiguration) {

    fun updateConfiguration(newConfig: AudioConfiguration) {
        config = newConfig
    }

    fun evaluateWindow(
        filteredSamples: DoubleArray,
        offset: Int = 0,
        length: Int = filteredSamples.size - offset
    ): SignalDetectionSnapshot {
        if (length <= 16) {
            return SignalDetectionSnapshot(
                syncToneDetected = false,
                carrierActive = false,
                bit0PowerDb = -110.0,
                bit1PowerDb = -110.0,
                syncPowerDb = -110.0,
                estimatedSnrDb = 0.0,
                dominantFreqHz = 0.0
            )
        }

        val p0 = FrequencyAnalyzer.computeGoertzelPower(
            filteredSamples, offset, length, config.sampleRate, config.freqBit0Hz
        )
        val p1 = FrequencyAnalyzer.computeGoertzelPower(
            filteredSamples, offset, length, config.sampleRate, config.freqBit1Hz
        )
        val pSync = FrequencyAnalyzer.computeGoertzelPower(
            filteredSamples, offset, length, config.sampleRate, config.freqSyncHz
        )

        // Probe an out-of-channel reference bin inside the passband to estimate local noise floor
        val guardProbeFreq = (config.freqBit0Hz + config.freqBit1Hz) / 2.0
        val pNoise = FrequencyAnalyzer.computeGoertzelPower(
            filteredSamples, offset, length, config.sampleRate, guardProbeFreq
        ).coerceAtLeast(1e-13)

        val db0 = (10.0 * log10(p0)).coerceIn(-115.0, 6.0)
        val db1 = (10.0 * log10(p1)).coerceIn(-115.0, 6.0)
        val dbSync = (10.0 * log10(pSync)).coerceIn(-115.0, 6.0)
        val dbNoise = (10.0 * log10(pNoise)).coerceIn(-115.0, 0.0)

        val maxSignalDb = max(max(db0, db1), dbSync)
        val snrDb = (maxSignalDb - dbNoise).coerceAtLeast(0.0)

        val syncDetected = dbSync > -62.0 && (dbSync - max(db0, db1)) > 2.5
        val carrierActive = maxSignalDb > -65.0 && snrDb >= config.minDetectionSnrDb

        val dominantFreq = when {
            dbSync >= db0 && dbSync >= db1 -> config.freqSyncHz
            db1 >= db0 -> config.freqBit1Hz
            else -> config.freqBit0Hz
        }

        return SignalDetectionSnapshot(
            syncToneDetected = syncDetected,
            carrierActive = carrierActive,
            bit0PowerDb = db0,
            bit1PowerDb = db1,
            syncPowerDb = dbSync,
            estimatedSnrDb = snrDb,
            dominantFreqHz = dominantFreq
        )
    }
}

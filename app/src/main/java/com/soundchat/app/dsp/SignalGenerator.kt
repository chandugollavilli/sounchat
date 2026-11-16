package com.soundchat.app.dsp

import com.soundchat.app.audio.AudioConfiguration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generates phase-controlled, raised-cosine-shaped PCM audio waveforms for
 * near-ultrasonic and audible acoustic transmission.
 * Raised-cosine guard ramps eliminate broadband speaker clicks at symbol boundaries.
 */
object SignalGenerator {

    fun generateToneSymbol(
        frequencyHz: Double,
        sampleRate: Int,
        numSamples: Int,
        guardSamples: Int,
        amplitude: Float = 0.92f
    ): ShortArray {
        val out = ShortArray(numSamples)
        val safeGuard = guardSamples.coerceIn(0, numSamples / 3)
        val omega = 2.0 * PI * frequencyHz / sampleRate.toDouble()
        var phase = 0.0

        for (i in 0 until numSamples) {
            val env = when {
                safeGuard > 0 && i < safeGuard -> {
                    0.5 * (1.0 - cos(PI * i.toDouble() / safeGuard.toDouble()))
                }
                safeGuard > 0 && i >= numSamples - safeGuard -> {
                    val rem = numSamples - 1 - i
                    0.5 * (1.0 - cos(PI * rem.toDouble() / safeGuard.toDouble()))
                }
                else -> 1.0
            }
            val sample = (sin(phase) * amplitude * env * 32767.0).toInt().coerceIn(-32767, 32767)
            out[i] = sample.toShort()
            phase += omega
            if (phase >= 2.0 * PI) phase -= 2.0 * PI
        }
        return out
    }

    /**
     * Generates a complete PCM waveform for a sequence of frequencies, where each
     * entry in [symbolFrequenciesHz] is played for one symbol duration.
     */
    fun synthesizeSymbolSequence(
        symbolFrequenciesHz: DoubleArray,
        config: AudioConfiguration
    ): ShortArray {
        val symLen = config.samplesPerSymbol
        val guard = config.guardSamples
        val leadSilenceSamples = symLen / 2
        val tailSilenceSamples = symLen
        val totalSamples = leadSilenceSamples + (symbolFrequenciesHz.size * symLen) + tailSilenceSamples
        val pcm = ShortArray(totalSamples)

        var writePos = leadSilenceSamples
        for (freq in symbolFrequenciesHz) {
            val symbolPcm = generateToneSymbol(
                frequencyHz = freq,
                sampleRate = config.sampleRate,
                numSamples = symLen,
                guardSamples = guard,
                amplitude = config.txAmplitude
            )
            System.arraycopy(symbolPcm, 0, pcm, writePos, symLen)
            writePos += symLen
        }
        return pcm
    }
}

package com.soundchat.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cascaded 2nd-Order Biquad Band-Pass Filter (Direct Form II Transposed).
 * Isolates the near-ultrasonic carrier band (e.g., 18.3 kHz – 20.2 kHz) and
 * rejects ambient speech, HVAC hum, and low-frequency environmental noise.
 */
class BandPassFilter(
    sampleRate: Int,
    lowCutoffHz: Double,
    highCutoffHz: Double
) {
    private val stage1: BiquadSection
    private val stage2: BiquadSection

    init {
        val nyquist = sampleRate / 2.0
        val safeLow = lowCutoffHz.coerceIn(100.0, nyquist * 0.96)
        val safeHigh = highCutoffHz.coerceIn(safeLow + 100.0, nyquist * 0.99)
        val centerFreq = sqrt(safeLow * safeHigh)
        val bandwidth = (safeHigh - safeLow).coerceAtLeast(150.0)
        // Cascading two identical 2nd-order stages narrows -3 dB bandwidth by ~0.644x;
        // widen each stage's bandwidth by 1.4x so all carriers (18.75/19.35/19.75 kHz) sit inside the flat passband.
        val q = (centerFreq / (bandwidth * 1.4)).coerceIn(0.7, 25.0)

        stage1 = BiquadSection(sampleRate, centerFreq, q)
        stage2 = BiquadSection(sampleRate, centerFreq, q)
    }

    fun process(samples: ShortArray): DoubleArray {
        val out = DoubleArray(samples.size)
        for (i in samples.indices) {
            val normalized = samples[i] / 32768.0
            val s1 = stage1.step(normalized)
            out[i] = stage2.step(s1)
        }
        return out
    }

    fun processDoubles(samples: DoubleArray): DoubleArray {
        val out = DoubleArray(samples.size)
        for (i in samples.indices) {
            val s1 = stage1.step(samples[i])
            out[i] = stage2.step(s1)
        }
        return out
    }

    fun reset() {
        stage1.reset()
        stage2.reset()
    }

    private class BiquadSection(
        sampleRate: Int,
        centerFreqHz: Double,
        q: Double
    ) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        private var z1 = 0.0
        private var z2 = 0.0

        init {
            val omega = 2.0 * PI * centerFreqHz / sampleRate.toDouble()
            val sinW = sin(omega)
            val cosW = cos(omega)
            val alpha = sinW / (2.0 * q)

            val norm = 1.0 + alpha
            b0 = (alpha) / norm
            b1 = 0.0
            b2 = (-alpha) / norm
            a1 = (-2.0 * cosW) / norm
            a2 = (1.0 - alpha) / norm
        }

        fun step(x: Double): Double {
            val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2
            z2 = b2 * x - a2 * y
            return y
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }
    }
}

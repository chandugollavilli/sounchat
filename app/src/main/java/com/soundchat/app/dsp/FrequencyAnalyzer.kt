package com.soundchat.app.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

data class SpectrumAnalysisResult(
    val peakFrequencyHz: Double,
    val peakMagnitudeDb: Double,
    val rmsSignalDbFs: Double,
    val noiseFloorDb: Double,
    val snrDb: Double,
    val targetBit0PowerDb: Double,
    val targetBit1PowerDb: Double,
    val targetSyncPowerDb: Double,
    val spectrumBinsDb: FloatArray,
    val waveformNormalized: FloatArray
)

/**
 * Provides both high-precision Goertzel single-frequency power detection and
 * Radix-2 Cooley-Tukey FFT spectrum analysis with parabolic peak interpolation.
 */
object FrequencyAnalyzer {

    /**
     * Computes normalized Goertzel linear power at [targetFreqHz] over [samples].
     * Applying a Hanning window reduces spectral leakage between adjacent BFSK tones.
     */
    fun computeGoertzelPower(
        samples: DoubleArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
        sampleRate: Int,
        targetFreqHz: Double,
        applyWindow: Boolean = true
    ): Double {
        if (length <= 0 || offset < 0 || offset + length > samples.size) return 0.0

        val omega = (2.0 * PI * targetFreqHz) / sampleRate.toDouble()
        val coeff = 2.0 * cos(omega)
        var s0: Double
        var s1 = 0.0
        var s2 = 0.0

        for (i in 0 until length) {
            val w = if (applyWindow && length > 1) {
                0.5 * (1.0 - cos(2.0 * PI * i / (length - 1)))
            } else {
                1.0
            }
            val x = samples[offset + i] * w
            s0 = x + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
        val norm = (length.toDouble() * length.toDouble()) / 4.0
        return (power / norm.coerceAtLeast(1.0)).coerceAtLeast(1e-15)
    }

    fun computeGoertzelDb(
        samples: DoubleArray,
        offset: Int = 0,
        length: Int = samples.size - offset,
        sampleRate: Int,
        targetFreqHz: Double
    ): Double {
        val power = computeGoertzelPower(samples, offset, length, sampleRate, targetFreqHz)
        return (10.0 * log10(power)).coerceIn(-120.0, 6.0)
    }

    /**
     * Full spectrum & signal analysis on a PCM frame.
     */
    fun analyzeFrame(
        rawPcm: ShortArray,
        filteredSamples: DoubleArray,
        sampleRate: Int,
        freqBit0Hz: Double,
        freqBit1Hz: Double,
        freqSyncHz: Double,
        searchBandLowHz: Double = 1000.0,
        searchBandHighHz: Double = (sampleRate / 2.0) - 200.0
    ): SpectrumAnalysisResult {
        val n = highestPowerOfTwo(rawPcm.size.coerceAtMost(2048)).coerceAtLeast(256)
        val real = DoubleArray(n)
        val imag = DoubleArray(n)

        var sumSquares = 0.0
        for (i in 0 until minOf(rawPcm.size, n)) {
            val s = rawPcm[i] / 32768.0
            sumSquares += s * s
            val window = 0.5 * (1.0 - cos(2.0 * PI * i / (n - 1)))
            real[i] = filteredSamples.getOrElse(i) { s } * window
            imag[i] = 0.0
        }

        val rms = sqrt(sumSquares / n.toDouble()).coerceAtLeast(1e-7)
        val rmsDbFs = (20.0 * log10(rms)).coerceIn(-110.0, 0.0)

        fftInPlace(real, imag)

        val halfN = n / 2
        val binResolutionHz = sampleRate.toDouble() / n.toDouble()
        val magnitudesDb = DoubleArray(halfN)

        val minBin = (searchBandLowHz / binResolutionHz).toInt().coerceIn(1, halfN - 2)
        val maxBin = (searchBandHighHz / binResolutionHz).toInt().coerceIn(minBin + 1, halfN - 1)

        var peakBin = minBin
        var peakMagLinear = 1e-12
        val linearMagnitudes = DoubleArray(halfN)

        for (k in 0 until halfN) {
            val mag = (2.0 * sqrt(real[k] * real[k] + imag[k] * imag[k])) / n.toDouble()
            linearMagnitudes[k] = mag
            magnitudesDb[k] = (20.0 * log10(mag.coerceAtLeast(1e-7))).coerceIn(-120.0, 6.0)
            if (k in minBin..maxBin && mag > peakMagLinear) {
                peakMagLinear = mag
                peakBin = k
            }
        }

        // Parabolic interpolation around peakBin for sub-bin frequency precision
        val interpolatedBin = if (peakBin in 1 until (halfN - 1)) {
            val alpha = ln(linearMagnitudes[peakBin - 1].coerceAtLeast(1e-12))
            val beta = ln(linearMagnitudes[peakBin].coerceAtLeast(1e-12))
            val gamma = ln(linearMagnitudes[peakBin + 1].coerceAtLeast(1e-12))
            val denom = alpha - 2.0 * beta + gamma
            val delta = if (kotlin.math.abs(denom) > 1e-9) {
                0.5 * (alpha - gamma) / denom
            } else {
                0.0
            }
            peakBin + delta.coerceIn(-0.5, 0.5)
        } else {
            peakBin.toDouble()
        }

        val peakFreqHz = interpolatedBin * binResolutionHz
        val peakMagDb = magnitudesDb[peakBin]

        // Compute noise floor as median/mean of bins in the band excluding 3 bins around peak
        var noiseSum = 0.0
        var noiseCount = 0
        for (k in minBin..maxBin) {
            if (kotlin.math.abs(k - peakBin) > 2) {
                noiseSum += magnitudesDb[k]
                noiseCount++
            }
        }
        val noiseFloorDb = if (noiseCount > 0) (noiseSum / noiseCount) else -95.0
        val snrDb = (peakMagDb - noiseFloorDb).coerceAtLeast(0.0)

        val bit0Db = computeGoertzelDb(filteredSamples, 0, filteredSamples.size, sampleRate, freqBit0Hz)
        val bit1Db = computeGoertzelDb(filteredSamples, 0, filteredSamples.size, sampleRate, freqBit1Hz)
        val syncDb = computeGoertzelDb(filteredSamples, 0, filteredSamples.size, sampleRate, freqSyncHz)

        // Downsample spectrum to 64 visual bars for the UI oscilloscope
        val visualBinCount = 64
        val visualSpectrum = FloatArray(visualBinCount)
        for (b in 0 until visualBinCount) {
            val startIdx = (b * halfN) / visualBinCount
            val endIdx = (((b + 1) * halfN) / visualBinCount).coerceAtLeast(startIdx + 1)
            var maxInGroup = -110.0
            for (k in startIdx until minOf(endIdx, halfN)) {
                if (magnitudesDb[k] > maxInGroup) maxInGroup = magnitudesDb[k]
            }
            // Normalize -100 dB..0 dB into 0f..1f
            visualSpectrum[b] = ((maxInGroup + 95.0) / 95.0).toFloat().coerceIn(0.02f, 1f)
        }

        val waveformPoints = 96
        val waveform = FloatArray(waveformPoints)
        val step = (rawPcm.size.toFloat() / waveformPoints).coerceAtLeast(1f)
        for (i in 0 until waveformPoints) {
            val idx = (i * step).toInt().coerceIn(0, (rawPcm.size - 1).coerceAtLeast(0))
            waveform[i] = if (rawPcm.isNotEmpty()) (rawPcm[idx] / 32768f) else 0f
        }

        return SpectrumAnalysisResult(
            peakFrequencyHz = peakFreqHz,
            peakMagnitudeDb = peakMagDb,
            rmsSignalDbFs = rmsDbFs,
            noiseFloorDb = noiseFloorDb,
            snrDb = snrDb,
            targetBit0PowerDb = bit0Db,
            targetBit1PowerDb = bit1Db,
            targetSyncPowerDb = syncDb,
            spectrumBinsDb = visualSpectrum,
            waveformNormalized = waveform
        )
    }

    private fun highestPowerOfTwo(v: Int): Int {
        var p = 1
        while (p * 2 <= v) p *= 2
        return p
    }

    /**
     * In-place Radix-2 Cooley-Tukey FFT.
     */
    fun fftInPlace(real: DoubleArray, imag: DoubleArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * PI / len.toDouble()
            val wLenR = cos(angle)
            val wLenI = sin(angle)

            var i = 0
            while (i < n) {
                var wR = 1.0
                var wI = 0.0
                for (k in 0 until halfLen) {
                    val uR = real[i + k]
                    val uI = imag[i + k]
                    val vR = real[i + k + halfLen] * wR - imag[i + k + halfLen] * wI
                    val vI = real[i + k + halfLen] * wI + imag[i + k + halfLen] * wR
                    real[i + k] = uR + vR
                    imag[i + k] = uI + vI
                    real[i + k + halfLen] = uR - vR
                    imag[i + k + halfLen] = uI - vI
                    val nextWR = wR * wLenR - wI * wLenI
                    wI = wR * wLenI + wI * wLenR
                    wR = nextWR
                }
                i += len
            }
            len = len shl 1
        }
    }
}

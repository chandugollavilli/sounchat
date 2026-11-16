package com.soundchat.app.dsp

import com.soundchat.app.audio.AudioConfiguration

/**
 * Modular acoustic modulation interface so BFSK, MFSK, or DSSS can be swapped
 * without modifying the protocol or UI layers.
 */
interface AcousticModulator {
    fun modulateFrameBytes(frameBytes: ByteArray, config: AudioConfiguration): ShortArray
    fun bytesToBits(bytes: ByteArray): BooleanArray
}

/**
 * Robust Binary Frequency Shift Keying (BFSK) Modulator operating in the
 * 18.5 kHz – 20.0 kHz near-ultrasonic band (or configurable audible test band).
 *
 * Physical Layer Frame Structure:
 * - 2 Wakeup Sync Symbols at [AudioConfiguration.freqSyncHz]
 * - Framed Packet Bits (which start with Preamble `0xAA` and Sync Word `0xD3 0x91`)
 *   mapped to `freqBit0Hz` (0) and `freqBit1Hz` (1).
 * - 1 Postamble Sync Symbol at [AudioConfiguration.freqSyncHz]
 */
class BfskModulator : AcousticModulator {

    override fun bytesToBits(bytes: ByteArray): BooleanArray {
        val bits = BooleanArray(bytes.size * 8)
        var idx = 0
        for (b in bytes) {
            val unsigned = b.toInt() and 0xFF
            for (shift in 7 downTo 0) {
                bits[idx++] = ((unsigned shr shift) and 1) == 1
            }
        }
        return bits
    }

    override fun modulateFrameBytes(frameBytes: ByteArray, config: AudioConfiguration): ShortArray {
        val payloadBits = bytesToBits(frameBytes)
        val wakeupSyncSymbols = 2
        val tailSyncSymbols = 1
        val totalSymbols = wakeupSyncSymbols + payloadBits.size + tailSyncSymbols
        val freqs = DoubleArray(totalSymbols)

        var pos = 0
        repeat(wakeupSyncSymbols) {
            freqs[pos++] = config.freqSyncHz
        }
        for (bit in payloadBits) {
            freqs[pos++] = if (bit) config.freqBit1Hz else config.freqBit0Hz
        }
        repeat(tailSyncSymbols) {
            freqs[pos++] = config.freqSyncHz
        }

        return SignalGenerator.synthesizeSymbolSequence(freqs, config)
    }
}

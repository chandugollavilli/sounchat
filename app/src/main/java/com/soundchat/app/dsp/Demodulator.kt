package com.soundchat.app.dsp

import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.protocol.PacketHeader
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max

data class DemodulatedFrameCandidate(
    val rawBytes: ByteArray,
    val averageSnrDb: Double,
    val bit0MeanDb: Double,
    val bit1MeanDb: Double,
    val sampleOffset: Int
)

/**
 * Symbol-synchronized BFSK Demodulator using Goertzel matched-filter energy detection.
 *
 * Performs sub-symbol phase search (`samplesPerSymbol / 4` granularity) over the
 * 24-bit physical preamble + sync sequence (`0xAA 0xD3 0x91`) so asynchronous
 * physical devices lock accurately onto symbol boundaries before decoding the
 * header, variable-length UTF-8 payload, and CRC-16 checksum.
 */
class BfskDemodulator(private var config: AudioConfiguration) {

    // Expected 24-bit prefix at the start of every PacketEncoder frame:
    // Preamble (0xAA = 10101010) + SyncHigh (0xD3 = 11010011) + SyncLow (0x91 = 10010001)
    private val syncPrefixBits: BooleanArray = booleanArrayOf(
        // 0xAA
        true, false, true, false, true, false, true, false,
        // 0xD3
        true, true, false, true, false, false, true, true,
        // 0x91
        true, false, false, true, false, false, false, true
    )

    private var rollingSamples = DoubleArray(0)

    // Tracks an in-progress frame whose 12-byte header was already locked in rollingSamples
    // so subsequent AudioRecord chunks do not re-scan from sample 0 or truncate the preamble.
    private var lockedSyncOffset: Int = -1
    private var lockedRequiredSamples: Int = 0
    private var scannedUpToOffset: Int = 0

    // Dynamically cover the longest possible packet (12B header + 64B payload + 2B CRC = 624 bits)
    // plus 3 seconds of headroom so ROBUST (45ms/bit) frames are never prematurely truncated.
    private val maxRollingSamples: Int
        get() {
            val maxPacketBits = (PacketHeader.TOTAL_HEADER_BYTES + PacketHeader.MAX_PAYLOAD_BYTES + PacketHeader.CRC_BYTES) * 8
            return (maxPacketBits * config.samplesPerSymbol) + (config.sampleRate * 3)
        }

    fun updateConfiguration(newConfig: AudioConfiguration) {
        config = newConfig
        reset()
    }

    fun reset() {
        rollingSamples = DoubleArray(0)
        lockedSyncOffset = -1
        lockedRequiredSamples = 0
        scannedUpToOffset = 0
    }

    /**
     * Appends live filtered audio samples from [AudioReceiver] and attempts to extract
     * any complete acoustic packet frames present in the stream.
     */
    fun appendAndExtractFrames(filteredChunk: DoubleArray): List<DemodulatedFrameCandidate> {
        if (filteredChunk.isEmpty()) return emptyList()

        val combinedSize = rollingSamples.size + filteredChunk.size
        val nextBuffer = if (combinedSize > maxRollingSamples) {
            val dropCount = combinedSize - maxRollingSamples
            val keptFromOld = (rollingSamples.size - dropCount).coerceAtLeast(0)
            DoubleArray(keptFromOld + filteredChunk.size).also { dest ->
                if (keptFromOld > 0) {
                    System.arraycopy(rollingSamples, dropCount, dest, 0, keptFromOld)
                }
                System.arraycopy(filteredChunk, 0, dest, keptFromOld, filteredChunk.size)
            }.also {
                if (lockedSyncOffset >= 0) {
                    lockedSyncOffset -= dropCount
                    if (lockedSyncOffset < 0) {
                        lockedSyncOffset = -1
                        lockedRequiredSamples = 0
                    }
                }
                scannedUpToOffset = (scannedUpToOffset - dropCount).coerceAtLeast(0)
            }
        } else {
            DoubleArray(combinedSize).also { dest ->
                System.arraycopy(rollingSamples, 0, dest, 0, rollingSamples.size)
                System.arraycopy(filteredChunk, 0, dest, rollingSamples.size, filteredChunk.size)
            }
        }
        rollingSamples = nextBuffer

        val results = mutableListOf<DemodulatedFrameCandidate>()
        var lastConsumedSample = 0

        while (true) {
            // Fast path: if we already locked a valid 24-bit sync + 12-byte header, wait until all samples arrive
            if (lockedSyncOffset >= 0) {
                if (rollingSamples.size < lockedSyncOffset + lockedRequiredSamples) {
                    break
                }
                val totalFrameBytes = lockedRequiredSamples / (8 * config.samplesPerSymbol)
                val fullFrame = decodeFullFrameWithMetrics(
                    samples = rollingSamples,
                    startSample = lockedSyncOffset,
                    symLen = config.samplesPerSymbol,
                    totalBytes = totalFrameBytes
                )
                results.add(fullFrame)
                val nextCursor = lockedSyncOffset + lockedRequiredSamples
                lastConsumedSample = nextCursor
                scannedUpToOffset = nextCursor
                lockedSyncOffset = -1
                lockedRequiredSamples = 0
                continue
            }

            val searchFrom = maxOf(lastConsumedSample, scannedUpToOffset)
            val outcome = scanForSingleFrame(rollingSamples, searchFrom)
            if (outcome.candidate != null) {
                results.add(outcome.candidate)
                lastConsumedSample = outcome.nextCursor
                scannedUpToOffset = outcome.nextCursor
            } else if (outcome.pendingStartOffset >= 0) {
                lockedSyncOffset = outcome.pendingStartOffset
                lockedRequiredSamples = outcome.pendingRequiredSamples
                break
            } else {
                scannedUpToOffset = outcome.scannedToOffset
                break
            }
        }

        if (lastConsumedSample > 0 && lastConsumedSample <= rollingSamples.size) {
            rollingSamples = rollingSamples.copyOfRange(lastConsumedSample, rollingSamples.size)
            if (lockedSyncOffset >= 0) {
                lockedSyncOffset -= lastConsumedSample
            }
            scannedUpToOffset = (scannedUpToOffset - lastConsumedSample).coerceAtLeast(0)
        } else if (lockedSyncOffset < 0) {
            // Only trim idle noise history when no frame header is actively locked.
            // Keep enough overlap for a full header search window (~1.6s).
            val minOverlapSamples = ((PacketHeader.TOTAL_HEADER_BYTES + 2) * 8 * config.samplesPerSymbol) + config.sampleRate
            if (rollingSamples.size > minOverlapSamples * 2) {
                val drop = rollingSamples.size - minOverlapSamples
                rollingSamples = rollingSamples.copyOfRange(drop, rollingSamples.size)
                scannedUpToOffset = (scannedUpToOffset - drop).coerceAtLeast(0)
            }
        }

        return results
    }

    /**
     * Demodulates a complete standalone PCM buffer (used in unit tests, Phase 1/2 diagnostics,
     * and local loopback verification).
     */
    fun demodulatePcmBuffer(rawPcm: ShortArray): List<DemodulatedFrameCandidate> {
        val samples = if (config.bandPassFilterEnabled) {
            val filter = BandPassFilter(
                sampleRate = config.sampleRate,
                lowCutoffHz = config.bandMode.passBandLowHz,
                highCutoffHz = config.bandMode.passBandHighHz
            )
            filter.process(rawPcm)
        } else {
            DoubleArray(rawPcm.size) { i -> rawPcm[i] / 32768.0 }
        }

        val found = mutableListOf<DemodulatedFrameCandidate>()
        var cursor = 0
        while (cursor < samples.size) {
            val outcome = scanForSingleFrame(samples, cursor)
            if (outcome.candidate != null) {
                found.add(outcome.candidate)
                cursor = outcome.nextCursor
            } else {
                break
            }
        }
        return found
    }

    private data class ScanOutcome(
        val candidate: DemodulatedFrameCandidate? = null,
        val nextCursor: Int = 0,
        val pendingStartOffset: Int = -1,
        val pendingRequiredSamples: Int = 0,
        val scannedToOffset: Int = 0
    )

    /**
     * Scans [samples] starting from [startOffset] for a valid 24-bit sync prefix,
     * fine-tunes symbol timing, reads the fixed header to learn `payloadLength`,
     * and extracts the full frame if all samples have arrived.
     */
    private fun scanForSingleFrame(
        samples: DoubleArray,
        startOffset: Int
    ): ScanOutcome {
        val symLen = config.samplesPerSymbol
        val minConfirmBits = (PacketHeader.TOTAL_HEADER_BYTES + 2) * 8 // 112 bits (0-byte payload + 2B CRC)

        if (samples.size - startOffset < minConfirmBits * symLen) {
            return ScanOutcome(scannedToOffset = startOffset)
        }

        val step = (symLen / 4).coerceAtLeast(16)
        val maxSearchSample = samples.size - (minConfirmBits * symLen)

        var candidateOffset = startOffset
        while (candidateOffset <= maxSearchSample) {
            val matchScore = scoreSyncPrefix(samples, candidateOffset, symLen)
            if (matchScore > 0.0) {
                // Refine alignment in a +/- symLen/2 window around candidateOffset
                var bestOffset = candidateOffset
                var bestScore = matchScore
                val fineStep = (symLen / 8).coerceAtLeast(8)
                val fineStart = (candidateOffset - symLen / 2).coerceAtLeast(startOffset)
                val fineEnd = (candidateOffset + symLen / 2).coerceAtMost(maxSearchSample)

                var probe = fineStart
                while (probe <= fineEnd) {
                    val s = scoreSyncPrefix(samples, probe, symLen)
                    if (s > bestScore) {
                        bestScore = s
                        bestOffset = probe
                    }
                    probe += fineStep
                }

                // Decode the 12-byte header first
                val headerBytes = decodeBytesAt(samples, bestOffset, symLen, PacketHeader.TOTAL_HEADER_BYTES)
                val protoVersion = headerBytes[3].toInt() and 0xFF
                val packetTypeValid = com.soundchat.app.protocol.AcousticPacketType.fromCode(headerBytes[4].toInt() and 0xFF) != null
                val payloadLen = headerBytes[PacketHeader.PAYLOAD_LENGTH_INDEX].toInt() and 0xFF

                if (protoVersion == PacketHeader.PROTOCOL_VERSION &&
                    packetTypeValid &&
                    payloadLen <= PacketHeader.MAX_PAYLOAD_BYTES
                ) {
                    val totalFrameBytes = PacketHeader.TOTAL_HEADER_BYTES + payloadLen + PacketHeader.CRC_BYTES
                    val requiredSamples = totalFrameBytes * 8 * symLen

                    if (bestOffset + requiredSamples > samples.size) {
                        // Frame is currently arriving; lock bestOffset and wait for remaining samples
                        return ScanOutcome(
                            pendingStartOffset = bestOffset,
                            pendingRequiredSamples = requiredSamples,
                            scannedToOffset = bestOffset
                        )
                    }

                    val fullFrameWithMetrics = decodeFullFrameWithMetrics(
                        samples = samples,
                        startSample = bestOffset,
                        symLen = symLen,
                        totalBytes = totalFrameBytes
                    )
                    val nextCursor = bestOffset + requiredSamples
                    return ScanOutcome(
                        candidate = fullFrameWithMetrics,
                        nextCursor = nextCursor,
                        scannedToOffset = nextCursor
                    )
                }
            }
            candidateOffset += step
        }

        return ScanOutcome(scannedToOffset = candidateOffset)
    }

    /**
     * Checks if all 24 bits of [syncPrefixBits] match at [offset] and returns the
     * average log-likelihood separation score (> 0 when all 24 bits match cleanly).
     */
    private fun scoreSyncPrefix(samples: DoubleArray, offset: Int, symLen: Int): Double {
        var totalLogRatio = 0.0
        for (bitIdx in syncPrefixBits.indices) {
            val bitStart = offset + bitIdx * symLen
            val p0 = FrequencyAnalyzer.computeGoertzelPower(
                samples, bitStart, symLen, config.sampleRate, config.freqBit0Hz
            )
            val p1 = FrequencyAnalyzer.computeGoertzelPower(
                samples, bitStart, symLen, config.sampleRate, config.freqBit1Hz
            )
            val maxP = max(p0, p1)
            if (maxP < 1e-10) return -1.0 // Silence

            val detectedBit = p1 > p0
            if (detectedBit != syncPrefixBits[bitIdx]) {
                return -1.0
            }
            val ratio = if (detectedBit) {
                ln((p1 + 1e-12) / (p0 + 1e-12))
            } else {
                ln((p0 + 1e-12) / (p1 + 1e-12))
            }
            if (ratio < 0.25) return -1.0
            totalLogRatio += ratio
        }
        return totalLogRatio / syncPrefixBits.size.toDouble()
    }

    private fun decodeBytesAt(
        samples: DoubleArray,
        startSample: Int,
        symLen: Int,
        byteCount: Int
    ): ByteArray {
        val out = ByteArray(byteCount)
        var bitCursor = 0
        for (b in 0 until byteCount) {
            var value = 0
            for (bit in 7 downTo 0) {
                val samplePos = startSample + bitCursor * symLen
                val p0 = FrequencyAnalyzer.computeGoertzelPower(
                    samples, samplePos, symLen, config.sampleRate, config.freqBit0Hz
                )
                val p1 = FrequencyAnalyzer.computeGoertzelPower(
                    samples, samplePos, symLen, config.sampleRate, config.freqBit1Hz
                )
                if (p1 > p0) {
                    value = value or (1 shl bit)
                }
                bitCursor++
            }
            out[b] = value.toByte()
        }
        return out
    }

    private fun decodeFullFrameWithMetrics(
        samples: DoubleArray,
        startSample: Int,
        symLen: Int,
        totalBytes: Int
    ): DemodulatedFrameCandidate {
        val out = ByteArray(totalBytes)
        var bitCursor = 0
        var sumSnrDb = 0.0
        var sumBit0Db = 0.0
        var sumBit1Db = 0.0
        val totalBits = totalBytes * 8

        for (b in 0 until totalBytes) {
            var value = 0
            for (bit in 7 downTo 0) {
                val samplePos = startSample + bitCursor * symLen
                val p0 = FrequencyAnalyzer.computeGoertzelPower(
                    samples, samplePos, symLen, config.sampleRate, config.freqBit0Hz
                )
                val p1 = FrequencyAnalyzer.computeGoertzelPower(
                    samples, samplePos, symLen, config.sampleRate, config.freqBit1Hz
                )
                if (p1 > p0) {
                    value = value or (1 shl bit)
                }
                val db0 = (10.0 * log10(p0)).coerceIn(-110.0, 6.0)
                val db1 = (10.0 * log10(p1)).coerceIn(-110.0, 6.0)
                sumBit0Db += db0
                sumBit1Db += db1
                sumSnrDb += abs(db1 - db0)
                bitCursor++
            }
            out[b] = value.toByte()
        }

        return DemodulatedFrameCandidate(
            rawBytes = out,
            averageSnrDb = sumSnrDb / totalBits.toDouble(),
            bit0MeanDb = sumBit0Db / totalBits.toDouble(),
            bit1MeanDb = sumBit1Db / totalBits.toDouble(),
            sampleOffset = startSample
        )
    }

    fun bitsToBytes(bits: BooleanArray): ByteArray {
        val byteCount = bits.size / 8
        val out = ByteArray(byteCount)
        for (i in 0 until byteCount) {
            var b = 0
            for (bit in 0 until 8) {
                if (bits[i * 8 + bit]) {
                    b = b or (1 shl (7 - bit))
                }
            }
            out[i] = b.toByte()
        }
        return out
    }
}

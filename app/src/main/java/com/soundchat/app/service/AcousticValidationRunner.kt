package com.soundchat.app.service

import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.dsp.BandPassFilter
import com.soundchat.app.dsp.BfskDemodulator
import com.soundchat.app.dsp.BfskModulator
import com.soundchat.app.protocol.PacketDecodeResult
import com.soundchat.app.protocol.PacketDecoder
import com.soundchat.app.protocol.PacketEncoder

data class SimulatedFrameCheckResult(
    val payloadBytes: Int,
    val totalFrameBytes: Int,
    val airDurationMs: Long,
    val measuredSnrDb: Double,
    val crcPassed: Boolean,
    val decodedMatches: Boolean
)

/**
 * Provides Phase 7 validation utilities:
 * - Deterministic [resolvePhysicalValidationStatus] that never auto-passes two-device transfer.
 * - Representative payload tiers (2 B -> 64 B protocol max) for message-length stress testing.
 * - In-memory Tier A DSP pipeline verification (strictly separated from physical air transfer).
 */
object AcousticValidationRunner {

    val STRESS_PAYLOADS: List<String> = listOf(
        "HI",                                                                 // 2 B
        "HELLO WORLD",                                                        // 11 B
        "SOUNDCHAT_STRESS_28B_CHECK!!",                                       // 28 B
        "ULTRASONIC_BFSK_48B_PAYLOAD_TIMING_VALIDATION_01",                   // 48 B
        "SOUNDCHAT_MAX_64_BYTE_PAYLOAD_CRC16_TIMING_AND_DRIFT_CHECK_12345"    // 64 B (Max)
    )

    fun resolvePhysicalValidationStatus(
        simulatedDspVerified: Boolean,
        localLoopbackVerified: Boolean,
        physicalTwoDeviceRxVerified: Boolean,
        physicalTwoDeviceAckVerified: Boolean,
        inProgress: Boolean = false,
        failedTwoDeviceAck: Boolean = false
    ): PhysicalValidationStatus {
        return when {
            // Two-device transfer is ONLY passed when a real remote peer decoded and returned a valid ACK
            physicalTwoDeviceAckVerified -> PhysicalValidationStatus.PASSED_TWO_DEVICE
            inProgress -> PhysicalValidationStatus.IN_PROGRESS
            failedTwoDeviceAck -> PhysicalValidationStatus.FAILED_NO_REMOTE_ACK
            physicalTwoDeviceRxVerified -> PhysicalValidationStatus.PARTIAL_RX_ONLY
            localLoopbackVerified -> PhysicalValidationStatus.LOOPBACK_ONLY
            simulatedDspVerified -> PhysicalValidationStatus.SIMULATED_ONLY
            else -> PhysicalValidationStatus.NOT_RUN
        }
    }

    /**
     * Executes a Tier A in-memory DSP verification across PacketEncoder -> BfskModulator ->
     * BandPassFilter -> chunked BfskDemodulator -> PacketDecoder (CRC-16).
     * Never interacts with AudioTrack/AudioRecord and never sets physical validation flags.
     */
    fun runSimulatedFrameVerification(
        payload: String,
        messageId: Int,
        senderId: Int,
        receiverId: Int,
        config: AudioConfiguration
    ): SimulatedFrameCheckResult {
        val modulator = BfskModulator()
        val demodulator = BfskDemodulator(config)
        val (_, rawFrame) = PacketEncoder.encodeTextMessage(
            senderId = senderId,
            receiverId = receiverId,
            messageId = messageId,
            text = payload
        )
        val pcm = modulator.modulateFrameBytes(rawFrame, config)
        val airDurationMs = ((pcm.size.toDouble() / config.sampleRate.toDouble()) * 1000.0).toLong()

        // Stream in 2048-sample chunks with realistic asynchronous offset (e.g. 512 samples silence)
        val paddedStream = ShortArray(512 + pcm.size + 1024)
        System.arraycopy(pcm, 0, paddedStream, 512, pcm.size)

        val filter = if (config.bandPassFilterEnabled) {
            BandPassFilter(
                sampleRate = config.sampleRate,
                lowCutoffHz = config.bandMode.passBandLowHz,
                highCutoffHz = config.bandMode.passBandHighHz
            )
        } else {
            null
        }

        var matchedCandidateSnr = 0.0
        var crcPassed = false
        var textMatches = false

        var offset = 0
        val chunkSize = 2048
        while (offset < paddedStream.size) {
            val len = minOf(chunkSize, paddedStream.size - offset)
            val chunk = paddedStream.copyOfRange(offset, offset + len)
            val filtered = filter?.process(chunk)
                ?: DoubleArray(chunk.size) { idx -> chunk[idx] / 32768.0 }

            val extracted = demodulator.appendAndExtractFrames(filtered)
            for (cand in extracted) {
                matchedCandidateSnr = cand.averageSnrDb
                val decoded = PacketDecoder.decodeFrame(cand.rawBytes)
                if (decoded is PacketDecodeResult.Success) {
                    crcPassed = true
                    textMatches = decoded.packet.payloadText == payload
                }
            }
            offset += len
        }

        return SimulatedFrameCheckResult(
            payloadBytes = payload.toByteArray(Charsets.UTF_8).size,
            totalFrameBytes = rawFrame.size,
            airDurationMs = airDurationMs,
            measuredSnrDb = matchedCandidateSnr,
            crcPassed = crcPassed,
            decodedMatches = textMatches
        )
    }
}

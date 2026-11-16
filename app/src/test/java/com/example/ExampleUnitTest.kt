package com.example

import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.discovery.AcousticDiscovery
import com.soundchat.app.discovery.AcousticHandshake
import com.soundchat.app.dsp.BandPassFilter
import com.soundchat.app.dsp.BfskDemodulator
import com.soundchat.app.dsp.BfskModulator
import com.soundchat.app.dsp.FrequencyAnalyzer
import com.soundchat.app.dsp.SignalGenerator
import com.soundchat.app.protocol.AcknowledgementManager
import com.soundchat.app.protocol.CRC16
import com.soundchat.app.protocol.PacketDecodeResult
import com.soundchat.app.protocol.PacketDecoder
import com.soundchat.app.protocol.PacketEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `test CRC16 computation and verification`() {
        val sample = "HI".toByteArray(Charsets.UTF_8)
        val crc = CRC16.compute(sample)
        val withCrc = sample + byteArrayOf(
            ((crc shr 8) and 0xFF).toByte(),
            (crc and 0xFF).toByte()
        )
        assertTrue("Valid CRC should verify", CRC16.verify(withCrc))

        // Flip 1 bit to simulate acoustic corruption
        withCrc[0] = (withCrc[0].toInt() xor 0x04).toByte()
        assertFalse("Corrupted payload must fail CRC16", CRC16.verify(withCrc))
    }

    @Test
    fun `test packet encoding and decoding for HI, HELLO WORLD, and special characters`() {
        val payloads = listOf(
            "HI",
            "HELLO WORLD",
            "SoundChat 2026 #99! @$"
        )

        for ((idx, text) in payloads.withIndex()) {
            val (encodedPacket, rawBytes) = PacketEncoder.encodeTextMessage(
                senderId = 0xA1B2,
                receiverId = 0xC3D4,
                messageId = 100 + idx,
                text = text
            )

            val decodeResult = PacketDecoder.decodeFrame(rawBytes)
            assertTrue("Expected Success for '$text'", decodeResult is PacketDecodeResult.Success)
            val decoded = (decodeResult as PacketDecodeResult.Success).packet
            assertEquals(text, decoded.payloadText)
            assertEquals(0xA1B2, decoded.header.senderId)
            assertEquals(0xC3D4, decoded.header.receiverId)
            assertEquals(100 + idx, decoded.header.messageId)
            assertEquals(encodedPacket.crc16, decoded.crc16)
        }
    }

    @Test
    fun `test packet corruption detection via CRC16 mismatch`() {
        val (_, rawBytes) = PacketEncoder.encodeTextMessage(
            senderId = 0x1234,
            receiverId = 0x5678,
            messageId = 7,
            text = "HELLO WORLD"
        )
        // Corrupt a payload byte
        rawBytes[14] = (rawBytes[14].toInt() xor 0x10).toByte()
        val result = PacketDecoder.decodeFrame(rawBytes)
        assertTrue("Corrupted frame must return CrcMismatch", result is PacketDecodeResult.CrcMismatch)
    }

    @Test
    fun `test full near-ultrasonic BFSK modulation and demodulation of HI and HELLO WORLD`() {
        val config = AudioConfiguration(
            sampleRate = 48000,
            bandMode = AcousticBandMode.NEAR_ULTRASONIC
        )
        val modulator = BfskModulator()
        val demodulator = BfskDemodulator(config)

        for (msg in listOf("HI", "HELLO WORLD", "12345 !@#")) {
            val (_, rawFrame) = PacketEncoder.encodeTextMessage(
                senderId = 0x4A2F,
                receiverId = 0xFFFF,
                messageId = 42,
                text = msg
            )

            // Modulate to 48 kHz 16-bit PCM ultrasonic audio (18,750 Hz / 19,350 Hz)
            val pcmSamples = modulator.modulateFrameBytes(rawFrame, config)

            // Add arbitrary asynchronous leading silence (e.g. 337 samples) to simulate two unsynchronized phones
            val asyncPcm = ShortArray(337 + pcmSamples.size + 400)
            System.arraycopy(pcmSamples, 0, asyncPcm, 337, pcmSamples.size)

            val candidates = demodulator.demodulatePcmBuffer(asyncPcm)
            assertTrue("Demodulator should extract at least 1 candidate for '$msg'", candidates.isNotEmpty())

            val decodeResult = PacketDecoder.decodeFrame(candidates.first().rawBytes)
            assertTrue("Decoded frame must pass CRC-16 for '$msg'", decodeResult is PacketDecodeResult.Success)
            val reconstructed = (decodeResult as PacketDecodeResult.Success).packet.payloadText
            assertEquals(msg, reconstructed)
        }
    }

    @Test
    fun `test Goertzel and BandPassFilter ultrasonic carrier discrimination`() {
        val sampleRate = 48000
        val filter = BandPassFilter(sampleRate, 18300.0, 20200.0)
        val tone18750 = SignalGenerator.generateToneSymbol(
            frequencyHz = 18750.0,
            sampleRate = sampleRate,
            numSamples = 1536,
            guardSamples = 128
        )
        val filtered = filter.process(tone18750)
        val p18750 = FrequencyAnalyzer.computeGoertzelPower(filtered, 0, filtered.size, sampleRate, 18750.0)
        val p19350 = FrequencyAnalyzer.computeGoertzelPower(filtered, 0, filtered.size, sampleRate, 19350.0)

        assertTrue(
            "Goertzel power at 18,750 Hz ($p18750) must significantly exceed 19,350 Hz ($p19350)",
            p18750 > p19350 * 8.0
        )
    }

    @Test
    fun `test duplicate packet detection and acknowledgement matching`() {
        val ackManager = AcknowledgementManager()
        assertTrue(ackManager.recordIncomingAndCheckIsNew(0x1111, 50))
        assertFalse("Second arrival of same (sender, msgId) must be flagged as duplicate", ackManager.recordIncomingAndCheckIsNew(0x1111, 50))
        assertTrue("Different msgId from same sender must be accepted", ackManager.recordIncomingAndCheckIsNew(0x1111, 51))

        val deferred = ackManager.registerPendingAck(99)
        assertFalse(deferred.isCompleted)
        assertTrue(ackManager.onAckReceived(99))
        assertTrue(deferred.isCompleted)
    }

    @Test
    fun `test acoustic discovery beacon and pairing handshake encoding`() {
        val (_, beaconBytes) = AcousticDiscovery.createBeaconFrame(
            localDeviceId = 0x2B3C,
            localDeviceName = "Pixel8",
            sequenceNumber = 1,
            isUltrasonic = true
        )
        val decodedBeacon = (PacketDecoder.decodeFrame(beaconBytes) as PacketDecodeResult.Success).packet
        val peer = AcousticDiscovery.parseBeaconPacket(decodedBeacon, -35.0, 15.0, 19350.0)
        assertNotNull(peer)
        assertEquals(0x2B3C, peer?.deviceId)
        assertEquals("Pixel8", peer?.deviceName)

        val sessionId = AcousticHandshake.generateSessionId(0x2B3C, 0x7F8E)
        val (_, reqBytes) = AcousticHandshake.createPairRequestFrame(
            localDeviceId = 0x2B3C,
            localDeviceName = "Pixel8",
            targetDeviceId = 0x7F8E,
            messageId = 2,
            proposedSessionId = sessionId
        )
        val decodedReq = (PacketDecoder.decodeFrame(reqBytes) as PacketDecodeResult.Success).packet
        val parsedHandshake = AcousticHandshake.parseHandshakePacket(decodedReq)
        assertEquals("Pixel8", parsedHandshake.senderName)
        assertEquals(sessionId, parsedHandshake.sessionId)
    }

    @Test
    fun `test CRC16 CCITT-FALSE standard test vectors`() {
        // Standard IEEE/CCITT-FALSE vector: ASCII "123456789" -> 0x29B1 (poly=0x1021, init=0xFFFF)
        assertEquals(0x29B1, CRC16.compute("123456789".toByteArray(Charsets.US_ASCII)))
        assertEquals(0xFFFF, CRC16.compute(ByteArray(0)))
        assertEquals("29B1", CRC16.formatHex(CRC16.compute("123456789".toByteArray(Charsets.US_ASCII))))
    }

    @Test
    fun `test streaming 2048-sample chunked demodulation does not truncate long frames`() {
        // Exercises the real-time AudioReceiver -> BfskDemodulator.appendAndExtractFrames() path
        // for frames exceeding 4 seconds ("HI" at ROBUST 45ms/bit = 5.76s, "HELLO WORLD" at BALANCED 32ms/bit = 6.4s).
        val configs = listOf(
            AudioConfiguration(sampleRate = 48000, bandMode = AcousticBandMode.NEAR_ULTRASONIC, symbolDurationMs = 32) to "HELLO WORLD",
            AudioConfiguration(sampleRate = 48000, bandMode = AcousticBandMode.NEAR_ULTRASONIC, symbolDurationMs = 45) to "HI"
        )

        for ((cfg, msg) in configs) {
            val modulator = BfskModulator()
            val demodulator = BfskDemodulator(cfg)
            val (_, rawFrame) = PacketEncoder.encodeTextMessage(
                senderId = 0x55AA,
                receiverId = 0x9988,
                messageId = 313,
                text = msg
            )
            val framePcm = modulator.modulateFrameBytes(rawFrame, cfg)
            // Prepend 1.2s of silence and append 0.6s of trailing silence, streamed in 2048-sample AudioRecord chunks
            val leadingSilence = ShortArray(48000 + 9600)
            val trailingSilence = ShortArray(28800)
            val fullStream = leadingSilence + framePcm + trailingSilence

            val filter = BandPassFilter(
                sampleRate = cfg.sampleRate,
                lowCutoffHz = cfg.bandMode.passBandLowHz,
                highCutoffHz = cfg.bandMode.passBandHighHz
            )
            val extracted = mutableListOf<ByteArray>()
            var offset = 0
            val chunkSize = 2048
            while (offset < fullStream.size) {
                val len = minOf(chunkSize, fullStream.size - offset)
                val chunk = fullStream.copyOfRange(offset, offset + len)
                val filtered = filter.process(chunk)
                demodulator.appendAndExtractFrames(filtered).forEach { cand ->
                    extracted.add(cand.rawBytes)
                }
                offset += len
            }

            assertTrue("Expected extracted frame for streaming '$msg' (${cfg.symbolDurationMs}ms/bit)", extracted.isNotEmpty())
            val decoded = PacketDecoder.decodeFrame(extracted.first())
            assertTrue("Streaming decoded frame must pass CRC-16 for '$msg'", decoded is PacketDecodeResult.Success)
            assertEquals(msg, (decoded as PacketDecodeResult.Success).packet.payloadText)
        }
    }

    @Test
    fun `test ACK packet construction, decoding, and effectiveAckTimeoutMs sizing`() {
        val (ackPacket, ackRaw) = PacketEncoder.encodeAck(
            senderId = 0x2222,
            receiverId = 0x1111,
            acknowledgedMessageId = 777
        )
        assertEquals(17, ackRaw.size)
        val result = PacketDecoder.decodeFrame(ackRaw)
        assertTrue("ACK frame must decode cleanly", result is PacketDecodeResult.Success)
        val decodedAck = (result as PacketDecodeResult.Success).packet
        assertEquals(777, decodedAck.header.messageId)
        assertEquals(ackPacket.crc16, decodedAck.crc16)

        val balanced = AudioConfiguration(symbolDurationMs = 32)
        val robust = AudioConfiguration(symbolDurationMs = 45)
        assertTrue(
            "Balanced ACK timeout (${balanced.effectiveAckTimeoutMs}ms) must cover ~4.5s acoustic ACK transit",
            balanced.effectiveAckTimeoutMs >= 6500L
        )
        assertTrue(
            "Robust ACK timeout (${robust.effectiveAckTimeoutMs}ms) must cover ~6.3s acoustic ACK transit",
            robust.effectiveAckTimeoutMs >= 8000L
        )
    }

    @Test
    fun `test message-length stress payloads from 2B to 64B max through simulated DSP pipeline`() {
        val cfg = AudioConfiguration(
            sampleRate = 48000,
            bandMode = AcousticBandMode.NEAR_ULTRASONIC,
            symbolDurationMs = 32
        )
        val expectedLengths = listOf(2, 11, 28, 48, 64)
        assertEquals(expectedLengths.size, com.soundchat.app.service.AcousticValidationRunner.STRESS_PAYLOADS.size)

        com.soundchat.app.service.AcousticValidationRunner.STRESS_PAYLOADS.forEachIndexed { idx, payload ->
            val result = com.soundchat.app.service.AcousticValidationRunner.runSimulatedFrameVerification(
                payload = payload,
                messageId = 500 + idx,
                senderId = 0x1A2B,
                receiverId = 0x3C4D,
                config = cfg
            )
            assertEquals("Expected exact UTF-8 byte length", expectedLengths[idx], result.payloadBytes)
            assertTrue("CRC-16 must verify for ${result.payloadBytes}B payload", result.crcPassed)
            assertTrue("Decoded UTF-8 text must match for ${result.payloadBytes}B payload", result.decodedMatches)
        }
    }

    @Test
    fun `test PhysicalValidationStatus never marks two-device transfer passed automatically`() {
        val runner = com.soundchat.app.service.AcousticValidationRunner
        val notRun = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = false,
            localLoopbackVerified = false,
            physicalTwoDeviceRxVerified = false,
            physicalTwoDeviceAckVerified = false
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.NOT_RUN, notRun)

        val simOnly = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = true,
            localLoopbackVerified = false,
            physicalTwoDeviceRxVerified = false,
            physicalTwoDeviceAckVerified = false
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.SIMULATED_ONLY, simOnly)

        val loopbackOnly = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = true,
            localLoopbackVerified = true,
            physicalTwoDeviceRxVerified = false,
            physicalTwoDeviceAckVerified = false
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.LOOPBACK_ONLY, loopbackOnly)

        val partialRx = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = true,
            localLoopbackVerified = true,
            physicalTwoDeviceRxVerified = true,
            physicalTwoDeviceAckVerified = false
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.PARTIAL_RX_ONLY, partialRx)

        val failedTimeout = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = true,
            localLoopbackVerified = true,
            physicalTwoDeviceRxVerified = false,
            physicalTwoDeviceAckVerified = false,
            failedTwoDeviceAck = true
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.FAILED_NO_REMOTE_ACK, failedTimeout)

        val passed = runner.resolvePhysicalValidationStatus(
            simulatedDspVerified = true,
            localLoopbackVerified = true,
            physicalTwoDeviceRxVerified = false,
            physicalTwoDeviceAckVerified = true
        )
        assertEquals(com.soundchat.app.service.PhysicalValidationStatus.PASSED_TWO_DEVICE, passed)
    }
}

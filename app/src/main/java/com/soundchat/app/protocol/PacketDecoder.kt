package com.soundchat.app.protocol

sealed class PacketDecodeResult {
    data class Success(
        val packet: AcousticPacket,
        val rawFrameBytes: ByteArray
    ) : PacketDecodeResult()

    data class CrcMismatch(
        val expectedCrc: Int,
        val receivedCrc: Int,
        val partialHeader: PacketHeader?,
        val corruptedPayloadPreview: String
    ) : PacketDecodeResult()

    data class MalformedFrame(
        val reason: String
    ) : PacketDecodeResult()
}

/**
 * Decodes and validates binary acoustic frames into [AcousticPacket] instances.
 */
object PacketDecoder {

    fun decodeFrame(frameBytes: ByteArray): PacketDecodeResult {
        val minSize = PacketHeader.TOTAL_HEADER_BYTES + PacketHeader.CRC_BYTES
        if (frameBytes.size < minSize) {
            return PacketDecodeResult.MalformedFrame(
                "Frame too short (${frameBytes.size} bytes, minimum $minSize bytes)"
            )
        }

        val preamble = frameBytes[0]
        val syncHigh = frameBytes[1]
        val syncLow = frameBytes[2]

        if (preamble != PacketHeader.PREAMBLE_BYTE ||
            syncHigh != PacketHeader.SYNC_BYTE_HIGH ||
            syncLow != PacketHeader.SYNC_BYTE_LOW
        ) {
            return PacketDecodeResult.MalformedFrame(
                String.format(
                    "Invalid preamble/sync bytes: %02X %02X %02X",
                    preamble.toInt() and 0xFF,
                    syncHigh.toInt() and 0xFF,
                    syncLow.toInt() and 0xFF
                )
            )
        }

        val version = frameBytes[3].toInt() and 0xFF
        if (version != PacketHeader.PROTOCOL_VERSION) {
            return PacketDecodeResult.MalformedFrame("Unsupported protocol version: $version")
        }

        val typeCode = frameBytes[4].toInt() and 0xFF
        val packetType = AcousticPacketType.fromCode(typeCode)
            ?: return PacketDecodeResult.MalformedFrame("Unknown packet type code: 0x${String.format("%02X", typeCode)}")

        val senderId = ((frameBytes[5].toInt() and 0xFF) shl 8) or (frameBytes[6].toInt() and 0xFF)
        val receiverId = ((frameBytes[7].toInt() and 0xFF) shl 8) or (frameBytes[8].toInt() and 0xFF)
        val messageId = ((frameBytes[9].toInt() and 0xFF) shl 8) or (frameBytes[10].toInt() and 0xFF)
        val payloadLength = frameBytes[11].toInt() and 0xFF

        if (payloadLength > PacketHeader.MAX_PAYLOAD_BYTES) {
            return PacketDecodeResult.MalformedFrame("Payload length $payloadLength exceeds max ${PacketHeader.MAX_PAYLOAD_BYTES}")
        }

        val expectedTotalBytes = PacketHeader.TOTAL_HEADER_BYTES + payloadLength + PacketHeader.CRC_BYTES
        if (frameBytes.size < expectedTotalBytes) {
            return PacketDecodeResult.MalformedFrame(
                "Truncated frame: expected $expectedTotalBytes bytes for payloadLen=$payloadLength, got ${frameBytes.size}"
            )
        }

        val header = PacketHeader(
            preamble = preamble,
            syncWord = PacketHeader.SYNC_WORD,
            protocolVersion = version,
            packetType = packetType,
            senderId = senderId,
            receiverId = receiverId,
            messageId = messageId,
            payloadLength = payloadLength
        )

        val crcOffset = PacketHeader.TOTAL_HEADER_BYTES + payloadLength
        val expectedCrc = CRC16.compute(frameBytes, 0, crcOffset)
        val receivedCrc = ((frameBytes[crcOffset].toInt() and 0xFF) shl 8) or
            (frameBytes[crcOffset + 1].toInt() and 0xFF)

        val payloadBytes = frameBytes.copyOfRange(PacketHeader.TOTAL_HEADER_BYTES, crcOffset)

        if (expectedCrc != receivedCrc) {
            return PacketDecodeResult.CrcMismatch(
                expectedCrc = expectedCrc,
                receivedCrc = receivedCrc,
                partialHeader = header,
                corruptedPayloadPreview = payloadBytes.toString(Charsets.UTF_8)
            )
        }

        return PacketDecodeResult.Success(
            packet = AcousticPacket(
                header = header,
                payloadBytes = payloadBytes,
                crc16 = receivedCrc
            ),
            rawFrameBytes = frameBytes.copyOf(expectedTotalBytes)
        )
    }
}

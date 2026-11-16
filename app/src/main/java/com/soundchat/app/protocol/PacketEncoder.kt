package com.soundchat.app.protocol

/**
 * Encodes UTF-8 strings and control signals into framed binary packets with
 * preamble, sync sequence, protocol version, sender/receiver IDs, message ID,
 * payload length, UTF-8 bytes, and CRC-16 checksum.
 */
object PacketEncoder {

    fun encodePacket(
        packetType: AcousticPacketType,
        senderId: Int,
        receiverId: Int,
        messageId: Int,
        utf8Message: String
    ): Pair<AcousticPacket, ByteArray> {
        val rawUtf8 = utf8Message.toByteArray(Charsets.UTF_8)
        val truncatedPayload = if (rawUtf8.size > PacketHeader.MAX_PAYLOAD_BYTES) {
            rawUtf8.copyOf(PacketHeader.MAX_PAYLOAD_BYTES)
        } else {
            rawUtf8
        }

        val header = PacketHeader(
            packetType = packetType,
            senderId = senderId and 0xFFFF,
            receiverId = receiverId and 0xFFFF,
            messageId = messageId and 0xFFFF,
            payloadLength = truncatedPayload.size
        )

        val totalSize = PacketHeader.TOTAL_HEADER_BYTES + truncatedPayload.size + PacketHeader.CRC_BYTES
        val frame = ByteArray(totalSize)

        frame[0] = PacketHeader.PREAMBLE_BYTE
        frame[1] = PacketHeader.SYNC_BYTE_HIGH
        frame[2] = PacketHeader.SYNC_BYTE_LOW
        frame[3] = (header.protocolVersion and 0xFF).toByte()
        frame[4] = (header.packetType.code and 0xFF).toByte()
        frame[5] = ((header.senderId shr 8) and 0xFF).toByte()
        frame[6] = (header.senderId and 0xFF).toByte()
        frame[7] = ((header.receiverId shr 8) and 0xFF).toByte()
        frame[8] = (header.receiverId and 0xFF).toByte()
        frame[9] = ((header.messageId shr 8) and 0xFF).toByte()
        frame[10] = (header.messageId and 0xFF).toByte()
        frame[11] = (header.payloadLength and 0xFF).toByte()

        if (truncatedPayload.isNotEmpty()) {
            System.arraycopy(truncatedPayload, 0, frame, PacketHeader.TOTAL_HEADER_BYTES, truncatedPayload.size)
        }

        val crcOffset = PacketHeader.TOTAL_HEADER_BYTES + truncatedPayload.size
        val crc = CRC16.compute(frame, 0, crcOffset)
        frame[crcOffset] = ((crc shr 8) and 0xFF).toByte()
        frame[crcOffset + 1] = (crc and 0xFF).toByte()

        val packet = AcousticPacket(
            header = header,
            payloadBytes = truncatedPayload,
            crc16 = crc
        )
        return packet to frame
    }

    fun encodeTextMessage(
        senderId: Int,
        receiverId: Int,
        messageId: Int,
        text: String
    ): Pair<AcousticPacket, ByteArray> {
        return encodePacket(
            packetType = AcousticPacketType.DATA,
            senderId = senderId,
            receiverId = receiverId,
            messageId = messageId,
            utf8Message = text
        )
    }

    fun encodeAck(
        senderId: Int,
        receiverId: Int,
        acknowledgedMessageId: Int
    ): Pair<AcousticPacket, ByteArray> {
        return encodePacket(
            packetType = AcousticPacketType.ACK,
            senderId = senderId,
            receiverId = receiverId,
            messageId = acknowledgedMessageId,
            utf8Message = "ACK"
        )
    }

    fun formatFrameHex(bytes: ByteArray): String {
        return bytes.joinToString(" ") { String.format("%02X", it.toInt() and 0xFF) }
    }
}

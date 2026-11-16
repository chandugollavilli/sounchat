package com.soundchat.app.protocol

enum class AcousticPacketType(val code: Int, val label: String) {
    DATA(0x01, "DATA"),
    ACK(0x02, "ACK"),
    DISCOVERY_BEACON(0x03, "BEACON"),
    PAIR_REQUEST(0x04, "PAIR_REQ"),
    PAIR_CONFIRM(0x05, "PAIR_ACK"),
    DISCONNECT(0x06, "DISCONNECT");

    companion object {
        fun fromCode(code: Int): AcousticPacketType? {
            return entries.firstOrNull { it.code == (code and 0xFF) }
        }
    }
}

/**
 * Represents the 12-byte SoundChat acoustic protocol header:
 * [0]      Preamble:          0xAA (10101010 clock sync)
 * [1..2]   Sync Sequence:     0xD3, 0x91
 * [3]      Protocol Version:  0x01
 * [4]      Packet Type:       AcousticPacketType.code
 * [5..6]   Sender ID:         16-bit unsigned integer (displayed as 4-char hex e.g. "A4F2")
 * [7..8]   Receiver ID:       16-bit unsigned integer (0xFFFF = Broadcast)
 * [9..10]  Message ID:        16-bit unsigned sequence number
 * [11]     Payload Length:    0..64 bytes
 */
data class PacketHeader(
    val preamble: Byte = PREAMBLE_BYTE,
    val syncWord: Int = SYNC_WORD,
    val protocolVersion: Int = PROTOCOL_VERSION,
    val packetType: AcousticPacketType,
    val senderId: Int,
    val receiverId: Int,
    val messageId: Int,
    val payloadLength: Int
) {
    val senderHex: String
        get() = formatIdHex(senderId)

    val receiverHex: String
        get() = formatIdHex(receiverId)

    companion object {
        const val PREAMBLE_BYTE: Byte = 0xAA.toByte()
        const val SYNC_BYTE_HIGH: Byte = 0xD3.toByte()
        const val SYNC_BYTE_LOW: Byte = 0x91.toByte()
        const val SYNC_WORD: Int = 0xD391
        const val PROTOCOL_VERSION: Int = 0x01

        const val BROADCAST_ID: Int = 0xFFFF
        const val TOTAL_HEADER_BYTES: Int = 12
        const val PAYLOAD_LENGTH_INDEX: Int = 11
        const val CRC_BYTES: Int = 2
        const val MAX_PAYLOAD_BYTES: Int = 64

        fun formatIdHex(id: Int): String = String.format("%04X", id and 0xFFFF)

        fun parseHexId(hex: String): Int {
            return hex.trim().removePrefix("0x").toIntOrNull(16)?.and(0xFFFF) ?: BROADCAST_ID
        }
    }
}

data class AcousticPacket(
    val header: PacketHeader,
    val payloadBytes: ByteArray,
    val crc16: Int
) {
    val payloadText: String
        get() = payloadBytes.toString(Charsets.UTF_8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AcousticPacket) return false
        return header == other.header &&
            payloadBytes.contentEquals(other.payloadBytes) &&
            crc16 == other.crc16
    }

    override fun hashCode(): Int {
        var result = header.hashCode()
        result = 31 * result + payloadBytes.contentHashCode()
        result = 31 * result + crc16
        return result
    }
}

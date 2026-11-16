package com.soundchat.app.discovery

import com.soundchat.app.protocol.AcousticPacket
import com.soundchat.app.protocol.AcousticPacketType
import com.soundchat.app.protocol.PacketEncoder
import com.soundchat.app.protocol.PacketHeader
import kotlin.random.Random

/**
 * Manages the acoustic pairing handshake (`PAIR_REQUEST`, `PAIR_CONFIRM`, `DISCONNECT`).
 * Unknown devices are NEVER automatically accepted; incoming requests require explicit
 * user confirmation before transmitting `PAIR_CONFIRM`.
 */
object AcousticHandshake {

    fun generateSessionId(localId: Int, remoteId: Int): String {
        val salt = Random.nextInt(0x10, 0xFF)
        val combined = ((localId xor remoteId) and 0xFFFF)
        return String.format("SES-%04X-%02X", combined, salt)
    }

    fun createPairRequestFrame(
        localDeviceId: Int,
        localDeviceName: String,
        targetDeviceId: Int,
        messageId: Int,
        proposedSessionId: String
    ): Pair<AcousticPacket, ByteArray> {
        val cleanName = localDeviceName.replace("|", "").take(14)
        val payload = "REQ|$cleanName|$proposedSessionId"
        return PacketEncoder.encodePacket(
            packetType = AcousticPacketType.PAIR_REQUEST,
            senderId = localDeviceId,
            receiverId = targetDeviceId,
            messageId = messageId,
            utf8Message = payload
        )
    }

    fun createPairConfirmFrame(
        localDeviceId: Int,
        localDeviceName: String,
        targetDeviceId: Int,
        messageId: Int,
        acceptedSessionId: String
    ): Pair<AcousticPacket, ByteArray> {
        val cleanName = localDeviceName.replace("|", "").take(14)
        val payload = "CFM|$cleanName|$acceptedSessionId"
        return PacketEncoder.encodePacket(
            packetType = AcousticPacketType.PAIR_CONFIRM,
            senderId = localDeviceId,
            receiverId = targetDeviceId,
            messageId = messageId,
            utf8Message = payload
        )
    }

    fun createDisconnectFrame(
        localDeviceId: Int,
        targetDeviceId: Int,
        messageId: Int,
        sessionId: String
    ): Pair<AcousticPacket, ByteArray> {
        return PacketEncoder.encodePacket(
            packetType = AcousticPacketType.DISCONNECT,
            senderId = localDeviceId,
            receiverId = targetDeviceId,
            messageId = messageId,
            utf8Message = "DSC|$sessionId"
        )
    }

    data class ParsedHandshakePayload(
        val senderId: Int,
        val senderName: String,
        val sessionId: String
    )

    fun parseHandshakePacket(packet: AcousticPacket): ParsedHandshakePayload {
        val parts = packet.payloadText.split("|")
        val senderHex = PacketHeader.formatIdHex(packet.header.senderId)
        val name = parts.getOrNull(1)?.ifBlank { "Node-$senderHex" } ?: "Node-$senderHex"
        val sessionId = parts.getOrNull(2)?.ifBlank { "SES-$senderHex" } ?: "SES-$senderHex"
        return ParsedHandshakePayload(
            senderId = packet.header.senderId,
            senderName = name,
            sessionId = sessionId
        )
    }
}

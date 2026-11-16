package com.soundchat.app.discovery

import com.soundchat.app.protocol.AcousticPacket
import com.soundchat.app.protocol.AcousticPacketType
import com.soundchat.app.protocol.PacketEncoder
import com.soundchat.app.protocol.PacketHeader

/**
 * Generates and parses acoustic discovery beacons (`AcousticPacketType.DISCOVERY_BEACON`).
 * Beacon Payload Format: `SC|<deviceName>|<bandCode>`
 */
object AcousticDiscovery {

    private const val BEACON_PREFIX = "SC|"

    fun createBeaconFrame(
        localDeviceId: Int,
        localDeviceName: String,
        sequenceNumber: Int,
        isUltrasonic: Boolean
    ): Pair<AcousticPacket, ByteArray> {
        val safeName = localDeviceName.replace("|", "").take(16).ifBlank { "Node" }
        val bandTag = if (isUltrasonic) "ULT" else "AUD"
        val payload = "$BEACON_PREFIX$safeName|$bandTag"
        return PacketEncoder.encodePacket(
            packetType = AcousticPacketType.DISCOVERY_BEACON,
            senderId = localDeviceId,
            receiverId = PacketHeader.BROADCAST_ID,
            messageId = sequenceNumber,
            utf8Message = payload
        )
    }

    fun parseBeaconPacket(
        packet: AcousticPacket,
        signalStrengthDbFs: Double,
        snrDb: Double,
        detectedCarrierHz: Double
    ): DiscoveredDevice? {
        if (packet.header.packetType != AcousticPacketType.DISCOVERY_BEACON) return null
        val text = packet.payloadText
        val name = if (text.startsWith(BEACON_PREFIX)) {
            val parts = text.removePrefix(BEACON_PREFIX).split("|")
            parts.firstOrNull()?.ifBlank { "Node-${packet.header.senderHex}" }
                ?: "Node-${packet.header.senderHex}"
        } else {
            text.take(16).ifBlank { "Node-${packet.header.senderHex}" }
        }

        return DiscoveredDevice(
            deviceId = packet.header.senderId,
            deviceName = name,
            signalStrengthDbFs = signalStrengthDbFs,
            snrDb = snrDb,
            detectedCarrierHz = detectedCarrierHz,
            pairingState = PairingState.DISCOVERED,
            sessionId = null,
            lastSeenEpochMs = System.currentTimeMillis()
        )
    }
}

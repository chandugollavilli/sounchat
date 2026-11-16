package com.soundchat.app.discovery

import com.soundchat.app.protocol.PacketHeader

enum class PairingState(val displayLabel: String) {
    DISCOVERED("Discovered (Unpaired)"),
    OUTGOING_REQUEST_SENT("Pairing Request Sent…"),
    INCOMING_REQUEST_PENDING("Incoming Pairing Request — Action Needed"),
    PAIRED("Paired (Acoustic Session Active)"),
    DISCONNECTED("Disconnected")
}

data class DiscoveredDevice(
    val deviceId: Int,
    val deviceName: String,
    val signalStrengthDbFs: Double,
    val snrDb: Double,
    val detectedCarrierHz: Double,
    val pairingState: PairingState = PairingState.DISCOVERED,
    val sessionId: String? = null,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
) {
    val deviceHexId: String
        get() = PacketHeader.formatIdHex(deviceId)
}

data class ActiveDeviceSession(
    val peerDeviceId: Int,
    val peerDeviceName: String,
    val sessionId: String,
    val establishedEpochMs: Long = System.currentTimeMillis(),
    val lastRssiDbFs: Double = -42.0,
    val lastSnrDb: Double = 14.0
) {
    val peerHexId: String
        get() = PacketHeader.formatIdHex(peerDeviceId)
}

package com.soundchat.app.data.models

/**
 * Exact real-time transmission and reception statuses specified in Section 8.
 * MESSAGE_DELIVERED is ONLY displayed after receiving and validating the
 * acoustic ACK frame from the receiving device.
 */
enum class TransmissionStatus(
    val displayTitle: String,
    val subtitle: String
) {
    IDLE(
        displayTitle = "IDLE",
        subtitle = "Transceiver standby · Ready for acoustic operation"
    ),
    LISTENING(
        displayTitle = "LISTENING",
        subtitle = "Microphone active · Monitoring ultrasonic passband"
    ),
    DISCOVERING(
        displayTitle = "DISCOVERING",
        subtitle = "Scanning & broadcasting acoustic discovery beacons"
    ),
    DEVICE_DETECTED(
        displayTitle = "DEVICE DETECTED",
        subtitle = "Nearby acoustic node identified via ultrasonic beacon"
    ),
    PAIRING(
        displayTitle = "PAIRING",
        subtitle = "Performing acoustic handshake & session exchange"
    ),
    TRANSMITTING(
        displayTitle = "TRANSMITTING",
        subtitle = "Modulating & emitting PCM ultrasonic wave via speaker"
    ),
    WAITING_FOR_ACKNOWLEDGEMENT(
        displayTitle = "WAITING FOR ACKNOWLEDGEMENT",
        subtitle = "Listening for CRC-verified acoustic ACK frame"
    ),
    MESSAGE_DELIVERED(
        displayTitle = "MESSAGE DELIVERED",
        subtitle = "Acoustic ACK packet received & CRC-16 verified"
    ),
    TRANSMISSION_FAILED(
        displayTitle = "TRANSMISSION FAILED",
        subtitle = "No valid ACK received within retry window"
    ),
    RETRYING(
        displayTitle = "RETRYING",
        subtitle = "Re-transmitting acoustic packet frame"
    )
}

package com.soundchat.app.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageDeliveryState(val label: String) {
    TRANSMITTING("TRANSMITTING"),
    WAITING_FOR_ACK("WAITING FOR ACK"),
    RETRYING("RETRYING"),
    SENT_UNACKNOWLEDGED("TRANSMITTED (NO-ACK MODE)"),
    DELIVERED_ACK_VERIFIED("MESSAGE DELIVERED (ACK VERIFIED)"),
    FAILED("TRANSMISSION FAILED"),
    RECEIVED_VALID("RECEIVED (CRC-16 OK)")
}

@Entity(tableName = "acoustic_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val packetMessageId: Int,
    val senderId: Int,
    val senderHex: String,
    val receiverId: Int,
    val receiverHex: String,
    val content: String,
    val isOutgoing: Boolean,
    val deliveryState: String,
    val crcHex: String,
    val carrierFreqHz: Int,
    val snrDb: Double,
    val durationMs: Long,
    val retryCount: Int = 0,
    val isLoopbackTest: Boolean = false,
    val timestampEpochMs: Long = System.currentTimeMillis()
)

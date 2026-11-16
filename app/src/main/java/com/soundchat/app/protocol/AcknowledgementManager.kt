package com.soundchat.app.protocol

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages awaiting acoustic ACK packets for reliable delivery and deduplicates
 * retransmitted incoming packets using a bounded history set.
 */
class AcknowledgementManager {

    private data class PacketKey(val senderId: Int, val messageId: Int)

    private val pendingAcks = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val seenIncomingPackets = LinkedHashSet<PacketKey>()
    private val maxDeduplicationHistory = 128

    fun registerPendingAck(messageId: Int): CompletableDeferred<Boolean> {
        val deferred = CompletableDeferred<Boolean>()
        pendingAcks[messageId and 0xFFFF] = deferred
        return deferred
    }

    fun onAckReceived(messageId: Int): Boolean {
        val normalizedId = messageId and 0xFFFF
        val deferred = pendingAcks.remove(normalizedId) ?: return false
        deferred.complete(true)
        return true
    }

    fun cancelPendingAck(messageId: Int) {
        val normalizedId = messageId and 0xFFFF
        pendingAcks.remove(normalizedId)?.complete(false)
    }

    fun cancelAll() {
        val keys = pendingAcks.keys.toList()
        for (k in keys) {
            pendingAcks.remove(k)?.complete(false)
        }
    }

    /**
     * Returns true if this `(senderId, messageId)` pair is NEW, or false if it was
     * already received recently (duplicate retransmission).
     */
    @Synchronized
    fun recordIncomingAndCheckIsNew(senderId: Int, messageId: Int): Boolean {
        val key = PacketKey(senderId and 0xFFFF, messageId and 0xFFFF)
        if (seenIncomingPackets.contains(key)) {
            return false
        }
        seenIncomingPackets.add(key)
        if (seenIncomingPackets.size > maxDeduplicationHistory) {
            val iterator = seenIncomingPackets.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }

    @Synchronized
    fun clearHistory() {
        seenIncomingPackets.clear()
        cancelAll()
    }
}

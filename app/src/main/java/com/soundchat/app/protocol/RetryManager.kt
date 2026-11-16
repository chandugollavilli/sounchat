package com.soundchat.app.protocol

import kotlinx.coroutines.withTimeoutOrNull

enum class DeliveryOutcome {
    DELIVERED_ACK_VERIFIED,
    TRANSMITTED_UNACKNOWLEDGED_MODE,
    FAILED_TIMEOUT_MAX_RETRIES,
    FAILED_HARDWARE_ERROR
}

data class RetryExecutionReport(
    val outcome: DeliveryOutcome,
    val attemptsUsed: Int,
    val totalDurationMs: Long
)

/**
 * Coordinates automatic retransmission with configurable retry count and ACK timeout.
 * Truthfully distinguishes between ACK-verified delivery (`DELIVERED_ACK_VERIFIED`)
 * and timeout failure (`FAILED_TIMEOUT_MAX_RETRIES`).
 */
class RetryManager(
    private val ackManager: AcknowledgementManager
) {
    suspend fun executeWithRetry(
        messageId: Int,
        requireAck: Boolean,
        maxRetries: Int,
        ackTimeoutMs: Long,
        onAttemptStart: suspend (attemptNumber: Int, isRetry: Boolean) -> Boolean,
        onWaitingForAck: suspend (attemptNumber: Int) -> Unit
    ): RetryExecutionReport {
        val startTime = System.currentTimeMillis()
        val totalAllowedAttempts = if (requireAck) (maxRetries.coerceAtLeast(0) + 1) else 1

        for (attempt in 1..totalAllowedAttempts) {
            val isRetry = attempt > 1
            val ackDeferred = if (requireAck) {
                ackManager.registerPendingAck(messageId)
            } else {
                null
            }

            val txSucceeded = onAttemptStart(attempt, isRetry)
            if (!txSucceeded) {
                ackManager.cancelPendingAck(messageId)
                return RetryExecutionReport(
                    outcome = DeliveryOutcome.FAILED_HARDWARE_ERROR,
                    attemptsUsed = attempt,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            }

            if (!requireAck || ackDeferred == null) {
                return RetryExecutionReport(
                    outcome = DeliveryOutcome.TRANSMITTED_UNACKNOWLEDGED_MODE,
                    attemptsUsed = attempt,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            }

            onWaitingForAck(attempt)
            val ackReceived = withTimeoutOrNull(ackTimeoutMs) {
                ackDeferred.await()
            } ?: false

            ackManager.cancelPendingAck(messageId)

            if (ackReceived) {
                return RetryExecutionReport(
                    outcome = DeliveryOutcome.DELIVERED_ACK_VERIFIED,
                    attemptsUsed = attempt,
                    totalDurationMs = System.currentTimeMillis() - startTime
                )
            }
        }

        return RetryExecutionReport(
            outcome = DeliveryOutcome.FAILED_TIMEOUT_MAX_RETRIES,
            attemptsUsed = totalAllowedAttempts,
            totalDurationMs = System.currentTimeMillis() - startTime
        )
    }
}

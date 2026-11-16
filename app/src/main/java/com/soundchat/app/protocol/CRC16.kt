package com.soundchat.app.protocol

/**
 * Standard CRC-16/CCITT-FALSE implementation (`polynomial = 0x1021`, `initial = 0xFFFF`).
 * Used to validate integrity of every acoustic packet over the air.
 */
object CRC16 {

    private const val POLYNOMIAL = 0x1021
    private const val INITIAL_VALUE = 0xFFFF

    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        var crc = INITIAL_VALUE
        for (i in offset until (offset + length)) {
            val byteVal = data[i].toInt() and 0xFF
            crc = crc xor (byteVal shl 8)
            for (bit in 0 until 8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc and 0xFFFF
    }

    fun verify(dataWithCrc: ByteArray): Boolean {
        if (dataWithCrc.size < 3) return false
        val payloadLen = dataWithCrc.size - 2
        val expectedCrc = compute(dataWithCrc, 0, payloadLen)
        val receivedHigh = dataWithCrc[payloadLen].toInt() and 0xFF
        val receivedLow = dataWithCrc[payloadLen + 1].toInt() and 0xFF
        val receivedCrc = (receivedHigh shl 8) or receivedLow
        return expectedCrc == receivedCrc
    }

    fun formatHex(crc: Int): String {
        return String.format("%04X", crc and 0xFFFF)
    }
}

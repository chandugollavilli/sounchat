package com.soundchat.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class AudioReceiver(private val context: Context) {

    private val isReceivingFlag = AtomicBoolean(false)
    private var activeRecord: AudioRecord? = null

    val isReceiving: Boolean
        get() = isReceivingFlag.get()

    fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Captures live PCM 16-bit mono audio from the device microphone and delivers
     * buffers to [onSamplesCaptured].
     * Uses UNPROCESSED or VOICE_RECOGNITION audio source first to prevent Android's
     * speech low-pass filter / AGC from stripping 18.5–20.0 kHz ultrasonic energy.
     */
    @SuppressLint("MissingPermission")
    suspend fun startListening(
        sampleRate: Int,
        frameSize: Int = 2048,
        onAudioSourceSelected: ((sourceLabel: String, actualSampleRateHz: Int, bufferFrames: Int) -> Unit)? = null,
        onSamplesCaptured: suspend (ShortArray, Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasRecordPermission()) return@withContext false
        stop()

        val minBufBytes = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufBytes <= 0) return@withContext false

        val bufferSizeInBytes = maxOf(minBufBytes * 4, frameSize * 4)

        val candidateSources = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED (Raw Mic)")
            }
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION (No AGC/LPF)")
            add(MediaRecorder.AudioSource.MIC to "DEFAULT MIC")
        }

        var record: AudioRecord? = null
        var chosenSourceLabel = "MIC"

        for ((sourceId, label) in candidateSources) {
            try {
                val candidate = AudioRecord(
                    sourceId,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
                )
                if (candidate.state == AudioRecord.STATE_INITIALIZED) {
                    record = candidate
                    chosenSourceLabel = label
                    break
                } else {
                    candidate.release()
                }
            } catch (_: Exception) {
            }
        }

        if (record == null) return@withContext false

        activeRecord = record
        isReceivingFlag.set(true)
        val actualRate = record.sampleRate.takeIf { it > 0 } ?: sampleRate
        val actualBufFrames = record.bufferSizeInFrames
        onAudioSourceSelected?.invoke(chosenSourceLabel, actualRate, actualBufFrames)

        val readBuffer = ShortArray(frameSize)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                return@withContext false
            }

            while (isActive && isReceivingFlag.get()) {
                val readCount = record.read(readBuffer, 0, frameSize)
                if (readCount > 0) {
                    val copy = readBuffer.copyOf(readCount)
                    onSamplesCaptured(copy, actualRate)
                }
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            releaseRecord(record)
            isReceivingFlag.set(false)
        }
    }

    fun stop() {
        isReceivingFlag.set(false)
        releaseRecord(activeRecord)
        activeRecord = null
    }

    private fun releaseRecord(record: AudioRecord?) {
        if (record == null) return
        try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop()
            }
        } catch (_: Exception) {
        }
        try {
            record.release()
        } catch (_: Exception) {
        }
    }
}

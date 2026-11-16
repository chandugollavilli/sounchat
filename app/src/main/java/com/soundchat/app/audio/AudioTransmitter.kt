package com.soundchat.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class AudioTransmitter {

    private val isTransmittingFlag = AtomicBoolean(false)
    private var activeTrack: AudioTrack? = null

    val isTransmitting: Boolean
        get() = isTransmittingFlag.get()

    /**
     * Plays a complete 16-bit PCM buffer (such as an encoded BFSK acoustic frame)
     * through the built-in speaker using Android AudioTrack.
     */
    suspend fun transmitPcmSamples(
        samples: ShortArray,
        sampleRate: Int,
        onTrackConfigured: ((actualSampleRateHz: Int, bufferFrames: Int) -> Unit)? = null,
        onChunkPlayed: ((FloatArray) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (samples.isEmpty()) return@withContext false
        stop()
        isTransmittingFlag.set(true)

        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        var track: AudioTrack? = null
        var completedNormally = false
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            if (track.state != AudioTrack.STATE_INITIALIZED) {
                return@withContext false
            }

            val actualSampleRate = track.sampleRate
            val bufferFrames = track.bufferSizeInFrames
            onTrackConfigured?.invoke(actualSampleRate, bufferFrames)

            activeTrack = track
            track.play()

            val chunkSize = 1024
            var offset = 0
            while (offset < samples.size && isActive && isTransmittingFlag.get()) {
                val count = minOf(chunkSize, samples.size - offset)
                val written = track.write(samples, offset, count)
                if (written < 0) break

                if (onChunkPlayed != null) {
                    val normalized = FloatArray(count) { i ->
                        samples[offset + i] / 32768f
                    }
                    onChunkPlayed(normalized)
                }
                offset += written
            }

            // Wait for the hardware AudioTrack playback head to finish draining all queued frames
            // so the trailing CRC-16 checksum symbols are never truncated by premature stop/flush.
            val bufferLatencyMs = ((bufferFrames.toDouble() / actualSampleRate.coerceAtLeast(8000)) * 1000.0).toLong()
            val maxDrainWaitMs = maxOf(250L, bufferLatencyMs + 180L)
            val drainStart = System.currentTimeMillis()
            while (isActive && isTransmittingFlag.get()) {
                val playedFrames = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (playedFrames >= samples.size.toLong()) break
                if (System.currentTimeMillis() - drainStart >= maxDrainWaitMs) break
                delay(15L)
            }
            completedNormally = isActive && isTransmittingFlag.get()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        } finally {
            releaseTrack(track, flushPending = !completedNormally)
            isTransmittingFlag.set(false)
        }
    }

    /**
     * Phase 1 Speaker Frequency Test:
     * Generates and streams a pure continuous sine wave at [frequencyHz] for [durationMs].
     */
    suspend fun playTestTone(
        frequencyHz: Double,
        sampleRate: Int,
        durationMs: Int,
        amplitude: Float = 0.85f,
        onTrackConfigured: ((actualSampleRateHz: Int, bufferFrames: Int) -> Unit)? = null,
        onWaveformChunk: ((FloatArray) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        stop()
        isTransmittingFlag.set(true)

        val totalSamples = ((sampleRate.toLong() * durationMs) / 1000L).toInt().coerceAtLeast(sampleRate / 4)
        val rampSamples = minOf(totalSamples / 10, (sampleRate * 0.015).toInt())
        val pcm = ShortArray(totalSamples)
        var phase = 0.0
        val phaseStep = 2.0 * PI * frequencyHz / sampleRate.toDouble()

        for (i in 0 until totalSamples) {
            val env = when {
                i < rampSamples -> 0.5 * (1.0 - kotlin.math.cos(PI * i / rampSamples))
                i > totalSamples - rampSamples -> {
                    val rem = totalSamples - i
                    0.5 * (1.0 - kotlin.math.cos(PI * rem / rampSamples))
                }
                else -> 1.0
            }
            val sampleVal = (sin(phase) * amplitude * env * 32767.0).toInt().coerceIn(-32767, 32767)
            pcm[i] = sampleVal.toShort()
            phase += phaseStep
            if (phase > 2.0 * PI) phase -= 2.0 * PI
        }

        transmitPcmSamples(
            samples = pcm,
            sampleRate = sampleRate,
            onTrackConfigured = onTrackConfigured,
            onChunkPlayed = onWaveformChunk
        )
    }

    fun stop() {
        isTransmittingFlag.set(false)
        releaseTrack(activeTrack, flushPending = true)
        activeTrack = null
    }

    private fun releaseTrack(track: AudioTrack?, flushPending: Boolean) {
        if (track == null) return
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                if (flushPending) {
                    track.pause()
                    track.flush()
                }
                track.stop()
            }
        } catch (_: Exception) {
        }
        try {
            track.release()
        } catch (_: Exception) {
        }
    }
}

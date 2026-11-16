package com.soundchat.app.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.os.Build

data class DeviceCapabilityReport(
    val micNearUltrasoundSupported: Boolean,
    val micNearUltrasoundRawProperty: String,
    val speakerNearUltrasoundSupported: Boolean,
    val speakerNearUltrasoundRawProperty: String,
    val unprocessedAudioSourceSupported: Boolean,
    val nativeSampleRateHz: Int,
    val nativeFramesPerBuffer: Int,
    val supportedSampleRates: List<Int>,
    val hasMicrophoneHardware: Boolean,
    val hasSpeakerOutput: Boolean,
    val inputDeviceNames: List<String>,
    val outputDeviceNames: List<String>,
    val nyquistFrequencyHz: Int,
    val canSampleUltrasonicBand: Boolean,
    val hardwareWarnings: List<String>
) {
    val isFullyUltrasonicVerified: Boolean
        get() = micNearUltrasoundSupported &&
            speakerNearUltrasoundSupported &&
            canSampleUltrasonicBand &&
            hasMicrophoneHardware &&
            hasSpeakerOutput
}

class AudioCapabilityChecker(private val context: Context) {

    fun inspectCapabilities(): DeviceCapabilityReport {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val packageManager = context.packageManager

        val micPropRaw = audioManager?.getProperty(
            AudioManager.PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND
        ) ?: "UNREPORTED"
        val speakerPropRaw = audioManager?.getProperty(
            AudioManager.PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND
        ) ?: "UNREPORTED"
        val unprocessedPropRaw = audioManager?.getProperty(
            AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED
        ) ?: "false"

        val micUltrasound = micPropRaw.equals("true", ignoreCase = true)
        val speakerUltrasound = speakerPropRaw.equals("true", ignoreCase = true)
        val unprocessedSupported = unprocessedPropRaw.equals("true", ignoreCase = true)

        val nativeSampleRate = audioManager?.getProperty(
            AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE
        )?.toIntOrNull() ?: 48000

        val nativeBufferFrames = audioManager?.getProperty(
            AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER
        )?.toIntOrNull() ?: 256

        val hasMicFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val hasOutputFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)

        val inputDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)
            ?.map { deviceTypeLabel(it.type) }
            ?.distinct()
            ?: emptyList()

        val outputDevices = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.map { deviceTypeLabel(it.type) }
            ?.distinct()
            ?: emptyList()

        val candidateRates = listOf(44100, 48000, 96000)
        val supportedRates = candidateRates.filter { rate ->
            val minIn = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val minOut = AudioTrack.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            minIn > 0 && minOut > 0
        }.ifEmpty { listOf(48000) }

        val bestRate = if (supportedRates.contains(48000)) 48000 else supportedRates.maxOrNull() ?: 44100
        val nyquist = bestRate / 2
        val canSampleUltrasonic = nyquist >= 22050

        val warnings = mutableListOf<String>()
        if (!micUltrasound) {
            warnings.add(
                "PROPERTY_SUPPORT_MIC_NEAR_ULTRASOUND is '$micPropRaw'. Microphone DSP or hardware anti-aliasing filter may attenuate 18.5–20 kHz."
            )
        }
        if (!speakerUltrasound) {
            warnings.add(
                "PROPERTY_SUPPORT_SPEAKER_NEAR_ULTRASOUND is '$speakerPropRaw'. Speaker frequency response above 18.5 kHz is not guaranteed by OEM."
            )
        }
        if (!unprocessedSupported) {
            warnings.add(
                "UNPROCESSED audio source not reported; using VOICE_RECOGNITION source to bypass voice low-pass filters."
            )
        }
        if (!hasMicFeature) {
            warnings.add("FEATURE_MICROPHONE is not declared on this device.")
        }
        if (!hasOutputFeature && outputDevices.isEmpty()) {
            warnings.add("No active audio output devices detected.")
        }

        return DeviceCapabilityReport(
            micNearUltrasoundSupported = micUltrasound,
            micNearUltrasoundRawProperty = micPropRaw,
            speakerNearUltrasoundSupported = speakerUltrasound,
            speakerNearUltrasoundRawProperty = speakerPropRaw,
            unprocessedAudioSourceSupported = unprocessedSupported,
            nativeSampleRateHz = nativeSampleRate,
            nativeFramesPerBuffer = nativeBufferFrames,
            supportedSampleRates = supportedRates,
            hasMicrophoneHardware = hasMicFeature || inputDevices.isNotEmpty(),
            hasSpeakerOutput = hasOutputFeature || outputDevices.isNotEmpty(),
            inputDeviceNames = inputDevices.ifEmpty { listOf("Built-in Mic") },
            outputDeviceNames = outputDevices.ifEmpty { listOf("Built-in Speaker") },
            nyquistFrequencyHz = nyquist,
            canSampleUltrasonicBand = canSampleUltrasonic,
            hardwareWarnings = warnings
        )
    }

    private fun deviceTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Mic"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Built-in Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Built-in Earpiece"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Audio"
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET) {
                "BLE Headset"
            } else {
                "Audio Device ($type)"
            }
        }
    }
}

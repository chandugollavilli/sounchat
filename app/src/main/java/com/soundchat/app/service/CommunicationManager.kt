package com.soundchat.app.service

import android.content.Context
import android.os.Build
import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioCapabilityChecker
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.audio.AudioReceiver
import com.soundchat.app.audio.AudioTransmitter
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.audio.TransmissionSpeedPreset
import com.soundchat.app.data.models.ChatMessageEntity
import com.soundchat.app.data.models.MessageDeliveryState
import com.soundchat.app.data.models.TransmissionStatus
import com.soundchat.app.data.repository.ChatRepository
import com.soundchat.app.discovery.AcousticDiscovery
import com.soundchat.app.discovery.AcousticHandshake
import com.soundchat.app.discovery.ActiveDeviceSession
import com.soundchat.app.discovery.DiscoveredDevice
import com.soundchat.app.discovery.PairingState
import com.soundchat.app.dsp.BandPassFilter
import com.soundchat.app.dsp.BfskDemodulator
import com.soundchat.app.dsp.BfskModulator
import com.soundchat.app.dsp.FrequencyAnalyzer
import com.soundchat.app.dsp.SignalDetector
import com.soundchat.app.protocol.AcknowledgementManager
import com.soundchat.app.protocol.AcousticPacketType
import com.soundchat.app.protocol.CRC16
import com.soundchat.app.protocol.DeliveryOutcome
import com.soundchat.app.protocol.PacketDecodeResult
import com.soundchat.app.protocol.PacketDecoder
import com.soundchat.app.protocol.PacketEncoder
import com.soundchat.app.protocol.PacketHeader
import com.soundchat.app.protocol.RetryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.random.Random

data class DiagnosticTelemetryState(
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    val transmittedFrequencyHz: Double = 0.0,
    val receivedFrequencyHz: Double = 0.0,
    val detectedSignalStrengthDbFs: Double = -105.0,
    val noiseFloorDb: Double = -95.0,
    val snrDb: Double = 0.0,
    val bit0PowerDb: Double = -105.0,
    val bit1PowerDb: Double = -105.0,
    val syncPowerDb: Double = -105.0,
    val carrierDetected: Boolean = false,
    val packetDetectedCount: Int = 0,
    val crcPassCount: Int = 0,
    val crcFailCount: Int = 0,
    val lastSyncResult: String = "Awaiting preamble/sync (0xAA 0xD3 0x91)",
    val lastCrcResult: String = "NONE",
    val lastAckResult: String = "No ACK transaction yet",
    val lastDecodedResult: String = "Awaiting acoustic frame…",
    val lastRawFrameHex: String = "—",
    val lastRemoteSenderIdHex: String = "None",
    val lastAckMessageId: Int? = null,
    val lastTransmissionDurationMs: Long = 0L,
    val actualSampleRateHz: Int = 48000,
    val actualRecordingSampleRateHz: Int = 0,
    val actualPlaybackSampleRateHz: Int = 0,
    val activeAudioSource: String = "Standby",
    val isSpeakerToneRunning: Boolean = false,
    val speakerTestFreqHz: Double = 18750.0,
    val isLoopbackRunning: Boolean = false,
    val lastLoopbackReport: String = "Not run yet",
    val localLoopbackVerified: Boolean = false,
    val physicalTwoDeviceRxVerified: Boolean = false,
    val physicalTwoDeviceAckVerified: Boolean = false,
    val simulatedDspVerified: Boolean = false,
    val physicalValidationStatus: PhysicalValidationStatus = PhysicalValidationStatus.NOT_RUN,
    val lastValidationTierUsed: ValidationExecutionTier? = null,
    val isDistanceProbeRunning: Boolean = false,
    val distanceTestLogs: List<DistanceTestEntry> = emptyList(),
    val lengthStressReport: LengthStressTestReport = LengthStressTestReport(),
    val reliabilityReport: RepeatedReliabilityReport = RepeatedReliabilityReport(),
    val spectrumBars: List<Float> = List(64) { 0.04f },
    val waveformPoints: List<Float> = List(96) { 0f },
    val eventLogs: List<String> = emptyList()
)

class CommunicationManager(
    context: Context,
    private val chatRepository: ChatRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val capabilityChecker = AudioCapabilityChecker(appContext)
    private val transmitter = AudioTransmitter()
    private val receiver = AudioReceiver(appContext)
    private val modulator = BfskModulator()
    private val ackManager = AcknowledgementManager()
    private val retryManager = RetryManager(ackManager)

    private val sequenceCounter = AtomicInteger(Random.nextInt(100, 900))

    val localDeviceId: Int = generateStableDeviceId()
    val localDeviceHex: String = PacketHeader.formatIdHex(localDeviceId)
    val localDeviceName: String = (Build.MODEL ?: "AndroidNode").take(14)

    private val _audioConfig = MutableStateFlow(AudioConfiguration())
    val audioConfig: StateFlow<AudioConfiguration> = _audioConfig.asStateFlow()

    private val _capabilityReport = MutableStateFlow(capabilityChecker.inspectCapabilities())
    val capabilityReport: StateFlow<DeviceCapabilityReport> = _capabilityReport.asStateFlow()

    private val _transmissionStatus = MutableStateFlow(TransmissionStatus.IDLE)
    val transmissionStatus: StateFlow<TransmissionStatus> = _transmissionStatus.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isBeaconAdvertising = MutableStateFlow(true)
    val isBeaconAdvertising: StateFlow<Boolean> = _isBeaconAdvertising.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _activeSession = MutableStateFlow<ActiveDeviceSession?>(null)
    val activeSession: StateFlow<ActiveDeviceSession?> = _activeSession.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticTelemetryState())
    val diagnostics: StateFlow<DiagnosticTelemetryState> = _diagnostics.asStateFlow()

    private var bandPassFilter = BandPassFilter(
        sampleRate = _audioConfig.value.sampleRate,
        lowCutoffHz = _audioConfig.value.bandMode.passBandLowHz,
        highCutoffHz = _audioConfig.value.bandMode.passBandHighHz
    )
    private val signalDetector = SignalDetector(_audioConfig.value)
    private val demodulator = BfskDemodulator(_audioConfig.value)

    private var receiveJob: Job? = null
    private var discoveryBeaconJob: Job? = null
    private var speakerToneJob: Job? = null
    private var transmitJob: Job? = null
    private var validationSuiteJob: Job? = null

    // Half-duplex guard: true when transmitting a normal device-to-device packet so the
    // local receiver does not decode its own speaker output unless in explicit Acoustic Loopback mode.
    @Volatile
    private var suppressSelfReception: Boolean = false

    // Sliding 2048-sample analysis windows so FFT resolution always stays 23.44 Hz/bin (@48kHz)
    // even if AudioRecord.read() returns smaller chunks.
    private val fftRawWindow = ShortArray(2048)
    private val fftFilteredWindow = DoubleArray(2048)
    private var fftWindowFilled = 0

    init {
        refreshDeviceCapabilities()
        appendLog("SoundChat node initialized [ID: $localDeviceHex · Model: $localDeviceName]")
    }

    fun refreshDeviceCapabilities(): DeviceCapabilityReport {
        val report = capabilityChecker.inspectCapabilities()
        _capabilityReport.value = report
        return report
    }

    fun hasRecordAudioPermission(): Boolean = receiver.hasRecordPermission()

    fun updateAudioConfiguration(transform: (AudioConfiguration) -> AudioConfiguration) {
        val updated = transform(_audioConfig.value)
        _audioConfig.value = updated
        bandPassFilter = BandPassFilter(
            sampleRate = updated.sampleRate,
            lowCutoffHz = updated.bandMode.passBandLowHz,
            highCutoffHz = updated.bandMode.passBandHighHz
        )
        signalDetector.updateConfiguration(updated)
        demodulator.updateConfiguration(updated)
        _diagnostics.update {
            it.copy(
                actualSampleRateHz = updated.sampleRate,
                speakerTestFreqHz = updated.freqBit0Hz
            )
        }
        appendLog(
            "Audio config updated: ${updated.bandMode.displayName}, SR=${updated.sampleRate}Hz, " +
                "f0=${updated.freqBit0Hz.toInt()}Hz, f1=${updated.freqBit1Hz.toInt()}Hz, " +
                "Symbol=${updated.symbolDurationMs}ms"
        )
    }

    fun setBandMode(mode: AcousticBandMode) {
        updateAudioConfiguration { it.withBandMode(mode) }
    }

    fun setSpeedPreset(preset: TransmissionSpeedPreset) {
        updateAudioConfiguration { it.withSpeedPreset(preset) }
    }

    fun setSampleRate(rateHz: Int) {
        updateAudioConfiguration { it.copy(sampleRate = rateHz) }
    }

    fun setCustomCarrierFrequencies(bit0Hz: Double, bit1Hz: Double, syncHz: Double) {
        updateAudioConfiguration {
            it.copy(
                freqBit0Hz = bit0Hz,
                freqBit1Hz = bit1Hz,
                freqSyncHz = syncHz
            )
        }
    }

    fun setSpeakerTestFrequency(freqHz: Double) {
        _diagnostics.update { it.copy(speakerTestFreqHz = freqHz.coerceIn(500.0, 21000.0)) }
    }

    /**
     * Phase 1 Speaker Frequency Test:
     * Plays a continuous pure tone at [frequencyHz] for [durationMs] through the phone speaker.
     */
    fun startSpeakerTestTone(
        frequencyHz: Double = _diagnostics.value.speakerTestFreqHz,
        durationMs: Int = 3500
    ) {
        speakerToneJob?.cancel()
        speakerToneJob = scope.launch {
            val cfg = _audioConfig.value
            _diagnostics.update {
                it.copy(
                    isSpeakerToneRunning = true,
                    transmittedFrequencyHz = frequencyHz,
                    speakerTestFreqHz = frequencyHz
                )
            }
            val prevStatus = _transmissionStatus.value
            _transmissionStatus.value = TransmissionStatus.TRANSMITTING
            appendLog("Phase 1 Speaker Test: Emitting ${frequencyHz.toInt()} Hz tone (${durationMs}ms @ ${cfg.sampleRate} Hz)")

            val startMs = System.currentTimeMillis()
            val ok = transmitter.playTestTone(
                frequencyHz = frequencyHz,
                sampleRate = cfg.sampleRate,
                durationMs = durationMs,
                amplitude = cfg.txAmplitude,
                onTrackConfigured = { actualTxRate, _ ->
                    _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                },
                onWaveformChunk = { chunk ->
                    val downsampled = downsampleWaveform(chunk, 96)
                    _diagnostics.update { it.copy(waveformPoints = downsampled) }
                }
            )
            val elapsed = System.currentTimeMillis() - startMs

            _diagnostics.update {
                it.copy(
                    isSpeakerToneRunning = false,
                    transmittedFrequencyHz = 0.0,
                    lastTransmissionDurationMs = elapsed
                )
            }
            _transmissionStatus.value = if (_isListening.value) {
                if (_isDiscovering.value) TransmissionStatus.DISCOVERING else TransmissionStatus.LISTENING
            } else {
                prevStatus.takeIf { it != TransmissionStatus.TRANSMITTING } ?: TransmissionStatus.IDLE
            }
            appendLog(
                if (ok) "Speaker test finished (${elapsed}ms)"
                else "Speaker test failed to initialize AudioTrack"
            )
        }
    }

    fun stopSpeakerTestTone() {
        speakerToneJob?.cancel()
        transmitter.stop()
        _diagnostics.update {
            it.copy(
                isSpeakerToneRunning = false,
                transmittedFrequencyHz = 0.0
            )
        }
        if (!_isListening.value) {
            _transmissionStatus.value = TransmissionStatus.IDLE
        }
    }

    /**
     * Enables continuous microphone capture & real-time ultrasonic DSP demodulation.
     */
    fun startListening(): Boolean {
        if (!receiver.hasRecordPermission()) {
            appendLog("Cannot start listening: RECORD_AUDIO permission not granted.")
            return false
        }
        if (_isListening.value && receiveJob?.isActive == true) {
            return true
        }

        receiveJob?.cancel()
        demodulator.reset()
        bandPassFilter.reset()
        _isListening.value = true
        _transmissionStatus.value = if (_isDiscovering.value) {
            TransmissionStatus.DISCOVERING
        } else {
            TransmissionStatus.LISTENING
        }

        val cfg = _audioConfig.value
        appendLog(
            "Receiver started @ ${cfg.sampleRate} Hz [Passband: ${cfg.bandMode.passBandLowHz.toInt()}–${cfg.bandMode.passBandHighHz.toInt()} Hz]"
        )

        receiveJob = scope.launch {
            val started = receiver.startListening(
                sampleRate = cfg.sampleRate,
                frameSize = 2048,
                onAudioSourceSelected = { srcLabel, actualRate, bufFrames ->
                    val current = _audioConfig.value
                    if (actualRate > 0 && actualRate != current.sampleRate) {
                        val syncedCfg = current.copy(sampleRate = actualRate)
                        bandPassFilter = BandPassFilter(
                            sampleRate = actualRate,
                            lowCutoffHz = syncedCfg.bandMode.passBandLowHz,
                            highCutoffHz = syncedCfg.bandMode.passBandHighHz
                        )
                        signalDetector.updateConfiguration(syncedCfg)
                        demodulator.updateConfiguration(syncedCfg)
                        appendLog("HAL sample rate fallback ($actualRate Hz); RX DSP resynchronized.")
                    }
                    _diagnostics.update {
                        it.copy(
                            activeAudioSource = srcLabel,
                            actualSampleRateHz = actualRate,
                            actualRecordingSampleRateHz = actualRate
                        )
                    }
                    appendLog("Microphone locked: $srcLabel @ $actualRate Hz (buf=$bufFrames frames)")
                },
                onSamplesCaptured = { pcmFrame, sr ->
                    processCapturedAudioFrame(pcmFrame, sr)
                }
            )

            if (!started) {
                _isListening.value = false
                _transmissionStatus.value = TransmissionStatus.IDLE
                appendLog("AudioRecord failed to initialize at ${cfg.sampleRate} Hz.")
            }
        }
        return true
    }

    fun stopListening() {
        stopDiscoveryMode()
        receiveJob?.cancel()
        receiveJob = null
        receiver.stop()
        demodulator.reset()
        fftWindowFilled = 0
        _isListening.value = false
        if (!transmitter.isTransmitting) {
            _transmissionStatus.value = TransmissionStatus.IDLE
        }
        _diagnostics.update {
            it.copy(
                activeAudioSource = "Standby (Mic Released)",
                receivedFrequencyHz = 0.0,
                carrierDetected = false
            )
        }
        appendLog("Receiver stopped and microphone released.")
    }

    private suspend fun processCapturedAudioFrame(pcmFrame: ShortArray, sampleRate: Int) {
        val currentCfg = _audioConfig.value
        val filtered = if (currentCfg.bandPassFilterEnabled) {
            bandPassFilter.process(pcmFrame)
        } else {
            DoubleArray(pcmFrame.size) { i -> pcmFrame[i] / 32768.0 }
        }

        // Maintain a full 2048-sample sliding window for consistent 23.4 Hz/bin FFT diagnostics
        val chunkLen = pcmFrame.size.coerceAtMost(2048)
        val keep = 2048 - chunkLen
        if (keep > 0) {
            System.arraycopy(fftRawWindow, chunkLen, fftRawWindow, 0, keep)
            System.arraycopy(fftFilteredWindow, chunkLen, fftFilteredWindow, 0, keep)
        }
        System.arraycopy(pcmFrame, pcmFrame.size - chunkLen, fftRawWindow, keep, chunkLen)
        System.arraycopy(filtered, filtered.size - chunkLen, fftFilteredWindow, keep, chunkLen)
        fftWindowFilled = (fftWindowFilled + chunkLen).coerceAtMost(2048)

        val searchLow = if (currentCfg.bandMode == AcousticBandMode.NEAR_ULTRASONIC) 16000.0 else 1000.0
        val searchHigh = (sampleRate / 2.0) - 150.0

        val spectrumResult = FrequencyAnalyzer.analyzeFrame(
            rawPcm = fftRawWindow,
            filteredSamples = fftFilteredWindow,
            sampleRate = sampleRate,
            freqBit0Hz = currentCfg.freqBit0Hz,
            freqBit1Hz = currentCfg.freqBit1Hz,
            freqSyncHz = currentCfg.freqSyncHz,
            searchBandLowHz = searchLow,
            searchBandHighHz = searchHigh
        )

        val detection = signalDetector.evaluateWindow(filtered)

        _diagnostics.update { state ->
            state.copy(
                receivedFrequencyHz = spectrumResult.peakFrequencyHz,
                detectedSignalStrengthDbFs = spectrumResult.rmsSignalDbFs,
                noiseFloorDb = spectrumResult.noiseFloorDb,
                snrDb = spectrumResult.snrDb,
                bit0PowerDb = detection.bit0PowerDb,
                bit1PowerDb = detection.bit1PowerDb,
                syncPowerDb = detection.syncPowerDb,
                carrierDetected = detection.carrierActive || detection.syncToneDetected,
                spectrumBars = spectrumResult.spectrumBinsDb.toList(),
                waveformPoints = spectrumResult.waveformNormalized.toList()
            )
        }

        // Half-duplex guard: ignore own speaker echo unless running explicit local loopback test
        if (suppressSelfReception) {
            return
        }

        val extractedCandidates = demodulator.appendAndExtractFrames(filtered)
        for (candidate in extractedCandidates) {
            handleDemodulatedCandidate(
                rawBytes = candidate.rawBytes,
                snrDb = maxOf(candidate.averageSnrDb, spectrumResult.snrDb),
                rssiDbFs = spectrumResult.rmsSignalDbFs,
                carrierHz = spectrumResult.peakFrequencyHz,
                isLoopback = _diagnostics.value.isLoopbackRunning
            )
        }
    }

    private suspend fun handleDemodulatedCandidate(
        rawBytes: ByteArray,
        snrDb: Double,
        rssiDbFs: Double,
        carrierHz: Double,
        isLoopback: Boolean
    ) {
        val hexStr = PacketEncoder.formatFrameHex(rawBytes)
        when (val decodeResult = PacketDecoder.decodeFrame(rawBytes)) {
            is PacketDecodeResult.Success -> {
                val packet = decodeResult.packet
                val crcHex = CRC16.formatHex(packet.crc16)
                val isRemotePeer = !isLoopback && packet.header.senderId != localDeviceId

                _diagnostics.update {
                    val nextRemoteRx = it.physicalTwoDeviceRxVerified || isRemotePeer
                    it.copy(
                        packetDetectedCount = it.packetDetectedCount + 1,
                        crcPassCount = it.crcPassCount + 1,
                        lastSyncResult = "LOCKED (0xAA 0xD3 0x91 · ${packet.header.packetType.label} msg#${packet.header.messageId})",
                        lastCrcResult = "VALID (0x$crcHex)",
                        lastDecodedResult = "[${packet.header.packetType.label}] \"${packet.payloadText}\" from ${packet.header.senderHex}",
                        lastRawFrameHex = hexStr,
                        lastRemoteSenderIdHex = if (isRemotePeer) packet.header.senderHex else it.lastRemoteSenderIdHex,
                        physicalTwoDeviceRxVerified = nextRemoteRx,
                        physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = it.simulatedDspVerified,
                            localLoopbackVerified = it.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = nextRemoteRx,
                            physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified
                        )
                    )
                }
                appendLog(
                    "RX Packet [${packet.header.packetType.label}] from ${packet.header.senderHex} " +
                        "msg#${packet.header.messageId} CRC=0x$crcHex OK: \"${packet.payloadText}\""
                )

                dispatchValidIncomingPacket(
                    packet = packet,
                    snrDb = snrDb,
                    rssiDbFs = rssiDbFs,
                    carrierHz = carrierHz,
                    isLoopback = isLoopback
                )
            }

            is PacketDecodeResult.CrcMismatch -> {
                val expHex = CRC16.formatHex(decodeResult.expectedCrc)
                val rcvHex = CRC16.formatHex(decodeResult.receivedCrc)
                _diagnostics.update {
                    it.copy(
                        packetDetectedCount = it.packetDetectedCount + 1,
                        crcFailCount = it.crcFailCount + 1,
                        lastSyncResult = "SYNC LOCKED (Header matched, payload corrupted)",
                        lastCrcResult = "CRC FAIL (Exp 0x$expHex != Got 0x$rcvHex)",
                        lastDecodedResult = "Corrupted frame rejected by CRC-16",
                        lastRawFrameHex = hexStr
                    )
                }
                appendLog("RX CRC-16 MISMATCH: expected 0x$expHex, received 0x$rcvHex. Packet dropped.")
            }

            is PacketDecodeResult.MalformedFrame -> {
                _diagnostics.update {
                    it.copy(lastSyncResult = "Malformed frame (${decodeResult.reason})")
                }
                appendLog("RX malformed frame ignored: ${decodeResult.reason}")
            }
        }
    }

    private suspend fun dispatchValidIncomingPacket(
        packet: com.soundchat.app.protocol.AcousticPacket,
        snrDb: Double,
        rssiDbFs: Double,
        carrierHz: Double,
        isLoopback: Boolean
    ) {
        val header = packet.header
        // Ignore packets addressed to a specific different node unless broadcast or loopback
        if (!isLoopback &&
            header.receiverId != PacketHeader.BROADCAST_ID &&
            header.receiverId != localDeviceId
        ) {
            return
        }

        when (header.packetType) {
            AcousticPacketType.DATA -> {
                val isNew = if (isLoopback) {
                    true
                } else {
                    ackManager.recordIncomingAndCheckIsNew(header.senderId, header.messageId)
                }

                if (isNew) {
                    val entity = ChatMessageEntity(
                        packetMessageId = header.messageId,
                        senderId = header.senderId,
                        senderHex = header.senderHex,
                        receiverId = header.receiverId,
                        receiverHex = header.receiverHex,
                        content = packet.payloadText,
                        isOutgoing = false,
                        deliveryState = MessageDeliveryState.RECEIVED_VALID.label,
                        crcHex = CRC16.formatHex(packet.crc16),
                        carrierFreqHz = carrierHz.toInt(),
                        snrDb = snrDb,
                        durationMs = (PacketHeader.TOTAL_HEADER_BYTES + header.payloadLength + 2) *
                            8L * _audioConfig.value.symbolDurationMs,
                        retryCount = 0,
                        isLoopbackTest = isLoopback
                    )
                    chatRepository.insertMessage(entity)
                } else {
                    appendLog("Duplicate DATA packet #${header.messageId} from ${header.senderHex} suppressed; re-sending ACK.")
                }

                // In real two-device communication, transmit acoustic ACK back after half-duplex guard delay
                if (!isLoopback && _audioConfig.value.requireAcknowledgement) {
                    scope.launch {
                        delay(240L)
                        transmitAckPacket(
                            targetReceiverId = header.senderId,
                            acknowledgedMessageId = header.messageId
                        )
                    }
                }
            }

            AcousticPacketType.ACK -> {
                if (isLoopback || header.senderId == localDeviceId) {
                    appendLog("Ignored self-echo/loopback ACK for msg#${header.messageId} (sender=${header.senderHex}).")
                    return
                }
                val matched = ackManager.onAckReceived(header.messageId)
                if (matched) {
                    _transmissionStatus.value = TransmissionStatus.MESSAGE_DELIVERED
                    _diagnostics.update {
                        it.copy(
                            lastAckResult = "ACK VERIFIED for msg#${header.messageId} from ${header.senderHex}",
                            lastRemoteSenderIdHex = header.senderHex,
                            lastAckMessageId = header.messageId,
                            physicalTwoDeviceAckVerified = true,
                            physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                                simulatedDspVerified = it.simulatedDspVerified,
                                localLoopbackVerified = it.localLoopbackVerified,
                                physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                                physicalTwoDeviceAckVerified = true
                            )
                        )
                    }
                    appendLog("Verified remote acoustic ACK received for msg#${header.messageId} from ${header.senderHex}!")
                }
            }

            AcousticPacketType.DISCOVERY_BEACON -> {
                val discovered = AcousticDiscovery.parseBeaconPacket(
                    packet = packet,
                    signalStrengthDbFs = rssiDbFs,
                    snrDb = snrDb,
                    detectedCarrierHz = carrierHz
                ) ?: return

                upsertDiscoveredDevice(discovered)
                if (_transmissionStatus.value == TransmissionStatus.DISCOVERING ||
                    _transmissionStatus.value == TransmissionStatus.LISTENING
                ) {
                    _transmissionStatus.value = TransmissionStatus.DEVICE_DETECTED
                }
            }

            AcousticPacketType.PAIR_REQUEST -> {
                val parsed = AcousticHandshake.parseHandshakePacket(packet)
                val incomingPeer = DiscoveredDevice(
                    deviceId = parsed.senderId,
                    deviceName = parsed.senderName,
                    signalStrengthDbFs = rssiDbFs,
                    snrDb = snrDb,
                    detectedCarrierHz = carrierHz,
                    pairingState = PairingState.INCOMING_REQUEST_PENDING,
                    sessionId = parsed.sessionId,
                    lastSeenEpochMs = System.currentTimeMillis()
                )
                upsertDiscoveredDevice(incomingPeer, preservePairingState = false)
                _transmissionStatus.value = TransmissionStatus.PAIRING
                appendLog("Incoming acoustic PAIR_REQUEST from ${incomingPeer.deviceName} (${incomingPeer.deviceHexId}) — awaiting user confirmation.")
            }

            AcousticPacketType.PAIR_CONFIRM -> {
                val parsed = AcousticHandshake.parseHandshakePacket(packet)
                val pairedPeer = DiscoveredDevice(
                    deviceId = parsed.senderId,
                    deviceName = parsed.senderName,
                    signalStrengthDbFs = rssiDbFs,
                    snrDb = snrDb,
                    detectedCarrierHz = carrierHz,
                    pairingState = PairingState.PAIRED,
                    sessionId = parsed.sessionId,
                    lastSeenEpochMs = System.currentTimeMillis()
                )
                upsertDiscoveredDevice(pairedPeer, preservePairingState = false)
                _activeSession.value = ActiveDeviceSession(
                    peerDeviceId = parsed.senderId,
                    peerDeviceName = parsed.senderName,
                    sessionId = parsed.sessionId,
                    lastRssiDbFs = rssiDbFs,
                    lastSnrDb = snrDb
                )
                _transmissionStatus.value = if (_isListening.value) {
                    TransmissionStatus.LISTENING
                } else {
                    TransmissionStatus.IDLE
                }
                appendLog("Acoustic session established with ${pairedPeer.deviceName} [${parsed.sessionId}]!")
            }

            AcousticPacketType.DISCONNECT -> {
                val parsed = AcousticHandshake.parseHandshakePacket(packet)
                if (_activeSession.value?.peerDeviceId == parsed.senderId) {
                    _activeSession.value = null
                }
                _discoveredDevices.update { list ->
                    list.map {
                        if (it.deviceId == parsed.senderId) {
                            it.copy(pairingState = PairingState.DISCONNECTED, sessionId = null)
                        } else {
                            it
                        }
                    }
                }
                appendLog("Peer ${PacketHeader.formatIdHex(parsed.senderId)} closed acoustic session.")
            }
        }
    }

    private fun upsertDiscoveredDevice(
        device: DiscoveredDevice,
        preservePairingState: Boolean = true
    ) {
        _discoveredDevices.update { current ->
            val existingIdx = current.indexOfFirst { it.deviceId == device.deviceId }
            if (existingIdx >= 0) {
                val existing = current[existingIdx]
                val updated = device.copy(
                    pairingState = if (preservePairingState && existing.pairingState != PairingState.DISCOVERED) {
                        existing.pairingState
                    } else {
                        device.pairingState
                    },
                    sessionId = device.sessionId ?: existing.sessionId
                )
                current.toMutableList().also { it[existingIdx] = updated }
            } else {
                current + device
            }
        }
    }

    /**
     * Sends a UTF-8 message over near-ultrasonic (or audible dev) sound waves.
     * Truthfully updates message status through TRANSMITTING -> WAITING_FOR_ACKNOWLEDGEMENT ->
     * RETRYING -> MESSAGE_DELIVERED or TRANSMISSION_FAILED.
     */
    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        transmitJob?.cancel()
        transmitJob = scope.launch {
            val cfg = _audioConfig.value
            val msgId = sequenceCounter.incrementAndGet() and 0xFFFF
            val targetId = _activeSession.value?.peerDeviceId ?: PacketHeader.BROADCAST_ID

            val (packet, rawFrameBytes) = PacketEncoder.encodeTextMessage(
                senderId = localDeviceId,
                receiverId = targetId,
                messageId = msgId,
                text = trimmed
            )
            val pcmWaveform = modulator.modulateFrameBytes(rawFrameBytes, cfg)
            val estDurationMs = ((pcmWaveform.size.toDouble() / cfg.sampleRate.toDouble()) * 1000.0).toLong()
            val crcHex = CRC16.formatHex(packet.crc16)

            val dbRowId = chatRepository.insertMessage(
                ChatMessageEntity(
                    packetMessageId = msgId,
                    senderId = localDeviceId,
                    senderHex = localDeviceHex,
                    receiverId = targetId,
                    receiverHex = PacketHeader.formatIdHex(targetId),
                    content = trimmed,
                    isOutgoing = true,
                    deliveryState = MessageDeliveryState.TRANSMITTING.label,
                    crcHex = crcHex,
                    carrierFreqHz = cfg.freqBit1Hz.toInt(),
                    snrDb = 0.0,
                    durationMs = estDurationMs,
                    retryCount = 0,
                    isLoopbackTest = false
                )
            )

            // Ensure receiver is running if ACK verification is enabled and permission is granted
            if (cfg.requireAcknowledgement && !_isListening.value && receiver.hasRecordPermission()) {
                startListening()
            }

            val ackTimeout = cfg.effectiveAckTimeoutMs
            val report = retryManager.executeWithRetry(
                messageId = msgId,
                requireAck = cfg.requireAcknowledgement,
                maxRetries = cfg.maxRetries,
                ackTimeoutMs = ackTimeout,
                onAttemptStart = { attempt, isRetry ->
                    _transmissionStatus.value = if (isRetry) {
                        TransmissionStatus.RETRYING
                    } else {
                        TransmissionStatus.TRANSMITTING
                    }
                    chatRepository.updateMessageStatus(
                        rowId = dbRowId,
                        newState = if (isRetry) {
                            "${MessageDeliveryState.RETRYING.label} (${attempt - 1}/${cfg.maxRetries})"
                        } else {
                            MessageDeliveryState.TRANSMITTING.label
                        },
                        retries = (attempt - 1).coerceAtLeast(0),
                        durationMs = estDurationMs
                    )

                    _diagnostics.update {
                        it.copy(
                            transmittedFrequencyHz = cfg.freqBit1Hz,
                            lastRawFrameHex = PacketEncoder.formatFrameHex(rawFrameBytes),
                            lastTransmissionDurationMs = estDurationMs
                        )
                    }
                    appendLog(
                        "TX DATA msg#$msgId (attempt $attempt) \"${packet.payloadText}\" " +
                            "[${rawFrameBytes.size}B, CRC=0x$crcHex, ${estDurationMs}ms]"
                    )

                    suppressSelfReception = true
                    val played = try {
                        transmitter.transmitPcmSamples(
                            samples = pcmWaveform,
                            sampleRate = cfg.sampleRate,
                            onTrackConfigured = { actualTxRate, _ ->
                                _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                            },
                            onChunkPlayed = { chunk ->
                                _diagnostics.update {
                                    it.copy(waveformPoints = downsampleWaveform(chunk, 96))
                                }
                            }
                        )
                    } finally {
                        delay(140L)
                        demodulator.reset()
                        suppressSelfReception = false
                        _diagnostics.update { it.copy(transmittedFrequencyHz = 0.0) }
                    }
                    played
                },
                onWaitingForAck = { attempt ->
                    _transmissionStatus.value = TransmissionStatus.WAITING_FOR_ACKNOWLEDGEMENT
                    chatRepository.updateMessageStatus(
                        rowId = dbRowId,
                        newState = MessageDeliveryState.WAITING_FOR_ACK.label,
                        retries = (attempt - 1).coerceAtLeast(0),
                        durationMs = estDurationMs
                    )
                    _diagnostics.update {
                        it.copy(lastAckResult = "Waiting for ACK msg#$msgId (timeout ${ackTimeout}ms)…")
                    }
                    appendLog("Waiting for acoustic ACK for msg#$msgId (timeout ${ackTimeout}ms)…")
                }
            )

            when (report.outcome) {
                DeliveryOutcome.DELIVERED_ACK_VERIFIED -> {
                    _transmissionStatus.value = TransmissionStatus.MESSAGE_DELIVERED
                    chatRepository.updateMessageStatus(
                        rowId = dbRowId,
                        newState = MessageDeliveryState.DELIVERED_ACK_VERIFIED.label,
                        retries = (report.attemptsUsed - 1).coerceAtLeast(0),
                        durationMs = report.totalDurationMs
                    )
                }

                DeliveryOutcome.TRANSMITTED_UNACKNOWLEDGED_MODE -> {
                    _transmissionStatus.value = if (_isListening.value) {
                        TransmissionStatus.LISTENING
                    } else {
                        TransmissionStatus.IDLE
                    }
                    chatRepository.updateMessageStatus(
                        rowId = dbRowId,
                        newState = MessageDeliveryState.SENT_UNACKNOWLEDGED.label,
                        retries = 0,
                        durationMs = report.totalDurationMs
                    )
                    _diagnostics.update {
                        it.copy(lastAckResult = "Unacknowledged mode (ACK not requested)")
                    }
                    appendLog("TX completed for msg#$msgId (Unacknowledged broadcast mode).")
                }

                DeliveryOutcome.FAILED_TIMEOUT_MAX_RETRIES,
                DeliveryOutcome.FAILED_HARDWARE_ERROR -> {
                    _transmissionStatus.value = TransmissionStatus.TRANSMISSION_FAILED
                    chatRepository.updateMessageStatus(
                        rowId = dbRowId,
                        newState = MessageDeliveryState.FAILED.label,
                        retries = (report.attemptsUsed - 1).coerceAtLeast(0),
                        durationMs = report.totalDurationMs
                    )
                    _diagnostics.update {
                        it.copy(
                            lastAckResult = "ACK TIMEOUT after ${report.attemptsUsed} attempts",
                            physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                                simulatedDspVerified = it.simulatedDspVerified,
                                localLoopbackVerified = it.localLoopbackVerified,
                                physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                                physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified,
                                failedTwoDeviceAck = true
                            )
                        )
                    }
                    appendLog("TX FAILED for msg#$msgId: No acoustic ACK received after ${report.attemptsUsed} attempts.")
                }
            }
        }
    }

    /**
     * Retries sending an existing failed or unacknowledged message from chat history.
     */
    fun retryMessage(messageEntity: ChatMessageEntity) {
        sendTextMessage(messageEntity.content)
    }

    private suspend fun transmitAckPacket(
        targetReceiverId: Int,
        acknowledgedMessageId: Int
    ) {
        val cfg = _audioConfig.value
        val (_, rawBytes) = PacketEncoder.encodeAck(
            senderId = localDeviceId,
            receiverId = targetReceiverId,
            acknowledgedMessageId = acknowledgedMessageId
        )
        val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
        appendLog("TX Acoustic ACK for msg#$acknowledgedMessageId to ${PacketHeader.formatIdHex(targetReceiverId)}")

        suppressSelfReception = true
        try {
            transmitter.transmitPcmSamples(
                samples = pcm,
                sampleRate = cfg.sampleRate,
                onTrackConfigured = { actualTxRate, _ ->
                    _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                }
            )
        } finally {
            delay(120L)
            demodulator.reset()
            suppressSelfReception = false
        }
    }

    /**
     * Local Acoustic Loopback Test (Clearly labeled as a single-device loopback test):
     * Transmits a real modulated packet through the speaker while allowing the local
     * receiver (or full DSP pipeline if mic is active) to demodulate and verify CRC-16.
     */
    fun runAcousticLoopbackTest(testPayload: String = "HI") {
        scope.launch {
            val cfg = _audioConfig.value
            val msgId = sequenceCounter.incrementAndGet() and 0xFFFF
            val (packet, rawFrameBytes) = PacketEncoder.encodeTextMessage(
                senderId = localDeviceId,
                receiverId = localDeviceId,
                messageId = msgId,
                text = testPayload
            )
            val pcmWaveform = modulator.modulateFrameBytes(rawFrameBytes, cfg)
            val durationMs = ((pcmWaveform.size.toDouble() / cfg.sampleRate.toDouble()) * 1000.0).toLong()

            _diagnostics.update {
                it.copy(
                    isLoopbackRunning = true,
                    transmittedFrequencyHz = cfg.freqBit1Hz,
                    lastLoopbackReport = "Running acoustic loopback for \"$testPayload\"…",
                    lastRawFrameHex = PacketEncoder.formatFrameHex(rawFrameBytes),
                    lastTransmissionDurationMs = durationMs
                )
            }
            _transmissionStatus.value = TransmissionStatus.TRANSMITTING
            appendLog("LOCAL LOOPBACK TEST: Emitting \"$testPayload\" (${rawFrameBytes.size}B, CRC=0x${CRC16.formatHex(packet.crc16)})")

            val wasListening = _isListening.value
            if (!wasListening && receiver.hasRecordPermission()) {
                startListening()
                delay(200L)
            }

            val packetsBefore = _diagnostics.value.crcPassCount

            // Allow self-reception during loopback test so physical speaker->mic path is tested!
            suppressSelfReception = false
            transmitter.transmitPcmSamples(
                samples = pcmWaveform,
                sampleRate = cfg.sampleRate,
                onTrackConfigured = { actualTxRate, _ ->
                    _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                },
                onChunkPlayed = { chunk ->
                    _diagnostics.update {
                        it.copy(waveformPoints = downsampleWaveform(chunk, 96))
                    }
                }
            )
            delay(350L)

            val packetsAfter = _diagnostics.value.crcPassCount
            if (packetsAfter > packetsBefore) {
                val reportMsg = "OVER-THE-AIR SPEAKER->MIC LOOPBACK PASSED: Decoded \"$testPayload\" with CRC-16 0x${CRC16.formatHex(packet.crc16)}"
                _diagnostics.update {
                    it.copy(
                        isLoopbackRunning = false,
                        transmittedFrequencyHz = 0.0,
                        lastLoopbackReport = reportMsg,
                        localLoopbackVerified = true,
                        lastValidationTierUsed = ValidationExecutionTier.LOCAL_LOOPBACK,
                        physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = it.simulatedDspVerified,
                            localLoopbackVerified = true,
                            physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified
                        )
                    )
                }
                appendLog(reportMsg)
            } else {
                // Verify DSP Modulator -> BandPassFilter -> Goertzel Demodulator -> PacketDecoder pipeline directly
                // and report honestly whether speaker-to-mic physical path attenuated or if mic permission was missing.
                val candidates = demodulator.demodulatePcmBuffer(pcmWaveform)
                val firstCandidate = candidates.firstOrNull()
                val dspVerified = firstCandidate != null &&
                    PacketDecoder.decodeFrame(firstCandidate.rawBytes) is PacketDecodeResult.Success

                if (firstCandidate != null && dspVerified) {
                    handleDemodulatedCandidate(
                        rawBytes = firstCandidate.rawBytes,
                        snrDb = firstCandidate.averageSnrDb,
                        rssiDbFs = -18.0,
                        carrierHz = cfg.freqBit1Hz,
                        isLoopback = true
                    )
                }

                val reason = if (!receiver.hasRecordPermission()) {
                    "DSP Pipeline Verified (\"$testPayload\", CRC 0x${CRC16.formatHex(packet.crc16)}). Grant Mic permission for physical Speaker->Mic capture."
                } else {
                    "DSP Pipeline Verified (\"$testPayload\", CRC 0x${CRC16.formatHex(packet.crc16)}). Raise device media volume >75% for physical Speaker->Mic pickup."
                }

                _diagnostics.update {
                    val simOk = it.simulatedDspVerified || dspVerified
                    it.copy(
                        isLoopbackRunning = false,
                        transmittedFrequencyHz = 0.0,
                        lastLoopbackReport = reason,
                        simulatedDspVerified = simOk,
                        lastValidationTierUsed = ValidationExecutionTier.SIMULATED_DSP,
                        physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = simOk,
                            localLoopbackVerified = it.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified
                        )
                    )
                }
                appendLog("Loopback result: $reason")
            }

            _transmissionStatus.value = if (_isListening.value) {
                TransmissionStatus.LISTENING
            } else {
                TransmissionStatus.IDLE
            }
        }
    }

    /**
     * Sound-based Device Discovery (Section 6):
     * Enables listening and periodically broadcasts short acoustic discovery beacons.
     */
    fun startDiscoveryMode(advertiseBeacon: Boolean = true): Boolean {
        _isBeaconAdvertising.value = advertiseBeacon
        _isDiscovering.value = true
        if (receiver.hasRecordPermission()) {
            startListening()
        }
        _transmissionStatus.value = TransmissionStatus.DISCOVERING
        appendLog("Acoustic Discovery Mode enabled (Advertise beacon: $advertiseBeacon)")

        discoveryBeaconJob?.cancel()
        if (advertiseBeacon) {
            discoveryBeaconJob = scope.launch {
                while (isActive && _isDiscovering.value) {
                    broadcastSingleDiscoveryBeacon()
                    delay(6500L)
                }
            }
        }
        return true
    }

    fun stopDiscoveryMode() {
        _isDiscovering.value = false
        discoveryBeaconJob?.cancel()
        discoveryBeaconJob = null
        if (_isListening.value) {
            _transmissionStatus.value = TransmissionStatus.LISTENING
        } else {
            _transmissionStatus.value = TransmissionStatus.IDLE
        }
    }

    fun broadcastSingleDiscoveryBeacon() {
        scope.launch {
            val cfg = _audioConfig.value
            val seq = sequenceCounter.incrementAndGet() and 0xFFFF
            val (_, rawBytes) = AcousticDiscovery.createBeaconFrame(
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceName,
                sequenceNumber = seq,
                isUltrasonic = cfg.bandMode == AcousticBandMode.NEAR_ULTRASONIC
            )
            val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
            appendLog("TX Acoustic Discovery Beacon [ID: $localDeviceHex · Name: $localDeviceName]")

            suppressSelfReception = true
            try {
                _diagnostics.update { it.copy(transmittedFrequencyHz = cfg.freqSyncHz) }
                transmitter.transmitPcmSamples(
                    samples = pcm,
                    sampleRate = cfg.sampleRate,
                    onTrackConfigured = { actualTxRate, _ ->
                        _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                    }
                )
            } finally {
                delay(100L)
                demodulator.reset()
                suppressSelfReception = false
                _diagnostics.update { it.copy(transmittedFrequencyHz = 0.0) }
            }
        }
    }

    /**
     * Sends an acoustic PAIR_REQUEST frame to a discovered device.
     */
    fun sendPairingRequest(targetDevice: DiscoveredDevice) {
        scope.launch {
            val cfg = _audioConfig.value
            val seq = sequenceCounter.incrementAndGet() and 0xFFFF
            val proposedSession = AcousticHandshake.generateSessionId(localDeviceId, targetDevice.deviceId)

            _transmissionStatus.value = TransmissionStatus.PAIRING
            upsertDiscoveredDevice(
                targetDevice.copy(
                    pairingState = PairingState.OUTGOING_REQUEST_SENT,
                    sessionId = proposedSession
                ),
                preservePairingState = false
            )

            val (_, rawBytes) = AcousticHandshake.createPairRequestFrame(
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceName,
                targetDeviceId = targetDevice.deviceId,
                messageId = seq,
                proposedSessionId = proposedSession
            )
            val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
            appendLog("TX Acoustic PAIR_REQUEST to ${targetDevice.deviceName} (${targetDevice.deviceHexId}) [$proposedSession]")

            suppressSelfReception = true
            try {
                transmitter.transmitPcmSamples(
                    samples = pcm,
                    sampleRate = cfg.sampleRate,
                    onTrackConfigured = { actualTxRate, _ ->
                        _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                    }
                )
            } finally {
                delay(100L)
                demodulator.reset()
                suppressSelfReception = false
            }
        }
    }

    /**
     * Explicitly confirms an incoming acoustic pairing request from a nearby device.
     */
    fun confirmIncomingPairing(peerDevice: DiscoveredDevice) {
        scope.launch {
            val cfg = _audioConfig.value
            val seq = sequenceCounter.incrementAndGet() and 0xFFFF
            val sessionId = peerDevice.sessionId
                ?: AcousticHandshake.generateSessionId(localDeviceId, peerDevice.deviceId)

            _transmissionStatus.value = TransmissionStatus.PAIRING
            val (_, rawBytes) = AcousticHandshake.createPairConfirmFrame(
                localDeviceId = localDeviceId,
                localDeviceName = localDeviceName,
                targetDeviceId = peerDevice.deviceId,
                messageId = seq,
                acceptedSessionId = sessionId
            )
            val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
            appendLog("TX Acoustic PAIR_CONFIRM to ${peerDevice.deviceName} (${peerDevice.deviceHexId}) [$sessionId]")

            suppressSelfReception = true
            try {
                transmitter.transmitPcmSamples(
                    samples = pcm,
                    sampleRate = cfg.sampleRate,
                    onTrackConfigured = { actualTxRate, _ ->
                        _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                    }
                )
            } finally {
                delay(100L)
                demodulator.reset()
                suppressSelfReception = false
            }

            upsertDiscoveredDevice(
                peerDevice.copy(
                    pairingState = PairingState.PAIRED,
                    sessionId = sessionId
                ),
                preservePairingState = false
            )
            _activeSession.value = ActiveDeviceSession(
                peerDeviceId = peerDevice.deviceId,
                peerDeviceName = peerDevice.deviceName,
                sessionId = sessionId,
                lastRssiDbFs = peerDevice.signalStrengthDbFs,
                lastSnrDb = peerDevice.snrDb
            )
            _transmissionStatus.value = if (_isListening.value) {
                TransmissionStatus.LISTENING
            } else {
                TransmissionStatus.IDLE
            }
        }
    }

    fun declineIncomingPairing(peerDevice: DiscoveredDevice) {
        upsertDiscoveredDevice(
            peerDevice.copy(pairingState = PairingState.DISCONNECTED, sessionId = null),
            preservePairingState = false
        )
        appendLog("Declined pairing request from ${peerDevice.deviceHexId}.")
    }

    fun disconnectActiveSession() {
        val current = _activeSession.value ?: return
        scope.launch {
            val cfg = _audioConfig.value
            val seq = sequenceCounter.incrementAndGet() and 0xFFFF
            val (_, rawBytes) = AcousticHandshake.createDisconnectFrame(
                localDeviceId = localDeviceId,
                targetDeviceId = current.peerDeviceId,
                messageId = seq,
                sessionId = current.sessionId
            )
            val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
            suppressSelfReception = true
            try {
                transmitter.transmitPcmSamples(
                    samples = pcm,
                    sampleRate = cfg.sampleRate,
                    onTrackConfigured = { actualTxRate, _ ->
                        _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                    }
                )
            } finally {
                delay(100L)
                demodulator.reset()
                suppressSelfReception = false
            }
            _activeSession.value = null
            _discoveredDevices.update { list ->
                list.map {
                    if (it.deviceId == current.peerDeviceId) {
                        it.copy(pairingState = PairingState.DISCONNECTED, sessionId = null)
                    } else {
                        it
                    }
                }
            }
            appendLog("Disconnected acoustic session [${current.sessionId}].")
        }
    }

    fun clearChatHistory() {
        scope.launch {
            chatRepository.clearAllHistory()
            ackManager.clearHistory()
            appendLog("Cleared local Room chat history.")
        }
    }

    // =========================================================================
    // PHASE 7: PHYSICAL VALIDATION, DISTANCE LOG, LENGTH STRESS & RELIABILITY
    // =========================================================================

    fun recordDistanceSnapshot(
        distanceCm: Int,
        tier: ValidationExecutionTier,
        notes: String = ""
    ) {
        val snap = _diagnostics.value
        val bandLabel = if (_audioConfig.value.bandMode == AcousticBandMode.NEAR_ULTRASONIC) {
            "18.75/19.35 kHz"
        } else {
            "2.4/3.0 kHz"
        }
        val crcOk = snap.lastCrcResult.startsWith("VALID")
        val remoteAck = tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL && snap.physicalTwoDeviceAckVerified
        val outcomeLabel = when (tier) {
            ValidationExecutionTier.SIMULATED_DSP -> "DSP Snapshot Only"
            ValidationExecutionTier.LOCAL_LOOPBACK -> if (crcOk) "Loopback RX OK" else "Loopback Pending"
            ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> when {
                remoteAck -> "2-Device ACK OK"
                crcOk -> "RX OK (No Remote ACK)"
                else -> "Awaiting Valid RX/ACK"
            }
        }
        val entry = DistanceTestEntry(
            id = System.currentTimeMillis(),
            timestampFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
            distanceCm = distanceCm.coerceIn(1, 2000),
            tier = tier,
            bandLabel = bandLabel,
            measuredPeakFreqHz = snap.receivedFrequencyHz,
            signalLevelDbFs = snap.detectedSignalStrengthDbFs,
            snrDb = snap.snrDb,
            syncStatus = snap.lastSyncResult,
            crcPassed = crcOk,
            ackVerifiedByRemote = remoteAck,
            latencyMs = snap.lastTransmissionDurationMs,
            outcomeLabel = outcomeLabel,
            notes = notes.trim()
        )
        _diagnostics.update {
            it.copy(distanceTestLogs = (listOf(entry) + it.distanceTestLogs).take(30))
        }
        appendLog("Distance Log @ ${entry.distanceCm}cm [${tier.shortLabel}]: ${entry.outcomeLabel} (SNR=${String.format(Locale.US, "%.1f", entry.snrDb)}dB)")
    }

    fun runDistanceProbeTest(
        distanceCm: Int,
        tier: ValidationExecutionTier,
        notes: String = ""
    ) {
        validationSuiteJob?.cancel()
        validationSuiteJob = scope.launch {
            _diagnostics.update {
                it.copy(
                    isDistanceProbeRunning = true,
                    lastValidationTierUsed = tier
                )
            }
            try {
                val cfg = _audioConfig.value
                val msgId = sequenceCounter.incrementAndGet() and 0xFFFF
                val probeText = "DIST_${distanceCm}CM"
                val bandLabel = if (cfg.bandMode == AcousticBandMode.NEAR_ULTRASONIC) "18.75/19.35 kHz" else "2.4/3.0 kHz"
                val startMs = System.currentTimeMillis()

                when (tier) {
                    ValidationExecutionTier.SIMULATED_DSP -> {
                        val res = AcousticValidationRunner.runSimulatedFrameVerification(
                            payload = probeText,
                            messageId = msgId,
                            senderId = localDeviceId,
                            receiverId = localDeviceId,
                            config = cfg
                        )
                        val passed = res.crcPassed && res.decodedMatches
                        _diagnostics.update {
                            val simOk = it.simulatedDspVerified || passed
                            it.copy(
                                simulatedDspVerified = simOk,
                                physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                                    simulatedDspVerified = simOk,
                                    localLoopbackVerified = it.localLoopbackVerified,
                                    physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                                    physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified
                                )
                            )
                        }
                        val entry = DistanceTestEntry(
                            id = System.currentTimeMillis(),
                            timestampFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            distanceCm = distanceCm,
                            tier = tier,
                            bandLabel = bandLabel,
                            measuredPeakFreqHz = cfg.freqBit1Hz,
                            signalLevelDbFs = -12.0,
                            snrDb = res.measuredSnrDb,
                            syncStatus = if (passed) "SIM SYNC 0xAA D3 91 OK" else "SIM SYNC FAIL",
                            crcPassed = res.crcPassed,
                            ackVerifiedByRemote = false,
                            latencyMs = res.airDurationMs,
                            outcomeLabel = if (passed) "Simulated DSP OK" else "Simulated DSP FAIL",
                            notes = notes.ifBlank { "In-memory only" }
                        )
                        _diagnostics.update {
                            it.copy(distanceTestLogs = (listOf(entry) + it.distanceTestLogs).take(30))
                        }
                    }

                    ValidationExecutionTier.LOCAL_LOOPBACK -> {
                        val (crcOk, latency, snr) = executeSingleAcousticLoopback(probeText, msgId, cfg)
                        val snap = _diagnostics.value
                        val entry = DistanceTestEntry(
                            id = System.currentTimeMillis(),
                            timestampFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            distanceCm = distanceCm,
                            tier = tier,
                            bandLabel = bandLabel,
                            measuredPeakFreqHz = snap.receivedFrequencyHz,
                            signalLevelDbFs = snap.detectedSignalStrengthDbFs,
                            snrDb = maxOf(snr, snap.snrDb),
                            syncStatus = snap.lastSyncResult,
                            crcPassed = crcOk,
                            ackVerifiedByRemote = false,
                            latencyMs = latency,
                            outcomeLabel = if (crcOk) "Loopback RX OK" else "Loopback No RX",
                            notes = notes.ifBlank { "Single-device Speaker->Mic" }
                        )
                        _diagnostics.update {
                            it.copy(distanceTestLogs = (listOf(entry) + it.distanceTestLogs).take(30))
                        }
                    }

                    ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> {
                        val outcome = executeSingleTwoDeviceWithAck(probeText, msgId, cfg, maxRetries = 1)
                        val snap = _diagnostics.value
                        val entry = DistanceTestEntry(
                            id = System.currentTimeMillis(),
                            timestampFormatted = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
                            distanceCm = distanceCm,
                            tier = tier,
                            bandLabel = bandLabel,
                            measuredPeakFreqHz = snap.receivedFrequencyHz,
                            signalLevelDbFs = snap.detectedSignalStrengthDbFs,
                            snrDb = snap.snrDb,
                            syncStatus = snap.lastSyncResult,
                            crcPassed = outcome.remoteAckVerified,
                            ackVerifiedByRemote = outcome.remoteAckVerified,
                            latencyMs = System.currentTimeMillis() - startMs,
                            outcomeLabel = if (outcome.remoteAckVerified) "2-Device ACK PASSED" else "No Remote ACK",
                            notes = notes.ifBlank { "Two-device over air" }
                        )
                        _diagnostics.update {
                            it.copy(distanceTestLogs = (listOf(entry) + it.distanceTestLogs).take(30))
                        }
                    }
                }
            } finally {
                _diagnostics.update {
                    it.copy(
                        isDistanceProbeRunning = false,
                        transmittedFrequencyHz = 0.0
                    )
                }
            }
        }
    }

    fun clearDistanceTestLogs() {
        _diagnostics.update { it.copy(distanceTestLogs = emptyList()) }
    }

    fun runMessageLengthStressTest(tier: ValidationExecutionTier) {
        validationSuiteJob?.cancel()
        validationSuiteJob = scope.launch {
            val payloads = AcousticValidationRunner.STRESS_PAYLOADS
            val cfg = _audioConfig.value
            val results = mutableListOf<LengthStressItemResult>()
            var anyTwoDeviceFailed = false

            _diagnostics.update {
                it.copy(
                    lastValidationTierUsed = tier,
                    lengthStressReport = LengthStressTestReport(
                        isRunning = true,
                        tier = tier,
                        currentStepIndex = 0,
                        totalSteps = payloads.size,
                        summaryStatus = "Running 2 B -> 64 B stress suite (${tier.shortLabel})…",
                        items = emptyList()
                    ),
                    physicalValidationStatus = if (tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL) {
                        AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = it.simulatedDspVerified,
                            localLoopbackVerified = it.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified,
                            inProgress = true
                        )
                    } else {
                        it.physicalValidationStatus
                    }
                )
            }
            appendLog("Phase 7 Message-Length Stress Test started [${tier.shortLabel}] (2B..64B)")

            try {
                for ((idx, payload) in payloads.withIndex()) {
                    if (!isActive) break
                    val msgId = sequenceCounter.incrementAndGet() and 0xFFFF
                    val byteCount = payload.toByteArray(Charsets.UTF_8).size

                    val itemResult: LengthStressItemResult = when (tier) {
                        ValidationExecutionTier.SIMULATED_DSP -> {
                            val sim = AcousticValidationRunner.runSimulatedFrameVerification(
                                payload = payload,
                                messageId = msgId,
                                senderId = localDeviceId,
                                receiverId = localDeviceId,
                                config = cfg
                            )
                            val ok = sim.crcPassed && sim.decodedMatches
                            LengthStressItemResult(
                                payloadBytes = sim.payloadBytes,
                                totalFrameBytes = sim.totalFrameBytes,
                                samplePayloadPreview = payload.take(14),
                                airDurationMs = sim.airDurationMs,
                                measuredSnrDb = sim.measuredSnrDb,
                                crcPassed = sim.crcPassed,
                                remoteAckVerified = false,
                                outcomeLabel = if (ok) "SIM OK" else "SIM FAIL"
                            )
                        }

                        ValidationExecutionTier.LOCAL_LOOPBACK -> {
                            val (crcOk, durationMs, snrDb) = executeSingleAcousticLoopback(payload, msgId, cfg)
                            delay(300L)
                            LengthStressItemResult(
                                payloadBytes = byteCount,
                                totalFrameBytes = PacketHeader.TOTAL_HEADER_BYTES + byteCount + PacketHeader.CRC_BYTES,
                                samplePayloadPreview = payload.take(14),
                                airDurationMs = durationMs,
                                measuredSnrDb = snrDb,
                                crcPassed = crcOk,
                                remoteAckVerified = false,
                                outcomeLabel = if (crcOk) "LOOPBACK OK" else "LOOPBACK MISS"
                            )
                        }

                        ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> {
                            val outcome = executeSingleTwoDeviceWithAck(payload, msgId, cfg, maxRetries = 1)
                            if (!outcome.remoteAckVerified) anyTwoDeviceFailed = true
                            delay(450L)
                            LengthStressItemResult(
                                payloadBytes = byteCount,
                                totalFrameBytes = PacketHeader.TOTAL_HEADER_BYTES + byteCount + PacketHeader.CRC_BYTES,
                                samplePayloadPreview = payload.take(14),
                                airDurationMs = outcome.elapsedMs,
                                measuredSnrDb = _diagnostics.value.snrDb,
                                crcPassed = outcome.remoteAckVerified,
                                remoteAckVerified = outcome.remoteAckVerified,
                                outcomeLabel = if (outcome.remoteAckVerified) "ACK VERIFIED" else "NO REMOTE ACK"
                            )
                        }
                    }

                    results.add(itemResult)
                    _diagnostics.update {
                        it.copy(
                            lengthStressReport = it.lengthStressReport.copy(
                                currentStepIndex = idx + 1,
                                items = results.toList(),
                                summaryStatus = "Completed ${idx + 1}/${payloads.size} (${byteCount}B -> ${itemResult.outcomeLabel})"
                            )
                        )
                    }
                }

                val passCount = when (tier) {
                    ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> results.count { it.remoteAckVerified }
                    else -> results.count { it.crcPassed }
                }
                val finalSummary = "${tier.shortLabel}: $passCount / ${results.size} payload lengths passed"
                _diagnostics.update { state ->
                    val nextSimOk = state.simulatedDspVerified ||
                        (tier == ValidationExecutionTier.SIMULATED_DSP && passCount == results.size && results.isNotEmpty())
                    state.copy(
                        simulatedDspVerified = nextSimOk,
                        lengthStressReport = state.lengthStressReport.copy(
                            isRunning = false,
                            summaryStatus = finalSummary
                        ),
                        physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = nextSimOk,
                            localLoopbackVerified = state.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = state.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = state.physicalTwoDeviceAckVerified,
                            failedTwoDeviceAck = tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL && anyTwoDeviceFailed
                        )
                    )
                }
                appendLog("Length Stress Test finished: $finalSummary")
            } finally {
                _diagnostics.update {
                    it.copy(
                        isLoopbackRunning = false,
                        transmittedFrequencyHz = 0.0,
                        lengthStressReport = it.lengthStressReport.copy(isRunning = false)
                    )
                }
            }
        }
    }

    fun runRepeatedReliabilityTest(
        repetitions: Int = 5,
        tier: ValidationExecutionTier,
        payload: String = "HI"
    ) {
        val count = repetitions.coerceIn(1, 20)
        validationSuiteJob?.cancel()
        validationSuiteJob = scope.launch {
            val cfg = _audioConfig.value
            val trials = mutableListOf<ReliabilityTrialEntry>()
            var crcPass = 0
            var remoteAckPass = 0
            var failCount = 0

            _diagnostics.update {
                it.copy(
                    lastValidationTierUsed = tier,
                    reliabilityReport = RepeatedReliabilityReport(
                        isRunning = true,
                        tier = tier,
                        totalPlanned = count,
                        completedCount = 0,
                        summaryStatus = "Running $count repeated trials (${tier.shortLabel})…"
                    ),
                    physicalValidationStatus = if (tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL) {
                        AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = it.simulatedDspVerified,
                            localLoopbackVerified = it.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = it.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = it.physicalTwoDeviceAckVerified,
                            inProgress = true
                        )
                    } else {
                        it.physicalValidationStatus
                    }
                )
            }
            appendLog("Phase 7 Repeated Reliability Test started: $count x \"$payload\" [${tier.shortLabel}]")

            try {
                for (i in 1..count) {
                    if (!isActive) break
                    val msgId = sequenceCounter.incrementAndGet() and 0xFFFF
                    val trial: ReliabilityTrialEntry = when (tier) {
                        ValidationExecutionTier.SIMULATED_DSP -> {
                            val sim = AcousticValidationRunner.runSimulatedFrameVerification(
                                payload = payload,
                                messageId = msgId,
                                senderId = localDeviceId,
                                receiverId = localDeviceId,
                                config = cfg
                            )
                            val ok = sim.crcPassed && sim.decodedMatches
                            if (ok) crcPass++ else failCount++
                            ReliabilityTrialEntry(
                                trialNumber = i,
                                messageId = msgId,
                                crcPassed = sim.crcPassed,
                                remoteAckVerified = false,
                                measuredSnrDb = sim.measuredSnrDb,
                                latencyMs = sim.airDurationMs,
                                outcomeLabel = if (ok) "SIM OK" else "SIM FAIL"
                            )
                        }

                        ValidationExecutionTier.LOCAL_LOOPBACK -> {
                            val (crcOk, latency, snr) = executeSingleAcousticLoopback(payload, msgId, cfg)
                            if (crcOk) crcPass++ else failCount++
                            delay(350L)
                            ReliabilityTrialEntry(
                                trialNumber = i,
                                messageId = msgId,
                                crcPassed = crcOk,
                                remoteAckVerified = false,
                                measuredSnrDb = snr,
                                latencyMs = latency,
                                outcomeLabel = if (crcOk) "LOOPBACK OK" else "MISS"
                            )
                        }

                        ValidationExecutionTier.TWO_DEVICE_PHYSICAL -> {
                            val outcome = executeSingleTwoDeviceWithAck(payload, msgId, cfg, maxRetries = 0)
                            if (outcome.remoteAckVerified) {
                                crcPass++
                                remoteAckPass++
                            } else {
                                failCount++
                            }
                            delay(450L)
                            ReliabilityTrialEntry(
                                trialNumber = i,
                                messageId = msgId,
                                crcPassed = outcome.remoteAckVerified,
                                remoteAckVerified = outcome.remoteAckVerified,
                                measuredSnrDb = _diagnostics.value.snrDb,
                                latencyMs = outcome.elapsedMs,
                                outcomeLabel = if (outcome.remoteAckVerified) "ACK OK" else "TIMEOUT"
                            )
                        }
                    }

                    trials.add(trial)
                    val avgLat = if (trials.isNotEmpty()) trials.map { it.latencyMs }.average().toLong() else 0L
                    val avgSnr = if (trials.isNotEmpty()) trials.map { it.measuredSnrDb }.average() else 0.0
                    _diagnostics.update {
                        it.copy(
                            reliabilityReport = RepeatedReliabilityReport(
                                isRunning = i < count,
                                tier = tier,
                                totalPlanned = count,
                                completedCount = trials.size,
                                crcPassCount = crcPass,
                                remoteAckSuccessCount = remoteAckPass,
                                failedOrTimeoutCount = failCount,
                                averageLatencyMs = avgLat,
                                averageSnrDb = avgSnr,
                                summaryStatus = "Trial $i/$count · Pass=${
                                    if (tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL) remoteAckPass else crcPass
                                }/$i",
                                trials = trials.toList()
                            )
                        )
                    }
                }

                val summary = if (tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL) {
                    "2-Device ACK: $remoteAckPass/${trials.size} passed"
                } else {
                    "${tier.shortLabel}: $crcPass/${trials.size} passed"
                }

                _diagnostics.update { state ->
                    val nextSimOk = state.simulatedDspVerified ||
                        (tier == ValidationExecutionTier.SIMULATED_DSP && crcPass == trials.size && trials.isNotEmpty())
                    state.copy(
                        simulatedDspVerified = nextSimOk,
                        reliabilityReport = state.reliabilityReport.copy(
                            isRunning = false,
                            summaryStatus = summary
                        ),
                        physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                            simulatedDspVerified = nextSimOk,
                            localLoopbackVerified = state.localLoopbackVerified,
                            physicalTwoDeviceRxVerified = state.physicalTwoDeviceRxVerified,
                            physicalTwoDeviceAckVerified = state.physicalTwoDeviceAckVerified,
                            failedTwoDeviceAck = tier == ValidationExecutionTier.TWO_DEVICE_PHYSICAL && remoteAckPass == 0 && failCount > 0
                        )
                    )
                }
                appendLog("Repeated Reliability Test complete: $summary")
            } finally {
                _diagnostics.update {
                    it.copy(
                        isLoopbackRunning = false,
                        transmittedFrequencyHz = 0.0,
                        reliabilityReport = it.reliabilityReport.copy(isRunning = false)
                    )
                }
            }
        }
    }

    fun cancelValidationSuites() {
        validationSuiteJob?.cancel()
        validationSuiteJob = null
        transmitter.stop()
        _diagnostics.update {
            it.copy(
                isDistanceProbeRunning = false,
                isLoopbackRunning = false,
                transmittedFrequencyHz = 0.0,
                lengthStressReport = it.lengthStressReport.copy(
                    isRunning = false,
                    summaryStatus = "Cancelled by user"
                ),
                reliabilityReport = it.reliabilityReport.copy(
                    isRunning = false,
                    summaryStatus = "Cancelled by user"
                )
            )
        }
        appendLog("Phase 7 validation suite cancelled.")
    }

    fun resetPhysicalValidationStatus() {
        _diagnostics.update {
            it.copy(
                simulatedDspVerified = false,
                localLoopbackVerified = false,
                physicalTwoDeviceRxVerified = false,
                physicalTwoDeviceAckVerified = false,
                physicalValidationStatus = PhysicalValidationStatus.NOT_RUN,
                lastValidationTierUsed = null,
                lastSyncResult = "Awaiting preamble/sync (0xAA 0xD3 0x91)",
                lastCrcResult = "NONE",
                lastAckResult = "No ACK transaction yet",
                lastRemoteSenderIdHex = "None",
                lastAckMessageId = null
            )
        }
        appendLog("Reset Phase 7/8 physical validation flags.")
    }

    private data class TwoDeviceProbeOutcome(
        val remoteAckVerified: Boolean,
        val elapsedMs: Long
    )

    private suspend fun executeSingleAcousticLoopback(
        payload: String,
        msgId: Int,
        cfg: AudioConfiguration
    ): Triple<Boolean, Long, Double> {
        val (packet, rawBytes) = PacketEncoder.encodeTextMessage(
            senderId = localDeviceId,
            receiverId = localDeviceId,
            messageId = msgId,
            text = payload
        )
        val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
        val startMs = System.currentTimeMillis()
        if (!_isListening.value && receiver.hasRecordPermission()) {
            startListening()
            delay(200L)
        }
        val passBefore = _diagnostics.value.crcPassCount
        suppressSelfReception = false
        _diagnostics.update {
            it.copy(
                isLoopbackRunning = true,
                transmittedFrequencyHz = cfg.freqBit1Hz,
                lastRawFrameHex = PacketEncoder.formatFrameHex(rawBytes)
            )
        }
        transmitter.transmitPcmSamples(
            samples = pcm,
            sampleRate = cfg.sampleRate,
            onTrackConfigured = { actualTxRate, _ ->
                _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
            }
        )
        delay(350L)
        val elapsed = System.currentTimeMillis() - startMs
        val passedOverAirMic = _diagnostics.value.crcPassCount > passBefore
        _diagnostics.update { state ->
            val nextLoopback = state.localLoopbackVerified || passedOverAirMic
            state.copy(
                isLoopbackRunning = false,
                transmittedFrequencyHz = 0.0,
                lastTransmissionDurationMs = elapsed,
                localLoopbackVerified = nextLoopback,
                lastLoopbackReport = if (passedOverAirMic) {
                    "OVER-THE-AIR SPEAKER->MIC LOOPBACK PASSED (\"$payload\", CRC=0x${CRC16.formatHex(packet.crc16)})"
                } else {
                    "Loopback did not decode over mic for \"$payload\" (check volume/mic permission)."
                },
                physicalValidationStatus = AcousticValidationRunner.resolvePhysicalValidationStatus(
                    simulatedDspVerified = state.simulatedDspVerified,
                    localLoopbackVerified = nextLoopback,
                    physicalTwoDeviceRxVerified = state.physicalTwoDeviceRxVerified,
                    physicalTwoDeviceAckVerified = state.physicalTwoDeviceAckVerified
                )
            )
        }
        return Triple(passedOverAirMic, elapsed, _diagnostics.value.snrDb)
    }

    private suspend fun executeSingleTwoDeviceWithAck(
        payload: String,
        msgId: Int,
        cfg: AudioConfiguration,
        maxRetries: Int
    ): TwoDeviceProbeOutcome {
        val targetId = _activeSession.value?.peerDeviceId ?: PacketHeader.BROADCAST_ID
        val (_, rawBytes) = PacketEncoder.encodeTextMessage(
            senderId = localDeviceId,
            receiverId = targetId,
            messageId = msgId,
            text = payload
        )
        val pcm = modulator.modulateFrameBytes(rawBytes, cfg)
        val estDurationMs = ((pcm.size.toDouble() / cfg.sampleRate.toDouble()) * 1000.0).toLong()

        if (!_isListening.value && receiver.hasRecordPermission()) {
            startListening()
            delay(200L)
        }

        val startMs = System.currentTimeMillis()
        val ackTimeout = cfg.effectiveAckTimeoutMs
        val report = retryManager.executeWithRetry(
            messageId = msgId,
            requireAck = true,
            maxRetries = maxRetries,
            ackTimeoutMs = ackTimeout,
            onAttemptStart = { _, _ ->
                _transmissionStatus.value = TransmissionStatus.TRANSMITTING
                _diagnostics.update {
                    it.copy(
                        transmittedFrequencyHz = cfg.freqBit1Hz,
                        lastRawFrameHex = PacketEncoder.formatFrameHex(rawBytes),
                        lastTransmissionDurationMs = estDurationMs
                    )
                }
                suppressSelfReception = true
                try {
                    transmitter.transmitPcmSamples(
                        samples = pcm,
                        sampleRate = cfg.sampleRate,
                        onTrackConfigured = { actualTxRate, _ ->
                            _diagnostics.update { it.copy(actualPlaybackSampleRateHz = actualTxRate) }
                        }
                    )
                } finally {
                    delay(140L)
                    demodulator.reset()
                    suppressSelfReception = false
                    _diagnostics.update { it.copy(transmittedFrequencyHz = 0.0) }
                }
            },
            onWaitingForAck = {
                _transmissionStatus.value = TransmissionStatus.WAITING_FOR_ACKNOWLEDGEMENT
                _diagnostics.update {
                    it.copy(lastAckResult = "Waiting for remote ACK msg#$msgId (${ackTimeout}ms)…")
                }
            }
        )
        if (report.outcome == DeliveryOutcome.DELIVERED_ACK_VERIFIED) {
            delay(30L)
        }
        val ackVerified = report.outcome == DeliveryOutcome.DELIVERED_ACK_VERIFIED &&
            _diagnostics.value.physicalTwoDeviceAckVerified
        _transmissionStatus.value = if (_isListening.value) TransmissionStatus.LISTENING else TransmissionStatus.IDLE
        return TwoDeviceProbeOutcome(
            remoteAckVerified = ackVerified,
            elapsedMs = System.currentTimeMillis() - startMs
        )
    }

    fun clearDiagnosticLogs() {
        _diagnostics.update {
            it.copy(
                packetDetectedCount = 0,
                crcPassCount = 0,
                crcFailCount = 0,
                lastSyncResult = "Awaiting preamble/sync (0xAA 0xD3 0x91)",
                lastCrcResult = "NONE",
                lastAckResult = "No ACK transaction yet",
                lastDecodedResult = "Awaiting acoustic frame…",
                lastRawFrameHex = "—",
                eventLogs = emptyList()
            )
        }
    }

    fun shutdown() {
        validationSuiteJob?.cancel()
        stopSpeakerTestTone()
        stopListening()
        transmitter.stop()
        ackManager.cancelAll()
    }

    private fun downsampleWaveform(chunk: FloatArray, targetPoints: Int): List<Float> {
        if (chunk.isEmpty()) return List(targetPoints) { 0f }
        val step = (chunk.size.toFloat() / targetPoints).coerceAtLeast(1f)
        return List(targetPoints) { idx ->
            val src = (idx * step).toInt().coerceIn(0, chunk.size - 1)
            chunk[src].coerceIn(-1f, 1f)
        }
    }

    private fun appendLog(entry: String) {
        val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        val line = "[$timeFmt] $entry"
        _diagnostics.update { state ->
            val updated = (listOf(line) + state.eventLogs).take(60)
            state.copy(eventLogs = updated)
        }
    }

    private fun generateStableDeviceId(): Int {
        return try {
            val prefs = appContext.getSharedPreferences("soundchat_node_prefs", Context.MODE_PRIVATE)
            val saved = prefs.getInt("stable_device_id", -1)
            if (saved in 0x1001..0xFFFE) {
                saved
            } else {
                val generated = (Random.nextInt(0x1001, 0xFFFE) and 0xFFFE).coerceIn(0x1001, 0xFFFE)
                prefs.edit().putInt("stable_device_id", generated).apply()
                generated
            }
        } catch (_: Exception) {
            val fallback = (abs((Build.MODEL + Build.MANUFACTURER + System.nanoTime()).hashCode()) and 0xFFFE)
            fallback.coerceIn(0x1001, 0xFFFE)
        }
    }
}

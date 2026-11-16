package com.soundchat.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soundchat.app.audio.AcousticBandMode
import com.soundchat.app.audio.AudioConfiguration
import com.soundchat.app.audio.DeviceCapabilityReport
import com.soundchat.app.audio.TransmissionSpeedPreset
import com.soundchat.app.data.database.SoundChatDatabase
import com.soundchat.app.data.models.ChatMessageEntity
import com.soundchat.app.data.models.TransmissionStatus
import com.soundchat.app.data.repository.ChatRepository
import com.soundchat.app.discovery.ActiveDeviceSession
import com.soundchat.app.discovery.DiscoveredDevice
import com.soundchat.app.service.CommunicationManager
import com.soundchat.app.service.DiagnosticTelemetryState
import com.soundchat.app.service.ValidationExecutionTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

enum class SoundChatDestination(val route: String, val label: String) {
    HOME("home", "Hub"),
    DIAGNOSTICS("diagnostics", "Diagnostics"),
    CHAT("chat", "Chat"),
    DEVICES("devices", "Discovery"),
    SETTINGS("settings", "Settings")
}

class SoundChatViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SoundChatDatabase.getInstance(application)
    private val repository = ChatRepository(database.chatMessageDao())
    val communicationManager = CommunicationManager(application, repository)

    private val _currentDestination = MutableStateFlow(SoundChatDestination.HOME)
    val currentDestination: StateFlow<SoundChatDestination> = _currentDestination.asStateFlow()

    private val _navigationHistory = ArrayDeque<SoundChatDestination>()

    val localDeviceHex: String = communicationManager.localDeviceHex
    val localDeviceName: String = communicationManager.localDeviceName

    val audioConfig: StateFlow<AudioConfiguration> = communicationManager.audioConfig
    val capabilityReport: StateFlow<DeviceCapabilityReport> = communicationManager.capabilityReport
    val transmissionStatus: StateFlow<TransmissionStatus> = communicationManager.transmissionStatus
    val isListening: StateFlow<Boolean> = communicationManager.isListening
    val isDiscovering: StateFlow<Boolean> = communicationManager.isDiscovering
    val isBeaconAdvertising: StateFlow<Boolean> = communicationManager.isBeaconAdvertising
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = communicationManager.discoveredDevices
    val activeSession: StateFlow<ActiveDeviceSession?> = communicationManager.activeSession
    val diagnostics: StateFlow<DiagnosticTelemetryState> = communicationManager.diagnostics

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.allMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun navigateTo(destination: SoundChatDestination) {
        if (destination != _currentDestination.value) {
            _navigationHistory.addLast(_currentDestination.value)
            _currentDestination.value = destination
        }
    }

    fun navigateBack(): Boolean {
        return if (_navigationHistory.isNotEmpty()) {
            _currentDestination.value = _navigationHistory.removeLast()
            true
        } else if (_currentDestination.value != SoundChatDestination.HOME) {
            _currentDestination.value = SoundChatDestination.HOME
            true
        } else {
            false
        }
    }

    fun hasRecordPermission(): Boolean = communicationManager.hasRecordAudioPermission()

    fun refreshCapabilities() {
        communicationManager.refreshDeviceCapabilities()
    }

    fun startListening(): Boolean = communicationManager.startListening()

    fun stopListening() {
        communicationManager.stopListening()
    }

    fun startDiscovery(advertise: Boolean = true) {
        communicationManager.startDiscoveryMode(advertise)
    }

    fun stopDiscovery() {
        communicationManager.stopDiscoveryMode()
    }

    fun broadcastSingleBeacon() {
        communicationManager.broadcastSingleDiscoveryBeacon()
    }

    fun sendPairingRequest(device: DiscoveredDevice) {
        communicationManager.sendPairingRequest(device)
    }

    fun confirmIncomingPairing(device: DiscoveredDevice) {
        communicationManager.confirmIncomingPairing(device)
    }

    fun declineIncomingPairing(device: DiscoveredDevice) {
        communicationManager.declineIncomingPairing(device)
    }

    fun disconnectSession() {
        communicationManager.disconnectActiveSession()
    }

    fun sendMessage(text: String) {
        communicationManager.sendTextMessage(text)
    }

    fun retryMessage(entity: ChatMessageEntity) {
        communicationManager.retryMessage(entity)
    }

    fun startSpeakerTestTone(freqHz: Double, durationMs: Int = 3500) {
        communicationManager.startSpeakerTestTone(freqHz, durationMs)
    }

    fun stopSpeakerTestTone() {
        communicationManager.stopSpeakerTestTone()
    }

    fun setSpeakerTestFrequency(freqHz: Double) {
        communicationManager.setSpeakerTestFrequency(freqHz)
    }

    fun runAcousticLoopbackTest(payload: String = "HI") {
        communicationManager.runAcousticLoopbackTest(payload)
    }

    fun setBandMode(mode: AcousticBandMode) {
        communicationManager.setBandMode(mode)
    }

    fun setSpeedPreset(preset: TransmissionSpeedPreset) {
        communicationManager.setSpeedPreset(preset)
    }

    fun setSampleRate(rateHz: Int) {
        communicationManager.setSampleRate(rateHz)
    }

    fun setCustomFrequencies(bit0Hz: Double, bit1Hz: Double, syncHz: Double) {
        communicationManager.setCustomCarrierFrequencies(bit0Hz, bit1Hz, syncHz)
    }

    fun setRequireAck(enabled: Boolean) {
        communicationManager.updateAudioConfiguration { it.copy(requireAcknowledgement = enabled) }
    }

    fun setBandPassEnabled(enabled: Boolean) {
        communicationManager.updateAudioConfiguration { it.copy(bandPassFilterEnabled = enabled) }
    }

    fun setMaxRetries(retries: Int) {
        communicationManager.updateAudioConfiguration { it.copy(maxRetries = retries.coerceIn(0, 5)) }
    }

    fun clearChatHistory() {
        communicationManager.clearChatHistory()
    }

    fun clearDiagnosticLogs() {
        communicationManager.clearDiagnosticLogs()
    }

    fun recordDistanceSnapshot(distanceCm: Int, tier: ValidationExecutionTier, notes: String) {
        communicationManager.recordDistanceSnapshot(distanceCm, tier, notes)
    }

    fun runDistanceProbeTest(distanceCm: Int, tier: ValidationExecutionTier, notes: String) {
        communicationManager.runDistanceProbeTest(distanceCm, tier, notes)
    }

    fun clearDistanceTestLogs() {
        communicationManager.clearDistanceTestLogs()
    }

    fun runMessageLengthStressTest(tier: ValidationExecutionTier) {
        communicationManager.runMessageLengthStressTest(tier)
    }

    fun runRepeatedReliabilityTest(repetitions: Int, tier: ValidationExecutionTier) {
        communicationManager.runRepeatedReliabilityTest(repetitions, tier)
    }

    fun cancelValidationSuites() {
        communicationManager.cancelValidationSuites()
    }

    fun resetPhysicalValidationStatus() {
        communicationManager.resetPhysicalValidationStatus()
    }

    override fun onCleared() {
        super.onCleared()
        communicationManager.shutdown()
    }
}

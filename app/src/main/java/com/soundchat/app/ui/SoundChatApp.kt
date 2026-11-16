package com.soundchat.app.ui

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soundchat.app.ui.screens.ChatScreen
import com.soundchat.app.ui.screens.DeviceScreen
import com.soundchat.app.ui.screens.DiagnosticScreen
import com.soundchat.app.ui.screens.HomeScreen
import com.soundchat.app.ui.screens.SettingsScreen
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SoundChatTheme
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.SurfaceElevatedGrey
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import kotlinx.coroutines.launch

@Composable
fun SoundChatApp(
    viewModel: SoundChatViewModel = viewModel()
) {
    SoundChatTheme {
        val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
        val audioConfig by viewModel.audioConfig.collectAsStateWithLifecycle()
        val capabilityReport by viewModel.capabilityReport.collectAsStateWithLifecycle()
        val transmissionStatus by viewModel.transmissionStatus.collectAsStateWithLifecycle()
        val isListening by viewModel.isListening.collectAsStateWithLifecycle()
        val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
        val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
        val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
        val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
        val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()

        val snackbarHostState = remember { SnackbarHostState() }
        val coroutineScope = rememberCoroutineScope()

        // Pending action that requested RECORD_AUDIO permission on-demand
        var pendingPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

        val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                pendingPermissionAction?.invoke()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Microphone permission is required to capture incoming ultrasonic signals."
                    )
                }
            }
            pendingPermissionAction = null
        }

        fun runWithMicPermission(action: () -> Unit) {
            if (viewModel.hasRecordPermission()) {
                action()
            } else {
                pendingPermissionAction = action
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val handleToggleListening: () -> Unit = {
            if (isListening) {
                viewModel.stopListening()
            } else {
                runWithMicPermission {
                    viewModel.startListening()
                }
            }
        }

        // Handle back press on secondary screens
        BackHandler(enabled = currentDestination != SoundChatDestination.HOME) {
            viewModel.navigateBack()
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDarkGrey,
                    modifier = Modifier.testTag("bottom_navigation_bar")
                ) {
                    SoundChatDestination.entries.forEach { dest ->
                        val selected = currentDestination == dest
                        NavigationBarItem(
                            selected = selected,
                            onClick = { viewModel.navigateTo(dest) },
                            icon = {
                                Icon(
                                    imageVector = when (dest) {
                                        SoundChatDestination.HOME -> Icons.Default.Home
                                        SoundChatDestination.DIAGNOSTICS -> Icons.Default.Science
                                        SoundChatDestination.CHAT -> Icons.AutoMirrored.Filled.Chat
                                        SoundChatDestination.DEVICES -> Icons.Default.Radar
                                        SoundChatDestination.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = dest.label
                                )
                            },
                            label = { Text(dest.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = UltrasonicCyan,
                                selectedTextColor = UltrasonicCyan,
                                indicatorColor = SurfaceElevatedGrey,
                                unselectedIconColor = TextSecondaryMuted,
                                unselectedTextColor = TextSecondaryMuted
                            ),
                            modifier = Modifier.testTag("bottom_nav_${dest.route}")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when (currentDestination) {
                        SoundChatDestination.HOME -> {
                            HomeScreen(
                                localDeviceHex = viewModel.localDeviceHex,
                                localDeviceName = viewModel.localDeviceName,
                                audioConfig = audioConfig,
                                capabilityReport = capabilityReport,
                                transmissionStatus = transmissionStatus,
                                isListening = isListening,
                                activeSession = activeSession,
                                diagnostics = diagnostics,
                                onToggleListening = handleToggleListening,
                                onQuickSendHi = {
                                    viewModel.sendMessage("HI")
                                    viewModel.navigateTo(SoundChatDestination.CHAT)
                                },
                                onNavigate = { viewModel.navigateTo(it) },
                                onSwitchBandMode = { viewModel.setBandMode(it) }
                            )
                        }

                        SoundChatDestination.DIAGNOSTICS -> {
                            DiagnosticScreen(
                                audioConfig = audioConfig,
                                capabilityReport = capabilityReport,
                                diagnostics = diagnostics,
                                isListening = isListening,
                                onToggleListening = handleToggleListening,
                                onSpeakerTestFreqChange = { viewModel.setSpeakerTestFrequency(it) },
                                onStartSpeakerTone = { viewModel.startSpeakerTestTone(it) },
                                onStopSpeakerTone = { viewModel.stopSpeakerTestTone() },
                                onRunLoopbackTest = { payload ->
                                    viewModel.runAcousticLoopbackTest(payload)
                                },
                                onRunDistanceProbe = { cm, tier, notes ->
                                    viewModel.runDistanceProbeTest(cm, tier, notes)
                                },
                                onRecordDistanceSnapshot = { cm, tier, notes ->
                                    viewModel.recordDistanceSnapshot(cm, tier, notes)
                                },
                                onClearDistanceLogs = { viewModel.clearDistanceTestLogs() },
                                onRunLengthStressTest = { tier ->
                                    viewModel.runMessageLengthStressTest(tier)
                                },
                                onRunReliabilityTest = { reps, tier ->
                                    viewModel.runRepeatedReliabilityTest(reps, tier)
                                },
                                onCancelValidationSuites = { viewModel.cancelValidationSuites() },
                                onResetValidationStatus = { viewModel.resetPhysicalValidationStatus() },
                                onRefreshCapabilities = { viewModel.refreshCapabilities() },
                                onClearLogs = { viewModel.clearDiagnosticLogs() }
                            )
                        }

                        SoundChatDestination.CHAT -> {
                            ChatScreen(
                                localDeviceHex = viewModel.localDeviceHex,
                                audioConfig = audioConfig,
                                transmissionStatus = transmissionStatus,
                                isListening = isListening,
                                activeSession = activeSession,
                                messages = chatMessages,
                                onToggleListening = handleToggleListening,
                                onSendMessage = { viewModel.sendMessage(it) },
                                onRetryMessage = { viewModel.retryMessage(it) }
                            )
                        }

                        SoundChatDestination.DEVICES -> {
                            DeviceScreen(
                                localDeviceHex = viewModel.localDeviceHex,
                                localDeviceName = viewModel.localDeviceName,
                                isDiscovering = isDiscovering,
                                discoveredDevices = discoveredDevices,
                                activeSession = activeSession,
                                onStartDiscovery = { advertise ->
                                    runWithMicPermission {
                                        viewModel.startDiscovery(advertise)
                                    }
                                },
                                onStopDiscovery = { viewModel.stopDiscovery() },
                                onBroadcastSingleBeacon = { viewModel.broadcastSingleBeacon() },
                                onRequestPairing = { viewModel.sendPairingRequest(it) },
                                onConfirmPairing = { viewModel.confirmIncomingPairing(it) },
                                onDeclinePairing = { viewModel.declineIncomingPairing(it) },
                                onDisconnectSession = { viewModel.disconnectSession() }
                            )
                        }

                        SoundChatDestination.SETTINGS -> {
                            SettingsScreen(
                                audioConfig = audioConfig,
                                capabilityReport = capabilityReport,
                                onBandModeSelected = { viewModel.setBandMode(it) },
                                onSampleRateSelected = { viewModel.setSampleRate(it) },
                                onSpeedPresetSelected = { viewModel.setSpeedPreset(it) },
                                onCustomFrequenciesChanged = { f0, f1, fSync ->
                                    viewModel.setCustomFrequencies(f0, f1, fSync)
                                },
                                onRequireAckChanged = { viewModel.setRequireAck(it) },
                                onBandPassChanged = { viewModel.setBandPassEnabled(it) },
                                onMaxRetriesChanged = { viewModel.setMaxRetries(it) },
                                onClearChatHistory = { viewModel.clearChatHistory() },
                                onOpenDiagnostics = {
                                    viewModel.navigateTo(SoundChatDestination.DIAGNOSTICS)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.soundchat.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soundchat.app.discovery.ActiveDeviceSession
import com.soundchat.app.discovery.DiscoveredDevice
import com.soundchat.app.discovery.PairingState
import com.soundchat.app.ui.theme.AmberWarning
import com.soundchat.app.ui.theme.CoralError
import com.soundchat.app.ui.theme.ObsidianBlack
import com.soundchat.app.ui.theme.SurfaceCardBorder
import com.soundchat.app.ui.theme.SurfaceDarkGrey
import com.soundchat.app.ui.theme.SurfaceElevatedGrey
import com.soundchat.app.ui.theme.TextPrimaryWhite
import com.soundchat.app.ui.theme.TextSecondaryMuted
import com.soundchat.app.ui.theme.UltrasonicCyan
import com.soundchat.app.ui.theme.UltrasonicEmerald
import java.util.Locale

@Composable
fun DeviceScreen(
    localDeviceHex: String,
    localDeviceName: String,
    isDiscovering: Boolean,
    discoveredDevices: List<DiscoveredDevice>,
    activeSession: ActiveDeviceSession?,
    onStartDiscovery: (Boolean) -> Unit,
    onStopDiscovery: () -> Unit,
    onBroadcastSingleBeacon: () -> Unit,
    onRequestPairing: (DiscoveredDevice) -> Unit,
    onConfirmPairing: (DiscoveredDevice) -> Unit,
    onDeclinePairing: (DiscoveredDevice) -> Unit,
    onDisconnectSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    var advertiseBeacons by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Acoustic Discovery Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discovery_header_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
            border = BorderStroke(1.dp, SurfaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = if (isDiscovering) UltrasonicEmerald else UltrasonicCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "ACOUSTIC DEVICE DISCOVERY",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimaryWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Local ID: $localDeviceHex ($localDeviceName) · Sound-Only Pairing",
                                style = MaterialTheme.typography.labelSmall,
                                color = UltrasonicCyan
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Periodically broadcast ultrasonic beacon",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryMuted
                    )
                    Switch(
                        checked = advertiseBeacons,
                        onCheckedChange = {
                            advertiseBeacons = it
                            if (isDiscovering) onStartDiscovery(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBlack,
                            checkedTrackColor = UltrasonicEmerald
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (isDiscovering) onStopDiscovery()
                            else onStartDiscovery(advertiseBeacons)
                        },
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("toggle_discovery_mode_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDiscovering) CoralError else UltrasonicEmerald,
                            contentColor = ObsidianBlack
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isDiscovering) "Stop Discovery" else "Discover Nearby",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    OutlinedButton(
                        onClick = onBroadcastSingleBeacon,
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                            .testTag("ping_beacon_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, UltrasonicCyan)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = null,
                            tint = UltrasonicCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Ping Beacon",
                            style = MaterialTheme.typography.labelLarge,
                            color = UltrasonicCyan
                        )
                    }
                }
            }
        }

        // Active Session Card (if paired)
        if (activeSession != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_session_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceElevatedGrey),
                border = BorderStroke(1.dp, UltrasonicEmerald)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = UltrasonicEmerald
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ACTIVE ACOUSTIC SESSION",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = UltrasonicEmerald
                                )
                                Text(
                                    text = "Peer: ${activeSession.peerDeviceName} (${activeSession.peerHexId}) · Session: ${activeSession.sessionId}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimaryWhite
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onDisconnectSession,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CoralError),
                            modifier = Modifier.testTag("disconnect_session_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = "Disconnect",
                                tint = CoralError,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Disconnect",
                                style = MaterialTheme.typography.labelSmall,
                                color = CoralError
                            )
                        }
                    }
                }
            }
        }

        // Discovered Nearby Devices List
        Text(
            text = "DISCOVERED NEARBY ACOUSTIC NODES (${discoveredDevices.size})",
            style = MaterialTheme.typography.labelLarge,
            color = UltrasonicCyan
        )

        if (discoveredDevices.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
                border = BorderStroke(1.dp, SurfaceCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = UltrasonicCyan.copy(alpha = 0.45f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isDiscovering) {
                            "Listening for Ultrasonic Beacons…"
                        } else {
                            "No Nearby Acoustic Devices Detected Yet"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimaryWhite
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enable \"Discover Nearby\" on Phone A and Phone B. No Bluetooth, Wi-Fi, or IP scanning is ever used—discovery beacons travel strictly as ultrasonic audio packets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("discovered_devices_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(discoveredDevices, key = { it.deviceId }) { peer ->
                    DiscoveredDeviceCard(
                        device = peer,
                        onRequestPairing = { onRequestPairing(peer) },
                        onConfirmPairing = { onConfirmPairing(peer) },
                        onDeclinePairing = { onDeclinePairing(peer) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredDeviceCard(
    device: DiscoveredDevice,
    onRequestPairing: () -> Unit,
    onConfirmPairing: () -> Unit,
    onDeclinePairing: () -> Unit
) {
    val borderColor = when (device.pairingState) {
        PairingState.PAIRED -> UltrasonicEmerald
        PairingState.INCOMING_REQUEST_PENDING -> AmberWarning
        else -> SurfaceCardBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("device_card_${device.deviceHexId}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDarkGrey),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevatedGrey),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = UltrasonicCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "${device.deviceName} [ID: ${device.deviceHexId}]",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = device.pairingState.displayLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (device.pairingState) {
                                PairingState.PAIRED -> UltrasonicEmerald
                                PairingState.INCOMING_REQUEST_PENDING -> AmberWarning
                                else -> UltrasonicCyan
                            }
                        )
                    }
                }

                Surface(
                    color = ObsidianBlack,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = String.format(
                            Locale.US,
                            "%.0f dBFS · SNR %.1f dB",
                            device.signalStrengthDbFs,
                            device.snrDb
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = UltrasonicEmerald,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (device.sessionId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Session ID: ${device.sessionId} · Carrier: ${device.detectedCarrierHz.toInt()} Hz",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondaryMuted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (device.pairingState) {
                PairingState.INCOMING_REQUEST_PENDING -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConfirmPairing,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("confirm_pair_${device.deviceHexId}"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UltrasonicEmerald,
                                contentColor = ObsidianBlack
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Confirm Acoustic Pair", style = MaterialTheme.typography.labelMedium)
                        }

                        OutlinedButton(
                            onClick = onDeclinePairing,
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("decline_pair_${device.deviceHexId}"),
                            border = BorderStroke(1.dp, CoralError),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CoralError, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Decline", style = MaterialTheme.typography.labelMedium, color = CoralError)
                        }
                    }
                }

                PairingState.DISCOVERED, PairingState.DISCONNECTED -> {
                    Button(
                        onClick = onRequestPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_pair_${device.deviceHexId}"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UltrasonicCyan,
                            contentColor = ObsidianBlack
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Send Acoustic Pairing Request",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                PairingState.OUTGOING_REQUEST_SENT -> {
                    OutlinedButton(
                        onClick = onRequestPairing,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, AmberWarning),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Re-send Acoustic Pairing Request",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberWarning
                        )
                    }
                }

                PairingState.PAIRED -> {
                    Text(
                        text = "Paired and ready for directed acoustic chat.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = UltrasonicEmerald
                    )
                }
            }
        }
    }
}

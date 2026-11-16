package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.soundchat.app.audio.AudioCapabilityChecker
import com.soundchat.app.ui.SoundChatApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `verify app name and AudioCapabilityChecker execution`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SoundChat", appName)

        val checker = AudioCapabilityChecker(context)
        val report = checker.inspectCapabilities()
        assertTrue(report.supportedSampleRates.isNotEmpty())
        assertTrue(report.nyquistFrequencyHz >= 22050)
    }

    @Test
    fun `verify main screen and navigation across Phase 1 Lab, Chat, Discovery, and Settings`() {
        composeTestRule.setContent {
            SoundChatApp()
        }
        composeTestRule.waitForIdle()

        // Verify Home Screen branding and status banner
        composeTestRule.onNodeWithText("SoundChat").assertIsDisplayed()
        composeTestRule.onNodeWithText("Communication Beyond Connectivity").assertIsDisplayed()
        composeTestRule.onNodeWithTag("transmission_status_banner").assertIsDisplayed()

        // Navigate to Live Acoustic Diagnostics Lab
        composeTestRule.onNodeWithTag("bottom_nav_diagnostics").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("LIVE ACOUSTIC DIAGNOSTICS LAB").assertIsDisplayed()
        composeTestRule.onNodeWithTag("phase7_validation_status_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("physical_validation_badge_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speaker_frequency_test_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("microphone_detection_test_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("loopback_and_packet_telemetry_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("distance_test_log_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("log_distance_snapshot_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("length_stress_test_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("reliability_test_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("physical_test_report_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Remote Sender ID").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Transmission Duration").performScrollTo().assertIsDisplayed()

        // Navigate to Acoustic Chat
        composeTestRule.onNodeWithTag("bottom_nav_chat").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_header_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_message_input").assertIsDisplayed()

        // Navigate to Acoustic Discovery
        composeTestRule.onNodeWithTag("bottom_nav_devices").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("discovery_header_card").assertIsDisplayed()

        // Navigate to Settings
        composeTestRule.onNodeWithTag("bottom_nav_settings").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DSP & PROTOCOL CONFIGURATION").assertIsDisplayed()
    }
}

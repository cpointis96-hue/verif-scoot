package com.scootcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import java.io.File

class CameraJourneyTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun recordRealCameraXClipAndResume() {
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.executeShellCommand("pm grant com.scootcheck.app android.permission.CAMERA")
        val label="CAM-${System.currentTimeMillis()}"
        compose.onNodeWithText("Ajouter un scooter").performClick()
        compose.onNodeWithText("Identifiant du scooter").performTextInput(label)
        compose.onNodeWithText("Ajouter",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nouvelle location").performClick()
        compose.onNodeWithText("Inspection départ",useUnmergedTree=true).performClick()
        compose.waitUntil(30000) {
            compose.onAllNodesWithText("Filmer cette face").fetchSemanticsNodes().firstOrNull()?.config?.contains(SemanticsProperties.Disabled)==false
        }
        compose.onNodeWithText("Filmer cette face").performScrollTo().performClick()
        Thread.sleep(2500)
        compose.onNodeWithText("Arrêter la vidéo").performScrollTo().performClick()
        compose.waitUntil(30000) {compose.onAllNodesWithText("Conserver cette face").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Conserver cette face").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.onAllNodesWithText("Droite · 2/4").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Droite · 2/4").performScrollTo()
        Thread.sleep(1500)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        device.takeScreenshot(File(context.getExternalFilesDir(null),"capture.png"))
        compose.onNodeWithText("Retour",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Départ : 1/4 faces conservées.").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Départ : 1/4 faces conservées.").assertExists()
    }
}

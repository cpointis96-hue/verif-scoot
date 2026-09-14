package com.scootcheck.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class JourneyTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun createInspectReviewCloseAndReopen() {
        val label="UI-${System.currentTimeMillis()}"
        compose.onNodeWithText("Ajouter un scooter").performClick()
        compose.onNodeWithText("Identifiant du scooter").performTextInput(label)
        compose.onNodeWithText("Ajouter",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nouvelle location").performClick()
        compose.onNodeWithText("Inspection départ",useUnmergedTree=true).assertExists()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=InspectionStore(context)
        val scooter=store.scooters().first {it.label==label}
        val rental=store.rentals(scooter.id).first()
        // Real media pipeline is seeded here; picker and live camera have separate UI tests.
        for(kind in listOf("departure","return")) for(face in 0..3) {
            val video=store.newVideo()
            InstrumentationRegistry.getInstrumentation().context.assets.open("fixture.mp4").use {input -> video.outputStream().use {input.copyTo(it)}}
            val frame=File(store.mediaDir,video.nameWithoutExtension+".jpg")
            VideoFrames.extract(video,frame)
            store.saveMedia(rental.id,kind,face,video,frame,"import")
        }
        compose.onNodeWithText("Retour",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Reprendre la location").performClick()
        compose.onNodeWithText("Clôturer la location").performScrollTo().assertIsNotEnabled()
        faces.forEachIndexed {index,name ->
            compose.onNodeWithText("$name · à vérifier").performScrollTo().performClick()
            compose.waitUntil(30000) {compose.onAllNodesWithText("J’ai vérifié cette face").fetchSemanticsNodes().firstOrNull()?.config?.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)==false}
            if(index==0) {
                UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"review.png"))
                compose.onAllNodesWithContentDescription("Vue du scooter").onFirst().performClick()
                compose.onNodeWithText("Fermer le zoom").performClick()
                compose.onNodeWithText("Note de vérification").performScrollTo().performTextInput("Vérifié dans le test")
            }
            compose.onNodeWithText("J’ai vérifié cette face").performScrollTo().performClick()
        }
        compose.onNodeWithText("Clôturer la location").performScrollTo().performClick()
        compose.onNodeWithText("Location clôturée").assertExists()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot(File(context.getExternalFilesDir(null),"closed.png"))
        compose.onNodeWithText("Exporter le rapport et les médias").performScrollTo().performClick()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(device.wait(Until.hasObject(By.text("Sharing 1 file")),15000))
        assertTrue(device.hasObject(By.text(java.util.regex.Pattern.compile("inspection-.*\\.zip"))))
        device.takeScreenshot(File(context.getExternalFilesDir(null),"export.png"))
        device.pressBack()
        compose.onNodeWithText("Retour",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Nouvelle location").assertExists()
        store.close()
    }
}

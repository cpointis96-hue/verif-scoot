package com.scootcheck.app

import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ImportJourneyTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun importThroughSystemPicker() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val resolver=context.contentResolver
        val name="SCOOT-FIXTURE-${System.currentTimeMillis()}.mp4"
        val uri=checkNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME,name); put(MediaStore.Video.Media.MIME_TYPE,"video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/ScootTests"); put(MediaStore.Video.Media.IS_PENDING,1)
        }))
        try {
            resolver.openOutputStream(uri)!!.use {output -> instrumentation.context.assets.open("fixture.mp4").use {it.copyTo(output)}}
            resolver.update(uri,ContentValues().apply {put(MediaStore.Video.Media.IS_PENDING,0)},null,null)
            compose.onNodeWithText("Ajouter un scooter").performClick()
            compose.onNodeWithText("Identifiant du scooter").performTextInput("IMPORT-${System.currentTimeMillis()}")
            compose.onNodeWithText("Ajouter",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Nouvelle location").performClick()
            compose.onNodeWithText("Inspection départ",useUnmergedTree=true).performClick()
            compose.onNodeWithText("Importer une vidéo de test").performScrollTo().performClick()
            val device=UiDevice.getInstance(instrumentation)
            device.waitForIdle()
            device.dumpWindowHierarchy(File(context.getExternalFilesDir(null),"picker.xml"))
            device.takeScreenshot(File(context.getExternalFilesDir(null),"picker.png"))
            val file=device.wait(Until.findObject(By.descStartsWith("Video taken on")),10000)
            assertNotNull("Video must be selectable in system picker; see picker.xml",file)
            file.click()
            compose.waitUntil(30000) {compose.onAllNodesWithText("Conserver cette face").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Vidéo importée : test, heure de prise de vue non attestée.").assertExists()
            compose.onNodeWithText("Conserver cette face").performScrollTo().performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("Droite · 2/4").fetchSemanticsNodes().isNotEmpty()}
        } finally { resolver.delete(uri,null,null) }
    }
}

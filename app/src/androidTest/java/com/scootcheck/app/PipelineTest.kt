package com.scootcheck.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class PipelineTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun texture(seed: Long=42): Bitmap {
        val bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap); canvas.drawColor(Color.LTGRAY)
        val random=java.util.Random(seed); val paint=Paint()
        repeat(450) { paint.color=Color.rgb(random.nextInt(220),random.nextInt(220),random.nextInt(220)); canvas.drawCircle(random.nextInt(640).toFloat(),random.nextInt(480).toFloat(),(3+random.nextInt(12)).toFloat(),paint) }
        return bitmap
    }
    @Test fun comparisonIdenticalIsQuiet() { val a=texture(); assertEquals("quiet",VisualComparator.compare(a,a).status); a.recycle() }
    @Test fun noTextureAbstains() { val a=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888); a.eraseColor(Color.GRAY); assertEquals("uncomparable",VisualComparator.compare(a,a).status); a.recycle() }
    @Test fun unrelatedSceneAbstains() { val a=texture(); val b=texture(777); assertEquals("uncomparable",VisualComparator.compare(a,b).status); a.recycle(); b.recycle() }
    @Test fun localChangeIsProposed() {
        val a=texture(); val b=a.copy(Bitmap.Config.ARGB_8888,true)
        Canvas(b).drawRect(270f,200f,340f,245f,Paint().apply { color=Color.WHITE })
        val result=VisualComparator.compare(a,b)
        assertEquals(result.reason,"candidate",result.status)
        assertTrue(result.boxes.any {it.x<0.55f && it.x+it.width>0.42f})
        a.recycle(); b.recycle()
    }
    @Test fun smallTranslationDoesNotInventChange() {
        val a=texture(); val b=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888)
        Canvas(b).drawBitmap(a,8f,5f,null)
        val result=VisualComparator.compare(a,b)
        assertEquals(result.reason,"quiet",result.status)
        a.recycle(); b.recycle()
    }
    @Test fun corruptVideoFailsWithoutOutput() {
        val file=File(context.cacheDir,"bad.mp4").apply {writeText("not a video")}; val out=File(context.cacheDir,"bad.jpg")
        assertThrows(Exception::class.java) { VideoFrames.extract(file,out) }
        assertFalse(out.exists()); file.delete()
    }
    @Test fun storageVideoReviewAndEvidenceRoundTrip() {
        val store=InspectionStore(context)
        try {
            val label="TEST-${UUID.randomUUID().toString().take(8)}"
            val scooter=store.createScooter(label)
            assertThrows(Exception::class.java) {store.createScooter(label.lowercase())}
            val rental=store.openRental(scooter)
            assertEquals(rental,store.openRental(scooter))
            assertThrows(IllegalStateException::class.java) {store.closeRental(rental)}
            for(kind in listOf("departure","return")) for(face in 0..3) {
                val video=store.newVideo()
                InstrumentationRegistry.getInstrumentation().context.assets.open("fixture.mp4").use { input -> video.outputStream().use {input.copyTo(it)} }
                val hash=sha256(video); val frame=File(store.mediaDir,video.nameWithoutExtension+".jpg")
                VideoFrames.extract(video,frame)
                assertEquals(hash,sha256(video)); assertTrue(frame.length()>0)
                store.saveMedia(rental,kind,face,video,frame,"import")
                assertThrows(Exception::class.java) {store.saveMedia(rental,kind,face,video,frame,"import")}
            }
            val reloaded=InspectionStore(context)
            assertEquals(4,reloaded.media(rental,"departure").size); reloaded.close()
            for(face in 0..3) store.review(rental,face,"checked","Test instrumenté",Comparison("quiet","test").json())
            store.closeRental(rental)
            assertThrows(IllegalStateException::class.java) {store.review(rental,0,"ignored","",Comparison("quiet","test").json())}
            val closed=store.rentals(scooter).first()
            val zip=EvidenceExport.export(context,store,closed,label)
            ZipFile(zip).use { archive ->
                val manifest=JSONObject(archive.getInputStream(archive.getEntry("manifest.json")).bufferedReader().readText())
                assertEquals(8,manifest.getJSONArray("media").length())
                assertEquals(4,manifest.getJSONArray("reviewEvents").length())
                val row=manifest.getJSONArray("media").getJSONObject(0)
                val extracted=File(context.cacheDir,"export-check.mp4")
                archive.getInputStream(archive.getEntry(row.getString("video"))).use {input -> extracted.outputStream().use { input.copyTo(it) }}
                assertEquals(row.getString("videoSha256"),sha256(extracted)); extracted.delete()
            }
            val first=store.media(rental,"departure").first()
            File(store.mediaDir,first.video).appendText("modified")
            assertThrows(IllegalStateException::class.java) {EvidenceExport.export(context,store,closed,label)}
            assertNotEquals(rental,store.openRental(scooter))
        } finally {store.close()}
    }
}

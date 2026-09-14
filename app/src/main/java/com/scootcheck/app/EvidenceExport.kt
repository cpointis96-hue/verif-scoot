package com.scootcheck.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EvidenceExport {
    fun export(context: Context, store: InspectionStore, rental: Rental, label: String): File {
        val media=store.media(rental.id,"departure") + store.media(rental.id,"return")
        val manifest=JSONObject().put("schema",1).put("scooter",label).put("rentalId",rental.id)
            .put("closed",rental.closed).put("rentalCreatedDeviceTime",rental.created)
            .put("exportedDeviceTime",System.currentTimeMillis()).put("operator","local-device-unverified")
            .put("limitations","Experimental visual comparison; not damage certification. Device time is not trusted timestamping. Four faces only; no complete underbody coverage. Central mask is not scooter segmentation.")
        val files=JSONArray()
        val folder=File(context.cacheDir,"reports").apply { mkdirs() }
        val output=File(folder,"inspection-${rental.id}-${System.currentTimeMillis()}.zip")
        try {
            ZipOutputStream(output.outputStream().buffered()).use { zip ->
                fun add(file: File, path: String): String {
                    val hash=sha256(file)
                    zip.putNextEntry(ZipEntry(path)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    return hash
                }
                media.forEach { m ->
                    val video=File(store.mediaDir,m.video); val frame=File(store.mediaDir,m.frame)
                    check(sha256(video)==m.hash) { "Intégrité du média modifiée : export interrompu." }
                    val path="${m.kind}/${m.face}"
                    files.put(JSONObject().put("kind",m.kind).put("face",faces[m.face]).put("source",m.source)
                        .put("savedDeviceTime",m.created).put("video","$path.mp4").put("videoSha256",add(video,"$path.mp4"))
                        .put("frame","$path.jpg").put("frameSha256",add(frame,"$path.jpg")))
                    val meta=File(frame.absolutePath+".json")
                    if(meta.exists()) add(meta,"$path-frame.json")
                }
                manifest.put("media",files).put("reviews",JSONArray().apply { store.reviews(rental.id).forEach {
                    put(JSONObject().put("face",faces[it.face]).put("decision",it.decision).put("note",it.note).put("analysis",JSONObject(it.analysis)))
                } })
                val events=JSONArray()
                store.readableDatabase.rawQuery("SELECT face,decision,note,analysis,created FROM review_events WHERE rental_id=? ORDER BY id",arrayOf("${rental.id}")).use { c ->
                    while(c.moveToNext()) events.put(JSONObject().put("face",faces[c.getInt(0)]).put("decision",c.getString(1)).put("note",c.getString(2)).put("analysis",JSONObject(c.getString(3))).put("deviceTime",c.getLong(4)))
                }
                manifest.put("reviewEvents",events)
                val bytes=manifest.toString(2).toByteArray(Charsets.UTF_8)
                zip.putNextEntry(ZipEntry("manifest.json")); zip.write(bytes); zip.closeEntry()
                val text=buildString {
                    appendLine("Inspection scooter : $label")
                    appendLine("Location : ${rental.id} · ${if(rental.closed) "clôturée" else "ouverte"}")
                    appendLine("Comparaison expérimentale. Les heures viennent du téléphone.")
                    appendLine("Consultez les dossiers departure et return pour les originaux.")
                    store.reviews(rental.id).forEach { appendLine("${faces[it.face]} : ${it.decision} ${it.note}") }
                    appendLine("SHA-256 des fichiers et historique de revue : manifest.json")
                }
                zip.putNextEntry(ZipEntry("rapport.txt")); zip.write(text.toByteArray()); zip.closeEntry()
            }
            return output
        } catch(e: Exception) { output.delete(); throw e }
    }
}

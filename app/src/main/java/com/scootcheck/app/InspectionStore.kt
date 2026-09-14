package com.scootcheck.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.security.MessageDigest
import java.util.UUID

val faces = listOf("Avant", "Droite", "Arrière", "Gauche")
data class Scooter(val id: Long, val label: String)
data class Rental(val id: Long, val scooterId: Long, val created: Long, val closed: Boolean)
data class Media(val id: Long, val rentalId: Long, val kind: String, val face: Int,
                 val video: String, val frame: String, val source: String, val hash: String, val created: Long)
data class Review(val face: Int, val decision: String, val note: String, val analysis: String)

fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

class InspectionStore(context: Context) : SQLiteOpenHelper(context, "inspections.db", null, 1) {
    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE scooters(id INTEGER PRIMARY KEY, label TEXT NOT NULL COLLATE NOCASE UNIQUE)")
        db.execSQL("CREATE TABLE rentals(id INTEGER PRIMARY KEY, scooter_id INTEGER NOT NULL REFERENCES scooters(id), created INTEGER NOT NULL, closed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE UNIQUE INDEX one_open ON rentals(scooter_id) WHERE closed=0")
        db.execSQL("CREATE TABLE media(id INTEGER PRIMARY KEY, rental_id INTEGER NOT NULL REFERENCES rentals(id), kind TEXT NOT NULL CHECK(kind IN ('departure','return')), face INTEGER NOT NULL CHECK(face BETWEEN 0 AND 3), video TEXT NOT NULL, frame TEXT NOT NULL, source TEXT NOT NULL, hash TEXT NOT NULL, created INTEGER NOT NULL, UNIQUE(rental_id,kind,face))")
        db.execSQL("CREATE TABLE reviews(rental_id INTEGER NOT NULL REFERENCES rentals(id), face INTEGER NOT NULL, decision TEXT NOT NULL, note TEXT NOT NULL, analysis TEXT NOT NULL, created INTEGER NOT NULL, PRIMARY KEY(rental_id,face))")
        db.execSQL("CREATE TABLE review_events(id INTEGER PRIMARY KEY, rental_id INTEGER NOT NULL, face INTEGER NOT NULL, decision TEXT NOT NULL, note TEXT NOT NULL, analysis TEXT NOT NULL, created INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    @Synchronized fun createScooter(label: String): Long {
        val name = label.trim()
        require(name.isNotEmpty() && name.length <= 60) { "Saisissez un identifiant de 1 à 60 caractères." }
        return writableDatabase.insertOrThrow("scooters", null, ContentValues().apply { put("label", name) })
    }
    fun scooters(): List<Scooter> = readableDatabase.rawQuery("SELECT id,label FROM scooters ORDER BY label", null).use { c ->
        buildList { while(c.moveToNext()) add(Scooter(c.getLong(0), c.getString(1))) }
    }
    fun rentals(scooterId: Long): List<Rental> = readableDatabase.rawQuery("SELECT id,scooter_id,created,closed FROM rentals WHERE scooter_id=? ORDER BY id DESC", arrayOf("$scooterId")).use { c ->
        buildList { while(c.moveToNext()) add(Rental(c.getLong(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1)) }
    }
    @Synchronized fun openRental(scooterId: Long): Long {
        rentals(scooterId).firstOrNull { !it.closed }?.let { return it.id }
        return writableDatabase.insertOrThrow("rentals", null, ContentValues().apply {
            put("scooter_id", scooterId); put("created", System.currentTimeMillis())
        })
    }
    fun media(rentalId: Long, kind: String): List<Media> = readableDatabase.rawQuery(
        "SELECT id,rental_id,kind,face,video,frame,source,hash,created FROM media WHERE rental_id=? AND kind=? ORDER BY face", arrayOf("$rentalId", kind)
    ).use { c -> buildList { while(c.moveToNext()) add(Media(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3), c.getString(4), c.getString(5), c.getString(6), c.getString(7), c.getLong(8))) } }
    fun newVideo(): File = File(mediaDir, "${UUID.randomUUID()}.mp4")
    private fun ensureOpen(id: Long) {
        readableDatabase.rawQuery("SELECT closed FROM rentals WHERE id=?", arrayOf("$id")).use {
            check(it.moveToFirst() && it.getInt(0) == 0) { "Cette location est clôturée ou introuvable." }
        }
    }
    @Synchronized fun saveMedia(rentalId: Long, kind: String, face: Int, file: File, frame: File, source: String) {
        ensureOpen(rentalId)
        require(kind in listOf("departure", "return") && face in 0..3)
        require(source in listOf("camera", "import"))
        check(file.length() > 0 && frame.length() > 0) { "Média incomplet. Reprenez cette face." }
        if (kind == "return") check(media(rentalId, "departure").size == 4) { "Terminez le départ avant le retour." }
        writableDatabase.insertOrThrow("media", null, ContentValues().apply {
            put("rental_id", rentalId); put("kind", kind); put("face", face)
            put("video", file.name); put("frame", frame.name); put("source", source)
            put("hash", sha256(file)); put("created", System.currentTimeMillis())
        })
    }
    fun reviews(rentalId: Long): List<Review> = readableDatabase.rawQuery("SELECT face,decision,note,analysis FROM reviews WHERE rental_id=? ORDER BY face", arrayOf("$rentalId")).use { c ->
        buildList { while(c.moveToNext()) add(Review(c.getInt(0), c.getString(1), c.getString(2), c.getString(3))) }
    }
    @Synchronized fun review(rentalId: Long, face: Int, decision: String, note: String, analysis: String) {
        ensureOpen(rentalId)
        require(face in 0..3 && decision in listOf("confirmed", "ignored", "checked"))
        check(media(rentalId, "return").any { it.face == face })
        val values = ContentValues().apply {
            put("rental_id", rentalId); put("face", face); put("decision", decision)
            put("note", note.take(2000)); put("analysis", analysis); put("created", System.currentTimeMillis())
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("review_events", null, values)
            writableDatabase.insertWithOnConflict("reviews", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }
    @Synchronized fun closeRental(rentalId: Long) {
        ensureOpen(rentalId)
        check(media(rentalId, "departure").size == 4 && media(rentalId, "return").size == 4 && reviews(rentalId).size == 4) { "Vérifiez les quatre faces avant de clôturer." }
        writableDatabase.execSQL("UPDATE rentals SET closed=1 WHERE id=?", arrayOf(rentalId))
    }
}

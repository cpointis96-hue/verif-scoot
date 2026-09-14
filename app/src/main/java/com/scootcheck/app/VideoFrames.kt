package com.scootcheck.app

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.File

object VideoFrames {
    fun sharpness(bitmap: Bitmap): Double {
        val small = Bitmap.createScaledBitmap(bitmap, 160, 120, true)
        val pixels = IntArray(160 * 120)
        small.getPixels(pixels, 0, 160, 0, 0, 160, 120)
        fun gray(p: Int) = ((p shr 16 and 255) * 0.299 + (p shr 8 and 255) * 0.587 + (p and 255) * 0.114)
        var sum = 0.0
        for (y in 1 until 119) for(x in 1 until 159) {
            val i = y*160+x
            val lap = gray(pixels[i-1]) + gray(pixels[i+1]) + gray(pixels[i-160]) + gray(pixels[i+160]) - 4*gray(pixels[i])
            sum += lap*lap
        }
        if (small !== bitmap) small.recycle()
        return sum / (158*118)
    }
    fun extract(video: File, output: File): File {
        val retriever = MediaMetadataRetriever()
        var best: Bitmap? = null
        try {
            retriever.setDataSource(video.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            require(duration in 500..120000) { "Choisissez un clip de 0,5 à 120 secondes pour cette face." }
            var bestScore = -1.0
            var selectedUs = 0L
            for (index in 1..5) {
                val requestedUs = duration * 1000 * index / 6
                val frame = retriever.getScaledFrameAtTime(requestedUs, MediaMetadataRetriever.OPTION_CLOSEST, 1280, 1280) ?: continue
                val score = sharpness(frame)
                if (score > bestScore) { best?.recycle(); best = frame; bestScore = score; selectedUs = requestedUs }
                else frame.recycle()
            }
            val chosen = checkNotNull(best) { "Vidéo illisible. Essayez un MP4 ou refilmez cette face." }
            output.outputStream().use { check(chosen.compress(Bitmap.CompressFormat.JPEG, 94, it)) }
            File(output.absolutePath + ".json").writeText("""{"requestedTimeUs":$selectedUs,"selection":"sharpest-of-five","sharpness":$bestScore,"exactTimestampGuaranteed":false}""")
            return output
        } finally { best?.recycle(); retriever.release() }
    }
}

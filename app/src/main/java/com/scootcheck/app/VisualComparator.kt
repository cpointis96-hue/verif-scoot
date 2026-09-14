package com.scootcheck.app

import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.*
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

// Coordinates refer to the departure frame, normalized to [0,1].
data class ChangeBox(val x: Float, val y: Float, val width: Float, val height: Float)
data class Comparison(val status: String, val reason: String, val boxes: List<ChangeBox> = emptyList()) {
    companion object {
        fun fromJson(text: String): Comparison {
            val value = JSONObject(text)
            val items = value.optJSONArray("boxes") ?: JSONArray()
            return Comparison(value.getString("status"), value.getString("reason"), (0 until items.length()).map {
                val box = items.getJSONObject(it)
                ChangeBox(box.getDouble("x").toFloat(), box.getDouble("y").toFloat(), box.getDouble("width").toFloat(), box.getDouble("height").toFloat())
            })
        }
    }
    fun json(): String = JSONObject().put("algorithm", "orb-local-difference-0.1")
        .put("status", status).put("reason", reason).put("damageClassification", false)
        .put("boxes", JSONArray().apply { boxes.forEach { b -> put(JSONObject().put("x",b.x).put("y",b.y).put("width",b.width).put("height",b.height)) } }).toString()
}

object VisualComparator {
    fun compare(before: Bitmap, after: Bitmap): Comparison {
        if (!OpenCVLoader.initLocal()) return Comparison("uncomparable", "Analyse indisponible. Vérifiez les images manuellement.")
        val owned = mutableListOf<Mat>()
        fun mat() = Mat().also { owned.add(it) }
        fun refuse(reason: String) = Comparison("uncomparable", reason)
        val orb = ORB.create(2200)
        val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
        val matches = mutableListOf<MatOfDMatch>()
        try {
            if (abs(before.width.toDouble()/before.height - after.width.toDouble()/after.height) > 0.15)
                return refuse("Cadrages différents. Revue manuelle nécessaire.")
            val a = mat(); val b = mat()
            fun gray(bitmap: Bitmap, out: Mat) {
                val rgba = mat(); Utils.bitmapToMat(bitmap, rgba)
                Imgproc.cvtColor(rgba, out, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.resize(out, out, Size(640.0, 640.0 * bitmap.height / bitmap.width))
            }
            gray(before,a); gray(after,b)
            for (im in listOf(a,b)) {
                val mean = Core.mean(im).`val`[0]
                val lap = mat(); Imgproc.Laplacian(im,lap,CvType.CV_64F)
                val m = MatOfDouble().also { owned.add(it) }; val sd = MatOfDouble().also { owned.add(it) }
                Core.meanStdDev(lap,m,sd)
                if (mean < 25 || mean > 235 || sd.toArray()[0] < 3) return refuse("Image sombre, surexposée ou peu détaillée. Vérifiez ou refilmez.")
            }
            // This central mask excludes some background; it is not scooter segmentation.
            val roi = Mat.zeros(a.size(),CvType.CV_8UC1).also { owned.add(it) }
            Imgproc.rectangle(roi, Point(a.cols()*0.1,a.rows()*0.1),Point(a.cols()*0.9,a.rows()*0.9),Scalar(255.0),-1)
            val ka = MatOfKeyPoint().also { owned.add(it) }; val kb = MatOfKeyPoint().also { owned.add(it) }
            val da = mat(); val db = mat()
            orb.detectAndCompute(a,roi,ka,da); orb.detectAndCompute(b,roi,kb,db)
            if (da.empty() || db.rows() < 2) return refuse("Pas assez de détails communs pour aligner les vues.")
            matcher.knnMatch(da,db,matches,2)
            val good = matches.mapNotNull { pair -> pair.toArray().let { if(it.size == 2 && it[0].distance < 0.72*it[1].distance) it[0] else null } }
            if (good.size < 18) return refuse("Angles ou scènes trop différents pour une comparaison automatique.")
            val pa = ka.toArray(); val pb = kb.toArray()
            val src = MatOfPoint2f(*good.map { pb[it.trainIdx].pt }.toTypedArray()).also { owned.add(it) }
            val dst = MatOfPoint2f(*good.map { pa[it.queryIdx].pt }.toTypedArray()).also { owned.add(it) }
            val inliers = mat()
            val h = Calib3d.findHomography(src,dst,Calib3d.RANSAC,3.0,inliers).also { owned.add(it) }
            if(h.empty() || Core.countNonZero(inliers) < 15 || Core.countNonZero(inliers).toDouble()/good.size < 0.45)
                return refuse("Alignement incertain. Vérification manuelle nécessaire.")
            val supports = good.indices.filter { inliers.get(it,0)[0] != 0.0 }.map { pa[good[it].queryIdx].pt }
            if (supports.maxOf { it.x } - supports.minOf { it.x } < a.cols()*0.3 || supports.maxOf { it.y } - supports.minOf { it.y } < a.rows()*0.3)
                return refuse("Les détails communs ne couvrent pas assez la vue.")
            val corners = MatOfPoint2f(Point(0.0,0.0),Point(b.cols().toDouble(),0.0),Point(b.cols().toDouble(),b.rows().toDouble()),Point(0.0,b.rows().toDouble())).also { owned.add(it) }
            val transformed = MatOfPoint2f().also { owned.add(it) }; Core.perspectiveTransform(corners,transformed,h)
            val pts = transformed.toArray()
            if(pts.any { !it.x.isFinite() || !it.y.isFinite() || it.x < -a.cols()*0.3 || it.x > a.cols()*1.3 || it.y < -a.rows()*0.3 || it.y > a.rows()*1.3 })
                return refuse("Déformation trop importante entre les prises de vue.")
            val polygon = MatOfPoint(*pts).also { owned.add(it) }
            val areaRatio = abs(Imgproc.contourArea(polygon))/(a.rows()*a.cols())
            if(areaRatio !in 0.65..1.45 || !Imgproc.isContourConvex(polygon)) return refuse("Perspective trop différente.")
            val sourcePixels=ByteArray(b.total().toInt()); b.get(0,0,sourcePixels)
            val coefficients=DoubleArray(9); h.get(0,0,coefficients)
            val warped=PerspectiveWarp.warp(sourcePixels,b.cols(),b.rows(),a.cols(),a.rows(),coefficients)
            val aligned=Mat(a.rows(),a.cols(),CvType.CV_8UC1).also { owned.add(it) }; aligned.put(0,0,warped.pixels)
            val valid=Mat(a.rows(),a.cols(),CvType.CV_8UC1).also { owned.add(it) }; valid.put(0,0,warped.valid)
            Core.bitwise_and(valid,roi,valid)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(9.0,9.0)).also { owned.add(it) }
            Imgproc.erode(valid,valid,kernel)
            val coverage = Core.countNonZero(valid).toDouble()/Core.countNonZero(roi)
            if(coverage < 0.8) return refuse("Recouvrement insuffisant entre les deux vues.")
            val aa = ByteArray((a.total()).toInt()); val bb = ByteArray(aa.size); val mask = ByteArray(aa.size)
            a.get(0,0,aa); aligned.get(0,0,bb); valid.get(0,0,mask)
            val histogram = IntArray(511)
            for(i in aa.indices) if(mask[i].toInt() != 0) histogram[(aa[i].toInt() and 255)-(bb[i].toInt() and 255)+255]++
            var cumulative=0; var offset=0
            for(i in histogram.indices) { cumulative+=histogram[i]; if(cumulative >= Core.countNonZero(valid)/2) { offset=i-255; break } }
            val diff = ByteArray(aa.size)
            for(i in aa.indices) if(mask[i].toInt()!=0 && abs((aa[i].toInt() and 255)-(bb[i].toInt() and 255)-offset)>38) diff[i]=255.toByte()
            val binary=Mat(a.rows(),a.cols(),CvType.CV_8UC1).also { owned.add(it) }; binary.put(0,0,diff)
            val changed=Core.countNonZero(binary).toDouble()/Core.countNonZero(valid)
            if(changed > 0.22) return refuse("Trop de changements de lumière, de pose ou de contenu. Revue manuelle.")
            val tiny=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,Size(3.0,3.0)).also { owned.add(it) }
            Imgproc.morphologyEx(binary,binary,Imgproc.MORPH_OPEN,tiny)
            Imgproc.dilate(binary,binary,tiny)
            val contours=mutableListOf<MatOfPoint>(); val hierarchy=mat()
            Imgproc.findContours(binary,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE)
            owned.addAll(contours)
            val boxes=contours.filter { Imgproc.contourArea(it) > a.total()*0.0008 }.map { Imgproc.boundingRect(it) }
                .sortedByDescending { it.area() }.take(8).map { ChangeBox(it.x.toFloat()/a.cols(),it.y.toFloat()/a.rows(),it.width.toFloat()/a.cols(),it.height.toFloat()/a.rows()) }
            return if(boxes.isEmpty()) Comparison("quiet","Aucune différence saillante proposée. Vérifiez aussi les bords et les petits détails.")
            else Comparison("candidate","Changement visuel possible. Reflets et saleté peuvent aussi déclencher une suggestion.",boxes)
        } catch (_: Exception) { return refuse("Analyse impossible. Les originaux restent disponibles pour la revue.") }
        finally { matches.forEach { it.release() }; owned.forEach { it.release() }; orb.clear(); matcher.clear() }
    }
}

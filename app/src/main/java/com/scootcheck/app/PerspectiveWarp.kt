package com.scootcheck.app

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class WarpedGray(val pixels: ByteArray, val valid: ByteArray)

/** CPU resampling avoids the native ARM warp path that crashes on the API 35 AVD. */
object PerspectiveWarp {
    fun warp(source: ByteArray, width: Int, height: Int, destinationWidth: Int, destinationHeight: Int, h: DoubleArray): WarpedGray {
        require(source.size == width*height && h.size == 9)
        val a=h[0]; val b=h[1]; val c=h[2]; val d=h[3]; val e=h[4]; val f=h[5]; val g=h[6]; val j=h[7]; val k=h[8]
        val inverse=doubleArrayOf(e*k-f*j,c*j-b*k,b*f-c*e,f*g-d*k,a*k-c*g,c*d-a*f,d*j-e*g,b*g-a*j,a*e-b*d)
        val determinant=a*inverse[0]+b*inverse[3]+c*inverse[6]
        require(determinant.isFinite() && abs(determinant)>1e-10) { "Singular perspective transform" }
        // Homogeneous division cancels the common 1/determinant factor.
        val pixels=ByteArray(destinationWidth*destinationHeight)
        val valid=ByteArray(pixels.size)
        for(y in 0 until destinationHeight) for(x in 0 until destinationWidth) {
            val denominator=inverse[6]*x+inverse[7]*y+inverse[8]
            if(abs(denominator)<1e-10) continue
            val sx=(inverse[0]*x+inverse[1]*y+inverse[2])/denominator
            val sy=(inverse[3]*x+inverse[4]*y+inverse[5])/denominator
            if(!sx.isFinite() || !sy.isFinite() || sx<0 || sy<0 || sx>=width-1 || sy>=height-1) continue
            val ix=floor(sx).toInt(); val iy=floor(sy).toInt(); val dx=sx-ix; val dy=sy-iy
            val index=iy*width+ix
            fun value(i:Int) = (source[i].toInt() and 255).toDouble()
            val top=value(index)*(1-dx)+value(index+1)*dx
            val bottom=value(index+width)*(1-dx)+value(index+width+1)*dx
            pixels[y*destinationWidth+x]=(top*(1-dy)+bottom*dy).roundToInt().coerceIn(0,255).toByte()
            valid[y*destinationWidth+x]=255.toByte()
        }
        return WarpedGray(pixels,valid)
    }
}

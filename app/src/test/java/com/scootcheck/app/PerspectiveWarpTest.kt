package com.scootcheck.app

import org.junit.Assert.*
import org.junit.Test

class PerspectiveWarpTest {
    @Test fun identityPreservesPixelsAndMarksSamplingBorderInvalid() {
        val source=ByteArray(100) {it.toByte()}
        val result=PerspectiveWarp.warp(source,10,10,10,10,doubleArrayOf(1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0))
        assertEquals(source[44],result.pixels[44]); assertEquals(255,result.valid[44].toInt() and 255)
        assertEquals(0,result.valid[99].toInt())
    }
    @Test fun forwardTranslationSamplesInverseCoordinates() {
        val source=ByteArray(100) {it.toByte()}
        val result=PerspectiveWarp.warp(source,10,10,10,10,doubleArrayOf(1.0,0.0,2.0,0.0,1.0,1.0,0.0,0.0,1.0))
        assertEquals(source[32],result.pixels[44]); assertEquals(0,result.valid[0].toInt())
    }
    @Test fun singularMatrixIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { PerspectiveWarp.warp(ByteArray(100),10,10,10,10,DoubleArray(9)) }
    }
}

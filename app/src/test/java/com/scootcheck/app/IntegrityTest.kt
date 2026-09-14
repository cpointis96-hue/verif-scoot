package com.scootcheck.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IntegrityTest {
    @Test fun sha256MatchesStandardAndDetectsModification() {
        val file=File.createTempFile("scoot-hash", ".txt")
        try {
            file.writeText("abc")
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",sha256(file))
            file.appendText("d")
            assertNotEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",sha256(file))
        } finally { file.delete() }
    }
}

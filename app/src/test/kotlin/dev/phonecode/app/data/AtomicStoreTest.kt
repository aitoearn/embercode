package dev.phonecode.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class AtomicStoreTest {
    @Test fun replacesFilesWithoutLeavingTemporaryData() {
        val dir = Files.createTempDirectory("atomic-store-test").toFile()
        try {
            val file = dir.resolve("state.json")
            file.writeTextAtomically("before")
            file.writeTextAtomically("after")
            assertEquals("after", file.readText())
            assertTrue(dir.listFiles().orEmpty().none { it.extension == "tmp" })
        } finally {
            dir.deleteRecursively()
        }
    }
}

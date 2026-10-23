package dev.retrobit.agent.local

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class LocalModelStoreTest {
    private lateinit var store: LocalModelStore
    private lateinit var input: File
    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        store = LocalModelStore(app)
        store.delete()
        input = File(app.cacheDir, "chosen.gguf")
        input.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 3, 0, 0, 0) + ByteArray(64))
    }
    @After fun cleanup() { runBlocking { store.delete(); input.delete(); Unit } }
    @Test fun `import copies into private no backup storage`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        assertTrue(store.ready)
        assertEquals(72L, store.sizeBytes)
        assertArrayEquals(input.readBytes(), LocalInference.modelFile!!.readBytes())
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(LocalInference.modelFile!!.canonicalPath.startsWith(app.noBackupFilesDir.canonicalPath + "/"))
    }
    @Test fun `bad replacement preserves previous model`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        input.writeText("not a GGUF")
        assertTrue(runCatching { store.import(Uri.fromFile(input)) {} }.isFailure)
        assertTrue(store.ready)
        assertEquals(72L, store.sizeBytes)
        assertFalse(File(LocalInference.modelFile!!.parentFile, "import.partial").exists())
    }
    @Test fun `delete removes only private copy not original document`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        store.delete()
        assertFalse(store.ready)
        assertTrue(input.isFile)
    }
    @Test fun `checksum mismatch preserves previous model and removes partial`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        val original = LocalInference.modelFile!!.readBytes()
        assertTrue(runCatching { input.inputStream().use { store.install(it, "new", input.length(), "0".repeat(64)) {} } }.isFailure)
        assertArrayEquals(original, LocalInference.modelFile!!.readBytes())
        assertFalse(File(LocalInference.modelFile!!.parentFile, "import.partial").exists())
    }
    @Test fun `truncated declared body preserves previous model`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        assertTrue(runCatching { input.inputStream().use { store.install(it, "new", 999, null) {} } }.isFailure)
        assertEquals(72L, store.sizeBytes)
    }
    @Test fun `successful import exposes computed hash`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, store.sha256)
    }

    @Test fun `file picker import verifies supplied publisher checksum too`() = runBlocking {
        store.import(Uri.fromFile(input)) {}
        val original = store.sha256
        val result = runCatching { store.import(Uri.fromFile(input), "0".repeat(64)) {} }
        assertTrue(result.isFailure); assertEquals(original, store.sha256)
        assertEquals(72L, store.sizeBytes)
    }

}

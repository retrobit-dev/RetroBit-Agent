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
}

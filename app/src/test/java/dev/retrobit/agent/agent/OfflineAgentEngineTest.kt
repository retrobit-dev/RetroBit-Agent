package dev.retrobit.agent.agent

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.retrobit.agent.ai.*
import dev.retrobit.agent.data.*
import dev.retrobit.agent.local.LocalInference
import dev.retrobit.agent.local.LocalBackend
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class OfflineAgentEngineTest {
    private fun scenario(responses: List<String>): Pair<ChatMessage, ProjectFiles> = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val settings = SettingsStore(app)
        settings.updateConfig { it.copy(provider = ProviderKind.ON_DEVICE, localContext = 4096) }
        val repository = ProjectRepository(app, settings)
        val project = repository.create("Offline test", "blank", DesignSystem())
        var round = 0
        var closed = 0
        var piece: ByteArray? = null
        val backend = object : LocalBackend {
            override fun open(path: ByteArray, context: Int, threads: Int, temperature: Float): Long { assertEquals(4096, context); return 1L }
            override fun begin(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>, grammar: ByteArray): Int {
                assertEquals("system", roles.first().decodeToString())
                assertTrue(contents.first().decodeToString().contains("Kamu agen proyek"))
                assertTrue(contents.first().decodeToString().contains("OFFLINE TOOL PROTOCOL"))
                assertTrue(roles.size <= 6); assertTrue(grammar.isNotEmpty())
                piece = responses.getOrElse(round++) { """{"message":"done"}""" }.toByteArray()
                return 100
            }
            override fun next(handle: Long): ByteArray? = piece.also { piece = null }
            override fun close(handle: Long) { closed++ }
        }
        val client = LocalInference.clientForTest(settings.config.value, backend)
        val engine = AgentEngine(app, repository, settings, SecretStore(app)) { config, _ ->
            assertEquals(ProviderKind.ON_DEVICE, config.provider); client
        }
        val previous = LocalInference.modelFile
        val fakeFile = File.createTempFile("fake-gguf", ".gguf").apply { writeText("GGUF fake backend only") }
        LocalInference.modelFile = fakeFile
        try {
            val result = engine.run(project, "Buat aplikasi todo", emptyList()) {}
            assertTrue(repository.filesFor(project.id).latestSnapshot() != null)
            assertEquals(round, closed)
            result to repository.filesFor(project.id)
        } finally { LocalInference.modelFile = previous; fakeFile.delete() }

    }
    @Test fun `offline envelopes execute real file tools and finish summary`() {
        val (result, files) = scenario(listOf(
            """{"tool":"write_file","arguments":{"path":"index.html","content":"<h1>Offline todo</h1>"}}""",
            """{"tool":"validate_project","arguments":{}}""",
            """{"message":"Selesai"}""",
        ))
        assertEquals("<h1>Offline todo</h1>", files.read("index.html"))
        assertEquals(2, result.steps.size); assertEquals(listOf("index.html"), result.filesChanged)
        assertFalse(result.isError); assertTrue(result.content.contains("Tool dijalankan: 2"))
    }
    @Test fun `malformed offline tool never writes or executes`() {
        val (result, files) = scenario(listOf("""{"tool":"write_file","arguments":{"path":"x","content":"unfinished"""))
        assertTrue(result.isError); assertFalse(files.exists("x")); assertTrue(result.steps.isEmpty())
    }
    @Test fun `unknown offline tool never executes`() {
        val (result, _) = scenario(listOf("""{"tool":"shell","arguments":{"cmd":"id"}}"""))
        assertTrue(result.isError); assertTrue(result.steps.isEmpty())
    }
    @Test fun `step limit applies to offline loop`() {
        val (result, _) = scenario(List(20) { """{"tool":"list_files","arguments":{}}""" })
        assertEquals(AgentEngine.MAX_STEPS, result.steps.size)
        assertTrue(result.content.contains("Batas 14 langkah"))
    }
}

package dev.retrobit.agent.ai

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The local-model path is the only genuinely unlimited one, so it has to work
 * the first time: nobody debugs their own PC from a phone they just installed
 * an app on.
 */
class LocalServerTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun tags(vararg names: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"models":[${names.joinToString(",") { """{"name":"$it","size":123}""" }}]}""")

    // --- URL shaping ----------------------------------------------------------

    @Test
    fun `a bare host gets a scheme and the v1 prefix`() {
        assertEquals("http://192.168.1.10/v1", LocalServer.normalizeBaseUrl("192.168.1.10"))
    }

    @Test
    fun `host and port are accepted the way people type them`() {
        assertEquals("http://192.168.1.10:11434/v1", LocalServer.normalizeBaseUrl("192.168.1.10:11434"))
    }

    @Test
    fun `an explicit scheme is left alone`() {
        assertEquals("https://gpu.local:8443/v1", LocalServer.normalizeBaseUrl("https://gpu.local:8443"))
        assertEquals("http://10.0.2.2:11434/v1", LocalServer.normalizeBaseUrl("HTTP://10.0.2.2:11434"))
    }

    @Test
    fun `a full base url is not given a second v1`() {
        assertEquals("http://127.0.0.1:11434/v1", LocalServer.normalizeBaseUrl("http://127.0.0.1:11434/v1"))
        assertEquals("http://127.0.0.1:11434/v1", LocalServer.normalizeBaseUrl("http://127.0.0.1:11434/v1/"))
    }

    @Test
    fun `a custom path is preserved instead of growing a v1`() {
        assertEquals("http://box:8080/openai", LocalServer.normalizeBaseUrl("box:8080/openai"))
    }

    @Test
    fun `blank stays blank so save can complain about it`() {
        assertEquals("", LocalServer.normalizeBaseUrl("   "))
    }

    @Test
    fun `tags live outside the v1 namespace`() {
        assertEquals("http://192.168.1.10:11434/api/tags", LocalServer.tagsUrl("192.168.1.10:11434"))
        assertEquals("http://192.168.1.10:11434/api/tags", LocalServer.tagsUrl("http://192.168.1.10:11434/v1"))
    }

    // --- parsing --------------------------------------------------------------

    @Test
    fun `model names are read in server order`() {
        val body = """{"models":[{"name":"qwen2.5-coder:7b"},{"name":"llama3.2"}]}"""
        assertEquals(listOf("qwen2.5-coder:7b", "llama3.2"), LocalServer.models(body))
    }

    @Test
    fun `names are trimmed and duplicates collapsed`() {
        val body = """{"models":[{"name":" a "},{"name":"a"},{"name":"  "},{"other":1}]}"""
        assertEquals(listOf("a"), LocalServer.models(body))
    }

    @Test
    fun `garbage bodies produce no models instead of throwing`() {
        assertEquals(emptyList<String>(), LocalServer.models("not json"))
        assertEquals(emptyList<String>(), LocalServer.models("{}"))
        assertEquals(emptyList<String>(), LocalServer.models("""{"models":{}}"""))
        assertEquals(emptyList<String>(), LocalServer.models(""))
    }

    // --- hints ----------------------------------------------------------------

    @Test
    fun `the emulator address explains why it fails on a phone`() {
        assertTrue(LocalServer.hint("http://10.0.2.2:11434/v1").contains("emulator"))
        assertTrue(LocalServer.hint("http://10.0.2.2:11434/v1").contains("192.168.1.10"))
    }

    @Test
    fun `localhost is called out as the phone itself`() {
        assertTrue(LocalServer.hint("http://localhost:11434/v1").contains("HP itu sendiri"))
        assertTrue(LocalServer.hint("127.0.0.1:11434").contains("HP itu sendiri"))
    }

    @Test
    fun `a plain lan address gets the ollama host command`() {
        assertTrue(LocalServer.hint("192.168.1.10:11434").contains("OLLAMA_HOST=0.0.0.0"))
    }

    // --- against a real server ------------------------------------------------

    @Test
    fun `the client asks for tags and returns what the server has`() {
        server.enqueue(tags("qwen2.5-coder:7b", "llama3.2"))

        val result = runBlocking { AiClientFactory.listLocalModels(server.url("/").toString(), "") }

        assertEquals(listOf("qwen2.5-coder:7b", "llama3.2"), result.getOrThrow())
        assertEquals("/api/tags", server.takeRequest().path)
    }

    @Test
    fun `a base url with v1 still hits the right path`() {
        server.enqueue(tags("llama3.2"))

        runBlocking { AiClientFactory.listLocalModels(server.url("/v1").toString(), "") }

        assertEquals("/api/tags", server.takeRequest().path)
    }

    @Test
    fun `a refused connection is reported, not swallowed`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))

        val result = runBlocking { AiClientFactory.listLocalModels(server.url("/").toString(), "") }

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message, message.contains("403"))
    }

    @Test
    fun `an unreachable server fails instead of hanging`() {
        val dead = server.url("/").toString()
        server.shutdown()

        val result = runBlocking { AiClientFactory.listLocalModels(dead, "") }

        assertTrue(result.isFailure)
    }
}

package dev.retrobit.agent.agent

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.ai.WireTurn
import dev.retrobit.agent.data.ChatConversation
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.SecretEntry
import dev.retrobit.agent.data.SecretStore
import dev.retrobit.agent.data.SettingsStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real chat engine against a local server: real stores, real HTTP
 * client, real SSE parser. Only the model on the far end is fake.
 *
 * The branches worth guarding are the ones a user hits first — demo mode, an
 * empty conversation, Auto with no keys — plus the accounting that feeds the
 * usage screen, because a wrong number there is invisible until it matters.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], application = Application::class)
class PlainChatEngineTest {

    private lateinit var context: Context
    private lateinit var settings: SettingsStore
    private lateinit var secrets: SecretStore
    private lateinit var engine: PlainChatEngine
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settings = SettingsStore(context)
        secrets = SecretStore(context)
        engine = PlainChatEngine(settings, secrets)
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    // --- helpers --------------------------------------------------------------

    /** Points the engine at the mock server and stores a key for [provider]. */
    private fun connect(provider: ProviderKind = ProviderKind.OPENAI, model: String = "gpt-4.1-mini") {
        settings.updateConfig {
            AiConfig(
                provider = provider,
                baseUrl = server.url("/v1").toString().trimEnd('/'),
                model = model,
            )
        }
        secrets.put(SecretEntry(AgentEngine.keyFor(provider.name), "sk-test"))
    }

    private fun conversation(vararg texts: String, systemPrompt: String = "") = ChatConversation(
        messages = texts.mapIndexed { index, text ->
            ChatMessage(role = if (index % 2 == 0) Role.USER else Role.ASSISTANT, content = text)
        },
        systemPrompt = systemPrompt,
    )

    private fun sse(vararg events: String) = MockResponse()
        .setHeader("Content-Type", "text/event-stream; charset=utf-8")
        .setBody(events.joinToString("\n\n", postfix = "\n\n"))

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun completion(text: String) = json(
        """{"choices":[{"message":{"role":"assistant","content":"$text"}}],""" +
            """"usage":{"prompt_tokens":5,"completion_tokens":4}}"""
    )

    private fun ask(text: String = "Halo", persona: String = "PERSONA GLOBAL"): ChatMessage = runBlocking {
        engine.reply(conversation(text), persona) { }
    }

    private fun stream(text: String = "Halo"): Pair<List<String>, ChatMessage> {
        val chunks = mutableListOf<String>()
        val reply = runBlocking {
            engine.replyStream(conversation(text), "PERSONA GLOBAL", { }) { chunks.add(it) }
        }
        return chunks to reply
    }

    private fun lastBody(): String = server.takeRequest().body.readUtf8()

    // --- refusals that must not cost a request --------------------------------

    @Test
    fun `demo mode refuses and never touches the network`() {
        val reply = ask()

        assertTrue(reply.isError)
        assertTrue(reply.content, reply.content.contains("Mode demo"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `empty conversation is answered without a request`() {
        connect()

        val reply = runBlocking { engine.reply(ChatConversation(), "PERSONA GLOBAL") { } }

        assertEquals("Tidak ada pesan untuk dijawab.", reply.content)
        assertTrue(reply.isError)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `auto without any key explains what to store`() {
        settings.updateConfig { AiConfig(provider = ProviderKind.AUTO) }

        val reply = ask()

        assertTrue(reply.isError)
        assertTrue(reply.content, reply.content.contains("Mode Auto"))
        assertEquals(0, server.requestCount)
    }

    // --- the happy paths ------------------------------------------------------

    @Test
    fun `streaming reply forwards deltas, labels the model and records usage`() {
        connect()
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"content":"Halo"}}]}""",
                """data: {"choices":[{"delta":{"content":" dunia"}}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":3}}""",
                "data: [DONE]",
            )
        )

        val (chunks, reply) = stream()

        assertEquals(listOf("Halo", " dunia"), chunks)
        assertEquals("Halo dunia", reply.content)
        assertEquals("gpt-4.1-mini", reply.modelLabel)
        assertEquals(15, reply.tokens)
        assertFalse(reply.isError)

        val usage = settings.usage.value
        assertEquals(12L, usage.promptTokens)
        assertEquals(3L, usage.completionTokens)
        assertEquals(1L, usage.requests)
    }

    @Test
    fun `plain reply posts a non-streaming completion`() {
        connect()
        server.enqueue(completion("Halo dari server"))

        val reply = ask()

        assertEquals("Halo dari server", reply.content)
        assertEquals(9, reply.tokens)
        assertFalse(lastBody().contains("\"stream\":true"))
    }

    @Test
    fun `the engine announces which model it is contacting`() {
        connect()
        server.enqueue(completion("ok"))
        val notices = mutableListOf<String>()

        runBlocking { engine.reply(conversation("Halo"), "PERSONA GLOBAL") { notices.add(it) } }

        assertEquals(listOf("Menghubungi OpenAI · gpt-4.1-mini"), notices)
    }

    @Test
    fun `output budget is clamped to the chat limit`() {
        connect()
        settings.updateConfig { it.copy(maxTokens = 99_999) }
        server.enqueue(completion("ok"))
        ask()
        assertTrue(lastBody().contains("\"max_tokens\":4096"))

        settings.updateConfig { it.copy(maxTokens = 1) }
        server.enqueue(completion("ok"))
        ask()
        assertTrue(lastBody().contains("\"max_tokens\":256"))
    }

    // --- failures -------------------------------------------------------------

    @Test
    fun `blank answer becomes an error and records no usage`() {
        connect()
        server.enqueue(sse("""data: {"choices":[{"delta":{}}]}""", "data: [DONE]"))

        val reply = ask()

        assertTrue(reply.isError)
        assertTrue(reply.content, reply.content.contains("kosong"))
        assertEquals(0L, settings.usage.value.requests)
    }

    @Test
    fun `rejected key surfaces the provider explanation`() {
        connect()
        server.enqueue(json("""{"error":{"message":"invalid key"}}""").setResponseCode(401))

        val reply = ask()

        assertTrue(reply.isError)
        assertTrue(reply.content, reply.content.contains("ditolak"))
        assertTrue(reply.content, reply.content.contains("401"))
        assertEquals(0L, settings.usage.value.requests)
    }

    // --- prompt assembly ------------------------------------------------------

    @Test
    fun `conversation persona wins over the global one`() {
        connect()
        server.enqueue(completion("ok"))

        runBlocking {
            engine.reply(conversation("Halo", systemPrompt = "PERSONA PERCAKAPAN"), "PERSONA GLOBAL") { }
        }

        val body = lastBody()
        assertTrue(body.contains("PERSONA PERCAKAPAN"))
        assertTrue(body.contains("MODE OBROLAN"))
        assertFalse("global persona leaked into a chat with its own", body.contains("PERSONA GLOBAL"))
    }

    @Test
    fun `global persona fills in when the conversation has none`() {
        connect()
        server.enqueue(completion("ok"))

        ask()

        assertTrue(lastBody().contains("PERSONA GLOBAL"))
    }

    @Test
    fun `default persona is used when nothing is configured`() {
        val prompt = PlainChatEngine.systemPrompt(ChatConversation(), "")

        assertTrue(prompt.contains(SettingsStore.DEFAULT_SYSTEM_PROMPT.trim().take(60)))
        assertTrue(prompt.contains("MODE OBROLAN"))
    }

    @Test
    fun `wire turns keep the last twenty messages and trim long ones`() {
        val long = "a".repeat(8_050) + "TAIL"
        val messages = (1..24).map { ChatMessage(role = Role.USER, content = "pesan-$it") } +
            ChatMessage(role = Role.USER, content = long)

        val turns = PlainChatEngine.transcript(messages)

        assertEquals(20, turns.size)
        val last = turns.last() as WireTurn.User
        assertEquals(8_000, last.text.length)
        assertFalse(last.text.endsWith("TAIL"))
    }

    @Test
    fun `error and empty bubbles never reach the wire`() {
        val messages = listOf(
            ChatMessage(role = Role.USER, content = "Halo"),
            ChatMessage(role = Role.ASSISTANT, content = "gagal", isError = true),
            ChatMessage(role = Role.ASSISTANT, content = "   "),
            ChatMessage(role = Role.SYSTEM, content = "instruksi"),
        )

        val turns = PlainChatEngine.transcript(messages)

        assertEquals(1, turns.size)
        assertTrue(turns.single() is WireTurn.User)
    }
}

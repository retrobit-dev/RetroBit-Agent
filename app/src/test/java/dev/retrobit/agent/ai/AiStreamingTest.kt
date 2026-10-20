package dev.retrobit.agent.ai

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Drives the real HTTP clients against a local server that speaks each
 * provider's streaming dialect. This is the closest thing to an integration
 * test we can run without an API key: same request building, same SSE parsing,
 * same error mapping.
 */
class AiStreamingTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        http = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sse(vararg events: String): MockResponse = MockResponse()
        .setHeader("Content-Type", "text/event-stream; charset=utf-8")
        .setBody(events.joinToString("\n\n", postfix = "\n\n"))

    private fun openAiClient() = OpenAiCompatibleClient(
        http, server.url("/v1").toString().trimEnd('/'), "sk-test", "gpt-4.1-mini", ProviderKind.OPENAI,
    )

    private fun collect(): Pair<List<String>, AiResponse> {
        val chunks = mutableListOf<String>()
        val response = runBlocking {
            openAiClient().chatStream(
                system = "sys",
                turns = listOf(WireTurn.User("halo")),
                tools = emptyList(),
                temperature = 0.2,
                maxTokens = 128,
                onDelta = { chunks.add(it) },
            )
        }
        return chunks to response
    }

    // --- OpenAI-style ---------------------------------------------------------

    @Test
    fun `openai stream concatenates deltas and reads usage`() {
        server.enqueue(
            sse(
                """data: {"choices":[{"delta":{"role":"assistant"}}]}""",
                """data: {"choices":[{"delta":{"content":"Halo"}}]}""",
                """data: {"choices":[{"delta":{"content":" dunia"}}]}""",
                """data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":11,"completion_tokens":2}}""",
                "data: [DONE]",
            )
        )

        val (chunks, response) = collect()

        assertEquals(listOf("Halo", " dunia"), chunks)
        assertEquals("Halo dunia", response.text)
        assertEquals(TokenUsage(11, 2), response.usage)
    }

    @Test
    fun `openai stream request asks for streaming and usage`() {
        server.enqueue(sse("""data: {"choices":[{"delta":{"content":"x"}}]}""", "data: [DONE]"))
        collect()

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(body.contains("stream_options"))
        assertTrue(body.contains("\"include_usage\":true"))
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
    }

    @Test
    fun `openai stream survives comment lines and blank frames`() {
        server.enqueue(
            sse(
                ": keep-alive",
                "",
                """data: {"choices":[{"delta":{"content":"a"}}]}""",
                """data: not-json""",
                """data: {"choices":[{"delta":{"content":"b"}}]}""",
                "data: [DONE]",
            )
        )

        val (chunks, response) = collect()
        assertEquals(listOf("a", "b"), chunks)
        assertEquals("ab", response.text)
    }

    @Test
    fun `non streaming call still parses the classic payload`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"choices":[{"message":{"content":"jawaban utuh"}}],"usage":{"prompt_tokens":3,"completion_tokens":4}}"""
                )
        )

        val response = runBlocking {
            openAiClient().chat("sys", listOf(WireTurn.User("halo")), emptyList(), 0.2, 128)
        }

        assertEquals("jawaban utuh", response.text)
        assertEquals(7, response.usage.total)
        assertFalse(server.takeRequest().body.readUtf8().contains("\"stream\""))
    }

    @Test
    fun `http errors surface as AiException`() {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"bad key"}}"""))

        val error = assertThrows(AiException::class.java) {
            runBlocking {
                openAiClient().chatStream("sys", listOf(WireTurn.User("halo")), emptyList(), 0.2, 64) {}
            }
        }

        assertEquals(401, error.statusCode)
        assertTrue(error.message!!.contains("API key ditolak"))
    }

    // --- Anthropic ------------------------------------------------------------

    @Test
    fun `anthropic stream reads text deltas and usage`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    event: message_start
                    data: {"type":"message_start","message":{"usage":{"input_tokens":9}}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hai"}}

                    event: content_block_delta
                    data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" kamu"}}

                    event: message_delta
                    data: {"type":"message_delta","usage":{"output_tokens":3}}

                    """.trimIndent()
                )
        )

        val chunks = mutableListOf<String>()
        val response = runBlocking {
            AnthropicClient(http, server.url("/").toString().trimEnd('/'), "sk-ant", "claude-haiku-4-5")
                .chatStream("sys", listOf(WireTurn.User("halo")), emptyList(), 0.3, 128) { chunks.add(it) }
        }

        assertEquals(listOf("Hai", " kamu"), chunks)
        assertEquals("Hai kamu", response.text)
        assertEquals(9, response.usage.prompt)
        assertEquals(3, response.usage.completion)

        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("sk-ant", request.getHeader("x-api-key"))
        assertTrue(request.body.readUtf8().contains("\"stream\":true"))
    }

    // --- Gemini ---------------------------------------------------------------

    @Test
    fun `gemini stream reads candidate parts`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setBody(
                    """
                    data: {"candidates":[{"content":{"parts":[{"text":"Satu"}]}}],"usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":1}}

                    data: {"candidates":[{"content":{"parts":[{"text":" dua"}]}}]}

                    """.trimIndent()
                )
        )

        val chunks = mutableListOf<String>()
        val response = runBlocking {
            GeminiClient(http, server.url("/").toString().trimEnd('/'), "AIza", "gemini-2.5-flash")
                .chatStream("sys", listOf(WireTurn.User("halo")), emptyList(), 0.5, 256) { chunks.add(it) }
        }

        assertEquals(listOf("Satu", " dua"), chunks)
        assertEquals("Satu dua", response.text)
        assertEquals(4, response.usage.prompt)

        val request = server.takeRequest()
        assertTrue(request.path!!.contains(":streamGenerateContent"))
        assertTrue(request.path!!.contains("alt=sse"))
        assertEquals("AIza", request.getHeader("x-goog-api-key"))
    }

    // --- parser ---------------------------------------------------------------

    @Test
    fun `sse reader stops at done and skips non data lines`() {
        val body = """
            : ping
            event: message
            data: {"a":1}
            data: [DONE]
            data: {"b":2}
        """.trimIndent().toResponseBody("text/event-stream".toMediaType())

        val payloads = mutableListOf<String>()
        runBlocking { body.readSse { payloads.add(it) } }

        assertEquals(listOf("""{"a":1}"""), payloads)
    }

    @Test
    fun `sse reader tolerates an empty body`() {
        val payloads = mutableListOf<String>()
        val empty = "".toResponseBody("text/event-stream".toMediaType())
        runBlocking { empty.readSse { payloads.add(it) } }
        assertTrue(payloads.isEmpty())
    }
}

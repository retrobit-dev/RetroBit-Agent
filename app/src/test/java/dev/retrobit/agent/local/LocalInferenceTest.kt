package dev.retrobit.agent.local

import dev.retrobit.agent.ai.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class LocalInferenceTest {
    private lateinit var file: File
    private var oldFile: File? = null
    private val config = AiConfig(provider = ProviderKind.ON_DEVICE, localContext = 4096)
    private class Backend : LocalBackend {
        var opened = 0
        var closed = 0
        var context = 0
        var linkError: LinkageError? = null
        var failBegin = false
        var failNext = false
        var grammarText = ""
        var roles = listOf<String>()
        var contents = listOf<String>()
        val pieces = ArrayDeque<ByteArray>()
        override fun open(path: ByteArray, context: Int, threads: Int, temperature: Float): Long {
            linkError?.let { throw it }
            opened++; this.context = context; return 1
        }
        override fun begin(handle: Long, roles: Array<ByteArray>, contents: Array<ByteArray>, grammar: ByteArray): Int {
            if (failBegin) error("Prompt terlalu panjang")
            grammarText = grammar.decodeToString()
            this.roles = roles.map { it.toString(Charsets.UTF_8) }
            this.contents = contents.map { it.toString(Charsets.UTF_8) }
            return 10
        }
        override fun next(handle: Long): ByteArray? {
            if (failNext) error("decode gagal")
            return if (pieces.isEmpty()) null else pieces.removeFirst()
        }
        override fun close(handle: Long) { closed++ }
    }
    @Before fun setup() {
        oldFile = LocalInference.modelFile
        file = File.createTempFile("test-model", ".gguf")
        LocalInference.modelFile = file
    }
    @After fun cleanup() { LocalInference.modelFile = oldFile; file.delete() }

    private fun answer(backend: Backend, max: Int = 512, sink: suspend (String) -> Unit = {}): AiResponse = runBlocking {
        LocalInference.clientForTest(config, backend).chatStream("Sistem", listOf(WireTurn.User("Halo 👋")), emptyList(), 0.6, max, sink)
    }

    @Test fun `offline provider needs no key and is not demo`() {
        assertFalse(config.isDemo)
        assertFalse(config.catalog().requiresKey)
        assertEquals(ProviderKind.ON_DEVICE, config.catalog().kind)
    }
    @Test fun `output is streamed and resources freed`() {
        val backend = Backend().apply { pieces.add("Hai".toByteArray()); pieces.add("!".toByteArray()) }
        val chunks = mutableListOf<String>()
        assertEquals("Hai!", answer(backend) { chunks += it }.text)
        assertEquals(listOf("Hai", "!"), chunks)
        assertEquals(1, backend.opened); assertEquals(1, backend.closed)
    }
    @Test fun `unicode and roles reach JNI as UTF8 not modified UTF8`() {
        val backend = Backend()
        answer(backend)
        assertEquals(listOf("system", "user"), backend.roles)
        assertEquals(listOf("Sistem", "Halo 👋"), backend.contents)
    }
    @Test fun `model absent fails without loading native library`() {
        file.delete()
        val backend = Backend()
        val failure = runCatching { answer(backend) }.exceptionOrNull()
        assertTrue(failure is AiException)
        assertTrue(failure!!.message.orEmpty().contains("GGUF"))
        assertEquals(0, backend.opened)
    }
    @Test fun `factory offline path cannot fall through to an HTTP URL`() {
        file.delete()
        val result = runCatching { runBlocking {
            AiClientFactory.create(config.copy(baseUrl = "https://should-not-be-contacted.invalid"), "")
                .chat("S", listOf(WireTurn.User("U")), emptyList(), 0.0, 1)
        } }
        assertTrue(result.exceptionOrNull() is AiException)
        assertTrue(result.exceptionOrNull()!!.message.orEmpty().contains("GGUF"))
    }
    @Test fun `incomplete tool JSON is never executed`() {
        val backend = Backend()
        val result = runCatching { runBlocking {
            LocalInference.clientForTest(config, backend).chat("S", emptyList(), listOf(AiToolDef("write", "write", buildJsonObject {})), 0.0, 1)
        } }
        assertTrue(result.exceptionOrNull() is AiException)
        assertEquals(1, backend.opened)
        assertEquals(1, backend.closed)
    }
    @Test fun `failed prompt releases model`() {
        val backend = Backend().apply { failBegin = true }
        assertTrue(runCatching { answer(backend) }.exceptionOrNull() is AiException)
        assertEquals(1, backend.closed)
    }
    @Test fun `failed decoding releases model`() {
        val backend = Backend().apply { failNext = true }
        assertTrue(runCatching { answer(backend) }.exceptionOrNull() is AiException)
        assertEquals(1, backend.closed)
    }
    @Test fun `cancelled stream releases model and preserves cancellation`() {
        val backend = Backend().apply { pieces.add("text".toByteArray()) }
        val failure = runCatching { answer(backend) { throw CancellationException("stop") } }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(1, backend.closed)
    }
    @Test fun `output budget caps generation`() {
        val backend = Backend().apply { repeat(10) { pieces.add("a".toByteArray()) } }
        assertEquals("aa", answer(backend, max = 2).text)
        assertEquals(1, backend.closed)
    }
    @Test fun `prefill batches do not spend output budget`() {
        val backend = Backend().apply { pieces.add(byteArrayOf()); pieces.add("a".toByteArray()) }
        assertEquals("a", answer(backend, max = 1).text)
    }
    @Test fun `missing JNI library gives an actionable error`() {
        val backend = Backend().apply { linkError = UnsatisfiedLinkError("missing") }
        assertTrue(runCatching { answer(backend) }.exceptionOrNull() is AiException)
        assertEquals(0, backend.closed)
    }
    @Test fun `failed native class initialization is also reported instead of crashing`() {
        val backend = Backend().apply { linkError = NoClassDefFoundError("NativeBridge") }
        assertTrue(runCatching { answer(backend) }.exceptionOrNull() is AiException)
        assertEquals(0, backend.closed)
    }
    @Test fun `local answers have no guessed cloud billing`() {
        assertEquals(TokenUsage(), answer(Backend()).usage)
    }
    @Test fun `offline tool mode sends grammar and parses without leaking raw JSON to chat`() = runBlocking {
        val backend = Backend().apply { pieces.add("""{"tool":"list_files","arguments":{}}""".toByteArray()) }
        val tools = dev.retrobit.agent.agent.AgentToolRunner.DEFINITIONS
        val deltas = mutableListOf<String>()
        val result = LocalInference.clientForTest(config, backend).chatStream("System", listOf(WireTurn.User("Buat app")), tools, 0.0, 1024) { deltas += it }
        assertEquals("list_files", result.toolCalls.single().name)
        assertTrue(backend.grammarText.contains("tool-name"))
        assertTrue(deltas.isEmpty()); assertEquals(1, backend.closed)
    }

}

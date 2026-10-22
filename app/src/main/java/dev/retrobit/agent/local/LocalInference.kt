package dev.retrobit.agent.local

import dev.retrobit.agent.ai.AiClient
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.AiException
import dev.retrobit.agent.ai.AiResponse
import dev.retrobit.agent.ai.AiToolDef
import dev.retrobit.agent.ai.WireTurn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One model in memory at a time, shared by chat, import and delete operations. */
object LocalInference {
    internal val gate = Mutex()
    const val MAX_CONTEXT = 4096
    const val MAX_OUTPUT = 1024

    /** Reassigned in Application.onCreate; never resolved from model-supplied paths. */
    internal var modelFile: File? = null

    fun client(config: AiConfig): AiClient = Client(config, JniBackend)
    internal fun clientForTest(config: AiConfig, backend: LocalBackend): AiClient = Client(config, backend)

    private class Client(private val config: AiConfig, private val backend: LocalBackend) : AiClient {
        override suspend fun chat(system: String, turns: List<WireTurn>, tools: List<AiToolDef>, temperature: Double, maxTokens: Int): AiResponse =
            chatStream(system, turns, tools, temperature, maxTokens) { }

        override suspend fun chatStream(
            system: String, turns: List<WireTurn>, tools: List<AiToolDef>, temperature: Double,
            maxTokens: Int, onDelta: suspend (String) -> Unit,
        ): AiResponse = withContext(Dispatchers.Default) {
            gate.withLock {
                val file = modelFile?.takeIf { it.isFile }
                    ?: throw AiException("Belum ada model GGUF. Buka AI Cloud → AI di HP → Impor GGUF.")
                val roles = mutableListOf("system")
                val contents = mutableListOf(if (tools.isEmpty()) system else system + OfflineToolProtocol.instructions(tools))
                turns.forEach { turn ->
                    if (tools.isNotEmpty()) {
                        val (role, content) = OfflineToolProtocol.turn(turn)
                        roles += role; contents += content
                    } else when (turn) {
                        is WireTurn.User -> { roles += "user"; contents += turn.text }
                        is WireTurn.Assistant -> { roles += "assistant"; contents += turn.text }
                        is WireTurn.ToolResult -> throw AiException("Riwayat tool tidak didukung oleh mode Chat offline.")
                    }
                }
                var handle = 0L
                try {
                    currentCoroutineContext().ensureActive()
                    handle = backend.open(
                        file.absolutePath.toByteArray(Charsets.UTF_8),
                        config.localContext.coerceIn(512, MAX_CONTEXT),
                        Runtime.getRuntime().availableProcessors().coerceIn(1, 4),
                        temperature.toFloat().coerceIn(0f, 1.2f),
                    )
                    currentCoroutineContext().ensureActive()
                    backend.begin(handle, roles.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(), contents.map { it.toByteArray(Charsets.UTF_8) }.toTypedArray(),
                        if (tools.isEmpty()) byteArrayOf() else OfflineToolProtocol.grammar(tools).toByteArray(Charsets.UTF_8))
                    val utf8 = Utf8Pieces()
                    val text = StringBuilder()
                    var emitted = 0
                    while (emitted < maxTokens.coerceIn(1, MAX_OUTPUT)) {
                        currentCoroutineContext().ensureActive()
                        val bytes = backend.next(handle) ?: break
                        if (bytes.isEmpty()) continue // prompt prefill, not an output token
                        emitted++
                        val piece = utf8.append(bytes)
                        if (piece.isNotEmpty()) { text.append(piece); if (tools.isEmpty()) onDelta(piece) }
                    }
                    currentCoroutineContext().ensureActive()
                    val tail = utf8.append(byteArrayOf(), end = true)
                    if (tail.isNotEmpty()) { text.append(tail); if (tools.isEmpty()) onDelta(tail) }
                    // No guessed token/cost accounting: the cloud billing counters stay unchanged.
                    if (tools.isEmpty()) AiResponse(text.toString(), emptyList()) else OfflineToolProtocol.parse(text.toString(), tools)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: LinkageError) {
                    throw AiException("Mesin native belum tersedia untuk perangkat ini. Pasang APK yang memuat arm64-v8a.")
                } catch (e: IllegalStateException) {
                    throw AiException(e.message ?: "Inferensi lokal gagal.")
                } finally {
                    if (handle != 0L) backend.close(handle)
                }
            }
        }
    }
}

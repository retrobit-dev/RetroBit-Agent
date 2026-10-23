package dev.retrobit.agent.agent

import dev.retrobit.agent.ai.AiClient
import dev.retrobit.agent.ai.AiClientFactory
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.AiException
import dev.retrobit.agent.ai.AutoRouter
import dev.retrobit.agent.ai.KeyRing
import dev.retrobit.agent.ai.Planner
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.ai.QuotaPolicy
import dev.retrobit.agent.ai.TokenUsage
import dev.retrobit.agent.ai.WireTurn
import dev.retrobit.agent.ai.aiCooldowns
import dev.retrobit.agent.data.ChatConversation
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.ChatSpace
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.SecretStore
import dev.retrobit.agent.data.SettingsStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.net.UnknownHostException

/**
 * Plain chat: one request, one answer, no tools and no project files.
 *
 * The agent loop in [AgentEngine] always carries a tool catalogue and a product
 * engineer persona, which makes it a poor fit for "just talk to a model".
 * This engine sends the conversation itself and nothing else.
 */
class PlainChatEngine(
    private val settings: SettingsStore,
    private val secrets: SecretStore,
) {

    companion object {
        /** Chat answers stay shorter than an agent turn; 4k is plenty for prose. */
        const val MAX_OUTPUT_TOKENS = 4096

        /** Turns a stored conversation into the wire format, system prompt excluded. */
        fun transcript(messages: List<ChatMessage>): List<WireTurn> =
            ChatSpace.history(messages).map { message ->
                when (message.role) {
                    Role.USER -> WireTurn.User(message.content.take(8000))
                    else -> WireTurn.Assistant(message.content.take(8000), emptyList())
                }
            }

        /** The user's global persona, with a line that keeps chat answers in chat mode. */
        fun systemPrompt(conversation: ChatConversation, global: String): String {
            val persona = conversation.systemPrompt.ifBlank { global }.ifBlank { SettingsStore.DEFAULT_SYSTEM_PROMPT }
            return """
                $persona

                ## MODE OBROLAN
                Ini obrolan biasa, bukan sesi membangun aplikasi. Jawab langsung dalam prosa
                yang rapi dan lengkap. Jangan menyebut file, tool, preview, atau proyek —
                semua kemampuan itu tidak aktif di sini.
            """.trimIndent()
        }
    }

    /** Full answer, no partial updates. */
    suspend fun reply(
        conversation: ChatConversation,
        persona: String,
        onNotice: (String) -> Unit,
    ): ChatMessage = run(conversation, persona, onNotice, onDelta = null)

    /**
     * Same as [reply], but [onDelta] receives each text fragment as the provider
     * emits it. Called from a background dispatcher — callers must marshal to the
     * UI thread themselves. A provider that cannot stream still works: the default
     * implementation in [AiClient] hands over the finished text in one chunk.
     */
    suspend fun replyStream(
        conversation: ChatConversation,
        persona: String,
        onNotice: (String) -> Unit,
        onDelta: suspend (String) -> Unit,
    ): ChatMessage = run(conversation, persona, onNotice, onDelta)

    private suspend fun run(
        conversation: ChatConversation,
        persona: String,
        onNotice: (String) -> Unit,
        onDelta: (suspend (String) -> Unit)?,
    ): ChatMessage {
        val config = settings.config.value
        if (config.isDemo) {
            return failure(
                "Mode demo tidak memanggil model, jadi obrolan belum bisa dijawab.\n\n" +
                    "Buka **AI Cloud**, simpan satu key gratis (Gemini, Groq, atau OpenRouter), " +
                    "lalu pilih provider itu atau **Auto**. Tanpa komputer/key: pilih **AI di HP** dan impor model GGUF.",
            )
        }

        val system = systemPrompt(conversation, persona)
        val turns = transcript(conversation.messages)
        if (turns.isEmpty()) {
            return failure("Tidak ada pesan untuk dijawab.")
        }

        val candidates = if (config.provider == ProviderKind.AUTO) {
            AutoRouter.candidates(secrets, config)
        } else {
            listOf(config)
        }
        if (candidates.isEmpty()) {
            return failure(
                "Mode Auto belum menemukan provider. Simpan minimal satu API key di AI Cloud — " +
                    "Gemini dan Groq punya kuota gratis — atau pilih provider tertentu.",
            )
        }

        // Every stored key is a separate attempt, and a provider whose daily
        // free budget is gone is skipped before a request is wasted on it.
        val attempts = Planner.attempts(
            candidates = candidates,
            keysFor = { kind -> KeyRing.parse(secrets.get(AgentEngine.keyFor(kind.name))) },
            quota = settings.quotaState(),
            today = QuotaPolicy.today(),
            cooldowns = aiCooldowns,
        )
        if (attempts.isEmpty()) {
            return failure(
                "Kuota harian semua provider sudah habis, atau key-nya sedang jeda setelah ditolak.\n\n" +
                    "Tambah key lain di AI Cloud (satu per baris) atau jalankan model lokal lewat Ollama — " +
                    "yang lokal tidak punya batas harian.",
            )
        }

        var lastError = ""
        var cost = 0.0
        var emitted = 0
        for (attempt in attempts) {
            currentCoroutineContext().ensureActive()
            val candidate = attempt.config
            onNotice(
                (if (candidate.provider == ProviderKind.ON_DEVICE) "Memuat model di HP · ${candidate.displayModel}" else "Menghubungi ${candidate.provider.label} · ${candidate.displayModel}") +
                    if (attempt.keyLabel.isBlank()) "" else " · key ${attempt.keyLabel}",
            )
            try {
                val client = AiClientFactory.create(candidate, attempt.key)
                val sink = onDelta
                val response = if (sink == null) {
                    client.chat(
                        system = system,
                        turns = turns,
                        tools = emptyList(),
                        temperature = candidate.temperature,
                        maxTokens = candidate.maxTokens.coerceIn(256, MAX_OUTPUT_TOKENS),
                    )
                } else {
                    client.chatStream(
                        system = system,
                        turns = turns,
                        tools = emptyList(),
                        temperature = candidate.temperature,
                        maxTokens = candidate.maxTokens.coerceIn(256, MAX_OUTPUT_TOKENS),
                        onDelta = { piece ->
                            emitted += piece.length
                            sink(piece)
                        },
                    )
                }
                val usage = response.usage
                val (inputPrice, outputPrice) = AiConfig.estimatedPricePerMillion(candidate.displayModel)
                cost = usage.prompt / 1_000_000.0 * inputPrice + usage.completion / 1_000_000.0 * outputPrice
                if (response.text.isBlank()) {
                    lastError = "${candidate.provider.label} mengembalikan jawaban kosong."
                    continue
                }
                record(usage, cost)
                settings.noteRequest(candidate.provider)
                return ChatMessage(
                    role = Role.ASSISTANT,
                    content = response.text.trim(),
                    modelLabel = candidate.displayModel,
                    tokens = usage.total,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiException) {
                aiCooldowns.cool(candidate.provider, attempt.key, e.statusCode)
                lastError = e.message.orEmpty()
                // Half an answer is already on screen; a second attempt would
                // append a different model's reply to the same bubble.
                if (emitted > 0) {
                    return failure("Jawaban terputus: $lastError")
                }
            } catch (e: UnknownHostException) {
                lastError = "Tidak dapat terhubung ke ${candidate.provider.label}. Periksa koneksi atau base URL."
                if (emitted > 0) return failure("Jawaban terputus: $lastError")
            } catch (e: IOException) {
                lastError = "Koneksi terputus saat menghubungi ${candidate.provider.label}: ${e.message}"
                if (emitted > 0) return failure("Jawaban terputus: $lastError")
            }
        }

        return failure(lastError.ifBlank { "Provider tidak mengembalikan jawaban." })
    }

    private fun record(usage: TokenUsage, cost: Double) {
        if (usage.total > 0) {
            settings.recordUsage(usage.prompt.toLong(), usage.completion.toLong(), cost)
        }
    }

    private fun failure(message: String) = ChatMessage(
        role = Role.ASSISTANT,
        content = message,
        isError = true,
        modelLabel = "",
    )
}

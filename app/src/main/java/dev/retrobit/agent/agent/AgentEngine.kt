package dev.retrobit.agent.agent

import dev.retrobit.agent.ai.AiClientFactory
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.AiException
import dev.retrobit.agent.ai.TokenUsage
import dev.retrobit.agent.ai.WireTurn
import dev.retrobit.agent.ai.parseObject
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.DatabaseManager
import dev.retrobit.agent.data.DesignSystem
import android.content.Context
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.ProjectRepository
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.SecretStore
import dev.retrobit.agent.data.SettingsStore
import dev.retrobit.agent.data.StepStatus
import dev.retrobit.agent.data.AgentStep
import dev.retrobit.agent.data.Templates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.UnknownHostException

sealed interface AgentEvent {
    data class StepStarted(val step: AgentStep) : AgentEvent
    data class StepFinished(val step: AgentStep) : AgentEvent
    data class AssistantText(val text: String) : AgentEvent
    data class Notice(val text: String) : AgentEvent
}

class AgentEngine(
    private val context: Context,
    private val repository: ProjectRepository,
    private val settings: SettingsStore,
    private val secrets: SecretStore,
) {

    companion object {
        const val MAX_STEPS = 14
        fun keyFor(providerName: String) = "ai.apiKey.$providerName"
    }

    suspend fun run(
        project: Project,
        prompt: String,
        history: List<ChatMessage>,
        onEvent: suspend (AgentEvent) -> Unit,
    ): ChatMessage {
        val config = settings.config.value
        val db = DatabaseManager(context, project.id)
        repository.filesFor(project.id).createSnapshot()
        return try {
            if (config.isDemo) {
                DemoAgent.run(project, prompt, repository, db, onEvent)
            } else {
                runCloud(project, prompt, history, config, db, onEvent)
            }
        } finally {
            db.close()
        }
    }

    private suspend fun runCloud(
        project: Project,
        prompt: String,
        history: List<ChatMessage>,
        config: AiConfig,
        db: DatabaseManager,
        onEvent: suspend (AgentEvent) -> Unit,
    ): ChatMessage {
        val apiKey = secrets.get(keyFor(config.provider.name)).orEmpty()
        if (config.catalog().requiresKey && apiKey.isBlank()) {
            return errorMessage(
                "API key ${config.provider.label} belum diatur.\nBuka tab AI Cloud untuk menghubungkan provider, atau pakai mode Demo untuk mencoba tanpa key.",
                emptyList(), emptyList(), config,
            )
        }

        val files = repository.filesFor(project.id)
        var design = project.design
        val startedAt = System.currentTimeMillis()
        val finishedSteps = mutableListOf<AgentStep>()
        val changedFiles = linkedSetOf<String>()
        val textParts = mutableListOf<String>()
        var usage = TokenUsage()

        val runner = AgentToolRunner(
            files = files,
            db = db,
            secrets = secrets,
            currentDesign = { design },
            onDesignChange = { newDesign ->
                design = newDesign
                Templates.applyDesign(files, newDesign)
                repository.update(project.copy(design = newDesign))
                repository.notifyFilesChanged()
            },
            onProjectFileChanged = { repository.notifyFilesChanged() },
        )

        val client = AiClientFactory.create(config, apiKey)
        val persona = project.systemPrompt?.takeIf { it.isNotBlank() }
            ?: settings.systemPrompt.ifBlank { SettingsStore.DEFAULT_SYSTEM_PROMPT }
        val system = buildSystemPrompt(persona, project, files.treeText(), design, db, secrets, settings.agentLanguage)
        val transcript = mutableListOf<WireTurn>()
        history.filter { it.role != Role.SYSTEM && it.content.isNotBlank() }.takeLast(10).forEach { message ->
            when (message.role) {
                Role.USER -> transcript.add(WireTurn.User(message.content.take(6000)))
                Role.ASSISTANT -> transcript.add(WireTurn.Assistant(message.content.take(6000), emptyList()))
                else -> Unit
            }
        }
        transcript.add(WireTurn.User(prompt))

        var lastError: String? = null
        try {
            var stepIndex = 0
            while (stepIndex < MAX_STEPS) {
                currentCoroutineContext().ensureActive()
                if (stepIndex > 0) delay(150)
                onEvent(AgentEvent.Notice("Menghubungi ${config.provider.label} · ${config.displayModel}"))
                val response = try {
                    client.chat(
                        system = system,
                        turns = transcript,
                        tools = AgentToolRunner.DEFINITIONS,
                        temperature = config.temperature,
                        maxTokens = config.maxTokens,
                    )
                } catch (e: AiException) {
                    lastError = e.message
                    break
                } catch (e: UnknownHostException) {
                    lastError = "Tidak dapat terhubung ke ${config.provider.label}. Periksa koneksi internet atau base URL."
                    break
                } catch (e: IOException) {
                    lastError = "Koneksi terputus saat menghubungi ${config.provider.label}: ${e.message}"
                    break
                }

                usage = TokenUsage(
                    prompt = usage.prompt + response.usage.prompt,
                    completion = usage.completion + response.usage.completion,
                )
                if (response.text.isNotBlank()) {
                    textParts.add(response.text.trim())
                    onEvent(AgentEvent.AssistantText(response.text.trim()))
                }
                if (response.toolCalls.isEmpty()) break

                transcript.add(WireTurn.Assistant(response.text.trim(), response.toolCalls))
                for (call in response.toolCalls) {
                    currentCoroutineContext().ensureActive()
                    val args = parseObject(call.argumentsJson)
                    val step = AgentStep(
                        tool = call.name,
                        title = AgentToolRunner.titleFor(call.name, args),
                        detail = "Berjalan…",
                        status = StepStatus.RUNNING,
                    )
                    onEvent(AgentEvent.StepStarted(step))
                    val callStart = System.currentTimeMillis()
                    val outcome = runner.execute(call)
                    val duration = System.currentTimeMillis() - callStart
                    val finished = step.copy(
                        title = if (outcome.title.isNotBlank()) outcome.title else step.title,
                        detail = outcome.detail,
                        status = if (outcome.success) StepStatus.DONE else StepStatus.FAILED,
                        durationMs = duration,
                    )
                    finishedSteps.add(finished)
                    changedFiles.addAll(outcome.filesChanged)
                    onEvent(AgentEvent.StepFinished(finished))
                    transcript.add(WireTurn.ToolResult(call.id, call.name, outcome.modelOutput.take(16000)))
                }
                stepIndex++
            }

            if (stepIndex >= MAX_STEPS) {
                textParts.add("Batas ${MAX_STEPS} langkah tercapai. Minta saya melanjutkan jika masih ada yang kurang.")
            }
            if (lastError != null && textParts.isEmpty() && finishedSteps.isEmpty()) {
                return errorMessage(lastError!!, finishedSteps, changedFiles.toList(), config)
            }

            val cost = estimateCost(config, usage)
            if (usage.total > 0) {
                settings.recordUsage(usage.prompt.toLong(), usage.completion.toLong(), cost)
            }
            repository.update(project.copy(design = design, lastPrompt = prompt))
            repository.notifyFilesChanged()

            val fallback = when {
                changedFiles.isNotEmpty() -> "Selesai. ${changedFiles.size} file diperbarui: ${changedFiles.joinToString(", ").take(240)}"
                finishedSteps.isNotEmpty() -> "Selesai. ${finishedSteps.size} langkah dijalankan."
                else -> "Tidak ada perubahan yang dibuat."
            }
            if (lastError != null) {
                textParts.add("⚠️ $lastError")
            }
            val content = if (textParts.isEmpty()) fallback else textParts.joinToString("\n\n")
            return ChatMessage(
                role = Role.ASSISTANT,
                content = content,
                steps = finishedSteps,
                filesChanged = changedFiles.toList(),
                modelLabel = config.displayModel,
                tokens = usage.total,
                isError = lastError != null && changedFiles.isEmpty(),
            ).also {
                if (lastError != null) onEvent(AgentEvent.Notice(lastError))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return errorMessage(
                "Terjadi kesalahan: ${e.message ?: e.javaClass.simpleName}",
                finishedSteps, changedFiles.toList(), config,
            )
        }
    }

    private fun estimateCost(config: AiConfig, usage: TokenUsage): Double {
        val (inputPrice, outputPrice) = AiConfig.estimatedPricePerMillion(config.displayModel)
        return usage.prompt / 1_000_000.0 * inputPrice + usage.completion / 1_000_000.0 * outputPrice
    }

    private fun errorMessage(
        message: String,
        steps: List<AgentStep>,
        files: List<String>,
        config: AiConfig,
    ): ChatMessage = ChatMessage(
        role = Role.ASSISTANT,
        content = message,
        steps = steps,
        filesChanged = files,
        isError = true,
        modelLabel = config.displayModel,
    )

    private fun buildSystemPrompt(
        persona: String,
        project: Project,
        tree: String,
        design: DesignSystem,
        db: DatabaseManager,
        secrets: SecretStore,
        language: String,
    ): String {
        val secretNames = secrets.secrets().map { it.key }
        val requested = secrets.requests().map { it.key }
        val languageRule = if (language == "en") {
            "Respond in English. Code comments and UI copy should match the user's request language."
        } else {
            "Balas dalam Bahasa Indonesia yang santai dan jelas. Salinan UI mengikuti bahasa permintaan pengguna."
        }
        return """
            $persona

            ## PERAN PRODUK
            Kamu adalah RetroBit, AI product engineer yang bekerja di dalam aplikasi Android RetroBit Agent.
            Kamu membangun aplikasi web statis lengkap yang langsung tampil di preview WebView perangkat.

            ## PROYEK SAAT INI
            Nama: ${project.name}
            Tagline: ${project.tagline}
            Entry point: index.html (WAJIB ada)
            Tanggal: ${java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale("id")).format(java.util.Date())}

            Struktur file:
            $tree

            ## DESIGN SYSTEM
            primary=${design.primary} accent=${design.accent} background=${design.background} surface=${design.surface}
            text=${design.textColor} muted=${design.muted} radius=${design.radius}px font=${design.font} mode=${design.mode}
            Semua halaman memakai variabel CSS --rb-primary, --rb-accent, --rb-bg, --rb-surface, --rb-text, --rb-muted, --rb-radius, --rb-font.
            Pertahankan variabel ini saat menulis CSS. Untuk mengubah tema gunakan tool update_design.

            ## DATABASE (SQLite perangkat)
            ${db.statsText()}
            API di preview: window.RetroBitDB.query(sql, params) → Promise<{columns, rows, error, message, changes}>,
            window.RetroBitDB.exec(sql), dan window.RetroBitToast(pesan). Gunakan untuk aplikasi yang butuh data nyata.

            ## SECRET
            ${if (secretNames.isEmpty()) "Belum ada secret tersimpan." else "Tersimpan (nama saja): ${secretNames.joinToString(", ")}"}
            ${if (requested.isEmpty()) "" else "Menunggu diisi pengguna: ${requested.joinToString(", ")}"}
            Jangan pernah mengarang atau menulis nilai rahasia ke dalam file. Gunakan window.__RETROBIT_ENV__.NAMA_SECRET
            hanya untuk secret yang ditandai boleh diekspos, dan minta lewat request_secret bila belum ada.

            ## ATURAN MEMBANGUN
            - Hasil harus berjalan di WebView dengan protokol file:// tanpa server, tanpa build step, tanpa bundler.
            - Gunakan satu index.html berisi CSS dan JS inline bila memungkinkan. File tambahan boleh: css/js terpisah, svg, json, md.
            - Boleh memuat library dari CDN (https) tetapi aplikasi harus tetap tampil rapi bila offline.
            - Mobile-first, responsif hingga desktop, kontras memadai, target sentuh minimal 44px, dan hormati safe-area.
            - Jangan memakai lorem ipsum. Tulis konten Indonesia yang meyakinkan dan relevan.
            - Akses file lokal via fetch/XHR tidak diizinkan; tanam data langsung di JS.
            - Jika mengubah aplikasi yang sudah ada, baca dulu file terkait, pertahankan fitur yang sudah bekerja.
            - Jangan pernah menghapus index.html. Jangan membuat file biner.
            - Utamakan hasil yang indah: hierarki tipografi jelas, spacing lega, animasi halus, dan detail kecil yang terasa premium.

            ## PROTOKOL KERJA
            1) Jika perlu konteks, panggil list_files / read_file / inspect_database lebih dulu.
            2) Rencanakan sebentar, lalu langsung tulis file dengan tool. Jangan menampilkan kode panjang di balasan chat.
            3) Verifikasi hasil (baca ulang bagian penting) dan perbaiki bila ada kesalahan.
            4) Akhiri dengan ringkasan 1–3 kalimat tentang apa yang berubah dan apa yang bisa dicoba selanjutnya.
            Kamu boleh memanggil beberapa tool sekaligus. Jangan bertanya balik untuk hal kecil — ambil keputusan terbaik.
            $languageRule
        """.trimIndent()
    }
}

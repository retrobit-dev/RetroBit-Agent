package dev.retrobit.agent.agent

import dev.retrobit.agent.data.AgentStep
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.DatabaseManager
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.ProjectRepository
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.StepStatus
import dev.retrobit.agent.data.Templates
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Deterministic template simulator, used only after an explicit DEMO selection.
 * It builds real screens from the bundled templates so the whole flow can be
 * tried without an API key. It is explicitly labelled as a demo in the UI.
 */
object DemoAgent {

    private data class Intent(val template: String, val preset: String, val emoji: String)

    private fun detect(prompt: String): Intent? {
        val p = prompt.lowercase(Locale.ROOT)
        fun has(vararg keys: String) = keys.any { p.contains(it) }
        return when {
            has("toko", "jual", "ecommerce", "e-commerce", "katalog", "produk", "commerce", "belanja", "restoran", "kafe", "cafe", "makanan") ->
                Intent("commerce", "sunset", "🛍️")
            has("tugas", "todo", "to-do", "task", "checklist", "produktivitas", "kebiasaan") ->
                Intent("tasks", "aurora", "✅")
            has("chat", "obrolan", "pesan", "messaging", "percakapan", "forum") ->
                Intent("chat", "midnight", "💬")
            has("sehat", "fitness", "olahraga", "workout", "kesehatan", "lari", "gym") ->
                Intent("fitness", "midnight", "💪")
            has("portofolio", "portfolio", "karya", "desainer", "fotografer", "studio") ->
                Intent("portfolio", "mono", "🎨")
            has("landing", "saas", "marketing", "startup", "bisnis", "promosi", "website perusahaan") ->
                Intent("saas", "midnight", "☁️")
            has("kosong", "blank", "dari nol", "kanvas") ->
                Intent("blank", "candy", "✦")
            else -> null
        }
    }

    suspend fun run(
        project: Project,
        prompt: String,
        repository: ProjectRepository,
        db: DatabaseManager,
        onEvent: suspend (AgentEvent) -> Unit,
    ): ChatMessage {
        val startedAt = System.currentTimeMillis()
        val files = repository.filesFor(project.id)
        val steps = mutableListOf<AgentStep>()
        val changed = linkedSetOf("index.html")
        var design = project.design

        suspend fun step(
            tool: String,
            title: String,
            detail: String,
            pause: Long = 420,
        ): AgentStep {
            val running = AgentStep(tool = tool, title = title, detail = "Berjalan…", status = StepStatus.RUNNING)
            onEvent(AgentEvent.StepStarted(running))
            delay(pause)
            val done = running.copy(detail = detail, status = StepStatus.DONE, durationMs = pause)
            steps.add(done)
            onEvent(AgentEvent.StepFinished(done))
            return done
        }

        onEvent(AgentEvent.Notice("Mode demo · membangun tanpa cloud"))

        step("list_files", "Membaca struktur proyek", "${files.fileCount()} file ditemukan")

        val intent = detect(prompt)
        val targetTemplate = intent?.template ?: project.templateId
        val info = Templates.byId(targetTemplate)
        val sameTemplate = targetTemplate == project.templateId
        design = if (intent != null) presetFor(intent.preset) else rotatePreset(project.design)

        step(
            "plan", "Menyusun rencana",
            "${info.name} · ${design.preset} · ${if (sameTemplate) "perbarui tema" else "template baru"}"
        )

        if (!sameTemplate) {
            repository.applyTemplate(project.id, targetTemplate, design)
            changed.addAll(listOf("index.html"))
        } else {
            Templates.applyDesign(files, design)
            repository.notifyFilesChanged()
        }

        step(
            "write_file", "Menulis index.html",
            "${files.fileCount()} file · tema ${design.preset}"
        )
        if (!sameTemplate) delay(260)

        if (prompt.matches(Regex(".*(database|tabel|data|sql|crud).*", RegexOption.IGNORE_CASE))) {
            val preset = when (targetTemplate) {
                "commerce" -> "products"
                "chat" -> "users"
                else -> "tasks"
            }
            db.seedPreset(preset)
            step("create_table", "Menyiapkan database", "Tabel contoh: $preset")
        }

        step("verify", "Memeriksa preview", if (files.exists("index.html")) "index.html siap" else "Memulihkan index.html")
        if (!files.exists("index.html")) {
            files.ensureEntryPoint()
            repository.notifyFilesChanged()
        }

        val updated = project.copy(
            templateId = targetTemplate,
            design = design,
            emoji = intent?.emoji ?: project.emoji,
            tagline = info.tagline,
            lastPrompt = prompt,
            updatedAt = System.currentTimeMillis(),
        )
        repository.update(updated)
        repository.notifyFilesChanged()

        val headline = if (sameTemplate) {
            "Saya menyegarkan tampilan **${project.name}** dengan tema **${design.preset}** dan warna aksen ${design.primary}."
        } else {
            "Saya membuat aplikasi **${info.name}** — ${info.tagline.lowercase()} — lengkap dengan tema **${design.preset}**."
        }
        val dbLine = if (steps.any { it.tool == "create_table" }) "\n\nDatabase contoh juga sudah diisi, buka tab **Data** untuk melihat tabelnya." else ""
        val content = """
            $headline

            **Mode demo aktif.** Semua perubahan di atas nyata dan bisa kamu buka di tab Preview. Untuk instruksi bebas — menambah login, mengubah layout, menghubungkan API — hubungkan provider AI di **AI Cloud**, lalu minta lagi.

            Coba juga: “buat toko online”, “aplikasi tugas”, “portofolio desainer”, atau “dashboard kesehatan”.$dbLine
        """.trimIndent()

        return ChatMessage(
            role = Role.ASSISTANT,
            content = content,
            steps = steps,
            filesChanged = changed.toList(),
            modelLabel = "Demo lokal",
            tokens = 0,
            createdAt = System.currentTimeMillis(),
        ).also {
            onEvent(AgentEvent.AssistantText(content))
        }
    }

    private fun presetFor(id: String): DesignSystem =
        DesignSystem.PRESETS.firstOrNull { it.id == id }?.design ?: DesignSystem()

    private fun rotatePreset(current: DesignSystem): DesignSystem {
        val presets = DesignSystem.PRESETS
        val index = presets.indexOfFirst { it.id == current.preset }.coerceAtLeast(0)
        return presets[(index + 1) % presets.size].design
    }
}

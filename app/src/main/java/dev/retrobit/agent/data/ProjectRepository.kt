package dev.retrobit.agent.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Owns project metadata, per-project files and conversation threads.
 * Metadata lives in filesDir/projects.json; files live in filesDir/projects/<id>/.
 */
class ProjectRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val lock = Mutex()
    private val indexFile = File(context.filesDir, "projects.json")

    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    private val _fileVersion = MutableStateFlow(0L)

    /** Bumped after every file write so preview / explorer can refresh. */
    val fileVersion: StateFlow<Long> = _fileVersion.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            val hadIndex = indexFile.exists()
            val loaded = runCatching {
                if (hadIndex) json.decodeFromString<List<Project>>(indexFile.readText()) else emptyList()
            }.getOrElse { emptyList() }
            _projects.value = loaded.sortedByDescending { it.updatedAt }
            // Seed the sample project only on the very first launch, not after the
            // user deletes every project on purpose.
            if (!hadIndex) seedFirstProject()
        }
    }

    private suspend fun seedFirstProject() {
        val demo = Project(
            name = "Nimbus",
            tagline = "Landing page SaaS",
            emoji = "☁️",
            templateId = "saas",
            design = DesignSystem.PRESETS.first().design,
        )
        val files = filesFor(demo.id)
        Templates.apply(context.assets, files, "saas", demo.design)
        val updated = demo.copy(updatedAt = System.currentTimeMillis() - 60_000)
        _projects.value = listOf(updated)
        persist()
    }

    suspend fun create(name: String, templateId: String, design: DesignSystem, emoji: String? = null): Project =
        withContext(Dispatchers.IO) {
            val info = Templates.byId(templateId)
            val accent = runCatching {
                0xFF000000L or (android.graphics.Color.parseColor(design.primary).toLong() and 0xFFFFFFL)
            }.getOrDefault(0xFF7C5CFF)
            val project = Project(
                name = name.ifBlank { "Aplikasi Baru" },
                tagline = info.tagline,
                emoji = emoji ?: info.emoji,
                accent = accent,
                templateId = templateId,
                design = design,
            )
            Templates.apply(context.assets, filesFor(project.id), templateId, design)
            lock.withLock {
                _projects.value = (listOf(project) + _projects.value)
                persist()
            }
            _fileVersion.value++
            project
        }

    suspend fun update(project: Project, touch: Boolean = true) = withContext(Dispatchers.IO) {
        lock.withLock {
            val next = project.copy(updatedAt = if (touch) System.currentTimeMillis() else project.updatedAt)
            _projects.value = _projects.value.map { if (it.id == next.id) next else it }.sortedByDescending { it.updatedAt }
            persist()
        }
    }

    suspend fun delete(projectId: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            _projects.value = _projects.value.filterNot { it.id == projectId }
            persist()
        }
        runCatching { File(context.filesDir, "projects/$projectId").deleteRecursively() }
        runCatching { context.deleteDatabase("rb_$projectId.db") }
    }

    suspend fun duplicate(projectId: String): Project? = withContext(Dispatchers.IO) {
        val source = _projects.value.firstOrNull { it.id == projectId } ?: return@withContext null
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        val srcRoot = filesFor(source.id).root
        val dstRoot = filesFor(copy.id).root
        if (srcRoot.exists()) srcRoot.copyRecursively(dstRoot, overwrite = true)
        lock.withLock {
            _projects.value = listOf(copy) + _projects.value
            persist()
        }
        _fileVersion.value++
        copy
    }

    suspend fun applyTemplate(projectId: String, templateId: String, design: DesignSystem) = withContext(Dispatchers.IO) {
        val files = filesFor(projectId)
        files.clearSources()
        Templates.apply(context.assets, files, templateId, design)
        _fileVersion.value++
    }

    fun filesFor(projectId: String): ProjectFiles =
        ProjectFiles(File(context.filesDir, "projects/$projectId"))

    fun project(projectId: String): Project? = _projects.value.firstOrNull { it.id == projectId }

    fun notifyFilesChanged() {
        _fileVersion.value++
    }

    suspend fun loadThread(projectId: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        val file = filesFor(projectId).metaFile("chat.json")
        runCatching {
            if (file.exists()) json.decodeFromString<ChatThread>(file.readText()).messages else emptyList()
        }.getOrElse { emptyList() }
    }

    suspend fun saveThread(projectId: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        val file = filesFor(projectId).metaFile("chat.json")
        runCatching {
            file.writeText(json.encodeToString(ChatThread(messages.takeLast(200))))
        }
    }

    suspend fun clearThread(projectId: String) = withContext(Dispatchers.IO) {
        runCatching { filesFor(projectId).metaFile("chat.json").delete() }
    }

    private fun persist() {
        runCatching { indexFile.writeText(json.encodeToString(_projects.value)) }
    }
}

package dev.retrobit.agent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.agent.AppContainer
import dev.retrobit.agent.agent.AgentEvent
import dev.retrobit.agent.data.AgentStep
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.ColumnInfo
import dev.retrobit.agent.data.DatabaseManager
import dev.retrobit.agent.data.DesignPreset
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.data.FileNode
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.QueryResult
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.SecretEntry
import dev.retrobit.agent.data.SecretRequest
import dev.retrobit.agent.data.TableInfo
import dev.retrobit.agent.data.Templates
import dev.retrobit.agent.preview.ConsoleEntry
import dev.retrobit.agent.preview.LanPreviewServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class LiveAgentState(
    val running: Boolean = false,
    val steps: List<AgentStep> = emptyList(),
    val text: String = "",
    val notice: String = "",
    val startedAt: Long = 0,
)

data class OpenFile(
    val path: String,
    val content: String,
    val isBinary: Boolean = false,
)

data class LanState(
    val running: Boolean = false,
    val url: String = "",
    val hits: Int = 0,
    val lastPath: String = "",
    val error: String? = null,
)

class WorkspaceViewModel(
    private val container: AppContainer,
    val projectId: String,
) : ViewModel() {

    private val repo = container.repository
    private val settings = container.settings
    private val secrets = container.secrets
    private val files get() = repo.filesFor(projectId)

    val database = DatabaseManager(container.context, projectId)
    val config = settings.config
    val autoPreview = settings.autoPreviewFlow
    val filesRoot: File get() = repo.filesFor(projectId).root

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _live = MutableStateFlow(LiveAgentState())
    val live: StateFlow<LiveAgentState> = _live.asStateFlow()
    private var agentJob: Job? = null

    private val _files = MutableStateFlow<List<FileNode>>(emptyList())
    val fileNodes: StateFlow<List<FileNode>> = _files.asStateFlow()

    val fileVersion: StateFlow<Long> = repo.fileVersion

    private val _reloadTick = MutableStateFlow(0)
    val reloadTick: StateFlow<Int> = _reloadTick.asStateFlow()

    private val _openFile = MutableStateFlow<OpenFile?>(null)
    val openFile: StateFlow<OpenFile?> = _openFile.asStateFlow()

    private val _hasSnapshot = MutableStateFlow(false)
    val hasSnapshot: StateFlow<Boolean> = _hasSnapshot.asStateFlow()

    private val _design = MutableStateFlow(DesignSystem())
    val design: StateFlow<DesignSystem> = _design.asStateFlow()
    private var designDirty = false

    private val _tables = MutableStateFlow<List<TableInfo>>(emptyList())
    val tables: StateFlow<List<TableInfo>> = _tables.asStateFlow()

    private val _selectedTable = MutableStateFlow<String?>(null)
    val selectedTable: StateFlow<String?> = _selectedTable.asStateFlow()

    private val _tablePreview = MutableStateFlow<QueryResult?>(null)
    val tablePreview: StateFlow<QueryResult?> = _tablePreview.asStateFlow()

    private val _tableSchema = MutableStateFlow<List<ColumnInfo>>(emptyList())
    val tableSchema: StateFlow<List<ColumnInfo>> = _tableSchema.asStateFlow()

    private val _queryResult = MutableStateFlow<QueryResult?>(null)
    val queryResult: StateFlow<QueryResult?> = _queryResult.asStateFlow()

    private val _queryRunning = MutableStateFlow(false)
    val queryRunning: StateFlow<Boolean> = _queryRunning.asStateFlow()

    private val _secretEntries = MutableStateFlow<List<SecretEntry>>(emptyList())
    val secretEntries: StateFlow<List<SecretEntry>> = _secretEntries.asStateFlow()

    private val _secretRequests = MutableStateFlow<List<SecretRequest>>(emptyList())
    val secretRequests: StateFlow<List<SecretRequest>> = _secretRequests.asStateFlow()

    private val _exposedSecrets = MutableStateFlow<Map<String, String>>(emptyMap())
    val exposedSecrets: StateFlow<Map<String, String>> = _exposedSecrets.asStateFlow()

    private val _console = MutableStateFlow<List<ConsoleEntry>>(emptyList())
    val console: StateFlow<List<ConsoleEntry>> = _console.asStateFlow()

    private val _lan = MutableStateFlow(LanState())
    val lan: StateFlow<LanState> = _lan.asStateFlow()
    private var lanServer: LanPreviewServer? = null

    val project: StateFlow<Project?> = repo.projects
        .map { list -> list.firstOrNull { it.id == projectId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repo.project(projectId))

    init {
        viewModelScope.launch {
            if (repo.projects.value.isEmpty()) repo.load()
            val loaded = repo.project(projectId)
            if (loaded != null && !designDirty) _design.value = loaded.design
            _messages.value = repo.loadThread(projectId)
            refreshFiles()
            refreshDb()
            refreshSecrets()
        }
        viewModelScope.launch {
            repo.projects.collect { list ->
                val current = list.firstOrNull { it.id == projectId } ?: return@collect
                if (!designDirty) _design.value = current.design
            }
        }
        viewModelScope.launch {
            repo.fileVersion.collect {
                refreshFiles()
                _openFile.value?.let { opened ->
                    val content = withContext(Dispatchers.IO) { files.read(opened.path) }
                    if (content != null && content != opened.content) {
                        _openFile.value = opened.copy(content = content)
                    }
                }
            }
        }
    }

    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty() || _live.value.running) return
        val currentProject = project.value ?: return
        val prior = _messages.value
        val userMessage = ChatMessage(role = Role.USER, content = text)
        _messages.value = prior + userMessage
        persistMessages()
        _live.value = LiveAgentState(running = true, startedAt = System.currentTimeMillis())
        agentJob = viewModelScope.launch {
            try {
                val result = container.engine.run(currentProject, text, prior) { event ->
                    when (event) {
                        is AgentEvent.StepStarted -> _live.update { state ->
                            if (state.steps.any { it.id == event.step.id }) state
                            else state.copy(steps = state.steps + event.step)
                        }
                        is AgentEvent.StepFinished -> _live.update { state ->
                            state.copy(steps = state.steps.map { if (it.id == event.step.id) event.step else it })
                        }
                        is AgentEvent.AssistantText -> _live.update { state ->
                            state.copy(text = if (state.text.isBlank()) event.text else state.text + "\n\n" + event.text)
                        }
                        is AgentEvent.Notice -> _live.update { it.copy(notice = event.text) }
                    }
                }
                _messages.update { it + result }
                persistMessages()
            } catch (e: CancellationException) {
                val stopped = ChatMessage(
                    role = Role.ASSISTANT,
                    content = "Dihentikan. File yang sudah ditulis tetap tersimpan dan bisa kamu lihat di tab Preview.",
                    modelLabel = "—",
                )
                _messages.update { it + stopped }
                persistMessages()
            } catch (e: Exception) {
                val failure = ChatMessage(
                    role = Role.ASSISTANT,
                    content = "Gagal menjalankan agen: ${e.message ?: e.javaClass.simpleName}",
                    isError = true,
                    modelLabel = "—",
                )
                _messages.update { it + failure }
                persistMessages()
            } finally {
                _live.value = LiveAgentState()
                refreshFiles()
                refreshDb()
                refreshSecrets()
                _reloadTick.update { it + 1 }
                agentJob = null
            }
        }
    }

    fun stopAgent() {
        agentJob?.cancel()
    }

    fun restoreLatestSnapshot() {
        viewModelScope.launch(Dispatchers.IO) {
            val snapshot = files.latestSnapshot() ?: return@launch
            val restored = files.restoreSnapshot(snapshot)
            if (restored) {
                repo.notifyFilesChanged()
                refreshFiles()
                withContext(Dispatchers.Main) { _reloadTick.update { it + 1 } }
            }
        }
    }

    fun clearConversation() {
        if (_live.value.running) return
        _messages.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) { repo.clearThread(projectId) }
    }

    fun reloadPreview() {
        _reloadTick.update { it + 1 }
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching { files.list() }.getOrElse { emptyList() }
            val snapshotAvailable = runCatching { files.latestSnapshot() != null }.getOrDefault(false)
            withContext(Dispatchers.Main) {
                _files.value = list
                _hasSnapshot.value = snapshotAvailable
            }
        }
    }

    fun openFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching { files.read(path) }.getOrNull()
            withContext(Dispatchers.Main) {
                _openFile.value = OpenFile(path, content ?: "File biner — tidak dapat ditampilkan.", content == null)
            }
        }
    }

    fun closeFile() {
        _openFile.value = null
    }

    fun saveFile(path: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            files.write(path, content)
            repo.notifyFilesChanged()
            openFile(path)
        }
    }

    fun createFile(path: String) {
        val clean = path.trim().removePrefix("/")
        if (clean.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!files.exists(clean)) files.write(clean, if (clean.endsWith(".css")) "/* ${clean} */\n" else "")
            repo.notifyFilesChanged()
            openFile(clean)
        }
    }

    fun deleteFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            files.delete(path)
            repo.notifyFilesChanged()
            if (_openFile.value?.path == path) _openFile.value = null
        }
    }

    fun updateDesign(transform: (DesignSystem) -> DesignSystem) {
        designDirty = true
        _design.value = transform(_design.value)
    }

    fun applyPreset(preset: DesignPreset) {
        designDirty = true
        _design.value = preset.design
    }

    fun saveDesign() {
        val currentProject = project.value ?: return
        val snapshot = _design.value
        viewModelScope.launch(Dispatchers.IO) {
            Templates.applyDesign(files, snapshot)
            repo.update(currentProject.copy(design = snapshot))
            repo.notifyFilesChanged()
            designDirty = false
            withContext(Dispatchers.Main) { _reloadTick.update { it + 1 } }
        }
    }

    fun resetDesign() {
        _design.value = project.value?.design ?: DesignSystem()
        designDirty = false
    }

    fun refreshDb() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = runCatching { database.tables() }.getOrElse { emptyList() }
            withContext(Dispatchers.Main) {
                _tables.value = list
                val selected = _selectedTable.value
                if (selected != null && list.none { it.name == selected }) {
                    _selectedTable.value = null
                    _tablePreview.value = null
                    _tableSchema.value = emptyList()
                }
            }
        }
    }

    fun selectTable(name: String) {
        _selectedTable.value = name
        viewModelScope.launch(Dispatchers.IO) {
            val result = database.rowPreview(name)
            val schema = database.columns(name)
            withContext(Dispatchers.Main) {
                _tablePreview.value = result
                _tableSchema.value = schema
            }
        }
    }

    fun runQuery(sql: String) {
        if (sql.isBlank() || _queryRunning.value) return
        _queryRunning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val result = database.run(sql)
            withContext(Dispatchers.Main) {
                _queryResult.value = result
                _queryRunning.value = false
            }
            refreshDb()
        }
    }

    fun seedDatabase(preset: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.seedPreset(preset)
            refreshDb()
        }
    }

    fun createTable(name: String) {
        if (!DatabaseManager.isSafeIdentifier(name.trim())) return
        viewModelScope.launch(Dispatchers.IO) {
            database.createTable(
                name.trim(),
                listOf(
                    dev.retrobit.agent.data.AgentColumn("id", "INTEGER", primaryKey = true),
                    dev.retrobit.agent.data.AgentColumn("title", "TEXT", notNull = true),
                    dev.retrobit.agent.data.AgentColumn("status", "TEXT", default = "'active'"),
                    dev.retrobit.agent.data.AgentColumn("created_at", "TEXT", default = "CURRENT_TIMESTAMP"),
                ),
                seedRows = 4,
            )
            refreshDb()
        }
    }

    fun clearDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            database.clearAll()
            withContext(Dispatchers.Main) {
                _selectedTable.value = null
                _tablePreview.value = null
                _tableSchema.value = emptyList()
                _queryResult.value = null
            }
            refreshDb()
        }
    }

    fun refreshSecrets() {
        val entries = secrets.secrets()
        _secretEntries.value = entries
        _secretRequests.value = secrets.requests()
        _exposedSecrets.value = entries.filter { it.exposeToPreview }.associate { it.key to it.value }
    }

    fun saveSecret(key: String, value: String, note: String, expose: Boolean) {
        val clean = key.trim().uppercase().replace(Regex("[^A-Z0-9_]"), "_")
        if (clean.isBlank() || value.isBlank()) return
        secrets.put(SecretEntry(clean, value, note.trim(), expose))
        secrets.removeRequest(clean)
        refreshSecrets()
    }

    fun deleteSecret(key: String) {
        secrets.remove(key)
        refreshSecrets()
    }

    fun addConsole(entry: ConsoleEntry) {
        _console.update { (it + entry).takeLast(300) }
    }

    fun clearConsole() {
        _console.value = emptyList()
    }

    fun toggleLan() {
        if (_lan.value.running) {
            lanServer?.stop()
            lanServer = null
            _lan.value = LanState()
            return
        }
        val port = settings.lanPort
        val server = LanPreviewServer(files, port) { path, _ ->
            _lan.update { it.copy(hits = it.hits + 1, lastPath = path) }
        }
        server.start().fold(
            onSuccess = { url ->
                lanServer = server
                _lan.value = LanState(running = true, url = url)
            },
            onFailure = { error ->
                _lan.value = LanState(error = "Tidak bisa membuka port $port: ${error.message}")
            },
        )
    }

    fun dismissLanError() {
        _lan.update { it.copy(error = null) }
    }

    suspend fun exportZip(): File? = withContext(Dispatchers.IO) {
        val currentProject = project.value ?: return@withContext null
        val dir = File(container.context.cacheDir, "exports").apply { mkdirs() }
        val safeName = currentProject.name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { "retrobit" }
        val destination = File(dir, "$safeName.zip")
        runCatching { files.exportZip(destination) }.getOrNull()
    }

    override fun onCleared() {
        agentJob?.cancel()
        lanServer?.stop()
        lanServer = null
        database.close()
        super.onCleared()
    }
}

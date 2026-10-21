package dev.retrobit.agent.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.agent.AppContainer
import dev.retrobit.agent.agent.AgentEngine
import dev.retrobit.agent.ai.AiClientFactory
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.AutoRouter
import dev.retrobit.agent.ai.AutoTarget
import dev.retrobit.agent.ai.KeyRing
import dev.retrobit.agent.ai.LocalServer
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.ai.QuotaPolicy
import dev.retrobit.agent.ai.QuotaState
import dev.retrobit.agent.data.SecretEntry
import kotlinx.coroutines.launch

data class ConnectionTest(val ok: Boolean, val message: String)

class CloudViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings
    private val secrets = container.secrets
    val config = settings.config
    val usage = settings.usage
    val quota = settings.quota

    var provider by mutableStateOf(ProviderKind.DEMO)
        private set
    var baseUrl by mutableStateOf("")
        private set
    var model by mutableStateOf("")
        private set
    var apiKey by mutableStateOf("")
    var temperature by mutableStateOf(0.6f)
    var maxTokens by mutableStateOf("8192")
    var testing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<ConnectionTest?>(null)
        private set
    var savedMessage by mutableStateOf<String?>(null)
        private set
    var keyStored by mutableStateOf(false)
        private set

    /** Models the local server reported, once the user asks for them. */
    var localModels by mutableStateOf<List<String>>(emptyList())
        private set
    var detectingModels by mutableStateOf(false)
        private set
    var detectMessage by mutableStateOf<String?>(null)
        private set

    var themeMode by mutableStateOf(settings.themeMode)
        private set
    var language by mutableStateOf(settings.agentLanguage)
        private set
    var autoPreview by mutableStateOf(settings.autoOpenPreview)
        private set

    var systemPrompt by mutableStateOf(settings.systemPrompt)
        private set

    val demoMode: Boolean get() = provider == ProviderKind.DEMO
    val autoMode: Boolean get() = provider == ProviderKind.AUTO

    /** Which providers Auto can see, and whether their key is already stored. */
    val autoTargets: List<AutoTarget> get() = AutoRouter.targets(secrets)

    /** How many keys the field for [kind] holds today. Several = pooled quota. */
    fun keyCount(kind: ProviderKind): Int =
        KeyRing.parse(secrets.get(AgentEngine.keyFor(kind.name))).size

    /** Free requests left today for [kind], or null when the provider is uncapped. */
    fun remaining(kind: ProviderKind, state: QuotaState): Int? =
        state.remaining(kind, QuotaPolicy.today(), keyCount(kind))

    init {
        val current = config.value
        provider = current.provider
        val preset = current.catalog()
        baseUrl = current.baseUrl.ifBlank { preset.baseUrl }
        model = current.model.ifBlank { preset.models.firstOrNull().orEmpty() }
        apiKey = secrets.get(AgentEngine.keyFor(provider.name)).orEmpty()
        keyStored = apiKey.isNotBlank()
        temperature = current.temperature.toFloat()
        maxTokens = current.maxTokens.toString()
    }

    fun chooseProvider(kind: ProviderKind) {
        provider = kind
        val preset = AiConfig.presetFor(kind)
        baseUrl = if (kind == ProviderKind.AUTO) "" else preset.baseUrl
        model = preset.models.firstOrNull().orEmpty()
        apiKey = secrets.get(AgentEngine.keyFor(kind.name)).orEmpty()
        keyStored = apiKey.isNotBlank()
        testResult = null
        savedMessage = null
        localModels = emptyList()
        detectMessage = null
    }

    fun updateBaseUrl(value: String) {
        baseUrl = value
    }

    fun updateModel(value: String) {
        model = value
    }

    /** Where the local server should be, phrased for the current host. */
    val localHint: String get() = LocalServer.hint(baseUrl.ifBlank { AiConfig.presetFor(ProviderKind.OLLAMA).baseUrl })

    /**
     * Asks the local server which models it has. Also rewrites a bare host into
     * a full base URL, because that is what people type.
     */
    fun detectLocalModels() {
        if (detectingModels) return
        val base = LocalServer.normalizeBaseUrl(baseUrl.ifBlank { AiConfig.presetFor(ProviderKind.OLLAMA).baseUrl })
        baseUrl = base
        val key = apiKey.trim().ifBlank { secrets.get(AgentEngine.keyFor(provider.name)).orEmpty() }
        detectingModels = true
        detectMessage = null
        viewModelScope.launch {
            val result = AiClientFactory.listLocalModels(base, key)
            result.fold(
                onSuccess = { models ->
                    localModels = models
                    detectMessage = when {
                        models.isEmpty() -> "Server menjawab, tetapi belum ada model. Jalankan: ollama pull qwen2.5-coder:7b"
                        else -> "${models.size} model ditemukan di server."
                    }
                    if (models.isNotEmpty() && model.isBlank()) model = models.first()
                },
                onFailure = { error ->
                    localModels = emptyList()
                    detectMessage = "Tidak bisa menghubungi ${LocalServer.tagsUrl(base)}: ${error.message ?: "gagal"}"
                },
            )
            detectingModels = false
        }
    }

    fun save() {
        val preset = AiConfig.presetFor(provider)
        val cleanKey = apiKey.trim()
        if (provider == ProviderKind.AUTO) {
            val ready = autoTargets.any { it.usable }
            settings.updateConfig { current ->
                current.copy(
                    provider = ProviderKind.AUTO,
                    baseUrl = "",
                    model = "auto",
                    temperature = temperature.toDouble(),
                    maxTokens = maxTokens.toIntOrNull()?.coerceIn(512, 64000) ?: 8192,
                    connectedAt = if (ready) System.currentTimeMillis() else current.connectedAt,
                )
            }
            savedMessage = if (ready) {
                "Auto aktif. RetroBit akan memakai provider pertama yang punya key."
            } else {
                "Auto disimpan, tetapi belum ada API key. Isi key di provider mana pun di atas."
            }
            testResult = null
            return
        }
        if (provider != ProviderKind.DEMO) {
            if (baseUrl.isBlank()) {
                savedMessage = "Base URL wajib diisi."
                return
            }
            if (preset.requiresKey && cleanKey.isBlank() && !keyStored) {
                savedMessage = "API key wajib diisi untuk ${preset.kind.label}."
                return
            }
            if (cleanKey.isNotBlank()) {
                secrets.put(SecretEntry(AgentEngine.keyFor(provider.name), cleanKey, "${provider.label} API key"))
                keyStored = true
                apiKey = cleanKey
            }
        }
        val normalizedBase = when (provider) {
            ProviderKind.DEMO -> ""
            ProviderKind.OLLAMA -> LocalServer.normalizeBaseUrl(baseUrl)
            else -> baseUrl.trim()
        }
        settings.updateConfig { current ->
            current.copy(
                provider = provider,
                baseUrl = normalizedBase,
                model = if (provider == ProviderKind.DEMO) "retrobit-demo" else model.trim(),
                temperature = temperature.toDouble(),
                maxTokens = maxTokens.toIntOrNull()?.coerceIn(512, 64000) ?: 8192,
                connectedAt = if (provider == ProviderKind.DEMO) 0 else if (keyStored || !preset.requiresKey) System.currentTimeMillis() else current.connectedAt,
            )
        }
        savedMessage = if (provider == ProviderKind.DEMO) "Mode demo aktif." else "Konfigurasi tersimpan."
        testResult = null
    }

    fun testConnection() {
        if (provider == ProviderKind.DEMO) {
            testResult = ConnectionTest(true, "Mode demo berjalan offline, tanpa API key.")
            return
        }
        if (provider == ProviderKind.AUTO) {
            testAutoConnection()
            return
        }
        val preset = AiConfig.presetFor(provider)
        val key = apiKey.trim().ifBlank { secrets.get(AgentEngine.keyFor(provider.name)).orEmpty() }
        if (preset.requiresKey && key.isBlank()) {
            testResult = ConnectionTest(false, "Isi API key terlebih dahulu.")
            return
        }
        val probeConfig = AiConfig(
            provider = provider,
            baseUrl = baseUrl.trim().ifBlank { preset.baseUrl },
            model = model.trim().ifBlank { preset.models.firstOrNull().orEmpty() },
            temperature = 0.0,
            maxTokens = 64,
        )
        testing = true
        testResult = null
        viewModelScope.launch {
            val result = AiClientFactory.testConnection(probeConfig, key)
            testResult = result.fold(
                onSuccess = { ConnectionTest(true, "Terhubung! Model menjawab: ${it.trim().ifBlank { "OK" }}") },
                onFailure = { ConnectionTest(false, it.message ?: "Gagal terhubung") },
            )
            testing = false
        }
    }

    /** Probes the Auto chain in order and reports the first provider that answers. */
    private fun testAutoConnection() {
        val candidates = AutoRouter.candidates(secrets, config.value)
        if (candidates.isEmpty()) {
            testResult = ConnectionTest(false, "Belum ada API key. Isi key Gemini, Groq, atau provider lain di atas.")
            return
        }
        testing = true
        testResult = null
        viewModelScope.launch {
            var report: ConnectionTest? = null
            for (candidate in candidates) {
                val key = secrets.get(AgentEngine.keyFor(candidate.provider.name)).orEmpty()
                val probe = candidate.copy(temperature = 0.0, maxTokens = 64)
                val result = AiClientFactory.testConnection(probe, key)
                report = result.fold(
                    onSuccess = {
                        ConnectionTest(
                            true,
                            "Auto memakai ${candidate.provider.label} · ${candidate.model}. Jawaban: ${it.trim().ifBlank { "OK" }}",
                        )
                    },
                    onFailure = { failure ->
                        ConnectionTest(false, "${candidate.provider.label}: ${failure.message ?: "gagal"}")
                    },
                )
                if (report.ok) break
            }
            testResult = report
            testing = false
        }
    }

    fun disconnect() {
        if (provider == ProviderKind.DEMO) return
        secrets.remove(AgentEngine.keyFor(provider.name))
        apiKey = ""
        keyStored = false
        settings.updateConfig { it.copy(connectedAt = 0, provider = ProviderKind.DEMO) }
        provider = ProviderKind.DEMO
        baseUrl = ""
        model = "retrobit-demo"
        testResult = ConnectionTest(true, "Provider diputuskan. Kembali ke mode demo.")
    }

    fun setTheme(mode: String) {
        themeMode = mode
        settings.themeMode = mode
    }

    fun updateLanguage(value: String) {
        language = value
        settings.agentLanguage = value
    }

    fun updateAutoPreview(value: Boolean) {
        autoPreview = value
        settings.autoOpenPreview = value
    }

    fun updateSystemPrompt(value: String) {
        systemPrompt = value
    }

    fun saveSystemPrompt() {
        settings.systemPrompt = systemPrompt
        systemPrompt = settings.systemPrompt
        savedMessage = "System prompt global disimpan. Chat baru memakai teks ini."
    }

    fun resetSystemPrompt() {
        settings.resetSystemPrompt()
        systemPrompt = settings.systemPrompt
        savedMessage = "System prompt kembali ke default."
    }

    fun resetUsage() {
        settings.resetUsage()
    }

    fun clearSavedMessage() {
        savedMessage = null
    }
}

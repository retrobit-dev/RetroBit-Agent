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
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.data.SecretEntry
import kotlinx.coroutines.launch

data class ConnectionTest(val ok: Boolean, val message: String)

class CloudViewModel(private val container: AppContainer) : ViewModel() {

    val settings = container.settings
    private val secrets = container.secrets
    val config = settings.config
    val usage = settings.usage

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

    var themeMode by mutableStateOf(settings.themeMode)
        private set
    var language by mutableStateOf(settings.agentLanguage)
        private set
    var autoPreview by mutableStateOf(settings.autoOpenPreview)
        private set

    var systemPrompt by mutableStateOf(settings.systemPrompt)
        private set

    val demoMode: Boolean get() = provider == ProviderKind.DEMO

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
        baseUrl = preset.baseUrl
        model = preset.models.firstOrNull().orEmpty()
        apiKey = secrets.get(AgentEngine.keyFor(kind.name)).orEmpty()
        keyStored = apiKey.isNotBlank()
        testResult = null
        savedMessage = null
    }

    fun updateBaseUrl(value: String) {
        baseUrl = value
    }

    fun updateModel(value: String) {
        model = value
    }

    fun save() {
        val preset = AiConfig.presetFor(provider)
        val cleanKey = apiKey.trim()
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
        settings.updateConfig { current ->
            current.copy(
                provider = provider,
                baseUrl = if (provider == ProviderKind.DEMO) "" else baseUrl.trim(),
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

    fun setLanguage(value: String) {
        language = value
        settings.agentLanguage = value
    }

    fun setAutoPreview(value: Boolean) {
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

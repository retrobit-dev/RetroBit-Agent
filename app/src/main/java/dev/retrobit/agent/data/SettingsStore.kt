package dev.retrobit.agent.data

import android.content.Context
import android.content.SharedPreferences
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.ProviderKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("retrobit_settings", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    private val _usage = MutableStateFlow(loadUsage())
    val usage: StateFlow<UsageRecord> = _usage.asStateFlow()

    private val _themeMode = MutableStateFlow(prefs.getString("theme_mode", "dark") ?: "dark")
    val themeModeFlow: StateFlow<String> = _themeMode.asStateFlow()

    var themeMode: String
        get() = _themeMode.value
        set(value) {
            prefs.edit().putString("theme_mode", value).apply()
            _themeMode.value = value
        }

    private val _agentLanguage = MutableStateFlow(prefs.getString("agent_language", "id") ?: "id")
    val agentLanguageFlow: StateFlow<String> = _agentLanguage.asStateFlow()

    var agentLanguage: String
        get() = _agentLanguage.value
        set(value) {
            prefs.edit().putString("agent_language", value).apply()
            _agentLanguage.value = value
        }

    private val _autoPreview = MutableStateFlow(prefs.getBoolean("auto_preview", true))
    val autoPreviewFlow: StateFlow<Boolean> = _autoPreview.asStateFlow()

    var autoOpenPreview: Boolean
        get() = _autoPreview.value
        set(value) {
            prefs.edit().putBoolean("auto_preview", value).apply()
            _autoPreview.value = value
        }

    var lanPort: Int
        get() = prefs.getInt("lan_port", 8787)
        set(value) = prefs.edit().putInt("lan_port", value).apply()

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) = prefs.edit().putBoolean("onboarded", value).apply()

    fun updateConfig(transform: (AiConfig) -> AiConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit().putString("ai_config", json.encodeToString(AiConfig.serializer(), next)).apply()
    }

    fun recordUsage(promptTokens: Long, completionTokens: Long, costUsd: Double) {
        val current = _usage.value
        val next = current.copy(
            promptTokens = current.promptTokens + promptTokens,
            completionTokens = current.completionTokens + completionTokens,
            requests = current.requests + 1,
            costUsd = current.costUsd + costUsd,
        )
        _usage.value = next
        prefs.edit()
            .putLong("usage_prompt", next.promptTokens)
            .putLong("usage_completion", next.completionTokens)
            .putLong("usage_requests", next.requests)
            .putFloat("usage_cost", next.costUsd.toFloat())
            .apply()
    }

    fun resetUsage() {
        _usage.value = UsageRecord()
        prefs.edit()
            .putLong("usage_prompt", 0)
            .putLong("usage_completion", 0)
            .putLong("usage_requests", 0)
            .putFloat("usage_cost", 0f)
            .apply()
    }

    private fun loadConfig(): AiConfig {
        val raw = prefs.getString("ai_config", null) ?: return AiConfig()
        return runCatching { json.decodeFromString(AiConfig.serializer(), raw) }
            .getOrElse { AiConfig(provider = ProviderKind.DEMO) }
    }

    private fun loadUsage(): UsageRecord = UsageRecord(
        promptTokens = prefs.getLong("usage_prompt", 0),
        completionTokens = prefs.getLong("usage_completion", 0),
        requests = prefs.getLong("usage_requests", 0),
        costUsd = prefs.getFloat("usage_cost", 0f).toDouble(),
    )
}

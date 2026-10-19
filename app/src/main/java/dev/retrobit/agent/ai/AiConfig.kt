package dev.retrobit.agent.ai

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind(val label: String) {
    DEMO("Demo lokal"),
    OPENROUTER("OpenRouter"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    DEEPSEEK("DeepSeek"),
    MISTRAL("Mistral"),
    TOGETHER("Together AI"),
    OLLAMA("Ollama / lokal"),
    CUSTOM("OpenAI-compatible"),
}

@Serializable
data class ProviderPreset(
    val kind: ProviderKind,
    val baseUrl: String,
    val models: List<String>,
    val keyHint: String,
    val requiresKey: Boolean = true,
    val docsUrl: String = "",
)

@Serializable
data class AiConfig(
    val provider: ProviderKind = ProviderKind.DEMO,
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.6,
    val maxTokens: Int = 8192,
    val connectedAt: Long = 0,
) {
    val isDemo: Boolean get() = provider == ProviderKind.DEMO
    val displayModel: String get() = model.ifBlank { catalog().models.firstOrNull().orEmpty() }

    fun catalog(): ProviderPreset = CATALOG.firstOrNull { it.kind == provider } ?: CATALOG.first()

    companion object {
        val CATALOG = listOf(
            ProviderPreset(
                ProviderKind.OPENROUTER, "https://openrouter.ai/api/v1",
                listOf(
                    "anthropic/claude-sonnet-4.5",
                    "anthropic/claude-haiku-4.5",
                    "openai/gpt-4.1",
                    "openai/gpt-4.1-mini",
                    "google/gemini-2.5-pro",
                    "deepseek/deepseek-chat-v3.1",
                    "qwen/qwen3-coder",
                    "meta-llama/llama-3.3-70b-instruct",
                ),
                keyHint = "sk-or-v1-…", docsUrl = "openrouter.ai/keys"
            ),
            ProviderPreset(
                ProviderKind.OPENAI, "https://api.openai.com/v1",
                listOf("gpt-4.1", "gpt-4.1-mini", "o4-mini", "gpt-4o-mini"),
                keyHint = "sk-…", docsUrl = "platform.openai.com/api-keys"
            ),
            ProviderPreset(
                ProviderKind.ANTHROPIC, "https://api.anthropic.com",
                listOf("claude-sonnet-4-5", "claude-haiku-4-5", "claude-opus-4-1"),
                keyHint = "sk-ant-…", docsUrl = "console.anthropic.com"
            ),
            ProviderPreset(
                ProviderKind.GEMINI, "https://generativelanguage.googleapis.com",
                listOf("gemini-2.5-flash", "gemini-2.5-pro"),
                keyHint = "AIza…", docsUrl = "aistudio.google.com/apikey"
            ),
            ProviderPreset(
                ProviderKind.GROQ, "https://api.groq.com/openai/v1",
                listOf("llama-3.3-70b-versatile", "qwen/qwen3-32b", "openai/gpt-oss-120b"),
                keyHint = "gsk_…", docsUrl = "console.groq.com/keys"
            ),
            ProviderPreset(
                ProviderKind.DEEPSEEK, "https://api.deepseek.com/v1",
                listOf("deepseek-chat", "deepseek-reasoner"),
                keyHint = "sk-…", docsUrl = "platform.deepseek.com"
            ),
            ProviderPreset(
                ProviderKind.MISTRAL, "https://api.mistral.ai/v1",
                listOf("mistral-large-latest", "codestral-latest", "mistral-small-latest"),
                keyHint = "…", docsUrl = "console.mistral.ai"
            ),
            ProviderPreset(
                ProviderKind.TOGETHER, "https://api.together.xyz/v1",
                listOf("Qwen/Qwen3-Coder-480B-A35B-Instruct", "Qwen/Qwen2.5-Coder-32B-Instruct", "deepseek-ai/DeepSeek-V3"),
                keyHint = "…", docsUrl = "api.together.ai/settings/api-keys"
            ),
            ProviderPreset(
                ProviderKind.OLLAMA, "http://10.0.2.2:11434/v1",
                listOf("qwen2.5-coder:7b", "llama3.2", "deepseek-coder-v2"),
                keyHint = "opsional", requiresKey = false, docsUrl = "ollama.com"
            ),
            ProviderPreset(
                ProviderKind.CUSTOM, "https://",
                listOf(""),
                keyHint = "opsional", requiresKey = false, docsUrl = ""
            ),
            ProviderPreset(
                ProviderKind.DEMO, "",
                listOf("retrobit-demo"),
                keyHint = "", requiresKey = false, docsUrl = ""
            ),
        )

        fun presetFor(kind: ProviderKind): ProviderPreset = CATALOG.first { it.kind == kind }

        /** Rough USD price per 1M tokens used for the usage estimate. */
        fun estimatedPricePerMillion(model: String): Pair<Double, Double> {
            val m = model.lowercase()
            return when {
                "opus" in m -> 15.0 to 75.0
                "sonnet" in m -> 3.0 to 15.0
                "haiku" in m -> 0.8 to 4.0
                "gemini-2.5-pro" in m -> 1.25 to 10.0
                "gemini" in m -> 0.3 to 2.5
                "gpt-4.1-mini" in m || "4o-mini" in m || "o4-mini" in m -> 0.4 to 1.6
                "gpt-4.1" in m || "gpt-4o" in m -> 2.5 to 10.0
                "deepseek" in m -> 0.3 to 1.2
                "qwen" in m || "llama" in m || "gpt-oss" in m -> 0.2 to 0.8
                "mistral-large" in m -> 2.0 to 6.0
                "codestral" in m -> 0.3 to 0.9
                else -> 1.0 to 3.0
            }
        }
    }
}

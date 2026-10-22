package dev.retrobit.agent.ai

import kotlinx.serialization.Serializable

@Serializable
enum class ProviderKind(val label: String) {
    AUTO("Auto"),
    DEMO("Demo lokal"),
    OPENROUTER("OpenRouter"),
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GEMINI("Google Gemini"),
    GROQ("Groq"),
    DEEPSEEK("DeepSeek"),
    MISTRAL("Mistral"),
    TOGETHER("Together AI"),
    ON_DEVICE("AI di HP (offline)"),
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
    /** Provider has a standing free tier even though the model id has no marker. */
    val freeTier: Boolean = false,
)

@Serializable
data class AiConfig(
    val provider: ProviderKind = ProviderKind.DEMO,
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.6,
    val maxTokens: Int = 8192,
    val connectedAt: Long = 0,
    val localContext: Int = 4096,
) {
    val isDemo: Boolean get() = provider == ProviderKind.DEMO
    val displayModel: String get() = model.ifBlank { catalog().models.firstOrNull().orEmpty() }

    fun catalog(): ProviderPreset = CATALOG.firstOrNull { it.kind == provider } ?: CATALOG.first()

    companion object {
        val CATALOG = listOf(
            ProviderPreset(
                ProviderKind.OPENROUTER, "https://openrouter.ai/api/v1",
                listOf(
                    "openrouter/free",
                    "google/gemma-4-31b-it:free",
                    "nvidia/nemotron-3-super-120b-a12b:free",
                    "anthropic/claude-sonnet-4.5",
                    "openai/gpt-4.1-mini",
                    "google/gemini-2.5-pro",
                    "deepseek/deepseek-chat-v3.1",
                    "qwen/qwen3-coder",
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
                listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro"),
                keyHint = "AIza…", docsUrl = "aistudio.google.com/apikey", freeTier = true
            ),
            ProviderPreset(
                ProviderKind.GROQ, "https://api.groq.com/openai/v1",
                listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "qwen/qwen3-32b"),
                keyHint = "gsk_…", docsUrl = "console.groq.com/keys", freeTier = true
            ),
            ProviderPreset(
                ProviderKind.DEEPSEEK, "https://api.deepseek.com/v1",
                listOf("deepseek-chat", "deepseek-reasoner"),
                keyHint = "sk-…", docsUrl = "platform.deepseek.com"
            ),
            ProviderPreset(
                ProviderKind.MISTRAL, "https://api.mistral.ai/v1",
                listOf("mistral-small-latest", "mistral-large-latest", "codestral-latest"),
                keyHint = "…", docsUrl = "console.mistral.ai", freeTier = true
            ),
            ProviderPreset(
                ProviderKind.TOGETHER, "https://api.together.xyz/v1",
                listOf("Qwen/Qwen3-Coder-480B-A35B-Instruct", "Qwen/Qwen2.5-Coder-32B-Instruct", "deepseek-ai/DeepSeek-V3"),
                keyHint = "…", docsUrl = "api.together.ai/settings/api-keys"
            ),
            ProviderPreset(
                ProviderKind.ON_DEVICE, "", listOf("GGUF di perangkat"),
                keyHint = "Tidak perlu key", requiresKey = false,
            ),
            ProviderPreset(
                ProviderKind.OLLAMA, "http://10.0.2.2:11434/v1",
                listOf("qwen2.5-coder:7b", "llama3.2", "deepseek-coder-v2"),
                keyHint = "opsional", requiresKey = false, docsUrl = "ollama.com", freeTier = true
            ),
            ProviderPreset(
                ProviderKind.CUSTOM, "https://",
                listOf(""),
                keyHint = "opsional", requiresKey = false, docsUrl = ""
            ),
            ProviderPreset(
                ProviderKind.AUTO, "",
                listOf("Auto"),
                keyHint = "", requiresKey = false, docsUrl = ""
            ),
            ProviderPreset(
                ProviderKind.DEMO, "",
                listOf("retrobit-demo"),
                keyHint = "", requiresKey = false, docsUrl = ""
            ),
        )

        fun presetFor(kind: ProviderKind): ProviderPreset = CATALOG.first { it.kind == kind }

        /**
         * OpenRouter marks zero-cost models with a `:free` suffix and also offers
         * an `openrouter/free` router that picks one of them for you.
         */
        fun isFreeModel(model: String): Boolean {
            val m = model.trim().lowercase()
            return m == "openrouter/free" || m.endsWith(":free")
        }

        /** Rough USD price per 1M tokens used for the usage estimate. */
        fun estimatedPricePerMillion(model: String): Pair<Double, Double> {
            if (isFreeModel(model)) return 0.0 to 0.0
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

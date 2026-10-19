package dev.retrobit.agent.ai

import dev.retrobit.agent.agent.AgentEngine
import dev.retrobit.agent.data.SecretStore

/** One provider that Auto would consider, and the model it would use there. */
data class AutoTarget(
    val kind: ProviderKind,
    val model: String,
    val hasKey: Boolean,
    val usable: Boolean,
)

/**
 * Picks the provider for the "Auto" option.
 *
 * RetroBit has no server that bills inference, so Auto cannot behave exactly like
 * a hosted builder: it routes across the keys the user already saved instead of
 * choosing a model on our behalf. The order is cheapest-capable first — the
 * providers with standing free tiers lead, paid ones follow, and Demo closes the
 * list so Auto always has something to run.
 */
object AutoRouter {

    /** Ordered provider -> model pairs used when the user leaves the model on Auto. */
    val PRIORITY: List<Pair<ProviderKind, String>> = listOf(
        ProviderKind.GEMINI to "gemini-2.5-flash",
        ProviderKind.GROQ to "openai/gpt-oss-120b",
        ProviderKind.OPENROUTER to "openrouter/free",
        ProviderKind.MISTRAL to "mistral-small-latest",
        ProviderKind.TOGETHER to "Qwen/Qwen2.5-Coder-32B-Instruct",
        ProviderKind.DEEPSEEK to "deepseek-chat",
        ProviderKind.ANTHROPIC to "claude-haiku-4-5",
        ProviderKind.OPENAI to "gpt-4.1-mini",
        ProviderKind.OLLAMA to "qwen2.5-coder:7b",
    )

    /**
     * Everything Auto can see, keyed or not, so the Cloud screen can show what is
     * missing. Ollama needs no key, but probing a server that is not running costs
     * a connection timeout, so it only joins once the user opts in by filling the
     * optional key field.
     */
    fun targets(secrets: SecretStore): List<AutoTarget> = PRIORITY.map { (kind, model) ->
        val hasKey = !secrets.get(AgentEngine.keyFor(kind.name)).isNullOrBlank()
        AutoTarget(kind = kind, model = model, hasKey = hasKey, usable = hasKey)
    }

    /** Concrete configs to try, best first. Empty when nothing is configured. */
    fun candidates(secrets: SecretStore, base: AiConfig): List<AiConfig> =
        targets(secrets)
            .filter { it.usable }
            .map { target ->
                base.copy(
                    provider = target.kind,
                    baseUrl = AiConfig.presetFor(target.kind).baseUrl,
                    model = target.model,
                )
            }
}

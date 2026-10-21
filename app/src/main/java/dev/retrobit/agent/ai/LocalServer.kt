package dev.retrobit.agent.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Helpers for the one provider that is actually unlimited: a model running on
 * the user's own machine.
 *
 * Two things go wrong for everybody the first time. The base URL is typed the
 * way a browser address bar takes it ("192.168.1.10:11434"), and the model name
 * is guessed from a list that does not match what is installed. Both are cheap
 * to fix with code instead of documentation.
 */
object LocalServer {

    /** Where an emulator finds the host machine. Meaningless on a phone, which is why the hint exists. */
    const val EMULATOR_BASE_URL = "http://10.0.2.2:11434/v1"

    /**
     * Accepts `host`, `host:port`, `http://host`, `host/v1`, with or without a
     * trailing slash, and always returns a URL the OpenAI-compatible client can
     * append `/chat/completions` to.
     */
    fun normalizeBaseUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Rebuilt rather than passed through: 'HTTP://' is legal in a browser
        // address bar, but every comparison downstream is lowercase.
        val withScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) -> "http://" + trimmed.substring(7)
            trimmed.startsWith("https://", ignoreCase = true) -> "https://" + trimmed.substring(8)
            else -> "http://$trimmed"
        }
        val withoutSlash = withScheme.trimEnd('/')
        val afterScheme = withoutSlash.substringAfter("://")
        // No path at all means the user gave a host: Ollama's OpenAI endpoint
        // lives under /v1, so add it instead of sending requests to /chat/completions.
        return if (afterScheme.contains('/')) withoutSlash else "$withoutSlash/v1"
    }

    /** Ollama's model list lives outside /v1. */
    fun tagsUrl(baseUrl: String): String {
        val normalized = normalizeBaseUrl(baseUrl)
        val root = normalized.removeSuffix("/v1")
        return "$root/api/tags"
    }

    /** Names from `GET /api/tags`, newest Ollama first in the JSON it returns. */
    fun models(body: String): List<String> {
        val root = runCatching { sharedJson.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return emptyList()
        val array: JsonArray = root["models"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            runCatching {
                val name = element.jsonObject["name"]?.jsonPrimitive?.content?.trim()
                name?.takeIf { it.isNotEmpty() }
            }.getOrNull()
        }.distinct()
    }

    /** One line the user can act on, given where the URL points. */
    fun hint(baseUrl: String): String {
        val host = normalizeBaseUrl(baseUrl).substringAfter("://").substringBefore('/')
        return when {
            host.startsWith("10.0.2.2") ->
                "10.0.2.2 hanya berlaku di emulator. Di HP, ganti dengan IP komputer, mis. 192.168.1.10:11434."
            host.startsWith("127.0.0.1") || host.startsWith("localhost") ->
                "localhost berarti HP itu sendiri, bukan komputer. Pakai IP komputer di jaringan yang sama."
            else ->
                "Di komputer: OLLAMA_HOST=0.0.0.0 ollama serve — lalu pastikan HP dan komputer di Wi-Fi yang sama."
        }
    }
}

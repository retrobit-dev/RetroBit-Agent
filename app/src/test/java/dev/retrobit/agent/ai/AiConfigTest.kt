package dev.retrobit.agent.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiConfigTest {

    @Test
    fun `every provider kind has a catalog entry`() {
        ProviderKind.entries.forEach { kind ->
            assertNotNull("no preset for $kind", AiConfig.CATALOG.firstOrNull { it.kind == kind })
        }
    }

    @Test
    fun `demo mode needs no key`() {
        val preset = AiConfig.presetFor(ProviderKind.DEMO)
        assertFalse(preset.requiresKey)
        assertTrue(AiConfig(provider = ProviderKind.DEMO).isDemo)
        assertEquals(ProviderKind.ON_DEVICE, AiConfig().provider)
    }

    // Ollama runs on the local network and a custom endpoint may sit behind its
    // own auth, so only the hosted providers are keyed.
    private val keyless = setOf(
        ProviderKind.AUTO, ProviderKind.DEMO, ProviderKind.OLLAMA, ProviderKind.CUSTOM, ProviderKind.ON_DEVICE,
    )

    @Test
    fun `hosted providers require a key`() {
        ProviderKind.entries.filter { it !in keyless }.forEach { kind ->
            val preset = AiConfig.presetFor(kind)
            assertTrue("${kind.label} should require a key", preset.requiresKey)
            assertTrue("${kind.label} needs a key hint", preset.keyHint.isNotBlank())
            assertTrue("${kind.label} needs a base url", preset.baseUrl.isNotBlank())
            assertTrue("${kind.label} needs at least one model", preset.models.isNotEmpty())
        }
    }

    @Test
    fun `local and custom providers stay usable without a key`() {
        keyless.forEach { kind ->
            val preset = AiConfig.presetFor(kind)
            assertFalse("${kind.label} should not require a key", preset.requiresKey)
        }
        // Custom endpoints are the one preset whose URL the user must fill in.
        assertEquals("https://", AiConfig.presetFor(ProviderKind.CUSTOM).baseUrl)
    }

    @Test
    fun `every catalog entry has a unique kind`() {
        val kinds = AiConfig.CATALOG.map { it.kind }
        assertEquals(kinds.size, kinds.toSet().size)
        assertEquals(ProviderKind.entries.toSet(), kinds.toSet())
    }

    @Test
    fun `display model falls back to the first catalog model`() {
        val config = AiConfig(provider = ProviderKind.OPENAI, model = "")
        assertEquals(config.catalog().models.first(), config.displayModel)
    }

    @Test
    fun `explicit model wins over the catalog default`() {
        val config = AiConfig(provider = ProviderKind.OPENAI, model = "gpt-4.1-mini")
        assertEquals("gpt-4.1-mini", config.displayModel)
    }

    @Test
    fun `custom provider is present in the catalog too`() {
        val config = AiConfig(provider = ProviderKind.CUSTOM, baseUrl = "")
        assertEquals(ProviderKind.CUSTOM, config.catalog().kind)
        assertTrue(config.catalog().docsUrl.isNotBlank() || config.catalog().baseUrl.isNotBlank())
    }

    @Test
    fun `price table matches the documented families`() {
        assertEquals(15.0 to 75.0, AiConfig.estimatedPricePerMillion("claude-opus-4"))
        assertEquals(3.0 to 15.0, AiConfig.estimatedPricePerMillion("claude-sonnet-4.5"))
        assertEquals(0.8 to 4.0, AiConfig.estimatedPricePerMillion("claude-haiku-4.5"))
        assertEquals(1.25 to 10.0, AiConfig.estimatedPricePerMillion("gemini-2.5-pro"))
        assertEquals(0.4 to 1.6, AiConfig.estimatedPricePerMillion("gpt-4.1-mini"))
        assertEquals(0.3 to 1.2, AiConfig.estimatedPricePerMillion("deepseek-chat-v3.1"))
    }

    @Test
    fun `price lookup is case insensitive`() {
        assertEquals(
            AiConfig.estimatedPricePerMillion("claude-sonnet-4.5"),
            AiConfig.estimatedPricePerMillion("Claude-Sonnet-4.5"),
        )
    }

    @Test
    fun `unknown model gets the generic price`() {
        assertEquals(1.0 to 3.0, AiConfig.estimatedPricePerMillion("mystery-model"))
    }

    // --- free models ---------------------------------------------------------

    @Test
    fun `free models cost nothing`() {
        assertEquals(0.0 to 0.0, AiConfig.estimatedPricePerMillion("google/gemma-4-31b-it:free"))
        assertEquals(0.0 to 0.0, AiConfig.estimatedPricePerMillion("openrouter/free"))
        assertTrue(AiConfig.isFreeModel("deepseek/deepseek-r1:FREE"))
        assertFalse(AiConfig.isFreeModel("anthropic/claude-sonnet-4.5"))
    }

    @Test
    fun `free marker is not confused with a paid prefix`() {
        assertFalse(AiConfig.isFreeModel("openrouter/free-form-model"))
        assertFalse(AiConfig.isFreeModel(""))
    }

    // --- auto router ---------------------------------------------------------

    @Test
    fun `auto priority starts with the providers that have a free tier`() {
        assertEquals(ProviderKind.GEMINI, AutoRouter.PRIORITY.first().first)
        val freeFirst = AutoRouter.PRIORITY.take(3).map { it.first }
        assertEquals(listOf(ProviderKind.GEMINI, ProviderKind.GROQ, ProviderKind.OPENROUTER), freeFirst)
    }

    @Test
    fun `auto priority is unique, catalogued and never demo or auto`() {
        val kinds = AutoRouter.PRIORITY.map { it.first }
        assertEquals(kinds.size, kinds.toSet().size)
        kinds.forEach { kind ->
            assertNotNull("no catalog entry for $kind", AiConfig.CATALOG.firstOrNull { it.kind == kind })
            assertFalse(kind == ProviderKind.DEMO || kind == ProviderKind.AUTO || kind == ProviderKind.CUSTOM)
        }
        AutoRouter.PRIORITY.forEach { (_, model) ->
            assertTrue("blank auto model", model.isNotBlank())
        }
    }

    @Test
    fun `auto prefers a free model on openrouter`() {
        val model = AutoRouter.PRIORITY.first { it.first == ProviderKind.OPENROUTER }.second
        assertTrue(AiConfig.isFreeModel(model))
    }
}

class AiErrorTest {

    @Test
    fun `401 explains how to fix the key`() {
        val error = errorFor(401, "{\"error\":\"bad key\"}", "OpenAI")
        assertEquals(401, error.statusCode)
        assertTrue(error.message!!.contains("API key ditolak oleh OpenAI"))
        assertTrue(error.message!!.contains("bad key"))
    }

    @Test
    fun `429 mentions the rate limit`() {
        assertTrue(errorFor(429, "", "Groq").message!!.contains("rate limit"))
    }

    @Test
    fun `5xx blames the provider`() {
        assertTrue(errorFor(503, "", "Gemini").message!!.contains("sedang bermasalah"))
    }

    @Test
    fun `long bodies are truncated in the message`() {
        val message = errorFor(400, "x".repeat(2000), "Mistral").message!!
        assertTrue(message.length < 600)
    }

    @Test
    fun `extractError reads both error shapes`() {
        assertEquals("nope", extractError("{\"error\":{\"message\":\"nope\"}}"))
        assertEquals("plain", extractError("{\"error\":\"plain\"}"))
        assertNull(extractError("{\"ok\":true}"))
        assertNull(extractError("<html>not json</html>"))
    }
}

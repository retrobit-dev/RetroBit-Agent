package dev.retrobit.agent.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The policy behind "unlimited": pooled keys, daily budgets and cooldowns.
 * All of it is pure, so none of this needs a device or a network.
 */
class UnlimitedTest {

    private fun config(kind: ProviderKind, model: String = "m") =
        AiConfig(provider = kind, baseUrl = "http://localhost", model = model)

    // --- KeyRing --------------------------------------------------------------

    @Test
    fun `a single key parses to one entry`() {
        assertEquals(listOf("sk-abc"), KeyRing.parse("sk-abc"))
    }

    @Test
    fun `lines are trimmed and blanks dropped`() {
        val raw = "  sk-one  \n\n\tsk-two\n   \nsk-three\n"
        assertEquals(listOf("sk-one", "sk-two", "sk-three"), KeyRing.parse(raw))
    }

    @Test
    fun `the same key pasted twice counts once`() {
        assertEquals(listOf("gsk_1", "gsk_2"), KeyRing.parse("gsk_1\ngsk_2\ngsk_1"))
    }

    @Test
    fun `absurd pastes are capped`() {
        val raw = (1..40).joinToString("\n") { "key-$it" }
        assertEquals(KeyRing.MAX_KEYS, KeyRing.parse(raw).size)
    }

    @Test
    fun `missing secret parses to nothing`() {
        assertEquals(emptyList<String>(), KeyRing.parse(null))
        assertEquals(emptyList<String>(), KeyRing.parse("   \n  "))
    }

    // --- cooldowns ------------------------------------------------------------

    @Test
    fun `rate limited key rests for five minutes`() {
        var now = 1_000L
        val cooldowns = KeyCooldowns { now }

        cooldowns.cool(ProviderKind.GROQ, "gsk_1", 429)
        assertTrue(cooldowns.cooling(ProviderKind.GROQ, "gsk_1"))

        now += KeyCooldowns.RATE_LIMITED_MS - 1
        assertTrue(cooldowns.cooling(ProviderKind.GROQ, "gsk_1"))

        now += 1
        assertFalse(cooldowns.cooling(ProviderKind.GROQ, "gsk_1"))
    }

    @Test
    fun `rejected key rests for a day, billing error for an hour`() {
        val cooldowns = KeyCooldowns { 0L }
        assertEquals(KeyCooldowns.REJECTED_MS, cooldowns.durationFor(401))
        assertEquals(KeyCooldowns.REJECTED_MS, cooldowns.durationFor(403))
        assertEquals(KeyCooldowns.QUOTA_MS, cooldowns.durationFor(402))
        assertEquals(0L, cooldowns.durationFor(500))
        assertEquals(0L, cooldowns.durationFor(0))
    }

    @Test
    fun `a server error does not sideline the key`() {
        val cooldowns = KeyCooldowns { 0L }
        cooldowns.cool(ProviderKind.GEMINI, "g-1", 500)
        assertFalse(cooldowns.cooling(ProviderKind.GEMINI, "g-1"))
    }

    @Test
    fun `cooldown is scoped to one provider`() {
        val cooldowns = KeyCooldowns { 0L }
        cooldowns.cool(ProviderKind.GEMINI, "shared", 429)
        assertTrue(cooldowns.cooling(ProviderKind.GEMINI, "shared"))
        assertFalse(cooldowns.cooling(ProviderKind.GROQ, "shared"))
    }

    // --- quota ----------------------------------------------------------------

    @Test
    fun `counts accumulate within a day and reset on the next`() {
        var state = QuotaState()
        repeat(3) { state = state.record(ProviderKind.GEMINI, 100) }

        assertEquals(3, state.usedToday(ProviderKind.GEMINI, 100))
        assertEquals(0, state.usedToday(ProviderKind.GEMINI, 101))
        assertEquals(QuotaPolicy.GEMINI_PER_KEY - 3, state.remaining(ProviderKind.GEMINI, 100))
        assertEquals(QuotaPolicy.GEMINI_PER_KEY, state.remaining(ProviderKind.GEMINI, 101))
    }

    @Test
    fun `each stored key adds its own daily budget`() {
        val state = QuotaState(day = 7, used = mapOf(ProviderKind.GROQ.name to 900))
        assertEquals(100, state.remaining(ProviderKind.GROQ, 7, keys = 1))
        assertEquals(1_100, state.remaining(ProviderKind.GROQ, 7, keys = 2))
    }

    @Test
    fun `an exhausted provider reports zero, not a negative number`() {
        val state = QuotaState(day = 7, used = mapOf(ProviderKind.OPENROUTER.name to 80))
        assertEquals(0, state.remaining(ProviderKind.OPENROUTER, 7))
        assertTrue(state.exhausted(ProviderKind.OPENROUTER, 7))
    }

    @Test
    fun `local and paid providers have no daily ceiling`() {
        val state = QuotaState(day = 7, used = mapOf(ProviderKind.OLLAMA.name to 9_999))
        assertNull(state.remaining(ProviderKind.OLLAMA, 7))
        assertNull(state.remaining(ProviderKind.ANTHROPIC, 7))
        assertFalse(state.exhausted(ProviderKind.OLLAMA, 7))
    }

    // --- planner --------------------------------------------------------------

    @Test
    fun `several keys become several attempts with readable labels`() {
        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.GROQ)),
            keysFor = { listOf("k1", "k2", "k3") },
            quota = QuotaState(),
            today = 1,
            cooldowns = KeyCooldowns { 0L },
        )

        assertEquals(listOf("k1", "k2", "k3"), attempts.map { it.key })
        assertEquals(listOf("1/3", "2/3", "3/3"), attempts.map { it.keyLabel })
    }

    @Test
    fun `a provider with no budget left is skipped entirely`() {
        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.OPENROUTER), config(ProviderKind.GROQ)),
            keysFor = { listOf("k") },
            quota = QuotaState(day = 3, used = mapOf(ProviderKind.OPENROUTER.name to 50)),
            today = 3,
            cooldowns = KeyCooldowns { 0L },
        )

        assertEquals(listOf(ProviderKind.GROQ), attempts.map { it.config.provider })
    }

    @Test
    fun `a cooling key steps aside but its sibling still runs`() {
        var now = 0L
        val cooldowns = KeyCooldowns { now }
        cooldowns.cool(ProviderKind.GROQ, "k1", 429)

        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.GROQ)),
            keysFor = { listOf("k1", "k2") },
            quota = QuotaState(),
            today = 1,
            cooldowns = cooldowns,
        )

        assertEquals(listOf("k2"), attempts.map { it.key })
        assertEquals(listOf("2/2"), attempts.map { it.keyLabel })
    }

    @Test
    fun `every key cooling means no attempt, so the caller can explain`() {
        val cooldowns = KeyCooldowns { 0L }
        cooldowns.cool(ProviderKind.GEMINI, "k1", 401)

        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.GEMINI)),
            keysFor = { listOf("k1") },
            quota = QuotaState(),
            today = 1,
            cooldowns = cooldowns,
        )

        assertTrue(attempts.isEmpty())
    }

    @Test
    fun `a local server needs no key and still gets one attempt`() {
        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.OLLAMA, "qwen2.5-coder:7b")),
            keysFor = { emptyList() },
            quota = QuotaState(),
            today = 1,
            cooldowns = KeyCooldowns { 0L },
        )

        assertEquals(1, attempts.size)
        assertEquals("", attempts.single().key)
        assertEquals("", attempts.single().keyLabel)
    }

    @Test
    fun `candidate order is what the user's priority decided`() {
        val attempts = Planner.attempts(
            candidates = listOf(
                config(ProviderKind.GEMINI),
                config(ProviderKind.GROQ),
                config(ProviderKind.ANTHROPIC),
            ),
            keysFor = { listOf("k") },
            quota = QuotaState(),
            today = 1,
            cooldowns = KeyCooldowns { 0L },
        )

        assertEquals(
            listOf(ProviderKind.GEMINI, ProviderKind.GROQ, ProviderKind.ANTHROPIC),
            attempts.map { it.config.provider },
        )
    }

    @Test
    fun `an expensive agent run retries the same provider only twice`() {
        val attempts = Planner.attempts(
            candidates = listOf(config(ProviderKind.GROQ)),
            keysFor = { listOf("k1", "k2", "k3", "k4") },
            quota = QuotaState(),
            today = 1,
            cooldowns = KeyCooldowns { 0L },
            maxPerProvider = 2,
        )

        assertEquals(listOf("k1", "k2"), attempts.map { it.key })
    }
}

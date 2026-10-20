package dev.retrobit.agent.ai

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Nobody gives away frontier inference, so "unlimited" here has to mean three
 * things that are actually true:
 *
 *  1. a provider's daily quota is never the reason a conversation stops —
 *     Auto moves to the next provider that still has budget;
 *  2. one key is not the limit — every provider accepts several, and a key that
 *     just got rate-limited steps aside instead of failing the whole turn;
 *  3. a local model (Ollama) has no quota at all, because the tokens are
 *     computed on the user's own hardware.
 *
 * Everything in this file is pure so the policy can be tested without a device.
 */
object KeyRing {

    /** More than this in one field is almost certainly a paste accident. */
    const val MAX_KEYS = 12

    /**
     * One key per line. Blank lines and duplicates are dropped: pasting the same
     * key twice would otherwise look like two independent quotas.
     */
    fun parse(raw: String?): List<String> = raw
        .orEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .take(MAX_KEYS)
        .toList()
}

/** A concrete request: a provider config plus the key to send with it. */
data class Attempt(
    val config: AiConfig,
    val key: String,
    /** "2/3" when the provider holds several keys, blank when it holds one. */
    val keyLabel: String = "",
)

/**
 * Keys that recently failed, and for how long to leave them alone.
 *
 * In memory only. After a restart a bad key gets one more chance, which costs a
 * single failed request — cheaper than persisting timers for something one 429
 * corrects on its own.
 */
class KeyCooldowns(private val now: () -> Long = System::currentTimeMillis) {

    companion object {
        const val RATE_LIMITED_MS = 5 * 60_000L
        const val QUOTA_MS = 60 * 60_000L
        const val REJECTED_MS = 24 * 60 * 60_000L
    }

    private val until = HashMap<String, Long>()

    /** How long a given HTTP status should sideline a key. 0 means "not the key's fault". */
    fun durationFor(status: Int): Long = when (status) {
        429 -> RATE_LIMITED_MS
        402 -> QUOTA_MS
        401, 403 -> REJECTED_MS
        else -> 0L
    }

    fun cool(provider: ProviderKind, key: String, status: Int) {
        val duration = durationFor(status)
        if (duration <= 0) return
        until[slot(provider, key)] = now() + duration
    }

    fun cooling(provider: ProviderKind, key: String): Boolean {
        val slot = slot(provider, key)
        val end = until[slot] ?: return false
        if (end <= now()) {
            until.remove(slot)
            return false
        }
        return true
    }

    fun clear() = until.clear()

    /** Hash, so the secret itself is never held in a second place. */
    private fun slot(provider: ProviderKind, key: String) = "${provider.name}|${key.hashCode()}"
}

/**
 * Daily request budgets for the providers whose free tiers publish one.
 *
 * These are deliberately conservative copies, not promises: a provider may cut
 * them at any time. They exist to steer Auto away from a provider that is about
 * to answer 429, not to bill anyone.
 */
object QuotaPolicy {
    const val GEMINI_PER_KEY = 200
    const val GROQ_PER_KEY = 1_000
    const val OPENROUTER_PER_KEY = 50

    /** Requests per day per key, or null when there is nothing worth capping. */
    fun limitPerKey(kind: ProviderKind): Int? = when (kind) {
        ProviderKind.GEMINI -> GEMINI_PER_KEY
        ProviderKind.GROQ -> GROQ_PER_KEY
        ProviderKind.OPENROUTER -> OPENROUTER_PER_KEY
        // Local inference and paid providers have no daily ceiling to respect.
        else -> null
    }

    /** A day number both engines agree on: the user's local calendar day. */
    fun today(): Long = LocalDate.now().toEpochDay()
}

/** What has been spent today, per provider. Only free tiers are tracked. */
@Serializable
data class QuotaState(
    val day: Long = 0,
    val used: Map<String, Int> = emptyMap(),
) {

    fun usedToday(kind: ProviderKind, today: Long): Int =
        if (day == today) used[kind.name] ?: 0 else 0

    /** Requests left today across every key the user stored, or null if uncapped. */
    fun remaining(kind: ProviderKind, today: Long, keys: Int = 1): Int? {
        val limit = QuotaPolicy.limitPerKey(kind) ?: return null
        return (limit * keys.coerceAtLeast(1) - usedToday(kind, today)).coerceAtLeast(0)
    }

    fun exhausted(kind: ProviderKind, today: Long, keys: Int = 1): Boolean =
        remaining(kind, today, keys) == 0

    fun record(kind: ProviderKind, today: Long): QuotaState {
        val base = if (day == today) used else emptyMap()
        return QuotaState(today, base + (kind.name to (base[kind.name] ?: 0) + 1))
    }
}

/**
 * Turns the configured candidates into the ordered list of requests to try.
 *
 * Skips a provider whose free budget is gone, skips a key that is currently
 * cooling down, and expands a provider with several keys into several attempts
 * so one rate limit does not end the turn.
 */
object Planner {

    fun attempts(
        candidates: List<AiConfig>,
        keysFor: (ProviderKind) -> List<String>,
        quota: QuotaState,
        today: Long,
        cooldowns: KeyCooldowns,
        /** An agent run is expensive, so it only retries the same provider twice. */
        maxPerProvider: Int = KeyRing.MAX_KEYS,
    ): List<Attempt> {
        val out = mutableListOf<Attempt>()
        candidates.forEach { candidate ->
            val kind = candidate.provider
            val keys = keysFor(kind)
            if (quota.exhausted(kind, today, keys.size)) return@forEach
            // A provider that needs no key (Ollama, custom endpoint) still gets
            // one attempt with an empty secret.
            val pool = if (keys.isEmpty()) listOf("") else keys
            val usable = pool.filterNot { cooldowns.cooling(kind, it) }
            if (usable.isEmpty()) return@forEach
            usable.take(maxPerProvider).forEach { key ->
                val index = if (keys.size > 1) keys.indexOf(key) + 1 else 0
                out += Attempt(
                    config = candidate,
                    key = key,
                    keyLabel = if (keys.size > 1) "$index/${keys.size}" else "",
                )
            }
        }
        return out
    }
}

/** Process-wide so chat and the agent see the same cooldowns. */
val aiCooldowns = KeyCooldowns()

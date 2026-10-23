package dev.retrobit.agent.local

import dev.retrobit.agent.ai.WireTurn

/** Explicit rolling context for small models. Project files remain the source of truth. */
internal object OfflineAgentContext {
    fun recent(turns: List<WireTurn>): List<WireTurn> {
        if (turns.isEmpty()) return emptyList()
        // Keep the actual user request unchanged; retain the last two tool cycles.
        return listOf(turns.first()) + turns.drop(1).takeLast(4).map { turn -> when (turn) {
            is WireTurn.ToolResult -> turn.copy(content = turn.content.take(1200) + if (turn.content.length > 1200) "\n[output dipotong; read_file: start_char, max_chars=900]" else "")
            is WireTurn.Assistant -> if (turn.toolCalls.isEmpty()) turn.copy(text = turn.text.take(400)) else
                WireTurn.Assistant("Tool sebelumnya: ${turn.toolCalls.joinToString { it.name }}. Argumen lama dihilangkan untuk menghemat konteks; hasil tool menyusul.", emptyList())
            else -> turn
        } }
    }
}

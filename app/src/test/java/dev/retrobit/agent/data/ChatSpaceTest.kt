package dev.retrobit.agent.data

import dev.retrobit.agent.agent.PlainChatEngine
import dev.retrobit.agent.ai.WireTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSpaceTest {

    private fun message(role: Role, content: String, at: Long = 0) =
        ChatMessage(role = role, content = content, createdAt = at)

    // --- titles --------------------------------------------------------------

    @Test
    fun `title uses the first line and collapses whitespace`() {
        assertEquals("Halo", ChatSpace.titleFor("Halo\n\ndunia"))
        assertEquals("Halo dunia", ChatSpace.titleFor("  Halo    dunia  "))
        assertEquals("Judul", ChatSpace.titleFor("## Judul"))
    }

    @Test
    fun `blank input keeps the default title`() {
        assertEquals(ChatSpace.DEFAULT_TITLE, ChatSpace.titleFor(""))
        assertEquals(ChatSpace.DEFAULT_TITLE, ChatSpace.titleFor("   \n  "))
    }

    @Test
    fun `long titles are clipped with an ellipsis`() {
        val title = ChatSpace.titleFor("kata ".repeat(40))
        assertTrue(title.length <= 46)
        assertTrue(title.endsWith("…"))
    }

    // --- history window -------------------------------------------------------

    @Test
    fun `history drops system messages and blanks`() {
        val history = ChatSpace.history(
            listOf(
                message(Role.SYSTEM, "prompt"),
                message(Role.USER, "halo"),
                message(Role.ASSISTANT, "  "),
                message(Role.ASSISTANT, "hai"),
            )
        )
        assertEquals(listOf("halo", "hai"), history.map { it.content })
    }

    @Test
    fun `history keeps only the newest turns`() {
        val messages = (1..30).map { message(Role.USER, "pesan $it") }
        val history = ChatSpace.history(messages)
        assertEquals(ChatSpace.HISTORY_WINDOW, history.size)
        assertEquals("pesan 30", history.last().content)
        assertEquals("pesan 11", history.first().content)
    }

    @Test
    fun `preview shows the last line of the conversation`() {
        val conversation = ChatConversation(
            messages = listOf(
                message(Role.USER, "pertanyaan"),
                message(Role.ASSISTANT, "jawaban panjang\nbaris kedua"),
            )
        )
        assertEquals("jawaban panjang", ChatSpace.preview(conversation))
        assertEquals("", ChatSpace.preview(ChatConversation()))
    }

    // --- wire format ----------------------------------------------------------

    @Test
    fun `transcript maps roles onto the wire format`() {
        val turns = PlainChatEngine.transcript(
            listOf(
                message(Role.USER, "halo"),
                message(Role.ASSISTANT, "hai"),
                message(Role.SYSTEM, "abaikan"),
            )
        )
        assertEquals(2, turns.size)
        assertTrue(turns[0] is WireTurn.User)
        assertTrue(turns[1] is WireTurn.Assistant)
        assertEquals("hai", (turns[1] as WireTurn.Assistant).text)
        assertTrue((turns[1] as WireTurn.Assistant).toolCalls.isEmpty())
    }

    @Test
    fun `transcript truncates very long messages`() {
        val turns = PlainChatEngine.transcript(listOf(message(Role.USER, "x".repeat(20_000))))
        assertEquals(8_000, (turns.single() as WireTurn.User).text.length)
    }

    // --- persona --------------------------------------------------------------

    @Test
    fun `system prompt keeps the persona and adds the chat rule`() {
        val conversation = ChatConversation(systemPrompt = "Kamu adalah asisten santai.")
        val prompt = PlainChatEngine.systemPrompt(conversation, global = "abaikan")
        assertTrue(prompt.contains("Kamu adalah asisten santai."))
        assertTrue(prompt.contains("MODE OBROLAN"))
        assertFalse(prompt.contains("abaikan"))
    }

    @Test
    fun `system prompt falls back to the global persona`() {
        val prompt = PlainChatEngine.systemPrompt(ChatConversation(systemPrompt = ""), global = "Persona global")
        assertTrue(prompt.contains("Persona global"))
    }

    @Test
    fun `system prompt never ends up empty`() {
        val prompt = PlainChatEngine.systemPrompt(ChatConversation(systemPrompt = ""), global = "   ")
        assertTrue(prompt.contains(SettingsStore.DEFAULT_SYSTEM_PROMPT.take(40)))
    }
}

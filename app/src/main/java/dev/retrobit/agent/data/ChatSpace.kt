package dev.retrobit.agent.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * A conversation in the plain chat mode: no project, no tools, no files.
 * Messages reuse [ChatMessage] so the same bubbles and markdown renderer apply.
 */
@Serializable
data class ChatConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = ChatSpace.DEFAULT_TITLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<ChatMessage> = emptyList(),
    /** Persona snapshot taken when the conversation was created. */
    val systemPrompt: String = "",
    val modelLabel: String = "",
)

/** Pure helpers shared by the store, the engine and the unit tests. */
object ChatSpace {

    const val DEFAULT_TITLE = "Obrolan baru"

    /** Number of past messages sent back to the model, newest last. */
    const val HISTORY_WINDOW = 20

    /** Stored conversations are capped so the JSON file cannot grow forever. */
    const val MAX_CONVERSATIONS = 100

    /** Messages kept per conversation on disk. */
    const val MAX_MESSAGES = 400

    /**
     * A readable title from the first thing the user typed: single line, no
     * markdown noise, short enough for the list row on a phone.
     */
    fun titleFor(text: String): String {
        val clean = text
            .lineSequence()
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
            .trim()
            .trimStart('#', '-', '*', '>', ' ')
            .replace(Regex("\\s+"), " ")
        if (clean.isBlank()) return DEFAULT_TITLE
        return if (clean.length <= 46) clean else clean.take(45).trimEnd() + "…"
    }

    /** Last user/assistant turns, oldest first — what goes on the wire. */
    fun history(messages: List<ChatMessage>): List<ChatMessage> =
        messages
            .filter { it.role == Role.USER || it.role == Role.ASSISTANT }
            // Error bubbles are notices to the user, not something the model
            // said. Replaying "API key ditolak oleh OpenAI" as an assistant turn
            // taught the model to answer with apologies.
            .filter { !it.isError }
            .filter { it.content.isNotBlank() }
            .takeLast(HISTORY_WINDOW)

    /** Short preview for the conversation list. */
    fun preview(conversation: ChatConversation): String {
        val last = conversation.messages.lastOrNull { it.content.isNotBlank() } ?: return ""
        return last.content.lineSequence().firstOrNull().orEmpty().take(72)
    }
}

/**
 * Persists plain-chat conversations to filesDir/chat_space.json. Kept separate
 * from ProjectRepository so chat mode never touches projects.json or a project
 * directory — deleting a chat can never damage an app the user built.
 */
class ChatSpaceStore(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }
    private val lock = Mutex()
    private val file = File(context.filesDir, "chat_space.json")

    private val _conversations = MutableStateFlow<List<ChatConversation>>(emptyList())
    val conversations: StateFlow<List<ChatConversation>> = _conversations.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        lock.withLock {
            val loaded = runCatching {
                if (file.exists()) json.decodeFromString<List<ChatConversation>>(file.readText()) else emptyList()
            }.getOrElse { emptyList() }
            _conversations.value = loaded.sortedByDescending { it.updatedAt }
        }
    }

    suspend fun create(systemPrompt: String): ChatConversation = withContext(Dispatchers.IO) {
        val conversation = ChatConversation(systemPrompt = systemPrompt)
        lock.withLock {
            val next = (listOf(conversation) + _conversations.value).take(ChatSpace.MAX_CONVERSATIONS)
            _conversations.value = next
            persist(next)
        }
        conversation
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val next = _conversations.value.filterNot { it.id == id }
            _conversations.value = next
            persist(next)
        }
    }

    suspend fun rename(id: String, title: String) = withContext(Dispatchers.IO) {
        lock.withLock {
            val next = mutate(id) { it.copy(title = title.ifBlank { ChatSpace.DEFAULT_TITLE }) }
            _conversations.value = next
            persist(next)
        }
    }

    /**
     * Appends messages. A conversation still carrying the default title adopts
     * the first user message as its name, which keeps the list readable without
     * asking the user to name chats.
     */
    suspend fun append(id: String, messages: List<ChatMessage>) = withContext(Dispatchers.IO) {
        if (messages.isEmpty()) return@withContext
        lock.withLock {
            val next = mutate(id) { conversation ->
                val title = if (
                    conversation.title == ChatSpace.DEFAULT_TITLE &&
                    messages.first().role == Role.USER
                ) {
                    ChatSpace.titleFor(messages.first().content)
                } else {
                    conversation.title
                }
                conversation.copy(
                    title = title,
                    messages = (conversation.messages + messages).takeLast(ChatSpace.MAX_MESSAGES),
                    updatedAt = System.currentTimeMillis(),
                    modelLabel = messages.last().modelLabel.ifBlank { conversation.modelLabel },
                )
            }
            _conversations.value = next.sortedByDescending { it.updatedAt }
            persist(_conversations.value)
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        lock.withLock {
            _conversations.value = emptyList()
            persist(emptyList())
        }
    }

    private fun mutate(id: String, transform: (ChatConversation) -> ChatConversation): List<ChatConversation> =
        _conversations.value.map { if (it.id == id) transform(it) else it }

    private fun persist(conversations: List<ChatConversation>) {
        runCatching {
            AtomicFiles.writeText(file, json.encodeToString(conversations))
        }
    }
}

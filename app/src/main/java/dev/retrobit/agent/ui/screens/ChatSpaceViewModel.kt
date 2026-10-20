package dev.retrobit.agent.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.agent.AppContainer
import dev.retrobit.agent.agent.PlainChatEngine
import dev.retrobit.agent.ai.AutoRouter
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.data.ChatConversation
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.Role
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class ChatSpaceViewModel(private val container: AppContainer) : ViewModel() {

    private val store = container.chatSpace
    private val engine = container.plainChat
    val settings = container.settings
    val secrets = container.secrets

    val conversations = store.conversations
    val config = settings.config

    var activeId by mutableStateOf<String?>(null)
        private set
    var draft by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var notice by mutableStateOf("")
        private set

    /** Text of the answer currently arriving, empty when nothing is streaming. */
    var streamingText by mutableStateOf("")
        private set

    private var job: Job? = null

    init {
        viewModelScope.launch { store.load() }
    }

    val active: ChatConversation?
        get() = activeId?.let { id -> conversations.value.firstOrNull { it.id == id } }

    /** True when Auto is selected and at least one provider could answer. */
    val canChat: Boolean
        get() {
            val current = config.value
            return when (current.provider) {
                ProviderKind.DEMO -> false
                ProviderKind.AUTO -> AutoRouter.candidates(secrets, current).isNotEmpty()
                else -> true
            }
        }

    val modelSummary: String
        get() {
            val current = config.value
            return when (current.provider) {
                ProviderKind.DEMO -> "Demo lokal"
                ProviderKind.AUTO -> "Auto"
                else -> current.displayModel
            }
        }

    fun newChat() {
        viewModelScope.launch {
            val conversation = store.create(settings.systemPrompt)
            activeId = conversation.id
            draft = ""
            notice = ""
        }
    }

    fun open(id: String) {
        activeId = id
        notice = ""
    }

    fun backToList() {
        stop()
        activeId = null
    }

    fun delete(id: String) {
        viewModelScope.launch {
            if (activeId == id) activeId = null
            store.delete(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            activeId = null
            store.clearAll()
        }
    }

    fun send() {
        val text = draft.trim()
        val conversation = active
        if (text.isEmpty() || sending || conversation == null) return
        draft = ""
        sending = true
        notice = ""
        streamingText = ""
        job = viewModelScope.launch {
            // Deltas arrive on an IO thread; a channel hands them to this
            // Main-dispatched coroutine so Compose state stays single-threaded.
            val chunks = Channel<String>(Channel.UNLIMITED)
            val painter = launch { paintStream(chunks) }
            try {
                val user = ChatMessage(role = Role.USER, content = text)
                store.append(conversation.id, listOf(user))
                val current = store.conversations.value.firstOrNull { it.id == conversation.id }
                if (current == null) return@launch
                val reply = engine.replyStream(
                    conversation = current,
                    persona = settings.systemPrompt,
                    onNotice = { line -> notice = line },
                    onDelta = { piece -> chunks.send(piece) },
                )
                store.append(conversation.id, listOf(reply))
            } finally {
                chunks.close()
                painter.join()
                streamingText = ""
                sending = false
            }
        }
    }

    /**
     * Coalesces deltas into ~60 ms repaints. Updating Compose state on every
     * token would re-parse the whole markdown document dozens of times a second.
     */
    private suspend fun paintStream(chunks: Channel<String>) {
        val buffer = StringBuilder()
        var lastPaint = 0L
        for (chunk in chunks) {
            buffer.append(chunk)
            val now = System.currentTimeMillis()
            if (now - lastPaint >= 60) {
                streamingText = buffer.toString()
                lastPaint = now
            }
        }
        if (buffer.isNotEmpty()) streamingText = buffer.toString()
    }

    fun stop() {
        job?.cancel()
        job = null
        sending = false
    }

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}

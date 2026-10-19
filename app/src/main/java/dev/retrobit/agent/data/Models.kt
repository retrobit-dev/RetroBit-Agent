package dev.retrobit.agent.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DesignSystem(
    val preset: String = "midnight",
    val primary: String = "#7C5CFF",
    val accent: String = "#39D0FF",
    val background: String = "#0B0B12",
    val surface: String = "#15151F",
    val textColor: String = "#F5F5FA",
    val muted: String = "#8C8CA1",
    val radius: Int = 20,
    val font: String = "Inter",
    val mode: String = "dark",
) {
    fun cssVariables(): String = """
        :root{
          --rb-primary:$primary;
          --rb-accent:$accent;
          --rb-bg:$background;
          --rb-surface:$surface;
          --rb-text:$textColor;
          --rb-muted:$muted;
          --rb-radius:${radius}px;
          --rb-font:'$font','Inter',system-ui,-apple-system,sans-serif;
        }
    """.trimIndent()

    companion object {
        val PRESETS: List<DesignPreset> = listOf(
            DesignPreset("midnight", "Midnight", "Deep night dengan aksen ungu neon",
                DesignSystem("midnight", "#7C5CFF", "#39D0FF", "#0B0B12", "#15151F", "#F5F5FA", "#8C8CA1", 20, "Inter", "dark")),
            DesignPreset("aurora", "Aurora", "Gradasi hijau-teal yang segar",
                DesignSystem("aurora", "#22D3A5", "#67E8F9", "#06120F", "#0C1F1B", "#ECFDF5", "#7FB3A8", 24, "Inter", "dark")),
            DesignPreset("sunset", "Sunset", "Hangat oranye dan magis",
                DesignSystem("sunset", "#FF7A45", "#FFC53D", "#150D0A", "#22140F", "#FFF3EA", "#C29A87", 18, "Inter", "dark")),
            DesignPreset("candy", "Candy", "Bersih, terang, dan ramah",
                DesignSystem("candy", "#6366F1", "#F472B6", "#F7F7FB", "#FFFFFF", "#14141F", "#6B6B80", 20, "Inter", "light")),
            DesignPreset("mono", "Mono", "Editorial minimalis hitam-putih",
                DesignSystem("mono", "#111111", "#555555", "#FAFAF8", "#FFFFFF", "#101010", "#707070", 12, "Inter", "light")),
            DesignPreset("terminal", "Terminal", "Hacker green, tipografi mono",
                DesignSystem("terminal", "#4AF626", "#9BFF8A", "#050805", "#0B120B", "#D9FFD2", "#6F9A69", 8, "JetBrains Mono", "dark")),
        )
    }
}

@Serializable
data class DesignPreset(
    val id: String,
    val name: String,
    val tagline: String,
    val design: DesignSystem,
)

@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val tagline: String = "",
    val emoji: String = "🚀",
    val accent: Long = 0xFF7C5CFF,
    val templateId: String = "saas",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val design: DesignSystem = DesignSystem(),
    val model: String = "",
    val favorite: Boolean = false,
    val lastPrompt: String = "",
)

@Serializable
enum class Role { USER, ASSISTANT, SYSTEM }

@Serializable
enum class StepStatus { RUNNING, DONE, FAILED, SKIPPED }

@Serializable
data class AgentStep(
    val id: String = UUID.randomUUID().toString(),
    val tool: String,
    val title: String,
    val detail: String = "",
    val status: StepStatus = StepStatus.RUNNING,
    val durationMs: Long = 0,
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val steps: List<AgentStep> = emptyList(),
    val filesChanged: List<String> = emptyList(),
    val isError: Boolean = false,
    val modelLabel: String = "",
    val tokens: Int = 0,
)

@Serializable
data class ChatThread(
    val messages: List<ChatMessage> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class FileNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0,
    val depth: Int = 0,
)

@Serializable
data class TableInfo(
    val name: String,
    val rows: Long,
    val columns: Int,
)

@Serializable
data class ColumnInfo(
    val name: String,
    val type: String,
    val notNull: Boolean,
    val primaryKey: Boolean,
    val defaultValue: String? = null,
)

@Serializable
data class QueryResult(
    val columns: List<String> = emptyList(),
    val rows: List<List<String?>> = emptyList(),
    val rowsAffected: Int = 0,
    val message: String = "",
    val error: String? = null,
    val elapsedMs: Long = 0,
    val isSelect: Boolean = false,
)

@Serializable
data class SecretEntry(
    val key: String,
    val value: String,
    val note: String = "",
    val exposeToPreview: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class SecretRequest(
    val key: String,
    val description: String,
    val fulfilled: Boolean = false,
)

@Serializable
data class UsageRecord(
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val requests: Long = 0,
    val costUsd: Double = 0.0,
)

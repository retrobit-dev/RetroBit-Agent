package dev.retrobit.agent.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DesignSystem(
    val preset: String = "midnight",
    val primary: String = "#DEDED5",
    val accent: String = "#F4F4EC",
    val background: String = "#0A0A0A",
    val surface: String = "#141413",
    val textColor: String = "#EAEAE2",
    val muted: String = "#8E8E86",
    val radius: Int = 4,
    val font: String = "Pixel Operator",
    val mode: String = "dark",
) {
    /**
     * Design tokens as CSS. The bundled Pixel Operator webfont is declared here so
     * generated pages, the WebView preview and exported ZIPs all share the same type.
     */
    fun cssVariables(): String = """
        @font-face{
          font-family:'Pixel Operator';
          src:url('assets/fonts/PixelOperator.ttf') format('truetype');
          font-display:swap;
        }
        :root{
          --rb-primary:$primary;
          --rb-accent:$accent;
          --rb-bg:$background;
          --rb-surface:$surface;
          --rb-text:$textColor;
          --rb-muted:$muted;
          --rb-radius:${radius}px;
          --rb-font:'$font','Pixel Operator',ui-monospace,'Courier New',monospace;
        }
    """.trimIndent()

    /** True when every token is (nearly) desaturated — the e-paper rule. */
    fun isGrayscale(): Boolean = listOf(primary, accent, background, surface, textColor, muted).all { hex ->
        val rgb = hex.trimStart('#').toLongOrNull(16) ?: return@all true
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        (maxOf(r, g, b) - minOf(r, g, b)) <= 18
    }

    /** Snaps a legacy coloured design onto the nearest monochrome preset. */
    fun grayscaleFallback(): DesignSystem {
        val target = PRESETS.firstOrNull { it.design.mode == mode } ?: PRESETS.first()
        return copy(
            preset = target.design.preset,
            primary = target.design.primary,
            accent = target.design.accent,
            background = target.design.background,
            surface = target.design.surface,
            textColor = target.design.textColor,
            muted = target.design.muted,
            font = "Pixel Operator",
        )
    }

    companion object {
        val PRESETS: List<DesignPreset> = listOf(
            DesignPreset("midnight", "Midnight", "E-paper malam, kontras tinggi",
                DesignSystem("midnight", "#DEDED5", "#F4F4EC", "#0A0A0A", "#141413", "#EAEAE2", "#8E8E86", 4, "Pixel Operator", "dark")),
            DesignPreset("aurora", "Aurora", "Abu dingin seperti layar e-ink",
                DesignSystem("aurora", "#CBD3CC", "#EEF3EC", "#0B0D0C", "#151817", "#E4E9E3", "#7F867F", 3, "Pixel Operator", "dark")),
            DesignPreset("sunset", "Sunset", "Kertas tua yang hangat",
                DesignSystem("sunset", "#E2D8C7", "#F6EEDF", "#0D0B08", "#191510", "#EFE7DA", "#8D8678", 5, "Pixel Operator", "dark")),
            DesignPreset("candy", "Candy", "Terang, lembut, tanpa warna",
                DesignSystem("candy", "#2B2B25", "#141410", "#EFEFE8", "#FBFAF4", "#191914", "#6B6B62", 6, "Pixel Operator", "light")),
            DesignPreset("mono", "Mono", "Editorial hitam-putih murni",
                DesignSystem("mono", "#121210", "#3D3D38", "#F4F4EE", "#FFFFFF", "#0E0E0C", "#6A6A62", 2, "Pixel Operator", "light")),
            DesignPreset("terminal", "Terminal", "Fosfor monokrom, tipografi piksel",
                DesignSystem("terminal", "#E9E9E0", "#FFFFFF", "#050505", "#0D0D0C", "#DCDCD3", "#7C7C74", 0, "Pixel Operator", "dark")),
            DesignPreset("paper", "Paper", "E-paper siang hari",
                DesignSystem("paper", "#1A1A16", "#4A4A42", "#ECECE5", "#F9F9F3", "#15150F", "#5F5F57", 2, "Pixel Operator", "light")),
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
    val accent: Long = 0xFFDEDED5,
    val templateId: String = "saas",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val design: DesignSystem = DesignSystem(),
    val model: String = "",
    val favorite: Boolean = false,
    val lastPrompt: String = "",
    /**
     * Snapshot of the system prompt for this chat.
     * "" = legacy project (filled from the global default on load),
     * null = explicitly follows the global default, any text = pinned copy.
     */
    val systemPrompt: String? = "",
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

package dev.retrobit.agent.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val items: List<String>) : MdBlock
    data class Numbered(val items: List<String>) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data object Divider : MdBlock
    data object Space : MdBlock
}

private fun parseMarkdown(raw: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = raw.replace("\r\n", "\n").split('\n')
    var i = 0
    var paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) {
            blocks.add(MdBlock.Paragraph(paragraph.toString().trim()))
            paragraph = StringBuilder()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flushParagraph()
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    code.appendLine(lines[i])
                    i++
                }
                blocks.add(MdBlock.Code(language, code.toString().trimEnd()))
            }
            trimmed == "---" || trimmed == "***" -> {
                flushParagraph()
                blocks.add(MdBlock.Divider)
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 4)
                blocks.add(MdBlock.Heading(level, trimmed.dropWhile { it == '#' }.trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("- ") || l.startsWith("* ") || l.startsWith("• ")) {
                        items.add(l.substring(2).trim())
                        i++
                    } else break
                }
                blocks.add(MdBlock.Bullet(items))
                continue
            }
            Regex("^\\d+\\.\\s").containsMatchIn(trimmed) -> {
                flushParagraph()
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (Regex("^\\d+\\.\\s").containsMatchIn(l)) {
                        items.add(l.substringAfter('.').trim())
                        i++
                    } else break
                }
                blocks.add(MdBlock.Numbered(items))
                continue
            }
            trimmed.startsWith("> ") -> {
                flushParagraph()
                blocks.add(MdBlock.Quote(trimmed.removePrefix("> ").trim()))
            }
            trimmed.isEmpty() -> {
                flushParagraph()
                if (blocks.lastOrNull() !is MdBlock.Space) blocks.add(MdBlock.Space)
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private val inlinePattern = Regex(
    """(\*\*[^*\n]+\*\*)|(`[^`\n]+`)|(\*[^*\n]+\*)|(\[[^\]\n]+\]\([^)\n]+\))"""
)

@Composable
fun inlineMarkdown(text: String): AnnotatedString {
    val colors = MaterialTheme.colorScheme
    return remember(text, colors) {
        buildAnnotatedString {
            var last = 0
            inlinePattern.findAll(text).forEach { match ->
                if (match.range.first > last) append(text.substring(last, match.range.first))
                val token = match.value
                when {
                    token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.removeSurrounding("**"))
                    }
                    token.startsWith("`") -> withStyle(
                        SpanStyle(
                            fontFamily = MonoFont,
                            color = Palette.Accent,
                            background = Palette.Accent.copy(alpha = 0.10f),
                            fontSize = 12.5.sp,
                        )
                    ) { append(token.trim('`')) }
                    token.startsWith("*") -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(token.trim('*'))
                    }
                    token.startsWith("[") -> {
                        val label = token.substringAfter('[').substringBefore(']')
                        withStyle(SpanStyle(color = Palette.Primary, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                            append(label)
                        }
                    }
                    else -> append(token)
                }
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val blocks = remember(text) { parseMarkdown(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        inlineMarkdown(block.text),
                        style = style,
                        color = color,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                    )
                }
                is MdBlock.Paragraph -> Text(inlineMarkdown(block.text), style = baseStyle, color = color)
                is MdBlock.Bullet -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEach { item ->
                        Row {
                            Box(
                                Modifier
                                    .padding(top = 8.dp, end = 8.dp)
                                    .width(4.dp)
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Palette.Primary)
                            )
                            Text(inlineMarkdown(item), style = baseStyle, color = color, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Numbered -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { index, item ->
                        Row {
                            Text(
                                "${index + 1}.",
                                style = baseStyle,
                                color = Palette.Accent,
                                fontFamily = MonoFont,
                                modifier = Modifier.width(22.dp),
                            )
                            Text(inlineMarkdown(item), style = baseStyle, color = color, modifier = Modifier.weight(1f))
                        }
                    }
                }
                is MdBlock.Quote -> Row {
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Palette.Primary.copy(alpha = 0.5f))
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(inlineMarkdown(block.text), style = baseStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = FontStyle.Italic)
                }
                is MdBlock.Code -> CodeBlock(block.code, block.language)
                MdBlock.Divider -> HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MdBlock.Space -> Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String = "") {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A10))
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(12.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.03f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                language.ifBlank { "code" }.lowercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted,
                fontFamily = MonoFont,
            )
            Text(
                "${code.lines().size} baris",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Muted.copy(alpha = 0.7f),
            )
        }
        Box(Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)) {
            Text(
                highlightCode(code, language),
                fontFamily = MonoFont,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = Palette.Text,
            )
        }
    }
}

private val codeKeywordRegex = Regex(
    """(//[^\n]*|/\*[\s\S]*?\*/|<!--[\s\S]*?-->|#[^\n]*)""" +
        """|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`)""" +
        """|\b(fun|val|var|if|else|for|while|return|const|let|function|class|new|import|from|export|async|await|try|catch|throw|true|false|null|undefined|this|def|elif|INSERT|SELECT|FROM|WHERE|CREATE|TABLE|UPDATE|DELETE|JOIN|GROUP|ORDER|BY|LIMIT|NOT|PRIMARY|KEY|TEXT|INTEGER)\b""" +
        """|(\b\d+(?:\.\d+)?\b)"""
)

fun highlightCode(code: String, language: String): AnnotatedString = buildAnnotatedString {
    val commentColor = Palette.Muted.copy(alpha = 0.75f)
    val stringColor = Color(0xFF9BE7A8)
    val keywordColor = Palette.Pink
    val numberColor = Color(0xFFFFC978)
    var last = 0
    codeKeywordRegex.findAll(code).forEach { match ->
        if (match.range.first > last) append(code.substring(last, match.range.first))
        val value = match.value
        val style = when {
            match.groups[1] != null -> SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)
            match.groups[2] != null -> SpanStyle(color = stringColor)
            match.groups[3] != null -> SpanStyle(color = keywordColor, fontWeight = FontWeight.Medium)
            match.groups[4] != null -> SpanStyle(color = numberColor)
            else -> SpanStyle()
        }
        withStyle(style) { append(value) }
        last = match.range.last + 1
    }
    if (last < code.length) append(code.substring(last))
}

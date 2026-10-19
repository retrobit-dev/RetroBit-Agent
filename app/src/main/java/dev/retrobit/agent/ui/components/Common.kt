package dev.retrobit.agent.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.PixelFont
import dev.retrobit.agent.ui.theme.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun AuroraBackground(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val line = colors.onBackground.copy(alpha = if (colors.background.luminance() > 0.5f) 0.05f else 0.07f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .drawBehind {
                // Faint engineering grid — the cyberdeck "e-paper" texture.
                val step = 28.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, colors.background.copy(alpha = 0.55f), colors.background),
                    )
                )
            }
    ) { content() }
}

@Composable
fun BrandMark(size: Dp = 34.dp, glyph: String = "R") {
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary)
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = onPrimary,
            fontFamily = PixelFont,
            fontSize = (size.value * 0.52f).sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    val labelColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = if (enabled) 0.4f else 0.15f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = labelColor, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text,
                color = labelColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun NeoCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    background: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = background,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}

@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Palette.Muted,
    background: Color = Palette.Muted.copy(alpha = 0.12f),
    dot: Boolean = false,
    monospace: Boolean = false,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
            .background(background)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) {
            Box(Modifier.size(5.dp).background(color))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = if (monospace) MonoFont else PixelFont,
            letterSpacing = 0.6.sp,
            maxLines = 1,
        )
    }
}

@Composable
fun SectionLabel(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.4.sp,
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    emoji: String = "",
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
            }
        } else {
            Text(emoji, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun LoadingDots(color: Color = Palette.Accent) {
    val transition = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
        }
    }
}

/** Parses #RRGGBB / #AARRGGBB into a Compose Color without ever throwing. */
fun hexColor(hex: String, fallback: Color = Palette.Accent): Color = runCatching {
    Color(android.graphics.Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
}.getOrDefault(fallback)

data class ToolVisual(val icon: ImageVector, val tint: Color)

fun toolVisual(tool: String): ToolVisual = when (tool) {
    "list_files" -> ToolVisual(PixelIcons.Folder, Palette.Accent)
    "read_file" -> ToolVisual(PixelIcons.FileText, Palette.Muted)
    "write_file" -> ToolVisual(PixelIcons.Pencil, Palette.Success)
    "edit_file" -> ToolVisual(PixelIcons.Pencil, Palette.Success)
    "delete_file" -> ToolVisual(PixelIcons.Trash, Palette.Danger)
    "rename_file" -> ToolVisual(PixelIcons.Move, Palette.Warning)
    "update_design" -> ToolVisual(PixelIcons.Palette, Palette.Pink)
    "create_table" -> ToolVisual(PixelIcons.Database, Palette.Primary)
    "run_sql" -> ToolVisual(PixelIcons.Terminal, Palette.Accent)
    "inspect_database" -> ToolVisual(PixelIcons.Database, Palette.Accent)
    "request_secret" -> ToolVisual(PixelIcons.Key, Palette.Warning)
    "plan" -> ToolVisual(PixelIcons.Sparkles, Palette.Primary)
    "verify" -> ToolVisual(PixelIcons.CheckDouble, Palette.Success)
    else -> ToolVisual(PixelIcons.Sparkles, Palette.Muted)
}

fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "baru saja"
        diff < 3_600_000 -> "${diff / 60_000} mnt lalu"
        diff < 86_400_000 -> "${diff / 3_600_000} jam lalu"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} hari lalu"
        else -> SimpleDateFormat("d MMM", Locale("id")).format(Date(timestamp))
    }
}

fun fileName(path: String): String = path.substringAfterLast('/')

fun fileExtension(path: String): String = path.substringAfterLast('.', "").lowercase()
/**
 * Full-screen prompt editor shared by the global setting (AI Cloud) and the
 * per-chat override (workspace menu). Monospace body, explicit save/reset.
 */
@Composable
fun SystemPromptDialog(
    title: String,
    description: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PixelIcons.Terminal, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = MonoFont,
                        lineHeight = 17.sp,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${value.length} karakter · dikirim sebagai bagian awal system prompt",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onReset != null && resetLabel != null) {
                    TextButton(onClick = onReset) {
                        Text(resetLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Batal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GradientButton("Simpan", onClick = onSave)
            }
        }
    }
}

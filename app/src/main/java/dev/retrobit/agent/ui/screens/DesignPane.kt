package dev.retrobit.agent.ui.screens

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.theme.PixelFont
import dev.retrobit.agent.ui.icons.PixelIcons

private data class TokenTarget(val label: String, val value: String, val apply: (String) -> Unit)

@Composable
fun DesignPane(vm: WorkspaceViewModel, modifier: Modifier = Modifier) {
    val design by vm.design.collectAsStateWithLifecycle()
    val project by vm.project.collectAsStateWithLifecycle()
    var colorTarget by remember { mutableStateOf<TokenTarget?>(null) }

    val current = project
    val dirty = current != null && current.design != design

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Design system", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(7.dp))
                    if (dirty) Pill("belum disimpan", color = Palette.Warning, background = Palette.Warning.copy(alpha = 0.12f))
                    else Pill("tersinkron", color = Palette.Success, background = Palette.Success.copy(alpha = 0.1f), dot = true)
                }
                Text(
                    "Token CSS yang dipakai seluruh halaman",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { vm.resetDesign() }) {
                Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GradientButton("Simpan", onClick = { vm.saveDesign() })
        }

        DesignPreview(design)
        Spacer(Modifier.height(18.dp))

        Text("Preset", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DesignSystem.PRESETS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { preset ->
                        val selected = preset.design.primary == design.primary && preset.design.background == design.background
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (selected) Palette.Primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .border(
                                    1.dp,
                                    if (selected) Palette.Primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable { vm.applyPreset(preset) }
                                .padding(10.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(preset.design.primary, preset.design.accent, preset.design.surface).forEach { hex ->
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(parseColor(hex, Palette.Primary))
                                    )
                                }
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                preset.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Warna", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
        ) {
            val tokens = listOf(
                TokenTarget("Primary", design.primary) { vm.updateDesign { d -> d.copy(primary = it) } },
                TokenTarget("Accent", design.accent) { vm.updateDesign { d -> d.copy(accent = it) } },
                TokenTarget("Background", design.background) { vm.updateDesign { d -> d.copy(background = it) } },
                TokenTarget("Surface", design.surface) { vm.updateDesign { d -> d.copy(surface = it) } },
                TokenTarget("Text", design.textColor) { vm.updateDesign { d -> d.copy(textColor = it) } },
                TokenTarget("Muted", design.muted) { vm.updateDesign { d -> d.copy(muted = it) } },
            )
            tokens.forEachIndexed { index, token ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { colorTarget = token }
                        .padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(parseColor(token.value, Palette.Primary))
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f), CircleShape)
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(token.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text(
                        token.value.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = MonoFont,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != tokens.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp)
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Bentuk & tipografi", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Radius sudut", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("${design.radius}px", style = MaterialTheme.typography.labelSmall, color = Palette.Accent, fontFamily = MonoFont)
            }
            Slider(
                value = design.radius.toFloat(),
                onValueChange = { vm.updateDesign { d -> d.copy(radius = it.toInt()) } },
                valueRange = 0f..12f,
                colors = SliderDefaults.colors(
                    thumbColor = Palette.Accent,
                    activeTrackColor = Palette.Primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text("Font", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Pixel Operator", "Pixel Operator Wide", "Mono", "Serif").forEach { font ->
                    val active = design.font == font
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) Palette.Primary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .border(1.dp, if (active) Palette.Primary.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(3.dp))
                            .clickable { vm.updateDesign { d -> d.copy(font = font) } }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text(
                            font.removePrefix("Pixel Operator").trim().ifBlank { "Pixel" },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = webFontFamily(font),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Token disimpan sebagai variabel CSS --rb-* dan ditulis ulang ke semua file HTML saat kamu menekan Simpan. Preview langsung mengikuti.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(28.dp))
    }

    colorTarget?.let { target ->
        ColorDialog(
            title = target.label,
            current = target.value,
            onPick = {
                target.apply(it)
                colorTarget = null
            },
            onDismiss = { colorTarget = null },
        )
    }
}

@Composable
private fun DesignPreview(design: DesignSystem) {
    val bg = parseColor(design.background, Palette.Bg)
    val surface = parseColor(design.surface, Palette.Surface)
    val primary = parseColor(design.primary, Palette.Primary)
    val accent = parseColor(design.accent, Palette.Accent)
    val text = parseColor(design.textColor, Color.White)
    val muted = parseColor(design.muted, Palette.Muted)
    val radius = design.radius.dp
    val font = webFontFamily(design.font)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(5.dp))
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(5.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), accent.copy(alpha = 0.10f))))
                )
                Spacer(Modifier.width(7.dp))
                Text("Aplikasi kamu", color = text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = font)
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(22.dp).height(4.dp).clip(CircleShape).background(muted.copy(alpha = 0.35f)))
                    Box(Modifier.width(14.dp).height(4.dp).clip(CircleShape).background(muted.copy(alpha = 0.35f)))
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "Desain yang terasa\nmahal, tanpa ribet.",
                color = text,
                fontSize = 22.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = font,
                letterSpacing = (-0.6).sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "Warna, radius, dan tipografi otomatis konsisten di setiap layar.",
                color = muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = font,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(radius * 0.55f))
                        .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.22f), accent.copy(alpha = 0.10f))))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Mulai", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = font)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(radius * 0.55f))
                        .background(surface)
                        .border(1.dp, muted.copy(alpha = 0.25f), RoundedCornerShape(radius * 0.55f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("Pelajari", color = text, fontSize = 11.sp, fontFamily = font)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { index ->
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(radius * 0.5f))
                            .background(surface)
                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(radius * 0.5f))
                            .padding(9.dp)
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(listOf(primary, accent, muted).getOrElse(index) { primary }.copy(alpha = 0.35f))
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(Modifier.fillMaxWidth(0.8f).height(4.dp).clip(CircleShape).background(text.copy(alpha = 0.5f)))
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.fillMaxWidth(0.55f).height(3.dp).clip(CircleShape).background(muted.copy(alpha = 0.4f)))
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorDialog(
    title: String,
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Strict monochrome ramp: e-paper ink, paper and the greys between.
    val swatches = listOf(
        "#050505", "#0A0A0A", "#121212", "#1B1B1A", "#262625", "#31312F",
        "#3F3F3C", "#4E4E4A", "#5F5F5A", "#70706A", "#82827B", "#94948C",
        "#A6A69E", "#B8B8AF", "#C9C9C0", "#DADAD1", "#EAEAE2", "#F4F4EC",
        "#FFFFFF", "#EDEDE7", "#DCDCD4", "#C4C4BB", "#9C9C93", "#6A6A63",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Warna $title") },
        text = {
            Column {
                Text("Pilih dari palet, atau ketik kode hex.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                swatches.chunked(5).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { hex ->
                            val color = parseColor(hex, Palette.Primary)
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (hex.equals(current, true)) 2.dp else 1.dp,
                                        if (hex.equals(current, true)) Palette.Accent else Color.White.copy(alpha = 0.16f),
                                        CircleShape
                                    )
                                    .clickable { onPick(hex) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (hex.equals(current, true)) {
                                    Icon(PixelIcons.Check, null, tint = if (color.brightnessOf() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(18.dp).clip(CircleShape).background(parseColor(current, Palette.Primary)))
                    Spacer(Modifier.width(8.dp))
                    Text(current.uppercase(), fontFamily = MonoFont, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    Text("Hex", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup", color = Palette.Accent) } },
    )
}

private fun parseColor(hex: String, fallback: Color): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(fallback)

private fun Color.brightnessOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

private fun webFontFamily(name: String): FontFamily = when {
    name.contains("Mono", true) -> FontFamily.Monospace
    name.contains("Serif", true) -> FontFamily.Serif
    else -> PixelFont
}
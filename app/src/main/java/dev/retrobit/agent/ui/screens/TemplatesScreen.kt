package dev.retrobit.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.data.TemplateInfo
import dev.retrobit.agent.data.Templates
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun TemplatesScreen(
    vm: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var busy by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 16.dp,
            bottom = 28.dp + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(38.dp, "T")
                Spacer(Modifier.width(11.dp))
                Column {
                    Text("Template", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "Titik awal yang siap diubah AI",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(2.dp))
            Text(
                "Mulai dari yang\nsudah terlihat bagus.",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Setiap template adalah aplikasi utuh: HTML, CSS, JS, dan design token. Klik pakai, lalu lanjutkan lewat chat.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )
        }
        items(Templates.ALL, key = { it.id }) { info ->
            TemplateShowcaseCard(
                info = info,
                busy = busy == info.id,
                onUse = {
                    if (busy != null) return@TemplateShowcaseCard
                    busy = info.id
                    vm.create(info.name, info.id) { project ->
                        busy = null
                        onOpenProject(project.id)
                    }
                },
            )
        }
        item {
            Text(
                "Punya ide lain? Buat proyek kosong dan jelaskan semuanya lewat chat — agen akan menulis dari nol.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun TemplateShowcaseCard(
    info: TemplateInfo,
    busy: Boolean,
    onUse: () -> Unit,
) {
    val design = Templates.designFor(info.id)
    val primary = parse(design.primary)
    val accent = parse(design.accent)
    val bg = parse(design.background)
    val shape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), accent.copy(alpha = 0.10f))))
        ) {
            MockAppWindow(
                design = design,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(bg.copy(alpha = 0.72f))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text("${info.emoji}  ${info.category}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(8.dp))
                Pill(info.category, color = primary, background = primary.copy(alpha = 0.12f))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .then(
                            if (busy) Modifier.background(primary.copy(alpha = 0.3f))
                            else Modifier.background(Brush.linearGradient(listOf(primary.copy(alpha = 0.25f), accent.copy(alpha = 0.10f))))
                        )
                        .clickable(enabled = !busy, onClick = onUse)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (busy) "Membuat…" else "Pakai",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(PixelIcons.ArrowRight, null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(info.tagline, style = MaterialTheme.typography.labelLarge, color = primary)
            Spacer(Modifier.height(5.dp))
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MockAppWindow(design: DesignSystem, modifier: Modifier = Modifier) {
    val bg = parse(design.background)
    val surface = parse(design.surface)
    val primary = parse(design.primary)
    val accent = parse(design.accent)
    val text = parse(design.textColor)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg.copy(alpha = 0.9f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .padding(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
            Spacer(Modifier.width(3.dp))
            Box(Modifier.size(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.weight(1f)) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(text.copy(alpha = 0.55f))
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.55f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(text.copy(alpha = 0.2f))
                )
                Spacer(Modifier.height(3.dp))
                Box(
                    Modifier
                        .width(54.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.22f), accent.copy(alpha = 0.10f))))
                )
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(surface.copy(alpha = 0.9f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .width(64.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.verticalGradient(listOf(primary.copy(alpha = 0.55f), accent.copy(alpha = 0.2f))))
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(surface)
                )
            }
        }
    }
}

private fun parse(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrDefault(Palette.Primary)
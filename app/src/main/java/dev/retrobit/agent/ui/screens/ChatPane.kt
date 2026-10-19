package dev.retrobit.agent.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.data.AgentStep
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.data.StepStatus
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.LoadingDots
import dev.retrobit.agent.ui.components.MarkdownText
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.relativeTime
import dev.retrobit.agent.ui.components.toolVisual
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.icons.PixelIcons

private data class Suggestion(val emoji: String, val title: String, val prompt: String)

private val starterSuggestions = listOf(
    Suggestion("☕", "Landing page produk", "Buat landing page untuk brand kopi lokal bernama Kopi Senja. Gunakan tema gelap yang hangat."),
    Suggestion("🛍️", "Toko online", "Buat toko online sederhana dengan katalog produk, keranjang, dan checkout."),
    Suggestion("✅", "Aplikasi tugas", "Buat aplikasi daftar tugas dengan filter, prioritas, dan penyimpanan data."),
    Suggestion("🎨", "Ubah tema", "Ubah tema aplikasi jadi lebih terang dan elegan dengan aksen emas."),
)

@Composable
fun ChatPane(
    project: Project,
    messages: List<ChatMessage>,
    live: LiveAgentState,
    demoMode: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable(project.id) { mutableStateOf("") }
    val listState = rememberLazyListState()
    val hasWelcome = messages.isEmpty() && !live.running
    val itemCount = messages.size + (if (live.running) 1 else 0) + (if (hasWelcome) 1 else 0)

    LaunchedEffect(itemCount, live.steps.size, live.text.length) {
        if (itemCount > 0) {
            runCatching { listState.animateScrollToItem(itemCount - 1) }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (messages.isEmpty() && !live.running) {
                item { ChatWelcome(project, demoMode, onSend = { onSend(it) }) }
            }
            items(messages.size) { index ->
                val message = messages[index]
                if (message.role == Role.USER) {
                    UserBubble(message)
                } else {
                    AssistantMessage(message, project)
                }
            }
            if (live.running) {
                item { LiveAssistantCard(live) }
            }
                }


        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            running = live.running,
            onSend = {
                val value = draft.trim()
                if (value.isNotEmpty()) {
                    onSend(value)
                    draft = ""
                }
            },
            onStop = onStop,
        )
    }
}

@Composable
private fun ChatWelcome(project: Project, demoMode: Boolean, onSend: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(40.dp)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    "Halo, saya RetroBit.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (demoMode) "Mode demo · siap membuat aplikasi" else "Agen siap membantu ${project.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Ceritakan aplikasi yang kamu inginkan. Saya menulis file, menyusun desain, mengatur database, dan hasilnya langsung bisa dilihat di tab Preview.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            starterSuggestions.take(2).forEach { suggestion ->
                SuggestionCard(suggestion, Modifier.weight(1f)) { onSend(suggestion.prompt) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            starterSuggestions.drop(2).forEach { suggestion ->
                SuggestionCard(suggestion, Modifier.weight(1f)) { onSend(suggestion.prompt) }
            }
        }
        if (demoMode) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Warning.copy(alpha = 0.06f))
                    .border(1.dp, Palette.Warning.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🧪", fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Mode demo membuat aplikasi dari template. Hubungkan provider di AI Cloud untuk instruksi bebas.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(suggestion: Suggestion, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.65f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Icon(PixelIcons.Sparkles, null, tint = Palette.Accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.height(8.dp))
        Text(
            suggestion.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(4.dp, 4.dp, 1.dp, 4.dp))
                .background(Palette.SurfaceHigh)
                .border(1.dp, Palette.StrokeSoft, RoundedCornerShape(4.dp, 4.dp, 1.dp, 4.dp))
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Text(
                message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 21.sp,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            relativeTime(message.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

@Composable
private fun AssistantMessage(message: ChatMessage, project: Project) {
    var stepsOpen by remember(message.id) { mutableStateOf(message.steps.size <= 4) }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(24.dp, "R")
            Spacer(Modifier.width(8.dp))
            Text(
                "RetroBit",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                relativeTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            )
            Spacer(Modifier.weight(1f))
            if (message.modelLabel.isNotBlank()) {
                Pill(message.modelLabel, monospace = true)
            }
        }
        Spacer(Modifier.height(9.dp))
        if (message.steps.isNotEmpty()) {
            StepsCard(
                steps = message.steps,
                expanded = stepsOpen,
                running = false,
                onToggle = { stepsOpen = !stepsOpen },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (message.isError) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Danger.copy(alpha = 0.07f))
                    .border(1.dp, Palette.Danger.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(13.dp)
            ) {
                MarkdownText(
                    text = message.content,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } else {
            MarkdownText(
                text = message.content,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (message.filesChanged.isNotEmpty() || message.tokens > 0) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (message.filesChanged.isNotEmpty()) {
                    Pill(
                        "${message.filesChanged.size} file berubah",
                        color = Palette.Success,
                        background = Palette.Success.copy(alpha = 0.1f),
                    )
                }
                if (message.tokens > 0) {
                    Pill("${message.tokens} token", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun LiveAssistantCard(live: LiveAgentState) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(24.dp, "R")
            Spacer(Modifier.width(8.dp))
            Text(
                "RetroBit sedang bekerja",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(8.dp))
            LoadingDots()
        }
        Spacer(Modifier.height(10.dp))
        StepsCard(
            steps = live.steps,
            expanded = true,
            running = true,
            onToggle = { },
        )
        if (live.text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            MarkdownText(live.text, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(11.dp), color = Palette.Accent, strokeWidth = 1.4.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                live.notice.ifBlank { "Menyiapkan langkah berikutnya…" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun StepsCard(
    steps: List<AgentStep>,
    expanded: Boolean,
    running: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PixelIcons.Sparkles, null, tint = Palette.Accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                if (running) "Aktivitas" else "Aktivitas agen",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Pill("${steps.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Icon(
                if (expanded) PixelIcons.ChevronUp else PixelIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                if (steps.isEmpty()) {
                    Text(
                        "Menunggu langkah pertama…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                steps.forEachIndexed { index, step ->
                    StepRow(step, isLast = index == steps.lastIndex, running = running && step.status == StepStatus.RUNNING)
                }
            }
        }
    }
}

@Composable
private fun StepRow(step: AgentStep, isLast: Boolean, running: Boolean) {
    val visual = toolVisual(step.tool)
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(visual.tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (running) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        color = visual.tint,
                        strokeWidth = 1.4.dp,
                    )
                } else {
                    Icon(visual.icon, contentDescription = null, tint = visual.tint, modifier = Modifier.size(12.dp))
                }
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (step.durationMs > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${step.durationMs}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontFamily = MonoFont,
                    )
                }
            }
            if (step.detail.isNotBlank() && step.detail != "Berjalan…") {
                Text(
                    step.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (step.status == StepStatus.FAILED) Palette.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    running: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        val shape = RoundedCornerShape(6.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                .padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (draft.isEmpty()) {
                    Text(
                        if (running) "Agen sedang bekerja…" else "Minta perubahan atau fitur baru…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 6000) onDraftChange(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Palette.Accent),
                    maxLines = 6,
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .then(
                        if (running || draft.isBlank()) {
                            Modifier.background(
                                if (running) Palette.Danger.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            Modifier.background(Palette.Primary)
                        }
                    )
                    .clickable {
                        if (running) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (running) PixelIcons.Stop else PixelIcons.Send,
                    contentDescription = if (running) "Hentikan" else "Kirim",
                    tint = when {
                        running -> Palette.Danger
                        draft.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "Agen dapat mengubah file proyek. Periksa hasil di Preview.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.ChatConversation
import dev.retrobit.agent.data.ChatMessage
import dev.retrobit.agent.data.ChatSpace
import dev.retrobit.agent.data.Role
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.GhostButton
import dev.retrobit.agent.ui.components.LoadingDots
import dev.retrobit.agent.ui.components.MarkdownText
import dev.retrobit.agent.ui.components.NeoCard
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.relativeTime
import dev.retrobit.agent.ui.icons.PixelIcons
import dev.retrobit.agent.ui.theme.Palette

/**
 * Plain chat: a list of conversations plus a thread view. No project, no tools,
 * no files — the model is used as a model.
 */
@Composable
fun ChatSpaceScreen(
    vm: ChatSpaceViewModel,
    onOpenCloud: () -> Unit,
) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val active = vm.activeId?.let { id -> conversations.firstOrNull { it.id == id } }

    if (active == null) {
        ConversationList(
            conversations = conversations,
            model = vm.modelSummary,
            onOpen = vm::open,
            onDelete = vm::delete,
            onNew = vm::newChat,
            onOpenCloud = onOpenCloud,
        )
    } else {
        ChatThread(
            vm = vm,
            conversation = active,
            onBack = vm::backToList,
            onNew = vm::newChat,
            onOpenCloud = onOpenCloud,
        )
    }
}

@Composable
private fun ConversationList(
    conversations: List<ChatConversation>,
    model: String,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onNew: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(30.dp, "C")
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Chat", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Obrolan bebas · tanpa tool, tanpa proyek",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(model, monospace = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))

        NeoCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNew)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PixelIcons.Plus, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Obrolan baru",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    model,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (conversations.isEmpty()) {
            EmptyState(
                icon = PixelIcons.Comment,
                title = "Belum ada obrolan",
                description = "Tanya apa saja — tanpa proyek, tanpa file. Cocok untuk model gratis: " +
                    "satu pertanyaan, satu jawaban.",
                action = {
                    GhostButton(text = "Buka AI Cloud", onClick = onOpenCloud)
                },
            )
        } else {
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationRow(conversation, onOpen, onDelete)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ChatConversation,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val preview = ChatSpace.preview(conversation)
    NeoCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen(conversation.id) }
                .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    preview.ifBlank { "Kosong" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        relativeTime(conversation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    )
                    if (conversation.modelLabel.isNotBlank()) {
                        Spacer(Modifier.width(7.dp))
                        Pill(conversation.modelLabel, monospace = true, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            IconButton(onClick = { onDelete(conversation.id) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    PixelIcons.Trash,
                    contentDescription = "Hapus obrolan",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatThread(
    vm: ChatSpaceViewModel,
    conversation: ChatConversation,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    val listState = rememberLazyListState()
    val lastId = conversation.messages.lastOrNull()?.id
    LaunchedEffect(lastId, vm.sending) {
        val target = if (conversation.messages.isEmpty()) 0 else conversation.messages.size
        if (listState.layoutInfo.totalItemsCount > 0) {
            listState.animateScrollToItem(target.coerceAtMost(listState.layoutInfo.totalItemsCount - 1))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 12.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(PixelIcons.ArrowLeft, "Kembali", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    conversation.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    "${conversation.messages.size} pesan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onNew, modifier = Modifier.size(48.dp)) {
                Icon(PixelIcons.Plus, "Obrolan baru", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onOpenCloud, modifier = Modifier.size(48.dp)) {
                Icon(PixelIcons.Cloud, "AI Cloud", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        if (conversation.messages.isEmpty()) {
            EmptyState(
                icon = PixelIcons.Comment,
                title = "Mulai mengobrol",
                description = "Tulis pertanyaan di bawah. Model menjawab tanpa menyentuh file proyek.",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                items(conversation.messages, key = { it.id }) { message ->
                    if (message.role == Role.USER) UserBubble(message) else AssistantBubble(message)
                }
                if (vm.sending) {
                    item(key = "typing") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BrandMark(22.dp, "R")
                            Spacer(Modifier.width(9.dp))
                            Text(
                                vm.notice.ifBlank { "Menunggu jawaban…" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            LoadingDots()
                        }
                    }
                }
                item { Spacer(Modifier.height(10.dp)) }
            }
        }

        ChatComposer(
            draft = vm.draft,
            onDraftChange = { vm.draft = it },
            sending = vm.sending,
            canChat = vm.canChat,
            onSend = vm::send,
            onStop = vm::stop,
            onOpenCloud = onOpenCloud,
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
private fun AssistantBubble(message: ChatMessage) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(22.dp, "R")
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
            if (message.tokens > 0) Pill("${message.tokens} token", monospace = true)
            else if (message.modelLabel.isNotBlank()) Pill(message.modelLabel, monospace = true)
        }
        Spacer(Modifier.height(8.dp))
        if (message.isError) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Danger.copy(alpha = 0.07f))
                    .border(1.dp, Palette.Danger.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                    .padding(13.dp)
            ) {
                MarkdownText(text = message.content, color = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            MarkdownText(text = message.content, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ChatComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    canChat: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onOpenCloud: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding()) {
        if (!canChat) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCloud)
                    .padding(horizontal = 18.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PixelIcons.Warning, null, tint = Palette.Warning, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    "Belum ada provider. Ketuk untuk membuka AI Cloud.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Warning,
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(start = 15.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (draft.isEmpty()) {
                    Text(
                        if (sending) "Menunggu jawaban…" else "Tulis pesan…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 8000) onDraftChange(it) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(Palette.Accent),
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .then(
                        if (sending || draft.isBlank()) {
                            Modifier.background(
                                if (sending) Palette.Danger.copy(alpha = 0.16f)
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        } else {
                            Modifier.background(Palette.Primary)
                        }
                    )
                    .clickable { if (sending) onStop() else onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (sending) PixelIcons.Stop else PixelIcons.Send,
                    contentDescription = if (sending) "Hentikan" else "Kirim",
                    tint = when {
                        sending -> Palette.Danger
                        draft.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onPrimary
                    },
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

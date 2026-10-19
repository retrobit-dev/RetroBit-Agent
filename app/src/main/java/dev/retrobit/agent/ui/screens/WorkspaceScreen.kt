@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.retrobit.agent.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.preview.ConsoleEntry
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.GhostButton
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.hexColor
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.SystemPromptDialog
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.theme.PixelFont
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.retrobit.agent.ui.icons.PixelIcons

enum class WorkspaceTab(val label: String, val icon: ImageVector) {
    CHAT("Chat", PixelIcons.Comment),
    PREVIEW("Preview", PixelIcons.Smartphone),
    CODE("Kode", PixelIcons.Code),
    DESIGN("Desain", PixelIcons.Palette),
    DATA("Data", PixelIcons.Database),
    VAULT("Vault", PixelIcons.Key),
}

@Composable
fun WorkspaceScreen(vm: WorkspaceViewModel, onBack: () -> Unit) {
    val project by vm.project.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val live by vm.live.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val console by vm.console.collectAsStateWithLifecycle()
    val lan by vm.lan.collectAsStateWithLifecycle()
    val files by vm.fileNodes.collectAsStateWithLifecycle()
    val hasSnapshot by vm.hasSnapshot.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(WorkspaceTab.CHAT) }
    var menuOpen by remember { mutableStateOf(false) }
    var consoleOpen by remember { mutableStateOf(false) }
    var lanOpen by remember { mutableStateOf(false) }
    var clearOpen by remember { mutableStateOf(false) }
    var restoreOpen by remember { mutableStateOf(false) }
    var promptOpen by remember { mutableStateOf(false) }
    var promptDraft by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val current = project

    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Memuat proyek…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                GhostButton("Kembali", onClick = onBack)
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(PixelIcons.ArrowLeft, "Kembali", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.linearGradient(listOf(Palette.SurfaceHigh, Palette.Surface))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    current.name.take(1).uppercase(),
                    fontFamily = PixelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    current.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (config.isDemo) Palette.Warning else Palette.Success)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (config.isDemo) "Demo · ${files.size} file" else "${config.displayModel} · ${files.size} file",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (live.running) {
                Pill("bekerja", color = Palette.Accent, background = Palette.Accent.copy(alpha = 0.12f), dot = true)
                Spacer(Modifier.width(4.dp))
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(38.dp)) {
                    Icon(PixelIcons.MoreVertical, "Menu", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Muat ulang preview") },
                        leadingIcon = { Icon(PixelIcons.Reload, null, modifier = Modifier.size(17.dp)) },
                        onClick = { menuOpen = false; vm.reloadPreview(); tab = WorkspaceTab.PREVIEW },
                    )
                    DropdownMenuItem(
                        text = { Text("Console") },
                        leadingIcon = { Icon(PixelIcons.Terminal, null, modifier = Modifier.size(17.dp)) },
                        onClick = { menuOpen = false; consoleOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text(if (lan.running) "Preview LAN aktif" else "Preview via LAN") },
                        leadingIcon = { Icon(PixelIcons.Globe, null, modifier = Modifier.size(17.dp)) },
                        onClick = { menuOpen = false; lanOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Ekspor ZIP") },
                        leadingIcon = { Icon(PixelIcons.Download, null, modifier = Modifier.size(17.dp)) },
                        onClick = {
                            menuOpen = false
                            if (!exporting) {
                                exporting = true
                                scope.launch {
                                    val file = vm.exportZip()
                                    exporting = false
                                    if (file != null) {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/zip"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Bagikan proyek"))
                                        }
                                    }
                                }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("System prompt obrolan") },
                        leadingIcon = { Icon(PixelIcons.Terminal, null, modifier = Modifier.size(17.dp)) },
                        onClick = {
                            menuOpen = false
                            promptDraft = current.systemPrompt?.takeIf { it.isNotBlank() } ?: vm.settingsPrompt()
                            promptOpen = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Pulihkan versi sebelumnya") },
                        leadingIcon = { Icon(PixelIcons.Undo, null, modifier = Modifier.size(17.dp)) },
                        enabled = hasSnapshot,
                        onClick = { menuOpen = false; restoreOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Bersihkan percakapan") },
                        leadingIcon = { Icon(PixelIcons.Trash, null, modifier = Modifier.size(17.dp)) },
                        onClick = { menuOpen = false; clearOpen = true },
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            WorkspaceTab.entries.forEach { item ->
                val selected = tab == item
                Row(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) Palette.Primary.copy(alpha = 0.16f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) Palette.Primary.copy(alpha = 0.45f) else Color.Transparent,
                            RoundedCornerShape(3.dp),
                        )
                        .clickable { tab = item }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = if (selected) Palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            when (tab) {
                WorkspaceTab.CHAT -> ChatPane(
                    project = current,
                    messages = messages,
                    live = live,
                    demoMode = config.isDemo,
                    onSend = { vm.send(it); },
                    onStop = { vm.stopAgent() },
                )
                WorkspaceTab.PREVIEW -> PreviewTab(
                    vm = vm,
                    onOpenConsole = { consoleOpen = true },
                    onOpenLan = { lanOpen = true },
                )
                WorkspaceTab.CODE -> CodePane(vm)
                WorkspaceTab.DESIGN -> DesignPane(vm)
                WorkspaceTab.DATA -> DataPane(vm)
                WorkspaceTab.VAULT -> VaultPane(vm)
            }
        }
    }

    if (consoleOpen) {
        ConsoleSheet(
            entries = console,
            onDismiss = { consoleOpen = false },
            onClear = { vm.clearConsole() },
            onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
        )
    }

    if (lanOpen) {
        LanSheet(
            vm = vm,
            onDismiss = { lanOpen = false },
            onCopy = { clipboard.setText(AnnotatedString(it)) },
        )
    }

    if (promptOpen) {
        SystemPromptDialog(
            title = "System prompt obrolan",
            description = "Hanya berlaku untuk ${current.name}. Chat lain menyimpan prompt masing-masing.",
            value = promptDraft,
            onValueChange = { promptDraft = it },
            onSave = {
                vm.saveSystemPrompt(promptDraft)
                promptOpen = false
            },
            onDismiss = { promptOpen = false },
            resetLabel = "Ikuti global",
            onReset = {
                vm.saveSystemPrompt(null)
                promptOpen = false
            },
        )
    }

    if (restoreOpen) {
        AlertDialog(
            onDismissRequest = { restoreOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Pulihkan versi sebelumnya?") },
            text = { Text("RetroBit menyimpan salinan otomatis sebelum setiap perintah AI. File saat ini akan diganti dengan salinan terakhir. Database dan secret tidak berubah.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.restoreLatestSnapshot()
                    restoreOpen = false
                }) { Text("Pulihkan", color = Palette.Accent) }
            },
            dismissButton = { TextButton(onClick = { restoreOpen = false }) { Text("Batal") } },
        )
    }

    if (clearOpen) {
        AlertDialog(
            onDismissRequest = { clearOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Bersihkan percakapan?") },
            text = { Text("Riwayat chat di proyek ini dihapus. File dan database tetap aman.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearConversation()
                    clearOpen = false
                }) { Text("Bersihkan", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { clearOpen = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun ConsoleSheet(
    entries: List<ConsoleEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val errors = entries.count { it.isError }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PixelIcons.Terminal, null, tint = Palette.Accent, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("Console", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${entries.size} pesan · $errors error",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (errors > 0) Palette.Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (entries.isNotEmpty()) {
                    IconButton(onClick = { onCopy(entries.joinToString("\n") { "[${it.level}] ${it.message}" }) }) {
                        Icon(PixelIcons.Copy, "Salin", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                    TextButton(onClick = onClear) {
                        Text("Bersihkan", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (entries.isEmpty()) {
                EmptyState(
                    icon = PixelIcons.FileText,
                    title = "Console bersih",
                    description = "Pesan console.log dan error dari halaman preview akan muncul di sini setelah preview dimuat.",
                )
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 430.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val tint = when {
                            entry.isError -> Palette.Danger
                            entry.level.startsWith("WARN") -> Palette.Warning
                            entry.level.startsWith("DEBUG") -> Palette.Muted
                            else -> Palette.Accent
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    entry.level.take(5),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFont,
                                    color = tint,
                                    fontSize = 9.sp,
                                    modifier = Modifier.width(38.dp),
                                )
                                Text(
                                    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(entry.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFont,
                                    color = Palette.Muted.copy(alpha = 0.5f),
                                    fontSize = 9.sp,
                                )
                                if (entry.source.isNotBlank()) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${entry.source}:${entry.line}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = MonoFont,
                                        color = Palette.Muted.copy(alpha = 0.5f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            Text(
                                entry.message,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFont,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(start = 38.dp, top = 2.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Tersedia juga window.RetroBitToast(\"pesan\") dan window.RetroBitDB untuk query database.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun LanSheet(
    vm: WorkspaceViewModel,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val lan by vm.lan.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 26.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(PixelIcons.Globe, null, tint = Palette.Accent, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Preview via LAN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Buka di HP, tablet, atau laptop lain",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Pill(
                    if (lan.running) "aktif" else "mati",
                    color = if (lan.running) Palette.Success else Palette.Muted,
                    background = (if (lan.running) Palette.Success else Palette.Muted).copy(alpha = 0.12f),
                    dot = true,
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "RetroBit menjalankan server kecil di perangkat ini. Siapa pun di jaringan Wi-Fi yang sama dapat membuka preview aplikasimu — tanpa internet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (lan.running) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, Palette.Accent.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            lan.url,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = MonoFont,
                            color = Palette.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${lan.hits} permintaan · terakhir ${lan.lastPath.ifBlank { "—" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onCopy(lan.url) }) {
                        Icon(PixelIcons.Copy, "Salin", tint = Palette.Accent, modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Success))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Perangkat harus berada di Wi-Fi yang sama.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp)
                ) {
                    Text(
                        "Server belum berjalan. Aktifkan untuk mendapatkan tautan preview.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            lan.error?.let { error ->
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Danger.copy(alpha = 0.07f))
                        .border(1.dp, Palette.Danger.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.dismissLanError() }) {
                        Text("Tutup", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            GradientButton(
                text = if (lan.running) "Matikan server" else "Aktifkan server LAN",
                modifier = Modifier.fillMaxWidth(),
                onClick = { vm.toggleLan() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Butuh port ${lan.url.substringAfterLast(':').substringBefore('/').ifBlank { "8787" }} yang bebas dan izin jaringan lokal.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
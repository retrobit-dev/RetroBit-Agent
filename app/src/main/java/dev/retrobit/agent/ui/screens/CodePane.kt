package dev.retrobit.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.FileNode
import dev.retrobit.agent.data.ProjectFiles
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.highlightCode
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import java.util.Locale
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun CodePane(vm: WorkspaceViewModel, modifier: Modifier = Modifier) {
    val nodes by vm.fileNodes.collectAsStateWithLifecycle()
    val openFile by vm.openFile.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }
    var newPath by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize()) {
        val file = openFile
        if (file == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("File proyek", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${nodes.count { !it.isDirectory }} file · entry point index.html",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Palette.Primary.copy(alpha = 0.12f))
                        .clickable { showCreate = true }
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PixelIcons.Plus, null, tint = Palette.Primary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("File baru", style = MaterialTheme.typography.labelSmall, color = Palette.Primary)
                }
            }
            if (nodes.isEmpty()) {
                EmptyState(
                    icon = PixelIcons.Folder,
                    title = "Belum ada file",
                    description = "Minta AI membuat aplikasi, atau tambahkan file sendiri.",
                    action = { GradientButton("File baru", onClick = { showCreate = true }) },
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    items(nodes.size) { index ->
                        val node = nodes[index]
                        FileRow(node) {
                            if (!node.isDirectory) vm.openFile(node.path)
                        }
                    }
                    item {
                        Text(
                            "Folder _meta menyimpan percakapan dan tidak ditampilkan.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.closeFile() }, modifier = Modifier.size(36.dp)) {
                    Icon(PixelIcons.ArrowLeft, "Kembali", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(19.dp))
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        file.path,
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = MonoFont,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${ProjectFiles.formatBytes(file.content.toByteArray().size.toLong())} · ${file.content.lines().size} baris",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!file.isBinary && file.content.length < 120_000) {
                    IconButton(onClick = { showEditor = true }, modifier = Modifier.size(34.dp)) {
                        Icon(PixelIcons.Pencil, "Edit", tint = Palette.Accent, modifier = Modifier.size(17.dp))
                    }
                }
                IconButton(onClick = { deleteTarget = nodes.firstOrNull { it.path == file.path } }, modifier = Modifier.size(34.dp)) {
                    Icon(PixelIcons.Trash, "Hapus", tint = Palette.Muted, modifier = Modifier.size(17.dp))
                }
            }
            CodeReader(file.content, Modifier.weight(1f))
        }
    }

    if (showCreate) {
        SimpleInputDialog(
            title = "File baru",
            label = "Path",
            placeholder = "assets/app.js",
            value = newPath,
            onValueChange = { newPath = it },
            confirmText = "Buat",
            onDismiss = { showCreate = false; newPath = "" },
            onConfirm = {
                vm.createFile(newPath)
                showCreate = false
                newPath = ""
            },
        )
    }

    val editing = openFile
    if (showEditor && editing != null) {
        FileEditorDialog(
            path = editing.path,
            initial = editing.content,
            onDismiss = { showEditor = false },
            onSave = { content ->
                vm.saveFile(editing.path, content)
                showEditor = false
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus ${target.name}?") },
            text = { Text("File akan dihapus dari proyek. Tindakan ini tidak bisa dibatalkan.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteFile(target.path)
                    deleteTarget = null
                }) { Text("Hapus", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun FileRow(node: FileNode, onClick: () -> Unit) {
    val tint = when (node.name.substringAfterLast('.', "")) {
        "html" -> Palette.Primary
        "css" -> Palette.Accent
        "js", "ts" -> Palette.Warning
        "json" -> Palette.Success
        "svg", "png", "jpg", "webp" -> Palette.Pink
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = !node.isDirectory, onClick = onClick)
            .padding(start = (10 + node.depth * 16).dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (node.isDirectory) PixelIcons.Folder else PixelIcons.FileText,
            contentDescription = null,
            tint = if (node.isDirectory) Palette.Muted else tint,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            node.name + if (node.isDirectory) "/" else "",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (!node.isDirectory) MonoFont else FontFamily.Default,
            color = if (node.isDirectory) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!node.isDirectory) {
            Text(
                ProjectFiles.formatBytes(node.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                fontFamily = MonoFont,
            )
        }
    }
}

@Composable
private fun CodeReader(content: String, modifier: Modifier = Modifier) {
    val allLines = content.lines()
    val lines = allLines.take(1500)
    val gutterWidth = (lines.size.toString().length * 8 + 20).dp
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Column(
                    Modifier
                        .width(gutterWidth)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
                        .padding(vertical = 12.dp)
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            "${index + 1}",
                            fontFamily = MonoFont,
                            fontSize = 10.5.sp,
                            lineHeight = 18.sp,
                            color = Palette.Muted.copy(alpha = 0.45f),
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp),
                        )
                    }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 12.dp, horizontal = 10.dp)
                ) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            highlightCode(lines.joinToString("\n"), ""),
                            fontFamily = MonoFont,
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                            color = Palette.Text,
                        )
                    }
                }
            }
        }
        if (allLines.size > 1500) {
            Text(
                "Menampilkan 1.500 dari ${allLines.size} baris.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
fun SimpleInputDialog(
    title: String,
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = MonoFont, fontSize = 13.sp),
                        cursorBrush = SolidColor(Palette.Accent),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box {
                                if (value.isEmpty()) {
                                    Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MonoFont)
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = value.isNotBlank()) {
                Text(confirmText, color = if (value.isNotBlank()) Palette.Accent else Palette.Muted)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

@Composable
private fun FileEditorDialog(
    path: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(path) { mutableStateOf(initial) }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.background)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Edit file", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "$path · ${text.lines().size} baris",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = MonoFont,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onDismiss) { Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                TextButton(onClick = { onSave(text) }) { Text("Simpan", color = Palette.Accent, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    textStyle = TextStyle(
                        color = Palette.Text,
                        fontFamily = MonoFont,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                    ),
                    cursorBrush = SolidColor(Palette.Accent),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("UTF-8", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%,d karakter", text.length),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "index.html utuh lebih aman diedit lewat AI",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}
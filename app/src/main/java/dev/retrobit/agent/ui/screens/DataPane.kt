package dev.retrobit.agent.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.QueryResult
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.NeoCard
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette

@Composable
fun DataPane(vm: WorkspaceViewModel, modifier: Modifier = Modifier) {
    val tables by vm.tables.collectAsStateWithLifecycle()
    val selected by vm.selectedTable.collectAsStateWithLifecycle()
    val preview by vm.tablePreview.collectAsStateWithLifecycle()
    val schema by vm.tableSchema.collectAsStateWithLifecycle()
    val result by vm.queryResult.collectAsStateWithLifecycle()
    val running by vm.queryRunning.collectAsStateWithLifecycle()
    var sql by rememberSaveable { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var tableName by remember { mutableStateOf("") }

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
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Storage, null, tint = Palette.Accent, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Database proyek", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "SQLite di perangkat · ${tables.size} tabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.refreshDb() }, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Outlined.Refresh, "Muat ulang", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Palette.Primary.copy(alpha = 0.12f))
                    .clickable { showCreate = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Add, null, tint = Palette.Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tabel", style = MaterialTheme.typography.labelSmall, color = Palette.Primary)
            }
        }

        if (tables.isEmpty()) {
            NeoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    EmptyState(
                        emoji = "🗄️",
                        title = "Database masih kosong",
                        description = "Buat tabel pertama, atau isi dengan data contoh untuk dicoba di preview.",
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        PresetButton("Tugas", Modifier.weight(1f)) { vm.seedDatabase("tasks") }
                        PresetButton("Pengguna", Modifier.weight(1f)) { vm.seedDatabase("users") }
                        PresetButton("Produk", Modifier.weight(1f)) { vm.seedDatabase("products") }
                    }
                }
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                tables.forEach { table ->
                    val active = selected == table.name
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) Palette.Primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                if (active) Palette.Primary.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { vm.selectTable(table.name) }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Text(
                            table.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = MonoFont,
                            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "${table.rows} baris · ${table.columns} kolom",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }

            if (schema.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            selected ?: "Skema",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = MonoFont,
                        )
                        Spacer(Modifier.height(9.dp))
                        schema.forEach { column ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    column.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = MonoFont,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                if (column.primaryKey) {
                                    Pill("PK", color = Palette.Warning, background = Palette.Warning.copy(alpha = 0.12f))
                                    Spacer(Modifier.width(5.dp))
                                }
                                Pill(column.type.ifBlank { "TEXT" }, color = Palette.Accent, background = Palette.Accent.copy(alpha = 0.08f), monospace = true)
                                if (column.notNull) {
                                    Spacer(Modifier.width(5.dp))
                                    Pill("NOT NULL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            preview?.let { data ->
                Spacer(Modifier.height(14.dp))
                DataTableCard(title = "Data ${selected.orEmpty()}", result = data)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text("SQL console", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(7.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF08080E))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.03f))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Warning))
                Spacer(Modifier.width(6.dp))
                Text("sqlite", style = MaterialTheme.typography.labelSmall, color = Palette.Muted, fontFamily = MonoFont)
                Spacer(Modifier.weight(1f))
                Text(
                    if (selected != null) "SELECT * FROM $selected LIMIT 20" else "SELECT name FROM sqlite_master",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted.copy(alpha = 0.6f),
                    fontFamily = MonoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            sql = if (selected != null) "SELECT * FROM $selected LIMIT 20" else "SELECT name FROM sqlite_master WHERE type='table';"
                        }
                        .padding(4.dp),
                )
            }
            BasicTextField(
                value = sql,
                onValueChange = { sql = it },
                textStyle = TextStyle(
                    color = Palette.Text,
                    fontFamily = MonoFont,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
                cursorBrush = SolidColor(Palette.Accent),
                minLines = 3,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (sql.isEmpty()) {
                            Text(
                                "SELECT * FROM …;\nINSERT INTO …;\nCREATE TABLE …;",
                                fontFamily = MonoFont,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Palette.Muted.copy(alpha = 0.5f),
                            )
                        }
                        inner()
                    }
                },
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.02f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Hanya proyek ini · aman untuk eksperimen",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Muted.copy(alpha = 0.7f),
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (sql.isBlank() || running) Color.White.copy(alpha = 0.06f) else Palette.Accent.copy(alpha = 0.16f))
                        .clickable(enabled = sql.isNotBlank() && !running) { vm.runQuery(sql) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (running) {
                        CircularProgressIndicator(Modifier.size(12.dp), color = Palette.Accent, strokeWidth = 1.5.dp)
                    } else {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            null,
                            tint = if (sql.isBlank()) Palette.Muted else Palette.Accent,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "Jalankan",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (sql.isBlank()) Palette.Muted else Palette.Accent,
                    )
                }
            }
        }

        result?.let { query ->
            Spacer(Modifier.height(12.dp))
            if (query.error != null) {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = Palette.Danger.copy(alpha = 0.35f),
                    background = Palette.Danger.copy(alpha = 0.06f),
                ) {
                    Column {
                        Text("Error", style = MaterialTheme.typography.labelMedium, color = Palette.Danger)
                        Spacer(Modifier.height(4.dp))
                        Text(query.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, fontFamily = MonoFont, lineHeight = 17.sp)
                    }
                }
            } else if (query.isSelect) {
                DataTableCard(
                    title = "${query.message} · ${query.elapsedMs}ms",
                    result = query,
                )
            } else {
                NeoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = Palette.Success.copy(alpha = 0.25f),
                    background = Palette.Success.copy(alpha = 0.05f),
                ) {
                    Text(
                        "✓ ${query.message} (${query.elapsedMs}ms)",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Success,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        if (tables.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showClear = true }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.DeleteOutline, null, tint = Palette.Danger.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Kosongkan database proyek",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Danger.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Preview bisa membaca dan menulis database lewat window.RetroBitDB.query(sql, params).",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showCreate) {
        SimpleInputDialog(
            title = "Tabel baru",
            label = "Nama tabel",
            placeholder = "notes",
            value = tableName,
            onValueChange = { tableName = it },
            confirmText = "Buat",
            onDismiss = { showCreate = false; tableName = "" },
            onConfirm = {
                vm.createTable(tableName)
                showCreate = false
                tableName = ""
            },
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Kosongkan database?") },
            text = { Text("Semua tabel dan data di proyek ini akan dihapus. File aplikasi tidak terpengaruh.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearDatabase()
                    showClear = false
                }) { Text("Hapus semua", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Batal") } },
        )
    }
}

@Composable
private fun DataTableCard(title: String, result: QueryResult) {
    NeoCard(modifier = Modifier.fillMaxWidth(), padding = 0.dp) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Pill("${result.rows.size} baris", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (result.columns.isEmpty()) {
                Text(
                    "Tidak ada kolom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Row(Modifier.background(Color.White.copy(alpha = 0.04f))) {
                            result.columns.forEach { column ->
                                Text(
                                    column,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Palette.Accent,
                                    fontFamily = MonoFont,
                                    modifier = Modifier
                                        .width(132.dp)
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (result.rows.isEmpty()) {
                            Text(
                                "Tabel kosong.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        result.rows.forEachIndexed { index, row ->
                            Row(
                                Modifier.background(
                                    if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.015f)
                                )
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        cell ?: "null",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (cell == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                        fontFamily = MonoFont,
                                        modifier = Modifier
                                            .width(132.dp)
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

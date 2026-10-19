@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.CloudQueue
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.data.TemplateInfo
import dev.retrobit.agent.data.Templates
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.hexColor
import dev.retrobit.agent.ui.components.NeoCard
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.relativeTime
import dev.retrobit.agent.ui.theme.Palette

@Composable
fun ProjectsScreen(
    vm: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    onOpenCloud: () -> Unit,
    onBrowseTemplates: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var showCreate by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    val projects = vm.projects.value
    val config = vm.config.value
    val filtered = remember(projects, search) {
        if (search.isBlank()) projects
        else projects.filter {
            it.name.contains(search, true) || it.tagline.contains(search, true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 96.dp + contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrandMark(38.dp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("RetroBit", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            "AI app studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(onClick = onOpenCloud)
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (config.isDemo) Palette.Warning else Palette.Success)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (config.isDemo) "Demo" else "Cloud",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Bangun aplikasi\nlewat percakapan.",
                    style = MaterialTheme.typography.displayMedium.copy(
                        brush = Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.onBackground, Palette.Primary, Palette.Accent)
                        )
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Jelaskan idemu — agen menulis kode, mengatur desain, dan menyiapkan database. Lihat hasilnya langsung di preview.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 21.sp,
                )
            }

            item {
                CloudStatusCard(
                    connected = !config.isDemo,
                    model = config.displayModel,
                    onOpenCloud = onOpenCloud,
                )
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text(
                        "Proyek kamu",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("${projects.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Template →",
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBrowseTemplates)
                            .padding(4.dp),
                    )
                }
            }

            item {
                SearchField(value = search, onValueChange = { search = it })
            }

            if (filtered.isEmpty()) {
                item {
                    NeoCard(modifier = Modifier.fillMaxWidth()) {
                        EmptyState(
                            emoji = if (projects.isEmpty()) "🌱" else "🔍",
                            title = if (projects.isEmpty()) "Belum ada proyek" else "Tidak ditemukan",
                            description = if (projects.isEmpty()) "Mulai dari template atau kanvas kosong, lalu ceritakan aplikasi yang ingin kamu buat."
                            else "Coba kata kunci lain atau buat proyek baru.",
                            action = {
                                GradientButton("Buat proyek", icon = Icons.Outlined.Add, onClick = { showCreate = true })
                            },
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { project ->
                    ProjectCard(
                        project = project,
                        onOpen = { onOpenProject(project.id) },
                        onFavorite = { vm.toggleFavorite(project) },
                        onDuplicate = { vm.duplicate(project.id) { onOpenProject(it.id) } },
                        onDelete = { deleteTarget = project },
                    )
                }
            }

            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(18.dp))
                        .clickable { showCreate = true }
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("＋", color = Palette.Accent, fontSize = 22.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Proyek baru", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Mulai dari template atau kanvas kosong",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 18.dp + contentPadding.calculateBottomPadding())
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Palette.Primary, Palette.Accent)))
                .clickable { showCreate = true }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text("Baru", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showCreate) {
        NewProjectSheet(
            onDismiss = { showCreate = false },
            onCreate = { name, template ->
                showCreate = false
                vm.create(name, template) { onOpenProject(it.id) }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus ${target.name}?") },
            text = { Text("Semua file, percakapan, dan database proyek ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target.id)
                    deleteTarget = null
                }) { Text("Hapus", color = Palette.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun CloudStatusCard(connected: Boolean, model: String, onOpenCloud: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (connected) Palette.Success.copy(alpha = 0.07f)
                else Palette.Warning.copy(alpha = 0.07f)
            )
            .border(1.dp, if (connected) Palette.Success.copy(alpha = 0.25f) else Palette.Warning.copy(alpha = 0.22f), shape)
            .clickable(onClick = onOpenCloud)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (connected) Palette.Success.copy(alpha = 0.14f) else Palette.Warning.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.CloudQueue,
                contentDescription = null,
                tint = if (connected) Palette.Success else Palette.Warning,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (connected) "Agen cloud aktif" else "Mode demo aktif",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                if (connected) model else "Hubungkan provider untuk agen sungguhan",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Cari proyek…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = hexColor(project.accent)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.55f), Palette.Accent.copy(alpha = 0.85f)))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent)))
            )
            Text(project.emoji, fontSize = 27.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (project.favorite) {
                    Spacer(Modifier.width(5.dp))
                    Icon(Icons.Outlined.Star, contentDescription = null, tint = Palette.Warning, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                project.tagline.ifBlank { Templates.byId(project.templateId).tagline },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    project.design.preset,
                    color = accent,
                    background = accent.copy(alpha = 0.12f),
                    monospace = false,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    relativeTime(project.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Outlined.MoreVert, contentDescription = "Opsi", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (project.favorite) "Hapus dari favorit" else "Tandai favorit") },
                    leadingIcon = { Icon(Icons.Outlined.StarBorder, null, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = false; onFavorite() },
                )
                DropdownMenuItem(
                    text = { Text("Duplikat") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Palette.Danger) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = Palette.Danger, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
fun NewProjectSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, templateId: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var template by remember { mutableStateOf("saas") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 22.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Proyek baru", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "Pilih titik awal, lalu minta AI mengembangkannya.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BrandMark(32.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text("Nama proyek", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(7.dp))
            BasicTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                singleLine = true,
                decorationBox = { inner ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp))
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                "Contoh: Aplikasi kasir kopi",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(20.dp))
            Text("Template", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Templates.ALL.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { info ->
                            TemplateTile(
                                info = info,
                                selected = template == info.id,
                                onClick = { template = info.id },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            GradientButton(
                text = if (name.isBlank()) "Buat proyek" else "Buat “$name”",
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Outlined.Add,
                onClick = {
                    val chosen = Templates.byId(template)
                    onCreate(name.ifBlank { chosen.name }, template)
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "File dibuat di perangkat dan langsung bisa dipreview.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
fun TemplateTile(
    info: TemplateInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val design = Templates.designFor(info.id)
    val primary = Color(android.graphics.Color.parseColor(design.primary))
    val accent = hexColor(design.accent)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(13.dp),
            )
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(primary, accent))),
                contentAlignment = Alignment.Center,
            ) {
                Text(info.emoji, fontSize = 13.sp)
            }
            Spacer(Modifier.width(7.dp))
            Text(
                info.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            info.tagline,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

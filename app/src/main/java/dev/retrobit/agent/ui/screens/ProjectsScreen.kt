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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.Project
import dev.retrobit.agent.ui.LocalDrawer
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
import dev.retrobit.agent.ui.theme.PixelFont
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun ProjectsScreen(
    vm: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    onOpenCloud: () -> Unit,
    onBrowseTemplates: () -> Unit,
    onCreateWithPrompt: (prompt: String, projectId: String) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var showCreate by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var quickPrompt by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var platform by rememberSaveable { mutableStateOf("android") }
    var deleteTarget by remember { mutableStateOf<Project?>(null) }
    val projects by vm.projects.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val drawer = LocalDrawer.current

    val filtered = remember(projects, search, filter) {
        projects
            .filter { if (filter == "saved") it.favorite else true }
            .filter {
                search.isBlank() ||
                    it.name.contains(search, true) ||
                    it.tagline.contains(search, true)
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 10.dp,
            bottom = 28.dp + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { drawer?.open() }, modifier = Modifier.size(34.dp)) {
                    Icon(PixelIcons.Menu, "Menu", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(4.dp))
                BrandMark(28.dp)
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("RetroBit", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "cyberdeck studio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                        .clickable(onClick = onOpenCloud)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(if (config.isDemo) Palette.Muted else Palette.Accent)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (config.isDemo) "DEMO" else "CLOUD",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showCreate = true }, modifier = Modifier.size(34.dp)) {
                    Icon(PixelIcons.Plus, "Proyek baru", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                }
            }
        }

        item {
            Text(
                "What will you build,\nRetro?",
                style = MaterialTheme.typography.displayLarge.copy(
                    brush = Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.onBackground,
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
                            Palette.Muted,
                        )
                    ),
                ),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Tulis satu kalimat. RetroBit menyusun file, design system, dan database-nya — lalu menampilkannya di preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )
        }

        item {
            ComposerCard(
                value = quickPrompt,
                onValueChange = { quickPrompt = it },
                modelLabel = if (config.isDemo) "Demo lokal" else config.displayModel,
                onOpenCloud = onOpenCloud,
                onOpenCreate = { showCreate = true },
                onSend = {
                    val prompt = quickPrompt.trim()
                    if (prompt.isNotEmpty()) {
                        quickPrompt = ""
                        vm.createFromPrompt(prompt) { project ->
                            onCreateWithPrompt(prompt.take(600), project.id)
                        }
                    }
                },
            )
        }

        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformChip("iPhone", PixelIcons.Apple, platform == "iphone") { platform = "iphone" }
                    PlatformChip("Android", PixelIcons.Android, platform == "android") { platform = "android" }
                    PlatformChip("Web", PixelIcons.Browser, platform == "web") { platform = "web" }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    if (platform == "android") "Preview berjalan di WebView perangkat ini."
                    else "Preview di aplikasi memakai WebView; ekspor ZIP untuk membuka target ${platform.ifBlank { "lain" }}.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Recents", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.width(7.dp))
                Pill("${filtered.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(
                    "Templates ->",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .clickable(onClick = onBrowseTemplates)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }

        item {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip("All", filter == "all") { filter = "all" }
                    FilterChip("Saved", filter == "saved") { filter = "saved" }
                }
                Spacer(Modifier.height(10.dp))
                SearchField(value = search, onValueChange = { search = it })
            }
        }

        if (filtered.isEmpty()) {
            item {
                NeoCard(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        emoji = if (projects.isEmpty()) "[]" else "?",
                        title = if (projects.isEmpty()) "Belum ada proyek" else "Tidak ditemukan",
                        description = if (projects.isEmpty()) "Tulis ide di atas atau mulai dari template. Semua file dibuat di perangkat."
                        else "Coba kata kunci lain, atau simpan proyek sebagai favorit.",
                        action = {
                            GradientButton("Buat proyek", icon = PixelIcons.Plus, onClick = { showCreate = true })
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
            CloudStatusCard(
                connected = !config.isDemo,
                model = config.displayModel,
                onOpenCloud = onOpenCloud,
            )
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
private fun ComposerCard(
    value: String,
    onValueChange: (String) -> Unit,
    modelLabel: String,
    onOpenCloud: () -> Unit,
    onOpenCreate: () -> Unit,
    onSend: () -> Unit,
) {
    val shape = RoundedCornerShape(5.dp)
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface.copy(alpha = 0.85f))
            .border(1.dp, colors.outline, shape)
            .padding(12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            if (value.isEmpty()) {
                Text(
                    "An app that…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { if (it.length <= 1200) onValueChange(it) },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Palette.Accent),
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .border(1.dp, colors.outlineVariant, RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenCreate),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PixelIcons.Plus, "Template", tint = colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .clickable(onClick = onOpenCloud)
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modelLabel.ifBlank { "Model" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(4.dp))
                Icon(PixelIcons.ChevronDown, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(13.dp))
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (value.isBlank()) colors.surfaceVariant else colors.primary)
                    .border(1.dp, colors.outline, RoundedCornerShape(3.dp))
                    .clickable(enabled = value.isNotBlank(), onClick = onSend),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    PixelIcons.Send,
                    "Kirim",
                    tint = if (value.isBlank()) colors.onSurfaceVariant else colors.onPrimary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) colors.primary else colors.surface.copy(alpha = 0.5f))
            .border(1.dp, if (active) colors.primary else colors.outlineVariant, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) colors.onPrimary else colors.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) colors.onPrimary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterChip(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = if (active) colors.onPrimary else colors.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) colors.primary else Color.Transparent)
            .border(1.dp, if (active) colors.primary else colors.outlineVariant, RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun CloudStatusCard(connected: Boolean, model: String, onOpenCloud: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
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
                .clip(RoundedCornerShape(3.dp))
                .background(if (connected) Palette.Success.copy(alpha = 0.14f) else Palette.Warning.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PixelIcons.Cloud,
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
        Icon(PixelIcons.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PixelIcons.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
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
    val shape = RoundedCornerShape(6.dp)
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
                .clip(RoundedCornerShape(5.dp))
                .background(Brush.linearGradient(listOf(Palette.SurfaceHigh, Palette.Surface, Palette.StrokeSoft))),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.05f), Color.Transparent)))
            )
            Text(
                project.name.take(1).uppercase(),
                fontFamily = PixelFont,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.9f),
            )
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
                    Icon(PixelIcons.Star, contentDescription = null, tint = Palette.Warning, modifier = Modifier.size(13.dp))
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
                Icon(PixelIcons.MoreVertical, contentDescription = "Opsi", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (project.favorite) "Hapus dari favorit" else "Tandai favorit") },
                    leadingIcon = { Icon(PixelIcons.Star, null, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = false; onFavorite() },
                )
                DropdownMenuItem(
                    text = { Text("Duplikat") },
                    leadingIcon = { Icon(PixelIcons.Copy, null, modifier = Modifier.size(17.dp)) },
                    onClick = { menuOpen = false; onDuplicate() },
                )
                DropdownMenuItem(
                    text = { Text("Hapus", color = Palette.Danger) },
                    leadingIcon = { Icon(PixelIcons.Trash, null, tint = Palette.Danger, modifier = Modifier.size(17.dp)) },
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
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
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
                icon = PixelIcons.Plus,
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
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(listOf(primary.copy(alpha = 0.9f), accent.copy(alpha = 0.95f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    info.name.take(1).uppercase(),
                    fontFamily = PixelFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                )
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
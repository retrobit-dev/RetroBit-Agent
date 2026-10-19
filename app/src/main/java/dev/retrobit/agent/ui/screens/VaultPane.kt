package dev.retrobit.agent.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.data.SecretEntry
import dev.retrobit.agent.data.SecretRequest
import dev.retrobit.agent.ui.components.EmptyState
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.NeoCard
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun VaultPane(vm: WorkspaceViewModel, modifier: Modifier = Modifier) {
    val entries by vm.secretEntries.collectAsStateWithLifecycle()
    val requests by vm.secretRequests.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var editing by remember { mutableStateOf<SecretEntry?>(null) }
    var creating by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<SecretRequest?>(null) }
    var deleteTarget by remember { mutableStateOf<SecretEntry?>(null) }
    val revealed = remember(entries) { mutableListOf<String>().toMutableStateList() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.Warning.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(PixelIcons.Lock, null, tint = Palette.Warning, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Secret & kunci API", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${entries.size} tersimpan · terenkripsi di perangkat",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(Palette.Primary.copy(alpha = 0.12f))
                    .clickable { creating = true }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(PixelIcons.Plus, null, tint = Palette.Primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Tambah", style = MaterialTheme.typography.labelSmall, color = Palette.Primary)
            }
        }

        Text(
            "Simpan kunci milik aplikasi yang kamu bangun (mis. token API). Nilai tidak pernah dikirim sebagai bagian prompt ke model. Tandai “preview” hanya jika aman diekspos ke sisi klien.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(16.dp))

        if (requests.isNotEmpty()) {
            Text("DIMINTA AGEN", style = MaterialTheme.typography.labelSmall, letterSpacing = 1.2.sp, color = Palette.Warning)
            Spacer(Modifier.height(7.dp))
            requests.forEach { request ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.Warning.copy(alpha = 0.05f))
                        .border(1.dp, Palette.Warning.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                        .clickable { prefill = request }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(PixelIcons.Key, null, tint = Palette.Warning, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(request.key, style = MaterialTheme.typography.labelMedium, fontFamily = MonoFont, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            request.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("Isi ->", style = MaterialTheme.typography.labelSmall, color = Palette.Warning)
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (entries.isEmpty()) {
            NeoCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = PixelIcons.Key,
                    title = "Belum ada secret",
                    description = "Tambahkan kunci API untuk aplikasi buatanmu. Semuanya dienkripsi dengan Android Keystore.",
                    action = { GradientButton("Tambah secret", icon = PixelIcons.Plus, onClick = { creating = true }) },
                )
            }
        } else {
            entries.forEach { entry ->
                val isRevealed = revealed.contains(entry.key)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                        .padding(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Palette.Warning.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(PixelIcons.Key, null, tint = Palette.Warning, modifier = Modifier.size(13.dp))
                        }
                        Spacer(Modifier.width(9.dp))
                        Text(
                            entry.key,
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = MonoFont,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (entry.exposeToPreview) {
                            Pill("preview", color = Palette.Warning, background = Palette.Warning.copy(alpha = 0.12f))
                        }
                    }
                    if (entry.note.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(entry.note, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isRevealed) entry.value else maskValue(entry.value),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFont,
                            color = if (isRevealed) Palette.Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                if (isRevealed) revealed.remove(entry.key) else revealed.add(entry.key)
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(
                                if (isRevealed) PixelIcons.EyeOff else PixelIcons.Eye,
                                "Lihat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(entry.value)) },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(PixelIcons.Copy, "Salin", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                        }
                        IconButton(
                            onClick = { deleteTarget = entry },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Icon(PixelIcons.Trash, "Hapus", tint = Palette.Muted, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                .padding(13.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(PixelIcons.Lock, null, tint = Palette.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    "Android Keystore · AES-GCM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "Kunci enkripsi tidak pernah meninggalkan perangkat. Pencadangan tidak menyertakan vault ini.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (creating || editing != null) {
        SecretDialog(
            initial = editing,
            prefillKey = prefill?.key.orEmpty(),
            prefillNote = prefill?.description.orEmpty(),
            onDismiss = { creating = false; editing = null; prefill = null },
            onSave = { key, value, note, expose ->
                vm.saveSecret(key, value, note, expose)
                creating = false
                editing = null
                prefill = null
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Hapus ${target.key}?") },
            text = { Text("Secret akan dihapus dari perangkat. Aplikasi yang membutuhkannya harus diberi nilai baru.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSecret(target.key)
                    deleteTarget = null
                }) { Text("Hapus", color = Palette.Danger) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } },
        )
    }
}

@Composable
private fun SecretDialog(
    initial: SecretEntry?,
    prefillKey: String,
    prefillNote: String,
    onDismiss: () -> Unit,
    onSave: (key: String, value: String, note: String, expose: Boolean) -> Unit,
) {
    var key by remember { mutableStateOf(initial?.key ?: prefillKey) }
    var value by remember { mutableStateOf(initial?.value.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note ?: prefillNote) }
    var expose by remember { mutableStateOf(initial?.exposeToPreview ?: false) }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (initial == null) "Secret baru" else "Edit ${initial.key}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.uppercase().replace(" ", "_").take(64) },
                    label = { Text("Nama", style = MaterialTheme.typography.labelSmall) },
                    placeholder = { Text("API_KEY", fontFamily = MonoFont) },
                    singleLine = true,
                    enabled = initial == null && prefillKey.isBlank(),
                    shape = RoundedCornerShape(3.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it.take(4000) },
                    label = { Text("Nilai", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(3.dp),
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) PixelIcons.EyeOff else PixelIcons.Eye,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = MonoFont),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(9.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("Catatan (opsional)", style = MaterialTheme.typography.labelSmall) },
                    singleLine = true,
                    shape = RoundedCornerShape(3.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (expose) Palette.Warning.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Ekspos ke preview", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Tersedia sebagai window.__RETROBIT_ENV__",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp,
                        )
                    }
                    Switch(
                        checked = expose,
                        onCheckedChange = { expose = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.Warning, checkedThumbColor = Color.White),
                    )
                }
                Text(
                    "Jangan ekspos secret server. Siapa pun yang membuka preview dapat membacanya.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.Danger.copy(alpha = 0.85f),
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key, value, note, expose) },
                enabled = key.isNotBlank() && value.isNotBlank(),
            ) {
                Text(
                    "Simpan",
                    color = if (key.isNotBlank() && value.isNotBlank()) Palette.Accent else Palette.Muted,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

private fun maskValue(value: String): String = when {
    value.length <= 4 -> "••••"
    value.length <= 10 -> "••••" + value.takeLast(2)
    else -> "••••••••" + value.takeLast(4)
}
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.unit.sp
import dev.retrobit.agent.BuildConfig
import dev.retrobit.agent.R
import dev.retrobit.agent.ai.AiConfig
import dev.retrobit.agent.ai.ProviderKind
import dev.retrobit.agent.ai.QuotaState
import dev.retrobit.agent.ui.components.BrandMark
import dev.retrobit.agent.ui.components.GhostButton
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.NeoCard
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.components.SystemPromptDialog
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import java.util.Locale
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun CloudScreen(
    vm: CloudViewModel,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    val quota by vm.quota.collectAsStateWithLifecycle()
    var showKey by remember { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp)
            .padding(bottom = 28.dp + contentPadding.calculateBottomPadding())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(38.dp, "C")
            Spacer(Modifier.width(11.dp))
            Column {
                Text("AI Cloud", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Otak untuk agenmu",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            Pill(
                if (vm.demoMode) "Demo" else "Aktif",
                color = if (vm.demoMode) Palette.Warning else Palette.Success,
                background = (if (vm.demoMode) Palette.Warning else Palette.Success).copy(alpha = 0.12f),
                dot = true,
            )
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Hubungkan model favoritmu.",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "RetroBit bekerja dengan provider OpenAI-compatible maupun Anthropic dan Gemini. API key hanya tersimpan di perangkat, terenkripsi lewat Android Keystore, dan tidak pernah ikut ke preview aplikasi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AiConfig.CATALOG.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { preset ->
                        ProviderTile(
                            kind = preset.kind,
                            selected = vm.provider == preset.kind,
                            requiresKey = preset.requiresKey,
                            modifier = Modifier.weight(1f),
                            onClick = { vm.chooseProvider(preset.kind) },
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        if (vm.demoMode) {
            NeoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = Palette.Warning.copy(alpha = 0.3f),
                background = Palette.Warning.copy(alpha = 0.05f),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            PixelIcons.Warning,
                            contentDescription = null,
                            tint = Palette.Warning,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Mode demo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Agen akan membuat aplikasi nyata dari template bawaan, tanpa memanggil model cloud. Pilih provider di atas untuk mengaktifkan agen penuh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }
        } else {
            NeoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(PixelIcons.Key, null, tint = Palette.Accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Kredensial", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.weight(1f))
                        Text(
                            AiConfig.presetFor(vm.provider).docsUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = Palette.Accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (vm.autoMode) {
                        AutoPlan(vm, quota)
                    }
                    if (!vm.autoMode) {
                    CloudField(
                        label = "Base URL",
                        value = vm.baseUrl,
                        onValueChange = vm::updateBaseUrl,
                        placeholder = AiConfig.presetFor(vm.provider).baseUrl,
                        keyboardType = KeyboardType.Uri,
                        mono = true,
                    )
                    if (vm.provider == ProviderKind.OLLAMA) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            vm.localHint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            lineHeight = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GhostButton(
                                text = if (vm.detectingModels) "Mendeteksi…" else "Deteksi model",
                                modifier = Modifier.weight(1f),
                                onClick = { vm.detectLocalModels() },
                            )
                        }
                        vm.detectMessage?.let { message ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CloudField(
                        label = "Model",
                        value = vm.model,
                        onValueChange = vm::updateModel,
                        placeholder = AiConfig.presetFor(vm.provider).models.firstOrNull().orEmpty(),
                        mono = true,
                    )
                    val suggestions = vm.localModels.ifEmpty {
                        AiConfig.presetFor(vm.provider).models.filter { it.isNotBlank() }
                    }
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            suggestions.take(6).forEach { suggestion ->
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (vm.model == suggestion) Palette.Primary.copy(alpha = 0.18f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        )
                                        .clickable { vm.updateModel(suggestion) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            suggestion,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (vm.model == suggestion) Palette.Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = MonoFont,
                                            maxLines = 1,
                                        )
                                        if (AiConfig.isFreeModel(suggestion)) {
                                            Spacer(Modifier.width(5.dp))
                                            FreeBadge()
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    CloudField(
                        label = "API key (satu per baris untuk menggabungkan kuota)",
                        value = vm.apiKey,
                        onValueChange = { vm.apiKey = it },
                        placeholder = AiConfig.presetFor(vm.provider).keyHint,
                        keyboardType = KeyboardType.Password,
                        mono = true,
                        maxLines = 4,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) PixelIcons.EyeOff else PixelIcons.Eye,
                                    contentDescription = if (showKey) "Sembunyikan" else "Tampilkan",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                    if (vm.keyStored) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(PixelIcons.Lock, null, tint = Palette.Success, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                "Key tersimpan terenkripsi di perangkat",
                                style = MaterialTheme.typography.labelSmall,
                                color = Palette.Success,
                            )
                        }
                    }

                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Suhu kreativitas",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            String.format(Locale.US, "%.1f", vm.temperature),
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Accent,
                            fontFamily = MonoFont,
                        )
                    }
                    Slider(
                        value = vm.temperature,
                        onValueChange = { vm.temperature = it },
                        valueRange = 0f..1.2f,
                        colors = SliderDefaults.colors(
                            thumbColor = Palette.Accent,
                            activeTrackColor = Palette.Primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    CloudField(
                        label = "Maks token keluaran",
                        value = vm.maxTokens,
                        onValueChange = { vm.maxTokens = it.filter { ch -> ch.isDigit() }.take(6) },
                        placeholder = "8192",
                        keyboardType = KeyboardType.Number,
                        mono = true,
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            text = "Simpan",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.save() },
                        )
                        GhostButton(
                            text = if (vm.testing) "Menguji…" else "Uji koneksi",
                            modifier = Modifier.weight(1f),
                            onClick = { vm.testConnection() },
                        )
                    }
                    if (vm.testing) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(13.dp), color = Palette.Accent, strokeWidth = 1.5.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Menghubungi provider…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    vm.testResult?.let { result ->
                        Spacer(Modifier.height(12.dp))
                        ResultBanner(ok = result.ok, message = result.message)
                    }
                    vm.savedMessage?.let { message ->
                        Spacer(Modifier.height(10.dp))
                        Text(message, style = MaterialTheme.typography.labelSmall, color = Palette.Success)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { vm.disconnect() }) {
                        Text("Putuskan provider", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pemakaian", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.resetUsage() }) {
                        Text("Reset", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UsageMetric("Permintaan", usage.requests.toString(), Modifier.weight(1f))
                    UsageMetric(
                        "Token",
                        formatCompact(usage.promptTokens + usage.completionTokens),
                        Modifier.weight(1f),
                    )
                    UsageMetric("Estimasi", String.format(Locale.US, "\$%.3f", usage.costUsd), Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Estimasi biaya bersifat perkiraan berdasarkan harga publik model. Tagihan sebenarnya mengikuti provider.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Tanpa batas", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(8.dp))
                UnlimitedRow("1", "Beberapa key per provider", "Tiga key Groq = 3.000 permintaan/hari. Tempel satu key per baris.")
                UnlimitedRow("2", "Auto pindah sendiri", "Provider yang kuota hariannya habis dilewati sebelum request dikirim.")
                UnlimitedRow("3", "Model lokal", "Ollama di komputer sendiri tidak punya kuota per hari sama sekali.")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Angka kuota diambil dari batas gratis yang dipublikasikan provider dan bisa berubah sewaktu-waktu. " +
                        "Tidak ada layanan yang memberi inferensi tanpa batas tanpa ada yang membayarnya — di sini yang membayar " +
                        "adalah akun gratis milikmu, dipakai bergiliran.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Preferensi aplikasi", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Tema", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Grayscale e-paper · ink & paper",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill("TERKUNCI", color = Palette.Muted, background = Color.Transparent)
                }
                Spacer(Modifier.height(14.dp))
                PreferenceRow(
                    title = "Bahasa agen",
                    subtitle = "Bahasa balasan AI",
                    options = listOf("id" to "Indonesia", "en" to "English"),
                    selected = vm.language,
                    onSelect = { vm.updateLanguage(it) },
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Preview otomatis", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Muat ulang preview setiap file berubah",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = vm.autoPreview,
                        onCheckedChange = { vm.updateAutoPreview(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Palette.Primary,
                        ),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        NeoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "System prompt global",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill("chat baru", color = Palette.Accent, background = Palette.Accent.copy(alpha = 0.08f))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    vm.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GhostButton("Edit prompt", icon = PixelIcons.Terminal, onClick = { showPrompt = true })
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${vm.systemPrompt.length} karakter",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Diterapkan otomatis ke setiap proyek baru. Percakapan yang sudah ada menyimpan salinannya sendiri dan tidak ikut berubah.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(PixelIcons.Lock, null, tint = Palette.Muted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "RetroBit tidak mengirim kode atau kunci ke server kami. Permintaan AI langsung menuju provider pilihanmu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.cloud_footer_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }

    if (showPrompt) {
        SystemPromptDialog(
            title = "System prompt global",
            description = "Dipakai untuk semua chat baru. Chat yang sudah ada tetap memakai prompt miliknya sendiri.",
            value = vm.systemPrompt,
            onValueChange = { vm.updateSystemPrompt(it) },
            onSave = {
                vm.saveSystemPrompt()
                showPrompt = false
            },
            onDismiss = { showPrompt = false },
            resetLabel = "Reset ke default",
            onReset = { vm.resetSystemPrompt() },
        )
    }
}

@Composable
private fun FreeBadge() {
    Box(
        Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(Palette.Success.copy(alpha = 0.16f))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    ) {
        Text(
            "GRATIS",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Success,
            fontFamily = MonoFont,
        )
    }
}

/** Shown instead of the URL/model/key fields when the provider is Auto. */
@Composable
private fun AutoPlan(vm: CloudViewModel, quota: QuotaState) {
    Column {
        Text(
            "Auto mencoba provider berurutan dan berhenti di yang pertama menjawab. Urutannya dari yang punya kuota gratis.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(10.dp))
        vm.autoTargets.forEach { target ->
            val free = AiConfig.isFreeModel(target.model) || AiConfig.presetFor(target.kind).freeTier
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (target.hasKey) PixelIcons.Check else PixelIcons.Close,
                    contentDescription = null,
                    tint = if (target.hasKey) Palette.Success else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${target.kind.label} · ${target.model}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (target.hasKey) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val keys = if (target.hasKey) vm.keyCount(target.kind) else 0
                    val left = if (target.hasKey) vm.remaining(target.kind, quota) else null
                    Text(
                        when {
                            !target.hasKey -> "belum ada key"
                            left == 0 -> "kuota hari ini habis"
                            left != null && keys > 1 -> "sisa $left hari ini · $keys key"
                            left != null -> "sisa $left hari ini"
                            keys > 1 -> "$keys key · tanpa batas harian"
                            else -> "siap dipakai"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (left == 0) Palette.Warning else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                if (free && target.hasKey) FreeBadge()
            }
            Spacer(Modifier.height(7.dp))
        }
        Text(
            "Ollama ikut hanya kalau kolom key-nya diisi (boleh teks apa saja) — supaya Auto tidak menunggu server lokal yang mati.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Beberapa key di satu kolom digabung jadi satu kolam kuota, dan Auto melewati provider yang hariannya habis.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            lineHeight = 15.sp,
        )
    }
}

@Composable
private fun ProviderTile(
    kind: ProviderKind,
    selected: Boolean,
    requiresKey: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) Palette.Primary.copy(alpha = 0.13f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (selected) Palette.Primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    when {
                        kind == ProviderKind.DEMO -> Palette.Warning
                        requiresKey -> Palette.Accent
                        else -> Palette.Success
                    }
                )
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                kind.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    kind == ProviderKind.AUTO -> "pilih otomatis"
                    kind == ProviderKind.DEMO -> "tanpa key"
                    requiresKey -> if (AiConfig.presetFor(kind).freeTier) "API key · gratis" else "API key"
                    else -> "lokal / opsional"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CloudField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = maxLines == 1,
        maxLines = maxLines,
        shape = RoundedCornerShape(4.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            fontSize = 12.5.sp,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
    )
}

@Composable
private fun ResultBanner(ok: Boolean, message: String) {
    val tint = if (ok) Palette.Success else Palette.Danger
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(tint.copy(alpha = 0.08f))
            .border(1.dp, tint.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (ok) PixelIcons.Check else PixelIcons.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 16.sp)
    }
}

@Composable
private fun UnlimitedRow(number: String, title: String, detail: String) {
    Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = MonoFont)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun UsageMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(11.dp)
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontFamily = MonoFont, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

}

@Composable
private fun PreferenceRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(3.dp)
        ) {
            options.forEach { (value, label) ->
                val active = selected == value
                Box(
                    // The visible chip stays compact; the touch area is 48 dp so
                    // the smoke walk's target audit does not flag it.
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onSelect(value) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun formatCompact(value: Long): String = when {
    value < 1_000 -> value.toString()
    value < 1_000_000 -> String.format(Locale.US, "%.1fk", value / 1000.0)
    else -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
}

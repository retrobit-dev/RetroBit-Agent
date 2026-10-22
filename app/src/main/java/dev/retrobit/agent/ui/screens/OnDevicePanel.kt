package dev.retrobit.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.retrobit.agent.data.ProjectFiles
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.components.NeoCard

@Composable
fun OnDevicePanel(vm: CloudViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importLocalModel(uri)
    }
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("AI langsung di HP", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tanpa laptop, server, atau API key. Impor model Instruct GGUF tepercaya (satu berkas, maksimum 3 GiB). " +
                        "Untuk GT 30 Pro 12 GB, mulai dari Qwen2.5-Coder 1.5B Instruct Q4_K_M. Model tidak disertakan dalam APK.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = vm::probeLocalEngine, modifier = Modifier.heightIn(min = 48.dp)) { Text("Periksa mesin") }
                vm.localMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(vm.localModelName, style = MaterialTheme.typography.labelLarge)
                if (vm.localModelBytes > 0) Text(ProjectFiles.formatBytes(vm.localModelBytes), style = MaterialTheme.typography.bodySmall)
                TextButton(
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !vm.importingModel && !vm.testing,
                    onClick = { picker.launch(arrayOf("*/*")) },
                ) { Text(if (vm.localModelBytes > 0) "Ganti model GGUF" else "Impor GGUF") }
                if (vm.importingModel) {
                    Text("Disalin: ${ProjectFiles.formatBytes(vm.importBytes)}. Jangan tutup aplikasi.")
                    TextButton(onClick = vm::cancelModelImport) { Text("Batalkan impor") }
                }
                Text("Konteks (prompt + jawaban)", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(2048, 4096).forEach { size ->
                        TextButton(onClick = { vm.updateLocalContext(size) }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (vm.localContext == size) "[$size token]" else "$size token")
                        }
                    }
                }
                OutlinedTextField(
                    value = vm.maxTokens,
                    onValueChange = { vm.maxTokens = it.filter(Char::isDigit).take(4) },
                    label = { Text("Maks token jawaban (1–1024)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Mode awal: Chat offline, streaming, tombol Berhenti. Tool agen pembuat proyek dan analisis APK belum tersedia di mode ini. " +
                        "CPU hingga 4 thread; tidak ada klaim akselerasi NPU. Konteks/panas/RAM tetap membatasi pemakaian.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton("Simpan", Modifier.weight(1f).heightIn(min = 48.dp), enabled = !vm.importingModel && !vm.testing) { vm.save() }
                    TextButton(onClick = vm::testConnection, enabled = !vm.testing && !vm.importingModel) {
                        Text(if (vm.testing) "Menguji…" else "Uji model")
                    }
                }
                Text("Uji model memuat GGUF dan meminta jawaban singkat. Pemuatan awal dapat lama; RAM dilepas setelah jawaban selesai. Berhenti diproses di antara batch token.", style = MaterialTheme.typography.bodySmall)
                vm.savedMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                vm.testResult?.let { Text(if (it.ok) "OK · ${it.message}" else "Gagal · ${it.message}", style = MaterialTheme.typography.bodySmall) }
                if (vm.localModelBytes > 0) TextButton(
                    onClick = vm::deleteLocalModel, enabled = !vm.importingModel && !vm.testing,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text("Hapus model dari penyimpanan HP") }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

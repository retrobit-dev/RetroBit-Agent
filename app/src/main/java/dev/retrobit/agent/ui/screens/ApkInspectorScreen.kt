package dev.retrobit.agent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.retrobit.agent.apk.ApkReport
import dev.retrobit.agent.data.ProjectFiles
import dev.retrobit.agent.ui.LocalDrawer
import dev.retrobit.agent.ui.components.GradientButton
import dev.retrobit.agent.ui.icons.PixelIcons

@Composable
fun ApkInspectorScreen(vm: ApkInspectorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val drawer = LocalDrawer.current
    var section by remember { mutableStateOf("Ringkasan") }
    var query by remember { mutableStateOf("") }
    var pendingExport by remember { mutableStateOf<ApkReport?>(null) }
    val clipboard = LocalClipboardManager.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { section = "Ringkasan"; query = ""; vm.inspect(uri) }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snapshot = pendingExport
        pendingExport = null
        if (uri != null && snapshot != null) vm.export(uri, snapshot)
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = { drawer?.open() }) { Icon(PixelIcons.Menu, "Menu") }
            Text("Inspeksi APK", style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Pilih APK dari sumber yang Anda percaya. Baca dari HP tanpa memasang atau menjalankannya. Tidak ada upload ke AI/server. Bukan dekompilasi, scanner malware, bypass proteksi, atau compiler APK.", style = MaterialTheme.typography.bodyMedium)
            }
            item {
                GradientButton("Pilih APK", Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !state.busy && !state.saving) {
                    picker.launch(arrayOf("*/*"))
                }
                TextButton(onClick = { section = "Ringkasan"; query = ""; vm.inspectSelf() }, enabled = !state.busy && !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Analisis APK RetroBit")
                }
                Text("Satu APK/base APK, maksimum 256 MiB. APKM/XAPK/APKS, ZIP64, ZIP proyek, dan paket terproteksi tertentu tidak didukung. Pembacaan metadata Android dapat membutuhkan waktu; tetap buka aplikasi.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.busy) item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Disalin: ${ProjectFiles.formatBytes(state.copied)} · memeriksa ZIP/metadata Android")
                TextButton(onClick = vm::cancel, modifier = Modifier.heightIn(min = 48.dp)) { Text("Batalkan inspeksi") }
            }
            state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall) } }
            val report = state.report
            if (report != null) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Ringkasan", "Izin", "Sertifikat", "Komponen", "Berkas").forEach { name ->
                            TextButton(onClick = { section = name }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (section == name) "[$name]" else name) }
                        }
                    }
                }
                when (section) {
                    "Ringkasan" -> {
                        item { SelectionContainer { Text("${report.packageName}\nVersi ${report.versionName} (${report.versionCode})\n${report.sourceName}", style = MaterialTheme.typography.titleMedium) } }
                        item { SelectionContainer { Text("SHA-256 APK:\n${report.sha256}\nUkuran ${ProjectFiles.formatBytes(report.bytes)}\nSDK minimum ${report.minSdk} · target ${report.targetSdk}\nDebuggable: ${report.debuggable}\nSplit dilaporkan: ${report.splitName ?: "tidak ada/tidak dilaporkan"}") } }
                        item { Text("${report.entryCount} entry · ${report.dexCount} classes*.dex di root\n${report.resourceCount} entry resource · ${report.assetCount} asset\nABI native: ${report.nativeAbis.joinToString().ifBlank { "tidak ditemukan" }}\nBlok signing modern terdeteksi: ${report.signingBlockPresent}") }
                        item { Text("Sertifikat dilaporkan: ${report.certificates.size}. Buka tab Sertifikat untuk fingerprint. Adanya blok signing/certifikat bukan bukti verifikasi tanda tangan penuh atau aplikasi aman.") }
                    }
                    "Izin" -> {
                        item { Text("${report.permissionCount} permission diminta; menampilkan ${report.permissions.size}. Ini bukan daftar izin yang sudah diberikan atau penilaian malware.") }
                        items(report.permissions) { permission -> SelectionContainer { Text(permission) } }
                    }
                    "Sertifikat" -> {
                        item { Text("Fingerprint SHA-256 DER adalah identitas signer yang dilaporkan Android, bukan bukti keaslian/kepercayaan. Tanggal validitas bukan status aman. Maksimum 16 signer dan 16 riwayat rotasi.") }
                        if (report.certificates.isEmpty()) item { Text("Android tidak melaporkan sertifikat. Ini tidak berarti verifikasi berhasil.") }
                        items(report.certificates) { cert -> SelectionContainer {
                            Text("${if (cert.currentSigner) "Signer saat ini" else "Riwayat rotasi"}\nSHA-256 sertifikat:\n${cert.sha256}\nSubject: ${cert.subject.ifBlank { "tidak terbaca" }}\nIssuer: ${cert.issuer.ifBlank { "tidak terbaca" }}\nValid: ${cert.validFrom} → ${cert.validUntil}")
                        } }
                    }
                    "Komponen" -> {
                        item { Text("${report.componentCount} komponen; menampilkan ${report.components.size}. Exported bukan otomatis berarti rentan; intent-filter dan kode tidak dianalisis.") }
                        items(report.components) { component -> SelectionContainer { Text("${component.kind}: ${component.name}\nexported=${component.exported} · enabled=${component.enabled}") } }
                    }
                    "Berkas" -> {
                        item {
                            OutlinedTextField(query, { query = it.take(120) }, label = { Text("Cari nama berkas APK") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Text("Menampilkan paling banyak ${report.entries.size} dari ${report.entryCount} entry. Filter hanya berlaku pada daftar ini. res/ dan resources.arsc dicatat, bukan diubah menjadi XML/source asli.", style = MaterialTheme.typography.bodySmall)
                        }
                        items(report.entries.filter { it.path.contains(query, ignoreCase = true) }) { entry -> SelectionContainer { Text("${entry.path}\n${ProjectFiles.formatBytes(entry.bytes)} · terkompresi ${ProjectFiles.formatBytes(entry.compressedBytes)}", style = MaterialTheme.typography.bodySmall) } }
                    }
                }
                item {
                    HorizontalDivider()
                    TextButton(onClick = { clipboard.setText(AnnotatedString(report.summary())) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Salin ringkasan") }
                    TextButton(onClick = { pendingExport = report; exporter.launch("retrobit-apk-report.json") }, enabled = !state.saving && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (state.saving) "Menyimpan…" else "Simpan laporan JSON") }
                    TextButton(onClick = vm::clear, enabled = !state.saving && !state.busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("Hapus laporan dari layar") }
                }
            }
        }
    }
    }
}

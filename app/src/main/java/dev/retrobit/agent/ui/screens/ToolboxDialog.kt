package dev.retrobit.agent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.retrobit.agent.agent.AgentToolRunner

@Composable
fun ToolboxDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
        title = { Text("Tool agen proyek (${AgentToolRunner.DEFINITIONS.size})") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Buat proyek dari Beranda/template, lalu kirim tugas di Chat proyek. Agen cloud dan offline memakai tool yang sama. Model kecil mungkin perlu tugas pendek dan konteks 4096.")
                Text("Perubahan file dicatat di langkah agen. Snapshot file sebelum sesi bisa dipulihkan lewat menu; snapshot tidak mencakup database. Tidak ada shell/root, pembacaan nilai secret, atau compiler APK.")
                AgentToolRunner.DEFINITIONS.forEach { tool ->
                    Text(tool.name, style = MaterialTheme.typography.labelLarge)
                    Text(tool.description, style = MaterialTheme.typography.bodySmall)
                }
                Text("Preview dan ekspor ZIP dilakukan dari tab/menu proyek. validate_project hanya cek struktur/JSON, bukan tes runtime atau build APK.")
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Tutup tool") } },
    )
}

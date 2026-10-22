package dev.retrobit.agent.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Private, non-backed-up storage; imported paths never come from a model or filename. */
class LocalModelStore(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "local-models").apply { mkdirs() }
    private val file = File(directory, "model.gguf")
    private val preferences = context.getSharedPreferences("local_model", Context.MODE_PRIVATE)

    init {
        // A killed process may leave a large unfinished copy. It is never the
        // active model and can be reclaimed safely on the next app startup.
        File(directory, "import.partial").delete()
        LocalInference.modelFile = file
    }

    val ready: Boolean get() = file.isFile
    val label: String get() = if (ready) preferences.getString("name", "Model GGUF").orEmpty() else "Belum ada model"
    val sizeBytes: Long get() = if (ready) file.length() else 0L

    suspend fun import(uri: Uri, progress: suspend (Long) -> Unit) = withContext(Dispatchers.IO) {
        LocalInference.gate.withLock {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }.getOrNull()?.take(120) ?: "Model GGUF"
            val partial = File(directory, "import.partial")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(partial).use { output ->
                        var last = 0L
                        GgufImport.copy(input, output) { copied ->
                            currentCoroutineContext().ensureActive()
                            require(directory.usableSpace > 64L * 1024 * 1024 + 256 * 1024) { "Penyimpanan hampir penuh. Kosongkan ruang sebelum impor." }
                            if (copied - last >= 16 * 1024 * 1024) { progress(copied); last = copied }
                        }
                        output.fd.sync()
                    }
                } ?: error("Tidak bisa membaca berkas yang dipilih.")
                currentCoroutineContext().ensureActive()
                // Same filesystem: a failed/cancelled copy never destroys the previous model.
                Files.move(partial.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                preferences.edit().putString("name", name).apply()
                progress(file.length())
            } finally {
                partial.delete()
            }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        LocalInference.gate.withLock {
            check(!file.exists() || file.delete()) { "Berkas model tidak bisa dihapus." }
            preferences.edit().clear().apply()
        }
    }
}

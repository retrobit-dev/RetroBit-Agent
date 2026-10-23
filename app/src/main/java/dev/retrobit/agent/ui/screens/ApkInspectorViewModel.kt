package dev.retrobit.agent.ui.screens

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.retrobit.agent.apk.ApkInspector
import dev.retrobit.agent.apk.ApkReport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ApkInspectorState(val busy: Boolean = false, val copied: Long = 0, val report: ApkReport? = null, val message: String? = null, val saving: Boolean = false)

class ApkInspectorViewModel(private val context: Context) : ViewModel() {
    private val inspector = ApkInspector(context.applicationContext)
    private val mutable = MutableStateFlow(ApkInspectorState())
    val state = mutable.asStateFlow()
    private var job: Job? = null

    fun inspect(uri: Uri) = start { progress -> inspector.inspect(uri, progress) }
    fun inspectSelf() = start { progress -> inspector.inspectSelf(progress) }
    private fun start(action: suspend (suspend (Long) -> Unit) -> ApkReport) {
        if (mutable.value.busy || mutable.value.saving) return
        mutable.value = ApkInspectorState(busy = true, message = "Menyalin dan membaca metadata lokal…")
        job = viewModelScope.launch {
            try {
                val report = action { bytes -> mutable.update { it.copy(copied = bytes) } }
                mutable.update { it.copy(report = report, message = "Selesai. Salinan sementara dihapus; APK asli tidak diubah.") }
            } catch (e: CancellationException) {
                mutable.update { it.copy(message = "Dibatalkan; salinan sementara dibersihkan.") }
                throw e
            } catch (e: Exception) {
                mutable.update { it.copy(message = e.message ?: "APK tidak dapat dianalisis.") }
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun cancel() {
        if (!mutable.value.busy) return
        mutable.update { it.copy(message = "Meminta pembatalan… I/O provider/parsing Android yang berjalan perlu selesai dahulu.") }
        job?.cancel()
    }
    fun clear() { if (!mutable.value.busy && !mutable.value.saving) mutable.value = ApkInspectorState() }

    fun export(uri: Uri, report: ApkReport) {
        if (mutable.value.busy || mutable.value.saving) return
        mutable.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val text = report.json()
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("Tujuan laporan tidak dapat ditulis.")
                }
                mutable.update { it.copy(message = "Laporan JSON disimpan. APK tidak disertakan dalam laporan.") }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                mutable.update { it.copy(message = "Gagal menyimpan: ${e.message}. File tujuan mungkin parsial.") }
            } finally { mutable.update { it.copy(saving = false) } }
        }
    }
}

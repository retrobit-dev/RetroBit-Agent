package dev.retrobit.agent.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.time.Instant

@Serializable
data class ApkCertificate(val sha256: String, val subject: String, val issuer: String, val validFrom: String, val validUntil: String, val currentSigner: Boolean)
@Serializable
data class ApkComponent(val kind: String, val name: String, val exported: Boolean, val enabled: Boolean)
@Serializable
data class ApkReport(
    val sourceName: String, val bytes: Long, val sha256: String,
    val packageName: String, val versionName: String, val versionCode: Long,
    val minSdk: Int, val targetSdk: Int, val debuggable: Boolean,
    val splitName: String?, val certificates: List<ApkCertificate>,
    val permissions: List<String>, val permissionCount: Int,
    val components: List<ApkComponent>, val componentCount: Int,
    val entries: List<ApkEntry>, val entryCount: Int, val declaredUncompressedBytes: Long,
    val dexCount: Int, val resourceCount: Int, val assetCount: Int, val nativeAbis: List<String>,
    val signingBlockPresent: Boolean,
    val schemaVersion: Int = 1,
    val scope: String = "Inspeksi statis satu APK; bukan dekompilasi, bukti keamanan, verifikasi tanda tangan penuh, atau build APK. Sertifikat adalah metadata yang dilaporkan Android. Permission diminta belum tentu diberikan. Listing/komponen/permission dapat dipotong sesuai batas.",
) {
    fun json(): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(this)
    fun summary(): String = buildString {
        appendLine("INSPEKSI APK — bukan bukti keamanan/keaslian")
        appendLine("$sourceName\n$packageName\nVersi $versionName ($versionCode)\n$bytes byte\nSHA-256 APK: $sha256")
        appendLine("SDK $minSdk → $targetSdk; debuggable=$debuggable; split=${splitName ?: "base/tidak dilaporkan"}")
        appendLine("$entryCount entry; $dexCount DEX; $resourceCount resource; $assetCount asset; ABI: ${nativeAbis.joinToString()}")
        certificates.forEach { appendLine("Sertifikat ${if (it.currentSigner) "saat ini" else "riwayat"}: ${it.sha256}") }
        appendLine("Permission diminta: $permissionCount; komponen: $componentCount")
        permissions.take(40).forEach { appendLine(it) }
        append(scope)
    }
}

internal class ApkInspector(
    private val context: Context,
    private val readPackage: (File) -> PackageInfo? = { archiveInfo(context, it) },
) {
    private val directory = File(context.noBackupFilesDir, "apk-inspector").apply { mkdirs() }
    init { directory.listFiles()?.filter { it.name.endsWith(".partial.apk") }?.forEach { it.delete() } }

    suspend fun inspect(uri: Uri, progress: suspend (Long) -> Unit): ApkReport = withContext(Dispatchers.IO) {
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.take(160) else null
            }
        }.getOrNull() ?: "APK pilihan"
        context.contentResolver.openInputStream(uri)?.use { analyze(it, name, progress) } ?: error("Berkas APK tidak dapat dibuka.")
    }

    suspend fun inspectSelf(progress: suspend (Long) -> Unit): ApkReport = withContext(Dispatchers.IO) {
        File(context.applicationInfo.sourceDir).inputStream().use { analyze(it, "RetroBit terpasang (base APK)", progress) }
    }

    internal suspend fun analyze(input: InputStream, name: String, progress: suspend (Long) -> Unit): ApkReport {
        val temp = File.createTempFile("inspect-", ".partial.apk", directory)
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            var last = 0L
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n == -1) break
                    if (n == 0) continue
                    bytes += n
                    require(bytes <= ApkArchiveReader.MAX_BYTES) { "APK melebihi batas 256 MiB." }
                    require(directory.usableSpace > 32L * 1024 * 1024) { "Penyimpanan hampir penuh; perlu cadangan 32 MiB." }
                    digest.update(buffer, 0, n); output.write(buffer, 0, n)
                    if (bytes - last >= 1024 * 1024) { progress(bytes); last = bytes }
                }
            }
            progress(bytes)
            val coroutine = currentCoroutineContext()
            coroutine.ensureActive()
            val zip = ApkArchiveReader.scan(temp) { coroutine.ensureActive() }
            coroutine.ensureActive()
            val info = readPackage(temp) ?: error("Android tidak dapat membaca APK ini. APK rusak, terproteksi, atau format tidak didukung.")
            coroutine.ensureActive()
            val app = info.applicationInfo ?: error("Metadata aplikasi tidak ditemukan.")
            val componentCount = (info.activities?.size ?: 0) + (info.services?.size ?: 0) + (info.receivers?.size ?: 0) + (info.providers?.size ?: 0)
            val components = buildList<ApkComponent> {
                fun addSome(kind: String, array: Array<out android.content.pm.ComponentInfo>?) {
                    array.orEmpty().take(200 - size).forEach { add(ApkComponent(kind, it.name.orEmpty().take(256), it.exported, it.enabled)) }
                }
                addSome("activity", info.activities); addSome("service", info.services)
                addSome("receiver", info.receivers); addSome("provider", info.providers)
            }
            @Suppress("DEPRECATION") val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            ApkReport(
                name.take(160), bytes, digest.digest().hex(), info.packageName.orEmpty().take(256), info.versionName.orEmpty().take(160), code,
                app.minSdkVersion, app.targetSdkVersion, app.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0,
                info.splitNames?.joinToString(",")?.take(256), certificates(info),
                info.requestedPermissions.orEmpty().take(500).map { it.take(256) }.sorted(), info.requestedPermissions?.size ?: 0,
                components, componentCount,
                zip.entries, zip.totalEntries, zip.declaredBytes, zip.dexCount, zip.resourceCount, zip.assetCount, zip.nativeAbis, zip.signingBlockPresent,
            )
        } finally {
            check(!temp.exists() || temp.delete()) { "Salinan sementara APK gagal dihapus. Buka ulang inspeksi untuk mencoba pembersihan lagi." }
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        private fun archiveInfo(context: Context, file: File): PackageInfo? {
            val signatures = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
            return context.packageManager.getPackageArchiveInfo(file.absolutePath, signatures or flags)
                ?: context.packageManager.getPackageArchiveInfo(file.absolutePath, flags)
        }
        @Suppress("DEPRECATION")
        private fun certificates(info: PackageInfo): List<ApkCertificate> {
            val current = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners.orEmpty().toList() else info.signatures.orEmpty().toList()
            val history = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.signingCertificateHistory.orEmpty().toList() else emptyList()
            require(current.size <= 16 && history.size <= 16) { "Jumlah sertifikat melampaui batas inspeksi." }
            return (current.map { it to true } + history.map { it to false }).map { (signature, currentSigner) ->
                val raw = signature.toByteArray()
                require(raw.size <= 64 * 1024) { "Sertifikat APK terlalu besar." }
                val hash = MessageDigest.getInstance("SHA-256").digest(raw).hex()
                val cert = runCatching { CertificateFactory.getInstance("X.509").generateCertificate(raw.inputStream()) as X509Certificate }.getOrNull()
                ApkCertificate(hash, cert?.subjectX500Principal?.name.orEmpty().take(256), cert?.issuerX500Principal?.name.orEmpty().take(256),
                    cert?.notBefore?.let { Instant.ofEpochMilli(it.time).toString() }.orEmpty(), cert?.notAfter?.let { Instant.ofEpochMilli(it.time).toString() }.orEmpty(), currentSigner)
            }.distinctBy { it.sha256 }
        }
        private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

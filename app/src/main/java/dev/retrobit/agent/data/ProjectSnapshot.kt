package dev.retrobit.agent.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.CRC32

/** Stage and fully validate before touching live sources. Metadata/SQLite are never restored. */
internal object ProjectSnapshot {
    @Serializable internal data class Journal(val originals: List<String>, val replacements: List<String>)

    /** Called under the shared source lock when a project is reopened. */
    fun recover(files: ProjectFiles) {
        File(files.root, ProjectFiles.META_DIR).listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("restore-") }.forEach { transaction ->
                runCatching {
                    val journalFile = File(transaction, "journal.json")
                    if (File(transaction, "complete").isFile || !journalFile.exists()) {
                        check(transaction.deleteRecursively()); return@runCatching
                    }
                    require(journalFile.length() <= 1024 * 1024)
                    val journal = Json.decodeFromString<Journal>(journalFile.readText())
                    require((journal.originals + journal.replacements).all {
                        it.isNotEmpty() && it !in listOf(".", "..", ProjectFiles.META_DIR) && '/' !in it && '\\' !in it
                    })
                    for (name in journal.replacements - journal.originals.toSet()) {
                        check(File(files.root, name).deleteRecursively())
                    }
                    for (name in journal.originals) {
                        val old = File(transaction, "previous/$name")
                        if (old.exists()) {
                            check(File(files.root, name).deleteRecursively())
                            Files.move(old.toPath(), File(files.root, name).toPath(), StandardCopyOption.ATOMIC_MOVE)
                        }
                    }
                    check(transaction.deleteRecursively())
                } // If the filesystem is still unavailable, keep every recovery copy.
            }
    }

    data class Limits(val entries: Int = 2_000, val perFile: Long = 32L * 1024 * 1024, val total: Long = 128L * 1024 * 1024)
    fun restore(files: ProjectFiles, snapshot: File, limits: Limits = Limits(),
                move: (File, File) -> Unit = { from, to -> Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE); Unit }): Boolean = synchronized(files.sourceLock) {
        val transaction = files.metaFile("restore-${UUID.randomUUID()}")
        val staged = File(transaction, "staged")
        val backup = File(transaction, "previous")
        val originals = mutableListOf<File>()
        val installed = mutableListOf<File>()
        var safeToClean = true
        try {
            check(staged.mkdirs() && backup.mkdirs())
            val names = hashSetOf<String>()
            var count = 0
            var total = 0L
            var sourceFiles = 0
            require(snapshot.length() in 22..192L * 1024 * 1024) { "Arsip snapshot terlalu besar/tidak lengkap" }
            ZipFile(snapshot).use { zip ->
                require(zip.size() <= limits.entries)
                val entries = zip.entries()
                val buffer = ByteArray(64 * 1024)
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    require(++count <= limits.entries) { "Snapshot memiliki terlalu banyak entry" }
                    val raw = entry.name
                    require(raw.length in 1..512 && !raw.startsWith('/') && '\\' !in raw && ':' !in raw && raw.none { it.code < 32 })
                    val relative = files.resolve(raw).canonicalFile.relativeTo(files.root.canonicalFile).path
                    require(names.add(relative)) { "Path snapshot duplikat" }
                    val target = File(staged, relative)
                    if (entry.isDirectory) {
                        check(target.mkdirs() || target.isDirectory)
                    } else {
                        require(entry.size in 0..limits.perFile)
                        check(target.parentFile!!.mkdirs() || target.parentFile!!.isDirectory)
                        var size = 0L
                        val crc = CRC32()
                        zip.getInputStream(entry).use { input -> target.outputStream().use { out ->
                            while (true) {
                                val n = input.read(buffer)
                                if (n < 0) break
                                size += n; total += n
                                require(size <= limits.perFile && total <= limits.total) { "Snapshot melebihi batas ukuran" }
                                check(staged.usableSpace > 32L * 1024 * 1024) { "Penyimpanan hampir penuh" }
                                crc.update(buffer, 0, n); out.write(buffer, 0, n)
                            }
                        } }
                        require(size == entry.size && crc.value == entry.crc) { "Snapshot rusak: ukuran/CRC tidak cocok" }
                        sourceFiles++
                    }
                }
            }
            require(sourceFiles > 0) { "Snapshot kosong" }
            // Preserve the preview entry before starting the commit, too.
            if (!File(staged, ProjectFiles.ENTRY_POINT).exists()) {
                AtomicFiles.writeText(File(staged, ProjectFiles.ENTRY_POINT), "<!doctype html><html><body>Preview siap</body></html>")
            }
            val oldSources = files.root.listFiles().orEmpty().filter { it.name != ProjectFiles.META_DIR }
            val newSources = staged.listFiles().orEmpty().toList()
            val journal = Json.encodeToString(Journal(oldSources.map { it.name }, newSources.map { it.name }))
            require(journal.toByteArray(Charsets.UTF_8).size <= 1024 * 1024) { "Journal pemulihan terlalu besar" }
            AtomicFiles.writeText(File(transaction, "journal.json"), journal)
            for (old in oldSources) {
                move(old, File(backup, old.name)); originals += old
            }
            for (new in newSources) {
                val destination = File(files.root, new.name)
                move(new, destination); installed += destination
            }
            AtomicFiles.writeText(File(transaction, "complete"), "done")
            true
        } catch (_: Exception) {
            // Best-effort rollback. Keep the recovery directory if any rollback
            // operation fails; never delete the last remaining source copy.
            for (file in installed.asReversed()) if (!file.deleteRecursively()) safeToClean = false
            for (old in originals.asReversed()) {
                try { move(File(backup, old.name), old) } catch (_: Exception) { safeToClean = false }
            }
            false
        } finally {
            if (safeToClean) transaction.deleteRecursively()
        }
    }
}

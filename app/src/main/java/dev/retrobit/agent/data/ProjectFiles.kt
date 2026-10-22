package dev.retrobit.agent.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Virtual file system for a single project. Every path is relative to the project root.
 * The `_meta` folder holds conversation/design metadata and is hidden from the explorer.
 */
class ProjectFiles(val root: File) {

    init {
        if (!root.exists()) root.mkdirs()
    }

    companion object {
        const val META_DIR = "_meta"
        const val ENTRY_POINT = "index.html"

        fun formatBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
        private val IGNORED = setOf(META_DIR, "node_modules", ".git", "dist")
        private val TEXT_EXTENSIONS = setOf(
            "html", "htm", "css", "js", "mjs", "jsx", "ts", "tsx", "json", "md", "txt",
            "svg", "xml", "yml", "yaml", "csv", "sql", "env", "gitignore", "kt", "kts", "gradle", "properties"
        )
    }

    fun resolve(path: String): File {
        val cleaned = path.trim().replace('\\', '/').trimStart('/')
        require(cleaned.isNotEmpty()) { "Path kosong" }
        require(!cleaned.split('/').any { it == ".." }) { "Path tidak valid: $path" }
        require(cleaned != META_DIR && !cleaned.startsWith("$META_DIR/")) { "_meta hanya untuk internal" }
        val file = File(root, cleaned)
        val base = root.canonicalPath + File.separator
        require(file.canonicalPath.startsWith(base)) { "Path keluar dari proyek atau menunjuk root" }
        val relative = file.canonicalFile.relativeTo(root.canonicalFile).path.replace('\\', '/')
        require(relative != META_DIR && !relative.startsWith("$META_DIR/")) { "_meta hanya untuk internal" }
        return file
    }

    fun exists(path: String): Boolean = runCatching { resolve(path).exists() }.getOrDefault(false)

    fun read(path: String): String? = runCatching {
        val f = resolve(path)
        if (f.isFile) f.readText() else null
    }.getOrNull()

    fun write(path: String, content: String): File {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }

    fun append(path: String, content: String) {
        val file = resolve(path)
        file.parentFile?.mkdirs()
        file.appendText(content)
    }

    fun edit(path: String, find: String, replace: String, all: Boolean = false): Boolean {
        val current = read(path) ?: return false
        if (!current.contains(find)) return false
        val next = if (all) current.replace(find, replace) else current.replaceFirst(find, replace)
        write(path, next)
        return true
    }

    fun delete(path: String): Boolean {
        val file = resolve(path)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun rename(path: String, newPath: String): Boolean {
        val from = resolve(path)
        val to = resolve(newPath)
        if (!from.exists() || to.exists()) return false
        to.parentFile?.mkdirs()
        return from.renameTo(to)
    }

    fun ensureEntryPoint(): Boolean {
        if (!resolve(ENTRY_POINT).exists()) {
            write(
                ENTRY_POINT,
                """
                <!doctype html>
                <html lang="id"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>RetroBit Preview</title></head>
                <body style="font-family:system-ui;background:#0B0B12;color:#F5F5FA;display:grid;place-items:center;height:100vh;margin:0">
                <div style="text-align:center">
                  <div style="font-size:40px">🤖</div>
                  <h2>Preview siap</h2>
                  <p style="color:#8C8CA1">Minta AI membuat sesuatu untuk melihat hasilnya di sini.</p>
                </div></body></html>
                """.trimIndent()
            )
            return true
        }
        return false
    }

    fun readEntryPoint(): String = read(ENTRY_POINT) ?: ""

    fun list(path: String = ""): List<FileNode> {
        val dir = if (path.isBlank()) root else resolve(path)
        if (!dir.isDirectory) return emptyList()
        val result = mutableListOf<FileNode>()
        collect(dir, result, 0)
        return result
    }

    private fun collect(dir: File, out: MutableList<FileNode>, depth: Int) {
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: return
        for (f in files) {
            if (f.name in IGNORED || java.nio.file.Files.isSymbolicLink(f.toPath())) continue
            val rel = f.relativeTo(root).path.replace('\\', '/')
            out.add(FileNode(rel, f.name, f.isDirectory, if (f.isFile) f.length() else 0, depth))
            if (f.isDirectory && depth < 6) collect(f, out, depth + 1)
        }
    }

    fun allFiles(): List<File> = root.walkTopDown()
        .onEnter { it == root || (it.name !in IGNORED && !java.nio.file.Files.isSymbolicLink(it.toPath())) }
        .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .toList()

    fun totalBytes(): Long = allFiles().sumOf { it.length() }

    fun fileCount(): Int = allFiles().count { it.relativeTo(root).path.replace('\\', '/').let { p -> p.split('/').none { it in IGNORED } } }

    fun isTextFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in TEXT_EXTENSIONS || ext.isEmpty()
    }

    fun snapshot(maxCharsPerFile: Int = 12000): String {
        val sb = StringBuilder()
        for (file in allFiles().sortedBy { it.path }) {
            val rel = file.relativeTo(root).path.replace('\\', '/')
            if (rel.startsWith("$META_DIR/")) continue
            if (!isTextFile(rel)) {
                sb.appendLine("--- $rel (binary, ${file.length()} bytes) ---")
                continue
            }
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            sb.appendLine("--- $rel ---")
            sb.appendLine(if (text.length > maxCharsPerFile) text.take(maxCharsPerFile) + "\n… (dipotong)" else text)
        }
        return sb.toString()
    }

    fun treeText(): String = buildString {
        for (node in list()) {
            val indent = "  ".repeat(node.depth)
            if (node.isDirectory) appendLine("$indent${node.name}/")
            else appendLine("$indent${node.name} (${formatBytes(node.sizeBytes)})")
        }
    }.ifBlank { "(kosong)" }

    fun exportZip(destination: File): File {
        destination.parentFile?.mkdirs()
        ZipOutputStream(FileOutputStream(destination)).use { zip ->
            for (file in allFiles().sortedBy { it.path }) {
                val rel = file.relativeTo(root).path.replace('\\', '/')
                if (rel.startsWith("$META_DIR/")) continue
                zip.putNextEntry(ZipEntry(rel))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return destination
    }

    fun metaFile(name: String): File {
        val dir = File(root, META_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, name)
    }

    /** Zips current sources into _meta/snapshots, keeping the newest 10. */
    fun createSnapshot(): File? {
        val dir = File(root, "$META_DIR/snapshots")
        if (!dir.exists() && !dir.mkdirs()) return null
        val destination = File(dir, "snapshot-${System.currentTimeMillis()}.zip")
        val created = runCatching { exportZip(destination) }.getOrNull() ?: return null
        if (runCatching { allFiles().isEmpty() }.getOrDefault(true)) {
            runCatching { destination.delete() }
            return null
        }
        dir.listFiles()?.filter { it.extension == "zip" }
            ?.sortedByDescending { it.name }
            ?.drop(10)
            ?.forEach { runCatching { it.delete() } }
        return created
    }

    fun latestSnapshot(): File? = File(root, "$META_DIR/snapshots")
        .listFiles()
        ?.filter { it.extension == "zip" }
        ?.maxByOrNull { it.lastModified() }

    /** Restores a snapshot created by [createSnapshot]. Returns false when the zip is unusable. */
    fun restoreSnapshot(snapshot: File): Boolean {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        runCatching {
            ZipInputStream(snapshot.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    if (!entry.isDirectory && name.isNotBlank() && !name.startsWith("/") && !name.split('/').contains("..")) {
                        entries.add(name to zip.readBytes())
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }.getOrElse { return false }
        if (entries.isEmpty()) return false
        clearSources()
        for ((name, bytes) in entries) {
            runCatching {
                val target = resolve(name)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }
        }
        ensureEntryPoint()
        return true
    }

    fun clear() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    /** Removes generated source files but keeps conversation / metadata. */
    fun clearSources() {
        root.listFiles()?.forEach { if (it.name != META_DIR) it.deleteRecursively() }
    }

}

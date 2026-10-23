package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest

/** Shared boundary for LAN and embedded preview; never serve internal metadata. */
internal class PreviewFiles(private val files: ProjectFiles) {
    val origin: String = "https://p-" + MessageDigest.getInstance("SHA-256")
        .digest(files.root.canonicalPath.toByteArray(Charsets.UTF_8)).take(12)
        .joinToString("") { "%02x".format(it.toInt() and 255) } + ".retrobit.invalid"

    fun isLocal(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.rawAuthority == URI(origin).rawAuthority
    }.getOrDefault(false)

    fun resolve(rawPath: String): File {
        require(rawPath.startsWith('/') && !rawPath.startsWith("//")) { "Path preview tidak valid" }
        // '+' is literal in a path, unlike in application/x-www-form-urlencoded.
        val path = URLDecoder.decode(rawPath.replace("+", "%2B"), "UTF-8")
        require(path.none { it.code < 32 || it.code == 127 } && '\\' !in path) { "Path preview tidak valid" }
        val relative = if (path == "/") ProjectFiles.ENTRY_POINT else path.removePrefix("/")
        require(!relative.startsWith('/')) { "Path preview tidak valid" }
        val target = files.resolve(relative)
        val canonical = target.canonicalFile.relativeTo(files.root.canonicalFile).path
        require(canonical.split(File.separatorChar).none {
            val name = it.lowercase()
            name == ".git" || name == ".env" || name.startsWith(".env.") || name.endsWith(".jks") || name.endsWith(".keystore")
        }) { "Berkas konfigurasi/kunci privat tidak dilayani preview" }
        return target
    }

    data class Asset(val bytes: Long, val stream: InputStream)

    /** Validate the opened descriptor, not a path that an atomic editor can replace. */
    fun open(file: File, maxBytes: Long = MAX_FILE_BYTES): Asset {
        val input = file.inputStream()
        try {
            val size = input.channel.size()
            require(size <= maxBytes) { "File melebihi batas preview" }
            val bounded = object : FilterInputStream(input) {
                private var remaining = size
                override fun read(): Int {
                    if (remaining == 0L) return -1
                    return super.read().also { if (it >= 0) remaining-- }
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (length == 0) return 0
                    if (remaining == 0L) return -1
                    return input.read(buffer, offset, minOf(length.toLong(), remaining).toInt()).also { if (it > 0) remaining -= it }
                }
                override fun skip(n: Long): Long = input.skip(n.coerceIn(0, remaining)).also { remaining -= it }
                override fun available(): Int = minOf(input.available().toLong(), remaining).toInt()
                override fun markSupported() = false
                override fun reset() { throw java.io.IOException("Mark/reset unsupported") }
            }
            return Asset(size, bounded)
        } catch (e: Exception) { input.close(); throw e }
    }

    companion object {
        const val MAX_FILE_BYTES = 64L * 1024 * 1024
        const val MAX_HTML_BYTES = 2L * 1024 * 1024
        // Native bridges are available to all WebView frames, so frames/objects
        // are forbidden. Project JS (including explicitly imported scripts) is trusted.
        const val CSP = "frame-src 'none'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'"
        fun mime(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js", "mjs" -> "application/javascript"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "ico" -> "image/x-icon"
            "woff2" -> "font/woff2"
            "woff" -> "font/woff"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}

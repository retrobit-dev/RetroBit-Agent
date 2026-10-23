package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import java.io.File
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

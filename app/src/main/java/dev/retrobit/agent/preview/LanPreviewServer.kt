package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Tiny read-only HTTP server that exposes a project folder on the local network,
 * so the app can be opened from a phone, tablet or desktop browser.
 */
class LanPreviewServer(
    private val files: ProjectFiles,
    private val port: Int,
    private val onHit: (String, Int) -> Unit = { _, _ -> },
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    val isRunning: Boolean get() = running.get()

    fun start(): Result<String> = runCatching {
        if (running.get()) return@runCatching url()
        val socket = ServerSocket(port)
        serverSocket = socket
        running.set(true)
        worker = thread(name = "retrobit-lan-preview", isDaemon = true) {
            while (running.get()) {
                try {
                    val client = socket.accept()
                    thread(isDaemon = true) { handle(client) }
                } catch (e: IOException) {
                    if (running.get()) onHit("/error", 500)
                    break
                }
            }
        }
        url()
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        worker = null
    }

    fun url(): String = "http://${localIp()}:$port/"

    private fun localIp(): String {
        return runCatching {
            val addresses = NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
            (addresses.firstOrNull { it.isSiteLocalAddress } ?: addresses.firstOrNull())?.hostAddress
        }.getOrNull() ?: "127.0.0.1"
    }

    private fun handle(client: Socket) {
        client.use { socket ->
            socket.soTimeout = 8000
            val input = socket.getInputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            val rawPath = parts[1].substringBefore('?')
            val path = URLDecoder.decode(rawPath, "UTF-8")
            val relative = if (path == "/" || path.isBlank()) ProjectFiles.ENTRY_POINT
            else path.removePrefix("/")

            val target: File? = runCatching {
                if (relative.contains("..")) null else File(files.root, relative).takeIf { it.isFile }
            }.getOrNull() ?: File(files.root, ProjectFiles.ENTRY_POINT).takeIf { it.isFile }

            if (target == null) {
                respond(socket, 404, "text/plain", "404 — proyek belum memiliki index.html".toByteArray())
                onHit(path, 404)
                return
            }
            val mime = mimeType(target.name)
            val bytes = runCatching { target.readBytes() }.getOrNull()
            if (bytes == null) {
                respond(socket, 500, "text/plain", "Gagal membaca file".toByteArray())
                onHit(path, 500)
            } else {
                respond(socket, 200, mime, bytes)
                onHit(if (path == "/") "/index.html" else path, 200)
            }
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == 10) break // '\n'
            if (b != 13) sb.append(b.toChar()) // skip '\r'
            if (sb.length > 4096) return null
        }
        return sb.toString()
    }

    private fun respond(socket: Socket, code: Int, mime: String, body: ByteArray) {
        val out = BufferedOutputStream(socket.getOutputStream())
        val reason = if (code == 200) "OK" else if (code == 404) "Not Found" else "Error"
        val textual = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml")
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $mime")
            if (textual) append("; charset=utf-8")
            append("\r\n")
            append("X-Content-Type-Options: nosniff\r\n")
            append("Content-Length: ${body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Access-Control-Allow-Origin: *\r\n")
            append("Connection: close\r\n\r\n")
        }
        out.write(header.toByteArray(Charsets.UTF_8))
        out.write(body)
        out.flush()
    }

    private fun mimeType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
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

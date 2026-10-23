package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import java.util.concurrent.ConcurrentHashMap
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
    private val policy = PreviewFiles(files)
    private val slots = Semaphore(8)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    @Volatile private var serverSocket: ServerSocket? = null
    private var worker: Thread? = null

    val isRunning: Boolean get() = running.get()

    @Synchronized fun start(): Result<String> = runCatching {
        if (running.get()) return@runCatching url()
        val socket = ServerSocket(port)
        serverSocket = socket
        running.set(true)
        worker = thread(name = "retrobit-lan-preview", isDaemon = true) {
            while (running.get() && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    if (!running.get() || !slots.tryAcquire()) { client.close(); continue }
                    clients.add(client)
                    thread(isDaemon = true, name = "retrobit-lan-client") {
                        try { handle(client) } catch (_: Exception) {
                            // A malformed request or disconnected client must not kill the listener.
                        } finally { clients.remove(client); runCatching { client.close() }; slots.release() }
                    }
                } catch (e: IOException) {
                    if (running.get() && serverSocket === socket) { running.set(false); onHit("/error", 500) }
                    break
                }
            }
        }
        url()
    }

    @Synchronized fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        clients.forEach { runCatching { it.close() } }
        serverSocket = null
        worker = null
    }

    fun url(): String = "http://${localIp()}:${serverSocket?.localPort ?: port}/"

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
            socket.soTimeout = 3000
            val input = socket.getInputStream()
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return
            if (parts.size != 3 || parts[2] !in listOf("HTTP/1.0", "HTTP/1.1")) {
                respond(socket, 400, "text/plain", "Bad request".toByteArray()); return
            }
            val head = parts[0] == "HEAD"
            if (parts[0] != "GET" && !head) {
                respond(socket, 405, "text/plain", "Read-only preview".toByteArray()); return
            }
            val path = parts[1].substringBefore('?')
            val target = runCatching { policy.resolve(path) }.getOrNull()
            if (target == null) {
                respond(socket, 403, "text/plain", "Forbidden".toByteArray(), head); onHit(path, 403); return
            }
            // Missing assets must be 404, not index.html under a misleading MIME type.
            if (!target.isFile) {
                respond(socket, 404, "text/plain", "Not found".toByteArray(), head); onHit(path, 404); return
            }
            if (target.length() > PreviewFiles.MAX_FILE_BYTES) {
                respond(socket, 413, "text/plain", "File exceeds preview limit".toByteArray(), head); onHit(path, 413); return
            }
            val asset = runCatching { policy.open(target) }.getOrNull()
            if (asset == null) {
                respond(socket, 413, "text/plain", "File unavailable or too large".toByteArray(), head); return
            }
            asset.stream.use { source ->
                val size = asset.bytes
                writeHeaders(socket, 200, PreviewFiles.mime(target.name), size)
                if (!head) {
                    val out = socket.getOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    var remaining = size
                    while (remaining > 0) {
                        val n = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) break
                        out.write(buffer, 0, n); remaining -= n
                    }
                    out.flush()
                }
            }
            onHit(path, 200)
        }
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        val deadline = System.nanoTime() + 8_000_000_000L
        while (true) {
            if (System.nanoTime() >= deadline) throw IOException("Request header timeout")
            val b = input.read()
            if (b == -1) return if (sb.isEmpty()) null else sb.toString()
            if (b == 10) break // '\n'
            if (b != 13) sb.append(b.toChar()) // skip '\r'
            if (sb.length > 4096) return null
        }
        return sb.toString()
    }

    private fun respond(socket: Socket, code: Int, mime: String, body: ByteArray, head: Boolean = false) {
        writeHeaders(socket, code, mime, body.size.toLong())
        if (!head) { socket.getOutputStream().write(body); socket.getOutputStream().flush() }
    }

    private fun writeHeaders(socket: Socket, code: Int, mime: String, size: Long) {
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"
            405 -> "Method Not Allowed"; 413 -> "Content Too Large"; else -> "Error"
        }
        val textual = mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json") || mime.contains("xml")
        val header = buildString {
            append("HTTP/1.1 $code $reason\r\n")
            append("Content-Type: $mime")
            if (textual) append("; charset=utf-8")
            append("\r\nX-Content-Type-Options: nosniff\r\n")
            append("Content-Length: $size\r\nCache-Control: no-store\r\n")
            if (code == 405) append("Allow: GET, HEAD\r\n")
            append("Connection: close\r\n\r\n")
        }
        socket.getOutputStream().apply { write(header.toByteArray(Charsets.UTF_8)); flush() }
    }
}

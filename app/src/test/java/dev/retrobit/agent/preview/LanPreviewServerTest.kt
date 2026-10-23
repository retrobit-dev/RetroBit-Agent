package dev.retrobit.agent.preview

import dev.retrobit.agent.data.ProjectFiles
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.net.Socket
import java.net.URI
import java.nio.file.Files

class LanPreviewServerTest {
    @get:Rule val temp = TemporaryFolder()
    private lateinit var files: ProjectFiles
    private lateinit var server: LanPreviewServer
    private var port = 0
    @Before fun setup() {
        files = ProjectFiles(temp.newFolder())
        files.write("index.html", "<h1>safe preview</h1>")
        files.metaFile("chat.json").writeText("private conversation")
        server = LanPreviewServer(files, 0)
        port = URI(server.start().getOrThrow()).port
    }
    @After fun stop() { server.stop() }
    private fun request(path: String, method: String = "GET"): String = Socket("127.0.0.1", port).use {
        it.soTimeout = 3000
        it.getOutputStream().write("$method $path HTTP/1.1\r\nHost: test\r\n\r\n".toByteArray())
        it.getInputStream().readBytes().toString(Charsets.UTF_8)
    }
    @Test fun `GET streams sources with correct MIME and no wildcard CORS`() {
        files.write("assets/app.js", "console.log('ok')")
        val response = request("/assets/app.js")
        assertTrue(response.startsWith("HTTP/1.1 200")); assertTrue(response.endsWith("console.log('ok')"))
        assertTrue(response.contains("application/javascript")); assertFalse(response.contains("Access-Control-Allow-Origin: *"))
    }
    @Test fun `metadata traversal and malformed encoding are forbidden and listener survives`() {
        listOf("/_meta/chat.json", "/%5fmeta/chat.json", "/%2e%2e/private", "/%ZZ", "/%2f_meta/chat.json").forEach {
            val response = request(it)
            assertTrue(it, response.startsWith("HTTP/1.1 403")); assertFalse(response.contains("private conversation"))
        }
        assertTrue(request("/").contains("safe preview"))
    }
    @Test fun `symlink sibling cannot be read over LAN`() {
        val outside = temp.newFolder(); File(outside, "secret").writeText("private sibling")
        Files.createSymbolicLink(File(files.root, "link").toPath(), outside.toPath())
        val response = request("/link/secret")
        assertTrue(response.startsWith("HTTP/1.1 403")); assertFalse(response.contains("private sibling"))
    }
    @Test fun `unknown assets are 404 not SPA HTML fallback`() {
        assertTrue(request("/missing.js").startsWith("HTTP/1.1 404"))
    }
    @Test fun `HEAD returns length but no body and POST is rejected`() {
        val response = request("/", "HEAD")
        assertTrue(response.startsWith("HTTP/1.1 200")); assertTrue(response.endsWith("\r\n\r\n"))
        assertTrue(request("/", "POST").startsWith("HTTP/1.1 405"))
    }
    @Test fun `oversize file rejected before reading into memory`() {
        RandomAccessFile(File(files.root, "large.bin"), "rw").use { it.setLength(PreviewFiles.MAX_FILE_BYTES + 1) }
        assertTrue(request("/large.bin").startsWith("HTTP/1.1 413"))
    }
    @Test fun `stop releases port and restart accepts new requests`() {
        server.stop(); assertFalse(server.isRunning)
        port = URI(server.start().getOrThrow()).port
        assertTrue(request("/").contains("safe preview")); assertTrue(server.isRunning)
    }
}

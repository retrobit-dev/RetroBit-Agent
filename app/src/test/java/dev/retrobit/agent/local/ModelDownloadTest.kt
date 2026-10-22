package dev.retrobit.agent.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.InetAddress

class ModelDownloadTest {
    private lateinit var server: MockWebServer
    private lateinit var downloader: ModelDownloader
    private val url = "https://models.example/model.gguf"
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        // Only test traffic is rewritten onto a local HTTP server. All URLs
        // visible to the downloader/redirect policy remain public HTTPS.
        val client = OkHttpClient.Builder().followRedirects(false).addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
        downloader = ModelDownloader(client)
    }
    @After fun cleanup() { server.shutdown() }
    @Test fun `HTTPS recommendation accepted`() { assertEquals("huggingface.co", ModelDownloadPolicy.url(ModelDownloadPolicy.RECOMMENDED_URL).host) }
    @Test fun `cleartext credential local host and alternate port rejected`() {
        listOf("http://models.example/a", "https://user:pass@models.example/a", "https://localhost/a", "https://box.local/a", "https://models.example:8443/a").forEach {
            assertTrue(it, runCatching { ModelDownloadPolicy.url(it) }.isFailure)
        }
    }
    @Test fun `private and special addresses rejected by actual resolver policy`() {
        listOf("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.1.1", "100.64.0.1", "0.0.0.0", "::1", "fc00::1", "fe80::1", "ff02::1").forEach {
            assertFalse(it, ModelDownloadPolicy.publicAddress(InetAddress.getByName(it)))
        }
        assertTrue(ModelDownloadPolicy.publicAddress(InetAddress.getByName("1.1.1.1")))
        assertTrue(ModelDownloadPolicy.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun `checksum optional but strictly validated`() {
        assertNull(ModelDownloadPolicy.checksum(" "))
        assertEquals("a".repeat(64), ModelDownloadPolicy.checksum("A".repeat(64)))
        assertTrue(runCatching { ModelDownloadPolicy.checksum("123") }.isFailure)
    }
    @Test fun `200 body is consumed once with length and label`() = runBlocking {
        server.enqueue(MockResponse().setBody("abc"))
        val result = downloader.download(url) { input, length, name ->
            assertEquals(3L, length); assertEquals("model.gguf", name); input.readBytes().decodeToString()
        }
        assertEquals("abc", result); assertEquals("/model.gguf", server.takeRequest().path)
    }
    @Test fun `HTTPS redirect followed manually`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "https://cdn.example/real.gguf"))
        server.enqueue(MockResponse().setBody("ok"))
        assertEquals("ok", downloader.download(url) { input, _, _ -> input.readBytes().decodeToString() })
        assertEquals(2, server.requestCount)
    }
    @Test fun `redirect to HTTP rejected without requesting it`() {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://cdn.example/file.gguf"))
        assertTrue(runCatching { runBlocking { downloader.download(url) { _, _, _ -> Unit } } }.isFailure)
        assertEquals(1, server.requestCount)
    }
    @Test fun `redirect loop bounded`() {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", url)) }
        assertTrue(runCatching { runBlocking { downloader.download(url) { _, _, _ -> Unit } } }.isFailure)
        assertEquals(6, server.requestCount)
    }
    @Test fun `404 or partial response never reaches installer`() {
        for (code in listOf(404, 206, 403)) {
            server.enqueue(MockResponse().setResponseCode(code))
            var installed = false
            assertTrue(runCatching { runBlocking { downloader.download(url) { _, _, _ -> installed = true } } }.isFailure)
            assertFalse(installed)
        }
    }
    @Test fun `oversize declared length rejected before body copy`() {
        server.enqueue(MockResponse().setBody("x").setHeader("Content-Length", GgufImport.MAX_BYTES + 1))
        var consumed = false
        assertTrue(runCatching { runBlocking { downloader.download(url) { _, _, _ -> consumed = true } } }.isFailure)
        assertFalse(consumed)
    }
    @Test fun `cancellation preserved and response closed`() {
        server.enqueue(MockResponse().setBody("x"))
        val result = runCatching { runBlocking { downloader.download(url) { _, _, _ -> throw CancellationException("cancel") } } }
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}

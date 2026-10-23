package dev.retrobit.agent.local

import dev.retrobit.agent.ai.executeAsync
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.net.UnknownHostException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Only direct HTTPS model downloads: no credentials, cleartext redirects or local servers. */
internal object ModelDownloadPolicy {
    const val RECOMMENDED_URL = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf?download=true"
    fun url(raw: String): HttpUrl {
        val url = raw.trim().toHttpUrlOrNull() ?: error("URL download tidak valid.")
        require(url.isHttps) { "Download model wajib HTTPS." }
        require(url.username.isEmpty() && url.password.isEmpty()) { "URL tidak boleh berisi username/password." }
        require(url.port == 443) { "Gunakan HTTPS port 443." }
        require(url.host != "localhost" && !url.host.endsWith(".localhost") && !url.host.endsWith(".local")) { "URL harus menuju server publik." }
        return url.newBuilder().fragment(null).build()
    }
    fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        return if (b.size == 4) {
            b[0] != 0 && b[0] != 127 && b[0] < 224 &&
                !(b[0] == 100 && b[1] in 64..127) && !(b[0] == 169 && b[1] == 254) &&
                !(b[0] == 198 && b[1] in 18..19)
        } else {
            // Reject unique-local IPv6 and IPv4-mapped addresses as well.
            (b[0] and 0xe0) == 0x20 && !(b[0] == 0x20 && b[1] == 0x02) && !(b.take(4) == listOf(0x20, 0x01, 0x0d, 0xb8))
        }
    }
    fun checksum(raw: String): String? = raw.trim().lowercase().takeIf { it.isNotEmpty() }?.also {
        require(it.matches(Regex("[0-9a-f]{64}"))) { "SHA-256 harus 64 karakter heksadesimal, atau kosong." }
    }
}

internal class ModelDownloader(
    // Tests inject a MockWebServer client. Production uses a DNS resolver that
    // validates every actual resolution, including redirects (not a preflight lookup).
    private val http: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false).followSslRedirects(false)
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = InetAddress.getAllByName(hostname).toList().also { addresses ->
                if (addresses.isEmpty() || !addresses.all(ModelDownloadPolicy::publicAddress)) throw UnknownHostException("Alamat download bukan server publik.")
            }
        })
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES).build(),
) {
    suspend fun <T> download(rawUrl: String, consume: suspend (InputStream, Long, String) -> T): T {
        var url = ModelDownloadPolicy.url(rawUrl)
        repeat(6) { attempt ->
            currentCoroutineContext().ensureActive()
            http.executeAsync(Request.Builder().url(url).get().build()).use { response ->
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    require(attempt < 5) { "Terlalu banyak redirect download." }
                    val next = response.header("Location")?.let { url.resolve(it) } ?: error("Redirect download tidak valid.")
                    url = ModelDownloadPolicy.url(next.toString())
                } else {
                    require(response.code == 200) { "Download gagal (HTTP ${response.code}). Gunakan tautan langsung GGUF publik, bukan halaman login." }
                    val body = response.body ?: error("Server tidak mengirim berkas.")
                    val size = body.contentLength()
                    require(size <= GgufImport.MAX_BYTES) { "Model melebihi batas 3 GiB." }
                    val name = url.pathSegments.lastOrNull()?.takeIf { it.endsWith(".gguf", ignoreCase = true) }
                        ?: ModelDownloadPolicy.url(rawUrl).pathSegments.lastOrNull()?.takeIf { it.endsWith(".gguf", ignoreCase = true) }
                        ?: "Model download.gguf"
                    return body.byteStream().use { consume(it, size, name) }
                }
            }
        }
        error("Terlalu banyak redirect download.")
    }
}

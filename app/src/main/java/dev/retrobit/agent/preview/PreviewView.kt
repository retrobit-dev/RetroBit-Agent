package dev.retrobit.agent.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.retrobit.agent.data.DatabaseManager
import dev.retrobit.agent.data.DesignSystem
import dev.retrobit.agent.data.ProjectFiles
import dev.retrobit.agent.ui.components.Pill
import dev.retrobit.agent.ui.theme.MonoFont
import dev.retrobit.agent.ui.theme.Palette
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import dev.retrobit.agent.ui.icons.PixelIcons

data class ConsoleEntry(
    val level: String,
    val message: String,
    val source: String = "",
    val line: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
) {
    val isError: Boolean get() = level.equals("ERROR", true) || level.equals("SEVERE", true)
}

enum class PreviewDevice(val label: String, val widthDp: Int?) {
    PHONE("HP", 390),
    TABLET("Tablet", 768),
    DESKTOP("Penuh", null),
}

class PreviewBridge(
    private val databaseProvider: () -> DatabaseManager?,
    private val secretsProvider: () -> Map<String, String>,
    private val onConsole: (String, String) -> Unit,
) {
    private val json = Json { prettyPrint = false }

    @JavascriptInterface
    fun query(sql: String, argsJson: String?): String {
        val db = databaseProvider() ?: return """{"columns":[],"rows":[],"error":"Database belum siap","message":"","changes":0}"""
        return runCatching { db.queryAsJsonForBridge(sql, argsJson) }
            .getOrElse { """{"columns":[],"rows":[],"error":${JsonPrimitive(it.message ?: "query error")},"message":"","changes":0}""" }
    }

    @JavascriptInterface
    fun env(): String = runCatching { json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), buildJsonObject { secretsProvider().forEach { (k, v) -> put(k, JsonPrimitive(v)) } }) }
        .getOrElse { "{}" }

    @JavascriptInterface
    fun log(level: String, message: String) {
        onConsole(level.uppercase(), message.take(4000))
    }

    @JavascriptInterface
    fun isPreview(): Boolean = true
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun PreviewPane(
    filesRoot: File,
    design: DesignSystem,
    exposedSecrets: Map<String, String>,
    database: DatabaseManager?,
    reloadSignal: Any,
    modifier: Modifier = Modifier,
    onConsole: (ConsoleEntry) -> Unit,
) {
    val context = LocalContext.current
    val webRef = remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    val policy = remember(filesRoot) { PreviewFiles(ProjectFiles(filesRoot)) }
    val latestPolicy by rememberUpdatedState(policy)

    val liveConfig = remember { mutableStateOf(design to exposedSecrets) }
    liveConfig.value = design to exposedSecrets

    // The WebView registers its bridge once; keep the callbacks pointing at the
    // latest composition state instead of capturing stale values.
    val latestDatabase by rememberUpdatedState(database)
    val latestSecrets by rememberUpdatedState(exposedSecrets)
    val latestConsole by rememberUpdatedState(onConsole)
    val bridge = remember {
        PreviewBridge(
            databaseProvider = { latestDatabase },
            secretsProvider = { latestSecrets },
            onConsole = { level, message -> latestConsole(ConsoleEntry(level, message)) },
        )
    }

    Box(modifier = modifier.clip(RoundedCornerShape(5.dp)).background(Palette.Bg)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = false
                        allowContentAccess = false
                        // A per-project HTTPS virtual origin serves only validated project
                        // paths. No file:// access to app-private storage is needed.
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        textZoom = 100
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    addJavascriptInterface(bridge, "RetroBitNative")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            latestConsole(
                                ConsoleEntry(
                                    level = msg.messageLevel().name,
                                    message = msg.message(),
                                    source = msg.sourceId().orEmpty().substringAfterLast('/'),
                                    line = msg.lineNumber(),
                                )
                            )
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val uri = request?.url ?: return null
                            val boundary = latestPolicy
                            if (!boundary.isLocal(uri.toString())) {
                                // Never let remote top-level documents acquire the native bridge.
                                if (request.isForMainFrame || uri.scheme in listOf("file", "content")) return previewError(403)
                                return null // External HTTPS assets use ordinary browser CORS.
                            }
                            if (request.method != "GET") return previewError(405)
                            return runCatching {
                                val target = boundary.resolve(uri.encodedPath.orEmpty())
                                if (!target.isFile) return previewError(404)
                                val mime = PreviewFiles.mime(target.name)
                                val max = if (mime == "text/html") PreviewFiles.MAX_HTML_BYTES else PreviewFiles.MAX_FILE_BYTES
                                if (target.length() > max) return previewError(413)
                                val stream = if (mime == "text/html") {
                                    val (currentDesign, currentSecrets) = liveConfig.value
                                    val source = boundary.open(target, max).stream.use { input ->
                                        val bytes = input.readNBytesBounded(max.toInt())
                                        bytes.toString(Charsets.UTF_8)
                                    }
                                    PreviewInjector.inject(source, currentDesign, currentSecrets).byteInputStream(Charsets.UTF_8)
                                } else boundary.open(target, max).stream
                                WebResourceResponse(mime, "UTF-8", 200, "OK", mapOf(
                                    "Content-Security-Policy" to PreviewFiles.CSP,
                                    "X-Content-Type-Options" to "nosniff", "Cache-Control" to "no-store",
                                ), stream)
                            }.getOrElse { previewError(403) }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            if (url != null && latestPolicy.isLocal(url)) {
                                val (currentDesign, currentSecrets) = liveConfig.value
                                view?.evaluateJavascript(PreviewInjector.runtimePatch(currentDesign, currentSecrets), null)
                                val origin = latestPolicy.origin
                                // No HTML, secret, SQL result or filesystem path is logged.
                                // UIAutomator does not reliably expose WebView DOM text.
                                val ready = """(function(){return location.origin === ${JsonPrimitive(origin)} && document.readyState === 'complete' && !!document.body && document.body.innerText.trim().length > 0 && window.__RETROBIT_NATIVE__ === true;})()"""
                                view?.evaluateJavascript(ready) { result ->
                                    if (result == "true" && latestPolicy.origin == origin) {
                                        android.util.Log.i("RetroBitPreview", "ready virtual-origin bridge=1 content=1")
                                    }
                                }
                            }
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                loading = false
                                latestConsole(ConsoleEntry("ERROR", "Halaman gagal dimuat: ${error?.description}", line = 0))
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return false
                            if (latestPolicy.isLocal(url.toString())) return false
                            if (request.isForMainFrame && url.scheme in listOf("https", "http", "mailto", "tel")) {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                            }
                            return true
                        }
                    }
                    webRef.value = this
                }
            },
            update = { webRef.value = it },
        )

        LaunchedEffect(reloadSignal, filesRoot, webRef.value) {
            val web = webRef.value ?: return@LaunchedEffect
            hasError = false
            loading = true
            web.loadUrl("${policy.origin}/index.html")
        }

        // Design + secret changes are hot-patched instead of reloading the page,
        // so input state and scroll position survive while editing the design tab.
        LaunchedEffect(design, exposedSecrets, webRef.value) {
            val web = webRef.value ?: return@LaunchedEffect
            if (!loading) {
                web.evaluateJavascript(PreviewInjector.runtimePatch(design, exposedSecrets), null)
            }
        }

        AnimatedVisibility(visible = loading, modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Palette.Accent.copy(alpha = 0.25f))
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.height(2.dp).fillMaxWidth(),
                    color = Palette.Accent,
                    trackColor = Color.Transparent,
                )
            }
        }
        if (hasError) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Preview gagal dimuat", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Periksa index.html atau lihat console.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webRef.value?.let { web ->
                runCatching {
                    web.stopLoading()
                    web.removeJavascriptInterface("RetroBitNative")
                    web.destroy()
                }
            }
            webRef.value = null
        }
    }
}

private fun previewError(code: Int): WebResourceResponse = WebResourceResponse(
    "text/plain", "UTF-8", code, "Preview blocked", mapOf("Cache-Control" to "no-store"),
    "Preview resource unavailable ($code)".byteInputStream(),
)

private fun java.io.InputStream.readNBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(output.size().toLong() + n <= limit) { "HTML preview terlalu besar" }
        output.write(buffer, 0, n)
    }
    return output.toByteArray()
}

@Composable
fun PreviewToolbar(
    device: PreviewDevice,
    onDeviceChange: (PreviewDevice) -> Unit,
    onReload: () -> Unit,
    onOpenExternal: (() -> Unit)? = null,
    errorCount: Int,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PreviewDevice.entries.forEach { preset ->
                val selected = preset == device
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                        .clickable { onDeviceChange(preset) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        preset.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        if (errorCount > 0) {
            Box(Modifier.clip(CircleShape)) {
                Pill("$errorCount error", color = Palette.Danger, background = Palette.Danger.copy(alpha = 0.14f), dot = true)
            }
        }
        IconButton(onClick = onOpenConsole, modifier = Modifier.size(34.dp)) {
            Text(">_", color = Palette.Accent, fontFamily = MonoFont, fontSize = 12.sp)
        }
        IconButton(onClick = onReload, modifier = Modifier.size(34.dp)) {
            Icon(PixelIcons.Reload, contentDescription = "Muat ulang", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (onOpenExternal != null) {
            IconButton(onClick = onOpenExternal, modifier = Modifier.size(34.dp)) {
                Icon(PixelIcons.Browser, contentDescription = "Buka di browser", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

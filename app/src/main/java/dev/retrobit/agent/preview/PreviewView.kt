package dev.retrobit.agent.preview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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

@SuppressLint("SetJavaScriptEnabled")
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
    val html by remember(filesRoot, design, exposedSecrets, reloadSignal) {
        mutableStateOf(readHtml(filesRoot))
    }

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

    Box(modifier = modifier.clip(RoundedCornerShape(5.dp)).background(Color(0xFF08080E))) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        allowFileAccess = true
                        allowContentAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        textZoom = 100
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    addJavascriptInterface(bridge, "RetroBitNative")
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            onConsole(
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
                        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                            loading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            loading = false
                            val (currentDesign, currentSecrets) = liveConfig.value
                            view?.evaluateJavascript(PreviewInjector.runtimePatch(currentDesign, currentSecrets), null)
                        }

                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                            if (request?.isForMainFrame == true) {
                                hasError = true
                                loading = false
                                onConsole(ConsoleEntry("ERROR", "Halaman gagal dimuat: ${error?.description}", line = 0))
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url ?: return false
                            return when {
                                url.scheme == "file" -> false
                                url.scheme == "about" -> false
                                url.scheme == "data" -> false
                                else -> {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url.toString())))
                                    }
                                    true
                                }
                            }
                        }
                    }
                    webRef.value = this
                }
            },
            update = { webRef.value = it },
        )

        LaunchedEffect(html, reloadSignal, filesRoot, webRef.value) {
            val web = webRef.value ?: return@LaunchedEffect
            val base = "file://${filesRoot.absolutePath.trimEnd('/')}/"
            val source = html.ifBlank { "<html><body style='font-family:sans-serif;color:#8D8DA3;background:#0B0B12;padding:32px'>Belum ada index.html</body></html>" }
            val injected = PreviewInjector.inject(source, design, exposedSecrets)
            hasError = false
            loading = true
            web.loadDataWithBaseURL(base, injected, "text/html", "utf-8", null)
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

private fun readHtml(root: File): String {
    val entry = File(root, ProjectFiles.ENTRY_POINT)
    return runCatching { if (entry.exists()) entry.readText() else "" }.getOrElse { "" }
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
                Icon(PixelIcons.ExternalLink, contentDescription = "Buka di browser", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

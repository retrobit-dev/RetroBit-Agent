package dev.retrobit.agent.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.ResponseBody
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ToolCall(val id: String, val name: String, val argumentsJson: String)

data class AiToolDef(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

sealed interface WireTurn {
    data class User(val text: String) : WireTurn
    data class Assistant(val text: String, val toolCalls: List<ToolCall>) : WireTurn
    data class ToolResult(val callId: String, val name: String, val content: String) : WireTurn
}

data class TokenUsage(val prompt: Int = 0, val completion: Int = 0) {
    val total: Int get() = prompt + completion
}

data class AiResponse(
    val text: String,
    val toolCalls: List<ToolCall>,
    val usage: TokenUsage = TokenUsage(),
)

class AiException(message: String, val statusCode: Int = 0) : Exception(message)

interface AiClient {
    suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse

    /**
     * Same contract as [chat], but reports text as it arrives so the UI can paint
     * a partial answer. The default keeps every client usable: it waits for the
     * full response and emits it in one chunk.
     */
    suspend fun chatStream(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        onDelta: suspend (String) -> Unit,
    ): AiResponse = chat(system, turns, tools, temperature, maxTokens).also { response ->
        if (response.text.isNotBlank()) onDelta(response.text)
    }
}

object AiClientFactory {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    fun create(config: AiConfig, apiKey: String): AiClient {
        val base = config.baseUrl.ifBlank { config.catalog().baseUrl }
        return when (config.provider) {
            ProviderKind.ANTHROPIC -> AnthropicClient(http, base, apiKey, config.displayModel)
            ProviderKind.GEMINI -> GeminiClient(http, base, apiKey, config.displayModel)
            else -> OpenAiCompatibleClient(http, base, apiKey, config.displayModel, config.provider)
        }
    }

    suspend fun testConnection(config: AiConfig, apiKey: String): Result<String> = runCatching {
        val client = create(config, apiKey)
        val response = client.chat(
            system = "You are a connectivity probe. Reply with exactly: OK",
            turns = listOf(WireTurn.User("ping")),
            tools = emptyList(),
            temperature = 0.0,
            maxTokens = 20,
        )
        response.text.ifBlank { "Terhubung" }.take(160)
    }
}

internal val jsonMedia = "application/json; charset=utf-8".toMediaType()
internal val sharedJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal suspend fun OkHttpClient.executeAsync(request: Request): Response =
    suspendCancellableCoroutine { cont ->
        val call = newCall(request)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
    }

/**
 * Walks a server-sent-events body and hands every `data:` payload to [onEvent].
 * Providers frame events slightly differently but all of them end a stream with
 * either `[DONE]` or a closed socket, so both are treated as the end.
 */
internal suspend fun ResponseBody.readSse(onEvent: suspend (String) -> Unit) {
    val source = source()
    while (true) {
        val line = try {
            source.readUtf8Line()
        } catch (e: InterruptedIOException) {
            null
        }
        if (line == null) break
        if (line.isBlank() || line.startsWith(":")) continue
        if (!line.startsWith("data:")) continue
        val payload = line.removePrefix("data:").trim()
        if (payload == "[DONE]") break
        if (payload.isNotEmpty()) onEvent(payload)
    }
}

internal fun errorFor(status: Int, body: String, provider: String): AiException {
    val snippet = body.take(400).replace("\n", " ")
    val friendly = when (status) {
        401, 403 -> "API key ditolak oleh $provider. Periksa kembali key di AI Cloud."
        402 -> "$provider meminta pembayaran/kredit. Isi saldo lalu coba lagi."
        404 -> "Model atau endpoint tidak ditemukan di $provider."
        429 -> "$provider sedang membatasi permintaan (rate limit). Tunggu sebentar."
        in 500..599 -> "Server $provider sedang bermasalah. Coba beberapa saat lagi."
        else -> "$provider mengembalikan error $status."
    }
    return AiException("$friendly ($status) · $snippet", status)
}

internal fun extractError(body: String): String? = runCatching {
    val obj = sharedJson.parseToJsonElement(body).jsonObject
    val err = obj["error"]
    when (err) {
        is JsonObject -> err["message"]?.jsonPrimitive?.content
        is JsonPrimitive -> err.content
        else -> null
    }
}.getOrNull()

internal class OpenAiCompatibleClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val provider: ProviderKind,
) : AiClient {

    private fun messagesJson(turns: List<WireTurn>, system: String): JsonArray = buildJsonArray {
        add(buildJsonObject {
            put("role", "system")
            put("content", system)
        })
        turns.forEach { turn ->
            when (turn) {
                is WireTurn.User -> add(buildJsonObject {
                    put("role", "user")
                    put("content", turn.text)
                })
                is WireTurn.Assistant -> add(buildJsonObject {
                    put("role", "assistant")
                    put("content", turn.text.ifBlank { "" })
                    if (turn.toolCalls.isNotEmpty()) {
                        put("tool_calls", buildJsonArray {
                            turn.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("id", call.id)
                                    put("type", "function")
                                    put("function", buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson.ifBlank { "{}" })
                                    })
                                })
                            }
                        })
                    }
                })
                is WireTurn.ToolResult -> add(buildJsonObject {
                    put("role", "tool")
                    put("tool_call_id", turn.callId)
                    put("name", turn.name)
                    put("content", turn.content)
                })
            }
        }
    }

    private fun isReasoningModel(): Boolean = provider == ProviderKind.OPENAI &&
        (model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") || model.startsWith("gpt-5"))

    private fun payloadJson(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean,
    ): JsonObject {
        val isOpenAiReasoning = isReasoningModel()
        return buildJsonObject {
            put("model", model)
            put("messages", messagesJson(turns, system))
            if (!isOpenAiReasoning) put("temperature", temperature)
            put(if (isOpenAiReasoning) "max_completion_tokens" else "max_tokens", maxTokens)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            put("function", buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            })
                        })
                    }
                })
                put("tool_choice", "auto")
            }
            if (stream) {
                put("stream", true)
                // Only OpenAI documents this flag; other compatible endpoints reject it.
                if (provider == ProviderKind.OPENAI) {
                    put("stream_options", buildJsonObject { put("include_usage", true) })
                }
            }
        }
    }

    private fun request(body: JsonObject): Request {
        val url = baseUrl.trimEnd('/') + "/chat/completions"
        val builder = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("Content-Type", "application/json")
        if (apiKey.isNotBlank()) builder.header("Authorization", "Bearer $apiKey")
        if (provider == ProviderKind.OPENROUTER) {
            builder.header("HTTP-Referer", "https://retrobit.dev")
            builder.header("X-Title", "RetroBit Agent")
        }
        return builder.build()
    }

    private fun usageOf(root: JsonObject): TokenUsage = root["usage"]?.let { u ->
        runCatching {
            TokenUsage(
                prompt = u.jsonObject["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                completion = u.jsonObject["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        }.getOrDefault(TokenUsage())
    } ?: TokenUsage()

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens, stream = false)
        http.executeAsync(request(body)).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = extractError(text) ?: text
                throw errorFor(response.code, detail, provider.label)
            }
            val root = sharedJson.parseToJsonElement(text).jsonObject
            val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                ?: throw AiException("Respons tidak dikenali dari ${provider.label}")
            val content = when (val c = message["content"]) {
                is JsonPrimitive -> if (c is JsonNull) "" else c.content
                is JsonArray -> c.joinToString("") { part ->
                    runCatching { part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")
                }
                else -> ""
            }
            val calls = message["tool_calls"]?.jsonArray?.map { el ->
                val obj = el.jsonObject
                val fn = obj["function"]?.jsonObject
                ToolCall(
                    id = obj["id"]?.jsonPrimitive?.content ?: "call_${UUID.randomUUID().toString().take(8)}",
                    name = fn?.get("name")?.jsonPrimitive?.content.orEmpty(),
                    argumentsJson = fn?.get("arguments")?.jsonPrimitive?.content ?: "{}",
                )
            }?.filter { it.name.isNotBlank() } ?: emptyList()
            return AiResponse(content, calls, usageOf(root))
        }
    }

    override suspend fun chatStream(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        onDelta: suspend (String) -> Unit,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens, stream = true)
        return withContext(Dispatchers.IO) {
            http.executeAsync(request(body)).use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw errorFor(response.code, extractError(text) ?: text, provider.label)
                }
                val payload = response.body ?: throw AiException("Respons kosong dari ${provider.label}")
                val builder = StringBuilder()
                var usage = TokenUsage()
                payload.readSse { data ->
                    val root = runCatching { sharedJson.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@readSse
                    usageOf(root).takeIf { it.total > 0 }?.let { usage = it }
                    val delta = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
                    val piece = when (val c = delta?.get("content")) {
                        is JsonPrimitive -> if (c is JsonNull) "" else c.content
                        is JsonArray -> c.joinToString("") { part ->
                            runCatching { part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")
                        }
                        else -> ""
                    }
                    if (piece.isNotEmpty()) {
                        builder.append(piece)
                        // Called on the IO dispatcher; callers marshal to the UI.
                        onDelta(piece)
                    }
                }
                AiResponse(builder.toString(), emptyList(), usage)
            }
        }
    }
}

internal class AnthropicClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AiClient {

    private fun payloadJson(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        stream: Boolean,
    ): JsonObject {
        // Anthropic requires alternating roles: merge consecutive blocks of the same role.
        val merged = mutableListOf<Pair<String, MutableList<JsonElement>>>()
        fun push(role: String, block: JsonObject) {
            val last = merged.lastOrNull()
            if (last != null && last.first == role) last.second.add(block)
            else merged.add(role to mutableListOf(block))
        }
        turns.forEach { turn ->
            when (turn) {
                is WireTurn.User -> push("user", buildJsonObject {
                    put("type", "text")
                    put("text", turn.text)
                })
                is WireTurn.Assistant -> {
                    if (turn.text.isNotBlank()) push("assistant", buildJsonObject {
                        put("type", "text")
                        put("text", turn.text)
                    })
                    turn.toolCalls.forEach { call ->
                        push("assistant", buildJsonObject {
                            put("type", "tool_use")
                            put("id", call.id)
                            put("name", call.name)
                            put("input", parseObject(call.argumentsJson))
                        })
                    }
                }
                is WireTurn.ToolResult -> push("user", buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", turn.callId)
                    put("content", turn.content)
                })
            }
        }
        val messages = buildJsonArray {
            merged.forEach { (role, blocks) ->
                if (blocks.isEmpty()) return@forEach
                add(buildJsonObject {
                    put("role", role)
                    put("content", JsonArray(blocks.toList()))
                })
            }
        }
        return buildJsonObject {
            put("model", model)
            put("system", system)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("messages", messages)
            if (stream) put("stream", true)
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("input_schema", tool.parameters)
                        })
                    }
                })
            }
        }
    }

    private fun request(body: JsonObject): Request = Request.Builder()
        .url(baseUrl.trimEnd('/') + "/v1/messages")
        .post(body.toString().toRequestBody(jsonMedia))
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .header("Content-Type", "application/json")
        .build()

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens, stream = false)
        http.executeAsync(request(body)).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFor(response.code, extractError(text) ?: text, "Anthropic")
            val root = sharedJson.parseToJsonElement(text).jsonObject
            val content = root["content"]?.jsonArray ?: JsonArray(emptyList())
            val textOut = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            content.forEach { el ->
                val obj = el.jsonObject
                when (obj["type"]?.jsonPrimitive?.content) {
                    "text" -> textOut.append(obj["text"]?.jsonPrimitive?.content.orEmpty())
                    "tool_use" -> calls.add(
                        ToolCall(
                            id = obj["id"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
                            name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                            argumentsJson = (obj["input"] ?: buildJsonObject { }).toString(),
                        )
                    )
                }
            }
            val usage = root["usage"]?.let {
                runCatching {
                    TokenUsage(
                        prompt = it.jsonObject["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        completion = it.jsonObject["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                }.getOrDefault(TokenUsage())
            } ?: TokenUsage()
            return AiResponse(textOut.toString(), calls.filter { it.name.isNotBlank() }, usage)
        }
    }

    override suspend fun chatStream(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        onDelta: suspend (String) -> Unit,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens, stream = true)
        return withContext(Dispatchers.IO) {
            http.executeAsync(request(body)).use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw errorFor(response.code, extractError(text) ?: text, "Anthropic")
                }
                val payload = response.body ?: throw AiException("Respons kosong dari Anthropic")
                val builder = StringBuilder()
                var usage = TokenUsage()
                payload.readSse { data ->
                    val root = runCatching { sharedJson.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@readSse
                    when (root["type"]?.jsonPrimitive?.content) {
                        "content_block_delta" -> {
                            val piece = root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                            if (piece.isNotEmpty()) {
                                builder.append(piece)
                                onDelta(piece)
                            }
                        }
                        "message_start" -> {
                            val start = root["message"]?.jsonObject?.get("usage")?.jsonObject
                            usage = usage.copy(
                                prompt = start?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: usage.prompt,
                            )
                        }
                        "message_delta" -> {
                            val out = root["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                            if (out != null) usage = usage.copy(completion = out)
                        }
                    }
                }
                AiResponse(builder.toString(), emptyList(), usage)
            }
        }
    }
}

internal class GeminiClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AiClient {

    private fun payloadJson(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): JsonObject {
        val contents = mutableListOf<JsonObject>()
        // Group consecutive tool results into a single user turn.
        var pendingToolParts: MutableList<JsonElement>? = null
        fun flushTools() {
            val parts = pendingToolParts ?: return
            if (parts.isNotEmpty()) contents.add(buildJsonObject {
                put("role", "user")
                put("parts", JsonArray(parts.toList()))
            })
            pendingToolParts = null
        }
        turns.forEach { turn ->
            when (turn) {
                is WireTurn.User -> {
                    flushTools()
                    contents.add(buildJsonObject {
                        put("role", "user")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", turn.text) }) })
                    })
                }
                is WireTurn.Assistant -> {
                    flushTools()
                    contents.add(buildJsonObject {
                        put("role", "model")
                        put("parts", buildJsonArray {
                            if (turn.text.isNotBlank()) add(buildJsonObject { put("text", turn.text) })
                            turn.toolCalls.forEach { call ->
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", call.name)
                                        put("args", parseObject(call.argumentsJson))
                                    })
                                })
                            }
                        })
                    })
                }
                is WireTurn.ToolResult -> {
                    val list = pendingToolParts ?: mutableListOf<JsonElement>().also { pendingToolParts = it }
                    list.add(buildJsonObject {
                        put("functionResponse", buildJsonObject {
                            put("name", turn.name)
                            put("response", buildJsonObject { put("result", turn.content) })
                        })
                    })
                }
            }
        }
        flushTools()

        return buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) })
            })
            put("contents", JsonArray(contents))
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", maxTokens)
            })
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("functionDeclarations", buildJsonArray {
                            tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parameters)
                                })
                            }
                        })
                    })
                })
            }
        }
    }

    private fun request(body: JsonObject, stream: Boolean): Request {
        val suffix = if (stream) "streamGenerateContent?alt=sse" else "generateContent"
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:$suffix"
        return Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .build()
    }

    private fun usageOf(root: JsonObject): TokenUsage = root["usageMetadata"]?.let {
        runCatching {
            TokenUsage(
                prompt = it.jsonObject["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                completion = it.jsonObject["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            )
        }.getOrDefault(TokenUsage())
    } ?: TokenUsage()

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens)
        http.executeAsync(request(body, stream = false)).use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFor(response.code, extractError(text) ?: text, "Gemini")
            val root = sharedJson.parseToJsonElement(text).jsonObject
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val parts = candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: JsonArray(emptyList())
            val textOut = StringBuilder()
            val calls = mutableListOf<ToolCall>()
            parts.forEach { el ->
                val obj = el.jsonObject
                obj["text"]?.let { textOut.append(it.jsonPrimitive.content) }
                obj["functionCall"]?.let { fn ->
                    val fnObj = fn.jsonObject
                    calls.add(
                        ToolCall(
                            id = "gemini_${UUID.randomUUID().toString().take(8)}",
                            name = fnObj["name"]?.jsonPrimitive?.content.orEmpty(),
                            argumentsJson = (fnObj["args"] ?: buildJsonObject { }).toString(),
                        )
                    )
                }
            }
            return AiResponse(textOut.toString(), calls.filter { it.name.isNotBlank() }, usageOf(root))
        }
    }

    override suspend fun chatStream(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
        onDelta: suspend (String) -> Unit,
    ): AiResponse {
        val body = payloadJson(system, turns, tools, temperature, maxTokens)
        return withContext(Dispatchers.IO) {
            http.executeAsync(request(body, stream = true)).use { response ->
                if (!response.isSuccessful) {
                    val text = response.body?.string().orEmpty()
                    throw errorFor(response.code, extractError(text) ?: text, "Gemini")
                }
                val payload = response.body ?: throw AiException("Respons kosong dari Gemini")
                val builder = StringBuilder()
                var usage = TokenUsage()
                payload.readSse { data ->
                    val root = runCatching { sharedJson.parseToJsonElement(data).jsonObject }.getOrNull()
                        ?: return@readSse
                    usageOf(root).takeIf { it.total > 0 }?.let { usage = it }
                    val parts = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject?.get("parts")?.jsonArray ?: return@readSse
                    parts.forEach { el ->
                        val piece = runCatching { el.jsonObject["text"]?.jsonPrimitive?.content.orEmpty() }
                            .getOrDefault("")
                        if (piece.isNotEmpty()) {
                            builder.append(piece)
                            onDelta(piece)
                        }
                    }
                }
                AiResponse(builder.toString(), emptyList(), usage)
            }
        }
    }
}

internal fun parseObject(raw: String): JsonObject = runCatching {
    when (val parsed = sharedJson.parseToJsonElement(raw)) {
        is JsonObject -> parsed
        is JsonPrimitive -> sharedJson.parseToJsonElement(parsed.content).jsonObject
        else -> buildJsonObject { }
    }
}.getOrElse { buildJsonObject { } }

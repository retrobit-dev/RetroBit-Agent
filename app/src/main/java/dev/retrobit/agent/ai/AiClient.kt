package dev.retrobit.agent.ai

import kotlinx.coroutines.suspendCancellableCoroutine
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
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
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

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
        val messages = buildJsonArray {
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

        val isOpenAiReasoning = provider == ProviderKind.OPENAI &&
            (model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4") || model.startsWith("gpt-5"))
        val body = buildJsonObject {
            put("model", model)
            put("messages", messages)
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
        }

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

        http.executeAsync(builder.build()).use { response ->
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
            val usage = root["usage"]?.let { u ->
                runCatching {
                    TokenUsage(
                        prompt = u.jsonObject["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        completion = u.jsonObject["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                }.getOrDefault(TokenUsage())
            } ?: TokenUsage()
            return AiResponse(content, calls, usage)
        }
    }
}

internal class AnthropicClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AiClient {

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
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
        val body = buildJsonObject {
            put("model", model)
            put("system", system)
            put("max_tokens", maxTokens)
            put("temperature", temperature)
            put("messages", messages)
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
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/messages")
            .post(body.toString().toRequestBody(jsonMedia))
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .build()
        http.executeAsync(request).use { response ->
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
}

internal class GeminiClient(
    private val http: OkHttpClient,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
) : AiClient {

    override suspend fun chat(
        system: String,
        turns: List<WireTurn>,
        tools: List<AiToolDef>,
        temperature: Double,
        maxTokens: Int,
    ): AiResponse {
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

        val body = buildJsonObject {
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
        val url = "${baseUrl.trimEnd('/')}/v1beta/models/$model:generateContent"
        val request = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody(jsonMedia))
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .build()
        http.executeAsync(request).use { response ->
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
            val usage = root["usageMetadata"]?.let {
                runCatching {
                    TokenUsage(
                        prompt = it.jsonObject["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        completion = it.jsonObject["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    )
                }.getOrDefault(TokenUsage())
            } ?: TokenUsage()
            return AiResponse(textOut.toString(), calls.filter { it.name.isNotBlank() }, usage)
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

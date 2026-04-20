package com.orqa.chat.llm

import android.util.Base64
import com.orqa.chat.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ChatMessage(
    val role: String,
    val content: String,
    val imageBase64: String = "",
    val imageMime: String = ""
)

data class LlmResponse(
    val text: String,
    val error: String = ""
)

class LlmService(private val settings: SettingsStore) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(
        systemPrompt: String,
        messages: List<ChatMessage>,
        provider: String,
        model: String
    ): LlmResponse = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                "gemini"    -> callGemini(systemPrompt, messages, model)
                "anthropic" -> callAnthropic(systemPrompt, messages, model)
                "local"     -> callLocalServer(systemPrompt, messages, model)
                else        -> callGemini(systemPrompt, messages, model)
            }
        } catch (e: Exception) {
            LlmResponse("", "Error: ${e.message}")
        }
    }

    // ── Gemini ────────────────────────────────────────────────────

    private suspend fun callGemini(
        system: String,
        messages: List<ChatMessage>,
        model: String
    ): LlmResponse {
        val key = settings.getString(SettingsStore.GEMINI_KEY)
        if (key.isEmpty()) return LlmResponse("", "No Gemini API key set. Go to Settings.")

        val contents = JSONArray()

        // Gemini uses system_instruction separately
        val geminiContents = buildGeminiContents(messages)
        geminiContents.forEach { contents.put(it) }

        val body = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts",
                JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 1024)
                put("temperature", 0.7)
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
        val response = post(url, body.toString())
        return parseGeminiResponse(response)
    }

    private fun buildGeminiContents(messages: List<ChatMessage>): List<JSONObject> =
        messages.map { msg ->
            val parts = JSONArray()
            if (msg.imageBase64.isNotEmpty()) {
                parts.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", msg.imageMime.ifEmpty { "image/jpeg" })
                        put("data", msg.imageBase64)
                    })
                })
            }
            parts.put(JSONObject().put("text", msg.content))
            JSONObject().apply {
                put("role", if (msg.role == "assistant") "model" else "user")
                put("parts", parts)
            }
        }

    private fun parseGeminiResponse(json: String): LlmResponse {
        return try {
            val obj = JSONObject(json)
            if (obj.has("error")) {
                val err = obj.getJSONObject("error")
                return LlmResponse("", err.optString("message", "Gemini error"))
            }
            val text = obj
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
            LlmResponse(text)
        } catch (e: Exception) {
            LlmResponse("", "Failed to parse Gemini response: ${e.message}\n\nRaw: ${json.take(300)}")
        }
    }

    // ── Anthropic Claude ──────────────────────────────────────────

    private suspend fun callAnthropic(
        system: String,
        messages: List<ChatMessage>,
        model: String
    ): LlmResponse {
        val key = settings.getString(SettingsStore.ANTHROPIC_KEY)
        if (key.isEmpty()) return LlmResponse("", "No Anthropic API key set. Go to Settings.")

        val msgs = JSONArray()
        messages.forEach { msg ->
            val content: Any = if (msg.imageBase64.isNotEmpty()) {
                JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "image")
                        put("source", JSONObject().apply {
                            put("type", "base64")
                            put("media_type", msg.imageMime.ifEmpty { "image/jpeg" })
                            put("data", msg.imageBase64)
                        })
                    })
                    put(JSONObject().apply {
                        put("type", "text")
                        put("text", msg.content)
                    })
                }
            } else {
                msg.content
            }
            msgs.put(JSONObject().apply {
                put("role", msg.role)
                put("content", content)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1024)
            put("system", system)
            put("messages", msgs)
        }

        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody(JSON))
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: ""
        return try {
            val obj = JSONObject(responseBody)
            if (obj.has("error")) {
                val err = obj.getJSONObject("error")
                return LlmResponse("", err.optString("message", "Anthropic error"))
            }
            val text = obj.getJSONArray("content").getJSONObject(0).getString("text")
            LlmResponse(text)
        } catch (e: Exception) {
            LlmResponse("", "Failed to parse response: ${e.message}")
        }
    }

    // ── Local server (laptop running server.py) ───────────────────

    private suspend fun callLocalServer(
        system: String,
        messages: List<ChatMessage>,
        model: String
    ): LlmResponse {
        val baseUrl = settings.getString(SettingsStore.LOCAL_SERVER_URL)
            .trimEnd('/')
            .ifEmpty { "http://192.168.1.100:5000" }

        val msgs = JSONArray()
        messages.forEach { msg ->
            msgs.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("messages", msgs)
            put("mode", "troubleshoot")
        }

        val response = post("$baseUrl/chat", body.toString())
        return try {
            val obj = JSONObject(response)
            if (obj.has("error")) return LlmResponse("", obj.getString("error"))
            LlmResponse(obj.getString("reply"))
        } catch (e: Exception) {
            LlmResponse("", "Local server error: ${e.message}")
        }
    }

    // ── HTTP helper ───────────────────────────────────────────────

    private fun post(url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON))
            .build()
        return client.newCall(request).execute().body?.string() ?: "{}"
    }
}

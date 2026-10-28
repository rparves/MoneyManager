package com.rparves.moneymanager.ai

import android.content.Context
import android.util.Base64
import com.rparves.moneymanager.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal clients for Google Gemini and Anthropic Claude, using the user's own API key. */
object Llm {
    fun defaultModel(provider: String) = when (provider) {
        "claude" -> "claude-haiku-5-5"
        else -> "gemini-flash-latest"
    }

    fun isConfigured(ctx: Context): Boolean {
        val p = ctx.app().prefs
        return p.aiProvider != "none" && p.aiKey.isNotBlank()
    }

    suspend fun ask(ctx: Context, prompt: String, jpeg: ByteArray?): String = withContext(Dispatchers.IO) {
        val p = ctx.app().prefs
        val provider = p.aiProvider
        val key = p.aiKey.trim()
        val model = p.aiModel.trim().ifBlank { defaultModel(provider) }
        require(provider != "none" && key.isNotBlank()) { "Add an AI key in Settings first" }
        val image = jpeg?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        if (provider == "claude") claude(key, model, prompt, image) else gemini(key, model, prompt, image)
    }

    private fun claude(key: String, model: String, prompt: String, image: String?): String {
        val content = JSONArray()
        if (image != null) {
            content.put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image)))
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val res = post(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"),
            body,
        )
        val blocks = res.optJSONArray("content") ?: error("Empty reply from Claude")
        return (0 until blocks.length()).map { blocks.getJSONObject(it) }
            .filter { it.optString("type") == "text" }.joinToString("") { it.optString("text") }
    }

    private fun gemini(key: String, model: String, prompt: String, image: String?): String {
        val parts = JSONArray()
        if (image != null) {
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", image)))
        }
        parts.put(JSONObject().put("text", prompt))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json"))
        val res = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key),
            body,
        )
        val cand = res.optJSONArray("candidates")?.optJSONObject(0) ?: error("Gemini returned no answer")
        val p = cand.optJSONObject("content")?.optJSONArray("parts") ?: error("Gemini returned no answer")
        return (0 until p.length()).joinToString("") { p.getJSONObject(it).optString("text") }
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val msg = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(
                    when (code) {
                        401, 403 -> "The AI key was rejected. Check it in Settings."
                        404 -> "Model not found. Check the model name in Settings."
                        429 -> "AI usage limit reached. Try again later."
                        else -> msg?.takeIf { it.isNotBlank() } ?: "AI request failed ($code)"
                    }
                )
            }
            return JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }
}

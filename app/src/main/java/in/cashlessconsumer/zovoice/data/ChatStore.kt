package `in`.cashlessconsumer.zovoice.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ChatMessage(val role: String, val text: String, val ts: Long)

/** Persists the transcript as JSON in app-private storage. */
class ChatStore(context: Context) {
    private val file = File(context.filesDir, "conversation.json")

    fun load(): List<ChatMessage> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                ChatMessage(o.optString("role", "assistant"), o.optString("text"), o.optLong("ts"))
            }.filter { it.text.isNotEmpty() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(messages: List<ChatMessage>) {
        try {
            val arr = JSONArray()
            messages.takeLast(MAX).forEach { m ->
                arr.put(
                    JSONObject()
                        .put("role", m.role)
                        .put("text", m.text)
                        .put("ts", m.ts)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun clear() {
        file.delete()
    }

    private companion object {
        const val MAX = 300
    }
}

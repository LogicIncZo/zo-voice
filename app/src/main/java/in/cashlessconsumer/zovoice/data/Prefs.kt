package `in`.cashlessconsumer.zovoice.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.getSharedPreferences("zo_voice", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString(KEY_TOKEN, "").orEmpty()
        set(v) = sp.edit().putString(KEY_TOKEN, v).apply()

    var modelName: String
        get() = sp.getString(KEY_MODEL, "").orEmpty()
        set(v) = sp.edit().putString(KEY_MODEL, v).apply()

    var personaId: String
        get() = sp.getString(KEY_PERSONA, "").orEmpty()
        set(v) = sp.edit().putString(KEY_PERSONA, v).apply()

    var autoListen: Boolean
        get() = sp.getBoolean(KEY_AUTO_LISTEN, true)
        set(v) = sp.edit().putBoolean(KEY_AUTO_LISTEN, v).apply()

    var speakResponses: Boolean
        get() = sp.getBoolean(KEY_SPEAK, true)
        set(v) = sp.edit().putBoolean(KEY_SPEAK, v).apply()

    var speechRate: Float
        get() = sp.getFloat(KEY_RATE, 1.0f)
        set(v) = sp.edit().putFloat(KEY_RATE, v).apply()

    var speechPitch: Float
        get() = sp.getFloat(KEY_PITCH, 1.0f)
        set(v) = sp.edit().putFloat(KEY_PITCH, v).apply()

    var conversationId: String
        get() = sp.getString(KEY_CONVERSATION, "").orEmpty()
        set(v) = sp.edit().putString(KEY_CONVERSATION, v).apply()

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_MODEL = "model_name"
        const val KEY_PERSONA = "persona_id"
        const val KEY_AUTO_LISTEN = "auto_listen"
        const val KEY_SPEAK = "speak_responses"
        const val KEY_RATE = "speech_rate"
        const val KEY_PITCH = "speech_pitch"
        const val KEY_CONVERSATION = "conversation_id"
    }
}

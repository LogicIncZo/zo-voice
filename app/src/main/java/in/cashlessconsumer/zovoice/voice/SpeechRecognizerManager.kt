package `in`.cashlessconsumer.zovoice.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Wraps the platform SpeechRecognizer. All entry points are safe to call from
 * any thread; recognizer lifecycle is pinned to the main thread as required.
 */
class SpeechRecognizerManager(
    private val context: Context,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (String?) -> Unit, // null message = transient, safe to retry
    private val onListeningEnd: () -> Unit,
) : RecognitionListener {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        mainHandler.post {
            if (!isAvailable()) {
                onError("Speech recognition is not available on this device. Install Google Speech Services or use the text box.")
                return@post
            }
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(this) }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            try {
                recognizer?.startListening(intent)
            } catch (e: Exception) {
                onError("Could not start the microphone: ${e.message}")
            }
        }
    }

    fun stop() {
        mainHandler.post {
            recognizer?.let { rec ->
                try { rec.stopListening() } catch (_: Exception) {}
            }
        }
    }

    /** Full teardown — only on ViewModel clear. */
    fun destroy() {
        mainHandler.post { destroyLocked() }
    }

    private fun destroyLocked() {
        recognizer?.let { rec ->
            try {
                rec.stopListening()
            } catch (_: Exception) {
            }
            try {
                rec.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onPartialResults(partialResults: Bundle?) {
        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (!text.isNullOrBlank()) onPartial(text.trim())
    }

    override fun onResults(results: Bundle?) {
        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()
            .trim()
        onListeningEnd()
        if (text.isEmpty()) {
            onError("I didn't catch that.")
        } else {
            onFinal(text)
        }
    }

    override fun onError(error: Int) {
        onListeningEnd()
        val message: String? = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network error — check connectivity."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "The current speech language isn't supported by this device."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT -> null
            else -> "Speech recognizer error ($error)."
        }
        if (error == SpeechRecognizer.ERROR_CLIENT) {
            mainHandler.post { destroyLocked() }
        }
        if (message != null) onError(message) else onError(null)
    }
}

package `in`.cashlessconsumer.zovoice.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Streaming TTS: feed() gets stream chunks, buffers them, and speaks complete
 * sentences as they arrive so speech starts before the full response lands.
 * onQueueEmpty fires once, after the stream is complete AND all utterances
 * have finished playing — the signal for the hands-free loop to re-open the mic.
 */
class TtsManager(
    context: Context,
    private val onQueueEmpty: () -> Unit,
) {
    @Volatile var rate = 1.0f
    @Volatile var pitch = 1.0f
    @Volatile var enabled = true

    private var tts: TextToSpeech? = null
    private val ready = AtomicBoolean(false)
    private val pending = AtomicInteger(0)
    private val speaking = AtomicBoolean(false)
    private val streamDone = AtomicBoolean(true)
    private val earlyQueue = ArrayDeque<String>()

    private val chunker = SentenceChunker { text ->
        synchronized(earlyQueue) {
            if (!ready.get()) {
                earlyQueue.add(text)
                return@synchronized
            }
        }
        enqueue(text)
    }

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                pickLanguage()
                tts?.setOnUtteranceProgressListener(listener)
                ready.set(true)
                val early = synchronized(earlyQueue) {
                    val copy = earlyQueue.toList()
                    earlyQueue.clear()
                    copy
                }
                early.forEach { enqueue(it) }
            }
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {}
        override fun onDone(utteranceId: String?) = onUtteranceFinished()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = onUtteranceFinished()
        override fun onError(utteranceId: String?, errorCode: Int) = onUtteranceFinished()
    }

    private fun pickLanguage() {
        val candidates = listOf(Locale.getDefault(), Locale.US)
        for (loc in candidates) {
            val r = tts?.setLanguage(loc)
            if (r != null && r >= 0) return
        }
    }

    /** Append a streamed text chunk; speaks each completed sentence. */
    fun feed(chunk: String) {
        if (!enabled || chunk.isEmpty()) return
        chunker.feed(chunk)
    }

    /** Call once the stream has ended: flushes the tail and marks the turn done. */
    fun streamComplete() {
        chunker.flush()
        streamDone.set(true)
    }

    fun reset() {
        chunker.reset()
        streamDone.set(false)
    }

    fun isBusy(): Boolean = pending.get() > 0 || speaking.get()

    /** User-initiated barge-in: silence everything without firing onQueueEmpty. */
    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        pending.set(0)
        speaking.set(false)
        streamDone.set(true)
        chunker.reset()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready.set(false)
    }

    private fun onUtteranceFinished() {
        if (pending.decrementAndGet() <= 0 && streamDone.get()) {
            speaking.set(false)
            onQueueEmpty()
        }
    }

    private fun enqueue(text: String) {
        val engine = tts ?: return
        pending.incrementAndGet()
        speaking.set(true)
        try {
            engine.setSpeechRate(rate)
            engine.setPitch(pitch)
            engine.speak(text, TextToSpeech.QUEUE_ADD, Bundle(), "zv-${System.nanoTime()}")
        } catch (_: Exception) {
            pending.decrementAndGet()
        }
    }
}

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
    private val buffer = StringBuilder()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                pickLanguage()
                tts?.setOnUtteranceProgressListener(listener)
                ready.set(true)
                synchronized(buffer) { flushLocked(final = false) }
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
        synchronized(buffer) {
            buffer.append(chunk)
            flushLocked(final = false)
        }
    }

    /** Call once the stream has ended: flushes the tail and marks the turn done. */
    fun streamComplete() {
        synchronized(buffer) { flushLocked(final = true) }
        streamDone.set(true)
    }

    fun reset() {
        synchronized(buffer) { buffer.setLength(0) }
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
        synchronized(buffer) { buffer.setLength(0) }
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

    private val sentenceEnders = charArrayOf('.', '!', '?', '\n')

    private fun flushLocked(final: Boolean) {
        val sb = buffer
        var consumed = 0
        var i = 0
        while (i < sb.length) {
            if (sb[i] in sentenceEnders) {
                val seg = sanitize(sb.substring(consumed, i + 1))
                if (seg.isNotEmpty()) enqueue(seg)
                consumed = i + 1
            }
            i++
        }
        if (final) {
            val rest = sanitize(sb.substring(consumed))
            if (rest.isNotEmpty()) enqueue(rest)
            sb.setLength(0)
        } else if (consumed > 0) {
            val tail = sb.substring(consumed)
            sb.setLength(0)
            sb.append(tail)
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

    private val fencedCode = Regex("(?s)```.*?(```|$)")
    private val mdSymbols = Regex("[*_`#>\\[\\]]")
    private val bareUrls = Regex("\\(?https?://\\S+\\)?")
    private val whitespace = Regex("\\s+")

    private fun sanitize(text: String): String = text
        .replace(fencedCode, " Code block. ")
        .replace(bareUrls, "")
        .replace(mdSymbols, "")
        .replace(whitespace, " ")
        .trim()
}

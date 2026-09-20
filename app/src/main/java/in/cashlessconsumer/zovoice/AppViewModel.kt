package `in`.cashlessconsumer.zovoice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.cashlessconsumer.zovoice.data.ChatMessage
import `in`.cashlessconsumer.zovoice.voice.SentenceChunker
import `in`.cashlessconsumer.zovoice.data.ChatStore
import `in`.cashlessconsumer.zovoice.data.Prefs
import `in`.cashlessconsumer.zovoice.data.ZoApi
import `in`.cashlessconsumer.zovoice.data.ZoException
import `in`.cashlessconsumer.zovoice.voice.SpeechRecognizerManager
import `in`.cashlessconsumer.zovoice.voice.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import `in`.cashlessconsumer.zovoice.data.ZoConversation
import `in`.cashlessconsumer.zovoice.data.ZoConversations
import okhttp3.Call
import java.util.concurrent.atomic.AtomicBoolean

enum class Phase { Idle, Listening, Thinking, Speaking }

data class UiState(
    val messages: List<ChatMessage> = emptyList(),
    val phase: Phase = Phase.Idle,
    val partial: String = "",
    val status: String? = null,
    val error: String? = null,
    val elapsedSec: Int = 0,
)

data class SettingsState(
    val token: String = "",
    val model: String = "",
    val persona: String = "",
    val speak: Boolean = true,
    val handsFree: Boolean = true,
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val chatStore = ChatStore(app)

    val ui = MutableStateFlow(UiState(messages = chatStore.load()))
    val settings = MutableStateFlow(
        SettingsState(
            token = prefs.token,
            model = prefs.modelName,
            persona = prefs.personaId,
            speak = prefs.speakResponses,
            handsFree = prefs.autoListen,
            rate = prefs.speechRate,
            pitch = prefs.speechPitch,
        )
    )

    val models = MutableStateFlow<List<ZoApi.ModelInfo>>(emptyList())
    val modelsLoading = MutableStateFlow(false)
    val modelsError = MutableStateFlow<String?>(null)
    val personas = MutableStateFlow<List<ZoApi.PersonaInfo>>(emptyList())
    val conversations = MutableStateFlow<List<ZoConversation>>(emptyList())
    val conversationsError = MutableStateFlow<String?>(null)
    private var conversationsLoaded = false
    val personasLoading = MutableStateFlow(false)
    val personasError = MutableStateFlow<String?>(null)

    private val tts = TtsManager(app) {
        viewModelScope.launch(Dispatchers.Main) { onQueueDrained() }
    }
    private val asr = SpeechRecognizerManager(
        app,
        onPartial = { text ->
            ui.update { if (it.phase == Phase.Listening) it.copy(partial = text) else it }
        },
        onFinal = { text ->
            if (ui.value.phase == Phase.Listening) send(text)
        },
        onError = { message -> onAsrError(message) },
        onListeningEnd = {},
    )

    init {
        tts.rate = prefs.speechRate
        tts.pitch = prefs.speechPitch
        tts.enabled = prefs.speakResponses
        if (prefs.autoListen && prefs.token.isNotBlank()) {
            viewModelScope.launch(Dispatchers.Main) {
                delay(700)
                if (ui.value.phase == Phase.Idle) startListening()
            }
        }
    }

    private var conversationId: String = prefs.conversationId
    private var activeCall: Call? = null
    private var tickerJob: Job? = null
    private val turnActive = AtomicBoolean(false)
    private val assistantText = StringBuilder()
    private var consecutiveAsrErrors = 0

    // ---- mic / phase control ----

    fun micPressed() {
        viewModelScope.launch(Dispatchers.Main) {
            when (ui.value.phase) {
                Phase.Listening -> stopListening()
                Phase.Thinking -> cancelTurn(userStopped = true, errorText = null)
                Phase.Speaking -> {
                    tts.stopSpeaking()
                    ui.update { it.copy(phase = Phase.Idle) }
                    startListening()
                }
                Phase.Idle -> startListening()
            }
        }
    }

    fun startListening() {
        if (prefs.token.isBlank()) {
            ui.update { it.copy(error = "Add your Zo API token in Settings first.") }
            return
        }
        if (!asr.isAvailable()) {
            ui.update { it.copy(error = "No speech recognizer on this device — use the text box.") }
            return
        }
        ui.update { it.copy(phase = Phase.Listening, partial = "", error = null) }
        asr.start()
    }

    private fun stopListening() {
        asr.stop()
        ui.update { it.copy(phase = Phase.Idle, partial = "") }
    }

    private fun maybeAutoListen() {
        if (!prefs.autoListen || prefs.token.isBlank()) return
        viewModelScope.launch(Dispatchers.Main) {
            delay(900)
            if (ui.value.phase == Phase.Idle) startListening()
        }
    }

    private fun onAsrError(message: String?) {
        if (ui.value.phase == Phase.Listening) {
            ui.update { it.copy(phase = Phase.Idle, partial = "") }
        }
        if (message != null) {
            consecutiveAsrErrors++
            ui.update { it.copy(error = message) }
        }
        if (prefs.autoListen && prefs.token.isNotBlank() && consecutiveAsrErrors < 4) {
            viewModelScope.launch(Dispatchers.Main) {
                delay(1500)
                if (ui.value.phase == Phase.Idle) startListening()
            }
        }
    }

    // ---- send / turn lifecycle ----

    fun sendText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        asr.stop()
        send(t)
    }

    private fun send(text: String) {
        if (handleVoiceCommand(text)) return
        val token = prefs.token
        if (token.isBlank()) {
            ui.update { it.copy(phase = Phase.Idle, error = "Add your Zo API token in Settings first.") }
            return
        }
        consecutiveAsrErrors = 0

        val now = System.currentTimeMillis()
        val msgs = ui.value.messages + ChatMessage("user", text, now)
        synchronized(assistantText) { assistantText.setLength(0) }
        tts.reset()
        turnActive.set(true)
        ui.update {
            it.copy(messages = msgs, phase = Phase.Thinking, partial = "", status = null, error = null, elapsedSec = 0)
        }
        startTicker()

        val opts = ZoApi.Options(token, prefs.modelName, prefs.personaId)
        activeCall = ZoApi.ask(
            opts = opts,
            input = text,
            conversationId = conversationId,
            onDelta = { chunk ->
                synchronized(assistantText) { assistantText.append(chunk) }
                val snapshot = synchronized(assistantText) { assistantText.toString() }
                ui.update { it.copy(partial = snapshot, status = null) }
                tts.feed(chunk)
            },
            onStatus = { s -> ui.update { it.copy(status = s) } },
            onDone = { convId ->
                if (convId.isNotEmpty()) {
                    conversationId = convId
                    prefs.conversationId = convId
                }
                viewModelScope.launch(Dispatchers.Main) {
                    tts.streamComplete()
                    finishTurn()
                }
            },
            onError = { t ->
                viewModelScope.launch(Dispatchers.Main) {
                    tts.stopSpeaking()
                    cancelTurn(userStopped = false, errorText = friendly(t))
                }
            },
        )
    }

    private fun finishTurn() {
        if (!turnActive.compareAndSet(true, false)) return
        tickerJob?.cancel()
        val text = synchronized(assistantText) { assistantText.toString() }
        val msgs = ui.value.messages.toMutableList()
        if (text.isNotBlank()) msgs += ChatMessage("assistant", text, System.currentTimeMillis())
        chatStore.save(msgs)
        val speakingNow = tts.isBusy()
        ui.update {
            it.copy(
                messages = msgs,
                phase = if (speakingNow) Phase.Speaking else Phase.Idle,
                partial = "",
                status = null,
            )
        }
        if (!speakingNow) maybeAutoListen()
    }

    private fun cancelTurn(userStopped: Boolean, errorText: String?) {
        activeCall?.cancel()
        activeCall = null
        if (!turnActive.compareAndSet(true, false)) return
        tickerJob?.cancel()
        val text = synchronized(assistantText) { assistantText.toString() }
        val msgs = ui.value.messages.toMutableList()
        if (text.isNotBlank()) {
            msgs += ChatMessage("assistant", text + if (userStopped) " —" else "", System.currentTimeMillis())
        }
        if (errorText != null) msgs += ChatMessage("assistant", "⚠ $errorText", System.currentTimeMillis())
        chatStore.save(msgs)
        ui.update {
            it.copy(messages = msgs, phase = Phase.Idle, partial = "", status = null, error = errorText ?: it.error)
        }
    }

    private fun onQueueDrained() {
        if (ui.value.phase == Phase.Speaking) {
            ui.update { it.copy(phase = Phase.Idle) }
            maybeAutoListen()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch(Dispatchers.Main) {
            var sec = 0
            while (isActive) {
                delay(1000)
                sec++
                ui.update { it.copy(elapsedSec = sec) }
            }
        }
    }

    private fun friendly(t: Throwable): String =
        (t as? ZoException)?.message ?: t.message ?: "Something went wrong talking to Zo."

    // ---- existing conversations: list, continue, hear updates ----

    fun loadConversations(force: Boolean = false) {
        if (prefs.token.isBlank()) {
            conversationsError.value = "Add your Zo API token in Settings first."
            return
        }
        if (conversationsLoaded && !force) return
        conversationsLoaded = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val remote = ZoConversations.list(prefs.token)
                val localId = prefs.conversationId
                val merged = if (localId.isNotBlank() && remote.none { it.id == localId }) {
                    val localTitle = ui.value.messages.firstOrNull { it.role == "user" }
                        ?.text?.take(60) ?: "This conversation"
                    remote + ZoConversation(localId, localTitle, null, null, local = true)
                } else {
                    remote
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    conversations.value = merged
                    conversationsError.value = null
                }
            } catch (t: Throwable) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    conversationsError.value = friendly(t)
                }
            }
        }
    }

    fun continueConversation(c: ZoConversation) {
        activeCall?.cancel(); activeCall = null
        tickerJob?.cancel(); turnActive.set(false)
        tts.stopSpeaking(); asr.stop()
        conversationId = c.id
        prefs.conversationId = c.id
        viewModelScope.launch(Dispatchers.IO) {
            var msgs: List<ChatMessage> = emptyList()
            var spoke = "Continuing: ${SentenceChunker.sanitize(c.title)}."
            try {
                msgs = ZoConversations.history(prefs.token, c.id)
            } catch (t: Throwable) {
                if (c.local) msgs = chatStore.load()
                spoke = "Continuing: ${SentenceChunker.sanitize(c.title)}. History not loaded — ${friendly(t)}"
            }
            val loaded = msgs
            val announce = spoke
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                chatStore.save(loaded)
                ui.update { it.copy(messages = loaded, phase = Phase.Idle, partial = "", status = null) }
                if (prefs.speakResponses) tts.speakNow(announce)
                if (prefs.autoListen) maybeAutoListen()
            }
        }
    }

    fun speakUpdates(c: ZoConversation) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val hist = ZoConversations.history(prefs.token, c.id)
                val digest = "Latest in ${SentenceChunker.sanitize(c.title)}. " +
                    ZoConversations.speakableDigest(hist)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (prefs.speakResponses) { tts.stopSpeaking(); tts.speakNow(digest) }
                }
            } catch (t: Throwable) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (prefs.speakResponses) tts.speakNow(friendly(t))
                }
            }
        }
    }

    fun catchMeUp() {
        ui.update { it.copy(status = "Catching you up…") }
        viewModelScope.launch(Dispatchers.IO) {
            val speakOnMain: (String) -> Unit = { text ->
                viewModelScope.launch(Dispatchers.Main) {
                    ui.update { it.copy(status = null) }
                    if (prefs.speakResponses) { tts.stopSpeaking(); tts.speakNow(text) }
                }
            }
            try {
                val convs = ZoConversations.list(prefs.token)
                val target = convs.firstOrNull()
                if (target == null) {
                    speakOnMain("You have no recent Zo conversations.")
                    return@launch
                }
                val hist = try {
                    ZoConversations.history(prefs.token, target.id)
                } catch (_: Throwable) { emptyList() }
                val title = SentenceChunker.sanitize(target.title)
                speakOnMain(
                    if (hist.isEmpty()) {
                        "Your most recent conversation is $title. Its history is not readable with this token."
                    } else {
                        "Catching you up on $title. " + ZoConversations.speakableDigest(hist)
                    }
                )
            } catch (t: Throwable) {
                speakOnMain(friendly(t))
            }
        }
    }

    /** Local voice commands, no Zo round-trip. Returns true if consumed. */
    private fun handleVoiceCommand(raw: String): Boolean {
        val t = raw.trim().lowercase().trimEnd(',', '.', '!', '?')
        val isNew = Regex("^(new|start)( a | an | )(new )?(chat|conversation)$").matches(t)
        val isCatchUp = t.contains("catch me up") || t.contains("what did i miss") ||
            t.contains("read my conversations") || t.contains("hear my conversations") ||
            t == "updates" || t == "catch up"
        val isOpen = t.contains("open conversations") || t.contains("show conversations") ||
            t.contains("list conversations")
        return when {
            isNew -> { tts.speakNow("New conversation."); newConversation(); true }
            isCatchUp -> { catchMeUp(); true }
            isOpen -> { loadConversations(); true }
            else -> false
        }
    }

    // ---- conversation management ----

    fun newConversation() {
        activeCall?.cancel()
        activeCall = null
        tickerJob?.cancel()
        turnActive.set(false)
        tts.stopSpeaking()
        asr.stop()
        conversationId = ""
        prefs.conversationId = ""
        chatStore.clear()
        consecutiveAsrErrors = 0
        ui.value = UiState()
        if (prefs.autoListen && prefs.token.isNotBlank()) {
            viewModelScope.launch(Dispatchers.Main) {
                delay(500)
                if (ui.value.phase == Phase.Idle) startListening()
            }
        }
    }

    // ---- settings ----

    fun updateSettings(transform: (SettingsState) -> SettingsState) {
        settings.update(transform)
        val s = settings.value
        prefs.token = s.token
        prefs.modelName = s.model
        prefs.personaId = s.persona
        prefs.speakResponses = s.speak
        prefs.autoListen = s.handsFree
        prefs.speechRate = s.rate
        prefs.speechPitch = s.pitch
        tts.rate = s.rate
        tts.pitch = s.pitch
        tts.enabled = s.speak
    }

    fun loadModels() {
        if (modelsLoading.value) return
        modelsLoading.value = true
        modelsError.value = null
        viewModelScope.launch {
            try {
                models.value = ZoApi.listModels(prefs.token)
            } catch (t: Throwable) {
                modelsError.value = friendly(t)
            } finally {
                modelsLoading.value = false
            }
        }
    }

    fun loadPersonas() {
        if (personasLoading.value) return
        personasLoading.value = true
        personasError.value = null
        viewModelScope.launch {
            try {
                personas.value = ZoApi.listPersonas(prefs.token)
            } catch (t: Throwable) {
                personasError.value = friendly(t)
            } finally {
                personasLoading.value = false
            }
        }
    }

    override fun onCleared() {
        activeCall?.cancel()
        tickerJob?.cancel()
        asr.stop()
        tts.shutdown()
        super.onCleared()
    }
}

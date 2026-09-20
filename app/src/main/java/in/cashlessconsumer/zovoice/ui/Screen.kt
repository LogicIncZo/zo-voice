package `in`.cashlessconsumer.zovoice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.update
import `in`.cashlessconsumer.zovoice.AppViewModel
import `in`.cashlessconsumer.zovoice.Phase
import `in`.cashlessconsumer.zovoice.UiState
import `in`.cashlessconsumer.zovoice.data.ChatMessage

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003259),
    primaryContainer = Color(0xFF1B4B77),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBBC7DB),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF23282E),
    onSurfaceVariant = Color(0xFFC1C7CF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ZoVoiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

@Composable
fun ZoVoiceApp(vm: AppViewModel) {
    var screen by remember { mutableStateOf("chat") }
    ZoVoiceTheme {
        BackHandler(enabled = screen == "settings") { screen = "chat" }
        if (screen == "settings") {
            SettingsScreen(vm = vm, onBack = { screen = "chat" })
        } else {
            ChatScreen(vm = vm, onOpenSettings = { screen = "settings" })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: AppViewModel, onOpenSettings: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening()
        else vm.ui.update { it.copy(error = "Microphone permission denied — enable it in system settings, or type below.") }
    }

    val micTap: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) vm.micPressed() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Zo Voice", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = { vm.newConversation() }) {
                        Icon(Icons.Filled.Add, contentDescription = "New conversation")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (ui.messages.isEmpty() && ui.phase == Phase.Idle && ui.partial.isEmpty()) {
                EmptyState(tokenSet = settings.token.isNotBlank())
            } else {
                Transcript(ui = ui, modifier = Modifier.weight(1f))
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = statusLine(ui),
                    color = if (ui.error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                MicButton(phase = ui.phase, onTap = micTap)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Voice in, voice out — Zo keeps the thread.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                TextFallbackRow(vm = vm, enabled = ui.phase == Phase.Idle || ui.phase == Phase.Listening)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun statusLine(ui: UiState): String = when (ui.phase) {
    Phase.Listening -> "Listening…"
    Phase.Thinking -> buildString {
        append("Thinking · ${ui.elapsedSec}s")
        ui.status?.let { append(" · $it") }
    }
    Phase.Speaking -> "Speaking… tap the button to interrupt"
    Phase.Idle -> ui.error ?: "Tap the mic and talk"
}

@Composable
private fun MicButton(phase: Phase, onTap: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "mic")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "scale"
    )
    val isListening = phase == Phase.Listening
    val busy = phase == Phase.Thinking || phase == Phase.Speaking
    FloatingActionButton(
        onClick = onTap,
        shape = CircleShape,
        containerColor = if (isListening) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.primaryContainer,
        contentColor = if (isListening) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .size(84.dp)
            .scale(if (isListening) scale else 1f)
    ) {
        Icon(
            imageVector = if (busy) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (busy) "Stop" else "Talk",
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun TextFallbackRow(vm: AppViewModel, enabled: Boolean) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            enabled = enabled,
            placeholder = { Text("…or type instead", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(22.dp)
        )
        Spacer(Modifier.size(8.dp))
        IconButton(
            onClick = {
                vm.sendText(text)
                text = ""
            },
            enabled = enabled && text.isNotBlank()
        ) {
            Icon(Icons.Filled.ArrowForward, contentDescription = "Send")
        }
    }
}

@Composable
private fun Transcript(ui: UiState, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        reverseLayout = true,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (ui.phase == Phase.Thinking || ui.partial.isNotBlank()) {
            item(key = "streaming") { StreamingBubble(partial = ui.partial, status = ui.status) }
        }
        items(ui.messages.reversed()) { msg -> Bubble(msg = msg) }
    }
}

@Composable
private fun Bubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                msg.text,
                Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                fontSize = 14.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun StreamingBubble(partial: String, status: String?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                Text(
                    if (partial.isBlank()) "…" else partial,
                    fontSize = 14.sp,
                    lineHeight = 19.sp
                )
                if (status != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(status, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(tokenSet: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Talk to your Zo", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Speak, hear the answer out loud, keep adding voice instructions. The conversation continues across sessions.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!tokenSet) {
            Spacer(Modifier.height(18.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "First run: open Settings and paste a Zo access token\n(Zo web app → Settings → Advanced → Access Tokens).",
                    Modifier.padding(14.dp),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    val modelsLoading by vm.modelsLoading.collectAsState()
    val modelsError by vm.modelsError.collectAsState()
    val personas by vm.personas.collectAsState()
    val personasLoading by vm.personasLoading.collectAsState()
    val personasError by vm.personasError.collectAsState()

    var showToken by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var personaMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    TextButton(onClick = onBack) { Text("Done") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionLabel("Connection")
            OutlinedTextField(
                value = s.token,
                onValueChange = { v -> vm.updateSettings { it.copy(token = v) } },
                label = { Text("Zo API access token") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) {
                        Text(if (showToken) "Hide" else "Show")
                    }
                }
            )
            Text(
                "Get one from your Zo web app → Settings → Advanced → Access Tokens. It grants full access to your Zo — keep it private.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("Model & persona (optional)")
            Box {
                OutlinedTextField(
                    value = s.model,
                    onValueChange = { v -> vm.updateSettings { it.copy(model = v) } },
                    label = { Text("Model name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("server default") },
                    trailingIcon = {
                        IconButton(onClick = {
                            vm.loadModels()
                            modelMenu = true
                        }) { Icon(Icons.Filled.Refresh, contentDescription = "Load models") }
                    }
                )
                DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                    when {
                        modelsLoading -> DropdownMenuItem(
                            text = { Text("Loading…") },
                            onClick = {}
                        )
                        modelsError != null -> DropdownMenuItem(
                            text = { Text(modelsError ?: "") },
                            onClick = {}
                        )
                        else -> models.forEach { m ->
                            DropdownMenuItem(
                                text = { Text("${m.label} · ${m.vendor}") },
                                onClick = {
                                    vm.updateSettings { it.copy(model = m.name) }
                                    modelMenu = false
                                }
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedTextField(
                    value = s.persona,
                    onValueChange = { v -> vm.updateSettings { it.copy(persona = v) } },
                    label = { Text("Persona id") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("active persona") },
                    trailingIcon = {
                        IconButton(onClick = {
                            vm.loadPersonas()
                            personaMenu = true
                        }) { Icon(Icons.Filled.Refresh, contentDescription = "Load personas") }
                    }
                )
                DropdownMenu(expanded = personaMenu, onDismissRequest = { personaMenu = false }) {
                    when {
                        personasLoading -> DropdownMenuItem(text = { Text("Loading…") }, onClick = {})
                        personasError != null -> DropdownMenuItem(
                            text = { Text(personasError ?: "") },
                            onClick = {}
                        )
                        else -> personas.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name) },
                                onClick = {
                                    vm.updateSettings { it.copy(persona = p.id) }
                                    personaMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            SectionLabel("Voice")
            SettingSwitch(
                title = "Speak responses",
                subtitle = "Reads Zo's answers out loud as they stream in.",
                checked = s.speak,
                onChange = { v -> vm.updateSettings { it.copy(speak = v) } }
            )
            SettingSwitch(
                title = "Hands-free loop",
                subtitle = "Re-opens the mic after each answer so you can keep talking.",
                checked = s.handsFree,
                onChange = { v -> vm.updateSettings { it.copy(handsFree = v) } }
            )
            Text("Speech rate · %.1fx".format(s.rate), fontSize = 13.sp)
            Slider(
                value = s.rate,
                onValueChange = { v -> vm.updateSettings { it.copy(rate = v) } },
                valueRange = 0.5f..2f
            )
            Text("Pitch · %.1f".format(s.pitch), fontSize = 13.sp)
            Slider(
                value = s.pitch,
                onValueChange = { v -> vm.updateSettings { it.copy(pitch = v) } },
                valueRange = 0.5f..2f
            )

            Spacer(Modifier.height(18.dp))
            SectionLabel("Conversation")
            Button(onClick = { vm.newConversation() }) { Text("Start a new conversation") }
            Text(
                "Starts a fresh Zo session (new conversation id). The transcript on this device is cleared.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(
                "Zo Voice v0.1.0 · talks to https://api.zo.computer/zo/ask with streaming.\n" +
                    "Speech-to-text and text-to-speech run on-device via Android's speech services.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

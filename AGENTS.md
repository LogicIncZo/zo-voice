# zo-voice — project notes

Android voice client for Zo. Kotlin 2.0.21 + Compose (BOM 2024.10.01) + OkHttp 4.12.
Package: `in.cashlessconsumer.zovoice` (namespace string in app/build.gradle.kts is unescaped).

## Ground-truth Zo API facts (verified 2026-09-20 against live API)

- Auth: `Authorization: Bearer <token>` (access token from Zo Settings → Advanced → Access Tokens).
- `POST /zo/ask` body: `{input, stream, conversation_id?, model_name?, persona_id?}`.
  Response (non-stream): `{output, conversation_id}`. Streaming: SSE, conversation id comes
  from the `x-conversation-id` response header (not the body).
- Real stream events (differ from the docs page):
  - `PartStartEvent`: `data.part.content`, `part.part_kind` ∈ {`text`, `thinking`}. Append only `text`.
  - `PartDeltaEvent`: `data.delta.content_delta`, `delta.part_delta_kind` ∈ {`text`, `thinking`}.
  - `AgentRuntimeStreamChunk`: `{type: "status"|"persisted"..., data: {message, phase?}}` → status line.
  - `FrontendModelRequest` (echo of request — ignore), `completed` (`data.status: succeeded|...`), `Error`.
- `GET /models/available` → `{models: [{model_name, label, vendor, is_byok}]}`;
  `GET /personas/available` → `{personas: [{id, name}]}`.
- `GET /conversations` + `GET /conversations/{id}` exist but 401 the internal identity
  token — they expect a `zo_sk_` user access token (verified live 2026-09-20; response
  shape undocumented, so `ZoConversations.kt` parses shape-tolerantly: bare array or
  wrapped object, field aliases for id/title/updated/preview, ISO or epoch timestamps).
  `ZoConversations.speakableDigest` renders history as spoken "You said / Zo said" text.
- MCP `api.zo.computer/mcp` (identity token) exposes the 104 agent tools only — no
  conversation listing; zo-tui has no prior art either.

## Gotchas (hit during first build — do not regress)

- **K2 rejects `in` in package/import paths unless backtick-escaped.** Every `.kt` file uses
  ``package `in`.cashlessconsumer.zovoice`` / ``import `in`.…``. Kotlin 1.x accepted bare `in.`; K2 (2.0+)
  does not. New files MUST follow this. applicationId/namespace stay as plain strings.
- TopAppBar takes colors via `TopAppBarDefaults.topAppBarColors(containerColor = …)`,
  not a direct `containerColor` param (Scaffold does have a direct one).
- Material3 `@OptIn(ExperimentalMaterial3Api::class)` on anything using TopAppBar.
- Mic/Stop icons need `material-icons-extended` (core set lacks them).
- AGP 8.7.3 + Gradle 8.10 + JDK 17 + compileSdk 35; buildToolsVersion pinned 34.0.0.
- Launcher icon: adaptive (`mipmap-anydpi-v26` → `@drawable/ic_launcher_foreground` full-bleed
  432px + `@color/ic_launcher_background` #0B0F19) + legacy density PNGs. Source art:
  `docs/logo.png` (generated, wing-over-waveform). Sandbox builds: keep
  `kotlin.compiler.execution.strategy=in-process` in gradle.properties — a separate Kotlin
  daemon OOM-killed the container once.

## Architecture

- `AppViewModel` owns the loop: `Idle → Listening → Thinking → Speaking → (hands-free) Listening`.
  ASR/TTS callbacks converge on Main; Zo stream deltas arrive on an OkHttp thread and are
  funnelled through thread-safe `MutableStateFlow.update` + `TtsManager.feed` (synchronized buffer).
- `ZoApi.ask` is callback-based (not suspend) so `Call.cancel()` is the single cancel path
  (stop button / barge-in / new chat); canceled calls are filtered by `call.isCanceled()`.
- `TtsManager` sentence-chunks the stream (`. ! ? \n`), sanitizes markdown, enqueues with
  QUEUE_ADD; fires queue-empty only after `streamComplete()` (turn finished) so listening
  never restarts mid-turn.
- Hands-free: after queue drains → `maybeAutoListen()` (900 ms debounce); consecutive ASR
  errors capped at 4 to avoid hot loops.
- State: prefs (SharedPreferences), transcript (`filesDir/conversation.json`, 300-msg cap),
  `conversation_id` persisted → conversations resume across process death.

## Build / verify

The agent loop — one command, no emulator:

```bash
make verify        # unit tests + lint + debug build
make apk           # verify + copy APK to releases/
make test          # JVM unit tests only (fastest signal)
./gradlew compileDebugKotlin   # fastest compile error check
```

Unit tests (22, `app/src/test/`) cover the pure-logic core: `ZoSseParser`,
`SentenceChunker`, `ZoConversations` parsing/digests. Keep new logic out of Android framework classes so it
stays JVM-testable; `org.json` on the JVM comes from `testImplementation
org.json:json` (Android ships a stub).

Legacy granular commands:

```bash
./gradlew assembleDebug        # debug APK
./gradlew compileDebugKotlin   # fast error check
```

Local SDK: `local.properties` → `sdk.dir=/opt/android-sdk` (gitignored).

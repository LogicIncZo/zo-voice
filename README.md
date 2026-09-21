# Zo Voice

<p align="center"><img src="docs/logo.png" width="160" alt="Zo Voice logo"/></p>

[![android](https://github.com/LogicIncZo/zo-voice/actions/workflows/android.yml/badge.svg)](https://github.com/LogicIncZo/zo-voice/actions/workflows/android.yml)

Talk to your Zo from Android. Voice in, voice out — hands-free follow-ups, conversations
that continue across sessions, live streaming replies spoken as they arrive.

Built on the Zo API (`POST /zo/ask` with SSE streaming) and Android's built-in
`SpeechRecognizer` (STT) + `TextToSpeech` (TTS). No third-party speech services,
no extra API keys beyond your Zo access token.

## Engineering loop

```bash
make verify   # JVM unit tests + lint + build — no emulator needed
make apk      # verify + drop APK in releases/
```

Networking + protocol logic (SSE parsing, `/zo/ask`, conversations, sentence chunking)
lives in the [zo-kotlin SDK](https://github.com/LogicIncZo/zo-kotlin) (`dev.zocomputer:ask`),
consumed from source via Gradle composite build when `../zo-kotlin` exists, else from Maven.
App side keeps thin typealiases (`data/ZoApi.kt`, `data/ZoConversations.kt`,
`voice/SentenceChunker.kt`) and `data/ZoBridge.kt` (ChatMessage ↔ HistoryMessage).

See CONTRIBUTING.md for conventions and known gotchas.

## Auto-update (v0.4.0)

On launch (+ every 6h while open) the app checks GitHub Releases
(`repos/LogicIncZo/zo-voice/releases/latest`), compares `tag_name` against the
installed `versionName`, and on a strictly-newer tag shows an Update card
(version + notes head + size). Install streams the release APK through
FileProvider to the system package installer (REQUEST_INSTALL_PACKAGES;
user approves once in Android settings for sideloaded apps). Dismissals are
remembered per tag. Manual re-check lives in Settings → Updates.

## Conversations & catch-up (v0.2.0)

- **Conversations screen** — lists your existing Zo conversations (`GET /conversations`).
  Tap to continue one (history loads into the transcript, hands-free picks up where it left off).
- **Hear updates** — the speaker button on any conversation reads its latest messages aloud;
  the megaphone in the chat top bar does a spoken catch-up of your most recent conversation.
- **Voice commands** — just say *"catch me up"*, *"new conversation"*, or *"open conversations"*;
  they're handled locally, no Zo round-trip.

## Install

- Grab the prebuilt APK: `releases/zo-voice-v0.1.0-debug.apk` (debug-signed; sideload it —
  "install unknown apps" permission needed). Requires Android 8.0+ (minSdk 26).
- Or build from source (below) / CI artifact from the `android` GitHub Actions workflow.

## Setup

1. Open Zo Voice → Settings (gear icon).
2. Paste your Zo API access token. Get one from your Zo web app:
   Settings → Advanced → **Access Tokens**. The token grants full access to your Zo —
   keep it private.
3. Optional: pick a model or persona (`Load models` / `Load personas` fetch from
   `/models/available` and `/personas/available`), toggle **Speak responses** and
   **Hands-free** (auto-listen), set speech rate/pitch.

Tap the mic, talk. Zo's answer streams in (you see it live) and is spoken sentence-by-sentence
as text arrives. With hands-free on, the mic re-opens automatically after each answer —
just keep talking. Tap mic while Zo is speaking to barge in.

## Features

- Continuous voice conversation (`conversation_id` persisted across app restarts)
- Streaming SSE → sentence-chunked TTS (hears the answer early, not after the whole run)
- Live status while Zo works ("Thinking · 12s · Using tools…")
- Barge-in: tap to interrupt speech and talk again
- Text fallback input (type instead of talk)
- Model + persona pickers, rate/pitch controls
- Transcript persisted locally (`conversation.json`); "new chat" starts a fresh Zo conversation

## Build

```bash
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/app-debug.apk
```

Android Studio (Hedgehog+) or CLI with JDK 17 + Android SDK 35. CI: `.github/workflows/android.yml`.

## How it talks to Zo

| Call | Purpose |
| --- | --- |
| `POST /zo/ask` `{input, stream:true, conversation_id?, model_name?, persona_id?}` | Ask; SSE stream |
| `Authorization: Bearer <token>` | Auth |
| `x-conversation-id` response header | Conversation continuity |
| `GET /models/available`, `GET /personas/available` | Settings pickers |

The zo-kotlin SDK handles the wire: it speaks the SSE protocol below and
exposes `ZoApi`, `ZoConversations`, `ZoSseParser`, `SentenceChunker` with 33 JVM tests.

Stream events handled: `PartStartEvent` + `PartDeltaEvent` (only `part_kind`/`part_delta_kind`
== `text`; `thinking` parts are skipped), `AgentRuntimeStreamChunk` (status line), `completed`
(success/failure), `Error`. Full notes in `AGENTS.md`.

## Limitations / roadmap

- The platform recognizer bleeps and needs network on many devices (install Google speech
  services for best accuracy; on-device recognition varies by OEM).
- No wake word — tap to start (or hands-free loop). Background/lock-screen use not wired yet.
- TTS voice quality is the system engine's; fenced code blocks are summarized as "(code)".
- Tool-heavy Zo turns can take a while — the status line keeps you posted.

GPL-3.0-or-later.

# Contributing

Agentic-loop-friendly: one command verifies everything, and the pieces that decide
what gets spoken are pure Kotlin with JVM unit tests (no emulator required).

## One-shot verify

```bash
make verify   # = ./gradlew testDebugUnitTest lintDebug assembleDebug
make apk      # verify + copy APK to releases/
```

CI runs the same three tasks on every push to `main` and uploads the APK artifact.

## Conventions

- Atomic commits: one concern per commit, imperative subject.
- UI is Compose + Material 3; logic goes in pure Kotlin classes (`data/`, `voice/`)
  so it stays unit-testable. New parse/speak logic must ship with tests in
  `app/src/test/`.
- Do not bump Kotlin/AGP/Compose versions casually — the build is pinned to a
  known-good matrix (Kotlin 2.0.21, AGP 8.7.3, compileSdk 35, JDK 17).

## Known gotchas

- **Package name `in.*` is not a typo** — CashlessConsumer's domain. Kotlin 2/K2
  requires backticks because `in` is a keyword: `package `in`.cashlessconsumer.zovoice`
  and `import `in`.cashlessconsumer.zovoice...`. Keep them.
- Material3 `TopAppBar` colors go through `TopAppBarDefaults.topAppBarColors(...)`,
  not a bare `containerColor` parameter.
- Unit tests: `org.json` ships as an Android stub — the real implementation comes
  from `testImplementation("org.json:json:20240303")`.
- Zo SSE stream facts (event names, delta shapes) were verified live against
  `api.zo.computer` on 2026-09-20 — see `data/ZoSseParser.kt` before trusting
  docs alone.

## Where things live

- `data/ZoSseParser.kt` — SSE event parsing (what becomes text)
- `data/ZoApi.kt` — HTTP + streaming client for `POST /zo/ask`
- `voice/SentenceChunker.kt` — sentence segmentation + markdown stripping (what gets spoken)
- `voice/` — SpeechRecognizer + TTS managers, hands-free loop in `AppViewModel`
- `ui/Screen.kt` — Compose UI

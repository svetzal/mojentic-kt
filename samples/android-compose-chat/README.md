# android-compose-chat

Reference integration showing how to consume **mojentic-core + mojentic-openai** from an Android Compose application.

> **This is a documentation sample, not part of the library distribution.** It lives under `samples/` deliberately — it's not in `settings.gradle.kts`, not built by CI, and not published to Maven Central. Copy the relevant files into an Android Studio project that you create separately.

## What it demonstrates

- Holding a `ChatSession` inside a Compose `ViewModel`.
- Streaming the assistant's reply into a `MutableStateFlow<List<UiMessage>>` so the Compose tree recomposes per token.
- Cancelling the in-flight turn via `Job.cancel()` from the UI (back-pressure on barge-in).
- Keeping the API key out of source — read it from `BuildConfig`, env, or a secrets manager — never hardcode.

## Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Compose entry point — hosts `ChatScreen`. |
| `ChatViewModel.kt` | Owns the `ChatSession`, exposes `messages: StateFlow<List<UiMessage>>` and `send(text: String)`. |
| `ChatScreen.kt` | Stateless Composable rendering the message list + input box. |
| `AndroidManifest.xml` | Minimal manifest declaring `MainActivity` and INTERNET permission. |
| `strings.xml` | App name. |

## How to use these files

1. Create a new Android Studio project: **Empty Compose Activity**, package `com.example.mojenticchat`, minSdk 24, compileSdk 36, AGP 9.2.0+.
2. Copy `MainActivity.kt`, `ChatViewModel.kt`, `ChatScreen.kt` into `app/src/main/java/com/example/mojenticchat/`.
3. Copy `AndroidManifest.xml` over the generated one (or merge the INTERNET permission line).
4. In `app/build.gradle.kts`, add dependencies on:
   - `com.mojentic:mojentic-core` (when published to Maven Central)
   - `com.mojentic:mojentic-openai`
   - `androidx.lifecycle:lifecycle-viewmodel-compose`
   - The latest Compose BOM
5. Set `OPENAI_API_KEY` in `local.properties` or your build environment and wire it through `BuildConfig.OPENAI_API_KEY` (see Android Studio docs for the standard pattern).
6. Build and run.

## Why this is not a separate Gradle module

The Kotlin port's `mojentic-core` and friends are Kotlin Multiplatform *library* modules. Building an Android *application* alongside the libraries would require adding the Android Application plugin, Compose plugin, Compose runtime, ~6 androidx artefacts (lifecycle, activity-compose, material3, ui-tooling, etc.), and Compose BOM-managed version alignment — all for code that's never consumed by the library distribution.

That payload would dwarf the actual library deps and dilute the message that this *is* a library, not an application framework. A documented sample with cut-and-paste source files communicates the integration pattern just as clearly without the build-system entanglement.

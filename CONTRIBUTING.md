# Contributing

Thanks for considering a contribution to WonderWhisper.

## Ground Rules

- Keep changes focused and reviewable.
- Do not commit API keys, keystores, release APKs/AABs, `.dev.vars`, `local.properties`, or generated build folders.
- Prefer small pull requests with clear testing notes.
- Follow the existing Kotlin and Jetpack Compose style.
- Add or update tests when changing provider selection, storage, permissions, or transcription behavior.

## Development Setup

1. Fork and clone the repository.
2. Open the project in Android Studio.
3. Let Gradle sync.
4. Add provider API keys inside the running app, not in source code.
5. Build with:

```bash
./gradlew assembleDebug
```

Useful checks:

```bash
./gradlew testDebugUnitTest
./gradlew lint
```

## Pull Requests

Please include:

- What changed
- Why it changed
- How you tested it
- Screenshots or screen recordings for UI changes
- Any provider/account setup needed to reproduce behavior

## Reporting Bugs

When filing a bug, include:

- Android version and device model
- App version or commit SHA
- Selected transcription provider and AI provider
- Whether the issue happens in Simple Mode, Pro Mode, or both
- Relevant logs with secrets removed

## Feature Requests

Feature requests are welcome. Please describe the workflow, not only the UI idea. The most useful requests explain what you are trying to do, where the app falls short, and what behavior you expected.

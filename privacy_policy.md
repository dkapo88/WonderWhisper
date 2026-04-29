# Privacy Notes for WonderWhisper

WonderWhisper is an open-source Android dictation app. This document describes the intended data flow for the public source tree. If you distribute a modified build, review and update this document for your exact configuration.

## Local Data

WonderWhisper may store the following data locally on the device:

- App settings
- Provider API keys
- Custom vocabulary
- Activity logs
- Temporary or saved audio files, depending on user settings

API keys are stored with AndroidX Security Crypto through the app's secure key manager.

## Cloud Providers

WonderWhisper can send audio and text to third-party providers when the user chooses cloud-backed transcription or AI cleanup. Supported providers may include Groq, OpenAI, Google Gemini, ElevenLabs, AssemblyAI, Deepgram, Mistral, OpenRouter, and Soniox.

Those providers process data under their own terms and privacy policies. Users should review provider policies before adding API keys.

## Accessibility Permission

WonderWhisper uses Android accessibility APIs to detect editable fields and insert dictated text. Accessibility access should be enabled only if the user understands and accepts this behavior.

## Clipboard

Some fallback insertion and command-mode flows may use clipboard context. Clipboard handling should remain time-limited and should avoid retaining sensitive content longer than needed.

## Maintainers and Distributors

If you publish your own build:

- Do not include provider API keys.
- Do not include signing keys or keystores.
- Document any hosted proxy or server-side processing you add.
- Update this privacy note to match your build.

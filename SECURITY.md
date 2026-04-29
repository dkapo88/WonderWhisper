# Security Policy

## Reporting Security Issues

Do not report sensitive security issues in public GitHub issues.

If you find a leaked key, credential handling issue, or vulnerability that could expose user data, please contact the maintainer privately through the contact options on the maintainer's GitHub profile.

## Secrets

WonderWhisper should not contain committed provider API keys. Users configure their own keys in the app.

Never commit:

- API keys
- Android signing keys or keystores
- `.dev.vars`
- `local.properties`
- release APK/AAB artifacts
- Gradle properties containing secrets

## Supported Versions

This is a volunteer-maintained open-source project. Security fixes are handled on a best-effort basis against the current public source.

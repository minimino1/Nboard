# Nboard

![Version](https://img.shields.io/badge/version-1.0.1-yellow)
![Android](https://img.shields.io/badge/android-8.0%2B%20(API%2026)-grey)
![License](https://img.shields.io/badge/license-AGPL--3.0-lightgrey)

<p align="center">
  <img src="docs/media/logo.png" alt="Nboard logo" width="96" height="96" />
</p>

<p align="center">
  Minimal Android keyboard with AI tools, clipboard power features, emoji search, and fast typing UX.
</p>

<p align="center">
  <a href="https://github.com/MathieuDvv/Nboard/releases">Download APK</a>
  ·
  <a href="https://github.com/MathieuDvv/Nboard/releases/latest">Download Latest Release</a>
  ·
  <a href="#build-locally">Build Locally</a>
  ·
  <a href="#features">Features</a>
</p>

## About

Built for Nothing Phone users who wanted a keyboard matching their device's minimal aesthetic — works great on any Android phone.

## Screenshots

| Keyboard | AI tools |
|---|---|
| ![Keyboard](docs/promo/keyboard.png) | ![AI tools](docs/promo/ai-tools.png) |

| Clipboard | Emoji |
|---|---|
| ![Clipboard](docs/promo/clipboard.png) | ![Emoji keyboard](docs/promo/emoji-keyboard.png) |

## Features

- AZERTY and QWERTY layouts
- Smart shift behavior (auto-capitalize, one-shot shift, caps lock)
- Local autocorrect (French / English / Both / Disabled)
- Long-press variants for letters and punctuation
- Spacebar cursor swipe
- Haptics and press animations
- Clipboard history with pin/delete + recent chip for text/images
- Emoji browser + search mode
- AI tools (Summarize, Fix Grammar, Expand, free prompt)
- Contextual action icon based on input field type
- Theme options: `System`, `Light`, `Dark`, `Dark (Classic)`
- Font options: `Inter` / `Roboto`
- Configurable side mode keys (AI / Clipboard / Emoji)

## Beta Features

- Word Prediction *(experimental)*
- Swipe Typing *(experimental)*

These features are currently in beta and may have occasional issues. We're actively improving them. Feedback welcome!

## Support Development

If you want to support Nboard development:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/dotslimy)

## Install APK (GitHub Releases)

1. Open [Releases](https://github.com/MathieuDvv/Nboard/releases).
2. Download the latest assets (`.apk` + source `.zip`).
3. Install on device:

```bash
adb install -r path/to/NBoard-v1.0.1-release.apk
```

4. On Android, enable **Nboard** in keyboard settings.
5. Select **Nboard** as your current keyboard.

Minimum Android version: **Android 8.0 (API 26)**.

## Build Locally

### Requirements

- Android Studio or Android SDK + JDK 17
- `adb` available in PATH
- Android 8.0+ target device/emulator (API 26+)

### Build

```bash
./gradlew assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install debug APK:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

- `app/src/main/java/com/nboard/ime/NboardImeService.kt` — keyboard engine and UI behavior
- `app/src/main/java/com/nboard/ime/MainActivity.kt` — settings app
- `app/src/main/java/com/nboard/ime/OnboardingActivity.kt` — onboarding flow
- `app/src/main/java/com/nboard/ime/KeyboardModeSettings.kt` — persisted preferences
- `app/src/main/java/com/nboard/ime/ai/GeminiClient.kt` — Gemini API client
- `app/src/main/java/com/nboard/ime/clipboard/ClipboardHistoryStore.kt` — clipboard persistence
- `app/src/main/res/layout/keyboard_view.xml` — keyboard layout

## AI Key Setup (Optional)

If you want AI features locally:

1. Copy `local.properties.example` to `local.properties`
2. Add your Gemini key:

```properties
GEMINI_API_KEY=YOUR_API_KEY_HERE
```

You can also set/update the key directly from the app settings.

## Privacy & Security

- AI features require internet access and send prompt text to the Gemini API.
- Clipboard history is stored locally on device.
- No telemetry or usage tracking is implemented.
- Nboard is open source, so behavior is fully auditable.

## What's Next (v1.1)

- Voice input
- Password autofill (AutofillManager integration)
- GIF search
- Improved swipe typing accuracy
- More AI quick actions
- Additional language support

## Contributing & Feedback

- Bug reports: [GitHub Issues](https://github.com/MathieuDvv/Nboard/issues)
- Feature requests/discussion: [GitHub Discussions](https://github.com/MathieuDvv/Nboard/discussions)
- Feedback from real typing usage is very useful and helps prioritize improvements.

## Troubleshooting

- **Keyboard doesn't appear**
  - Open Android settings.
  - Go to keyboard/input method settings.
  - Enable **Nboard** and set it as active keyboard.
  - Re-open the target app input field.
- **AI features not working**
  - Confirm internet connection is available.
  - Verify Gemini API key is set correctly.
  - Check Gemini API quota/billing limits.
- **Swipe typing not working**
  - Swipe Typing is still beta.
  - Make sure it is enabled in settings.
  - Update to the latest release and retry.
- **App crashes**
  - Please report with steps and device info in [GitHub Issues](https://github.com/MathieuDvv/Nboard/issues).

## Notes

- Behavior depends on host app editor support.
- Image paste support depends on target input accepting rich content.
- Design is heavily inspired by Nothing and its aesthetic.

## License

Licensed under **AGPL-3.0** — see [`LICENSE`](LICENSE).

Free for personal use. For commercial licensing inquiries, contact: `mathieu.davinha83@gmail.com`.

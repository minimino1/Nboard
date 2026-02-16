# Nboard

![Version](https://img.shields.io/badge/version-1.3.0-yellow)
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
  ·
  <a href="#contributing--feedback">Contributing</a>
</p>

## About

Built for Nothing Phone users who wanted a keyboard matching their device's minimal aesthetic — works great on any Android phone.

## Project Status

Nboard is **feature-complete** and in maintenance mode after v1.3.0.

I built this keyboard to solve my own problem: wanting a Nothing-inspired keyboard with AI tools. It works great, and I use it daily.

I'll fix critical bugs, but I'm not committing to major new features. The project does what it's meant to do.

If you want additional features, fork it! It's open source (AGPL-3.0) for exactly this reason.

### Dev Note (Branch Direction)

The project currently has two release tracks:

- `v1.3.0` = legacy/stable line (this `main` branch baseline)
- `v1.4.0` = new layout-system line (started as `v1.4.0-beta`, now continuing as `v1.4.0+`) on `feat/layout-pack-import`

There is currently **no merge planned** between these two lines.

Use whichever line fits your needs:
- Legacy experience: stay on `v1.3.0`
- New community layout system: use `v1.4.0` releases

## Screenshots

| Keyboard | AI tools |
|---|---|
| ![Keyboard](docs/promo/keyboard.png) | ![AI tools](docs/promo/ai-tools.png) |

| Clipboard | Emoji |
|---|---|
| ![Clipboard](docs/promo/clipboard.png) | ![Emoji keyboard](docs/promo/emoji-keyboard.png) |

## Features

- AZERTY and QWERTY layouts with classic and Gboard variants
- Smart shift behavior (auto-capitalize, one-shot shift, caps lock)
- Smart typing assists (auto-space after sentence punctuation, auto-capitalize after punctuation, return to letters after `number + space`)
- Local autocorrect + prediction (French / English / Both / Disabled) with dictionary + n-gram scoring and on-device learning
- Long-press variants for letters and punctuation (`!`, `?`, `;`, accents)
- Gboard punctuation row improvements (`','`, `'.'`, and `'`) with adaptive left punctuation key (`/` in URL fields, `@` in email fields)
- Optional number row toggle in settings
- Spacebar cursor swipe
- Haptics and press animations
- Clipboard history with pin/delete + recent chip for text/images
- Emoji browser + search mode
- AI tools (Summarize, Fix Grammar, Expand, free prompt with selected-text context support, language preserved by default)
- Contextual action icon based on input field type
- Theme options: `System`, `Light`, `Dark`, `Dark (Classic)`
- Font options: `Inter` / `Roboto`
- Configurable side mode keys (AI / Clipboard / Emoji) on classic layouts
- Gboard tool key behavior with press-and-hold quick access for AI / Clipboard / Emoji

## Beta Features

- Swipe Typing *(experimental)*
- Voice recognition (hold send) *(experimental)*
- Word prediction *(experimental)*

These features are currently in beta and may have occasional issues.

## How Autocorrect and Prediction Work

### Autocorrect (local, on-device)

- Nboard loads French and English frequency dictionaries from local assets.
- When a typed word is unknown, it generates close candidates (1 edit away): deletion, swap, replacement, insertion.
- It ranks candidates by frequency first, then keeps the closest/shortest match.
- In bilingual mode, the previous word gives a lightweight language hint (French or English) to prioritize suggestions.
- A trie + in-memory cache are used to keep lookup speed fast while typing.

### Word prediction (local, on-device)

- Nboard uses unigram frequencies (single-word popularity) and bigram frequencies (next-word pairs).
- If there is a previous word, bigram candidates are preferred.
- If there is no strong previous-word match, it falls back to top unigram matches.
- In bilingual mode, French and English candidates are merged with simple frequency-based ranking.
- The prediction bar returns up to 3 suggestions.

## Language Support and APK Size

Nboard currently focuses on **French** and **English**.

- **French** is first-class because I am French, the app is used by French users, and it supports **AZERTY** workflows.
- **English** is included because it is the most widely used language and improves everyday compatibility.

The app is bigger now mainly because of new local dictionary assets used by autocorrect and prediction:

- `app/src/main/assets/dictionaries/english_50k.txt`
- `app/src/main/assets/dictionaries/french_50k.txt`
- `app/src/main/assets/dictionaries/english_bigrams.txt`
- `app/src/main/assets/dictionaries/french_bigrams.txt`

These files increase APK size, but they keep correction/prediction fast and on-device (no network call required for core typing intelligence).

## Support Development

If you want to support Nboard development:

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/dotslimy)

## Install APK (GitHub Releases)

1. Open [Releases](https://github.com/MathieuDvv/Nboard/releases).
2. Download the latest assets (`.apk` + source `.zip`).
3. Choose your track:
   - `NBoard-v1.3.0-release.apk` (legacy line)
   - `NBoard-v1.4.0-release.apk` (new layout-system line, started as beta)
4. Install on device:

```bash
adb install -r path/to/NBoard-v1.4.0-release.apk
```

5. On Android, enable **Nboard** in keyboard settings.
6. Select **Nboard** as your current keyboard.

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

- `app/src/main/java/com/nboard/ime/NboardImeService.kt` — IME lifecycle + composition root
- `app/src/main/java/com/nboard/ime/NboardImeTextInput.kt` — key commit/delete pipeline, shift logic, smart typing integration
- `app/src/main/java/com/nboard/ime/NboardImeAutoCorrection.kt` — dictionary/variant correction flow
- `app/src/main/java/com/nboard/ime/NboardImeEmojiPrediction.kt` — emoji + prediction row rendering
- `app/src/main/java/com/nboard/ime/NboardImeBottomModes.kt` — bottom mode state and UI transitions
- `app/src/main/java/com/nboard/ime/NboardImeVoice.kt` — voice capture and transcript commit flow
- `app/src/main/java/com/nboard/ime/NboardImeClipboard.kt` — clipboard UI/history interactions
- `app/src/main/java/com/nboard/ime/AutoCorrect.kt` and `app/src/main/java/com/nboard/ime/BigramPredictor.kt` — local typing intelligence engines
- `app/src/main/java/com/nboard/ime/MainActivity.kt` — settings app
- `app/src/main/java/com/nboard/ime/OnboardingActivity.kt` — onboarding flow

## Developer Guide

Architecture and contributor onboarding are documented in [`DEVELOPER_GUIDE.md`](DEVELOPER_GUIDE.md).

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

## Future updates (Not planned yet)

- Password autofill (AutofillManager integration)
- GIF search

## Contributing & Feedback

- Contribution guidelines: [`CONTRIBUTING.md`](CONTRIBUTING.md)
- For bug fixes and feature PRs, read the scope and acceptance rules before starting work.
- Bug reports: [GitHub Issues](https://github.com/MathieuDvv/Nboard/issues)
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

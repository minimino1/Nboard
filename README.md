# Nboard

Nboard is a custom Android keyboard (`InputMethodService`) focused on a clean UI, fast typing, multilingual autocorrect (FR/EN), AI-assisted text actions, clipboard history, and emoji search.

## Current Features (V1)

- AZERTY / QWERTY layouts
- Smart shift behavior:
  - auto-capitalize sentence start
  - one-shot shift and caps lock
- Basic local autocorrect (no API):
  - French / English / Both modes
  - dictionary-based corrections
  - repeated-letter + multi-error handling
  - one-tap revert of last autocorrection
- Variant popups on long-press letters/punctuation (French-focused variants)
- Spacebar cursor swipe (Gboard-style)
- Haptics + press animations
- Clipboard system:
  - history list with pin/delete
  - recent clipboard chip
  - supports recent copied text and copied images preview/paste
- Emoji mode:
  - horizontal emoji browser
  - recent/most-used behavior
  - emoji search mode
- AI mode (Gemini):
  - free prompt
  - quick actions: Summarize / Fix Grammar / Expand
  - compact rewrite-style responses
- Contextual action button icon by input type / IME action
- Theme support: Light / Dark / System
- Font choice: Inter / Roboto
- Bottom key mode customization:
  - each side can be configured between pairs (AI, Clipboard, Emoji)
  - AI key keeps AI yellow style on whichever side AI is configured

## Project Structure

- `app/src/main/java/com/nboard/ime/NboardImeService.kt` – main keyboard logic
- `app/src/main/java/com/nboard/ime/MainActivity.kt` – settings screen
- `app/src/main/java/com/nboard/ime/KeyboardModeSettings.kt` – persisted user settings
- `app/src/main/java/com/nboard/ime/clipboard/ClipboardHistoryStore.kt` – clipboard storage
- `app/src/main/java/com/nboard/ime/ai/GeminiClient.kt` – Gemini HTTP client
- `app/src/main/res/layout/keyboard_view.xml` – keyboard UI layout

## Setup

1. Copy `local.properties.example` to `local.properties`.
2. Add your key:

```properties
GEMINI_API_KEY=YOUR_API_KEY_HERE
```

3. Build debug APK:

```bash
./gradlew assembleDebug
```

4. Install keyboard APK and enable **Nboard** in Android input settings.

## Notes

- This is a keyboard app prototype; behavior can vary depending on host app/editor capabilities.
- Image paste from clipboard depends on target field support for content commit.

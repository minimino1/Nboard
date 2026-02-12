# Upstream Base Recommendation

## Recommended Base

Use **AnySoftKeyboard** as upstream fork base for production hardening.

## Why This Base

- Mature IME codebase with broad language/input support
- Extensible architecture for additional keyboard behaviors
- Good fit for adding AI mode and clipboard panels without rebuilding core IME behaviors

## Integration Strategy

1. Start from upstream keyboard service and key rendering architecture.
2. Port the AI mode state machine from this prototype (`NboardImeService`).
3. Reuse Gemini client patterns from `GeminiClient.kt`.
4. Reuse clipboard persistence/panel logic from `ClipboardHistoryStore.kt` and chip rendering.
5. Keep the Nboard visual treatment (yellow AI accents, red default send button, compact row spacing).

## Security Notes

- Avoid hardcoding API keys in source control.
- For production, move key handling to a backend token exchange or managed proxy service.
- Keep strict network timeout + error handling in IME process to avoid blocking input.

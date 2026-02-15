# Changelog

All notable changes to this project are documented in this file.

## [1.3.0] - 2026-02-15

### Added

- Added Smart Typing assists: auto-space after sentence punctuation, auto-capitalize for the next letter, and auto-return from number layout to letters after `number + space`.
- Added stricter context-aware typing behavior gates using `EditorInfo.inputType` to disable assists in email, URL, password, username, number-only, and phone fields.
- Added upgraded local typing intelligence with stronger dictionary and n-gram (bigram/trigram) handling for prediction and autocorrect quality.
- Added a root developer architecture guide for contributors.

### Changed

- Refactored the IME implementation into focused modules (`NboardImeTextInput`, `NboardImeBottomModes`, `NboardImeAutoCorrection`, `NboardImeVoice`, `NboardImeClipboard`, `NboardImeEmojiPrediction`, etc.) instead of a large monolith.
- Reworked local autocorrect and prediction flow to improve readability, maintainability, and future extension points while keeping behavior on-device.
- Expanded automated test coverage across smart typing, autocorrect, prediction, trie logic, and layout mode behavior.

## [1.2.0] - 2026-02-14

### Added

- Added Gboard-style AZERTY and QWERTY layouts.
- Added Gboard punctuation keys (`','`, `'.'`, and `'`) with variants for `!`, `?`, and `;`.
- Added adaptive left punctuation key in Gboard layouts (`/` for URL fields, `@` for email fields).
- Added a settings toggle for the top number row.

### Changed

- Changed Gboard bottom-row proportions: narrower spacebar and balanced punctuation/tool key sizing.
- Changed Gboard tool access to a single key with hold menu for AI, Clipboard, and Emoji actions.
- Changed AI text transformation prompts to preserve the selected text language by default.

## [1.1.0] - 2026-02-13

### Added

- Added hold-to-talk voice recognition with live dictation and settings toggle.
- Added smoother, more precise swipe typing with an optional yellow trail.
- Added AI prompt selected-text context mode: prompt instructions can now apply directly to selected input text.
- Added voice recognition control to onboarding beta features.

### Changed

- Reorganized settings with a dedicated Beta Features section and conditional trail option visibility.
- Updated project roadmap to maintenance mode with future updates marked as not planned.

## [1.0.1] - 2026-02-13

### Fixed

- Fixed a keyboard crash when deleting items from clipboard history.
- Fixed emoji picker search insertion so selected emoji is committed to the app input field.
- Fixed prompt/search focus routing so typing can return to the host app text input instead of staying stuck in the inline pill.
- Added a `Disabled` mode for basic autocorrect in settings.

## [1.0.0] - 2026-02-13

### Added

- Initial public release of Nboard.
- AZERTY/QWERTY keyboard layouts.
- Local autocorrect for French/English/Both.
- AI tools (Summarize, Fix Grammar, Expand, free prompt).
- Clipboard history with pin/delete and recent clipboard chip.
- Emoji browser and emoji search mode.
- Onboarding flow and in-app settings.
- Theme modes: System, Light, Dark, Dark (Classic).
- Optional beta features: Word Prediction and Swipe Typing.

### Notes

- Word Prediction and Swipe Typing are marked beta in v1.0.0.

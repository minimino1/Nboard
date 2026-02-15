# Contributing to Nboard

Thank you for your interest in contributing to Nboard!

## Bug Reports

Bug reports are always welcome. Please include:
- Clear description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Device information (device model, Android version, Nboard version)
- Screenshots or logs if applicable

Open an issue using the "Bug Report" template.

## Bug Fix Pull Requests

Bug fix PRs are welcome and will likely be merged if:
- The fix resolves the issue described
- It doesn't break existing functionality
- Code follows the existing style
- You've tested it on your device

## Feature Contributions

Before starting work on a new feature, please note that Nboard has a focused scope. See the README section "What Won't Be Implemented" for features that won't be accepted.

For features not on that list, it's best to open an issue first to discuss whether it aligns with the project direction before investing time in implementation.

## What Won't Be Accepted

The following feature PRs will be closed as they won't be implemented (FORK instead):

- Keyboard size or height customization
- One-handed mode
- Custom themes beyond light/dark/system
- Floating keyboard mode
- Advanced customization options (key borders, corner radius, custom colors, etc.)
- Cloud features (sync, backup, import/export)

Those features could be implemented but are not planned:

- Media features (GIF search, stickers)
- Password manager integration

You can open PRs for these features:

- Additional keyboard layouts beyond AZERTY/QWERTY
- Additional languages beyond French/English (via complete language implementation - see below)

### Want These Features?

Nboard is open source (AGPL-3.0). You're welcome to fork the repository and add any features you'd like for your own use. The v1.3.0 refactor made the codebase modular and easier to extend.

If you create a compelling fork with features others want, you can maintain and distribute it yourself.

## Adding a New Language

Adding a language requires a complete implementation to be accepted. This is significant work (typically 10-20+ hours).

A complete language contribution must include:

### Required Components

1. **Keyboard Layout** - Layout file with correct character positions and language-specific characters
2. **Word Dictionary** - Minimum 10,000 most common words with frequency data (UTF-8 encoding)
3. **Bigram Dictionary** - Minimum 50,000 word pairs with frequency data for context-aware predictions
4. **Language Detection** - Indicators added to `SmartTypingBehavior.kt` for bilingual context switching
5. **RTL Support** - If applicable (Arabic, Hebrew, Persian, etc.)
6. **Tests** - Unit tests for dictionary loading, autocorrect accuracy, and predictions
7. **Documentation** - README updates, source attribution, screenshots

### Dictionary Sources

Dictionaries must be license-compatible (CC-BY, CC0, Public Domain, MIT, etc.) and properly attributed.

Recommended sources:
- Wiktionary frequency lists
- OpenSubtitles word frequencies
- Wikipedia dumps
- Google Books N-grams
- hermitdave/FrequencyWords on GitHub
- rspeer/wordfreq

Include a `SOURCES.md` file documenting your dictionary sources with name, license, and URL.

### Before Starting

Open an issue first to:
1. Confirm you understand the requirements
2. Get feedback on your planned dictionary sources
3. Discuss any language-specific considerations

Incomplete language PRs (e.g., just a layout without dictionaries) will be closed with a request to complete the missing requirements.

## Code Style

- Follow existing Kotlin conventions in the codebase
- Keep functions focused and reasonably sized
- Use descriptive variable names
- Add comments for complex logic
- Organize new code into appropriate modules following the v1.3.0 architecture

## Questions Before Starting

If you're planning to work on a significant contribution:
1. Check the "What Won't Be Implemented" section in the README
2. If your feature is on that list, consider forking instead
3. If you're unsure, open an issue to discuss before starting work

## Review Process

- Bug fixes: Usually reviewed within a few days
- Language additions: May take longer due to thorough testing requirements
- Other features: Will be evaluated based on project scope alignment

Thank you for contributing to Nboard.

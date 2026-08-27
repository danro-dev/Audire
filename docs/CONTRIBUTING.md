# Contributing to Audire

Thank you for your interest in contributing to **Audire**! We welcome bug reports, feature suggestions, UI improvements, localization, and code contributions.

---

## Code of Conduct

Please be respectful, constructive, and welcoming to everyone in discussions, issue threads, and pull requests.

---

## How to Contribute

### 1. Reporting Bugs
- Search existing issues to see if your bug has already been reported.
- If not, create a new issue with:
  - Device model & Android version (e.g. Pixel 8, Android 14).
  - Clear steps to reproduce the issue.
  - Expected vs. actual behavior.
  - Relevant logcat output if available.

### 2. Suggesting Features
- Open a feature request issue describing the use case and how it benefits offline/local-first reading and listening.

### 3. Submitting Code Changes
1. **Fork the repository** on GitHub / GitLab.
2. **Create a descriptive topic branch**:
   `ash
   git checkout -b feature/my-new-feature
   `
3. **Make your changes** adhering to the following guidelines:
   - Use Kotlin and Jetpack Compose best practices.
   - Respect the **local-first and zero-telemetry** principles (never add third-party analytics or non-free tracking SDKs).
   - Ensure the code compiles and passes unit tests:
     `ash
     ./gradlew test
     `
4. **Commit with clear commit messages**:
   `ash
   git commit -m "feat(reader): add custom line-height slider"
   `
5. **Open a Pull Request** against the main branch.

---

## Development Guidelines

- **Architecture:** MVVM with Kotlin Coroutines and StateFlow.
- **UI Framework:** Jetpack Compose + Material Design 3.
- **Language Support:** Bilingual ES/EN dictionary in LanguageManager.kt.
- **Storage:** Scoped Storage (DocumentFile / contentResolver) and Room ORM.
- **F-Droid Compatibility:** Avoid adding proprietary Google Play Services dependencies or closed-source binary blobs.

---

## License

By contributing to Audire, you agree that your contributions will be licensed under the project's [MIT License](../LICENSE).

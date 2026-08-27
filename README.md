# Audire

<div align="center">

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-blue.svg)](https://developer.android.com/jetpack/compose)
[![F-Droid](https://img.shields.io/badge/F--Droid-Ready-32CD32.svg)](docs/FDROID_SUBMISSION.md)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline%20%7C%20Zero%20Telemetry-orange.svg)](docs/PRIVACY.md)

**A modern, lightweight, privacy-focused, offline-first media reader and audiobook player for Android.**  
Play your personal audio library, read EPUB & PDF documents, view comics, and listen with Text-to-Speech narration — completely offline, with no subscriptions, no accounts, and zero tracking.

[Features](#-key-features) • [Screenshots](#-ui--screen-overview) • [Architecture](#-architecture-overview) • [Sidecar Metadata](#-portable-sidecar-metadata) • [Permissions](#-permissions-transparency) • [Build](#-build--run) • [F-Droid](#-f-droid-submission) • [Documentation](#-documentation)

</div>

---

## 🌟 Philosophy & Overview

**Audire** was designed for readers and listeners who value **digital sovereignty, simplicity, and privacy**. 

Unlike modern streaming platforms that require accounts, cloud syncing, and continuous telemetry, Audire operates **100% locally on your device**:
- **Zero Telemetry & Zero Trackers:** No analytics SDKs, no ad networks, no third-party profiling.
- **Local-First & Scoped Storage:** Only accesses folders you explicitly select via Android's Storage Access Framework.
- **Decentralized Portability:** Reading progress, bookmarks, and quotes can be stored directly alongside your media files in companion `.audire.meta` sidecars.
- **Complete Media Hub:** Seamlessly combines audiobooks, e-books (EPUB), documents (PDF), and visual media (CBZ/CBR comics) in a unified interface.

---

## 🚀 Key Features

| Category | Feature | Description |
|---|---|---|
| **🎧 Audio Player** | Multi-Format Audio Playback | Supports **MP3, M4B, M4A, AAC, FLAC, WAV, OGG, and OPUS**. |
| | Background Playback | Foreground Service with **MediaStyle notifications** and lock-screen playback controls. |
| | Playback Speed & Seeking | Adjustable playback speed (**0.5× to 3.0×**), ±30s quick seeking, and scrub bar. |
| | Immutable Progress Rule | Core domain invariant guaranteeing that saved progress never regresses accidentally. |
| | Auto-Save Engine | Automatically persists playback position every **5s, 10s, or 30s**. |
| **📖 E-Book & Reader** | EPUB Reader | Paginated reading with `HorizontalPager`, typography adjustments (12–36sp), and night mode. |
| | PDF Document Reader | Hardware-accelerated bitmap rendering with `PdfRenderer`, continuous scrolling, and pinch-to-zoom. |
| | Comics & Manga Viewer | Decodes **CBZ, CBR, and ZIP** image archives for smooth graphic reading. |
| | Text-to-Speech (TTS) | On-the-fly voice narration for EPUB and PDF pages with real-time speed control. |
| **🗂️ Library Management** | 3 Library View Modes | **3D Bookstore Shelf** (cover art cards), **Detailed List**, and **Physical Folder Tree Explorer**. |
| | Physical Folder Browser | In-memory directory hierarchy builder (`FolderNode`) with interactive breadcrumbs. |
| | Recursive Scanning | Fast Scoped Storage indexing with automatic format detection and duplicate prevention. |
| **💾 Portability** | Companion Sidecar Metadata | Reads and writes portable `<filename>.audire.meta` JSON files directly next to your books. |
| **🎨 Visuals & Covers** | Binary Cover Extraction | Fast binary extraction for embedded **ID3v2 APIC (MP3), MP4 `covr` (M4A/M4B), FLAC, and PDF Page 0**. |
| | Procedural 3D Cover Engine | Dynamically generates embossed 3D vector book spines and covers with deterministic palettes when no artwork exists. |
| **📜 Daily Inspiration** | 300 Daily Quotes Database | Curated literary quotes from historical authors in Spanish & English. |
| | Quotes Notebook | Highlight and save memorable book quotes with custom page/chapter references. |
| **🏆 Gamification** | 60+ Achievements | 7 progression categories: Streaks, Time, Library, Quotes, Habits, and Mastery. |
| | Listening Analytics | Daily goal tracking (60 min target), 7-day interactive bar chart, and listener rank tiers. |
| **🎨 Theming & i18n** | Material Design 3 Themes | 7 color presets (Dark, Light, Peach Sunset, Abyss Marine, Sage Mystic, Cosmic Slate) + **Custom Hex Palette Builder**. |
| | Instant Bilingual UI | Runtime Spanish (`es`) and English (`en`) dictionary switching without activity restarts. |

---

## 🏛️ Architecture Overview

Audire follows a clean, single-activity **MVVM (Model-View-ViewModel)** architecture powered by **Jetpack Compose**, **Kotlin Coroutines**, and **StateFlow**:

```
┌────────────────────────────────────────────────────────┐
│                      MainActivity                      │  Single Activity
│  ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ │  Declarative UI
│  │ LibraryScreen │ │ PlayerScreen  │ │ ReaderScreen  │ │  Material 3
│  └───────────────┘ └───────────────┘ └───────────────┘ │
│  ┌───────────────┐ ┌───────────────┐                   │
│  │  StatsScreen  │ │SettingsScreen │                   │
│  └───────────────┘ └───────────────┘                   │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow / Actions
┌───────────────────────────▼────────────────────────────┐
│                    AudiobookViewModel                  │  AndroidViewModel
│  • Library State & Search      • Media Player State    │  Single Source of Truth
│  • Scoped Storage Scanner      • Diagnostics & Stats   │  for UI Layer
└─────────────┬────────────────────────────┬─────────────┘
              │ Coroutines                 │ PlaybackController
┌─────────────▼─────────────┐ ┌────────────▼────────────────────────┐
│    AudiobookRepository    │ │    AudiobookPlaybackService         │
│  • Room DAO Abstraction   │ │  • Foreground Media Service         │
│  • Progress Inmutability  │ │  • MediaStyle Notification          │
│  • Sidecar Syncing        │ │  • Lockscreen Controls              │
└─────────────┬─────────────┘ └─────────────────────────────────────┘
              │
┌─────────────▼─────────────┐ ┌─────────────────────────────────────┐
│    Room Database (v4)     │ │        Sidecar Metadata             │
│  • audiobooks             │ │  • Direct File JSON (.audire.meta)  │
│  • scan_directories       │ │  • SAF Tree Writer                  │
│  • listening_logs         │ │  • Private Mirror Fallback          │
│  • book_quotes            │ └─────────────────────────────────────┘
└───────────────────────────┘
```

---

## 💾 Portable Sidecar Metadata (`.audire.meta`)

To prevent loss of playback progress or bookmarks when switching devices, moving storage cards, or reinstalling the app, Audire can export and synchronize lightweight companion JSON files alongside your media:

```json
{
  "formatVersion": 1,
  "appName": "Audire",
  "title": "Don Quijote de la Mancha",
  "author": "Miguel de Cervantes",
  "durationMillis": 18450000,
  "currentPositionMillis": 4520000,
  "isCompleted": false,
  "isFavorite": true,
  "lastListenedTime": 1724734800000,
  "coverUrl": "",
  "updatedAt": 1724734800000,
  "quotes": [
    {
      "quoteText": "El que lee mucho y anda mucho, ve mucho y sabe mucho.",
      "pageReference": "Capitulo XXV",
      "timestamp": 1724734800000
    }
  ]
}
```

---

## 📱 UI & Screen Overview

1. **Library Screen:**
   - Switch between **Bookstore 3D**, **List**, and **Folder Tree** modes.
   - Filter by format: *All · Audiobooks · PDFs · EPUBs · Comics*.
   - Filter by status: *All · Favorites · In Progress · Completed*.
   - Instant search across titles and authors.

2. **Player Screen:**
   - Album artwork display with interactive replacement picker.
   - Time elapsed / remaining with smooth scrub bar.
   - Speed toggle, ±30s seek buttons, and autosave status indicator.

3. **Reader Screen:**
   - Immersive fullscreen reading for EPUB and PDF.
   - Integrated Text-to-Speech (TTS) narration controls.
   - Font scaling (12–36sp), line spacing, and night reading themes.

4. **Stats & Achievements Screen:**
   - Daily progress ring against 60-minute goal.
   - Current streak and all-time record counters.
   - 7-day visual reading bar chart.
   - 60+ achievements with category filters (Streaks, Time, Library, Habits, Quotes, Mastery).

5. **Settings & Diagnostics Screen:**
   - Instant ES / EN bilingual toggle.
   - 7 Material 3 themes + custom RGB hex palette builder.
   - Auto-save frequency selector (5s / 10s / 30s / Manual).
   - Storage folder management and database reset tools.

---

## 🔒 Permissions Transparency

Audire adheres to the principle of least privilege. Every permission requested is directly tied to user-facing media functionality:

| Permission | Technical Descriptor | Justification (F-Droid Audit) |
|---|---|---|
| **Audio Access** | `android.permission.READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE` (<= API 32) | Required to read and index user-selected audiobooks. |
| **Foreground Service** | `android.permission.FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required to sustain audio playback when the app is minimized or the screen is turned off. |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Required on Android 13+ (API 33+) to display playback controls in the system notification shade. |
| **Internet (Optional)** | `android.permission.INTERNET` | Used exclusively for playing optional remote demo streams. Core app functionality is 100% offline. |

---

## 🛠️ Build & Run

### Prerequisites
- **Android Studio** Ladybug / Hedgehog or newer
- **JDK:** OpenJDK 17 or 11
- **Android SDK:** `compileSdk 36`, `targetSdk 36`, `minSdk 24` (Android 7.0+)

### Building from Terminal

```bash
# 1. Clone the repository
git clone https://github.com/danro-dev/Audire.git
cd Audire/source

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Run Unit Tests
./gradlew test

# 4. Build Release APK
./gradlew assembleRelease
```

### Release Signing
Release builds can be signed by setting the following environment variables:
```bash
export KEYSTORE_PATH="/path/to/my-upload-key.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
```

---

## 📦 F-Droid Submission

Audire is built from the ground up to be 100% compliant with F-Droid inclusion guidelines:
- Complete Free and Open Source code ([MIT License](LICENSE)).
- Zero non-free dependencies, closed binary blobs, or proprietary telemetry SDKs.
- Includes standard [Fastlane Metadata](fastlane/metadata/android/) in English and Spanish.
- Ready-to-use packaging recipe available in [docs/FDROID_SUBMISSION.md](docs/FDROID_SUBMISSION.md).

---

## 📚 Documentation

- 📐 [Architecture & Specification](docs/ARCHITECTURE.md) — Detailed technical specs, Room schema, and subsystems.
- 📦 [F-Droid Submission Guide](docs/FDROID_SUBMISSION.md) — Recipe template and metadata checklist for F-Droid.
- 🛡️ [Privacy Policy](docs/PRIVACY.md) — Full offline, zero-tracking declaration.
- 🤝 [Contributing Guidelines](docs/CONTRIBUTING.md) — Code style, pull request workflow, and community rules.
- 💻 [Source Code Readme](source/README.md) — Developer build instructions for the Android module.

---

## 📄 License

```
MIT License

Copyright (c) 2026 Audire Project Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```

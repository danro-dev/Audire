# Audire Architecture & Technical Specification

This document details the architectural layers, data models, state flows, and core subsystems of **Audire**.

---

## 1. High-Level Architecture

Audire follows a reactive, single-activity **MVVM (Model-View-ViewModel)** architecture built with **Jetpack Compose** and **Kotlin Coroutines / StateFlow**.

`
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
`

---

## 2. Core Subsystems

### 2.1 Media Playback & Foreground Service
- **Engine:** Android native MediaPlayer integrated through PlaybackController and AudiobookPlaybackService.
- **Foreground Service:** Uses oregroundServiceType="mediaPlayback" with NotificationCompat.MediaStyle to guarantee uninterrupted audio in the background and on lock screen.
- **Auto-Save Engine:** Dispatches playback progress every 5s, 10s, or 30s to the local database and sidecar files.
- **Immutable Progress Rule (AudiobookRepository):**
  A core domain invariant that prevents saved playback progress from decreasing during seek events, buffer resets, or unexpected service restarts unless isExplicitRewind = true is explicitly passed by user interaction.

### 2.2 Document & E-Book Reading Engine
- **PDF Renderer:** Employs Android's native PdfRenderer for hardware-accelerated page bitmap generation, continuous scroll in LazyColumn, pinch-to-zoom, and high-contrast night mode.
- **EPUB Reader:** Parses EPUB container ZIP archives and internal XHTML/HTML chapters into structured paginated text with a HorizontalPager, typography adjustments (font size 12–36sp), and night reading styles.
- **Comics / Manga:** CBZ / CBR / ZIP archive decoder that unzips image streams dynamically for full-screen manga and comic viewing.
- **Text-to-Speech (TTS):** Integrates Android's TextToSpeech engine for on-the-fly voice narration of text pages, with pitch, speed, and sentence-level playback controls.

### 2.3 Sidecar Companion Metadata (.audire.meta)
To solve the common problem of losing playback positions when switching devices or reinstalling apps, Audire features a decentralized **Sidecar Metadata Manager**:
- Writes companion JSON metadata files (<filename>.audire.meta) directly into the user's storage directories.
- Contains book title, author, duration, current playback position, favorite flag, completion status, and user-saved quotes with timestamps.
- Features a triple-fallback resolution system:
  1. Direct POSIX File API (for direct file paths).
  2. Storage Access Framework (SAF) Document tree writing.
  3. App-private mirror (/files/sidecars/) fallback.

### 2.4 Procedural Cover Art & ID3 Tag Extraction (ThumbnailManager)
- Extracts embedded ID3v2 APIC tags (MP3), MP4 covr atoms (M4A/M4B/AAC), and FLAC picture blocks via fast binary byte scanning.
- Renders page 0 of PDF documents into crisp cover thumbnails.
- Procedural Cover Generator: If no cover exists, Audire dynamically generates a stylized, embossed 3D book cover with deterministic palette selection based on title hash, spine shadows, and format badges.

### 2.5 Library Organization & Physical Directory Explorer (FolderHierarchyBuilder)
- **Bookstore 3D Grid:** Large cover cards with progress indicators and format badges.
- **Detailed List:** Compact rows with duration, completion badges, and quick action menus.
- **Physical Folder Browser:** Computes an in-memory directory tree (FolderNode) reflecting real storage folder hierarchies with interactive breadcrumbs.

### 2.6 Daily Quotes Database & Gamified Analytics
- **300 Daily Quotes Database:** Curated literary quotes from historical authors in Spanish and English with deterministic rotation.
- **Achievement Engine:** 60+ achievements across 7 categories (Rachas/Streaks, Tiempo/Time, Biblioteca/Library, Frases/Quotes, Hábitos/Habits, Maestría/Mastery).
- **Listening Logs:** Tracks daily listening duration with 7-day visual bar charts and rank progression (Rookie → Avid Listener → Grandmaster → Audire Sage).

---

## 3. Database Schema (Room v4)

### udiobooks
`sql
CREATE TABLE audiobooks (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    title TEXT NOT NULL,
    author TEXT NOT NULL,
    durationMillis INTEGER NOT NULL,
    filePath TEXT NOT NULL,
    coverUrl TEXT NOT NULL,
    currentPositionMillis INTEGER NOT NULL,
    lastListenedTime INTEGER NOT NULL,
    isCompleted INTEGER NOT NULL,
    isFavorite INTEGER NOT NULL
);
`

### scan_directories
`sql
CREATE TABLE scan_directories (
    path TEXT PRIMARY KEY NOT NULL,
    titlesFound INTEGER NOT NULL,
    lastScanTime INTEGER NOT NULL
);
`

### listening_logs
`sql
CREATE TABLE listening_logs (
    date TEXT PRIMARY KEY NOT NULL, -- Format: YYYY-MM-DD
    durationMillis INTEGER NOT NULL
);
`

### ook_quotes
`sql
CREATE TABLE book_quotes (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    bookId INTEGER NOT NULL,
    bookTitle TEXT NOT NULL,
    quoteText TEXT NOT NULL,
    pageReference TEXT NOT NULL,
    timestamp INTEGER NOT NULL
);
`

---

## 4. Internationalization & Theming

- **Language Engine (LanguageManager):** Code-based dictionary system with real-time Spanish (s) and English (n) language switching without activity recreation.
- **Material Design 3 Theme System (Theme.kt):** 7 color schemes (Dark, Light, Peach Sunset, Abyss Marine, Sage Mystic, Cosmic Slate, Custom Hex RGB) with dynamic background luminance detection.

# Audire

A local-first audiobook and e-book player for Android. Play your personal audio library and read PDF/EPUB documents offline — no cloud, no subscriptions, no tracking.

---

## Table of Contents

- [Features](#features)
- [Architecture Overview](#architecture-overview)
- [Project Structure](#project-structure)
- [Data Layer](#data-layer)
- [UI Screens](#ui-screens)
- [Background Playback](#background-playback)
- [Theme System](#theme-system)
- [Internationalization](#internationalization)
- [Permissions](#permissions)
- [Dependencies](#dependencies)
- [Build & Run](#build--run)
- [Known Limitations & Technical Debt](#known-limitations--technical-debt)

---

## Features

| Feature | Details |
|---------|---------|
| Audio playback | MP3, M4B, M4A, AAC, WAV, OGG via Android `MediaPlayer` |
| E-book reading | PDF via `PdfRenderer`, EPUB via ZIP parsing |
| Text-to-Speech narration | Android TTS engine reads EPUB/PDF pages aloud |
| Piper TTS voice catalog | UI for managing neural voice models (ES, EN, FR, DE) |
| Library management | Scoped Storage directory picker, recursive file scanning, auto-metadata extraction |
| Persistent progress | Position saved every 10s by default; never regresses (immutable progress rule) |
| Listening statistics | Daily log, streak tracking, rank system, weekly bar chart |
| Full theme engine | Dark, Light, 4 presets, and a fully custom hex-color palette builder |
| Bilingual UI | Spanish / English, switchable at runtime |
| Background playback | Foreground service with media notification and lock-screen controls |
| Offline-first | All core features work without internet access |

---

## Architecture Overview

```
┌─────────────────────────────────────────┐
│              MainActivity               │  Single Activity
│  (Compose UI — all screens in one file) │
└──────────────┬──────────────────────────┘
               │ observes StateFlow
┌──────────────▼──────────────────────────┐
│          AudiobookViewModel             │  MVVM — AndroidViewModel
│  Player · Library · Stats · Settings    │
└──────┬──────────────────────────┬───────┘
       │ coroutines                │ PlaybackController (singleton bridge)
┌──────▼──────┐         ┌─────────▼────────────────┐
│  Room DB    │         │  AudiobookPlaybackService │  Foreground Service
│  (3 tables) │         │  (media notification)     │
└─────────────┘         └──────────────────────────┘
```

**Patterns in use:**
- **MVVM** — `AudiobookViewModel` + `StateFlow` drives all UI state
- **Repository** — `AudiobookRepository` abstracts the DAO and owns domain rules
- **Singleton bridge** — `PlaybackController` connects the Service to the ViewModel without a bound service
- **Coroutines** — all async work via `viewModelScope`, `Dispatchers.IO/Default/Main`

**Intentional simplifications (see [Known Limitations](#known-limitations--technical-debt)):**
- Navigation is a `rememberSaveable { mutableStateOf("Library") }` string — no Compose Navigation NavHost
- All UI lives in `MainActivity.kt` — no per-screen files
- One ViewModel serves all 5 screens

---

## Project Structure

```
source/app/src/main/java/com/example/
│
├── MainActivity.kt               # Single Activity + all Composable screens (~3 200 lines)
├── AudiobookPlaybackService.kt   # Foreground Service for background audio
├── PlaybackController.kt         # Singleton bridge: Service ↔ ViewModel
├── LanguageManager.kt            # Runtime i18n dictionary (ES / EN)
│
├── data/
│   ├── Audiobook.kt              # Room @Entity — audio files and documents
│   ├── ScanDirectory.kt          # Room @Entity — user-granted scoped storage URIs
│   ├── ListeningLog.kt           # Room @Entity — one row per calendar day
│   ├── AudiobookDao.kt           # @Dao — all CRUD for the three entities
│   ├── AudiobookDatabase.kt      # @Database singleton (Room v3)
│   └── AudiobookRepository.kt   # Repository with immutable-progress rule
│
└── ui/
    ├── viewmodel/
    │   └── AudiobookViewModel.kt # AndroidViewModel — all app state
    └── theme/
        ├── Color.kt              # Material 3 color tokens
        ├── Theme.kt              # MyApplicationTheme — 7 color schemes
        └── Type.kt               # Typography scale
```

---

## Data Layer

### Entities

#### `Audiobook`
Represents any media item in the library. Audio files and documents (PDF/EPUB) share the same entity — type is inferred from `filePath` extension at runtime.

| Field | Type | Description |
|-------|------|-------------|
| `id` | `Int` (PK, autoGenerate) | Unique identifier |
| `title` | `String` | Display title |
| `author` | `String` | Author or narrator |
| `durationMillis` | `Long` | Total duration in ms; for documents: page count |
| `filePath` | `String` | Content URI, file path, or `demo://` prefix |
| `coverUrl` | `String` | File path to cached cover image |
| `currentPositionMillis` | `Long` | Saved playback position (or page index for docs) |
| `lastListenedTime` | `Long` | Unix timestamp of last interaction |
| `isCompleted` | `Boolean` | Set when position reaches end (within 2s) |
| `isFavorite` | `Boolean` | User-toggled favorite flag |

#### `ScanDirectory`
Tracks user-granted scoped storage tree URIs.

| Field | Type | Description |
|-------|------|-------------|
| `path` | `String` (PK) | Content URI string |
| `titlesFound` | `Int` | Number of media files found on last scan |
| `lastScanTime` | `Long` | Unix timestamp of last scan |

#### `ListeningLog`
One record per calendar day for statistics.

| Field | Type | Description |
|-------|------|-------------|
| `date` | `String` (PK) | Format: `"yyyy-MM-dd"` |
| `durationMillis` | `Long` | Total ms listened that day (accumulated) |

### Repository Rules

**Immutable Progress Rule** (`AudiobookRepository.updatePlaybackPosition`):
The saved position can never decrease unless `isExplicitRewind = true` is explicitly passed. This prevents accidental progress regression from seek events or service restarts. A book is marked `isCompleted` when the position reaches within 2 seconds of its total duration.

### Database

- Name: `sanctuary_database`
- Room version: 3
- Migration strategy: `fallbackToDestructiveMigration` (no migration scripts — DB is rebuilt on schema change)
- Single `AudiobookDao` covers all three entities

---

## UI Screens

### Navigation

Navigation is state-driven — a `selectedTab: String` in `MainScreen` drives a `when` block swapping composables. There is no NavHost.

```
App Launch
  └── MainScreen
        ├── Onboarding Dialog (first launch — blocks until a directory is selected)
        │
        ├── "Library" → LibraryScreen
        │     ├── tap audio file  → PlayerScreen
        │     └── tap PDF/EPUB    → ReaderScreen
        │
        ├── "Player" → PlayerScreen
        │     └── document loaded → "Enter Reading Mode" → ReaderScreen
        │
        ├── "Reader" → ReaderScreen (TopBar + BottomBar hidden, fullscreen)
        │     └── back → LibraryScreen
        │
        ├── "Stats"    → StatsScreen
        └── "Settings" → SettingsScreen
```

### LibraryScreen

Displays the user's book collection.

- Full-text search across title and author
- Status filters: All · Favorites · In Progress · Completed
- Format filters: All · Audiobooks · PDFs · EPUBs
- `LazyColumn` of `AudiobookCard` items with reading progress bar
- Per-card cover image picker (system gallery)
- Empty state with a directory picker call-to-action

### PlayerScreen

Standard audio player UI.

- Cover art (tap to replace from gallery)
- Title, author, autosave status pill
- Seek slider with elapsed / remaining time
- Controls: −30s skip, Play/Pause, +30s skip
- Playback speed cycle: 0.75× → 1× → 1.25× → 1.5× → 2× → ...
- Sleep timer button (UI only — not yet implemented)
- Chapters and Bookmarks buttons (UI only — not yet implemented)
- For PDF/EPUB files: redirects to ReaderScreen instead

### ReaderScreen

Immersive reading experience.

- **EPUB**: `HorizontalPager`, serif font, adjustable font size (12–36sp), TTS narration per page, page slider
- **PDF**: `LazyColumn` of rendered bitmap pages, pinch-to-zoom, optional "clean text mode" toggle
- Double-tap anywhere to toggle immersive (fullscreen) mode
- TTS play/pause toggle with speed control in the top bar
- Page progress slider and prev/next page buttons in the bottom bar

### StatsScreen

Listening analytics.

- Daily progress circular indicator vs 60-minute goal
- Current streak / Max streak cards
- Rank system: Rookie → Avid Listener → Grandmaster → Audire Sage (based on total hours)
- 7-day bar chart (dynamic scale)
- Stats simulator: +/−15 min buttons and full reset (for testing)

### SettingsScreen

App configuration.

- **Language**: ES / EN toggle
- **Theme**: Dark · Light · Peach Sunset · Abyss Marine · Sage Mystic · Cosmic Slate · Custom (hex color picker for primary, background, secondary)
- **Permissions**: storage access toggle, background playback toggle
- **Auto-save interval**: 5s / 10s / 30s / Manual
- **Voice personalization**: TTS engine configuration, naturalness (noise_scale) and expressiveness (noise_w) sliders, voice test button
- **Scan directories**: list of added directories, add/remove, title count per folder
- **Diagnostics**: DB latency, buffer health, cache usage, API version
- **System testing**: simulate incoming call, headset disconnect, forced close
- **Reset**: purge entire database and library

---

## Background Playback

`AudiobookPlaybackService` is an Android `Service` with `foregroundServiceType="mediaPlayback"`.

**Lifecycle:**
1. `AudiobookViewModel.syncWithPlaybackService()` is called after every playback state change
2. The service creates/updates a persistent media notification with Play/Pause and Stop actions
3. Notification actions send intents back to the service (`ACTION_PLAY`, `ACTION_PAUSE`, `ACTION_STOP`, `ACTION_UPDATE`)
4. The service delegates actual audio control to `PlaybackController`, which calls `viewModel.togglePlayPause()`

**`PlaybackController`** is a Kotlin `object` (singleton) that holds a nullable reference to the active `AudiobookViewModel`. The ViewModel registers itself in `init` and clears the reference in `onCleared`.

---

## Theme System

`MyApplicationTheme` in `Theme.kt` supports 7 color schemes:

| Mode key | Description |
|----------|-------------|
| `dark` | Default dark blue scheme |
| `light` | Light blue scheme |
| `preset_peach` | Peach Sunset — warm red on deep brown |
| `preset_ocean` | Abyss Marine — teal on deep navy |
| `preset_emerald` | Sage Mystic — emerald green on dark forest |
| `preset_cosmic` | Cosmic Slate — purple on deep indigo |
| `custom` | User-defined primary / background / secondary hex colors |

Custom themes use `buildDynamicColorScheme()` which auto-detects light vs dark mode from the background color's luminance (`> 0.45f` → light), then derives appropriate text, surface, and container colors automatically.

Theme selection and custom hex values are persisted to `SharedPreferences ("audire_prefs")` and survive process death.

---

## Internationalization

`LanguageManager` is a Kotlin `object` that holds two `Map<String, String>` dictionaries (Spanish and English). All UI strings that need translation call `LanguageManager.getString(key, lang)`. There are no Android `strings.xml` resources — the entire i18n system is code-based.

The selected language (`"es"` or `"en"`) is stored in `SharedPreferences` and exposed as a `StateFlow` from the ViewModel. Composables collect this flow and re-render when the language changes.

---

## Permissions

| Permission | When used |
|-----------|-----------|
| `READ_MEDIA_AUDIO` (API 33+) | Scanning audio files from user-selected directories |
| `READ_EXTERNAL_STORAGE` (≤ API 32) | Legacy fallback for audio scanning |
| `FOREGROUND_SERVICE` | Required to start the playback service |
| `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Required for media-type foreground service |
| `POST_NOTIFICATIONS` | Media playback notification (API 33+) |
| `INTERNET` | Demo streaming sample (La Metamorfosis) and future network features |

All file access uses Scoped Storage (`DocumentFile.fromTreeUri` + `contentResolver.takePersistableUriPermission`). No raw file path scanning.

---

## Dependencies

| Library | Purpose |
|---------|---------|
| `androidx.compose.bom` | Compose BOM — version alignment |
| `androidx.compose.material3` | Material 3 UI components |
| `androidx.compose.material.icons.*` | Extended icon set |
| `androidx.activity.compose` | `ComponentActivity` + Compose integration |
| `androidx.lifecycle.*` | `ViewModel`, `collectAsStateWithLifecycle` |
| `androidx.room.*` | Local SQLite ORM |
| `androidx.documentfile` | Scoped Storage tree traversal |
| `androidx.media` | `NotificationCompat.MediaStyle` |
| `coil.compose` | Async cover image loading |
| `retrofit` + `okhttp` + `moshi` | HTTP client (declared, partially unused) |
| `kotlinx.coroutines.*` | Async/concurrency primitives |
| `robolectric` + `roborazzi` | Unit + screenshot testing |

**Not yet in use (declared but disabled):** Firebase AI, CameraX, Accompanist Permissions, Datastore, Navigation Compose, Play Services Location.

---

## Build & Run

### Prerequisites

- Android Studio Hedgehog or later
- JDK 11
- Android SDK 36 (`compileSdk`)
- Minimum device: Android 7.0 (API 24)

### Steps

```bash
# Clone
git clone <repo-url>
cd Audire/source

# Open in Android Studio and run on a device or emulator (API 24+)
```

### Signing

Release builds expect the following environment variables:

```
KEYSTORE_PATH   — path to .jks file (default: <root>/my-upload-key.jks)
STORE_PASSWORD  — keystore password
KEY_PASSWORD    — key password
```

Debug builds use `debug.keystore` at the project root with standard Android debug credentials.

### First Launch

On first launch, an onboarding dialog prompts the user to select a folder. The app will scan that folder for audio files and documents. Three demo titles are pre-loaded (Don Quijote, El Principito, and a streaming sample of La Metamorfosis) if the library is empty after the initial scan.

---

## Known Limitations & Technical Debt

| Area | Issue |
|------|-------|
| **Monolithic ViewModel** | `AudiobookViewModel` serves all 5 screens. A per-screen split would improve testability and reduce recomposition scope. |
| **Monolithic UI file** | All ~3 200 lines of UI live in `MainActivity.kt`. Should be split into per-screen files under `ui/screens/`. |
| **Dual-use entity** | `Audiobook` is used for both audio files and documents. Type is inferred from the `filePath` extension at runtime, with `isDocumentFile()` checks scattered throughout. A proper domain split would use separate types. |
| **No Compose Navigation** | Tab state is a plain `String` — back stack, deep links, and transitions are not supported. |
| **No DI framework** | The ViewModel instantiates its own dependencies directly. Hilt or Koin would improve testability. |
| **Piper TTS simulated** | The voice download writes placeholder `.onnx` files. Actual ONNX inference is not implemented; TTS uses Android's built-in engine. |
| **No migration scripts** | `fallbackToDestructiveMigration` means a schema change wipes all user data. |
| **Singleton bridge** | `PlaybackController` holds a raw reference to the ViewModel. This works but is not lifecycle-safe in all edge cases. |
| **Player screen stubs** | Chapters and Bookmarks buttons are rendered but not functional. Sleep timer UI exists but does nothing. |
| **Non-atomic log upsert** | `addListeningDuration()` does read → add → write without a SQL transaction. Safe under single-dispatcher serial execution but not truly atomic. |

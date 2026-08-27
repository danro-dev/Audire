# Audire — Android Source Code & Build Guide

This directory contains the complete Android source code for **Audire**, built with Kotlin, Jetpack Compose, Material 3, and Room ORM.

---

## 🛠️ Build Requirements

- **Android Studio:** Ladybug / Hedgehog or newer
- **JDK:** OpenJDK 17 or 11
- **Android SDK:**
  - compileSdk: 36 (minorApiLevel = 1)
  - 	argetSdk: 36
  - minSdk: 24 (Android 7.0 Nougat)
- **Gradle Version:** Gradle 8.13+ (configured via Gradle Wrapper)

---

## 🚀 Building From Command Line

### 1. Build Debug APK
`ash
./gradlew assembleDebug
`
The resulting APK will be placed in pp/build/outputs/apk/debug/app-debug.apk.

### 2. Build Release APK (Unsigned / Signed)
`ash
./gradlew assembleRelease
`

To sign the release build, provide the following environment variables:
`ash
export KEYSTORE_PATH="/path/to/your/keystore.jks"
export STORE_PASSWORD="your-keystore-password"
export KEY_PASSWORD="your-key-password"
`

### 3. Run Unit Tests & Lint
`ash
./gradlew test
./gradlew lint
`

### 4. Run Screenshot Tests (Roborazzi)
`ash
./gradlew recordRoborazziDebug
./gradlew verifyRoborazziDebug
`

---

## 📂 Source Code Structure

`
app/src/main/
├── AndroidManifest.xml
├── java/com/example/
│   ├── MainActivity.kt               # Jetpack Compose single-activity & UI screens
│   ├── AudiobookPlaybackService.kt   # Foreground Media Service
│   ├── PlaybackController.kt         # Singleton bridge between Service and ViewModel
│   ├── LanguageManager.kt            # Runtime dictionary i18n (ES / EN)
│   │
│   ├── data/
│   │   ├── Audiobook.kt              # Room @Entity: audiobooks & documents
│   │   ├── ScanDirectory.kt          # Room @Entity: Scoped Storage tree URIs
│   │   ├── ListeningLog.kt           # Room @Entity: daily listening records
│   │   ├── BookQuote.kt              # Room @Entity: book quotes and bookmarks
│   │   ├── AudiobookDao.kt           # Room @Dao: CRUD operations & queries
│   │   ├── AudiobookDatabase.kt      # Room @Database singleton (v4)
│   │   ├── AudiobookRepository.kt    # Repository with immutable progress rule
│   │   ├── SidecarMetadataManager.kt # JSON companion .audire.meta read/write
│   │   ├── ThumbnailManager.kt       # ID3/MP4/FLAC/PDF extraction & procedural cover engine
│   │   ├── FolderHierarchy.kt        # Physical directory tree & breadcrumbs builder
│   │   ├── Achievement.kt            # 60+ achievements & progression manager
│   │   └── DailyQuotesDatabase.kt    # 300 curated literary quotes
│   │
│   └── ui/
│       ├── viewmodel/
│       │   └── AudiobookViewModel.kt # StateFlow & MVI/MVVM logic
│       └── theme/
│           ├── Color.kt              # Material 3 color tokens
│           ├── Theme.kt              # 7 color schemes & dynamic palette builder
│           └── Type.kt               # Typography scale
│
└── res/                              # Drawables, mipmaps, XML configs
`

---

## 🔒 Privacy & Clean Code Standards

- **Zero Trackers:** Do not add proprietary tracking, ad networks, or analytics libraries.
- **Scoped Storage:** Always use Android's DocumentFile and ContentResolver with persistable URI permissions.
- **FOSS Standards:** Ensure all dependencies are compatible with the MIT / F-Droid guidelines.

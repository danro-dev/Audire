# F-Droid Submission Guide for Audire

This document contains everything needed to prepare, package, and submit **Audire** to the official [F-Droid](https://f-droid.org/) repository or a custom F-Droid repo.

---

## 1. F-Droid Compliance Checklist

- [x] **100% Free & Open Source Software (FOSS):** Licensed under the OSI-approved [MIT License](../LICENSE).
- [x] **No Proprietary Dependencies:** No Google Play Services, Firebase Analytics, proprietary crash reporters, or closed SDKs.
- [x] **Zero Tracking & No Anti-Features:** No advertising, no tracking, no paid-only features.
- [x] **Builds from Source:** All code and assets can be built completely offline using standard Gradle tools.
- [x] **Standard Fastlane Structure:** Includes astlane/metadata/android/ for automatic ingestion of descriptions and metadata.

---

## 2. Permissions Transparency (For F-Droid Reviewers)

| Permission | Justification |
|---|---|
| READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE | Allows the user to index and play local audiobooks selected via Scoped Storage. |
| FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PLAYBACK | Required by Android to play audiobooks in the background when the screen turns off. |
| POST_NOTIFICATIONS | Required on Android 13+ (API 33+) to display playback controls in the system notification area. |
| INTERNET | Optional; allows streaming demo audiobooks or network resources. Audire does not use internet for tracking or telemetry. |

---

## 3. F-Droid Metadata Recipe Template (droiddata)

To submit Audire to the official droiddata repository, create a metadata file named after your pplicationId:

### File: metadata/com.aistudio.sanctuary.audpbk.yml

`yaml
Categories:
  - Multimedia
  - Reading
License: MIT
AuthorName: danro-dev
SourceCode: https://github.com/danro-dev/Audire
IssueTracker: https://github.com/danro-dev/Audire/issues
Changelog: https://github.com/danro-dev/Audire/releases

AutoName: Audire
Summary: Offline, privacy-first audiobook player and e-book reader with TTS
Description: |-
  Audire is a modern, lightweight, privacy-focused audiobook player and e-book reader
  built with Jetpack Compose and Material 3.

  Features:
  * Audio playback: MP3, M4B, M4A, AAC, FLAC, WAV, OGG, OPUS
  * E-Book & Document reading: PDF, EPUB, and CBZ comics
  * Text-to-Speech (TTS) narration with speed control
  * Physical folder browser and 3D bookstore shelf view
  * Decentralized sidecar metadata (.audire.meta) synchronization
  * Embedded ID3/MP4/FLAC/PDF cover art extraction and procedural cover generation
  * 300 daily literary quotes and saved quotes notebook
  * 60+ reading achievements and 7-day listening analytics
  * Material 3 dynamic themes and runtime Spanish/English bilingual support
  * 100% offline with zero telemetry and no trackers

RepoType: git
Repo: https://github.com/danro-dev/Audire.git

Builds:
  - versionName: '1.0'
    versionCode: 1
    commit: v1.0.0
    subdir: source
    gradle:
      - assembleRelease
    output: app/build/outputs/apk/release/app-release-unsigned.apk
    prebuild:
      - echo "Build initialized"

AutoUpdateMode: Version
UpdateCheckMode: Tags
`

---

## 4. How to Submit to F-Droid

1. **Tag a Release:**
   Ensure a Git release tag exists (e.g. 1.0.0):
   `ash
   git tag -a v1.0.0 -m "Release v1.0.0"
   git push origin v1.0.0
   `

2. **Fork droiddata:**
   Go to [https://gitlab.com/fdroid/fdroiddata](https://gitlab.com/fdroid/fdroiddata) and fork the repository.

3. **Add the Recipe:**
   Add metadata/com.aistudio.sanctuary.audpbk.yml (or your chosen application ID) to your fork.

4. **Test the Build with droid build:**
   `ash
   fdroid checkupdates com.aistudio.sanctuary.audpbk
   fdroid lint com.aistudio.sanctuary.audpbk
   fdroid build -v -s com.aistudio.sanctuary.audpbk
   `

5. **Open a Merge Request:**
   Submit a Merge Request to droid/fdroiddata on GitLab.

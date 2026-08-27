# Privacy Policy for Audire

**Last Updated:** August 2026

**Audire** is designed and built with a strict **local-first and privacy-by-design** philosophy. Your reading habits, audio playback progress, personal documents, and audiobooks belong exclusively to you.

---

## 1. No Data Collection or Tracking

- **Zero Telemetry:** Audire does not include analytics SDKs, tracking pixels, crash-reporting third-party tools, advertising networks, or user profiling systems.
- **No Accounts or Cloud Storage:** There is no registration, login, or cloud backend. All data is saved directly on your local device.
- **Zero Third-Party Data Sharing:** Your personal data is never transmitted, sold, rented, or shared with any third party.

---

## 2. Permissions & On-Device Storage

Audire requires only the minimal set of permissions needed to provide media playback and local file reading:

| Permission | Technical Name | Purpose |
|---|---|---|
| **Audio Access** | ndroid.permission.READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE | Used exclusively to index and play user-selected local audiobook files via Android's Storage Access Framework (SAF). |
| **Foreground Service** | ndroid.permission.FOREGROUND_SERVICE & FOREGROUND_SERVICE_MEDIA_PLAYBACK | Keeps playback alive when the app is minimized or the screen is locked, displaying media controls in the system notification. |
| **Notifications** | ndroid.permission.POST_NOTIFICATIONS | Displays the media playback notification with play/pause and seek controls (Android 13+). |
| **Internet (Optional)** | ndroid.permission.INTERNET | Only used when playing optional online demo streams or testing network endpoints. Local playback and reading work 100% offline. |

All file access is conducted through Android's **Storage Access Framework (Scoped Storage)**. Audire only accesses folders explicitly selected by the user.

---

## 3. Local Data Storage & Sidecar Files

- **Room Database:** App state, reading logs, and saved bookmarks are stored in an encrypted/private SQLite database inside the app's internal sandbox (/data/data/com.example/databases/).
- **Companion Sidecar Metadata (.audire.meta):** When enabled, Audire writes compact, plain-text JSON sidecar files alongside your books in your storage directories to allow portability of progress and quotes. You can delete or edit these files at any time without compromising the app.

---

## 4. Open Source & Verifiability

Audire is 100% Free and Open Source Software (FOSS) licensed under the **MIT License**. The entire source code is auditable by anyone.

---

## 5. Contact & Questions

If you have any questions regarding privacy or data handling, please open an issue in the project repository.

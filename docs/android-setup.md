# Android Setup

## Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34
- Android phone running Android 8.0+ (API 26+)
- USB cable or wireless debugging for sideloading

---

## Build the APK

1. **Clone the repo**
   ```bash
   git clone https://github.com/DroneWuKong/PlzHelp.git
   cd PlzHelp
   ```

2. **Open in Android Studio**
   - File → Open → select the `PlzHelp` folder (the root, not `windows/`)
   - Wait for Gradle sync to complete (~2 min on first run, downloads ~300MB of dependencies)

3. **Build the APK**
   - Build → Generate Signed Bundle/APK → APK
   - For testing: choose **debug** and use the default debug keystore
   - For distribution: create a keystore (Build → Generate Signed → create new)
   - Output goes to `app/build/outputs/apk/debug/app-debug.apk`

---

## Install on your phone

**Method 1 — USB**
1. Enable Developer Options: Settings → About Phone → tap Build Number 7 times
2. Enable USB Debugging in Developer Options
3. Connect phone via USB
4. In Android Studio: Run → Run 'app' (installs directly)

**Method 2 — Sideload APK**
1. Enable "Install unknown apps" in Android Settings → Apps → Special app access
2. Transfer `app-debug.apk` to your phone (USB, Google Drive, email)
3. Open a file manager on the phone, tap the APK, install

---

## First run

### 1. Set up an LLM provider

Go to **Settings** (⚙ icon in top-right):

**Gemini (recommended — free)**
- Provider: select Gemini
- Get a free API key at [aistudio.google.com](https://aistudio.google.com)
- Same key format used by Wingman AI (`AIzaSy...`)
- Free tier: 1,500 requests/day

**Claude (best quality)**
- Provider: select Claude Haiku (fast+cheap) or Claude Sonnet (best)
- API key from [console.anthropic.com](https://console.anthropic.com)
- Haiku: ~$0.001/message. Sonnet: ~$0.01/message

**Local server (air-gapped)**
- Provider: select Local Server
- Run `server.py` with Ollama on a laptop on the same WiFi
- Enter the laptop's IP: `http://192.168.x.x:5000`

### 2. Set up GitHub access (optional, for private repos)

In Settings:
- GitHub Token: generate at [github.com/settings/tokens](https://github.com/settings/tokens) with `repo` scope
- GitHub Username: `DroneWuKong`

Without a token, only public repos and public URLs sync. The Forge troubleshooting KB and handbook sync without a token.

### 3. Sync documents

Tap the **sync icon** (↻) in the header → **Sync All**

Default sources:
| Source | Type | Requires token |
|--------|------|---------------|
| Forge Troubleshooting KB | Public URL | No |
| Drone Integration Handbook | Public GitHub | No |
| DroneClear Forge | Private GitHub | Yes |
| ORQA PX4 Port | Private GitHub | Yes |

Wait for sync to complete. The chunk count in the header updates as files are processed.

### 4. Start chatting

Type a question and tap Send. First response may take 5–10 seconds while the model warms up.

---

## Adding custom sources

Tap ↻ → **Add Source** at the bottom of the Sync screen.

**GitHub repo:**
- Type: GitHub Repo
- Label: anything you want (shows on source tags)
- URL: `owner/repo` format, e.g. `DroneWuKong/droneclear_Forge`

**Public URL:**
- Type: Public URL
- Label: anything
- URL: direct link to a `.json`, `.md`, or `.txt` file

After adding, tap **Sync** on the new card to pull it immediately.

---

## Offline use

After syncing at least once, the app works fully offline for:
- Searching cached chunks
- Displaying conversation history

LLM calls still require internet (Gemini/Claude). For fully offline operation, use the **Local Server** provider pointed at a laptop running `server.py` with Ollama on the same WiFi network.

---

## Project structure

```
app/src/main/kotlin/com/orqa/chat/
  MainActivity.kt                Navigation host, entry point

  data/
    Database.kt                  Room entities (ChunkEntity, SyncSource, MessageEntity)
                                 DAOs (ChunkDao, SyncSourceDao, MessageDao)
    AppDatabase.kt               Room DB singleton + SettingsStore (DataStore)

  llm/
    LlmService.kt                API clients for Gemini, Claude, local server
                                 Image injection for vision queries

  sync/
    SyncManager.kt               GitHub Contents API fetcher
                                 Public URL fetcher
                                 Text chunking + Room insert
                                 File type filtering

  search/
    SearchEngine.kt              BM25 implementation in Kotlin
                                 CategoryMap — 15-category query classifier
                                 Category and KB score boosting

  ui/
    ChatViewModel.kt             All business logic
                                 Search → context assembly → LLM call → DB save
                                 Sync orchestration, settings observation
    ChatScreen.kt                Main chat UI (Compose)
                                 Message bubbles, source tags, typing indicator
                                 Image attachment picker
    SyncScreen.kt                Source management — add/remove/toggle/sync
                                 Cache management
    SettingsScreen.kt            Provider picker, model selection, API keys
    theme/Theme.kt               Dark ORQA color palette
```

---

## Key dependencies

| Library | Purpose |
|---------|---------|
| Jetpack Compose | UI framework |
| Room | Local SQLite database for chunk cache + messages |
| DataStore | Settings/preferences persistence |
| OkHttp | HTTP client for GitHub API + LLM calls |
| Coil | Image loading for message thumbnails |
| WorkManager | Background sync scheduling |
| Navigation Compose | Screen navigation |

---

## Troubleshooting

**Gradle sync fails**
- File → Invalidate Caches → Invalidate and Restart
- Check internet connection (downloads ~300MB of dependencies)
- Ensure JDK 17 is set: File → Project Structure → SDK Location

**Sync returns 0 files**
- Check GitHub token has `repo` scope
- Verify repo name format is `owner/repo`
- Public URL sources don't need a token

**LLM responses hang or time out**
- Gemini/Claude: check API key is correct in Settings
- Local server: verify laptop IP and that `server.py` is running
- Check phone is on same WiFi network as laptop (for local server)

**App crashes on launch**
- Clean build: Build → Clean Project → Rebuild Project
- Check Android SDK 34 is installed in Android Studio SDK Manager

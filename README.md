# ORQA Chat — Android App

Offline-capable AI chat over your ORQA documentation.
Runs on Android 8+ (API 26+). No Play Store required — sideload the APK.

---

## What it does

- Pulls docs from your GitHub repos and public databases over WiFi
- Caches everything locally in Room DB — works fully offline after first sync
- BM25 search over cached docs — same algorithm as the Python server
- Sends top matching chunks to Gemini / Claude / local server for answers
- Image attach — photo your board and ask about it
- Engineer Call mode — short answers for phone calls
- Dark tactical HUD UI matching the Forge/Wingman aesthetic

---

## Build requirements

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

---

## How to build

1. Open Android Studio
2. File → Open → select the `orqa-android` folder
3. Wait for Gradle sync to finish (downloads dependencies, ~2 min first time)
4. Build → Generate Signed Bundle/APK → APK
5. Use debug key for sideloading, or create a keystore for a proper signed APK

To sideload on your phone:
- Enable Developer Options on Android (Settings → About → tap Build Number 7 times)
- Enable "Install from unknown sources" or "Install unknown apps"
- Transfer the APK to your phone and tap it to install

---

## First run

1. Open the app
2. Tap ⚙ Settings
3. Paste your Gemini API key (free at aistudio.google.com)
4. Paste your GitHub token if you want private repo sync
5. Tap Save
6. Tap the Sync icon → Sync All
7. Wait for indexing to finish
8. Ask anything

---

## Data sources

The app comes pre-configured with:
- Forge Troubleshooting KB (public, no key needed)
- Drone Integration Handbook (public GitHub repo)
- DroneClear Forge (private, needs GitHub token)
- ORQA PX4 Port (private, needs GitHub token)

Add more in the Sync screen — GitHub repos or any public URL.

---

## Offline use

After syncing once over WiFi, all chunks are cached in Room DB on the device.
The app works fully offline for chat (search + LLM calls use the cached data).
Gemini/Claude still need internet for the actual LLM call.

For fully air-gapped use, point Settings → Local Server URL at a laptop
running server.py with Ollama on the same WiFi network.

---

## Project structure

```
app/src/main/kotlin/com/orqa/chat/
  MainActivity.kt          Navigation host
  data/
    Database.kt            Room entities + DAOs
    AppDatabase.kt         Room DB singleton + SettingsStore
  llm/
    LlmService.kt          Gemini, Claude, local server calls
  sync/
    SyncManager.kt         GitHub API + public URL fetching + chunking
  search/
    SearchEngine.kt        BM25 + category boosting
  ui/
    ChatViewModel.kt       All business logic
    ChatScreen.kt          Main chat UI
    SyncScreen.kt          Data source management
    SettingsScreen.kt      Keys + provider selection
    theme/Theme.kt         Dark ORQA color palette
```

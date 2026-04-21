# Troubleshooting

## Windows

### Python not found
```
'python' is not recognized as an internal or external command
```
Python isn't installed or "Add Python to PATH" wasn't checked during install.

Fix: Reinstall Python from [python.org](https://python.org/downloads) and check the PATH box. Then close and reopen CMD.

---

### pip install fails
```
ERROR: Could not find a version that satisfies the requirement flask
```
Usually a network issue or proxy blocking pip.

Fix:
```cmd
pip install flask flask-cors watchdog requests pdfplumber --break-system-packages
```
If behind a corporate proxy, contact your IT team about pip access.

---

### Ollama not found after SETUP.bat
Ollama installed but the PATH hasn't refreshed in the current CMD window.

Fix: Close CMD and reopen it. Run `ollama --version` to confirm.

---

### Chat hangs indefinitely
The browser sends a request but never gets a response.

Diagnoses:
1. Run `TEST.bat` — it checks Ollama connectivity and model availability
2. If using Ollama: look for the Ollama icon in the system tray (bottom right). If not there, search for Ollama in Start and open it
3. If using Claude/Gemini: check your API key in Settings (⚙ icon)
4. Check the CMD window running `server.py` for error messages

---

### Page loads but answers are wrong or irrelevant
The LLM is answering from general knowledge rather than your docs.

Check:
1. The header bar shows chunk count > 0. If 0, no docs are indexed
2. Run SYNC.bat to pull from GitHub, or drop files into `docs\`
3. The source tags under each answer show which files were used — if empty, search found no matches

---

### PDF not being indexed
PDFs over 2MB are skipped. Very short PDFs or image-only PDFs (scanned without OCR) may produce no text.

Fix:
- Run `PROCESS_PDFS.bat` to pre-process PDFs to markdown
- For image-only PDFs, you need an OCR tool first (Windows has built-in OCR in Office)

---

### SYNC.bat fails with "Bad credentials"
GitHub token is expired or was revoked (GitHub auto-revokes tokens found in public web pages).

Fix: Generate a new token at [github.com/settings/tokens](https://github.com/settings/tokens), then delete `.gh_token` and run SYNC.bat again.

---

### Port 5000 already in use
```
OSError: [Errno 98] Address already in use
```
Another process is using port 5000.

Fix: Edit START.bat and change `--port 5000` to `--port 8080`, then go to `http://localhost:8080`.

---

## Android

### Gradle sync fails in Android Studio
- File → Invalidate Caches → Invalidate and Restart
- Check internet connection (downloads ~300MB of dependencies on first sync)
- File → Project Structure → confirm JDK 17 is selected

---

### "Bad credentials" from GitHub sync
Token expired or revoked. Generate a new one at [github.com/settings/tokens](https://github.com/settings/tokens) with `repo` scope and update in Settings.

---

### Sync returns 0 chunks
- Verify GitHub token has `repo` scope for private repos
- For public repos, try without a token first
- Check the error message on the source card in the Sync screen

---

### LLM calls time out
- Gemini/Claude: check API key is correct
- Local server: verify the laptop IP, confirm `server.py` is running, confirm both devices are on the same WiFi
- Ollama on laptop: confirm `ollama serve` or tray app is running

---

### App crashes on launch
```
FATAL EXCEPTION: main
```
Usually a missing build configuration or incompatible SDK version.

Fix:
- Build → Clean Project → Rebuild Project
- Check Android SDK 34 is installed: Android Studio → SDK Manager

---

### "No docs cached" after sync
The sync ran but no chunks were stored. Possible causes:
- All files were filtered out (wrong extension, too large)
- GitHub API returned empty tree (repo may be empty or token lacks access)
- Network error during sync

Check the Sync screen — each source card shows file count and an error message if sync failed.

---

## Getting more help

Run `TEST.bat` (Windows) and paste the output. It covers the 7 most common failure points.

For Android, enable USB debugging and run:
```cmd
adb logcat | findstr "com.orqa.chat"
```
to see crash logs in real time.

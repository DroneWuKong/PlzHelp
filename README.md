# PlzHelp — ORQA Doc Chat

AI-powered chat over your ORQA documentation. Two versions — pick what fits your situation.

---

## Android app  (`/` root)

Fully standalone Android app. No laptop required.

- Pulls docs from GitHub repos and public databases over WiFi
- Caches everything locally — works fully offline after first sync
- BM25 search + Gemini / Claude / local server for answers
- Image attach — photo your board and ask about it
- Engineer Call mode — short answers for phone calls

**Requirements:** Android Studio, JDK 17, Android SDK 34

**Build:** Open the root folder in Android Studio → Build → Generate Signed APK → sideload to phone

**First run:**
1. Settings → paste Gemini key (free at aistudio.google.com)
2. Settings → paste GitHub token for private repo access
3. Sync → Sync All
4. Done

---

## Windows desktop  (`/windows/`)

Python server + browser UI. Runs on your laptop.

- Drop docs into `windows/docs/` folder
- Watches for file changes and re-indexes automatically
- Selectable LLM: Gemini, Claude, Ollama (offline), LM Studio
- Multi-folder support — point at GitHub repos, manual folders, anywhere
- PDF processing pipeline — converts manuals to clean markdown
- Wingman AI knowledge extraction — pulls FALLBACK_KB, WIRING_KB, category map
- Engineer Call mode

**Requirements:** Python 3.12+, Ollama (optional for offline)

**Quick start:**
```
cd windows
pip install -r requirements.txt
python server.py --offline        # Ollama
python server.py --key sk-ant-... # Claude
```
Then open http://localhost:5000

**Batch files (double-click):**
- `SETUP.bat` — installs everything including Ollama
- `SYNC.bat` — pulls latest from your GitHub repos into docs/
- `START.bat` — starts the server (offline/Ollama)
- `START_WITH_KEY.bat` — starts with Claude API key
- `PROCESS_PDFS.bat` — converts PDFs to better search format
- `TEST.bat` — diagnoses any setup issues

---

## Shared features

Both versions use the same core architecture:
- BM25 keyword search with category boosting (15 categories: wiring, motors, ESC, video, radio, GPS, battery, firmware, PID, ORQA, crash, build, compliance, platform, frame)
- Wingman AI knowledge base integration (FALLBACK_KB, WIRING_KB, ORQA mode KB)
- Forge troubleshooting database (58 diagnosed entries)
- Drone Integration Handbook (55 component pages + 7 firmware pages + 9 field guides)
- ORQA PX4 port context (QuadCore H7, WingCore H7 board definitions)
- Two chat modes: Troubleshoot (detailed) and Engineer Call (short, verbally relayable)

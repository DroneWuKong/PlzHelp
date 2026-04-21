# Windows Setup

## Requirements

- Windows 10 or 11
- Python 3.12 or newer
- Internet connection for initial setup (Ollama download, pip packages)
- Ollama (optional — for offline use)

---

## Install Python

1. Open the Microsoft Store and search **Python**, or go to [python.org/downloads](https://python.org/downloads)
2. Install Python 3.12 or newer
3. **Critical:** During install, check **"Add Python to PATH"** — this box is unchecked by default

After installing, close and reopen any CMD windows so the new PATH takes effect.

Verify:
```cmd
python --version
```

---

## First-time setup

Double-click `SETUP.bat`. It will:

1. Verify Python is installed
2. Run `pip install -r requirements.txt` (Flask, watchdog, requests, pdfplumber)
3. Download the Ollama installer from ollama.com (~120MB)
4. Install Ollama silently
5. Pull the `llama3.2` model (~2GB) — this is the slow step

`SETUP.bat` only needs to be run once. After that use `START.bat` daily.

---

## Starting the server

**Offline (Ollama):**
```cmd
START.bat
```
Or manually:
```cmd
cd windows
python server.py --offline
```

**With Claude API:**
```cmd
START_WITH_KEY.bat
```
Prompts for your Anthropic API key at startup. Key is not saved to disk.

Or manually:
```cmd
python server.py --key sk-ant-YOUR_KEY
```

**With Gemini API:**
```cmd
python server.py --provider gemini --key AIzaSy-YOUR_KEY
```

Then open **http://localhost:5000** in your browser.

---

## Adding documents

Drop any of the following into the `windows/docs/` folder:

| Type | Notes |
|------|-------|
| `.pdf` | Extracted automatically. Pre-process with `PROCESS_PDFS.bat` for better results |
| `.md` | Indexed directly |
| `.txt` | Indexed directly |
| `.json` | Flattened to key-value text. Files over 2MB are skipped |

The server watches the folder and re-indexes within ~2 seconds of any change. No restart needed.

### Subfolders

Subfolders are supported. A file at `docs/handbook/motors.md` gets indexed with source label `handbook/motors.md`.

---

## Syncing from GitHub

Run `SYNC.bat` to pull the latest docs from your GitHub repos into `docs/`.

First run asks for a GitHub Personal Access Token. To create one:
1. Go to [github.com/settings/tokens](https://github.com/settings/tokens)
2. Generate new token (classic)
3. Check `repo` scope
4. Copy and paste when prompted

The token is saved to `.gh_token` for future runs. Delete this file to remove it.

SYNC pulls:
- `forge_troubleshooting.json` from droneclear_Forge
- All handbook markdown files
- ORQA PX4 port session context and board files
- Ai-Project session context files

It also runs `strip_forge_db.py` to convert `forge_database.json` into a searchable flat markdown file, and `extract_wingman.py` to pull knowledge bases out of `wingman.html`.

---

## Processing PDFs

Raw PDFs work out of the box but give mediocre search results because the extracted text is flat and unstructured. Pre-processing converts them to clean semantic markdown.

Double-click `PROCESS_PDFS.bat`, or manually:

```cmd
# Offline (Ollama)
python process_pdf.py docs\ --provider ollama --model llama3.2

# With Claude
python process_pdf.py docs\ --key sk-ant-... 

# Just extract without LLM restructuring (fastest)
python process_pdf.py docs\ --no-restructure
```

This processes every PDF in `docs\`. Saves `.md` files next to the originals. The server picks them up automatically.

Only needs to be done once per document.

---

## Multiple folders

Point the server at multiple folders using repeated `--folder` flags:

```cmd
python server.py ^
  --folder "G:\My Drive\Orqa\Manuals":manuals ^
  --folder "C:\Projects\droneclear_Forge":forge ^
  --offline
```

The `PATH:LABEL` syntax after the colon sets the display label for source tags. Labels can be anything.

---

## Command-line reference

```
python server.py [options]

--folder PATH[:LABEL]   Folder to watch and index. Repeatable. Default: ./docs
--key KEY               API key (Anthropic or other provider)
--provider PROVIDER     LLM provider: anthropic | gemini | ollama | lmstudio | openai-compat
--model MODEL           Model name. Uses provider default if omitted
--base-url URL          Override LLM API base URL
--offline               Shortcut: use Ollama with llama3.2
--port PORT             HTTP port. Default: 5000
```

```
python process_pdf.py INPUT [options]

INPUT                   PDF file or folder of PDFs
--output DIR            Output directory (default: same as input)
--key KEY               API key
--provider PROVIDER     anthropic | ollama | lmstudio | openai-compat
--model MODEL           Model name
--no-restructure        Skip LLM restructuring, just extract text
```

---

## Diagnostics

If the server isn't working, run `TEST.bat`. It checks:

1. Python installed
2. Python packages installed
3. PDF support (pdfplumber)
4. Ollama installed
5. Ollama running and responding on port 11434
6. llama3.2 model downloaded
7. Ollama responds to a test query

Paste the output when reporting issues.

---

## File reference

| File | Purpose |
|------|---------|
| `server.py` | Flask server — indexing, search, LLM proxy |
| `process_pdf.py` | PDF → clean markdown pipeline |
| `extract_wingman.py` | Extracts knowledge bases from wingman.html |
| `strip_forge_db.py` | Strips forge_database.json to searchable markdown |
| `index.html` | Chat UI served at localhost:5000 |
| `requirements.txt` | Python dependencies |
| `SETUP.bat` | First-time install (Python packages + Ollama) |
| `START.bat` | Launch server with Ollama |
| `START_WITH_KEY.bat` | Launch server with Claude API key |
| `PROCESS_PDFS.bat` | Convert PDFs in docs\ |
| `SYNC.bat` | Pull latest from GitHub repos |
| `TEST.bat` | Diagnose setup issues |
| `docs/` | Drop your documents here |
| `.gh_token` | Saved GitHub token (delete to remove) |
| `_repos/` | Git clones used by SYNC.bat |

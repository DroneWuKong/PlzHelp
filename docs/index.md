# PlzHelp Documentation

ORQA Doc Chat — AI-powered documentation assistant for ORQA flight controllers, ESCs, and firmware.

## Contents

| Doc | What it covers |
|-----|----------------|
| [Architecture](architecture.md) | How the system works end-to-end |
| [Windows Setup](windows-setup.md) | Install and run the Windows desktop version |
| [Android Setup](android-setup.md) | Build and run the Android app |
| [Data Sources](data-sources.md) | How docs are indexed, synced, and cached |
| [LLM Providers](llm-providers.md) | Gemini, Claude, Ollama, LM Studio — config and tradeoffs |
| [Chat Modes](chat-modes.md) | Troubleshoot vs Engineer Call modes |
| [API Reference](api-reference.md) | Windows server REST API |
| [Troubleshooting](troubleshooting.md) | Common problems and fixes |

---

## Quick orientation

There are two versions of PlzHelp that share the same core idea — BM25 search over cached docs, then an LLM answers using the top matching chunks as context.

**Windows** (`/windows/`) — Python server + browser UI. Runs on your laptop, connects over `localhost`. Best for bench work.

**Android** (`/`) — Native Kotlin/Compose app. Syncs from GitHub and public sources, caches locally in Room DB. Works offline after first sync. Best for field use and phone calls.

Both support Gemini (free), Claude (paid), and local/offline LLMs.

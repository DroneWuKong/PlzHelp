# Architecture

## Overview

PlzHelp is a Retrieval-Augmented Generation (RAG) system. Every time you ask a question, it:

1. Searches indexed document chunks using BM25 keyword scoring
2. Picks the top 12 most relevant chunks
3. Sends those chunks + your question to an LLM
4. Returns the answer with source tags showing which file it came from

The LLM never has to read your entire docs library — it only sees the relevant pieces. This keeps responses fast and accurate regardless of how many documents you have.

---

## Data flow

```
Your question
     │
     ▼
Category detection (15 categories)
     │
     ▼
BM25 search over chunk index
  + category boost (1.4×)
  + Wingman KB boost (1.2×)
     │
     ▼
Top 12 chunks selected
     │
     ▼
System prompt assembled:
  [mode instruction]
  [category tag]
  [doc context chunks]
     │
     ▼
LLM API call (Gemini / Claude / Ollama / local)
     │
     ▼
Answer + source tags displayed
```

---

## Windows architecture

```
┌─────────────────────────────────────────────────┐
│  Browser (localhost:5000)                        │
│  index.html — chat UI, settings drawer, image   │
│               upload, source tags                │
└───────────────────┬─────────────────────────────┘
                    │ HTTP
┌───────────────────▼─────────────────────────────┐
│  server.py (Flask)                               │
│                                                  │
│  /         → serves index.html                  │
│  /status   → index state, folder stats          │
│  /config   → live provider/model switching       │
│  /chat     → search + LLM call + response        │
│                                                  │
│  ┌──────────────┐   ┌────────────────────────┐  │
│  │ BM25 index   │   │ File watcher           │  │
│  │ in-memory    │   │ (watchdog)             │  │
│  │ dict         │   │ re-indexes on change   │  │
│  └──────────────┘   └────────────────────────┘  │
└───────────────────┬─────────────────────────────┘
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  docs/ folder            LLM APIs
  (watched)               Gemini / Anthropic
  ├── forge_troubleshooting.json    / Ollama localhost
  ├── forge_parts.md                / LM Studio localhost
  ├── wingman/
  │   ├── fallback_kb.md
  │   ├── wiring_kb.md
  │   ├── orqa_kb.md
  │   └── category_map.json
  ├── handbook/
  └── orqa-manuals/ (your PDFs → .md)
```

---

## Android architecture

```
┌────────────────────────────────────────────────────┐
│  Compose UI                                         │
│  ChatScreen / SyncScreen / SettingsScreen           │
└──────────────────┬─────────────────────────────────┘
                   │ StateFlow
┌──────────────────▼─────────────────────────────────┐
│  ChatViewModel                                      │
│  - Orchestrates search → context build → LLM call  │
│  - Manages sync state, settings, image attach       │
└──────┬───────────────┬──────────────┬──────────────┘
       │               │              │
       ▼               ▼              ▼
 SearchEngine     LlmService     SyncManager
 BM25 over        Gemini /       GitHub API /
 Room DB          Claude /       Public URLs /
                  Local server   File chunking
       │                              │
       ▼                              ▼
 ┌──────────────────────────────────────────┐
 │  Room Database (SQLite on device)         │
 │  chunks table    — indexed doc chunks     │
 │  sync_sources    — configured sources     │
 │  messages        — conversation history   │
 └──────────────────────────────────────────┘
```

---

## BM25 search

BM25 (Best Match 25) is a probabilistic ranking algorithm. For each query term it computes:

```
score(chunk) = Σ IDF(term) × TF_normalized(term, chunk)

IDF(term) = log((N - df + 0.5) / (df + 0.5) + 1)
  N  = total chunk count
  df = chunks containing the term

TF_normalized = (tf × 2.2) / (tf + 1.2 × (1 - 0.75 + 0.75 × len/avgLen))
  tf     = term frequency in chunk
  len    = chunk word count
  avgLen = average chunk word count
```

### Score boosting

On top of raw BM25, two multipliers apply:

| Condition | Multiplier |
|-----------|-----------|
| Chunk source matches detected query category | 1.4× |
| Chunk is from Wingman KB files (fallback_kb, wiring_kb, orqa_kb) | 1.2× |

---

## Category detection

Every query is classified into one of 15 categories by keyword matching before search. The detected category is used to boost chunks from matching sources and is shown as a tag on each response.

Categories: `wiring`, `motors`, `escs`, `video`, `radio`, `gps`, `battery`, `firmware`, `pid`, `orqa`, `crash`, `build`, `compliance`, `platform`, `frame`

The category keyword map is extracted from Wingman's `category_map.json` during SYNC and can be extended by editing `docs/wingman/category_map.json`.

---

## Chunking

Documents are split into overlapping word-based chunks before indexing:

| Parameter | Value |
|-----------|-------|
| Chunk size | 600 words (Windows) / 500 words (Android) |
| Overlap | 80 words (Windows) / 60 words (Android) |
| Max chars per chunk | 2,000 |
| Max file size | 2 MB (skip above this) |

Overlap ensures that context spanning two chunks isn't missed.

---

## Context assembly

The system prompt sent to the LLM has three sections:

```
[Role definition — ORQA expert assistant]

[Mode instruction — TROUBLESHOOT or ENG CALL]

[Category tag if detected]

[Top 12 chunks, each labeled with folder/source]
```

Total context per query is typically 3,000–8,000 tokens depending on chunk size and LLM model limits.

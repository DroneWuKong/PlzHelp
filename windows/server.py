#!/usr/bin/env python3
"""
ORQA Local Doc Chat Server
- Watches multiple local folders for .md, .txt, .json files
- Indexes and chunks content for BM25 keyword search
- Serves the chatbox at http://localhost:5000
- Supports Anthropic Claude, Ollama, LM Studio, or any OpenAI-compatible backend

Usage examples:

  # Anthropic Claude with two folders
  python server.py \
    --folder ~/DroneWuKong/droneclear_Forge:forge \
    --folder ~/orqa-manuals:manuals \
    --key sk-ant-...

  # Ollama (local, no API key needed)
  python server.py \
    --folder ~/DroneWuKong/droneclear_Forge:forge \
    --folder ~/orqa-manuals:manuals \
    --provider ollama \
    --model llama3.2

  # LM Studio
  python server.py \
    --folder ~/DroneWuKong/droneclear_Forge:forge \
    --provider lmstudio \
    --model mistral-7b-instruct

  # Any OpenAI-compatible endpoint
  python server.py \
    --folder ~/DroneWuKong/droneclear_Forge:forge \
    --provider openai-compat \
    --base-url http://localhost:8080 \
    --model my-model
"""

import argparse
import json
import math
import os
import re
import threading
from collections import defaultdict
from pathlib import Path

import requests
from flask import Flask, jsonify, request, send_from_directory
from flask_cors import CORS
from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer

try:
    import pdfplumber
    PDF_SUPPORT = True
except ImportError:
    PDF_SUPPORT = False
    print("[warn] pdfplumber not installed — PDF indexing disabled. Run: pip install pdfplumber")

# ── Config ────────────────────────────────────────────────────────────────────

SUPPORTED_EXT    = {".md", ".txt", ".json", ".pdf"}
CHUNK_SIZE       = 600
CHUNK_OVERLAP    = 80
TOP_K            = 12
MAX_CHUNK_CHARS  = 2000
MAX_FILE_MB      = 2.0

SKIP_DIRS = {
    ".git", "node_modules", "__pycache__", ".next", "dist", "build",
    ".netlify", "venv", ".venv", ".mypy_cache", ".pytest_cache",
}
SKIP_FILES = {"package-lock.json", "yarn.lock", "poetry.lock"}

PROVIDER_DEFAULTS = {
    "anthropic":     {"base_url": "https://api.anthropic.com",                                          "model": "claude-sonnet-4-20250514"},
    "ollama":        {"base_url": "http://localhost:11434",                                              "model": "llama3.2"},
    "lmstudio":      {"base_url": "http://localhost:1234",                                               "model": "local-model"},
    "openai-compat": {"base_url": "http://localhost:8080",                                               "model": "local-model"},
    "gemini":        {"base_url": "https://generativelanguage.googleapis.com/v1beta/openai",             "model": "gemini-2.0-flash"},
}

app = Flask(__name__)
CORS(app)

# ── Global state ──────────────────────────────────────────────────────────────

index_lock   = threading.Lock()
chunks       = []
inverted     = defaultdict(set)
folder_stats = {}
index_ready  = False
cfg          = {}

# ── Text extraction ───────────────────────────────────────────────────────────

def extract_text(path: Path) -> str:
    ext = path.suffix.lower()
    try:
        if ext in (".md", ".txt"):
            return path.read_text(encoding="utf-8", errors="ignore")
        if ext == ".json":
            data = json.loads(path.read_text(encoding="utf-8", errors="ignore"))
            return json_to_text(data, path.name)
        if ext == ".pdf":
            return extract_pdf_text(path)
    except Exception as e:
        print(f"  [skip] {path.name}: {e}")
    return ""

def extract_pdf_text(path: Path) -> str:
    if not PDF_SUPPORT:
        return ""
    pages = []
    with pdfplumber.open(str(path)) as pdf:
        for page in pdf.pages:
            text = page.extract_text(x_tolerance=3, y_tolerance=3) or ""
            for table in (page.extract_tables() or []):
                rows = []
                for row in table:
                    clean = [str(cell or "").strip().replace("\n", " ") for cell in row]
                    rows.append(" | ".join(clean))
                if rows:
                    text += "\n" + "\n".join(rows)
            pages.append(text)
    raw = "\n\n".join(pages)
    raw = re.sub(r"-\n([a-z])", r"\1", raw)
    raw = re.sub(r"\n{3,}", "\n\n", raw)
    return raw

def json_to_text(data, filename: str) -> str:
    lines = [f"File: {filename}"]
    if isinstance(data, list):
        for item in data[:500]:
            if isinstance(item, dict):
                lines.append(" | ".join(f"{k}: {v}" for k, v in item.items() if v))
            else:
                lines.append(str(item))
    elif isinstance(data, dict):
        lines.extend(flatten_dict(data))
    return "\n".join(lines)

def flatten_dict(d, prefix="", depth=0) -> list:
    lines = []
    if depth > 4:
        return [f"{prefix}: {str(d)[:200]}"]
    for k, v in d.items():
        key = f"{prefix}.{k}" if prefix else k
        if isinstance(v, dict):
            lines.extend(flatten_dict(v, key, depth + 1))
        elif isinstance(v, list):
            if all(isinstance(x, (str, int, float)) for x in v):
                lines.append(f"{key}: {', '.join(str(x) for x in v[:20])}")
            else:
                for item in v[:50]:
                    if isinstance(item, dict):
                        lines.extend(flatten_dict(item, key, depth + 1))
        else:
            if v:
                lines.append(f"{key}: {v}")
    return lines

# ── Chunking ──────────────────────────────────────────────────────────────────

def make_chunks(text: str, source: str, folder_label: str) -> list:
    words = text.split()
    result = []
    i = 0
    while i < len(words):
        chunk = " ".join(words[i:i + CHUNK_SIZE])[:MAX_CHUNK_CHARS]
        result.append({"text": chunk, "source": source, "folder": folder_label})
        i += CHUNK_SIZE - CHUNK_OVERLAP
    return result

def tokenize(text: str) -> list:
    return re.findall(r"[a-z0-9_\-]+", text.lower())

# ── Indexer ───────────────────────────────────────────────────────────────────

def index_one_folder(folder: Path, label: str, new_chunks: list, new_inverted: defaultdict) -> int:
    n_files = 0
    for path in sorted(folder.rglob("*")):
        if any(p.startswith(".") or p in SKIP_DIRS for p in path.parts):
            continue
        if not path.is_file():
            continue
        if path.suffix.lower() not in SUPPORTED_EXT:
            continue
        if path.name in SKIP_FILES:
            continue
        if path.stat().st_size / 1_048_576 > MAX_FILE_MB:
            print(f"  [skip] {path.name} (>{MAX_FILE_MB} MB)")
            continue

        text = extract_text(path)
        if not text.strip():
            continue

        rel = str(path.relative_to(folder))
        file_chunks = make_chunks(text, rel, label)
        base = len(new_chunks)
        for j, ch in enumerate(file_chunks):
            idx = base + j
            new_chunks.append(ch)
            for term in set(tokenize(ch["text"])):
                new_inverted[term].add(idx)

        n_files += 1
        print(f"  [+] [{label}] {rel}  ({len(file_chunks)} chunks)")
    return n_files

def build_index():
    global chunks, inverted, folder_stats, index_ready

    folders = cfg.get("folders", [])
    print(f"\n[index] Building index across {len(folders)} folder(s)...")

    new_chunks   = []
    new_inverted = defaultdict(set)
    new_stats    = {}

    for folder_path, label in folders:
        p = Path(folder_path).expanduser().resolve()
        if not p.exists():
            print(f"  [warn] Not found: {p}")
            continue
        print(f"\n  Scanning [{label}] {p}")
        before  = len(new_chunks)
        n_files = index_one_folder(p, label, new_chunks, new_inverted)
        new_stats[label] = {
            "files":  n_files,
            "chunks": len(new_chunks) - before,
            "path":   str(p),
        }

    with index_lock:
        chunks       = new_chunks
        inverted     = new_inverted
        folder_stats = new_stats
        index_ready  = True

    total = sum(s["files"] for s in new_stats.values())
    print(f"\n[index] Done — {total} files, {len(new_chunks)} chunks\n")

# ── BM25 search ───────────────────────────────────────────────────────────────

# ── Category map (loaded from docs/wingman/category_map.json if present) ────

def load_category_map() -> dict:
    p = Path(__file__).parent / "docs" / "wingman" / "category_map.json"
    if p.exists():
        try:
            return json.load(open(p, encoding="utf-8"))
        except Exception:
            pass
    return {
        "wiring":   ["wire","solder","uart","pad","tx","rx","connect","pinout","gnd"],
        "motors":   ["motor","prop","vibrat","desync","spin","thrust","kv"],
        "escs":     ["esc","blheli","dshot","amp","mosfet","throttle"],
        "video":    ["vtx","video","fpv","camera","osd","antenna","signal"],
        "radio":    ["receiver","bind","elrs","crsf","crossfire","sbus","failsafe"],
        "gps":      ["gps","compass","heading","position","rtk"],
        "battery":  ["battery","lipo","voltage","cell","charge","sag","mah"],
        "firmware": ["betaflight","inav","ardupilot","px4","flash","configurator","cli"],
        "pid":      ["pid","tune","oscillat","wobble","filter","gyro"],
        "orqa":     ["orqa","quadcore","wingcore","3030","h743","h503","osd"],
    }

CATEGORY_MAP = load_category_map()

def detect_category(query: str) -> str:
    """Return the best-matching category for a query, or empty string."""
    q = query.lower()
    best_cat, best_score = "", 0
    for cat, keywords in CATEGORY_MAP.items():
        score = sum(1 for kw in keywords if kw in q)
        if score > best_score:
            best_cat, best_score = cat, score
    return best_cat if best_score > 0 else ""


def search(query: str, k: int = TOP_K) -> list:
    terms    = tokenize(query)
    scores   = defaultdict(float)
    n_docs   = max(len(chunks), 1)
    category = detect_category(query)

    with index_lock:
        for term in terms:
            if term not in inverted:
                continue
            df  = len(inverted[term])
            idf = math.log((n_docs - df + 0.5) / (df + 0.5) + 1)
            for idx in inverted[term]:
                tf = chunks[idx]["text"].lower().count(term)
                scores[idx] += idf * (tf * 2.2) / (tf + 1.2)
                # Boost chunks from category-matching sources
                if category and category in chunks[idx].get("source","").lower():
                    scores[idx] *= 1.4
                # Boost wingman KB files for all queries
                src = chunks[idx].get("source","")
                if "fallback_kb" in src or "wiring_kb" in src or "orqa_kb" in src:
                    scores[idx] *= 1.2

        ranked = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:k]
        return [chunks[idx] for idx, _ in ranked]

# ── File watcher ──────────────────────────────────────────────────────────────

class ReindexHandler(FileSystemEventHandler):
    def __init__(self):
        self._timer = None
        self._lock  = threading.Lock()

    def _schedule(self):
        with self._lock:
            if self._timer:
                self._timer.cancel()
            self._timer = threading.Timer(2.5, build_index)
            self._timer.start()

    def on_modified(self, event):
        if not event.is_directory: self._schedule()
    def on_created(self, event):
        if not event.is_directory: self._schedule()
    def on_deleted(self, event):
        if not event.is_directory: self._schedule()

# ── LLM providers ─────────────────────────────────────────────────────────────

def call_anthropic(system: str, messages: list) -> str:
    key = cfg.get("api_key") or os.environ.get("ANTHROPIC_API_KEY", "")
    if not key:
        raise ValueError("No Anthropic API key. Pass --key or set ANTHROPIC_API_KEY.")
    r = requests.post(
        f"{cfg['base_url']}/v1/messages",
        headers={
            "x-api-key": key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
        json={"model": cfg["model"], "max_tokens": 1024, "system": system, "messages": messages},
        timeout=60,
    )
    r.raise_for_status()
    return r.json()["content"][0]["text"]

def call_openai_compat(system: str, messages: list) -> str:
    """Covers Ollama, LM Studio, llama.cpp — all use the same /v1/chat/completions format."""
    all_msgs = [{"role": "system", "content": system}] + messages
    headers  = {"Content-Type": "application/json"}
    key = cfg.get("api_key") or os.environ.get("OPENAI_API_KEY", "")
    if key:
        headers["Authorization"] = f"Bearer {key}"
    r = requests.post(
        f"{cfg['base_url']}/v1/chat/completions",
        headers=headers,
        json={"model": cfg["model"], "max_tokens": 1024, "messages": all_msgs},
        timeout=120,
    )
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]

def call_llm(system: str, messages: list) -> str:
    if cfg.get("provider") == "anthropic":
        return call_anthropic(system, messages)
    return call_openai_compat(system, messages)

# ── Routes ────────────────────────────────────────────────────────────────────

@app.route("/")
def root():
    return send_from_directory(Path(__file__).parent, "index.html")

@app.route("/status")
def status():
    with index_lock:
        stats = dict(folder_stats)
    return jsonify({
        "ready":    index_ready,
        "provider": cfg.get("provider", "anthropic"),
        "model":    cfg.get("model", ""),
        "folders":  [
            {"label": lbl, "path": s["path"], "files": s["files"], "chunks": s["chunks"]}
            for lbl, s in stats.items()
        ],
        "total_files":  sum(s["files"]  for s in stats.values()),
        "total_chunks": sum(s["chunks"] for s in stats.values()),
    })

@app.route("/config", methods=["GET", "POST"])
def config():
    if request.method == "GET":
        return jsonify({
            "provider": cfg.get("provider", "anthropic"),
            "model":    cfg.get("model", ""),
            "has_key":  bool(cfg.get("api_key")),
        })
    body = request.get_json()
    provider = body.get("provider", cfg.get("provider"))
    model    = body.get("model",    "") or PROVIDER_DEFAULTS.get(provider, {}).get("model", "")
    api_key  = body.get("api_key",  cfg.get("api_key", ""))
    base_url = PROVIDER_DEFAULTS.get(provider, {}).get("base_url", cfg.get("base_url", ""))
    if provider == "gemini":
        base_url = PROVIDER_DEFAULTS["gemini"]["base_url"]
    cfg.update({"provider": provider, "model": model, "api_key": api_key, "base_url": base_url})
    print(f"[config] Switched to {provider} / {model}")
    return jsonify({"ok": True, "provider": provider, "model": model})

@app.route("/chat", methods=["POST"])
def chat():
    if not index_ready:
        return jsonify({"error": "Index not ready — try again in a moment."}), 503

    body     = request.get_json()
    messages = body.get("messages", [])
    mode     = body.get("mode", "troubleshoot")
    image    = body.get("image")   # {data: base64, media_type: "image/jpeg"}
    if not messages:
        return jsonify({"error": "No messages provided."}), 400

    # Extract text query (last message may be string or content list)
    last_msg = messages[-1]
    if isinstance(last_msg.get("content"), list):
        query = " ".join(p.get("text","") for p in last_msg["content"] if p.get("type")=="text")
    else:
        query = last_msg.get("content", "")

    hits  = search(query)
    category = detect_category(query)

    if hits:
        doc_context = "\n\n---\n\n".join(
            f"[{h['folder']} / {h['source']}]\n{h['text']}" for h in hits
        )
        context_block = (
            "The following content was retrieved from your local documentation. "
            "Use it as your primary reference:\n\n" + doc_context
        )
    else:
        context_block = "No matching documentation found. Answer from general ORQA knowledge."

    mode_instruction = (
        "ENGINEER CALL MODE: answers must be SHORT (max 6 lines). Lead with the direct answer. "
        "Plain numbered or dash lists only. No preamble."
        if mode == "engcall" else
        "TROUBLESHOOT MODE: give detailed, step-by-step diagnostic answers. "
        "Reference the source file label when relevant."
    )

    cat_note = f"Query category detected: {category}. " if category else ""

    system = (
        "You are an expert assistant for ORQA FPV flight controllers, ESCs, and firmware — "
        "QuadCore H7, WingCore H7, 3030 Lite F405, 3030 70A ESC, DTK APB, and the H503 OSD "
        "firmware rewrite project.\n\n"
        f"{mode_instruction}\n\n{cat_note}{context_block}"
    )

    # Inject image into last user message if provided
    llm_messages = [dict(m) for m in messages]
    if image and image.get("data"):
        provider  = cfg.get("provider","anthropic")
        last      = dict(llm_messages[-1])
        text_part = last.get("content","")
        if provider == "anthropic":
            last["content"] = [
                {"type":"image","source":{"type":"base64","media_type":image.get("media_type","image/jpeg"),"data":image["data"]}},
                {"type":"text","text":text_part if isinstance(text_part,str) else "Analyze this image."},
            ]
        else:
            # OpenAI-compat format (Gemini, Ollama llava, LM Studio)
            last["content"] = [
                {"type":"text","text":text_part if isinstance(text_part,str) else "Analyze this image."},
                {"type":"image_url","image_url":{"url":f"data:{image.get('media_type','image/jpeg')};base64,{image['data']}"}},
            ]
        llm_messages[-1] = last

    try:
        reply = call_llm(system, llm_messages)
    except requests.HTTPError as e:
        return jsonify({"error": f"LLM API error: {e.response.text}"}), 502
    except Exception as e:
        return jsonify({"error": str(e)}), 500

    sources = list(dict.fromkeys(f"[{h['folder']}] {h['source']}" for h in hits))
    return jsonify({"reply": reply, "sources": sources, "category": category})

# ── Entry point ───────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="ORQA Local Doc Chat Server")
    parser.add_argument(
        "--folder", dest="folders", action="append", metavar="PATH[:LABEL]",
        help="Folder to index. Repeat for multiple. Optional label: --folder ~/manuals:manuals. Defaults to ./docs",
    )
    parser.add_argument("--key",      default="", help="API key (Anthropic or OpenAI-compat)")
    parser.add_argument("--provider", default="anthropic",
                        choices=["anthropic", "ollama", "lmstudio", "openai-compat"])
    parser.add_argument("--model",    default="", help="Model name (uses provider default if blank)")
    parser.add_argument("--base-url", default="", dest="base_url",
                        help="Override API base URL")
    parser.add_argument("--offline",  action="store_true",
                        help="Shortcut: use Ollama with llama3.2, no API key required")
    parser.add_argument("--port",     default=5000, type=int)
    args = parser.parse_args()

    # Default to ./docs folder next to server.py if no folders specified
    if not args.folders:
        default_docs = Path(__file__).parent / "docs"
        default_docs.mkdir(exist_ok=True)
        args.folders = [str(default_docs) + ":docs"]
        print(f"[info] No --folder specified. Watching default: {default_docs}")

    # --offline overrides provider/model to Ollama defaults
    if args.offline:
        args.provider = "ollama"
        args.model    = args.model or "llama3.2"
        print("[offline] Using Ollama — make sure `ollama serve` is running.")

    # Parse PATH:LABEL pairs
    parsed = []
    for raw in args.folders:
        # Split on last colon, but only if what follows looks like a label (no slashes)
        parts = raw.rsplit(":", 1)
        if len(parts) == 2 and "/" not in parts[1] and "\\" not in parts[1] and parts[1]:
            parsed.append((parts[0], parts[1]))
        else:
            parsed.append((raw, Path(raw).expanduser().name))

    provider = args.provider
    defaults = PROVIDER_DEFAULTS[provider]
    model    = args.model    or defaults["model"]
    base_url = args.base_url or defaults["base_url"]

    cfg.update({
        "folders":  parsed,
        "provider": provider,
        "model":    model,
        "base_url": base_url.rstrip("/"),
        "api_key":  args.key or os.environ.get("ANTHROPIC_API_KEY", ""),
    })

    print(f"\n[config] Provider : {provider}")
    print(f"[config] Model    : {model}")
    print(f"[config] Endpoint : {base_url}")
    for path, label in parsed:
        print(f"[config] Folder   : [{label}] {path}")

    build_index()

    handler  = ReindexHandler()
    observer = Observer()
    for path, label in parsed:
        p = Path(path).expanduser().resolve()
        if p.exists():
            observer.schedule(handler, str(p), recursive=True)
            print(f"[watch] [{label}] {p}")
    observer.start()

    print(f"\n[server] http://localhost:{args.port}\n")

    try:
        app.run(port=args.port, debug=False, use_reloader=False)
    finally:
        observer.stop()
        observer.join()

if __name__ == "__main__":
    main()

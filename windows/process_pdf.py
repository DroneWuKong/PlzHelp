#!/usr/bin/env python3
"""
process_pdf.py — PDF → optimized markdown for the ORQA doc chat server.

What it does:
  1. Extracts text from PDF using pdfplumber (layout-aware)
  2. Cleans pagination artifacts, headers/footers, hyphenation
  3. Sends cleaned text to Claude or Ollama to restructure into
     semantic markdown with proper headers, sections, and tables
  4. Saves the .md file next to the PDF (or to --output dir)

The resulting markdown chunks much more cleanly than raw PDF text,
giving the search engine better signal and Claude better context.

Usage:
  # Process a single PDF using Anthropic
  python process_pdf.py QuadCore_User_Manual.pdf --key sk-ant-...

  # Process a single PDF using Ollama (offline)
  python process_pdf.py QuadCore_User_Manual.pdf --provider ollama --model llama3.2

  # Process an entire folder of PDFs
  python process_pdf.py ~/orqa-manuals/ --provider ollama --model llama3.2

  # Specify output directory
  python process_pdf.py ~/orqa-manuals/ --output ~/orqa-manuals/processed/
"""

import argparse
import os
import re
import sys
import textwrap
from pathlib import Path

import pdfplumber
import requests

# ── Config ─────────────────────────────────────────────────────────────────

PROVIDER_DEFAULTS = {
    "anthropic":     {"base_url": "https://api.anthropic.com",  "model": "claude-sonnet-4-20250514"},
    "ollama":        {"base_url": "http://localhost:11434",      "model": "llama3.2"},
    "lmstudio":      {"base_url": "http://localhost:1234",       "model": "local-model"},
    "openai-compat": {"base_url": "http://localhost:8080",       "model": "local-model"},
}

# Max chars to send to LLM per chunk during restructuring
# Keeps within context limits for smaller local models
RESTRUCTURE_CHUNK = 6000

# ── PDF extraction ──────────────────────────────────────────────────────────

def extract_pdf(path: Path) -> str:
    """Extract text from PDF, preserving approximate structure."""
    pages = []
    with pdfplumber.open(str(path)) as pdf:
        total = len(pdf.pages)
        for i, page in enumerate(pdf.pages, 1):
            print(f"  Extracting page {i}/{total}...", end="\r")

            # Extract tables first so we can handle them separately
            tables = page.extract_tables()
            table_text = ""
            for table in tables:
                if table:
                    rows = []
                    for row in table:
                        clean = [str(cell or "").strip().replace("\n", " ") for cell in row]
                        rows.append(" | ".join(clean))
                    if rows:
                        # Build markdown table
                        header = rows[0]
                        divider = " | ".join(["---"] * len(rows[0].split(" | ")))
                        table_text += "\n" + header + "\n" + divider + "\n"
                        table_text += "\n".join(rows[1:]) + "\n"

            # Extract main text
            text = page.extract_text(x_tolerance=3, y_tolerance=3) or ""
            pages.append(f"<!-- page {i} -->\n{text}\n{table_text}")

    print()
    return "\n\n".join(pages)


def clean_raw_text(text: str) -> str:
    """Remove common PDF extraction artifacts."""

    # Fix hyphenated line breaks (word- \ncontinues)
    text = re.sub(r"-\n([a-z])", r"\1", text)

    # Rejoin lines that aren't paragraph breaks
    # A paragraph break = blank line or line ending with . ? ! : followed by capital
    lines = text.split("\n")
    out   = []
    for i, line in enumerate(lines):
        stripped = line.strip()
        if not stripped:
            out.append("")
            continue
        # Keep page markers
        if stripped.startswith("<!-- page"):
            out.append("\n" + stripped)
            continue
        # If previous line exists and this looks like continuation
        if out and out[-1] and not out[-1].startswith("<!--"):
            prev = out[-1].rstrip()
            # Join if previous line doesn't end a sentence
            if prev and not prev[-1] in ".?!:" and not stripped[0].isupper():
                out[-1] = prev + " " + stripped
                continue
        out.append(stripped)

    text = "\n".join(out)

    # Collapse 3+ blank lines to 2
    text = re.sub(r"\n{3,}", "\n\n", text)

    # Remove obvious header/footer noise (short repeated lines)
    lines = text.split("\n")
    line_counts: dict = {}
    for l in lines:
        s = l.strip()
        if 3 < len(s) < 60:
            line_counts[s] = line_counts.get(s, 0) + 1

    # Lines appearing 3+ times are probably headers/footers
    noise = {l for l, c in line_counts.items() if c >= 3}
    lines = [l for l in lines if l.strip() not in noise]
    text  = "\n".join(lines)

    # Remove page markers now that we've used them for structure
    text = re.sub(r"\n<!-- page \d+ -->\n", "\n\n", text)

    return text.strip()


# ── LLM restructuring ────────────────────────────────────────────────────────

RESTRUCTURE_SYSTEM = """You are a technical documentation formatter. You receive raw extracted text from a PDF and output clean, well-structured markdown.

Rules:
- Identify and mark section headers with ## and ###
- Preserve all technical content exactly — numbers, pin names, register values, specs
- Format tables as proper markdown tables
- Keep lists as bullet or numbered lists
- Remove duplicate or garbled text
- Do NOT summarize or omit any content — this is archival restructuring
- Output ONLY the markdown, no commentary, no preamble
- Use --- between major sections for clean chunking"""

RESTRUCTURE_PROMPT = """Restructure this extracted PDF text into clean markdown. Preserve ALL technical content exactly.

RAW TEXT:
{text}"""


def call_anthropic_restructure(text: str, cfg: dict) -> str:
    key = cfg.get("api_key") or os.environ.get("ANTHROPIC_API_KEY", "")
    if not key:
        raise ValueError("No ANTHROPIC_API_KEY set.")
    r = requests.post(
        f"{cfg['base_url']}/v1/messages",
        headers={
            "x-api-key": key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
        json={
            "model":      cfg["model"],
            "max_tokens": 4096,
            "system":     RESTRUCTURE_SYSTEM,
            "messages":   [{"role": "user", "content": RESTRUCTURE_PROMPT.format(text=text)}],
        },
        timeout=120,
    )
    r.raise_for_status()
    return r.json()["content"][0]["text"]


def call_openai_compat_restructure(text: str, cfg: dict) -> str:
    headers = {"Content-Type": "application/json"}
    key = cfg.get("api_key") or os.environ.get("OPENAI_API_KEY", "")
    if key:
        headers["Authorization"] = f"Bearer {key}"
    r = requests.post(
        f"{cfg['base_url']}/v1/chat/completions",
        headers=headers,
        json={
            "model":      cfg["model"],
            "max_tokens": 4096,
            "messages": [
                {"role": "system",  "content": RESTRUCTURE_SYSTEM},
                {"role": "user",    "content": RESTRUCTURE_PROMPT.format(text=text)},
            ],
        },
        timeout=180,
    )
    r.raise_for_status()
    return r.json()["choices"][0]["message"]["content"]


def restructure_with_llm(text: str, cfg: dict) -> str:
    """Split text into chunks, restructure each, rejoin."""
    # Split into ~RESTRUCTURE_CHUNK char segments at paragraph boundaries
    paragraphs = text.split("\n\n")
    segments   = []
    current    = []
    current_len = 0

    for para in paragraphs:
        if current_len + len(para) > RESTRUCTURE_CHUNK and current:
            segments.append("\n\n".join(current))
            current     = [para]
            current_len = len(para)
        else:
            current.append(para)
            current_len += len(para)
    if current:
        segments.append("\n\n".join(current))

    print(f"  Restructuring {len(segments)} segment(s) via {cfg['provider']} / {cfg['model']}...")

    results = []
    for i, seg in enumerate(segments, 1):
        print(f"  Segment {i}/{len(segments)}...", end="\r")
        if cfg["provider"] == "anthropic":
            result = call_anthropic_restructure(seg, cfg)
        else:
            result = call_openai_compat_restructure(seg, cfg)
        results.append(result)

    print()
    return "\n\n---\n\n".join(results)


# ── Main processing pipeline ─────────────────────────────────────────────────

def process_pdf(pdf_path: Path, output_dir: Path, cfg: dict, restructure: bool) -> Path:
    print(f"\n[process] {pdf_path.name}")

    # 1. Extract
    print("  Extracting text...")
    raw = extract_pdf(pdf_path)

    # 2. Clean
    print("  Cleaning artifacts...")
    cleaned = clean_raw_text(raw)

    # 3. Optionally restructure
    if restructure:
        cleaned = restructure_with_llm(cleaned, cfg)
    else:
        print("  Skipping LLM restructure (--no-restructure)")

    # 4. Add header metadata
    header = textwrap.dedent(f"""\
        # {pdf_path.stem.replace('_', ' ').replace('-', ' ')}

        > Source: `{pdf_path.name}`
        > Processed: optimized markdown for semantic search

        ---

    """)
    final = header + cleaned

    # 5. Save
    out_path = output_dir / (pdf_path.stem + ".md")
    out_path.write_text(final, encoding="utf-8")
    size_kb = out_path.stat().st_size // 1024
    print(f"  Saved → {out_path}  ({size_kb} KB)")
    return out_path


# ── Entry point ─────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="PDF → optimized markdown for ORQA doc chat")
    parser.add_argument("input",        help="PDF file or folder of PDFs")
    parser.add_argument("--output",     default="", help="Output directory (default: same as input)")
    parser.add_argument("--key",        default="", help="API key")
    parser.add_argument("--provider",   default="anthropic",
                        choices=["anthropic", "ollama", "lmstudio", "openai-compat"])
    parser.add_argument("--model",      default="", help="Model name")
    parser.add_argument("--base-url",   default="", dest="base_url")
    parser.add_argument("--no-restructure", action="store_true",
                        help="Skip LLM restructuring — just clean and save raw extracted text")
    args = parser.parse_args()

    defaults = PROVIDER_DEFAULTS[args.provider]
    cfg = {
        "provider": args.provider,
        "model":    args.model    or defaults["model"],
        "base_url": (args.base_url or defaults["base_url"]).rstrip("/"),
        "api_key":  args.key or os.environ.get("ANTHROPIC_API_KEY", ""),
    }

    print(f"[config] Provider     : {cfg['provider']}")
    print(f"[config] Model        : {cfg['model']}")
    print(f"[config] Restructure  : {'yes' if not args.no_restructure else 'no (extract only)'}")

    input_path = Path(args.input).expanduser().resolve()

    # Collect PDFs
    if input_path.is_dir():
        pdfs = sorted(input_path.rglob("*.pdf"))
        default_out = input_path
    elif input_path.is_file() and input_path.suffix.lower() == ".pdf":
        pdfs = [input_path]
        default_out = input_path.parent
    else:
        print(f"[error] Not a PDF or directory: {input_path}")
        sys.exit(1)

    output_dir = Path(args.output).expanduser().resolve() if args.output else default_out
    output_dir.mkdir(parents=True, exist_ok=True)

    if not pdfs:
        print("[warn] No PDFs found.")
        sys.exit(0)

    print(f"[info] Found {len(pdfs)} PDF(s) → output to {output_dir}\n")

    for pdf in pdfs:
        try:
            process_pdf(pdf, output_dir, cfg, restructure=not args.no_restructure)
        except Exception as e:
            print(f"  [error] {pdf.name}: {e}")

    print(f"\n[done] {len(pdfs)} PDF(s) processed.")
    print(f"       Drop the .md files into your watched folder and the server picks them up automatically.")


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
extract_wingman.py — pulls reusable knowledge out of wingman.html

Extracts:
  - FALLBACK_KB    : hardcoded FPV knowledge base
  - WIRING_KB      : wiring-specific knowledge
  - Category map   : 15-category auto-tagger keyword list
  - ORQA mode KB   : ORQA-specific system prompt content

Saves each as a .md or .json file in docs\wingman\ so the
local doc chat server can index and search them.

Usage:
  python extract_wingman.py path\to\wingman.html
  python extract_wingman.py  (auto-detects from _repos\droneclear_Forge)
"""

import json
import os
import re
import sys
from pathlib import Path

# ── Paths ─────────────────────────────────────────────────────────

SCRIPT_DIR  = Path(__file__).parent.resolve()
OUTPUT_DIR  = SCRIPT_DIR / "docs" / "wingman"
AUTO_PATHS  = [
    SCRIPT_DIR / "_repos" / "droneclear_Forge" / "DroneClear Components Visualizer" / "wingman.html",
    SCRIPT_DIR / "_repos" / "droneclear_Forge" / "wingman.html",
]

# ── Helpers ───────────────────────────────────────────────────────

def read_html(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def extract_js_string_var(html: str, var_name: str) -> str:
    """Extract a JS template literal or string assigned to a const/let/var."""
    # Template literal: const VARNAME = `...`
    pattern = rf"(?:const|let|var)\s+{re.escape(var_name)}\s*=\s*`(.*?)`"
    m = re.search(pattern, html, re.DOTALL)
    if m:
        return m.group(1).strip()

    # Single-quoted string
    pattern2 = rf"(?:const|let|var)\s+{re.escape(var_name)}\s*=\s*'(.*?)'"
    m2 = re.search(pattern2, html, re.DOTALL)
    if m2:
        return m2.group(1).strip()

    # Double-quoted string
    pattern3 = rf'(?:const|let|var)\s+{re.escape(var_name)}\s*=\s*"(.*?)"'
    m3 = re.search(pattern3, html, re.DOTALL)
    if m3:
        return m3.group(1).strip()

    return ""


def extract_js_object(html: str, var_name: str) -> dict:
    """Extract a JS object/dict assigned to a variable."""
    pattern = rf"(?:const|let|var)\s+{re.escape(var_name)}\s*=\s*(\{{.*?\}})\s*;"
    m = re.search(pattern, html, re.DOTALL)
    if not m:
        return {}
    raw = m.group(1)
    # Convert JS object to JSON: wrap keys in quotes
    raw = re.sub(r"(\w+)\s*:", r'"\1":', raw)
    # Remove trailing commas
    raw = re.sub(r",\s*([\}\]])", r"\1", raw)
    # Convert single-quoted strings to double-quoted
    raw = raw.replace("'", '"')
    try:
        return json.loads(raw)
    except Exception:
        return {}


def extract_between_markers(html: str, start_marker: str, end_marker: str) -> str:
    """Extract text between two string markers."""
    start = html.find(start_marker)
    if start == -1:
        return ""
    start += len(start_marker)
    end = html.find(end_marker, start)
    if end == -1:
        return html[start:].strip()
    return html[start:end].strip()


def clean_js_string(s: str) -> str:
    """Remove JS escape sequences and clean up a string for markdown."""
    s = s.replace("\\n", "\n")
    s = s.replace("\\t", "  ")
    s = s.replace("\\'", "'")
    s = s.replace('\\"', '"')
    s = s.replace("\\\\", "\\")
    return s.strip()


def save(path: Path, content: str):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    size = len(content.encode("utf-8")) // 1024
    print(f"  → {path.relative_to(SCRIPT_DIR)}  ({size} KB)")


# ── Extractors ────────────────────────────────────────────────────

def extract_fallback_kb(html: str) -> bool:
    print("  Extracting FALLBACK_KB...")
    content = extract_js_string_var(html, "FALLBACK_KB")
    if not content:
        # Try searching for the pattern in system prompt blocks
        content = extract_between_markers(html, "FALLBACK_KB = `", "`")
    if not content:
        print("    !! FALLBACK_KB not found — may have a different var name in this version")
        return False
    content = clean_js_string(content)
    out = f"# Wingman Fallback Knowledge Base\n\n> Extracted from wingman.html — general FPV/drone knowledge\n\n---\n\n{content}\n"
    save(OUTPUT_DIR / "fallback_kb.md", out)
    return True


def extract_wiring_kb(html: str) -> bool:
    print("  Extracting WIRING_KB...")
    content = extract_js_string_var(html, "WIRING_KB")
    if not content:
        content = extract_between_markers(html, "WIRING_KB = `", "`")
    if not content:
        print("    !! WIRING_KB not found")
        return False
    content = clean_js_string(content)
    out = f"# Wingman Wiring Knowledge Base\n\n> Extracted from wingman.html — FPV wiring, pad labels, connections\n\n---\n\n{content}\n"
    save(OUTPUT_DIR / "wiring_kb.md", out)
    return True


def extract_orqa_kb(html: str) -> bool:
    print("  Extracting ORQA mode system prompt...")
    # Look for ORQA_SYSTEM or orqaSystem or similar
    for var in ["ORQA_SYSTEM", "ORQA_KB", "orqaSystem", "ORQA_PROMPT", "orqaPrompt"]:
        content = extract_js_string_var(html, var)
        if content:
            content = clean_js_string(content)
            out = f"# Wingman ORQA Mode Knowledge\n\n> Extracted from wingman.html — ORQA product system prompt\n\n---\n\n{content}\n"
            save(OUTPUT_DIR / "orqa_kb.md", out)
            return True

    # Fall back: search for the Orqa mode section by known content
    patterns = [
        r"(QuadCore H7.*?WingCore H7.*?3030.*?ESC.*?)(?:`;|`\s*\n)",
        r"(ORQA.*?QuadCore.*?UART.*?)(?:`;|`\s*\n)",
    ]
    for pat in patterns:
        m = re.search(pat, html, re.DOTALL | re.IGNORECASE)
        if m:
            content = clean_js_string(m.group(1))
            out = f"# Wingman ORQA Mode Knowledge\n\n> Extracted from wingman.html — ORQA product system prompt\n\n---\n\n{content}\n"
            save(OUTPUT_DIR / "orqa_kb.md", out)
            return True

    print("    !! ORQA KB not found as standalone var — check wingman.html manually")
    return False


def extract_category_map(html: str) -> bool:
    print("  Extracting category keyword map...")

    # Known Wingman category map structure
    KNOWN_CATEGORIES = {
        "wiring":     ["wire", "solder", "uart", "pad", "tx", "rx", "connect", "pinout", "ground", "gnd", "vcc", "power"],
        "motors":     ["motor", "prop", "propeller", "vibrat", "desync", "spin", "thrust", "kv", "stator"],
        "escs":       ["esc", "blheli", "dshot", "amp", "mosfet", "throttle", "protocol"],
        "video":      ["vtx", "video", "fpv", "camera", "osd", "antenna", "signal", "range", "channel", "frequency"],
        "radio":      ["receiver", "rx", "bind", "elrs", "crsf", "crossfire", "sbus", "failsafe", "telemetry", "rssi"],
        "gps":        ["gps", "compass", "heading", "toilet bowl", "position", "rtk", "glonass", "beidou", "mag"],
        "battery":    ["battery", "lipo", "voltage", "cell", "puffy", "charge", "sag", "mah", "capacity", "balance"],
        "firmware":   ["betaflight", "inav", "ardupilot", "px4", "flash", "configurator", "cli", "target", "firmware"],
        "compliance": ["ndaa", "blue uas", "itar", "export", "dod", "defense", "faa", "waiver", "authorization"],
        "pid":        ["pid", "tune", "oscillat", "wobble", "filter", "gyro", "rates", "expo", "p gain", "d gain"],
        "crash":      ["crash", "broke", "repair", "damage", "impact", "bent", "broken", "cracked", "burnt"],
        "build":      ["build", "recommend", "best", "compare", "which", "choose", "setup", "stack", "frame size"],
        "orqa":       ["orqa", "quadcore", "wingcore", "mrm", "fpv.one", "3030", "h743", "h503", "osd"],
        "platform":   ["platform", "drone model", "mavic", "skydio", "parrot", "autel", "dji", "inspire", "matrice"],
        "frame":      ["frame", "arm", "crack", "carbon", "mount", "standoff", "stack height", "prop size"],
    }

    # Try to extract from HTML first, fall back to known map
    extracted = extract_js_object(html, "CATEGORY_MAP")
    if not extracted:
        extracted = extract_js_object(html, "categoryMap")
    if not extracted:
        extracted = KNOWN_CATEGORIES
        print("    Using built-in category map (not found in HTML)")

    # Save as JSON for the server to use
    save(OUTPUT_DIR / "category_map.json", json.dumps(extracted, indent=2))

    # Also save as readable markdown
    lines = ["# Wingman Auto-Category Map\n\n> Used to classify queries and boost relevant search results\n\n"]
    for cat, keywords in extracted.items():
        lines.append(f"## {cat}\n")
        lines.append(f"Keywords: {', '.join(keywords)}\n\n")
    save(OUTPUT_DIR / "category_map.md", "".join(lines))
    return True


def extract_troubleshooting_entries(html: str) -> bool:
    """Extract any inline troubleshooting entries hardcoded in the JS."""
    print("  Checking for inline troubleshooting entries...")
    # Look for arrays of {problem, solution} or similar
    pattern = r"\{[^{}]*(?:problem|symptom|issue)[^{}]*(?:solution|fix|cause)[^{}]*\}"
    matches = re.findall(pattern, html, re.IGNORECASE | re.DOTALL)
    if not matches:
        print("    No inline entries found (they're in forge_troubleshooting.json — that's fine)")
        return False

    entries = []
    for m in matches[:100]:  # cap at 100
        try:
            # Normalize to JSON
            normalized = re.sub(r"(\w+)\s*:", r'"\1":', m)
            normalized = re.sub(r",\s*\}", "}", normalized)
            normalized = normalized.replace("'", '"')
            entry = json.loads(normalized)
            entries.append(entry)
        except Exception:
            pass

    if entries:
        content = "# Inline Troubleshooting Entries\n\n> Extracted from wingman.html\n\n---\n\n"
        for e in entries:
            content += f"**Issue:** {e.get('problem', e.get('symptom', e.get('issue', '')))}\n\n"
            content += f"**Fix:** {e.get('solution', e.get('fix', e.get('cause', '')))}\n\n---\n\n"
        save(OUTPUT_DIR / "inline_troubleshooting.md", content)
        return True

    return False


# ── Main ──────────────────────────────────────────────────────────

def main():
    # Find wingman.html
    if len(sys.argv) > 1:
        wingman_path = Path(sys.argv[1]).expanduser().resolve()
    else:
        wingman_path = None
        for p in AUTO_PATHS:
            if p.exists():
                wingman_path = p
                break

    if not wingman_path or not wingman_path.exists():
        print("[error] wingman.html not found.")
        print("  Run SYNC.bat first to clone the repos, or pass the path:")
        print("  python extract_wingman.py path\\to\\wingman.html")
        sys.exit(1)

    print(f"\n[extract] Reading {wingman_path.name}  ({wingman_path.stat().st_size // 1024} KB)")
    html = read_html(wingman_path)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    print(f"[extract] Writing to {OUTPUT_DIR.relative_to(SCRIPT_DIR)}\n")

    results = {
        "FALLBACK_KB":    extract_fallback_kb(html),
        "WIRING_KB":      extract_wiring_kb(html),
        "ORQA KB":        extract_orqa_kb(html),
        "Category map":   extract_category_map(html),
        "Inline entries": extract_troubleshooting_entries(html),
    }

    print()
    found    = sum(1 for v in results.values() if v)
    notfound = [k for k, v in results.items() if not v]
    print(f"[done] {found}/{len(results)} items extracted")
    if notfound:
        print(f"       Not found: {', '.join(notfound)}")
        print("       (These may have different var names in your version of wingman.html)")
    print(f"\n       Files saved to: docs\\wingman\\")
    print("       The server picks them up automatically — no restart needed.\n")


if __name__ == "__main__":
    main()

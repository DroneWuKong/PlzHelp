#!/usr/bin/env python3
"""
strip_forge_db.py — strips forge_database.json to lean searchable markdown.

forge_database.json is ~3,615 parts and often exceeds the 2MB index limit.
This extracts the searchable fields (name, category, description, specs,
NDAA status, PID) and writes a flat markdown file the server can index.

Usage:
  python strip_forge_db.py path/to/forge_database.json path/to/output.md
  python strip_forge_db.py   (auto-detects from _repos folder)
"""

import json
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).parent.resolve()

AUTO_INPUT = SCRIPT_DIR / "_repos" / "droneclear_Forge" / "DroneClear Components Visualizer" / "forge_database.json"
AUTO_OUTPUT = SCRIPT_DIR / "docs" / "forge_parts.md"


def strip(input_path: Path, output_path: Path):
    print(f"  Reading {input_path.name}  ({input_path.stat().st_size // 1024} KB)...")
    raw  = input_path.read_text(encoding="utf-8", errors="ignore")
    data = json.loads(raw)

    # Handle both list and dict formats
    if isinstance(data, dict):
        parts = list(data.values())
    elif isinstance(data, list):
        parts = data
    else:
        print("  !! Unexpected format")
        return

    lines = [
        "# Forge Parts Database",
        "",
        "> Searchable parts reference stripped from forge_database.json",
        "> Format: PID | Name | Category | Description | Specs | NDAA",
        "",
        "---",
        "",
    ]

    n = 0
    for p in parts:
        if not isinstance(p, dict):
            continue

        pid   = str(p.get("pid") or p.get("id") or "")
        name  = str(p.get("name") or "")
        cat   = str(p.get("category") or p.get("type") or "")
        desc  = str(p.get("description") or p.get("desc") or "")[:150]
        ndaa  = ""
        if p.get("ndaa") or p.get("ndaa_compliant") or p.get("ndaa_status") == "compliant":
            ndaa = "NDAA-compliant"
        elif p.get("ndaa_status") == "non-compliant":
            ndaa = "NDAA-non-compliant"

        # Flatten specs
        specs_raw = p.get("specs") or p.get("specifications") or p.get("spec") or {}
        if isinstance(specs_raw, dict):
            specs = " ".join(f"{k}:{v}" for k, v in specs_raw.items() if v)[:100]
        else:
            specs = str(specs_raw)[:100]

        # Skip empty entries
        if not name and not pid:
            continue

        parts_line = " | ".join(filter(None, [pid, name, cat, desc, specs, ndaa]))
        if parts_line.strip():
            lines.append(parts_line)
            n += 1

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text("\n".join(lines), encoding="utf-8")
    size_kb = output_path.stat().st_size // 1024
    print(f"  → {output_path.name}  ({n} parts, {size_kb} KB)")


def main():
    if len(sys.argv) == 3:
        input_path  = Path(sys.argv[1]).expanduser().resolve()
        output_path = Path(sys.argv[2]).expanduser().resolve()
    elif len(sys.argv) == 2:
        input_path  = Path(sys.argv[1]).expanduser().resolve()
        output_path = AUTO_OUTPUT
    else:
        input_path  = AUTO_INPUT
        output_path = AUTO_OUTPUT

    if not input_path.exists():
        print(f"[error] Not found: {input_path}")
        print("  Run SYNC.bat first to clone the repos.")
        sys.exit(1)

    print(f"\n[strip_forge_db] {input_path.name} → {output_path.name}")
    strip(input_path, output_path)
    print()


if __name__ == "__main__":
    main()

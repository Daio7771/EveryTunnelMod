#!/usr/bin/env python3
"""Copies the Spanish (Spain) translation to the other Spanish locales Minecraft offers.

`lang/es_es.json` is the one translation, written by hand. Minecraft does not fall back from
`es_mx` to `es_es`, so without these copies a player on another Spanish locale would see the
game in English. The notes need nothing: `NoteScreen` reads `notes/es/` for any `es_*`.

Run it after every change to `es_es.json`:

    python3 tools/lang/spanish_variants.py

The six files it writes are generated: change `es_es.json` and run this again, never edit them.
"""

import json
from pathlib import Path

LANG = Path(__file__).resolve().parents[2] / "src/main/resources/assets/every_tunnel/lang"
SOURCE = "es_es"
VARIANTS = ["es_ar", "es_cl", "es_ec", "es_mx", "es_uy", "es_ve"]


def main():
    text = (LANG / f"{SOURCE}.json").read_text(encoding="utf-8")
    json.loads(text)  # refuses a broken source before writing anything
    for name in VARIANTS:
        (LANG / f"{name}.json").write_text(text, encoding="utf-8")
    print(f"{SOURCE}.json copied to {', '.join(f'{v}.json' for v in VARIANTS)}")


if __name__ == "__main__":
    main()

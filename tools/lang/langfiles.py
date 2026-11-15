"""The mod's lang files, kept apart by chapter.

Minecraft reads one file per language (`assets/every_tunnel/lang/<locale>.json`), so those are generated: their sources are
`tools/lang/src/<part>/<locale>.json`, one folder per part of the game:

- `base`: Chapter 1 and everything every chapter shares (the menu, the HUD, the base game's blocks and creatures);
- `chapter2`, and later `chapter3`...: that chapter's own texts (its levels, its voices, its blocks and people).

[build] merges the parts, `base` first and then the chapters in order (a key in two parts is an error), writes the game's files
and copies `es_es` to the other Spanish locales (`spanish_variants.py`). English is written ASCII-escaped, Spanish as it is.

The tools that write texts (block and creature names, voice subtitles) call [update] with their part; texts written by hand go
in the part's file and then:

    python3 tools/lang/build.py

Never edit `assets/every_tunnel/lang/*.json` by hand: `LangFilesTest` checks they are the merge of the sources.
"""

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import spanish_variants  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools" / "lang" / "src"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "lang"
LOCALES = ["en_us", "es_es"]
BASE = "base"


def parts():
    """The parts in merge order: base, then each chapter's folder in order (chapter2, chapter3...)."""
    chapters = sorted((p.name for p in SRC.iterdir() if p.is_dir() and p.name.startswith("chapter")), key=lambda n: int(n[len("chapter"):]))
    return [BASE] + chapters


def _dump(locale, data):
    return json.dumps(data, indent=2, ensure_ascii=(locale == "en_us")) + "\n"


def load(part, locale):
    path = SRC / part / f"{locale}.json"
    return json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}


def save(part, locale, data):
    path = SRC / part / f"{locale}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_dump(locale, data), encoding="utf-8")


def merged(locale):
    out = {}
    owner = {}
    for part in parts():
        for key, value in load(part, locale).items():
            if key in out:
                raise SystemExit(f"{locale}: {key} is in both {owner[key]} and {part}")
            out[key] = value
            owner[key] = part
    return out


def build():
    for locale in LOCALES:
        (LANG / f"{locale}.json").write_text(_dump(locale, merged(locale)), encoding="utf-8")
    spanish_variants.main()


def update(part, locale, entries, drop_prefix=None):
    """Sets [entries] in [part]'s [locale] file (first dropping its keys that start with [drop_prefix]), then rebuilds."""
    data = load(part, locale)
    if drop_prefix:
        data = {k: v for k, v in data.items() if not k.startswith(drop_prefix)}
    data.update(entries)
    save(part, locale, data)
    build()

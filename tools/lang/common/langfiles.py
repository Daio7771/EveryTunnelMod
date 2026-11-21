"""The mod's lang files, kept apart by chapter.

Minecraft reads one file per language (`assets/every_tunnel/lang/<locale>.json`), so those are generated: their sources are
`tools/lang/src/<part>/<locale>.json`, one folder per part of the game:

- `common`: everything every chapter shares (the menu, the HUD, the shared blocks and creatures, the texts of the engine);
- `chapter1`, `chapter2`, and later `chapter3`...: that chapter's own texts (its levels, its voices, its blocks and people).

[build] merges the parts, `common` first and then the chapters in order (a key in two parts is an error), writes the game's files
and copies `es_es` to the other Spanish locales (`spanish_variants.py`). English is written ASCII-escaped, Spanish as it is.

The tools that write texts (block and creature names, voice subtitles) call [update] with their part; texts written by hand go
in the part's file and then:

    python3 tools/lang/common/build.py

Never edit `assets/every_tunnel/lang/*.json` by hand: `LangFilesTest` checks they are the merge of the sources.

The notes read in game (`assets/every_tunnel/notes/<en|es>/<id>.txt`) that are documents of a chapter are written from the lang
sources too, so a text lives in one place: [NOTES] names each note's id and the lang key it starts with (`<key>_title` then
`<key>_body`); [build] writes them (the title, a blank line, the body).
"""

import json
import sys
from pathlib import Path

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('lang')
sys.path.insert(0, str(Path(__file__).resolve().parent))

import spanish_variants  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
SRC = ROOT / "tools" / "lang" / "src"
LANG = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "lang"
LOCALES = ["en_us", "es_es"]
COMMON = "common"


def parts():
    """The parts in merge order: common (what every chapter shares), then each chapter's folder in order (chapter1, chapter2...)."""
    chapters = sorted((p.name for p in SRC.iterdir() if p.is_dir() and p.name.startswith("chapter")), key=lambda n: int(n[len("chapter"):]))
    return [COMMON] + chapters


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


NOTES_DIR = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "notes"
NOTES = {
    "ch2_l2_old_drainage": "every_tunnel.ch2.l2.old_drainage",
    "ch2_l2_maintenance_observation": "every_tunnel.ch2.l2.maintenance_observation",
    "ch2_l2_withdrawal_order": "every_tunnel.ch2.l2.withdrawal_order",
}


def write_notes():
    """The notes that come from the lang sources ([NOTES]), one file per language (`en`, `es`)."""
    for locale, folder in (("en_us", "en"), ("es_es", "es")):
        texts = merged(locale)
        (NOTES_DIR / folder).mkdir(parents=True, exist_ok=True)
        for note, key in NOTES.items():
            title, body = texts[key + "_title"], texts[key + "_body"]
            (NOTES_DIR / folder / f"{note}.txt").write_text(f"{title}\n\n{body}\n", encoding="utf-8")


def build():
    for locale in LOCALES:
        (LANG / f"{locale}.json").write_text(_dump(locale, merged(locale)), encoding="utf-8")
    spanish_variants.main()
    write_notes()


def update(part, locale, entries, drop_prefix=None):
    """Sets [entries] in [part]'s [locale] file (first dropping its keys that start with [drop_prefix]), then rebuilds."""
    data = load(part, locale)
    if drop_prefix:
        data = {k: v for k, v in data.items() if not k.startswith(drop_prefix)}
    data.update(entries)
    save(part, locale, data)
    build()

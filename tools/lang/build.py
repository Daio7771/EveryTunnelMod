#!/usr/bin/env python3
"""Writes the game's lang files from the sources by chapter (`tools/lang/src/<part>/<locale>.json`; see `langfiles.py`).

    python3 tools/lang/build.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import langfiles  # noqa: E402

if __name__ == "__main__":
    langfiles.build()
    print("lang files built from " + ", ".join(langfiles.parts()))

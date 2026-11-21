#!/usr/bin/env python3
"""Writes the game's lang files from the sources by chapter (`tools/lang/src/<part>/<locale>.json`; see `langfiles.py`).

    python3 tools/lang/common/build.py
"""

import sys
from pathlib import Path

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('lang')
sys.path.insert(0, str(Path(__file__).resolve().parent))

import langfiles  # noqa: E402

if __name__ == "__main__":
    langfiles.build()
    print("lang files built from " + ", ".join(langfiles.parts()))

"""
Where the tools' modules live. Each area of `tools/` (blocks, entity, film, gui, lang, map, prologue, sounds) has up to three
folders: `common/` (what every chapter shares, and the libraries), `chapter1/` and `chapter2/`. The modules import each other by plain
name (`import langfiles`), so a tool that needs another area's modules calls `toolpaths.use("lang", ...)`, which puts every part
folder of those areas on `sys.path`.
"""
import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
ROOT = TOOLS.parent
PARTS = ("common", "chapter1", "chapter2")


def use(*areas: str) -> None:
    for area in areas:
        for part in PARTS:
            folder = TOOLS / area / part
            if folder.is_dir() and str(folder) not in sys.path:
                sys.path.append(str(folder))

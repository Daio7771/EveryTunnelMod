"""
The main menu's scene for Chapter 2: the prologue's laboratory (room.py's plan) with its bench, the radiation
counter, the tubes and the lights, but no scientist: the menu draws blocks only (client.menu.MenuScene). The camera
starts in the room's south-west corner looking along the bench to the north-east and drifts slowly east and back,
easing at both ends; `vanish_x` puts the picture to the right of the menu's black.

It is not part of the world. Run (from the repository root):   python3 tools/prologue/chapter2/menu_scene.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  menu/lab_scene.json     the camera (and where it goes, in seconds), a palette of block states and the blocks

Everything here is a vanilla block or one of the mod's own, drawn with its own model, so there are no re-skinned models
to copy (the garage's scene needs them). The camera's numbers are the ones to change to frame it differently.
"""

import json
import sys
from pathlib import Path

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('prologue')
sys.path.insert(0, str(Path(__file__).resolve().parent))
import room  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "menu" / "lab_scene.json"

# The camera: where it starts and ends, how long the way takes (seconds; it goes and comes back), where it looks
# (Minecraft's yaw, 0 south and growing towards west, and pitch, positive down), its field of view and where the
# picture's middle sits across the screen.
CAMERA = {"pos": [3.0, 1.55, 7.2], "to": [9.5, 1.55, 7.2], "seconds": 70, "yaw": 241.0, "pitch": 5.0, "fov": 70.0, "vanish_x": 0.68}


def world():
    """Every block of the room, by cell: the fills in their order, then the single blocks; air left out."""
    cells = {}
    fills, blocks, _ = room.build()
    for (x0, y0, z0), (x1, y1, z1), block in fills:
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    cells[(x, y, z)] = block
    for cell, block in blocks:
        cells[cell] = block
    return {cell: block for cell, block in cells.items() if block != room.AIR}


def main():
    cells = world()
    palette, index, blocks = [], {}, []
    for cell in sorted(cells):
        state = cells[cell]
        if state not in index:
            index[state] = len(palette)
            palette.append({"state": state})
        blocks.append([*cell, index[state]])
    scene = {"camera": CAMERA, "palette": palette, "blocks": blocks}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(scene, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"Chapter 2's menu scene: {len(blocks)} blocks, {len(palette)} block states -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())

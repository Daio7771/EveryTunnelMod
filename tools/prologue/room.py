"""
The prologue's room, for Chapter 2: a modern, clean laboratory with one long bench, where the scientist of
the opening cinematic works. The camera tracks along the bench, left to right (+x), and stops behind him
(core.cutscene.PrologueScene); at the end he turns into a Pale man.

The room is not part of the map: the mod builds it when the cinematic starts, high over the world and far
from every level (platform.PrologueSceneHooks), and clears it when it ends, so this tool writes a plan
instead of a datapack. Coordinates are the room's own: x east, y up (the floor of the air is y = 0), z south;
the room is 20 x 5 x 9 of air, a shell of white concrete round it, its bench along the north wall.

Run after changing anything here (from the repository root):   python3 tools/prologue/room.py

Writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  cutscene/prologue_room.json     the plan (read by core.cutscene.RoomPlan): fills, single blocks, the ceiling's
                                  lights, where the scientist stands, where the radiation counter sounds and where
                                  the player is held

The bench is two blocks deep (z = 0 and 1) with its front towards the room (facing south), 1 block high: the
scientist stands at z = 2 facing it, his hands over its edge. The props stand on it (y = 1) facing south too.
The camera's lens (x from 4.5 to 12.3, z = 5.7, height 1.5) sees the bench and the north wall only.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "cutscene" / "prologue_room.json"

W, H, D = 20, 5, 9            # the air: x 0..19, y 0..4, z 0..8
MOD = "every_tunnel:"

WALL = "minecraft:white_concrete"
DADO = "minecraft:gray_concrete"
FLOOR = "minecraft:light_gray_concrete"
AIR = "minecraft:air"

# Ceiling lights (cells of the roof's layer, y = H) in two rows over the bench's front and the room's back.
LIGHT_XS = (2, 6, 10, 14, 18)
LIGHT_ZS = (2, 6)
VENTS = ((4, 4), (12, 4), (16, 4))

# What stands on the bench, west to east: (x, z, block). z = 1 the front row (near the camera), z = 0 against the wall.
PROPS = [
    (1, 0, "lab_monitor"), (1, 1, "lab_beakers"),
    (2, 1, "microscope"),
    (3, 0, "lab_monitor"), (3, 1, "tube_rack"),
    (4, 1, "glove_box"),
    (5, 0, "microscope"), (5, 1, "lab_beakers"),
    (6, 1, "tube_rack"),
    (7, 0, "lab_monitor"),
    (8, 1, "microscope"),
    (9, 0, "glove_box"), (9, 1, "lab_beakers"),
    (10, 1, "tube_rack"),
    (11, 0, "lab_monitor"), (11, 1, "radiation_counter"),     # its screen is in frame as the camera stops
    (12, 1, "microscope"),
    (13, 0, "lab_monitor"), (13, 1, "voice_recorder"),          # the log is dictated into it
    (14, 1, "tube_rack"),                         # his hands are over this
    (15, 1, "lab_beakers"),
    (16, 0, "lab_monitor"),
    (17, 1, "coffee_cup"),
    (18, 1, "lab_beakers"),
]

# On the north wall over the bench: posters (y = 2) and the two signs that say what the room is (y = 3).
WALL_DECOR = [
    (2, 2, "lab_poster"), (9, 2, "lab_poster"), (16, 2, "lab_poster"),
    (6, 3, "sign_radiation"), (13, 3, "sign_biohazard"),
]

# The scientist stands at the bench's front, his feet on the floor, facing north (yaw 180); the player (not
# drawn) is held in the room's south-west corner.
SCIENTIST = {"at": [14.5, 0.0, 2.5], "yaw": 180.0}
# The radiation counter's screen (the Geiger clicks come from here): the middle of its block, at its height.
COUNTER = {"at": [11.5, 1.35, 1.5], "yaw": 180.0}
VIEWER = {"at": [1.5, 0.0, 7.5], "yaw": 0.0}


def build():
    fills = [
        ((-1, -1, -1), (W, H, D), WALL),                  # the shell, solid
        ((0, 0, 0), (W - 1, H - 1, D - 1), AIR),          # the air
        ((0, -1, 0), (W - 1, -1, D - 1), FLOOR),
        # The wall's lowest course in a darker grey all round.
        ((-1, 0, -1), (-1, 0, D), DADO), ((W, 0, -1), (W, 0, D), DADO),
        ((-1, 0, -1), (W, 0, -1), DADO), ((-1, 0, D), (W, 0, D), DADO),
    ]
    blocks = []
    for x in range(W):
        for z in (0, 1):
            # A row's pieces join along the row: left is west, right is east for a bench facing south.
            state = f"facing=south,left={str(x > 0).lower()},right={str(x < W - 1).lower()}"
            blocks.append(((x, 0, z), f"{MOD}lab_bench[{state}]"))
    for x, z, name in PROPS:
        blocks.append(((x, 1, z), f"{MOD}{name}[facing=south]"))
    for x, y, name in WALL_DECOR:
        blocks.append(((x, y, 0), f"{MOD}{name}[facing=south]"))
    lights = [(x, H, z) for x in LIGHT_XS for z in LIGHT_ZS]
    for x, y, z in lights:
        blocks.append(((x, y, z), f"{MOD}lab_ceiling_light"))
    for x, z in VENTS:
        blocks.append(((x, H, z), f"{MOD}lab_ceiling_vent"))
    return fills, blocks, lights


def checks(blocks, lights):
    states = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "blockstates"
    for _, block in blocks:
        name = block.split("[")[0]
        if name.startswith(MOD) and not (states / f"{name[len(MOD):]}.json").exists():
            raise SystemExit(f"{name} is not a block of the mod (no blockstate): run tools/blocks/generate.py?")
    cells = [c for c, _ in blocks]
    if len(cells) != len(set(cells)):
        raise SystemExit("two blocks in one cell")
    for x, y, z in cells:
        if not (0 <= x < W and 0 <= z < D and 0 <= y <= H):
            raise SystemExit(f"a block outside the room: {(x, y, z)}")
    # Nothing on the bench in the scientist's own cell or between him and it.
    sx, _, sz = SCIENTIST["at"]
    if any(c == (int(sx), 0, int(sz)) or c == (int(sx), 1, int(sz)) for c in cells):
        raise SystemExit("the scientist stands inside a block")
    for x, y, z in lights:
        if y != H:
            raise SystemExit("a light off the ceiling")


def line(items):
    return "[\n" + ",\n".join("  " + json.dumps(i, separators=(",", ":")) for i in items) + "\n ]"


def main():
    fills, blocks, lights = build()
    checks(blocks, lights)
    plan = {
        "shell": {"from": [-1, -1, -1], "to": [W, H, D]},
        "interior": {"from": [0, 0, 0], "to": [W - 1, H - 1, D - 1]},
        "fills": [{"from": list(a), "to": list(b), "block": block} for a, b, block in fills],
        "blocks": [{"at": list(at), "block": block} for at, block in blocks],
        "lights": [list(c) for c in lights],
        "scientist": SCIENTIST,
        "counter": COUNTER,
        "viewer": VIEWER,
    }
    text = ("{\n" + ",\n".join(
        f' "{key}": ' + (line(value) if isinstance(value, list) else json.dumps(value, separators=(",", ":")))
        for key, value in plan.items()) + "\n}\n")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(text, encoding="utf-8")
    print(f"The prologue's room: {len(fills)} fills, {len(blocks)} blocks, {len(lights)} lights -> {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    sys.exit(main())

"""
Chapter 2, Level 2's decoration (phase 8): what the rooms hold besides their shell, by rule, from layout.json's `decor` (a list of
rules per style; a space can switch it off with `"decor": false`). Hand-placed pieces (the set pieces) are `prop` features there.

Rules never block the way: a block goes only on a cell of air touching a wall (or, for floor clusters, a corner), never within
`checks.decor.door_clear` blocks of an opening, never on a ladder, flight, deck or rail, never on a cell an anchor stands on or the
water covers, and never where something else already is. Which cells take one is decided by `cell_hash`, so the seed alone decides.

Rule kinds (all take `share`, the chance per eligible cell, and `salt`, which tells two rules apart):
- `run`: a pipe or cable tray run along a wall, `h` blocks over the floor, in stretches of `run` blocks every `period` (the
  stretches are a share of the wall, joined along the wall's direction).
- `wall`: a block against a wall `h` over the floor, facing away from it (`{facing}` in the block string): a valve wheel, a gauge,
  a dead sconce; with `attach`, only right under a block of that kind (a valve or gauge hangs on a pipe run).
- `stain`: a flat mark on a wall at one of the heights `h` (`{facing}`, `{pattern}` in the block string, `patterns` of them).
- `floor`: a block on a floor cell touching a wall, in a random facing (`{facing}`); `blocks` is a list picked by hash.
- `corner`: like `floor`, only on cells that touch two walls, and each also puts a second block beside it along the wall.
"""

import json
import math
from pathlib import Path

from level import FACING, SIDES, cell_hash

LEVEL_JSON = Path(__file__).resolve().parents[4] / "src" / "main" / "resources" / "data" / "every_tunnel" / "ch2" / "l2" / "level.json"

HORIZONTAL = ["north", "east", "south", "west"]


def door_zone(lv, clear):
    """{(x, y, z)} within `clear` blocks (in x and z) of an opening, over the y range of the opening and a little above."""
    out = set()
    for cells in lv.openings.values():
        ys = [c[1] for c in cells]
        for x, y, z in cells:
            for dx in range(-clear, clear + 1):
                for dz in range(-clear, clear + 1):
                    for yy in range(min(ys) - 1, max(ys) + 4):
                        out.add((x + dx, yy, z + dz))
    return out


def anchor_cells(lv):
    """The cells a player or a thing of level.json stands on: the places of the route, the loot (and the cells round it), the hunters' posts."""
    out = {(int(a["pos"][0] // 1), int(a["pos"][1]), int(a["pos"][2] // 1)) for a in lv.layout["anchors"] if a.get("kind") != "wall"}
    if LEVEL_JSON.exists():
        doc = json.loads(LEVEL_JSON.read_text(encoding="utf-8"))
        anchors = {a["id"]: a for a in lv.layout["anchors"]}
        for item in doc.get("loot", []):
            a = anchors.get(item["anchor"])
            if a:
                dx, dy, dz = item.get("offset", [0, 0, 0])
                x, y, z = (math.floor(v) for v in a["pos"])
                out.add((x + dx, y + dy, z + dz))
    return out


def lv_air_free(lv, c, s):
    return lv.air.get(c) == s.id and c not in lv.blocks and c not in lv.water


class Decor:
    def __init__(self, lv):
        self.lv = lv
        self.cfg = lv.layout["checks"]["decor"]
        self.doors = door_zone(lv, self.cfg["door_clear"])
        self.anchors = anchor_cells(lv)
        self.counts = {}
        self.cells = {}
        for c, owner in lv.air.items():
            self.cells.setdefault(owner, []).append(c)
        for cells in self.cells.values():
            cells.sort()

    def height(self, c):
        """How high a cell is over what a player stands on there (the floor, a deck, the overlook, a catwalk)."""
        return c[1] - self.lv.surface(c[0], c[1], c[2])

    def wall_sides(self, c):
        """[(dx, dz)] of the sides of a cell that touch a plain wall."""
        x, y, z = c
        out = []
        for dx, dz in SIDES:
            n = (x + dx, y, z + dz)
            role = self.lv.shell.get(n)
            if role and role[0] in ("wall", "dado") and n not in self.lv.blocks:
                out.append((dx, dz))
        return out

    def free(self, c, s):
        lv = self.lv
        return (lv.air.get(c) == s.id and c not in lv.blocks and c not in lv.water and c not in lv.climb and c not in lv.fill
                and c not in self.doors and (c[0], c[1], c[2]) not in self.anchors)

    def on_floor(self, c):
        under = self.lv.shell.get((c[0], c[1] - 1, c[2]))
        return bool(under and under[0] == "floor") and (c[0], c[1] - 1, c[2]) not in self.lv.blocks

    def roomy(self, c, s, sides):
        """True if the way past a solid block here stays two wide: the two cells away from each wall it touches are free air."""
        x, y, z = c
        for dx, dz in sides:
            for k in (1, 2):
                n = (x - dx * k, y, z - dz * k)
                if lv_air_free(self.lv, n, s):
                    continue
                return False
        return True

    def put(self, c, block, what):
        self.lv.blocks[c] = block
        self.counts[what] = self.counts.get(what, 0) + 1

    def chance(self, c, rule, salt=0):
        return cell_hash(*c, self.lv.seed + 500 + rule.get("salt", 0) + salt) % 10000 < rule["share"] * 10000

    # --- the rules ----------------------------------------------------------------------------

    def run(self, s, rule):
        lv = self.lv
        axis_of = {(1, 0): "z", (-1, 0): "z", (0, 1): "x", (0, -1): "x"}
        cells = {}
        for c in self.cells.get(s.id, []):
            x, y, z = c
            if not self.free(c, s):
                continue
            sides = self.wall_sides(c)
            if not sides or self.height(c) != rule["h"]:
                continue
            below = lv.blocks.get((x, y - 1, z))
            if rule.get("avoid_below") and isinstance(below, str) and below.startswith(rule["avoid_below"]):
                continue
            for dx, dz in sides:
                along = z if dx else x
                line = x + dx if dx else z + dz                      # which wall
                start = cell_hash(line, y, 0, lv.seed + 530 + rule.get("salt", 0)) % rule["period"]
                if (along + start) % rule["period"] < rule["run"]:
                    cells[c] = axis_of[(dx, dz)]
                    break
        for c, axis in cells.items():
            x, y, z = c
            n = {"x": ((1, 0), (-1, 0)), "z": ((0, 1), (0, -1))}[axis]
            links = {FACING[d]: (x + d[0], y, z + d[1]) in cells and cells[(x + d[0], y, z + d[1])] == axis for d in n}
            if not any(links.values()):
                continue                                              # a lone piece is no run
            props = {d: str(links.get(d, False)).lower() for d in HORIZONTAL} | {"up": "false", "down": "false"}
            self.put(c, rule["block"] + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]", rule["block"])

    def wall(self, s, rule):
        lv = self.lv
        for c in self.cells.get(s.id, []):
            sides = self.wall_sides(c)
            if not sides or not self.chance(c, rule) or not self.free(c, s) or self.height(c) != rule["h"]:
                continue
            if rule.get("attach") and not str(lv.blocks.get((c[0], c[1] + 1, c[2]), "")).startswith(rule["attach"]):
                continue
            dx, dz = sides[0]
            self.put(c, rule["block"].format(facing=FACING[(-dx, -dz)]), rule["block"].split("[")[0])

    def stain(self, s, rule):
        lv = self.lv
        for c in self.cells.get(s.id, []):
            x, y, z = c
            sides = self.wall_sides(c)
            if not sides or not self.chance(c, rule) or not self.free(c, s) or self.height(c) not in rule["h"]:
                continue
            dx, dz = sides[cell_hash(*c, lv.seed + 540) % len(sides)]
            pattern = cell_hash(*c, lv.seed + 541) % rule["patterns"]
            self.put(c, rule["block"].format(facing=FACING[(-dx, -dz)], pattern=pattern), rule["block"].split("[")[0])

    def floor(self, s, rule, corner=False):
        lv = self.lv
        for c in self.cells.get(s.id, []):
            x, y, z = c
            sides = self.wall_sides(c)
            if len(sides) < (2 if corner else 1) or not self.chance(c, rule) or not self.on_floor(c) or not self.free(c, s):
                continue
            if not self.roomy(c, s, sides):
                continue
            blocks = rule["blocks"]
            h = cell_hash(*c, lv.seed + 550)
            facing = HORIZONTAL[h % 4]
            self.put(c, blocks[(h >> 4) % len(blocks)].format(facing=facing), "floor")
            if corner:
                dx, dz = sides[0]
                for sx, sz in SIDES:
                    n = (x + sx, y, z + sz)
                    if (sx, sz) != (-dx, -dz) and (sx, sz) != (dx, dz) and self.free(n, s) and self.on_floor(n) and self.wall_sides(n):
                        h2 = cell_hash(*n, lv.seed + 551)
                        self.put(n, blocks[(h2 >> 4) % len(blocks)].format(facing=HORIZONTAL[h2 % 4]), "floor")
                        break

    def space(self, s):
        for rule in self.lv.layout["decor"].get(s.style, []):
            getattr(self, rule["kind"] if rule["kind"] != "corner" else "floor")(s, rule, *([True] if rule["kind"] == "corner" else []))


def lay_decor(lv):
    d = Decor(lv)
    for s in lv.spaces.values():
        if s.raw.get("decor", True):
            d.space(s)
    lv.decor_counts = d.counts

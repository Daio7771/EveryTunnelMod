"""
Chapter 2, Level 2's map: from layout.json, the plan the mod builds, the named places, the flood's stages and pictures.

Run (from the repository root):   python3 tools/map/chapter2/ch2l2/generate.py [--install]

It runs the checks of checks.py first and stops, writing nothing, if one fails. Then it writes tools/map/chapter2/ch2l2/output/
(git-ignored): map.json, anchors.json, flood.json and the previews of preview.py. With --install it also puts the three
JSON files into src/main/resources/data/every_tunnel/ch2/l2/ (committed with the mod): never edit those by hand, change
layout.json (or level.py) and run this again.

map.json, as Level 1's (chapter2.core.MapPlan): "origin" (the world position of plan cell 0, 0, 0), "clear" (the box emptied
before building, both corners included), "fills" (boxes of one block, or of a weighted "mix" with its "seed": the mod picks a
block for each cell with MapPlan.pick, as level.pick does here), "lights" (the lamps' cells), "texts" and "messages" (none yet)
and "gates" (each gate's cells and the block of each of its states; the build puts the "built" one).
anchors.json: points (the places of the route and the rest, each standing on its floor), zones (the boxes the level's systems
watch, from the floor's blocks to the air's top) and paths, in world coordinates. flood.json: each stage of the flood with
its trigger, timing and level, the gates' states while it holds water, and its water as boxes in plan coordinates.
"""

import json
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import checks  # noqa: E402
import level as L  # noqa: E402
import preview  # noqa: E402

ROOT = HERE.parents[3]
OUT = HERE / "output"
DATA = ROOT / "src" / "main" / "resources" / "data" / "every_tunnel" / "ch2" / "l2"


def compact(items):
    return "[\n" + ",\n".join("  " + json.dumps(i, separators=(",", ":")) for i in items) + "\n ]"


def dump(doc, path):
    text = "{\n" + ",\n".join(
        f' "{key}": ' + (compact(value) if isinstance(value, list) else json.dumps(value, separators=(",", ":")))
        for key, value in doc.items()) + "\n}\n"
    path.write_text(text, encoding="utf-8")
    return len(text)


def fill_entry(level, a, b, material):
    if isinstance(material, tuple):
        return {"from": list(a), "to": list(b), "mix": level.mix_of(material), "seed": level.mix_seed(material)}
    return {"from": list(a), "to": list(b), "block": material}


def write_plan(level):
    lo, hi = level.bounds()
    fills = L.merge_boxes(level.materials_cache)
    plan = {
        "origin": level.layout["origin"],
        "clear": {"from": list(lo), "to": list(hi)},
        "fills": [fill_entry(level, a, b, m) for a, b, m in fills],
        "lights": [list(c) for c in sorted(level.lamps)],
        "alarms": [list(c) for c in sorted(level.alarms)],
        "dim": [list(c) for c in level.dim],
        "texts": level.texts,
        "messages": [],
        "gates": [{"id": gid, "cells": [list(c) for c in cells], "states": states, "built": built}
                  for gid, (cells, states, built) in sorted(level.gates.items())],
    }
    size = dump(plan, OUT / "map.json")
    return len(fills), size


def write_anchors(level):
    ox, oy, oz = level.layout["origin"]

    def pos(x, y, z):
        return [round(ox + x, 2), round(oy + y, 2), round(oz + z, 2)]

    def entry(**kw):
        return {k: v for k, v in kw.items() if v is not None}

    anchors = [entry(id=a["id"], pos=pos(*a["pos"]), yaw=a.get("yaw", 0), provisional=True if a.get("provisional") else None)
               for a in level.layout["anchors"]]
    zones = []
    for z in level.layout["zones"]:
        x0, y0, z0, x1, y1, z1 = level.zone_box(z)
        zones.append(entry(id=z["id"], **{"from": [ox + x0, oy + y0, oz + z0], "to": [ox + x1, oy + y1, oz + z1]}))
    doc = {"dimension": level.layout["dimension"], "anchors": anchors, "zones": zones, "paths": []}
    dump(doc, OUT / "anchors.json")
    return len(anchors), len(zones)


def write_flood(level):
    built = {gid: g[2] for gid, g in level.gates.items()}
    still = set(level.water)
    stages = []
    for stage in level.layout["flood"]["stages"]:
        gates = dict(built, **stage["gates"])
        cells = checks.flooded(level, stage, gates) - still
        boxes = L.merge_boxes({c: "w" for c in cells})
        stages.append({k: stage[k] for k in ("id", "trigger", "delay_seconds", "seconds", "level", "gates")} |
                      {"cells": len(cells), "water": [[*a, *b] for a, b, _ in boxes]})
    doc = {"origin": level.layout["origin"], "stages": stages}
    (OUT / "flood.json").write_text(json.dumps(doc, separators=(",", ":")) + "\n", encoding="utf-8")
    return [s["cells"] for s in stages]


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    level = L.Level()
    errors, report, ways = checks.run(level)
    for line in report:
        print(line)
    if errors:
        print(f"\n{len(errors)} problem(s); nothing written:")
        for e in errors:
            print("  " + e)
        sys.exit(1)
    n_fills, size = write_plan(level)
    n_anchors, n_zones = write_anchors(level)
    flood = write_flood(level)
    preview.draw_all(level, ways, OUT)
    lo, hi = level.bounds()
    dims = [hi[i] - lo[i] + 1 for i in range(3)]
    print(f"{len(level.spaces)} spaces, {len(level.openings)} openings, {len(level.lamps)} lamps; {len(level.air)} cells of air, "
          f"{len(level.shell)} of shell, {len(level.water)} of still water; {n_fills} fills ({size // 1024} KB); "
          f"plan {dims[0]} x {dims[1]} x {dims[2]} (x, y, z); origin {level.layout['origin']}")
    print(f"{n_anchors} anchors, {n_zones} zones; flood stages of {', '.join(map(str, flood))} cells")
    if "--install" in sys.argv:
        DATA.mkdir(parents=True, exist_ok=True)
        for name in ("map.json", "anchors.json", "flood.json"):
            shutil.copy(OUT / name, DATA / name)
        print(f"installed into {DATA.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

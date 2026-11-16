"""
Chapter 2, Level 1's map: writes the plan the mod builds, the named places and a top-down picture, from level.py.

Run (from the repository root):   python3 tools/map/ch2l1/generate.py [--install]

Always writes tools/map/ch2l1/output/ (git-ignored): map.json (what the mod builds), anchors.json (the named places in
world coordinates) and level_preview.png (the plan from above, to check by eye). With --install it also puts map.json and
anchors.json into src/main/resources/data/every_tunnel/ch2/l1/ (committed with the mod): never edit those by hand.

map.json: "origin" (the world position of plan cell 0, 0, 0), "clear" (the box to empty before building, both corners
included, in plan coordinates), "fills" (boxes of one block, both corners included, in plan coordinates: shell, doors,
lamps, the time clock), "lights" (the lamps' cells, for turning them down and up) and "texts" (what each wall_text block says: its
cell, a lang key and a style). anchors.json is read by
core.ch2l1.Ch2L1AnchorsParser; a zone's box starts at the floor's blocks (y = origin y - 1), as a level's zones do.
"""

import json
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import level as L  # noqa: E402
import preview  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
OUT = Path(__file__).resolve().parent / "output"
DATA = ROOT / "src" / "main" / "resources" / "data" / "every_tunnel" / "ch2" / "l1"


def compact(items):
    return "[\n" + ",\n".join("  " + json.dumps(i, separators=(",", ":")) for i in items) + "\n ]"


def write_plan(level):
    lo, hi = level.bounds()
    fills = level.fills()
    plan = {
        "origin": list(L.ORIGIN),
        "clear": {"from": list(lo), "to": list(hi)},
        "fills": [{"from": list(a), "to": list(b), "block": block} for a, b, block in fills],
        "lights": [list(c) for c in level.lights()],
        "texts": [{"at": list(cell), "text": key, "style": style, **({"offset": offset} if offset else {})}
                  for cell, _, key, style, offset in L.WALL_TEXTS],
        "messages": [{"at": list(cell), "text": key, "size": size, "rotation": rotation, "chalk": level.chalk(cell, facing)}
                     for cell, facing, key, size, rotation in L.WALL_MESSAGES],
    }
    text = "{\n" + ",\n".join(
        f' "{key}": ' + (compact(value) if isinstance(value, list) else json.dumps(value, separators=(",", ":")))
        for key, value in plan.items()) + "\n}\n"
    (OUT / "map.json").write_text(text, encoding="utf-8")
    return lo, hi, fills


def write_anchors(level):
    anchors, zones, paths = L.named_places(level)
    ox, oy, oz = L.ORIGIN

    def pos(x, y, z):
        return [round(ox + x, 2), round(oy + y, 2), round(oz + z, 2)]

    def entry(**kw):
        return {k: v for k, v in kw.items() if v is not None}

    doc = {
        "dimension": L.DIMENSION,
        "anchors": [entry(id=i, pos=pos(*p), yaw=yaw, provisional=True if prov else None) for i, p, yaw, prov in anchors],
        "zones": [entry(id=i, **{"from": [ox + x0, oy + y0 - 1, oz + z0], "to": [ox + x1 - 1, oy + y0 + h - 1, oz + z1 - 1]},
                        provisional=True if prov else None)
                  for i, (x0, z0, x1, z1, h, y0), prov in zones],
        "paths": [entry(id=i, closed=True if closed else None, points=[pos(*p) for p in pts], provisional=True if prov else None)
                  for i, pts, closed, prov in paths],
    }
    text = "{\n" + ",\n".join(f' "{key}": ' + (compact(value) if isinstance(value, list) else json.dumps(value)) for key, value in doc.items()) + "\n}\n"
    (OUT / "anchors.json").write_text(text, encoding="utf-8")
    return len(anchors), len(zones), len(paths)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    level = L.Level()
    graph = L.check(level)
    lo, hi, fills = write_plan(level)
    n_anchors, n_zones, n_paths = write_anchors(level)
    preview.draw(level, OUT / "level_preview.png")
    size = [hi[i] - lo[i] + 1 for i in range(3)]
    air = len(level.air)
    print(f"{len(L.SPACES)} spaces, {len(L.DOORS)} doors, {len(level.lights())} lamps; {air} cells of air, {len(level.shell)} of shell "
          f"in {len(fills)} fills; plan {size[0]} x {size[1]} x {size[2]} (x, y, z) from {lo} to {hi}")
    print(f"{n_anchors} anchors, {n_zones} zones, {n_paths} paths; origin {L.ORIGIN}")
    print("doors graph:", {k: sorted({n for n, _ in v}) for k, v in graph.items()})
    if "--install" in sys.argv:
        DATA.mkdir(parents=True, exist_ok=True)
        shutil.copy(OUT / "map.json", DATA / "map.json")
        shutil.copy(OUT / "anchors.json", DATA / "anchors.json")
        print(f"installed into {DATA.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

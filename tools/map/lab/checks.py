"""
Checks on Level 5's plan (see level.py). Each returns its report lines and raises SystemExit when the
plan is wrong, so a bad LEVEL_5 never reaches a world.

- walk(): from the entry corridor, a walk over the plan's cells of the three layers, up or down one
  block at most (the flight joins layers 1 and 2) or down the ladder from the hatch to layer 3 and up the
  spiral stairs to the director's anteroom, must reach every cell but those furniture stands in (furnish.py;
  what a player walks through does not count), the collapse's, the pools' water off their catwalks and
  the sealed test chambers'. Locked doors, the grate and the hatch count as open: each has its way of
  opening.
- clear_ways(): no furniture that blocks stands in a room's cells beside a doorway or a corridor running into it.
- walled(): two spaces of a layer are kept apart by at least one block, unless a corridor or the
  stairs join them on purpose (a slip in the numbers fails here).
- stacked(): where a layer runs under the one over it (2 under 1, 3 under 2), its ceiling stays below
  that layer's floor, with a block of its own.
- joins_level_4(): the steel frame opens from Level 4's dark stretch, at its floor.
- zoned(): everywhere a player can be lies in a zone of Level 5's file, or they would be out of the
  levels.
"""

from collections import deque


LAYERS = (1, 2, 3)


def standing(lv):
    """(layer, x, z) -> the name of the furniture that blocks it: what stands on its floor, not walked through."""
    from lab.furnish import THROUGH
    floors = {}
    for (layer, x, z), floor in lv.floors.items():
        floors.setdefault((x, z), []).append((layer, floor))
    out = {}
    for (x, y, z), (name, _) in lv.furnish.items.items():
        for layer, floor in floors.get((x, z), ()):
            if y == floor + 1 and name not in THROUGH:
                out[(layer, x, z)] = name
    return out


def walk(lv):
    from lab.furnish import HATCH
    blocked = set(standing(lv)) | {(3, x, z) for x, z in lv.collapse_cells}
    water = {(3, x, z) for _, cells, decks, _, _ in lv.pools for x, z in cells - decks}
    sealed = {(s.layer, x, z) for s in lv.spaces if s.kind == "chamber" for x, z in s.cells()}
    ladder = (3, lv.p["LADDER"][1], lv.p["LADDER"][2])
    links = {(2, *HATCH): ladder, **lv.spiral_link()}
    start = (1, *lv.p["START"])
    seen = {start}
    queue = deque([start])
    while queue:
        layer, x, z = queue.popleft()
        here = lv.floors[(layer, x, z)]
        steps = [(other, x + dx, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) for other in LAYERS]
        for c in steps + ([links[(layer, x, z)]] if (layer, x, z) in links else []):
            if c in lv.floors and c not in seen and c not in blocked and c not in water and (
                    abs(lv.floors[c] - here) <= 1 or c == links.get((layer, x, z))):
                seen.add(c)
                queue.append(c)
    missing = set(lv.floors) - seen - blocked - sealed - water
    if missing:
        raise SystemExit(f"Level 5: {len(missing)} cells cannot be reached, e.g. {sorted(missing)[:3]}")
    per_layer = {n: sum(1 for c in seen if c[0] == n) for n in LAYERS}
    return [f"Walk: all {len(seen)} cells reached from the entry corridor ({per_layer[1]} on layer 1 with the stairs, "
            f"{per_layer[2]} on layer 2, {per_layer[3]} on layer 3 down the ladder, up to the office), round {len(blocked)} "
            f"of furniture and the collapse, {len(water)} of the pools' water off the catwalks, the {len(sealed)} of the sealed "
            f"chambers seen through their windows, locked doors, the grate and the hatch counted as open"]


def clear_ways(lv):
    """Nothing that blocks stands in a doorway's or a corridor's mouth: the cells of a room beside an opening or
    beside a corridor that runs into it (furnish.py). A walk may still get round a piece there; a player should
    not have to."""
    from lab.level import OPEN_KINDS
    blocked = standing(lv)
    ways = {(o.layer, x, z) for o in lv.openings for x, z in o.cells()}
    ways |= {(s.layer, x, z) for s in lv.spaces + [lv.flight] if s.kind in OPEN_KINDS for x, z in s.cells()}
    rooms = {(s.layer, x, z) for s in lv.spaces if s.kind not in OPEN_KINDS for x, z in s.cells()}
    mouths = {c for c in rooms if any((c[0], c[1] + dx, c[2] + dz) in ways for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    stuck = sorted((c, blocked[c]) for c in mouths if c in blocked)
    if stuck:
        raise SystemExit(f"Level 5: furniture in a way in: {stuck[:4]}")
    return [f"Ways in: the {len(mouths)} cells of the rooms beside doorways and corridors are clear"]


def walled(lv):
    def touches(a, b):
        ax0, az0, ax1, az1 = a.box
        bx0, bz0, bx1, bz1 = b.box
        return ax0 <= bx1 and bx0 <= ax1 and az0 <= bz1 and bz0 <= az1

    from lab.level import OPEN_KINDS
    rooms = [s for s in lv.spaces if s.kind not in OPEN_KINDS]
    for i, a in enumerate(rooms):
        for b in rooms[i + 1:]:
            if a.layer == b.layer and touches(a, b):
                raise SystemExit(f"Level 5: {a.id} and {b.id} have no wall between them")
    return [f"Walls: the {len(rooms)} rooms are all at least one block apart from the rooms of their layer"]


def stacked(lv):
    out = []
    shaft = {(x, z) for x, _, z in lv.shaft_air}
    for top, low in ((1, 2), (2, 3)):
        upper = {(x, z): f for (layer, x, z), f in lv.floors.items() if layer == top}
        tightest = None
        for (layer, x, z), floor in lv.floors.items():
            if layer != low:
                continue
            ceiling = floor + lv.height_at(low, x, z) + 1
            for dx in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    above = upper.get((x + dx, z + dz))
                    if above is None or abs(above - floor) <= 1 or (x + dx, z + dz) in shaft:
                        continue             # nothing over it, the stairs meeting it, or the ladder's shaft, on purpose
                    if ceiling >= above:
                        raise SystemExit(f"Level 5: layer {low}'s ceiling at {(x, z)} (y {ceiling}) meets layer {top}'s floor (y {above})")
                    gap = above - ceiling - 1
                    tightest = gap if tightest is None else min(tightest, gap)
        if tightest is None:
            out.append(f"Layers: layer {low} runs under no part of layer {top}")
        else:
            out.append(f"Layers: layer {low} runs under layer {top} with at least {tightest} block(s) between its ceiling and that floor")
    return out


def joins_level_4(lv, lounge):
    frame = next(o for o in lv.openings if o.kind == "frame")
    first = [lv.at(x, 1, z) for x, z in frame.cells()]
    sx0, sz0, sx1, sz1 = lounge.exit.box
    stretch = {lounge.at(x, 1, z) for x in range(sx0, sx1) for z in range(sz0, sz1)}
    for wx, wy, wz in first:
        if not any(abs(wx - sx) + abs(wz - sz) == 1 and wy == sy for sx, sy, sz in stretch):
            raise SystemExit(f"Level 5: the frame's cell {(wx, wy, wz)} does not open onto Level 4's dark stretch")
    far = min(lounge.at(x, 0, sz0)[0] for x in range(sx0, sx1))
    return [f"Join: the frame opens from Level 4's dark stretch at world {first[0]}..{first[-1]}, its far end at x {far}"]


def zoned(lv):
    zones = [box for _, box, _ in lv.zones()]

    def inside(pos):
        return any(b[0] <= pos[0] <= b[3] and b[1] <= pos[1] <= b[4] and b[2] <= pos[2] <= b[5] for b in zones)

    cells = [lv.at(x, floor + 1, z) for (_, x, z), floor in lv.floors.items()]
    outside = [c for c in cells if not inside(c)]
    if outside:
        raise SystemExit(f"Level 5: {len(outside)} cells lie in no zone, e.g. {outside[:3]}")
    return [f"Zones: all {len(cells)} cells a player can stand in lie in a zone ({len(lv.zones())} boxes in the file)"]

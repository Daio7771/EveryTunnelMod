"""
Checks on Level 5's plan (see level.py). Each returns its report lines and raises SystemExit when the
plan is wrong, so a bad LEVEL_5 never reaches a world.

- walk(): from the entry corridor, a walk over the plan's cells of both layers, up or down one block
  at most (the flight joins them), must reach every cell but those furniture stands in (furnish.py;
  what a player walks through does not count) and the sealed test chambers'. Locked doors and the grate
  count as open: each has its way of opening.
- walled(): two spaces of a layer are kept apart by at least one block, unless a corridor or the
  stairs join them on purpose (a slip in the numbers fails here).
- stacked(): where layer 2 runs under layer 1, its ceiling stays below layer 1's floor, with a block
  of its own.
- joins_level_4(): the steel frame opens from Level 4's dark stretch, at its floor.
- zoned(): everywhere a player can be lies in a zone of Level 5's file, or they would be out of the
  levels.
"""

from collections import deque


def walk(lv):
    from lab.furnish import THROUGH
    ground = {lv.layer_floor[layer] + 1: layer for layer in (1, 2)}
    blocked = {(ground[y], x, z) for (x, y, z), (name, _) in lv.furnish.items.items() if y in ground and name not in THROUGH}
    sealed = {(s.layer, x, z) for s in lv.spaces if s.kind == "chamber" for x, z in s.cells()}
    start = (1, *lv.p["START"])
    seen = {start}
    queue = deque([start])
    while queue:
        layer, x, z = queue.popleft()
        here = lv.floors[(layer, x, z)]
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for other in (1, 2):
                c = (other, x + dx, z + dz)
                if c in lv.floors and c not in seen and c not in blocked and abs(lv.floors[c] - here) <= 1:
                    seen.add(c)
                    queue.append(c)
    missing = set(lv.floors) - seen - blocked - sealed
    if missing:
        raise SystemExit(f"Level 5: {len(missing)} cells cannot be reached, e.g. {sorted(missing)[:3]}")
    per_layer = {n: sum(1 for c in seen if c[0] == n) for n in (1, 2)}
    return [f"Walk: all {len(seen)} cells reached from the entry corridor ({per_layer[1]} on layer 1 with the stairs, "
            f"{per_layer[2]} on layer 2), round {len(blocked)} of furniture, the {len(sealed)} of the sealed chambers "
            f"seen through their windows, locked doors counted as open"]


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
    upper = {(x, z): f for (layer, x, z), f in lv.floors.items() if layer == 1}
    tightest = None
    for (layer, x, z), floor in lv.floors.items():
        if layer != 2:
            continue
        ceiling = floor + lv.height_at(2, x, z) + 1
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                above = upper.get((x + dx, z + dz))
                if above is None or abs(above - floor) <= 1:
                    continue                 # nothing over it, or the stairs meeting it on purpose
                if ceiling >= above:
                    raise SystemExit(f"Level 5: layer 2's ceiling at {(x, z)} (y {ceiling}) meets layer 1's floor (y {above})")
                gap = above - ceiling - 1
                tightest = gap if tightest is None else min(tightest, gap)
    if tightest is None:
        return ["Layers: layer 2 runs under no part of layer 1"]
    return [f"Layers: layer 2 runs under layer 1 with at least {tightest} block(s) between its ceiling and layer 1's floor"]


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

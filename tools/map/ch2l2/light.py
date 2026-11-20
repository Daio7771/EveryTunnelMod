"""
Chapter 2, Level 2's light (phase 8): what the floor of each space is lit to in vanilla, and the invisible fill that brings it up.

The model is the game's block light with nothing else in it: a lit lamp gives its block's light level (layout.json `luminance`) at
its cell and one less for every step through the air (6 neighbours; a wall stops it). A lamp that flickers counts as off (the
worst moment), as does a dead one. A *walkable cell* is a cell of air a player can stand in, reached from the entry by the Walker
of checks.py (so a deck behind bars or the top of a pillar is not asked to be lit). Each one has a target: `checks.light.route_min`
in the lit spaces, `side_min` in the optional rooms; spaces listed in `checks.unlit` are skipped.

Where the lamps leave a walkable cell under target, `lay_fill` puts `minecraft:light[level=N]` two blocks over it, greedy from the
darkest cell, until none is left. The flood waterlogs the light blocks (they stay lit) and the level dims those of the spaces in
`checks.light.scripted` (the vestibule: the Guard's warning switches its lamps; `lv.dim` lists them, map.json `dim`). Spaces in
`checks.light.no_fill` (none now) are left to their lamps: there `check` fails on any cell under target, with the space, the cell
and its light.
"""

import heapq
from collections import deque

SIX = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]


def block_name(block):
    return block.split("[")[0]


def emitters(lv, with_fill=False):
    """{cell: level} of every lit light source of the plan (flickering and dead lamps give nothing)."""
    lum = lv.layout["luminance"]
    out = {}
    for c in lv.lamps:
        mode = lv.lamp_info[c][0]
        if mode == "on":
            out[c] = lum[block_name(lv.blocks[c])]
    out.update(lv.glow)
    if with_fill:
        for c, block in lv.fill.items():
            out[c] = int(block.split("level=")[1].rstrip("]"))
    return out


def spread(lv, source, level, light):
    """Raise `light` (cell -> level) with a source of `level` at `source`; returns the cells it raised."""
    raised = []
    if light.get(source, -1) >= level:
        return raised
    light[source] = level
    raised.append(source)
    queue = deque([(source, level)])
    while queue:
        (x, y, z), v = queue.popleft()
        if v <= 1:
            continue
        for dx, dy, dz in SIX:
            n = (x + dx, y + dy, z + dz)
            if n in lv.air and light.get(n, -1) < v - 1:
                light[n] = v - 1
                raised.append(n)
                queue.append((n, v - 1))
    return raised


def light_map(lv, sources):
    light = {}
    for c, level in sources.items():
        spread(lv, c, level, light)
    return light


def walkable(lv):
    """{cell: space id} of the cells of air a player can stand in and reach, in a space (not in an opening or on a ladder)."""
    import checks
    gates = {gid: g[2] for gid, g in lv.gates.items()} | lv.layout["checks"].get("route_gates", {})
    walker = checks.Walker(lv, gates)
    start = checks.start_cell(lv, walker, lv.layout["route"][0])
    seen = walker.search(start)
    return {c: lv.air[c] for c in seen if lv.air.get(c) in lv.spaces and c not in lv.climb and c not in lv.water and walker.standable(c)}


def targets(lv):
    """({cell: target}, set of the spaces the fill leaves alone): the walkable cells of the lit spaces and what each needs."""
    cfg = lv.layout["checks"]["light"]
    unlit = set(lv.layout["checks"].get("unlit", ()))
    side = set(cfg["side"])
    lit = {sid for sid in lv.spaces if sid not in unlit and (any(lv.lamp_info[c][1] == sid for c in lv.lamps)
                                                              or any(lv.air.get(c) == sid for c in lv.glow))}
    cells = {}
    for c, sid in walkable(lv).items():
        if sid in lit:
            cells[c] = cfg["side_min"] if sid in side else cfg["route_min"]
    return cells, set(cfg["no_fill"])


def lay_fill(lv):
    cfg = lv.layout["checks"]["light"]
    cells, no_fill = targets(lv)
    light = light_map(lv, emitters(lv))
    level = cfg.get("fill_level", 14)
    bad = {c for c, t in cells.items() if lv.air[c] not in no_fill and light.get(c, 0) < t}
    heap = [(light.get(c, 0), c) for c in bad]
    heapq.heapify(heap)
    stuck = []
    while heap:
        value, c = heapq.heappop(heap)
        if c not in bad:
            continue
        if light.get(c, 0) != value:
            heapq.heappush(heap, (light.get(c, 0), c))
            continue
        spot = next((p for p in ((c[0], c[1] + dy, c[2]) for dy in (2, 3, 1))
                     if lv.air.get(p) == lv.air[c] and p not in lv.blocks and p not in lv.climb and p not in lv.fill and p not in lv.water), None)
        if spot is None:
            bad.discard(c)
            stuck.append(c)
            continue
        lv.fill[spot] = f"minecraft:light[level={level}]"
        for n in spread(lv, spot, level, light):
            if n in bad and light[n] >= cells[n]:
                bad.discard(n)
            elif n in bad:
                heapq.heappush(heap, (light[n], n))
    scripted = set(cfg.get("scripted", ()))
    lv.dim = sorted(c for c in lv.fill if lv.air.get(c) in scripted)
    lv.fill_report = {"cells": cells, "no_fill": no_fill, "stuck": stuck}


def check(lv):
    """(errors, report lines): every walkable cell of a lit space reaches its target (counting the fill, except in the no_fill spaces)."""
    cells = lv.fill_report["cells"]
    no_fill = lv.fill_report["no_fill"]
    with_fill = light_map(lv, emitters(lv, True))
    lamps_only = light_map(lv, emitters(lv))
    errors, darkest = [], {}
    for c, target in sorted(cells.items()):
        sid = lv.air[c]
        value = (lamps_only if sid in no_fill else with_fill).get(c, 0)
        if sid not in darkest or value < darkest[sid][0]:
            darkest[sid] = (value, c, target)
        if value < target:
            errors.append(f"light: {sid} {c} is lit to {value}, under {target}")
    for c in lv.fill_report["stuck"]:
        errors.append(f"light: {lv.air[c]} {c}: nowhere to put a fill block over it")
    shown = errors[:12]
    if len(errors) > 12:
        shown.append(f"light: ... {len(errors) - 12} more cells under target in {len({e.split()[1] for e in errors})} spaces")
    tints = {}
    for c in lv.lamps:
        tint = lv.blocks[c].split("tint=")[1].rstrip("]") if "tint=" in lv.blocks[c] else "cage"
        tints[tint] = tints.get(tint, 0) + 1
    report = ["decoration: " + ", ".join(f"{k.split(':')[-1]} {v}" for k, v in sorted(lv.decor_counts.items())),
              "lamps by tint: " + ", ".join(f"{k} {v}" for k, v in sorted(tints.items()))
              + f"; emergency lamps {len(lv.glow)}, alarm lights {len(lv.alarms)}, sconces {sum('wall_sconce' in b for b in lv.blocks.values() if isinstance(b, str))}",
              f"light: {len(cells)} floor cells measured, {len(lv.fill)} invisible fill blocks (level {lv.layout['checks']['light'].get('fill_level', 14)}); "
              f"darkest cell by space: " + ", ".join(f"{sid} {v}" for sid, (v, _, _) in sorted(darkest.items()))]
    return shown, report

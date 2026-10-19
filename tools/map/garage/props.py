"""
Prop templates and placement rules. Each prop is a small function that writes blocks
into the World and tags its cells for the preview. Nothing is ever placed in the aisle
rows at body height (y 1-2) except thin floor decals, so the line of sight stays clear.
"""


def _tag(w, x, z, label):
    w.tags[(x, z)] = label
    w.reserved.add((x, z))


def _stall_front_back(g, s):
    """(front z = aisle edge row, back z = wall row, step from front to back)."""
    side, x0, x1, z0, z1, wall_z = s
    return (z1, z0, -1) if side == "A" else (z0, z1, 1)


# --- Stall props -------------------------------------------------------------------

def clutter(w, g, p, rng, s, count):
    """Something left in an empty stall: a pallet with boxes, drums or a bin."""
    side, x0, x1, z0, z1, wall_z = s
    front, back, step = _stall_front_back(g, s)
    z = back - step                         # just in front of the wheel stop
    kind = rng.choice(["pallet", "pallet", "drums", "bin"])
    if kind == "pallet":
        for x in range(x0, min(x1, x0 + 1) + 1):
            for dz in (0, -step):
                w.set(x, 1, z + dz, p.STAND_INS["pallet"])
                _tag(w, x, z + dz, "box")
        for x in range(x0, min(x1, x0 + 1) + 1):
            if rng.random() < 0.7:
                w.set(x, 1, z, p.STAND_INS["box"])
                if rng.random() < 0.4:
                    w.set(x, 2, z, p.STAND_INS["box"])
        count("pallet with boxes")
    elif kind == "drums":
        for x in range(x0, min(x1, x0 + rng.randrange(1, 3)) + 1):
            w.set(x, 1, z, p.STAND_INS["drum"])
            _tag(w, x, z, "box")
        count("drums")
    else:
        w.set(x1, 1, z, p.STAND_INS["bin"])
        _tag(w, x1, z, "bin")
        count("bin")


def small_props(w, g, p, rng, count):
    L, W = p.LENGTH, p.WIDTH
    S = p.STAND_INS

    def free_cell(x, z):
        return (x, z) not in w.reserved and all(w.free(x, y, z) for y in (1, 2)) and not g.in_aisle(z)

    # Extinguishers at the four inner corners of the bays and by some pillars.
    spots = [(1, 1), (1, W - 2), (L - 2, 1), (L - 2, W - 2)]
    for i, x in enumerate(g.pillar_xs):
        if i % 2 == 0:
            for z in g.pillar_zs:
                spots.append((x + 1, z))
    for x, z in spots:
        if free_cell(x, z) and rng.random() < 0.8:
            w.set(x, 1, z, S["extinguisher"])
            _tag(w, x, z, "extinguisher")
            count("extinguisher")

    # Loose cones and a shopping cart in free bay cells near pillars.
    cells = [(x + dx, z) for x in g.pillar_xs for z in g.pillar_zs for dx in (-1, 1)]
    rng.shuffle(cells)
    cones = 0
    for x, z in cells:
        if cones >= p.CONES:
            break
        if free_cell(x, z):
            w.set(x, 1, z, S["cone"])
            _tag(w, x, z, "cone")
            cones += 1
    count("cone", cones)
    carts = 0
    bay_cells = [(x, z) for x in range(2, L - 2) for z in range(1, W - 1) if not g.in_aisle(z)]
    rng.shuffle(bay_cells)
    for x, z in bay_cells:
        if carts >= p.CARTS:
            break
        if free_cell(x, z):
            w.set(x, 1, z, S["cart"] + "[facing=" + rng.choice(["north", "east", "south", "west"]) + "]")
            _tag(w, x, z, "cart")
            carts += 1
    count("shopping cart", carts)


# --- Ceiling ------------------------------------------------------------------------

def ceiling_services(w, g, p, rng, beam_xs):
    """Duct along one bay, pipes over both bays, cable trays along the aisle edges, hanging cables."""
    L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
    S = p.STAND_INS
    duct_z = 2
    pipe_zs = [g.aisle_z0 - 2, g.aisle_z1 + 2]
    for x in range(1, L - 1):
        if w.free(x, H - 1, duct_z) and (x, duct_z) not in w.dark:
            w.set(x, H - 1, duct_z, S["duct"])
        for z in pipe_zs:
            if 1 <= z <= W - 2 and w.free(x, H - 1, z) and (x, z) not in w.dark:
                w.set(x, H - 1, z, S["pipe"])
        for z in (g.aisle_z0, g.aisle_z1):
            if x not in beam_xs and w.free(x, H, z):
                w.set(x, H, z, S["tray"])
    # Duct hangers: chains from the ceiling to the duct every few blocks.
    for x in range(2, L - 2, 4):
        if x not in beam_xs and w.free(x, H, duct_z):
            w.set(x, H, duct_z, "minecraft:chain[axis=y]")
    hanging = 0
    for _ in range(200):
        if hanging >= p.HANGING_CABLES:
            break
        x, z = rng.randrange(2, L - 2), rng.randrange(1, W - 1)
        if w.free(x, H, z) and not w.free(x, H + 1, z):
            w.set(x, H, z, S["cable"])
            if not g.in_aisle(z) and rng.random() < 0.4 and w.free(x, H - 1, z):
                w.set(x, H - 1, z, S["cable"])
            hanging += 1


def light_plan(g, p, rng, beam_xs, w):
    """Fixture rows with gaps, dead and dim fixtures, and a few darker zones."""
    L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
    rows = [g.center_z]
    if p.LIGHT_ROWS == "all":
        rows = [1 + (g.bay_a - 1) // 2, g.center_z, g.aisle_z1 + 2 + (g.bay_b - 1) // 2]
    count = (L - 2 - 2 - p.LIGHT_LENGTH) // p.LIGHT_SPACING + 1
    span = (count - 1) * p.LIGHT_SPACING + p.LIGHT_LENGTH
    start = 1 + (L - 2 - span) // 2
    zones = []
    for _ in range(p.DARK_ZONES):
        a = rng.randrange(1, max(2, L - p.DARK_ZONE_LENGTH))
        zones.append((a, a + p.DARK_ZONE_LENGTH))
    plan = []
    for z in rows:
        for i in range(count):
            x0 = start + i * p.LIGHT_SPACING
            for shift in (0, 1, -1, 2, -2):          # step around beams
                xs = range(x0 + shift, x0 + shift + p.LIGHT_LENGTH)
                if not any(x in beam_xs for x in xs) and 1 <= xs[0] and xs[-1] <= L - 2:
                    break
            else:
                continue
            x0 += shift
            x1 = x0 + p.LIGHT_LENGTH - 1
            if any(w.get(x, H + 1, z) != p.STAND_INS["ceiling"] for x in range(x0, x1 + 1)):
                continue                               # collapsed ceiling or something in the way
            state = "lit"
            if any(a <= x0 <= b for a, b in zones):
                state = rng.choice(["dim", "dim", "dead", "dead", "gap"])
            elif any((x, z) in w.dark for x in range(x0, x1 + 1)):
                state = "dead"
            elif rng.random() < p.GAP_SHARE:
                state = "gap"
            elif rng.random() < p.DEAD_LIGHT_SHARE:
                state = "dead"
            plan.append((x0, x1, z, state))
    return plan


# --- Wear ---------------------------------------------------------------------------

def decals(w, g, p, rng, count):
    L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
    D = p.STAND_INS["decal"]
    S = p.STAND_INS
    solid_wall = {S["wall"], S["wall_base"], S["paint_band"]}
    n = 0
    for x in range(1, L - 1):
        for z in range(1, W - 1):
            if w.free(x, 1, z) and w.get(x, 0, z) and rng.random() < p.WEAR * 0.14:
                w.set(x, 1, z, f"{D}[down=true]")
                n += 1
            if w.free(x, H, z) and w.get(x, H + 1, z) == p.STAND_INS["ceiling"] and rng.random() < p.WEAR * 0.08:
                w.set(x, H, z, f"{D}[up=true]")
                n += 1
    for y in range(1, H + 1):
        for x in range(1, L - 1):
            for z, face, wz in ((1, "north", 0), (W - 2, "south", W - 1)):
                if w.free(x, y, z) and w.get(x, y, wz) in solid_wall and rng.random() < p.WEAR * 0.07:
                    w.set(x, y, z, f"{D}[{face}=true]")
                    n += 1
        for z in range(1, W - 1):
            for x, face, wx in ((1, "west", 0), (L - 2, "east", L - 1)):
                if w.free(x, y, z) and w.get(wx, y, z) in solid_wall and rng.random() < p.WEAR * 0.07:
                    w.set(x, y, z, f"{D}[{face}=true]")
                    n += 1
    count("decal (stain, crack, puddle...)", n)


# --- Oddities -------------------------------------------------------------------------

def place_oddity(kind, w, g, p, rng, take_stall, glyphs):
    L, W, H = p.LENGTH, p.WIDTH, p.CEILING_HEIGHT
    S = p.STAND_INS

    if kind == "blocked_lane":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        front, back, step = _stall_front_back(g, s)
        for x in range(x0 - 1, x1 + 2):
            if not g.is_pillar(x, front):
                w.set(x, 1, front, "minecraft:andesite_wall")
                _tag(w, x, front, "barrier")
        for x in range(x0, x1 + 1, 2):
            w.set(x, 1, front + step, S["cone"])
            _tag(w, x, front + step, "cone")
        for x in range(x0, x1 + 1):
            for z in range(min(front, back), max(front, back) + 1):
                w.reserved.add((x, z))
        return {"kind": kind, "x": x0, "z": front, "what": "barriers and cones close off a stall"}

    if kind == "collapse":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        zs = [z for z in range(z0, z1 + 1)][1:4]
        for x in range(x0, x1 + 1):
            for z in zs:
                w.remove(x, H + 1, z)                         # hole in the ceiling...
                w.set(x, H + 2, z, S["ceiling"])               # ...closed one block higher
                if rng.random() < 0.5:
                    w.set(x, H + 1, z, "minecraft:chain[axis=y]")   # rebar hanging out
                    if rng.random() < 0.5:
                        w.set(x, H, z, "minecraft:chain[axis=y]")
                # rubble on the floor
                r = rng.random()
                if r < 0.35:
                    w.set(x, 1, z, S["ceiling"])
                    if rng.random() < 0.3:
                        w.set(x, 2, z, "minecraft:andesite_slab[type=bottom]")
                elif r < 0.6:
                    w.set(x, 1, z, "minecraft:gravel")
                elif r < 0.85:
                    w.set(x, 1, z, rng.choice(["minecraft:cobblestone_slab[type=bottom]", "minecraft:andesite_slab[type=bottom]"]))
                else:
                    w.set(x, 1, z, "minecraft:stone_button[face=floor,facing=north,powered=false]")
                _tag(w, x, z, "collapse")
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                w.reserved.add((x, z))
                w.dark.add((x, z))
        return {"kind": kind, "x": x0, "z": zs[0], "what": "part of the ceiling has come down"}

    if kind == "walled_off":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        front, back, step = _stall_front_back(g, s)
        wall = "minecraft:stone_bricks"
        cells = [(x, front) for x in range(x0 - 1, x1 + 2)]
        cells += [(x0 - 1, z) for z in range(min(front, back), max(front, back) + 1)]
        cells += [(x1 + 1, z) for z in range(min(front, back), max(front, back) + 1)]
        gap = rng.choice(cells[1:-1])
        for x, z in cells:
            if g.is_pillar(x, z):
                continue
            for y in range(1, H + 1):
                if (x, z) == gap and y <= 2:
                    continue                                    # a way in
                if y == 2 and rng.random() < 0.15:
                    continue                                    # missing blocks to peek through
                w.set(x, y, z, wall)
            _tag(w, x, z, "walled_off")
        inside = [(x, z) for x in range(x0, x1 + 1) for z in range(min(front, back), max(front, back) + 1) if z != front]
        for x, z in inside:
            w.reserved.add((x, z))
            w.dark.add((x, z))
            if rng.random() < 0.3:
                w.set(x, 1, z, S["box"])
        for x in range(x0 - 1, x1 + 2):
            w.dark.add((x, front))
        return {"kind": kind, "x": x0, "z": front, "what": "a stall walled off with cinder blocks, one gap"}

    if kind == "odd_door":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        x = (x0 + x1) // 2
        z_wall, z_out, facing = (0, -1, "north") if side == "A" else (W - 1, W, "south")
        w.set(x, 1, z_wall, f"minecraft:dark_oak_door[facing={facing},half=lower,hinge=left,open=false]")
        w.set(x, 2, z_wall, f"minecraft:dark_oak_door[facing={facing},half=upper,hinge=left,open=false]")
        w.set(x, 1, z_out, S["wall"])                            # it opens onto more wall
        w.set(x, 2, z_out, S["wall"])
        w.set(x, 3, z_out, S["wall"])
        w.set(x, 0, z_out, S["floor"])
        for z in range(z0, z1 + 1):
            for xx in range(x0, x1 + 1):
                w.reserved.add((xx, z))
        _tag(w, x, wall_z, "door")
        return {"kind": kind, "x": x, "z": z_wall, "what": "a house door in the concrete wall that opens onto concrete"}

    if kind == "stairs_to_ceiling":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        front, back, step = _stall_front_back(g, s)
        facing = "north" if side == "A" else "south"
        for i in range(H):
            z = front + step * i
            if not (z0 <= z <= z1):
                break
            for x in (x0, x0 + 1):
                for y in range(1, i + 1):
                    w.set(x, y, z, "minecraft:andesite")
                w.set(x, i + 1, z, f"minecraft:andesite_stairs[facing={facing},half=bottom,shape=straight]")
                _tag(w, x, z, "stairs")
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                w.reserved.add((x, z))
        return {"kind": kind, "x": x0, "z": front, "what": "stairs that climb straight into the ceiling"}

    if kind == "extra_pillar":
        s = take_stall()
        if not s:
            return None
        side, x0, x1, z0, z1, wall_z = s
        x, z = (x0 + x1) // 2, (z0 + z1) // 2
        w.fill(x, 0, z, x, H, z, S["pillar"])
        w.set(x, 1, z, S["paint_band"])
        if p.SIGNS:
            w.set(x, 2, z, glyphs.pillar_state("??"))
        for zz in range(z0, z1 + 1):
            for xx in range(x0, x1 + 1):
                w.reserved.add((xx, zz))
        _tag(w, x, z, "extra_pillar")
        return {"kind": kind, "x": x, "z": z, "what": "a pillar outside the grid, its code is '??'"}

    if kind == "wrong_sign":
        if not p.SIGNS:
            return None
        wrong = p.LEVEL_SIGN[:-1] + rng.choice(["0", "3", "7"])
        for _ in range(50):
            x = rng.randrange(4, L - 4 - len(wrong))
            side_z, facing = rng.choice([(0, "south"), (W - 1, "north")])
            if all(w.get(x + i, 3, side_z) == S["wall"] for i in range(len(wrong))):
                break
        else:
            return None
        for i, ch in enumerate(wrong):
            xx = x + i if facing == "south" else x + len(wrong) - 1 - i
            w.set(xx, 3, side_z, glyphs.wall_state(ch, facing))
        _tag(w, x, side_z, "wrong_sign")
        return {"kind": kind, "x": x, "z": side_z, "what": f"a level sign that says {wrong}"}

    return None

"""
Final touches for the mod version of a level: the states of the mod's own blocks.

The builders place the mod blocks by name only (MOD_BLOCKS in map_generator.py), and
stains as "every_tunnel:stain[<face>=true]", the way the vanilla version places sculk
veins. `/setblock` takes a state exactly as given, so everything the game would work out
by itself when a player places the block is set here instead:

- props face a random way; an extinguisher next to a wall stands with its back to it;
  wheel stops lie along x, as the generator lays them
- stains get the way they face and a random picture; a floor stain is sometimes a
  puddle instead (as often as the vanilla version's puddle decal)
- pipes, ducts and cable trays connect like ConnectingPropBlock does: to the same block
  on any side it has an arm, and to a full solid face on the horizontal sides unless the
  run goes up or down there (Level 3 sets its valve risers itself: offices/level.py)
"""

# Pictures per surface; the same counts as StainBlock.patterns and PuddleBlock.PATTERNS.
STAIN_PATTERNS = {"up": 14, "down": 6, "north": 10, "south": 10, "east": 10, "west": 10}
PUDDLE_PATTERNS = 4
PUDDLE_SHARE = 1 / 8           # the vanilla floor decals are 8 kinds, one of them a puddle

FACING_PROPS = {"every_tunnel:cardboard_box", "every_tunnel:traffic_cone", "every_tunnel:fire_extinguisher",
                "every_tunnel:shopping_cart", "every_tunnel:pallet", "every_tunnel:oil_drum",
                "every_tunnel:trash_bin", "every_tunnel:hanging_cable"}
CONNECTING = {
    "every_tunnel:pipe": ("north", "east", "south", "west", "up", "down"),
    "every_tunnel:duct": ("north", "east", "south", "west", "up", "down"),
    "every_tunnel:cable_tray": ("north", "east", "south", "west"),
}
HORIZONTAL = ("north", "east", "south", "west")
OFFSETS = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0),
           "up": (0, 1, 0), "down": (0, -1, 0)}
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "down", "down": "up"}

# Blocks with full solid faces that runs end into (the level's surfaces and solid props).
SOLID_ROLES = ("floor", "floor_patch", "wall", "wall_base", "paint_band", "ceiling", "pillar",
               "line_white", "line_yellow", "glyph", "box", "elevator_wall")
SOLID_EXTRA = {"minecraft:stone_bricks", "minecraft:andesite"}


def block_id(state):
    return state.split("[")[0] if state else None


def properties(state):
    if "[" not in state:
        return {}
    return dict(kv.split("=") for kv in state[state.index("[") + 1:-1].split(","))


def finish(world, p, rng):
    S = p.STAND_INS
    solid = {block_id(S[r]) for r in SOLID_ROLES} | SOLID_EXTRA
    blocks = world.blocks
    for pos in sorted(blocks):                    # sorted: the same level always gets the same choices
        state = blocks[pos]
        bid = block_id(state)
        if bid in FACING_PROPS and "[" not in state:
            facing = rng.choice(HORIZONTAL)
            if bid == "every_tunnel:fire_extinguisher":
                walls = [f for f in HORIZONTAL if block_id(blocks.get(shift(pos, f))) in solid]
                if walls:
                    facing = OPPOSITE[walls[0]]
            blocks[pos] = f"{bid}[facing={facing}]"
        elif bid == "every_tunnel:wheel_stop":
            blocks[pos] = f"{bid}[facing=north]"
        elif bid == "every_tunnel:stain":
            face = next(f for f, v in properties(state).items() if v == "true")
            facing = OPPOSITE[face]               # on its floor (down) face it faces up
            if facing == "up" and rng.random() < PUDDLE_SHARE:
                blocks[pos] = f"every_tunnel:puddle[pattern={rng.randrange(PUDDLE_PATTERNS)}]"
            else:
                blocks[pos] = f"{bid}[facing={facing},pattern={rng.randrange(STAIN_PATTERNS[facing])}]"
    for pos in sorted(blocks):
        bid = block_id(blocks[pos])
        if bid in CONNECTING:
            others = {side: block_id(blocks.get(shift(pos, side))) for side in OFFSETS}
            joined = {side: side in CONNECTING[bid] and others[side] == bid for side in OFFSETS}
            riser = joined["up"] or joined["down"]
            sides = {side: joined[side] or (not riser and side in CONNECTING[bid] and side in HORIZONTAL
                                            and others[side] in solid) for side in OFFSETS}
            blocks[pos] = bid + "[" + ",".join(f"{side}={'true' if on else 'false'}" for side, on in sides.items()) + "]"


def shift(pos, side):
    dx, dy, dz = OFFSETS[side]
    return pos[0] + dx, pos[1] + dy, pos[2] + dz

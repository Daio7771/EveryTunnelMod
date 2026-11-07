"""
Level 5's layer 1, furnished: a cold, clean laboratory used until recently (stainless steel, white
melamine, surgical green plastic), room by room, in plan coordinates (see level.py), and what lies in it:
the wrench and the detector, the fuses, the noticeboard's cards, the fuse box by the stairs' lever, the
Hunters that guard it, and the figure seen from the Lobby.

A piece's facing is the way its front looks (or someone sitting on it looks), in the plan. Wall pieces
stand in the cell against their wall, facing away from it. y is over the layer's floor: 1 on the floor,
2 on top of a piece a block high or on the wall at eye height. Joined states (rows, stacks) are worked
out as in lounge/furnish.py.

Kept clear: the Lobby's way through, from the airlock to the ring (the figure is seen along it), every
doorway, and the middle of the corridors.
"""

from offices.furnish import STACK as OFFICE_STACK

ROW = {"lab_bench", "lab_cabinet", "lab_seats", "tool_board", "whiteboard", "bench"}
STACK = OFFICE_STACK | {"lab_shelf"}
# What a player walks through (ModBlocks: noCollision).
THROUGH = {"lab_chair", "tool_board", "lab_poster", "first_aid_box", "potted_plant", "wall_clock", "whiteboard", "note_board", "fuse_holder"}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def row(name, x0, x1, z, facing, y=1):
    return [(name, x, z, facing, y) for x in range(x0, x1 + 1)]


def column(name, x, z0, z1, facing, y=1):
    return [(name, x, z, facing, y) for z in range(z0, z1 + 1)]


def tall(name, x, z, facing, high=2):
    """A stack, `high` blocks."""
    return [(name, x, z, facing, y) for y in range(1, high + 1)]


FURNITURE = (
    # The Lobby: the reception desk with its chair and cabinets behind, seats along the south wall either
    # side of the way to the ring's west side, a plant in each corner, posters.
    row("lab_cabinet", 13, 17, 9, "south") + [("lab_chair", 15, 8, "south", 1)]
    + [("filing_cabinet", 12, 7, "south", 1), ("filing_cabinet", 18, 7, "south", 1), ("wall_clock", 15, 7, "south", 3)]
    + row("lab_seats", 3, 7, 21, "north") + row("lab_seats", 14, 18, 21, "north")
    + [("low_table", 5, 19, "north", 1), ("low_table", 16, 19, "north", 1)]
    + [("potted_plant", x, z, "south", 1) for x, z in ((2, 7), (20, 7), (2, 21), (20, 21))]
    + [("lab_poster", 2, 9, "east", 2), ("lab_poster", 2, 18, "east", 2)]
    # The Workshop: benches with pegboards over them along the north wall, an island bench in the
    # middle (the wrench and the detector on it), shelves of jars, tool chests, a cart, a whiteboard.
    + row("lab_bench", 30, 37, 2, "south") + row("tool_board", 30, 37, 2, "south", 2)
    + row("lab_bench", 46, 52, 2, "south") + row("tool_board", 46, 52, 2, "south", 2)
    + row("lab_bench", 38, 45, 7, "north")
    + [("lab_chair", x, 8, "north", 1) for x in (39, 42, 44)] + [("lab_chair", x, 6, "south", 1) for x in (40, 43)]
    + [p for z in range(4, 9) for p in tall("lab_shelf", 29, z, "east")]
    + [("tool_chest", 53, z, "west", 1) for z in (4, 5, 6)] + [("lab_cart", 52, 10, "west", 1)]
    + row("whiteboard", 31, 36, 12, "north", 2) + row("lab_cabinet", 46, 51, 12, "north")
    + [("first_aid_box", 38, 12, "north", 2)]
    # The Waiting room: the noticeboard on the north wall (NOTICEBOARD), two pairs of seat rows facing
    # each other round a low table, a water dispenser, a drinks machine, a cabinet, plants, a clock.
    + row("lab_seats", 59, 63, 4, "south") + row("lab_seats", 59, 63, 8, "north") + [("low_table", 61, 6, "north", 1)]
    + row("lab_seats", 69, 73, 4, "south") + row("lab_seats", 69, 73, 8, "north") + [("low_table", 71, 6, "north", 1)]
    + [("water_dispenser", 74, 11, "west", 1)] + tall("vending_machine", 58, 11, "east")
    + column("lab_cabinet", 74, 6, 7, "west")
    + [("potted_plant", 58, 2, "south", 1), ("potted_plant", 74, 2, "south", 1), ("wall_clock", 62, 12, "north", 3)]
    # The Lockers: lockers to hide in along both long walls, benches in between, a cabinet.
    + [p for x in list(range(24, 31)) + list(range(37, 44)) for p in tall("locker", x, 21, "south") + tall("locker", x, 28, "north")]
    + row("bench", 25, 29, 24, "north") + row("bench", 38, 42, 24, "north")
    + column("lab_cabinet", 24, 24, 25, "east")
    # The ring: posters and first aid on the walls, carts against them; the fuse box (FUSE_BOX) by the
    # stairs' lever in the east side.
    + [("lab_poster", 26, 14, "south", 2), ("lab_poster", 57, 14, "south", 2), ("first_aid_box", 48, 14, "south", 2)]
    + [("lab_cart", 30, 36, "north", 1), ("lab_cart", 55, 36, "north", 1), ("lab_poster", 42, 36, "north", 2)]
    + [("first_aid_box", 9, 27, "east", 2), ("lab_poster", 71, 21, "east", 2)]
)

# The Waiting room's noticeboard, cork boards side by side on the north wall, at eye height: (card note id, x, z).
NOTICEBOARD = [("ch1_l5_card_hunters", 64, 2), ("ch1_l5_card_cloud", 65, 2), ("ch1_l5_card_junior", 66, 2),
               ("ch1_l5_card_equipment", 67, 2)]

# The fuse box: a holder a slot on the east wall of the ring's east side, beside the stairs' lever (74, 25),
# at eye height, in amperage order (index into FUSE_AMPS); the 15A is in already.
FUSE_AMPS = (5, 10, 15, 20, 30)
FUSE_BOX = [(74, 27 + i, i) for i in range(len(FUSE_AMPS))]
FUSE_IN = 2

# (tag, x, z, y): what lies about. The missing fuses, one in each room, in plain sight on furniture.
LOOT = [
    ("wrench", 40, 7, 2), ("detector", 43, 7, 2),               # the Workshop's island
    ("fuse_10a", 14, 9, 2),                                     # the Lobby's desk
    ("fuse_30a", 53, 4, 2),                                     # a tool chest in the Workshop
    ("fuse_5a", 74, 6, 2),                                      # the Waiting room's cabinet
    ("fuse_20a", 24, 24, 2),                                    # the Lockers' cabinet
]

# Spawn points of the level's enemies and of the figure: (id, x, z, plan facing, tags).
POINTS = [
    # Three Hunters by the fuse box; these do no harm (DistortionHooks).
    ("hunters_fuse_box", 72, 29, "north", ["hunters", "harmless"]),
    # The figure, at the far end of the corridor leaving the Lobby, looking back at it; where it goes, round
    # the corner (LabFigureHooks).
    ("lab_figure", 73, 16, "west", ["lab_figure"]),
    ("lab_figure_exit", 73, 24, "south", ["lab_figure_exit"]),
]


class Furnish:
    """Layer 1's furniture in `lab` (a Lab, composed up to its levers), whose layer 1 air cells are `air`."""

    def __init__(self, lab, air, taken):
        self.lab, self.air = lab, air
        self.items = {}            # plan (x, y, z) -> (name, plan facing)
        self.taken = set(taken)    # plan cells already holding something (the levers)
        self.extra = {}            # plan (x, y, z) -> a state of its own (the fuse holders)

    def put(self, name, x, z, facing, y):
        c = (x, y, z)
        if c not in self.air or c in self.items or c in self.taken:
            raise SystemExit(f"Level 5: {name} at {c}: the cell is not free")
        self.items[c] = (name, facing)

    def lay(self):
        for name, x, z, facing, y in FURNITURE:
            self.put(name, x, z, facing, y)
        for _, x, z in NOTICEBOARD:
            self.put("note_board", x, z, "south", 2)
        for x, z, i in FUSE_BOX:
            self.put("fuse_holder", x, z, "west", 2)
            self.extra[(x, 2, z)] = i
        for tag, x, z, y in LOOT:
            if y == 2 and (x, 1, z) not in self.items:
                raise SystemExit(f"Level 5: {tag} at {(x, z)} has nothing under it")
            if (x, y, z) in self.items or (x, y, z) not in self.air:
                raise SystemExit(f"Level 5: {tag} at {(x, z)}: the cell is taken")
        return self

    def states(self):
        """{world position: state} of the furniture."""
        world_facing = self.lab.WORLD_FACING
        items = {}
        for (x, y, z), (name, facing) in self.items.items():
            items[self.lab.at(x, y, z)] = (name, world_facing[facing], (x, y, z))

        def same(pos, name, facing):
            other = items.get(pos)
            return other is not None and other[0] == name and other[1] == facing

        out = {}
        for (wx, wy, wz), (name, facing, plan) in items.items():
            bid = f"every_tunnel:{name}"
            if name in ROW:
                lx, lz = DIRS[{"north": "east", "east": "south", "south": "west", "west": "north"}[facing]]
                left, right = same((wx + lx, wy, wz + lz), name, facing), same((wx - lx, wy, wz - lz), name, facing)
                state = f"{bid}[facing={facing},left={str(left).lower()},right={str(right).lower()}]"
            elif name in STACK:
                up, down = same((wx, wy + 1, wz), name, facing), same((wx, wy - 1, wz), name, facing)
                state = f"{bid}[down={str(down).lower()},facing={facing},up={str(up).lower()}]"
            elif name == "fuse_holder":
                i = self.extra[plan]
                state = f"{bid}[amps={i},facing={facing},filled={str(i == FUSE_IN).lower()},lit=false]"
            else:
                state = f"{bid}[facing={facing}]"
            out[(wx, wy, wz)] = state
        return out

    def spawn_points(self, yaw):
        """The level file's spawn points of what lies about, the cards, the enemies and the figure; `yaw`
        turns a plan facing into a spawn point's yaw."""
        out = []
        for tag, x, z, y in LOOT:
            out.append({"id": tag, "pos": self.centre(x, y, z), "yaw": 0, "tags": [tag]})
        for nid, x, z in NOTICEBOARD:
            out.append({"id": nid, "pos": self.centre(x, 2, z), "yaw": 0, "tags": ["note"]})
        for pid, x, z, facing, tags in POINTS:
            out.append({"id": pid, "pos": self.centre(x, 1, z), "yaw": yaw(facing), "tags": tags})
        return out

    def centre(self, x, y, z):
        wx, wy, wz = self.lab.at(x, y, z)
        return [wx + 0.5, wy, wz + 0.5]

    def plan_cells(self):
        """For the preview: (x, z) -> what stands there at floor height."""
        return {(x, z): it[0] for (x, y, z), it in self.items.items() if y == 1 or it[0] in ("note_board", "fuse_holder")}

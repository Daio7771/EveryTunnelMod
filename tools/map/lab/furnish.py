"""
Level 5, furnished: a cold, clean laboratory used until recently (stainless steel, white melamine, surgical
green plastic), room by room, in plan coordinates (see level.py), and what lies in it or happens there.

- Layer 1, the tutorial: the wrench and the detector, the fuses, the noticeboard's cards, the fuse box by
  the stairs' lever, the harmless Hunters that guard it, the figure seen from the Lobby.
- Layer 2: the subjects (one in each sealed test chamber, an experiment card by each window), the Lab
  hall, the Office (its computer tells the chemical puzzle's answer), Storage and its Hunters, the
  chemical room (the reagents, the mixer, its pipe to the grate, the alarm's lights; where the alarm's
  enemies appear), the drain's hatch, and the first Distortion Junior, in the north corridor.

A piece's facing is the way its front looks (or someone sitting on it looks), in the plan. Wall pieces
stand in the cell against their wall, facing away from it. y is over the layer's floor: 1 on the floor,
2 on top of a piece a block high or on the wall at eye height. Joined states (rows, stacks) are worked
out as in lounge/furnish.py.

Kept clear: the ways through every room and doorway, the middle of the corridors, the Lobby's view down
the corridor to the ring (the figure), the middle of the Lab hall (the first Cloud appears there).
"""

from offices.furnish import STACK as OFFICE_STACK

ROW = {"lab_bench", "lab_cabinet", "lab_seats", "tool_board", "whiteboard", "bench", "office_desk"}
STACK = OFFICE_STACK | {"lab_shelf", "fume_hood"}
# What a player walks through (ModBlocks: noCollision).
THROUGH = {"lab_chair", "tool_board", "lab_poster", "first_aid_box", "potted_plant", "wall_clock", "whiteboard", "note_board",
           "fuse_holder", "office_chair", "lab_terminal", "alarm_light", "pipe"}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


def row(name, x0, x1, z, facing, y=1):
    return [(name, x, z, facing, y) for x in range(x0, x1 + 1)]


def column(name, x, z0, z1, facing, y=1):
    return [(name, x, z, facing, y) for z in range(z0, z1 + 1)]


def tall(name, x, z, facing, high=2):
    """A stack, `high` blocks."""
    return [(name, x, z, facing, y) for y in range(1, high + 1)]


# (name, x, z, facing, y) by layer.
FURNITURE = {
    1: (
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
        # The Waiting room: the noticeboard on the north wall (NOTE_BOARDS), two pairs of seat rows facing
        # each other round a low table, a water dispenser, a drinks machine, a cabinet, plants, a clock.
        + row("lab_seats", 59, 63, 4, "south") + row("lab_seats", 59, 63, 8, "north") + [("low_table", 61, 6, "north", 1)]
        + row("lab_seats", 69, 73, 4, "south") + row("lab_seats", 69, 73, 8, "north") + [("low_table", 71, 6, "north", 1)]
        + [("water_dispenser", 74, 11, "west", 1)] + tall("vending_machine", 58, 11, "east")
        + column("lab_cabinet", 74, 6, 7, "west")
        + [("potted_plant", 58, 2, "south", 1), ("potted_plant", 74, 2, "south", 1), ("wall_clock", 62, 12, "north", 3)]
        # The Lockers: lockers to hide in along both long walls, benches in between, a cabinet.
        + [p for x in list(range(24, 31)) + list(range(37, 44)) for p in tall("locker", x, 21, "south") + tall("locker", x, 28, "north")]
        + row("bench", 25, 29, 24, "north") + row("bench", 38, 42, 24, "north")
        + column("lab_cabinet", 24, 24, 25, "east") + column("lab_cabinet", 43, 24, 25, "west")
        # The ring: posters and first aid on the walls, carts against them; the fuse box (FUSE_BOX) by the
        # stairs' lever in the east side.
        + [("lab_poster", 26, 14, "south", 2), ("lab_poster", 57, 14, "south", 2), ("first_aid_box", 48, 14, "south", 2)]
        + [("lab_cart", 30, 36, "north", 1), ("lab_cart", 55, 36, "north", 1), ("lab_poster", 42, 36, "north", 2)]
        + [("first_aid_box", 9, 27, "east", 2), ("lab_poster", 71, 21, "east", 2)]
    ),
    2: (
        # The Lab hall: benches along the north and south walls between the ways out, a computer on each
        # row, carts, posters, first aid; its middle kept clear.
        row("lab_bench", 80, 90, 50, "south") + row("lab_bench", 81, 90, 70, "north")
        + [("lab_cart", 96, 52, "west", 1), ("lab_cart", 96, 68, "west", 1)]
        + [("lab_poster", 73, 53, "east", 2), ("lab_poster", 73, 67, "east", 2), ("first_aid_box", 97, 54, "west", 2)]
        # The Office: desks along the north wall with their chairs and computers, the desk in the middle
        # with the computer that tells the chemical puzzle's answer (TERMINALS), cabinets, a whiteboard, plants.
        + row("office_desk", 76, 80, 13, "south") + [("office_chair", x, 14, "north", 1) for x in (77, 79)]
        + row("office_desk", 88, 92, 13, "south") + [("office_chair", x, 14, "north", 1) for x in (89, 91)]
        + row("office_desk", 84, 86, 18, "south") + [("office_chair", 85, 19, "north", 1)]
        + column("filing_cabinet", 74, 15, 18, "east") + column("whiteboard", 96, 16, 20, "west", 2)
        + [("potted_plant", 74, 24, "north", 1), ("potted_plant", 96, 24, "north", 1), ("wall_clock", 85, 13, "south", 3)]
        # Storage: shelves two high along the north and south walls, carts; its middle clear (its Hunters).
        + [p for x in range(100, 114) for p in tall("lab_shelf", x, 80, "south") + tall("lab_shelf", x, 89, "north")]
        + [("lab_cart", 101, 84, "east", 1), ("lab_cart", 112, 86, "west", 1)] + column("lab_cabinet", 114, 84, 88, "west")
        # The chemical room: fume hoods along the north wall, two rows of benches, the mixer against the south
        # wall beside the grate (MIXER), the pipe from it over the grate (PIPE), the reagents' cabinet on the
        # grate's other side (the south route comes in at x 116 to 119: kept clear).
        + [p for x in range(112, 121) for p in tall("fume_hood", x, 50, "south")]
        + row("lab_bench", 114, 122, 57, "north") + row("lab_bench", 114, 122, 62, "south")
        + [("lab_chair", x, 56, "south", 1) for x in (115, 118, 121)] + [("lab_chair", x, 63, "north", 1) for x in (116, 120)]
        + row("lab_cabinet", 131, 134, 69, "north")
        + [p for z in range(52, 58) for p in tall("lab_shelf", 137, z, "west")]
        + [("first_aid_box", 108, 65, "east", 2), ("lab_poster", 137, 62, "west", 2)]
    ),
}

# Cork boards with a card each: (card note id, layer, x, z, facing), at eye height. The Waiting room's
# noticeboard; the experiment card by each test chamber's window, on the corridor's side.
NOTE_BOARDS = [("ch1_l5_card_hunters", 1, 64, 2, "south"), ("ch1_l5_card_cloud", 1, 65, 2, "south"),
               ("ch1_l5_card_junior", 1, 66, 2, "south"), ("ch1_l5_card_equipment", 1, 67, 2, "south"),
               ("ch1_l5_card_s1", 2, 21, 57, "south"), ("ch1_l5_card_s3", 2, 43, 57, "south"),
               ("ch1_l5_card_s2", 2, 29, 60, "north"), ("ch1_l5_card_s4", 2, 51, 60, "north")]

# The fuse box: a holder a slot on the east wall of the ring's east side, beside the stairs' lever (74, 25),
# at eye height, in amperage order (index into FUSE_AMPS); the 15A is in already.
FUSE_AMPS = (5, 10, 15, 20, 30)
FUSE_BOX = [(74, 27 + i, i) for i in range(len(FUSE_AMPS))]
FUSE_IN = 2

# Computers on desks and benches: (id, layer, x, z, facing). `terminal_office` tells the chemical puzzle's
# answer (ChemicalHooks); the others show what they have to show (TerminalHooks).
TERMINALS = [("terminal_office", 2, 85, 18, "south"), ("terminal_office_west", 2, 78, 13, "south"),
             ("terminal_office_east", 2, 90, 13, "south"), ("terminal_lab_hall_north", 2, 83, 50, "south"),
             ("terminal_lab_hall_south", 2, 87, 70, "north"), ("terminal_storage", 2, 114, 84, "west")]

# The chemical room's mixer against the south wall, looking into the room: a tank either side of its body.
MIXER = [("mixer_tank", 123, 69), ("mixer", 124, 69), ("mixer_tank", 125, 69)]
# The acid's pipe, along the south wall under the ceiling, from over the mixer to past the grate (x 127 to 130).
PIPE = [(x, 4, 69) for x in range(124, 131)]
# The alarm's red lights, high on the chemical room's walls: (x, z, facing).
ALARMS = [(110, 50, "south"), (135, 50, "south"), (110, 69, "north"), (135, 69, "north")]
# The hatch down to the drain, closed, flush with the floor in the middle of the room behind the grate; under
# it, for now (the drain is layer 3's), a pit a block deep in steel, dark at its bottom.
HATCH = (128, 75)
PIT_BOTTOM = "minecraft:black_concrete"

# (id, tag, layer, x, z, y): what lies about, in plain sight on furniture. Layer 1: the wrench and the
# detector; the missing fuses, one in each room; a snack and water in the Lockers, a battery in the Waiting
# room. Layer 2: a battery in the Office, the four reagents on their cabinet by the mixer, and Storage's
# supplies, which its Hunters guard. Nothing in the subjects' corridor, the Lab hall or the chemical room
# but the reagents.
LOOT = [
    ("wrench", "wrench", 1, 40, 7, 2), ("detector", "detector", 1, 43, 7, 2),     # the Workshop's island
    ("fuse_10a", "fuse_10a", 1, 14, 9, 2),                                        # the Lobby's desk
    ("fuse_30a", "fuse_30a", 1, 53, 4, 2),                                        # a tool chest in the Workshop
    ("fuse_5a", "fuse_5a", 1, 74, 6, 2),                                          # the Waiting room's cabinet
    ("fuse_20a", "fuse_20a", 1, 24, 24, 2),                                       # the Lockers' cabinet
    ("snack_lockers", "food_snack", 1, 43, 24, 2), ("water_lockers", "water", 1, 43, 25, 2),
    ("battery_waiting", "battery", 1, 74, 7, 2),
    ("battery_office", "battery", 2, 76, 13, 2),
] + [(f"reagent_{i + 1}", f"reagent_{i + 1}", 2, 131 + i, 69, 2) for i in range(4)] + [
    ("snack_storage_1", "food_snack", 2, 101, 84, 2), ("water_storage_1", "water", 2, 112, 86, 2),
    ("snack_storage_2", "food_snack", 2, 114, 85, 2), ("water_storage_2", "water", 2, 114, 86, 2),
    ("battery_storage_1", "battery", 2, 114, 87, 2), ("battery_storage_2", "battery", 2, 114, 88, 2),
]

# Spawn points of the level's enemies, its subjects, the figure, and the alarm's enemies: (id, layer, x, z,
# plan facing, tags).
POINTS = [
    # Three Hunters by the fuse box; these do no harm (DistortionHooks).
    ("hunters_fuse_box", 1, 72, 29, "north", ["hunters", "harmless"]),
    # The figure, at the far end of the corridor leaving the Lobby, looking back at it; where it goes, round
    # the corner (LabFigureHooks).
    ("lab_figure", 1, 73, 16, "west", ["lab_figure"]),
    ("lab_figure_exit", 1, 73, 24, "south", ["lab_figure_exit"]),
    # The subjects, in the middle of their chambers, facing their windows (SubjectHooks).
    ("subject_1", 2, 27, 50, "south", ["subject"]), ("subject_2", 2, 35, 67, "north", ["subject"]),
    ("subject_3", 2, 49, 50, "south", ["subject"]), ("subject_4", 2, 57, 67, "north", ["subject"]),
    # The first Distortion Junior, in plain sight down the north corridor from the Lab hall.
    ("junior_north", 2, 77, 38, "south", ["distortion_junior"]),
    # Storage's Hunters, guarding what is kept there.
    ("hunters_storage", 2, 107, 85, "west", ["hunters"]),
    # Where the chemical room's alarm brings its three Hunters and its Junior (ChemicalHooks).
    ("alarm_hunters", 2, 124, 60, "north", ["alarm_hunters"]),
    ("alarm_junior", 2, 120, 65, "north", ["alarm_junior"]),
]


class Furnish:
    """The level's furniture in `lab` (a Lab, composed up to its levers), whose air cells are `air`."""

    def __init__(self, lab, air, taken):
        self.lab, self.air = lab, air
        self.items = {}            # plan (x, y, z) -> (name, plan facing)
        self.taken = set(taken)    # plan cells already holding something (the levers, the windows)
        self.extra = {}            # plan (x, y, z) -> a state of its own

    def floor(self, layer):
        return self.lab.layer_floor[layer]

    def put(self, name, x, z, facing, y):
        c = (x, y, z)
        if c not in self.air or c in self.items or c in self.taken:
            raise SystemExit(f"Level 5: {name} at {c}: the cell is not free")
        self.items[c] = (name, facing)

    def lay(self):
        for layer, pieces in FURNITURE.items():
            for name, x, z, facing, y in pieces:
                self.put(name, x, z, facing, self.floor(layer) + y)
        for _, layer, x, z, facing in NOTE_BOARDS:
            self.put("note_board", x, z, facing, self.floor(layer) + 2)
        for x, z, i in FUSE_BOX:
            self.put("fuse_holder", x, z, "west", 2)
            self.extra[(x, 2, z)] = f"amps={i},facing={{facing}},filled={str(i == FUSE_IN).lower()},lit=false"
        f2 = self.floor(2)
        for _, layer, x, z, facing in TERMINALS:
            self.put("lab_terminal", x, z, facing, self.floor(layer) + 2)
        for name, x, z in MIXER:
            self.put(name, x, z, "north", f2 + 1)
        for x, y, z in PIPE:
            self.put("pipe", x, z, "north", f2 + y)
            # Along the plan's x: the world's z, so it joins north and south.
            self.extra[(x, f2 + y, z)] = "down=false,east=false,north=true,south=true,up=false,west=false"
        for x, z, facing in ALARMS:
            self.put("alarm_light", x, z, facing, f2 + 4)
        for _, tag, layer, x, z, y in LOOT:
            yy = self.floor(layer) + y
            if y == 2 and (x, yy - 1, z) not in self.items:
                raise SystemExit(f"Level 5: {tag} at {(x, z)} has nothing under it")
            if (x, yy, z) in self.items or (x, yy, z) not in self.air:
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
            if plan in self.extra:
                state = f"{bid}[{self.extra[plan].format(facing=facing)}]"
            elif name in ROW:
                lx, lz = DIRS[{"north": "east", "east": "south", "south": "west", "west": "north"}[facing]]
                left, right = same((wx + lx, wy, wz + lz), name, facing), same((wx - lx, wy, wz - lz), name, facing)
                state = f"{bid}[facing={facing},left={str(left).lower()},right={str(right).lower()}]"
            elif name in STACK:
                up, down = same((wx, wy + 1, wz), name, facing), same((wx, wy - 1, wz), name, facing)
                state = f"{bid}[down={str(down).lower()},facing={facing},up={str(up).lower()}]"
            else:
                state = f"{bid}[facing={facing}]"
            out[(wx, wy, wz)] = state
        return out

    def pit(self):
        """{world position: state} of the drain's hatch, set into the floor, and the pit under it."""
        x, z = HATCH
        f2 = self.floor(2)
        facing = self.lab.WORLD_FACING["north"]
        out = {self.lab.at(x, f2, z): f"every_tunnel:bunker_trapdoor[facing={facing},half=top,open=false,powered=false,waterlogged=false]",
               self.lab.at(x, f2 - 1, z): "minecraft:air", self.lab.at(x, f2 - 2, z): PIT_BOTTOM}
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            out[self.lab.at(x + dx, f2 - 1, z + dz)] = self.lab.state("steel")
        return out

    def spawn_points(self, yaw):
        """The level file's spawn points of what lies about, the cards, the computers, the enemies, the
        subjects and the figure; `yaw` turns a plan facing into a spawn point's yaw."""
        out = []
        for lid, tag, layer, x, z, y in LOOT:
            out.append({"id": lid, "pos": self.centre(x, self.floor(layer) + y, z), "yaw": 0, "tags": [tag]})
        for nid, layer, x, z, _ in NOTE_BOARDS:
            out.append({"id": nid, "pos": self.centre(x, self.floor(layer) + 2, z), "yaw": 0, "tags": ["note"]})
        for tid, layer, x, z, _ in TERMINALS:
            out.append({"id": tid, "pos": self.centre(x, self.floor(layer) + 2, z), "yaw": 0, "tags": ["terminal"]})
        for pid, layer, x, z, facing, tags in POINTS:
            out.append({"id": pid, "pos": self.centre(x, self.floor(layer) + 1, z), "yaw": yaw(facing), "tags": tags})
        return out

    def centre(self, x, y, z):
        wx, wy, wz = self.lab.at(x, y, z)
        return [wx + 0.5, wy, wz + 0.5]

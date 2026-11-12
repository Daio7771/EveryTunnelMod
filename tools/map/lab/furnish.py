"""
Level 5, furnished: a cold, clean laboratory used until recently (stainless steel, white melamine, surgical
green plastic), room by room, in plan coordinates (see level.py), and what lies in it or happens there.

- Layer 1, the tutorial: the wrench and the detector, the fuses, the safety briefing's cards, the fuse box by
  the stairs' lever, the harmless Hunters that guard it, the figure seen from the Lobby.
- Layer 2: the subjects (one in each sealed test chamber, with a drip stand and a tray of instruments; an
  experiment card by each window), the Lab hall, the Office (its computer tells the chemical puzzle's
  answer), Storage and its Hunters, the
  chemical room (the reagents, the mixer, its pipe to the grate, the alarm's lights; where the alarm's
  enemies appear), the drain's hatch, and the first Distortion Junior, in the north corridor.
- Layer 3, the drain (dirty, industrial): the Junction's sluice gates and its Junior (and its restart
  point), the Cistern's Hunters on its catwalks, the Pump room's pumps and supplies, the Hideout where the
  guard before you stayed (his body, his dead flashlight, his note), and up the stairs the director's
  office (modern, black leather and steel): his desk with the mini block of diorite, a few papers, coffee.

A piece's facing is the way its front looks (or someone sitting on it looks), in the plan. Wall pieces
stand in the cell against their wall, facing away from it. y is over the layer's floor: 1 on the floor,
2 on top of a piece a block high or on the wall at eye height. Joined states (rows, stacks) are worked
out as in lounge/furnish.py.

Kept clear: the ways through every room and doorway, the middle of the corridors, the Lobby's view down
the corridor to the ring (the figure), the middle of the Lab hall (the first Cloud appears there).
"""

from offices.furnish import STACK as OFFICE_STACK

ROW = {"lab_bench", "lab_cabinet", "lab_seats", "tool_board", "whiteboard", "bench", "office_desk",
       "sluice_gate", "mattress", "exec_desk", "credenza", "leather_sofa"}
STACK = OFFICE_STACK | {"lab_shelf", "fume_hood", "pump"}
# What a player walks through (ModBlocks: noCollision).
THROUGH = {"lab_chair", "tool_board", "lab_poster", "first_aid_box", "potted_plant", "wall_clock", "whiteboard", "note_board",
           "briefing_board", "briefing_sign", "wall_text", "lab_reader", "sign_extinguisher", "sign_exit", "sign_biohazard",
           "sign_radiation", "floor_line", "microscope", "glove_box", "lab_beakers", "lab_monitor", "lab_coat", "chair_jacket",
           "fuse_holder", "office_chair", "lab_terminal", "alarm_light", "pipe", "note_paper", "valve_wheel", "pressure_gauge",
           "tin_cans", "dead_flashlight", "mattress", "electrical_panel", "leather_chair", "exec_chair", "mini_diorite",
           "paper_stack", "coffee_cup", "desk_lamp_modern", "coffee_machine", "iv_drip", "chalk_arrow", "desk_phone",
           "lounge_painting", "wall_clock", "security_camera", "office_rug", "trophy_cup", "crystal_award", "month_plaque"}
# The director's office and the corridor out of it stand this much over layer 3's floor (LEVEL_5's `rise`).
RISE = 10
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
        + [("lab_monitor", 16, 9, "north", 2), ("coffee_cup", 13, 9, "north", 2)]           # on the desk, for whoever sat there
        + row("lab_seats", 3, 7, 21, "north") + row("lab_seats", 14, 18, 21, "north")
        + [("low_table", 5, 19, "north", 1), ("low_table", 16, 19, "north", 1)]
        + [("potted_plant", x, z, "south", 1) for x, z in ((2, 7), (20, 7), (2, 21), (20, 21))]
        + [("lab_poster", 2, 9, "east", 2), ("lab_poster", 2, 18, "east", 2)]
        # The Workshop: benches with pegboards over them along the north wall, an island bench in the
        # middle (the wrench and the detector on it), shelves of jars, tool chests, a cart, a whiteboard.
        + row("lab_bench", 30, 37, 2, "south") + row("tool_board", 30, 37, 2, "south", 2)
        + row("lab_bench", 46, 52, 2, "south") + row("tool_board", 46, 52, 2, "south", 2)
        + row("lab_bench", 38, 45, 7, "north")
        + [("lab_chair", x, 8, "north", 1) for x in (39, 44)] + [("lab_chair", x, 6, "south", 1) for x in (40, 43)]
        + [("chair_jacket", 42, 8, "north", 1), ("lab_coat", 53, 8, "west", 2)]                # someone's jacket, a coat on a hook
        + [("microscope", 38, 7, "south", 2), ("lab_beakers", 41, 7, "south", 2), ("glove_box", 44, 7, "south", 2),
           ("coffee_cup", 45, 7, "south", 2)]
        + [p for z in range(4, 9) for p in tall("lab_shelf", 29, z, "east")]
        + [("tool_chest", 53, z, "west", 1) for z in (4, 5, 6)] + [("lab_cart", 52, 10, "west", 1)]
        + row("whiteboard", 31, 36, 12, "north", 2) + row("lab_cabinet", 46, 51, 12, "north")
        + [("first_aid_box", 38, 12, "north", 2)]
        # The Waiting room: the safety briefing on the north wall (BRIEFING), two pairs of seat rows facing
        # each other round a low table, a water dispenser, a drinks machine, a cabinet, plants, a clock.
        + row("lab_seats", 59, 63, 4, "south") + row("lab_seats", 59, 63, 8, "north") + [("low_table", 61, 6, "north", 1)]
        + row("lab_seats", 69, 73, 4, "south") + row("lab_seats", 69, 73, 8, "north") + [("low_table", 71, 6, "north", 1)]
        + [("water_dispenser", 74, 11, "west", 1)] + tall("vending_machine", 58, 11, "east")
        + column("lab_cabinet", 74, 6, 7, "west")
        + [("potted_plant", 58, 2, "south", 1), ("potted_plant", 74, 2, "south", 1), ("wall_clock", 62, 12, "north", 3)]
        # The Lockers: lockers to hide in along both long walls, benches in between, a cabinet.
        + [p for x in list(range(24, 31)) + list(range(37, 44)) for p in tall("locker", x, 21, "south") + tall("locker", x, 28, "north")]
        + row("bench", 25, 29, 24, "north") + row("bench", 38, 42, 24, "north")
        + column("lab_cabinet", 24, 24, 25, "east") + column("lab_cabinet", 43, 24, 25, "west") + [("lab_coat", 24, 26, "east", 2)]
        # The ring: posters and first aid on the walls, carts against them; the fuse box (FUSE_BOX) by the
        # stairs' lever in the east side.
        + [("lab_poster", 26, 14, "south", 2), ("lab_poster", 57, 14, "south", 2), ("first_aid_box", 48, 14, "south", 2)]
        + [("lab_cart", 30, 36, "north", 1), ("lab_cart", 55, 36, "north", 1), ("lab_poster", 42, 36, "north", 2)]
        + [("first_aid_box", 9, 27, "east", 2), ("lab_poster", 71, 21, "east", 2)]
        # The niches in the ring's walls: benches, a water dispenser, the fire extinguishers under their signs.
        + row("bench", 28, 29, 37, "north") + column("bench", 8, 28, 29, "east")
        + [("water_dispenser", 48, 37, "north", 1), ("fire_extinguisher", 49, 37, "north", 1), ("sign_extinguisher", 49, 37, "north", 3)]
        + [("fire_extinguisher", 50, 18, "north", 1), ("sign_extinguisher", 50, 18, "north", 3), ("potted_plant", 51, 18, "north", 1)]
        # By the doors, their card readers (green); safety signs: the stairs down to the lab, radiation on the ring.
        + [("lab_reader", 40, 14, "south", 2), ("lab_reader", 65, 14, "south", 2), ("lab_reader", 9, 36, "north", 2)]
        + [("sign_exit", 11, 36, "north", 4), ("sign_biohazard", 9, 36, "north", 3), ("sign_radiation", 60, 17, "north", 3)]
    ),
    2: (
        # The test chambers: beside each subject on its stretcher (POINTS), a drip stand on one side, its tube towards
        # it, and a tray of instruments on the other, facing the window.
        [("iv_drip", 28, 50, "west", 1), ("instrument_tray", 26, 50, "south", 1),
         ("iv_drip", 34, 67, "east", 1), ("instrument_tray", 36, 67, "north", 1),
         ("iv_drip", 50, 50, "west", 1), ("instrument_tray", 48, 50, "south", 1),
         ("iv_drip", 56, 67, "east", 1), ("instrument_tray", 58, 67, "north", 1)]
        # The Lab hall: benches along the north and south walls between the ways out, a computer on each
        # row, carts, posters, first aid; its middle kept clear.
        + row("lab_bench", 80, 90, 50, "south") + row("lab_bench", 81, 90, 70, "north")
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
    3: (
        # The foot of the ladder: oil drums in its corners, away from the ladder and the tunnel's mouth.
        [("oil_drum", 137, 70, "west", 1), ("oil_drum", 136, 70, "south", 1), ("oil_drum", 137, 78, "west", 1)]
        # The Junction: a sluice gate in its north wall over the pool, a valve wheel and a gauge beside it, drums.
        + row("sluice_gate", 184, 188, 51, "south") + row("sluice_gate", 184, 188, 51, "south", 2)
        + row("sluice_gate", 184, 188, 51, "south", 3)
        + [("valve_wheel", 182, 51, "south", 2), ("pressure_gauge", 190, 51, "south", 2)]
        + [("oil_drum", 178, 66, "north", 1), ("oil_drum", 179, 66, "north", 1), ("oil_drum", 194, 51, "west", 1)]
        # The Cistern: a valve wheel and a gauge on its walls; nothing on its catwalks but its Hunters.
        + [("valve_wheel", 196, 70, "east", 2), ("pressure_gauge", 211, 70, "west", 2)]
        # The Pump room: the pumps against the south wall (PUMP_PIPES over them), a workbench by the door with
        # food and water, tool chests with batteries, the control panel, drums, gauges.
        + [p for x in (164, 168, 174, 178) for p in tall("pump", x, 97, "north")]
        + row("lab_bench", 162, 165, 89, "south") + [("tool_chest", 180, z, "west", 1) for z in (91, 92)]
        + [("electrical_panel", 161, 93, "east", 2), ("pressure_gauge", 175, 89, "south", 3), ("pressure_gauge", 177, 89, "south", 3)]
        + [("oil_drum", 161, 97, "east", 1), ("oil_drum", 180, 97, "west", 1), ("oil_drum", 180, 96, "west", 1)]
        # The Hideout: the guard's body against the north wall (POINTS), his dead flashlight and his note
        # (NOTE_PAPERS) by him, a mattress of rags, a crate for a table, empty tins.
        + [("dead_flashlight", 159, 50, "east", 1)] + row("mattress", 168, 169, 49, "south")
        + [("cardboard_box", 171, 52, "west", 1), ("tin_cans", 166, 51, "south", 1), ("tin_cans", 170, 54, "west", 1)]
        # The director's office: his desk at the far end, facing the door, his chair behind it, two chairs for
        # visitors; on the desk a lamp, the mini block of diorite, papers (and a free place by the diorite, in
        # front of him); a credenza behind him with coffee; a black leather sofa and a glass table; plants.
        + [(n, x, z, f, y + RISE) for n, x, z, f, y in
           column("exec_desk", 256, 57, 61, "west")
           + [("desk_lamp_modern", 256, 57, "west", 2), ("mini_diorite", 256, 58, "west", 2), ("paper_stack", 256, 60, "west", 2),
              ("coffee_cup", 256, 61, "west", 2)]
           + [("exec_chair", 257, 59, "west", 1), ("leather_chair", 254, 58, "east", 1), ("leather_chair", 254, 60, "east", 1)]
           + column("credenza", 260, 56, 62, "west") + [("coffee_machine", 260, 61, "west", 2), ("coffee_cup", 260, 60, "west", 2),
                                                        ("paper_stack", 260, 57, "west", 2)]
           + row("leather_sofa", 246, 249, 52, "south") + [("glass_table", 247, 54, "north", 1), ("glass_table", 248, 54, "north", 1)]
           + [("potted_plant", x, z, "south", 1) for x, z in ((243, 52), (243, 66), (260, 52), (260, 66))]
           # His runner from the door to his visitors' chairs, its gold edges outside; his trophies on a low credenza
           # by the south wall, their plaques over them (TROPHIES); his diplomas over the sofa (DIPLOMAS); his wall of
           # CCTV screens over the credenza behind him (MONITORS).
           + [("office_rug", x, 59, "east", 1) for x in range(243, 254)] + [("office_rug", x, 60, "west", 1) for x in range(243, 254)]
           + row("credenza", 255, 257, 66, "north")
           + [("trophy_cup", 255, 66, "north", 2), ("crystal_award", 256, 66, "north", 2), ("month_plaque", 257, 66, "north", 2)]
           # His secretary's anteroom, spotless and lit as if still open: her desk on the north side facing the way
           # through, its computer (TERMINALS) and phone, her chair; three leather chairs for visitors along the south
           # wall with a glass table; plants, a painting, a clock; the plate by his door (DIRECTOR_PLATE) and the
           # camera in the ceiling's corner (POINTS: the camera faces the stairs' doorway).
           + row("exec_desk", 238, 240, 56, "south") + [("desk_phone", 240, 56, "south", 2), ("paper_stack", 238, 56, "south", 2)]
           + [("exec_chair", 239, 55, "south", 1)]
           + [("leather_chair", x, 64, "north", 1) for x in (237, 238, 239)] + [("glass_table", 240, 64, "north", 1)]
           + [("potted_plant", x, z, "south", 1) for x, z in ((236, 55), (241, 55), (236, 64))]
           + [("lounge_painting", 239, 64, "north", 3), ("wall_clock", 239, 55, "south", 3)]
           + [("security_camera", 241, 64, "west", 5)]]
    ),
}

# The Pump room's pipes: up from each pump into a run along the south wall under the ceiling, both ends into
# the walls: (x, y, z) over layer 3's floor.
PUMP_PIPES = [(x, y, 97) for x in (164, 168, 174, 178) for y in (3, 4)] + [(x, 5, 97) for x in range(161, 181)]

# Loose notes lying about: (note id, layer, x, z, facing), on the floor. The guard's, by his body.
NOTE_PAPERS = [("ch1_l5_note_guard", 3, 161, 50, "south")]

# Cork boards with a card each: (card note id, layer, x, z, facing), at eye height. The Waiting room's
# noticeboard; the experiment card by each test chamber's window, on the corridor's side.
NOTE_BOARDS = [("ch1_l5_card_s1", 2, 21, 57, "south"), ("ch1_l5_card_s3", 2, 43, 57, "south"),
               ("ch1_l5_card_s2", 2, 29, 60, "north"), ("ch1_l5_card_s4", 2, 51, 60, "north")]

# The Waiting room's safety briefing (BriefingBoardBlock, BriefingSignBlock): on its north wall a cork board `cols`
# wide from `x` (column 0 on the left as seen from the front, the plan's x rising) and `rows` high from eye height,
# the plate over it from `sign_x`; each card is a spawn point at its column's cell in the bottom row (the block's
# NOTE_COLS).
BRIEFING = dict(layer=1, x=63, z=2, facing="south", cols=6, rows=2, sign_x=64, sign_cols=4,
                cards=("ch1_l5_card_hunters", "ch1_l5_card_cloud", "ch1_l5_card_junior", "ch1_l5_card_equipment"),
                note_cols=(0, 2, 3, 5))

# Layer 3: a chalk arrow (ChalkArrowBlock) at eye height on the tunnel's far wall at the foot of the ladder, pointing
# left (the plan's north), the way to the Hideout: `cols` blocks from (x, z), column 0 on the left as seen from the front.
CHALK_ARROW = dict(layer=3, x=154, z=74, facing="west", cols=3)
# Along a wall facing each way, to the right as seen from the front.
RIGHT_OF = {"south": (1, 0), "north": (-1, 0), "west": (0, 1), "east": (0, -1)}

# Layer 1's columns, (layer, x, z): the Lobby's and the Workshop's, floor to ceiling, finished like the walls (white
# tiles, the green band at their foot).
COLUMNS = [(1, 6, 11), (1, 6, 18), (1, 16, 11), (1, 16, 18), (1, 35, 7), (1, 48, 7)]
# Room nameplates on the walls (WallTextBlock in its lab style): (lang key, layer, x, z, facing, y, the side of its block
# the text keeps to, as read: the door's).
PLATES = [("sign.every_tunnel.room.workshop", 1, 43, 14, "south", 2, "left"),
          ("sign.every_tunnel.room.waiting_room", 1, 68, 14, "south", 2, "left"),
          ("sign.every_tunnel.room.lockers", 1, 36, 17, "north", 2, "right"),
          ("sign.every_tunnel.room.stairs", 1, 12, 36, "north", 2, "right")]
# The director's diplomas (WallTextBlock, framed certificates): (lang key, x, z, facing), over his office's floor at eye
# height; his trophies' brass plaques, over each trophy.
DIPLOMAS = [("sign.every_tunnel.diploma.service", 245, 52, "south"), ("sign.every_tunnel.diploma.director", 247, 52, "south"),
            ("sign.every_tunnel.diploma.excellence", 249, 52, "south"), ("sign.every_tunnel.diploma.retention", 251, 52, "south")]
TROPHIES = [("sign.every_tunnel.trophy.director", 255, 66, "north"), ("sign.every_tunnel.trophy.hours", 256, 66, "north"),
            ("sign.every_tunnel.trophy.month", 257, 66, "north")]
# The wall of CCTV screens behind his chair (MonitorBankBlock): its left column as seen from the front (x, z), the way
# it faces, columns and rows, its bottom row's height over the office's floor; cameras numbered left to right, top down.
MONITORS = dict(x=260, z=57, facing="west", cols=5, rows=3, y=3)
# The director's door's plate in the anteroom, brass (WallTextBlock): (lang key, layer, x, z, facing, y over the floor, side).
DIRECTOR_PLATE = ("sign.every_tunnel.room.director", 3, 241, 58, "west", RISE + 2, "right")
# The green line on the floor, from the Lobby down the ring's west side to the stairs' door: (layer, x, z0, z1).
FLOOR_LINE = (1, 10, 12, 36)

# The fuse box: a holder a slot on the east wall of the ring's east side, beside the stairs' lever (74, 25),
# at eye height, in amperage order (index into FUSE_AMPS); the 15A is in already.
FUSE_AMPS = (5, 10, 15, 20, 30)
FUSE_BOX = [(74, 27 + i, i) for i in range(len(FUSE_AMPS))]
FUSE_IN = 2

# Computers on desks and benches: (id, layer, x, z, facing). `terminal_office` tells the chemical puzzle's
# answer (ChemicalHooks); the others show what they have to show (TerminalHooks).
TERMINALS = [("terminal_anteroom", 3, 239, 56, "south"), ("terminal_office", 2, 85, 18, "south"), ("terminal_office_west", 2, 78, 13, "south"),
             ("terminal_office_east", 2, 90, 13, "south"), ("terminal_lab_hall_north", 2, 83, 50, "south"),
             ("terminal_lab_hall_south", 2, 87, 70, "north"), ("terminal_storage", 2, 114, 84, "west")]

# The chemical room's mixer against the south wall, looking into the room: a tank either side of its body.
MIXER = [("mixer_tank", 123, 69), ("mixer", 124, 69), ("mixer_tank", 125, 69)]
# The acid's pipe, along the south wall under the ceiling, from over the mixer to past the grate (x 127 to 130).
PIPE = [(x, 4, 69) for x in range(124, 131)]
# The alarm's red lights, high on the chemical room's walls: (x, z, facing).
ALARMS = [(110, 50, "south"), (135, 50, "south"), (110, 69, "north"), (135, 69, "north")]
# The hatch down to the drain, closed, flush with the floor in the middle of the room behind the grate; the
# ladder under it goes down to layer 3.
HATCH = (128, 75)

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
    # Layer 3: the Pump room's two batteries, food and water; the Hideout's last food, on his crate.
    ("battery_pump_1", "battery", 3, 180, 91, 2), ("battery_pump_2", "battery", 3, 180, 92, 2),
    ("snack_pump", "food_snack", 3, 163, 89, 2), ("water_pump", "water", 3, 164, 89, 2),
    ("snack_hideout", "food_snack", 3, 171, 52, 2),
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
    # Layer 3: the Junction's restart point, on its west ledge (ContinueGame), and its Junior, over the pool in
    # plain sight; the Cistern's Hunters, where its catwalks cross; the guard's body in the Hideout, sitting
    # against the wall (GuardHooks).
    ("checkpoint_junction", 3, 178, 54, "east", ["restart_point"]),
    ("junior_junction", 3, 190, 55, "west", ["distortion_junior"]),
    ("hunters_cistern", 3, 202, 73, "north", ["hunters"]),
    ("dead_guard", 3, 160, 49, "south", ["dead_guard"]),
    # The director, in his chair behind the desk, facing the door (DirectorHooks; his cinematic,
    # DirectorScene, plays round him).
    ("director", 3, 257, 59, "west", ["director"]),
    # Just inside his door, facing him: where his scene puts you (his door shuts behind you), and the restart point
    # once it is over (DirectorSceneHooks; no zone of its own, ContinueGame).
    ("checkpoint_director", 3, 243, 59, "east", ["restart_point", "director_inside"]),
    # In the anteroom, before his door: where `/everytunnel director` puts you to see his scene again.
    ("director_door_outside", 3, 240, 59, "east", ["director_debug"]),
]


class Furnish:
    """The level's furniture in `lab` (a Lab, composed up to its levers), whose air cells are `air`."""

    def __init__(self, lab, air, taken):
        self.lab, self.air = lab, air
        self.items = {}            # plan (x, y, z) -> (name, plan facing)
        self.taken = set(taken)    # plan cells already holding something (the levers, the windows)
        self.extra = {}            # plan (x, y, z) -> a state of its own
        self.texts = {}            # plan (x, y, z) -> (lang key, align): the room plates

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
        for layer, x, z in COLUMNS:
            f = self.floor(layer)
            for y in range(1, self.lab.height_at(layer, x, z) + 1):
                self.put("lab_column", x, z, "north", f + y)
        for key, layer, x, z, facing, y, align in PLATES:
            self.put("wall_text", x, z, facing, self.floor(layer) + y)
            self.texts[(x, self.floor(layer) + y, z)] = (key, align, "lab")
        key, layer, x, z, facing, y, align = DIRECTOR_PLATE
        self.put("wall_text", x, z, facing, self.floor(layer) + y)
        self.texts[(x, self.floor(layer) + y, z)] = (key, align, "brass")
        office = self.floor(3) + RISE
        for (key, x, z, facing), y, style in [(d, 2, "diploma") for d in DIPLOMAS] + [(t, 3, "plaque") for t in TROPHIES]:
            self.put("wall_text", x, z, facing, office + y)
            self.texts[(x, office + y, z)] = (key, "center", style)
        (dx, dz), mon = RIGHT_OF[MONITORS["facing"]], MONITORS
        for row_ in range(mon["rows"]):
            for col in range(mon["cols"]):
                x, z, y = mon["x"] + dx * col, mon["z"] + dz * col, office + mon["y"] + mon["rows"] - 1 - row_
                self.put("monitor_bank", x, z, mon["facing"], y)
                self.extra[(x, y, z)] = f"cam={row_ * mon['cols'] + col},facing={{facing}}"
        layer, x, z0, z1 = FLOOR_LINE
        for z in range(z0, z1 + 1):
            self.put("floor_line", x, z, "north", self.floor(layer) + 1)
        bf, bz = self.floor(BRIEFING["layer"]), BRIEFING["z"]
        for col in range(BRIEFING["cols"]):
            for row_ in range(BRIEFING["rows"]):
                self.put("briefing_board", BRIEFING["x"] + col, bz, BRIEFING["facing"], bf + 2 + row_)
                self.extra[(BRIEFING["x"] + col, bf + 2 + row_, bz)] = f"col={col},facing={{facing}},row={row_}"
        for col in range(BRIEFING["sign_cols"]):
            self.put("briefing_sign", BRIEFING["sign_x"] + col, bz, BRIEFING["facing"], bf + 2 + BRIEFING["rows"])
            self.extra[(BRIEFING["sign_x"] + col, bf + 2 + BRIEFING["rows"], bz)] = f"col={col},facing={{facing}}"
        cf, (dx, dz) = self.floor(CHALK_ARROW["layer"]) + 2, RIGHT_OF[CHALK_ARROW["facing"]]
        for col in range(CHALK_ARROW["cols"]):
            x, z = CHALK_ARROW["x"] + dx * col, CHALK_ARROW["z"] + dz * col
            self.put("chalk_arrow", x, z, CHALK_ARROW["facing"], cf)
            self.extra[(x, cf, z)] = f"col={col},facing={{facing}}"
        for x, z, i in FUSE_BOX:
            self.put("fuse_holder", x, z, "west", 2)
            self.extra[(x, 2, z)] = f"amps={i},facing={{facing}},filled={str(i == FUSE_IN).lower()},lit=false"
        f2 = self.floor(2)
        for _, layer, x, z, facing in TERMINALS:
            self.put("lab_terminal", x, z, facing, self.lab.floors[(layer, x, z)] + 2)
        for name, x, z in MIXER:
            self.put(name, x, z, "north", f2 + 1)
        for x, y, z in PIPE:
            self.put("pipe", x, z, "north", f2 + y)
            # Along the plan's x: the world's z, so it joins north and south.
            self.extra[(x, f2 + y, z)] = "down=false,east=false,north=true,south=true,up=false,west=false"
        for x, z, facing in ALARMS:
            self.put("alarm_light", x, z, facing, f2 + 4)
        for _, layer, x, z, facing in NOTE_PAPERS:
            self.put("note_paper", x, z, facing, self.floor(layer) + 1)
        # The Pump room's pipes, each joined to its neighbours, the risers down into their pumps, the run along
        # the wall into the walls at its ends. Directions are the world's: the plan's x is the world's z.
        f3 = self.floor(3)
        pipes = {(x, f3 + y, z) for x, y, z in PUMP_PIPES}
        world_dirs = {"south": (1, 0, 0), "north": (-1, 0, 0), "west": (0, 0, 1), "east": (0, 0, -1), "up": (0, 1, 0), "down": (0, -1, 0)}
        for x, y, z in sorted(pipes):
            self.put("pipe", x, z, "north", y)
        for x, y, z in pipes:
            flags = {}
            for name, (dx, dy, dz) in world_dirs.items():
                c = (x + dx, y + dy, z + dz)
                into_pump = name == "down" and self.items.get(c, ("",))[0] == "pump"
                into_wall = name in ("north", "south") and c not in self.air
                flags[name] = c in pipes or into_pump or into_wall
            self.extra[(x, y, z)] = ",".join(f"{k}={str(v).lower()}" for k, v in sorted(flags.items()))
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
            if name == "lab_column":
                state = self.lab.state("band" if (plan[0], plan[1] - 1, plan[2]) not in self.items else "tile")
            elif plan in self.texts:
                key, align, style = self.texts[plan]
                state = f'{bid}[facing={facing}]{{Text:"{key}",Style:"{style}",Offset:0.0f,Align:"{align}"}}'
            elif plan in self.extra:
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
        """{world position: state} of the drain's hatch, set into the floor, and the steel round the top of the
        shaft under it (the ladder goes down it to layer 3: level.py)."""
        x, z = HATCH
        f2 = self.floor(2)
        facing = self.lab.WORLD_FACING["north"]
        out = {self.lab.at(x, f2, z): f"every_tunnel:bunker_trapdoor[facing={facing},half=top,open=false,powered=false,waterlogged=false]"}
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
        for nid, layer, x, z, _ in NOTE_PAPERS:
            out.append({"id": nid, "pos": self.centre(x, self.floor(layer) + 1, z), "yaw": 0, "tags": ["note"]})
        for nid, col in zip(BRIEFING["cards"], BRIEFING["note_cols"]):
            out.append({"id": nid, "pos": self.centre(BRIEFING["x"] + col, self.floor(BRIEFING["layer"]) + 2, BRIEFING["z"]),
                        "yaw": 0, "tags": ["note"]})
        for tid, layer, x, z, _ in TERMINALS:
            out.append({"id": tid, "pos": self.centre(x, self.lab.floors[(layer, x, z)] + 2, z), "yaw": 0, "tags": ["terminal"]})
        for pid, layer, x, z, facing, tags in POINTS:
            y = self.lab.floors.get((layer, x, z), self.floor(layer)) + 1      # on a raised floor (the office), on it
            out.append({"id": pid, "pos": self.centre(x, y, z), "yaw": yaw(facing), "tags": tags})
        return out

    def centre(self, x, y, z):
        wx, wy, wz = self.lab.at(x, y, z)
        return [wx + 0.5, wy, wz + 0.5]

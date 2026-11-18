"""
Chapter 2 Level 2's Observant Guard: installs the user's model for GeckoLib and writes the animations it lacks.

Run after changing anything here or in the source (from the repository root):   python3 tools/entity/observant_guard.py

The model is the user's "corrupto" Guard (tools/entity/source/observant_guard/: geo, 256 x 256 texture, its idle, walk and attack
animations, the glowmask of its one white point, the editable Blockbench file and its LEEME.md). It is the source: change it in
Blockbench and export it there, never edit what this tool writes. This tool writes into src/main/resources/assets/every_tunnel/
(committed with the mod):
  geo/entity/observant_guard.geo.json                   the source geo, renamed geometry.observant_guard
  animations/entity/observant_guard.animation.json      idle, walk and attack from the source; chase, stunned death (a fall to the floor, then coming apart), punch, point and chest (its entrance) written here
                                                        on its bones
  textures/entity/observant_guard.png                   the source texture
  textures/entity/observant_guard_glowmask.png          the point's mask (drawn by client.render.EyesGlowLayer)
  and the entity's name in Chapter 2's lang part (tools/lang/src/chapter2); no spawn egg: it is never summoned
and tools/entity/output/observant_guard_preview.png (git-ignored): front and side views of the key poses.

The model: 2.5 blocks tall (40 units), facing north (-z), bones root > pelvis > body > head, arms (arm > forearm > hand) on the
body, legs (leg > shin > foot) on the pelvis. Rotations are degrees [x, y, z] as in the source (the attack raises the right arm at
-95). The source attack's blow lands at 0.62 s, the brain's windup (level.json guard.windup_seconds 0.6).
"""

import json
import math
import random
import shutil
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "lang"))
sys.path.insert(0, str(Path(__file__).resolve().parent))
import langfiles  # noqa: E402
import vibration_creature as vc  # noqa: E402  (its pose and drawing helpers)

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
SOURCE = Path(__file__).resolve().parent / "source" / "observant_guard"
OUT = Path(__file__).resolve().parent / "output"
NAME = "observant_guard"
SOURCE_NAME = "observant_guard_corrupto"

BONES = ["pelvis", "body", "head", "coat_tail", "left_arm", "left_forearm", "left_hand", "left_leg", "left_shin", "left_foot",
         "right_arm", "right_forearm", "right_hand", "right_leg", "right_shin", "right_foot"]


def scaled(channel, time_scale, amp=1.0):
    """A channel with its keyframe times multiplied by time_scale and its values by amp."""
    return {f"{float(t) * time_scale:.4g}": [round(c * amp, 2) for c in v] for t, v in channel.items()}


def chase_from(walk):
    """The walk quickened and leaning in: 0.9 s a stride, wider swings, the body bent forward and the head held."""
    out = {"loop": True, "animation_length": round(walk["animation_length"] * 0.64, 3), "bones": {}}
    for bone, channels in walk["bones"].items():
        out["bones"][bone] = {kind: scaled(ch, 0.64, 1.25 if kind == "rotation" and "leg" in bone or "arm" in bone else 1.0)
                              for kind, ch in channels.items()}
    out["bones"]["body"] = {"rotation": {"0": [13, 0, -1], "0.45": [13, 0, 1], "0.9": [13, 0, -1]}}
    out["bones"]["head"] = {"rotation": {"0": [-8, 0, -7], "0.45": [-8, 0, -7], "0.9": [-8, 0, -7]}}
    return out


def stunned():
    """Rigid and twitching (0.6 s loop): arms held stiff and half raised, legs locked, the body and head jerking a few degrees every
    50 ms, deterministic."""
    rng = random.Random(11)
    n = 12
    jerk = lambda amp: {f"{0.05 * i:.4g}": [round(rng.uniform(-amp, amp), 2) for _ in range(3)] for i in range(n)} | {"0.6": [0, 0, 0]}
    body = jerk(3.0)
    body["0.6"] = body["0"]
    head = {k: [v[0] + 6, v[1] * 1.5, v[2] - 6] for k, v in jerk(5.0).items()}
    head["0.6"] = head["0"]
    return {"loop": True, "animation_length": 0.6, "bones": {
        "body": {"rotation": body, "position": {"0": [0, 0, 0], "0.3": [0.25, 0, -0.2], "0.6": [0, 0, 0]}},
        "head": {"rotation": head},
        "left_arm": {"rotation": {"0": [-50, 0, -14], "0.3": [-46, 0, -18], "0.6": [-50, 0, -14]}},
        "right_arm": {"rotation": {"0": [-50, 0, 14], "0.3": [-46, 0, 18], "0.6": [-50, 0, 14]}},
        "left_forearm": {"rotation": {"0": [-25, 0, 0], "0.3": [-30, 0, 0], "0.6": [-25, 0, 0]}},
        "right_forearm": {"rotation": {"0": [-25, 0, 0], "0.3": [-30, 0, 0], "0.6": [-25, 0, 0]}},
        "left_leg": {"rotation": {"0": [6, 0, 0], "0.6": [6, 0, 0]}},
        "right_leg": {"rotation": {"0": [-6, 0, 0], "0.6": [-6, 0, 0]}},
    }}


FALL = 3.0     # the collapse takes this long: knees give, the whole figure falls forward onto the floor, settles


def death():
    """Falling to the floor, then coming apart, for 14 s (the brain's death_seconds): in the first 3 s the head drops, the knees give
    and the whole figure falls forward onto the floor with a small bounce (root rotated 90 degrees, lifted so that it lies on the
    ground); then it lies there twitching, and every bone drifts away from where it was, in its own direction and a little further
    each quarter second, flickering in and out of place (a glitch); over the last 2.5 s the whole figure shrinks to nothing. The
    cloud of particles is the mod's."""
    rng = random.Random(23)
    length, step = 14.0, 0.25
    times = [round(i * step, 4) for i in range(int(length / step) + 1)]
    bones = {}
    for b in BONES:
        direction = [rng.uniform(-1, 1), rng.uniform(-0.2, 1), rng.uniform(-1, 1)]
        pos, rot = {}, {}
        for t in times:
            grow = (max(0.0, t - FALL - 0.5) / (length - FALL - 0.5)) ** 1.6
            glitch = 1.0 if rng.random() > 0.2 * grow * 4 else rng.uniform(-0.6, 1.8)
            pos[f"{t:.4g}"] = [round(d * 14.0 * grow * glitch + rng.uniform(-1, 1) * (0.2 + 3.0 * grow), 2) for d in direction]
            rot[f"{t:.4g}"] = [round(rng.uniform(-1, 1) * (1.5 + 40.0 * grow), 2) for _ in range(3)]
        bones[b] = {"position": pos, "rotation": rot}
    # The collapse, on top: the head drops at 0.4 s, the knees give from 0.8 s, the fall is 1.5 to 2.3 s and bounces.
    for b, kind, frames in (
        ("head", "rotation", {"0": [0, 0, 0], "0.4": [28, 0, -10], "1.6": [50, 0, -10], "2.3": [-18, 0, 14], "2.6": [10, 0, 6]}),
        ("left_leg", "rotation", {"0": [0, 0, 0], "0.8": [-30, 0, 0], "1.5": [-55, 0, 0], "2.3": [-8, 0, 0]}),
        ("right_leg", "rotation", {"0": [0, 0, 0], "0.8": [-24, 0, 0], "1.5": [-50, 0, 0], "2.3": [-4, 0, 0]}),
        ("left_shin", "rotation", {"0": [0, 0, 0], "0.8": [40, 0, 0], "1.5": [75, 0, 0], "2.3": [10, 0, 0]}),
        ("right_shin", "rotation", {"0": [0, 0, 0], "0.8": [34, 0, 0], "1.5": [70, 0, 0], "2.3": [6, 0, 0]}),
        ("left_arm", "rotation", {"0": [0, 0, 0], "1.5": [-20, 0, 8], "2.3": [-70, 0, 20]}),
        ("right_arm", "rotation", {"0": [0, 0, 0], "1.5": [-20, 0, -8], "2.3": [-70, 0, -20]}),
    ):
        keep = {k: v for k, v in bones[b][kind].items() if float(k) > FALL}
        bones[b][kind] = dict(sorted({**frames, **keep}.items(), key=lambda kv: float(kv[0])))
    fall = {"0": [0, 0, 0], "1.5": [4, 0, 0], "1.9": [70, 0, 0], "2.15": [92, 0, 0], "2.4": [86, 0, 0], "2.7": [90, 0, 0], f"{length}": [90, 0, 0]}
    lift = {"0": [0, 0, 0], "1.5": [0, -1.5, 0], "1.9": [0, 1, 0], "2.7": [0, 3, 0], f"{length}": [0, 3, 0]}
    shrink = {f"{t:.4g}": [round(max(0.0, min(1.0, (length - t) / 2.5)), 3)] * 3 for t in times if t >= length - 2.5}
    shrink["0"] = [1, 1, 1]
    bones["root"] = {"rotation": fall, "position": lift, "scale": dict(sorted(shrink.items(), key=lambda kv: float(kv[0])))}
    return {"loop": "hold_on_last_frame", "animation_length": length, "bones": bones}


# Rotations are the animation's own values as GeckoLib turns them (see vibration_creature.rotate): for the arm at +x (the "left" one) a
# NEGATIVE z swings it outwards, a positive one towards the body; the arm at -x is the mirror. SIDE is that sign, so that a pose is
# written once for the left arm and mirrored for the right: `side(left_value)` -> (left, right).

def mirror(v):
    """A rotation of the left arm's side as the right one's: the same x and the opposite y and z."""
    return [v[0], -v[1], -v[2]]


def punch():
    """Punching the wall (1.6 s, the fist lands at 1.0 s, the intro's PUNCH_AT): the right arm drawn back with the body turning away, the
    blow straight ahead with the whole body leaning into it, the head following, then the arm stays out a moment and drops."""
    return {"loop": False, "animation_length": 1.6, "bones": {
        "body": {"rotation": {"0": [0, 0, 0], "0.6": [-6, -16, 0], "1.0": [18, 10, 0], "1.2": [16, 8, 0], "1.6": [0, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, -3], "0.6": [-4, -14, 0], "1.0": [10, 6, 0], "1.6": [0, 0, -3]}},
        # The right arm (at -x): outwards is a positive z.
        "right_arm": {"rotation": {"0": [0, 0, 0], "0.6": [50, 0, 12], "0.95": [-92, -6, 0], "1.2": [-88, -6, 0], "1.6": [0, 0, 0]}},
        "right_forearm": {"rotation": {"0": [0, 0, 0], "0.6": [-60, 0, 0], "0.95": [0, 0, 0], "1.2": [0, 0, 0], "1.6": [0, 0, 0]}},
        # The left one (at +x): outwards is a negative z.
        "left_arm": {"rotation": {"0": [0, 0, 0], "0.6": [-30, 0, -14], "1.0": [-20, 0, -8], "1.6": [0, 0, 0]}},
        "left_leg": {"rotation": {"0": [0, 0, 0], "0.6": [8, 0, 0], "1.0": [-12, 0, 0], "1.6": [0, 0, 0]}},
        "right_leg": {"rotation": {"0": [0, 0, 0], "0.6": [-6, 0, 0], "1.0": [16, 0, 0], "1.6": [0, 0, 0]}},
    }}


def point():
    """Pointing at the player (2.2 s, held on its last frame): the head turns and locks on, the right arm comes up stiff and straight
    until it is level with the eyes and stays there, jabbing at them twice (the arm thrust forward a hand's breadth, so that seen from
    where the player stands it is a jab at the camera); the other hand clenched at its side."""
    return {"loop": False, "animation_length": 2.2, "bones": {
        "right_arm": {"rotation": {"0": [0, 0, 0], "0.5": [-86, 0, 0], "0.9": [-92, 0, 0], "2.2": [-92, 0, 0]},
                      "position": {"0": [0, 0, 0], "0.9": [0, 0, 0], "1.05": [0, 0, -1.6], "1.25": [0, 0, 0], "1.55": [0, 0, 0], "1.7": [0, 0, -1.6], "1.9": [0, 0, 0]}},
        "right_forearm": {"rotation": {"0": [0, 0, 0], "0.5": [-2, 0, 0], "2.2": [-2, 0, 0]}},
        "head": {"rotation": {"0": [0, 0, -3], "0.4": [8, 0, -2], "2.2": [6, 0, -2]}},
        "body": {"rotation": {"0": [0, 0, 0], "0.5": [6, 0, 0], "2.2": [6, 0, 0]}},
        "left_arm": {"rotation": {"0": [0, 0, 0], "0.5": [-8, 0, -6], "2.2": [-8, 0, -6]}},
        "left_forearm": {"rotation": {"0": [0, 0, 0], "0.5": [-30, 0, 0], "2.2": [-30, 0, 0]}},
    }}


# The chest-beat's two poses of the left arm (the right one is its mirror): arms flung up and wide in a V with the head thrown back
# (the roar), then the fists brought down on the chest with the elbows flared out, so that each fist lands on
# its own side of the breastbone, a hand's breadth from the other and never touching it. Found with a geometry search against the
# model (`check_hands` below): the fist centres at the hit are at x = +-4.4, y = 25, 4.5 units in front of the chest (its front face is at z = -2.4).
# The blows: the first lands at CHEST_FIRST seconds, then every CHEST_EVERY (the intro's CHEST_AT and CHEST_EVERY, in ticks).
CHEST_FIRST = 0.4
CHEST_EVERY = 0.55
CHEST_OUT = {"arm": [-10, 0, -118], "fore": [-20, 0, -10], "body": [-4, 0, 0], "head": [-24, 0, -3]}
CHEST_HIT = {"arm": [5, 0, -58], "fore": [-145, 0, -20], "body": [3, 0, 0], "head": [-2, 0, -3]}


def chest():
    """Beating the chest with both fists (3 s): five blows, one every 0.55 s from 0.4 s (the intro's CHEST_AT and CHEST_EVERY), each with the arms flung
    up and wide in a V and the head thrown back, then the fists brought down on the chest with the elbows flared out.
    The fists land one on each side of the breastbone and never meet; at no frame are the hands nearer than a hand's breadth."""
    keys = {k: {} for k in ("arm_l", "arm_r", "fore_l", "fore_r", "body", "head")}

    def put(time, pose):
        key = f"{time:.4g}"
        keys["arm_l"][key] = pose["arm"]
        keys["arm_r"][key] = mirror(pose["arm"])
        keys["fore_l"][key] = pose["fore"]
        keys["fore_r"][key] = mirror(pose["fore"])
        keys["body"][key] = pose["body"]
        keys["head"][key] = pose["head"]

    rest = {"arm": [0, 0, 0], "fore": [0, 0, 0], "body": [0, 0, 0], "head": [0, 0, -3]}
    put(0.0, rest)
    for k in range(5):
        hit = CHEST_FIRST + CHEST_EVERY * k
        put(hit - CHEST_EVERY / 2, CHEST_OUT)
        put(hit, CHEST_HIT)
    put(3.0, rest)
    ordered = lambda d: dict(sorted(d.items(), key=lambda kv: float(kv[0])))
    return {"loop": False, "animation_length": 3.0, "bones": {
        "left_arm": {"rotation": ordered(keys["arm_l"])}, "right_arm": {"rotation": ordered(keys["arm_r"])},
        "left_forearm": {"rotation": ordered(keys["fore_l"])}, "right_forearm": {"rotation": ordered(keys["fore_r"])},
        "body": {"rotation": ordered(keys["body"])}, "head": {"rotation": ordered(keys["head"])},
    }}


def hand_clearance(geo, anim, step=0.05):
    """The least gap, over the animation, between the two hands' boxes along x (negative: they overlap on that axis in some frame, and
    in the same frame the other two axes are checked: the gap is only reported when the boxes also overlap in y and z), and when.
    Boxes are the axis-aligned hull of each hand's posed cubes, so a rotated fist counts a little bigger than it is."""
    names = [b["name"] for b in geo["bones"] for _ in b.get("cubes", [])]
    worst, at = 99.0, 0.0
    t = 0.0
    while t <= anim["animation_length"] + 1e-9:
        cubes = vc.posed_cubes(geo, anim, t)
        boxes = {}
        for hand in ("left_hand", "right_hand"):
            pts = [p for n, c in zip(names, cubes) if n == hand for p in c]
            boxes[hand] = [(min(p[i] for p in pts), max(p[i] for p in pts)) for i in range(3)]
        l, r = boxes["left_hand"], boxes["right_hand"]
        # Separation along each axis (positive: apart on it); the hands touch only if they overlap on all three.
        gaps = [max(l[i][0] - r[i][1], r[i][0] - l[i][1]) for i in range(3)]
        gap = max(gaps)
        if gap < worst:
            worst, at = gap, t
        t += step
    return worst, at


def check_hands(geo, anims):
    """The two fists must never touch in the chest beat (the first version had them meet in front of the body: GeckoLib turns z the
    other way round from what the preview drew). Stops the tool if they come within a hand's breadth of each other."""
    for name in ("chest", "stunned", "point", "punch", "attack", "chase", "walk"):
        worst, at = hand_clearance(geo, anims[f"animation.{NAME}.{name}"])
        print(f"  hands in {name}: least clearance {worst:.1f} units at {at:.2f} s")
        if name == "chest" and worst < 2.0:
            raise SystemExit(f"the fists come within {worst:.1f} units of each other in the chest beat (at {at:.2f} s)")


def animations(source):
    """The source's idle, walk and attack, renamed, and ours."""
    A = {}
    for name in ("idle", "walk", "attack"):
        A[f"animation.{NAME}.{name}"] = source["animations"][f"animation.{SOURCE_NAME}.{name}"]
    A[f"animation.{NAME}.chase"] = chase_from(A[f"animation.{NAME}.walk"])
    A[f"animation.{NAME}.stunned"] = stunned()
    A[f"animation.{NAME}.death"] = death()
    A[f"animation.{NAME}.punch"] = punch()
    A[f"animation.{NAME}.point"] = point()
    A[f"animation.{NAME}.chest"] = chest()
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


def write_preview(geo, anims):
    poses = [("idle", "idle", 0.0), ("walk", "walk", 0.35), ("chase", "chase", 0.2), ("attack: raised", "attack", 0.4),
             ("attack: blow", "attack", 0.62), ("stunned", "stunned", 0.15), ("death: falling", "death", 1.9), ("death: on the floor", "death", 6.0),
             ("punch: draw back", "punch", 0.6), ("punch: blow", "punch", 1.0), ("point", "point", 1.5), ("chest: arms up", "chest", 0.125), ("chest: blow", "chest", 0.4), ("chest: arms up", "chest", 0.675), ("chest: blow", "chest", 0.95)]
    scale, cell_w, cell_h = 5, 190, 260
    sheet = Image.new("RGB", (cell_w * len(poses), cell_h * 2 + 40), (24, 25, 28))
    d = ImageDraw.Draw(sheet)
    for i, (label, name, t) in enumerate(poses):
        cubes = vc.posed_cubes(geo, anims[f"animation.{NAME}.{name}"], t)
        for row, view in enumerate(("front", "side")):
            ox, oy = cell_w * i + cell_w // 2, 30 + cell_h * row + 220
            d.line((ox - 110, oy, ox + 110, oy), fill=(90, 90, 90))
            d.line((ox - 110, oy - 40 * scale, ox + 110, oy - 40 * scale), fill=(50, 70, 50))     # 2.5 blocks up
            vc.draw_view(d, cubes, ox, oy, view, scale)
        d.text((cell_w * i + 8, 8), label, fill=(210, 210, 210))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / f"{NAME}_preview.png")


def main():
    geo_file = json.loads((SOURCE / "geo" / f"{SOURCE_NAME}.geo.json").read_text(encoding="utf-8"))
    source_anims = json.loads((SOURCE / "animations" / f"{SOURCE_NAME}.animation.json").read_text(encoding="utf-8"))
    geo = geo_file["minecraft:geometry"][0]
    geo["description"]["identifier"] = f"geometry.{NAME}"
    anims = animations(source_anims)

    vc.write_json(ASSETS / "geo" / "entity" / f"{NAME}.geo.json", geo_file)
    vc.write_json(ASSETS / "animations" / "entity" / f"{NAME}.animation.json", anims)
    textures = ASSETS / "textures" / "entity"
    textures.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(SOURCE / "textures" / "entity" / f"{SOURCE_NAME}.png", textures / f"{NAME}.png")
    shutil.copyfile(SOURCE / "textures" / "entity" / f"{SOURCE_NAME}_glowmask.png", textures / f"{NAME}_glowmask.png")
    check_hands(geo, anims["animations"])
    write_preview(geo, anims["animations"])
    langfiles.update("chapter2", "en_us", {"entity.every_tunnel.observant_guard": "Observant Guard"})
    langfiles.update("chapter2", "es_es", {"entity.every_tunnel.observant_guard": "Guardia observador"})
    cubes = sum(len(b.get("cubes", [])) for b in geo["bones"])
    print(f"Observant Guard: {len(geo['bones'])} bones, {cubes} cubes, {len(anims['animations'])} animations "
          f"({', '.join(a.rsplit('.', 1)[1] for a in anims['animations'])})")


if __name__ == "__main__":
    sys.exit(main())

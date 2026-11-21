"""
Chapter 2 Level 2's vibration creature: installs the Stone Maw model for GeckoLib and writes its animations.

Run after changing anything here or in the source (from the repository root):   python3 tools/entity/chapter2/vibration_creature.py

The model is the user's Stone Maw (tools/entity/chapter2/source/stone_maw/: geo, 256 x 256 texture, its idle, walk and attack
animations, the editable Blockbench file and its LEEME.md). It is the source: change it in Blockbench and export it there,
never edit what this tool writes. This tool writes into src/main/resources/assets/every_tunnel/ (committed with the mod):
  geo/entity/vibration_creature.geo.json                 the source geo, renamed geometry.vibration_creature
  animations/entity/vibration_creature.animation.json    idle and walk from the source; orient, stalk, charge, strike,
                                                         recover, listen, limp, dying (to the kneel) and crushed written here on its bones
  textures/entity/vibration_creature.png                 the source texture
  and the entity's name in Chapter 2's lang part (tools/lang/src/chapter2); no spawn egg: it is never summoned
and tools/entity/common/output/vibration_creature_preview.png (git-ignored): front and side views of the key poses.

The model: 3.5 blocks tall (56 units), facing north (-z, the teeth in front), bones root > pelvis > body > head > jaw, arms
(arm > forearm > hand > fingers 1-4 and thumb) on the body, legs (leg > shin > foot) on the pelvis. Rotations are degrees
[x, y, z] as in the source: a negative x swings a hanging limb forwards (the attack raises the arms at -83), a positive x
leans an upright part forwards and opens the jaw. The strike's slam lands at 0.9 s, the brain's windup (level.json
creature.windup_seconds); the orient's jaw snaps shut at 0.95 s, the end of its 1 s.
"""

import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

import sys as _sys
from pathlib import Path as _Path
_sys.path.insert(0, str(_Path(__file__).resolve().parents[2]))
import toolpaths  # noqa: E402  (where each area keeps its common/chapter folders)
toolpaths.use('lang')
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "lang" / "common"))
import langfiles  # noqa: E402

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
SOURCE = Path(__file__).resolve().parent / "source" / "stone_maw"
OUT = Path(__file__).resolve().parent / "output"
NAME = "vibration_creature"


# =====================================================================================
# Animations

def keys(*frames):
    """[(time, [x, y, z]), ...] -> Bedrock keyframes."""
    return {f"{t:.4g}": [round(c, 2) for c in v] for t, v in frames}


def swing(length, amplitude, phase=0.0, offset=(0, 0, 0), axis=0, steps=8, cycles=1):
    """A sine swing over one loop (cycles times), sampled into keyframes."""
    frames = []
    for i in range(steps + 1):
        v = list(offset)
        v[axis] += amplitude * math.sin(2 * math.pi * cycles * i / steps + phase)
        frames.append((length * i / steps, v))
    return keys(*frames)


def still(v):
    return keys((0, v))


def legs(length, amplitude, bend, crouch=0.0):
    """Both legs in a stride: thighs swinging round crouch, shins bent by bend and folding as the leg comes forward."""
    return {
        "left_leg": {"rotation": swing(length, amplitude, offset=(-crouch, 0, 0))},
        "right_leg": {"rotation": swing(length, amplitude, math.pi, offset=(-crouch, 0, 0))},
        "left_shin": {"rotation": swing(length, amplitude * 0.6, -math.pi / 2, offset=(bend, 0, 0))},
        "right_shin": {"rotation": swing(length, amplitude * 0.6, math.pi / 2, offset=(bend, 0, 0))},
    }


def multi(length, offset, comps, steps=8):
    """Sine swings on several axes at once over one loop: comps = {axis: (amplitude, phase, cycles)} round the offset."""
    frames = []
    for i in range(steps * 2 + 1):
        v = list(offset)
        for axis, (amp, phase, cycles) in comps.items():
            v[axis] += amp * math.sin(2 * math.pi * cycles * i / (steps * 2) + phase)
        frames.append((length * i / (steps * 2), v))
    return keys(*frames)


# The crushed pose (the first slab slammed it flat): solved from the geometry like the kneel.
CRUSH = {"pelvis": [0, -12.0, 0], "body": [86, 0, 0], "head": [10, 0, 0], "leg": [86, 0, 0], "shin": [4, 0, 0],
         "left_arm": [-10, 0, -90], "right_arm": [-10, 0, 90]}


def our_animations():
    n = NAME
    A = {}
    A[f"animation.{n}.orient"] = {"loop": False, "animation_length": 1.0, "bones": {
        # The 1 s warning: the blind head sweeps to one side, then the other, the jaw opening; at 0.95 s the teeth snap shut.
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-4, -25, 0]), (0.6, [-4, 20, 0]), (0.85, [-6, 0, 0]), (1.0, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.5, [10, 0, 0]), (0.88, [22, 0, 0]), (0.95, [0, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.85, [5, 0, 0]), (1.0, [3, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.85, [-6, 0, -4]), (1.0, [-4, 0, -3]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.85, [-6, 0, 4]), (1.0, [-4, 0, 3]))},
    }}
    stalk = 1.6
    A[f"animation.{n}.stalk"] = {"loop": True, "animation_length": stalk, "bones": {
        # Creeping after a soft step: bent low, the head forward, the long arms reaching ahead, slow careful strides.
        "pelvis": {"position": swing(stalk / 2, 0.3, offset=(0, -1.6, 0), axis=1)},
        "body": {"rotation": swing(stalk, 2, axis=2, offset=(20, 0, 0))},
        "head": {"rotation": swing(stalk, 3, axis=1, offset=(-14, 0, 0))},
        "left_arm": {"rotation": swing(stalk, 8, math.pi, offset=(-22, 0, -3))},
        "right_arm": {"rotation": swing(stalk, 8, offset=(-22, 0, 3))},
        "left_forearm": {"rotation": still([-10, 0, 0])},
        "right_forearm": {"rotation": still([-10, 0, 0])},
        **legs(stalk, 12, 22, crouch=14),
    }}
    charge = 0.7
    A[f"animation.{n}.charge"] = {"loop": True, "animation_length": charge, "bones": {
        # Running at a point: leaned far forwards, jaw open, long strides, the arms swinging wide.
        "pelvis": {"position": swing(charge / 2, 0.8, offset=(0, -1.0, 0), axis=1)},
        "body": {"rotation": still([25, 0, 0])},
        "head": {"rotation": still([-18, 0, 0])},
        "jaw": {"rotation": swing(charge, 3, offset=(14, 0, 0))},
        "left_arm": {"rotation": swing(charge, 40, math.pi, offset=(-25, 0, -6))},
        "right_arm": {"rotation": swing(charge, 40, offset=(-25, 0, 6))},
        "left_forearm": {"rotation": still([-20, 0, 0])},
        "right_forearm": {"rotation": still([-20, 0, 0])},
        **legs(charge, 30, 18, crouch=8),
    }}
    A[f"animation.{n}.strike"] = {"loop": False, "animation_length": 1.3, "bones": {
        # The 0.9 s tell: both fists rise over the head, the body rearing back, the jaw wide; at 0.9-1.0 s the slam; then it
        # settles with its fists on the floor.
        "pelvis": {"position": keys((0, [0, 0, 0]), (0.9, [0, 0.5, 0]), (1.0, [0, -2.0, 0]), (1.3, [0, -1.5, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [-6, 0, 0]), (0.9, [-12, 0, 0]), (1.0, [22, 0, 0]), (1.3, [16, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.9, [8, 0, 0]), (1.0, [-16, 0, 0]), (1.3, [-10, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.9, [26, 0, 0]), (1.0, [8, 0, 0]), (1.3, [4, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-110, 0, -8]), (0.9, [-150, 0, -6]), (1.0, [-35, 0, 0]), (1.3, [-30, 0, 0]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-110, 0, 8]), (0.9, [-150, 0, 6]), (1.0, [-35, 0, 0]), (1.3, [-30, 0, 0]))},
        "left_forearm": {"rotation": keys((0, [0, 0, 0]), (0.9, [-30, 0, 0]), (1.0, [0, 0, 0]))},
        "right_forearm": {"rotation": keys((0, [0, 0, 0]), (0.9, [-30, 0, 0]), (1.0, [0, 0, 0]))},
        "left_leg": {"rotation": keys((0, [0, 0, 0]), (0.9, [4, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [-15, 0, 0]))},
        "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.9, [4, 0, 0]), (1.0, [-20, 0, 0]), (1.3, [-15, 0, 0]))},
        "left_shin": {"rotation": keys((0, [0, 0, 0]), (1.0, [30, 0, 0]), (1.3, [25, 0, 0]))},
        "right_shin": {"rotation": keys((0, [0, 0, 0]), (1.0, [30, 0, 0]), (1.3, [25, 0, 0]))},
    }}
    A[f"animation.{n}.recover"] = {"loop": True, "animation_length": 2.0, "bones": {
        # After the blow: slumped over its fists on the floor, the shoulders rising and falling slowly.
        "pelvis": {"position": still([0, -1.5, 0])},
        "body": {"rotation": swing(2.0, 2, offset=(18, 0, 0))},
        "head": {"rotation": still([-8, 0, 0])},
        "left_arm": {"rotation": swing(2.0, 2, offset=(-28, 0, 0))},
        "right_arm": {"rotation": swing(2.0, 2, offset=(-28, 0, 0))},
        "left_leg": {"rotation": still([-15, 0, 0])},
        "right_leg": {"rotation": still([-15, 0, 0])},
        "left_shin": {"rotation": still([25, 0, 0])},
        "right_shin": {"rotation": still([25, 0, 0])},
    }}
    A[f"animation.{n}.listen"] = {"loop": True, "animation_length": 2.0, "bones": {
        # Listening to the floor: still, head down and turned to one side, the jaw trembling.
        "body": {"rotation": still([8, 0, 0])},
        "head": {"rotation": keys((0, [10, 18, 10]), (1.0, [12, 22, 12]), (2.0, [10, 18, 10]))},
        "jaw": {"rotation": swing(2.0, 1.5, offset=(5, 0, 0), steps=16, cycles=8)},
        "left_arm": {"rotation": still([-6, 0, -2])},
        "right_arm": {"rotation": still([-6, 0, 2])},
    }}
    limp_len = 1.6
    A[f"animation.{n}.limp"] = {"loop": True, "animation_length": limp_len, "bones": {
        # Phase 6.2, the wounded walk to the centre: the right leg is stiff and dragged, the body leans forward and to that side, the right
        # arm hangs low with its knuckles near the floor, the head lolls with each step. The numbers come from the geometry (the
        # knuckles brush the floor at their lowest, the supporting foot stays on it), not by eye.
        "pelvis": {"position": multi(limp_len, (0, 1.2, 0), {1: (0.9, 0.0, 2)}),
                   "rotation": multi(limp_len, (0, 0, -4), {2: (3, 0.0, 1), 1: (3, 0.0, 1)})},
        "body": {"rotation": multi(limp_len, (22, 0, -7), {0: (2.5, 0.0, 2), 1: (5, math.pi, 1), 2: (2, 0.0, 1)})},
        "head": {"rotation": multi(limp_len, (-4, 0, 0), {0: (4, math.pi / 2, 2), 2: (7, math.pi / 2, 1), 1: (4, 0.0, 1)})},
        "jaw": {"rotation": still([8, 0, 0])},
        "left_arm": {"rotation": multi(limp_len, (-30, 0, -4), {0: (10, math.pi, 1)})},
        "left_forearm": {"rotation": multi(limp_len, (-34, 0, 0), {0: (8, math.pi, 1)})},
        "right_arm": {"rotation": multi(limp_len, (-42, 0, 3), {0: (5, 0.0, 1)})},
        "right_forearm": {"rotation": multi(limp_len, (-8, 0, 0), {0: (3, 0.0, 1)})},
        "left_leg": {"rotation": multi(limp_len, (-8, 0, 0), {0: (26, 0.0, 1)})},
        "left_shin": {"rotation": multi(limp_len, (14, 0, 0), {0: (16, -math.pi / 2, 1)})},
        "right_leg": {"rotation": multi(limp_len, (-6, 0, 0), {0: (9, math.pi, 1)})},
        "right_shin": {"rotation": multi(limp_len, (9, 0, 0), {0: (3, math.pi / 2, 1)})},
        "right_foot": {"rotation": multi(limp_len, (0, 0, 0), {0: (6, math.pi, 1)})},
    }}
    # The kneel (also the end of the dying walk): numbers solved from the geometry so the shins lie on the floor, both hands lie flat on it
    # (palm and fingers at y = 0) ahead of the knees, and the head hangs over them looking straight down.
    kneel = {"pelvis": [0, -3.2, 0], "body": [50, 0, 0], "head": [40, 0, 0], "leg": [-5, 0, 0], "shin": [90, 0, 0],
             "arm": [-35, 0, 0], "forearm": [-50, 0, 0], "hand": [-40, 0, 0]}
    dying = 4.5
    A[f"animation.{n}.dying"] = {"loop": "hold_on_last_frame", "animation_length": dying, "bones": {
        # Phase 6.2: after the last hit and the limp to the centre it drops to its knees (the injured right one first), the body folds
        # forward, both hands come down flat on the stone in front of the knees and the head lowers till it looks straight down. Then,
        # still, it listens one last time: a faint jaw tremble and one slow breath-like rise of the body (held at the end).
        "pelvis": {"position": keys((0, [0, 0, 0]), (0.8, [0, -1.2, 0]), (1.6, [0, -2.6, 0]), (2.4, kneel["pelvis"]),
                                    (3.4, [0, -3.0, 0]), (4.5, kneel["pelvis"]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.8, [10, 0, 0]), (1.6, [30, 0, 0]), (2.4, kneel["body"]),
                                  (3.4, [47.5, 0, 0]), (4.5, kneel["body"]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.9, [8, 4, 0]), (1.7, [22, 0, 0]), (2.4, kneel["head"]),
                                  (3.4, [42, 0, 0]), (4.5, kneel["head"]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (1.0, [10, 0, 0]), (2.4, [4, 0, 0]), (2.55, [7, 0, 0]), (2.7, [3, 0, 0]),
                                 (2.85, [7, 0, 0]), (3.0, [3, 0, 0]), (3.15, [7, 0, 0]), (3.3, [3, 0, 0]), (4.5, [4, 0, 0]))},
        "left_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-24, 0, -3]), (1.6, [-44, 0, -3]), (2.0, [-40, 0, -2]), (2.4, kneel["arm"]),
                                      (3.4, [-34, 0, 0]), (4.5, kneel["arm"]))},
        "right_arm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-24, 0, 3]), (1.6, [-44, 0, 3]), (2.0, [-40, 0, 2]), (2.4, kneel["arm"]),
                                       (3.4, [-34, 0, 0]), (4.5, kneel["arm"]))},
        "left_forearm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-24, 0, 0]), (1.6, [-56, 0, 0]), (2.0, [-52, 0, 0]), (2.4, kneel["forearm"]), (4.5, kneel["forearm"]))},
        "right_forearm": {"rotation": keys((0, [0, 0, 0]), (1.0, [-24, 0, 0]), (1.6, [-56, 0, 0]), (2.0, [-52, 0, 0]), (2.4, kneel["forearm"]), (4.5, kneel["forearm"]))},
        "left_hand": {"rotation": keys((0, [0, 0, 0]), (1.6, [-14, 0, 0]), (2.0, [-34, 0, 0]), (2.4, kneel["hand"]), (4.5, kneel["hand"]))},
        "right_hand": {"rotation": keys((0, [0, 0, 0]), (1.6, [-14, 0, 0]), (2.0, [-34, 0, 0]), (2.4, kneel["hand"]), (4.5, kneel["hand"]))},
        "left_leg": {"rotation": keys((0, [0, 0, 0]), (1.0, [-22, 0, 0]), (1.9, [-18, 0, 0]), (2.4, kneel["leg"]), (4.5, kneel["leg"]))},
        "left_shin": {"rotation": keys((0, [0, 0, 0]), (1.0, [45, 0, 0]), (1.9, [95, 0, 0]), (2.4, kneel["shin"]), (4.5, kneel["shin"]))},
        "right_leg": {"rotation": keys((0, [0, 0, 0]), (0.7, [-18, 0, 0]), (1.5, [-26, 0, 0]), (2.4, kneel["leg"]), (4.5, kneel["leg"]))},
        "right_shin": {"rotation": keys((0, [0, 0, 0]), (0.7, [40, 0, 0]), (1.5, [96, 0, 0]), (2.4, kneel["shin"]), (4.5, kneel["shin"]))},
    }}
    A[f"animation.{n}.crushed"] = {"loop": "hold_on_last_frame", "animation_length": 0.3, "bones": {
        # Phase 6.2: the first slab slams it flat: it starts from the kneel and in 0.12 s lies prone, chest and head on the floor, the legs
        # stretched out behind and the arms thrown out flat to the sides. Seen only a few ticks, before the burst of dust hides it.
        "pelvis": {"position": keys((0, kneel["pelvis"]), (0.12, CRUSH["pelvis"]))},
        "body": {"rotation": keys((0, kneel["body"]), (0.12, CRUSH["body"]))},
        "head": {"rotation": keys((0, kneel["head"]), (0.12, CRUSH["head"]))},
        "jaw": {"rotation": keys((0, [4, 0, 0]), (0.05, [24, 0, 0]), (0.12, [12, 0, 0]))},
        "left_arm": {"rotation": keys((0, kneel["arm"]), (0.12, CRUSH["left_arm"]))},
        "right_arm": {"rotation": keys((0, kneel["arm"]), (0.12, CRUSH["right_arm"]))},
        "left_forearm": {"rotation": keys((0, kneel["forearm"]), (0.12, [0, 0, 0]))},
        "right_forearm": {"rotation": keys((0, kneel["forearm"]), (0.12, [0, 0, 0]))},
        "left_hand": {"rotation": keys((0, kneel["hand"]), (0.12, [0, 0, 0]))},
        "right_hand": {"rotation": keys((0, kneel["hand"]), (0.12, [0, 0, 0]))},
        "left_leg": {"rotation": keys((0, kneel["leg"]), (0.12, CRUSH["leg"]))},
        "right_leg": {"rotation": keys((0, kneel["leg"]), (0.12, CRUSH["leg"]))},
        "left_shin": {"rotation": keys((0, kneel["shin"]), (0.12, CRUSH["shin"]))},
        "right_shin": {"rotation": keys((0, kneel["shin"]), (0.12, CRUSH["shin"]))},
    }}
    return A


def animations(source):
    """The source's idle and walk, renamed, and ours."""
    A = {}
    for name in ("idle", "walk"):
        A[f"animation.{NAME}.{name}"] = source["animations"][f"animation.stone_maw.{name}"]
    A.update(our_animations())
    return {"format_version": "1.8.0", "animations": A, "geckolib_format_version": 2}


# =====================================================================================
# Preview

def sample(channel, t):
    """A bone channel ({time: [x, y, z]}) at time t, linear between keyframes, held at the ends."""
    frames = sorted((float(k), v) for k, v in channel.items())
    if t <= frames[0][0]:
        return frames[0][1]
    for (t0, v0), (t1, v1) in zip(frames, frames[1:]):
        if t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 1.0
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return frames[-1][1]


def rotate(p, pivot, rot):
    """Rotates point p around pivot by Bedrock-style degrees (x, then y, then z)."""
    x, y, z = (p[i] - pivot[i] for i in range(3))
    rx, ry, rz = (math.radians(a) for a in rot)
    y, z = y * math.cos(rx) + z * math.sin(rx), -y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    # GeckoLib turns a bone about z the other way from a plain right-handed rotation of the Blockbench model (it mirrors x when it bakes
    # the model, and turns z by the animation's value as it is): a positive z swings the arm at +x (the "left" one) towards the body.
    # x and y match Blockbench's. Checked against GeckoLib 4.8.4's BakedModelFactory and RenderUtils.rotateMatrixAroundBone.
    x, y = x * math.cos(rz) + y * math.sin(rz), -x * math.sin(rz) + y * math.cos(rz)
    return [x + pivot[0], y + pivot[1], z + pivot[2]]


def posed_cubes(geo, anim, t):
    """Every cube's 8 corners in model space with the animation at time t applied."""
    bones = {b["name"]: b for b in geo["bones"]}
    bones_anim = anim["bones"] if anim else {}

    def pose(name):
        a = bones_anim.get(name, {})
        rot = sample(a["rotation"], t) if "rotation" in a else [0, 0, 0]
        pos = sample(a["position"], t) if "position" in a else [0, 0, 0]
        return rot, pos

    out = []
    for b in geo["bones"]:
        for c in b.get("cubes", []):
            o, s = c["origin"], c["size"]
            pts = [[o[0] + dx * s[0], o[1] + dy * s[1], o[2] + dz * s[2]] for dx in (0, 1) for dy in (0, 1) for dz in (0, 1)]
            if c.get("rotation"):
                pts = [rotate(q, c.get("pivot", [0, 0, 0]), c["rotation"]) for q in pts]
            bone = b
            while bone:
                rot, pos = pose(bone["name"])
                pts = [[v + d for v, d in zip(rotate(q, bone["pivot"], rot), pos)] for q in pts]
                bone = bones.get(bone.get("parent"))
            out.append(pts)
    return out


# The 6 faces of a cube by corner index (dx * 4 + dy * 2 + dz).
FACES = [(0, 1, 3, 2), (4, 6, 7, 5), (0, 4, 5, 1), (2, 3, 7, 6), (0, 2, 6, 4), (1, 5, 7, 3)]


def draw_view(d, cubes, ox, oy, view, scale):
    """Shaded faces, far ones first (front: looking at -z from the north; side: from the east, front to the right)."""
    def project(q):
        return (q[0], q[1], -q[2]) if view == "front" else (-q[2], q[1], -q[0])

    polys = []
    for pts in cubes:
        p = [project(q) for q in pts]
        for f in FACES:
            a, b, c = (p[i] for i in f[:3])
            u = [b[i] - a[i] for i in range(3)]
            v = [c[i] - a[i] for i in range(3)]
            nz = u[0] * v[1] - u[1] * v[0]
            n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], nz]
            ln = math.sqrt(sum(x * x for x in n)) or 1.0
            light = 0.35 + 0.65 * max(0.0, (0.4 * n[0] + 0.6 * n[1] - 0.7 * n[2]) / ln)
            depth = sum(p[i][2] for i in f) / 4
            polys.append((depth, [(ox + p[i][0] * scale, oy - p[i][1] * scale) for i in f], int(70 + 120 * light)))
    for depth, poly, g in sorted(polys, key=lambda x: -x[0]):
        d.polygon(poly, fill=(g, g, g - 4), outline=(40, 40, 44))


def write_preview(geo, anims):
    poses = [("idle", "idle", 0.0), ("orient: click", "orient", 0.94), ("stalk", "stalk", 0.4),
             ("charge", "charge", 0.2), ("strike: windup top", "strike", 0.9), ("strike: impact", "strike", 1.0),
             ("recover", "recover", 0.0), ("listen", "listen", 0.5),
             ("limp: step", "limp", 0.0), ("limp: drag", "limp", 0.8),
             ("dying: mid", "dying", 1.5), ("dying: held (kneel)", "dying", 3.0), ("crushed: held", "crushed", 0.3)]
    scale, cell_w, cell_h = 4, 300, 300
    sheet = Image.new("RGB", (cell_w * len(poses), cell_h * 2 + 40), (24, 25, 28))
    d = ImageDraw.Draw(sheet)
    for i, (label, name, t) in enumerate(poses):
        cubes = posed_cubes(geo, anims[f"animation.{NAME}.{name}"], t)
        for row, view in enumerate(("front", "side")):
            ox, oy = cell_w * i + cell_w // 2, 30 + cell_h * row + 260
            d.line((ox - 130, oy, ox + 130, oy), fill=(150, 150, 150), width=2)     # the floor
            d.line((ox - 130, oy - 56 * scale, ox + 130, oy - 56 * scale), fill=(50, 70, 50))     # 3.5 blocks up
            draw_view(d, cubes, ox, oy, view, scale)
        d.text((cell_w * i + 8, 8), label, fill=(210, 210, 210))
    OUT.mkdir(exist_ok=True)
    sheet.save(OUT / f"{NAME}_preview.png")


# =====================================================================================

def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def main():
    geo_file = json.loads((SOURCE / "geo" / "stone_maw.geo.json").read_text(encoding="utf-8"))
    source_anims = json.loads((SOURCE / "animations" / "stone_maw.animation.json").read_text(encoding="utf-8"))
    texture = Image.open(SOURCE / "textures" / "stone_maw.png")
    geo = geo_file["minecraft:geometry"][0]
    geo["description"]["identifier"] = f"geometry.{NAME}"
    anims = animations(source_anims)

    write_json(ASSETS / "geo" / "entity" / f"{NAME}.geo.json", geo_file)
    write_json(ASSETS / "animations" / "entity" / f"{NAME}.animation.json", anims)
    (ASSETS / "textures" / "entity").mkdir(parents=True, exist_ok=True)
    texture.save(ASSETS / "textures" / "entity" / f"{NAME}.png")
    write_preview(geo, anims["animations"])
    langfiles.update("chapter2", "en_us", {"entity.every_tunnel.vibration_creature": "Vibration Creature"})
    langfiles.update("chapter2", "es_es", {"entity.every_tunnel.vibration_creature": "Criatura de vibraciones"})
    cubes = sum(len(b.get("cubes", [])) for b in geo["bones"])
    print(f"Vibration creature (Stone Maw): {len(geo['bones'])} bones, {cubes} cubes, {len(anims['animations'])} animations "
          f"({', '.join(a.rsplit('.', 1)[1] for a in anims['animations'])})")


if __name__ == "__main__":
    sys.exit(main())

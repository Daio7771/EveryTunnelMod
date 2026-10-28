"""
The view from inside a Level 3 locker (client/hud/LockerView.kt): the inside of the door a
hand's width from the eyes, dark steel, with the vent's louvres across it. Through the slits the
world shows; they are soft at the edge, as something this close is out of focus. The louvres'
lips catch a little light from outside.

Run after changing anything here (from the repository root):   python3 tools/gui/locker_view.py

Writes (committed) src/main/resources/assets/every_tunnel/textures/gui/locker_view.png,
WIDTH x HEIGHT. The overlay stretches it over the whole screen, so the slits run across it
whatever the window's shape. A preview over a test picture (git-ignored) goes to
tools/gui/output/locker_view_preview.png.
"""

from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui"
OUT = Path(__file__).resolve().parent / "output"

WIDTH, HEIGHT = 512, 288                 # 16:9; stretched to the screen

# The louvres, as shares of the height (y) and width (x): how many, the middle one's centre,
# each slit's height, the distance between slit centres, and how far across they run.
SLITS = 6
SLITS_CENTRE = 0.47
SLIT_HEIGHT = 0.038
SLIT_PITCH = 0.085
SLITS_X = (0.13, 0.87)
SOFTNESS = 0.012                         # out of focus: the edge's blur, share of the height

STEEL = np.array([16, 18, 23], dtype=float)          # the door's inside, in the dark
STEEL_LIT = np.array([46, 50, 58], dtype=float)      # the same, round the slits where light comes in
LIP = np.array([92, 98, 108], dtype=float)           # a louvre's bent lip under each slit
GLOW_REACH = 0.07                                    # how far the light round a slit reaches


def smoothstep(edge0, edge1, x):
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def render():
    ys, xs = np.mgrid[0:HEIGHT, 0:WIDTH].astype(float)
    y = (ys + 0.5) / HEIGHT                       # 0 top .. 1 bottom
    x = (xs + 0.5) / WIDTH
    aspect = WIDTH / HEIGHT
    half_h = SLIT_HEIGHT / 2
    x0, x1 = SLITS_X
    open_ = np.zeros_like(y)                      # 1 where the world shows
    lip = np.zeros_like(y)
    near = np.full_like(y, 10.0)                  # distance to the nearest slit, in heights
    first = SLITS_CENTRE - SLIT_PITCH * (SLITS - 1) / 2
    for i in range(SLITS):
        cy = first + i * SLIT_PITCH
        # A slit is a rounded rectangle (stadium): distance from its centre line, in heights.
        dx = np.maximum(np.maximum(x0 + half_h / aspect - x, x - (x1 - half_h / aspect)), 0.0) * aspect
        dy = y - cy
        d = np.sqrt(dx * dx + dy * dy) - half_h
        open_ = np.maximum(open_, 1.0 - smoothstep(-SOFTNESS, SOFTNESS, d))
        near = np.minimum(near, np.maximum(d, 0.0))
        # The lip: a thin lit band just under the slit, fading towards the slit's ends.
        below = dy - half_h
        band = smoothstep(-0.004, 0.004, below) * (1.0 - smoothstep(0.004, 0.016, below))
        lip = np.maximum(lip, band * (1.0 - smoothstep(-0.03, 0.0, d - 0.012)) * np.clip(1.0 - dx * 6, 0.0, 1.0))
    glow = np.exp(-near / GLOW_REACH)
    colour = STEEL[None, None, :] + (STEEL_LIT - STEEL)[None, None, :] * glow[..., None]
    colour = colour + (LIP - colour) * (lip * 0.8)[..., None]
    # Darker towards the screen's edges, where the door is in the locker's shadow.
    edge = np.hypot((x - 0.5) * 1.6, (y - SLITS_CENTRE) * 1.6)
    colour *= (1.0 - 0.55 * smoothstep(0.35, 1.0, edge))[..., None]
    alpha = 1.0 - open_
    rgba = np.dstack([np.clip(colour, 0, 255), alpha * 255.0]).round().astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def preview(image):
    """The view over a test picture: a grey room with a bright doorway."""
    ys, xs = np.mgrid[0:HEIGHT, 0:WIDTH]
    room = np.full((HEIGHT, WIDTH, 3), 120, dtype=np.uint8)
    room[ys > HEIGHT * 0.6] = (90, 96, 110)
    door = (abs(xs - WIDTH * 0.6) < WIDTH * 0.05) & (ys > HEIGHT * 0.25)
    room[door] = (230, 232, 220)
    base = Image.fromarray(room, "RGB").convert("RGBA")
    base.alpha_composite(image)
    return base.resize((WIDTH * 2, HEIGHT * 2), Image.NEAREST)


def main():
    image = render()
    GUI.mkdir(parents=True, exist_ok=True)
    image.save(GUI / "locker_view.png")
    OUT.mkdir(exist_ok=True)
    preview(image).save(OUT / "locker_view_preview.png")
    print(f"locker_view.png {WIDTH}x{HEIGHT}; preview in {OUT / 'locker_view_preview.png'}")


if __name__ == "__main__":
    main()

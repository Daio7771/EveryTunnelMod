"""
The flashlight's on-screen spot without shaders (client/hud/FlashlightOverlay.kt). Block
light alone draws the beam as steps of light; drawn over the world, these two textures make it
read as a round flashlight spot: the screen dark around it and a little warmer in its middle.

Run after changing anything here (from the repository root):   python3 tools/gui/flashlight_spot.py

Writes (256x256, committed) into src/main/resources/assets/every_tunnel/textures/gui/:
- flashlight_spot.png: black, clear in the middle, darker towards the edge (the dark around
  the beam). The overlay draws it centred and fills beyond it with its edge darkness.
- flashlight_glow.png: white, faint in the middle, clear at the edge (the warm hot spot; the
  overlay tints it).
Previews (git-ignored) go to tools/gui/output/.
"""

from pathlib import Path
import math

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
GUI = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "gui"
OUT = Path(__file__).resolve().parent / "output"

SIZE = 256
# Spot: radius (share of the half size) where the dark starts, where it is full, and how dark.
CLEAR_RADIUS = 0.30
DARK_RADIUS = 0.95
EDGE_ALPHA = 0.90          # must match FlashlightOverlay.EDGE_ALPHA
# Glow: how bright its middle is and where it has faded out.
GLOW_ALPHA = 0.22
GLOW_RADIUS = 0.45


def smoothstep(edge0, edge1, x):
    t = min(1.0, max(0.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


def radial(alpha_at):
    image = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = image.load()
    half = SIZE / 2
    for y in range(SIZE):
        for x in range(SIZE):
            r = math.hypot(x + 0.5 - half, y + 0.5 - half) / half
            px[x, y] = alpha_at(r)
    return image


def main():
    spot = radial(lambda r: (0, 0, 0, round(255 * EDGE_ALPHA * smoothstep(CLEAR_RADIUS, DARK_RADIUS, r))))
    glow = radial(lambda r: (255, 255, 255, round(255 * GLOW_ALPHA * (1 - smoothstep(0.0, GLOW_RADIUS, r)))))
    GUI.mkdir(parents=True, exist_ok=True)
    spot.save(GUI / "flashlight_spot.png")
    glow.save(GUI / "flashlight_glow.png")

    OUT.mkdir(exist_ok=True)
    preview = Image.new("RGBA", (SIZE * 2, SIZE), (150, 150, 150, 255))
    tile = Image.new("RGBA", (SIZE, SIZE), (150, 150, 150, 255))
    tile.alpha_composite(spot)
    preview.paste(tile, (0, 0))
    tile = Image.new("RGBA", (SIZE, SIZE), (40, 40, 40, 255))
    tile.alpha_composite(glow)
    preview.paste(tile, (SIZE, 0))
    preview.save(OUT / "flashlight_spot_preview.png")
    print("wrote textures/gui/flashlight_spot.png, flashlight_glow.png and a preview")


if __name__ == "__main__":
    main()

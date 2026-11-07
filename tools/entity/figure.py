"""
The figure seen far off from Level 5's Lobby: nobody can tell who it is, a person's shape and nothing
more, as if against the light. A 64 x 64 texture in the player's layout (the renderer uses the player
model), all but black, a little less so at the edges, written to
assets/every_tunnel/textures/entity/lab_figure.png.

    python3 tools/entity/figure.py
"""

import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from film_walker import BODY, HEAD, LEFT_ARM, LEFT_LEG, RIGHT_ARM, RIGHT_LEG, faces, fill  # noqa: E402

OUT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "every_tunnel" / "textures" / "entity" / "lab_figure.png"

SHADOW = (8, 8, 10)
RIM = (22, 22, 26)


def main():
    rng = random.Random("lab-figure-1")
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for part in (HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG):
        for name, rect in faces(part).items():
            fill(img, rng, rect, SHADOW, noise=2)
            x0, y0, w, h = rect
            if name in ("right", "left", "top"):         # the light from behind catches the sides a little
                for y in range(y0, y0 + h):
                    img.putpixel((x0, y), RIM + (255,))
                    img.putpixel((x0 + w - 1, y), RIM + (255,))
    img.save(OUT)
    print(f"wrote {OUT.relative_to(OUT.parents[5])}")


if __name__ == "__main__":
    sys.exit(main())

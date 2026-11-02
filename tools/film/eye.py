"""
What Level 4's Cinema shows during the blackout: one huge eye looking about, blinking now and then, and
INTRUDER DETECTED over it (the seats hide the bottom of the screen), in the film's own look (film.py). A loop of 288 x 128 at 12 frames a second,
for the screen (client.effects.FilmPlayer). Same frames every run (a fixed seed).

    python3 tools/film/eye.py

Writes assets/every_tunnel/film_eye/: frame_0001.png... and film.json, replacing what was there.
"""

import json
import math
import random
import shutil
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

from film import FPS, HEIGHT, WIDTH, OldFilm

SEED = 14                        # the player's number
FRAMES = 72                      # 6 seconds
BLINK_AT, BLINK_FRAMES = 48, 6
TEXT = "INTRUDER DETECTED"
OUT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "every_tunnel" / "film_eye"

FONT = {"I": "111010010010111", "N": "1000111001101011001110001", "T": "111010010010010", "R": "110101110101101",
        "U": "101101101101111", "D": "110101101101110", "E": "111100110100111", "C": "111100100100111", " ": "000000000000000"}


def text_width(text, scale):
    return (sum(len(FONT[ch]) // 5 + 1 for ch in text) - 1) * scale


def draw_text(draw, x, y, text, scale, shade):
    for ch in text:
        glyph = FONT[ch]
        width = len(glyph) // 5
        for i, bit in enumerate(glyph):
            if bit == "1":
                px, py = x + (i % width) * scale, y + (i // width) * scale
                draw.rectangle((px, py, px + scale - 1, py + scale - 1), fill=shade)
        x += (width + 1) * scale


def eyelid(frame):
    """How open the eye is, 0 to 1: shut in the middle of a blink."""
    t = frame - BLINK_AT
    if 0 <= t < BLINK_FRAMES:
        return abs(t - (BLINK_FRAMES - 1) / 2) / ((BLINK_FRAMES - 1) / 2)
    return 1.0


def picture(frame, gaze):
    img = Image.new("L", (WIDTH, HEIGHT), 12)
    draw = ImageDraw.Draw(img)
    cx, cy, half_w, half_h = WIDTH // 2, 68, 72, 30
    opened = eyelid(frame)
    top = [(cx + half_w * k / 40, cy - half_h * opened * math.cos(k / 40 * math.pi / 2)) for k in range(-40, 41)]
    bottom = [(x, 2 * cy - y) for x, y in reversed(top)]
    if opened > 0.05:
        mask = Image.new("L", (WIDTH, HEIGHT), 0)
        ImageDraw.Draw(mask).polygon(top + bottom, fill=255)
        eye = Image.new("L", (WIDTH, HEIGHT), 12)
        d = ImageDraw.Draw(eye)
        d.polygon(top + bottom, fill=205)
        ix, iy = cx + gaze[0], cy + gaze[1]
        d.ellipse((ix - 25, iy - 25, ix + 25, iy + 25), fill=62)
        d.ellipse((ix - 11, iy - 11, ix + 11, iy + 11), fill=4)
        d.ellipse((ix - 16, iy - 15, ix - 10, iy - 9), fill=225)
        img.paste(eye, (0, 0), mask)
    draw.line(top, fill=235, width=2)
    draw.line(bottom, fill=235, width=2)
    scale = 2
    draw_text(draw, (WIDTH - text_width(TEXT, scale)) // 2, 16, TEXT, scale, 225)
    return np.asarray(img, dtype=np.float32)


def main():
    rng = random.Random(SEED)
    treat = OldFilm()
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    # Where it looks: a hold, a quick move, a hold; back where it began, for the loop.
    holds = [(0, 0), (-18, 2), (14, -2), (0, 0)]
    for frame in range(FRAMES):
        at = frame / FRAMES * (len(holds) - 1)
        k = min(int(at), len(holds) - 2)
        move = min(1.0, (at - k) * 5)
        gaze = tuple(holds[k][i] + (holds[k + 1][i] - holds[k][i]) * move + rng.uniform(-0.5, 0.5) for i in (0, 1))
        out = treat.apply(picture(frame, gaze))
        Image.fromarray(out).save(OUT / f"frame_{frame + 1:04d}.png", optimize=True)
    (OUT / "film.json").write_text(json.dumps({"fps": FPS, "frames": FRAMES, "width": WIDTH, "height": HEIGHT}, indent=1) + "\n")


if __name__ == "__main__":
    main()

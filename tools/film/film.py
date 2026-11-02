"""
The film of Level 4's Cinema: a recording made in game by the user, turned into old film, frame by
frame, for the screen (client.effects.FilmPlayer). Black and white, its shadows lifted (the levels are
dark, and the figure is darker), with grain, a vignette, an uneven flicker and a few scratches; 288 x 128
at 12 frames a second, cropped to the screen's shape from the middle of the picture. Same input, same
frames (a fixed seed).

    python3 tools/film/film.py recording.mp4 --start 8 --end 21     needs ffmpeg; seconds to keep
    python3 tools/film/film.py --test                               a short test pattern, no recording needed

The film in the game: tools/film/recording/film_take_2.mp4 --start 8 --end 22.

Writes assets/every_tunnel/film/: frame_0001.png... and film.json, replacing what was there.
"""

import argparse
import json
import random
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

WIDTH, HEIGHT, FPS = 288, 128, 12
SEED = 94                        # the film's running time, as the projection log has it
GAMMA = 0.55                     # under 1 lifts the shadows
GRAIN = 6.5
OUT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "assets" / "every_tunnel" / "film"
TEST_SECONDS = 5


def read_video(path, start=None, end=None):
    """Grey frames from ffmpeg, cropped to the screen's shape, as arrays of HEIGHT x WIDTH."""
    crop = f"crop=in_w:in_w*{HEIGHT}/{WIDTH}"
    span = (["-ss", str(start)] if start is not None else []) + (["-to", str(end)] if end is not None else [])
    command = ["ffmpeg", "-v", "error", *span, "-i", str(path), "-an", "-vf",
               f"fps={FPS},{crop},scale={WIDTH}:{HEIGHT}:flags=lanczos,format=gray", "-f", "rawvideo", "-"]
    process = subprocess.Popen(command, stdout=subprocess.PIPE)
    size = WIDTH * HEIGHT
    while True:
        data = process.stdout.read(size)
        if len(data) < size:
            break
        yield np.frombuffer(data, dtype=np.uint8).reshape(HEIGHT, WIDTH).astype(np.float32)
    if process.wait() != 0:
        raise SystemExit(f"ffmpeg could not read {path}")


def test_frames():
    """A moving bar and the frame's number, to try the whole way to the screen without a recording."""
    for k in range(TEST_SECONDS * FPS):
        img = Image.new("L", (WIDTH, HEIGHT), 70)
        d = ImageDraw.Draw(img)
        x = int(k / (TEST_SECONDS * FPS) * WIDTH)
        d.rectangle((x, 0, x + 12, HEIGHT), fill=190)
        d.text((12, 12), f"TEST FILM  {k + 1}", fill=235)
        d.text((WIDTH - 40, HEIGHT - 20), "RIGHT", fill=235)
        yield np.asarray(img, dtype=np.float32)


class OldFilm:
    """What makes it old: the same seed gives the same flaws in the same frames."""

    def __init__(self):
        self.rng = np.random.default_rng(SEED)
        self.py = random.Random(SEED)
        y, x = np.mgrid[0:HEIGHT, 0:WIDTH]
        d = np.hypot((x - WIDTH / 2) / (WIDTH / 2), (y - HEIGHT / 2) / (HEIGHT / 2))
        self.vignette = np.clip(1.08 - 0.5 * d ** 2, 0, 1).astype(np.float32)
        self.scratch_x = self.py.randrange(WIDTH)

    def apply(self, frame):
        light = 1.0 + self.py.gauss(0, 0.035)                   # the lamp's uneven light
        grain = self.rng.normal(0, GRAIN, frame.shape).astype(np.float32)
        lifted = 255.0 * np.power(frame / 255.0, GAMMA)
        out = (lifted * 0.94 + 6) * light * self.vignette + grain
        self.scratch_x = (self.scratch_x + self.py.randint(-2, 2)) % WIDTH
        if self.py.random() < 0.3:                              # a scratch that wanders
            out[:, self.scratch_x] += 45
        if self.py.random() < 0.08:                             # a speck
            sx, sy = self.py.randrange(WIDTH - 2), self.py.randrange(HEIGHT - 2)
            out[sy:sy + 2, sx:sx + 2] = 245 if self.py.random() < 0.5 else 8
        return np.clip(out, 0, 255).astype(np.uint8)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recording", nargs="?", help="the video to turn into the film")
    parser.add_argument("--test", action="store_true", help="a short test pattern instead")
    parser.add_argument("--start", type=float, help="the second of the recording to start at")
    parser.add_argument("--end", type=float, help="the second of the recording to end at")
    args = parser.parse_args()
    if bool(args.recording) == args.test:
        parser.error("give a recording, or --test")
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)
    film = OldFilm()
    frames = test_frames() if args.test else read_video(args.recording, args.start, args.end)
    count, size = 0, 0
    for frame in frames:
        count += 1
        path = OUT / f"frame_{count:04d}.png"
        Image.fromarray(film.apply(frame), "L").save(path, optimize=True)
        size += path.stat().st_size
    if count == 0:
        raise SystemExit("no frames")
    (OUT / "film.json").write_text(json.dumps({"fps": FPS, "frames": count, "width": WIDTH, "height": HEIGHT}, indent=1) + "\n")
    print(f"{count} frames, {count / FPS:.1f} s, {size / 1024 / 1024:.1f} MB -> {OUT}", file=sys.stdout)


if __name__ == "__main__":
    main()

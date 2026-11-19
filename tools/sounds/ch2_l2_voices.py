"""
Chapter 2, Level 2's voices: the reunion with the coworker and worker B (phase 6.1).

Like the director's, the prologue's and Level 1's, they are the mod's one AI exception in sound (docs/en/audio.md, "AI voices"):
ElevenLabs library voices from the project's author, who also writes the lines. They arrive as MP3 files in
~/Documents/FinalNiv2 (niv2_1.mp3, niv2_2.mp3, niv2_3_B.mp3, niv2_4.mp3; as in Level 1, a letter says who speaks, none means the
coworker); this tool turns them into mono Ogg, 44.1 kHz, in voice/ch2l2/ with one and the same gain for the group (-18 LUFS, as the
others), and prints each one's length, which is the `seconds` of its line in `level.json`'s `reunion.lines`. export.py puts each into the
mod as `voice.<id>`.

Run (from the repository root):   python3 tools/sounds/ch2_l2_voices.py
Without the MP3 folder it uses the Ogg files already in voice/ch2l2/.
"""

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import audio_io  # noqa: E402
import prologue_voices  # noqa: E402  (decode and the group's gain)

HERE = Path(__file__).resolve().parent
VOICE = HERE / "voice" / "ch2l2"
SOURCE = Path.home() / "Documents" / "FinalNiv2"
OGG_QUALITY = 0.6

# (id, the MP3, speaker), in the order of the scene.
LINES = [
    ("reunion_01", "niv2_1.mp3", "coworker"),
    ("reunion_02", "niv2_2.mp3", "coworker"),
    ("reunion_03_b", "niv2_3_B.mp3", "worker_b"),
    ("reunion_04", "niv2_4.mp3", "coworker"),
]


def read(line_id):
    x, sr = audio_io.read(VOICE / f"{line_id}.ogg")
    if x.ndim > 1:
        x = x.mean(axis=1)
    assert sr == 44100, f"{line_id}: {sr} Hz"
    return x


def main():
    paths = [SOURCE / name for _, name, _ in LINES]
    missing = [p.name for p in paths if not p.exists()]
    if missing:
        if all((VOICE / f"{i}.ogg").exists() for i, _, _ in LINES):
            print(f"{SOURCE}: no MP3s, using the Ogg files in {VOICE}")
            return 0
        raise SystemExit(f"{SOURCE}: missing {', '.join(missing)}")
    factor = 10 ** (prologue_voices.group_gain_db(paths) / 20.0)
    VOICE.mkdir(parents=True, exist_ok=True)
    for (line_id, _, speaker), path in zip(LINES, paths):
        x = prologue_voices.decode(path) * factor
        audio_io.write_ogg(VOICE / f"{line_id}.ogg", x, 44100, OGG_QUALITY)
        print(f"{line_id}: {len(x) / 44100:5.2f} s  {speaker}  peak {20 * np.log10(np.max(np.abs(x))):.1f} dB")
    return 0


def sounds():
    """(sound id, file path under sounds/, samples) of every voice file, for export.py."""
    for line_id, _, _ in LINES:
        yield f"voice.{line_id}", f"voice/ch2l2/{line_id}", read(line_id)


if __name__ == "__main__":
    sys.exit(main())

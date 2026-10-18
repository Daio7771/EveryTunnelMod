"""
Renders every sound in catalog.SOUNDS to WAV and builds a listening page.

Usage (from the repository root):
    python3 tools/sounds/generate.py              # all sounds
    python3 tools/sounds/generate.py drip relay   # only ids containing these words

Output (git-ignored): tools/sounds/out/
    <id as folder path>_<n>.wav    e.g. ambient/drip_3.wav
    extras/*.wav                   preview-only mixes (not game sounds)
    index.html                     open it in a browser to listen to everything

Every file is generated from a fixed seed (sound id + variant), so the same code always
gives the same sounds. Converting to .ogg for the mod is a later step.
"""

import html
import json
import sys
import zlib
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))

from catalog import SOUNDS  # noqa: E402
from dsp import SR, loop_seam, normalize, pan, place, samples, stereo, write_wav  # noqa: E402

OUT = Path(__file__).resolve().parent / "out"


def seed_for(sound_id, variant):
    return zlib.crc32(f"{sound_id}#{variant}".encode())


def file_name(sound_id, variant):
    parts = sound_id.split(".")
    return "/".join(parts[:-1] + [f"{parts[-1]}_{variant + 1}.wav"])


def check(x, is_loop):
    """Problems worth knowing before listening."""
    problems = []
    if not np.all(np.isfinite(x)):
        problems.append("NaN/inf samples")
    if is_loop and loop_seam(x) > 8.0:
        problems.append(f"loop seam jump {loop_seam(x):.1f}x")
    return problems


def render(selected):
    rendered = {}
    report = []
    for sound in selected:
        for v in range(sound.variants):
            rng = np.random.default_rng(seed_for(sound.id, v))
            x = sound.generate(rng, v)
            if sound.channels == "mono" and x.ndim != 1:
                raise ValueError(f"{sound.id} should be mono")
            if sound.channels == "stereo" and x.ndim != 2:
                raise ValueError(f"{sound.id} should be stereo")
            x = normalize(x, sound.peak_db)
            path = OUT / file_name(sound.id, v)
            path.parent.mkdir(parents=True, exist_ok=True)
            write_wav(path, x, rng)
            rendered[(sound.id, v)] = x
            rms = 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-12)
            report.append((file_name(sound.id, v), x.shape[0] / SR, rms, check(x, sound.loop)))
    return rendered, report


def extras(rendered):
    """Preview-only mixes: how some sounds work together. Not game files."""
    out = {}
    rng = np.random.default_rng(7)

    beats = [rendered[("player.heartbeat", v)][:, 0] for v in range(4) if ("player.heartbeat", v) in rendered]
    if beats:
        def heartbeat_track(seconds, bpm_at):
            """Random variation per beat and slightly uneven timing, like the game will do."""
            y = np.zeros(samples(seconds + 1.0))
            at = 0.0
            while at < seconds:
                place(y, beats[rng.integers(len(beats))], at)
                at += 60.0 / bpm_at(at) * rng.uniform(0.98, 1.02)
            return normalize(stereo(y, y), -1.0)

        out["heartbeat_70bpm"] = ("Heartbeat at 70 bpm (calm)", heartbeat_track(10.0, lambda at: 70.0))
        out["heartbeat_130bpm"] = ("Heartbeat at 130 bpm (scared)", heartbeat_track(10.0, lambda at: 130.0))
        out["heartbeat_rising"] = ("Heartbeat speeding up from 60 to 140 bpm (the Pale man getting closer)",
                                   heartbeat_track(20.0, lambda at: 60.0 + 80.0 * min(1.0, at / 18.0)))

    if ("ambient.garage.drone", 0) in rendered:
        seconds = 30.0
        n = samples(seconds)
        mix = rendered[("ambient.garage.drone", 0)][:n].copy() * 0.9

        def get(sound_id, v=0):
            x = rendered.get((sound_id, v))
            return None if x is None else x

        def loop_to(x, length):
            reps = int(np.ceil(length / x.shape[0]))
            return np.tile(x, reps)[:length]

        hum = get("block.fluorescent.hum")
        if hum is not None:
            mix += pan(loop_to(hum, n), -0.5) * 0.12
            mix += pan(np.roll(loop_to(hum, n), samples(3.3)), 0.6) * 0.05
        vent_sound = get("ambient.vent")
        if vent_sound is not None:
            mix += pan(loop_to(vent_sound, n), 0.8) * 0.08
        events = [
            ("ambient.drip", 0, 2.0, -0.7, 0.12), ("ambient.drip", 2, 6.5, -0.6, 0.10),
            ("ambient.rumble", 1, 4.0, 0.0, 0.25), ("ambient.sparks", 1, 11.0, 0.7, 0.10),
            ("ambient.drip", 4, 15.0, -0.7, 0.12), ("block.fluorescent.flicker", 3, 18.5, -0.5, 0.12),
            ("ambient.relay", 0, 22.0, 0.4, 0.20), ("ambient.drip", 5, 24.5, 0.5, 0.10),
            ("ambient.drip", 1, 27.0, -0.6, 0.12),
        ]
        for sound_id, v, at, position, gain in events:
            x = get(sound_id, v)
            if x is not None:
                place(mix, pan(x, position) * gain, at)
        out["level1_mix"] = ("Level 1 ambience mix (30 s): drone, two lights, vent, drips, sparks, relay...",
                             normalize(mix, -3.0))

    for name, (_, x) in out.items():
        path = OUT / "extras" / f"{name}.wav"
        path.parent.mkdir(parents=True, exist_ok=True)
        write_wav(path, x, rng)
    titles = {name: title for name, (title, _) in out.items()}
    (OUT / "extras" / "titles.json").write_text(json.dumps(titles, indent=2), encoding="utf-8")


def versioned(relative):
    """Link with the file's modification time, so the browser never plays a cached old version."""
    path = OUT / relative
    stamp = int(path.stat().st_mtime) if path.exists() else 0
    return f"{relative}?v={stamp}"


def write_index():
    titles_file = OUT / "extras" / "titles.json"
    extra_titles = json.loads(titles_file.read_text(encoding="utf-8")) if titles_file.exists() else {}
    groups = {}
    for sound in SOUNDS:
        groups.setdefault(sound.group, []).append(sound)
    parts = [
        "<!doctype html><html lang='en'><head><meta charset='utf-8'>",
        "<meta name='viewport' content='width=device-width, initial-scale=1'>",
        "<title>Every Tunnel sounds</title><style>",
        ":root{--bg:#f6f6f4;--fg:#1d1d1f;--muted:#666;--card:#fff;--line:#ddd;--tag:#ececec}",
        "@media (prefers-color-scheme: dark){:root{--bg:#141416;--fg:#e8e8ea;--muted:#9a9aa0;--card:#1e1e22;--line:#333;--tag:#2c2c31}}",
        "body{background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif;margin:0;padding:24px 16px}",
        "main{max-width:900px;margin:0 auto}h1{margin:0 0 4px}h2{margin:32px 0 8px;border-bottom:1px solid var(--line);padding-bottom:4px}",
        ".sound{background:var(--card);border:1px solid var(--line);border-radius:8px;padding:12px 14px;margin:10px 0}",
        ".id{font-family:ui-monospace,monospace;font-weight:600}.use{color:var(--muted)}",
        ".tag{display:inline-block;background:var(--tag);border-radius:4px;padding:0 6px;margin-left:6px;font-size:12px}",
        ".note{font-size:13px;color:var(--muted);margin-top:4px}",
        ".players{display:flex;flex-wrap:wrap;gap:8px;margin-top:8px}.players div{display:flex;flex-direction:column;font-size:12px;color:var(--muted)}",
        "audio{width:260px;max-width:100%}p.intro{color:var(--muted)}",
        "</style></head><body><main>",
        "<h1>Every Tunnel: generated sounds</h1>",
        "<p class='intro'>Python-generated sounds for the Levels 1-2 demo. Use headphones: much of this is low "
        "bass that laptop speakers cannot play. Each sound is normalized on its own (in game the volumes are set "
        "separately), and all are dry: Sound Physics Remastered adds the echo of the garage in game. "
        "Loops repeat seamlessly when played with the loop button.</p>",
    ]
    if extra_titles:
        parts.append("<h2>Listen together (preview only)</h2>")
        for name, title in extra_titles.items():
            parts.append(f"<div class='sound'><div class='use'>{html.escape(title)}</div>"
                         f"<div class='players'><audio controls preload='none' src='{versioned(f'extras/{name}.wav')}'></audio></div></div>")
    for group, sounds in groups.items():
        parts.append(f"<h2>{html.escape(group)}</h2>")
        for sound in sounds:
            kind = "loop" if sound.loop else ("one-shot" if sound.variants == 1 else f"{sound.variants} variations")
            parts.append(f"<div class='sound'><span class='id'>{sound.id}</span>"
                         f"<span class='tag'>{sound.channels}</span><span class='tag'>{kind}</span>"
                         f"<div class='use'>{html.escape(sound.use)}</div>")
            if sound.notes:
                parts.append(f"<div class='note'>{html.escape(sound.notes)}</div>")
            parts.append("<div class='players'>")
            for v in range(sound.variants):
                loop_attr = " loop" if sound.loop else ""
                parts.append(f"<div>#{v + 1}<audio controls preload='none'{loop_attr} "
                             f"src='{versioned(file_name(sound.id, v))}'></audio></div>")
            parts.append("</div></div>")
    parts.append("</main></body></html>")
    (OUT / "index.html").write_text("\n".join(parts), encoding="utf-8")


def main():
    filters = sys.argv[1:]
    selected = [s for s in SOUNDS if not filters or any(f in s.id for f in filters)]
    OUT.mkdir(parents=True, exist_ok=True)
    rendered, report = render(selected)
    if not filters:
        extras(rendered)  # a filtered run keeps the existing extras
    write_index()
    for name, seconds, rms, problems in report:
        flag = "  !! " + ", ".join(problems) if problems else ""
        print(f"{name:45s} {seconds:6.2f} s  rms {rms:6.1f} dBFS{flag}")
    print(f"{len(report)} files in {OUT}; open {OUT / 'index.html'}")


if __name__ == "__main__":
    main()

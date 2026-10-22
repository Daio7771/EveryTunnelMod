"""
Exports the sounds into the mod.

Usage (from the repository root):
    python3 tools/sounds/export.py

Writes Ogg Vorbis files to src/main/resources/assets/every_tunnel/sounds/ (same folder
layout as the sound ids: item.flashlight.on -> item/flashlight/on_1.ogg) and the
sounds.json that declares every sound event and its variations. Sources:
- every generated sound in catalog.SOUNDS (exactly what generate.py renders)
- every recording cut in cc0_cuts.CUTS (CC0, and the credited ones in licensed/)

The Ogg encoder stamps each file with a random stream number, so re-encoding unchanged
audio would still change the files. export_manifest.json keeps a hash of each file's
audio; files whose audio did not change are not rewritten, which keeps git diffs clean.
"""

import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import audio_io  # noqa: E402
import cc0_cuts  # noqa: E402
from catalog import SOUNDS  # noqa: E402
from dsp import SR, write_wav  # noqa: E402
from generate import OUT, file_name, render_variant, write_index  # noqa: E402

import numpy as np  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "every_tunnel"
SOUNDS_DIR = ASSETS / "sounds"
MANIFEST = Path(__file__).resolve().parent / "export_manifest.json"
NAMESPACE = "every_tunnel"
QUALITY = 0.6
# Longer files are streamed from disk instead of being fully decoded into memory.
STREAM_SECONDS = 8.0


def audio_hash(x):
    return hashlib.sha256(np.round(x * 32767).astype(np.int16).tobytes() + str(QUALITY).encode()).hexdigest()[:16]


def main():
    manifest = json.loads(MANIFEST.read_text()) if MANIFEST.exists() else {}
    new_manifest = {}
    events = {}
    written = skipped = 0

    def export(sound_id, relative, x, stream=None, volume=1.0, attenuation=None):
        nonlocal written, skipped
        path = SOUNDS_DIR / f"{relative}.ogg"
        digest = audio_hash(x)
        new_manifest[relative] = digest
        if path.exists() and manifest.get(relative) == digest:
            skipped += 1
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            audio_io.write_ogg(path, x, SR, QUALITY)
            written += 1
        name = f"{NAMESPACE}:{relative}"
        if stream is None:
            stream = x.shape[0] / SR > STREAM_SECONDS
        entry = {"name": name}
        if stream:
            entry["stream"] = True
        if volume != 1.0:
            entry["volume"] = volume
        if attenuation is not None:
            entry["attenuation_distance"] = attenuation
        if len(entry) == 1:
            entry = name
        events.setdefault(sound_id, {"sounds": []})["sounds"].append(entry)

    for sound in SOUNDS:
        for v in range(sound.variants):
            x, _ = render_variant(sound, v)
            export(sound.id, file_name(sound.id, v)[:-len(".wav")], x, sound.stream, sound.game_volume, sound.attenuation)

    cut_titles = {}
    (OUT / "cuts").mkdir(parents=True, exist_ok=True)
    for cut in cc0_cuts.CUTS:
        pieces, segments = cc0_cuts.render(cut)
        files = []
        for i, (x, (start, end)) in enumerate(zip(pieces, segments)):
            relative = file_name(cut.id, i)[:-len(".wav")]
            export(cut.id, relative, x, volume=cut.game_volume, attenuation=cut.attenuation)
            preview = relative.replace("/", "_") + ".wav"
            write_wav(OUT / "cuts" / preview, x, np.random.default_rng(0))
            files.append(preview)
            print(f"{cut.id} #{i + 1}: {cut.source} {start:.1f}-{end:.1f} s")
        cut_titles[cut.id] = {"use": cut.use, "source": cut.source, "channels": cut.channels, "files": files}
    (OUT / "cuts" / "titles.json").write_text(json.dumps(cut_titles, indent=2), encoding="utf-8")

    # Remove .ogg files that no sound produces any more.
    removed = 0
    for path in SOUNDS_DIR.rglob("*.ogg"):
        if path.relative_to(SOUNDS_DIR).with_suffix("").as_posix() not in new_manifest:
            path.unlink()
            removed += 1

    (ASSETS / "sounds.json").write_text(json.dumps(dict(sorted(events.items())), indent=2) + "\n", encoding="utf-8")
    MANIFEST.write_text(json.dumps(dict(sorted(new_manifest.items())), indent=2) + "\n", encoding="utf-8")
    write_index()
    size = sum(p.stat().st_size for p in SOUNDS_DIR.rglob("*.ogg")) / 1e6
    print(f"{len(events)} sound events, {len(new_manifest)} files ({written} written, {skipped} unchanged, "
          f"{removed} removed), {size:.1f} MB in {SOUNDS_DIR.relative_to(ROOT)}")


if __name__ == "__main__":
    main()

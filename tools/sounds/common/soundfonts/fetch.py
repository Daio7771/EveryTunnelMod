"""
Fetches the SoundFonts and sample sets the sampled music is played through (sampler.py).

Usage (from the repository root):
    python3 -I tools/sounds/common/soundfonts/fetch.py

Downloads each archive into its own folder under files/, checks its SHA-256, extracts it there
(7z or tar must be installed) and copies the .sf2 to tools/sounds/common/soundfonts/<name>.sf2. The
violin is a folder of WAV files (solo_violin/, each checked against solo_violin.sha256). What is
already there is left alone. Nothing here is in git (see .gitignore): all of it is CC0 and its
sources are in SOURCES.md, so anyone can fetch it again.
"""

import hashlib
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
FILES = HERE / "files"

# name, URL, SHA-256 of the archive
FONTS = [
    ("old_piano_fb", "https://github.com/freepats/old-piano-FB/releases/download/2020-04-01/PianoFB-small-SF2-20200401.7z",
     "e904474b1eb44088e3f508b65b9c387baa72cb58f7734f1495eab4c348cc89ce"),
]


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def extract(archive, into):
    into.mkdir(parents=True, exist_ok=True)
    if archive.name.endswith(".7z"):
        tool = shutil.which("7z") or shutil.which("7za") or shutil.which("7zr")
        if tool is None:
            sys.exit("7z is needed to unpack " + archive.name)
        subprocess.run([tool, "x", "-y", "-bso0", "-bsp0", f"-o{into}", str(archive)], check=True)
    else:
        subprocess.run(["tar", "-xf", str(archive), "-C", str(into)], check=True)


# The solo violin of VSCO 2 CE (Versilian Studios): sustained notes with vibrato, two dynamics, every third semitone
VIOLIN_URL = "https://raw.githubusercontent.com/sgossner/VSCO-2-CE/master/Strings/Solo%20Violin/Arco%20Vib/"


def fetch_violin():
    folder = HERE / "solo_violin"
    folder.mkdir(exist_ok=True)
    for line in (HERE / "solo_violin.sha256").read_text().splitlines():
        digest, name = line.split()
        target = folder / name
        if not target.exists():
            print(f"solo_violin: downloading {name}")
            urllib.request.urlretrieve(VIOLIN_URL + name, target)
        if sha256(target) != digest:
            sys.exit(f"{name}: the SHA-256 is not the one solo_violin.sha256 lists")
    print(f"solo_violin: {len(list(folder.glob('*.wav')))} files")


def main():
    fetch_violin()
    for name, url, digest in FONTS:
        target = HERE / f"{name}.sf2"
        if target.exists():
            print(f"{name}: there")
            continue
        folder = FILES / name
        folder.mkdir(parents=True, exist_ok=True)
        archive = folder / url.rsplit("/", 1)[1]
        if not archive.exists():
            print(f"{name}: downloading {url}")
            urllib.request.urlretrieve(url, archive)
        if sha256(archive) != digest:
            sys.exit(f"{archive.name}: the SHA-256 is not the one SOURCES.md was written for")
        extracted = folder / "extracted"
        if not extracted.exists():
            extract(archive, extracted)
        found = sorted(extracted.rglob("*.sf2"))
        if len(found) != 1:
            sys.exit(f"{name}: expected one .sf2 in the archive, found {len(found)}")
        shutil.copyfile(found[0], target)
        print(f"{name}: {target.name} ({target.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()

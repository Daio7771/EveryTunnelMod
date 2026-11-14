"""
Plays a score through a SoundFont with FluidSynth, for the music made of sampled instruments.

The notes are written in code as `Part` events in seconds (the file is a MIDI of one tick per
millisecond: 1000 ticks a quarter at one quarter a second). Each part is rendered alone with
its own SoundFont, dry (FluidSynth's reverb and chorus off, so the piece adds its own), and
comes back as a stereo float array at SR, to be mixed by the piece. Needs the `fluidsynth`
program on the PATH and the SoundFonts fetched (`tools/sounds/soundfonts/fetch.py`).

    part = Part("tools/sounds/soundfonts/old_piano_fb.sf2")
    part.note(1.0, 3.0, 71, 80)                 # start s, length s, MIDI note, velocity 1..127
    part.note(2.0, 3.0, 76, 80, channel=1)      # another channel: its own controllers
    part.ramp(0.0, 4.0, 11, 0, 127, channel=1)  # controller 11 (expression) from 0 to 127 over s
    audio = part.render(tail_s=8.0)

A channel is a voice with its own controllers: a pad that swells and fades chord by chord puts
neighbouring chords on different channels. Channel 9 is drums in General MIDI: not used.

FluidSynth's output is deterministic for the same SoundFont, MIDI file and version.

For multisamples that come as plain WAV files and no SoundFont (the solo violin), `SamplePlayer`
does the same job without FluidSynth: it takes the nearest recorded note, shifts it by the
few semitones left, crossfades the soft and loud recordings by velocity and fades each note out.
"""

import re
import shutil
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from scipy.io import wavfile
from scipy.signal import resample

SR = 44100
PPQ = 1000          # ticks per quarter; the tempo is one quarter a second, so a tick is a millisecond
CHANNELS = [0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14]   # 9 is drums; 15 closes the file


class SamplerError(RuntimeError):
    pass


def _vlq(n):
    """MIDI variable-length quantity."""
    out = [n & 0x7F]
    n >>= 7
    while n:
        out.append((n & 0x7F) | 0x80)
        n >>= 7
    return bytes(reversed(out))


class Part:
    """One instrument: a SoundFont preset and the events it plays."""

    def __init__(self, soundfont, bank=0, program=0):
        self.soundfont = Path(soundfont)
        self.bank = bank
        self.program = program
        self.events = []        # (tick, order, bytes)
        self.channels = {0}
        self.end = 0.0

    def _add(self, t, data, order=1):
        self.events.append((max(0, int(round(t * 1000.0))), order, bytes(data)))
        self.end = max(self.end, t)

    def _channel(self, channel):
        if channel not in CHANNELS:
            raise ValueError(f"channel {channel} is not usable (see CHANNELS)")
        self.channels.add(channel)
        return channel

    def note(self, start, length, midi, velocity, channel=0):
        """A note: on at `start` seconds, off `length` seconds later. Velocity 1..127."""
        c = self._channel(channel)
        v = int(np.clip(round(velocity), 1, 127))
        self._add(start, [0x90 | c, midi, v], order=2)
        self._add(start + length, [0x80 | c, midi, 0], order=0)

    def control(self, t, controller, value, channel=0):
        c = self._channel(channel)
        self._add(t, [0xB0 | c, controller, int(np.clip(round(value), 0, 127))], order=1)

    def ramp(self, start, length, controller, a, b, channel=0, step=0.05):
        """A controller going from a to b over `length` seconds in steps of `step` s (a smooth swell, a fade)."""
        n = max(1, int(round(length / step)))
        for i in range(n + 1):
            x = i / n
            self.control(start + length * x, controller, a + (b - a) * x * x * (3.0 - 2.0 * x), channel)

    def bend(self, t, cents, range_cents=200, channel=0):
        """Pitch bend, in cents within the default +-200 cents range."""
        c = self._channel(channel)
        v = int(np.clip(8192 + 8192 * cents / range_cents, 0, 16383))
        self._add(t, [0xE0 | c, v & 0x7F, v >> 7], order=1)

    def midi(self, tail_s):
        """The Standard MIDI File: tempo, program, events, and a last event `tail_s` after the last one."""
        track = bytearray()
        track += b"\x00\xff\x51\x03" + (1_000_000).to_bytes(3, "big")           # one quarter a second
        for c in sorted(self.channels):
            track += b"\x00" + bytes([0xB0 | c, 0, self.bank & 0x7F])         # bank select
            track += b"\x00" + bytes([0xC0 | c, self.program])
        last = 0
        events = sorted(self.events)
        events.append((int(round((self.end + tail_s) * 1000.0)), 1, bytes([0xB0 | 15, 7, 100])))
        for tick, _, data in events:
            track += _vlq(tick - last) + data
            last = tick
        track += b"\x00\xff\x2f\x00"
        return (b"MThd" + (6).to_bytes(4, "big") + (0).to_bytes(2, "big") + (1).to_bytes(2, "big")
                + PPQ.to_bytes(2, "big") + b"MTrk" + len(track).to_bytes(4, "big") + bytes(track))

    def render(self, tail_s=8.0, gain=0.6):
        """Plays the part and returns it as stereo float, dry: (n, 2) at SR."""
        exe = shutil.which("fluidsynth")
        if exe is None:
            raise SamplerError("fluidsynth is not installed (the program, e.g. `pacman -S fluidsynth`)")
        if not self.soundfont.is_file():
            raise SamplerError(f"{self.soundfont} is missing: run tools/sounds/soundfonts/fetch.py")
        with tempfile.TemporaryDirectory() as tmp:
            mid = Path(tmp) / "part.mid"
            wav = Path(tmp) / "part.wav"
            mid.write_bytes(self.midi(tail_s))
            run = subprocess.run(
                [exe, "-ni", "-r", str(SR), "-g", str(gain), "-R", "0", "-C", "0", "-T", "wav", "-O", "float",
                 "-F", str(wav), str(self.soundfont), str(mid)],
                capture_output=True, text=True, timeout=300)
            if run.returncode != 0 or not wav.is_file():
                raise SamplerError(f"fluidsynth failed on {self.soundfont.name}: {run.stderr.strip()[-300:]}")
            rate, data = wavfile.read(wav)
        data = data.astype(np.float64)
        if data.ndim == 1:
            data = np.stack([data, data], axis=1)
        return data


def presets(soundfont):
    """(bank, program, name) of the SoundFont's presets, read from its `phdr` chunk."""
    raw = Path(soundfont).read_bytes()
    pos = raw.find(b"phdr")
    size = int.from_bytes(raw[pos + 4:pos + 8], "little")
    out = []
    for i in range(0, size - 38, 38):
        rec = raw[pos + 8 + i:pos + 8 + i + 38]
        name = rec[:20].split(b"\x00")[0].decode("latin-1")
        out.append((int.from_bytes(rec[22:24], "little"), int.from_bytes(rec[20:22], "little"), name))
    return out


NOTE_NAMES = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note_number(name):
    """MIDI note of a name like G3, A#4 or Bb2 (C4 = 60)."""
    n = NOTE_NAMES[name[0]] + (1 if "#" in name else -1 if "b" in name else 0)
    return n + 12 * (int(name[-1]) + 1)


class SamplePlayer:
    """
    A multisampled instrument from a folder of WAV files, one long note each, named
    `<anything>_<note>_<layer>.wav` (`LLVln_ArcoVib_G3_f.wav`: G3, layer f). Layers are dynamics, soft
    to loud in alphabetical order of their letters (`p` before `f`); velocity 0..1 crossfades them.
    """

    FILE = re.compile(r"_([A-G][#b]?-?\d)_([a-z]+)\.wav$")

    def __init__(self, folder, layers=("p", "f")):
        self.layers = layers
        self.samples = {layer: {} for layer in layers}      # layer -> root MIDI -> stereo float array
        folder = Path(folder)
        for path in sorted(folder.glob("*.wav")):
            m = self.FILE.search(path.name)
            if m and m.group(2) in self.samples:
                rate, data = wavfile.read(path)
                if rate != SR:
                    raise SamplerError(f"{path.name} is {rate} Hz, not {SR}")
                data = data.astype(np.float64) / (32768.0 if data.dtype == np.int16 else 2147483648.0)
                self.samples[m.group(2)][note_number(m.group(1))] = data if data.ndim == 2 else np.stack([data, data], axis=1)
        if not all(self.samples.values()):
            raise SamplerError(f"{folder} has no samples: run tools/sounds/soundfonts/fetch.py")
        # Recordings differ in level from note to note: each is brought to its layer's average (the first 4 s)
        for layer, roots in self.samples.items():
            level = {r: np.sqrt(np.mean(x[:4 * SR] ** 2)) for r, x in roots.items()}
            target = float(np.mean(list(level.values())))
            for r in roots:
                roots[r] = roots[r] * (target / level[r])
        self.notes = []         # (start, length, midi, velocity, release)

    def note(self, start, length, midi, velocity, release=0.4):
        """A note: from `start` for `length` seconds, then `release` s of fade. Velocity 0..1."""
        self.notes.append((start, length, midi, float(np.clip(velocity, 0.0, 1.0)), release))

    def _voice(self, layer, midi, seconds):
        """`seconds` of the nearest recorded note of a layer, shifted to `midi`."""
        roots = self.samples[layer]
        root = min(roots, key=lambda r: abs(r - midi))
        ratio = 2.0 ** ((midi - root) / 12.0)
        source = roots[root]
        take = min(len(source), int(seconds * ratio * SR) + 1)
        piece = source[:take]
        if midi != root:
            piece = resample(piece, max(2, int(round(take / ratio))), axis=0)
        return piece[:int(seconds * SR)]

    def render(self, tail_s=4.0):
        """Plays the notes and returns them as stereo float at SR."""
        end = max(start + length + release for start, length, _, _, release in self.notes) + tail_s
        out = np.zeros((int(end * SR) + 1, 2))
        soft, loud = self.layers[0], self.layers[-1]
        for start, length, midi, velocity, release in self.notes:
            seconds = length + release
            weights = {soft: np.cos(velocity * np.pi / 2.0), loud: np.sin(velocity * np.pi / 2.0)}
            voice = sum(w * self._voice(layer, midi, seconds) for layer, w in weights.items()
                        if w > 1e-3 and layer in self.samples)
            n = len(voice)
            env = np.ones(n)
            fade_in = min(n, int(0.008 * SR))
            env[:fade_in] = np.linspace(0.0, 1.0, fade_in)
            hold = min(n, int(length * SR))
            fade_out = n - hold
            if fade_out > 0:
                env[hold:] = np.cos(np.linspace(0.0, np.pi / 2.0, fade_out)) ** 2
            i = int(round(start * SR))
            out[i:i + n] += voice * env[:, None]
        return out

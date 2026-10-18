"""
Small DSP toolkit for the Every Tunnel sound generators (numpy + scipy only).

Conventions:
- Sample rate SR (44.1 kHz). Mono signals are 1-D float arrays, stereo signals are
  (n, 2) arrays. Values are roughly in -1..1 before the final normalize().
- Everything random takes a numpy Generator, so a fixed seed always gives the same file.
- Loops are built "circularly": FFT-shaped noise, periodic modulation and tones with a
  whole number of cycles all repeat exactly after n samples, so the loop point is
  seamless without a crossfade (check with loop_seam()).
"""

import numpy as np
from scipy import signal
from scipy.interpolate import CubicSpline
from scipy.io import wavfile

SR = 44100
TAU = 2.0 * np.pi


# ---------------------------------------------------------------------------
# Time

def samples(seconds):
    return int(round(seconds * SR))


def time_axis(n):
    return np.arange(n) / SR


def loop_frequency(freq, loop_seconds):
    """Nearest frequency with a whole number of cycles in the loop, so it repeats exactly."""
    return max(1, round(freq * loop_seconds)) / loop_seconds


# ---------------------------------------------------------------------------
# Noise (circular: generated in the frequency domain, so it loops by itself)

def _band_gain(f, lo, hi, order):
    gain = np.ones_like(f)
    if lo is not None:
        with np.errstate(divide="ignore"):
            gain *= 1.0 / np.sqrt(1.0 + (lo / np.maximum(f, 1e-9)) ** (2 * order))
    if hi is not None:
        gain *= 1.0 / np.sqrt(1.0 + (f / hi) ** (2 * order))
    return gain


def shaped_noise(n, rng, exponent=0.0, lo=None, hi=None, order=4):
    """
    Noise with power falling as 1/f^exponent (0 white, 1 pink, 2 brown), optionally
    band-limited with Butterworth-like edges. Unit standard deviation.
    """
    spectrum = np.fft.rfft(rng.standard_normal(n))
    f = np.fft.rfftfreq(n, 1.0 / SR)
    f_safe = f.copy()
    f_safe[0] = f[1] if len(f) > 1 else 1.0
    spectrum *= f_safe ** (-exponent / 2.0) * _band_gain(f, lo, hi, order)
    spectrum[0] = 0.0
    x = np.fft.irfft(spectrum, n)
    return x / (np.std(x) + 1e-12)


def fft_filter(x, lo=None, hi=None, order=4):
    """Circular band filter (keeps loops seamless). Works on mono or stereo."""
    n = x.shape[0]
    f = np.fft.rfftfreq(n, 1.0 / SR)
    gain = _band_gain(f, lo, hi, order)
    if x.ndim == 2:
        gain = gain[:, None]
    return np.fft.irfft(np.fft.rfft(x, axis=0) * gain, n, axis=0)


# ---------------------------------------------------------------------------
# Time-domain filters (for one-shots)

def lowpass(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, "low", fs=SR, output="sos"), x, axis=0)


def highpass(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, "high", fs=SR, output="sos"), x, axis=0)


def bandpass(x, lo, hi, order=4):
    return signal.sosfilt(signal.butter(order, [lo, hi], "band", fs=SR, output="sos"), x, axis=0)


def resonator(x, freq, q):
    """Narrow resonant peak (for modal / ringing sounds driven by an excitation)."""
    b, a = signal.iirpeak(freq, q, fs=SR)
    return signal.lfilter(b, a, x)


# ---------------------------------------------------------------------------
# Modulation and envelopes

def smooth_random(n, rate_hz, rng, periodic=False):
    """Slow random wobble in about -1..1, changing direction about rate_hz times per second."""
    points = max(4, int(n / SR * rate_hz) + 2)
    values = rng.uniform(-1.0, 1.0, points)
    positions = np.linspace(0, n, points)
    if periodic:
        values[-1] = values[0]
        spline = CubicSpline(positions, values, bc_type="periodic")
    else:
        spline = CubicSpline(positions, values)
    y = spline(np.arange(n))
    return np.clip(y, -1.2, 1.2) / 1.2


def envelope(n, points):
    """Piecewise linear envelope from (seconds, level) points."""
    times, levels = zip(*points)
    return np.interp(time_axis(n), times, levels)


def fade(x, fade_in=0.005, fade_out=0.005):
    """Raised-cosine fades at both ends (removes clicks)."""
    y = x.copy()
    for length, ramp_up in ((samples(fade_in), True), (samples(fade_out), False)):
        length = min(length, y.shape[0])
        if length <= 1:
            continue
        ramp = 0.5 - 0.5 * np.cos(np.linspace(0.0, np.pi, length))
        if y.ndim == 2:
            ramp = ramp[:, None]
        if ramp_up:
            y[:length] *= ramp
        else:
            y[-length:] *= ramp[::-1]
    return y


# ---------------------------------------------------------------------------
# Sources

def damped_partials(n, freqs, amps, taus, rng, start=0.0):
    """Sum of exponentially decaying sines starting at `start` seconds (modal synthesis)."""
    y = np.zeros(n)
    s0 = samples(start)
    if s0 >= n:
        return y
    t = time_axis(n - s0)
    for f, a, tau in zip(freqs, amps, taus):
        if f >= SR * 0.45:
            continue
        y[s0:] += a * np.exp(-t / tau) * np.sin(TAU * f * t + rng.uniform(0, TAU))
    return y


def glide_tone(freq_curve, harmonics=(1.0,), phase=0.0):
    """Tone following a per-sample frequency curve; `harmonics` are the amplitudes of 1x, 2x, 3x..."""
    ph = TAU * np.cumsum(freq_curve) / SR + phase
    y = np.zeros_like(freq_curve)
    for k, amp in enumerate(harmonics, start=1):
        if amp:
            y += amp * np.sin(k * ph)
    return y


def burst(n, rng, start, length, lo=None, hi=None, tau=None, amp=1.0):
    """Short filtered noise burst (clicks, impacts, crackles) added at `start` seconds."""
    y = np.zeros(n)
    s0 = samples(start)
    m = max(2, samples(length))
    if s0 >= n:
        return y
    m = min(m, n - s0)
    pad = samples(0.01)
    noise = rng.standard_normal(m + pad)
    if lo is not None and hi is not None:
        noise = bandpass(noise, lo, hi, order=2)
    elif lo is not None:
        noise = highpass(noise, lo, order=2)
    elif hi is not None:
        noise = lowpass(noise, hi, order=2)
    noise = noise[pad:]
    if tau is not None:
        noise *= np.exp(-time_axis(m) / tau)
    y[s0:s0 + m] += amp * fade(noise, 0.0003, 0.0003)
    return y


def mains_hum(t, f0, rng, harmonics=30, tilt=1.3):
    """Electrical hum: f0 and its harmonics with falling amplitude."""
    y = np.zeros_like(t)
    for k in range(1, harmonics + 1):
        if k * f0 >= SR * 0.45:
            break
        y += (1.0 / k ** tilt) * np.sin(TAU * k * f0 * t + rng.uniform(0, TAU))
    return y


def saw(t, freq, rng):
    """Naive sawtooth (fine for low notes that get low-passed afterwards)."""
    return signal.sawtooth(TAU * freq * t + rng.uniform(0, TAU))


def drive(x, amount):
    """Soft saturation; also adds harmonics so low sounds are audible on small speakers."""
    return np.tanh(amount * x) / np.tanh(amount)


# ---------------------------------------------------------------------------
# Stereo, mixing, output

def stereo(left, right):
    return np.stack([left, right], axis=1)


def pan(mono, position):
    """Constant-power pan, position -1 (left) .. 1 (right)."""
    angle = (position + 1.0) * np.pi / 4.0
    return stereo(mono * np.cos(angle), mono * np.sin(angle))


def place(buffer, sound, start, circular=False):
    """Adds `sound` into `buffer` at `start` seconds; circular wraps the tail to the beginning (loops)."""
    s0 = samples(start)
    n = buffer.shape[0]
    if circular:
        idx = (s0 + np.arange(sound.shape[0])) % n
        np.add.at(buffer, idx, sound)
    else:
        end = min(n, s0 + sound.shape[0])
        if end > s0:
            buffer[s0:end] += sound[:end - s0]
    return buffer


def crossfade_loop(x, fade_seconds):
    """
    Loop for sounds that cannot be built circularly: render `fade_seconds` longer than the
    loop, then blend that extra tail into the beginning (equal power). The result's last
    sample flows into its first one.
    """
    f = samples(fade_seconds)
    length = x.shape[0] - f
    out = x[:length].copy()
    ramp = np.linspace(0.0, 1.0, f)
    if x.ndim == 2:
        ramp = ramp[:, None]
    out[:f] = x[:f] * np.sqrt(ramp) + x[length:] * np.sqrt(1.0 - ramp)
    return out


def normalize(x, peak_db=-1.0):
    peak = np.max(np.abs(x))
    return x if peak == 0 else x * (10 ** (peak_db / 20.0) / peak)


def loop_seam(x):
    """Jump at the loop point relative to the typical sample-to-sample change (about 1 = seamless)."""
    mono = x if x.ndim == 1 else x.mean(axis=1)
    typical = np.median(np.abs(np.diff(mono))) + 1e-12
    return abs(mono[0] - mono[-1]) / typical


def write_wav(path, x, rng):
    """16-bit WAV with TPDF dither."""
    scaled = x * 32767.0 + (rng.random(x.shape) - rng.random(x.shape))
    wavfile.write(path, SR, np.clip(np.round(scaled), -32768, 32767).astype(np.int16))

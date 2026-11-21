"""
Reading source recordings (WAV, MP3, FLAC, OGG) and writing Ogg Vorbis for the mod.

Uses the `soundfile` package when it is installed, otherwise calls the system's
libsndfile (1.1 or newer, for MP3 support) directly through ctypes, so no Python
package has to be installed. On Debian/Ubuntu libsndfile1 is usually already present.
"""

import ctypes
import ctypes.util

import numpy as np

try:
    import soundfile as _soundfile
except ImportError:
    _soundfile = None


class _SfInfo(ctypes.Structure):
    _fields_ = [
        ("frames", ctypes.c_int64),
        ("samplerate", ctypes.c_int),
        ("channels", ctypes.c_int),
        ("format", ctypes.c_int),
        ("sections", ctypes.c_int),
        ("seekable", ctypes.c_int),
    ]


_SFM_READ = 0x10
_SFM_WRITE = 0x20
_SF_FORMAT_OGG = 0x200000
_SF_FORMAT_VORBIS = 0x0060
_SFC_SET_VBR_ENCODING_QUALITY = 0x1300

_lib = None


def _libsndfile():
    global _lib
    if _lib is None:
        name = ctypes.util.find_library("sndfile")
        if name is None:
            raise RuntimeError("libsndfile not found: install libsndfile1, or the Python package soundfile")
        lib = ctypes.CDLL(name)
        lib.sf_open.restype = ctypes.c_void_p
        lib.sf_open.argtypes = [ctypes.c_char_p, ctypes.c_int, ctypes.POINTER(_SfInfo)]
        lib.sf_readf_double.restype = ctypes.c_int64
        lib.sf_readf_double.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_double), ctypes.c_int64]
        lib.sf_writef_double.restype = ctypes.c_int64
        lib.sf_writef_double.argtypes = [ctypes.c_void_p, ctypes.POINTER(ctypes.c_double), ctypes.c_int64]
        lib.sf_command.restype = ctypes.c_int
        lib.sf_command.argtypes = [ctypes.c_void_p, ctypes.c_int, ctypes.c_void_p, ctypes.c_int]
        lib.sf_close.argtypes = [ctypes.c_void_p]
        lib.sf_strerror.restype = ctypes.c_char_p
        lib.sf_strerror.argtypes = [ctypes.c_void_p]
        _lib = lib
    return _lib


def read(path):
    """Returns (samples, sample_rate); samples are float64, (n,) for mono or (n, channels)."""
    if _soundfile is not None:
        data, sr = _soundfile.read(str(path), dtype="float64")
        return data, sr
    lib = _libsndfile()
    info = _SfInfo()
    handle = lib.sf_open(str(path).encode(), _SFM_READ, ctypes.byref(info))
    if not handle:
        raise IOError(f"cannot read {path}: {lib.sf_strerror(None).decode()}")
    try:
        # MP3 frame counts can be estimates: read in blocks until the end.
        blocks = []
        block = 65536
        while True:
            buf = np.zeros(block * info.channels, dtype=np.float64)
            got = lib.sf_readf_double(handle, buf.ctypes.data_as(ctypes.POINTER(ctypes.c_double)), block)
            if got <= 0:
                break
            blocks.append(buf[:got * info.channels])
    finally:
        lib.sf_close(handle)
    data = np.concatenate(blocks) if blocks else np.zeros(0)
    data = data.reshape(-1, info.channels)
    return (data[:, 0] if info.channels == 1 else data), info.samplerate


def write_ogg(path, data, sample_rate, quality=0.6):
    """Ogg Vorbis, variable bitrate; quality 0..1 (0.6 is about 130-160 kbps stereo)."""
    data = np.clip(np.asarray(data, dtype=np.float64), -1.0, 1.0)
    channels = 1 if data.ndim == 1 else data.shape[1]
    if _soundfile is not None:
        _soundfile.write(str(path), data, sample_rate, format="OGG", subtype="VORBIS")
        return
    lib = _libsndfile()
    info = _SfInfo(0, sample_rate, channels, _SF_FORMAT_OGG | _SF_FORMAT_VORBIS, 0, 0)
    handle = lib.sf_open(str(path).encode(), _SFM_WRITE, ctypes.byref(info))
    if not handle:
        raise IOError(f"cannot write {path}: {lib.sf_strerror(None).decode()}")
    try:
        q = ctypes.c_double(quality)
        lib.sf_command(handle, _SFC_SET_VBR_ENCODING_QUALITY, ctypes.byref(q), ctypes.sizeof(q))
        interleaved = np.ascontiguousarray(data.reshape(-1, channels))
        # Small blocks: one huge write crashes libsndfile's Vorbis encoder.
        block = 4096
        for start in range(0, interleaved.shape[0], block):
            chunk = np.ascontiguousarray(interleaved[start:start + block])
            frames = chunk.shape[0]
            written = lib.sf_writef_double(handle, chunk.ctypes.data_as(ctypes.POINTER(ctypes.c_double)), frames)
            if written != frames:
                raise IOError(f"short write to {path}: {written} of {frames} frames")
    finally:
        lib.sf_close(handle)

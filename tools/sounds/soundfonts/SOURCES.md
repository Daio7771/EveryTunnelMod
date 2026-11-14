# SoundFonts

Instruments for the music played through FluidSynth and `SamplePlayer` (`tools/sounds/sampler.py`),
all **CC0 1.0** (public domain: no credit needed, listed here anyway). Nothing here is in git:
`fetch.py` downloads each archive (with its SHA-256), unpacks it under `files/` and copies the
`.sf2` next to it as `<name>.sf2`; the violin's WAV files go straight into `solo_violin/`.

| File | Instrument | What it is | Source | License |
|---|---|---|---|---|
| `old_piano_fb.sf2` | Old Piano FB, small (2020-04-01) | A honky-tonk upright piano, a little out of tune, recorded by the [FreePats](https://freepats.zenvoid.org/) project (the page calls it "Honky-tonk Piano") | [freepats.zenvoid.org/Piano/honky-tonk-piano.html](https://freepats.zenvoid.org/Piano/honky-tonk-piano.html) | CC0 1.0 |

## Sample sets (WAV, no SoundFont)

| Folder | Instrument | What it is | Source | License |
|---|---|---|---|---|
| `solo_violin/` | VSCO 2: CE, Solo Violin, Arco Vib | 30 WAV files of a solo violin bowing one long note with vibrato (G3 to C7, a file every third semitone, two dynamics, `p` and `f`, about 14 s each). Played by `sampler.py`'s `SamplePlayer` | [github.com/sgossner/VSCO-2-CE](https://github.com/sgossner/VSCO-2-CE/tree/master/Strings/Solo%20Violin/Arco%20Vib) | CC0 1.0; its README asks for credit to Versilian Studios / Sam Gossner, so here it is, and not to sell the samples themselves (only music rendered from them ships) |

`music.menu.chapter_2` is played by these two instruments.

Only the sounds rendered from them (the `.ogg` files, e.g. `music.menu.chapter_2`) are part of
the mod; the samples and SoundFonts are not shipped.

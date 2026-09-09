# FLOW

A browser-based 3D rhythm game that ships as **one self-contained HTML file**.
No install, no backend, no network. Everything — audio synthesis, chart
generation, colour extraction, saves — runs on-device.

## Play

- **Easiest — one file:** open **`FLOW.html`**. Everything (styles, game code,
  Three.js) is inlined into that single file, so it needs no folder structure,
  no server and no network. Download it anywhere — a Chromebook's Downloads
  folder, a USB stick, an email attachment — and double-click it.
- **From source:** open `index.html` (it loads `css/` and `js/` from alongside
  it), or serve the folder with `python3 -m http.server`.
- **Rebuild the single file** after changing any source:  `node build.js`
- **Host it:** any static host — GitHub Pages, Netlify, Vercel, S3.
- **Controls:** `D F J K` on desktop, tap the on-screen lanes on touch devices,
  `Esc` to pause. Touchscreen laptops get both at once.

## What makes it different

Most rhythm games play you a recording and score how close your taps land to a
chart drawn over it. Flow ties the music and the input together instead.

**You Are The Instrument.** In every synthesized mode the drums, bass and pads
are scheduled ahead — but the *lead melody is never scheduled at all*. Each
chart note carries a real frequency, and it only sounds when you hit it: in
tune when you're clean, detuned when you're late, a dead muted string when you
miss. A perfect run performs the song. A sloppy one audibly falls apart. (An
imported recording can't be gated like that, so misses duck and lowpass the
track instead.)

**Echo Debt.** A missed note doesn't just vanish — it comes back 8 bars later
as a violet echo note. Catch it and the debt clears with a score bonus; ignore
it and it's a second miss. The song replays your mistakes at you.

**Blackout.** A four-bar phrase plays lit, then repeats with the notes
invisible. You read it once, then perform it from memory — call and response
rather than sight-reading.

**Conductor.** No fixed BPM. Your own tap spacing is measured and eased into
the tempo of the bars being generated ahead of you, so the whole arrangement —
drums, bass, chords — speeds up and slows down to chase you.

**Ghost Race.** Your best run on a chart is recorded hit-by-hit and replayed
beside you as a translucent rival, including the exact bar where it broke.

**Flow State.** Chain enough clean hits and the run changes on all three
axes at once: hit windows widen, score doubles, and the lead voice starts
harmonizing itself in octaves and fifths.

The lead being player-gated doesn't mean silence when you miss: drums, bass,
pads *and a scheduled arpeggio counter-melody* always play, so the track
stands up as music on its own. Your hits are the top line on top of it.

## Modes

| Mode | What it is |
|---|---|
| Story Mode | 8 chapters of original score; each teaches a new mechanic |
| Your Music | Import your own file — auto-charted, album-colored |
| Rewind | Notes rise away from you; reading direction inverted |
| Blackout | Phrases vanish and repeat — played from memory |
| Tile Hopper | You're a ball bouncing lane to lane; every hit is a hop |
| Conductor | You set the tempo |
| Ghost Race | Race a recording of your best run |
| Marathon | All 8 chapters, one combo, one meter, no resets |
| Endless | Generative chart that tightens until you drop it |
| Daily Remix | One chapter + one modifier, seeded by the date |
| Drills | Short practice loops scored on accuracy alone |

## Your Music

A Steam-Big-Picture-style shelf: a grid of large cover tiles with a hero
panel for whatever's focused (click once to focus, again to play). Search is
filterable by **All / Songs / Artists** — the Artists filter also groups the
shelf under artist headings.

Drag in songs — one, a selection, or an entire folder — or use the **Folder**
button. Flow:

- reads each file's **own tags** for title, artist and **embedded cover art**
  (ID3v2 for MP3, iTunes atoms for M4A/MP4, Vorbis comments and PICTURE blocks
  for FLAC/OGG/Opus, RIFF INFO for WAV), so a dropped library names itself;
- matches loose files in the same drop by filename — `Song.lrc` attaches to
  `Song.mp3`, and a `cover.jpg` / `folder.jpg` applies to the whole folder;
- runs **onset detection** over three energy bands to find real transients,
  estimates BPM from the inter-onset histogram, and builds a playable chart —
  no server, no fingerprinting service;
- **extracts a palette from the album art** by canvas pixel sampling and
  repaints the lanes, notes, receptors, spectrum bars and menu with it, so
  every song's stage looks like its cover;
- **generates a cover** from the title/artist hash when no art is supplied, in
  the game's own palette family, so the library never has blank tiles;
- syncs a **right-hand lyrics panel**, karaoke-style. Lyrics don't need a
  file: hit **Lyrics** on any song to paste them in, or fetch timed lyrics
  from [LRCLIB](https://lrclib.net) (free and key-less) with one click. A
  matching `.lrc` dropped alongside the audio still works too;
- stores everything in IndexedDB — nothing is uploaded anywhere.

If a file can't be decoded (a DRM-protected purchase, an exotic codec), Flow
says so and returns you to the library instead of hanging on a blank stage.

### Spotify / Apple Music

Not wired up, deliberately. Full-track playback from either service needs a
registered developer app, OAuth secrets held server-side, and a paid account —
none of which can ship safely inside a downloadable client. The usual
workaround is searching by song title, which surfaces unrelated artists with
matching titles. Rather than do that, streaming ships disabled with local file
import as the real path. If you run your own backend, add credentials to
`js/streaming-config.js` and set `ENABLED = true`; the hooks in
`js/streaming.js` are already in place.

## Feel

Feedback is layered so that fast play still reads at a glance:

- judgement pops twice — a large tier-coloured word in the centre (gold-pink
  PERFECT, green GREAT, blue GOOD, grey-red MISS) and a small one anchored to
  the lane you actually hit, so your eye never leaves the tile;
- every hit throws a particle burst and shockwave at the tile's own position,
  and the combo number punches on *every* increment while physically growing
  as the streak does;
- combo milestones (10 / 25 / 50 / 100 / 150 / 200 / 300 / 500) escalate:
  wider bursts, a shockwave down every lane, a camera punch, and a
  screen-edge colour wash that deepens with the streak;
- notes carry a rim glow and a motion trail, and special notes (holds,
  bursts, echoes) get an approach ring that tightens as they arrive — a
  moment rather than permanent decoration;
- the background is a beat-pulsed ambient glow with drifting dust, light
  streaks and slow parallax rings, all tinted by the current palette.

## Performance

Built to stay smooth on old laptops without dropping quality on good ones:

- a live FPS sampler moves between **low / medium / high** tiers automatically,
  changing pixel ratio, fog distance, spectrum-bar count, particle budget,
  side geometry, note detail (rim glow / trails / rings) and which background
  layers draw at all — or pick a tier manually in Settings;
- every note mesh, spark and shockwave comes from a **pre-allocated pool**, so
  gameplay never allocates mid-run (the usual cause of stutter);
- **no lighting at all** — every material is unlit, so there is zero per-pixel
  lighting work; the beat pulse is done by scaling colours instead;
- sparks are a single **InstancedMesh** (one draw call, not 220) and the
  speed-line grid is one UV-scrolled plane (not 26 meshes); a full high-tier
  frame is ~69 draw calls;
- a **Resolution** slider renders below native and upscales — the single
  biggest win on integrated GPUs, and low tier does it automatically;
- one vendored dependency, no post-processing passes, no shadow maps.

If the browser suspends the audio context, the gameplay clock falls back to
the wall clock and rejoins the audio clock when it returns, so a stalled
context can never freeze the notes on screen.

## Layout

```
index.html
css/style.css
build.js                bundles everything into the single-file FLOW.html
FLOW.html               the built single-file game (open this one)
js/device.js            platform + hardware-tier detection
js/tags.js              ID3 / MP4 / FLAC / OGG / WAV metadata + cover art
js/persist.js           saves, best scores, ghosts, settings
js/color.js             album-art palette extraction
js/audio-engine.js      synthesis, tempo-map timeline, onset auto-charting
js/composer.js          procedural arranger (drums/bass/pad/lead)
js/story-data.js        8 chapters + narrative
js/modes.js             the 11 modes as modifier sets
js/ghost.js             ghost recording / playback
js/library.js           IndexedDB music library
js/lyrics.js            .lrc parsing + synced panel
js/vfx.js               ambient particle field, pooled 3D effects
js/game.js              gameplay, mechanics, rendering
js/main.js              screens and wiring
js/vendor/three.min.js  Three.js r160
```

All music in Flow is generated at runtime from music-theory rules. No
copyrighted audio is bundled.

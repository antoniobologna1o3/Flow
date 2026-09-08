# FLOW

A browser-based 3D rhythm game. No build step, no backend, no install —
open `index.html` and play. Everything (audio synthesis, chart generation,
color extraction, saves) runs on-device.

## Play

- **Local:** open `index.html`, or serve the folder (`python3 -m http.server`)
  for the smoothest result.
- **Host it:** any static host — GitHub Pages, Netlify, Vercel, S3. Three.js is
  vendored in `js/vendor/`, so the game works fully offline once loaded.
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

Import any audio file you own. Flow:

- runs **onset detection** over three energy bands to find real transients,
  estimates BPM from the inter-onset histogram, and builds a playable chart —
  no server, no fingerprinting service;
- **extracts a palette from the album art** by canvas pixel sampling and
  repaints the lanes, notes, receptors, spectrum bars and menu with it, so
  every song's stage looks like its cover;
- **generates a cover** from the title/artist hash when no art is supplied, in
  the game's own palette family, so the library never has blank tiles;
- syncs a **right-hand lyrics panel** from a `.lrc` (timestamped) or plain
  `.txt` file, karaoke-style;
- stores everything in IndexedDB — nothing is uploaded anywhere.

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
- one vendored dependency, no post-processing passes, no shadow maps.

## Layout

```
index.html
css/style.css
js/device.js            platform + hardware-tier detection
js/persist.js           saves, best scores, ghosts, settings
js/color.js             album-art palette extraction
js/audio-engine.js      synthesis, tempo-map timeline, onset auto-charting
js/composer.js          procedural arranger (drums/bass/pad/lead)
js/story-data.js        8 chapters + narrative
js/modes.js             the 10 modes as modifier sets
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

# Flow

A browser-based 3D rhythm game. No build step, no backend — open `index.html`
(or host the folder) and play.

## Play it

- **Locally:** open `index.html` in a modern desktop or mobile browser, or serve
  the folder (`python3 -m http.server`) for the smoothest experience.
- **Host it anywhere static:** GitHub Pages, Netlify, Vercel, S3 — it's plain
  HTML/CSS/JS with one vendored dependency (`js/vendor/three.min.js`), so it
  works fully offline once loaded.

## Features

- **Auto device detection** — on first launch Flow guesses PC / Mobile / Touch
  laptop from touch support, pointer type and screen size, then asks you to
  confirm (changeable anytime in Settings).
- **Touch + keyboard, unified** — keyboard lanes (`D F J K`) on desktop,
  on-screen touch lanes on phones/tablets/touchscreen laptops, both driven by
  pointer events so they work together on hybrid devices.
- **3D note highway** (Three.js) with an adaptive-quality system: it samples
  live FPS and automatically drops pixel ratio / fog distance / particle count
  on older laptops, then scales back up if the machine can handle more —
  no manual tuning required, though Settings also offers a manual override.
- **Story Mode** — six chapters of **original, procedurally-composed music**
  (synthesized live via the Web Audio API from music-theory rules — scales,
  chord tones, seeded randomness), each with narrative dialogue before/after.
  No copyrighted audio is bundled anywhere.
- **Flow State** — the unique mechanic: sustained high performance (Flow
  meter > 85%) live-reshapes the scene's lighting, fog, and receptor glow,
  so skilled play visibly and audibly changes the run in real time.
- **Your Music mode** — import any local audio file (+ optional album art +
  optional lyrics). The album art's own colors are extracted client-side and
  applied directly to the note highway's lane/receptor colors, so every
  imported song's stage looks like its cover. No embedded art? Flow generates
  a distinct on-brand cover from the song's title/artist automatically.
  Songs without a hand-made chart are auto-charted on-device via onset
  detection (no server, no external service).
- **Right-side lyrics panel** — drop in a `.lrc` (timestamped) or plain `.txt`
  lyrics file and it scrolls/highlights in sync on the right edge of the
  screen during play.
- **Library search** — filter your imported songs by artist or title.
- **Streaming (Spotify / Apple Music)** — playing those services' catalogs
  requires a registered developer app, backend-held OAuth secrets, and (for
  Spotify) a Premium account; none of that can live safely in a static
  client. Rather than faking a "search" that surfaces unrelated random songs
  with matching titles, Flow ships this honestly disabled with the real
  file-import path as the working alternative — see
  `js/streaming-config.js` if you want to wire up your own backend.

## Project layout

```
index.html
css/style.css
js/device.js          device/platform + hardware-tier guessing
js/color.js            album-art palette extraction (canvas pixel sampling)
js/audio-engine.js     Web Audio synth playback + file playback + auto-charting
js/story-data.js       procedural song composer + 6 story chapters
js/library.js          IndexedDB-backed local music library
js/lyrics.js           .lrc/.txt parsing + sync panel
js/game.js             Three.js gameplay, input, scoring, Flow State
js/main.js             screen/state wiring
js/streaming-config.js / js/streaming.js   optional Spotify/Apple hooks (disabled by default)
js/vendor/three.min.js vendored Three.js r160 (no CDN dependency)
```

## Controls

- **PC:** `D F J K` for the four lanes, `Esc` to pause.
- **Mobile / touch laptop:** tap the on-screen lanes at the bottom of the
  screen.

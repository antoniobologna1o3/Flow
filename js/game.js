/* ============================================================
   Gameplay.

   Distinctive mechanics implemented here:
   · You Are The Instrument — the lead melody is never scheduled by
     the audio engine. Each chart note carries a frequency, and it
     only sounds when you hit it (in tune when clean, detuned when
     late, a dead muted string on a miss). A perfect run performs
     the song; a bad one audibly falls apart. Imported recordings
     can't be gated, so they duck and lowpass on a miss instead.
   · Echo Debt — missed notes return 8 bars later as violet echoes.
     Catch one and the debt clears; the song literally replays your
     mistakes at you.
   · Blackout — a lit phrase is repeated with the notes invisible.
     Call and response: read it once, then play it from memory.
   · Conductor — no fixed BPM. Your tap rate rewrites the tempo of
     the bars being generated ahead of you.
   · Ghost Race — a beat-indexed recording of your best run replays
     beside you.
   ============================================================ */
const Game = (() => {
  const LANE_KEYS = ["D", "F", "J", "K"];
  const UNITS_PER_BEAT = 3.1;
  const LEAD_BEATS = 2.6;               // how far ahead notes appear
  const WINDOWS = { perfect: 0.052, great: 0.105, good: 0.17 };
  const ECHO_DELAY_BEATS = 32;          // 8 bars
  const NOTE_POOL = 140;

  let renderer, scene, camera, clock, pools;
  let receptors = [], laneMats = [], echoMat, hiddenMat, holdMats = [];
  let spectrumBars = [], highway, highwayGrid, sideWalls = [];
  let notePool = [], noteCursor = 0;
  let ghostMarkers = [];

  let quality = "auto", tier = "medium";
  let fpsBuf = [], animId = null;
  let laneCount = 4;

  let run = null;                        // the whole active-run state
  let onFinish = null, onChainAdvance = null;
  let lyricsCtl = null;
  let spectrumData = null;
  let shake = 0, camBaseY = 2.0, camBaseZ = 5.6;
  let paletteHex = [0x7c5cff, 0xff5ca8, 0x5cf0ff];

  /* ================= init ================= */
  function init() {
    const canvas = document.getElementById("game-canvas");
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, powerPreference: "high-performance" });
    renderer.setClearColor(0x05040e, 1);

    scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0x05040e, 7, 26);
    camera = new THREE.PerspectiveCamera(64, 1, 0.1, 70);
    camera.position.set(0, camBaseY, camBaseZ);
    camera.lookAt(0, 0.2, -6);

    scene.add(new THREE.AmbientLight(0xffffff, 0.5));
    const key = new THREE.PointLight(0x7c5cff, 1.6, 26);
    key.position.set(0, 5, 3);
    scene.add(key);
    const rim = new THREE.PointLight(0x5cf0ff, 1.1, 30);
    rim.position.set(0, 2, -14);
    scene.add(rim);
    scene.userData.key = key; scene.userData.rim = rim;

    highway = new THREE.Mesh(
      new THREE.PlaneGeometry(4.2, 40),
      new THREE.MeshStandardMaterial({ color: 0x0d0c20, roughness: 0.82, metalness: 0.15 })
    );
    highway.rotation.x = -Math.PI / 2;
    highway.position.z = -12;
    scene.add(highway);

    // moving grid lines give speed a readable texture
    highwayGrid = new THREE.Group();
    for (let i = 0; i < 26; i++) {
      const bar = new THREE.Mesh(
        new THREE.PlaneGeometry(4.2, 0.022),
        new THREE.MeshBasicMaterial({ color: 0x3b3670, transparent: true, opacity: 0.5 })
      );
      bar.rotation.x = -Math.PI / 2;
      bar.position.z = -i * 1.6;
      highwayGrid.add(bar);
    }
    scene.add(highwayGrid);

    // side walls — cheap depth cue that reads as a corridor
    for (const sx of [-2.1, 2.1]) {
      const wall = new THREE.Mesh(
        new THREE.PlaneGeometry(40, 2.4),
        new THREE.MeshBasicMaterial({ color: 0x120f2c, transparent: true, opacity: 0.55, side: THREE.DoubleSide })
      );
      wall.rotation.y = Math.PI / 2;
      wall.position.set(sx, 1.2, -12);
      scene.add(wall);
      sideWalls.push(wall);
    }

    buildLanes(4);
    buildSpectrum(24);
    buildNotePool();

    pools = VFX.createPools(scene, THREE);
    clock = new THREE.Clock();
    window.addEventListener("resize", onResize);
    onResize();
    bindInput();
  }

  function buildLanes(count) {
    for (const r of receptors) scene.remove(r);
    receptors = []; laneMats = []; holdMats = [];
    laneCount = count;
    const lw = 4 / count;
    for (let i = 0; i < count; i++) {
      const x = -2 + lw * i + lw / 2;

      const div = new THREE.Mesh(
        new THREE.PlaneGeometry(0.016, 40),
        new THREE.MeshBasicMaterial({ color: 0x2b2757, transparent: true, opacity: 0.65 })
      );
      div.rotation.x = -Math.PI / 2;
      div.position.set(-2 + lw * i, 0.012, -12);
      scene.add(div);

      const mat = new THREE.MeshStandardMaterial({
        color: 0x7c5cff, emissive: 0x7c5cff, emissiveIntensity: 0.55, roughness: 0.35,
      });
      laneMats.push(mat);
      holdMats.push(new THREE.MeshStandardMaterial({
        color: 0x7c5cff, emissive: 0x7c5cff, emissiveIntensity: 0.35,
        transparent: true, opacity: 0.55, roughness: 0.4,
      }));

      const rec = new THREE.Mesh(new THREE.BoxGeometry(lw * 0.86, 0.055, 0.42), mat.clone());
      rec.position.set(x, 0.05, 2.2);
      scene.add(rec);
      receptors.push(rec);
    }
    echoMat = new THREE.MeshStandardMaterial({
      color: 0xc08bff, emissive: 0xc08bff, emissiveIntensity: 0.9, roughness: 0.3,
    });
    hiddenMat = new THREE.MeshStandardMaterial({
      color: 0x2a2550, emissive: 0x120f28, emissiveIntensity: 0.1,
      transparent: true, opacity: 0.16, roughness: 0.6,
    });
  }

  function buildSpectrum(count) {
    for (const b of spectrumBars) scene.remove(b);
    spectrumBars = [];
    for (let i = 0; i < count; i++) {
      const side = i < count / 2 ? -1 : 1;
      const idx = i % (count / 2);
      const bar = new THREE.Mesh(
        new THREE.BoxGeometry(0.17, 1, 0.17),
        new THREE.MeshStandardMaterial({ color: 0x7c5cff, emissive: 0x7c5cff, emissiveIntensity: 0.5 })
      );
      bar.position.set(side * (2.5 + (idx % 3) * 0.42), 0, 1.6 - idx * 1.25);
      bar.scale.y = 0.1;
      scene.add(bar);
      spectrumBars.push(bar);
    }
  }

  function buildNotePool() {
    const geo = new THREE.BoxGeometry(1, 0.26, 0.34);
    for (let i = 0; i < NOTE_POOL; i++) {
      const m = new THREE.Mesh(geo, laneMats[0]);
      m.visible = false;
      scene.add(m);
      notePool.push(m);
    }
  }

  function nextMesh() {
    const m = notePool[noteCursor = (noteCursor + 1) % NOTE_POOL];
    m.visible = true;
    return m;
  }

  function onResize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    updateCameraBase();
    const dpr = tier === "low" ? 1 : tier === "medium" ? Math.min(1.4, devicePixelRatio || 1) : Math.min(2, devicePixelRatio || 1);
    renderer.setPixelRatio(dpr);
  }

  // A tall portrait phone needs a higher, wider camera or the highway
  // collapses into the bottom strip of the screen.
  function updateCameraBase() {
    const portrait = camera.aspect < 0.85;
    const rewind = run && run.flags.scrollDir === -1;
    if (rewind) {
      camBaseY = portrait ? 3.4 : 2.7;
      camBaseZ = portrait ? 2.2 : 1.4;
      camera.fov = portrait ? 76 : 64;
    } else {
      camBaseY = portrait ? 3.5 : 2.0;
      camBaseZ = portrait ? 6.6 : 5.6;
      camera.fov = portrait ? 74 : 64;
    }
    camera.updateProjectionMatrix();
  }

  /* ================= quality ================= */
  function setQualityPref(q) {
    quality = q;
    if (q !== "auto") applyTier(q);
  }

  function applyTier(t) {
    if (tier === t) return;
    tier = t;
    scene.fog.far = t === "low" ? 17 : t === "medium" ? 22 : 28;
    highwayGrid.visible = t !== "low";
    sideWalls.forEach((w) => (w.visible = t === "high"));
    const bars = t === "low" ? 0 : t === "medium" ? 16 : 24;
    if (spectrumBars.length !== bars) buildSpectrum(bars);
    if (pools) pools.setBudget(t === "low" ? 0.3 : t === "medium" ? 0.62 : 1);
    onResize();
    const el = document.getElementById("tier-readout");
    if (el) el.textContent = "tier " + t;
  }

  function sampleFps(dt) {
    if (dt <= 0) return;
    fpsBuf.push(1 / dt);
    if (fpsBuf.length > 100) fpsBuf.shift();
    const avg = fpsBuf.reduce((a, b) => a + b, 0) / fpsBuf.length;
    const el = document.getElementById("fps-readout");
    if (el) el.textContent = "FPS " + Math.round(avg);
    if (quality !== "auto" || fpsBuf.length < 70) return;
    if (avg < 38 && tier !== "low") applyTier(tier === "high" ? "medium" : "low");
    else if (avg > 57 && tier === "low") applyTier("medium");
    else if (avg > 59 && tier === "medium") applyTier("high");
  }

  /* ================= palette ================= */
  function applyPalette(pal) {
    paletteHex = pal
      ? [pal.primary, pal.secondary, pal.accent].map((c) => new THREE.Color(c).getHex())
      : [0x7c5cff, 0xff5ca8, 0x5cf0ff];
    receptors.forEach((r, i) => {
      const hex = paletteHex[i % paletteHex.length];
      r.material.color.setHex(hex);
      r.material.emissive.setHex(hex);
    });
    laneMats.forEach((m, i) => {
      const hex = paletteHex[i % paletteHex.length];
      m.color.setHex(hex); m.emissive.setHex(hex);
      holdMats[i].color.setHex(hex); holdMats[i].emissive.setHex(hex);
    });
    spectrumBars.forEach((b, i) => {
      const hex = paletteHex[i % paletteHex.length];
      b.material.color.setHex(hex); b.material.emissive.setHex(hex);
    });
    scene.userData.key.color.setHex(paletteHex[0]);
    scene.userData.rim.color.setHex(paletteHex[2]);
  }

  /* ================= chart preparation ================= */
  function prepareChart(leadNotes, flags, songBeats) {
    let notes = leadNotes.map((n, i) => ({
      id: i, beat: n.beat, lane: n.lane, freq: n.freq || 0,
      type: n.type || "tap", dur: n.dur || 0,
      hidden: false, echo: false, judged: false, mesh: null, channel: 0,
    }));

    if (flags.mirror) notes.forEach((n) => (n.lane = laneCount - 1 - n.lane));

    if (flags.split) notes.forEach((n) => (n.channel = n.lane < laneCount / 2 ? 0 : 1));

    // Blackout: every second 4-bar phrase becomes a hidden repeat of the
    // phrase before it — you hear it lit, then perform it blind.
    if (flags.blackout) {
      const PHRASE = 16; // beats
      const phrases = Math.ceil((songBeats || 0) / PHRASE);
      const rebuilt = [];
      for (let p = 0; p < phrases; p++) {
        const start = p * PHRASE, end = start + PHRASE;
        const inPhrase = notes.filter((n) => n.beat >= start && n.beat < end);
        if (p % 2 === 0) {
          rebuilt.push(...inPhrase);
        } else {
          const prev = notes.filter((n) => n.beat >= start - PHRASE && n.beat < start);
          for (const n of prev) {
            rebuilt.push({ ...n, id: n.id + 100000, beat: n.beat + PHRASE, hidden: true, judged: false, mesh: null });
          }
        }
      }
      notes = rebuilt.sort((a, b) => a.beat - b.beat);
    }

    return notes;
  }

  /* ================= run lifecycle ================= */
  function startRun(cfg) {
    // cfg: { mode, flags, chartId, title, subtitle, song?, buffer?, record?, palette?, lyrics?, art?, chain? }
    const flags = Object.assign({ scrollDir: 1, echoDebt: true }, cfg.flags || {});
    document.body.classList.add("in-game");

    applyPalette(cfg.palette || null);

    run = {
      mode: cfg.mode,
      flags,
      chartId: cfg.chartId,
      title: cfg.title,
      subtitle: cfg.subtitle || "",
      notes: [],
      idx: 0,
      active: [],
      score: 0, combo: 0, maxCombo: 0, flow: 0,
      judge: { perfect: 0, great: 0, good: 0, miss: 0 },
      echoQueue: 0, echoCaught: 0,
      paused: false, ended: false,
      startedAt: performance.now(),
      songBeats: 0,
      inverted: false,
      tapTimes: [],
      genState: cfg.genState || null,
      chain: cfg.chain || null,
      chainIdx: cfg.chainIdx || 0,
      carried: cfg.carried || null,
      ghostPlayer: null,
      ghostRec: Ghost.recorder(),
      isBuffer: !!cfg.buffer,
      lastBeatPulse: -1,
      blackoutActive: false,
      speed: flags.speed || 1,
    };

    if (run.carried) {
      run.score = run.carried.score;
      run.combo = run.carried.combo;
      run.maxCombo = run.carried.maxCombo;
      run.flow = run.carried.flow;
      run.judge = run.carried.judge;
    }

    // ghost
    if (flags.ghostRace) {
      const g = Persist.getGhost("story", cfg.chartId);
      run.ghostPlayer = Ghost.player(g);
      const hud = document.getElementById("ghost-hud");
      hud.classList.toggle("hidden", !run.ghostPlayer);
      if (!run.ghostPlayer) banner("NO GHOST YET", "this run becomes one");
    } else {
      document.getElementById("ghost-hud").classList.add("hidden");
    }

    // HUD chrome
    document.getElementById("hud-mode-chip").textContent = cfg.modeLabel || cfg.mode;
    const album = document.getElementById("album-corner");
    if (cfg.art) {
      album.classList.remove("hidden");
      document.getElementById("album-corner-img").src = cfg.art;
      document.getElementById("album-corner-title").textContent = cfg.title;
      document.getElementById("album-corner-artist").textContent = cfg.subtitle || "";
    } else album.classList.add("hidden");

    const lyricsPanel = document.getElementById("lyrics-panel");
    if (cfg.lyrics) {
      lyricsPanel.classList.remove("hidden");
      lyricsCtl = Lyrics.mount(document.getElementById("lyrics-scroll"));
      lyricsCtl.setCues(Lyrics.parse(cfg.lyrics, cfg.duration || 200));
    } else {
      lyricsPanel.classList.add("hidden");
      lyricsCtl = null;
    }

    buildTouchLanes();
    resetHud();
    pools.clear();
    notePool.forEach((m) => (m.visible = false));
    const rz = flags.scrollDir === -1 ? RECEPTOR_FAR : RECEPTOR_NEAR;
    receptors.forEach((r) => (r.position.z = rz));
    // Rewind puts the hit line at the far end, so sit the camera much
    // closer to it — otherwise the whole run happens in the distance.
    updateCameraBase();
    camera.position.z = camBaseZ;

    // ---- audio + chart ----
    if (cfg.buffer) {
      run.notes = prepareChart(cfg.chart.notes, flags, cfg.chart.notes.length ? cfg.chart.notes[cfg.chart.notes.length - 1].beat : 0);
      run.songBeats = run.notes.length ? run.notes[run.notes.length - 1].beat + 8 : 0;
      AudioEngine.playBuffer(0, cfg.chart.bpm * run.speed);
    } else if (flags.generative) {
      const gen = Composer.generateBars(run.genState, 0, 8, run.genState.bpm);
      run.notes = prepareChart(gen.lead, flags, gen.untilBeat);
      run.songBeats = Infinity;
      AudioEngine.playSong(
        { bpm: run.genState.bpm, beats: gen.untilBeat, layers: gen.layers },
        { onNeedBars: (fromBeat, bpmNow) => onNeedBars(fromBeat, bpmNow) }
      );
    } else {
      const song = cfg.song;
      run.notes = prepareChart(song.lead, flags, song.beats);
      run.songBeats = song.beats;
      AudioEngine.playSong({ bpm: song.bpm * run.speed, beats: song.beats, layers: song.layers });
    }

    if (flags.inversion) run.inversionBeat = run.songBeats * 0.5;

    if (!animId) loop();
    announceMode(cfg);
  }

  function announceMode(cfg) {
    const f = run.flags;
    if (f.tempoFollow) banner("CONDUCTOR", "your tempo drives the song");
    else if (f.blackout) banner("BLACKOUT", "lit phrase, then play it blind");
    else if (f.scrollDir === -1) banner("REWIND", "notes rise — read upward");
    else if (f.split) banner("SPLIT SIGNAL", "two channels, four lanes");
    else if (f.chain) banner("MARATHON", "one combo, all the way down");
    else if (cfg.bannerText) banner(cfg.bannerText, cfg.bannerSub || "");
  }

  /* generative feeder: hands new bars to the audio engine and the chart */
  function onNeedBars(fromBeat, bpmNow) {
    if (!run || !run.genState) return null;
    const st = run.genState;

    if (run.flags.escalate) {
      st.density = Math.min(1.4, (st.density || 0.8) + 0.035);
      st.bpm = Math.min(178, (st.bpm || 120) + 1.6);
    }

    let bpm = st.bpm;
    if (run.flags.tempoFollow) {
      // Conductor: derive tempo from the player's own recent tap spacing
      const t = run.tapTimes;
      if (t.length >= 4) {
        const ivs = [];
        for (let i = 1; i < t.length; i++) ivs.push(t[i] - t[i - 1]);
        ivs.sort((a, b) => a - b);
        const med = ivs[Math.floor(ivs.length / 2)];
        if (med > 0.08 && med < 1.6) {
          let target = 60 / med;
          while (target > 190) target /= 2;
          while (target < 68) target *= 2;
          // ease toward the player rather than snapping
          bpm = st.bpm + (target - st.bpm) * 0.35;
          bpm = Math.max(66, Math.min(190, bpm));
          st.bpm = bpm;
        }
      }
    }

    const gen = Composer.generateBars(st, fromBeat, 4, bpm);
    const prepared = prepareChart(gen.lead, run.flags, gen.untilBeat);
    run.notes.push(...prepared);
    return { layers: gen.layers, untilBeat: gen.untilBeat, startBeat: fromBeat, bpm };
  }

  /* ================= input ================= */
  let bound = false;
  const held = new Set();
  function bindInput() {
    if (bound) return;
    bound = true;

    addEventListener("keydown", (e) => {
      if (e.repeat) return;
      if (e.key === "Escape" && run && !run.ended) { togglePause(); return; }
      const i = LANE_KEYS.indexOf(e.key.toUpperCase());
      if (i >= 0 && !held.has(i)) { held.add(i); tapLane(i); }
    });
    addEventListener("keyup", (e) => {
      const i = LANE_KEYS.indexOf(e.key.toUpperCase());
      if (i >= 0) held.delete(i);
    });

    const el = document.getElementById("touch-lanes");
    el.addEventListener("pointerdown", (e) => {
      const laneEl = e.target.closest(".touch-lane");
      if (!laneEl) return;
      e.preventDefault();
      laneEl.setPointerCapture?.(e.pointerId);
      laneEl.classList.add("pressed");
      tapLane(+laneEl.dataset.lane);
    });
    const release = (e) => {
      const laneEl = e.target.closest?.(".touch-lane");
      if (laneEl) laneEl.classList.remove("pressed");
    };
    el.addEventListener("pointerup", release);
    el.addEventListener("pointercancel", release);
  }

  function buildTouchLanes() {
    const el = document.getElementById("touch-lanes");
    el.innerHTML = "";
    for (let i = 0; i < laneCount; i++) {
      const d = document.createElement("div");
      d.className = "touch-lane";
      d.dataset.lane = i;
      d.dataset.key = document.body.dataset.platform === "mobile" ? "" : LANE_KEYS[i];
      el.appendChild(d);
    }
    const platform = document.body.dataset.platform;
    el.classList.toggle("hidden", platform === "pc" && !Device.hasTouch());
  }

  function tapLane(lane) {
    if (!run || run.paused || run.ended) return;
    flashReceptor(lane);

    if (run.flags.tempoFollow) {
      const now = performance.now() / 1000;
      run.tapTimes.push(now);
      if (run.tapTimes.length > 8) run.tapTimes.shift();
    }

    const nowBeat = AudioEngine.currentBeat();
    const secPerBeat = 60 / Math.max(1, AudioEngine.currentBpm());
    const effLane = run.inverted ? laneCount - 1 - lane : lane;

    let best = null, bestDelta = Infinity;
    for (const n of run.active) {
      if (n.judged || n.lane !== effLane) continue;
      const d = Math.abs(n.beat - nowBeat) * secPerBeat;
      if (d < bestDelta) { bestDelta = d; best = n; }
    }

    if (best && bestDelta <= WINDOWS.good * (run.flow > 0.85 ? 1.25 : 1)) {
      judgeHit(best, bestDelta, lane);
    } else {
      // empty tap: no penalty, but a dry click so the lane still responds
      pools.shockwave(laneX(lane), receptorZ(), 0x30306a, 0.35);
    }
  }

  /* ================= judging ================= */
  function judgeHit(note, delta, pressedLane) {
    note.judged = true;
    const widen = run.flow > 0.85 ? 1.25 : 1;
    let tier2 = "good";
    if (delta <= WINDOWS.perfect * widen) tier2 = "perfect";
    else if (delta <= WINDOWS.great * widen) tier2 = "great";

    run.judge[tier2]++;
    run.combo++;
    run.maxCombo = Math.max(run.maxCombo, run.combo);

    const base = { perfect: 320, great: 210, good: 110 }[tier2];
    const flowMult = run.flow > 0.85 ? 2 : 1;
    const echoBonus = note.echo ? 1.5 : 1;
    run.score += Math.round((base + run.combo * 2) * flowMult * echoBonus);

    run.flow = Math.min(1, run.flow + (tier2 === "perfect" ? 0.05 : tier2 === "great" ? 0.034 : 0.016));

    if (note.echo) { run.echoCaught++; run.echoQueue = Math.max(0, run.echoQueue - 1); }

    // === You Are The Instrument ===
    if (!run.isBuffer && note.freq) {
      AudioEngine.leadNote(note.freq, tier2, { harmonize: run.flow > 0.85 });
    }

    run.ghostRec.record(note.beat, tier2, run.score);

    const color = note.echo ? 0xc08bff : paletteHex[pressedLane % paletteHex.length];
    const rz = receptorZ();
    pools.burst(laneX(pressedLane), 0.22, rz, color, tier2 === "perfect" ? 18 : 11, tier2 === "perfect" ? 1.25 : 0.9);
    pools.shockwave(laneX(pressedLane), rz, color, tier2 === "perfect" ? 1.2 : 0.8);
    if (getOpt("shake")) shake = Math.min(0.5, shake + (tier2 === "perfect" ? 0.1 : 0.05));

    showJudgement(note.echo ? "echo" : tier2);
    updateHud(true);
    hideMesh(note);
  }

  function judgeMiss(note) {
    note.judged = true;
    run.judge.miss++;
    run.combo = 0;
    run.flow = Math.max(0, run.flow - 0.11);
    run.score = Math.max(0, run.score - 20);

    if (run.isBuffer) AudioEngine.duck(1);
    else if (note.freq) AudioEngine.leadNote(note.freq, "dead");

    run.ghostRec.record(note.beat, "miss", run.score);

    // === Echo Debt ===
    if (run.flags.echoDebt && !note.echo && getOpt("echo")) {
      const returnBeat = note.beat + ECHO_DELAY_BEATS;
      if (returnBeat < run.songBeats - 2) {
        run.notes.push({
          id: note.id + 500000, beat: returnBeat, lane: note.lane, freq: note.freq,
          type: "tap", dur: 0, hidden: false, echo: true, judged: false, mesh: null, channel: note.channel,
        });
        run.notes.sort((a, b) => a.beat - b.beat);
        // keep the spawn cursor pointing at the right place after re-sort
        const nowBeat = AudioEngine.currentBeat();
        run.idx = run.notes.findIndex((n) => n.beat > nowBeat + LEAD_BEATS);
        if (run.idx < 0) run.idx = run.notes.length;
        run.echoQueue++;
      }
    }

    showJudgement("miss");
    updateHud(false);
    hideMesh(note);
    flashScreen(0xff5470, 0.28);
  }

  function hideMesh(note) {
    if (note.mesh) { note.mesh.visible = false; note.mesh = null; }
  }

  /* ================= HUD ================= */
  function resetHud() {
    document.getElementById("hud-score").textContent = run.score;
    document.getElementById("hud-combo").classList.add("hidden");
    document.getElementById("flow-fill").style.width = (run.flow * 100) + "%";
    document.getElementById("echo-debt").classList.add("hidden");
  }

  let lastFlowState = false;
  function updateHud(good) {
    const scoreEl = document.getElementById("hud-score");
    scoreEl.textContent = run.score;
    scoreEl.classList.remove("bump"); void scoreEl.offsetWidth; scoreEl.classList.add("bump");

    const comboEl = document.getElementById("hud-combo");
    if (run.combo > 2) {
      comboEl.classList.remove("hidden");
      document.getElementById("combo-num").textContent = run.combo;
      const milestone = run.combo % 25 === 0;
      comboEl.classList.remove("pulse", "milestone");
      void comboEl.offsetWidth;
      comboEl.classList.add(milestone ? "milestone" : "pulse");
      if (milestone) {
        banner(run.combo + " CHAIN", "");
        pools.burst(0, 0.5, receptorZ(), paletteHex[2], 30, 1.6);
        flashScreen(paletteHex[2], 0.3);
      }
    } else comboEl.classList.add("hidden");

    const meter = document.getElementById("flow-meter");
    document.getElementById("flow-fill").style.width = Math.round(run.flow * 100) + "%";
    const inFlow = run.flow > 0.85;
    meter.classList.toggle("maxed", inFlow);
    if (inFlow !== lastFlowState) {
      lastFlowState = inFlow;
      applyFlowState(inFlow);
    }

    const echoEl = document.getElementById("echo-debt");
    echoEl.classList.toggle("hidden", run.echoQueue === 0);
    document.getElementById("echo-count").textContent = run.echoQueue;
  }

  function applyFlowState(on) {
    receptors.forEach((r) => (r.material.emissiveIntensity = on ? 1.5 : 0.55));
    scene.fog.color.setHex(on ? 0x150c2e : 0x05040e);
    renderer.setClearColor(on ? 0x120a26 : 0x05040e, 1);
    scene.userData.key.intensity = on ? 2.6 : 1.6;
    AudioEngine.setLayerGain("pad", on ? 0.55 : 0.32);
    AudioEngine.setLayerGain("lead", on ? 0.62 : 0.5);
    if (on) {
      banner("FLOW STATE", "wider windows · double score · harmonized");
      flashScreen(paletteHex[2], 0.45);
    }
  }

  function showJudgement(t) {
    const el = document.getElementById("judgement-pop");
    el.textContent = t === "echo" ? "ECHO CLEARED" : t.toUpperCase();
    el.className = "judgement-pop j-" + t;
    void el.offsetWidth;
    el.classList.add("show");
  }

  function banner(text, sub) {
    const el = document.getElementById("event-banner");
    el.innerHTML = text + (sub ? `<small>${sub}</small>` : "");
    el.classList.remove("show"); void el.offsetWidth; el.classList.add("show");
  }

  function flashScreen(colorHex, strength) {
    if (!getOpt("particles")) return;
    const el = document.getElementById("game-flash");
    const c = typeof colorHex === "number" ? "#" + colorHex.toString(16).padStart(6, "0") : colorHex;
    el.style.setProperty("--flash-color", c);
    el.style.opacity = "";
    el.classList.remove("fire"); void el.offsetWidth; el.classList.add("fire");
  }

  function flashReceptor(lane) {
    const r = receptors[lane];
    if (r) r.userData.flash = 0.14;
    const el = document.querySelector(`.touch-lane[data-lane="${lane}"]`);
    if (el) { el.classList.add("pressed"); setTimeout(() => el.classList.remove("pressed"), 90); }
  }

  function getOpt(name) {
    const map = { shake: "opt-shake", particles: "opt-particles", trails: "opt-trails", echo: "opt-echo" };
    const el = document.getElementById(map[name]);
    return el ? el.checked : true;
  }

  function laneX(lane) {
    const lw = 4 / laneCount;
    return -2 + lw * lane + lw / 2;
  }

  // The hit line sits near the camera normally and at the far end in
  // Rewind, where notes rise away from you instead of falling toward you.
  const RECEPTOR_NEAR = 2.2, RECEPTOR_FAR = -9.5;
  function receptorZ() {
    return run && run.flags.scrollDir === -1 ? RECEPTOR_FAR : RECEPTOR_NEAR;
  }

  /* ================= loop ================= */
  function loop() {
    animId = requestAnimationFrame(loop);
    const dt = Math.min(0.05, clock.getDelta());
    sampleFps(dt);

    if (run && !run.paused && !run.ended) step(dt);

    pools.update(dt, camera);
    renderer.render(scene, camera);
  }

  function step(dt) {
    const nowBeat = AudioEngine.currentBeat();
    const secPerBeat = 60 / Math.max(1, AudioEngine.currentBpm());
    const dir = run.flags.scrollDir;
    const recZ = receptorZ();

    // lane inversion event
    if (run.flags.inversion && !run.inverted && nowBeat > run.inversionBeat) {
      run.inverted = true;
      banner("INVERSION", "lanes are mirrored from here");
      flashScreen(0xff5470, 0.5);
    }

    // spawn
    while (run.idx < run.notes.length && run.notes[run.idx].beat - LEAD_BEATS <= nowBeat) {
      const n = run.notes[run.idx++];
      if (n.judged) continue;
      if (n.beat < nowBeat - 0.5) continue;
      run.active.push(n);
    }

    // update actives
    for (let i = run.active.length - 1; i >= 0; i--) {
      const n = run.active[i];
      if (n.judged) { run.active.splice(i, 1); continue; }

      const beatsAway = n.beat - nowBeat;
      const z = recZ - dir * beatsAway * UNITS_PER_BEAT;
      const drawLane = run.inverted ? laneCount - 1 - n.lane : n.lane;

      if (!n.mesh) {
        n.mesh = nextMesh();
        n.mesh.material = n.echo ? echoMat : n.hidden ? hiddenMat : (n.type === "hold" ? holdMats[n.lane % laneCount] : laneMats[n.lane % laneCount]);
        const lw = 4 / laneCount;
        n.mesh.scale.set(lw * 0.8, 1, n.type === "hold" ? Math.max(1, n.dur * UNITS_PER_BEAT / 0.34) : 1);
      }
      n.mesh.position.set(laneX(drawLane), 0.17, z);
      n.mesh.visible = true;

      // Blackout fade: hidden notes vanish before they're readable
      if (n.hidden) {
        const fade = Math.max(0, Math.min(1, (beatsAway - 0.8) / 1.2));
        n.mesh.material = hiddenMat;
        hiddenMat.opacity = 0.16 * fade;
      }

      // echoes wobble so they read as different objects at a glance
      if (n.echo) n.mesh.rotation.z = Math.sin(performance.now() / 140 + n.id) * 0.25;

      const lateSec = (nowBeat - n.beat) * secPerBeat;
      if (lateSec > WINDOWS.good + 0.03) {
        judgeMiss(n);
        run.active.splice(i, 1);
      }
    }

    // receptors: idle breathe + press flash
    receptors.forEach((r, i) => {
      if (r.userData.flash > 0) {
        r.userData.flash -= dt;
        r.scale.y = 2.4;
        r.material.emissiveIntensity = 2.2;
      } else {
        r.scale.y += (1 - r.scale.y) * Math.min(1, dt * 14);
        const target = lastFlowState ? 1.5 : 0.55;
        r.material.emissiveIntensity += (target - r.material.emissiveIntensity) * Math.min(1, dt * 8);
      }
    });

    // scrolling grid + beat pulse
    if (highwayGrid.visible) {
      const scroll = (nowBeat * UNITS_PER_BEAT * dir) % 1.6;
      highwayGrid.position.z = scroll;
    }
    const beatPhase = nowBeat - Math.floor(nowBeat);
    const pulse = Math.pow(1 - beatPhase, 3);
    scene.userData.key.intensity = (lastFlowState ? 2.6 : 1.6) + pulse * 1.1;

    // spectrum bars driven by the actual output
    if (spectrumBars.length) {
      if (!spectrumData || spectrumData.length !== AudioEngine.binCount()) {
        spectrumData = new Uint8Array(Math.max(1, AudioEngine.binCount()));
      }
      AudioEngine.getSpectrum(spectrumData);
      const per = Math.max(1, Math.floor(spectrumData.length / spectrumBars.length));
      spectrumBars.forEach((bar, i) => {
        let sum = 0;
        for (let k = 0; k < per; k++) sum += spectrumData[i * per + k] || 0;
        let v = sum / per / 255;
        // Some browsers give an analyser no data until the tab has real audio
        // output; fall back to the beat clock so the bars still perform.
        const fallback = pulse * (0.75 - 0.5 * (i / spectrumBars.length)) + 0.04;
        v = Math.max(v, fallback * (0.35 + 0.65 * run.flow));
        const target = 0.12 + v * 5.4;
        bar.scale.y += (target - bar.scale.y) * Math.min(1, dt * 16);
        bar.position.y = bar.scale.y / 2;
        bar.material.emissiveIntensity = 0.35 + v * 1.5;
      });
    }

    // camera: shake + subtle drift + flow push-in
    shake *= Math.pow(0.0016, dt);
    const shakeAmt = getOpt("shake") ? shake : 0;
    const targetZ = camBaseZ - (lastFlowState ? 0.55 : 0);
    camera.position.x = (Math.random() - 0.5) * shakeAmt * 0.55 + Math.sin(performance.now() / 3400) * 0.06;
    camera.position.y = camBaseY + (Math.random() - 0.5) * shakeAmt * 0.4 + pulse * 0.035;
    camera.position.z += (targetZ - camera.position.z) * Math.min(1, dt * 3);
    camera.lookAt(0, 0.2, dir === 1 ? -6 : -11);

    // ghost
    if (run.ghostPlayer) {
      const g = run.ghostPlayer.update(nowBeat);
      document.getElementById("ghost-score").textContent = g.score;
      const delta = run.score - g.score;
      const dEl = document.getElementById("ghost-delta");
      dEl.textContent = (delta >= 0 ? "+" : "") + delta;
      dEl.className = "ghost-delta " + (delta >= 0 ? "ahead" : "behind");
      if (g.fired && g.fired !== "m" && getOpt("particles")) {
        pools.shockwave(0, recZ, 0x8f8fbb, 0.35);
      }
    }

    if (lyricsCtl) {
      lyricsCtl.update(AudioEngine.beatToTime(nowBeat) - AudioEngine.beatToTime(0));
    }

    // completion
    if (!run.flags.generative && run.idx >= run.notes.length && run.active.length === 0 && nowBeat > run.songBeats - 1) {
      finish();
    }
  }

  /* ================= transport ================= */
  function togglePause() {
    if (!run || run.ended) return;
    run.paused = !run.paused;
    document.getElementById("pause-overlay").classList.toggle("hidden", !run.paused);
    if (run.paused) AudioEngine.pause(); else AudioEngine.resume();
  }

  function quit() {
    if (run) run.ended = true;
    AudioEngine.stop();
    document.body.classList.remove("in-game");
    document.getElementById("pause-overlay").classList.add("hidden");
  }

  function finish() {
    if (!run || run.ended) return;
    run.ended = true;

    // Marathon: roll straight into the next chapter, carrying everything
    if (run.flags.chain && run.chain && run.chainIdx < run.chain.length - 1 && onChainAdvance) {
      const carried = {
        score: run.score, combo: run.combo, maxCombo: run.maxCombo,
        flow: run.flow, judge: run.judge,
      };
      const nextIdx = run.chainIdx + 1;
      AudioEngine.stop();
      onChainAdvance(nextIdx, carried);
      return;
    }

    AudioEngine.stop();
    document.body.classList.remove("in-game");

    const j = run.judge;
    const total = j.perfect + j.great + j.good + j.miss;
    const accuracy = total ? (j.perfect + j.great * 0.72 + j.good * 0.4) / total : 0;
    const grade = accuracy > 0.97 ? "S+" : accuracy > 0.93 ? "S" : accuracy > 0.87 ? "A"
      : accuracy > 0.76 ? "B" : accuracy > 0.62 ? "C" : "D";

    const result = {
      mode: run.mode, chartId: run.chartId, title: run.title,
      score: run.score, maxCombo: run.maxCombo, accuracy, grade,
      judge: j, total, echoCaught: run.echoCaught,
      ghost: run.ghostRec.finish({ score: run.score, grade }),
      ghostFinal: run.ghostPlayer ? run.ghostPlayer.finalScore : null,
      flags: run.flags,
    };
    if (onFinish) onFinish(result);
  }

  function endless() { return run && run.flags.generative; }
  function forceFinish() { if (run && !run.ended) { run.flags.generative = false; finish(); } }

  return {
    init, setQualityPref, startRun, togglePause, quit, forceFinish,
    setOnFinish: (fn) => { onFinish = fn; },
    setOnChainAdvance: (fn) => { onChainAdvance = fn; },
    applyPalette, isEndless: endless,
    getRun: () => run,
  };
})();

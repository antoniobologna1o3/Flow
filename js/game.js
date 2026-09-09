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
  let spectrumBars = [], highway, highwayGrid, gridTex = null, sideWalls = [];
  let notePool = [], noteCursor = 0;
  let ghostMarkers = [];

  let quality = "auto", tier = "medium", renderScale = 0;   // 0 = follow tier
  let fpsBuf = [], animId = null;
  let laneCount = 4;

  let run = null;                        // the whole active-run state
  let onFinish = null, onChainAdvance = null;
  let lyricsCtl = null;
  let spectrumData = null;
  let shake = 0, camKick = 0, camBaseY = 2.0, camBaseZ = 5.6;
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

    // No lights: every material is unlit, which removes all per-pixel
    // lighting work. The "beat pulse" is done by scaling colours instead.

    highway = new THREE.Mesh(
      new THREE.PlaneGeometry(4.2, 40),
      new THREE.MeshBasicMaterial({ color: 0x0d0c20 })
    );
    highway.rotation.x = -Math.PI / 2;
    highway.position.z = -12;
    scene.add(highway);

    // Speed lines used to be 26 separate meshes; now it's one plane with a
    // repeating texture scrolled by UV offset — same look, 1 draw call.
    gridTex = VFX.Textures.gridTexture(THREE);
    gridTex.repeat.set(1, 25);
    highwayGrid = new THREE.Mesh(
      new THREE.PlaneGeometry(4.2, 40),
      new THREE.MeshBasicMaterial({
        map: gridTex, color: 0x4a44880, transparent: true, opacity: 0.42, depthWrite: false,
      })
    );
    highwayGrid.material.color.setHex(0x4a4488);
    highwayGrid.rotation.x = -Math.PI / 2;
    highwayGrid.position.set(0, 0.014, -12);
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
    buildBackdrop();
    buildHopper();

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

      const tileTex = VFX.Textures.tileTexture(THREE);
      const mat = new THREE.MeshBasicMaterial({ map: tileTex, color: 0x7c5cff });
      mat.userData.base = 0x7c5cff;
      laneMats.push(mat);
      const hold = new THREE.MeshBasicMaterial({
        map: tileTex, color: 0x7c5cff, transparent: true, opacity: 0.6,
      });
      hold.userData.base = 0x7c5cff;
      holdMats.push(hold);

      const recMat = new THREE.MeshBasicMaterial({ map: tileTex, color: 0x7c5cff });
      recMat.userData.base = 0x7c5cff;
      const rec = new THREE.Mesh(new THREE.BoxGeometry(lw * 0.86, 0.055, 0.42), recMat);
      rec.position.set(x, 0.05, 2.2);
      scene.add(rec);
      receptors.push(rec);
    }
    const tileTex = VFX.Textures.tileTexture(THREE);
    echoMat = new THREE.MeshBasicMaterial({ map: tileTex, color: 0xc08bff });
    echoMat.userData.base = 0xc08bff;
    hiddenMat = new THREE.MeshBasicMaterial({
      map: tileTex, color: 0x2a2550, transparent: true, opacity: 0.16,
    });
    hiddenMat.userData.base = 0x2a2550;
  }

  function buildSpectrum(count) {
    for (const b of spectrumBars) scene.remove(b);
    spectrumBars = [];
    for (let i = 0; i < count; i++) {
      const side = i < count / 2 ? -1 : 1;
      const idx = i % (count / 2);
      const barMat = new THREE.MeshBasicMaterial({ color: 0x7c5cff });
      barMat.userData.base = 0x7c5cff;
      const bar = new THREE.Mesh(new THREE.BoxGeometry(0.17, 1, 0.17), barMat);
      bar.position.set(side * (3.5 + (idx % 3) * 0.55), 0, 0.4 - idx * 1.7);
      bar.scale.y = 0.1;
      scene.add(bar);
      spectrumBars.push(bar);
    }
  }

  /* ---------- background: depth behind the track ---------- */
  let starField, backGlow, parallax = [], streaks = [];
  let noteDetail = 1;   // 0 = body only, 1 = +glow, 2 = +trail/ring

  function buildBackdrop() {
    // drifting dust — one Points object, effectively free to render
    const count = 260;
    const pos = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      pos[i * 3]     = (Math.random() - 0.5) * 46;
      pos[i * 3 + 1] = Math.random() * 20 - 2;
      pos[i * 3 + 2] = -Math.random() * 46 + 4;
    }
    const geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.BufferAttribute(pos, 3));
    starField = new THREE.Points(geo, new THREE.PointsMaterial({
      color: 0x9a8cff, size: 0.09, transparent: true, opacity: 0.5,
      blending: THREE.AdditiveBlending, depthWrite: false, sizeAttenuation: true,
    }));
    scene.add(starField);

    // the ambient glow that breathes on the beat, sitting behind the track
    backGlow = new THREE.Mesh(
      new THREE.PlaneGeometry(34, 18),
      new THREE.MeshBasicMaterial({
        color: 0x7c5cff, transparent: true, opacity: 0.1,
        blending: THREE.AdditiveBlending, depthWrite: false,
      })
    );
    backGlow.position.set(0, 4, -30);
    scene.add(backGlow);

    // parallax rings drift slower than the track, so depth reads as depth
    for (let i = 0; i < 5; i++) {
      const ring = new THREE.Mesh(
        new THREE.RingGeometry(2.4 + i * 1.1, 2.5 + i * 1.1, 6),
        new THREE.MeshBasicMaterial({
          color: 0x5cf0ff, transparent: true, opacity: 0.07,
          blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide,
        })
      );
      ring.position.set((i % 2 ? 1 : -1) * (5 + i * 1.6), 2.6 + (i % 3) * 2.2, -18 - i * 4.5);
      ring.rotation.z = Math.random() * Math.PI;
      ring.userData.spin = (Math.random() - 0.5) * 0.12;
      ring.userData.depth = 0.16 + i * 0.07;   // parallax factor
      scene.add(ring);
      parallax.push(ring);
    }

    // light streaks flying past the corridor
    for (let i = 0; i < 8; i++) {
      const s = new THREE.Mesh(
        new THREE.PlaneGeometry(0.05, 5),
        new THREE.MeshBasicMaterial({
          color: 0x5cf0ff, transparent: true, opacity: 0.16,
          blending: THREE.AdditiveBlending, depthWrite: false,
        })
      );
      s.rotation.x = -Math.PI / 2;
      s.position.set((Math.random() - 0.5) * 26, 0.4 + Math.random() * 6, -Math.random() * 40);
      s.userData.speed = 8 + Math.random() * 16;
      scene.add(s);
      streaks.push(s);
    }
  }

  function updateBackdrop(dt, pulse, level) {
    if (starField) {
      starField.rotation.y += dt * 0.012;
      starField.position.z = (starField.position.z + dt * 1.4) % 8;
      starField.material.opacity = 0.34 + pulse * 0.3 + level * 0.2;
    }
    if (backGlow) {
      // beat-synced ambient pulse behind the track
      const target = 0.08 + pulse * 0.13 + level * 0.12 + (lastFlowState ? 0.1 : 0);
      backGlow.material.opacity += (target - backGlow.material.opacity) * Math.min(1, dt * 10);
      backGlow.scale.setScalar(1 + pulse * 0.06);
    }
    for (const r of parallax) {
      r.rotation.z += r.userData.spin * dt;
      r.position.z += dt * r.userData.depth * 6;
      if (r.position.z > 6) r.position.z = -40;
      r.material.opacity = 0.05 + pulse * 0.05;
    }
    if (tier !== "low") {
      for (const s of streaks) {
        s.position.z += dt * s.userData.speed;
        if (s.position.z > 8) {
          s.position.z = -42;
          s.position.x = (Math.random() - 0.5) * 26;
          s.position.y = 0.4 + Math.random() * 6;
        }
        s.material.opacity = 0.08 + pulse * 0.16;
      }
    }
  }

  /* ---------- Tile Hopper: a ball that jumps the tiles ---------- */
  let hopper = null;

  function buildHopper() {
    const g = new THREE.Group();
    const ball = new THREE.Mesh(
      new THREE.SphereGeometry(0.27, 20, 14),
      (() => { const m = new THREE.MeshBasicMaterial({ color: 0xbfa8ff }); m.userData.base = 0xbfa8ff; return m; })()
    );
    g.add(ball);
    const halo = new THREE.Mesh(
      new THREE.PlaneGeometry(0.85, 0.85),
      new THREE.MeshBasicMaterial({
        color: 0x7c5cff, transparent: true, opacity: 0.2,
        blending: THREE.AdditiveBlending, depthWrite: false,
      })
    );
    halo.rotation.x = -Math.PI / 2;
    halo.position.y = -0.22;
    g.add(halo);
    g.visible = false;
    scene.add(g);
    hopper = { group: g, ball, halo, lane: 1, fromX: 0, toX: 0, t: 1, hopDur: 0.2, squash: 0, fell: 0 };
  }

  function hopTo(lane, power = 1) {
    if (!hopper) return;
    hopper.fromX = hopper.group.position.x;
    hopper.toX = laneX(lane);
    hopper.lane = lane;
    hopper.t = 0;
    hopper.hopDur = 0.19;
    hopper.power = power;
  }

  function updateHopper(dt, recZ) {
    if (!hopper || !hopper.group.visible) return;
    hopper.t = Math.min(1, hopper.t + dt / hopper.hopDur);
    const k = hopper.t;
    const x = hopper.fromX + (hopper.toX - hopper.fromX) * (k * k * (3 - 2 * k));
    const arc = Math.sin(k * Math.PI) * 0.75 * (hopper.power || 1);
    const land = k >= 1 ? 1 : 0;

    // squash on landing, stretch at the top of the arc
    hopper.squash = Math.max(0, hopper.squash - dt * 6);
    if (land && hopper.squash === 0 && hopper._airborne) {
      hopper.squash = 1;
      hopper._airborne = false;
      pools.burst(x, 0.1, recZ, paletteHex[hopper.lane % paletteHex.length], 6, 0.5);
    }
    if (k < 1) hopper._airborne = true;

    hopper.fell = Math.max(0, hopper.fell - dt * 2.2);
    const sq = hopper.squash;
    hopper.group.position.set(x, 0.26 + arc - hopper.fell * 0.5, recZ);
    hopper.ball.scale.set(1 + sq * 0.4 + arc * 0.1, 1 - sq * 0.35 + arc * 0.18, 1 + sq * 0.4);
    hopper.halo.scale.setScalar(1 + arc * 0.5);
    hopper.halo.material.opacity = 0.24 - arc * 0.14;
    setGlow(hopper.ball.material, 0.85 + (lastFlowState ? 0.5 : 0) + sq * 0.6);
  }

  // Each pooled note is a small rig: the beveled body, an additive rim
  // glow that lifts it off the dark track, a motion trail that sells the
  // approach speed, and a ring that only appears on special notes.
  const NOTE_GEO = new THREE.BoxGeometry(1, 0.26, 0.34);
  const GLOW_GEO = new THREE.PlaneGeometry(1.5, 1.0);
  const TRAIL_GEO = new THREE.PlaneGeometry(1, 1);
  const RING_GEO = new THREE.RingGeometry(0.44, 0.52, 24);

  function buildNotePool() {
    for (let i = 0; i < NOTE_POOL; i++) {
      const group = new THREE.Group();

      const body = new THREE.Mesh(NOTE_GEO, laneMats[0]);
      group.add(body);

      const glow = new THREE.Mesh(GLOW_GEO, new THREE.MeshBasicMaterial({
        color: 0x7c5cff, transparent: true, opacity: 0.2,
        blending: THREE.AdditiveBlending, depthWrite: false,
      }));
      glow.rotation.x = -Math.PI / 2;
      glow.position.y = -0.12;
      group.add(glow);

      const trail = new THREE.Mesh(TRAIL_GEO, new THREE.MeshBasicMaterial({
        color: 0x7c5cff, transparent: true, opacity: 0.22,
        blending: THREE.AdditiveBlending, depthWrite: false,
      }));
      trail.rotation.x = -Math.PI / 2;
      trail.position.y = -0.1;
      group.add(trail);

      const ring = new THREE.Mesh(RING_GEO, new THREE.MeshBasicMaterial({
        color: 0xffffff, transparent: true, opacity: 0,
        blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide,
      }));
      ring.rotation.x = -Math.PI / 2;
      ring.position.y = -0.06;
      ring.visible = false;
      group.add(ring);

      group.visible = false;
      scene.add(group);
      notePool.push({ group, body, glow, trail, ring });
    }
  }

  function nextMesh() {
    const rig = notePool[noteCursor = (noteCursor + 1) % NOTE_POOL];
    rig.group.visible = true;
    return rig;
  }

  function onResize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    updateCameraBase();
    // Pixel count dominates cost on integrated GPUs, so low tier renders
    // *below* native and lets the browser upscale. Users can override.
    const auto = tier === "low" ? 0.65 : tier === "medium" ? 0.9 : Math.min(1.35, devicePixelRatio || 1);
    const scale = renderScale > 0 ? renderScale : auto;
    renderer.setPixelRatio(Math.max(0.4, Math.min(2, scale)));
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

  function setRenderScale(v) { renderScale = v; onResize(); }

  function setRenderScale(v) { renderScale = v; onResize(); }

  function applyTier(t) {
    if (tier === t) return;
    tier = t;
    scene.fog.far = t === "low" ? 17 : t === "medium" ? 22 : 28;
    highwayGrid.visible = t === "high";
    sideWalls.forEach((w) => (w.visible = t === "high"));
    const bars = t === "low" ? 0 : t === "medium" ? 16 : 24;
    if (spectrumBars.length !== bars) buildSpectrum(bars);
    if (pools) pools.setBudget(t === "low" ? 0.3 : t === "medium" ? 0.62 : 1);

    // The new background layers are the first thing to go on weak hardware:
    // the beat-pulsed glow stays (it carries the music), the fill-rate-heavy
    // extras don't.
    if (starField) {
      starField.visible = t !== "low";
      starField.material.size = t === "high" ? 0.09 : 0.07;
    }
    parallax.forEach((r) => (r.visible = t === "high"));
    streaks.forEach((s) => (s.visible = t !== "low"));
    if (backGlow) backGlow.visible = t !== "low";
    noteDetail = t === "low" ? 0 : t === "medium" ? 1 : 2;
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
      r.material.userData.base = hex;
    });
    laneMats.forEach((m, i) => {
      const hex = paletteHex[i % paletteHex.length];
      m.color.setHex(hex); m.userData.base = hex;
      holdMats[i].color.setHex(hex); holdMats[i].userData.base = hex;
    });
    spectrumBars.forEach((b, i) => {
      const hex = paletteHex[i % paletteHex.length];
      b.material.color.setHex(hex); b.material.userData.base = hex;
    });

    if (backGlow) backGlow.material.color.setHex(paletteHex[0]);
    if (starField) starField.material.color.setHex(paletteHex[2]);
    parallax.forEach((r, i) => r.material.color.setHex(paletteHex[(i + 1) % paletteHex.length]));
    streaks.forEach((s2) => s2.material.color.setHex(paletteHex[2]));
    if (hopper) {
      hopper.ball.material.userData.base = paletteHex[0];
      setGlow(hopper.ball.material, 0.9);
      hopper.halo.material.color.setHex(paletteHex[0]);
    }
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
    notePool.forEach((r) => { r.group.visible = false; r.group.rotation.z = 0; });
    const rz = flags.scrollDir === -1 ? RECEPTOR_FAR : RECEPTOR_NEAR;
    receptors.forEach((r) => (r.position.z = rz));

    shake = 0; camKick = 0;
    const wash = document.getElementById("combo-wash");
    if (wash) wash.style.opacity = 0;
    if (hopper) {
      hopper.group.visible = !!flags.hopper;
      hopper.lane = Math.floor(laneCount / 2);
      hopper.fromX = hopper.toX = laneX(hopper.lane);
      hopper.group.position.set(hopper.toX, 0.26, rz);
      hopper.t = 1; hopper.fell = 0; hopper.squash = 0;
      hopper.ball.material.userData.base = paletteHex[0];
      setGlow(hopper.ball.material, 0.9);
      hopper.halo.material.color.setHex(paletteHex[0]);
    }
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
    floatJudgement(note.echo ? "echo" : tier2, pressedLane);
    if (run.flags.hopper) hopTo(pressedLane, tier2 === "perfect" ? 1.25 : 1);
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
    floatJudgement("miss", note.lane);
    if (run.flags.hopper && hopper) hopper.fell = 1;
    updateHud(false);
    hideMesh(note);
    flashScreen(0xff5470, 0.28);
  }

  function hideMesh(note) {
    if (note.mesh) {
      note.mesh.group.visible = false;
      note.mesh.group.rotation.z = 0;
      note.mesh = null;
    }
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

      // the combo is the hype number: it physically grows as the streak does
      comboEl.style.setProperty("--combo-scale", (1 + Math.min(0.85, run.combo / 160)).toFixed(3));

      const milestone = MILESTONES.includes(run.combo);
      comboEl.classList.remove("pulse", "milestone");
      void comboEl.offsetWidth;
      comboEl.classList.add(milestone ? "milestone" : "pulse");
      if (milestone) celebrateMilestone(run.combo);
    } else {
      comboEl.classList.add("hidden");
      comboEl.style.setProperty("--combo-scale", "1");
    }
    updateComboWash();

    const meter = document.getElementById("flow-meter");
    document.getElementById("flow-fill").style.width = Math.round(run.flow * 100) + "%";
    const inFlow = run.flow > 0.85;
    meter.classList.toggle("maxed", inFlow);
    meter.classList.toggle("near", !inFlow && run.flow > 0.65);
    if (inFlow !== lastFlowState) {
      lastFlowState = inFlow;
      applyFlowState(inFlow);
    }

    const echoEl = document.getElementById("echo-debt");
    echoEl.classList.toggle("hidden", run.echoQueue === 0);
    const echoNum = document.getElementById("echo-count");
    if (echoNum.textContent !== String(run.echoQueue)) {
      echoNum.textContent = run.echoQueue;
      echoEl.classList.remove("bump"); void echoEl.offsetWidth; echoEl.classList.add("bump");
    }
  }

  const MILESTONES = [10, 25, 50, 100, 150, 200, 300, 500];

  // Escalating payoff: each threshold hits harder than the last.
  function celebrateMilestone(combo) {
    const step = MILESTONES.indexOf(combo);
    const power = 1 + step * 0.45;
    banner(combo + " CHAIN", step >= 3 ? "unbroken" : "");
    pools.burst(0, 0.6, receptorZ(), paletteHex[2], Math.round(22 + step * 12), 1.3 + step * 0.35);
    for (let i = 0; i < laneCount; i++) {
      pools.shockwave(laneX(i), receptorZ(), paletteHex[i % paletteHex.length], 1 + step * 0.3);
    }
    flashScreen(paletteHex[2], 0.45);
    if (getOpt("shake")) shake = Math.min(1.1, shake + 0.28 * power);   // camera punch
    camKick = Math.min(1, 0.35 + step * 0.16);
    const wash = document.getElementById("combo-wash");
    wash.classList.remove("surge"); void wash.offsetWidth; wash.classList.add("surge");
  }

  // Screen-edge colour wash that builds with the streak.
  function updateComboWash() {
    const wash = document.getElementById("combo-wash");
    if (!wash) return;
    const c = paletteHex[2].toString(16).padStart(6, "0");
    wash.style.setProperty("--wash", "#" + c);
    wash.style.opacity = run.combo > 8
      ? Math.min(0.42, (run.combo - 8) / 260 + (lastFlowState ? 0.12 : 0)).toFixed(3)
      : 0;
  }

  function applyFlowState(on) {
    receptors.forEach((r) => setGlow(r.material, on ? 1.35 : 0.9));
    scene.fog.color.setHex(on ? 0x150c2e : 0x05040e);
    renderer.setClearColor(on ? 0x120a26 : 0x05040e, 1);

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

  // A second, smaller judgement that pops at the tile's own screen
  // position, so your eye never has to leave the lane you just hit.
  const floatPool = [];
  let floatCursor = 0;
  const _projV = new THREE.Vector3();
  function floatJudgement(tier2, lane) {
    const host = document.getElementById("judge-float");
    if (!host) return;
    if (!floatPool.length) {
      for (let i = 0; i < 8; i++) {
        const b = document.createElement("b");
        host.appendChild(b);
        floatPool.push(b);
      }
    }
    _projV.set(laneX(lane), 0.75, receptorZ()).project(camera);
    if (_projV.z > 1) return;
    const el = floatPool[floatCursor = (floatCursor + 1) % floatPool.length];
    el.textContent = tier2 === "echo" ? "ECHO" : tier2.toUpperCase();
    el.className = "j-" + tier2;
    el.style.left = ((_projV.x * 0.5 + 0.5) * 100) + "%";
    el.style.top = ((-_projV.y * 0.5 + 0.5) * 100) + "%";
    void el.offsetWidth;
    el.classList.add("fire");
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

  // With unlit materials there is no emissive channel, so "glow" means
  // scaling the material's base colour. Cheaper, and looks the same on a
  // dark track.
  const _glowC = new THREE.Color();
  function setGlow(mat, intensity) {
    const base = mat.userData.base;
    if (base === undefined) return;
    _glowC.setHex(base).multiplyScalar(Math.max(0.15, intensity));
    mat.color.copy(_glowC);
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
        const special = n.echo || n.type === "burst" || n.type === "hold";
        n.mesh.body.material = n.echo ? echoMat : n.hidden ? hiddenMat
          : (n.type === "hold" ? holdMats[n.lane % laneCount] : laneMats[n.lane % laneCount]);
        const lw = 4 / laneCount;
        n.mesh.body.scale.set(lw * 0.8, 1, n.type === "hold" ? Math.max(1, n.dur * UNITS_PER_BEAT / 0.34) : 1);

        const tint = n.echo ? 0xc08bff : paletteHex[n.lane % paletteHex.length];
        n.mesh.glow.material.color.setHex(tint);
        n.mesh.trail.material.color.setHex(tint);
        n.mesh.ring.material.color.setHex(n.echo ? 0xc08bff : paletteHex[2]);
        n.mesh.glow.scale.set(lw * 0.9, 1, 1);
        n.mesh.glow.visible = noteDetail >= 1 && !n.hidden && getOpt("particles");
        n.mesh.trail.visible = noteDetail >= 2 && !n.hidden && getOpt("trails");
        // the portal ring is a moment, not decoration — specials only
        n.mesh.ring.visible = noteDetail >= 1 && special && !n.hidden;
      }
      n.mesh.group.position.set(laneX(drawLane), 0.17, z);
      n.mesh.group.visible = true;

      // motion trail stretches behind along travel direction
      if (n.mesh.trail.visible) {
        const len = 1.5 + Math.min(2.4, UNITS_PER_BEAT * 0.55);
        n.mesh.trail.scale.set((4 / laneCount) * 0.55, len, 1);
        n.mesh.trail.position.z = dir * len * 0.5;
        n.mesh.trail.material.opacity = 0.2 * Math.min(1, beatsAway / 1.4);
      }

      // approach ring tightens as a special note nears the line
      if (n.mesh.ring.visible) {
        const k = Math.max(0, Math.min(1, beatsAway / LEAD_BEATS));
        n.mesh.ring.scale.setScalar(0.7 + k * 2.6);
        n.mesh.ring.material.opacity = 0.75 * (1 - k) * (1 - k);
        n.mesh.ring.rotation.z += dt * 1.6;
      }

      // Blackout fade: hidden notes vanish before they're readable
      if (n.hidden) {
        const fade = Math.max(0, Math.min(1, (beatsAway - 0.8) / 1.2));
        n.mesh.body.material = hiddenMat;
        hiddenMat.opacity = 0.16 * fade;
      }

      // echoes wobble so they read as different objects at a glance
      if (n.echo) n.mesh.group.rotation.z = Math.sin(performance.now() / 140 + n.id) * 0.25;

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
        setGlow(r.material, 1.7);
      } else {
        r.scale.y += (1 - r.scale.y) * Math.min(1, dt * 14);
        const target = lastFlowState ? 1.35 : 0.9;
        setGlow(r.material, target);
      }
    });

    // scrolling grid + beat pulse
    if (highwayGrid.visible && gridTex) {
      gridTex.offset.y = (-nowBeat * dir * 0.55) % 1;
    }
    const beatPhase = nowBeat - Math.floor(nowBeat);
    const pulse = Math.pow(1 - beatPhase, 3);


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
        const target = 0.1 + v * 2.3;
        bar.scale.y += (target - bar.scale.y) * Math.min(1, dt * 16);
        bar.position.y = bar.scale.y / 2;
        setGlow(bar.material, 0.45 + v * 0.9);
      });
    }

    updateBackdrop(dt, pulse, AudioEngine.getLevel());
    updateHopper(dt, recZ);

    // camera: shake + subtle drift + flow push-in
    shake *= Math.pow(0.0016, dt);
    camKick *= Math.pow(0.02, dt);
    const shakeAmt = getOpt("shake") ? shake : 0;
    const targetZ = camBaseZ - (lastFlowState ? 0.55 : 0) - camKick * 1.5;
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
    init, setQualityPref, setRenderScale, startRun, togglePause, quit, forceFinish,
    setOnFinish: (fn) => { onFinish = fn; },
    setOnChainAdvance: (fn) => { onChainAdvance = fn; },
    applyPalette, isEndless: endless,
    getRun: () => run,
    // draw-call / triangle counts, for the perf readout and for tuning
    stats: () => (renderer ? { ...renderer.info.render, programs: renderer.info.programs ? renderer.info.programs.length : 0 } : null),
  };
})();

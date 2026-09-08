/* Core gameplay: Three.js note highway, adaptive-quality 3D rendering,
   keyboard + touch input, judgement/scoring, Flow State (the unique hook),
   right-side lyrics sync, and album-art-driven color theming. */
const Game = (() => {
  const LANE_COUNT = 4;
  const LANE_KEYS_PC = ["D", "F", "J", "K"];
  const APPROACH_TIME = 1.9; // seconds a note takes to travel the highway
  const HIT_WINDOWS = { perfect: 0.05, great: 0.1, good: 0.16 };

  let renderer, scene, camera, clock;
  let laneMeshes = [], receptorMeshes = [], noteGroup, particleGroup;
  let quality = "auto";
  let effectiveQuality = "medium";
  let fpsHistory = [];
  let lastFrameT = 0;
  let animId = null;

  let chart = null;       // { notes, duration, bpm }
  let source = null;      // 'story' | 'library'
  let sourceMeta = null;  // chapter or track record
  let activeNotes = [];   // spawned note objects with mesh + state
  let noteIdx = 0;
  let score = 0, combo = 0, maxCombo = 0;
  let judgeCounts = { perfect: 0, great: 0, good: 0, miss: 0 };
  let flow = 0; // 0..1
  let paused = false;
  let ended = false;
  let lyricsCtl = null;
  let onFinish = null;
  let laneColorBase = null;
  let heldLaneKeys = new Set();
  let pressFlashUntil = [0, 0, 0, 0];

  function init() {
    const canvas = document.getElementById("game-canvas");
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: "high-performance" });
    renderer.setClearColor(0x05050f, 1);
    scene = new THREE.Scene();
    scene.fog = new THREE.Fog(0x05050f, 6, 22);
    camera = new THREE.PerspectiveCamera(64, 1, 0.1, 60);
    camera.position.set(0, 2.0, 5.6);
    camera.lookAt(0, 0.2, -6);

    const ambient = new THREE.AmbientLight(0xffffff, 0.55);
    scene.add(ambient);
    const key = new THREE.PointLight(0x7c5cff, 1.4, 20);
    key.position.set(0, 4, 2);
    scene.add(key);

    // Highway
    const highwayGeo = new THREE.PlaneGeometry(4, 24, 1, 1);
    const highwayMat = new THREE.MeshStandardMaterial({ color: 0x11112a, roughness: 0.85, metalness: 0.1 });
    const highway = new THREE.Mesh(highwayGeo, highwayMat);
    highway.rotation.x = -Math.PI / 2;
    highway.position.z = -8;
    scene.add(highway);

    laneMeshes = [];
    receptorMeshes = [];
    const laneWidth = 4 / LANE_COUNT;
    for (let i = 0; i < LANE_COUNT; i++) {
      const x = -2 + laneWidth * i + laneWidth / 2;
      const divGeo = new THREE.PlaneGeometry(0.02, 24);
      const divMat = new THREE.MeshBasicMaterial({ color: 0x2a2a55, transparent: true, opacity: 0.7 });
      const div = new THREE.Mesh(divGeo, divMat);
      div.rotation.x = -Math.PI / 2;
      div.position.set(-2 + laneWidth * i, 0.01, -8);
      scene.add(div);

      const recGeo = new THREE.BoxGeometry(laneWidth * 0.85, 0.06, 0.4);
      const recMat = new THREE.MeshStandardMaterial({ color: 0x7c5cff, emissive: 0x7c5cff, emissiveIntensity: 0.6 });
      const rec = new THREE.Mesh(recGeo, recMat);
      rec.position.set(x, 0.05, 2.2);
      scene.add(rec);
      receptorMeshes.push(rec);
      laneMeshes.push({ x });
    }

    noteGroup = new THREE.Group();
    scene.add(noteGroup);
    particleGroup = new THREE.Group();
    scene.add(particleGroup);

    clock = new THREE.Clock();
    window.addEventListener("resize", onResize);
    onResize();
    setupInput();
  }

  function onResize() {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    const dpr = effectiveQuality === "low" ? 1 : effectiveQuality === "medium" ? Math.min(1.5, window.devicePixelRatio || 1) : Math.min(2, window.devicePixelRatio || 1);
    renderer.setPixelRatio(dpr);
  }

  function setQualityPref(q) { quality = q; applyQuality(effectiveQuality); }

  function applyQuality(tier) {
    effectiveQuality = tier;
    scene.fog.far = tier === "low" ? 14 : tier === "medium" ? 18 : 24;
    renderer.shadowMap.enabled = false;
    onResize();
  }

  // ---------- Input ----------
  let inputHandlersBound = false;
  function setupInput() {
    if (inputHandlersBound) return;
    inputHandlersBound = true;
    window.addEventListener("keydown", (e) => {
      if (paused || ended) return;
      const idx = LANE_KEYS_PC.indexOf(e.key.toUpperCase());
      if (idx >= 0 && !heldLaneKeys.has(idx)) { heldLaneKeys.add(idx); hitLane(idx); flashLane(idx); }
      if (e.key === "Escape") togglePause();
    });
    window.addEventListener("keyup", (e) => {
      const idx = LANE_KEYS_PC.indexOf(e.key.toUpperCase());
      if (idx >= 0) heldLaneKeys.delete(idx);
    });

    const touchLanesEl = document.getElementById("touch-lanes");
    touchLanesEl.addEventListener("pointerdown", (e) => {
      if (paused || ended) return;
      const laneEl = e.target.closest(".touch-lane");
      if (!laneEl) return;
      const idx = parseInt(laneEl.dataset.lane, 10);
      hitLane(idx);
      laneEl.classList.add("pressed");
    });
    touchLanesEl.addEventListener("pointerup", (e) => {
      const laneEl = e.target.closest(".touch-lane");
      if (laneEl) laneEl.classList.remove("pressed");
    });
    touchLanesEl.addEventListener("pointerleave", (e) => {
      const laneEl = e.target.closest(".touch-lane");
      if (laneEl) laneEl.classList.remove("pressed");
    }, true);
  }

  function flashLane(idx) { pressFlashUntil[idx] = performance.now() + 120; }

  function buildTouchLanes() {
    const el = document.getElementById("touch-lanes");
    el.innerHTML = "";
    for (let i = 0; i < LANE_COUNT; i++) {
      const d = document.createElement("div");
      d.className = "touch-lane";
      d.dataset.lane = i;
      d.dataset.key = LANE_KEYS_PC[i];
      el.appendChild(d);
    }
  }

  // ---------- Chart loading ----------
  function loadStoryChapter(chapterId) {
    const ch = StoryData.getChapter(chapterId);
    chart = ch.track;
    source = "story";
    sourceMeta = ch;
    laneColorBase = null;
    resetPalette();
    applyPaletteToScene(null);
    startRun(() => AudioEngine.playSynthTrack(chart.notes.filter((n) => n.lane >= 0), chart.duration));
  }

  async function loadLibraryTrack(record) {
    source = "library";
    sourceMeta = record;
    ColorExtract.applyPalette(record.palette);
    applyPaletteToScene(record.palette);
    AudioEngine.ensureCtx();
    const buffer = await AudioEngine.loadFile(record.audioBlob);
    const auto = await AudioEngine.autoChart(buffer, LANE_COUNT);
    chart = { notes: auto.notes.map((n) => ({ ...n, gain: 0.5, wave: "sine", freq: 0 })), duration: auto.duration, bpm: auto.bpm };
    startRun(() => AudioEngine.playBuffer(0));
  }

  function resetPalette() {
    document.documentElement.style.removeProperty("--game-primary");
    document.documentElement.style.removeProperty("--game-secondary");
    document.documentElement.style.removeProperty("--game-accent");
  }

  // Bar/receptor/note colors take their reference directly from the album
  // art's extracted palette in Your Music mode, so the whole highway is
  // visually "of" that album, not a generic reskin.
  function applyPaletteToScene(palette) {
    laneColorBase = palette
      ? [palette.primary, palette.secondary, palette.accent].map((c) => new THREE.Color(c).getHex())
      : null;
    const defaultColor = 0x7c5cff;
    receptorMeshes.forEach((r, i) => {
      const hex = laneColorBase ? laneColorBase[i % laneColorBase.length] : defaultColor;
      r.material.color.setHex(hex);
      r.material.emissive.setHex(hex);
    });
  }

  function startRun(playFn) {
    score = 0; combo = 0; maxCombo = 0; flow = 0; noteIdx = 0; ended = false; paused = false;
    judgeCounts = { perfect: 0, great: 0, good: 0, miss: 0 };
    activeNotes.forEach((n) => noteGroup.remove(n.mesh));
    activeNotes = [];
    document.getElementById("hud-score").textContent = "0";
    document.getElementById("hud-combo").classList.add("hidden");
    document.getElementById("flow-fill").style.width = "0%";

    buildTouchLanes();
    const touchLanesEl = document.getElementById("touch-lanes");
    const needsTouch = document.body.dataset.platform !== "pc" || Device.hasTouch();
    touchLanesEl.classList.toggle("hidden", !needsTouch);

    // Lyrics panel (right side)
    const lyricsPanel = document.getElementById("lyrics-panel");
    const lyricsScroll = document.getElementById("lyrics-scroll");
    if (source === "library" && sourceMeta.lyricsText) {
      lyricsPanel.classList.remove("hidden");
      lyricsCtl = Lyrics.mount(lyricsScroll);
      lyricsCtl.setCues(Lyrics.parse(sourceMeta.lyricsText, chart.duration));
    } else {
      lyricsPanel.classList.add("hidden");
      lyricsCtl = null;
    }

    // Album corner (Your Music mode)
    const albumCorner = document.getElementById("album-corner");
    if (source === "library") {
      albumCorner.classList.remove("hidden");
      document.getElementById("album-corner-img").src = sourceMeta.artDataUrl;
      document.getElementById("album-corner-title").textContent = sourceMeta.title;
      document.getElementById("album-corner-artist").textContent = sourceMeta.artist;
    } else {
      albumCorner.classList.add("hidden");
    }

    // Build note meshes ahead of time (instanced-ish: reuse simple boxes,
    // capped count keeps this cheap even on low-end tier).
    playFn();
    if (!animId) loop();
  }

  const NOTE_GEO = new THREE.BoxGeometry(0.62, 0.28, 0.28);
  function laneWidthUnit() { return 4 / LANE_COUNT; }
  function laneX(lane) { return -2 + laneWidthUnit() * lane + laneWidthUnit() / 2; }

  function spawnNote(n) {
    const palette = laneColorBase;
    const color = palette
      ? palette[n.lane % palette.length]
      : (n.type === "hold" ? 0x5cf0ff : n.type === "burst" ? 0xff5ca8 : 0x7c5cff);
    const mat = new THREE.MeshStandardMaterial({ color, emissive: color, emissiveIntensity: 0.5, roughness: 0.4 });
    const mesh = new THREE.Mesh(NOTE_GEO, mat);
    mesh.position.set(laneX(n.lane), 0.15, -12);
    if (n.type === "hold") mesh.scale.z = Math.max(1, (n.dur || 0.3) * 4);
    noteGroup.add(mesh);
    return { data: n, mesh, judged: false };
  }

  function popParticles(lane, colorHex) {
    if (getSettings().particles === false) return;
    const count = effectiveQuality === "low" ? 4 : effectiveQuality === "medium" ? 8 : 14;
    for (let i = 0; i < count; i++) {
      const geo = new THREE.SphereGeometry(0.045, 5, 5);
      const mat = new THREE.MeshBasicMaterial({ color: colorHex });
      const m = new THREE.Mesh(geo, mat);
      m.position.set(laneX(lane) + (Math.random() - 0.5) * 0.3, 0.2, 2.2);
      m.userData.v = new THREE.Vector3((Math.random() - 0.5) * 2.4, Math.random() * 2.6 + 0.6, (Math.random() - 0.5) * 1.2);
      m.userData.life = 0.5 + Math.random() * 0.3;
      particleGroup.add(m);
    }
  }

  function getSettings() {
    return {
      particles: document.getElementById("opt-particles")?.checked ?? true,
      shake: document.getElementById("opt-shake")?.checked ?? true,
    };
  }

  function hitLane(lane) {
    const now = AudioEngine.currentTime();
    let best = null, bestDelta = Infinity;
    for (const n of activeNotes) {
      if (n.judged || n.data.lane !== lane) continue;
      const delta = Math.abs(n.data.time - now);
      if (delta < bestDelta) { bestDelta = delta; best = n; }
    }
    if (best && bestDelta <= HIT_WINDOWS.good) {
      judge(best, bestDelta);
    } else {
      // No note close enough: small combo-safe miss feedback only if there
      // truly is nothing pending nearby (avoids punishing free tapping).
    }
  }

  function judge(noteObj, delta) {
    noteObj.judged = true;
    let tier = "good";
    if (delta <= HIT_WINDOWS.perfect) tier = "perfect";
    else if (delta <= HIT_WINDOWS.great) tier = "great";
    judgeCounts[tier]++;
    const pts = { perfect: 300, great: 200, good: 100 }[tier];
    combo++;
    maxCombo = Math.max(maxCombo, combo);
    score += pts + combo * 2;
    flow = Math.min(1, flow + (tier === "perfect" ? 0.045 : tier === "great" ? 0.03 : 0.015));
    updateHud();
    showJudgement(tier);
    const colorHex = { perfect: 0x5cf0ff, great: 0x7cff5c, good: 0xffd75c }[tier];
    popParticles(noteObj.data.lane, colorHex);
    noteGroup.remove(noteObj.mesh);
  }

  function missNote(noteObj) {
    noteObj.judged = true;
    judgeCounts.miss++;
    combo = 0;
    flow = Math.max(0, flow - 0.08);
    updateHud();
    showJudgement("miss");
    noteGroup.remove(noteObj.mesh);
  }

  function showJudgement(tier) {
    const el = document.getElementById("judgement-pop");
    el.textContent = tier.toUpperCase() + (tier === "miss" ? "" : "!");
    el.className = "judgement-pop show j-" + tier;
    void el.offsetWidth;
    el.classList.add("show");
  }

  function updateHud() {
    document.getElementById("hud-score").textContent = String(score);
    const comboEl = document.getElementById("hud-combo");
    if (combo > 1) {
      comboEl.textContent = combo + "x";
      comboEl.classList.remove("hidden");
      comboEl.style.animation = "none"; void comboEl.offsetWidth; comboEl.style.animation = "";
    } else comboEl.classList.add("hidden");
    document.getElementById("flow-fill").style.width = Math.round(flow * 100) + "%";
    applyFlowState();
  }

  // FLOW STATE — the unique hook: sustained flow visually and audibly
  // reshapes the run in real time rather than just tracking a score.
  let flowStateActive = false;
  function applyFlowState() {
    const active = flow > 0.85;
    if (active === flowStateActive) return;
    flowStateActive = active;
    receptorMeshes.forEach((r) => {
      r.material.emissiveIntensity = active ? 1.4 : 0.6;
    });
    scene.fog.color.set(active ? 0x1a0d33 : 0x05050f);
    renderer.setClearColor(active ? 0x120a24 : 0x05050f, 1);
    camera.fov = active ? 62 : 58;
    camera.updateProjectionMatrix();
  }

  function togglePause() {
    paused = !paused;
    document.getElementById("pause-overlay").classList.toggle("hidden", !paused);
    if (paused) AudioEngine.pause(); else AudioEngine.resume();
  }

  function restartCurrent() {
    document.getElementById("pause-overlay").classList.add("hidden");
    if (source === "story") loadStoryChapter(sourceMeta.id);
    else loadLibraryTrack(sourceMeta);
  }

  // ---------- Main loop with adaptive quality (old-laptop friendly) ----------
  function loop() {
    animId = requestAnimationFrame(loop);
    const dt = clock.getDelta();
    sampleFps(dt);

    if (!paused && chart) {
      const now = AudioEngine.currentTime();

      while (noteIdx < chart.notes.length && chart.notes[noteIdx].time - APPROACH_TIME <= now) {
        const n = chart.notes[noteIdx];
        if (n.lane >= 0) activeNotes.push(spawnNote(n));
        noteIdx++;
      }

      for (let i = activeNotes.length - 1; i >= 0; i--) {
        const n = activeNotes[i];
        if (n.judged) { activeNotes.splice(i, 1); continue; }
        const progress = (now - (n.data.time - APPROACH_TIME)) / APPROACH_TIME;
        const z = -12 + progress * (12 + 2.2);
        n.mesh.position.z = z;
        if (now - n.data.time > HIT_WINDOWS.good + 0.05) {
          missNote(n);
          activeNotes.splice(i, 1);
        }
      }

      for (let i = particleGroup.children.length - 1; i >= 0; i--) {
        const p = particleGroup.children[i];
        p.userData.life -= dt;
        if (p.userData.life <= 0) { particleGroup.remove(p); continue; }
        p.position.addScaledVector(p.userData.v, dt);
        p.userData.v.y -= dt * 4;
      }

      receptorMeshes.forEach((r, i) => {
        const flashed = performance.now() < pressFlashUntil[i];
        r.scale.y = flashed ? 1.8 : 1;
      });

      if (lyricsCtl) lyricsCtl.update(now);

      if (noteIdx >= chart.notes.length && activeNotes.length === 0 && now > chart.duration - 0.3) {
        finishRun();
      }
    }

    renderer.render(scene, camera);
  }

  function sampleFps(dt) {
    if (dt <= 0) return;
    fpsHistory.push(1 / dt);
    if (fpsHistory.length > 90) fpsHistory.shift();
    const avg = fpsHistory.reduce((a, b) => a + b, 0) / fpsHistory.length;
    const readout = document.getElementById("fps-readout");
    if (readout) readout.textContent = "FPS: " + Math.round(avg);

    if (quality !== "auto") { if (effectiveQuality !== quality) applyQuality(quality); return; }
    if (fpsHistory.length < 60) return;
    if (avg < 40 && effectiveQuality !== "low") applyQuality("low");
    else if (avg >= 40 && avg < 55 && effectiveQuality === "high") applyQuality("medium");
    else if (avg >= 55 && effectiveQuality === "low" && fpsHistory.length >= 90) applyQuality("medium");
    else if (avg >= 58 && effectiveQuality === "medium" && fpsHistory.length >= 90) applyQuality("high");
  }

  function finishRun() {
    if (ended) return;
    ended = true;
    AudioEngine.stop();
    const total = judgeCounts.perfect + judgeCounts.great + judgeCounts.good + judgeCounts.miss;
    const accuracy = total ? ((judgeCounts.perfect + judgeCounts.great * 0.7 + judgeCounts.good * 0.4) / total) : 0;
    const grade = accuracy > 0.95 ? "S" : accuracy > 0.88 ? "A" : accuracy > 0.75 ? "B" : accuracy > 0.6 ? "C" : "D";
    if (onFinish) onFinish({ score, maxCombo, accuracy, grade, judgeCounts, source, sourceMeta });
  }

  return {
    init, setQualityPref,
    loadStoryChapter, loadLibraryTrack,
    togglePause, restartCurrent,
    setOnFinish: (fn) => { onFinish = fn; },
    getState: () => ({ score, combo, maxCombo, judgeCounts }),
  };
})();

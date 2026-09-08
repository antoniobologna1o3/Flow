/* App shell: boot, menu, mode dispatch, selection screens, results. */
(() => {
  let platform = "pc";
  let currentMode = null;        // Modes entry
  let currentChapterId = null;
  let currentTrack = null;
  let resultsAction = null;
  let livePalette = { p1: "#7c5cff", p2: "#ff5ca8", p3: "#5cf0ff" };

  const $ = (id) => document.getElementById(id);

  /* ---------------- navigation ---------------- */
  function go(id) {
    document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
    $(id).classList.add("active");
  }

  function setPlatform(p) {
    platform = p;
    document.body.dataset.platform = p;
    Persist.setSetting("platform", p);
    const labels = {
      pc: "PC · keyboard D F J K",
      mobile: "Mobile · tap the lanes",
      touch: "Touch laptop · tap or type",
    };
    $("menu-platform-badge").textContent = labels[p];
    $("opt-platform").value = p;
  }

  /* ---------------- boot ---------------- */
  function boot() {
    const guessed = Device.guessPlatform();
    const saved = Persist.getSetting("platform", null);

    setTimeout(() => {
      $("boot-detecting").classList.add("hidden");
      $("boot-result").classList.remove("hidden");
      const names = { pc: "PC / laptop", mobile: "mobile device", touch: "touch-screen laptop" };
      $("boot-guess").textContent = names[guessed];
      const card = document.querySelector(`.choice-card[data-platform="${guessed}"]`);
      if (card) card.classList.add("suggested");
    }, 1000);

    document.querySelectorAll(".choice-card").forEach((c) => {
      c.addEventListener("click", () => {
        setPlatform(c.dataset.platform);
        renderMenu();
        go("screen-menu");
      });
    });

    setPlatform(saved || guessed);
  }

  /* ---------------- menu ---------------- */
  function renderMenu() {
    const list = $("mode-list");
    list.innerHTML = "";
    const cleared = Persist.storyProgress();
    const daily = Modes.dailyPick();
    const dailyChapter = StoryData.chapters.find((c) => c.id === daily.chapterId);

    Modes.order.forEach((key, i) => {
      const m = Modes[key];
      const btn = document.createElement("button");
      btn.className = "mode-item";
      if (m.group && Modes[Modes.order[i - 1]]?.group !== m.group) btn.classList.add("group-gap");
      btn.style.animationDelay = i * 0.045 + "s";

      let sub = m.sub;
      if (key === "story") sub = `${cleared}/8 cleared · original score`;
      if (key === "daily") sub = `${dailyChapter.title} · ${daily.mod.label}`;
      if (key === "library") sub = libraryCount === null ? "import your own songs" : `${libraryCount} song${libraryCount === 1 ? "" : "s"} imported`;

      btn.innerHTML = `<span class="mi-name">${m.name}</span><span class="mi-sub">${sub}</span>`;
      if (key === "ghost" && cleared === 0) {
        btn.classList.add("locked");
        btn.innerHTML += `<span class="mi-tag">CLEAR A CHAPTER</span>`;
      }
      btn.addEventListener("click", () => {
        if (btn.classList.contains("locked")) return;
        openMode(m);
      });
      btn.addEventListener("mouseenter", () => setDiscTint(m));
      list.appendChild(btn);
    });

    const lp = Persist.getLastPlayed();
    const lpEl = $("last-played");
    if (lp) {
      lpEl.classList.remove("hidden");
      $("last-played-art").src = lp.art || gradientDataUrl(lp.gradient || ["#7c5cff", "#5cf0ff"]);
      $("lp-title").textContent = lp.title;
      $("lp-go").onclick = () => resumeLast(lp);
    } else lpEl.classList.add("hidden");
  }

  let libraryCount = null;
  function setDiscTint(m) {
    const disc = $("menu-disc");
    if (m.id === "library") disc.style.background = `conic-gradient(from 0deg, ${livePalette.p1}, ${livePalette.p2}, ${livePalette.p3}, ${livePalette.p1})`;
    else disc.style.background = "";
  }

  function gradientDataUrl(g) {
    const c = document.createElement("canvas");
    c.width = c.height = 64;
    const x = c.getContext("2d");
    const grad = x.createLinearGradient(0, 0, 64, 64);
    grad.addColorStop(0, g[0]); grad.addColorStop(1, g[1]);
    x.fillStyle = grad; x.fillRect(0, 0, 64, 64);
    return c.toDataURL();
  }

  function resumeLast(lp) {
    const mode = Modes[lp.mode] || Modes.story;
    if (lp.mode === "library") { openMode(Modes.library); return; }
    currentMode = mode;
    if (lp.chapterId) startChapter(lp.chapterId, mode);
    else openMode(mode);
  }

  /* ---------------- mode dispatch ---------------- */
  function openMode(m) {
    currentMode = m;
    switch (m.kind) {
      case "chapters": openChapterSelect(m); break;
      case "library": go("screen-library"); refreshLibrary(); break;
      case "chain": startMarathon(); break;
      case "generative": startGenerative(m); break;
      case "daily": startDaily(); break;
      case "drills": openDrillSelect(); break;
    }
  }

  function openChapterSelect(m) {
    $("select-title").textContent = m.name;
    $("select-desc").textContent = m.desc;
    const grid = $("select-grid");
    grid.innerHTML = "";
    StoryData.chapters.forEach((ch, i) => {
      const best = Persist.getBest(m.id === "story" ? "story" : m.id, ch.id);
      const card = document.createElement("button");
      card.className = "chapter-card";
      card.style.background = `linear-gradient(150deg, ${ch.gradient[0]}, ${ch.gradient[1]})`;
      card.style.animationDelay = i * 0.05 + "s";
      card.innerHTML = `
        <div class="cc-shine"></div>
        <span class="cc-num">CH ${ch.id}</span>
        ${best ? `<span class="cc-grade">${best.grade}</span>` : ""}
        <span class="cc-title">${ch.title}</span>
        <span class="cc-meta">${ch.spec.bpm} BPM · ${ch.subtitle}</span>
        ${best ? `<span class="cc-best">BEST ${best.score.toLocaleString()} · ${Math.round(best.accuracy * 100)}%</span>` : ""}
      `;
      card.addEventListener("click", () => startChapter(ch.id, m));
      grid.appendChild(card);
    });
    go("screen-select");
  }

  function openDrillSelect() {
    $("select-title").textContent = "Drills";
    $("select-desc").textContent = Modes.drills.desc;
    const grid = $("select-grid");
    grid.innerHTML = "";
    Modes.drillList.forEach((d, i) => {
      const card = document.createElement("button");
      card.className = "chapter-card";
      card.style.background = `linear-gradient(150deg, #2b2a52, #4b3f8c)`;
      card.style.animationDelay = i * 0.05 + "s";
      card.innerHTML = `
        <div class="cc-shine"></div>
        <span class="cc-num">DRILL</span>
        <span class="cc-title">${d.name}</span>
        <span class="cc-meta">${d.bpm} BPM · ${d.sub}</span>`;
      card.addEventListener("click", () => startDrill(d));
      grid.appendChild(card);
    });
    go("screen-select");
  }

  /* ---------------- story / chapter runs ---------------- */
  function startChapter(id, mode) {
    currentChapterId = id;
    currentMode = mode;
    const ch = StoryData.getChapter(id);
    if (mode.id === "story" && ch.narrative) {
      playNarrative(ch.narrative, ch, () => launchChapter(id, mode));
    } else launchChapter(id, mode);
  }

  function launchChapter(id, mode, carried, chainIdx) {
    const ch = StoryData.getChapter(id);
    Persist.setLastPlayed({ mode: mode.id, chapterId: id, title: ch.title, gradient: ch.gradient });
    go("screen-game");
    Game.applyPalette(null);
    Game.startRun({
      mode: mode.id,
      modeLabel: mode.name,
      flags: Object.assign({}, mode.flags, mode.id === "story" ? chapterFlags(ch) : {}),
      chartId: id,
      title: ch.title,
      subtitle: ch.subtitle,
      song: ch.song,
      chain: mode.chain || null,
      chainIdx: chainIdx || 0,
      carried: carried || null,
    });
  }

  // Story chapters progressively switch their own mechanics on
  function chapterFlags(ch) {
    switch (ch.teaches) {
      case "blackout": return { blackout: true };
      case "split": return { split: true };
      case "inversion": return { inversion: true };
      case "all": return { blackout: true, inversion: true, split: true };
      default: return {};
    }
  }

  function startMarathon() {
    const m = Modes.marathon;
    launchChapter(m.chain[0], m, null, 0);
  }

  Game.setOnChainAdvance((nextIdx, carried) => {
    const m = Modes.marathon;
    launchChapter(m.chain[nextIdx], m, carried, nextIdx);
  });

  function startDaily() {
    const daily = Modes.dailyPick();
    const ch = StoryData.getChapter(daily.chapterId);
    const flags = Object.assign({}, Modes.daily.flags);
    flags[daily.mod.key] = daily.mod.value;
    currentMode = Modes.daily;
    currentChapterId = daily.chapterId;
    Persist.setLastPlayed({ mode: "daily", chapterId: daily.chapterId, title: ch.title + " · " + daily.mod.label, gradient: ch.gradient });
    go("screen-game");
    Game.applyPalette(null);
    Game.startRun({
      mode: "daily", modeLabel: "Daily Remix", flags,
      chartId: "d" + daily.seed, title: ch.title, subtitle: daily.mod.label,
      song: ch.song, bannerText: daily.mod.label, bannerSub: "today's modifier",
    });
  }

  function startGenerative(m) {
    currentMode = m;
    const genState = {
      bpm: m.id === "conductor" ? 108 : 118,
      root: ["A", "D", "E", "G"][Math.floor(Math.random() * 4)],
      scale: m.id === "conductor" ? "dorian" : "minor",
      seed: Math.floor(Math.random() * 9999),
      density: m.id === "endless" ? 0.75 : 0.85,
      drums: "driving", octave: 5, energy: 0.8, laneCount: 4,
    };
    currentChapterId = m.id;
    go("screen-game");
    Game.applyPalette(null);
    Game.startRun({
      mode: m.id, modeLabel: m.name, flags: m.flags,
      chartId: m.id, title: m.name, subtitle: "", genState,
    });
  }

  function startDrill(d) {
    const genState = {
      bpm: d.bpm, root: "A", scale: "minor", seed: 7,
      density: d.pattern === "stream" ? 1.3 : d.pattern === "burst" ? 0.7 : 0.9,
      drums: d.pattern === "stream" ? "driving" : "straight",
      octave: 5, energy: d.pattern === "burst" ? 0.9 : 0.5, laneCount: 4,
    };
    currentMode = Modes.drills;
    currentChapterId = d.id;
    go("screen-game");
    Game.applyPalette(null);
    Game.startRun({
      mode: "drills", modeLabel: "Drill · " + d.name,
      flags: Modes.drills.flags, chartId: d.id, title: d.name, subtitle: d.sub,
      genState, bannerText: d.name.toUpperCase(), bannerSub: d.sub,
    });
  }

  /* ---------------- narrative ---------------- */
  let typeTimer = null;
  function playNarrative(lines, ch, done) {
    go("screen-narrative");
    const core = document.querySelector(".na-core");
    if (ch) core.style.background = `radial-gradient(circle at 35% 30%, ${ch.gradient[1]}, ${ch.gradient[0]})`;
    let i = 0;
    const btn = $("narrative-continue");

    function show() {
      $("narrative-speaker").textContent = lines[i].speaker;
      typewrite($("narrative-text"), lines[i].text);
    }
    function next() {
      clearInterval(typeTimer);
      const el = $("narrative-text");
      if (el.dataset.full && el.textContent !== el.dataset.full) {
        el.textContent = el.dataset.full;   // first click completes the line
        return;
      }
      i++;
      if (i >= lines.length) { btn.removeEventListener("click", next); done(); }
      else show();
    }
    btn.addEventListener("click", next);
    show();
  }

  function typewrite(el, text) {
    clearInterval(typeTimer);
    el.dataset.full = text;
    el.textContent = "";
    let i = 0;
    typeTimer = setInterval(() => {
      el.textContent = text.slice(0, ++i);
      if (i >= text.length) clearInterval(typeTimer);
    }, 18);
  }

  /* ---------------- library ---------------- */
  async function refreshLibrary(query) {
    const all = await Library.getAll();
    libraryCount = all.length;
    const shown = query ? Library.search(all, query) : all;
    renderLibrary(shown, all.length);
  }

  function renderLibrary(tracks, totalCount) {
    const list = $("library-list");
    $("library-empty").classList.toggle("hidden", totalCount > 0);
    list.innerHTML = "";
    tracks.forEach((t, i) => {
      const row = document.createElement("div");
      row.className = "track-row";
      row.style.animationDelay = Math.min(i, 12) * 0.03 + "s";
      row.style.setProperty("--row-accent", t.palette.primary);
      row.innerHTML = `
        <img class="track-art" src="${t.artDataUrl}" alt="" />
        <div class="track-meta">
          <div class="track-title">${esc(t.title)}</div>
          <div class="track-artist">${esc(t.artist)}</div>
          <div class="track-tags">
            ${t.lyricsText ? '<span class="track-tag">LYRICS</span>' : ""}
            ${t.generatedArt ? '<span class="track-tag">GEN COVER</span>' : '<span class="track-tag">COVER</span>'}
          </div>
        </div>
        <button class="track-del" aria-label="Delete">✕</button>`;
      row.addEventListener("click", (e) => {
        if (e.target.closest(".track-del")) return;
        playTrack(t);
      });
      row.querySelector(".track-del").addEventListener("click", async (e) => {
        e.stopPropagation();
        await Library.remove(t.id);
        refreshLibrary($("library-search").value);
      });
      list.appendChild(row);
    });
  }

  async function playTrack(t) {
    currentTrack = t;
    currentMode = Modes.library;
    livePalette = { p1: t.palette.primary, p2: t.palette.secondary, p3: t.palette.accent };
    ColorExtract.applyPalette(t.palette);
    Persist.setLastPlayed({ mode: "library", title: t.title, art: t.artDataUrl });

    go("screen-game");
    document.getElementById("event-banner").innerHTML = "LISTENING…<small>building your chart</small>";
    document.getElementById("event-banner").classList.add("show");

    let buffer, chart;
    try {
      AudioEngine.ensureCtx();
      buffer = await AudioEngine.loadFile(t.audioBlob);
      chart = await AudioEngine.autoChart(buffer, 4);
    } catch (err) {
      // Unsupported codec, DRM, or a corrupt file — say so and go back
      // rather than sitting on an empty stage forever.
      document.getElementById("event-banner").classList.remove("show");
      go("screen-library");
      refreshLibrary();
      toastError("<b>Couldn't play \"" + escapeHtml(t.title) + "\".</b><br>" +
        "Your browser couldn't decode this file. MP3, M4A, WAV and OGG work best; " +
        "DRM-protected files (like purchased iTunes tracks) can't be decoded by any browser.");
      return;
    }

    Game.startRun({
      mode: "library", modeLabel: "Your Music",
      flags: Modes.library.flags,
      chartId: t.id, title: t.title, subtitle: t.artist,
      buffer: true, chart, duration: buffer.duration,
      palette: t.palette, art: t.artDataUrl, lyrics: t.lyricsText,
    });
  }

  function esc(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  /* ---------------- batch import: drop a folder or many files ---------------- */

  // Recursively walk a dropped directory entry (Chrome/Edge/Safari).
  function readEntry(entry, out, depth = 0) {
    return new Promise((resolve) => {
      if (!entry || depth > 8) return resolve();
      if (entry.isFile) {
        entry.file(
          (f) => { out.push(f); resolve(); },
          () => resolve()
        );
      } else if (entry.isDirectory) {
        const reader = entry.createReader();
        const all = [];
        const readBatch = () => reader.readEntries(async (entries) => {
          if (!entries.length) {
            for (const e of all) await readEntry(e, out, depth + 1);
            return resolve();
          }
          all.push(...entries);
          readBatch();                       // readEntries returns 100 at a time
        }, () => resolve());
        readBatch();
      } else resolve();
    });
  }

  async function filesFromDataTransfer(dt) {
    const out = [];
    const items = dt.items ? [...dt.items] : [];
    const entries = items
      .map((it) => (it.webkitGetAsEntry ? it.webkitGetAsEntry() : null))
      .filter(Boolean);
    if (entries.length) {
      for (const e of entries) await readEntry(e, out);
      if (out.length) return out;
    }
    return dt.files ? [...dt.files] : [];
  }

  async function importFiles(files) {
    const list = [...files];
    if (!list.length) return;
    const toast = $("import-toast");
    const fill = $("it-fill");
    const text = $("it-text");
    toast.classList.remove("hidden");
    fill.style.width = "0%";
    text.textContent = "Reading files…";

    let result;
    try {
      result = await Library.addMany(list, (done, total, name) => {
        fill.style.width = total ? Math.round((done / total) * 100) + "%" : "0%";
        text.textContent = name
          ? `Importing ${done + 1} of ${total} — ${name}`
          : `Imported ${total} song${total === 1 ? "" : "s"}`;
      });
    } catch (err) {
      toast.classList.add("hidden");
      toastError("<b>Import failed.</b><br>" + escapeHtml(String(err && err.message ? err.message : err)));
      return;
    }

    await refreshLibrary($("library-search").value);
    renderMenu();

    if (!result.total) {
      toast.classList.add("hidden");
      toastError("<b>No audio files found.</b><br>Drop MP3, M4A, FLAC, OGG or WAV files (or a folder containing them).");
      return;
    }
    text.textContent = `Added ${result.added} song${result.added === 1 ? "" : "s"}` +
      (result.failed.length ? ` · ${result.failed.length} skipped` : "");
    fill.style.width = "100%";
    setTimeout(() => toast.classList.add("hidden"), 2600);
    if (result.failed.length) {
      toastError("<b>Skipped " + result.failed.length + " file" + (result.failed.length === 1 ? "" : "s") + ":</b><br>" +
        result.failed.slice(0, 4).map((f) => escapeHtml(f.name)).join("<br>"));
    }
  }

  function wireDropZone() {
    const overlay = $("drop-overlay");
    let depth = 0;
    const show = () => overlay.classList.remove("hidden");
    const hide = () => { depth = 0; overlay.classList.add("hidden"); };

    addEventListener("dragenter", (e) => { e.preventDefault(); depth++; show(); });
    addEventListener("dragover", (e) => { e.preventDefault(); e.dataTransfer.dropEffect = "copy"; });
    addEventListener("dragleave", (e) => { e.preventDefault(); if (--depth <= 0) hide(); });
    addEventListener("drop", async (e) => {
      e.preventDefault();
      hide();
      const files = await filesFromDataTransfer(e.dataTransfer);
      if (!files.length) return;
      go("screen-library");
      await importFiles(files);
    });
  }

  function wireLibrary() {
    wireDropZone();

    $("btn-add-folder").addEventListener("click", () => $("file-folder").click());
    $("file-folder").addEventListener("change", async (e) => {
      const files = [...e.target.files];
      e.target.value = "";
      await importFiles(files);
    });
    $("file-batch").addEventListener("change", async (e) => {
      const files = [...e.target.files];
      e.target.value = "";
      await importFiles(files);
    });

    $("error-close").addEventListener("click", () => $("error-toast").classList.add("hidden"));

    $("library-search").addEventListener("input", (e) => refreshLibrary(e.target.value));

    $("btn-add-track").addEventListener("click", () => $("file-batch").click());
  }

  /* ---------------- results ---------------- */
  Game.setOnFinish((r) => {
    const modeId = r.mode === "daily" ? "daily" : r.mode;
    const isBest = Persist.submitResult(modeId, r.chartId, r);

    // ghost: keep the best run's recording for Ghost Race
    if (modeId === "story" && isBest && r.ghost && r.ghost.events.length > 4) {
      Persist.saveGhost("story", r.chartId, r.ghost);
    }

    $("results-sub").textContent = (Modes[r.mode]?.name || r.mode) + (isBest ? " · NEW BEST" : "");
    $("results-title").textContent = r.title;
    $("results-grade").textContent = r.grade;
    $("stat-score").textContent = r.score.toLocaleString();
    $("stat-combo").textContent = r.maxCombo;
    $("stat-accuracy").textContent = Math.round(r.accuracy * 100) + "%";

    const total = Math.max(1, r.total);
    [["perfect", r.judge.perfect], ["great", r.judge.great], ["good", r.judge.good], ["miss", r.judge.miss]]
      .forEach(([k, v]) => {
        $("j-" + k).textContent = v;
        $("bar-" + k).style.setProperty("--w", (v / total * 100) + "%");
      });

    const extra = [];
    if (r.echoCaught) extra.push(`Cleared <b>${r.echoCaught}</b> echo${r.echoCaught === 1 ? "" : "es"}.`);
    if (r.ghostFinal != null) {
      const d = r.score - r.ghostFinal;
      extra.push(d >= 0 ? `Beat your ghost by <b>${d.toLocaleString()}</b>.` : `Ghost won by <b>${(-d).toLocaleString()}</b>.`);
    }
    if (r.flags.blackout) extra.push("Half of that was played blind.");
    if (r.flags.tempoFollow) extra.push("You set every tempo in it.");
    $("results-extra").innerHTML = extra.join(" ");

    // what "Continue" does next
    const ch = StoryData.chapters.find((c) => c.id === r.chartId);
    if (r.mode === "story" && ch && ch.outro) {
      resultsAction = () => playNarrative(ch.outro, ch, () => openChapterSelect(Modes.story));
    } else if (currentMode && currentMode.kind === "chapters") {
      resultsAction = () => openChapterSelect(currentMode);
    } else if (currentMode && currentMode.kind === "drills") {
      resultsAction = () => openDrillSelect();
    } else if (r.mode === "library") {
      resultsAction = () => { go("screen-library"); refreshLibrary(); };
    } else {
      resultsAction = () => { renderMenu(); go("screen-menu"); };
    }

    go("screen-results");
  });

  function wireResults() {
    $("results-continue").addEventListener("click", () => {
      const a = resultsAction; resultsAction = null;
      if (a) a(); else { renderMenu(); go("screen-menu"); }
    });
    $("results-retry").addEventListener("click", () => {
      if (!currentMode) return;
      if (currentMode.kind === "chapters") launchChapter(currentChapterId, currentMode);
      else if (currentMode.kind === "generative") startGenerative(currentMode);
      else if (currentMode.kind === "daily") startDaily();
      else if (currentMode.kind === "drills") {
        const d = Modes.drillList.find((x) => x.id === currentChapterId);
        if (d) startDrill(d);
      } else if (currentMode.kind === "library" && currentTrack) playTrack(currentTrack);
      else if (currentMode.kind === "chain") startMarathon();
    });
  }

  /* ---------------- settings + chrome ---------------- */
  function wireChrome() {
    document.querySelectorAll("[data-go]").forEach((b) =>
      b.addEventListener("click", () => {
        if (b.dataset.go === "screen-menu") renderMenu();
        go(b.dataset.go);
      })
    );
    $("btn-settings").addEventListener("click", () => go("screen-settings"));

    $("btn-pause").addEventListener("click", () => Game.togglePause());
    $("btn-resume").addEventListener("click", () => Game.togglePause());
    $("btn-restart").addEventListener("click", () => {
      Game.quit();
      $("results-retry").click();
    });
    $("btn-quit").addEventListener("click", () => {
      // generative modes never end on their own — quitting scores the run
      if (Game.isEndless()) {
        $("pause-overlay").classList.add("hidden");
        const r = Game.getRun();
        if (r) r.paused = false;
        AudioEngine.resume();
        Game.forceFinish();
      } else {
        Game.quit();
        renderMenu();
        go("screen-menu");
      }
    });

    $("btn-streaming").addEventListener("click", () => $("modal-connect").classList.remove("hidden"));
    $("connect-close").addEventListener("click", () => $("modal-connect").classList.add("hidden"));

    const q = $("opt-quality");
    q.value = Persist.getSetting("quality", "auto");
    Game.setQualityPref(q.value);
    q.addEventListener("change", () => {
      Persist.setSetting("quality", q.value);
      Game.setQualityPref(q.value);
      VFX.Ambient.setQuality(q.value === "auto" ? "high" : q.value);
    });

    $("opt-platform").addEventListener("change", (e) => setPlatform(e.target.value));

    ["shake", "particles", "trails", "echo"].forEach((k) => {
      const el = $("opt-" + k);
      el.checked = Persist.getSetting(k, true);
      el.addEventListener("change", () => Persist.setSetting(k, el.checked));
    });

    const off = $("opt-offset");
    off.value = Persist.getSetting("offset", 0);
    $("offset-val").textContent = off.value + " ms";
    AudioEngine.setOffset(+off.value);
    off.addEventListener("input", () => {
      $("offset-val").textContent = off.value + " ms";
      AudioEngine.setOffset(+off.value);
      Persist.setSetting("offset", +off.value);
    });
  }

  /* ---------------- go ---------------- */
  // Nothing here may take the whole app down with it: on an old laptop
  // WebGL can be missing entirely, and in private browsing IndexedDB can
  // be blocked. Either used to leave the boot screen stuck forever.
  addEventListener("DOMContentLoaded", async () => {
    try {
      Game.init();
    } catch (err) {
      showFatal(err);
      return;
    }
    try { VFX.Ambient.start($("particle-canvas"), () => livePalette); } catch (e) { console.warn(e); }
    try { boot(); } catch (e) { showFatal(e); return; }
    try { wireChrome(); } catch (e) { console.warn(e); }
    try { wireLibrary(); } catch (e) { console.warn(e); }
    try { wireResults(); } catch (e) { console.warn(e); }
    try {
      const all = await Library.getAll();
      libraryCount = all.length;
    } catch (e) {
      libraryCount = 0;
      console.warn("Library unavailable:", e);
    }
    try { renderMenu(); } catch (e) { console.warn(e); }
  });

  function showFatal(err) {
    const detecting = $("boot-detecting");
    if (detecting) detecting.classList.add("hidden");
    const box = document.createElement("div");
    box.className = "boot-fatal";
    const webglMissing = !hasWebGL();
    box.innerHTML = webglMissing
      ? "<b>This browser can't start 3D graphics.</b><br>Flow needs WebGL. Try enabling hardware acceleration " +
        "(Chrome: Settings → System → \"Use graphics acceleration when available\"), then reload. " +
        "If you're in a private window or have WebGL disabled by policy, try a normal window."
      : "<b>Something went wrong starting the game.</b><br>" + escapeHtml(String(err && err.message ? err.message : err));
    const content = document.querySelector(".boot-content");
    if (content) content.appendChild(box);
    console.error(err);
  }

  function hasWebGL() {
    try {
      const c = document.createElement("canvas");
      return !!(c.getContext("webgl") || c.getContext("experimental-webgl"));
    } catch (e) { return false; }
  }

  // Any unexpected failure becomes visible instead of a frozen screen.
  function toastError(msg) {
    const t = $("error-toast");
    if (!t) return;
    $("error-text").innerHTML = msg;
    t.classList.remove("hidden");
    clearTimeout(toastError._t);
    toastError._t = setTimeout(() => t.classList.add("hidden"), 9000);
  }
  addEventListener("error", (e) => {
    if (e && e.message) toastError("<b>Error:</b> " + escapeHtml(e.message));
  });
  addEventListener("unhandledrejection", (e) => {
    const r = e && e.reason;
    toastError("<b>Error:</b> " + escapeHtml(String(r && r.message ? r.message : r)));
  });

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }
})();

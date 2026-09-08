/* App shell: boot, menu, mode dispatch, selection screens, results. */
(() => {
  let platform = "pc";
  let currentMode = null;        // Modes entry
  let currentChapterId = null;
  let currentTrack = null;
  let resultsAction = null;
  let pickedAudio = null, pickedArt = null, pickedLyrics = null;
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

    AudioEngine.ensureCtx();
    const buffer = await AudioEngine.loadFile(t.audioBlob);
    const chart = await AudioEngine.autoChart(buffer, 4);

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

  function wireLibrary() {
    $("library-search").addEventListener("input", (e) => refreshLibrary(e.target.value));

    $("btn-add-track").addEventListener("click", () => {
      pickedAudio = pickedArt = pickedLyrics = null;
      $("pick-audio-label").textContent = "Audio file — required";
      $("pick-art-label").textContent = "Album art — optional";
      $("pick-lyrics-label").textContent = "Lyrics .lrc / .txt — optional";
      $("art-preview-wrap").classList.add("hidden");
      $("add-title").value = ""; $("add-artist").value = "";
      $("modal-add-track").classList.remove("hidden");
    });
    $("add-cancel").addEventListener("click", () => $("modal-add-track").classList.add("hidden"));

    $("pick-audio").addEventListener("click", () => $("file-audio").click());
    $("pick-art").addEventListener("click", () => $("file-art").click());
    $("pick-lyrics").addEventListener("click", () => $("file-lyrics").click());

    $("file-audio").addEventListener("change", (e) => {
      pickedAudio = e.target.files[0] || null;
      if (!pickedAudio) return;
      $("pick-audio-label").textContent = pickedAudio.name;
      if (!$("add-title").value) $("add-title").value = pickedAudio.name.replace(/\.[^.]+$/, "");
    });

    $("file-art").addEventListener("change", async (e) => {
      pickedArt = e.target.files[0] || null;
      if (!pickedArt) return;
      $("pick-art-label").textContent = pickedArt.name;
      const url = URL.createObjectURL(pickedArt);
      $("art-preview").src = url;
      $("art-preview-wrap").classList.remove("hidden");
      // show the palette we'd pull off the cover, before saving
      const img = new Image();
      img.onload = async () => {
        const pal = await ColorExtract.fromImage(img);
        $("art-swatches").innerHTML = [pal.primary, pal.secondary, pal.accent]
          .map((c, i) => `<i style="background:${c};animation-delay:${i * .06}s"></i>`).join("");
      };
      img.src = url;
    });

    $("file-lyrics").addEventListener("change", (e) => {
      pickedLyrics = e.target.files[0] || null;
      if (pickedLyrics) $("pick-lyrics-label").textContent = pickedLyrics.name;
    });

    $("add-save").addEventListener("click", async () => {
      if (!pickedAudio) { $("pick-audio").style.borderColor = "var(--miss)"; return; }
      const btn = $("add-save");
      btn.disabled = true; btn.textContent = "Saving…";
      const lyricsText = pickedLyrics ? await pickedLyrics.text() : null;
      await Library.addTrack({
        title: $("add-title").value || pickedAudio.name,
        artist: $("add-artist").value || "Unknown Artist",
        audioFile: pickedAudio, artFile: pickedArt, lyricsText,
      });
      btn.disabled = false; btn.textContent = "Save";
      $("modal-add-track").classList.add("hidden");
      refreshLibrary($("library-search").value);
    });
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
  addEventListener("DOMContentLoaded", async () => {
    Game.init();
    VFX.Ambient.start($("particle-canvas"), () => livePalette);
    boot();
    wireChrome();
    wireLibrary();
    wireResults();
    const all = await Library.getAll();
    libraryCount = all.length;
    renderMenu();
  });
})();

/* App shell: boot/device-detect, navigation, menu wiring, library modal,
   settings, and story chapter select. */
(() => {
  let platform = "pc";
  let pendingChapterAfterNarrative = null;
  let currentChapterId = null;
  let pickedAudioFile = null, pickedArtFile = null, pickedLyricsFile = null;

  function go(screenId) {
    document.querySelectorAll(".screen").forEach((s) => s.classList.remove("active"));
    document.getElementById(screenId).classList.add("active");
  }

  function setPlatform(p) {
    platform = p;
    document.body.dataset.platform = p;
    const badge = document.getElementById("menu-platform-badge");
    const labels = { pc: "🖥️ PC mode — keyboard (D F J K)", mobile: "📱 Mobile — touch lanes", touch: "🖱️ Touch laptop — touch + keyboard" };
    if (badge) badge.textContent = labels[p] || "";
    const sel = document.getElementById("opt-platform");
    if (sel) sel.value = p;
  }

  function bootSequence() {
    const guessed = Device.guessPlatform();
    setTimeout(() => {
      document.getElementById("boot-detecting").classList.add("hidden");
      const resultEl = document.getElementById("boot-result");
      resultEl.classList.remove("hidden");
      const labelMap = { pc: "a PC or laptop", mobile: "a mobile device", touch: "a touch-screen laptop" };
      document.getElementById("boot-guess").textContent = labelMap[guessed];
      document.getElementById("boot-guess").dataset.value = guessed;
    }, 900);

    document.querySelectorAll(".choice-card").forEach((card) => {
      card.addEventListener("click", () => {
        setPlatform(card.dataset.platform);
        go("screen-menu");
      });
    });
  }

  function wireNav() {
    document.querySelectorAll("[data-go]").forEach((btn) => {
      btn.addEventListener("click", () => go(btn.dataset.go));
    });
    document.getElementById("btn-settings").addEventListener("click", () => go("screen-settings"));
  }

  function wireSettings() {
    const q = document.getElementById("opt-quality");
    q.addEventListener("change", () => Game.setQualityPref(q.value));
    document.getElementById("opt-platform").addEventListener("change", (e) => setPlatform(e.target.value));
  }

  // ---------- Story mode ----------
  function renderChapters() {
    const grid = document.getElementById("story-chapters");
    grid.innerHTML = "";
    StoryData.chapters.forEach((ch, i) => {
      const card = document.createElement("button");
      card.className = "chapter-card";
      card.style.background = `linear-gradient(135deg, ${ch.gradient[0]}, ${ch.gradient[1]})`;
      card.innerHTML = `
        <span class="ch-num">CHAPTER ${i + 1}</span>
        <span class="ch-title">${ch.title}</span>
        <span class="ch-mood">${ch.music.bpm} BPM · ${capitalize(ch.mood)}</span>
      `;
      card.addEventListener("click", () => startChapterFlow(ch.id));
      grid.appendChild(card);
    });
  }
  function capitalize(s) { return s.charAt(0).toUpperCase() + s.slice(1); }

  function startChapterFlow(id) {
    currentChapterId = id;
    const ch = StoryData.getChapter(id);
    playNarrative(ch.narrative, () => {
      go("screen-game");
      Game.loadStoryChapter(id);
    });
  }

  function playNarrative(lines, onDone) {
    go("screen-narrative");
    let i = 0;
    const artEl = document.getElementById("narrative-art");
    const speakerEl = document.getElementById("narrative-speaker");
    const textEl = document.getElementById("narrative-text");
    const btn = document.getElementById("narrative-continue");
    const ch = StoryData.chapters.find((c) => c.id === currentChapterId) || { gradient: ["#7c5cff", "#5cf0ff"] };
    artEl.style.background = `linear-gradient(135deg, ${ch.gradient[0]}, ${ch.gradient[1]})`;

    function show() {
      const line = lines[i];
      speakerEl.textContent = line.speaker;
      typewrite(textEl, line.text);
    }
    function advance() {
      i++;
      if (i >= lines.length) { btn.removeEventListener("click", advance); onDone(); }
      else show();
    }
    btn.addEventListener("click", advance);
    show();
  }

  let typeTimer = null;
  function typewrite(el, text) {
    clearInterval(typeTimer);
    el.textContent = "";
    let i = 0;
    typeTimer = setInterval(() => {
      el.textContent += text[i] || "";
      i++;
      if (i > text.length) clearInterval(typeTimer);
    }, 16);
  }

  function onChapterFinished(result) {
    const ch = StoryData.chapters.find((c) => c.id === currentChapterId);
    showResults(result, () => {
      if (ch && ch.outro && ch.outro.length) {
        playNarrative(ch.outro, () => go("screen-story"));
      } else go("screen-story");
    });
  }

  // ---------- Library (Your Music) ----------
  async function refreshLibrary() {
    const tracks = await Library.getAll();
    renderLibraryList(tracks);
    return tracks;
  }

  function renderLibraryList(tracks) {
    const list = document.getElementById("library-list");
    const empty = document.getElementById("library-empty");
    empty.classList.toggle("hidden", tracks.length > 0);
    list.innerHTML = "";
    tracks.forEach((t) => {
      const row = document.createElement("div");
      row.className = "track-row";
      row.innerHTML = `
        <img class="track-art" src="${t.artDataUrl}" alt="" />
        <div class="track-meta">
          <div class="track-title">${escapeHtml(t.title)}</div>
          <div class="track-artist">${escapeHtml(t.artist)}</div>
        </div>
        <button class="track-del" aria-label="Delete">🗑</button>
      `;
      row.addEventListener("click", (e) => {
        if (e.target.closest(".track-del")) return;
        currentChapterId = null;
        go("screen-game");
        Game.loadLibraryTrack(t);
      });
      row.querySelector(".track-del").addEventListener("click", async (e) => {
        e.stopPropagation();
        await Library.remove(t.id);
        refreshLibrary();
      });
      list.appendChild(row);
    });
  }
  function escapeHtml(s) { return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c])); }

  function wireLibrary() {
    document.getElementById("library-search").addEventListener("input", async (e) => {
      const all = await Library.getAll();
      renderLibraryList(Library.search(all, e.target.value));
    });

    document.getElementById("btn-add-track").addEventListener("click", () => {
      pickedAudioFile = pickedArtFile = pickedLyricsFile = null;
      document.getElementById("pick-audio-label").textContent = "Choose audio file (required)";
      document.getElementById("pick-art-label").textContent = "Choose album art (optional)";
      document.getElementById("pick-lyrics-label").textContent = "Choose lyrics .lrc/.txt (optional)";
      document.getElementById("art-preview-wrap").classList.add("hidden");
      document.getElementById("add-title").value = "";
      document.getElementById("add-artist").value = "";
      document.getElementById("modal-add-track").classList.remove("hidden");
    });
    document.getElementById("add-cancel").addEventListener("click", () => {
      document.getElementById("modal-add-track").classList.add("hidden");
    });

    document.getElementById("pick-audio").addEventListener("click", () => document.getElementById("file-audio").click());
    document.getElementById("pick-art").addEventListener("click", () => document.getElementById("file-art").click());
    document.getElementById("pick-lyrics").addEventListener("click", () => document.getElementById("file-lyrics").click());

    document.getElementById("file-audio").addEventListener("change", (e) => {
      pickedAudioFile = e.target.files[0] || null;
      if (pickedAudioFile) {
        document.getElementById("pick-audio-label").textContent = pickedAudioFile.name;
        if (!document.getElementById("add-title").value) {
          document.getElementById("add-title").value = pickedAudioFile.name.replace(/\.[^.]+$/, "");
        }
      }
    });
    document.getElementById("file-art").addEventListener("change", (e) => {
      pickedArtFile = e.target.files[0] || null;
      if (pickedArtFile) {
        document.getElementById("pick-art-label").textContent = pickedArtFile.name;
        const url = URL.createObjectURL(pickedArtFile);
        document.getElementById("art-preview").src = url;
        document.getElementById("art-preview-wrap").classList.remove("hidden");
      }
    });
    document.getElementById("file-lyrics").addEventListener("change", (e) => {
      pickedLyricsFile = e.target.files[0] || null;
      if (pickedLyricsFile) document.getElementById("pick-lyrics-label").textContent = pickedLyricsFile.name;
    });

    document.getElementById("add-save").addEventListener("click", async () => {
      if (!pickedAudioFile) { alert("Please choose an audio file."); return; }
      const title = document.getElementById("add-title").value || pickedAudioFile.name;
      const artist = document.getElementById("add-artist").value || "Unknown Artist";
      let lyricsText = null;
      if (pickedLyricsFile) lyricsText = await pickedLyricsFile.text();
      await Library.addTrack({ title, artist, audioFile: pickedAudioFile, artFile: pickedArtFile, lyricsText });
      document.getElementById("modal-add-track").classList.add("hidden");
      refreshLibrary();
    });
  }

  function wireConnectModal() {
    document.getElementById("tile-connect").addEventListener("click", () => {
      document.getElementById("modal-connect").classList.remove("hidden");
    });
    document.getElementById("connect-close").addEventListener("click", () => {
      document.getElementById("modal-connect").classList.add("hidden");
    });
  }

  // ---------- Game controls (pause/results) ----------
  function wireGameControls() {
    document.getElementById("btn-pause").addEventListener("click", () => Game.togglePause());
    document.getElementById("btn-resume").addEventListener("click", () => Game.togglePause());
    document.getElementById("btn-restart").addEventListener("click", () => Game.restartCurrent());
    document.getElementById("btn-quit").addEventListener("click", () => {
      document.getElementById("pause-overlay").classList.add("hidden");
      go("screen-menu");
    });
  }

  let resultsContinueCb = null;
  function showResults(result, continueCb) {
    resultsContinueCb = continueCb;
    document.getElementById("results-title").textContent =
      result.source === "story" ? (StoryData.chapters.find(c => c.id === currentChapterId)?.title || "Song Complete") : (result.sourceMeta?.title || "Song Complete");
    document.getElementById("results-grade").textContent = result.grade;
    document.getElementById("stat-score").textContent = result.score;
    document.getElementById("stat-combo").textContent = result.maxCombo;
    document.getElementById("stat-accuracy").textContent = Math.round(result.accuracy * 100) + "%";
    document.getElementById("j-perfect").textContent = result.judgeCounts.perfect;
    document.getElementById("j-great").textContent = result.judgeCounts.great;
    document.getElementById("j-good").textContent = result.judgeCounts.good;
    document.getElementById("j-miss").textContent = result.judgeCounts.miss;
    go("screen-results");
  }

  function wireResults() {
    document.getElementById("results-continue").addEventListener("click", () => {
      const cb = resultsContinueCb;
      resultsContinueCb = null;
      if (cb) cb(); else go("screen-menu");
    });
    document.getElementById("results-menu").addEventListener("click", () => go("screen-menu"));
  }

  Game.setOnFinish((result) => {
    if (result.source === "story") onChapterFinished(result);
    else showResults(result, () => go("screen-library"));
  });

  window.addEventListener("DOMContentLoaded", () => {
    Game.init();
    bootSequence();
    wireNav();
    wireSettings();
    wireLibrary();
    wireConnectModal();
    wireGameControls();
    wireResults();
    renderChapters();
    refreshLibrary();
    setPlatform(Device.guessPlatform());
  });
})();

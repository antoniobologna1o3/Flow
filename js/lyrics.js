/* Lyrics parsing + right-side karaoke-style sync panel. */
const Lyrics = (() => {
  // Parses .lrc ([mm:ss.xx]text) or falls back to plain lines (no timing,
  // just shown as static reference text with even estimated spacing).
  function parse(text, fallbackDuration = 180) {
    if (!text) return [];
    const lrcLine = /\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]\s*(.*)/;
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const timed = [];
    let anyTimed = false;
    for (const line of lines) {
      const m = line.match(lrcLine);
      if (m) {
        anyTimed = true;
        const min = parseInt(m[1], 10);
        const sec = parseInt(m[2], 10);
        const ms = m[3] ? parseInt(m[3].padEnd(3, "0"), 10) : 0;
        const time = min * 60 + sec + ms / 1000;
        const content = m[4].trim();
        if (content) timed.push({ time, text: content });
      } else if (!line.startsWith("[")) {
        timed.push({ time: null, text: line });
      }
    }
    if (anyTimed) return timed.filter((l) => l.time !== null).sort((a, b) => a.time - b.time);
    // No timestamps: spread evenly across the track duration
    return timed.map((l, i) => ({ time: (i / Math.max(1, timed.length)) * fallbackDuration, text: l.text }));
  }

  function mount(container) {
    let cues = [];
    let lastActive = -1;
    container.innerHTML = "";
    const scroll = container;

    function setCues(list) {
      cues = list;
      lastActive = -1;
      scroll.innerHTML = cues
        .map((c, i) => `<div class="lyric-line" data-i="${i}">${escapeHtml(c.text)}</div>`)
        .join("");
    }

    function update(currentTime) {
      if (!cues.length) return;
      let idx = -1;
      for (let i = 0; i < cues.length; i++) {
        if (cues[i].time <= currentTime) idx = i; else break;
      }
      if (idx === lastActive) return;
      lastActive = idx;
      scroll.querySelectorAll(".lyric-line.active, .lyric-line.near")
        .forEach((n) => n.classList.remove("active", "near"));
      if (idx >= 0) {
        for (const off of [-1, 1]) {
          const near = scroll.querySelector(`.lyric-line[data-i="${idx + off}"]`);
          if (near) near.classList.add("near");
        }
        const el = scroll.querySelector(`.lyric-line[data-i="${idx}"]`);
        if (el) {
          el.classList.add("active");
          const offset = el.offsetTop - scroll.parentElement.clientHeight / 2 + el.clientHeight / 2;
          scroll.style.transform = `translateY(${-offset}px)`;
        }
      }
    }

    return { setCues, update };
  }

  function escapeHtml(s) {
    return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  return { parse, mount };
})();

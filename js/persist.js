/* localStorage-backed progress: best scores, grades, settings,
   last-played, and the per-chart ghost recordings. */
const Persist = (() => {
  const KEY = "flow.save.v2";
  let data = load();

  function load() {
    try {
      const raw = localStorage.getItem(KEY);
      if (raw) return JSON.parse(raw);
    } catch (e) {}
    return { bests: {}, settings: {}, lastPlayed: null, ghosts: {}, totals: { notes: 0, runs: 0 } };
  }

  function save() {
    try { localStorage.setItem(KEY, JSON.stringify(data)); } catch (e) {}
  }

  function chartKey(modeId, chartId) { return `${modeId}:${chartId}`; }

  function getBest(modeId, chartId) {
    return data.bests[chartKey(modeId, chartId)] || null;
  }

  function submitResult(modeId, chartId, result) {
    const k = chartKey(modeId, chartId);
    const prev = data.bests[k];
    const isBest = !prev || result.score > prev.score;
    if (isBest) {
      data.bests[k] = {
        score: result.score, grade: result.grade,
        accuracy: result.accuracy, maxCombo: result.maxCombo, at: Date.now(),
      };
    }
    data.totals.runs++;
    data.totals.notes += result.total || 0;
    save();
    return isBest;
  }

  function setLastPlayed(entry) { data.lastPlayed = entry; save(); }
  function getLastPlayed() { return data.lastPlayed; }

  function getSetting(k, fallback) { return data.settings[k] !== undefined ? data.settings[k] : fallback; }
  function setSetting(k, v) { data.settings[k] = v; save(); }

  function saveGhost(modeId, chartId, ghost) {
    data.ghosts[chartKey(modeId, chartId)] = ghost;
    save();
  }
  function getGhost(modeId, chartId) { return data.ghosts[chartKey(modeId, chartId)] || null; }

  function storyProgress() {
    let cleared = 0;
    for (const ch of StoryData.chapters) {
      if (data.bests[chartKey("story", ch.id)]) cleared++;
    }
    return cleared;
  }

  function totals() { return data.totals; }

  return {
    getBest, submitResult, setLastPlayed, getLastPlayed,
    getSetting, setSetting, saveGhost, getGhost, storyProgress, totals,
  };
})();

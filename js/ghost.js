/* Ghost racing — records every judgement of a run as a compact
   timeline, then replays it beside a later attempt so you can see
   exactly where past-you pulled ahead or dropped the chain. */
const Ghost = (() => {
  function recorder() {
    const events = [];
    return {
      record(beat, tier, score) { events.push([+beat.toFixed(3), tier[0], score]); },
      finish(meta) { return { v: 1, events, ...meta }; },
      size() { return events.length; },
    };
  }

  function player(ghostData) {
    if (!ghostData || !ghostData.events || !ghostData.events.length) return null;
    const ev = ghostData.events;
    let idx = 0;
    let score = 0, combo = 0, lastTier = null, firedThisFrame = null;

    return {
      update(beat) {
        firedThisFrame = null;
        while (idx < ev.length && ev[idx][0] <= beat) {
          const [, tier, s] = ev[idx];
          score = s;
          if (tier === "m") combo = 0; else combo++;
          lastTier = tier;
          firedThisFrame = tier;
          idx++;
        }
        return { score, combo, tier: lastTier, fired: firedThisFrame };
      },
      finalScore: ghostData.score || (ev.length ? ev[ev.length - 1][2] : 0),
      grade: ghostData.grade || "—",
      done() { return idx >= ev.length; },
    };
  }

  return { recorder, player };
})();

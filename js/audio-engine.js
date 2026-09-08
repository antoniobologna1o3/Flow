/* Web Audio playback + procedural synthesis engine.
   - Synthesizes original story-mode songs (no copyrighted audio needed).
   - Plays user-uploaded files and runs onset-detection to auto-chart them. */
const AudioEngine = (() => {
  let ctx = null;
  let masterGain = null;
  let analyser = null;
  let currentSource = null; // AudioBufferSourceNode for uploaded tracks
  let startCtxTime = 0;
  let startOffset = 0;
  let playing = false;
  let mode = "silent"; // 'synth' | 'buffer'
  let synthSchedule = [];
  let synthTimer = null;
  let synthDuration = 0;

  function ensureCtx() {
    if (!ctx) {
      ctx = new (window.AudioContext || window.webkitAudioContext)();
      masterGain = ctx.createGain();
      masterGain.gain.value = 0.9;
      analyser = ctx.createAnalyser();
      analyser.fftSize = 256;
      masterGain.connect(analyser);
      analyser.connect(ctx.destination);
    }
    if (ctx.state === "suspended") ctx.resume();
    return ctx;
  }

  function currentTime() {
    if (!playing) return startOffset;
    return startOffset + (ctx.currentTime - startCtxTime);
  }

  function getAnalyser() { ensureCtx(); return analyser; }

  // ---------- Synth (story mode) playback ----------
  // notes: [{time, dur, freq, wave, gain, lane}], durations in seconds
  function playSynthTrack(notes, totalDuration) {
    stop();
    ensureCtx();
    mode = "synth";
    synthSchedule = notes.slice().sort((a, b) => a.time - b.time);
    synthDuration = totalDuration;
    startOffset = 0;
    startCtxTime = ctx.currentTime + 0.15;
    playing = true;
    scheduleAheadLoop();
  }

  let nextIdx = 0;
  function scheduleAheadLoop() {
    nextIdx = 0;
    clearInterval(synthTimer);
    synthTimer = setInterval(() => {
      if (!playing) return;
      const now = currentTime();
      const horizon = now + 0.25;
      while (nextIdx < synthSchedule.length && synthSchedule[nextIdx].time <= horizon) {
        scheduleNote(synthSchedule[nextIdx]);
        nextIdx++;
      }
      if (now > synthDuration + 1) stop();
    }, 40);
  }

  function scheduleNote(n) {
    const t = startCtxTime + n.time;
    const osc = ctx.createOscillator();
    const g = ctx.createGain();
    osc.type = n.wave || "sine";
    osc.frequency.setValueAtTime(n.freq, t);
    const peak = (n.gain ?? 0.5);
    g.gain.setValueAtTime(0, t);
    g.gain.linearRampToValueAtTime(peak, t + 0.012);
    g.gain.exponentialRampToValueAtTime(0.0008, t + (n.dur || 0.25));
    osc.connect(g);
    g.connect(masterGain);
    osc.start(t);
    osc.stop(t + (n.dur || 0.25) + 0.05);
  }

  // ---------- Buffer (uploaded file) playback ----------
  let decodedBuffer = null;
  async function loadFile(file) {
    ensureCtx();
    const arr = await file.arrayBuffer();
    decodedBuffer = await ctx.decodeAudioData(arr.slice(0));
    return decodedBuffer;
  }

  function playBuffer(offset = 0) {
    if (!decodedBuffer) return;
    stop();
    ensureCtx();
    mode = "buffer";
    currentSource = ctx.createBufferSource();
    currentSource.buffer = decodedBuffer;
    currentSource.connect(masterGain);
    startOffset = offset;
    startCtxTime = ctx.currentTime + 0.05;
    currentSource.start(startCtxTime, offset);
    playing = true;
    currentSource.onended = () => { if (mode === "buffer") playing = false; };
  }

  function stop() {
    playing = false;
    clearInterval(synthTimer);
    if (currentSource) {
      try { currentSource.stop(); } catch (e) {}
      currentSource.disconnect();
      currentSource = null;
    }
  }

  function pause() {
    if (!playing) return;
    startOffset = currentTime();
    playing = false;
    clearInterval(synthTimer);
    if (currentSource) { try { currentSource.stop(); } catch (e) {} currentSource = null; }
  }

  function resume() {
    if (playing) return;
    if (mode === "buffer") playBuffer(startOffset);
    else if (mode === "synth") {
      ensureCtx();
      startCtxTime = ctx.currentTime + 0.05;
      playing = true;
      scheduleAheadLoop();
      // fast-forward nextIdx to current offset
      nextIdx = synthSchedule.findIndex((n) => n.time >= startOffset);
      if (nextIdx < 0) nextIdx = synthSchedule.length;
    }
  }

  function getDuration() {
    if (mode === "buffer" && decodedBuffer) return decodedBuffer.duration;
    return synthDuration;
  }

  // ---------- Onset-detection auto-charting for uploaded songs ----------
  // Returns { bpm, notes: [{time, lane, type}] } derived purely from the
  // audio's own energy profile — no external metadata needed.
  async function autoChart(buffer, laneCount = 4) {
    const sr = buffer.sampleRate;
    const data = buffer.getChannelData(0);
    const hop = Math.floor(sr * 0.02); // 20ms frames
    const frames = Math.floor(data.length / hop);
    const bandCount = 3;
    const energies = Array.from({ length: bandCount }, () => new Float32Array(frames));

    // Simple 3-band split via crude FIR-ish differencing (fast, dependency-free)
    for (let f = 0; f < frames; f++) {
      let low = 0, mid = 0, high = 0;
      const start = f * hop;
      let prev = data[start] || 0;
      for (let i = 0; i < hop; i++) {
        const s = data[start + i] || 0;
        const diff = s - prev;
        prev = s;
        low += Math.abs(s);
        mid += Math.abs(diff);
        high += Math.abs(diff - (data[start + i - 1] || 0) + (data[start + i - 2] || 0));
      }
      energies[0][f] = low / hop;
      energies[1][f] = mid / hop;
      energies[2][f] = high / hop;
    }

    // Onset = local energy peak that exceeds a rolling average threshold
    const notes = [];
    const windowSize = 43; // ~ 0.86s rolling window at 20ms hop
    for (let band = 0; band < bandCount; band++) {
      const e = energies[band];
      for (let f = 2; f < frames - 2; f++) {
        let avg = 0, count = 0;
        for (let k = Math.max(0, f - windowSize); k < f; k++) { avg += e[k]; count++; }
        avg = count ? avg / count : 0;
        const threshold = avg * 1.5 + 0.0025;
        if (e[f] > threshold && e[f] > e[f - 1] && e[f] >= e[f + 1]) {
          notes.push({ time: (f * hop) / sr, band, strength: e[f] });
        }
      }
    }

    notes.sort((a, b) => a.time - b.time);

    // Merge onsets that land within 60ms of each other (keep strongest)
    const merged = [];
    for (const n of notes) {
      const last = merged[merged.length - 1];
      if (last && n.time - last.time < 0.06) {
        if (n.strength > last.strength) merged[merged.length - 1] = n;
      } else merged.push(n);
    }

    // Rough BPM estimate from inter-onset intervals (median-based, clamped)
    const intervals = [];
    for (let i = 1; i < merged.length; i++) intervals.push(merged[i].time - merged[i - 1].time);
    intervals.sort((a, b) => a - b);
    let bpm = 120;
    if (intervals.length) {
      let median = intervals[Math.floor(intervals.length / 2)];
      let candidate = 60 / Math.max(0.08, median);
      while (candidate > 190) candidate /= 2;
      while (candidate < 70) candidate *= 2;
      bpm = Math.round(candidate);
    }

    // Density scaling: cap notes/sec so charts stay playable, favor strongest
    const maxPerSecond = 3.4;
    const chart = [];
    let windowStart = 0;
    const byTimeWindow = [];
    for (const n of merged) {
      const w = Math.floor(n.time);
      byTimeWindow[w] = byTimeWindow[w] || [];
      byTimeWindow[w].push(n);
    }
    for (const w in byTimeWindow) {
      const arr = byTimeWindow[w].sort((a, b) => b.strength - a.strength).slice(0, Math.ceil(maxPerSecond));
      chart.push(...arr);
    }
    chart.sort((a, b) => a.time - b.time);

    const laneForBand = (band) => {
      // low->outer lanes (bass feel), high->inner lanes, spread across laneCount
      const map = [0, Math.floor(laneCount / 2), laneCount - 1];
      return Math.min(laneCount - 1, map[band] ?? (band % laneCount));
    };

    const finalNotes = chart.map((n, i) => {
      const nextSame = chart[i + 1];
      const isHold = nextSame && nextSame.band === n.band && (nextSame.time - n.time) < 0.5 && (nextSame.time - n.time) > 0.22;
      return {
        time: n.time,
        lane: laneForBand(n.band),
        type: isHold ? "hold" : (n.strength > 0.05 ? "burst" : "tap"),
        dur: isHold ? (nextSame.time - n.time) : 0,
      };
    });

    return { bpm, notes: finalNotes, duration: buffer.duration };
  }

  return {
    ensureCtx, getAnalyser, currentTime, getDuration,
    playSynthTrack, loadFile, playBuffer, stop, pause, resume, autoChart,
    isPlaying: () => playing,
  };
})();

/* ============================================================
   Web Audio engine.

   Two playback paths share one beat-based timeline:
     - SYNTH  : story/generative modes. Drums, bass and pad are
                scheduled ahead; the LEAD melody is deliberately not.
                The lead only sounds when the player hits its note
                ("You Are The Instrument"), so a clean run performs
                the melody and a sloppy one audibly falls apart.
     - BUFFER : imported files. The melody can't be gated on a fixed
                recording, so misses duck + lowpass the track instead.

   Timeline is a tempo map (beat -> ctx time), which lets Conductor
   mode change BPM live, bar by bar, without desyncing gameplay.
   ============================================================ */
const AudioEngine = (() => {
  let ctx = null;
  let master, musicBus, duckFilter, analyser, reverb, reverbSend, delayNode, delayFB, delaySend;
  const bus = {};

  let mode = "idle";            // 'synth' | 'buffer' | 'idle'
  let playing = false;
  let offsetMs = 0;             // player-calibrated audio offset

  /* ---------- tempo map ---------- */
  // Segments: { beat, time, bpm } — beat b >= seg.beat maps to
  // seg.time + (b - seg.beat) * 60 / seg.bpm
  let segs = [];

  function resetTimeline(startTime, bpm) {
    segs = [{ beat: 0, time: startTime, bpm }];
  }
  function pushTempo(beat, bpm) {
    const t = beatToTime(beat);
    segs.push({ beat, time: t, bpm });
  }
  function segFor(beat) {
    let s = segs[0];
    for (const seg of segs) { if (seg.beat <= beat) s = seg; else break; }
    return s;
  }
  function beatToTime(beat) {
    const s = segFor(beat);
    return s.time + (beat - s.beat) * (60 / s.bpm);
  }
  function timeToBeat(time) {
    let s = segs[0];
    for (const seg of segs) { if (seg.time <= time) s = seg; else break; }
    return s.beat + (time - s.time) / (60 / s.bpm);
  }
  function currentBeat() {
    if (!ctx) return 0;
    return timeToBeat(ctx.currentTime + offsetMs / 1000);
  }
  function currentBpm() { return segFor(currentBeat()).bpm; }

  /* ---------- graph ---------- */
  function ensureCtx() {
    if (!ctx) {
      ctx = new (window.AudioContext || window.webkitAudioContext)();

      master = ctx.createGain();       master.gain.value = 0.85;
      musicBus = ctx.createGain();     musicBus.gain.value = 1;
      duckFilter = ctx.createBiquadFilter();
      duckFilter.type = "lowpass";
      duckFilter.frequency.value = 20000;

      analyser = ctx.createAnalyser();
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.75;

      // cheap generated-impulse reverb — huge quality win for ~15 lines
      reverb = ctx.createConvolver();
      reverb.buffer = makeImpulse(1.9, 2.4);
      reverbSend = ctx.createGain(); reverbSend.gain.value = 0.26;

      delayNode = ctx.createDelay(1.2);
      delayNode.delayTime.value = 0.28;
      delayFB = ctx.createGain(); delayFB.gain.value = 0.32;
      delaySend = ctx.createGain(); delaySend.gain.value = 0.2;
      delayNode.connect(delayFB); delayFB.connect(delayNode);

      for (const name of ["drums", "bass", "pad", "lead"]) {
        const g = ctx.createGain();
        g.gain.value = { drums: 0.9, bass: 0.75, pad: 0.32, lead: 0.5 }[name];
        g.connect(musicBus);
        bus[name] = g;
      }

      musicBus.connect(duckFilter);
      musicBus.connect(reverbSend); reverbSend.connect(reverb); reverb.connect(duckFilter);
      musicBus.connect(delaySend);  delaySend.connect(delayNode); delayNode.connect(duckFilter);

      duckFilter.connect(analyser);
      analyser.connect(master);
      master.connect(ctx.destination);
    }
    if (ctx.state === "suspended") ctx.resume();
    return ctx;
  }

  function makeImpulse(seconds, decay) {
    const rate = 44100, len = Math.floor(rate * seconds);
    const buf = new (window.OfflineAudioContext || window.webkitOfflineAudioContext)(2, len, rate).createBuffer(2, len, rate);
    for (let c = 0; c < 2; c++) {
      const d = buf.getChannelData(c);
      for (let i = 0; i < len; i++) {
        d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, decay);
      }
    }
    return buf;
  }

  /* ---------- voices ---------- */
  function env(gainNode, t, peak, attack, hold, release) {
    const g = gainNode.gain;
    g.setValueAtTime(0.0001, t);
    g.exponentialRampToValueAtTime(Math.max(0.0002, peak), t + attack);
    g.setValueAtTime(Math.max(0.0002, peak), t + attack + hold);
    g.exponentialRampToValueAtTime(0.0001, t + attack + hold + release);
  }

  function kick(t, gain = 1) {
    const o = ctx.createOscillator(), g = ctx.createGain();
    o.type = "sine";
    o.frequency.setValueAtTime(132, t);
    o.frequency.exponentialRampToValueAtTime(42, t + 0.13);
    env(g, t, 0.95 * gain, 0.004, 0.02, 0.19);
    o.connect(g); g.connect(bus.drums);
    o.start(t); o.stop(t + 0.3);
  }

  function snare(t, gain = 1) {
    const noise = ctx.createBufferSource();
    noise.buffer = noiseBuffer();
    const bp = ctx.createBiquadFilter();
    bp.type = "bandpass"; bp.frequency.value = 1900; bp.Q.value = 0.8;
    const g = ctx.createGain();
    env(g, t, 0.5 * gain, 0.003, 0.01, 0.15);
    noise.connect(bp); bp.connect(g); g.connect(bus.drums);
    noise.start(t); noise.stop(t + 0.22);

    const o = ctx.createOscillator(), og = ctx.createGain();
    o.type = "triangle"; o.frequency.setValueAtTime(196, t);
    env(og, t, 0.24 * gain, 0.003, 0.005, 0.09);
    o.connect(og); og.connect(bus.drums);
    o.start(t); o.stop(t + 0.15);
  }

  function hat(t, gain = 1, open = false) {
    const noise = ctx.createBufferSource();
    noise.buffer = noiseBuffer();
    const hp = ctx.createBiquadFilter();
    hp.type = "highpass"; hp.frequency.value = 7600;
    const g = ctx.createGain();
    env(g, t, 0.17 * gain, 0.002, 0.004, open ? 0.19 : 0.038);
    noise.connect(hp); hp.connect(g); g.connect(bus.drums);
    noise.start(t); noise.stop(t + 0.3);
  }

  let _noiseBuf = null;
  function noiseBuffer() {
    if (_noiseBuf) return _noiseBuf;
    const len = ctx.sampleRate * 0.5;
    _noiseBuf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = _noiseBuf.getChannelData(0);
    for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    return _noiseBuf;
  }

  function bassNote(t, freq, dur, gain = 1) {
    const o = ctx.createOscillator(), o2 = ctx.createOscillator();
    const lp = ctx.createBiquadFilter(), g = ctx.createGain();
    o.type = "sawtooth"; o.frequency.value = freq;
    o2.type = "square"; o2.frequency.value = freq / 2; o2.detune.value = 6;
    lp.type = "lowpass";
    lp.frequency.setValueAtTime(Math.max(220, freq * 6), t);
    lp.frequency.exponentialRampToValueAtTime(Math.max(160, freq * 2.2), t + dur * 0.8);
    lp.Q.value = 6;
    env(g, t, 0.42 * gain, 0.012, dur * 0.55, dur * 0.5);
    o.connect(lp); o2.connect(lp); lp.connect(g); g.connect(bus.bass);
    o.start(t); o2.start(t); o.stop(t + dur + 0.2); o2.stop(t + dur + 0.2);
  }

  function padChord(t, freqs, dur, gain = 1) {
    for (const f of freqs) {
      for (const det of [-7, 7]) {
        const o = ctx.createOscillator(), g = ctx.createGain();
        o.type = "triangle"; o.frequency.value = f; o.detune.value = det;
        env(g, t, 0.1 * gain, dur * 0.32, dur * 0.28, dur * 0.6);
        o.connect(g); g.connect(bus.pad);
        o.start(t); o.stop(t + dur + 0.4);
      }
    }
  }

  // Lead voice — normally fired live by a player hit, not by the scheduler.
  // quality: 'perfect' | 'great' | 'good' | 'dead'
  function leadNote(freq, quality = "perfect", opts = {}) {
    if (!ctx || !freq) return;
    const t = ctx.currentTime + 0.001;
    const dur = opts.dur || 0.34;
    const detune = { perfect: 0, great: 4, good: 12, dead: 46 }[quality] ?? 0;
    const level = { perfect: 1, great: 0.86, good: 0.6, dead: 0.3 }[quality] ?? 1;

    const voices = opts.harmonize ? [1, 2, 1.5] : [1];  // Flow State adds harmonics
    for (const mult of voices) {
      const o = ctx.createOscillator(), g = ctx.createGain(), lp = ctx.createBiquadFilter();
      o.type = quality === "dead" ? "sawtooth" : "square";
      o.frequency.value = freq * mult;
      o.detune.value = detune + (mult !== 1 ? 3 : 0);
      lp.type = "lowpass";
      lp.frequency.setValueAtTime(quality === "dead" ? 900 : 5200, t);
      lp.frequency.exponentialRampToValueAtTime(quality === "dead" ? 300 : 1500, t + dur);
      env(g, t, 0.3 * level / voices.length, 0.006, dur * 0.3, dur * 0.75);
      o.connect(lp); lp.connect(g); g.connect(bus.lead);
      o.start(t); o.stop(t + dur + 0.3);
    }
  }

  /* ---------- synth scheduling ---------- */
  let song = null;          // { bpm, beats, layers:{drums,bass,pad}, ... }
  let schedTimer = null;
  let cursor = { drums: 0, bass: 0, pad: 0 };
  let onNeedBars = null;    // generative callback (Conductor/Endless)
  let generatedTo = 0;      // beats generated so far

  function playSong(newSong, opts = {}) {
    stop();
    ensureCtx();
    mode = "synth";
    song = { layers: { drums: [], bass: [], pad: [] }, ...newSong };
    onNeedBars = opts.onNeedBars || null;
    generatedTo = song.beats || 0;
    cursor = { drums: 0, bass: 0, pad: 0 };
    resetTimeline(ctx.currentTime + 0.35, song.bpm);
    playing = true;
    schedTimer = setInterval(tick, 25);
    tick();
  }

  function tick() {
    if (!playing || mode !== "synth") return;
    const nowBeat = currentBeat();
    const horizon = nowBeat + 2.5;   // schedule ~2.5 beats ahead

    for (const layer of ["drums", "bass", "pad"]) {
      const list = song.layers[layer] || [];
      while (cursor[layer] < list.length && list[cursor[layer]].beat <= horizon) {
        const ev = list[cursor[layer]++];
        const t = beatToTime(ev.beat);
        if (t < ctx.currentTime - 0.05) continue;
        if (layer === "drums") {
          if (ev.kind === "kick") kick(t, ev.gain);
          else if (ev.kind === "snare") snare(t, ev.gain);
          else hat(t, ev.gain, ev.kind === "openhat");
        } else if (layer === "bass") {
          bassNote(t, ev.freq, ev.dur, ev.gain);
        } else {
          padChord(t, ev.freqs, ev.dur, ev.gain);
        }
      }
    }

    // generative modes: ask for more bars before we run dry
    if (onNeedBars && nowBeat > generatedTo - 8) {
      const bpmNow = currentBpm();
      const added = onNeedBars(generatedTo, bpmNow);
      if (added) {
        for (const layer of ["drums", "bass", "pad"]) {
          if (added.layers && added.layers[layer]) song.layers[layer].push(...added.layers[layer]);
        }
        generatedTo = added.untilBeat;
        if (added.bpm && Math.abs(added.bpm - bpmNow) > 0.5) pushTempo(added.startBeat, added.bpm);
      }
    }

    if (!onNeedBars && nowBeat > (song.beats || 0) + 2) stop();
  }

  /* ---------- buffer (imported file) path ---------- */
  let decoded = null, srcNode = null, bufStartCtx = 0, bufOffset = 0;

  async function loadFile(file) {
    ensureCtx();
    const arr = await file.arrayBuffer();
    decoded = await ctx.decodeAudioData(arr);
    return decoded;
  }

  function playBuffer(offset = 0, bpm = 120) {
    if (!decoded) return;
    stop();
    ensureCtx();
    mode = "buffer";
    srcNode = ctx.createBufferSource();
    srcNode.buffer = decoded;
    srcNode.connect(musicBus);
    bufOffset = offset;
    bufStartCtx = ctx.currentTime + 0.08;
    resetTimeline(bufStartCtx - offset, bpm);
    srcNode.start(bufStartCtx, offset);
    playing = true;
    srcNode.onended = () => { if (mode === "buffer") playing = false; };
  }

  // Imported audio can't have its melody gated, so a miss ducks the mix.
  function duck(strength = 1) {
    if (!ctx) return;
    const t = ctx.currentTime;
    duckFilter.frequency.cancelScheduledValues(t);
    duckFilter.frequency.setValueAtTime(20000, t);
    duckFilter.frequency.exponentialRampToValueAtTime(Math.max(320, 1400 - 900 * strength), t + 0.03);
    duckFilter.frequency.exponentialRampToValueAtTime(20000, t + 0.42);
    musicBus.gain.cancelScheduledValues(t);
    musicBus.gain.setValueAtTime(musicBus.gain.value, t);
    musicBus.gain.linearRampToValueAtTime(Math.max(0.25, 1 - 0.45 * strength), t + 0.03);
    musicBus.gain.linearRampToValueAtTime(1, t + 0.5);
  }

  function setLayerGain(name, value, ramp = 0.3) {
    if (!ctx || !bus[name]) return;
    const t = ctx.currentTime;
    bus[name].gain.cancelScheduledValues(t);
    bus[name].gain.setValueAtTime(bus[name].gain.value, t);
    bus[name].gain.linearRampToValueAtTime(value, t + ramp);
  }

  /* ---------- transport ---------- */
  function stop() {
    playing = false;
    clearInterval(schedTimer); schedTimer = null;
    if (srcNode) { try { srcNode.stop(); } catch (e) {} srcNode.disconnect(); srcNode = null; }
  }

  let pausedBeat = 0;
  function pause() {
    if (!playing) return;
    pausedBeat = currentBeat();
    if (mode === "buffer") bufOffset = Math.max(0, ctx.currentTime - bufStartCtx + bufOffset);
    stop();
  }

  function resume() {
    if (playing) return;
    ensureCtx();
    if (mode === "buffer") { playBuffer(bufOffset, currentBpm()); return; }
    if (mode === "synth" && song) {
      const bpm = segFor(pausedBeat).bpm;
      segs = [{ beat: pausedBeat, time: ctx.currentTime + 0.2, bpm }];
      for (const layer of ["drums", "bass", "pad"]) {
        const list = song.layers[layer] || [];
        cursor[layer] = list.findIndex((e) => e.beat >= pausedBeat);
        if (cursor[layer] < 0) cursor[layer] = list.length;
      }
      playing = true;
      schedTimer = setInterval(tick, 25);
    }
  }

  /* ---------- analysis for imported tracks ---------- */
  // Onset detection over 3 crude energy bands -> lanes, plus a BPM
  // estimate from the inter-onset histogram.
  async function autoChart(buffer, laneCount = 4, density = 1) {
    const sr = buffer.sampleRate;
    const data = buffer.getChannelData(0);
    const hop = Math.floor(sr * 0.0116);           // ~11.6ms frames
    const frames = Math.floor(data.length / hop);
    const bands = [new Float32Array(frames), new Float32Array(frames), new Float32Array(frames)];

    let lp = 0, prev = 0, prevDiff = 0;
    for (let f = 0; f < frames; f++) {
      let low = 0, mid = 0, high = 0;
      const start = f * hop;
      for (let i = 0; i < hop; i++) {
        const s = data[start + i] || 0;
        lp += (s - lp) * 0.08;                     // one-pole lowpass
        const diff = s - prev; prev = s;
        const hf = diff - prevDiff; prevDiff = diff;
        low += Math.abs(lp);
        mid += Math.abs(diff);
        high += Math.abs(hf);
      }
      bands[0][f] = low / hop; bands[1][f] = mid / hop; bands[2][f] = high / hop;
    }

    const onsets = [];
    const win = 40;
    for (let b = 0; b < 3; b++) {
      const e = bands[b];
      for (let f = 2; f < frames - 2; f++) {
        let avg = 0, n = 0;
        for (let k = Math.max(0, f - win); k < f; k++) { avg += e[k]; n++; }
        avg = n ? avg / n : 0;
        if (e[f] > avg * 1.55 + 0.0022 && e[f] > e[f - 1] && e[f] >= e[f + 1]) {
          onsets.push({ time: (f * hop) / sr, band: b, strength: e[f] / (avg + 1e-6) });
        }
      }
    }
    onsets.sort((a, b) => a.time - b.time);

    const merged = [];
    for (const o of onsets) {
      const last = merged[merged.length - 1];
      if (last && o.time - last.time < 0.075) {
        if (o.strength > last.strength) merged[merged.length - 1] = o;
      } else merged.push(o);
    }

    // BPM: histogram of inter-onset intervals folded into 70-180
    const hist = new Map();
    for (let i = 1; i < merged.length; i++) {
      let iv = merged[i].time - merged[i - 1].time;
      if (iv < 0.1 || iv > 2) continue;
      let b = 60 / iv;
      while (b > 180) b /= 2;
      while (b < 70) b *= 2;
      const key = Math.round(b);
      hist.set(key, (hist.get(key) || 0) + 1);
    }
    let bpm = 120, bestCount = 0;
    for (const [k, v] of hist) if (v > bestCount) { bestCount = v; bpm = k; }

    // Density cap per second so charts stay playable
    const perSec = new Map();
    for (const o of merged) {
      const s = Math.floor(o.time);
      if (!perSec.has(s)) perSec.set(s, []);
      perSec.get(s).push(o);
    }
    const cap = Math.round(3.2 * density);
    const kept = [];
    for (const [, arr] of perSec) {
      arr.sort((a, b) => b.strength - a.strength);
      kept.push(...arr.slice(0, cap));
    }
    kept.sort((a, b) => a.time - b.time);

    const beat = 60 / bpm;
    let lastLane = -1;
    const notes = kept.map((o, i) => {
      // band picks a lane region, then alternate within it so the chart
      // doesn't hammer a single key
      const region = o.band === 0 ? 0 : o.band === 1 ? 1 : 2;
      let lane = [0, 1 + (i % 2), laneCount - 1][region];
      if (lane === lastLane && laneCount > 1) lane = (lane + 1) % laneCount;
      lastLane = lane;
      const next = kept[i + 1];
      const gap = next ? next.time - o.time : 9;
      const isHold = gap > beat * 0.9 && gap < beat * 2.6 && o.strength > 2.4;
      return {
        time: o.time,
        beat: o.time / beat,
        lane,
        type: isHold ? "hold" : o.strength > 3.2 ? "burst" : "tap",
        dur: isHold ? Math.min(gap * 0.7, beat * 2) : 0,
      };
    });

    return { bpm, notes, duration: buffer.duration };
  }

  /* ---------- misc ---------- */
  function getLevel() {
    if (!analyser) return 0;
    const arr = new Uint8Array(analyser.frequencyBinCount);
    analyser.getByteFrequencyData(arr);
    let sum = 0;
    for (let i = 0; i < arr.length; i++) sum += arr[i];
    return sum / (arr.length * 255);
  }
  function getSpectrum(out) {
    if (!analyser) return out;
    analyser.getByteFrequencyData(out);
    return out;
  }

  return {
    ensureCtx, playSong, playBuffer, loadFile, stop, pause, resume,
    leadNote, duck, setLayerGain, autoChart,
    currentBeat, beatToTime, timeToBeat, currentBpm, pushTempo,
    getLevel, getSpectrum, binCount: () => (analyser ? analyser.frequencyBinCount : 0),
    isPlaying: () => playing,
    setOffset: (ms) => { offsetMs = ms; },
    getOffset: () => offsetMs,
    getMode: () => mode,
  };
})();

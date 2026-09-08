/* ============================================================
   Procedural composer.

   Builds an arrangement from music-theory rules: a key, a chord
   progression, a drum pattern and a melodic contour. Output is
   split into scheduled backing layers (drums/bass/pad) and a LEAD
   line that becomes the playable chart — every note the player hits
   is a real melody note, so the chart and the tune are the same
   object rather than one being decoration over the other.
   ============================================================ */
const Composer = (() => {
  const SCALES = {
    major:    [0, 2, 4, 5, 7, 9, 11],
    minor:    [0, 2, 3, 5, 7, 8, 10],
    dorian:   [0, 2, 3, 5, 7, 9, 10],
    phrygian: [0, 1, 3, 5, 7, 8, 10],
    lydian:   [0, 2, 4, 6, 7, 9, 11],
    mixo:     [0, 2, 4, 5, 7, 9, 10],
  };
  const NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];

  // Common progressions as scale degrees (0-indexed)
  const PROGRESSIONS = [
    [0, 5, 3, 4],
    [0, 4, 5, 3],
    [5, 3, 0, 4],
    [0, 3, 4, 4],
    [5, 4, 3, 4],
    [0, 6, 5, 4],
  ];

  const DRUM_PATTERNS = {
    // 16 slots per bar (16th notes)
    straight: {
      kick:  [1,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
      snare: [0,0,0,0, 1,0,0,0, 0,0,0,0, 1,0,0,0],
      hat:   [1,0,1,0, 1,0,1,0, 1,0,1,0, 1,0,1,0],
    },
    driving: {
      kick:  [1,0,0,0, 0,0,1,0, 1,0,0,0, 0,0,0,0],
      snare: [0,0,0,0, 1,0,0,0, 0,0,0,0, 1,0,0,1],
      hat:   [1,1,1,1, 1,1,1,1, 1,1,1,1, 1,1,1,1],
    },
    halftime: {
      kick:  [1,0,0,0, 0,0,0,0, 0,0,1,0, 0,0,0,0],
      snare: [0,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
      hat:   [1,0,0,1, 0,0,1,0, 1,0,0,1, 0,0,1,0],
    },
    breaks: {
      kick:  [1,0,0,1, 0,0,1,0, 0,0,1,0, 0,1,0,0],
      snare: [0,0,0,0, 1,0,0,0, 0,1,0,0, 1,0,0,1],
      hat:   [1,0,1,1, 0,1,1,0, 1,0,1,1, 0,1,1,0],
    },
    sparse: {
      kick:  [1,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0],
      snare: [0,0,0,0, 0,0,0,0, 1,0,0,0, 0,0,0,0],
      hat:   [0,0,1,0, 0,0,1,0, 0,0,1,0, 0,0,1,0],
    },
  };

  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function freq(semisFromC4, octave = 4) {
    return 440 * Math.pow(2, (semisFromC4 + (octave - 4) * 12 - 9) / 12);
  }

  function degreeToSemis(scale, degree) {
    const oct = Math.floor(degree / scale.length);
    return scale[((degree % scale.length) + scale.length) % scale.length] + oct * 12;
  }

  /* ---------- one section (a run of bars sharing a feel) ---------- */
  function buildSection(cfg, out, startBeat) {
    const {
      rng, scale, rootSemi, bars, drumPattern, density, laneCount,
      octave, energy, progression, syncopation,
    } = cfg;

    const pat = DRUM_PATTERNS[drumPattern] || DRUM_PATTERNS.straight;
    let lastDegree = 0;

    for (let bar = 0; bar < bars; bar++) {
      const barBeat = startBeat + bar * 4;
      const chordDeg = progression[bar % progression.length];

      // --- drums ---
      for (let s = 0; s < 16; s++) {
        const beat = barBeat + s / 4;
        if (pat.kick[s]) out.drums.push({ beat, kind: "kick", gain: 1 });
        if (pat.snare[s]) out.drums.push({ beat, kind: "snare", gain: 0.9 });
        if (pat.hat[s]) {
          const open = s % 8 === 6 && rng() > 0.6;
          out.drums.push({ beat, kind: open ? "openhat" : "hat", gain: 0.55 + rng() * 0.3 });
        }
      }
      // fill at the end of every 4th bar
      if (bar % 4 === 3) {
        for (let s = 12; s < 16; s++) {
          if (rng() > 0.35) out.drums.push({ beat: barBeat + s / 4, kind: "snare", gain: 0.4 + (s - 12) * 0.14 });
        }
      }

      // --- bass: root on 1, plus movement ---
      const rootFreq = freq(rootSemi + degreeToSemis(scale, chordDeg), 2);
      out.bass.push({ beat: barBeat, freq: rootFreq, dur: 0.55, gain: 1 });
      out.bass.push({ beat: barBeat + 1.5, freq: rootFreq, dur: 0.35, gain: 0.75 });
      if (energy > 0.5) {
        out.bass.push({ beat: barBeat + 2.5, freq: freq(rootSemi + degreeToSemis(scale, chordDeg + 4), 2), dur: 0.4, gain: 0.8 });
        out.bass.push({ beat: barBeat + 3.5, freq: freq(rootSemi + degreeToSemis(scale, chordDeg + 2), 2), dur: 0.3, gain: 0.7 });
      }

      // --- pad: triad held across the bar ---
      out.pad.push({
        beat: barBeat,
        freqs: [0, 2, 4].map((i) => freq(rootSemi + degreeToSemis(scale, chordDeg + i), 3)),
        dur: 3.6, gain: 1,
      });

      // --- lead / chart: the melody the player performs ---
      const slots = [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5];
      const sixteenths = syncopation ? [0.25, 0.75, 1.25, 1.75, 2.25, 2.75, 3.25, 3.75] : [];
      const candidates = slots.concat(sixteenths).sort((a, b) => a - b);

      for (const off of candidates) {
        const isEighth = slots.includes(off);
        const chance = (isEighth ? 0.82 : 0.34) * density;
        if (rng() > chance) continue;

        // melodic contour: step around the chord tones, occasional leap
        const chordTones = [chordDeg, chordDeg + 2, chordDeg + 4];
        let degree;
        if (rng() > 0.62) degree = chordTones[Math.floor(rng() * 3)];
        else degree = lastDegree + (rng() > 0.5 ? 1 : -1);
        degree = Math.max(-3, Math.min(11, degree));
        lastDegree = degree;

        const noteOct = octave + (degree > 7 ? 1 : 0);
        const f = freq(rootSemi + degreeToSemis(scale, degree), noteOct);

        // lane follows pitch contour so the chart reads like the melody looks
        const span = 14;
        const norm = Math.max(0, Math.min(1, (degree + 3) / span));
        const lane = Math.max(0, Math.min(laneCount - 1, Math.floor(norm * laneCount)));

        const beat = barBeat + off;
        const nextGap = 0.5;
        let type = "tap";
        if (rng() > 0.93) type = "hold";
        else if (rng() > 0.9 && energy > 0.6) type = "burst";

        out.lead.push({
          beat, freq: f, lane, type,
          dur: type === "hold" ? nextGap * 2 : 0,
          degree,
        });
      }
    }
    return startBeat + bars * 4;
  }

  /* ---------- full song ---------- */
  function compose(spec) {
    const rng = mulberry32(spec.seed || 1);
    const scale = SCALES[spec.scale] || SCALES.minor;
    const rootSemi = NAMES.indexOf(spec.root || "C");
    const laneCount = spec.laneCount || 4;
    const progression = PROGRESSIONS[(spec.seed || 1) % PROGRESSIONS.length];

    const out = { drums: [], bass: [], pad: [], lead: [] };
    let beat = 0;

    // Arrangement: intro -> verse -> lift -> chorus -> break -> chorus -> outro
    const chorus = spec.chorusDrums || "driving";
    const arrangement = spec.arrangement || [
      { bars: 2, drums: "sparse",   density: 0.35, energy: 0.2,  sync: false },  // intro
      { bars: 4, drums: "straight", density: 0.7,  energy: 0.45, sync: false },  // verse 1
      { bars: 4, drums: "driving",  density: 0.85, energy: 0.7,  sync: true },   // lift
      { bars: 4, drums: chorus,     density: 1.0,  energy: 0.9,  sync: true },   // chorus 1
      { bars: 2, drums: "halftime", density: 0.45, energy: 0.35, sync: false },  // break
      { bars: 4, drums: "straight", density: 0.8,  energy: 0.6,  sync: false },  // verse 2
      { bars: 4, drums: "breaks",   density: 0.95, energy: 0.85, sync: true },   // bridge
      { bars: 4, drums: chorus,     density: 1.05, energy: 1.0,  sync: true },   // chorus 2
      { bars: 4, drums: chorus,     density: 1.1,  energy: 1.0,  sync: true },   // final chorus
      { bars: 2, drums: "sparse",   density: 0.4,  energy: 0.25, sync: false },  // outro
    ];

    const sections = [];
    for (const sec of arrangement) {
      const start = beat;
      beat = buildSection({
        rng, scale, rootSemi, bars: sec.bars,
        drumPattern: sec.drums,
        density: sec.density * (spec.densityScale || 1),
        laneCount,
        octave: spec.octave || 5,
        energy: sec.energy,
        progression,
        syncopation: sec.sync,
      }, out, start);
      sections.push({ startBeat: start, endBeat: beat, energy: sec.energy });
    }

    out.lead.sort((a, b) => a.beat - b.beat);
    // enforce a minimum gap per lane so charts stay physically playable
    const lastPerLane = {};
    const cleanLead = [];
    for (const n of out.lead) {
      const last = lastPerLane[n.lane];
      if (last !== undefined && n.beat - last < 0.24) continue;
      lastPerLane[n.lane] = n.beat;
      cleanLead.push(n);
    }
    out.lead = cleanLead;

    return {
      bpm: spec.bpm,
      beats: beat,
      duration: (beat * 60) / spec.bpm,
      layers: { drums: out.drums, bass: out.bass, pad: out.pad },
      lead: out.lead,
      sections,
      key: `${spec.root} ${spec.scale}`,
    };
  }

  /* ---------- generative bars (Conductor / Endless) ---------- */
  function generateBars(state, startBeat, bars, bpm) {
    const rng = mulberry32(Math.floor(startBeat * 7919) ^ (state.seed || 3));
    const scale = SCALES[state.scale] || SCALES.minor;
    const rootSemi = NAMES.indexOf(state.root || "A");
    const out = { drums: [], bass: [], pad: [], lead: [] };
    const progression = PROGRESSIONS[(state.seed || 3) % PROGRESSIONS.length];
    const endBeat = buildSection({
      rng, scale, rootSemi, bars,
      drumPattern: state.drums || "driving",
      density: state.density || 0.9,
      laneCount: state.laneCount || 4,
      octave: state.octave || 5,
      energy: state.energy ?? 0.8,
      progression,
      syncopation: (state.density || 0.9) > 0.8,
    }, out, startBeat);
    out.lead.sort((a, b) => a.beat - b.beat);
    return { layers: { drums: out.drums, bass: out.bass, pad: out.pad }, lead: out.lead, untilBeat: endBeat, startBeat, bpm };
  }

  return { compose, generateBars, SCALES, PROGRESSIONS };
})();

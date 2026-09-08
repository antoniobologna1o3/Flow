/* Story Mode content: original, procedurally-composed music (no copyrighted
   audio) + narrative beats. The "unique" hook: FLOW STATE — as combo grows,
   the composer's own melody responds live (see game.js applyFlowState),
   the chapter's mood literally brightens with your performance. */
const StoryData = (() => {
  const NOTE_NAMES = ["C","C#","D","D#","E","F","F#","G","G#","A","A#","B"];
  function noteFreq(name, octave) {
    const idx = NOTE_NAMES.indexOf(name);
    const n = idx + (octave - 4) * 12;
    return 440 * Math.pow(2, (n - 9) / 12);
  }

  // Procedurally compose a track from a scale + chord progression + rhythm
  // seed, so every chapter is original music generated from music-theory
  // rules rather than hand-sampled or copied audio.
  function compose({ bpm, bars, scaleRoot, scaleType, seed, laneCount, mood }) {
    const scales = {
      major: [0,2,4,5,7,9,11],
      minor: [0,2,3,5,7,8,10],
      dorian: [0,2,3,5,7,9,10],
      phrygian:[0,1,3,5,7,8,10],
      lydian: [0,2,4,6,7,9,11],
    };
    const scale = scales[scaleType] || scales.major;
    const rootIdx = NOTE_NAMES.indexOf(scaleRoot);
    const beatDur = 60 / bpm;
    let rng = mulberry32(seed);

    const notes = [];
    const totalBeats = bars * 4;
    let waveByMood = mood === "bright" ? "triangle" : mood === "dark" ? "sawtooth" : "square";

    for (let beat = 0; beat < totalBeats; beat += 0.5) {
      const barPos = beat % 4;
      const isDownbeat = barPos === 0;
      const density = 0.55 + 0.25 * Math.sin(beat / 6);
      if (rng() > density) continue;

      const degree = scale[Math.floor(rng() * scale.length)];
      const octave = 4 + (rng() > 0.75 ? 1 : 0) + (isDownbeat ? 0 : 0);
      const semis = rootIdx + degree;
      const noteName = NOTE_NAMES[((semis % 12) + 12) % 12];
      const oct = octave + Math.floor(semis / 12);
      const freq = noteFreq(noteName, oct);

      const lane = Math.floor(rng() * laneCount);
      const dur = (rng() > 0.85 ? 2 : rng() > 0.6 ? 1 : 0.5) * beatDur;
      let type = "tap";
      if (dur >= beatDur * 1.5) type = "hold";
      else if (rng() > 0.88) type = "burst";

      notes.push({
        time: beat * beatDur,
        dur,
        freq,
        wave: waveByMood,
        gain: isDownbeat ? 0.55 : 0.4,
        lane,
        type,
      });
    }
    // Bass anchor every downbeat for musical glue
    for (let beat = 0; beat < totalBeats; beat += 4) {
      notes.push({
        time: beat * beatDur, dur: beatDur * 1.8,
        freq: noteFreq(scaleRoot, 2), wave: "sine", gain: 0.35, lane: -1, type: "bass",
      });
    }
    notes.sort((a, b) => a.time - b.time);
    return { notes, duration: totalBeats * beatDur, bpm };
  }

  function mulberry32(a) {
    return function () {
      a |= 0; a = (a + 0x6D2B79F5) | 0;
      let t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const chapters = [
    {
      id: 1, title: "First Signal", mood: "bright",
      gradient: ["#7c5cff", "#5cf0ff"],
      music: { bpm: 100, bars: 24, scaleRoot: "C", scaleType: "major", seed: 11, mood: "bright" },
      narrative: [
        { speaker: "???", text: "Something's transmitting on a frequency no one's used in years." },
        { speaker: "You", text: "It sounds like... music? Or is it calling me?" },
        { speaker: "???", text: "Follow the beat. That's the only way in." },
      ],
      outro: [{ speaker: "???", text: "You caught the signal. It's getting stronger now." }],
    },
    {
      id: 2, title: "Static Bloom", mood: "dark",
      gradient: ["#ff5ca8", "#7c5cff"],
      music: { bpm: 118, bars: 26, scaleRoot: "A", scaleType: "minor", seed: 27, mood: "dark" },
      narrative: [
        { speaker: "Echo", text: "Careful — the static here bites back if you miss the rhythm." },
        { speaker: "You", text: "It's like the whole room is breathing in time." },
      ],
      outro: [{ speaker: "Echo", text: "You're not just listening anymore. You're part of it." }],
    },
    {
      id: 3, title: "Neon Drift", mood: "bright",
      gradient: ["#5cf0ff", "#5cff9d"],
      music: { bpm: 132, bars: 28, scaleRoot: "D", scaleType: "dorian", seed: 42, mood: "bright" },
      narrative: [
        { speaker: "Echo", text: "Faster now. The city up ahead moves at its own tempo." },
        { speaker: "You", text: "I can feel every beat before it lands." },
      ],
      outro: [{ speaker: "Echo", text: "Flow State. That's what they call it when the beat becomes you." }],
    },
    {
      id: 4, title: "Glass Horizon", mood: "melancholy",
      gradient: ["#7c5cff", "#ffd75c"],
      music: { bpm: 88, bars: 22, scaleRoot: "E", scaleType: "phrygian", seed: 5, mood: "dark" },
      narrative: [
        { speaker: "???", text: "Every signal fades eventually. This one's almost gone." },
        { speaker: "You", text: "Then I'll play it until it can't." },
      ],
      outro: [{ speaker: "???", text: "...it held on. Because you did." }],
    },
    {
      id: 5, title: "Overdrive", mood: "intense",
      gradient: ["#ff5c5c", "#ff5ca8"],
      music: { bpm: 150, bars: 30, scaleRoot: "F#", scaleType: "lydian", seed: 91, mood: "dark" },
      narrative: [
        { speaker: "Echo", text: "This is the fastest signal in the network. Hold on." },
      ],
      outro: [{ speaker: "Echo", text: "Nobody's ever synced with it that cleanly before." }],
    },
    {
      id: 6, title: "Afterglow", mood: "triumphant",
      gradient: ["#5cf0ff", "#ff5ca8"],
      music: { bpm: 108, bars: 32, scaleRoot: "C", scaleType: "major", seed: 63, mood: "bright" },
      narrative: [
        { speaker: "Echo", text: "This is the source. Everything you've played led here." },
        { speaker: "You", text: "One more time. All of it, together." },
      ],
      outro: [{ speaker: "Echo", text: "The signal's yours now. Play it whenever you need to come back." }],
    },
  ];

  function getChapter(id) {
    const ch = chapters.find((c) => c.id === id);
    if (!ch) return null;
    if (!ch._composed) ch._composed = compose({ ...ch.music, laneCount: 4 });
    return { ...ch, track: ch._composed };
  }

  return { chapters, getChapter, compose };
})();

/* ============================================================
   Mode registry. Every mode is the same run engine plus modifiers,
   which keeps ten modes from becoming ten codebases.

   Modifier flags read by game.js:
     scrollDir      1 = toward you (default), -1 = notes rise away
     blackout       phrases go invisible and repeat — play from memory
     echoDebt       missed notes return later as catchable echoes
     split          lanes belong to two panned channels
     inversion      lanes mirror partway through
     generative     chart is produced live, never ends
     tempoFollow    YOUR tap rate sets the song's BPM
     ghostRace      race a recording of your own best run
     chain          play a list of chapters as one continuous run
     loop           repeat a short pattern forever (drills)
   ============================================================ */
const Modes = {
  story: {
    id: "story", name: "Story Mode", sub: "8 chapters · original score",
    kind: "chapters", flags: { echoDebt: true },
    desc: "Eight chapters of original music that teach a new mechanic each time. The melody isn't a backing track — you play it.",
  },

  library: {
    id: "library", name: "Your Music", sub: "import · auto-chart · your colors",
    kind: "library", flags: { echoDebt: true },
    desc: "Bring a song you own. Flow reads its waveform, builds a chart, and repaints the whole stage using the colors of its cover.",
  },

  rewind: {
    id: "rewind", name: "Rewind", sub: "notes rise · reading inverted",
    kind: "chapters", flags: { scrollDir: -1, echoDebt: true },
    group: 1,
    desc: "The same charts, scrolling the wrong way. Reading direction is the most automatic thing your hands learn — this takes it away.",
  },

  blackout: {
    id: "blackout", name: "Blackout", sub: "phrases vanish · play from memory",
    kind: "chapters", flags: { blackout: true, echoDebt: true },
    group: 1,
    desc: "Every phrase plays once lit, then repeats with the notes invisible. You're not reading any more — you're remembering.",
  },

  conductor: {
    id: "conductor", name: "Conductor", sub: "you set the tempo",
    kind: "generative", flags: { generative: true, tempoFollow: true },
    group: 1,
    desc: "No fixed BPM. The song speeds up and slows down to match how fast you're actually tapping — push it and the whole arrangement chases you.",
  },

  ghost: {
    id: "ghost", name: "Ghost Race", sub: "race your best run",
    kind: "chapters", flags: { ghostRace: true, echoDebt: true },
    group: 2,
    desc: "Your best run on a chart is recorded hit-by-hit and replayed beside you as a translucent rival — including the exact bar where it broke.",
  },

  marathon: {
    id: "marathon", name: "Marathon", sub: "all 8 · one combo · no resets",
    kind: "chain", chain: [1, 2, 3, 4, 5, 6, 7, 8], flags: { chain: true, echoDebt: true },
    group: 2,
    desc: "Every chapter back to back with a single combo and a single Flow meter carried the whole way. One early miss echoes a long time.",
  },

  endless: {
    id: "endless", name: "Endless", sub: "generative · escalates forever",
    kind: "generative", flags: { generative: true, escalate: true, echoDebt: true },
    group: 2,
    desc: "A chart written bar by bar while you play it, tightening density and tempo until you drop it.",
  },

  daily: {
    id: "daily", name: "Daily Remix", sub: "", // filled at runtime
    kind: "daily", flags: { echoDebt: true },
    group: 3,
    desc: "One chapter, one modifier, seeded by today's date — the same run for everyone, once a day.",
  },

  drills: {
    id: "drills", name: "Drills", sub: "practice loops · accuracy only",
    kind: "drills", flags: { loop: true, generative: true },
    group: 3,
    desc: "Short isolated loops for one skill at a time, scored on rolling accuracy instead of a letter grade.",
  },
};

/* Daily Remix: deterministic per calendar day */
Modes.dailyPick = function () {
  const d = new Date();
  const seed = d.getUTCFullYear() * 10000 + (d.getUTCMonth() + 1) * 100 + d.getUTCDate();
  const chapterId = (seed % 8) + 1;
  const mods = [
    { key: "scrollDir", value: -1, label: "REWIND" },
    { key: "blackout", value: true, label: "BLACKOUT" },
    { key: "split", value: true, label: "SPLIT SIGNAL" },
    { key: "inversion", value: true, label: "INVERSION" },
    { key: "speed", value: 1.25, label: "OVERCLOCK" },
    { key: "mirror", value: true, label: "MIRROR" },
  ];
  const mod = mods[seed % mods.length];
  return { seed, chapterId, mod };
};

Modes.drillList = [
  { id: "steady", name: "Steady Tap", sub: "raw accuracy", pattern: "steady", bpm: 120 },
  { id: "alt", name: "Alternation", sub: "two-lane switching", pattern: "alt", bpm: 130 },
  { id: "burst", name: "Bursts", sub: "chord stabs", pattern: "burst", bpm: 124 },
  { id: "stream", name: "Streams", sub: "sixteenth runs", pattern: "stream", bpm: 140 },
];

Modes.order = ["story", "library", "rewind", "blackout", "conductor", "ghost", "marathon", "endless", "daily", "drills"];

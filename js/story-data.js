/* Story Mode: 8 chapters of original, procedurally-composed music.
   Each chapter declares a key, tempo, arrangement feel and a mechanic
   the chapter introduces — the narrative and the mechanic ramp together. */
const StoryData = (() => {
  const chapters = [
    {
      id: 1, title: "Doors Open", subtitle: "the queue outside",
      gradient: ["#3d2f7a", "#7c5cff"],
      spec: { bpm: 92, root: "C", scale: "major", seed: 11, octave: 5, densityScale: 0.85, chorusDrums: "straight" },
      teaches: null,
      narrative: [
        { speaker: "???", text: "You can hear it through the fence before you can see anything at all." },
        { speaker: "You", text: "That's not a recording. Something's playing this live." },
        { speaker: "???", text: "Then stop listening and start playing. The melody only exists if you hit it." },
      ],
      outro: [{ speaker: "???", text: "Doors are open. Whatever's in there has been waiting for someone who can keep time." }],
    },
    {
      id: 2, title: "First Signal", subtitle: "learning the language",
      gradient: ["#7c5cff", "#5cf0ff"],
      spec: { bpm: 104, root: "G", scale: "major", seed: 23, octave: 5, densityScale: 0.85 },
      teaches: "echo",
      narrative: [
        { speaker: "Echo", text: "Careful. This place keeps everything you drop." },
        { speaker: "You", text: "Keeps it how?" },
        { speaker: "Echo", text: "Miss a note and it comes back at you eight bars later, wearing a different color. Catch it and you're square." },
      ],
      outro: [{ speaker: "Echo", text: "You cleared your debts. Not everyone does." }],
    },
    {
      id: 3, title: "Static Bloom", subtitle: "the crowd finds its pulse",
      gradient: ["#ff5ca8", "#7c5cff"],
      spec: { bpm: 118, root: "A", scale: "minor", seed: 37, octave: 5, densityScale: 1, chorusDrums: "driving" },
      teaches: "flow",
      narrative: [
        { speaker: "Echo", text: "Chain enough clean hits and you'll feel the room tilt toward you." },
        { speaker: "You", text: "Everything just got… louder. Warmer." },
        { speaker: "Echo", text: "That's Flow. The song starts harmonizing with you instead of at you." },
      ],
      outro: [{ speaker: "Echo", text: "You found the state. Now try holding it when it costs you something." }],
    },
    {
      id: 4, title: "Neon Drift", subtitle: "moving without looking",
      gradient: ["#5cf0ff", "#5cff9d"],
      spec: { bpm: 132, root: "D", scale: "dorian", seed: 51, octave: 5, densityScale: 1.05, chorusDrums: "breaks" },
      teaches: "blackout",
      narrative: [
        { speaker: "Echo", text: "This next stretch, the lights cut out mid-phrase." },
        { speaker: "You", text: "How am I supposed to read notes I can't see?" },
        { speaker: "Echo", text: "You don't read them. It repeats the phrase it just played. Play it back from memory." },
      ],
      outro: [{ speaker: "Echo", text: "Your hands knew it before your eyes did. That's the whole trick." }],
    },
    {
      id: 5, title: "Glass Horizon", subtitle: "the quiet one",
      gradient: ["#7c5cff", "#ffd75c"],
      spec: { bpm: 84, root: "E", scale: "phrygian", seed: 67, octave: 4, densityScale: 0.9, chorusDrums: "halftime" },
      teaches: null,
      narrative: [
        { speaker: "???", text: "Every signal thins out eventually. This one's nearly transparent." },
        { speaker: "You", text: "Then I'll be the loud part." },
      ],
      outro: [{ speaker: "???", text: "It held. Because you did." }],
    },
    {
      id: 6, title: "Cross Talk", subtitle: "two songs at once",
      gradient: ["#ff8a5c", "#ff5ca8"],
      spec: { bpm: 140, root: "F#", scale: "lydian", seed: 83, octave: 5, densityScale: 1.15, chorusDrums: "breaks" },
      teaches: "split",
      narrative: [
        { speaker: "Echo", text: "Two channels, hard left and hard right, fighting for the same four lanes." },
        { speaker: "You", text: "Which one do I follow?" },
        { speaker: "Echo", text: "Both. They lock together every eight bars — ride those bars and the rest is just holding on." },
      ],
      outro: [{ speaker: "Echo", text: "They synced. You made them sync." }],
    },
    {
      id: 7, title: "Overdrive", subtitle: "no more teaching",
      gradient: ["#ff5470", "#ff5ca8"],
      spec: { bpm: 152, root: "B", scale: "minor", seed: 97, octave: 5, densityScale: 1.25, chorusDrums: "driving" },
      teaches: "inversion",
      narrative: [
        { speaker: "Echo", text: "Fastest thing on the network, and halfway through it flips the lanes on you." },
        { speaker: "You", text: "Of course it does." },
      ],
      outro: [{ speaker: "Echo", text: "Nobody has ridden that inversion clean on a first pass. Nobody." }],
    },
    {
      id: 8, title: "Afterglow", subtitle: "one more song",
      gradient: ["#5cf0ff", "#ff5ca8"],
      spec: { bpm: 110, root: "C", scale: "major", seed: 113, octave: 5, densityScale: 1.1 },
      teaches: "all",
      narrative: [
        { speaker: "Echo", text: "This is the source. Everything you've played was a piece of this one." },
        { speaker: "You", text: "Then let's hear all of it." },
        { speaker: "Echo", text: "Every mechanic, from the first bar. You already know the language." },
      ],
      outro: [
        { speaker: "Echo", text: "The signal's yours now." },
        { speaker: "Echo", text: "Bring your own songs next time. It'll learn those too." },
      ],
    },
  ];

  const cache = new Map();
  function getChapter(id, laneCount = 4) {
    const ch = chapters.find((c) => c.id === id);
    if (!ch) return null;
    const key = `${id}_${laneCount}`;
    if (!cache.has(key)) cache.set(key, Composer.compose({ ...ch.spec, laneCount }));
    return { ...ch, song: cache.get(key) };
  }

  return { chapters, getChapter };
})();

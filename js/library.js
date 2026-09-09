/* "Your Music" local library — IndexedDB-backed so audio files (which are
   too large for localStorage) persist across sessions. Everything runs
   on-device; nothing is uploaded anywhere. */
const Library = (() => {
  const DB_NAME = "flow-library";
  const STORE = "tracks";
  let dbPromise = null;

  function db() {
    if (dbPromise) return dbPromise;
    dbPromise = new Promise((resolve, reject) => {
      const req = indexedDB.open(DB_NAME, 1);
      req.onupgradeneeded = () => {
        const d = req.result;
        if (!d.objectStoreNames.contains(STORE)) {
          d.createObjectStore(STORE, { keyPath: "id" });
        }
      };
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    return dbPromise;
  }

  async function addTrack({ title, artist, audioFile, artFile, lyricsText }) {
    const id = "t_" + Date.now() + "_" + Math.random().toString(36).slice(2, 8);
    let artDataUrl = null;
    let palette = { primary: "#7c5cff", secondary: "#ff5ca8", accent: "#5cf0ff" };
    let embedded = false;

    // The file usually already knows what it is — read its own tags for
    // title, artist and cover art before falling back to anything else.
    let tags = { title: null, artist: null, album: null, picture: null };
    try { tags = await Tags.read(audioFile); } catch (e) {}
    if (!title || !title.trim()) title = tags.title || stripExt(audioFile.name);
    if (!artist || artist === "Unknown Artist") artist = tags.artist || "Unknown Artist";

    if (!artFile && tags.picture) { artFile = tags.picture.blob; embedded = true; }

    if (artFile) {
      artDataUrl = await fileToDataUrl(artFile);
      palette = await paletteFromDataUrl(artDataUrl);
    } else {
      // No embedded/uploaded art: generate a distinct-but-on-brand cover
      // from the track's own characteristics (title/artist hash -> hue),
      // consistent with Flow's two accent colors either way.
      const hue = hashHue(title + artist);
      artDataUrl = generateCoverDataUrl(title, artist, hue);
      palette = paletteFromHue(hue);
    }

    const record = {
      id, title: title || "Untitled", artist: artist || "Unknown Artist",
      audioBlob: audioFile, artDataUrl, palette,
      lyricsText: lyricsText || null,
      generatedArt: !artFile,
      embeddedArt: embedded,
      album: tags.album || null,
      addedAt: Date.now(),
    };
    const d = await db();
    await new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(record);
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
    return record;
  }

  async function getAll() {
    const d = await db();
    return new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, "readonly");
      const req = tx.objectStore(STORE).getAll();
      req.onsuccess = () => resolve(req.result.sort((a, b) => b.addedAt - a.addedAt));
      req.onerror = () => reject(req.error);
    });
  }

  // Patch fields on an existing track (used by the in-app lyrics editor,
  // so lyrics no longer require a file on disk).
  async function updateTrack(id, patch) {
    const d = await db();
    const rec = await new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, "readonly");
      const req = tx.objectStore(STORE).get(id);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
    if (!rec) return null;
    Object.assign(rec, patch);
    await new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, "readwrite");
      tx.objectStore(STORE).put(rec);
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
    return rec;
  }

  async function remove(id) {
    const d = await db();
    return new Promise((resolve, reject) => {
      const tx = d.transaction(STORE, "readwrite");
      tx.objectStore(STORE).delete(id);
      tx.oncomplete = resolve;
      tx.onerror = () => reject(tx.error);
    });
  }

  function search(tracks, query) {
    const q = (query || "").trim().toLowerCase();
    if (!q) return tracks;
    return tracks.filter((t) =>
      t.title.toLowerCase().includes(q) || t.artist.toLowerCase().includes(q)
    );
  }

  function stripExt(name) { return String(name || "Untitled").replace(/\.[^.]+$/, ""); }

  // Import many files at once (a folder drop, a multi-select). Audio files
  // become tracks; loose images and .lrc files in the same drop are matched
  // to them by filename, or used as a shared album cover.
  async function addMany(files, onProgress) {
    const list = [...files];
    const isAudio = (f) => /\.(mp3|m4a|mp4|aac|flac|ogg|oga|opus|wav|wave|weba|webm)$/i.test(f.name) || (f.type || "").startsWith("audio/");
    const isImage = (f) => /\.(jpe?g|png|gif|webp|bmp)$/i.test(f.name) || (f.type || "").startsWith("image/");
    const isLyric = (f) => /\.(lrc|txt)$/i.test(f.name);

    const audio = list.filter(isAudio);
    const images = list.filter(isImage);
    const lyrics = list.filter(isLyric);

    // a "cover.jpg" / "folder.jpg" style image applies to the whole drop
    const shared = images.find((f) => /(^|\/)(cover|folder|front|album|artwork)\.[a-z]+$/i.test(f.name)) || null;

    const results = { added: 0, failed: [], total: audio.length };
    for (let i = 0; i < audio.length; i++) {
      const f = audio[i];
      if (onProgress) onProgress(i, audio.length, f.name);
      try {
        const base = stripExt(baseName(f.name)).toLowerCase();
        const art = images.find((im) => stripExt(baseName(im.name)).toLowerCase() === base) || shared;
        const lyricFile = lyrics.find((l) => stripExt(baseName(l.name)).toLowerCase() === base);
        const lyricsText = lyricFile ? await lyricFile.text() : null;
        await addTrack({ title: null, artist: null, audioFile: f, artFile: art || null, lyricsText });
        results.added++;
      } catch (e) {
        results.failed.push({ name: f.name, error: e && e.message ? e.message : String(e) });
      }
    }
    if (onProgress) onProgress(audio.length, audio.length, null);
    return results;
  }

  function baseName(path) {
    const parts = String(path).split(/[\\/]/);
    return parts[parts.length - 1];
  }

  function fileToDataUrl(file) {
    return new Promise((resolve, reject) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.onerror = reject;
      r.readAsDataURL(file);
    });
  }

  async function paletteFromDataUrl(dataUrl) {
    const img = await loadImg(dataUrl);
    return ColorExtract.fromImage(img);
  }
  function loadImg(src) {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = "anonymous";
      img.onload = () => resolve(img);
      img.onerror = reject;
      img.src = src;
    });
  }

  function hashHue(str) {
    let h = 0;
    for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
    return h % 360;
  }

  function paletteFromHue(hue) {
    const hsl = (h, s, l) => `hsl(${h},${s}%,${l}%)`;
    return {
      primary: hsl(hue, 70, 60),
      secondary: hsl((hue + 40) % 360, 75, 55),
      accent: hsl((hue + 190) % 360, 80, 65),
    };
  }

  // Generates a small abstract gradient "cover" canvas as a data URL so
  // imported songs without embedded artwork still get a distinct tile,
  // staying within Flow's own palette family (see paletteFromHue above).
  function generateCoverDataUrl(title, artist, hue) {
    const size = 300;
    const canvas = document.createElement("canvas");
    canvas.width = size; canvas.height = size;
    const ctx = canvas.getContext("2d");
    const grad = ctx.createLinearGradient(0, 0, size, size);
    grad.addColorStop(0, `hsl(${hue},70%,55%)`);
    grad.addColorStop(1, `hsl(${(hue + 150) % 360},70%,45%)`);
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, size, size);

    // Soft concentric rings for texture, seeded by title+artist so the
    // same song always renders the same generated cover.
    let seed = hashHue(title + "|" + artist) + 1;
    const rand = () => { seed = (seed * 1103515245 + 12345) & 0x7fffffff; return (seed / 0x7fffffff); };
    ctx.globalAlpha = 0.25;
    for (let i = 0; i < 5; i++) {
      ctx.beginPath();
      ctx.arc(rand() * size, rand() * size, 30 + rand() * 90, 0, Math.PI * 2);
      ctx.fillStyle = `hsl(${(hue + 90 + i * 30) % 360},80%,70%)`;
      ctx.fill();
    }
    ctx.globalAlpha = 1;
    ctx.fillStyle = "rgba(255,255,255,0.92)";
    ctx.font = "bold 42px sans-serif";
    ctx.fillText((title || "?").slice(0, 1).toUpperCase(), size / 2 - 14, size / 2 + 14);
    return canvas.toDataURL("image/png");
  }

  return { addTrack, addMany, updateTrack, getAll, remove, search };
})();

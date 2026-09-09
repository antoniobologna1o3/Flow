/* ============================================================
   Audio metadata reader — title, artist, album and embedded
   cover art, straight out of the file. No dependencies, no
   network: we just walk the container's own tag structures.

   Supported: MP3 (ID3v2.2/2.3/2.4), M4A/MP4 (iTunes atoms),
   FLAC (Vorbis comment + PICTURE block), OGG/Opus (Vorbis
   comment incl. base64 METADATA_BLOCK_PICTURE), WAV (RIFF INFO).
   ============================================================ */
const Tags = (() => {

  async function read(file) {
    // Tags live at the head of the file (and, for MP4, sometimes the
    // tail), so read a bounded slice rather than the whole track.
    const HEAD = 3 * 1024 * 1024;
    const headBuf = new Uint8Array(await file.slice(0, Math.min(HEAD, file.size)).arrayBuffer());
    const out = { title: null, artist: null, album: null, picture: null };

    try {
      if (has(headBuf, 0, "ID3")) return finish(readID3(headBuf), file);
      if (has(headBuf, 0, "fLaC")) return finish(readFLAC(headBuf), file);
      if (has(headBuf, 0, "OggS")) return finish(readOgg(headBuf), file);
      if (has(headBuf, 0, "RIFF")) return finish(readRIFF(headBuf), file);
      if (has(headBuf, 4, "ftyp")) return finish(await readMP4(file), file);
    } catch (e) {
      // A malformed tag must never stop a song from being importable.
      console.warn("Tag read failed:", e);
    }
    return finish(out, file);
  }

  function finish(tags, file) {
    const t = tags || {};
    return {
      title: clean(t.title),
      artist: clean(t.artist),
      album: clean(t.album),
      picture: t.picture || null,   // { blob, mime }
    };
  }

  function clean(s) {
    if (!s) return null;
    // Tag strings are routinely NUL-terminated or NUL-padded, and UTF-16
    // text can carry a byte-order mark. Strip both, explicitly.
    const v = String(s).replace(/\u0000+/g, "").replace(/^\uFEFF/, "").trim();
    return v.length ? v : null;
  }

  /* ---------- helpers ---------- */
  function has(buf, off, str) {
    for (let i = 0; i < str.length; i++) if (buf[off + i] !== str.charCodeAt(i)) return false;
    return true;
  }
  function be32(b, o) { return (b[o] << 24 | b[o + 1] << 16 | b[o + 2] << 8 | b[o + 3]) >>> 0; }
  function le32(b, o) { return (b[o] | b[o + 1] << 8 | b[o + 2] << 16 | b[o + 3] << 24) >>> 0; }
  function be24(b, o) { return (b[o] << 16 | b[o + 1] << 8 | b[o + 2]) >>> 0; }
  function synch(b, o) { return ((b[o] & 0x7f) << 21) | ((b[o + 1] & 0x7f) << 14) | ((b[o + 2] & 0x7f) << 7) | (b[o + 3] & 0x7f); }

  const utf8 = new TextDecoder("utf-8");
  const latin1 = new TextDecoder("iso-8859-1");
  function decodeText(bytes, encoding) {
    try {
      switch (encoding) {
        case 0: return latin1.decode(bytes);
        case 1: return new TextDecoder("utf-16").decode(bytes);
        case 2: return new TextDecoder("utf-16be").decode(bytes);
        default: return utf8.decode(bytes);
      }
    } catch (e) { return latin1.decode(bytes); }
  }

  function pic(bytes, mime) {
    if (!bytes || !bytes.length) return null;
    return { blob: new Blob([bytes], { type: mime || "image/jpeg" }), mime: mime || "image/jpeg" };
  }

  /* ---------- ID3v2 (MP3) ---------- */
  function readID3(b) {
    const out = { title: null, artist: null, album: null, picture: null };
    const major = b[3];
    const flags = b[5];
    const size = synch(b, 6);
    let p = 10;
    if (flags & 0x40) p += synch(b, p) ;           // skip extended header
    const end = Math.min(b.length, 10 + size);

    const idLen = major === 2 ? 3 : 4;
    while (p + idLen + (major === 2 ? 3 : 6) <= end) {
      const id = latin1.decode(b.subarray(p, p + idLen));
      if (!/^[A-Z0-9]{3,4}$/.test(id)) break;      // padding / garbage

      let fsize, headerLen;
      if (major === 2) { fsize = be24(b, p + 3); headerLen = 6; }
      else if (major === 4) { fsize = synch(b, p + 4); headerLen = 10; }
      else { fsize = be32(b, p + 4); headerLen = 10; }

      const dstart = p + headerLen;
      const dend = Math.min(dstart + fsize, end);
      if (fsize <= 0 || dend <= dstart) break;
      const data = b.subarray(dstart, dend);

      if (id === "TIT2" || id === "TT2") out.title = textFrame(data);
      else if (id === "TPE1" || id === "TP1") out.artist = textFrame(data);
      else if (id === "TALB" || id === "TAL") out.album = textFrame(data);
      else if ((id === "APIC" || id === "PIC") && !out.picture) out.picture = apicFrame(data, id === "PIC");

      p = dend;
    }
    return out;
  }

  function textFrame(d) {
    if (!d.length) return null;
    return decodeText(d.subarray(1), d[0]);
  }

  function apicFrame(d, isV22) {
    let o = 0;
    const enc = d[o++];
    let mime;
    if (isV22) { mime = "image/" + latin1.decode(d.subarray(o, o + 3)).toLowerCase(); o += 3; }
    else {
      const start = o;
      while (o < d.length && d[o] !== 0) o++;
      mime = latin1.decode(d.subarray(start, o)) || "image/jpeg";
      o++;
    }
    o++;  // picture type byte
    // description, terminated by 1 or 2 nulls depending on encoding
    if (enc === 1 || enc === 2) {
      while (o + 1 < d.length && !(d[o] === 0 && d[o + 1] === 0)) o += 2;
      o += 2;
    } else {
      while (o < d.length && d[o] !== 0) o++;
      o++;
    }
    if (mime === "image/jpg") mime = "image/jpeg";
    return pic(d.subarray(o), mime);
  }

  /* ---------- FLAC ---------- */
  function readFLAC(b) {
    const out = { title: null, artist: null, album: null, picture: null };
    let p = 4;
    while (p + 4 <= b.length) {
      const header = b[p];
      const last = (header & 0x80) !== 0;
      const type = header & 0x7f;
      const len = be24(b, p + 1);
      const start = p + 4;
      const end = Math.min(start + len, b.length);
      if (type === 4) applyVorbis(parseVorbisComment(b.subarray(start, end)), out);
      else if (type === 6 && !out.picture) out.picture = parseFlacPicture(b.subarray(start, end));
      if (last) break;
      p = start + len;
      if (len <= 0) break;
    }
    return out;
  }

  function parseFlacPicture(d) {
    let o = 4;                                  // picture type
    const mimeLen = be32(d, o); o += 4;
    const mime = latin1.decode(d.subarray(o, o + mimeLen)); o += mimeLen;
    const descLen = be32(d, o); o += 4; o += descLen;
    o += 16;                                    // w,h,depth,colors
    const dataLen = be32(d, o); o += 4;
    return pic(d.subarray(o, o + dataLen), mime);
  }

  function parseVorbisComment(d) {
    const fields = {};
    let o = 0;
    const vlen = le32(d, o); o += 4 + vlen;
    const count = le32(d, o); o += 4;
    for (let i = 0; i < count && o + 4 <= d.length; i++) {
      const len = le32(d, o); o += 4;
      const s = utf8.decode(d.subarray(o, o + len)); o += len;
      const eq = s.indexOf("=");
      if (eq > 0) fields[s.slice(0, eq).toUpperCase()] = s.slice(eq + 1);
    }
    return fields;
  }

  function applyVorbis(f, out) {
    out.title = out.title || f.TITLE || null;
    out.artist = out.artist || f.ARTIST || f.ALBUMARTIST || null;
    out.album = out.album || f.ALBUM || null;
    if (!out.picture && f.METADATA_BLOCK_PICTURE) {
      try {
        const bin = atob(f.METADATA_BLOCK_PICTURE);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        out.picture = parseFlacPicture(bytes);
      } catch (e) {}
    }
  }

  /* ---------- OGG / Opus ---------- */
  function readOgg(b) {
    const out = { title: null, artist: null, album: null, picture: null };
    // Walk Ogg pages and concatenate payloads until we've seen the
    // comment header, which is small and always near the start.
    let p = 0;
    const chunks = [];
    let total = 0;
    while (p + 27 <= b.length && has(b, p, "OggS") && total < 1_500_000) {
      const segCount = b[p + 26];
      const segTable = p + 27;
      let payloadLen = 0;
      for (let i = 0; i < segCount; i++) payloadLen += b[segTable + i];
      const payload = b.subarray(segTable + segCount, segTable + segCount + payloadLen);
      chunks.push(payload);
      total += payloadLen;
      p = segTable + segCount + payloadLen;
      if (chunks.length > 12) break;
    }
    const joined = new Uint8Array(total);
    let o = 0;
    for (const c of chunks) { joined.set(c, o); o += c.length; }

    for (let i = 0; i < joined.length - 8; i++) {
      if (joined[i] === 3 && has(joined, i + 1, "vorbis")) {
        applyVorbis(parseVorbisComment(joined.subarray(i + 7)), out); return out;
      }
      if (has(joined, i, "OpusTags")) {
        applyVorbis(parseVorbisComment(joined.subarray(i + 8)), out); return out;
      }
    }
    return out;
  }

  /* ---------- WAV (RIFF INFO) ---------- */
  function readRIFF(b) {
    const out = { title: null, artist: null, album: null, picture: null };
    let p = 12;
    while (p + 8 <= b.length) {
      const id = latin1.decode(b.subarray(p, p + 4));
      const size = le32(b, p + 4);
      if (id === "LIST" && has(b, p + 8, "INFO")) {
        let q = p + 12;
        const end = Math.min(p + 8 + size, b.length);
        while (q + 8 <= end) {
          const sid = latin1.decode(b.subarray(q, q + 4));
          const ssize = le32(b, q + 4);
          const val = latin1.decode(b.subarray(q + 8, q + 8 + ssize));
          if (sid === "INAM") out.title = val;
          else if (sid === "IART") out.artist = val;
          else if (sid === "IPRD") out.album = val;
          q += 8 + ssize + (ssize % 2);
        }
        break;
      }
      p += 8 + size + (size % 2);
      if (size <= 0) break;
    }
    return out;
  }

  /* ---------- MP4 / M4A ---------- */
  async function readMP4(file) {
    const out = { title: null, artist: null, album: null, picture: null };
    // moov can sit at the end of the file, so walk top-level atoms
    // with real reads instead of assuming it's in the head slice.
    const size = file.size;
    let pos = 0;
    while (pos < size - 8) {
      const head = new Uint8Array(await file.slice(pos, pos + 8).arrayBuffer());
      if (head.length < 8) break;
      let asize = be32(head, 0);
      const type = latin1.decode(head.subarray(4, 8));
      if (asize === 1) {
        const ext = new Uint8Array(await file.slice(pos + 8, pos + 16).arrayBuffer());
        asize = be32(ext, 4);                    // ignore the high word
        if (!asize) break;
      }
      if (asize < 8) break;
      if (type === "moov") {
        const moov = new Uint8Array(await file.slice(pos, pos + Math.min(asize, 8 * 1024 * 1024)).arrayBuffer());
        walkAtoms(moov, 8, moov.length, out);
        break;
      }
      pos += asize;
    }
    return out;
  }

  function walkAtoms(b, start, end, out) {
    let p = start;
    while (p + 8 <= end) {
      const asize = be32(b, p);
      const type = latin1.decode(b.subarray(p + 4, p + 8));
      if (asize < 8) break;
      const inner = Math.min(p + asize, end);

      if (type === "udta" || type === "ilst" || type === "trak") walkAtoms(b, p + 8, inner, out);
      else if (type === "meta") walkAtoms(b, p + 12, inner, out);   // meta has a version/flags word
      else if (type === "\xa9nam" || type === "\xa9ART" || type === "\xa9alb" || type === "aART" || type === "covr") {
        const val = mp4Data(b, p + 8, inner);
        if (!val) { p += asize; continue; }
        if (type === "covr") {
          if (!out.picture) out.picture = pic(val.bytes, val.flags === 14 ? "image/png" : "image/jpeg");
        } else {
          const text = utf8.decode(val.bytes);
          if (type === "\xa9nam") out.title = out.title || text;
          else if (type === "\xa9alb") out.album = out.album || text;
          else out.artist = out.artist || text;
        }
      }
      p += asize;
    }
  }

  function mp4Data(b, start, end) {
    let p = start;
    while (p + 8 <= end) {
      const asize = be32(b, p);
      const type = latin1.decode(b.subarray(p + 4, p + 8));
      if (asize < 8) return null;
      if (type === "data") {
        const flags = be32(b, p + 8) & 0x00ffffff;
        return { bytes: b.subarray(p + 16, Math.min(p + asize, end)), flags };
      }
      p += asize;
    }
    return null;
  }

  return { read };
})();

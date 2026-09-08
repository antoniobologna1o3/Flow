/* Extracts a small palette (primary / secondary / accent) from an album-art
   image, purely client-side via canvas pixel sampling — no external API. */
const ColorExtract = (() => {
  function fromImage(imgEl) {
    return new Promise((resolve) => {
      try {
        const size = 48; // downsample for speed
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext("2d", { willReadFrequently: true });
        ctx.drawImage(imgEl, 0, 0, size, size);
        const { data } = ctx.getImageData(0, 0, size, size);

        // Bucket colors by quantized hue/lightness, weighted by saturation
        // so vivid album-cover colors win over flat backgrounds.
        const buckets = new Map();
        for (let i = 0; i < data.length; i += 4) {
          const r = data[i], g = data[i + 1], b = data[i + 2], a = data[i + 3];
          if (a < 128) continue;
          const [h, s, l] = rgbToHsl(r, g, b);
          if (l < 0.06 || l > 0.95) continue; // ignore near-black/white
          const key = `${Math.round(h / 12)}_${Math.round(l * 4)}`;
          const weight = 0.15 + s; // saturated colors matter more
          if (!buckets.has(key)) buckets.set(key, { r: 0, g: 0, b: 0, w: 0 });
          const bucket = buckets.get(key);
          bucket.r += r * weight;
          bucket.g += g * weight;
          bucket.b += b * weight;
          bucket.w += weight;
        }

        const sorted = [...buckets.values()]
          .map((b) => ({ r: b.r / b.w, g: b.g / b.w, b: b.b / b.w, w: b.w }))
          .sort((a, b) => b.w - a.w);

        const palette = sorted.slice(0, 3);
        while (palette.length < 3) {
          palette.push(palette[palette.length - 1] || { r: 124, g: 92, b: 255 });
        }

        resolve(separate([
          rgbToHex(palette[0]),
          rgbToHex(palette[1]),
          rgbToHex(palette[2]),
        ]));
      } catch (err) {
        // Cross-origin / decode failure -> fall back to default Flow palette
        resolve({ primary: "#7c5cff", secondary: "#ff5ca8", accent: "#5cf0ff" });
      }
    });
  }

  function rgbToHsl(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h, s, l = (max + min) / 2;
    if (max === min) { h = s = 0; }
    else {
      const d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      switch (max) {
        case r: h = (g - b) / d + (g < b ? 6 : 0); break;
        case g: h = (b - r) / d + 2; break;
        default: h = (r - g) / d + 4;
      }
      h *= 60;
    }
    return [h, s, l];
  }

  function rgbToHex({ r, g, b }) {
    const c = (v) => Math.max(0, Math.min(255, Math.round(v))).toString(16).padStart(2, "0");
    return `#${c(r)}${c(g)}${c(b)}`;
  }

  // Lanes are told apart by colour, so a flat single-colour cover (very
  // common on minimalist artwork) must not collapse all three slots into
  // the same value. Keep the cover's dominant hue as primary, then fan the
  // other two out far enough to stay readable at speed.
  function separate(hexes) {
    const hsl = hexes.map(hexToHsl);
    const MIN_HUE = 42;                    // degrees
    for (let i = 1; i < hsl.length; i++) {
      for (let k = 0; k < i; k++) {
        let d = Math.abs(hsl[i][0] - hsl[k][0]);
        if (d > 180) d = 360 - d;
        if (d < MIN_HUE) {
          hsl[i][0] = (hsl[k][0] + MIN_HUE * (i === 1 ? 1 : 2) + 360) % 360;
        }
      }
      // and keep them all bright enough to read against a dark track
      hsl[i][1] = Math.max(0.5, hsl[i][1]);
      hsl[i][2] = Math.min(0.72, Math.max(0.45, hsl[i][2]));
    }
    hsl[0][1] = Math.max(0.42, hsl[0][1]);
    hsl[0][2] = Math.min(0.72, Math.max(0.45, hsl[0][2]));
    return {
      primary: hslToHex(hsl[0]),
      secondary: hslToHex(hsl[1]),
      accent: hslToHex(hsl[2]),
    };
  }

  function hexToHsl(hex) {
    const n = parseInt(hex.slice(1), 16);
    const [h, s, l] = rgbToHsl((n >> 16) & 255, (n >> 8) & 255, n & 255);
    return [h, s, l];
  }

  function hslToHex([h, s, l]) {
    h = ((h % 360) + 360) % 360;
    const c = (1 - Math.abs(2 * l - 1)) * s;
    const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
    const m = l - c / 2;
    let r = 0, g = 0, b = 0;
    if (h < 60) [r, g, b] = [c, x, 0];
    else if (h < 120) [r, g, b] = [x, c, 0];
    else if (h < 180) [r, g, b] = [0, c, x];
    else if (h < 240) [r, g, b] = [0, x, c];
    else if (h < 300) [r, g, b] = [x, 0, c];
    else [r, g, b] = [c, 0, x];
    return rgbToHex({ r: (r + m) * 255, g: (g + m) * 255, b: (b + m) * 255 });
  }

  function applyPalette(palette) {
    const root = document.documentElement.style;
    root.setProperty("--game-primary", palette.primary);
    root.setProperty("--game-secondary", palette.secondary);
    root.setProperty("--game-accent", palette.accent);
  }

  return { fromImage, applyPalette };
})();

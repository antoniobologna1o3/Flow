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

        resolve({
          primary: rgbToHex(palette[0]),
          secondary: rgbToHex(palette[1]),
          accent: rgbToHex(palette[2]),
        });
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

  function applyPalette(palette) {
    const root = document.documentElement.style;
    root.setProperty("--game-primary", palette.primary);
    root.setProperty("--game-secondary", palette.secondary);
    root.setProperty("--game-accent", palette.accent);
  }

  return { fromImage, applyPalette };
})();

/* ============================================================
   VFX.
   - Ambient(): the menu particle field. Three depth layers (dim
     motes, mid embers, a few blurred foreground ones) so the
     background reads dimensional rather than like uniform noise.
   - Pools: pre-allocated Three.js meshes for hit sparks and
     shockwaves, so gameplay never allocates mid-run — the single
     biggest cause of stutter on weak laptops.
   ============================================================ */
const VFX = (() => {

  /* ---------------- ambient menu particles ---------------- */
  const Ambient = (() => {
    let canvas, ctx, raf = null, parts = [], w = 0, h = 0, quality = "high";
    let hueSource = () => ({ p1: "#7c5cff", p2: "#ff5ca8", p3: "#5cf0ff" });

    const LAYERS = [
      { count: 34, size: [0.5, 1.2], speed: [2, 6],  alpha: [.06, .18], blur: 0 },
      { count: 18, size: [1.2, 2.4], speed: [6, 14], alpha: [.18, .42], blur: 0 },
      { count: 5,  size: [3.5, 7],   speed: [14, 26],alpha: [.16, .3],  blur: 2.5 },
    ];

    function start(canvasEl, paletteFn) {
      canvas = canvasEl;
      ctx = canvas.getContext("2d");
      if (paletteFn) hueSource = paletteFn;
      resize();
      window.addEventListener("resize", resize);
      build();
      if (!raf) loop();
    }

    function setQuality(q) { quality = q; build(); }

    function resize() {
      if (!canvas) return;
      const dpr = quality === "low" ? 1 : Math.min(1.5, window.devicePixelRatio || 1);
      w = canvas.clientWidth; h = canvas.clientHeight;
      canvas.width = Math.max(1, Math.floor(w * dpr));
      canvas.height = Math.max(1, Math.floor(h * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function build() {
      parts = [];
      const scale = quality === "low" ? 0.35 : quality === "medium" ? 0.7 : 1;
      LAYERS.forEach((L, li) => {
        const n = Math.max(3, Math.round(L.count * scale));
        for (let i = 0; i < n; i++) {
          parts.push({
            layer: li,
            x: Math.random() * w,
            y: Math.random() * h,
            r: rand(L.size[0], L.size[1]),
            vy: -rand(L.speed[0], L.speed[1]),
            vx: rand(-4, 4),
            a: rand(L.alpha[0], L.alpha[1]),
            phase: Math.random() * Math.PI * 2,
            flick: rand(0.4, 1.6),
            blur: L.blur,
            tint: Math.random(),
          });
        }
      });
    }

    function rand(a, b) { return a + Math.random() * (b - a); }

    let last = performance.now();
    function loop() {
      raf = requestAnimationFrame(loop);
      const now = performance.now();
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      if (!ctx || !w) return;

      ctx.clearRect(0, 0, w, h);
      const pal = hueSource();
      const colors = [pal.p1, pal.p2, pal.p3];

      for (const p of parts) {
        p.y += p.vy * dt;
        p.x += p.vx * dt + Math.sin(now / 2200 + p.phase) * 6 * dt;
        if (p.y < -12) { p.y = h + 12; p.x = Math.random() * w; }
        if (p.x < -12) p.x = w + 12;
        if (p.x > w + 12) p.x = -12;

        const flick = 0.75 + 0.25 * Math.sin(now / 420 * p.flick + p.phase);
        ctx.globalAlpha = p.a * flick;
        ctx.fillStyle = colors[Math.floor(p.tint * colors.length)] || colors[0];
        ctx.filter = p.blur ? `blur(${p.blur}px)` : "none";
        ctx.beginPath();
        ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.filter = "none";
      ctx.globalAlpha = 1;
    }

    function stop() { if (raf) cancelAnimationFrame(raf); raf = null; }

    return { start, stop, setQuality, resize };
  })();

  /* ---------------- generated textures ---------------- */
  // Drawn once into a canvas rather than shipped as image files, so the
  // single-file build stays self-contained. White-based so a material's
  // colour tints them per lane.
  const Textures = (() => {
    let tile = null, glow = null, grid = null;

    function tileTexture(THREE) {
      if (tile) return tile;
      const S = 128, c = document.createElement("canvas");
      c.width = c.height = S;
      const x = c.getContext("2d");

      // body: vertical gradient gives the slab a lit top and shaded base
      const g = x.createLinearGradient(0, 0, 0, S);
      g.addColorStop(0, "#ffffff");
      g.addColorStop(0.18, "#e8e8ff");
      g.addColorStop(0.55, "#9d9dc8");
      g.addColorStop(1, "#5a5a86");
      roundRect(x, 4, 4, S - 8, S - 8, 18);
      x.fillStyle = g; x.fill();

      // top bevel highlight
      x.globalCompositeOperation = "lighter";
      const hl = x.createLinearGradient(0, 4, 0, S * 0.34);
      hl.addColorStop(0, "rgba(255,255,255,.85)");
      hl.addColorStop(1, "rgba(255,255,255,0)");
      roundRect(x, 8, 6, S - 16, S * 0.32, 14);
      x.fillStyle = hl; x.fill();

      // diagonal sheen
      const sh = x.createLinearGradient(0, S, S, 0);
      sh.addColorStop(0.36, "rgba(255,255,255,0)");
      sh.addColorStop(0.5, "rgba(255,255,255,.3)");
      sh.addColorStop(0.64, "rgba(255,255,255,0)");
      x.fillStyle = sh; x.fillRect(0, 0, S, S);
      x.globalCompositeOperation = "source-over";

      // inner rim
      roundRect(x, 5, 5, S - 10, S - 10, 17);
      x.strokeStyle = "rgba(255,255,255,.55)"; x.lineWidth = 2; x.stroke();

      // fine grain so large tiles do not read as flat plastic
      const img = x.getImageData(0, 0, S, S), d = img.data;
      for (let i = 0; i < d.length; i += 4) {
        const n = (Math.random() - 0.5) * 14;
        d[i] += n; d[i + 1] += n; d[i + 2] += n;
      }
      x.putImageData(img, 0, 0);

      tile = new THREE.CanvasTexture(c);
      tile.anisotropy = 1;
      return tile;
    }

    function glowTexture(THREE) {
      if (glow) return glow;
      const S = 64, c = document.createElement("canvas");
      c.width = c.height = S;
      const x = c.getContext("2d");
      const g = x.createRadialGradient(S / 2, S / 2, 0, S / 2, S / 2, S / 2);
      g.addColorStop(0, "rgba(255,255,255,.9)");
      g.addColorStop(0.4, "rgba(255,255,255,.35)");
      g.addColorStop(1, "rgba(255,255,255,0)");
      x.fillStyle = g; x.fillRect(0, 0, S, S);
      glow = new THREE.CanvasTexture(c);
      return glow;
    }

    // One scrolling texture replaces what used to be 26 separate meshes.
    function gridTexture(THREE) {
      if (grid) return grid;
      const W = 8, H = 128, c = document.createElement("canvas");
      c.width = W; c.height = H;
      const x = c.getContext("2d");
      x.clearRect(0, 0, W, H);
      x.fillStyle = "rgba(255,255,255,.85)";
      x.fillRect(0, 0, W, 3);
      grid = new THREE.CanvasTexture(c);
      grid.wrapS = grid.wrapT = THREE.RepeatWrapping;
      return grid;
    }

    function roundRect(x, px, py, w, h, r) {
      x.beginPath();
      x.moveTo(px + r, py);
      x.arcTo(px + w, py, px + w, py + h, r);
      x.arcTo(px + w, py + h, px, py + h, r);
      x.arcTo(px, py + h, px, py, r);
      x.arcTo(px, py, px + w, py, r);
      x.closePath();
    }

    return { tileTexture, glowTexture, gridTexture };
  })();

  /* ---------------- pooled 3D gameplay effects ---------------- */
  function createPools(scene, THREE) {
    const MAX_SPARKS = 220;
    const MAX_RINGS = 14;

    // Sparks are one InstancedMesh instead of 220 separate meshes — the
    // single biggest draw-call saving in the whole scene.
    const sparkGeo = new THREE.PlaneGeometry(0.1, 0.1);
    const sparkMat = new THREE.MeshBasicMaterial({
      map: Textures.glowTexture(THREE), transparent: true, depthWrite: false,
      blending: THREE.AdditiveBlending, vertexColors: true,
    });
    const sparkMesh = new THREE.InstancedMesh(sparkGeo, sparkMat, MAX_SPARKS);
    sparkMesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
    sparkMesh.frustumCulled = false;
    sparkMesh.count = MAX_SPARKS;
    const colorAttr = new THREE.InstancedBufferAttribute(new Float32Array(MAX_SPARKS * 3), 3);
    sparkGeo.setAttribute("color", colorAttr);
    scene.add(sparkMesh);

    const sparks = [];
    for (let i = 0; i < MAX_SPARKS; i++) {
      sparks.push({ life: 0, max: 1, alive: false, pos: new THREE.Vector3(), vel: new THREE.Vector3(), spin: 0, rot: 0, scale: 1 });
    }

    const ringGeo = new THREE.RingGeometry(0.28, 0.36, 20);
    const rings = [];
    for (let i = 0; i < MAX_RINGS; i++) {
      const m = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({
        color: 0xffffff, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false,
        blending: THREE.AdditiveBlending,
      }));
      m.rotation.x = -Math.PI / 2;
      m.visible = false;
      scene.add(m);
      rings.push({ mesh: m, life: 0, max: 1, power: 1 });
    }

    let sparkCursor = 0, ringCursor = 0, budget = 1;
    const _m = new THREE.Matrix4();
    const _q = new THREE.Quaternion();
    const _s = new THREE.Vector3();
    const _hidden = new THREE.Matrix4().makeScale(0, 0, 0);
    const _c = new THREE.Color();

    function setBudget(b) { budget = b; }

    function burst(x, y, z, color, count = 14, power = 1) {
      const n = Math.max(2, Math.round(count * budget));
      _c.setHex(color);
      for (let i = 0; i < n; i++) {
        const idx = sparkCursor = (sparkCursor + 1) % MAX_SPARKS;
        const p = sparks[idx];
        p.alive = true;
        p.pos.set(x, y, z);
        const ang = Math.random() * Math.PI * 2;
        p.vel.set(
          Math.cos(ang) * (0.9 + Math.random() * 2.2) * power,
          1.6 + Math.random() * 3.2 * power,
          Math.sin(ang) * (0.7 + Math.random() * 1.6)
        );
        p.spin = (Math.random() - 0.5) * 12;
        p.rot = 0;
        p.life = 0;
        p.max = 0.4 + Math.random() * 0.42;
        colorAttr.setXYZ(idx, _c.r, _c.g, _c.b);
      }
      colorAttr.needsUpdate = true;
    }

    function shockwave(x, z, color, power = 1) {
      const r = rings[ringCursor = (ringCursor + 1) % MAX_RINGS];
      r.mesh.visible = true;
      r.mesh.position.set(x, 0.06, z);
      r.mesh.material.color.setHex(color);
      r.mesh.material.opacity = 0.85;
      r.mesh.scale.setScalar(0.4);
      r.life = 0;
      r.max = 0.5 + 0.2 * power;
      r.power = power;
    }

    function update(dt, camera) {
      if (camera) _q.copy(camera.quaternion);
      let any = false;
      for (let i = 0; i < MAX_SPARKS; i++) {
        const p = sparks[i];
        if (!p.alive) { sparkMesh.setMatrixAt(i, _hidden); continue; }
        p.life += dt;
        if (p.life >= p.max) {
          p.alive = false;
          sparkMesh.setMatrixAt(i, _hidden);
          any = true;
          continue;
        }
        const k = p.life / p.max;
        p.pos.addScaledVector(p.vel, dt);
        p.vel.y -= 9 * dt;
        p.rot += p.spin * dt;
        // fade by shrinking, since one shared material can't hold per-instance alpha
        const sc = (1 - k) * (1 - k) * 1.4;
        _s.set(sc, sc, sc);
        _m.compose(p.pos, _q, _s);
        sparkMesh.setMatrixAt(i, _m);
        any = true;
      }
      if (any) sparkMesh.instanceMatrix.needsUpdate = true;

      for (const r of rings) {
        if (!r.mesh.visible) continue;
        r.life += dt;
        if (r.life >= r.max) { r.mesh.visible = false; continue; }
        const k = r.life / r.max;
        r.mesh.scale.setScalar(0.4 + k * 4.2 * (r.power || 1));
        r.mesh.material.opacity = 0.85 * (1 - k);
      }
    }

    function clear() {
      for (let i = 0; i < MAX_SPARKS; i++) {
        sparks[i].alive = false;
        sparkMesh.setMatrixAt(i, _hidden);
      }
      sparkMesh.instanceMatrix.needsUpdate = true;
      for (const r of rings) r.mesh.visible = false;
    }

    clear();
    return { burst, shockwave, update, clear, setBudget };
  }

  return { Ambient, Textures, createPools };
})();

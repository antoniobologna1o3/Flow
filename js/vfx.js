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

  /* ---------------- pooled 3D gameplay effects ---------------- */
  function createPools(scene, THREE) {
    const sparkGeo = new THREE.PlaneGeometry(0.09, 0.09);
    const ringGeo = new THREE.RingGeometry(0.28, 0.36, 22);
    const MAX_SPARKS = 220;
    const MAX_RINGS = 14;

    const sparks = [];
    for (let i = 0; i < MAX_SPARKS; i++) {
      const m = new THREE.Mesh(sparkGeo, new THREE.MeshBasicMaterial({
        color: 0xffffff, transparent: true, opacity: 0, depthWrite: false,
      }));
      m.visible = false;
      scene.add(m);
      sparks.push({ mesh: m, life: 0, max: 1, vel: new THREE.Vector3(), spin: 0 });
    }

    const rings = [];
    for (let i = 0; i < MAX_RINGS; i++) {
      const m = new THREE.Mesh(ringGeo, new THREE.MeshBasicMaterial({
        color: 0xffffff, transparent: true, opacity: 0, side: THREE.DoubleSide, depthWrite: false,
      }));
      m.rotation.x = -Math.PI / 2;
      m.visible = false;
      scene.add(m);
      rings.push({ mesh: m, life: 0, max: 1 });
    }

    let sparkCursor = 0, ringCursor = 0;
    let budget = 1; // scaled by quality tier

    function setBudget(b) { budget = b; }

    function burst(x, y, z, color, count = 14, power = 1) {
      const n = Math.max(2, Math.round(count * budget));
      for (let i = 0; i < n; i++) {
        const p = sparks[sparkCursor = (sparkCursor + 1) % MAX_SPARKS];
        p.mesh.visible = true;
        p.mesh.position.set(x, y, z);
        p.mesh.material.color.setHex(color);
        p.mesh.material.opacity = 1;
        p.mesh.scale.setScalar(1);
        const ang = Math.random() * Math.PI * 2;
        const up = 1.6 + Math.random() * 3.2 * power;
        p.vel.set(Math.cos(ang) * (0.9 + Math.random() * 2.2) * power, up, Math.sin(ang) * (0.7 + Math.random() * 1.6));
        p.spin = (Math.random() - 0.5) * 12;
        p.life = 0;
        p.max = 0.4 + Math.random() * 0.42;
      }
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
      for (const p of sparks) {
        if (!p.mesh.visible) continue;
        p.life += dt;
        if (p.life >= p.max) { p.mesh.visible = false; continue; }
        const k = p.life / p.max;
        p.mesh.position.addScaledVector(p.vel, dt);
        p.vel.y -= 9 * dt;
        p.mesh.material.opacity = (1 - k) * (1 - k);
        p.mesh.scale.setScalar(1 - k * 0.55);
        p.mesh.rotation.z += p.spin * dt;
        if (camera) p.mesh.quaternion.copy(camera.quaternion);
      }
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
      for (const p of sparks) p.mesh.visible = false;
      for (const r of rings) r.mesh.visible = false;
    }

    return { burst, shockwave, update, clear, setBudget };
  }

  return { Ambient, createPools };
})();

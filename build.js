#!/usr/bin/env node
/* Bundles the whole game into one self-contained FLOW.html.
   No sibling css/ or js/ folders needed — which is what makes it work
   when you just download a single file and double-click it (Chromebook,
   phone downloads folder, email attachment, USB stick).

   Usage: node build.js
*/
const fs = require("fs");
const path = require("path");

const ROOT = __dirname;
const SRC = path.join(ROOT, "index.html");
const OUT = path.join(ROOT, "FLOW.html");

let html = fs.readFileSync(SRC, "utf8");

// --- inline the stylesheet ---
html = html.replace(/<link rel="stylesheet" href="([^"]+)"\s*\/?>/g, (m, href) => {
  const css = fs.readFileSync(path.join(ROOT, href), "utf8");
  return `<style>\n${css}\n</style>`;
});

// --- inline every script, in order ---
html = html.replace(/<script src="([^"]+)"><\/script>/g, (m, src) => {
  const js = fs.readFileSync(path.join(ROOT, src), "utf8");
  // A source file containing "</script>" inside a string would close the
  // tag early; escape it defensively.
  const safe = js.replace(/<\/script>/gi, "<\\/script>");
  return `<script>\n/* ===== ${src} ===== */\n${safe}\n</script>`;
});

if (/<script src=|<link rel="stylesheet"/.test(html)) {
  console.error("Bundle still references external files — aborting.");
  process.exit(1);
}

// A single-file build has no sibling README, so state what it is up front.
html = html.replace(
  "<body>",
  `<body>
<!-- FLOW — single-file build. Everything (styles, game code, Three.js) is
     inlined below, so this one file is the whole game. Works offline and
     from a plain file:// double-click. Built by build.js. -->`
);

fs.writeFileSync(OUT, html);
const kb = (fs.statSync(OUT).size / 1024).toFixed(0);
console.log(`Wrote ${path.relative(ROOT, OUT)} (${kb} KB, self-contained)`);

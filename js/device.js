/* Device / platform detection.
   Guesses PC vs Mobile vs Touch-laptop, but the user always confirms/overrides
   on the boot screen, and can change it later in Settings. */
const Device = (() => {
  function guessPlatform() {
    const ua = navigator.userAgent || "";
    const hasTouch = (navigator.maxTouchPoints || 0) > 0 || "ontouchstart" in window;
    const coarsePointer = matchMedia("(pointer: coarse)").matches;
    const smallScreen = Math.min(window.innerWidth, window.innerHeight) < 560;
    const mobileUA = /Android|iPhone|iPad|iPod|Mobile|Windows Phone/i.test(ua);

    if ((mobileUA || smallScreen) && hasTouch) return "mobile";
    if (hasTouch || coarsePointer) return "touch"; // touchscreen laptop / tablet-as-desktop
    return "pc";
  }

  function estimateTier() {
    // Rough hardware tier guess used only as an *initial* graphics-quality hint;
    // the live FPS sampler in game.js will correct this at runtime either way.
    const cores = navigator.hardwareConcurrency || 4;
    const mem = navigator.deviceMemory || 4; // GB, Chrome-only, undefined elsewhere
    let score = 0;
    score += cores >= 8 ? 2 : cores >= 4 ? 1 : 0;
    score += mem >= 8 ? 2 : mem >= 4 ? 1 : 0;
    if (score >= 3) return "high";
    if (score >= 1) return "medium";
    return "low";
  }

  return {
    guessPlatform,
    estimateTier,
    hasTouch: () => (navigator.maxTouchPoints || 0) > 0 || "ontouchstart" in window,
  };
})();

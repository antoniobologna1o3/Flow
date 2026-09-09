/* Streaming integration config.
   Spotify/Apple Music playback requires a registered developer app + a
   backend to hold OAuth client secrets (and Spotify full-track playback
   requires a Premium account + their Web Playback SDK). None of that can
   live safely in a static, downloadable client, so this ships disabled by
   default — Your Music (local file import) is the real, working path.

   If you stand up your own tiny backend for the OAuth code exchange, fill
   these in and flip ENABLED to true; js/streaming.js already calls the
   hooks below wherever the UI needs them. */
const StreamingConfig = {
  ENABLED: false,
  SPOTIFY_CLIENT_ID: "",
  SPOTIFY_REDIRECT_URI: window.location.origin + window.location.pathname,
  APPLE_MUSIC_DEV_TOKEN: "",
};

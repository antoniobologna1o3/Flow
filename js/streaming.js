/* Thin hook layer for streaming services. With StreamingConfig.ENABLED
   false (the shipped default) every call here just resolves to "not
   available" so the UI can show the honest explainer modal instead of
   faking a search that would return unrelated random songs. */
const Streaming = (() => {
  function isAvailable() { return !!StreamingConfig.ENABLED; }

  async function connectSpotify() {
    if (!isAvailable() || !StreamingConfig.SPOTIFY_CLIENT_ID) {
      throw new Error("Spotify not configured. See js/streaming-config.js.");
    }
    const scope = "streaming user-read-email user-read-private";
    const url = `https://accounts.spotify.com/authorize?client_id=${encodeURIComponent(StreamingConfig.SPOTIFY_CLIENT_ID)}&response_type=token&redirect_uri=${encodeURIComponent(StreamingConfig.SPOTIFY_REDIRECT_URI)}&scope=${encodeURIComponent(scope)}`;
    window.location.href = url;
  }

  async function connectAppleMusic() {
    if (!isAvailable() || !StreamingConfig.APPLE_MUSIC_DEV_TOKEN) {
      throw new Error("Apple Music not configured. See js/streaming-config.js.");
    }
    if (!window.MusicKit) throw new Error("MusicKit JS not loaded.");
    const music = window.MusicKit.configure({
      developerToken: StreamingConfig.APPLE_MUSIC_DEV_TOKEN,
      app: { name: "Flow", build: "1.0.0" },
    });
    await music.authorize();
    return music;
  }

  return { isAvailable, connectSpotify, connectAppleMusic };
})();

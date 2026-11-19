# Yiffur 2.0 (Remastered)

A ground-up 2026 Kotlin + Jetpack Compose rewrite of **Yiffur**, the native Android client for [e926.net](https://e926.net) and [e621.net](https://e621.net).

## Highlights

- **November 2026 e621 API v2 Compliant**
  - Uses `mode=extended` on `/posts.json`, `/posts/{id}.json`, `/posts/random.json`, `/popular.json`, and `/favorites.json` with the modern `files` / `stats` / `has` / `relationships` / `pools` schema and all 9 tag categories (`artist`, `contributor`, `copyright`, `character`, `species`, `lore`, `meta`, `general`, `invalid`).
  - Automatic WebP preview/sample selection with JPG fallback, plus full 2026 `files.video` multi-stream support (H.264 MP4 variant, 720p, 480p, WebM, AV1 original).
  - HTTP Basic Auth via `Authorization` header on `.json` API endpoints only (never sent to static CDN requests) and mandatory descriptive `User-Agent`.
  - Built-in 2 req/sec rate limiter with polite exponential backoff on HTTP `429` / `503`.
  - Live account verification & profile/blacklist sync via `GET /users/me.json`.
- **Modern Android Architecture**
  - 100% Kotlin 2.0 + Jetpack Compose Material 3 (Edge-to-Edge, Predictive Back, Splash Screen API).
  - Room database (persistent search tabs, local offline favorites, saved search shortcuts, compound blacklist rules, search history), DataStore Preferences, and Android Keystore AES-256-GCM encrypted credential storage.
  - Automatic one-time migration from Yiffur 1.x (`YiffurUserSettings`).
  - Coil 3 (images + animated GIF/WebP) & AndroidX Media3 ExoPlayer (MP4 & WebM playback with quality/speed/mute/loop controls).
  - Scoped Storage `MediaStore` downloads (no legacy storage permission required on Android 10–15).
- **Complete Feature Set**
  - **Browse**: Live `/tags/autocomplete.json` search bar, persistent multi-tab search sessions, cursor (`b<id>`) & page-jump (1–750) pagination, Sort & Media Filter sheet, Saved Search shortcuts, Masonry Flow / Uniform Grid / Single Column layouts (1–5 columns), long-press quick action menu.
  - **Explore**: Popular posts (Day / Week / Month + date navigation), Pools browser, Post Sets browser, Random Post roulette.
  - **Fullscreen Media Viewer**: Pinch-to-zoom, double-tap zoom, animated GIFs, Media3 video player with quality switcher, interactive e621 image **Notes** overlay (`/notes.json`), immersive mode, **Privacy Shield** curtain, live post voting & favoriting, native DText renderer (`[spoiler]`, `[section]`, `[quote]`, `[code]`, `post #id`, `pool #id`, `[[wiki]]`), interactive Tag Builder, in-app Wiki & Artist profile sheets, full Comments view/post/edit/quote-reply/vote.
  - **Comic & Collection Reader**: Dedicated Pool viewer in exact canonical `post_ids` order with vertical **Comic Strip Reader** + Grid mode, Reverse Order, and Parent/Children family viewer.
  - **Blacklist Engine**: Full e621 compound rule syntax (`young -rating:s`, `score:<0`, `*_vore`, `type:webm`, `~or`) with 1-tap sync from your e621 account or e621 defaults.
  - **Privacy & Backup**: `FLAG_SECURE` hide-in-recents toggle, media cache manager, and JSON backup/restore via Storage Access Framework (credentials excluded).

## Build

Requires JDK 17 and Android SDK Platform 35 (`compileSdk = 35`, `minSdk = 26`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

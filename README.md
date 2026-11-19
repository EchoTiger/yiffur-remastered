# Yiffur Remastered (v2.2.0)

A ground-up 2026 Kotlin + Jetpack Compose rewrite of **Yiffur**, the native Android client for [e926.net](https://e926.net) and [e621.net](https://e621.net).
Engineered for ultra-low CPU load, seamless background adaptation on constrained/older devices (Android 7.0+ / API 24+), personalized on-device discovery, visual tag blacklist compounding, and gold-standard media rendering on modern flagships.

This is a ground-up remaster of the old Yiffur project from 2019.

## Highlights

### Personalized "For You" Discovery Feed & Tag Blacklist Compounding (v2.2.0)
- **Explore → "For You" Feed (`ForYouRecommendationEngine`)**: 100% on-device personalized discovery feed synthesized from your local/account favorites, saved searches, search history, and viewed-post signals (Room schema v2 with lossless `MIGRATION_1_2`). Switch between **Balanced Mix**, **Unseen Only**, and **Wildcards**, tap live taste chips to focus, tune/mute preference tags without globally blacklisting them, or long-press any post to mark **"Less like this"**.
- **Visual Tag Blacklist Compounding**: Interactive Compound Rule Builder with `Require (AND)`, `Except (-)` & `Any (~OR)` operator toggles, quick metatags (`rating:e`, `-rating:s`, `type:webm`, `score:<0`, `width:`, `height:`, `duration:`, `tagcount:`), live plain-English explanations, inline rule editing, multi-select merging of existing rules into single compound rules, and 1-tap **"Blacklist Combo"** right inside the Post Viewer's Tag Builder.

### Adaptive Performance & Gold-Standard Media Engine (v2.1.0)
- **Live Adaptive Performance Governor (`AdaptivePerformanceEngine`)**: Continuously monitors device RAM/CPU class, Android thermal status (`PowerManager`), memory trim signals (`onTrimMemory`), Android Battery Saver, metered cellular networks, and off-main-thread `Window.OnFrameMetricsAvailableListener` frame timings with anti-oscillation hysteresis. Automatically scales decode dimensions, bitmap format (`RGB_565` vs `ARGB_8888`), prefetch budget, offscreen pager pages, and video resolution (`Ultra` → `Balanced` → `Power Saver` → `Survival`) so scrolling and viewing stay fluid even on massive desktop artwork.
- **Gold-Standard Tiled Subsampling (`me.saket.telephoto:zoomable-image-coil3`)**: Multi-megapixel 4K–10K desktop PNGs/JPGs decode through `BitmapRegionDecoder` tiles with tiny memory footprint, smart progressive zoom-to-original upgrade, and pixel-accurate panning e621 Notes overlay (`transformedContentBounds`).
- **Hardware-Codec-Aware Media3 Video Engine**: Probes `MediaCodecList` for dedicated silicon decoders (`AV1`, `VP9`, `H.264`) so devices without hardware AV1 never peg CPU at 100% software-decoding e621 AV1 streams. Backed by a persistent Media3 LRU `SimpleCache` (zero redundant network/TLS work on looping videos) and adaptive `DefaultLoadControl` buffers.
- **Zero-Subcomposition Feed Pipeline**: Coil 3.1.0 with bounded parallel decode threads, exact-dimension `RGB_565` grid thumbnails, scroll-idle viewport prefetching, Compose stability configuration, and off-main-thread `Dispatchers.Default` blacklist/feed filtering.
- **Broad Hardware Compatibility**: Supports **Android 7.0 Nougat through Android 15 (`minSdk = 24`, `targetSdk = 35`)** via Core Library Desugaring and API-adaptive hardware bitmap / GIF decoder selection (`AnimatedImageDecoder` API 28+, `GifDecoder` API 24–27).

### Automatic In-App Updates & Dual-Engine Push Notifications
- **Sovereign GitHub Releases Auto-Updater**: Checks `EchoTiger/yiffur-remastered` releases periodically via WorkManager (every 12h) and on startup, streams the matching APK with progress tracking & optional SHA-256 checksum verification, and launches Android's Package Installer via `FileProvider`.
- **Dual-Engine Push Notifications**:
  - **Firebase Cloud Messaging (`com.ugf.yiffur1.MyFirebase`)**: Preserves the exact legacy service class name for seamless upgrades from Yiffur v1.x. Supports topic subscriptions (`yiffur_announcements`, `yiffur_updates`) and rich deep-link payloads (`post_id`, `pool_id`, `query`, `type = "update"`). Safely auto-disables when `app/google-services.json` is not present.
  - **Local Saved Search / Artist Alerts (`TagWatchWorker`)**: Sovereign WorkManager background checks (zero Google dependency) notifying when new posts appear for your saved tag shortcuts.

### November 2026 e621 API v2 & Complete Feature Set
- **November 2026 e621 API v2 Compliant**: `mode=extended` across posts/popular/favorites, all 9 tag categories, WebP previews/samples with JPG fallback, `files.video` multi-stream variants, HTTP Basic Auth strictly on `.json` API endpoints, 2 req/sec rate limiter + exponential backoff on `429`/`503`, and live `/users/me.json` sync.
- **Browse, Explore, Viewer & Comic Reader**: Persistent multi-tab search sessions, live `/tags/autocomplete.json`, cursor (`b<id>`) & page-jump (1–750) pagination, Popular (Day/Week/Month + date stepping), Pools & vertical Comic Strip Reader, Post Sets, Random Post roulette, interactive e621 Notes, native DText renderer, Comments, Tag Builder, Wiki/Artist sheets, Privacy Shield curtain, and compound Blacklist engine.
- **Seamless Legacy v1.x Migration**: Automatic one-time migration from `YiffurUserSettings` SharedPreferences on in-place upgrades (`versionCode = 20200`, strictly higher than both legacy `v1.001` code `2` and `v1.303` code `303`), plus JSON Backup Import supporting **both** Yiffur v2 backups and legacy Yiffur v1 exports.

## Build & Signing

Requires JDK 17 and Android SDK Platform 35 (`compileSdk = 35`, `minSdk = 24`).

```bash
./gradlew testDebugUnitTest assembleDebug
```

### Optional Release Signing & Legacy Package ID (`com.ugf0.yiffur`)
- Provide `YIFFUR_KEYSTORE_PATH`, `YIFFUR_KEYSTORE_PASSWORD`, `YIFFUR_KEY_ALIAS`, and `YIFFUR_KEY_PASSWORD` via environment variables or `local.properties` to build signed release APKs/AABs (`./gradlew assembleRelease` / `./gradlew bundleRelease`).
- Place `app/google-services.json` locally to enable Firebase Cloud Messaging (`BuildConfig.FIREBASE_ENABLED = true`); builds without it succeed cleanly in local-only mode.
- Pass `-Pyiffur.legacyPackage=true` to target the original 2020 package ID `com.ugf0.yiffur` (default is `com.ugf.yiffur1`).

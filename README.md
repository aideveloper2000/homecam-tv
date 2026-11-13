# HomeCam TV

A Google TV (Android TV) app for viewing live IP cameras and DVR/NVR streams,
in the spirit of SuperCam Plus. Leanback browse UI, full-screen live playback,
support for **RTSP** (H.264/H.265 + AAC) and **MJPEG-over-HTTP** cameras.

Built with Kotlin, AndroidX Leanback and media3/ExoPlayer.

## Features

- **TV-first browse screen** — Leanback `BrowseSupportFragment` with a category
  sidebar ("All Cameras", one row per group, "Settings") and image-card grid.
- **Live full-screen playback** — ExoPlayer `RtspMediaSource` for RTSP (forced
  over RTP/TCP so it works through routers that block UDP), with automatic
  reconnect when a stream drops.
- **MJPEG support** — a small built-in MJPEG renderer for cameras that expose an
  HTTP `multipart/x-mixed-replace` stream instead of RTSP.
- **Camera management** — add / edit / delete cameras, grouped into categories,
  persisted locally (SharedPreferences + Gson). Each camera stores its
  **serial number** and **IP / domain**.
- **Live status check** — every camera is probed when the grid loads (RTSP
  `OPTIONS` on a raw socket, HTTP GET for MJPEG) and shows a green/red status
  dot on its card; **Check status** on the details screen re-probes and reports
  latency and the response.
- **Online lookup** — crawls the web for the camera's name + serial number
  (Bing RSS) and lists the matching results, so you can find the device's
  documentation, IP/domain or support pages.
- **D-pad friendly** — every screen is navigable with a TV remote; the play/pause
  media keys are handled in the player.
- **Runs on TV and phone** — `android.software.leanback` and
  `android.hardware.touchscreen` are both `required="false"`, so the same APK
  installs on Google TV, Android TV and phones/tablets.

## Requirements

- Android Studio (or the Android SDK command-line tools)
- JDK 17+
- Android SDK: platform 34, build-tools 34.0.0
- Gradle 8.7 (the wrapper is included)

## Build

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`.

## Use

1. Open **HomeCam TV** on your Google TV.
2. Select **Add camera** (Settings row).
3. Fill in the stream details:
   - **Protocol** — `RTSP` or `MJPEG`
   - **Host** — camera IP or hostname
   - **Port** — `554` for RTSP, `80`/`8080` for MJPEG
   - **Path** — e.g. `/live`, `/stream1`, `/video.mjpg`
   - **Username / Password** — optional, sent as basic-auth / URL credentials
   - **Group** — optional category for the sidebar
   - **Serial number** — the device label serial, used by **Lookup online**
4. Press **Save**, then select the camera card → **Play**.

### Typical RTSP URLs

| Camera | Path example |
| --- | --- |
| Hikvision | `/Streaming/Channels/101` |
| Dahua | `/cam/realmonitor?channel=1&subtype=0` |
| Reolink | `/h264Preview_01_main` |
| Generic | `/live` |

The app builds the URL as `rtsp://[user:pass@]host:port/path`.

## Project layout

```
app/src/main/
├── java/com/homeai/homecamtv/
│   ├── MainActivity.kt              # hosts the Leanback browse fragment
│   ├── PlaybackActivity.kt          # full-screen RTSP/MJPEG player
│   ├── CameraFormActivity.kt        # add / edit camera form
│   ├── data/
│   │   ├── Camera.kt                # camera model + stream URL builder
│   │   ├── CameraStore.kt           # SharedPreferences + Gson persistence
│   │   ├── CameraStatusChecker.kt   # RTSP/HTTP reachability probe
│   │   └── WebLookup.kt             # Bing RSS crawl for a camera's identifiers
│   ├── player/
│   │   └── MjpegView.kt             # MJPEG multipart stream renderer
│   └── ui/
│       ├── MainBrowseFragment.kt    # sidebar + camera grid
│       ├── CameraCardPresenter.kt   # image-card presenter
│       ├── CameraDetailsFragment.kt # per-camera Play / Edit / Delete
│       └── Cards.kt                 # action / hint card models
└── res/                             # layouts, themes, drawables, strings
```

## Notes / limitations

- RTSP playback uses ExoPlayer's RTSP stack: H.264/H.265 video and AAC/PCM
  audio. Other RTSP payloads are not supported.
- RTP is forced over TCP (`setForceUseRtpTcp(true)`), which is the most
  reliable transport through home routers.
- No ONVIF auto-discovery yet — cameras are added by URL. PTZ controls are not
  implemented.
- The online lookup queries Bing's RSS feed for the camera's name + serial
  number and shows the results in a list; it is a public-web search, not a
  camera-specific registry, and needs an internet connection.
- Camera definitions are stored in the app's private storage; there is no
  cloud sync.

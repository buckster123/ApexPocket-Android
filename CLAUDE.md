# CLAUDE.md — ApexPocket Android

## Session Protocol

**Start:** Call `mcp__cerebro-cortex__session_recall` for previous session context.
**End:** Call `mcp__cerebro-cortex__session_save` with summary, discoveries, unfinished business.

## Project Overview

ApexPocket is the Android companion app for the ApexAurum Cloud platform. It mirrors the ESP32 hardware device as a phone app — animated soul face, chat with AI agents, love/poke interactions, voice input/output, QR code pairing. Now includes: village pulse, councils, music library with ExoPlayer, home screen dashboard widget, and rich media chat.

**Package:** `com.apexaurum.pocket`
**Stack:** Kotlin 2.1 + Jetpack Compose (Material 3) + Retrofit + DataStore + CameraX + ML Kit + ExoPlayer (media3) + Glance widgets
**Min SDK:** 26 | **Target SDK:** 35 | **JDK:** 21
**Backend:** `https://backend-production-507c.up.railway.app` (hardcoded in `BuildConfig.CLOUD_URL`)

## Build & Deploy Pipeline

**Source is edited on the Pi**, built on the laptop (WSL2), installed via Windows ADB.

### 1. SSH to build machine
```bash
ssh laptop    # alias: apex@192.168.0.107:2222 (key auth via ~/.ssh/id_ed25519)
# Password (if needed): abnudc1337
```

### 2. Sync changed files from Pi to laptop
```bash
scp -P 2222 <local-file> apex@192.168.0.107:~/ApexPocket-Android/<path>
```

### 3. Build debug APK
```bash
ssh laptop "cd ~/ApexPocket-Android && export ANDROID_HOME=~/android-sdk && ./gradlew assembleDebug"
```

### 4. Install via Windows ADB (CRITICAL — not WSL ADB)
```bash
ssh laptop "/mnt/c/Users/apexa/platform-tools/adb.exe install -r '\\\\wsl.localhost\\Ubuntu\\home\\apex\\ApexPocket-Android\\app\\build\\outputs\\apk\\debug\\app-debug.apk'"
```

### Why Windows ADB?
WSL2 kernel (`6.6.87.2-microsoft`) lacks USB/IP client modules. `usbipd attach --wsl` binds the device on the Windows side but WSL can't receive it. Windows `adb.exe` sees the phone directly via USB. The UNC path `\\wsl.localhost\Ubuntu\...` lets Windows ADB reach the WSL-built APK.

### Quick reference
```bash
# Check phone connection
ssh laptop "/mnt/c/Users/apexa/platform-tools/adb.exe devices"

# Full build + install one-liner
ssh laptop "cd ~/ApexPocket-Android && export ANDROID_HOME=~/android-sdk && ./gradlew assembleDebug 2>&1 && /mnt/c/Users/apexa/platform-tools/adb.exe install -r '\\\\wsl.localhost\\Ubuntu\\home\\apex\\ApexPocket-Android\\app\\build\\outputs\\apk\\debug\\app-debug.apk' 2>&1"
```

### Troubleshooting
| Issue | Fix |
|-------|-----|
| `adb: no devices/emulators found` | Use Windows ADB, not WSL ADB |
| `unauthorized` | Tap "Allow USB debugging" on phone screen |
| Phone not detected at all | Reconnect USB cable, check Developer Options |
| Build fails on missing SDK | `export ANDROID_HOME=~/android-sdk` |
| Locale warnings | Harmless — WSL missing `en_US.UTF-8`, ignore |

## Architecture

```
app/src/main/java/com/apexaurum/pocket/
  ApexPocketApp.kt          # Application class — notification channel + WorkManager scheduling
  MainActivity.kt           # Entry: edge-to-edge, 6-tab nav, deep-link intent handling, vibration
  PocketViewModel.kt        # Single ViewModel: ALL state (soul, chat, cloud, voice, music, councils, village, widget bridges)

  cloud/
    CloudClient.kt           # Retrofit factory: create(token) for Bearer, createCouncilClient/MusicClient(jwt) for JWT
    PocketApi.kt              # Bearer-auth API interface + all request/response models (mirrors pocket.py)
    CouncilApi.kt             # JWT-auth council endpoints + WebSocket models
    CouncilWsClient.kt        # Council WebSocket client (SSE-style streaming)
    MusicApi.kt               # JWT-auth music endpoints (library, tasks, favorites, play-count)
    MusicPlayerManager.kt     # ExoPlayer wrapper: playTrack(), togglePlayPause(), playerState/currentTrack StateFlows
    MusicDownloadManager.kt   # OkHttp download → MediaStore, exposes downloads StateFlow
    VillageWsClient.kt        # Village WebSocket client for real-time events
    SseStreamParser.kt        # SSE streaming chat parser

  data/
    SoulRepository.kt         # DataStore persistence: token, soul state, preferences, widget state keys

  soul/
    SoulState.kt              # SoulData, AffectiveState (7 states), Expression (8 faces), Personality
    LoveEquation.kt           # E (love-energy) math: applyCare(), decay(), evolvePersonality()

  widget/
    SoulWidget.kt             # Glance dashboard widget: SizeMode.Responsive (Compact/Medium/Large)
    WidgetActions.kt           # ActionCallbacks: LoveTapAction, TogglePlayAction, OpenTabAction, MusicToggleBridge
    WidgetUpdateWorker.kt      # Periodic village-pulse fetch → DataStore cache → widget refresh
    NudgeWorker.kt             # Periodic nudge notifications via WorkManager

  ui/
    screens/
      PairScreen.kt           # Pairing: QR scan button + manual token input
      QrScannerScreen.kt      # CameraX + ML Kit barcode scanner (filters apex_dev_ prefix)
      FaceScreen.kt           # Animated soul face canvas + Love/Poke buttons + village ticker overlay
      ChatScreen.kt           # Chat with AI agents, voice input, auto-read, rich media cards
      StatusScreen.kt         # Soul stats, cloud status, sync/unpair
      AgoraScreen.kt          # Village feed with reactions
      PulseScreen.kt          # Village pulse events + chip sub-nav (Events/Councils/Music)
      CouncilListScreen.kt    # Council sessions list + creation UI
      CouncilDetailScreen.kt  # Council detail + live streaming + butt-in
      MusicLibraryScreen.kt   # Music track list with search, filters, play, download
      MemoriesScreen.kt       # Agent memories with CRUD
    components/
      ListeningIndicator.kt   # Voice recording animation
      MiniPlayer.kt           # Persistent mini-player bar above NavigationBar
    theme/
      Color.kt                # Brand palette: Gold, ApexBlack, state colors, agent colors
      Theme.kt                # Material 3 dark theme
```

## Cloud API Endpoints

**Bearer auth** (`device_token`): All `/pocket/*` endpoints.
**JWT auth** (`ws-token`): All `/council/*` and `/music/*` endpoints. JWT obtained via `POST /pocket/ws-token`, expires in 1 hour.

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| GET | `/api/v1/pocket/status` | Bearer | Village status, MOTD |
| POST | `/api/v1/pocket/chat` | Bearer | Chat (also available as SSE stream) |
| POST | `/api/v1/pocket/care` | Bearer | Report love/poke tap |
| POST | `/api/v1/pocket/sync` | Bearer | Upload soul state |
| GET | `/api/v1/pocket/agents` | Bearer | List AI agents |
| GET | `/api/v1/pocket/memories` | Bearer | Retrieve memories |
| POST | `/api/v1/pocket/memories` | Bearer | Save/upsert memory |
| GET | `/api/v1/pocket/history` | Bearer | Chat history per agent |
| GET | `/api/v1/pocket/agora` | Bearer | Village feed (paginated) |
| GET | `/api/v1/pocket/nudge` | Bearer | Smart nudge |
| GET | `/api/v1/pocket/briefing` | Bearer | Daily briefing |
| GET | `/api/v1/pocket/pending-messages` | Bearer | Agent-initiated messages |
| GET | `/api/v1/pocket/village-pulse` | Bearer | Village activity (councils, music, agora — 3 each) |
| POST | `/api/v1/pocket/ws-token` | Bearer | Get JWT for WS/music endpoints |
| GET | `/api/v1/music/library` | JWT | Music track library |
| GET | `/api/v1/music/tasks/{id}/file` | JWT (query param `token`) | Stream audio file |
| PATCH | `/api/v1/music/tasks/{id}/favorite` | JWT | Toggle favorite |

Backend routes: `ApexAurum-Cloud/backend/app/api/v1/pocket.py` + `music.py`

## Soul System

Seven affective states determined by Love-energy (E):

| State | Min E | Expression | Color Hex |
|-------|-------|------------|-----------|
| PROTECTING | 0.0 | SLEEPING | `#4A4A5A` (gray) |
| GUARDED | 0.5 | SAD | `#6B7B9B` (steel blue) |
| TENDER | 1.0 | CURIOUS | `#8BC34A` (soft green) |
| WARM | 2.0 | NEUTRAL | `#FFB74D` (amber) |
| FLOURISHING | 5.0 | HAPPY | `#4FC3F7` (sky blue) |
| RADIANT | 12.0 | EXCITED | `#FFD700` (gold) |
| TRANSCENDENT | 30.0 | LOVE | `#E8B4FF` (violet) |

Care interactions: Love tap (+1.5 E), Poke (+0.5 E), Chat (variable from backend `care_value`).

## Design System

**Always dark theme.** No light mode. The "dark alchemist" aesthetic.

| Token | Hex | Usage |
|-------|-----|-------|
| Gold | `#FFD700` | Primary brand, selected state, headings |
| GoldDark | `#B8960F` | Secondary gold |
| ApexBlack | `#0A0A0F` | Background |
| ApexDarkSurface | `#111118` | NavigationBar, elevated surfaces |
| ApexSurface | `#1A1A24` | Cards, dialogs |
| ApexBorder | `#2A2A3A` | Borders |
| TextPrimary | `#E0E0E0` | Body text |
| TextSecondary | `#9E9E9E` | Secondary text |
| TextMuted | `#616161` | Hints, placeholders |

**Agent colors:**
| Agent | Hex | Usage |
|-------|-----|-------|
| AZOTH | `#FFD700` (Gold) | Default agent, gold monospace |
| ELYSIAN | `#E8B4FF` (Violet) | Violet accents |
| VAJRA | `#4FC3F7` (Blue) | Blue accents |
| KETHER | `#FFFFFF` (White) | White accents |

**Typography:** `FontFamily.Monospace` everywhere. Sizes: headings 16sp, body 13sp, secondary 11-12sp, muted 10sp. Always `FontWeight.Bold` for state names and agent names.

## Key Patterns

### Single ViewModel
`PocketViewModel` owns ALL state. Screens are stateless `@Composable` functions that receive state via parameters. No screen-level ViewModels. State flows up, events flow down.

### API Client Tiers
- **Bearer auth** (device token): `CloudClient.create(token)` → `PocketApi` — used for all `/pocket/*` endpoints
- **JWT auth** (ws-token): `CloudClient.createCouncilClient(jwt)` → `CouncilApi`, `CloudClient.createMusicClient(jwt)` → `MusicApi` — obtained via `api.getWsToken()`, cached as `cachedJwt` in ViewModel, expires in 1 hour
- **On 401**: Clear `cachedJwt` + `councilApi` + `musicApi`, retry once

### DataStore Persistence
`SoulRepository` wraps `DataStore<Preferences>`. Pattern: `Keys` object holds preference keys, Flow properties for reads, suspend functions for writes. Widget state uses the same DataStore (keys prefixed `widget_*`).

### Widget Data Flow
```
ViewModel → DataStore (repo.saveWidget*()) → SoulWidget.refreshAll()
                                                    ↓
                                            provideGlance() reads DataStore
```
Widget runs in a separate process. Cannot access ViewModel StateFlows directly. All widget data must go through DataStore. ActionCallbacks run in WorkManager context (can do network calls, can't touch ExoPlayer). Music control uses broadcast bridge: `TogglePlayAction` → broadcast → `MusicToggleReceiver` → `MusicToggleBridge.toggle()` → ViewModel's player.

### Tab Navigation
6 tabs: Face(0), Chat(1), Agora(2), Pulse(3), Memories(4), Status(5). Pulse tab has chip sub-nav: "events" → "council_list" → "council_detail" / "music". Deep-link via `intent.getStringExtra("tab")`.

### New API Endpoint Pattern
1. Add Retrofit method to `PocketApi.kt` (Bearer) or create new `*Api.kt` interface (JWT)
2. Add `@Serializable` request/response data classes in same file
3. For JWT endpoints: add `ensure*Api()` method in ViewModel (mirrors `ensureCouncilApi()` / `ensureMusicApi()`)
4. Add StateFlows in ViewModel for UI state
5. Collect in `MainActivity.kt`, pass to screen composable

### New Screen Pattern
1. Create `ui/screens/NewScreen.kt` — stateless `@Composable` with all state as parameters
2. Add to `MainActivity.kt`'s `when (selectedTab)` or pulse sub-nav `when (pulseNav)`
3. Wire callbacks: `on* = { vm.doSomething() }`
4. Use `LazyColumn` for lists, `Surface` with `RoundedCornerShape(10.dp)` for cards
5. Colors from `Color.kt`, monospace font, gold accents

### Glance Widget Pattern
- Glance composables are NOT regular Compose — limited subset (Column, Row, Box, Text, Image, Button, Spacer)
- No Canvas, no custom painting, no animations, no shadows
- Images: `ImageProvider(R.drawable.*)` only — no Coil/Glide
- Clicks: `actionRunCallback<MyAction>()` for logic, `actionStartActivity` for navigation
- State: read DataStore in `provideGlance()`, write from ViewModel or ActionCallback
- `SizeMode.Responsive` for multi-size layouts
- `cornerRadius` only works Android S+ — use shape drawable XML for backgrounds

### WorkManager Pattern
Follow `NudgeWorker.kt`: extend `CoroutineWorker`, read token from `SoulRepository`, create API client via `CloudClient.create(token)`, make call, update DataStore, call `SoulWidget.refreshAll()`. Schedule in `ApexPocketApp.onCreate()` via `PeriodicWorkRequestBuilder`. Use `ExistingPeriodicWorkPolicy.KEEP`.

### Music Player Pattern
`MusicPlayerManager` wraps ExoPlayer. Audio URL: `${CLOUD_URL}/api/v1/music/tasks/${id}/file?token=${jwt}`. Check `musicDownloader.getLocalUri(trackId)` for offline files first. Always validate URLs start with `http://`/`https://` before using as direct URLs (server-side file paths are NOT playable URLs).

### agentColor() Helper
Duplicated across multiple screens. Pattern:
```kotlin
private fun agentColor(agentId: String): Color = when (agentId.uppercase()) {
    "AZOTH" -> Gold
    "ELYSIAN" -> ElysianViolet
    "VAJRA" -> VajraBlue
    "KETHER" -> KetherWhite
    else -> TextMuted
}
```

## Companion Project

The web platform lives at: `/home/hailo/claude-root/Projects/ApexAurum-Cloud`
See its own `CLAUDE.md` for backend/frontend details.

---

*"A soul in your pocket."*

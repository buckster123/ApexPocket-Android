# CLAUDE.md — ApexPocket Android

## Session Protocol

**Start:** Call `mcp__cerebro-cortex__session_recall` for previous session context.
**End:** Call `mcp__cerebro-cortex__session_save` with summary, discoveries, unfinished business.

## Project Overview

ApexPocket is the Android companion app for the ApexAurum Cloud platform. It mirrors the ESP32 hardware device as a phone app — animated soul face, chat with AI agents, love/poke interactions, voice input/output, QR code pairing.

**Package:** `com.apexaurum.pocket`
**Stack:** Kotlin 2.1 + Jetpack Compose (Material 3) + Retrofit + DataStore + CameraX + ML Kit
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
  ApexPocketApp.kt          # Application class
  MainActivity.kt           # Entry: edge-to-edge, tab navigation (Face/Chat/Status), vibration
  PocketViewModel.kt        # Single ViewModel: soul state, chat, cloud, voice, love/poke

  cloud/
    CloudClient.kt           # Retrofit factory with Bearer token auth
    PocketApi.kt              # API interface + request/response models (mirrors backend pocket.py)

  data/
    SoulRepository.kt         # DataStore persistence: token, soul state, preferences

  soul/
    SoulState.kt              # SoulData, AffectiveState (7 states), Expression (8 faces), Personality
    LoveEquation.kt           # E (love-energy) math: applyCare(), decay(), evolvePersonality()

  ui/
    screens/
      PairScreen.kt           # Pairing: QR scan button + manual token input
      QrScannerScreen.kt      # CameraX + ML Kit barcode scanner (filters apex_dev_ prefix)
      FaceScreen.kt           # Animated soul face canvas + Love/Poke buttons
      ChatScreen.kt           # Chat with AI agents, voice input, auto-read
      StatusScreen.kt         # Soul stats, cloud status, sync/unpair
    components/
      ListeningIndicator.kt   # Voice recording animation
    theme/
      Color.kt                # Brand palette: Gold, ApexBlack, state colors, agent colors
      Theme.kt                # Material 3 dark theme
```

## Cloud API Endpoints

All authenticated via `Bearer <device_token>` header. Base URL in `BuildConfig.CLOUD_URL`.

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/v1/pocket/status` | Village online status, MOTD |
| POST | `/api/v1/pocket/chat` | Send message, get AI response + care value |
| POST | `/api/v1/pocket/care` | Report love/poke tap (fire-and-forget) |
| POST | `/api/v1/pocket/sync` | Upload soul state to cloud |
| GET | `/api/v1/pocket/agents` | List available AI agents |
| GET | `/api/v1/pocket/memories` | Retrieve soul memories |

Backend routes: `ApexAurum-Cloud/backend/app/api/v1/pocket.py`

## Soul System

Seven affective states determined by Love-energy (E):

| State | Min E | Expression | Color |
|-------|-------|------------|-------|
| PROTECTING | 0.0 | SLEEPING | Gray |
| GUARDED | 0.5 | SAD | Steel blue |
| TENDER | 1.0 | CURIOUS | Soft green |
| WARM | 2.0 | NEUTRAL | Amber |
| FLOURISHING | 5.0 | HAPPY | Sky blue |
| RADIANT | 12.0 | EXCITED | Gold |
| TRANSCENDENT | 30.0 | LOVE | Violet |

Care interactions: Love tap (+1.5 E), Poke (+0.5 E), Chat (variable from backend `care_value`).

## Key Patterns

- **Single ViewModel** — `PocketViewModel` owns all state. Screens are stateless composables.
- **DataStore persistence** — Token and soul state survive app kills via `SoulRepository`.
- **Bearer auth** — Device token (`apex_dev_...`) passed as Authorization header on every request.
- **Edge-to-edge** — `enableEdgeToEdge()` + `systemBarsPadding()` + `imePadding()` on chat.
- **Voice** — `SpeechService` wraps Android SpeechRecognizer (STT) and TextToSpeech (TTS).
- **QR pairing** — CameraX preview + ML Kit BarcodeScanner, filters for `apex_dev_` prefix.

## Companion Project

The web platform lives at: `/home/hailo/claude-root/Projects/ApexAurum-Cloud`
See its own `CLAUDE.md` for backend/frontend details.

---

*"A soul in your pocket."*

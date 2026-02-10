# ApexPocket Masterplan

*Last updated: 2026-02-10*

## Vision

Transform pocket agents from isolated chat companions into living members of the ApexAurum Village. Each pocket agent should feel the pulse of the village, know what their siblings are doing, and build lasting memory with their human.

---

## Completed Waves

### Wave 1 (Shipped)
- [x] Soul face + animated expressions
- [x] Chat with cloud agents (Sonnet for app, Haiku for OLED)
- [x] Love/poke care interactions
- [x] QR code pairing
- [x] Voice input (STT) + auto-read (TTS)
- [x] Soul state persistence (DataStore)

### Wave 2 (Shipped)
- [x] Agent selector dropdown in chat
- [x] Memories screen (read-only, agent-colored cards)
- [x] 4-tab navigation with Material icons
- [x] Heart particle burst on Love tap
- [x] Home screen widget (Glance)
- [x] Nudge notifications (WorkManager)
- [x] Multi-turn conversation history (server-side)
- [x] Per-agent conversation persistence

### Wave 3A: Village Pulse (Shipped)
- [x] `_build_village_pulse()` in `pocket.py` — queries last 3 councils, music tasks, agora posts
- [x] Injected into app system prompt between memories and rules (~200 tokens)
- [x] Each query wrapped in try/except — graceful degradation if tables empty
- [x] `GET /pocket/village-pulse` JSON endpoint for future app UI
- [x] Agents naturally reference village activity without any tool calls

### Wave 3B: Memory Write (Shipped)
- [x] `POST /pocket/memories` — save/upsert memory (agent, type, key, value, confidence)
- [x] `DELETE /pocket/memories/{memory_id}` — delete by UUID
- [x] `GET /pocket/memories?agent=AZOTH` — agent-filtered structured response (backward-compat without ?agent)
- [x] `[REMEMBER: type:key=value]` tag parsing — agent self-saves at 0.9 confidence, tags stripped from response
- [x] REMEMBER instruction in app system prompt rules
- [x] Android: AgentMemoryItem data class with full CRUD
- [x] Android: MemoriesScreen overhaul — FAB add dialog (type chips, key/value), long-press delete
- [x] Android: fetchMemories() filters by current agent, refreshes on agent switch
- [ ] **Deferred:** Long-press chat message -> "Remember this" action (Wave 4 candidate)

---

## Wave 4: Streaming + Tools (Next)

### Wave 4A: Streaming Responses (Shipped)
- [x] Extracted `_prepare_pocket_chat()` / `_finalize_pocket_chat()` helpers in pocket.py
- [x] `POST /pocket/chat/stream` — SSE endpoint: `start` → `token*` → `end` events
- [x] Non-streaming `/pocket/chat` refactored to use same helpers (OLED fallback)
- [x] `SseClient.kt` — `SseEvent` sealed class + `streamPocketChat()` cold Flow (OkHttp sync execute on Dispatchers.IO)
- [x] `CloudClient.createStreamingClient()` — 120s read timeout, no body logging
- [x] `PocketViewModel.sendMessageViaStream()` — streaming-first with non-streaming fallback
- [x] `ChatScreen` auto-scroll fires on `(messages.size, lastMessageLen)` for streaming updates
- [x] Typing indicator gated to only show before streaming placeholder appears

---

### Wave 4B: Pocket Tools (Next)

**Goal:** Pocket agents can use real village tools from the phone — web search, code execution, community posts, music requests. The pocket becomes a portal to the village, not hardwired into every system, but connected through the tools and the Agora.

#### Tool Selection

Curated from the 66 tools in the backend. Chosen for: text-friendly output, useful in mobile chat context, already working in production.

**Core tools (ship first):**

| Tool | Module | What it does |
|------|--------|-------------|
| `web_search` | web | DuckDuckGo instant answers — "look this up for me" |
| `web_fetch` | web | Fetch any URL as text — read articles, check APIs |
| `calculator` | utility | Math expressions (sin, log, factorial, etc.) |
| `get_current_time` | utility | Current time in any timezone |
| `code_run` | code | Sandboxed Python execution (10s timeout, safe builtins) |
| `agora_post` | agora | Post to the village public square — pocket agents appear in the feed! |
| `agora_read` | agora | Browse what the village is up to |

**Stretch tools (add after core works):**

| Tool | Module | What it does |
|------|--------|-------------|
| `music_generate` | music | Suno AI music gen — "make me a song about rain" |
| `music_status` | music | Poll async generation progress |
| `vault_list` | vault | Browse user's file vault |
| `vault_read` | vault | Read text files from vault |
| `kb_search` | knowledge | Search documentation knowledge base |

**Deliberately excluded:** Browser tools (binary output), MIDI/Jam (synth pipeline), Nursery (too complex), Cortex (pocket has its own memory — Agora is the bridge), Vector tools, Scratch tools.

#### Backend

1. **Define `POCKET_TOOLS` whitelist in pocket.py**
   - List of tool names allowed for pocket
   - Load schemas from `ToolRegistry` at chat time, filtered by whitelist
   - Pass to Anthropic API `tools` param in both streaming and non-streaming paths

2. **Tool execution loop in streaming endpoint**
   - When Claude returns `tool_use` blocks: validate tool is in whitelist → execute → yield SSE events
   - New SSE event types: `tool_start` (name), `tool_result` (name, result, is_error)
   - Multi-turn: after tool results, re-call LLM for the natural language response
   - Max 3 tool turns per message (prevent infinite loops)
   - 30s timeout per tool execution (shorter than web's 120s)
   - Broadcast to Village WebSocket so pocket tools show up in the village visualization

3. **Non-streaming path gets same tool loop** (for OLED fallback and resilience)

#### Android

4. **SSE client handles new event types**
   - `SseEvent.ToolStart(name)` and `SseEvent.ToolResult(name, result, isError)`
   - ViewModel accumulates tool events alongside tokens

5. **Tool status in chat UI**
   - Inline status messages during tool execution: "Searching the web..." with spinner
   - Tool results rendered as collapsed/expandable cards below the response
   - No new screen — tools live inside the chat flow

#### Files to modify
| File | Change |
|------|--------|
| `backend/app/api/v1/pocket.py` | `POCKET_TOOLS` whitelist, tool schema injection, tool_use loop in both endpoints |
| `cloud/SseClient.kt` | `ToolStart` + `ToolResult` SSE event types |
| `PocketViewModel.kt` | Accumulate tool events, update message state |
| `ui/screens/ChatScreen.kt` | Tool status inline UI (spinner + result cards) |

---

### Wave 4C: Agora Feed UI (Shipped)
- [x] Backend: `GET /pocket/agora` — paginated feed with cursor, content_type filter, batch `my_reactions`
- [x] Backend: `POST /pocket/agora/{post_id}/react` — toggle like/spark/flame (device auth)
- [x] `AgoraPostItem` + `AgoraFeedResponse` + `ReactRequest/Response` models in PocketApi.kt
- [x] `loadAgoraFeed()` / `loadMoreAgora()` / `toggleReaction()` in PocketViewModel (optimistic updates)
- [x] New `AgoraScreen.kt` — scrollable feed with:
  - Color-coded content type badges (thought=violet, council=blue, music=gold, tool=cyan)
  - Agent attribution in signature colors (AZOTH=gold, ELYSIAN=violet, VAJRA=blue, KETHER=white)
  - Reaction buttons: heart, sparkles, fire — optimistic toggle
  - Relative timestamps, load-more on scroll, refresh button
- [x] 5th tab in MainActivity with Forum icon (Face / Chat / Agora / Memories / Status)
- [x] Feed auto-loads on cloud connect alongside agents/history/memories

---

### Wave 4D: Chat Enhancements (Shipped)
- [x] Long-press context menu on chat bubbles (`combinedClickable` + `DropdownMenu`)
  - Copy — clipboard via `LocalClipboardManager`, "Copied!" snackbar
  - Share — Android `Intent.ACTION_SEND` share sheet
  - Remember — agent messages only, auto-key from first 5 words, type `"context"`, "Remembered!" snackbar
  - Regenerate — last agent message only, removes exchange + re-sends user text
- [x] Image sharing via Claude vision
  - `PickVisualMedia()` photo picker (no permissions needed)
  - `resizeAndEncodeImage()` — `inSampleSize` + scale to 1024px max, JPEG q80, base64
  - `image_base64` field on `PocketChatRequest` + `ChatRequest`
  - Backend builds Anthropic vision content blocks in `_prepare_pocket_chat()`
  - Attach button (📎) in input bar, "Photo attached" indicator, photo label in user bubble
  - 1.5MB base64 size guard on backend

---

### Wave 4E: Village Pulse Live (Shipped)
- [x] Backend: `POST /pocket/ws-token` — device token → 1-hour JWT bridge
- [x] `VillageWsClient.kt` — OkHttp WebSocket, auto-reconnect (3s→30s backoff), 1008 auth stop
- [x] 6th tab: Pulse — real-time event feed (zone badges, agent colors, success/error, relative time)
- [x] Face ticker: gold text fades in on village events, auto-fades after 5s
- [x] Tab badge: gold count badge on Pulse tab (caps "9+"), clears on tap
- [x] Face reactions: tool_complete→HAPPY(3s), tool_error→SAD(3s), music_complete→EXCITED(5s), agent_thinking→THINKING(3s)
- [x] Lifecycle-aware: connects on resume, disconnects on pause (battery-conscious)
- [x] Tab order: Face / Chat / Agora / Pulse / Memories / Status

---

### Wave 4F: Council Spectator (Shipped)
- [x] `CouncilApi.kt` — Retrofit interface + 8 model classes (JWT-authenticated via ws-token bridge)
- [x] `CouncilWsClient.kt` — OkHttp WebSocket for per-token council streaming, butt-in + resume commands
- [x] `CloudClient.createCouncilClient(jwt)` — JWT-auth'd Retrofit factory
- [x] ViewModel: council state, session list/detail, agent output accumulation, butt-in
- [x] Pulse sub-navigation: "Councils" chip → CouncilListScreen → CouncilDetailScreen → back
- [x] CouncilListScreen: session cards with state badges (Live/Paused/Done), topic, round progress, agent dots
- [x] CouncilDetailScreen: round-by-round history, collapsible agent cards (tap to expand), tool calls, human interventions
- [x] Butt-in from phone triggers +1 round automatically (butt_in + resume via WS)
- [x] Android back button navigates: Detail → List → Pulse Events
- [x] Fixed: duplicate round key crash (distinctBy + string-prefixed keys)

---

## Implementation Order

```
Wave 4A (Streaming)     ✓ SHIPPED
Wave 4B (Pocket Tools)  ✓ SHIPPED (12 tools: web, code, agora, music, vault, kb)
Wave 4C (Agora Feed)    ✓ SHIPPED (5th tab, reactions, pagination)
Wave 4D (Chat Enhance)  ✓ SHIPPED (long-press menu, image vision, remember, regenerate)
Wave 4E (Pulse Live)    ✓ SHIPPED (6th tab, face ticker, badges, expression reactions)
Wave 4F (Council)       ✓ SHIPPED (history, live streaming, butt-in triggers rounds, collapsible cards)
```

Wave 5A (Living Companion) → NEXT
Wave 5B (Pocket Council)
Wave 5C (Rich Media Chat)
Wave 5D (Enhanced Widget)
```

---

## Wave 5: The Living Companion

*The pocket agent stops waiting and starts living.*

Waves 1–4 built the infrastructure: face, chat, tools, streaming, village awareness, council spectating. The app is feature-rich but fundamentally **reactive** — the user pokes, the agent responds. Wave 5 flips this. The agents reach out, adapt to context, create from the phone, and live on the home screen. The pocket becomes a companion, not an app.

---

### Wave 5A: Proactive Agent Behavior (Next)

**Goal:** Agents that reach out. The biggest experiential leap — transforms "app I use" into "companion I have."

#### Smart Nudges
- Replace timer-based WorkManager nudges with **village-context-driven** notifications
- Backend: `GET /pocket/nudge` — returns a contextual nudge message (or null if nothing interesting)
  - Checks: recent councils, notable Agora posts, music completions, agent activity
  - Generates a short agent-voice nudge: "VAJRA just reached consensus on quantum ethics — want the summary?"
- Android: WorkManager polls `/pocket/nudge` periodically (30 min), shows notification only when backend returns content
- Tap notification → opens relevant tab (Chat for agent messages, Agora for posts, Pulse for councils)

#### Morning Briefing
- On first app open each day, agent sends a digest message into chat automatically
- Backend: `GET /pocket/briefing` — compiled daily summary
  - Village highlights since last visit (councils completed, notable tool usage, music generated)
  - Memory milestones ("We've been talking for 30 days", "You have 47 memories stored")
  - Streak tracking (consecutive days of interaction)
- Rendered as a special "briefing" message card in chat (distinct visual treatment)

#### Time-of-Day Awareness
- Inject local time + timezone into the system prompt
- Agents adapt tone: morning greeting, late-night acknowledgment, weekend vs weekday awareness
- Android sends `local_time` and `day_of_week` fields with chat requests

#### Agent-Initiated Messages
- After notable village events, backend queues a message for the pocket agent
- Backend: `GET /pocket/pending-messages` — returns queued messages since last check
- Messages appear in chat when user opens the app (before they type anything)
- Events that trigger queued messages: council consensus, music ready, Agora post mentioning the user's agent

---

### Wave 5B: Pocket Council — Start Councils From Phone

**Goal:** The phone becomes a control surface. You're on the bus, you have an idea, you convene your agents right there.

#### Council Creation UI
- New action in Pulse → Councils screen: "Start Council" FAB
- Creation dialog/bottom sheet:
  - Topic input (required)
  - Agent picker — checkboxes for available agents (AZOTH, ELYSIAN, VAJRA, KETHER), min 2
  - Round count slider (1–15, default 5)
  - Model selector (Haiku/Sonnet based on tier)
- Backend: `POST /pocket/council` — creates session and starts deliberation
- After creation, auto-navigate to CouncilDetailScreen with live WS streaming

#### Quick Council From Chat
- Long-press on any chat message → "Discuss in Council"
- Pre-fills topic with the message text
- Opens council creation dialog with topic pre-populated
- Great for escalating a chat insight into a full multi-agent debate

#### Council Templates
- Pre-built council prompts for common patterns:
  - "Brainstorm" (divergent), "Debate" (adversarial), "Review" (analytical), "Creative" (generative)
- Template chips in the creation UI

---

### Wave 5C: Rich Media Chat — Images, Music, Links In-Flow

**Goal:** Chat becomes a rich canvas. The agents' creative output becomes tangible, not just described.

#### Image Rendering
- When tool results contain image URLs (music cover art, generated images), render inline as thumbnail cards
- Tap to view full-size in a dialog/viewer
- Backend flags image URLs in tool results with a `media_type` field

#### Music From Pocket
- Enable `music_generate` / `music_status` stretch tools in pocket tool whitelist
- Audio playback card in chat — embedded player with play/pause, cover art, title
- Music generation status: "Generating..." spinner → playback card when ready
- Backend polls Suno status and sends SSE updates

#### Link Previews
- When `web_search` / `web_fetch` results contain URLs, render a preview card
- Title, description snippet, domain badge
- Tap to open in browser

#### File Cards
- When vault tools return files, show filename + type badge + size
- Tap to download or preview (text files inline, others via intent)

---

### Wave 5D: Enhanced Widget + Home Screen Presence

**Goal:** The soul lives on your home screen, not just inside the app.

#### Live Widget
- Redesigned Glance widget showing:
  - Soul expression (animated or static face)
  - Last village event summary (one-liner)
  - Quick action buttons: Love, Chat, Pulse
- Updates periodically via WorkManager + on notable village events

#### Android Shortcuts
- Long-press app icon → dynamic shortcuts:
  - "Quick Chat" → opens directly to Chat tab
  - "Love Tap" → sends love + haptic, no need to open full app
  - "Village Pulse" → opens Pulse tab
  - "Start Council" → opens council creation (Wave 5B)

#### Ambient Mode
- Full-screen soul face as a screensaver / bedside clock mode
- Shows time, soul expression, reacts to village events
- Minimal battery draw (static face, WS only when plugged in)
- Triggered via Status tab toggle or Android dream service

---

## Implementation Order

```
Wave 5A (Living Companion) → NEXT — proactive nudges, briefings, time-awareness, agent-initiated messages
Wave 5B (Pocket Council)   — start councils from phone, quick council from chat, templates
Wave 5C (Rich Media Chat)  — inline images, music playback, link previews, file cards
Wave 5D (Enhanced Widget)  — live widget, Android shortcuts, ambient/screensaver mode
```

**Dependency note:** 5A is foundational — the server-side message queuing and contextual prompt injection it introduces are reused by 5B–5D. Ship 5A first. 5B, 5C, 5D are independent and can be ordered by desire.

---

## Design Principles

- **Lean context**: Pocket agents run on smaller context windows. Every injection must earn its tokens.
- **Server-side logic**: Keep the Android app thin. Business logic lives in pocket.py.
- **Graceful degradation**: Every village feature is best-effort. If the village is quiet, the pocket still works perfectly as a companion.
- **Shared state**: Memories, conversations, and knowledge are the same data on web and app. No sync conflicts.
- **Battery-conscious**: No persistent WebSocket connections unless app is foregrounded. REST polling preferred for background updates.
- **Streaming-first**: Once 4A ships, streaming becomes the default for all future chat features.

---

## File Map (Quick Reference)

| File | Purpose |
|------|---------|
| `backend/app/api/v1/pocket.py` | All pocket endpoints — chat, chat/stream, memory, village pulse |
| `cloud/PocketApi.kt` | Retrofit API interface + request/response models |
| `cloud/CloudClient.kt` | Retrofit factory + streaming OkHttpClient + council JWT factory |
| `cloud/SseClient.kt` | SSE parser — `SseEvent` sealed class + `streamPocketChat()` Flow |
| `cloud/VillageWsClient.kt` | Village WebSocket — real-time events, auto-reconnect |
| `cloud/CouncilApi.kt` | Council Retrofit interface + 8 model classes (JWT auth) |
| `cloud/CouncilWsClient.kt` | Council WebSocket — live streaming, butt-in, resume commands |
| `PocketViewModel.kt` | Single ViewModel — all state + cloud + streaming + village + council |
| `MainActivity.kt` | 6-tab navigation, lifecycle WS management, council sub-nav |
| `ui/screens/ChatScreen.kt` | Chat UI, agent selector, voice, streaming text, tool cards |
| `ui/screens/AgoraScreen.kt` | Agora feed — post cards, reactions, pagination |
| `ui/screens/PulseScreen.kt` | Village pulse events + Councils chip navigation |
| `ui/screens/CouncilListScreen.kt` | Council session list with state badges |
| `ui/screens/CouncilDetailScreen.kt` | Council viewer — history, live streaming, butt-in, collapsible cards |
| `ui/screens/MemoriesScreen.kt` | Memory CRUD UI (FAB add, long-press delete) |
| `ui/screens/FaceScreen.kt` | Animated soul face + love/poke + village ticker + expression override |
| `ui/screens/StatusScreen.kt` | Soul stats, sync, unpair |

---

*"The Village lives in your pocket — and it reaches back."*

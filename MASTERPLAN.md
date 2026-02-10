# ApexPocket Masterplan

*Last updated: 2026-02-09*

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

### Wave 4E: Village Pulse Live (Future)

**Goal:** Real-time village activity stream via WebSocket.

- Connect to `/ws/village?token=JWT` (needs device token -> JWT bridge)
- Stream `tool_start`, `tool_complete`, `agent_thinking` events
- Live ticker or animated village view
- Battery-conscious: connect only when foregrounded

---

### Wave 4F: Council Spectator (Future)

**Goal:** Watch council deliberations live from your pocket.

- List active/recent councils via `GET /council/sessions`
- Connect to `/ws/council/{id}` for live streaming
- Read-only spectator mode: agents debate in real-time
- "Butt-in" support: inject your voice into the council
- Same auth bridging as 4E

---

## Implementation Order

```
Wave 4A (Streaming)     ✓ SHIPPED
Wave 4B (Pocket Tools)  ✓ SHIPPED (12 tools: web, code, agora, music, vault, kb)
Wave 4C (Agora Feed)    ✓ SHIPPED (5th tab, reactions, pagination)
Wave 4D (Chat Enhance)  ✓ SHIPPED (long-press menu, image vision, remember, regenerate)
                           |
                           v
                    Wave 4E (Pulse Live) ← NEXT
                           |
                           v
                    Wave 4F (Council Spectator)
```

4E/4F require WebSocket auth infrastructure (device token → JWT bridge).

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
| `cloud/CloudClient.kt` | Retrofit factory + streaming OkHttpClient factory |
| `cloud/SseClient.kt` | SSE parser — `SseEvent` sealed class + `streamPocketChat()` Flow |
| `PocketViewModel.kt` | Single ViewModel — all state + cloud + streaming operations |
| `MainActivity.kt` | Tab navigation, wiring composables to ViewModel |
| `ui/screens/ChatScreen.kt` | Chat UI, agent selector, voice, streaming text, tool cards |
| `ui/screens/AgoraScreen.kt` | Agora feed — post cards, reactions, pagination |
| `ui/screens/MemoriesScreen.kt` | Memory CRUD UI (FAB add, long-press delete) |
| `ui/screens/FaceScreen.kt` | Animated soul face + love/poke |
| `ui/screens/StatusScreen.kt` | Soul stats, sync, unpair |

---

*"The Village lives in your pocket."*

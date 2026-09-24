# 🔍 In-Staff Known Issues & Exploit Triage Ledger

The purpose of this file is to document suspected issues, potential bypasses, and edge cases thoroughly with **step-by-step reproduction guides** before making code modifications.

---

## 📋 Legend

* `[Pending Testing]` — Reported or hypothesized; requires in-game reproduction with staff or test accounts.
* `[Confirmed]` — Reproduced and verified in-game; scheduled for mitigation in an upcoming branch.
* `[Resolved]` — Mitigated, tested, and verified on live servers.
* `[Discarded]` — Not a bug, intended vanilla behavior, or non-reproducible.

---

## 🛡️ Triage Ledger

### 1. Anti-Cheat & Client Integrity Bypasses
* `[Pending Testing]` — **Mod Spoofing:** A sophisticated user could compile a cheat client into a custom `.jar` and change the internal mod ID to match something benign (e.g., `jei`) to bypass both the blacklist and the hash check.
* `[Pending Testing]` — **Token Scrubbing:** If a banned player uses a VPN (to change their IP) and manually deletes the `.instaff_token` file (or reinstalls their modpack), they will bypass the offline evasion check as a new token will be generated.
* `[Confirmed]` — **Payload Versioning:** There is no payload version negotiation during the integrity handshake. Mismatched client/server versions will fail unpredictably (decoder exceptions) rather than providing a clean disconnect message to the player.

### 2. Inventory Inspection (`/invsee`) Concurrency
* `[Pending Testing]` — **Simultaneous Interaction Desync:** If an admin is manipulating a live player's inventory via `/invsee` at the exact same millisecond the player drops the item (pressing `Q`) or opens a chest, there could be a visual state desync.
* `[Resolved]` — **Offline Save Race Condition:** Intended behavior confirmed. If an admin is editing an offline player's inventory and that player connects before the admin closes the menu, the admin's modifications are safely discarded (`save_aborted` message sent to staff) and the player's live inventory is unaffected, preventing data corruption.

### 3. Playtime & I/O Async Edge Cases
* `[Pending Testing]` — **Abrupt Shutdown Data Loss:** If the server process is forcefully killed (e.g., power outage) exactly between the data snapshot and the async thread writing to `.tmp`, a few seconds of data might be lost. Ensure the rollback mechanism is atomic and works.
* `[Confirmed]` — **Clean Shutdown Data Loss:** The `PlaytimeTracker` lacks a `ServerStoppingEvent` hook. If the server is stopped normally (e.g., via `/stop`), online players do not trigger `PlayerLoggedOutEvent` and their entire current session playtime is lost.

### 4. Offline-Mode Identity Collisions
* `[Pending Testing]` — **Same-Name Conflict:** If a premium player named "Notch" and a cracked player named "Notch" join at different times, they share the same UUID, inventory, and permissions. Check if they inherit the same punishment records (expected behavior, but needs testing).

### 5. Chat & Mute Bypasses
* `[Resolved]` — **Command Chat Bypass:** The `/mute` system previously only intercepted `ServerChatEvent`. Intercepted via `CommandEvent` in `ModerationEventHandler` with configurable `blockedMuteCommands` in `InStaffConfig`.
* `[Confirmed]` — **Item Text Bypass:** Muted players can still write in books, place signs with text, and rename items in anvils, as these events are not currently intercepted.

### 6. Client Rendering & Visuals
* `[Confirmed]` — **GUI Misalignment in `/invsee`:** The slot placement coordinates for the target's inventory (armor, offhand, main) and the staff's own inventory do not align perfectly with the background container texture.

### 7. Scalability & Performance
* `[Confirmed]` — **Unbounded JSON Growth & Full File Rewrite:** `punishments_history.json` and `playtime.json` are completely rewritten to disk (`.tmp` swap) on every save. There is no rotation or archival limit, meaning write times will increase linearly as the server ages.
* `[Confirmed]` — **In-Memory History List:** `PunishmentManager` uses a `CopyOnWriteArrayList` for the entire punishment history. On servers with thousands of records, the array copy on every new punishment will become a performance bottleneck.
* `[Confirmed]` — **Linear Token Indexing:** `findLastKnownClientToken` performs a linear scan through the entire punishment history from end to start, which shares the scalability concerns of the unbounded JSON growth.

---

## 📜 Session Test Logs Template

### Test Log: 2026-09-16 (QA)
- **Tested Feature:** Inventory Inspection (`/invsee`) Offline Save Race Condition
- **Participants:** Rian
- **Result:** Pass (Intended Behavior Confirmed)
- **Observations:** When an admin is viewing an offline player's inventory and the player logs in, any modifications made by the admin are successfully discarded upon closing the menu. The player's live inventory remains perfectly intact and no server errors or corruption occur.
- **Next Steps:** None. `guardedOfflineSave` works flawlessly.

### Test Log: YYYY-MM-DD
- **Tested Feature:** [Feature Name]
- **Participants:** Host & Players
- **Result:** [Pass / Fail / Edge Case Found]
- **Observations:** [Details]
- **Next Steps:** [Action Plan]
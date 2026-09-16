# CODE_REVIEW.md — In-Staff Mod

> Automated review checklist for AI agents. Based on `ARCHITECTURE.md` (SSOT).
> **Before reviewing any diff**: read the full `ARCHITECTURE.md`. This document doesn't replace it — it translates the invariants into objective, actionable checks.

---

## How to use this document

1. Load `ARCHITECTURE.md` as reference context.
2. For each changed file in the diff, identify the package (`client/`, `commands/`, `moderation/`, `access/`, `inspection/`, `protection/`, `network/`, `config/`, `util/`) and apply the matching sections below.
3. Tag every finding with a severity (see [Severities](#severities)).
4. Produce the report in the [expected output format](#expected-output-format) at the end.
5. Never approve a PR with any `BLOCKER` item without explicit flagging.

---

## Severities

| Level | Meaning |
|:---|:---|
| `BLOCKER` | Violates an invariant from ARCHITECTURE.md §4. Cannot be merged. |
| `HIGH` | Real risk of crash, security exploit, or data corruption, but not a named invariant. |
| `MEDIUM` | Deviation from the expected architectural pattern (e.g. wrong subsystem, wrong side) without immediate risk. |
| `LOW` | Style, naming, incomplete i18n, cosmetic. |
| `NIT` | Optional suggestion. |

---

## 1. Client/Server Side Separation

Reference: §2 "Side Distribution Rules", Invariant 7.

- [ ] No file in `commands/`, `moderation/`, `access/`, `protection/`, `config/` imports `com.tio.instaff.client.*`. → **BLOCKER** if violated.
- [ ] New client-only code lives inside `client/` and is only loaded via `InStaffClient.java` (`@Mod(dist=CLIENT)`).
- [ ] Classes in `inspection/` (`InvseeMenu`, `EnderseeMenu`) remain the only client↔server contact point for inspection — don't add shortcuts that read inventory directly from the client.
- [ ] New payloads in `network/` have handlers registered on the correct side; the payload definition itself may be shared, but the handling logic must not be.
- [ ] `util/` stays free of any side dependency (no `Minecraft.getInstance()`, no side-specific `ServerLevel` usage without abstraction).

**Text red flag**: any `import net.minecraft.client.*` outside `client/`.

---

## 2. Server Tick Thread Stability / Performance

Reference: Invariant 1, §3.4 (Chunk Quarantine), §3.1 (Freezes/PlayerTickEvent).

- [ ] No tick event handler (`PlayerTickEvent`, `ServerTickEvent`, ticking block entity) may propagate an uncaught exception. New logic in these handlers needs a try/catch or must go through the `ChunkQuarantineHandler` pattern.
- [ ] New disk I/O operations (JSON, `.dat`, config) do **not** run synchronously and blocking on the main tick thread for large volumes of data. If blocking, evaluate whether it should be asynchronous.
- [ ] Use of `System.currentTimeMillis()` for elapsed-time logic → **BLOCKER** (Invariant 4). Must use `Util.getMillis()`.
- [ ] `PlayerLoggedInEvent`/`PlayerLoggedOutEvent` handlers (bans, playtime) don't do expensive work (large file reads, hashing) synchronously without need.
- [ ] In-memory data structures (`PunishmentManager`, `WhitelistManager`) accessed by multiple threads (network + tick) are thread-safe (`ConcurrentHashMap`, etc.) or documented as main-thread-only access.
- [ ] Loops over all online players, all punishments, etc., don't introduce per-tick complexity that degrades on large servers (e.g. avoidable O(n²)).
- [ ] `ChunkQuarantineHandler`: any newly caught exception type still gets logged to `instaff/quarantine.log` with exact XYZ coordinates, and the corrupted entity/block entity is removed, not just logged.

---

## 3. Security

### 3.1 UUID and Offline Mode
Reference: §3.1, Invariant 8.

- [ ] **Zero HTTP requests to `api.mojang.com` or any external identity-resolution API.** → **BLOCKER** if any external network call is introduced to resolve name↔UUID.
- [ ] Offline-mode UUID resolution exclusively uses `UUIDUtil.createOfflinePlayerUUID(playerName)` / `server.getProfileCache()`, never an external lookup.
- [ ] New code handling punishment/identity explicitly handles both online-mode and offline-mode, never assuming UUID v4 always.

### 3.2 Ban Evasion
Reference: §3.1 "Offline Ban Evasion Mitigation".

- [ ] New ban-related functionality still allows association with IP and/or installation token (via `IntegrityResponsePayload`), not relying on UUID alone when the server is offline-mode.
- [ ] No new logic allows trivial ban bypass via reconnecting with a different name without going through the existing mitigation layers.

### 3.3 Integrity Handshake / Anti-Cheat
Reference: §3.5.

- [ ] The server never treats the client's response (`IntegrityResponsePayload`) as cryptographic proof — any new security logic that blindly trusts the client-reported hash must be flagged as `HIGH` (the client can lie; this is a deterrent, not proof).
- [ ] `integrity.timeoutSeconds` is still respected; no hung handshakes without a timeout.
- [ ] Changes to `blacklistedModIds` / `blacklistedHashes` don't break backward config compatibility without migration.
- [ ] Handshake failures result in disconnection with a clear reason, never a silent fail-open (allowing entry without validation due to an unhandled error/exception).

### 3.4 Ban-Item and Invsee
Reference: §3.3, §3.4.

- [ ] Writes to `playerdata/<UUID>.dat` (offline invsee) follow the atomic `.tmp` + `Files.move(REPLACE_EXISTING)` pattern (see §4 below). Direct write without swap → **BLOCKER**.
- [ ] `BanItemManager`: new interception points (use, placement, pickup) don't leave gaps (e.g. a new interaction event that bypasses `NO_USE`/`NO_PLACE`/`TOTAL`).

---

## 4. General Invariants (ARCHITECTURE.md §4)

Direct, item-by-item check — any violation is `BLOCKER`:

- [ ] **1. Server Thread Stability** — no uncaught exception can bring down the tick thread.
- [ ] **2. Client + Server Required** — no new feature assumes vanilla client support.
- [ ] **3. Server-Authoritative Config** — moderation config remains `ModConfig.Type.SERVER`; client cannot override server rules.
- [ ] **4. Monotonic Timings** — `Util.getMillis()`, never `System.currentTimeMillis()` for elapsed time.
- [ ] **5. Atomic File Writes** — every disk write uses `.tmp` swap + `Files.move(..., REPLACE_EXISTING)`.
- [ ] **6. Complete Internationalization** — every new player-facing message goes through `LocalizationHelper` and exists in **both** `en_us.json` and `pt_br.json`. Missing key in either file → `LOW`/`MEDIUM` depending on whether it breaks fallback.
- [ ] **7. Side Separation** — see Section 1 above.
- [ ] **8. Offline-Mode Integrity** — see Section 3.1 above.

---

## 5. Package Structure and Conventions

Reference: §2.

- [ ] New code lives in the correct package per the responsibility table (e.g. punishment logic doesn't leak into `commands/`; commands don't implement business logic directly — they must delegate to the subsystem's manager).
- [ ] New Brigadier commands follow the existing naming pattern (`*Command.java`) and live in `commands/`.
- [ ] Type enums (`PunishmentType`, `BanItemMode`) are extended, not duplicated with parallel logic.
- [ ] Optional integrations (e.g. Curios) follow the soft-dependency pattern (`ModList.get().isLoaded(...)`), no hard classpath dependency.

---

## 6. General Code Quality (Java/NeoForge)

- [ ] No `@SuppressWarnings` used to silence a real problem without a justifying comment.
- [ ] Resources (file streams, etc.) closed correctly (try-with-resources).
- [ ] UUID/Player nullability handled explicitly (no potential NPE on offline player lookup).
- [ ] Relevant logs use the mod's logger, not `System.out.println`.
- [ ] Tests (if the project has them) cover the change, especially duration logic (`DurationParser`) and command parsing.

---

## Expected Output Format

Upon completing the review, the agent must produce:

```markdown
## Summary
<1-3 sentences about the diff>

## Findings

### BLOCKER
- `path/File.java:line` — description, violated invariant (§X)

### HIGH
- ...

### MEDIUM
- ...

### LOW / NIT
- ...

## Verdict
[ ] Approved with no reservations
[ ] Approved with comments (no BLOCKER)
[ ] Blocked — requires fixes before merge
```

Omit a severity section if there are no findings for it.

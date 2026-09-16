# ARCHITECTURE.md — In-Staff Mod

> **Single Source of Truth (SSOT)** for the mod's technical architecture and design decisions.
> Every AI agent, contributor, or maintainer must read this document before modifying code.
> **Last updated**: September 2026 — version 1.0.0

---

## 1. Overview and Scope

| Field | Value |
|:---|:---|
| **Mod ID** | `instaff` |
| **Name** | In-Staff |
| **Minecraft** | 1.21.1 |
| **Mod Loader** | NeoForge 21.1.x (FML) |
| **Config Type** | `SERVER` (TOML, authoritative — only the server controls moderation rules) |
| **License** | CC-BY-NC-4.0 |
| **Execution** | **Client + Server** — mod must be installed on both sides |
| **Authentication** | Supports both **Online** (`online-mode=true`) and **Offline** (`online-mode=false`) servers |

### Purpose

In-Staff provides a comprehensive **administration, moderation, and security suite** for modded Minecraft 1.21.1 servers running NeoForge:

1. **Moderation Engine** — Unified UUID-based punishment system supporting bans, temp-bans, mutes, temp-mutes, timeouts, player freezing, and audit history.
2. **Dynamic Maintenance** — Instant maintenance mode locking general logins with customizable MOTD and staff-only bypass.
3. **Smart Whitelist** — In-game dynamic whitelist supporting reload-on-the-fly and localized custom kick messages.
4. **Activity & Playtime Tracker** — Accurate session tracking, total accumulated playtime, and last-seen telemetry.
5. **Live & Offline Invsee** — Interactive live synchronization for online inventory, Ender Chest, and Curios accessories, plus offline `playerdata/<UUID>.dat` manipulation.
6. **Ban-Item System** — Item-level restriction engine offering total confiscation, use-only denial, and block-placement denial.
7. **Safe-Unload & Chunk Quarantine** — Ticking block entity / entity crash interception, preventing corrupted objects from crashing the entire world.
8. **Client Integrity & Anti-Cheat** — Network handshake verifying client mod and resource pack hashes during login, with server-configurable whitelist/blacklist.

### Why Client + Server?

In-Staff is designed exclusively for **modded NeoForge servers** where all players already run NeoForge. Vanilla servers have mature plugin ecosystems (EssentialsX, LuckPerms, etc.) that cover moderation without needing mods. In-Staff exists to fill the gap for modded servers where plugins are unavailable.

Requiring the mod on both sides enables:
- **Client Integrity Verification**: Scanning the client's local `mods/` folder and computing file hashes — impossible without client-side code.
- **Robust Player Freeze**: Client-side movement clamping prevents rendering jitter and client prediction desync.
- **Custom GUI Screens**: Rich inspection menus (Invsee, Endersee, Curios) with proper client rendering.
- **Visual Feedback**: Graying out banned items in the player's own inventory, maintenance screen overlays, etc.

A vanilla client connecting without In-Staff installed will be rejected during the NeoForge mod negotiation handshake.

---

## 2. Package Map and Responsibilities

```
com.tio.instaff/
├── InStaff.java                     ← Common entrypoint (@Mod), lifecycle bootstrapping
├── InStaffClient.java               ← Client entrypoint (@Mod, dist=CLIENT)
├── client/                          ← Client-only code (never imported by server code)
│   ├── screen/                      ← Custom GUI screens
│   │   ├── InvseeScreen.java        ← Client-rendered /invsee GUI
│   │   └── EnderseeScreen.java      ← Client-rendered /endersee GUI
│   └── integrity/                   ← Client-side integrity scanner
│       └── ClientHashScanner.java   ← Scans local mods/ and resourcepacks/, computes SHA-256
├── commands/                        ← Brigadier commands (server-side)
│   ├── StaffCommand.java            ← /staff, /instaff (central administrative hub)
│   ├── PunishCommands.java          ← /ban, /tempban, /mute, /tempmute, /kick, /freeze, /unban, /unmute
│   ├── MaintenanceCommand.java      ← /maintenance [on|off|status|whitelist]
│   ├── WhitelistCommand.java        ← /swhitelist [add|remove|list|reload|on|off]
│   ├── InvseeCommand.java           ← /invsee, /endersee [player]
│   ├── BanItemCommand.java          ← /banitem [add|remove|list|check]
│   ├── HistoryCommand.java          ← /history, /checkpunish [player]
│   └── PlaytimeCommand.java         ← /playtime, /seen [player]
├── config/
│   └── InStaffConfig.java           ← TOML server configuration specifications (ModConfig.Type.SERVER)
├── moderation/                      ← Punishment subsystem (server-side)
│   ├── PunishmentManager.java       ← In-memory index, JSON store, query engine
│   ├── PunishmentType.java          ← Enum: BAN, TEMP_BAN, MUTE, TEMP_MUTE, FREEZE, KICK
│   └── PunishmentRecord.java        ← POJO record: id, targetUUID, staffUUID, reason, expiry, created
├── access/                          ← Session & access controls (server-side)
│   ├── MaintenanceManager.java      ← Maintenance state, ping MOTD listener, kick handler
│   ├── WhitelistManager.java        ← Dynamic whitelist store and login validator
│   └── PlaytimeTracker.java         ← Session timer, total playtime persistence, /seen lookup
├── inspection/                      ← Inventory & container inspection (shared menus)
│   ├── InvseeMenu.java              ← Custom AbstractContainerMenu synced with target player
│   ├── EnderseeMenu.java            ← Custom menu synced with target EnderChest
│   ├── OfflinePlayerDataHelper.java ← Safe NBT loading/writing for offline playerdata/*.dat
│   └── CuriosIntegration.java       ← [Planned] Optional soft-dependency for Curios accessories
├── protection/                      ← Item restrictions and world health (server-side)
│   ├── BanItemManager.java          ← Restricted item store and action validator
│   ├── BanItemMode.java             ← Enum: TOTAL, NO_USE, NO_PLACE
│   └── ChunkQuarantineHandler.java  ← Exception handler catching ticking entity/block errors
├── network/                         ← Network payloads (client <-> server)
│   ├── InStaffNetwork.java          ← Payload registrar for NeoForge networking
│   ├── IntegrityRequestPayload.java ← Server → Client: "send me your hashes"
│   └── IntegrityResponsePayload.java← Client → Server: "here are my hashes"
└── util/
    ├── DurationParser.java          ← Parses human duration strings ("1d12h", "30m", "7d")
    ├── LocalizationHelper.java      ← Server-side translations with en_us fallback
    └── TextUtil.java                ← Formatting helpers, timestamps, chat prefixes

src/main/resources/assets/instaff/lang/
├── en_us.json                       ← English translations (mandatory fallback)
└── pt_br.json                       ← Brazilian Portuguese translations
```

### Side Distribution Rules

| Package | Side | Rule |
|:---|:---|:---|
| `client/` | CLIENT only | Loaded via `InStaffClient.java` (`@Mod(dist=CLIENT)`). Server code must **never** import from `client/`. |
| `commands/` | SERVER only | Brigadier commands are server-authoritative. |
| `moderation/`, `access/`, `protection/` | SERVER only | All moderation logic runs on the server. |
| `inspection/` | BOTH | `*Menu.java` classes are shared (server creates the menu, client renders it). |
| `network/` | BOTH | Payload definitions are shared; handlers are side-specific. |
| `config/` | SERVER | `ModConfig.Type.SERVER` — only the server controls moderation rules. |
| `util/` | BOTH | Pure utility classes with no side dependency. |

---

## 3. Subsystem Architecture

### 3.1 Moderation Engine (`moderation/`)

- **Identifier & Resolution**: Always UUID-based. Lookups from names are resolved via `ServerPlayer` or the server's `GameProfileCache`.
  - **Online Servers (`online-mode=true`)**: Standard Mojang v4 UUIDs.
  - **Offline Servers (`online-mode=false`)**: Deterministic v3 UUIDs generated via `UUIDUtil.createOfflinePlayerUUID(playerName)` (`nameUUIDFromBytes("OfflinePlayer:" + name)`).
  - **Strict Invariant — Zero External Mojang HTTP Requests**: Never query `api.mojang.com` or external web APIs. On offline servers, external APIs return Mojang account UUIDs that do NOT match the server's local deterministic UUIDs, causing bans and data to target the wrong identity.
  - **Offline Ban Evasion Mitigation**: Because offline mode players can alter their launcher nickname to spawn with a fresh UUID, the moderation engine supports combining UUID bans with IP bans and client-side installation tokens exchanged during the `IntegrityResponsePayload` handshake.
- **Punishment Lifecycle**:
  - **Bans**: Intercepted in `PlayerEvent.PlayerLoggedInEvent`. If an active ban exists, the player is immediately disconnected (`player.connection.disconnect(...)`) with a styled Component showing the ban reason, issuer name, and expiry timestamp.
  - **Mutes**: Intercepted in `ServerChatEvent` and private message commands (`/tell`, `/msg`, `/w`). Blocked messages notify the muted player with remaining time.
  - **Freezes**: Server-side: clamps movement in `PlayerTickEvent`, preventing position changes and interactions. Client-side: suppresses camera rotation and movement input to prevent jitter.
- **Audit History**: Every sanction (active or expired) remains recorded in `instaff/punishments_history.json` for staff auditing via `/history <player>`.

### 3.2 Access & Maintenance (`access/`)

- **Maintenance Mode**:
  - Toggled via `/maintenance on|off`.
  - When active:
    - Server List Ping (`ServerStatusPacketListenerImpl` / `ServerStatus`) reflects the maintenance MOTD.
    - Incoming logins from players without staff bypass (OP level 2+) are rejected with `instaff.maintenance.kick_message`.
- **Dynamic Whitelist**:
  - Operates alongside or replaces the vanilla whitelist with instant reload capabilities and customized kick messages.
- **Playtime Tracker**:
  - Captures `loginTime` on `PlayerEvent.PlayerLoggedInEvent`.
  - Accumulates session elapsed time on `PlayerEvent.PlayerLoggedOutEvent` into `instaff/playtime.json`.

### 3.3 Inspection & Invsee (`inspection/`)

- **Live Synchronization**:
  - Online targets share their `Inventory` and `EnderChest` directly through customized container menus (`InvseeMenu` / `EnderseeMenu`).
  - Menus are registered as shared code (server creates the container, client renders `InvseeScreen`).
  - Edits made by staff are immediately reflected in the target player's inventory in real time.
- **Offline Inspection**:
  - Reads `world/playerdata/<UUID>.dat` via `NbtIo.readCompressed`.
  - Loads inventory tags into a virtual container.
  - Upon menu closure, modifications are written back to the `.dat` file atomically (`.tmp` swap).
- **Curios Compatibility [Planned]**:
  - Will be checked via `ModList.get().isLoaded("curios")`. If present, accessory slots are appended to the inspection view.

### 3.4 Protection & Chunk Quarantine (`protection/`)

- **Ban-Item**:
  - Server-side: Intercepts `PlayerInteractEvent.RightClickItem` (`NO_USE`), `PlayerInteractEvent.RightClickBlock` and `BlockEvent.EntityPlaceEvent` (`NO_PLACE`), and `EntityItemPickupEvent` plus inventory ticks (`TOTAL`).
  - Client-side: Optionally renders banned items as grayed-out or with a red overlay in the player's inventory.
- **Chunk Quarantine**:
  - Wraps risky block entity ticks or handles unhandled tick exceptions.
  - Corrupted entities or block entities that would cause server crash are stripped, logged to `instaff/quarantine.log` with exact world XYZ coordinates, and cleanly removed.

### 3.5 Client Integrity & Anti-Cheat (`network/`)

The client integrity system verifies that connecting clients are running an approved set of mods and resource packs.

#### Handshake Flow

```
┌──────────┐                              ┌──────────┐
│  SERVER   │                              │  CLIENT   │
└─────┬────┘                              └─────┬────┘
      │  1. Player joins (post-login)           │
      │────── IntegrityRequestPayload ─────────>│
      │       (server sends list of required    │
      │        + blacklisted mod IDs/hashes)    │
      │                                         │
      │                          2. Client scans│
      │                          local mods/ and│
      │                          resourcepacks/ │
      │                          computes SHA-256│
      │                          per file       │
      │                                         │
      │<───── IntegrityResponsePayload ─────────│
      │       (client sends back manifest:      │
      │        filename → SHA-256 hash)         │
      │                                         │
      │  3. Server validates:                   │
      │     - Required mods present?            │
      │     - Blacklisted mods absent?          │
      │     - Known X-Ray/cheat hashes?         │
      │                                         │
      │  4a. PASS → allow play                  │
      │  4b. FAIL → disconnect with reason      │
      └─────────────────────────────────────────┘
```

#### Configuration

| Config Key | Type | Default | Description |
|:---|:---|:---|:---|
| `integrity.enabled` | bool | `true` | Enable/disable the integrity handshake |
| `integrity.timeoutSeconds` | int | `10` | Seconds to wait for client response before kicking |
| `integrity.requiredMods` | list | `[]` | Mod IDs that must be present on the client |
| `integrity.blacklistedHashes` | list | `[]` | SHA-256 hashes of known cheat mods/packs |
| `integrity.blacklistedModIds` | list | `["xray", "freecam", "baritone"]` | Mod IDs that are forbidden |

#### Security Notes

- The hash scan runs on the **client JVM** and the server trusts the response. A determined cheater could patch the mod to lie. This is a **deterrent**, not a cryptographic proof. It raises the bar significantly beyond "just install X-Ray".
- For higher security, combine with server-side detection (ore mining pattern analysis, movement anomaly detection).

---

## 4. Invariants and Golden Rules

1. **Server Thread Stability**: No exception from any module is allowed to bubble up and crash the server tick thread.
2. **Client + Server Required**: In-Staff requires NeoForge on both client and server. Vanilla clients are not supported and will be rejected during mod negotiation.
3. **Server-Authoritative Configuration**: All moderation config uses `ModConfig.Type.SERVER`. The client cannot override server rules.
4. **Monotonic Timings**: Never use `System.currentTimeMillis()` for elapsed-time logic; always use `Util.getMillis()`.
5. **Atomic File Writes**: Any disk write must use a `.tmp` file swap via `Files.move(..., StandardCopyOption.REPLACE_EXISTING)`.
6. **Complete Internationalization**: Every player-facing message must use `LocalizationHelper` and be defined in both `en_us.json` and `pt_br.json`.
7. **Side Separation**: Server code must never import from `com.tio.instaff.client.*`. Client code accesses shared types from `network/`, `inspection/`, and `util/` only.
8. **Offline-Mode (`online-mode=false`) Integrity**: All UUID resolution must remain strictly local and deterministic (`server.getProfileCache()` or `UUIDUtil.createOfflinePlayerUUID`). External HTTP requests to Mojang APIs are strictly forbidden. Moderation and access systems must implement IP and client-token associations to mitigate offline ban evasion.
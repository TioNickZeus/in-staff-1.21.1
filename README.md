# In-Staff (Minecraft 1.21.1 / NeoForge)

**In-Staff** is a production-ready, dual-sided administration, moderation, telemetry, and security suite built natively for **Minecraft 1.21.1** on **NeoForge** (21.1.250+).

Designed specifically for modded servers where both the server and players run NeoForge, In-Staff gives server administrators and staff teams full authoritative control without relying on external Bukkit/Spigot/Paper hybrid bridges.

> **Important Dual-Sided Requirement**: Both the server and all connecting clients must have In-Staff installed. Vanilla clients will be rejected during the NeoForge network handshake. Client integrity and anti-cheat modules run active verification against unauthorized mods and resource packs.

---

## Key Highlights & Architectural Invariants

- **Dual-Sided Client Integrity**: Enforces a cryptographic handshake during login (`IntegrityRequestPayload` / `IntegrityResponsePayload`). Scans client-side `mods/` directory with SHA-256 hashes, detects blacklisted mod IDs (`xray`, `baritone`, `freecam`), and verifies required mod configurations.
- **Offline Server (`online-mode=false`) Compatibility**:
  - Deterministic UUID resolution via `UUIDUtil.createOfflinePlayerUUID(username)`.
  - Zero external Mojang HTTP API calls (`api.mojang.com`), preventing identity collisions and rate limiting.
  - Mitigates offline ban evasion by binding persistent client installation tokens (`.instaff_token`) and IP addresses to punishment records.
- **Atomic File Persistence**: All data is written with temporary `.tmp` file swaps (`Files.move(REPLACE_EXISTING, ATOMIC_MOVE)`), preventing corrupt or half-written JSON files during abrupt shutdowns.
- **Zero Server Crash Policy (Chunk Quarantine)**: Isolates and logs corrupted ticking entities and block entities directly into `instaff/quarantine.log` instead of crashing the server.
- **Bidirectional Live & Offline Inspection**: Inspect player inventories (41 slots) and Ender Chests (27 slots) in real-time or modify offline playerdata (`world/playerdata/<UUID>.dat`) directly with atomic safety.
- **Native Bilingual Localization**: Built-in support for English (`en_us`) and Brazilian Portuguese (`pt_br`).

---

## Commands Reference

All administrative commands require **OP level 2** permissions, except `/playtime` without arguments which is accessible to all players.

### Summary Table

| Command | Arguments | Permission | Description |
| :--- | :--- | :--- | :--- |
| `/staff` (or `/instaff`) | None | OP Level 2 | Displays an overview and help guide for all staff commands. |
| `/isban` | `<player> [reason]` | OP Level 2 | Permanently bans a player (online or offline), kicks target, and logs to audit history. |
| `/istempban` | `<player> <duration> [reason]` | OP Level 2 | Temporarily bans a player for a specific duration. |
| `/isunban` | `<player>` | OP Level 2 | Revokes an active ban and updates audit records. |
| `/mute` | `<player> [reason]` | OP Level 2 | Permanently mutes a player's server chat messages. |
| `/tempmute` | `<player> <duration> [reason]` | OP Level 2 | Temporarily mutes a player's server chat messages. |
| `/unmute` | `<player>` | OP Level 2 | Revokes an active mute on a player. |
| `/kick` | `<player> [reason]` | OP Level 2 | Disconnects an online player from the server with audit logging. |
| `/freeze` | `<player>` | OP Level 2 | Toggles freeze state (clamps movement, blocks placement/interaction). |
| `/maintenance` | `on` \| `off` \| `status` | OP Level 2 | Toggles server maintenance mode and displays active status. |
| `/maintenance bypass` | `add <player>` \| `remove <player>` | OP Level 2 | Manages player exemptions from maintenance kick checks. |
| `/swhitelist` | `on` \| `off` \| `list` \| `reload` | OP Level 2 | Manages the standalone Smart Whitelist. |
| `/swhitelist` | `add <player>` \| `remove <player>` | OP Level 2 | Adds or removes players from the Smart Whitelist. |
| `/isinvsee` | `<player>` | OP Level 2 | Opens full 41-slot inventory (armor, offhand, main, hotbar) for online/offline players. |
| `/isendersee` | `<player>` | OP Level 2 | Opens 27-slot Ender Chest container for online or offline players. |
| `/banitem add` | `<item> [mode]` | OP Level 2 | Restricts an item with a specific restriction mode (`TOTAL`, `NO_USE`, `NO_PLACE`). |
| `/banitem remove` | `<item>` | OP Level 2 | Removes restrictions from an item. |
| `/banitem list` | None | OP Level 2 | Lists all currently restricted items and their respective modes. |
| `/banitem check` | `[item]` | OP Level 2 | Checks restriction mode of held item or specified item identifier. |
| `/history` (or `/checkpunish`) | `<player>` | OP Level 2 | Displays complete punishment history audit trail for target player. |
| `/playtime` | None | Everyone | Shows the sender's total accumulated server playtime. |
| `/playtime` | `<player>` | OP Level 2 | Shows accumulated playtime and current online status of target player. |
| `/seen` | `<player>` | OP Level 2 | Shows telemetry info: status, last seen date/time, first join, and total playtime. |

---

## Detailed Command Usage

### Moderation & Sanctions

#### 1. Ban & Temporary Ban (`/isban`, `/istempban`)
```text
/isban <player> [reason...]
/istempban <player> <duration> [reason...]
/isunban <player>
```
- **Duration syntax**: Supports composite strings such as `30m`, `2h`, `1d12h`, `7d`, `1w`, `1mo`, `1y`.
- **Offline support**: Works seamlessly on offline players by resolving local deterministic UUIDs.
- **Offline Ban Evasion**: Records player IP and client hardware installation token (`.instaff_token`) to prevent players from bypassing bans by simply changing their nickname in offline launchers.
- **Broadcast**: Automatically broadcasts disciplinary actions to online players when `broadcastPunishments = true`.

#### 2. Chat Mute & Temporary Mute
```text
/mute <player> [reason...]
/tempmute <player> <duration> [reason...]
/unmute <player>
```
- Intercepts incoming `ServerChatEvent`. When a muted player attempts to send chat messages, the message is cancelled, and the player is reminded of their remaining mute duration and reason.

#### 3. Kick & Freeze
```text
/kick <player> [reason...]
/freeze <player>
```
- `/kick` immediately disconnects the player and logs the disciplinary event into the persistent audit trail.
- `/freeze` clamps player coordinates, zeroes delta movement, cancels block breaking, block placing, item clicking, and entity interactions. A reminder notification is displayed every 5 seconds.

---

### Access Control

#### 1. Maintenance Mode
```text
/maintenance on
/maintenance off
/maintenance status
/maintenance bypass add <player>
/maintenance bypass remove <player>
```
- When active, unauthorized players attempting to connect are disconnected with a customizable localized kick message (`instaff.maintenance.kick_message`).
- Staff members (OP level 2+) and players added to `/maintenance bypass add` can connect freely.
- Displays a custom MOTD in the multiplayer server list.

#### 2. Smart Whitelist
```text
/swhitelist on
/swhitelist off
/swhitelist add <player>
/swhitelist remove <player>
/swhitelist list
/swhitelist reload
```
- Operates independently from the vanilla whitelist system.
- Supports on-the-fly disk reloads and deterministic UUID storage in `instaff/whitelist.json`.

---

### Inspection Engine

#### 1. Inventory Inspection (`/isinvsee`)
```text
/isinvsee <player>
```
- Opens an administrative container displaying:
  - **Armor Slots**: Head, Chestplate, Leggings, Boots.
  - **Offhand Slot**: Dedicated offhand weapon/shield slot.
  - **Main Inventory & Hotbar**: All 36 standard inventory slots.
  - **Staff Inventory**: Bottom area allows seamless bidirectional shift-clicking and item transfers.
- **Online Players**: Bidirectional real-time synchronization directly with the player's active inventory.
- **Offline Players**: Reads and writes `world/playerdata/<UUID>.dat` NBT atomically with `.tmp` swaps upon container closure. Self-inspection is blocked.

#### 2. Ender Chest Inspection (`/isendersee`)
```text
/isendersee <player>
```
- Opens a 27-slot Ender Chest interface with live bidirectional synchronization (for online players) or atomic NBT persistence (for offline players).

---

### Protection & Restricted Items (`/banitem`)

```text
/banitem add <item_id> [mode]
/banitem remove <item_id>
/banitem list
/banitem check [item_id]
```

#### Restriction Modes:
- `TOTAL` (or `ALL`): Complete restriction. Item cannot be held in inventory, used, placed, or picked up. If detected in inventory, it is confiscated and removed automatically.
- `NO_USE` (or `INTERACT`): Prevents right-clicking, consuming, or activating the item.
- `NO_PLACE`: Prevents placing the item as a block in the world.

*Staff bypass*: Players with OP level 2 bypass ban-item restrictions.

---

### Telemetry & Auditing

#### 1. Playtime & Last Seen
```text
/playtime
/playtime <player>
/seen <player>
```
- Accurately measures monotonic session durations via `net.minecraft.Util.getMillis()` and persists accumulated totals to `instaff/playtime.json`.
- `/seen` displays whether the player is currently online, their last disconnect timestamp, their first recorded join timestamp, and total lifetime playtime.

#### 2. History Audit Trail
```text
/history <player>
/checkpunish <player>
```
- Displays all historical sanctions (Bans, TempBans, Mutes, TempMutes, Kicks, Freezes) targeting the player with timestamp, staff issuer, reason, and status:
  - `[ACTIVE]` (Ongoing punishment)
  - `[EXPIRED]` (Elapsed temporary punishment)
  - `[REVOKED]` (Forgiven/unbanned/unmuted by staff)

---

## Configuration (`config/instaff-server.toml`)

The server configuration file is located at `world/serverconfig/instaff-server.toml` (or `defaultconfigs/instaff-server.toml`):

```toml
[moderation]
    # Broadcast punishments (bans, mutes, kicks) to all players
    broadcastPunishments = true
    # Mitigate offline ban evasion using IP and client installation token matching
    preventOfflineBanEvasion = true
    # Default duration when omitted in mutes
    defaultMuteDuration = "1h"
    # Maximum allowed temporary ban duration in days
    maxTempBanDays = 365

[maintenance]
    # Enable maintenance mode
    enabled = false
    # Server list MOTD during maintenance
    motd = "§cServer under Maintenance §8- §eStaff Only"
    # Kick message translation key or raw text
    kickMessage = "instaff.maintenance.kick_message"

[protection]
    # Isolate corrupted ticking entities/tiles instead of crashing server
    quarantineCorruptedEntities = true
    # Log quarantine events to instaff/quarantine.log
    logQuarantineEvents = true

[integrity]
    # Enable client mod/hash integrity verification handshake
    enabled = true
    # Timeout in seconds before kicking unresponsive clients
    timeoutSeconds = 10
    # Mod IDs required on connecting clients
    requiredMods = []
    # Blacklisted SHA-256 hashes of forbidden jars
    blacklistedHashes = []
    # Blacklisted mod IDs (e.g. cheat mods)
    blacklistedModIds = ["xray", "freecam", "baritone"]
```

---

## Data Storage (`instaff/`)

All persistent data is stored in JSON format inside the server root's `instaff/` directory:

- `instaff/punishments_history.json` - Complete audit trail of all issued sanctions and revocations.
- `instaff/maintenance.json` - Maintenance status and staff bypass UUID list.
- `instaff/whitelist.json` - Smart Whitelist configuration and whitelisted UUID/name pairs.
- `instaff/banned_items.json` - Restricted item IDs and their active restriction modes.
- `instaff/playtime.json` - Lifetime playtime and connection telemetry for all players.
- `instaff/quarantine.log` - Audit log recording isolated corrupt entities and block entities.

---

## Data Collection & Privacy

In-Staff is self-hosted, decentralized software: it does not send any data to the 
mod's developer, to any third party, or to any external service. All data described 
below is collected, stored, and controlled entirely by the individual server owner 
and their hosting provider — not by the mod's author.

In-Staff collects the following data from connecting clients to support moderation 
and anti-ban-evasion features:

- **Mod/resource pack integrity**: SHA-256 hashes of files in your `mods/` and 
  `resourcepacks/` folders (not the files themselves), compared against server-defined 
  whitelists/blacklists.
- **Installation token**: a persistent identifier used to deter offline ban evasion. 
  Derived from an OS installation identifier (or a random UUID fallback) and hashed 
  with SHA-256 on the client before transmission. The server never receives or 
  stores raw identifiers — only the one-way cryptographic hash.
- **IP address**: standard for any Minecraft server connection, additionally logged 
  alongside punishment records to deter ban evasion.

**Who controls this data**: Once collected, this data is stored in plain JSON files 
(`instaff/` folder) on the server you connect to. The server owner and their hosting 
provider control how long it is retained (typically for as long as the server operates) 
and who can access it — in-game, this data is visible only to staff with OP level 2+ 
via commands like `/history` and `/seen`. If you have questions about data retention 
or deletion, contact the specific server's staff/owner, not the mod's developer.

---

## Installation & Requirements

- **Java**: JDK 21
- **NeoForge**: `21.1.250` or higher
- **Minecraft**: `1.21.1`

### Server Installation
1. Place `instaff-<version>.jar` into your server's `mods/` folder.
2. Start the server once to generate initial configuration files.

### Client Installation
1. Place `instaff-<version>.jar` into your client launcher's `mods/` folder.
2. Launch Minecraft using the NeoForge 1.21.1 profile.

---

## Building from Source

To compile the mod JAR from source:

```powershell
./gradlew build
```

The compiled artifact will be located in:
```text
build/libs/instaff-<version>.jar
```

To run the automated test suite (47 tests covering all modules):
```powershell
./gradlew test
```

---

## License

Licensed under [CC-BY-NC-4.0](https://creativecommons.org/licenses/by-nc/4.0/).  
Author: **TioNickZeus**

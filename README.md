# In-Staff (Minecraft 1.21.1 / NeoForge)

**In-Staff** is a comprehensive administration, moderation, and server security suite for Minecraft 1.21.1 on NeoForge.

Designed for **modded NeoForge servers** where all players already run NeoForge. Gives server administrators, moderators, and staff teams total control without needing external plugins.

> **Both the server and all connecting clients must have In-Staff installed.** Vanilla clients will be rejected during the NeoForge mod negotiation handshake. For vanilla servers, use established plugin solutions like EssentialsX or LuckPerms instead.

## Core Features

- **Client Integrity & Anti-Cheat** - Verifies client mod/resource pack hashes during login to detect unauthorized mods (X-Ray, Freecam, etc.).
- **Moderation Suite** - Apply temporary or permanent bans, timeouts, mutes, and freeze players via UUID with full audit history.
- **Dynamic Maintenance Mode** - Block non-staff player logins with customizable kick messages and maintenance MOTD.
- **Real-Time Dynamic Whitelist** - Manage player access on the fly without restarting the server.
- **Activity & Playtime Tracker** - Log total play time, first join, and last seen timestamps per player.
- **Punishment History & Audit Log** - Keep a persistent, searchable record of all sanctions issued by staff.
- **Live & Offline Invsee** - Inspect and modify player inventories, Ender Chests, and Curios accessory slots in real time (with offline NBT support).
- **Ban-Item System** - Block problematic or exploit-prone items with granular restriction modes (total ban, use-only, or placement-only).
- **Safe-Unload / Chunk Quarantine** - Safely catch ticking tile/entity exceptions to prevent whole-server crashes and isolate corrupted objects.

## Installation

### Requirements
- **Java 21** (JDK 21)
- **NeoForge** `21.1.250`+

### Server
Place `in-staff-<version>.jar` in the server's `mods/` folder.

### Client
Place the same `in-staff-<version>.jar` in the client's `mods/` folder. Players connecting without the mod will be rejected.

## Building from Source

```powershell
./gradlew build
```

The compiled JAR will be located in `build/libs/in-staff-<version>.jar`.

## License
Licensed under CC-BY-NC-4.0. Author: **TioNickZeus**.
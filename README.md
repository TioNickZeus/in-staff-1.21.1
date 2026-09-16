# In-Staff (Minecraft 1.21.1 / NeoForge)

**In-Staff** is a comprehensive administration, moderation, and server security suite for Minecraft 1.21.1 on NeoForge.

Designed to give server administrators, moderators, and staff teams total control over their servers without needing heavy external plugins or unstable workarounds.

## 🌟 Core Features

- **Dynamic Maintenance Mode** — Block non-staff player logins with customizable kick messages and maintenance MOTD during server upgrades or testing.
- **Moderation Suite** — Apply temporary or permanent bans, timeouts, mutes, and freeze players via UUID.
- **Real-Time Dynamic Whitelist** — Manage player access on the fly without restarting the server.
- **Activity & Playtime Tracker** — Log total play time, first join, and last seen timestamps per player.
- **Punishment History & Audit Log** — Keep a persistent, searchable record of all sanctions issued by staff.
- **Live & Offline Invsee** — Inspect and modify player inventories, Ender Chests, and Curios accessory slots in real time (with offline NBT support).
- **Ban-Item System** — Block problematic or exploit-prone items with granular restriction modes (total ban, use-only, or placement-only).
- **Safe-Unload / Chunk Quarantine** — Safely catch ticking tile/entity exceptions to prevent whole-server crashes and isolate corrupted objects.
- **Client Integrity & Anti-Cheat Handshake** — Verify client mod/pack hashes to prevent unfair advantages.

## 🛠️ Building & Developing

### Requirements
- **Java 21** (JDK 21)
- NeoForge `21.1.250`+

### Compilation
```powershell
./gradlew build
```

The compiled JAR will be located in `build/libs/in-staff-1.0.0.jar`.

## 📜 License
Licensed under CC-BY-NC-4.0. Author: **TioNickZeus**.

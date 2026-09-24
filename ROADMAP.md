# In-Staff Roadmap & Feature Board

This document tracks planned features, current sprint goals, and technical milestones for **In-Staff** (Minecraft 1.21.1 / NeoForge).

---

## Version 1.0.0 — Foundation & Moderation Suite

- [x] **Project Initialization & Scaffolding**: NeoForge 1.21.1 template cleaned, build scripts tuned, and architectural guidelines established.
- [x] **Client + Server Architecture**: Dual-sided mod with `InStaffClient.java` client entrypoint, `client/` package structure, and side separation rules.
- [x] **Core Moderation Engine**:
  - [x] UUID-based ban and temp-ban system with custom kick/rejection screen.
  - [x] Chat mute and temp-mute system with remaining time alerts.
  - [x] Player freeze mechanic restricting movement, interactions, and rotation (server + client enforcement).
  - [x] Punishment audit history ledger (`/history <player>`).
  - [x] Human duration parser (`1d`, `12h`, `30m`).
  - [x] Offline server (`online-mode=false`) compatibility (local `UUIDUtil.createOfflinePlayerUUID` and `GameProfileCache` resolution, zero Mojang HTTP queries).
  - [x] Offline ban evasion mitigation (dual IP + client installation token binding).
- [x] **Access & Maintenance**:
  - [x] Dynamic maintenance mode with custom MOTD and staff whitelist bypass.
  - [x] In-game dynamic whitelist with live reload and custom kick messages.
  - [x] Player playtime tracker and `/seen` last-login telemetry.
- [x] **Inspection & Container Auditing**:
  - [x] Live online `/invsee` with bidirectional synchronization.
  - [x] Client-side `InvseeScreen` rendering.
  - [x] Live online `/endersee` for Ender Chest inspection.
  - [x] Client-side `EnderseeScreen` rendering.
  - [x] Offline player inventory loading and saving via `playerdata/<UUID>.dat`.
  - [ ] Curios accessory integration (soft dependency).
- [x] **World Protection & Health**:
  - [x] Ban-Item system with granular modes (`TOTAL`, `NO_USE`, `NO_PLACE`).
  - [x] Client-side banned item visual feedback (grayed-out rendering).
  - [x] Safe-Unload / Chunk Quarantine for corrupted ticking block entities and entities.
- [x] **Client Integrity (Anti-Cheat)**:
  - [x] Client-side hash scanner (`ClientHashScanner`) for `mods/` and `resourcepacks/`.
  - [x] Server-side hash validation with configurable whitelist/blacklist.
  - [x] Network handshake payloads (`IntegrityRequestPayload`, `IntegrityResponsePayload`).
  - [x] Configurable timeout and kick messages.

## Version 1.1.0 — Technical Debt & Polish

- [x] **Logging Standardization**: Replace generic `System.err.println` and `printStackTrace()` in defensive tick-loop try/catch blocks (`ProtectionEventHandler`, `ModerationEventHandler`, `IntegrityEventHandler`) with the centralized `InStaff.LOGGER.error`.
- [x] **Command Delegation Strictness**: Refactor punishment commands (e.g., `/kick`, `/ban`) to fully delegate player disconnection logic to `PunishmentManager` instead of issuing the `connection.disconnect()` call directly from the command execution layer.
- [ ] **Curios Accessory Integration**: Implement soft-dependency support for inspecting and modifying Curios slots via `/invsee`.
- [ ] **Mute Command Bypass**: Intercept `CommandEvent` in `ModerationEventHandler` to prevent muted players from using chat-related commands. Instead of hardcoding, expose a `blocked_mute_commands` string list in the config file, defaulting to standard chat/PM commands (`/msg`, `/tell`, `/g`, `/global`, `/w`, `/reply`, etc.).
- [ ] **Invsee GUI Alignment**: Fine-tune the pixel coordinates (X, Y) of the slots in `InvseeMenu` and the background texture dimensions in `InvseeScreen` so they align perfectly in-game.
- [x] **IMPORTANT: Command Collision Mitigation**: Rename the `/invsee` and `/endersee` commands to something unique (e.g., `/isinvsee` and `/isendersee`) to prevent critical command execution conflicts with other popular server utility mods (like Essentials).
- [ ] **Pre-Join Disconnection**: Refactor punishment login checks to happen during the network login handshake phase (e.g. `ServerLoginPacketListenerImpl`) rather than `PlayerLoggedInEvent`. This prevents banned players from briefly appearing in the world or triggering "Player joined the game" messages before being kicked, which is especially noticeable on integrated servers like Essential.
- [ ] **Staff Mod Inspection (`/modinspec`)**: Cache the `IntegrityResponsePayload` mod lists in `ServerIntegrityValidator` upon successful login. Implement a `/modinspec <player>` command to allow staff to view a live player's loaded mods on demand without needing to disconnect them, aiding in "gray-area" investigations.
- [ ] **Deterministic Client Token (Machine ID Fallback)**: Enhance the installation token so a stable identifier survives a naive modpack reinstall, instead of resetting whenever `.instaff_token` is deleted.
  - Keep today's behavior as the primary path: random value generated once and persisted to `.instaff_token`.
  - Add a fallback for when no token file exists: derive the value from an OS-level machine identifier instead of generating a fresh random one — `/etc/machine-id` on Linux (plain file read, no dependency needed), `MachineGuid` from the Windows registry, `IOPlatformUUID` on macOS (both via `ProcessBuilder`) — then persist the result to `.instaff_token` for future reads.
  - **Explicitly rejected**: reading physical disk serial numbers (via `oshi`/JNA). Same trust ceiling as the OS machine ID (client still self-reports the value), but adds a heavier cross-platform dependency, worse reliability on NVMe/RAID/VM setups, and raises privacy concerns as a hardware-level identifier. Not worth the added complexity for the same security ceiling.
  - **Known limitation (by design, not a blocker)**: raises the bar against a noob who reinstalls the modpack, but does not stop a determined evader — a modified client can still report an arbitrary value, same trust model as the current token and the client integrity hash check ("deterrent, not cryptographic proof").
  - **Risk to watch**: cloned VM images (common on some VPS/cloud-gaming providers) can share the same Linux `machine-id` if it wasn't regenerated on clone (`systemd-machine-id-setup`), which could cause false-positive collisions between two legitimate players on different cloud instances. No fix beyond documenting it for now; revisit if it generates real complaints.
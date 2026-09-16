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

- [ ] **Logging Standardization**: Replace generic `System.err.println` and `printStackTrace()` in defensive tick-loop try/catch blocks (`ProtectionEventHandler`, `ModerationEventHandler`, `IntegrityEventHandler`) with the centralized `InStaff.LOGGER.error`.
- [ ] **Command Delegation Strictness**: Refactor punishment commands (e.g., `/kick`, `/ban`) to fully delegate player disconnection logic to `PunishmentManager` instead of issuing the `connection.disconnect()` call directly from the command execution layer.
- [ ] **Curios Accessory Integration**: Implement soft-dependency support for inspecting and modifying Curios slots via `/invsee`.
- [ ] **Mute Command Bypass**: Intercept `CommandEvent` in `ModerationEventHandler` to prevent muted players from using chat-related commands (`/msg`, `/tell`, `/g`, `/global`, etc.) that bypass `ServerChatEvent`.

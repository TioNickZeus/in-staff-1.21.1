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

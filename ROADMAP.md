# In-Staff Roadmap & Feature Board

This document tracks planned features, current sprint goals, and technical milestones for **In-Staff** (Minecraft 1.21.1 / NeoForge).

---

## Version 1.0.0 — Foundation & Moderation Suite

- [x] **Project Initialization & Scaffolding**: NeoForge 1.21.1 template cleaned, build scripts tuned, and architectural guidelines established.
- [x] **Client + Server Architecture**: Dual-sided mod with `InStaffClient.java` client entrypoint, `client/` package structure, and side separation rules.
- [ ] **Core Moderation Engine**:
  - [ ] UUID-based ban and temp-ban system with custom kick/rejection screen.
  - [ ] Chat mute and temp-mute system with remaining time alerts.
  - [ ] Player freeze mechanic restricting movement, interactions, and rotation (server + client enforcement).
  - [ ] Punishment audit history ledger (`/history <player>`).
  - [ ] Human duration parser (`1d`, `12h`, `30m`).
  - [ ] Offline server (`online-mode=false`) compatibility (local `UUIDUtil.createOfflinePlayerUUID` and `GameProfileCache` resolution, zero Mojang HTTP queries).
  - [ ] Offline ban evasion mitigation (dual IP + client installation token binding).
- [ ] **Access & Maintenance**:
  - [ ] Dynamic maintenance mode with custom MOTD and staff whitelist bypass.
  - [ ] In-game dynamic whitelist with live reload and custom kick messages.
  - [ ] Player playtime tracker and `/seen` last-login telemetry.
- [ ] **Inspection & Container Auditing**:
  - [ ] Live online `/invsee` with bidirectional synchronization.
  - [ ] Client-side `InvseeScreen` rendering.
  - [ ] Live online `/endersee` for Ender Chest inspection.
  - [ ] Client-side `EnderseeScreen` rendering.
  - [ ] Offline player inventory loading and saving via `playerdata/<UUID>.dat`.
  - [ ] Curios accessory integration (soft dependency).
- [ ] **World Protection & Health**:
  - [ ] Ban-Item system with granular modes (`TOTAL`, `NO_USE`, `NO_PLACE`).
  - [ ] Client-side banned item visual feedback (grayed-out rendering).
  - [ ] Safe-Unload / Chunk Quarantine for corrupted ticking block entities and entities.
- [ ] **Client Integrity (Anti-Cheat)**:
  - [ ] Client-side hash scanner (`ClientHashScanner`) for `mods/` and `resourcepacks/`.
  - [ ] Server-side hash validation with configurable whitelist/blacklist.
  - [ ] Network handshake payloads (`IntegrityRequestPayload`, `IntegrityResponsePayload`).
  - [ ] Configurable timeout and kick messages.